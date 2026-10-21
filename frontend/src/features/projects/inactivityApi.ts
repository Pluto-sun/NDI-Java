import { jsonBody, request, type Page } from '../../shared/api/http';
import type { ProjectUser } from './types';

export interface InactivityCase {
  id: string;
  projectId: string;
  projectCode: string;
  projectName: string;
  status: 'OPEN' | 'FORWARDED' | 'RESOLVED';
  lastActivityAt: string;
  createdAt: string;
  closedAt: string | null;
  canForward: boolean;
  events: { kind: string; summary: string; occurredAt: string }[];
}

export const inactivityApi = {
  mine: (history: boolean, page: number) =>
    request<Page<InactivityCase>>(`/inactivity-notices?history=${history}&page=${page}&size=20`),
  recipients: (id: string) => request<ProjectUser[]>(`/inactivity-cases/${id}/recipients`),
  forward: (id: string, recipientId: string, note: string) =>
    request<void>(`/inactivity-cases/${id}/forward`, {
      method: 'POST',
      ...jsonBody({ recipientId, note }),
    }),
};
