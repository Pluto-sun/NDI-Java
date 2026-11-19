import { jsonBody, requestAt, requestBlobAt } from '../api/http';
import type { ImportWizardApi } from './context';
import type { ImportTask, ImportBatch } from './types';

export function tabularImportApi(root: string): ImportWizardApi {
  const request = <T>(path: string, init?: RequestInit) => requestAt<T>(root, path, init);
  return {
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
    originalFailedImportRows: (id) => requestBlobAt(root, `/imports/${id}/failed-rows/original`),
  };
}
