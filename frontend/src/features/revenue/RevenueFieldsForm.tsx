import { Select } from '../../shared/ui/Select';
import type { Reference } from './api';

/** 来源字段由后端定义，创建表单与导入映射共用一份字段清单。 */
export function RevenueFieldsForm({
  reference,
  values,
  onChange,
}: {
  reference: Reference;
  values: Record<string, string>;
  onChange: (field: string, value: string) => void;
}) {
  const primary = new Set([
    ...reference.required,
    '费用账期',
    '地市/组织名称',
    '业务号码/项目编号',
    '项目名称',
    '客户名称',
  ]);
  const fieldInput = (field: string) => (
    <label key={field}>
      {field}
      {reference.required.includes(field) ? ' *' : '（选填）'}
      {field === '省份名称' ? (
        <Select
          required
          value={values[field] ?? ''}
          onChange={(e) => onChange(field, e.target.value)}
        >
          <option value="">请选择</option>
          {reference.provinces.map((p) => (
            <option key={p.id} value={p.name}>
              {p.name}
            </option>
          ))}
          <option value="具体组织">具体组织</option>
        </Select>
      ) : (
        <input
          value={values[field] ?? ''}
          required={
            reference.required.includes(field) ||
            (field === '地市/组织名称' && values['省份名称'] === '具体组织')
          }
          type={
            ['月账期', '费用账期'].includes(field)
              ? 'month'
              : field === '审签日期'
                ? 'date'
                : 'text'
          }
          inputMode={
            field === '条款金额' || field === '产品本月计收不含税额/标品含税收入'
              ? 'decimal'
              : undefined
          }
          maxLength={2000}
          onChange={(e) => onChange(field, e.target.value)}
        />
      )}
    </label>
  );
  return (
    <>
      <div className="form-grid">
        {reference.fields.filter((f) => primary.has(f)).map(fieldInput)}
      </div>
      <details className="revenue-optional">
        <summary>其他来源字段（选填）</summary>
        <div className="form-grid">
          {reference.fields.filter((f) => !primary.has(f)).map(fieldInput)}
        </div>
      </details>
    </>
  );
}
