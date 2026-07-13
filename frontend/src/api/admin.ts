import { request } from './request';

export interface UserListItem {
  id: number;
  username: string;
  displayName: string;
  email: string;
  role: 'USER' | 'ADMIN';
  dailyTokenQuota: number;
  createdAt: string;
}

export interface UserPage {
  items: UserListItem[];
  total: number;
  page: number;
  size: number;
}

export type UserRole = 'USER' | 'ADMIN';

export const adminApi = {
  getUsers(params: { keyword?: string; page?: number; size?: number } = {}) {
    return request.get<UserPage>('/api/admin/users', { params });
  },
  getUser(id: number) {
    return request.get<UserListItem>(`/api/admin/users/${id}`);
  },
  updateRole(id: number, role: UserRole) {
    return request.put<UserListItem>(`/api/admin/users/${id}/role`, { role });
  },
  updateQuota(id: number, dailyTokenQuota: number) {
    return request.put<UserListItem>(`/api/admin/users/${id}/quota`, { dailyTokenQuota });
  },
  deleteUser(id: number) {
    return request.delete<void>(`/api/admin/users/${id}`);
  },
};
