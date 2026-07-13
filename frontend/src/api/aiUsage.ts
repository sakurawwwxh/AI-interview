import { request } from './request';

export interface AiUsageDTO {
  dailyTokens: number;
  dailyLimit: number;
  monthlyTokens: number;
  dailyRequestCount: number;
  quotaEnabled: boolean;
  secondsUntilReset: number;
}

export const aiUsageApi = {
  getUsage(): Promise<AiUsageDTO> {
    return request.get('/api/ai/usage');
  },
};
