import { useState } from 'react';
import { adminApi } from './api';
import { Feedback } from '../../shared/ui/Feedback';
import { Modal } from '../../shared/ui/Modal';

/** 仅补建基础目录，不覆盖已有省分、地市或项目归属。 */
export function ProvinceCreateForm({ onCreated }: { onCreated: () => Promise<void> }) {
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [open, setOpen] = useState(false);
  return (
    <div className="organization-create-action">
      <button type="button" className="secondary-button" onClick={() => setOpen(true)}>
        新增省分
      </button>
      {open && (
        <Modal title="新增省分" busy={busy} onClose={() => setOpen(false)}>
          <form
            aria-label="新增省分"
            aria-busy={busy}
            onSubmit={async (event) => {
              event.preventDefault();
              setError('');
              setMessage('');
              setBusy(true);
              try {
                await adminApi.createProvince(name);
                setName('');
                await onCreated();
                setMessage('省分已添加，可继续新增地市和配置账号。');
              } catch (cause) {
                setError(cause instanceof Error ? cause.message : '省分创建失败');
              } finally {
                setBusy(false);
              }
            }}
          >
            <p>
              首次建档请先新增省分，再添加地市及责任账号。省分名称不能重复；组织编码由系统生成。
            </p>
            {error && <Feedback tone="error">{error}</Feedback>}
            {message && <Feedback tone="success">{message}</Feedback>}
            <label>
              新增省分名称
              <input
                required
                maxLength={50}
                value={name}
                disabled={busy}
                onChange={(event) => setName(event.target.value)}
              />
            </label>
            <div className="form-actions">
              <button className="primary-button" disabled={busy || !name.trim()}>
                {busy ? '正在添加…' : '添加省分'}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}
