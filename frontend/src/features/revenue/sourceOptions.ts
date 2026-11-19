import type { Recognition, Reference } from './api';

export const revenueProjectStatuses = ['实施中', '已交付', '已上线'] as const;

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
    else if (values['省份名称'] !== value) next['地市/组织名称'] = '';
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
      '',
      ...reference.provinces.map((p) => p.name),
      '具体组织',
      ...Object.keys(reference.fixedOrganizations ?? {}),
    ];
  const fixed = reference.fixedOrganizations?.[values['省份名称']];
  if (field !== '地市/组织名称') return undefined;
  if (fixed) return [fixed];
  const name = values['省份名称']?.trim() ?? '';
  if (name === '具体组织' || name === '--') return undefined;
  const shortName = (value: string) =>
    value.replace(/(特别行政区|维吾尔自治区|壮族自治区|回族自治区|自治区|省|市)$/, '');
  const province =
    reference.provinces.find((p) => p.name === name) ??
    reference.provinces.find((p) => shortName(p.name) === name);
  return [
    '',
    ...new Set(
      reference.cities
        .filter((city) => city.provinceId === province?.id && !city.legacyMunicipalityCity)
        .map((city) => city.name),
    ),
  ];
}

export function integratedAreaName(recognition: Recognition | null, reference?: Reference | null) {
  return (
    recognition?.integratedOrganization ??
    reference?.provinces.find(({ id }) => id === recognition?.integratedProvinceId)?.name
  );
}
