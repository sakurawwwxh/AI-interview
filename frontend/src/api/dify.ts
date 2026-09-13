import { request } from './request';

export type DifySyncStatus = 'PENDING' | 'SYNCED' | 'SUCCESS' | 'FAILED';

export interface DifySyncStatusItem {
  knowledgeBaseId: number;
  name: string;
  ownerUserId: number | null;
  ownerUsername: string | null;
  difyDocumentId: string | null;
  syncStatus: DifySyncStatus | null;
  lastSyncTime: string | null;
  errorMessage: string | null;
}

export interface DifySyncLog {
  id: number;
  knowledgeBaseId: number | null;
  difyDocumentId: string | null;
  syncDirection: 'TO_DIFY' | 'FROM_DIFY';
  syncAction: 'CREATE' | 'UPDATE' | 'DELETE';
  syncStatus: DifySyncStatus;
  errorMessage: string | null;
  createdAt: string;
}

export const difyAdminApi = {
  getSyncStatus: () => request.get<DifySyncStatusItem[]>('/api/dify/sync/status'),
  getSyncLogs: (knowledgeBaseId?: number, limit = 12) => request.get<DifySyncLog[]>('/api/dify/sync/logs', {
    params: { ...(knowledgeBaseId ? { knowledgeBaseId } : {}), limit },
  }),
  syncNow: () => request.post<void>('/api/dify/sync'),
  syncKnowledgeBaseToDify: (knowledgeBaseId: number) => request.post<void>(`/api/dify/knowledge-bases/${knowledgeBaseId}/sync-to-dify`),
  transferKnowledgeBaseOwner: (knowledgeBaseId: number, userId: number) => request.put<void>(`/api/dify/knowledge-bases/${knowledgeBaseId}/owner`, { userId }),
};
