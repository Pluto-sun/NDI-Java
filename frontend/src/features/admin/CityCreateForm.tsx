import { Select } from '../../shared/ui/Select';
import { useState } from 'react';
import { adminApi, type AdminConfiguration } from './api';
import { Feedback } from '../../shared/ui/Feedback';
import { Modal } from '../../shared/ui/Modal';

export function CityCreateForm({
  configuration,
  onCreated,
}: {
  configuration: AdminConfiguration;
  onCreated: () => Promise<void>;
}) {
  const [provinceId, setProvinceId] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [open, setOpen] = useState(false);
  return (
    <div className="organization-create-action">
      <button type="button" className="secondary-button" onClick={() => setOpen(true)}>
        新增地市
      </button>
      {open && (
        <Modal title="新增地市" busy={busy} onClose={() => setOpen(false)}>
          <form
            aria-busy={busy}
            onSubmit={async (event) => {
              event.preventDefault();
              setError('');
              setMessage('');
              setBusy(true);
              try {
                await adminApi.createCity(provinceId, name);
                await onCreated();
                setName('');
                setMessage('地市已添加，账号归属选项已更新。');
              } catch (cause) {
                setError(cause instanceof Error ? cause.message : '地市创建失败');
              } finally {
                setBusy(false);
              }
            }}
          >
            <p>在已有省分下添加地市，不修改既有地市和项目归属。同省名称不能重复。</p>
            {error && <Feedback tone="error">{error}</Feedback>}
            {message && <Feedback tone="success">{message}</Feedback>}
            <div className="form-grid">
              <label>
                地市归属省分
                <Select
                  required
                  value={provinceId}
                  onChange={(event) => setProvinceId(event.target.value)}
                >
                  <option value="">请选择省分</option>
                  {configuration.provinces.map((province) => (
                    <option key={province.id} value={province.id}>
                      {province.name}
                    </option>
                  ))}
                </Select>
              </label>
              <label>
                新增地市名称
                <input
                  required
                  maxLength={50}
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                />
              </label>
            </div>
            <div className="form-actions">
              <button className="primary-button" disabled={busy}>
                {busy ? '正在添加…' : '添加地市'}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}
