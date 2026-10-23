import { Select } from '../../shared/ui/Select';
import { Modal } from '../../shared/ui/Modal';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { projectsApi } from '../projects/api';
import { openProjectDocument } from '../projects/openDocument';
import type {
  ApprovalSummary,
  ApprovalTask,
  ProjectDocument,
  ProjectUser,
  ReferenceData,
} from '../projects/types';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { amount, dateTime } from '../../shared/ui/format';
import { changeFieldLabels, changeValue } from '../projects/majorChangeFields';
import { refreshNotificationCounts } from './notificationEvents';

const statusLabels: Record<ApprovalSummary['status'], string> = {
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤销',
  CONFLICT: '数据冲突',
};
const changeLabels: Record<NonNullable<ApprovalSummary['changeType']>, string> = {
  PROJECT_NAME: '名称变更',
  TOTAL_AMOUNT: '金额变更',
  STAGE_ROLLBACK: '阶段回退',
  TERMINATE: '项目终止',
  FIELD_CHANGES: '重大变更',
};

function title(request: ApprovalSummary, reference: ReferenceData) {
  if (request.requestType === 'MAJOR_CHANGE')
    return changeLabels[request.changeType!] ?? '重大变更';
  const label = (code: string) =>
    reference.stages.find((item) => item.code === code)?.label ?? code;
  return `${label(request.fromStageCode)} → ${label(request.targetStageCode ?? '')}`;
}

export function ApprovalsPage({ reference }: { reference: ReferenceData }) {
  const [items, setItems] = useState<ApprovalTask[]>([]);
  const [summaries, setSummaries] = useState<ApprovalSummary[]>([]);
  const [view, setView] = useState<'inbox' | 'submitted' | 'records'>('inbox');
  const [status, setStatus] = useState<ApprovalSummary['status'] | ''>('');
  const [revision, setRevision] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setItems([]);
    setSummaries([]);
    setTotalPages(0);
    setTotalElements(0);
    const source =
      view === 'inbox'
        ? projectsApi.approvalInbox(page)
        : view === 'submitted'
          ? projectsApi.submittedApprovals(page)
          : projectsApi.approvalRecords(page, status);
    source
      .then((result) => {
        if (!active) return;
        if (view === 'inbox') setItems(result.items as ApprovalTask[]);
        else setSummaries(result.items as ApprovalSummary[]);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '待办加载失败');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [page, view, status, revision]);
  const label = (code: string | null) =>
    reference.stages.find((item) => item.code === code)?.label ?? code ?? '—';
  return (
    <section>
      <div className="page-heading">
        <div>
          <span className="eyebrow">项目管理 / 审批</span>
          <h1>审批与申请</h1>
          <p>
            待办仅显示指定给我的申请；申请及记录均按账号当前项目数据范围读取，查看记录不授予审批权。
          </p>
        </div>
      </div>
      <div className="action-row" role="group" aria-label="审批视图">
        <button
          type="button"
          className={view === 'inbox' ? 'primary-button' : 'secondary-button'}
          onClick={() => {
            setPage(0);
            setView('inbox');
          }}
        >
          待我审批
        </button>
        <button
          type="button"
          className={view === 'submitted' ? 'primary-button' : 'secondary-button'}
          onClick={() => {
            setPage(0);
            setView('submitted');
          }}
        >
          我发起的申请
        </button>
        <button
          type="button"
          className={view === 'records' ? 'primary-button' : 'secondary-button'}
          onClick={() => {
            setPage(0);
            setView('records');
          }}
        >
          可见审批记录
        </button>
      </div>
      {view === 'records' && (
        <label>
          审批记录状态
          <Select
            value={status}
            onChange={(event) => {
              setPage(0);
              setStatus(event.target.value as typeof status);
            }}
          >
            <option value="">全部状态</option>
            {Object.entries(statusLabels).map(([code, text]) => (
              <option value={code} key={code}>
                {text}
              </option>
            ))}
          </Select>
        </label>
      )}
      {error && (
        <Feedback tone="error">
          {error}
          <button
            type="button"
            className="inline-link"
            onClick={() => setRevision((value) => value + 1)}
          >
            重新加载审批列表
          </button>
        </Feedback>
      )}
      {loading ? (
        <Loading />
      ) : (
        <div className="panel table-panel">
          <div className="panel-top">
            <strong>
              {view === 'inbox'
                ? '待审批申请'
                : view === 'submitted'
                  ? '我发起的申请'
                  : '可见审批记录'}
            </strong>
            <span>共 {totalElements} 条</span>
          </div>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>项目与申请</th>
                  <th>阶段</th>
                  <th>提交时间</th>
                  <th>{view === 'inbox' ? '截止时间' : '状态'}</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {view === 'inbox'
                  ? items.map((item) => (
                      <tr key={item.requestId}>
                        <td>
                          <strong>{item.projectName}</strong>
                          <small className="table-subtitle">
                            {item.requestType === 'STAGE_ADVANCEMENT' ? '阶段推进' : '重大变更'}
                          </small>
                        </td>
                        <td>
                          {item.targetStageCode
                            ? `${label(item.fromStageCode)} → ${label(item.targetStageCode)}`
                            : `${label(item.fromStageCode)}（阶段不变）`}
                        </td>
                        <td>{dateTime(item.submittedAt)}</td>
                        <td>
                          {dateTime(item.dueAt)}
                          {item.overdue && <span className="overdue">已逾期</span>}
                        </td>
                        <td>
                          <Link className="inline-link" to={`/approvals/${item.requestId}`}>
                            查看并处理
                          </Link>
                        </td>
                      </tr>
                    ))
                  : summaries.map((item) => (
                      <tr key={item.id}>
                        <td>
                          <Link className="table-link" to={`/projects/${item.projectId}`}>
                            {item.originalProjectName}
                          </Link>
                          <small className="table-subtitle">
                            {item.requestType === 'STAGE_ADVANCEMENT'
                              ? '阶段推进'
                              : (changeLabels[item.changeType!] ?? '重大变更')}
                          </small>
                        </td>
                        <td>
                          {item.targetStageCode
                            ? `${label(item.fromStageCode)} → ${label(item.targetStageCode)}`
                            : label(item.fromStageCode)}
                        </td>
                        <td>{dateTime(item.submittedAt)}</td>
                        <td>
                          {statusLabels[item.status]}
                          {item.reviewOpinion && (
                            <small className="table-subtitle">{item.reviewOpinion}</small>
                          )}
                        </td>
                        <td>
                          <Link className="inline-link" to={`/approvals/${item.id}`}>
                            查看申请
                          </Link>
                        </td>
                      </tr>
                    ))}
              </tbody>
            </table>
          </div>
          {(view === 'inbox' ? !items.length : !summaries.length) && (
            <div className="empty-state">暂无申请</div>
          )}
          {totalPages > 1 && (
            <div className="pager">
              <span>
                第 {page + 1} / {totalPages} 页
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
                  disabled={page + 1 >= totalPages}
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

export function ApprovalDetailPage({
  me,
  reference,
}: {
  me: ProjectUser;
  reference: ReferenceData;
}) {
  const { id } = useParams();
  const [item, setItem] = useState<ApprovalSummary | null>(null);
  const [opinion, setOpinion] = useState('');
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const [evidence, setEvidence] = useState<ProjectDocument | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const loadRevision = useRef(0);
  const load = useCallback(async () => {
    if (!id) return;
    const revision = ++loadRevision.current;
    setError('');
    try {
      const request = await projectsApi.approval(id);
      if (revision !== loadRevision.current) return;
      setItem(request);
      if (request.evidenceDocumentId) {
        const files = await projectsApi.documents(request.projectId);
        if (revision === loadRevision.current)
          setEvidence(files.find((file) => file.id === request.evidenceDocumentId) ?? null);
      }
    } catch (cause) {
      if (revision === loadRevision.current)
        setError(cause instanceof Error ? cause.message : '申请加载失败');
    }
  }, [id]);
  useEffect(() => {
    void load();
    return () => {
      loadRevision.current += 1;
    };
  }, [load]);

  async function decide(approved: boolean) {
    if (!id) return;
    // 用户决策后，初始化请求不得覆盖校验提示或审批结果。
    loadRevision.current += 1;
    if (!approved && !opinion.trim()) {
      setError('驳回必须填写审核意见。');
      return;
    }
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const result = await projectsApi.decide(id, approved, opinion.trim());
      setItem(result);
      refreshNotificationCounts();
      setMessage(
        approved ? '审批已通过，项目正式数据已更新。' : '申请已驳回，项目正式数据未变化。',
      );
    } catch (cause) {
      await load();
      setError(cause instanceof Error ? cause.message : '审批失败，请刷新申请后重试');
    } finally {
      setBusy(false);
    }
  }

  async function openEvidence() {
    if (!item || !evidence) return;
    try {
      await openProjectDocument(item.projectId, evidence.id);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '材料打开失败');
    }
  }

  async function withdraw() {
    if (!id || busy || item?.status !== 'PENDING') return;
    loadRevision.current += 1;
    setBusy(true);
    setError('');
    try {
      const result = await projectsApi.withdraw(id);
      setItem((current) =>
        current
          ? { ...current, status: result.status, decidedAt: result.decidedAt, canReview: false }
          : null,
      );
      setMessage('申请已撤销。');
      setWithdrawOpen(false);
      refreshNotificationCounts();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '撤销失败');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section>
      <div className="page-heading">
        <div>
          <Link className="back-link" to="/approvals">
            ← 返回审批待办
          </Link>
          <h1>申请详情</h1>
          <p>审批通过前不更改项目正式数据。</p>
        </div>
      </div>
      {error && <Feedback tone="error">{error}</Feedback>}
      {message && <Feedback tone="success">{message}</Feedback>}
      {!item && !error && <Loading />}
      {item && (
        <div className="panel approval-detail">
          <div className="approval-header">
            <div>
              <span className="eyebrow">
                {item.requestType === 'STAGE_ADVANCEMENT' ? '阶段推进' : '重大变更'}
              </span>
              <h2>{title(item, reference)}</h2>
            </div>
            <span className="stage">{statusLabels[item.status]}</span>
          </div>
          <dl className="facts">
            <div>
              <dt>项目</dt>
              <dd>
                <Link className="inline-link" to={`/projects/${item.projectId}`}>
                  {item.originalProjectName}
                </Link>
              </dd>
            </div>
            <div>
              <dt>提交时间 / 截止时间</dt>
              <dd>
                {dateTime(item.submittedAt)} / {dateTime(item.dueAt)}
              </dd>
            </div>
            {item.proposedProjectName && !item.fieldChanges?.length && (
              <div>
                <dt>新名称</dt>
                <dd>{item.proposedProjectName}</dd>
              </div>
            )}
            {item.proposedTotalAmount != null && !item.fieldChanges?.length && (
              <div>
                <dt>调整后总金额</dt>
                <dd>{amount(item.proposedTotalAmount)} 万元</dd>
              </div>
            )}
            {item.proposedContractNo && (
              <div>
                <dt>合同编号</dt>
                <dd>{item.proposedContractNo}</dd>
              </div>
            )}
            {item.proposedSignedAmount != null && (
              <div>
                <dt>签约金额</dt>
                <dd>{amount(item.proposedSignedAmount)} 万元</dd>
              </div>
            )}
            {item.stageOccurredAt && (
              <div>
                <dt>阶段发生日期</dt>
                <dd>
                  {new Date(item.stageOccurredAt).toLocaleDateString('zh-CN', {
                    timeZone: 'Asia/Shanghai',
                  })}
                </dd>
              </div>
            )}
            {item.evidenceDocumentId && (
              <div className="span-two">
                <dt>阶段材料</dt>
                <dd>
                  {evidence ? (
                    <button
                      type="button"
                      className="text-button"
                      onClick={() => void openEvidence()}
                    >
                      {evidence.originalName}（查看）
                    </button>
                  ) : (
                    '材料暂不可用'
                  )}
                </dd>
              </div>
            )}
            {item.changeReason && (
              <div className="span-two">
                <dt>变更原因</dt>
                <dd>{item.changeReason}</dd>
              </div>
            )}
            {item.reviewOpinion && (
              <div className="span-two">
                <dt>审核意见</dt>
                <dd>{item.reviewOpinion}</dd>
              </div>
            )}
          </dl>
          {!!item.fieldChanges?.length && (
            <div className="table-scroll">
              <table className="field-change-table" aria-label="重大变更字段对照">
                <thead>
                  <tr>
                    <th>变更字段</th>
                    <th>提交时原值</th>
                    <th>申请目标值</th>
                  </tr>
                </thead>
                <tbody>
                  {item.fieldChanges.map((field) => (
                    <tr key={field.fieldCode}>
                      <td>{changeFieldLabels[field.fieldCode]}</td>
                      <td>{changeValue(field.fieldCode, field.originalValue, reference)}</td>
                      <td>{changeValue(field.fieldCode, field.proposedValue, reference)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {item.status === 'PENDING' &&
            item.canReview &&
            item.approverId === me.id &&
            !me.roleCodes.includes('SYSTEM_ADMIN') && (
              <form
                className="request-form"
                onSubmit={(event) => {
                  event.preventDefault();
                  void decide(true);
                }}
              >
                <label>
                  审核意见（通过时可选，驳回时必填）
                  <textarea
                    rows={3}
                    maxLength={2000}
                    value={opinion}
                    onChange={(event) => setOpinion(event.target.value)}
                  />
                </label>
                <div className="form-actions">
                  <button
                    type="button"
                    className="secondary-button"
                    disabled={busy}
                    onClick={() => void decide(false)}
                  >
                    驳回申请
                  </button>
                  <button
                    type="submit"
                    className="primary-button"
                    disabled={busy || (!!item.evidenceDocumentId && !evidence)}
                  >
                    {busy ? '处理中…' : '审批通过'}
                  </button>
                </div>
              </form>
            )}
          {item.status === 'PENDING' && item.initiatorId === me.id && (
            <div className="form-actions">
              <button
                type="button"
                className="secondary-button"
                disabled={busy}
                onClick={() => setWithdrawOpen(true)}
              >
                撤销申请
              </button>
            </div>
          )}
          {withdrawOpen && (
            <Modal title="撤销审批申请" busy={busy} onClose={() => setWithdrawOpen(false)}>
              <h2 tabIndex={-1}>确认撤销这条待审申请？</h2>
              <p>撤销后不能继续审批，申请记录仍会保留。如需再次申请，请新建申请。</p>
              {error && <Feedback tone="error">{error}</Feedback>}
              <div className="form-actions">
                <button
                  type="button"
                  className="secondary-button"
                  disabled={busy}
                  onClick={() => setWithdrawOpen(false)}
                >
                  保留申请
                </button>
                <button
                  type="button"
                  className="primary-button"
                  disabled={busy}
                  onClick={() => void withdraw()}
                >
                  {busy ? '撤销中…' : '确认撤销'}
                </button>
              </div>
            </Modal>
          )}
        </div>
      )}
    </section>
  );
}
