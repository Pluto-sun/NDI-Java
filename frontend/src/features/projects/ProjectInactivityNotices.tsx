import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { dateTime } from '../../shared/ui/format';
import { inactivityApi, type InactivityCase } from './inactivityApi';
import type { ProjectUser } from './types';
import { refreshNotificationCounts } from '../approvals/notificationEvents';

export function ProjectInactivityNotices() {
  const [items, setItems] = useState<InactivityCase[]>([]);
  const [history, setHistory] = useState(false);
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    let active = true;
    setBusy(true);
    setError('');
    inactivityApi
      .mine(history, page)
      .then((result) => {
        if (active) {
          setItems(result.items);
          setPages(result.totalPages);
        }
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '督办加载失败');
      })
      .finally(() => {
        if (active) setBusy(false);
      });
    return () => {
      active = false;
    };
  }, [history, page, revision]);

  return (
    <section className="inactivity-section" aria-labelledby="inactivity-heading">
      <div className="inactivity-heading">
        <div>
          <h2 id="inactivity-heading">项目超时督办</h2>
          <p className="subtle">
            连续 30 天未变动发起督办，33 天和 36 天逐级升级。更新项目进展或正式信息后自动闭环。
            标记已读只清除个人未读提醒，不关闭督办，也不代替转发或更新项目。
          </p>
        </div>
        <div className="form-actions" aria-label="督办列表筛选">
          <button
            type="button"
            className="secondary-button"
            aria-pressed={!history}
            onClick={() => {
              setHistory(false);
              setPage(0);
              setMessage('');
            }}
          >
            待处理
          </button>
          <button
            type="button"
            className="secondary-button"
            aria-pressed={history}
            onClick={() => {
              setHistory(true);
              setPage(0);
              setMessage('');
            }}
          >
            处理历史
          </button>
        </div>
      </div>
      {message && <Feedback tone="success">{message}</Feedback>}
      {error && (
        <Feedback tone="error">
          {error}
          <button
            type="button"
            className="text-button"
            onClick={() => setRevision((value) => value + 1)}
          >
            重新加载督办
          </button>
        </Feedback>
      )}
      {busy ? (
        <Loading />
      ) : (
        !error && (
          <>
            <div className="inactivity-list">
              {items.map((item) => (
                <InactivityCard
                  key={item.id}
                  item={item}
                  onRead={() => {
                    refreshNotificationCounts();
                    setMessage('已标记为已读，督办仍需处理。');
                    setRevision((value) => value + 1);
                  }}
                  onForwarded={() => {
                    refreshNotificationCounts();
                    setMessage('督办已转发，处理记录已保留。');
                    setPage(0);
                    setRevision((value) => value + 1);
                  }}
                />
              ))}
              {!items.length && (
                <div className="panel empty-state">
                  {history ? '暂无督办处理历史' : '暂无待处理督办'}
                </div>
              )}
            </div>
            {pages > 1 && (
              <div className="pager">
                <span>
                  第 {page + 1} / {pages} 页
                </span>
                <div>
                  <button
                    type="button"
                    className="secondary-button"
                    disabled={page === 0}
                    onClick={() => setPage(page - 1)}
                  >
                    上一页督办
                  </button>
                  <button
                    type="button"
                    className="secondary-button"
                    disabled={page + 1 >= pages}
                    onClick={() => setPage(page + 1)}
                  >
                    下一页督办
                  </button>
                </div>
              </div>
            )}
          </>
        )
      )}
    </section>
  );
}

function InactivityCard({
  item,
  onForwarded,
  onRead,
}: {
  item: InactivityCase;
  onForwarded: () => void;
  onRead: () => void;
}) {
  const [recipients, setRecipients] = useState<ProjectUser[] | null>(null);
  const [recipientId, setRecipientId] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function markRead() {
    setBusy(true);
    setError('');
    try {
      await inactivityApi.markRead(item.id);
      onRead();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '标记已读失败');
    } finally {
      setBusy(false);
    }
  }

  async function openForward() {
    setBusy(true);
    setError('');
    try {
      setRecipients(await inactivityApi.recipients(item.id));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '接收人加载失败');
    } finally {
      setBusy(false);
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await inactivityApi.forward(item.id, recipientId, note.trim());
      onForwarded();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '督办转发失败');
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className="panel inactivity-card">
      <div className="inactivity-heading">
        <h3>
          <Link className="table-link" to={`/projects/${item.projectId}`}>
            {item.projectCode} · {item.projectName}
          </Link>
        </h3>
        <span className="subtle">
          {{ OPEN: '待处理', FORWARDED: '已转发', RESOLVED: '已闭环' }[item.status]}
        </span>
      </div>
      <p className="subtle">上次项目变动：{dateTime(item.lastActivityAt)}</p>
      {item.unread ? (
        <button
          type="button"
          className="secondary-button"
          disabled={busy}
          onClick={() => void markRead()}
        >
          {busy ? '处理中…' : '标记督办已读'}
        </button>
      ) : (
        item.status === 'OPEN' && <p className="subtle">已读，仍待处理</p>
      )}
      <ol className="inactivity-events" aria-label="督办处理记录">
        {item.events.map((event, index) => (
          <li key={`${event.kind}-${index}`}>
            <time dateTime={event.occurredAt}>{dateTime(event.occurredAt)}</time>
            <p>{event.summary}</p>
          </li>
        ))}
      </ol>
      {error && <Feedback tone="error">{error}</Feedback>}
      {item.canForward && recipients === null && (
        <button
          type="button"
          className="secondary-button"
          disabled={busy}
          onClick={() => void openForward()}
        >
          {busy ? '加载接收人…' : '转发督办'}
        </button>
      )}
      {item.canForward && recipients !== null && (
        <form className="inline-form" onSubmit={submit}>
          {!recipients.length && (
            <Feedback>暂无符合职责和属地的启用接收人，请联系管理员维护账号后重试。</Feedback>
          )}
          <label>
            接收人
            <select
              required
              value={recipientId}
              disabled={busy}
              onChange={(event) => setRecipientId(event.target.value)}
            >
              <option value="">请选择接收人</option>
              {recipients.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.displayName}
                </option>
              ))}
            </select>
          </label>
          <label>
            督办备注
            <textarea
              required
              maxLength={2000}
              value={note}
              disabled={busy}
              rows={3}
              onChange={(event) => setNote(event.target.value)}
            />
          </label>
          <div className="form-actions">
            <button
              type="button"
              className="secondary-button"
              disabled={busy}
              onClick={() => {
                setRecipients(null);
                setError('');
              }}
            >
              取消
            </button>
            <button
              type="submit"
              className="primary-button"
              disabled={busy || !recipientId || !note.trim()}
            >
              {busy ? '发送中…' : '发送督办'}
            </button>
          </div>
        </form>
      )}
      {!item.canForward && item.status === 'OPEN' && (
        <p className="subtle">请进入项目追加进展或维护项目资料，督办将随有效更新自动闭环。</p>
      )}
    </article>
  );
}
