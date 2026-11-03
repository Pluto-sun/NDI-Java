import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { ListPagination } from '../../shared/ui/ListPagination';
import { dateTime } from '../../shared/ui/format';
import { refreshNotificationCounts } from '../approvals/notificationEvents';
import { revenueApi, statusLabels, money, type Approval, type Notice } from './api';
const message = (e: unknown) => (e instanceof Error ? e.message : '操作失败，请稍后重试');
export function RevenuePending() {
  const [view, setView] = useState('pending'),
    [page, setPage] = useState(0);
  const [pages, setPages] = useState(0),
    [total, setTotal] = useState(0);
  const [items, setItems] = useState<Approval[]>([]),
    [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    const pending =
      view === 'pending'
        ? revenueApi
            .pending()
            .then((items) => ({ items, totalPages: 1, totalElements: items.length }))
        : revenueApi.requestPage(view, page);
    pending
      .then((result) => {
        if (active) {
          setItems(result.items);
          setPages(result.totalPages);
          setTotal(result.totalElements);
        }
      })
      .catch((e) => {
        if (active) setError(message(e));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [view, page]);
  return (
    <section className="message-section" aria-label="计收审核列表">
      <div className="message-section-heading">
        <div>
          <h2>计收审核待办</h2>
          <p className="subtle">查看认定金额、发起人与办理状态，进入计收详情处理申请。</p>
        </div>
      </div>
      <div className="action-row" role="group" aria-label="计收审核视图">
        {[
          ['pending', '计收待我审核'],
          ['mine', '计收我发起的申请'],
          ['history', '计收审核历史'],
        ].map(([key, label]) => (
          <button
            type="button"
            key={key}
            className="secondary-button"
            aria-pressed={view === key}
            onClick={() => {
              setPage(0);
              setView(key);
            }}
          >
            {label}
          </button>
        ))}
      </div>
      <div className="panel table-panel">
        <div className="panel-top">
          <strong>
            {view === 'pending' ? '待审核申请' : view === 'mine' ? '我发起的申请' : '审核历史'}
          </strong>
          {!loading && <span>共 {total} 条</span>}
        </div>
        {error && <Feedback tone="error">{error}</Feedback>}
        {loading ? (
          <Loading />
        ) : (
          !error && (
            <>
              <ul className="message-list">
                {items.map((r) => (
                  <li className="message-item" key={r.id}>
                    <div>
                      <Link className="message-item-title" to={`/revenue/${r.entryId}`}>
                        {r.proposed.reportedProjectName}
                      </Link>
                      <div className="message-item-meta">
                        <span className={`message-state message-state-${r.status.toLowerCase()}`}>
                          {statusLabels[r.status]}
                        </span>
                        <span>发起人：{r.initiatorName}</span>
                        <time dateTime={r.createdAt}>{dateTime(r.createdAt)}</time>
                      </div>
                    </div>
                    <div className="message-item-actions">
                      <strong className="message-amount">
                        {money(r.proposed.amount)} <small>元</small>
                      </strong>
                      <Link className="inline-link" to={`/revenue/${r.entryId}`}>
                        {view === 'pending' ? '查看并处理' : '查看申请'}
                      </Link>
                    </div>
                  </li>
                ))}
              </ul>
              {!items.length && <div className="empty-state">暂无计收审批记录</div>}
              {pages > 1 && (
                <ListPagination
                  label="计收申请"
                  page={page}
                  totalPages={pages}
                  totalElements={total}
                  onPageChange={setPage}
                />
              )}
            </>
          )
        )}
      </div>
    </section>
  );
}
export function RevenueNotices() {
  const [items, setItems] = useState<Notice[]>([]),
    [error, setError] = useState('');
  const [loading, setLoading] = useState(true),
    [reading, setReading] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    revenueApi
      .notices()
      .then((items) => {
        if (active) setItems(items);
      })
      .catch((e) => {
        if (active) setError(message(e));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);
  async function read(id: string) {
    setReading(id);
    setError('');
    try {
      await revenueApi.read(id);
      setItems((current) =>
        current.map((item) =>
          item.id === id ? { ...item, readAt: new Date().toISOString() } : item,
        ),
      );
      refreshNotificationCounts();
    } catch (e) {
      setError(message(e));
    } finally {
      setReading(null);
    }
  }
  return (
    <section className="panel table-panel message-section" aria-label="计收通知列表">
      <div className="panel-top message-section-heading">
        <div>
          <h2>计收通知</h2>
          <p className="subtle">计收认定申请与审核结果，点击事项查看详情。</p>
        </div>
      </div>
      {error && <Feedback tone="error">{error}</Feedback>}
      {loading ? (
        <Loading />
      ) : (
        <>
          <ul className="message-list">
            {items.map((n) => (
              <li className="message-item" data-unread={!n.readAt} key={n.id}>
                <div>
                  <Link className="message-item-title" to={`/revenue/${n.entryId}`}>
                    {n.message}
                  </Link>
                  <div className="message-item-meta">
                    <span className={`message-state ${n.readAt ? '' : 'message-state-unread'}`}>
                      {n.readAt ? '已读' : '未读'}
                    </span>
                    <time dateTime={n.createdAt}>{dateTime(n.createdAt)}</time>
                    <span>计收管理</span>
                  </div>
                </div>
                <div className="message-item-actions">
                  {!n.readAt && (
                    <button
                      type="button"
                      className="secondary-button"
                      disabled={reading !== null}
                      onClick={() => void read(n.id)}
                    >
                      {reading === n.id ? '正在标记…' : '标记已读'}
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
          {!items.length && <div className="empty-state">暂无计收通知</div>}
        </>
      )}
    </section>
  );
}
