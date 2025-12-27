import axios from 'axios';
import type { UploadResponse } from '../types/resume';

const API_BASE_URL = import.meta.env.PROD ? '' : 'http://localhost:8080';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 120000, // 2分钟超时，AI分析需要时间
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
      // 成功时直接返回data
      response.data = result.data;
    } else {
      // 业务错误抛出异常
      return Promise.reject(new Error(result.message || '请求失败'));
    }
    return response;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export const resumeApi = {
  /**
   * 上传简历并获取分析结果
   */
  async uploadAndAnalyze(file: File): Promise<UploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await api.post<UploadResponse>('/api/resume/upload', formData, {
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
