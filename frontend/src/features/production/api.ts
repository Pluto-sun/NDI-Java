import { jsonBody, requestAt, type Page } from '../../shared/api/http';
import { tabularImportApi } from '../../shared/imports/tabularImportApi';
export const categories = [
  { code: 'NETWORK', label: '5G工业专网', amount: 'networkAmount', fields: ['networkOrderAmount'] },
  {
    code: 'GEWU',
    label: '格物工业互联网平台',
    amount: 'gewuAmount',
    fields: ['gewuOrderAmount', 'gewuRevenueAmount'],
  },
  {
    code: 'BIG_DATA',
    label: '工业大数据平台',
    amount: 'bigDataAmount',
    fields: ['bigDataOrderAmount', 'bigDataRevenueAmount'],
  },
  {
    code: 'WANGWU',
    label: '万悟工业智能体平台',
    amount: 'wangwuAmount',
    fields: ['wangwuOrderAmount', 'wangwuRevenueAmount'],
  },
] as const;
export type AmountField = (typeof categories)[number]['fields'][number];
type ProductTotal = (typeof categories)[number]['amount'];

export interface PlanValues extends Record<AmountField, string | null> {
  reviewerName: string;
  planMonth: string;
  provinceId: string;
  cityId: string;
  projectName: string;
  customerName: string;
  projectStatus: string | null;
  remark: string | null;
  productIntroduction: string | null;
}
export interface Plan extends PlanValues, Record<ProductTotal, string | null> {
  categories: string[];
  id: string;
  version: number;
  provinceName: string;
  cityName: string;
  createdAt: string;
  updatedAt: string;
  writable: boolean;
}
export interface Reference {
  provinces: { id: string; name: string }[];
  cities: { id: string; provinceId: string; name: string }[];
  writable: boolean;
}
const root = '/api/v1/production/plans';
const request = <T>(path: string, init?: RequestInit) => requestAt<T>(root, path, init);
export const productionApi = {
  list: (query: URLSearchParams) => request<Page<Plan>>(`?${query}`),
  detail: (id: string, signal?: AbortSignal) => request<Plan>(`/${id}`, { signal }),
  reference: () => request<Reference>('/reference-data'),
  create: (plan: PlanValues) => request<Plan>('', { method: 'POST', ...jsonBody(plan) }),
  update: (plan: Plan, values: PlanValues) =>
    request<Plan>(`/${plan.id}`, {
      method: 'PUT',
      ...jsonBody({ version: plan.version, plan: values }),
    }),
  delete: (plan: Plan) =>
    request<void>(`/${plan.id}?version=${plan.version}`, { method: 'DELETE' }),
};
export const productionImportApi = tabularImportApi(root);
