import './message-center.css';
import { MessageCenterTabs } from '../../shared/ui/MessageCenterTabs';
import { ListPagination } from '../../shared/ui/ListPagination';
import { RevenueNotices } from '../revenue/RevenueSignals';
import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
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
  const [params, setParams] = useSearchParams();
  const category = ['inactivity', 'revenue'].includes(params.get('category') ?? '')
    ? params.get('category')!
    : 'approval';
  const [total, setTotal] = useState(0);
  const [items, setItems] = useState<ApprovalNotification[]>([]);
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  const [unreadOnly, setUnreadOnly] = useState(true);
  const [revision, setRevision] = useState(0);
  const [reading, setReading] = useState<string | null>(null);
  useEffect(() => {
    if (category !== 'approval') return;
    let active = true;
    setBusy(true);
    projectsApi
      .notifications(page, unreadOnly)
      .then((result) => {
        if (active) {
          setItems(result.items);
          setPages(result.totalPages);
          setTotal(result.totalElements);
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
  }, [page, unreadOnly, revision, category]);
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
    <section className="message-center">
      <div className="page-heading">
        <div>
          <h1>消息通知</h1>
          <p>按业务查看申请动态与项目督办，及时处理需要关注的事项。</p>
        </div>
      </div>
      <MessageCenterTabs
        label="消息分类"
        value={category}
        onChange={(value) => {
          const next = new URLSearchParams(params);
          next.set('category', value);
          setParams(next);
        }}
        options={[
          { value: 'approval', label: '审批通知', description: '申请结果与逾期提醒' },
          { value: 'inactivity', label: '项目督办', description: '超时跟进与处理记录' },
          { value: 'revenue', label: '计收通知', description: '计收认定审核动态' },
        ]}
      />
      {category === 'revenue' ? (
        <RevenueNotices />
      ) : category === 'inactivity' ? (
        <ProjectInactivityNotices />
      ) : (
        <section className="panel table-panel message-section" aria-label="审批通知列表">
          <div className="panel-top message-section-heading">
            <div>
              <h2>审批通知</h2>
              <p className="subtle">标记已读不代表审批已完成，请进入申请处理。</p>
            </div>
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
          </div>
          {error && <Feedback tone="error">{error}</Feedback>}
          {busy ? (
            <Loading />
          ) : (
            <>
              <ul className="message-list">
                {items.map((item) => (
                  <li className="message-item" key={item.id} data-unread={!item.readAt}>
                    <div>
                      <Link
                        className="message-item-title"
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
                      <div className="message-item-meta">
                        <span
                          className={`message-state ${item.readAt ? '' : 'message-state-unread'}`}
                        >
                          {item.readAt ? '已读' : '未读'}
                        </span>
                        <time dateTime={item.createdAt}>{dateTime(item.createdAt)}</time>
                        <span>项目审批</span>
                      </div>
                    </div>
                    <div className="message-item-actions">
                      {!item.readAt && (
                        <button
                          type="button"
                          className="secondary-button"
                          disabled={reading !== null}
                          onClick={() => void read(item)}
                        >
                          {reading === item.id ? '正在标记…' : '标记已读'}
                        </button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
              {!items.length && <div className="empty-state">暂无通知</div>}
              {pages > 1 && (
                <ListPagination
                  label="审批通知"
                  page={page}
                  totalPages={pages}
                  totalElements={total}
                  onPageChange={setPage}
                />
              )}
            </>
          )}
        </section>
      )}
    </section>
  );
}
