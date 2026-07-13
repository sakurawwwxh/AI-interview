import { request } from './request';

export type PracticeTaskStatus = 'TODO' | 'COMPLETED' | 'IGNORED';

export interface PracticeTaskItem {
  id: number;
  status: PracticeTaskStatus;
  question: string;
  category: string;
  originalScore: number;
  lastScore: number | null;
  improvement: number | null;
  attemptCount: number;
  createdAt: string;
  lastPracticedAt: string | null;
  nextReviewAt: string | null;
  reviewIntervalDays: number | null;
}

export interface PracticeTaskPage {
  items: PracticeTaskItem[];
  total: number;
  page: number;
  size: number;
}

export interface PracticeAttempt {
  id: number;
  answer: string;
  score: number;
  feedback: string;
  createdAt: string;
}

export interface PracticeTaskDetail extends PracticeTaskItem {
  originalAnswer: string | null;
  originalFeedback: string | null;
  referenceAnswer: string | null;
  keyPoints: string[];
  attempts: PracticeAttempt[];
}

export interface PracticeSummary {
  todoCount: number;
  completedCount: number;
  ignoredCount: number;
  completedThisWeek: number;
  averageImprovement: number | null;
}

export const practiceApi = {
  getTasks(params: { status?: PracticeTaskStatus; category?: string; page?: number; size?: number } = {}) {
    return request.get<PracticeTaskPage>('/api/practice/tasks', { params });
  },
  getTask(id: number) {
    return request.get<PracticeTaskDetail>(`/api/practice/tasks/${id}`);
  },
  submitAttempt(id: number, answer: string) {
    return request.post<{ attempt: PracticeAttempt; taskStatus: PracticeTaskStatus; improvement: number | null }>(
      `/api/practice/tasks/${id}/attempts`, { answer });
  },
  updateStatus(id: number, status: PracticeTaskStatus) {
    return request.patch<PracticeTaskItem>(`/api/practice/tasks/${id}`, { status });
  },
  getSummary() {
    return request.get<PracticeSummary>('/api/practice/summary');
  },
};
