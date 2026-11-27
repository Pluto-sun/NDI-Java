import { jsonBody, requestAt } from '../api/http';

export type ListId =
  'REVENUE' | 'PROJECTS' | 'PRODUCTION' | 'GEWU' | 'WANGWU' | 'AGENTS_LIVE' | 'AGENTS_DEVELOPMENT';

export type Field = { id: string; label: string };
type Fixed = { fixedFields: string[] };
type Catalog = {
  maxFixed: number;
  lists: (Fixed & { id: ListId; title: string; fields: Field[] })[];
};

export const listColumnsApi = {
  read: (list: ListId, signal: AbortSignal) =>
    requestAt<Fixed>('/api/v1/list-columns', '/' + list, { signal }),
  manage: () => requestAt<Catalog>('/api/v1/admin/list-columns', ''),
  save: (list: ListId, fixedFields: string[]) =>
    requestAt<Fixed>('/api/v1/admin/list-columns', '/' + list, {
      method: 'PUT',
      ...jsonBody({ fixedFields }),
    }),
};
