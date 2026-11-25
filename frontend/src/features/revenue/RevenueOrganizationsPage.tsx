import { useEffect, useState } from 'react';
import { Feedback } from '../../shared/ui/Feedback';
import { Select } from '../../shared/ui/Select';
import { revenueApi, type OrganizationAssignment, type Reference } from './api';

export function RevenueOrganizationsPage() {
  const [items, setItems] = useState<OrganizationAssignment[] | null>(null);
  const [provinces, setProvinces] = useState<Reference['provinces']>([]);
  const [editing, setEditing] = useState<OrganizationAssignment | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    let active = true;
    Promise.all([revenueApi.organizations(), revenueApi.reference()])
      .then(([rows, reference]) => {
        if (!active) return;
        setItems(rows);
        setProvinces(reference.provinces);
      })
      .catch((cause: Error) => active && setError(cause.message));
    return () => {
      active = false;
    };
  }, []);

  async function save() {
    if (!editing) return;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const saved = await revenueApi.assignOrganization(editing.key, editing.provinceId);
      setItems(
        (previous) =>
          previous?.map((row) =>
            row.key === saved.key ? { ...row, provinceId: saved.provinceId } : row,
          ) ?? null,
      );
      setEditing(null);
      setNotice('组织归属已保存');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '组织归属保存失败');
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="panel revenue-organization-panel">
      <p>为计收台账中的具体组织设置归属省份，用于计收列表筛选和省分查看。</p>
      {error && <Feedback tone="error">{error}</Feedback>}
      {notice && <Feedback tone="success">{notice}</Feedback>}
      {!items && !error && <p role="status">正在加载组织…</p>}
      {items?.length === 0 && <p className="subtle">当前计收台账暂无具体组织。</p>}
      {!!items?.length && (
        <div className="table-scroll">
          <table aria-label="计收组织归属">
            <thead>
              <tr>
                <th>组织全称</th>
                <th>归属省份</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.key}>
                  <td>{item.name}</td>
                  <td>
                    {editing?.key === item.key ? (
                      <Select
                        aria-label={`${item.name}的归属省份`}
                        value={editing.provinceId ?? ''}
                        disabled={saving}
                        onChange={(event) =>
                          setEditing({ ...editing, provinceId: event.target.value || null })
                        }
                      >
                        <option value="">未分配</option>
                        {provinces.map((province) => (
                          <option key={province.id} value={province.id}>
                            {province.name}
                          </option>
                        ))}
                      </Select>
                    ) : (
                      (provinces.find(({ id }) => id === item.provinceId)?.name ??
                      (item.provinceId ? '省份已停用' : '未分配'))
                    )}
                  </td>
                  <td>
                    <div className="inline-actions">
                      {editing?.key === item.key ? (
                        <>
                          <button
                            type="button"
                            className="primary-button"
                            disabled={saving}
                            onClick={() => void save()}
                          >
                            保存
                          </button>
                          <button
                            type="button"
                            className="secondary-button"
                            disabled={saving}
                            onClick={() => setEditing(null)}
                          >
                            取消
                          </button>
                        </>
                      ) : (
                        <button
                          type="button"
                          className="secondary-button"
                          disabled={!!editing}
                          onClick={() => {
                            setNotice('');
                            setEditing(item);
                          }}
                        >
                          配置
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
