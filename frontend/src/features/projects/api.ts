import { jsonBody, request, type Page } from '../../shared/api/http';
import type {
  CreateProjectInput,
  ProjectDetail,
  ProjectMilestone,
  ProjectProgress,
  ProjectSummary,
  ProjectUser,
  ReferenceData,
  WorkbenchBreakdown,
  WorkbenchOverview,
  ApprovalSummary,
  ApprovalTask,
  DocumentPurpose,
  InitiatorLevel,
  ProjectDocument,
  UploadIntent,
} from './types';

export interface ProjectQuery {
  page: number;
  projectName?: string;
  stage?: string;
}

export const projectsApi = {
  localActors: () => request<ProjectUser[]>('/dev/actors'),
  me: () => request<ProjectUser>('/me'),
  referenceData: () => request<ReferenceData>('/reference-data'),
  provinceLeaders: (provinceId: string) =>
    request<ProjectUser[]>(`/province-leaders?provinceId=${encodeURIComponent(provinceId)}`),
  stageApprovers: (provinceId: string) =>
    request<ProjectUser[]>(`/stage-approvers?provinceId=${encodeURIComponent(provinceId)}`),
  majorChangeApprovers: (provinceId: string, initiatorLevel: InitiatorLevel) =>
    request<ProjectUser[]>(
      `/major-change-approvers?provinceId=${encodeURIComponent(provinceId)}&initiatorLevel=${initiatorLevel}`,
    ),
  list: ({ page, projectName, stage }: ProjectQuery) => {
    const query = new URLSearchParams({ page: String(page), size: '20' });
    if (projectName?.trim()) query.set('projectName', projectName.trim());
    if (stage) query.set('stage', stage);
    return request<Page<ProjectSummary>>(`?${query}`);
  },
  detail: (id: string) => request<ProjectDetail>(`/${id}`),
  progresses: (id: string) => request<Page<ProjectProgress>>(`/${id}/progress?page=0&size=20`),
  milestones: (id: string) => request<Page<ProjectMilestone>>(`/${id}/milestones?page=0&size=20`),
  create: (data: CreateProjectInput) =>
    request<ProjectSummary>('', { method: 'POST', ...jsonBody(data) }),
  appendProgress: (id: string, progressContent: string, nextAction: string) =>
    request<ProjectProgress>(`/${id}/progress`, {
      method: 'POST',
      ...jsonBody({ progressContent, nextAction }),
    }),
  overview: () => request<WorkbenchOverview>('/workbench/overview'),
  breakdown: () => request<WorkbenchBreakdown>('/workbench/breakdown'),
  documents: (id: string) => request<ProjectDocument[]>(`/${id}/documents`),
  beginUpload: (
    id: string,
    data: {
      purpose: DocumentPurpose;
      originalName: string;
      mediaType: string;
      byteSize: number;
      sha256Hex: string;
    },
  ) => request<UploadIntent>(`/${id}/documents/uploads`, { method: 'POST', ...jsonBody(data) }),
  completeUpload: (id: string, documentId: string) =>
    request<ProjectDocument>(`/${id}/documents/${documentId}/complete`, { method: 'POST' }),
  downloadUrl: (id: string, documentId: string) =>
    request<{ downloadUrl: string }>(`/${id}/documents/${documentId}/download-url`),
  submitStage: (
    id: string,
    data: {
      initiatorLevel: InitiatorLevel;
      targetStage: string;
      approverId: string;
      stageOccurredOn: string;
      evidenceDocumentId?: string;
      contractNo?: string;
      signedAmount?: string;
    },
  ) => request<ApprovalSummary>(`/${id}/stage-advancements`, { method: 'POST', ...jsonBody(data) }),
  submitMajorChange: (
    id: string,
    data: {
      changeType: string;
      initiatorLevel: InitiatorLevel;
      approverId: string;
      reason: string;
      projectName?: string;
      totalAmount?: string;
      targetStage?: string;
    },
  ) => request<ApprovalSummary>(`/${id}/major-changes`, { method: 'POST', ...jsonBody(data) }),
  approvalInbox: (page: number) =>
    request<Page<ApprovalTask>>(`/approvals/inbox?page=${page}&size=20`),
  approval: (id: string) => request<ApprovalSummary>(`/approvals/${id}`),
  decide: (id: string, approved: boolean, opinion: string) =>
    request<ApprovalSummary>(`/approvals/${id}/decision`, {
      method: 'POST',
      ...jsonBody({ approved, opinion }),
    }),
  withdraw: (id: string) =>
    request<ApprovalSummary>(`/approvals/${id}/withdraw`, { method: 'POST' }),
};
