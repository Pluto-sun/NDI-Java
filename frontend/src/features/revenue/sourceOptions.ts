import type { Reference } from './api';

/** 固定组织选项来自后端字典，创建与导入行修正使用同一规则。 */
export function changeSourceField(
  values: Record<string, string>,
  field: string,
  value: string,
  reference: Reference,
) {
  const next = { ...values, [field]: value };
  if (field === '省份名称') {
    const fixed = reference.fixedOrganizations?.[value];
    if (fixed) next['地市/组织名称'] = fixed;
    else if (reference.fixedOrganizations?.[values['省份名称']]) next['地市/组织名称'] = '';
  }
  return next;
}

export function sourceFieldOptions(
  field: string,
  values: Record<string, string>,
  reference: Reference,
) {
  if (field === '省份名称')
    return [
      ...reference.provinces.map((p) => p.name),
      '具体组织',
      ...Object.keys(reference.fixedOrganizations ?? {}),
    ];
  const fixed = reference.fixedOrganizations?.[values['省份名称']];
  if (field === '地市/组织名称' && fixed) return [fixed];
  return undefined;
}
