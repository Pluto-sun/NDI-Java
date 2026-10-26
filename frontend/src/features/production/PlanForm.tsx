import { useState } from 'react';
import { Feedback } from '../../shared/ui/Feedback';
import { Select } from '../../shared/ui/Select';
import { categories, type PlanValues, type Reference } from './api';

const empty: PlanValues = {
  reviewerName: '',
  planMonth: new Date().toLocaleDateString('sv-SE').slice(0, 7),
  provinceId: '',
  cityId: '',
  projectName: '',
  customerName: '',
  categories: [],
  networkAmount: null,
  gewuAmount: null,
  bigDataAmount: null,
  wangwuAmount: null,
  projectStatus: '',
};
/** 手工新增与编辑共用金额和类别交互，清空保留为空而非转换为零。 */
export function PlanForm({
  reference,
  initial,
  onSave,
  onCancel,
}: {
  reference: Reference;
  initial?: PlanValues;
  onSave: (values: PlanValues) => Promise<void>;
  onCancel: () => void;
}) {
  const [values, setValues] = useState<PlanValues>(() =>
    initial
      ? { ...initial }
      : {
          ...empty,
          provinceId: reference.provinces.length === 1 ? reference.provinces[0].id : '',
          cityId: reference.cities.length === 1 ? reference.cities[0].id : '',
        },
  );
  const [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  function field<K extends keyof PlanValues>(key: K, value: PlanValues[K]) {
    setValues((old) => ({ ...old, [key]: value }));
  }
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    const amounts = categories.map((c) => values[c.amount]);
    if (amounts.every((a) => a === null || a === '')) {
      setError('四项排产金额至少填写一项，允许填写 0');
      return;
    }
    setBusy(true);
    try {
      await onSave(values);
    } catch (c) {
      setError(c instanceof Error ? c.message : '保存失败');
    } finally {
      setBusy(false);
    }
  }
  return (
    <form className="production-form" onSubmit={(e) => void submit(e)} aria-busy={busy}>
      {error && <Feedback tone="error">{error}</Feedback>}
      <p className="subtle">带 * 为必填。金额单位为万元；接口人为文本信息，不影响数据权限。</p>
      <fieldset disabled={busy}>
        <div className="form-grid">
          <label>
            接口人 *
            <input
              required
              maxLength={100}
              value={values.reviewerName}
              onChange={(e) => field('reviewerName', e.target.value)}
            />
          </label>
          <label>
            排产月份 *
            <input
              required
              type="month"
              min="1900-01"
              value={values.planMonth}
              onChange={(e) => field('planMonth', e.target.value)}
            />
          </label>
          <label>
            省份 *
            <Select
              required
              value={values.provinceId}
              onChange={(e) =>
                setValues((old) => ({ ...old, provinceId: e.target.value, cityId: '' }))
              }
            >
              <option value="">请选择省份</option>
              {reference.provinces.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </Select>
          </label>
          <label>
            地市 *
            <Select
              required
              value={values.cityId}
              onChange={(e) => field('cityId', e.target.value)}
            >
              <option value="">请选择地市</option>
              {reference.cities
                .filter((c) => c.provinceId === values.provinceId)
                .map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
            </Select>
          </label>
          <label>
            项目名称 *
            <input
              required
              maxLength={300}
              value={values.projectName}
              onChange={(e) => field('projectName', e.target.value)}
            />
          </label>
          <label>
            客户名称 *
            <input
              required
              maxLength={300}
              value={values.customerName}
              onChange={(e) => field('customerName', e.target.value)}
            />
          </label>
        </div>
        <div className="production-category-section">
          <span>三化类别 *（可多选）</span>
          <div className="production-category-options">
            {categories.map((c) => (
              <label className="check-label" key={c.code}>
                <input
                  type="checkbox"
                  checked={values.categories.includes(c.code)}
                  onChange={(e) => {
                    const checked = e.target.checked;
                    setValues((old) => ({
                      ...old,
                      categories: checked
                        ? [...old.categories, c.code]
                        : old.categories.filter((code) => code !== c.code),
                      [c.amount]: checked ? old[c.amount] : null,
                    }));
                  }}
                />
                {c.label}
              </label>
            ))}
          </div>
        </div>
        <div className="form-grid">
          {categories.map((c) => (
            <label key={c.code}>
              {c.field}（万元）
              <input
                type="number"
                min="0"
                step="0.01"
                placeholder="未排产"
                disabled={!values.categories.includes(c.code)}
                value={values[c.amount] ?? ''}
                onChange={(e) => field(c.amount, e.target.value === '' ? null : e.target.value)}
              />
            </label>
          ))}
          <label className="full-width">
            项目状态（选填）
            <input
              maxLength={200}
              value={values.projectStatus ?? ''}
              onChange={(e) => field('projectStatus', e.target.value)}
            />
          </label>
        </div>
      </fieldset>
      <div className="form-actions">
        <button className="secondary-button" type="button" disabled={busy} onClick={onCancel}>
          取消
        </button>
        <button className="primary-button" disabled={busy}>
          {busy ? '保存中…' : '保存排产'}
        </button>
      </div>
    </form>
  );
}
