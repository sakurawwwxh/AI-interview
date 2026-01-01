import { request, getErrorMessage } from './request';

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
   * 删除会话
   */
  async deleteSession(sessionId: number): Promise<void> {
    return request.delete(`/api/rag-chat/sessions/${sessionId}`);
  },

  /**
   * 发送消息（流式SSE）
   */
  async sendMessageStream(
    sessionId: number,
    question: string,
    onMessage: (chunk: string) => void,
    onComplete: () => void,
    onError: (error: Error) => void
  ): Promise<void> {
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/rag-chat/sessions/${sessionId}/messages/stream`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ question }),
        }
      );

      if (!response.ok) {
        // 尝试解析错误响应
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

      // 从 SSE 事件中提取内容
      const extractEventContent = (event: string): string | null => {
        if (!event.trim()) return null;

        // 处理多行 SSE 格式，每行可能以 "data:" 开头
        const lines = event.split('\n');
        const contentLines: string[] = [];

        for (const line of lines) {
          let content = line.trim();
          // 移除 "data:" 前缀
          if (content.startsWith('data:')) {
            content = content.substring(5).trim();
          }
          // 跳过空行和事件类型行（如 "event: message"）
          if (content && !content.startsWith('event:') && !content.startsWith('id:')) {
            contentLines.push(content);
          }
        }

        const result = contentLines.join('\n');
        return result || null;
      };

      while (true) {
        const { done, value } = await reader.read();

        if (done) {
          if (buffer) {
            const content = extractEventContent(buffer);
            if (content) {
              onMessage(content);
            }
          }
          onComplete();
          break;
        }

        buffer += decoder.decode(value, { stream: true });

        // SSE 事件以 \n\n 分隔，但也需要处理单行的情况
        let newlineIndex = buffer.indexOf('\n\n');
        if (newlineIndex === -1) {
          // 如果没有找到 \n\n，尝试处理单行 data: 格式
          const singleLineIndex = buffer.indexOf('\n');
          if (singleLineIndex !== -1 && buffer.substring(0, singleLineIndex).startsWith('data:')) {
            const line = buffer.substring(0, singleLineIndex);
            const content = extractEventContent(line);
            if (content) {
              onMessage(content);
            }
            buffer = buffer.substring(singleLineIndex + 1);
          }
          continue;
        }

        // 处理完整的事件块
        const eventBlock = buffer.substring(0, newlineIndex);
        buffer = buffer.substring(newlineIndex + 2);

        const content = extractEventContent(eventBlock);
        if (content !== null) {
          onMessage(content);
        }
      }
    } catch (error) {
      onError(new Error(getErrorMessage(error)));
    }
  },
};
