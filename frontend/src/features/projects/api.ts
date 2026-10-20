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
};
