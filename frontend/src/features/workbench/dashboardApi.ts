import { request } from '../../shared/api/http';

export interface DashboardValue {
  count: number;
  amount: number;
}
export interface DashboardSummary {
  projectCount: number;
  totalAmount: number;
  provinceCount: number;
  network: DashboardValue;
  digital: DashboardValue;
  intelligent: DashboardValue;
  gewu: DashboardValue;
  bigData: DashboardValue;
}
export interface Dashboard {
  from: string;
  to: string;
  current: DashboardSummary;
  previous: DashboardSummary;
  stages: { stageCode: string; summary: DashboardSummary }[];
  provinces: { id: string; name: string; summary: DashboardSummary }[];
  trend: { month: string; summary: DashboardSummary }[];
}
export interface DashboardFilter {
  from: string;
  to: string;
  stage: string;
  includeTerminated: boolean;
  category?: string;
  product?: string;
}
export interface DashboardActivity {
  id: string;
  projectId: string;
  projectName: string;
  province: string;
  industry: string;
  products: string | null;
  leaderName: string;
  eventType: string;
  occurredAt: string;
  beforeStage: string | null;
  afterStage: string | null;
  beforeAmount: number | null;
  afterAmount: number | null;
}
function query(filter: DashboardFilter) {
  const params = new URLSearchParams();
  Object.entries(filter).forEach(([key, value]) => {
    if (value !== '' && value !== undefined) params.set(key, String(value));
  });
  return params;
}
export const dashboardApi = {
  view: (filter: DashboardFilter, signal?: AbortSignal) =>
    request<Dashboard>(`/workbench/dashboard?${query(filter)}`, { signal }),
  activities: (filter: DashboardFilter, signal?: AbortSignal) =>
    request<DashboardActivity[]>(`/workbench/dashboard/activities?${query(filter)}`, { signal }),
};
export const dashboardCategories = [
  { key: 'network', label: '网络化', product: '5G工业专网' },
  { key: 'digital', label: '数字化', product: '格物、大数据' },
  { key: 'intelligent', label: '智能化', product: '万悟工业智能体平台' },
] as const;
export const dashboardProducts = [
  { code: 'NETWORK', key: 'network', label: '5G工业专网' },
  { code: 'GEWU', key: 'gewu', label: '格物工业互联网平台' },
  { code: 'BIG_DATA', key: 'bigData', label: '工业大数据平台' },
  { code: 'WANGWU', key: 'intelligent', label: '万悟工业智能体平台' },
] as const;
export const percent = (value: number, total: number) =>
  total ? `${((value / total) * 100).toFixed(1)}%` : '0.0%';
