import { request } from './request';
import type {
  CreateInterviewRequest,
  CurrentQuestionResponse,
  InterviewReport,
  InterviewSession,
  SubmitAnswerRequest,
  SubmitAnswerResponse
} from '../types/interview';

export const interviewApi = {
  /**
   * 创建面试会话
   */
  async createSession(req: CreateInterviewRequest): Promise<InterviewSession> {
    return request.post<InterviewSession>('/api/interview/sessions', req, {
      timeout: 180000, // 3分钟超时，AI生成问题需要时间
    });
  },

  /**
   * 获取会话信息
   */
  async getSession(sessionId: string): Promise<InterviewSession> {
    return request.get<InterviewSession>(`/api/interview/sessions/${sessionId}`);
  },

  /**
   * 获取当前问题
   */
  async getCurrentQuestion(sessionId: string): Promise<CurrentQuestionResponse> {
    return request.get<CurrentQuestionResponse>(`/api/interview/sessions/${sessionId}/question`);
  },

  /**
   * 提交答案
   */
  async submitAnswer(req: SubmitAnswerRequest): Promise<SubmitAnswerResponse> {
    return request.post<SubmitAnswerResponse>(
      `/api/interview/sessions/${req.sessionId}/answers`,
      {
        questionIndex: req.questionIndex,
        answer: req.answer,
        answerDurationSeconds: req.answerDurationSeconds,
      },
      {
        timeout: 180000, // 3分钟超时
      }
    );
  },

  /**
   * 获取面试报告
   */
  async getReport(sessionId: string): Promise<InterviewReport> {
    return request.get<InterviewReport>(`/api/interview/sessions/${sessionId}/report`, {
      timeout: 180000, // 3分钟超时，AI评估需要时间
    });
  },

  /**
   * 查找未完成的面试会话。
   * 后端无会话时返回 data=null（HTTP 仍成功），不再走业务异常。
   */
  async findUnfinishedSession(resumeId: number): Promise<InterviewSession | null> {
    try {
      const session = await request.get<InterviewSession | null>(
        `/api/interview/sessions/unfinished/${resumeId}`,
      );
      return session ?? null;
    } catch {
      // 网络/鉴权等真实错误时不阻断开面配置
      return null;
    }
  },

  /**
   * 暂存答案（不进入下一题）
   */
  async saveAnswer(req: SubmitAnswerRequest): Promise<void> {
    return request.put<void>(
      `/api/interview/sessions/${req.sessionId}/answers`,
      {
        questionIndex: req.questionIndex,
        answer: req.answer,
        answerDurationSeconds: req.answerDurationSeconds,
      }
    );
  },

  /**
   * 提前交卷
   */
  async completeInterview(sessionId: string): Promise<void> {
    return request.post<void>(`/api/interview/sessions/${sessionId}/complete`);
  },

  /**
   * 评估失败后重新入队评估
   */
  async retryEvaluation(sessionId: string): Promise<void> {
    return request.post<void>(`/api/interview/sessions/${sessionId}/retry-evaluation`);
  },
};
