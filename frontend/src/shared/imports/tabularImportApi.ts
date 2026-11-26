import { importTaskBody } from './limits';
import { jsonBody, requestAt, requestBlobAt } from '../api/http';
import type { ImportWizardApi } from './context';
import type { ImportTask, ImportBatch } from './types';

export function tabularImportApi(root: string): ImportWizardApi {
  const request = <T>(path: string, init?: RequestInit) => requestAt<T>(root, path, init);
  return {
    importTask: () => request<ImportTask | null>('/imports/task'),
    uploadImportTask: (file, expectedVersion, expectedTaskId, signal) => {
      const body = new FormData();
      body.append('file', file);
      if (expectedVersion !== undefined) body.append('expectedVersion', String(expectedVersion));
      if (expectedTaskId) body.append('expectedTaskId', expectedTaskId);
      return request<ImportTask>('/imports/task', { method: 'POST', body, signal });
    },
    saveImportTask: (
      expectedTaskId,
      expectedVersion,
      step,
      configuration,
      selectedRowNumbers,
      signal,
    ) =>
      request<ImportTask>('/imports/task', {
        method: 'PUT',
        ...importTaskBody(
          { expectedTaskId, expectedVersion, step, configuration, selectedRowNumbers },
          signal,
        ),
      }),
    discardImportTask: (expectedVersion, expectedTaskId) =>
      request<void>(
        `/imports/task?expectedVersion=${expectedVersion}&expectedTaskId=${expectedTaskId}`,
        { method: 'DELETE' },
      ),
    importBatch: (id) => request<ImportBatch>(`/imports/${id}`),
    reviseImportRow: (id, row, expectedRevision, changes, signal) =>
      request<ImportBatch>(`/imports/${id}/rows/${row}`, {
        method: 'PUT',
        signal,
        ...jsonBody({ expectedRevision, changes }),
      }),
    confirmImport: (id, selectedRowNumbers, signal) =>
      request<ImportBatch>(`/imports/${id}/confirm`, {
        method: 'POST',
        signal,
        ...jsonBody({ selectedRowNumbers }),
      }),
    importTemplate: () => requestBlobAt(root, '/imports/template'),
    originalFailedImportRows: (id) => requestBlobAt(root, `/imports/${id}/failed-rows/original`),
  };
}
