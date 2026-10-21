import { useEffect, useState } from 'react';
import { inactivityApi, type InactivityAudit } from './inactivityApi';
import type { Page } from '../../shared/api/http';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { dateTime } from '../../shared/ui/format';

const labels: Record<string, string> = {
  INACTIVITY_MANUAL_TRIGGER: '管理员手动演练',
  INACTIVITY_REMINDER_PROVINCE: '通知省分负责人',
  INACTIVITY_REMINDER_INTERFACE: '通知接口人',
  INACTIVITY_REMINDER_GROUP: '通知集团',
  INACTIVITY_FORWARD: '转发督办',
  INACTIVITY_RESOLVED: '督办闭环',
};

export function ProjectInactivityAuditPanel({
  projectId,
  revision = 0,
}: {
  projectId: string;
  revision?: number;
}) {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<InactivityAudit> | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setBusy(true);
    setError('');
    inactivityApi
      .audit(projectId, page)
      .then((data) => {
        if (active) setResult(data);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '督办审计加载失败');
      })
      .finally(() => {
        if (active) setBusy(false);
      });
    return () => {
      active = false;
    };
  }, [projectId, revision, page, retry]);
  return (
    <section className="panel project-inactivity-audit" aria-label="督办审计记录">
      <h2>督办审计记录</h2>
      <p className="subtle">
        按时间倒序展示发送、转发、闭环和管理员演练记录。手动演练不改变项目实际最近变动时间。
      </p>
      {error ? (
        <Feedback tone="error">
          {error}
          <button
            type="button"
            className="text-button"
            onClick={() => setRetry((value) => value + 1)}
          >
            重新加载督办审计
          </button>
        </Feedback>
      ) : busy ? (
        <Loading />
      ) : (
        result && (
          <>
            <ol className="inactivity-events">
              {result.items.map((item) => (
                <li key={item.id}>
                  <strong>{labels[item.action] ?? item.action}</strong> · {item.actorName}
                  <p>{item.note || '未填写说明'}</p>
                  <time>{dateTime(item.occurredAt)}</time>
                </li>
              ))}
            </ol>
            {!result.items.length && <p className="empty-state">暂无督办审计记录</p>}
            <div className="pager">
              <span>
                共 {result.totalElements} 条 · 第 {page + 1} 页
              </span>
              <button
                type="button"
                className="secondary-button"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                上一页审计
              </button>
              <button
                type="button"
                className="secondary-button"
                disabled={page + 1 >= result.totalPages}
                onClick={() => setPage(page + 1)}
              >
                下一页审计
              </button>
            </div>
          </>
        )
      )}
    </section>
  );
}
