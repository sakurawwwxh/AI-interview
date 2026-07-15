import { request } from './request';

export interface JobTarget {
  id: number;
  title: string;
  company?: string;
  jobDescription: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface JobMatch {
  id?: number | null;
  resumeId?: number | null;
  jobTargetId?: number | null;
  jobTitle?: string | null;
  score: number;
  summary: string;
  matchedSkills: string[];
  missingSkills: string[];
  suggestions: string[];
  optimizationSuggestions: ResumeOptimizationSuggestion[];
  optimizedResumeText: string;
  analyzedAt: string;
}

export interface ResumeOptimizationSuggestion {
  section: string;
  issue: string;
  recommendation: string;
  proposedText: string;
}

export const jobTargetApi = {
  list: () => request.get<JobTarget[]>('/api/job-targets'),
  create: (data: Pick<JobTarget, 'title' | 'company' | 'jobDescription'>) =>
    request.post<JobTarget>('/api/job-targets', data),
  update: (id: number, data: Pick<JobTarget, 'title' | 'company' | 'jobDescription'>) =>
    request.put<JobTarget>(`/api/job-targets/${id}`, data),
  activate: (id: number) => request.patch<JobTarget>(`/api/job-targets/${id}/activate`),
  remove: (id: number) => request.delete<void>(`/api/job-targets/${id}`),
  /** 实时匹配并落库 */
  match: (id: number, resumeId: number) =>
    request.post<JobMatch>(`/api/job-targets/${id}/resumes/${resumeId}/match`),
  /** 读取已保存的最近一次匹配 */
  latestMatch: (id: number, resumeId: number) =>
    request.get<JobMatch | null>(`/api/job-targets/${id}/resumes/${resumeId}/match/latest`),
  /** 某简历下全部匹配历史 */
  listMatchesByResume: (resumeId: number) =>
    request.get<JobMatch[]>(`/api/job-targets/resumes/${resumeId}/matches`),
};
