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
  ImportBatch,
  ImportBatchListItem,
  ExportTask,
  MonthlyWorkbench,
  TrendPoint,
  ApprovalNotification,
  ProjectChange,
  MonthlyRevision,
  ProjectDraft,
  ProjectDraftData,
  ProjectDuplicateCheck,
} from './types';

export interface ProjectQuery {
  page: number;
  projectName?: string;
  stage?: string;
  projectCode?: string;
  industryCode?: string;
  cityId?: string;
  includeTerminated?: boolean;
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
  list: ({
    page,
    projectName,
    stage,
    projectCode,
    industryCode,
    cityId,
    includeTerminated,
  }: ProjectQuery) => {
    const query = new URLSearchParams({ page: String(page), size: '20' });
    if (projectName?.trim()) query.set('projectName', projectName.trim());
    if (stage) query.set('stage', stage);
    if (projectCode?.trim()) query.set('projectCode', projectCode.trim());
    if (industryCode) query.set('industryCode', industryCode);
    if (cityId) query.set('cityId', cityId);
    if (includeTerminated !== undefined) query.set('includeTerminated', String(includeTerminated));
    return request<Page<ProjectSummary>>(`?${query}`);
  },
  detail: (id: string) => request<ProjectDetail>(`/${id}`),
  progresses: (id: string) => request<Page<ProjectProgress>>(`/${id}/progress?page=0&size=20`),
  milestones: (id: string) => request<Page<ProjectMilestone>>(`/${id}/milestones?page=0&size=20`),
  projectApprovals: (id: string, page: number) =>
    request<Page<ApprovalSummary>>(`/${id}/approvals?page=${page}&size=20`),
  projectChanges: (id: string, page: number) =>
    request<Page<ProjectChange>>(`/${id}/changes?page=${page}&size=20`),
  monthlyRevisions: (id: string, month: string) =>
    request<MonthlyRevision[]>(`/workbench/history/${id}/revisions?month=${month}`),
  create: (data: CreateProjectInput) =>
    request<ProjectSummary>('', { method: 'POST', ...jsonBody(data) }),
  duplicateCheck: (cityId: string, projectName: string) =>
    request<ProjectDuplicateCheck>(
      `/duplicate-check?cityId=${encodeURIComponent(cityId)}&projectName=${encodeURIComponent(projectName)}`,
    ),
  drafts: (page: number) => request<Page<ProjectDraft>>(`/drafts?page=${page}&size=20`),
  saveDraft: (data: ProjectDraftData, id?: string) =>
    request<ProjectDraft>(id ? `/drafts/${id}` : '/drafts', {
      method: id ? 'PUT' : 'POST',
      ...jsonBody({ title: data.projectName.trim() || '未命名项目草稿', data }),
    }),
  deleteDraft: (id: string) => request<null>(`/drafts/${id}`, { method: 'DELETE' }),
  appendProgress: (
    id: string,
    progressContent: string,
    nextAction: string,
    correctsProgressId?: string,
  ) =>
    request<ProjectProgress>(`/${id}/progress`, {
      method: 'POST',
      ...jsonBody({ progressContent, nextAction, correctsProgressId }),
    }),
  updateProfile: (
    id: string,
    data: {
      expectedVersion: number;
      customerName: string;
      industryCode: string;
      customerNeed: string;
      provinceLeaderUserId: string;
      cityAccountManagerName: string;
    },
  ) => request<ProjectDetail>(`/${id}/profile`, { method: 'PUT', ...jsonBody(data) }),
  overview: () => request<WorkbenchOverview>('/workbench/overview'),
  breakdown: () => request<WorkbenchBreakdown>('/workbench/breakdown'),
  monthly: (month: string, includeTerminated: boolean) =>
    request<MonthlyWorkbench>(
      `/workbench/history?month=${month}&includeTerminated=${includeTerminated}`,
    ),
  trend: (from: string, to: string, includeTerminated: boolean) =>
    request<TrendPoint[]>(
      `/workbench/trend?from=${from}&to=${to}&includeTerminated=${includeTerminated}`,
    ),
  previewImport: (file: File) => {
    const body = new FormData();
    body.append('file', file);
    return request<ImportBatch>('/imports/preview', { method: 'POST', body });
  },
  importBatch: (id: string) => request<ImportBatch>(`/imports/${id}`),
  importPlans: (page: number) =>
    request<Page<ImportBatchListItem>>(`/imports?page=${page}&size=20`),
  confirmImport: (id: string, selectedRowNumbers: number[]) =>
    request<ImportBatch>(`/imports/${id}/confirm`, {
      method: 'POST',
      ...jsonBody({ selectedRowNumbers }),
    }),
  cancelImport: (id: string) => request<ImportBatch>(`/imports/${id}`, { method: 'DELETE' }),
  exportProjects: (filter: Omit<ProjectQuery, 'page'>) =>
    request<ExportTask>('/exports', { method: 'POST', ...jsonBody(filter) }),
  exportTask: (id: string) => request<ExportTask>(`/exports/${id}`),
  exportDownload: (id: string) =>
    request<{ url: string; fileName: string }>(`/exports/${id}/download`),
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
  submittedApprovals: (page: number) =>
    request<Page<ApprovalSummary>>(`/approvals/submitted?page=${page}&size=20`),
  notifications: (page: number) =>
    request<Page<ApprovalNotification>>(`/approval-notifications?page=${page}&size=20`),
  markNotificationRead: (id: string) =>
    request<ApprovalNotification>(`/approval-notifications/${id}/read`, { method: 'POST' }),
  approval: (id: string) => request<ApprovalSummary>(`/approvals/${id}`),
  decide: (id: string, approved: boolean, opinion: string) =>
    request<ApprovalSummary>(`/approvals/${id}/decision`, {
      method: 'POST',
      ...jsonBody({ approved, opinion }),
    }),
  withdraw: (id: string) =>
    request<ApprovalSummary>(`/approvals/${id}/withdraw`, { method: 'POST' }),
};
