import { request } from './request';

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: {
    id: number;
    username: string;
    displayName: string;
    email: string;
    role: string;
    dailyTokenQuota: number;
  };
}

export interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
}

export const authApi = {
  register(username: string, password: string, email: string): Promise<AuthResponse> {
    return request.post('/api/auth/register', { username, password, email });
  },

  login(username: string, password: string): Promise<AuthResponse> {
    return request.post('/api/auth/login', { username, password });
  },

  refresh(refreshToken: string): Promise<RefreshResponse> {
    return request.post('/api/auth/refresh', { refreshToken });
  },

  logout(refreshToken: string): Promise<void> {
    return request.post('/api/auth/logout', { refreshToken });
  },

  getMe(): Promise<AuthResponse['user']> {
    return request.get('/api/auth/me');
  },

  updateProfile(displayName: string, email: string): Promise<AuthResponse['user']> {
    return request.put('/api/auth/me', { displayName, email });
  },

  changePassword(currentPassword: string, newPassword: string): Promise<void> {
    return request.post('/api/auth/change-password', { currentPassword, newPassword });
  },
};
