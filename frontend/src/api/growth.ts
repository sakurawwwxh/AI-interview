import { request } from './request';

export interface GrowthPlan {
  headline: string;
  targetRole: string | null;
  evaluatedInterviewCount: number;
  pendingPracticeCount: number;
  actions: Array<{ title: string; description: string; category: string | null; score: number | null; link: string; practiceTaskId: number | null; priority: 'HIGH' | 'MEDIUM' }>;
}

export const growthApi = { getPlan: () => request.get<GrowthPlan>('/api/growth/plan') };
