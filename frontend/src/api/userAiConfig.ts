import { request } from './request';

export interface UserAiConfig { provider: string; baseUrl: string; model: string; apiKeyMasked: string; fallbackToPlatform: boolean; updatedAt: string; }
export interface SaveUserAiConfig { provider: string; baseUrl: string; model: string; apiKey: string; fallbackToPlatform: boolean; }
export const userAiConfigApi = {
  get: () => request.get<UserAiConfig | null>('/api/ai-config'),
  save: (data: SaveUserAiConfig) => request.put<UserAiConfig>('/api/ai-config', data),
  test: (data: SaveUserAiConfig) => request.post<void>('/api/ai-config/test', data, { timeout: 30000 }),
  remove: () => request.delete<void>('/api/ai-config'),
};
