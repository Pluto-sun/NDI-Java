import { requestAt } from '../../shared/api/http';

export interface Money {
  network: string;
  digital: string;
  intelligent: string;
  total: string;
}
export interface Month {
  month: number;
  occurred: boolean;
  money: Money;
}
export interface Gewu {
  signedCount: number;
  recognizedCount: number;
  countGap: number;
  signedAmount: string;
  recognizedAmount: string;
  amountGap: string;
  countGapRate: string | null;
  amountGapRate: string | null;
}
export interface Region {
  key: string;
  name: string;
  adcode: string | null;
  geographic: boolean;
  organization: boolean;
  hasRevenue: boolean;
  months: Month[];
  total: Money;
  gewu: Gewu | null;
}
export interface AnnualOverview {
  year: number;
  currentMonth: number;
  nationalRevenue: boolean;
  projectScope: 'NATIONAL' | 'PROVINCE' | 'CITY';
  coveredProvinces: number;
  total: Money;
  months: Month[];
  regions: Region[];
  gewu: Gewu;
  conflicts: { contract: string; regions: string[] }[];
}

export function loadAnnualOverview(signal: AbortSignal) {
  return requestAt<AnnualOverview>('/api/v1/revenue/entries', '/annual-overview', { signal });
}
