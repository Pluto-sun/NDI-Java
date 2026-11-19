import { useState } from 'react';
import { Feedback } from '../../shared/ui/Feedback';
import { Select } from '../../shared/ui/Select';
import { categories, type PlanValues, type Reference } from './api';
import {
  expandScientificAmount,
  formatAmountUnits,
  parseAmountUnits,
  plainAmount,
} from './amounts';

const empty: PlanValues = {
  reviewerName: '',
  planMonth: new Date().toLocaleDateString('sv-SE').slice(0, 7),
  provinceId: '',
  cityId: '',
  projectName: '',
  customerName: '',
  networkOrderAmount: null,
  gewuOrderAmount: null,
  gewuRevenueAmount: null,
  bigDataOrderAmount: null,
  bigDataRevenueAmount: null,
  wangwuOrderAmount: null,
  wangwuRevenueAmount: null,
  projectStatus: '',
  remark: '',
  productIntroduction: '',
};
/** 手工新增与编辑共用金额表单，清空保留为空。 */
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
      ? {
          ...initial,
          ...Object.fromEntries(
            categories.flatMap((product) =>
              product.fields.map((key) => [
                key,
                initial[key] === null ? null : plainAmount(initial[key]!),
              ]),
            ),
          ),
        }
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
    const amounts = categories
      .flatMap((product) => product.fields.map((key) => values[key]))
      .filter((value) => value !== null)
      .map((value) => parseAmountUnits(expandScientificAmount(value)));
    if (amounts.some((value) => value === null)) {
      setError('排产金额须为非负数，最多四位小数，整数部分最多十六位');
      return;
    }
    if (!amounts.some((value) => value !== null && value > 0n)) {
      setError('至少填写一项大于 0 的排产金额');
      return;
    }
    setBusy(true);
    try {
      await onSave({
        ...values,
        ...Object.fromEntries(
          categories.flatMap((product) =>
            product.fields.map((key) => [
              key,
              values[key] === null ? null : expandScientificAmount(values[key]!),
            ]),
          ),
        ),
      });
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
            客户名称
            <input
              maxLength={300}
              value={values.customerName}
              onChange={(e) => field('customerName', e.target.value)}
            />
          </label>
        </div>
        <div className="form-grid production-product-amounts">
          {categories.map((product) => {
            const amounts = product.fields
              .map((key) => values[key])
              .filter((value) => value !== null);
            const units = amounts.map((value) => parseAmountUnits(expandScientificAmount(value)));
            const total =
              units.length && units.every((value) => value !== null)
                ? formatAmountUnits(units.reduce<bigint>((sum, value) => sum + (value ?? 0n), 0n))
                : '—';
            return (
              <fieldset key={product.code} className="production-product-amount">
                <legend>{product.label}</legend>
                {product.fields.map((key) => (
                  <label key={key}>
                    {key.endsWith('OrderAmount')
                      ? '政企运营平台预计下单金额'
                      : '数智业务管理系统预计计收金额'}
                    <input
                      inputMode="decimal"
                      maxLength={21}
                      placeholder="未填写"
                      value={values[key] ?? ''}
                      onChange={(e) => field(key, e.target.value.trim() || null)}
                      onBlur={(e) => field(key, expandScientificAmount(e.target.value) || null)}
                    />
                  </label>
                ))}
                <div className="production-product-total">
                  <span>总排产金额</span>
                  <output aria-label={`${product.label}总排产金额`}>{total}</output>
                </div>
              </fieldset>
            );
          })}
          <label className="full-width">
            项目状态（选填）
            <input
              maxLength={200}
              value={values.projectStatus ?? ''}
              onChange={(e) => field('projectStatus', e.target.value)}
            />
          </label>
          <label className="full-width">
            产品简介（选填）
            <textarea
              maxLength={2000}
              rows={3}
              value={values.productIntroduction ?? ''}
              onChange={(e) => field('productIntroduction', e.target.value)}
            />
          </label>
          <label className="full-width">
            备注（选填）
            <textarea
              aria-label="备注（选填）"
              maxLength={2000}
              rows={3}
              value={values.remark ?? ''}
              onChange={(e) => field('remark', e.target.value)}
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
