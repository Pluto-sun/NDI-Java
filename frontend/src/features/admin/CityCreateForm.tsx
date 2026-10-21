import { useState } from 'react';
import { adminApi, type AdminConfiguration } from './api';
import { Feedback } from '../../shared/ui/Feedback';

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
  return (
    <details className="panel form-panel admin-city">
      <summary>新增地市</summary>
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
            <select
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
            </select>
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
    </details>
  );
}
