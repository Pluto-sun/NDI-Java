import { jsonBody, requestAt, requestBlobAt, type Page } from '../../shared/api/http';
import type { ImportWizardApi } from '../../shared/imports/context';
import type { ImportTask, ImportBatch } from '../../shared/imports/types';
export const categories = [
  { code: 'NETWORK', label: '网络化', amount: 'networkAmount', field: '工业专网排产' },
  { code: 'GEWU', label: '格物', amount: 'gewuAmount', field: '格物平台排产' },
  { code: 'BIG_DATA', label: '大数据', amount: 'bigDataAmount', field: '工业大数据排产' },
  { code: 'WANGWU', label: '万悟', amount: 'wangwuAmount', field: '万悟平台排产' },
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
export const productionImportApi: ImportWizardApi = {
  importTask: () => request<ImportTask | null>('/imports/task'),
  uploadImportTask: (file, expectedVersion, expectedTaskId) => {
    const body = new FormData();
    body.append('file', file);
    if (expectedVersion !== undefined) body.append('expectedVersion', String(expectedVersion));
    if (expectedTaskId) body.append('expectedTaskId', expectedTaskId);
    return request<ImportTask>('/imports/task', { method: 'POST', body });
  },
  saveImportTask: (expectedTaskId, expectedVersion, step, configuration, selectedRowNumbers) =>
    request<ImportTask>('/imports/task', {
      method: 'PUT',
      keepalive: true,
      ...jsonBody({ expectedTaskId, expectedVersion, step, configuration, selectedRowNumbers }),
    }),
  discardImportTask: (expectedVersion, expectedTaskId) =>
    request<void>(
      `/imports/task?expectedVersion=${expectedVersion}&expectedTaskId=${expectedTaskId}`,
      { method: 'DELETE' },
    ),
  importBatch: (id) => request<ImportBatch>(`/imports/${id}`),
  reviseImportRow: (id, row, expectedRevision, changes) =>
    request<ImportBatch>(`/imports/${id}/rows/${row}`, {
      method: 'PUT',
      ...jsonBody({ expectedRevision, changes }),
    }),
  confirmImport: (id, selectedRowNumbers) =>
    request<ImportBatch>(`/imports/${id}/confirm`, {
      method: 'POST',
      ...jsonBody({ selectedRowNumbers }),
    }),
  importTemplate: () => requestBlobAt(root, '/imports/template'),
};
