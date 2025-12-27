import axios from 'axios';
import type { 
  InterviewSession, 
  CreateInterviewRequest, 
  SubmitAnswerRequest,
  SubmitAnswerResponse,
  CurrentQuestionResponse,
  InterviewReport 
} from '../types/interview';

const API_BASE_URL = import.meta.env.PROD ? '' : 'http://localhost:8080';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 180000, // 3分钟超时
});

// 统一响应结果类型
interface Result<T> {
  code: number;
  message: string;
  data: T;
}

// 响应拦截器：提取data字段
api.interceptors.response.use(
  (response) => {
    const result = response.data as Result<unknown>;
    if (result.code === 200) {
      response.data = result.data;
    } else {
      return Promise.reject(new Error(result.message || '请求失败'));
    }
    return response;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export const interviewApi = {
  /**
   * 创建面试会话
   */
  async createSession(request: CreateInterviewRequest): Promise<InterviewSession> {
    const response = await api.post<InterviewSession>('/api/interview/session', request);
    return response.data;
  },
  
  /**
   * 获取会话信息
   */
  async getSession(sessionId: string): Promise<InterviewSession> {
    const response = await api.get<InterviewSession>(`/api/interview/session/${sessionId}`);
    return response.data;
  },
  
  /**
   * 获取当前问题
   */
  async getCurrentQuestion(sessionId: string): Promise<CurrentQuestionResponse> {
    const response = await api.get<CurrentQuestionResponse>(`/api/interview/session/${sessionId}/question`);
    return response.data;
  },
  
  /**
   * 提交答案
   */
  async submitAnswer(request: SubmitAnswerRequest): Promise<SubmitAnswerResponse> {
    const response = await api.post<SubmitAnswerResponse>('/api/interview/answer', request);
    return response.data;
  },
  
  /**
   * 获取面试报告
   */
  async getReport(sessionId: string): Promise<InterviewReport> {
    const response = await api.get<InterviewReport>(`/api/interview/session/${sessionId}/report`);
    return response.data;
  },
};
