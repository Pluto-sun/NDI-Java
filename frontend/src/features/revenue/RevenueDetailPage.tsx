import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { Select } from '../../shared/ui/Select';
import { Modal } from '../../shared/ui/Modal';
import { ApprovalDecisionDialog } from '../../shared/approval/ApprovalDecisionDialog';
import { dateTime } from '../../shared/ui/format';
import { refreshNotificationCounts } from '../approvals/notificationEvents';
import type { ProjectUser } from '../projects/types';
import {
  revenueApi,
  stateLabels,
  statusLabels,
  money,
  type Entry,
  type Reference,
  type Approval,
  type History,
  type Recognition,
} from './api';
import './revenue.css';
const message = (e: unknown) => (e instanceof Error ? e.message : '操作失败，请稍后重试');
function RecognitionForm({
  entry,
  reference,
  onSaved,
  onClose,
  reviewer,
}: {
  entry: Entry;
  reference: Reference;
  onSaved: () => void;
  onClose: () => void;
  reviewer: boolean;
}) {
  const [value, setValue] = useState({
      amount: String(entry.recognition?.amount ?? ''),
      reportedProjectName: entry.recognition?.reportedProjectName ?? '',
      projectStatus: entry.recognition?.projectStatus ?? '',
      integratedProvinceId: entry.recognition?.integratedProvinceId ?? '',
    }),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await revenueApi.recognize(entry, {
        ...value,
        integratedProvinceId: value.integratedProvinceId || null,
      });
      refreshNotificationCounts();
      onSaved();
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal title={entry.recognition ? '修改认定' : '首次认定'} onClose={onClose} busy={busy}>
      <form onSubmit={save}>
        {error && <Feedback tone="error">{error}</Feedback>}
        <p>
          条款金额 {money(entry.clauseAmount)} 元；来源本月计收 {money(entry.sourceAmount)} 元。
          {reviewer ? '本次保存直接生效。' : '提交后由本省接口人审核，审核前保留当前结果。'}
        </p>
        <div className="form-grid">
          <label>
            认定金额（元）
            <input
              required
              inputMode="decimal"
              value={value.amount}
              onChange={(e) => setValue({ ...value, amount: e.target.value })}
            />
          </label>
          <label>
            通报项目名称
            <input
              required
              maxLength={300}
              value={value.reportedProjectName}
              onChange={(e) => setValue({ ...value, reportedProjectName: e.target.value })}
            />
          </label>
          <label>
            项目状态
            <Select
              value={value.projectStatus}
              onChange={(e) => setValue({ ...value, projectStatus: e.target.value })}
            >
              <option value="">未填写</option>
              <option>实施中</option>
              <option>已交付</option>
            </Select>
          </label>
          <label>
            对应省分（集成）
            <Select
              value={value.integratedProvinceId}
              onChange={(e) => setValue({ ...value, integratedProvinceId: e.target.value })}
            >
              <option value="">未填写</option>
              {reference.provinces.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </Select>
          </label>
        </div>
        <div className="dialog-actions">
          <button type="button" className="secondary-button" disabled={busy} onClick={onClose}>
            取消
          </button>
          <button className="primary-button" disabled={busy}>
            {reviewer ? '保存认定' : '提交审核'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
function RecognitionValue({
  value,
  reference,
}: {
  value: Recognition | null;
  reference: Reference;
}) {
  return value ? (
    <dl className="revenue-detail">
      <div>
        <dt>认定金额（元）</dt>
        <dd>{money(value.amount)}</dd>
      </div>
      <div>
        <dt>通报项目名称</dt>
        <dd>{value.reportedProjectName}</dd>
      </div>
      <div>
        <dt>项目状态</dt>
        <dd>{value.projectStatus || '未填写'}</dd>
      </div>
      <div>
        <dt>对应省分（集成）</dt>
        <dd>
          {reference.provinces.find((p) => p.id === value.integratedProvinceId)?.name || '未填写'}
        </dd>
      </div>
    </dl>
  ) : (
    <p className="subtle">尚无生效认定</p>
  );
}
export function RevenueDetailPage({ me }: { me: ProjectUser }) {
  const { id = '' } = useParams();
  const [params] = useSearchParams();
  const back = params.get('from');
  const [entry, setEntry] = useState<Entry | null>(null),
    [reference, setReference] = useState<Reference | null>(null),
    [requests, setRequests] = useState<Approval[]>([]),
    [history, setHistory] = useState<History[]>([]),
    [error, setError] = useState(''),
    [notice, setNotice] = useState(''),
    [editing, setEditing] = useState(false),
    [decision, setDecision] = useState<{ request: Approval; kind: string } | null>(null),
    [opinion, setOpinion] = useState(''),
    [busy, setBusy] = useState(false);
  const loadRevision = useRef(0);
  const load = useCallback(async () => {
    const revision = ++loadRevision.current;
    const [e, r, a, h] = await Promise.all([
      revenueApi.detail(id),
      revenueApi.reference(),
      revenueApi.requests(id),
      revenueApi.history(id),
    ]);
    if (loadRevision.current !== revision) return;
    setEntry(e);
    setReference(r);
    setRequests(a);
    setHistory(h);
  }, [id]);
  useEffect(() => {
    setEntry(null);
    void load().catch((e) => setError(message(e)));
    return () => {
      loadRevision.current += 1;
    };
  }, [load]);
  async function decide() {
    if (!entry || !decision) return;
    setBusy(true);
    try {
      await revenueApi.decide(entry, decision.request, decision.kind, opinion);
      setDecision(null);
      setNotice('审批操作已完成');
      refreshNotificationCounts();
      await load();
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  const openDecision = (request: Approval, kind: string) => {
    setError('');
    setOpinion('');
    setDecision({ request, kind });
  };
  return (
    <>
      <div className="page-heading">
        <h1>计收详情</h1>
        <div className="inline-actions">
          <Link className="secondary-button" to={back?.startsWith('/revenue?') ? back : '/revenue'}>
            返回计收列表
          </Link>
          {entry?.canRecognize && (
            <button className="primary-button" onClick={() => setEditing(true)}>
              {entry.recognition ? '修改认定' : '首次认定'}
            </button>
          )}
        </div>
      </div>
      {error && !decision && <Feedback tone="error">{error}</Feedback>}
      {notice && <Feedback tone="success">{notice}</Feedback>}
      {entry && reference ? (
        <>
          <section className="panel form-panel">
            <h2>来源信息</h2>
            <p>
              月账期 {entry.month} · {stateLabels[entry.state]} · 金额单位：元
            </p>
            <dl className="revenue-detail">
              {reference.fields.map((f) => (
                <div key={f}>
                  <dt>{f}</dt>
                  <dd>{entry.source[f] || '—'}</dd>
                </div>
              ))}
            </dl>
          </section>
          <section className="panel form-panel">
            <h2>当前生效认定</h2>
            <RecognitionValue value={entry.recognition} reference={reference} />
            {entry.recognizedAt && (
              <p className="subtle">生效时间：{dateTime(entry.recognizedAt)}</p>
            )}
          </section>
          <section className="panel form-panel">
            <h2>审核申请</h2>
            {requests.length === 0 && <p>暂无审核申请</p>}
            {requests.map((r) => (
              <article className="revenue-request" key={r.id}>
                <h3>
                  {statusLabels[r.status]} · {r.initiatorName} → {r.approverName}
                </h3>
                <p className="subtle">提交时间 {dateTime(r.createdAt)}</p>
                <RecognitionValue value={r.proposed} reference={reference} />
                {r.opinion && <p>审核意见：{r.opinion}</p>}
                {r.status === 'PENDING' && (
                  <div className="inline-actions">
                    {r.initiatorId === me.id && (
                      <button
                        className="secondary-button"
                        onClick={() => openDecision(r, 'WITHDRAWN')}
                      >
                        撤销申请
                      </button>
                    )}
                    {r.approverId === me.id &&
                      me.roleCodes.includes('INTERFACE_REVIEWER') &&
                      !reference.canCreate && (
                        <>
                          <button
                            className="primary-button"
                            onClick={() => openDecision(r, 'APPROVED')}
                          >
                            通过
                          </button>
                          <button
                            className="secondary-button"
                            onClick={() => openDecision(r, 'REJECTED')}
                          >
                            驳回
                          </button>
                        </>
                      )}
                  </div>
                )}
              </article>
            ))}
          </section>
          <section className="panel form-panel">
            <h2>认定修改历史</h2>
            {history.length === 0 && <p>暂无生效历史</p>}
            {history.map((h) => (
              <article className="revenue-request" key={h.id}>
                <h3>
                  {h.actorName} · {dateTime(h.occurredAt)}
                </h3>
                <p>修改前</p>
                <RecognitionValue value={h.beforeValue} reference={reference} />
                <p>生效后</p>
                <RecognitionValue value={h.afterValue} reference={reference} />
              </article>
            ))}
          </section>
          {editing && (
            <RecognitionForm
              entry={entry}
              reference={reference}
              reviewer={me.roleCodes.includes('INTERFACE_REVIEWER')}
              onClose={() => setEditing(false)}
              onSaved={() => {
                setEditing(false);
                setNotice('认定操作已保存');
                void load().catch((e) => setError(message(e)));
              }}
            />
          )}
          {decision && (
            <ApprovalDecisionDialog
              title={
                decision.kind === 'WITHDRAWN'
                  ? '撤销计收认定申请'
                  : decision.kind === 'APPROVED'
                    ? '通过计收认定申请'
                    : '驳回计收认定申请'
              }
              description="待审值只有审核通过后才会替换当前认定结果。"
              busy={busy}
              error={error}
              onClose={() => setDecision(null)}
              onConfirm={decide}
              opinion={{
                value: opinion,
                onChange: setOpinion,
                required: decision.kind === 'REJECTED',
              }}
            />
          )}
        </>
      ) : (
        !error && <Loading />
      )}
    </>
  );
}
