import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  BarChart3, BookOpen, BriefcaseBusiness, BrainCircuit, FileStack, LogOut,
  Menu, MessageSquare, Moon, Sun, Upload, UserRound, Users, Dumbbell,
  Shield, X, Home,
} from 'lucide-react';
import { useTheme } from '../hooks/useTheme';
import { useAuthStore } from '../stores/authStore';
import { aiUsageApi, type AiUsageDTO } from '../api/aiUsage';
import { authApi } from '../api/auth';
import { useEffect, useState } from 'react';

interface NavItem {
  id: string;
  path: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
}

interface NavGroup {
  id: string;
  title: string;
  items: NavItem[];
}

export default function Layout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();
  const user = useAuthStore((state) => state.user);
  const refreshToken = useAuthStore((state) => state.refreshToken);
  const logout = useAuthStore((state) => state.logout);
  const [usage, setUsage] = useState<AiUsageDTO | null>(null);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  useEffect(() => {
    aiUsageApi.getUsage().then(setUsage).catch(() => setUsage(null));
  }, []);
  useEffect(() => setMobileNavOpen(false), [location.pathname]);

  const handleLogout = async () => {
    try {
      if (refreshToken) await authApi.logout(refreshToken);
    } catch {
      // 始终清理本地登录态
    }
    logout();
    navigate('/login', { replace: true });
  };

  const navGroups: NavGroup[] = [
    { id: 'workspace', title: '准备', items: [
      { id: 'home', path: '/', label: '今天', icon: Home },
      { id: 'job-targets', path: '/job-targets', label: '目标岗位', icon: BriefcaseBusiness },
      { id: 'upload', path: '/upload', label: '上传简历', icon: Upload },
      { id: 'resumes', path: '/history', label: '简历库', icon: FileStack },
    ] },
    { id: 'training', title: '训练', items: [
      { id: 'interviews', path: '/interviews', label: '模拟面试', icon: Users },
      { id: 'practice', path: '/practice', label: '专项复练', icon: Dumbbell },
      { id: 'statistics', path: '/interview-statistics', label: '能力画像', icon: BarChart3 },
    ] },
    { id: 'library', title: '资料', items: [
      { id: 'knowledgebase', path: '/knowledgebase', label: '面试百科', icon: BookOpen },
      { id: 'chat', path: '/knowledgebase/chat', label: '知识问答', icon: MessageSquare },
      { id: 'profile', path: '/profile', label: '个人中心', icon: UserRound },
      ...(user?.role === 'ADMIN' ? [{ id: 'admin-users', path: '/admin/users', label: '用户管理', icon: Shield }] : []),
    ] },
  ];

  const isActive = (path: string) => path === '/'
    ? location.pathname === '/'
    : location.pathname === path || location.pathname.startsWith(`${path}/`);
  const usagePercent = usage && usage.dailyLimit > 0
    ? Math.min(100, Math.round((usage.dailyTokens / usage.dailyLimit) * 100)) : 0;

  return (
    <div className="relative min-h-screen bg-[var(--shell-bg)] text-[var(--shell-text)] transition-colors duration-300">
      {/* 氛围光晕 */}
      <div
        aria-hidden
        className="pointer-events-none fixed inset-0 -z-0 overflow-hidden"
      >
        <div className="absolute -left-24 top-0 h-72 w-72 rounded-full bg-teal-400/10 blur-3xl dark:bg-teal-400/8" />
        <div className="absolute bottom-0 right-0 h-80 w-80 rounded-full bg-sky-400/8 blur-3xl dark:bg-cyan-500/6" />
      </div>

      <div
        className={`fixed inset-0 z-40 bg-slate-950/40 backdrop-blur-[2px] transition-opacity lg:hidden ${mobileNavOpen ? 'opacity-100' : 'pointer-events-none opacity-0'}`}
        onClick={() => setMobileNavOpen(false)}
        aria-hidden="true"
      />
      <button
        type="button"
        onClick={() => setMobileNavOpen(true)}
        aria-label="打开导航"
        aria-expanded={mobileNavOpen}
        className="fixed right-4 top-4 z-30 inline-flex h-11 w-11 items-center justify-center rounded-xl border border-[var(--shell-border)] bg-[var(--shell-panel)] text-[var(--shell-text)] shadow-[var(--surface-elevated)] transition hover:bg-[var(--shell-hover)] focus:outline-none focus:ring-2 focus:ring-teal-500/40 lg:hidden"
      >
        <Menu className="h-5 w-5" />
      </button>

      <aside className={`fixed inset-y-0 left-0 z-50 flex w-[17.5rem] flex-col border-r border-[var(--shell-border)] bg-[var(--shell-panel)]/95 shadow-[var(--surface-elevated)] backdrop-blur-xl transition-transform duration-300 lg:translate-x-0 ${mobileNavOpen ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="flex h-[4.75rem] items-center justify-between border-b border-[var(--shell-border)] px-5">
          <Link to="/" className="group flex items-center gap-3" onClick={() => setMobileNavOpen(false)}>
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-teal-400 to-teal-700 text-white shadow-lg shadow-teal-600/25 transition group-hover:scale-[1.03]">
              <BrainCircuit className="h-4 w-4" />
            </span>
            <div className="leading-tight">
              <span className="block text-sm font-semibold tracking-tight">面试训练</span>
              <span className="text-[10px] font-medium uppercase tracking-[0.14em] text-[var(--shell-subtle)]">Studio</span>
            </div>
          </Link>
          <button type="button" onClick={() => setMobileNavOpen(false)} className="rounded-lg p-1.5 text-[var(--shell-muted)] transition hover:bg-[var(--shell-hover)] hover:text-[var(--shell-text)] lg:hidden" aria-label="关闭导航">
            <X className="h-5 w-5" />
          </button>
        </div>

        <nav className="flex-1 overflow-y-auto px-3 py-5 scrollbar-thin">
          {navGroups.map((group, index) => (
            <section key={group.id} className={index === 0 ? '' : 'mt-6 border-t border-[var(--shell-border)] pt-5'}>
              <p className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-[var(--shell-subtle)]">{group.title}</p>
              <div className="space-y-1">
                {group.items.map((item) => {
                  const active = isActive(item.path);
                  return (
                    <Link
                      key={item.id}
                      to={item.path}
                      onClick={() => setMobileNavOpen(false)}
                      className={`group relative flex h-10 items-center gap-3 rounded-xl px-3 text-sm transition ${
                        active
                          ? 'bg-[var(--shell-active)] font-medium text-[var(--shell-text)] shadow-sm'
                          : 'text-[var(--shell-muted)] hover:bg-[var(--shell-hover)] hover:text-[var(--shell-text)]'
                      }`}
                    >
                      {active && (
                        <span className="absolute left-0 top-1/2 h-5 w-0.5 -translate-y-1/2 rounded-full bg-teal-500" />
                      )}
                      <item.icon className={`h-4 w-4 shrink-0 ${active ? 'text-teal-600 dark:text-teal-400' : 'text-[var(--shell-subtle)] group-hover:text-[var(--shell-text)]'}`} />
                      <span>{item.label}</span>
                    </Link>
                  );
                })}
              </div>
            </section>
          ))}
        </nav>

        <div className="border-t border-[var(--shell-border)] px-4 py-4">
          {usage && (
            <div className="mb-4 rounded-xl border border-[var(--shell-border)] bg-[var(--shell-hover)]/60 px-3 py-2.5">
              <div className="mb-1.5 flex items-center justify-between text-[11px] text-[var(--shell-muted)]">
                <span>今日 AI 用量</span>
                <span className="tabular-nums font-medium text-[var(--shell-text)]">{usagePercent}%</span>
              </div>
              <div className="h-1.5 overflow-hidden rounded-full bg-[var(--shell-border)]">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-teal-500 to-cyan-400 transition-all duration-500"
                  style={{ width: `${usagePercent}%` }}
                />
              </div>
            </div>
          )}
          <div className="flex items-center gap-2.5">
            <span className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-teal-500/20 to-cyan-500/10 text-xs font-bold text-teal-700 ring-1 ring-teal-500/20 dark:text-teal-300">
              {(user?.displayName || user?.username || 'U').slice(0, 1).toUpperCase()}
            </span>
            <Link to="/profile" className="min-w-0 flex-1" onClick={() => setMobileNavOpen(false)}>
              <p className="truncate text-xs font-semibold">{user?.displayName || user?.username || '用户'}</p>
              <p className="mt-0.5 text-[11px] text-[var(--shell-muted)]">账户与模型</p>
            </Link>
            <button
              type="button"
              onClick={toggleTheme}
              title={theme === 'dark' ? '切换浅色模式' : '切换深色模式'}
              className="rounded-lg p-1.5 text-[var(--shell-muted)] transition hover:bg-[var(--shell-hover)] hover:text-[var(--shell-text)]"
              aria-label={theme === 'dark' ? '切换浅色模式' : '切换深色模式'}
            >
              {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            </button>
            <button
              type="button"
              onClick={handleLogout}
              title="退出登录"
              className="rounded-lg p-1.5 text-[var(--shell-muted)] transition hover:bg-red-500/10 hover:text-red-500"
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </div>
      </aside>

      <main className="relative min-h-screen min-w-0 px-4 pb-12 pt-20 sm:px-6 lg:ml-[17.5rem] lg:px-10 lg:pb-16 lg:pt-10 xl:px-14">
        <motion.div
          key={location.pathname}
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
        >
          <Outlet />
        </motion.div>
      </main>
    </div>
  );
}
