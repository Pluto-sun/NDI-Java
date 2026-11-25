import { requestAt } from '../../shared/api/http';

export interface Amounts {
  planned: string;
  recognized: string;
  gap: string;
  rate: string | null;
}

export interface OverviewMonth {
  month: string;
  amounts: Amounts;
  plannedIncrease: string | null;
  recognizedIncrease: string | null;
}

export interface OverviewProvince {
  key: string;
  name: string;
  adcode: string | null;
  province: boolean;
  total: Amounts;
  months: Amounts[];
}

export interface Overview {
  plannedProjects: number;
  recognizedProjects: number;
  total: Amounts;
  productionScope: 'NATIONAL' | 'PROVINCE' | 'CITY';
  revenueScope: 'NATIONAL' | 'PROVINCE';
  empty: boolean;
  categories: { name: string; amounts: Amounts }[];
  months: OverviewMonth[];
  provinces: OverviewProvince[];
}

export function fetchOverview(from: string, to: string, signal: AbortSignal) {
  const query = new URLSearchParams({ from, to });
  return requestAt<Overview>('/api/v1/production/plans', '/scheduling-overview?' + query, {
    signal,
  });
}

export function formatMoney(value: string, unit: '万' | '亿' = '亿') {
  if (unit === '万') {
    const [integer, fraction] = value.split('.');
    return integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (fraction ? '.' + fraction : '');
  }
  // 后端返回定点小数。亿元仅用于缩略展示，完整万元数可在提示中查看。
  const amount = Number(value) / 10000;
  const maximumFractionDigits = amount !== 0 && Math.abs(amount) < 0.01 ? 4 : 2;
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits, minimumFractionDigits: 2 }).format(
    amount,
  );
}

export function formatRate(value: string | null) {
  return value === null
    ? '—'
    : Number(value).toLocaleString('zh-CN', {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
      }) + '%';
}

export function monthLabel(month: string, showYear: boolean) {
  return showYear ? month : Number(month.slice(5)) + '月';
}

export function amountDescription(amounts: Amounts) {
  return `排产 ${formatMoney(amounts.planned, '万')} 万元 · 计收 ${formatMoney(amounts.recognized, '万')} 万元 · 兑现率 ${formatRate(amounts.rate)}`;
}
