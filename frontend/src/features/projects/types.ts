export interface CodeLabel {
  code: string;
  label: string;
}
export interface ProvinceOption {
  id: string;
  name: string;
}
export interface CityOption {
  id: string;
  provinceId: string;
  name: string;
}
export interface ProjectUser {
  id: string;
  displayName: string;
  provinceId: string | null;
  cityId: string | null;
  roleCodes: string[];
}
export interface ReferenceData {
  provinces: ProvinceOption[];
  cities: CityOption[];
  industries: CodeLabel[];
  categories: CodeLabel[];
  stages: CodeLabel[];
  applicationScenarios: CodeLabel[];
}
export interface CategoryAmount {
  categoryCode: string;
  expectedAmount: number;
}
export interface CreateProjectInput {
  provinceId: string;
  cityId: string;
  projectName: string;
  customerName: string;
  industryCode: string;
  customerNeed: string;
  constructionContentRaw: string;
  productPromotionContent: string;
  remark: string;
  totalAmount: string;
  provinceLeaderUserId: string;
  cityAccountManagerName: string;
  categoryAmounts: { categoryCode: string; expectedAmount: string }[];
  initialProgress: string;
  nextAction: string;
  draftId?: string;
  confirmNew?: boolean;
  duplicateReason?: string;
  signingSubjectCode: 'PROVINCE_SIGN' | 'DS_SIGN';
  productSupportExpertNames: string[];
  plannedBidMonth: string | null;
  applicationScenarioCodes: string[];
}
export interface ProjectDuplicateCheck {
  status: 'NONE' | 'STRONG_DUPLICATE' | 'DUPLICATE_CANDIDATE';
  candidates: { projectId: string; projectCode: string; projectName: string; similarity: number }[];
}
export interface ProjectDraftData {
  cityId: string;
  leaderId: string;
  projectName: string;
  customerName: string;
  industryCode: string;
  customerNeed: string;
  constructionContentRaw?: string;
  productPromotionContent?: string;
  remark?: string;
  totalAmount: string;
  cityAccountManagerName: string;
  initialProgress: string;
  nextAction: string;
  categories: Record<string, string>;
  confirmNew: boolean;
  duplicateReason: string;
  signingSubjectCode: 'PROVINCE_SIGN' | 'DS_SIGN';
  productSupportExpertNames?: string[];
  plannedBidMonth?: string;
  applicationScenarioCodes?: string[];
}
export interface ProjectDraft {
  id: string;
  title: string;
  data: ProjectDraftData;
  createdAt: string;
  updatedAt: string;
}
export interface ProjectSummary {
  id: string;
  projectCode: string;
  projectName: string;
  customerName: string;
  industryCode: string;
  totalAmount: number;
  currentStageCode: string;
  categoryAmounts: CategoryAmount[];
  createdAt: string;
}
export interface ProjectDetail extends ProjectSummary {
  provinceId: string;
  cityId: string;
  customerNeed: string;
  constructionContentRaw: string;
  productPromotionContent: string | null;
  remark: string | null;
  contractNo: string | null;
  signedAmount: number | null;
  provinceLeaderUserId: string;
  cityAccountManagerName: string;
  signingSubjectCode: 'PROVINCE_SIGN' | 'DS_SIGN';
  productSupportExpertNames: string[];
  plannedBidMonth: string | null;
  applicationScenarioCodes: string[];
  versionNo: number;
  updatedAt: string;
}
export interface ProjectProgress {
  id: string;
  progressContent: string;
  nextAction: string;
  occurredAt: string;
  correctsProgressId: string | null;
}
export interface ProjectMilestone {
  id: string;
  stageCode: string;
  sourceType: string;
  occurredAt: string;
  descriptionText: string;
}
export interface WorkbenchOverview {
  projectCount: number;
  totalExpectedAmount: number;
  stages: { stageCode: string; projectCount: number; expectedAmount: number }[];
  approvals: { pendingCount: number; overdueCount: number };
}
export interface WorkbenchBreakdown {
  cities: { cityId: string; cityName: string; projectCount: number; expectedAmount: number }[];
  industries: {
    industryCode: string;
    industryName: string;
    projectCount: number;
    expectedAmount: number;
  }[];
}
export type InitiatorLevel = 'CITY' | 'PROVINCE';
export type DocumentPurpose = 'GENERAL' | 'CUSTOMER_SOLUTION' | 'AWARD_NOTICE';
export interface ProjectDocument {
  id: string;
  purposeCode: DocumentPurpose;
  originalName: string;
  mediaType: string;
  byteSize: number;
  completedAt: string;
}
export interface UploadIntent {
  documentId: string;
  method: string;
  uploadUrl: string;
  requiredHeaders: Record<string, string>;
}
export interface ApprovalSummary {
  id: string;
  projectId: string;
  requestType: 'STAGE_ADVANCEMENT' | 'MAJOR_CHANGE';
  changeType: 'PROJECT_NAME' | 'TOTAL_AMOUNT' | 'STAGE_ROLLBACK' | 'TERMINATE' | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'CONFLICT';
  initiatorId: string;
  approverId: string;
  fromStageCode: string;
  targetStageCode: string | null;
  originalProjectName: string;
  originalTotalAmount: number;
  proposedProjectName: string | null;
  proposedTotalAmount: number | null;
  proposedContractNo: string | null;
  proposedSignedAmount: number | null;
  evidenceDocumentId: string | null;
  stageOccurredAt: string | null;
  changeReason: string | null;
  reviewOpinion: string | null;
  submittedAt: string;
  dueAt: string;
}
export interface ApprovalTask {
  requestId: string;
  projectId: string;
  projectName: string;
  requestType: string;
  fromStageCode: string;
  targetStageCode: string | null;
  submittedAt: string;
  dueAt: string;
  overdue: boolean;
}
export interface ImportBatch {
  batchId: string;
  status: string;
  totalRows: number;
  validRows: number;
  failedRows: number;
  skippedRows: number;
  importedRows: number;
  confirmedAt: string | null;
  rows: {
    rowNumber: number;
    status: string;
    errorsJson: string;
    projectId: string | null;
    sourceCellsJson: string | null;
    revisionNo: number;
  }[];
}
export interface ImportBatchListItem {
  batchId: string;
  fileName: string;
  status: string;
  totalRows: number;
  validRows: number;
  failedRows: number;
  importedRows: number;
  createdAt: string;
  confirmedAt: string | null;
}
export interface ImportWorkbookInspection {
  fileName: string;
  fields: string[];
  sheets: {
    name: string;
    headerCandidates: { rowNo: number; headers: string[] }[];
  }[];
}
export interface ExportTask {
  id: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  rowCount: number | null;
  requestedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  errorMessage: string | null;
}
export interface MonthlyWorkbench {
  month: string;
  includeTerminated: boolean;
  projectCount: number;
  totalExpectedAmount: number;
  stages: WorkbenchOverview['stages'];
  cities: WorkbenchBreakdown['cities'];
  industries: WorkbenchBreakdown['industries'];
}
export interface TrendPoint {
  month: string;
  projectCount: number;
  totalExpectedAmount: number;
}
export interface ApprovalNotification {
  id: string;
  requestId: string;
  kind: string;
  createdAt: string;
  readAt: string | null;
}
export interface ProjectChange {
  id: string;
  eventType: string;
  aggregateVersion: number;
  payload: string;
  actorId: string;
  occurredAt: string;
}
export interface MonthlyRevision {
  month: string;
  projectId: string;
  revisionNo: number;
  sourceKind: string;
  sourceId: string;
  reason: string;
  beforeStageCode: string | null;
  afterStageCode: string | null;
  beforeIndustryCode: string | null;
  afterIndustryCode: string | null;
  beforeTotalAmount: number | null;
  afterTotalAmount: number | null;
  revisedAt: string;
}
