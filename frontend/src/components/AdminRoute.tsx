import { Navigate } from 'react-router-dom';
import { useAuthStore } from '../stores/authStore';

/** Prevents non-admin users from entering an administration route in the client. */
export default function AdminRoute({ children }: { children: React.ReactNode }) {
  const user = useAuthStore((state) => state.user);
  return user?.role === 'ADMIN' ? <>{children}</> : <Navigate to="/" replace />;
}
