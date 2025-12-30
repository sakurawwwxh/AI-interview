import axios from 'axios';

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

export interface KnowledgeBaseItem {
  id: number;
  name: string;
  originalFilename: string;
  fileSize: number;
  contentType: string;
  uploadedAt: string;
  lastAccessedAt: string;
  accessCount: number;
  questionCount: number;
}

export interface UploadKnowledgeBaseResponse {
  knowledgeBase: {
    id: number;
    name: string;
    fileSize: number;
    contentLength: number;
  };
  storage: {
    fileKey: string;
    fileUrl: string;
  };
  duplicate: boolean;
}

export interface QueryRequest {
  knowledgeBaseIds: number[];  // 支持多个知识库
  question: string;
}

export interface QueryResponse {
  answer: string;
  knowledgeBaseId: number;
  knowledgeBaseName: string;
}

export const knowledgeBaseApi = {
  /**
   * 上传知识库文件
   */
  async uploadKnowledgeBase(file: File, name?: string): Promise<UploadKnowledgeBaseResponse> {
    const formData = new FormData();
    formData.append('file', file);
    if (name) {
      formData.append('name', name);
    }
    
    const response = await api.post<UploadKnowledgeBaseResponse>('/api/knowledgebase/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    
    return response.data;
  },
  
  /**
   * 获取所有知识库列表
   */
  async getAllKnowledgeBases(): Promise<KnowledgeBaseItem[]> {
    const response = await api.get<KnowledgeBaseItem[]>('/api/knowledgebase/list');
    return response.data;
  },
  
  /**
   * 获取知识库详情
   */
  async getKnowledgeBase(id: number): Promise<KnowledgeBaseItem> {
    const response = await api.get<KnowledgeBaseItem>(`/api/knowledgebase/${id}`);
    return response.data;
  },
  
  /**
   * 删除知识库
   */
  async deleteKnowledgeBase(id: number): Promise<void> {
    await api.delete(`/api/knowledgebase/${id}`);
  },
  
  /**
   * 基于知识库回答问题
   */
  async queryKnowledgeBase(request: QueryRequest): Promise<QueryResponse> {
    const response = await api.post<QueryResponse>('/api/knowledgebase/query', request);
    return response.data;
  },
};

