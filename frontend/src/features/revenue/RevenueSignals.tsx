import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Feedback } from '../../shared/ui/Feedback';
import { dateTime } from '../../shared/ui/format';
import { refreshNotificationCounts } from '../approvals/notificationEvents';
import { revenueApi, statusLabels, type Approval, type Notice } from './api';
const message = (e: unknown) => (e instanceof Error ? e.message : '操作失败，请稍后重试');
export function RevenuePending() {
  const [view, setView] = useState('pending'),
    [page, setPage] = useState(0),
    [pages, setPages] = useState(0);
  const [items, setItems] = useState<Approval[]>([]),
    [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    const pending =
      view === 'pending'
        ? revenueApi.pending().then((items) => ({ items, totalPages: 1 }))
        : revenueApi.requestPage(view, page);
    pending
      .then((result) => {
        if (active) {
          setItems(result.items);
          setPages(result.totalPages);
          setError('');
        }
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, [view, page]);
  return (
    <section className="panel form-panel">
      <h2>计收审核待办</h2>
      <div className="action-row" role="group" aria-label="计收审核视图">
        {[
          ['pending', '计收待我审核'],
          ['mine', '计收我发起的申请'],
          ['history', '计收审核历史'],
        ].map(([key, label]) => (
          <button
            type="button"
            key={key}
            className={view === key ? 'primary-button' : 'secondary-button'}
            onClick={() => {
              setItems([]);
              setPage(0);
              setView(key);
            }}
          >
            {label}
          </button>
        ))}
      </div>
      {error && <Feedback tone="error">{error}</Feedback>}
      {items.length ? (
        items.map((r) => (
          <p key={r.id}>
            <Link to={`/revenue/${r.entryId}`}>
              {r.initiatorName}的计收认定申请 · {statusLabels[r.status]} · {dateTime(r.createdAt)}
            </Link>
          </p>
        ))
      ) : (
        <p className="subtle">暂无计收审批记录</p>
      )}
      {pages > 1 && (
        <div className="pagination">
          <button
            className="secondary-button"
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
          >
            上一页计收申请
          </button>
          <span>
            第{page + 1}/{pages}页
          </span>
          <button
            className="secondary-button"
            disabled={page + 1 >= pages}
            onClick={() => setPage(page + 1)}
          >
            下一页计收申请
          </button>
        </div>
      )}
    </section>
  );
}
export function RevenueNotices() {
  const [items, setItems] = useState<Notice[]>([]),
    [error, setError] = useState('');
  const load = () => revenueApi.notices().then(setItems);
  useEffect(() => {
    let active = true;
    revenueApi
      .notices()
      .then((x) => {
        if (active) setItems(x);
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, []);
  return (
    <section className="panel form-panel">
      <h2>计收通知</h2>
      {error && <Feedback tone="error">{error}</Feedback>}
      {items.length ? (
        items.map((n) => (
          <div className="revenue-notice" key={n.id}>
            <Link to={`/revenue/${n.entryId}`}>{n.message}</Link>
            <span>{dateTime(n.createdAt)}</span>
            {!n.readAt && (
              <button
                className="secondary-button"
                onClick={() => {
                  void revenueApi
                    .read(n.id)
                    .then(() => {
                      refreshNotificationCounts();
                      return load();
                    })
                    .catch((e) => setError(message(e)));
                }}
              >
                标记已读
              </button>
            )}
          </div>
        ))
      ) : (
        <p className="subtle">暂无计收通知</p>
      )}
    </section>
  );
}
