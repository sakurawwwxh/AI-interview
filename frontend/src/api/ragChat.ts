import { getAuthorizationHeader, request, getErrorMessage } from './request';

const API_BASE_URL = import.meta.env.PROD ? '' : 'http://localhost:8080';

// ========== 类型定义 ==========

export interface RagChatSession {
  id: number;
  title: string;
  knowledgeBaseIds: number[];
  createdAt: string;
}

export interface RagChatSessionListItem {
  id: number;
  title: string;
  messageCount: number;
  knowledgeBaseNames: string[];
  updatedAt: string;
  isPinned: boolean;
}

export interface RagChatMessage {
  id: number;
  type: 'user' | 'assistant';
  content: string;
  createdAt: string;
}

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

export interface RagChatSessionDetail {
  id: number;
  title: string;
  knowledgeBases: KnowledgeBaseItem[];
  messages: RagChatMessage[];
  createdAt: string;
  updatedAt: string;
}

// ========== API 函数 ==========

export const ragChatApi = {
  /**
   * 创建新会话
   */
  async createSession(knowledgeBaseIds: number[], title?: string): Promise<RagChatSession> {
    return request.post<RagChatSession>('/api/rag-chat/sessions', {
      knowledgeBaseIds,
      title,
    });
  },

  /**
   * 获取会话列表
   */
  async listSessions(): Promise<RagChatSessionListItem[]> {
    return request.get<RagChatSessionListItem[]>('/api/rag-chat/sessions');
  },

  /**
   * 获取会话详情
   */
  async getSessionDetail(sessionId: number): Promise<RagChatSessionDetail> {
    return request.get<RagChatSessionDetail>(`/api/rag-chat/sessions/${sessionId}`);
  },

  /**
   * 更新会话标题
   */
  async updateSessionTitle(sessionId: number, title: string): Promise<void> {
    return request.put(`/api/rag-chat/sessions/${sessionId}/title`, { title });
  },

  /**
   * 更新会话知识库
   */
  async updateKnowledgeBases(sessionId: number, knowledgeBaseIds: number[]): Promise<void> {
    return request.put(`/api/rag-chat/sessions/${sessionId}/knowledge-bases`, {
      knowledgeBaseIds,
    });
  },

  /**
   * 切换会话置顶状态
   */
  async togglePin(sessionId: number): Promise<void> {
    return request.put(`/api/rag-chat/sessions/${sessionId}/pin`);
  },

  /**
   * 删除会话
   */
  async deleteSession(sessionId: number): Promise<void> {
    return request.delete(`/api/rag-chat/sessions/${sessionId}`);
  },

  /**
   * 发送消息（Dify 聊天助手流式SSE，走 session 保存对话历史）
   * @param signal 可选的 AbortSignal，用于停止生成
   */
  async sendDifyMessageStream(
    sessionId: number,
    question: string,
    onMessage: (chunk: string) => void,
    onComplete: () => void,
    onError: (error: Error) => void,
    signal?: AbortSignal
  ): Promise<void> {
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/rag-chat/sessions/${sessionId}/messages/dify-stream`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...getAuthorizationHeader() },
          body: JSON.stringify({ question }),
          signal,
        }
      );

      if (!response.ok) {
        try {
          const errorData = await response.json();
          if (errorData && errorData.message) throw new Error(errorData.message);
        } catch { /* ignore */ }
        throw new Error(`请求失败 (${response.status})`);
      }

      const reader = response.body?.getReader();
      if (!reader) throw new Error('无法获取响应流');

      const decoder = new TextDecoder();
      let buffer = '';

      const extractEvent = (event: string): { type: string; data: string } | null => {
        if (!event.trim()) return null;
        const lines = event.split('\n');
        let eventType = 'message';
        const contentParts: string[] = [];
        for (const line of lines) {
          if (line.startsWith('event:')) eventType = line.substring(6).trim();
          else if (line.startsWith('data:')) contentParts.push(line.substring(5));
        }
        if (contentParts.length === 0 && eventType === 'message') return null;
        const data = contentParts.join('').replace(/\\n/g, '\n').replace(/\\r/g, '\r');
        return { type: eventType, data };
      };

      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          if (buffer.trim()) {
            const evt = extractEvent(buffer);
            if (evt) {
              if (evt.type === 'error') { onError(new Error(evt.data || '回答生成失败')); return; }
              if (evt.data) onMessage(evt.data);
            }
          }
          onComplete();
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        let idx: number;
        while ((idx = buffer.indexOf('\n\n')) !== -1) {
          const block = buffer.substring(0, idx);
          buffer = buffer.substring(idx + 2);
          const evt = extractEvent(block);
          if (!evt) continue;
          if (evt.type === 'error') { onError(new Error(evt.data || '回答生成失败')); return; }
          if (evt.type === 'ping') continue;
          if (evt.data) onMessage(evt.data);
        }
      }
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') { onComplete(); return; }
      onError(new Error(getErrorMessage(error)));
    }
  },

  /**
   * 发送消息（流式SSE）
   * @param signal 可选的 AbortSignal，用于停止生成
   */
  async sendMessageStream(
    sessionId: number,
    question: string,
    onMessage: (chunk: string) => void,
    onComplete: () => void,
    onError: (error: Error) => void,
    signal?: AbortSignal
  ): Promise<void> {
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/rag-chat/sessions/${sessionId}/messages/stream`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...getAuthorizationHeader() },
          body: JSON.stringify({ question }),
          signal, // 支持 AbortController 中止
        }
      );

      if (!response.ok) {
        try {
          const errorData = await response.json();
          if (errorData && errorData.message) {
            throw new Error(errorData.message);
          }
        } catch {
          // 忽略解析错误
        }
        throw new Error(`请求失败 (${response.status})`);
      }

      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error('无法获取响应流');
      }

      const decoder = new TextDecoder();
      let buffer = '';

      // 从 SSE 事件中提取内容和事件类型
      const extractEvent = (event: string): { type: string; data: string } | null => {
        if (!event.trim()) return null;

        const lines = event.split('\n');
        let eventType = 'message';
        const contentParts: string[] = [];

        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            contentParts.push(line.substring(5));
          }
        }

        if (contentParts.length === 0 && eventType === 'message') return null;

        const data = contentParts.join('')
          .replace(/\\n/g, '\n')
          .replace(/\\r/g, '\r');

        return { type: eventType, data };
      };

      while (true) {
        const { done, value } = await reader.read();

        if (done) {
          // 处理缓冲区剩余内容
          if (buffer.trim()) {
            const evt = extractEvent(buffer);
            if (evt) {
              if (evt.type === 'error') {
                onError(new Error(evt.data || '回答生成失败'));
                return;
              }
              if (evt.data) {
                onMessage(evt.data);
              }
            }
          }
          onComplete();
          break;
        }

        buffer += decoder.decode(value, { stream: true });

        // 统一按 \n\n 切块处理 SSE 事件
        let newlineIndex: number;
        while ((newlineIndex = buffer.indexOf('\n\n')) !== -1) {
          const eventBlock = buffer.substring(0, newlineIndex);
          buffer = buffer.substring(newlineIndex + 2);

          const evt = extractEvent(eventBlock);
          if (!evt) continue;

          // 处理 error 事件
          if (evt.type === 'error') {
            onError(new Error(evt.data || '回答生成失败'));
            return;
          }

          // 忽略 ping 心跳事件，只处理 data 内容
          if (evt.type === 'ping') continue;

          if (evt.data) {
            onMessage(evt.data);
          }
        }
      }
    } catch (error) {
      // AbortError 是用户主动中止，不算错误
      if (error instanceof Error && error.name === 'AbortError') {
        onComplete();
        return;
      }
      onError(new Error(getErrorMessage(error)));
    }
  },

  /**
   * 通过 Dify 聊天助手发送消息（流式SSE）
   * @param question 用户问题
   * @param signal 可选的 AbortSignal
   */
  async sendDifyChatStream(
    question: string,
    onMessage: (chunk: string) => void,
    onComplete: () => void,
    onError: (error: Error) => void,
    signal?: AbortSignal
  ): Promise<void> {
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/dify/chat/stream`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...getAuthorizationHeader() },
          body: JSON.stringify({ query: question, conversationId: '' }),
          signal,
        }
      );

      if (!response.ok) {
        try {
          const errorData = await response.json();
          if (errorData && errorData.message) throw new Error(errorData.message);
        } catch { /* ignore */ }
        throw new Error(`请求失败 (${response.status})`);
      }

      const reader = response.body?.getReader();
      if (!reader) throw new Error('无法获取响应流');

      const decoder = new TextDecoder();
      let buffer = '';

      const extractEvent = (event: string): { type: string; data: string } | null => {
        if (!event.trim()) return null;
        const lines = event.split('\n');
        let eventType = 'message';
        const contentParts: string[] = [];
        for (const line of lines) {
          if (line.startsWith('event:')) eventType = line.substring(6).trim();
          else if (line.startsWith('data:')) contentParts.push(line.substring(5));
        }
        if (contentParts.length === 0 && eventType === 'message') return null;
        const data = contentParts.join('').replace(/\\n/g, '\n').replace(/\\r/g, '\r');
        return { type: eventType, data };
      };

      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          if (buffer.trim()) {
            const evt = extractEvent(buffer);
            if (evt) {
              if (evt.type === 'error') { onError(new Error(evt.data || '工作流调用失败')); return; }
              if (evt.data) onMessage(evt.data);
            }
          }
          onComplete();
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        let idx: number;
        while ((idx = buffer.indexOf('\n\n')) !== -1) {
          const block = buffer.substring(0, idx);
          buffer = buffer.substring(idx + 2);
          const evt = extractEvent(block);
          if (!evt) continue;
          if (evt.type === 'error') { onError(new Error(evt.data || '工作流调用失败')); return; }
          if (evt.type === 'ping') continue;
          if (evt.data) onMessage(evt.data);
        }
      }
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') { onComplete(); return; }
      onError(new Error(getErrorMessage(error)));
    }
  },
};
