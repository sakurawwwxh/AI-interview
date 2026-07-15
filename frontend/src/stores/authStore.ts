import { create } from 'zustand';

export interface AuthUser {
  id: number;
  username: string;
  displayName: string;
  email: string;
  role: string;
  dailyTokenQuota: number;
}

interface AuthState {
  token: string | null;
  refreshToken: string | null;
  user: AuthUser | null;
  hydrated: boolean;
  login: (token: string, refreshToken: string, user: AuthUser) => void;
  logout: () => void;
  setToken: (token: string) => void;
  updateUser: (user: AuthUser) => void;
  loadFromStorage: () => void;
}

const TOKEN_KEY = 'auth_token';
const REFRESH_KEY = 'auth_refresh';
const USER_KEY = 'auth_user';

function loadStoredAuth(): Pick<AuthState, 'token' | 'refreshToken' | 'user'> {
  try {
    const userStr = localStorage.getItem(USER_KEY);
    return {
      token: localStorage.getItem(TOKEN_KEY),
      refreshToken: localStorage.getItem(REFRESH_KEY),
      user: userStr ? JSON.parse(userStr) as AuthUser : null,
    };
  } catch {
    return { token: null, refreshToken: null, user: null };
  }
}

const storedAuth = loadStoredAuth();

export const useAuthStore = create<AuthState>((set) => ({
  ...storedAuth,
  hydrated: true,

  login: (token, refreshToken, user) => {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(REFRESH_KEY, refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    set({ token, refreshToken, user, hydrated: true });
  },

  logout: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
    set({ token: null, refreshToken: null, user: null, hydrated: true });
  },

  setToken: (token) => {
    localStorage.setItem(TOKEN_KEY, token);
    set({ token, hydrated: true });
  },

  updateUser: (user) => {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    set({ user, hydrated: true });
  },

  loadFromStorage: () => {
    set({ ...loadStoredAuth(), hydrated: true });
  },
}));
