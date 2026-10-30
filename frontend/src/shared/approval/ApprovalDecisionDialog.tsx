import type { ReactNode } from 'react';
import { Modal } from '../ui/Modal';
import { Feedback } from '../ui/Feedback';

/** 业务只传动作与提交方法；焦点约束、错误展示和重复提交保护统一处理。 */
export function ApprovalDecisionDialog({
  title,
  heading,
  description,
  busy,
  error,
  onClose,
  onConfirm,
  opinion,
  cancelLabel = '取消',
  confirmLabel = '确认',
}: {
  title: string;
  heading?: string;
  description: ReactNode;
  busy: boolean;
  error: string;
  onClose: () => void;
  onConfirm: () => void | Promise<void>;
  opinion?: { value: string; onChange: (value: string) => void; required: boolean };
  cancelLabel?: string;
  confirmLabel?: string;
}) {
  return (
    <Modal title={title} busy={busy} onClose={onClose}>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (!busy) void onConfirm();
        }}
      >
        {heading && <h2 tabIndex={-1}>{heading}</h2>}
        <p>{description}</p>
        {error && <Feedback tone="error">{error}</Feedback>}
        {opinion && (
          <ApprovalOpinionField
            {...opinion}
            label={opinion.required ? '审核意见（必填）' : '审核意见（选填）'}
          />
        )}
        <div className="dialog-actions">
          <button type="button" className="secondary-button" disabled={busy} onClick={onClose}>
            {cancelLabel}
          </button>
          <button className="primary-button" disabled={busy}>
            {busy ? '处理中…' : confirmLabel}
          </button>
        </div>
      </form>
    </Modal>
  );
}
export function ApprovalOpinionField({
  value,
  onChange,
  required = false,
  label,
}: {
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  label: string;
}) {
  return (
    <label>
      {label}
      <textarea
        rows={3}
        value={value}
        required={required}
        maxLength={2000}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}
