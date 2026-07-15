import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios';
import { useAuthStore } from '../stores/authStore';
import { authApi } from './auth';

interface Result<T = unknown> {
  code: number;
  message: string;
  data: T;
}

type RetriableConfig = AxiosRequestConfig & { _retry?: boolean };
type PendingRequest = { config: RetriableConfig; resolve: (value: AxiosResponse) => void; reject: (reason?: unknown) => void };

const baseURL = import.meta.env.PROD ? '' : 'http://localhost:8080';
const instance: AxiosInstance = axios.create({ baseURL, timeout: 60000 });

let isRefreshing = false;
let pendingQueue: PendingRequest[] = [];

function applyAuthorization<T extends AxiosRequestConfig>(config: T, token: string): T {
  (config as AxiosRequestConfig).headers = {
    ...(config.headers ?? {}),
    Authorization: `Bearer ${token}`,
  };
  return config;
}

function flushQueue(error?: unknown, token?: string) {
  const queue = pendingQueue;
  pendingQueue = [];
  queue.forEach(({ config, resolve, reject }) => {
    if (error || !token) {
      reject(error ?? new Error('登录已过期'));
      return;
    }
    instance(applyAuthorization(config, token)).then(resolve).catch(reject);
  });
}

async function refreshAndRetry(originalConfig: RetriableConfig): Promise<AxiosResponse> {
  if (originalConfig.url?.includes('/api/auth/refresh') || originalConfig._retry) {
    throw new Error('登录已过期');
  }
  if (isRefreshing) {
    return new Promise<AxiosResponse>((resolve, reject) => pendingQueue.push({ config: originalConfig, resolve, reject }));
  }

  const refreshToken = useAuthStore.getState().refreshToken;
  if (!refreshToken) {
    const error = new Error('登录已过期');
    useAuthStore.getState().logout();
    window.location.assign('/login');
    throw error;
  }

  originalConfig._retry = true;
  isRefreshing = true;
  try {
    const refreshed = await authApi.refresh(refreshToken);
    const store = useAuthStore.getState();
    if (store.user) {
      store.login(refreshed.accessToken, refreshed.refreshToken, store.user);
    } else {
      store.setToken(refreshed.accessToken);
    }
    flushQueue(undefined, refreshed.accessToken);
    return instance(applyAuthorization(originalConfig, refreshed.accessToken));
  } catch (error) {
    flushQueue(error);
    useAuthStore.getState().logout();
    window.location.assign('/login');
    throw error;
  } finally {
    isRefreshing = false;
  }
}

instance.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

instance.interceptors.response.use(
  (response) => {
    const result = response.data as Result;
    if (!result || typeof result !== 'object' || !('code' in result)) return response;
    if (result.code === 200) {
      response.data = result.data;
      return response;
    }
    if (result.code === 9005) return refreshAndRetry(response.config as RetriableConfig);
    return Promise.reject(new Error(result.message || '请求失败'));
  },
  (error) => {
    const result = error.response?.data as Result | undefined;
    if (error.response?.status === 401 && result?.code === 9005) {
      return refreshAndRetry(error.config as RetriableConfig);
    }
    if (result?.message) return Promise.reject(new Error(result.message));
    return Promise.reject(error instanceof Error ? error : new Error('网络连接失败，请检查网络'));
  },
);

export function getAuthorizationHeader(): Record<string, string> {
  const token = useAuthStore.getState().token;
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export const request = {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.get(url, config).then((res) => res.data);
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.post(url, data, config).then((res) => res.data);
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.put(url, data, config).then((res) => res.data);
  },
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.patch(url, data, config).then((res) => res.data);
  },
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.delete(url, config).then((res) => res.data);
  },
  upload<T>(url: string, formData: FormData, config?: AxiosRequestConfig): Promise<T> {
    return instance.post(url, formData, {
      timeout: 120000,
      headers: { 'Content-Type': 'multipart/form-data' },
      ...config,
    }).then((res) => res.data);
  },
  getInstance(): AxiosInstance {
    return instance;
  },
};

export function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '未知错误';
}

export default request;
