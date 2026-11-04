/** 未填写的来源值只在展示层替换，不改变保存及判重使用的空字符串。 */
export function revenueText(value: string | number | null | undefined): string {
  const text = value === null || value === undefined ? '' : String(value).trim();
  return text || '--';
}

/** 保留十进制字符串精度，未填写与零金额分别显示。 */
export function money(value: string | number | null | undefined): string {
  const text = revenueText(value);
  if (text === '--') return text;
  const [integer, fraction = ''] = text.split('.');
  return integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + '.' + fraction.padEnd(2, '0');
}
