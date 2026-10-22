import { useEffect, useState } from 'react';
import { projectsApi } from './api';
import type { Page } from '../../shared/api/http';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { dateTime } from '../../shared/ui/format';
import type { ProjectOperationAudit } from './types';

/** 与待办、正式变更和督办链路分别展示，避免把待审操作误读成主档生效。 */
export function ProjectOperationAuditPanel({
  projectId,
  revision,
}: {
  projectId: string;
  revision: number;
}) {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<ProjectOperationAudit> | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setBusy(true);
    setError('');
    projectsApi
      .operationAudit(projectId, page)
      .then((data) => {
        if (active) setResult(data);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '操作审计加载失败');
      })
      .finally(() => {
        if (active) setBusy(false);
      });
    return () => {
      active = false;
    };
  }, [projectId, page, revision, retry]);
  return (
    <section className="panel project-operation-audit" aria-label="项目操作审计">
      <h2>项目操作审计</h2>
      <p className="subtle">
        记录创建、进展、资料维护及审批操作；提交申请、驳回和撤销不代表正式项目变更。
      </p>
      {error && (
        <Feedback tone="error">
          {error}{' '}
          <button
            type="button"
            className="text-button"
            onClick={() => setRetry((value) => value + 1)}
          >
            重试操作审计
          </button>
        </Feedback>
      )}
      {busy ? (
        <Loading />
      ) : (
        !error &&
        result && (
          <>
            <div className="table-scroll">
              <table>
                <caption>项目操作记录</caption>
                <thead>
                  <tr>
                    <th scope="col">时间</th>
                    <th scope="col">操作</th>
                    <th scope="col">操作人</th>
                  </tr>
                </thead>
                <tbody>
                  {result.items.map((item) => (
                    <tr key={item.id}>
                      <td>{dateTime(item.occurredAt)}</td>
                      <td>{item.summary}</td>
                      <td>{item.operatorName}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!result.items.length && <p className="empty-state">暂无操作审计记录</p>}
            {result.totalPages > 1 && (
              <div className="pager">
                <span>
                  第 {page + 1} / {result.totalPages} 页
                </span>
                <div>
                  <button
                    type="button"
                    className="secondary-button"
                    disabled={page === 0}
                    onClick={() => setPage((value) => value - 1)}
                  >
                    上一页操作
                  </button>
                  <button
                    type="button"
                    className="secondary-button"
                    disabled={page + 1 >= result.totalPages}
                    onClick={() => setPage((value) => value + 1)}
                  >
                    下一页操作
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
