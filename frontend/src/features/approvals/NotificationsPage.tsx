import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { projectsApi } from '../projects/api';
import type { ApprovalNotification } from '../projects/types';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { dateTime } from '../../shared/ui/format';
import { ProjectInactivityNotices } from '../projects/ProjectInactivityNotices';

const labels: Record<string, string> = {
  SUBMITTED: '有新的审批申请',
  APPROVED: '申请已通过',
  REJECTED: '申请被驳回',
  WITHDRAWN: '申请已撤销',
  OVERDUE: '审批已逾期',
  CONFLICT: '申请发生冲突',
};

export function NotificationsPage() {
  const [items, setItems] = useState<ApprovalNotification[]>([]);
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    setBusy(true);
    projectsApi
      .notifications(page)
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
  }, [page]);
  async function read(item: ApprovalNotification) {
    try {
      const updated = await projectsApi.markNotificationRead(item.id);
      setItems((current) => current.map((value) => (value.id === updated.id ? updated : value)));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '标记已读失败');
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
                      <Link className="table-link" to={`/approvals/${item.requestId}`}>
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
                          onClick={() => void read(item)}
                        >
                          标记已读
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
