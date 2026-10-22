export function amount(value: number | null | undefined): string {
  return value == null
    ? '—'
    : new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value);
}

/** 表单金额以分比较，避免浮点数相加产生误判。 */
export function amountToCents(value: string): bigint | null {
  if (!/^\d+(?:\.\d{1,2})?$/.test(value)) return null;
  const [whole, fraction = ''] = value.split('.');
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
}

export function dateTime(value: string | null | undefined, timeZone?: string): string {
  return value
    ? new Intl.DateTimeFormat('zh-CN', {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone,
      }).format(new Date(value))
    : '—';
}

/** 旧进展缺业务日期时，按系统经营时区展示其原记录日期。 */
export function businessDate(value: string): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(value));
  const part = (type: string) => parts.find((item) => item.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

/** 统一经营时区的上月，直接计算年月，避免月末溢出及浏览器时区差异。 */
export function previousBusinessMonth(now = new Date()): string {
  const [year, month] = businessDate(now.toISOString()).split('-').map(Number);
  return month === 1 ? `${year - 1}-12` : `${year}-${String(month - 1).padStart(2, '0')}`;
}
