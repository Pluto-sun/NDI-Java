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
