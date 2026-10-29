import type { ImportBatch, ImportConfiguration, ImportNameReview, ImportTask } from './types';

export function sourceCells(value: string | null): Record<string, string> {
  try {
    const parsed: unknown = JSON.parse(value ?? '{}');
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? Object.fromEntries(
          Object.entries(parsed).filter(
            ([key, value]) => !key.startsWith('__') && typeof value === 'string',
          ),
        )
      : {};
  } catch {
    return {};
  }
}
export function rowErrors(value: string): string[] {
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map(String) : ['行错误信息无法解析'];
  } catch {
    return ['行错误信息无法解析'];
  }
}
export function nameAccepted(review: ImportNameReview | null) {
  return (
    !review ||
    review.level === 'NONE' ||
    (review.confirmed && (review.level !== 'STRONG' || (review.reason?.trim().length ?? 0) >= 5))
  );
}
export function selectedFor(task: ImportTask) {
  return new Set(
    task.selectedRows ??
      task.batch?.rows.filter((row) => row.status === 'READY').map((row) => row.rowNumber) ??
      [],
  );
}
const normalized = (value: string) =>
  value
    .normalize('NFKC')
    .toLowerCase()
    .replace(/[\s\p{P}\p{S}]/gu, '')
    .replace(/万元|万/g, '');
const aliases: Record<string, string[]> = {
  排产月份: ['月份', '排产月'],
  接口人: ['接口人姓名', '排产接口人'],
  省分: ['省份', '所属省份', '归属省分', '省公司'],
  地市: ['城市', '所属地市', '地市名称', '归属地市'],
  客户名称: ['客户', '客户单位', '客户公司'],
  客户所属行业: ['所属行业', '行业', '行业名称'],
  项目名称: ['项目名', '商机名称', '商机'],
  客户需求: ['项目建设内容', '建设内容', '项目内容', '项目需求', '需求'],
  拟招标月份: ['拟招标日期', '预计招标月份'],
  项目负责人: ['负责人', '项目责任人', '责任人', '负责人员'],
  '项目总金额（万）': ['总金额', '项目金额', '预计总金额', '项目总金额（万元）'],
  项目阶段: ['阶段', '当前阶段', '生命周期'],
  三化类别: ['三化分类', '类别'],
  合同编号: ['合同号'],
  '签约金额（万）': ['签约金额', '合同金额'],
};
/** 旧职责列不能充当新的统一负责人字段。 */
export function allowedMappingSource(field: string, header: string) {
  return (
    field !== '项目负责人' ||
    !['省分负责人', '地市客户经理', '产品单位支撑专家'].some((name) =>
      normalized(header).includes(normalized(name)),
    )
  );
}
/** 先占用精确表头，再用别名和包含关系推断，避免同一源列被重复映射。 */
export function suggestMapping(fields: string[], headers: string[]) {
  const result: Record<string, string> = Object.fromEntries(fields.map((field) => [field, '']));
  const used = new Set<string>();
  for (const field of fields) {
    const exact = headers.find((header) => normalized(header) === normalized(field));
    if (exact && !used.has(exact)) {
      result[field] = exact;
      used.add(exact);
    }
  }
  for (const field of fields)
    if (!result[field]) {
      const variants = [field, ...(aliases[field] ?? [])].map(normalized);
      const header = headers.find(
        (header) =>
          allowedMappingSource(field, header) &&
          !used.has(header) &&
          variants.includes(normalized(header)),
      );
      if (header) {
        result[field] = header;
        used.add(header);
        continue;
      }
      const candidate = headers.find(
        (header) =>
          allowedMappingSource(field, header) &&
          !used.has(header) &&
          !fields.some((other) => normalized(other) === normalized(header)) &&
          variants.some(
            (v) =>
              v.length >= 3 &&
              normalized(header).length >= 3 &&
              (v.includes(normalized(header)) || normalized(header).includes(v)),
          ),
      );
      if (candidate) {
        result[field] = candidate;
        used.add(candidate);
      }
    }
  if (
    fields.includes('三化类别') &&
    !result['三化类别'] &&
    headers.some((header) => /^三化类别[一二三四]$/.test(header))
  )
    result['三化类别'] = '__CATEGORY_SLOTS__';
  return result;
}
export function chooseSheet(task: ImportTask, name: string, rowNo?: number): ImportConfiguration {
  const sheet = task.inspection.sheets.find((sheet) => sheet.name === name);
  const candidates = sheet?.headerCandidates ?? [];
  const best = rowNo
    ? candidates.find((row) => row.rowNo === rowNo)
    : [...candidates].sort((a, b) => {
        const count = (headers: string[]) =>
          Object.values(suggestMapping(task.inspection.fields, headers)).filter(Boolean).length;
        return count(b.headers) - count(a.headers) || a.rowNo - b.rowNo;
      })[0];
  return {
    sheetName: name,
    headerRowNo: best?.rowNo ?? null,
    fieldMapping: suggestMapping(task.inspection.fields, best?.headers ?? []),
  };
}
export function downloadWorkbook(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export type ImportRow = ImportBatch['rows'][number];

/** 固定必填列缺少映射时不允许离开第二步；条件字段由服务端再次核对整份文件。 */
export function missingMappings(task: ImportTask, configuration: ImportConfiguration): string[] {
  const result = task.fieldRules
    .filter((rule) => rule.requirement === 'REQUIRED' && !configuration.fieldMapping[rule.name])
    .map((rule) => rule.name);
  if (
    task.fieldRules.some((rule) => rule.name === '项目总金额（万）') &&
    !task.fieldRules
      .filter((rule) => rule.name.endsWith('预计金额（万）'))
      .some((rule) => configuration.fieldMapping[rule.name])
  )
    result.push('至少一项三化预计金额');
  if (
    task.fieldRules.some((rule) => rule.name === '排产月份') &&
    !task.fieldRules
      .filter((rule) => rule.name.includes('排产（万元）'))
      .some((rule) => configuration.fieldMapping[rule.name])
  )
    result.push('至少一项排产金额');
  return result;
}
