import { jsonBody, requestAt, type Page } from '../../shared/api/http';
import { tabularImportApi } from '../../shared/imports/tabularImportApi';
export interface Recognition {
  amount: string | number;
  reportedProjectName: string;
  projectStatus: string;
  integratedProvinceId: string | null;
  integratedOrganization?: string | null;
}
export interface Entry {
  id: string;
  version: number;
  month: string;
  provinceId: string | null;
  specificOrganization: boolean;
  contractNo: string;
  clauseNo: string;
  clauseAmount: string | number;
  sourceAmount: string | number;
  source: Record<string, string>;
  recognition: Recognition | null;
  state: string;
  pendingRequestId: string | null;
  canRecognize: boolean;
  recognizedAt: string | null;
}
export interface Reference {
  integratedOrganizations: string[];
  fixedOrganizations: Record<string, string>;
  provinces: { id: string; name: string }[];
  cities: { id: string; provinceId: string; name: string; legacyMunicipalityCity?: boolean }[];
  fields: string[];
  required: string[];
  canCreate: boolean;
}
export interface Approval {
  id: string;
  entryId: string;
  initiatorId: string;
  approverId: string;
  baseVersion: number;
  version: number;
  status: string;
  proposed: Recognition;
  opinion: string | null;
  createdAt: string;
  decidedAt: string | null;
  initiatorName: string;
  approverName: string;
}
export interface History {
  id: string;
  actorName: string;
  occurredAt: string;
  beforeValue: Recognition | null;
  afterValue: Recognition;
}
export interface Notice {
  id: string;
  entryId: string;
  message: string;
  createdAt: string;
  readAt: string | null;
}
export interface Listing extends Page<Entry> {
  summary: {
    total: number;
    unrecognized: number;
    recognized: number;
    pending: number;
    recognizedAmount: string | number;
  };
}
const root = '/api/v1/revenue/entries';
const request = <T>(path: string, init?: RequestInit) => requestAt<T>(root, path, init);
export const revenueApi = {
  counts: (signal?: AbortSignal) =>
    request<{ unread: number; pending: number }>('/counts', { signal }),
  reference: () => request<Reference>('/reference-data'),
  list: (query: URLSearchParams) => request<Listing>(`?${query}`),
  detail: (id: string) => request<Entry>(`/${id}`),
  create: (source: Record<string, string>) =>
    request<Entry>('', { method: 'POST', ...jsonBody({ source }) }),
  recognize: (entry: Entry, recognition: Recognition) =>
    request<Entry>(`/${entry.id}/recognition`, {
      method: 'POST',
      ...jsonBody({ version: entry.version, recognition }),
    }),
  requests: (id: string) => request<Approval[]>(`/${id}/requests`),
  history: (id: string) => request<History[]>(`/${id}/history`),
  requestPage: (view: string, page: number) =>
    request<Page<Approval>>(`/requests?view=${view}&page=${page}&size=20`),
  pending: () => request<Approval[]>('/pending'),
  notices: () => request<Notice[]>('/notices'),
  read: (id: string) => request<void>(`/notices/${id}/read`, { method: 'POST' }),
  decide: (entry: Entry, approval: Approval, decision: string, opinion: string) =>
    request<Entry>(`/requests/${approval.id}/decision`, {
      method: 'POST',
      ...jsonBody({
        entryVersion: entry.version,
        requestVersion: approval.version,
        decision,
        opinion,
      }),
    }),
};
export const stateLabels: Record<string, string> = {
  UNRECOGNIZED: '未认定',
  PENDING: '待审核',
  RECOGNIZED: '已认定',
  CHANGE_PENDING: '修改待审核',
};
export const statusLabels: Record<string, string> = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤销',
};
export { money } from './format';
export const revenueImportApi = tabularImportApi(root);
