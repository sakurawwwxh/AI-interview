import axios from 'axios';
import type { ResumeAnalysisResponse } from '../types/resume';

const API_BASE_URL = import.meta.env.PROD ? '' : 'http://localhost:8080';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 120000, // 2分钟超时，AI分析需要时间
});

export const resumeApi = {
  /**
   * 上传简历并获取分析结果
   */
  async uploadAndAnalyze(file: File): Promise<ResumeAnalysisResponse> {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await api.post<ResumeAnalysisResponse>('/api/resume/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    
    return response.data;
  },
  
  /**
   * 健康检查
   */
  async healthCheck(): Promise<{ status: string; service: string }> {
    const response = await api.get('/api/resume/health');
    return response.data;
  },
};
