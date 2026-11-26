import { requestAt } from '../../shared/api/http';
import { formatCockpitMoney } from '../../shared/ui/format';

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
  return unit === '万' ? formatCockpitMoney(value) : formatCockpitMoney(value, 4, 2);
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
