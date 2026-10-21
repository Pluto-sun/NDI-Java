import { jsonBody, requestAt, requestBlobAt, type Page } from '../../shared/api/http';

export interface BackupTask {
  id: string;
  backupCode: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  requestedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  archiveBytes: number | null;
  sha256: string | null;
  snapshotAt: string | null;
  tableCount: number | null;
  objectCount: number | null;
  errorCode: string | null;
}
const root = '/api/v1/admin/backups';
export const backupApi = {
  list: (page: number, signal?: AbortSignal) =>
    requestAt<{ enabled: boolean; records: Page<BackupTask> }>(root, `?page=${page}&size=20`, {
      signal,
      cache: 'no-store',
    }),
  create: (requestId: string) =>
    requestAt<BackupTask>(root, '', { method: 'POST', ...jsonBody({ requestId }) }),
  check: (id: string) => requestAt<null>(root, `/${id}/download-check`, { method: 'POST' }),
  blob: (id: string) => requestBlobAt(root, `/${id}/download`),
  url: (id: string) => `${root}/${id}/download`,
};
