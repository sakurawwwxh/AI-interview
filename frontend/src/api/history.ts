const API_BASE = import.meta.env.PROD ? '' : 'http://localhost:8080';

// 统一响应结果类型
interface Result<T> {
  code: number;
  message: string;
  data: T;
}

// 封装fetch请求，解析Result结构
async function fetchWithResult<T>(url: string): Promise<T> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error('网络请求失败');
  }
  const result: Result<T> = await response.json();
  if (result.code === 200) {
    return result.data;
  }
  throw new Error(result.message || '请求失败');
}

export interface ResumeListItem {
  id: number;
  filename: string;
  fileSize: number;
  uploadedAt: string;
  accessCount: number;
  latestScore?: number;
  lastAnalyzedAt?: string;
  interviewCount: number;
}

export interface AnalysisItem {
  id: number;
  overallScore: number;
  contentScore: number;
  structureScore: number;
  skillMatchScore: number;
  expressionScore: number;
  projectScore: number;
  summary: string;
  analyzedAt: string;
  strengths: string[];
  suggestions: any[];
}

export interface InterviewItem {
  id: number;
  sessionId: string;
  totalQuestions: number;
  status: string;
  overallScore: number | null;
  overallFeedback: string | null;
  createdAt: string;
  completedAt: string | null;
  questions?: any[];
  strengths?: string[];
  improvements?: string[];
  referenceAnswers?: any[];
}

export interface AnswerItem {
  questionIndex: number;
  question: string;
  category: string;
  userAnswer: string;
  score: number;
  feedback: string;
  referenceAnswer?: string;
  keyPoints?: string[];
  answeredAt: string;
}

export interface ResumeDetail {
  id: number;
  filename: string;
  fileSize: number;
  contentType: string;
  storageUrl: string;
  uploadedAt: string;
  accessCount: number;
  resumeText: string;
  analyses: AnalysisItem[];
  interviews: InterviewItem[];
}

export interface InterviewDetail extends InterviewItem {
  answers: AnswerItem[];
}

export const historyApi = {
  /**
   * 获取所有简历列表
   */
  async getResumes(): Promise<ResumeListItem[]> {
    return fetchWithResult<ResumeListItem[]>(`${API_BASE}/api/resume/list`);
  },

  /**
   * 获取简历详情
   */
  async getResumeDetail(id: number): Promise<ResumeDetail> {
    return fetchWithResult<ResumeDetail>(`${API_BASE}/api/resume/${id}/detail`);
  },

  /**
   * 获取面试详情
   */
  async getInterviewDetail(sessionId: string): Promise<InterviewDetail> {
    return fetchWithResult<InterviewDetail>(`${API_BASE}/api/interview/${sessionId}/detail`);
  },

  /**
   * 导出简历分析报告PDF
   */
  async exportAnalysisPdf(resumeId: number): Promise<Blob> {
    const response = await fetch(`${API_BASE}/api/resume/${resumeId}/export`);
    if (!response.ok) {
      throw new Error('导出PDF失败');
    }
    return response.blob();
  },

  /**
   * 导出面试报告PDF
   */
  async exportInterviewPdf(sessionId: string): Promise<Blob> {
    const response = await fetch(`${API_BASE}/api/interview/${sessionId}/export`);
    if (!response.ok) {
      throw new Error('导出PDF失败');
    }
    return response.blob();
  },

  /**
   * 删除简历
   */
  async deleteResume(id: number): Promise<void> {
    const response = await fetch(`${API_BASE}/api/resume/${id}`, {
      method: 'DELETE',
    });
    if (!response.ok) {
      const result: Result<void> = await response.json();
      throw new Error(result.message || '删除简历失败');
    }
    const result: Result<void> = await response.json();
    if (result.code !== 200) {
      throw new Error(result.message || '删除简历失败');
    }
  },

  /**
   * 删除面试记录
   */
  async deleteInterview(sessionId: string): Promise<void> {
    const response = await fetch(`${API_BASE}/api/interview/${sessionId}`, {
      method: 'DELETE',
    });
    if (!response.ok) {
      const result: Result<void> = await response.json();
      throw new Error(result.message || '删除面试记录失败');
    }
    const result: Result<void> = await response.json();
    if (result.code !== 200) {
      throw new Error(result.message || '删除面试记录失败');
    }
  },
};
