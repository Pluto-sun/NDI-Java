import { jsonBody, request, requestBlob, type Page } from '../../shared/api/http';
import type {
  ProjectOperationAudit,
  CreateProjectInput,
  ProjectDetail,
  ProjectMilestone,
  ProjectProgress,
  ProjectSummary,
  ProjectUser,
  ReferenceData,
  WorkbenchBreakdown,
  WorkbenchOverview,
  ProjectRecentActivity,
  ApprovalSummary,
  ApprovalTask,
  DocumentPurpose,
  InitiatorLevel,
  ProjectDocument,
  UploadIntent,
  ImportBatch,
  ImportBatchListItem,
  ImportWorkbookInspection,
  ExportTask,
  MonthlyWorkbench,
  TrendPoint,
  StageTrendPoint,
  ApprovalNotification,
  ProjectChange,
  MonthlyRevision,
  ProjectMapView,
  ProjectDraft,
  ProjectDraftData,
  ProjectChangeField,
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
  signingSubjectCode?: string;
  provinceLeaderName?: string;
  cityAccountManagerName?: string;
  productSupportExpertName?: string;
  keyword?: string;
  opportunityDateStart?: string;
  opportunityDateEnd?: string;
}

/** 列表与导出使用相同的规范化条件，空枚举、空 UUID 不发送；布尔值 false 必须保留。 */
function projectFilterValues(filter: Omit<ProjectQuery, 'page'>): Record<string, string | boolean> {
  const values: Record<string, string | boolean> = {};
  for (const [key, value] of Object.entries(filter)) {
    if (typeof value === 'boolean') values[key] = value;
    else if (typeof value === 'string' && value.trim()) values[key] = value.trim();
  }
  return values;
}

export const projectsApi = {
  operationAudit: (id: string, page: number) =>
    request<Page<ProjectOperationAudit>>(`/${id}/operation-audit?page=${page}&size=20`),
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
  list: ({ page, ...filter }: ProjectQuery) => {
    const query = new URLSearchParams({ page: String(page), size: '20' });
    for (const [key, value] of Object.entries(projectFilterValues(filter))) {
      query.set(key, String(value));
    }
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
    data: {
      progressContent: string;
      nextAction: string;
      correctsProgressId?: string;
      progressDate?: string;
      ownerName?: string;
      dueDate?: string;
    },
  ) =>
    request<ProjectProgress>(`/${id}/progress`, {
      method: 'POST',
      ...jsonBody(data),
    }),
  updateProfile: (
    id: string,
    data: {
      expectedVersion: number;
      customerName: string;
      industryCode: string;
      customerNeed: string;
      constructionContentRaw: string;
      productPromotionContent: string;
      remark: string;
      provinceLeaderUserId: string;
      cityAccountManagerName: string;
      productSupportExpertNames: string[];
      plannedBidMonth: string;
      applicationScenarioCodes: string[];
    },
  ) => request<ProjectDetail>(`/${id}/profile`, { method: 'PUT', ...jsonBody(data) }),
  overview: () => request<WorkbenchOverview>('/workbench/overview'),
  recentActivities: () => request<ProjectRecentActivity[]>('/workbench/recent-activities'),
  map: (provinceId: string | null, includeTerminated: boolean, stage?: string, month?: string) => {
    const query = new URLSearchParams({ includeTerminated: String(includeTerminated) });
    if (provinceId) query.set('provinceId', provinceId);
    if (stage) query.set('stage', stage);
    if (month) query.set('month', month);
    return request<ProjectMapView>(`/workbench/map?${query}`);
  },
  mapBoundary: (adcode: string) => requestBlob(`/workbench/map/boundaries/${adcode}`),
  breakdown: () => request<WorkbenchBreakdown>('/workbench/breakdown'),
  monthly: (month: string, includeTerminated: boolean) =>
    request<MonthlyWorkbench>(
      `/workbench/history?month=${month}&includeTerminated=${includeTerminated}`,
    ),
  trend: (from: string, to: string, includeTerminated: boolean) =>
    request<TrendPoint[]>(
      `/workbench/trend?from=${from}&to=${to}&includeTerminated=${includeTerminated}`,
    ),
  stageTrend: (from: string, to: string, includeTerminated: boolean, stages: string[]) => {
    const query = new URLSearchParams({ from, to, includeTerminated: String(includeTerminated) });
    stages.forEach((stage) => query.append('stage', stage));
    return request<StageTrendPoint[]>(`/workbench/stage-trend?${query}`);
  },
  inspectImport: (file: File) => {
    const body = new FormData();
    body.append('file', file);
    return request<ImportWorkbookInspection>('/imports/inspect', { method: 'POST', body });
  },
  previewImport: (
    file: File,
    layout?: { sheetName: string; headerRowNo: number; mapping: Record<string, string> },
  ) => {
    const body = new FormData();
    body.append('file', file);
    if (layout) {
      body.append('sheetName', layout.sheetName);
      body.append('headerRowNo', String(layout.headerRowNo));
      body.append('mappingJson', JSON.stringify(layout.mapping));
    }
    return request<ImportBatch>('/imports/preview', { method: 'POST', body });
  },
  importTemplate: () => requestBlob('/imports/template'),
  failedImportRows: (id: string) => requestBlob(`/imports/${id}/failed-rows/export`),
  importBatch: (id: string) => request<ImportBatch>(`/imports/${id}`),
  reviseImportRow: (
    id: string,
    rowNumber: number,
    expectedRevision: number,
    changes: Record<string, string>,
  ) =>
    request<ImportBatch>(`/imports/${id}/rows/${rowNumber}`, {
      method: 'PUT',
      ...jsonBody({ expectedRevision, changes }),
    }),
  importPlans: (page: number) =>
    request<Page<ImportBatchListItem>>(`/imports?page=${page}&size=20`),
  confirmImport: (id: string, selectedRowNumbers: number[]) =>
    request<ImportBatch>(`/imports/${id}/confirm`, {
      method: 'POST',
      ...jsonBody({ selectedRowNumbers }),
    }),
  resumeImport: (id: string) => request<ImportBatch>(`/imports/${id}/resume`, { method: 'POST' }),
  cancelImport: (id: string) => request<ImportBatch>(`/imports/${id}`, { method: 'DELETE' }),
  exportFields: () => request<import('./types').ExportFieldOption[]>('/exports/fields'),
  exportProjects: (filter: Omit<ProjectQuery, 'page'>, fields?: string[]) =>
    request<ExportTask>('/exports', {
      method: 'POST',
      ...jsonBody({ ...projectFilterValues(filter), ...(fields ? { fields } : {}) }),
    }),
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
  deleteDocument: (id: string, documentId: string) =>
    request<void>(`/${id}/documents/${documentId}`, { method: 'DELETE' }),
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
      items?: { fieldCode: ProjectChangeField; newValue: string }[];
    },
  ) => request<ApprovalSummary>(`/${id}/major-changes`, { method: 'POST', ...jsonBody(data) }),
  approvalInbox: (page: number) =>
    request<Page<ApprovalTask>>(`/approvals/inbox?page=${page}&size=20`),
  submittedApprovals: (page: number) =>
    request<Page<ApprovalSummary>>(`/approvals/submitted?page=${page}&size=20`),
  notifications: (page: number, unreadOnly = false) =>
    request<Page<ApprovalNotification>>(
      `/approval-notifications?page=${page}&size=20&unreadOnly=${unreadOnly}`,
    ),
  notificationCounts: (signal?: AbortSignal) =>
    request<import('./types').NotificationCounts>('/notification-counts', {
      signal,
      cache: 'no-store',
    }),
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
