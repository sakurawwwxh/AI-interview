import { Navigate } from 'react-router-dom';
import { useAuthStore } from '../stores/authStore';

/**
 * 路由守卫：未登录时跳转登录页
 */
export default function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = useAuthStore((s) => s.token);
  const hydrated = useAuthStore((s) => s.hydrated);

  if (!hydrated) {
    return <div className="min-h-screen" />;
  }

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
