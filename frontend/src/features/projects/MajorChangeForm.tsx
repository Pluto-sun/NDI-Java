import { Select } from '../../shared/ui/Select';
import { useEffect, useState, type FormEvent } from 'react';
import { Feedback } from '../../shared/ui/Feedback';
import { Modal } from '../../shared/ui/Modal';
import { projectsApi } from './api';
import { changeFieldLabels } from './majorChangeFields';
import type {
  InitiatorLevel,
  ProjectChangeField,
  ProjectDetail,
  ProjectUser,
  ReferenceData,
} from './types';

const stageOrder = ['SOLUTION_EXCHANGE', 'BIDDING', 'SIGNED', 'COLLECTION'];
const categoryCodes: Partial<Record<ProjectChangeField, string>> = {
  NETWORK_AMOUNT: 'NETWORK',
  GEWU_AMOUNT: 'GEWU',
  BIG_DATA_AMOUNT: 'BIG_DATA',
  WANGWU_AMOUNT: 'WANGWU',
};

export function MajorChangeForm({
  project,
  level,
  approvers,
  reference,
  busy,
  disabled,
  onBusyChange,
  onSubmitted,
}: {
  project: ProjectDetail;
  level: InitiatorLevel;
  approvers: ProjectUser[];
  reference: ReferenceData;
  busy: boolean;
  disabled: boolean;
  onBusyChange: (value: boolean) => void;
  onSubmitted: (id: string) => void;
}) {
  const [selected, setSelected] = useState<ProjectChangeField[]>(['PROJECT_NAME']);
  const [values, setValues] = useState<Partial<Record<ProjectChangeField, string>>>({});
  const [approverId, setApproverId] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [open, setOpen] = useState(false);
  useEffect(() => {
    setApproverId('');
  }, [level, project.id]);

  function current(field: ProjectChangeField) {
    if (field === 'PROJECT_NAME') return project.projectName;
    if (field === 'TOTAL_AMOUNT') return `${project.totalAmount} 万元`;
    if (field === 'STAGE')
      return (
        reference.stages.find((stage) => stage.code === project.currentStageCode)?.label ??
        project.currentStageCode
      );
    const value = project.categoryAmounts.find(
      (item) => item.categoryCode === categoryCodes[field],
    );
    return value ? `${value.expectedAmount} 万元` : '未选择';
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    onBusyChange(true);
    try {
      const result = await projectsApi.submitMajorChange(project.id, {
        changeType: 'FIELD_CHANGES',
        initiatorLevel: level,
        approverId,
        reason: reason.trim(),
        items: selected.map((fieldCode) => ({
          fieldCode,
          newValue: values[fieldCode]?.trim() ?? '',
        })),
      });
      onSubmitted(result.id);
      setOpen(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '重大变更申请失败');
    } finally {
      onBusyChange(false);
    }
  }

  return (
    <>
      <button
        type="button"
        className="secondary-button"
        disabled={disabled}
        onClick={() => setOpen(true)}
      >
        发起重大变更
      </button>
      {open && (
        <Modal title="重大变更申请" busy={busy} onClose={() => setOpen(false)}>
          <form className="request-form" aria-label="重大变更申请" onSubmit={submit}>
            <h3>重大变更</h3>
            <p className="subtle">
              支持一份申请修改多个字段。勾选的金额必须填写，分类金额合计不得超过变更后的项目总金额；未勾选字段保持当前值。
            </p>
            {error && <Feedback tone="error">{error}</Feedback>}
            <fieldset className="change-fieldset" disabled={busy || disabled}>
              <legend>选择变更字段</legend>
              <div className="change-field-options">
                {(Object.keys(changeFieldLabels) as ProjectChangeField[]).map((field) => (
                  <label className="checkbox-label" key={field}>
                    <input
                      type="checkbox"
                      checked={selected.includes(field)}
                      onChange={(event) =>
                        setSelected((current) =>
                          event.target.checked
                            ? [...current, field]
                            : current.filter((item) => item !== field),
                        )
                      }
                    />
                    变更{changeFieldLabels[field]}
                  </label>
                ))}
              </div>
              <div className="form-grid">
                {selected.map((field) => (
                  <label key={field}>
                    调整后{changeFieldLabels[field]}
                    {field !== 'PROJECT_NAME' && field !== 'STAGE' ? '（万元）' : ''}
                    {field === 'STAGE' ? (
                      <Select
                        required
                        value={values[field] ?? ''}
                        onChange={(event) => setValues({ ...values, [field]: event.target.value })}
                      >
                        <option value="">请选择前序阶段或已终止</option>
                        {reference.stages
                          .filter(
                            (stage) =>
                              stage.code === 'TERMINATED' ||
                              (stageOrder.indexOf(stage.code) >= 0 &&
                                stageOrder.indexOf(stage.code) <
                                  stageOrder.indexOf(project.currentStageCode)),
                          )
                          .map((stage) => (
                            <option key={stage.code} value={stage.code}>
                              {stage.label}
                            </option>
                          ))}
                      </Select>
                    ) : (
                      <input
                        required
                        type={field === 'PROJECT_NAME' ? 'text' : 'number'}
                        min={field === 'PROJECT_NAME' ? undefined : '0'}
                        step={field === 'PROJECT_NAME' ? undefined : '0.01'}
                        maxLength={field === 'PROJECT_NAME' ? 200 : undefined}
                        value={values[field] ?? ''}
                        onChange={(event) => setValues({ ...values, [field]: event.target.value })}
                      />
                    )}
                    <small className="subtle">当前：{current(field)}</small>
                  </label>
                ))}
                <label>
                  重大变更审批人
                  <Select
                    required
                    value={approverId}
                    onChange={(event) => setApproverId(event.target.value)}
                  >
                    <option value="">请选择审批人</option>
                    {approvers.map((user) => (
                      <option key={user.id} value={user.id}>
                        {user.displayName}
                      </option>
                    ))}
                  </Select>
                </label>
                <label className="span-two">
                  变更原因
                  <textarea
                    required
                    rows={3}
                    maxLength={2000}
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                  />
                </label>
              </div>
            </fieldset>
            {values.STAGE === 'TERMINATED' && selected.includes('STAGE') && (
              <Feedback>终止后项目只读，已有合同等字段保留。</Feedback>
            )}
            <button
              type="submit"
              className="secondary-button"
              disabled={busy || disabled || !approvers.length || !selected.length}
            >
              {busy ? '提交中…' : '提交重大变更申请'}
            </button>
          </form>
        </Modal>
      )}
    </>
  );
}
