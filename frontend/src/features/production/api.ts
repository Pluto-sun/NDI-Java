import { jsonBody, requestAt, type Page } from '../../shared/api/http';
import { tabularImportApi } from '../../shared/imports/tabularImportApi';
export const categories = [
  { code: 'NETWORK', label: '5G工业专网', amount: 'networkAmount' },
  { code: 'GEWU', label: '格物工业互联网平台', amount: 'gewuAmount' },
  { code: 'BIG_DATA', label: '工业大数据平台', amount: 'bigDataAmount' },
  { code: 'WANGWU', label: '万悟工业智能体平台', amount: 'wangwuAmount' },
] as const;
export interface PlanValues {
  reviewerName: string;
  planMonth: string;
  provinceId: string;
  cityId: string;
  projectName: string;
  customerName: string;
  categories: string[];
  networkAmount: string | number | null;
  gewuAmount: string | number | null;
  bigDataAmount: string | number | null;
  wangwuAmount: string | number | null;
  projectStatus: string | null;
}
export interface Plan extends PlanValues {
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
