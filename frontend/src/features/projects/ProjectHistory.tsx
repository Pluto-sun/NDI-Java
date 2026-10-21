import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { projectsApi } from './api';
import type {
  ApprovalSummary,
  MonthlyRevision,
  ProjectChange,
  ProjectUser,
  ReferenceData,
} from './types';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { dateTime } from '../../shared/ui/format';
import type { Page } from '../../shared/api/http';

const eventLabels: Record<string, string> = {
  PROJECT_CREATED: '项目创建',
  PROJECT_PROFILE_UPDATED: '普通信息维护',
  STAGE_ADVANCEMENT_APPROVED: '阶段推进通过',
  MAJOR_CHANGE_APPROVED: '重大变更通过',
};
const statusLabels: Record<string, string> = {
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤销',
  CONFLICT: '数据冲突',
};

function readablePayload(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

const categoryKeys = ['network', 'gewu', 'bigData', 'wangwu'] as const;
function hasCategoryChanges(item: MonthlyRevision) {
  return (
    !!item.beforeCategories &&
    !!item.afterCategories &&
    categoryKeys.some((key) => item.beforeCategories![key] !== item.afterCategories![key])
  );
}

export function ProjectHistory({
  projectId,
  me,
  reference,
}: {
  projectId: string;
  me: ProjectUser;
  reference: ReferenceData;
}) {
  const [page, setPage] = useState(0);
  const [approvals, setApprovals] = useState<Page<ApprovalSummary> | null>(null);
  const [changes, setChanges] = useState<Page<ProjectChange> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [month, setMonth] = useState(() => {
    const date = new Date();
    date.setMonth(date.getMonth() - 1);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
  });
  const [revisions, setRevisions] = useState<MonthlyRevision[] | null>(null);
  const [revisionError, setRevisionError] = useState('');
  useEffect(() => {
    let active = true;
    setLoading(true);
    Promise.all([
      projectsApi.projectApprovals(projectId, page),
      projectsApi.projectChanges(projectId, page),
    ])
      .then(([requests, events]) => {
        if (active) {
          setApprovals(requests);
          setChanges(events);
          setError('');
        }
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '历史记录加载失败');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [projectId, page]);
  const stage = (code: string | null) =>
    reference.stages.find((item) => item.code === code)?.label ?? code ?? '—';
  async function loadRevisions() {
    setRevisionError('');
    setRevisions(null);
    try {
      setRevisions(await projectsApi.monthlyRevisions(projectId, month));
    } catch (cause) {
      setRevisionError(cause instanceof Error ? cause.message : '快照修订加载失败');
    }
  }
  return (
    <div className="panel project-history">
      <h2>审批与变更历史</h2>
      {error && <Feedback tone="error">{error}</Feedback>}
      {loading ? (
        <Loading />
      ) : (
        <>
          <h3>审批申请</h3>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>申请</th>
                  <th>阶段</th>
                  <th>状态</th>
                  <th>发起时间</th>
                  <th>详情</th>
                </tr>
              </thead>
              <tbody>
                {approvals?.items.map((item) => (
                  <tr key={item.id}>
                    <td>
                      {item.requestType === 'STAGE_ADVANCEMENT' ? '阶段推进' : '重大变更'}
                      {item.changeReason && (
                        <small className="table-subtitle">{item.changeReason}</small>
                      )}
                    </td>
                    <td>
                      {stage(item.fromStageCode)}
                      {item.targetStageCode && ` → ${stage(item.targetStageCode)}`}
                    </td>
                    <td>
                      {statusLabels[item.status]}
                      {item.reviewOpinion && (
                        <small className="table-subtitle">{item.reviewOpinion}</small>
                      )}
                    </td>
                    <td>{dateTime(item.submittedAt)}</td>
                    <td>
                      {[item.initiatorId, item.approverId].includes(me.id) && (
                        <Link className="inline-link" to={`/approvals/${item.id}`}>
                          查看申请
                        </Link>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!approvals?.items.length && <div className="empty-state">暂无审批申请</div>}
          <h3>正式数据变更</h3>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>变更</th>
                  <th>版本</th>
                  <th>时间</th>
                  <th>内容</th>
                </tr>
              </thead>
              <tbody>
                {changes?.items.map((item) => (
                  <tr key={item.id}>
                    <td>{eventLabels[item.eventType] ?? item.eventType}</td>
                    <td>{item.aggregateVersion}</td>
                    <td>{dateTime(item.occurredAt)}</td>
                    <td>
                      <details>
                        <summary>查看变更记录</summary>
                        <pre className="json-preview">{readablePayload(item.payload)}</pre>
                      </details>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!changes?.items.length && <div className="empty-state">暂无正式变更记录</div>}
          {Math.max(approvals?.totalPages ?? 0, changes?.totalPages ?? 0) > 1 && (
            <div className="pager">
              <span>第 {page + 1} 页</span>
              <div>
                <button
                  type="button"
                  className="secondary-button"
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                >
                  上一页
                </button>
                <button
                  type="button"
                  className="secondary-button"
                  disabled={
                    page + 1 >= Math.max(approvals?.totalPages ?? 0, changes?.totalPages ?? 0)
                  }
                  onClick={() => setPage(page + 1)}
                >
                  下一页
                </button>
              </div>
            </div>
          )}
        </>
      )}
      <h3>月末快照修订</h3>
      <p className="subtle">
        历史月份仅在快照生成后可查询。审批跨月通过时，相关项目的历史数字可能被修订。
      </p>
      <div className="action-row">
        <label>
          月份
          <input type="month" value={month} onChange={(event) => setMonth(event.target.value)} />
        </label>
        <button
          className="secondary-button"
          type="button"
          disabled={!month}
          onClick={() => void loadRevisions()}
        >
          查询修订
        </button>
      </div>
      {revisionError && <Feedback tone="error">{revisionError}</Feedback>}
      {revisions &&
        (revisions.length ? (
          <div className="timeline">
            {revisions.map((item) => (
              <article key={item.revisionNo}>
                <time>
                  {dateTime(item.revisedAt)} · 第 {item.revisionNo} 次修订
                </time>
                <p>{item.reason}</p>
                <small>
                  {item.beforeStageCode &&
                    item.afterStageCode &&
                    `阶段：${stage(item.beforeStageCode)} → ${stage(item.afterStageCode)}；`}
                  {item.beforeIndustryCode &&
                    item.afterIndustryCode &&
                    `行业：${item.beforeIndustryCode} → ${item.afterIndustryCode}；`}
                  {item.beforeTotalAmount != null &&
                    item.afterTotalAmount != null &&
                    `金额：${item.beforeTotalAmount} → ${item.afterTotalAmount} 万元`}
                </small>
                {hasCategoryChanges(item) && (
                  <div className="table-scroll">
                    <table className="field-change-table" aria-label="分类金额修订对照">
                      <thead>
                        <tr>
                          <th>分类</th>
                          <th>修订前（万元）</th>
                          <th>修订后（万元）</th>
                        </tr>
                      </thead>
                      <tbody>
                        {categoryKeys
                          .filter(
                            (code) => item.beforeCategories![code] !== item.afterCategories![code],
                          )
                          .map((code) => (
                            <tr key={code}>
                              <td>
                                {
                                  {
                                    network: '网络化',
                                    gewu: '格物',
                                    bigData: '大数据',
                                    wangwu: '万悟',
                                  }[code]
                                }
                              </td>
                              <td>{item.beforeCategories![code]}</td>
                              <td>{item.afterCategories![code]}</td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </article>
            ))}
          </div>
        ) : (
          <div className="empty-state">该月份没有修订记录</div>
        ))}
    </div>
  );
}
