import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { projectsApi } from '../projects/api';
import type { ApprovalNotification } from '../projects/types';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { dateTime } from '../../shared/ui/format';
import { ProjectInactivityNotices } from '../projects/ProjectInactivityNotices';
import { refreshNotificationCounts } from './notificationEvents';

const labels: Record<string, string> = {
  SUBMITTED: '有新的审批申请',
  APPROVED: '申请已通过',
  REJECTED: '申请被驳回',
  REASSIGNED: '审批待办已交接给您',
  WITHDRAWN: '申请已撤销',
  OVERDUE: '审批已逾期',
  CONFLICT: '申请发生冲突',
};

export function NotificationsPage() {
  const navigate = useNavigate();
  const [items, setItems] = useState<ApprovalNotification[]>([]);
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  const [unreadOnly, setUnreadOnly] = useState(true);
  const [revision, setRevision] = useState(0);
  const [reading, setReading] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    setBusy(true);
    projectsApi
      .notifications(page, unreadOnly)
      .then((result) => {
        if (active) {
          setItems(result.items);
          setPages(result.totalPages);
          setError('');
        }
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '通知加载失败');
      })
      .finally(() => {
        if (active) setBusy(false);
      });
    return () => {
      active = false;
    };
  }, [page, unreadOnly, revision]);
  async function read(item: ApprovalNotification, open = false) {
    setError('');
    setReading(item.id);
    try {
      await projectsApi.markNotificationRead(item.id);
      refreshNotificationCounts();
      if (open) {
        void navigate(`/approvals/${item.requestId}`);
        return;
      }
      if (unreadOnly && items.length === 1 && page > 0) setPage(page - 1);
      setRevision((value) => value + 1);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '标记已读失败');
    } finally {
      setReading(null);
    }
  }
  return (
    <section>
      <div className="page-heading">
        <div>
          <span className="eyebrow">NOTIFICATIONS</span>
          <h1>消息通知</h1>
          <p>查看项目督办、申请进展与审批逾期提醒。</p>
        </div>
      </div>
      <ProjectInactivityNotices />
      <h2>审批通知</h2>
      <p className="subtle">消息标记已读不会完成审批；督办需转发或更新项目后闭环。</p>
      <label className="checkbox-label">
        <input
          type="checkbox"
          checked={unreadOnly}
          disabled={reading !== null}
          onChange={(event) => {
            setUnreadOnly(event.target.checked);
            setPage(0);
          }}
        />
        仅看未读审批通知
      </label>
      {error && <Feedback tone="error">{error}</Feedback>}
      {busy ? (
        <Loading />
      ) : (
        <div className="panel table-panel">
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>通知</th>
                  <th>时间</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <Link
                        className="table-link"
                        to={`/approvals/${item.requestId}`}
                        onClick={(event) => {
                          if (
                            item.readAt ||
                            event.metaKey ||
                            event.ctrlKey ||
                            event.shiftKey ||
                            event.altKey ||
                            event.button !== 0
                          )
                            return;
                          event.preventDefault();
                          if (!reading) void read(item, true);
                        }}
                      >
                        {labels[item.kind] ?? item.kind}
                      </Link>
                    </td>
                    <td>{dateTime(item.createdAt)}</td>
                    <td>{item.readAt ? '已读' : '未读'}</td>
                    <td>
                      {!item.readAt && (
                        <button
                          type="button"
                          className="text-button"
                          disabled={reading !== null}
                          onClick={() => void read(item)}
                        >
                          {reading === item.id ? '正在标记…' : '标记已读'}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!items.length && <div className="empty-state">暂无通知</div>}
          {pages > 1 && (
            <div className="pager">
              <span>
                第 {page + 1} / {pages} 页
              </span>
              <div>
                <button
                  className="secondary-button"
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                >
                  上一页
                </button>
                <button
                  className="secondary-button"
                  type="button"
                  disabled={page + 1 >= pages}
                  onClick={() => setPage(page + 1)}
                >
                  下一页
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
