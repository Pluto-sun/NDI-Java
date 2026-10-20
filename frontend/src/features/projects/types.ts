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
  totalAmount: string;
  provinceLeaderUserId: string;
  cityAccountManagerName: string;
  categoryAmounts: { categoryCode: string; expectedAmount: string }[];
  initialProgress: string;
  nextAction: string;
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
  contractNo: string | null;
  signedAmount: number | null;
  provinceLeaderUserId: string;
  cityAccountManagerName: string;
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
