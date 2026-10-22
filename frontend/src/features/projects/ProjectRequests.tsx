import { Select } from '../../shared/ui/Select';
import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { projectsApi } from './api';
import type {
  InitiatorLevel,
  ProjectDetail,
  ProjectDocument,
  ProjectUser,
  ReferenceData,
} from './types';
import { Feedback } from '../../shared/ui/Feedback';
import { MajorChangeForm } from './MajorChangeForm';
import { Modal } from '../../shared/ui/Modal';

const nextStages: Record<string, string | undefined> = {
  SOLUTION_EXCHANGE: 'BIDDING',
  BIDDING: 'SIGNED',
  SIGNED: 'COLLECTION',
};
const evidencePurpose: Record<string, string | undefined> = {
  BIDDING: 'CUSTOMER_SOLUTION',
  SIGNED: 'AWARD_NOTICE',
};

function levels(me: ProjectUser, project: ProjectDetail): InitiatorLevel[] {
  const result: InitiatorLevel[] = [];
  if (me.roleCodes.includes('SYSTEM_ADMIN')) return result;
  if (me.provinceId !== project.provinceId) return result;
  if (me.roleCodes.includes('CITY_REPORTER') && me.cityId === project.cityId) result.push('CITY');
  if (
    me.roleCodes.some((role) =>
      ['PROVINCE_REPORTER', 'PROVINCE_DIGITAL_LEADER', 'PROVINCE_INTELLIGENT_LEADER'].includes(
        role,
      ),
    )
  )
    result.push('PROVINCE');
  return result;
}

export function ProjectRequests({
  project,
  me,
  reference,
  documentsVersion,
}: {
  project: ProjectDetail;
  me: ProjectUser;
  reference: ReferenceData;
  documentsVersion: number;
}) {
  const availableLevels = levels(me, project);
  const [level, setLevel] = useState<InitiatorLevel>(availableLevels[0] ?? 'CITY');
  const [stageApprovers, setStageApprovers] = useState<ProjectUser[]>([]);
  const [changeApprovers, setChangeApprovers] = useState<ProjectUser[]>([]);
  const [documents, setDocuments] = useState<ProjectDocument[]>([]);
  const [stageApproverId, setStageApproverId] = useState('');
  const [stageDate, setStageDate] = useState(new Date().toLocaleDateString('en-CA'));
  const [evidenceId, setEvidenceId] = useState('');
  const [contractNo, setContractNo] = useState('');
  const [signedAmount, setSignedAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [requestId, setRequestId] = useState('');
  const [stageOpen, setStageOpen] = useState(false);
  const target = nextStages[project.currentStageCode];
  const requiredPurpose = target ? evidencePurpose[target] : undefined;
  const evidence = documents.filter((item) => item.purposeCode === requiredPurpose);
  const label = (code: string) =>
    reference.stages.find((item) => item.code === code)?.label ?? code;

  useEffect(() => {
    if (!availableLevels.length) return;
    let active = true;
    Promise.all([
      projectsApi.stageApprovers(project.provinceId),
      projectsApi.majorChangeApprovers(project.provinceId, level),
      projectsApi.documents(project.id),
    ])
      .then(([stageUsers, changeUsers, files]) => {
        if (!active) return;
        setStageApprovers(stageUsers);
        setChangeApprovers(changeUsers);
        setDocuments(files);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '申请选项加载失败');
      });
    return () => {
      active = false;
    };
  }, [project.id, project.provinceId, level, documentsVersion, availableLevels.length]);

  useEffect(() => {
    setStageApproverId('');
  }, [level]);
  if (!availableLevels.length || project.currentStageCode === 'TERMINATED') return null;

  async function submitStage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!target || !stageApproverId) return;
    setBusy(true);
    setError('');
    setRequestId('');
    try {
      const result = await projectsApi.submitStage(project.id, {
        initiatorLevel: level,
        targetStage: target,
        approverId: stageApproverId,
        stageOccurredOn: stageDate,
        ...(requiredPurpose
          ? { evidenceDocumentId: evidenceId }
          : {
              contractNo: contractNo.trim(),
              signedAmount,
            }),
      });
      setRequestId(result.id);
      setStageOpen(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '阶段推进申请失败');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel request-panel">
      <h2>项目申请</h2>
      <p className="subtle">审批通过前，项目主档不变；同一项目同一时间只能有一条待审申请。</p>
      <label className="request-level">
        发起职责
        <Select value={level} onChange={(event) => setLevel(event.target.value as InitiatorLevel)}>
          {availableLevels.map((item) => (
            <option key={item} value={item}>
              {item === 'CITY' ? '地市' : '省分'}
            </option>
          ))}
        </Select>
      </label>
      {error && <Feedback tone="error">{error}</Feedback>}
      {requestId && (
        <Feedback tone="success">
          申请已提交，正式数据等待审批。
          <Link className="inline-link" to={`/approvals/${requestId}`}>
            查看申请
          </Link>
        </Feedback>
      )}
      {target && (
        <button
          type="button"
          className="primary-button"
          disabled={!!requestId}
          onClick={() => setStageOpen(true)}
        >
          发起阶段推进
        </button>
      )}
      {target && stageOpen && (
        <Modal title="阶段推进申请" busy={busy} onClose={() => setStageOpen(false)}>
          <form className="request-form" onSubmit={submitStage}>
            {error && <Feedback tone="error">{error}</Feedback>}
            <h3>
              阶段推进：{label(project.currentStageCode)} → {label(target)}
            </h3>
            <div className="form-grid">
              <label>
                阶段发生日期
                <input
                  type="date"
                  required
                  value={stageDate}
                  max={new Date().toLocaleDateString('en-CA')}
                  min={project.createdAt.slice(0, 10)}
                  onChange={(event) => setStageDate(event.target.value)}
                />
              </label>
              <label>
                指定审批人
                <Select
                  required
                  value={stageApproverId}
                  onChange={(event) => setStageApproverId(event.target.value)}
                >
                  <option value="">请选择接口人</option>
                  {stageApprovers.map((user) => (
                    <option key={user.id} value={user.id}>
                      {user.displayName}
                    </option>
                  ))}
                </Select>
              </label>
              {requiredPurpose ? (
                <label className="span-two">
                  {target === 'BIDDING' ? '客户侧交流方案' : '中标通知书'}
                  <Select
                    required
                    value={evidenceId}
                    onChange={(event) => setEvidenceId(event.target.value)}
                  >
                    <option value="">请先在“项目资料”上传对应材料</option>
                    {evidence.map((item) => (
                      <option key={item.id} value={item.id}>
                        {item.originalName}
                      </option>
                    ))}
                  </Select>
                </label>
              ) : (
                <>
                  <label>
                    合同编号
                    <input
                      required
                      maxLength={100}
                      value={contractNo}
                      onChange={(event) => setContractNo(event.target.value)}
                    />
                  </label>
                  <label>
                    签约金额（万元）
                    <input
                      required
                      type="number"
                      min="0"
                      step="0.01"
                      value={signedAmount}
                      onChange={(event) => setSignedAmount(event.target.value)}
                    />
                  </label>
                </>
              )}
            </div>
            <button
              type="submit"
              className="primary-button"
              disabled={busy || !stageApprovers.length || !!requestId}
            >
              {busy ? '提交中…' : '提交阶段推进申请'}
            </button>
          </form>
        </Modal>
      )}
      <MajorChangeForm
        project={project}
        level={level}
        approvers={changeApprovers}
        reference={reference}
        busy={busy}
        disabled={!!requestId}
        onBusyChange={setBusy}
        onSubmitted={(id) => {
          setError('');
          setRequestId(id);
        }}
      />
    </div>
  );
}
