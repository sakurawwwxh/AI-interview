const API_BASE = 'http://localhost:8080/api/history';

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
    const response = await fetch(`${API_BASE}/resumes`);
    if (!response.ok) {
      throw new Error('获取简历列表失败');
    }
    return response.json();
  },

  /**
   * 获取简历详情
   */
  async getResumeDetail(id: number): Promise<ResumeDetail> {
    const response = await fetch(`${API_BASE}/resumes/${id}`);
    if (!response.ok) {
      throw new Error('获取简历详情失败');
    }
    return response.json();
  },

  /**
   * 获取面试详情
   */
  async getInterviewDetail(sessionId: string): Promise<InterviewDetail> {
    const response = await fetch(`${API_BASE}/interviews/${sessionId}`);
    if (!response.ok) {
      throw new Error('获取面试详情失败');
    }
    return response.json();
  },

  /**
   * 导出简历分析报告PDF
   */
  async exportAnalysisPdf(resumeId: number): Promise<Blob> {
    const response = await fetch(`${API_BASE}/export/analysis/${resumeId}`);
    if (!response.ok) {
      throw new Error('导出PDF失败');
    }
    return response.blob();
  },

  /**
   * 导出面试报告PDF
   */
  async exportInterviewPdf(sessionId: string): Promise<Blob> {
    const response = await fetch(`${API_BASE}/export/interview/${sessionId}`);
    if (!response.ok) {
      throw new Error('导出PDF失败');
    }
    return response.blob();
  },
};
