import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  BarChart3, BookOpen, BriefcaseBusiness, BrainCircuit, FileStack, LogOut,
  Menu, MessageSquare, Moon, Sun, Upload, UserRound, Users, Dumbbell,
  Shield, X, Home, CloudCog,
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
    const refreshUsage = () => {
      aiUsageApi.getUsage().then(setUsage).catch(() => setUsage(null));
    };
    refreshUsage();
    window.addEventListener('ai-usage-updated', refreshUsage);
    return () => window.removeEventListener('ai-usage-updated', refreshUsage);
  }, [location.pathname]);
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
      ...(user?.role === 'ADMIN' ? [
        { id: 'admin-dify', path: '/admin/dify', label: 'Dify 知识库', icon: CloudCog },
        { id: 'admin-users', path: '/admin/users', label: '用户管理', icon: Shield },
      ] : []),
      { id: 'profile', path: '/profile', label: '个人中心', icon: UserRound },
    ] },
  ];

  const isActive = (path: string) => path === '/'
    ? location.pathname === '/'
    : location.pathname === path || location.pathname.startsWith(`${path}/`);
  const usagePercent = usage && usage.dailyLimit > 0
    ? Math.min(100, Math.round((usage.dailyTokens / usage.dailyLimit) * 100)) : 0;

  return (
    <div className="relative min-h-screen bg-[var(--shell-bg)] text-[var(--shell-text)] transition-colors duration-300">
      {/* 键盘用户：跳过侧栏直达主内容 */}
      <a href="#main-content" className="skip-link">
        跳到主内容
      </a>

      {/* 双色氛围：青绿信号 + 琥珀暖光 */}
      <div aria-hidden className="pointer-events-none fixed inset-0 -z-0 overflow-hidden">
        <div className="absolute -left-28 -top-16 h-80 w-80 rounded-full bg-teal-500/[0.09] blur-3xl dark:bg-teal-400/[0.07]" />
        <div className="absolute -bottom-20 right-0 h-96 w-96 rounded-full bg-amber-500/[0.06] blur-3xl dark:bg-amber-400/[0.04]" />
        <div className="absolute left-1/3 top-1/2 h-64 w-64 -translate-y-1/2 rounded-full bg-cyan-400/[0.04] blur-3xl dark:bg-cyan-400/[0.03]" />
      </div>

      <div
        className={`fixed inset-0 z-40 bg-slate-950/50 backdrop-blur-[3px] transition-opacity duration-200 lg:hidden ${mobileNavOpen ? 'opacity-100' : 'pointer-events-none opacity-0'}`}
        onClick={() => setMobileNavOpen(false)}
        aria-hidden="true"
      />
      <button
        type="button"
        onClick={() => setMobileNavOpen(true)}
        aria-label="打开导航"
        aria-expanded={mobileNavOpen}
        aria-controls="app-sidebar"
        className="fixed right-4 top-4 z-30 inline-flex h-11 w-11 items-center justify-center rounded-xl border border-[var(--shell-border)] bg-[var(--shell-panel)] text-[var(--shell-text)] shadow-[var(--surface-elevated)] transition duration-200 hover:bg-[var(--shell-hover)] focus:outline-none focus-visible:ring-2 focus-visible:ring-teal-500/40 lg:hidden"
      >
        <Menu className="h-5 w-5" aria-hidden />
      </button>

      <aside
        id="app-sidebar"
        aria-label="主导航"
        className={`fixed inset-y-0 left-0 z-50 flex w-[17.5rem] flex-col border-r border-[var(--shell-border)] bg-[var(--shell-panel)]/92 shadow-[var(--surface-elevated)] backdrop-blur-xl transition-transform duration-300 lg:translate-x-0 ${mobileNavOpen ? 'translate-x-0' : '-translate-x-full'}`}
      >
        {/* 品牌区：卷首式 */}
        <div className="relative flex h-[5rem] items-center justify-between border-b border-[var(--shell-border)] px-5">
          <div className="pointer-events-none absolute inset-x-0 bottom-0 h-px bg-gradient-to-r from-transparent via-teal-500/30 to-transparent" />
          <Link to="/" className="group flex items-center gap-3" onClick={() => setMobileNavOpen(false)}>
            <span className="relative flex h-10 w-10 items-center justify-center rounded-2xl bg-gradient-to-br from-teal-400 via-teal-600 to-teal-900 text-white shadow-lg shadow-teal-700/30 transition duration-200 group-hover:scale-[1.04] group-hover:shadow-teal-600/40">
              <BrainCircuit className="h-[1.1rem] w-[1.1rem]" aria-hidden />
              <span className="absolute -right-0.5 -top-0.5 h-2 w-2 rounded-full bg-amber-400 ring-2 ring-[var(--shell-panel)]" aria-hidden />
            </span>
            <div className="leading-tight">
              <span className="font-display block text-[15px] font-semibold tracking-tight">面试训练</span>
              <span className="mt-0.5 block text-[10px] font-medium uppercase tracking-[0.2em] text-[var(--shell-subtle)]">
                Studio
              </span>
            </div>
          </Link>
          <button
            type="button"
            onClick={() => setMobileNavOpen(false)}
            className="ui-icon-btn lg:hidden"
            aria-label="关闭导航"
          >
            <X className="h-5 w-5" aria-hidden />
          </button>
        </div>

        <nav className="flex-1 overflow-y-auto px-3 py-5 scrollbar-thin" aria-label="功能分区">
          {navGroups.map((group, index) => (
            <section key={group.id} className={index === 0 ? '' : 'mt-6 border-t border-[var(--shell-border)] pt-5'} aria-labelledby={`nav-group-${group.id}`}>
              <p id={`nav-group-${group.id}`} className="px-3 pb-2.5 text-[10px] font-semibold uppercase tracking-[0.22em] text-[var(--shell-subtle)]">
                {group.title}
              </p>
              <div className="space-y-1">
                {group.items.map((item) => {
                  const active = isActive(item.path);
                  return (
                    <Link
                      key={item.id}
                      to={item.path}
                      onClick={() => setMobileNavOpen(false)}
                      aria-current={active ? 'page' : undefined}
                      className={`group relative flex min-h-11 items-center gap-3 rounded-xl px-3 text-[13px] transition duration-200 ${
                        active
                          ? 'bg-[var(--shell-active)] font-medium text-[var(--shell-text)] shadow-sm'
                          : 'text-[var(--shell-muted)] hover:bg-[var(--shell-hover)] hover:text-[var(--shell-text)]'
                      }`}
                    >
                      {active && (
                        <span className="absolute left-0 top-1/2 h-5 w-[3px] -translate-y-1/2 rounded-full bg-gradient-to-b from-teal-400 to-teal-700" aria-hidden />
                      )}
                      <item.icon className={`h-4 w-4 shrink-0 transition ${active ? 'text-teal-600 dark:text-teal-400' : 'text-[var(--shell-subtle)] group-hover:text-[var(--shell-text)]'}`} aria-hidden />
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
            <div
              className="mb-4 rounded-2xl border border-[var(--shell-border)] bg-[var(--shell-hover)]/70 px-3.5 py-3"
              role="status"
              aria-label={`今日 AI 用量 ${usagePercent}%`}
            >
              <div className="mb-2 flex items-center justify-between text-[11px] text-[var(--shell-muted)]">
                <span className="tracking-wide">今日 AI 用量</span>
                <span className="tabular-nums font-semibold text-[var(--shell-text)]">{usagePercent}%</span>
              </div>
              <div className="h-1.5 overflow-hidden rounded-full bg-[var(--shell-border)]" aria-hidden>
                <div
                  className="h-full rounded-full bg-gradient-to-r from-teal-600 via-teal-400 to-amber-400 transition-all duration-500"
                  style={{ width: `${usagePercent}%` }}
                />
              </div>
            </div>
          )}
          <div className="flex items-center gap-1.5">
            <span
              className="flex h-10 w-10 items-center justify-center rounded-full bg-gradient-to-br from-teal-500/25 to-amber-500/10 font-display text-sm font-bold text-teal-800 ring-1 ring-teal-500/25 dark:text-teal-200"
              aria-hidden
            >
              {(user?.displayName || user?.username || 'U').slice(0, 1).toUpperCase()}
            </span>
            <Link to="/profile" className="min-w-0 flex-1 rounded-lg px-1 py-1.5 transition hover:bg-[var(--shell-hover)]" onClick={() => setMobileNavOpen(false)}>
              <p className="truncate text-xs font-semibold">{user?.displayName || user?.username || '用户'}</p>
              <p className="mt-0.5 text-[11px] text-[var(--shell-muted)]">账户与模型</p>
            </Link>
            <button
              type="button"
              onClick={toggleTheme}
              title={theme === 'dark' ? '切换浅色模式' : '切换深色模式'}
              className="ui-icon-btn"
              aria-label={theme === 'dark' ? '切换浅色模式' : '切换深色模式'}
            >
              {theme === 'dark' ? <Sun className="h-4 w-4" aria-hidden /> : <Moon className="h-4 w-4" aria-hidden />}
            </button>
            <button
              type="button"
              onClick={handleLogout}
              title="退出登录"
              className="ui-icon-btn hover:bg-red-500/10 hover:text-red-500"
              aria-label="退出登录"
            >
              <LogOut className="h-4 w-4" aria-hidden />
            </button>
          </div>
        </div>
      </aside>

      <main
        id="main-content"
        tabIndex={-1}
        className="relative min-h-screen min-w-0 px-4 pb-14 pt-20 outline-none sm:px-6 lg:ml-[17.5rem] lg:px-10 lg:pb-16 lg:pt-10 xl:px-14"
      >
        {/*
          路由切换只用轻微位移，不用 opacity:0。
          否则在部分环境下动画未完成/被打断时，会出现「页面空白，刷新才正常」。
        */}
        <motion.div
          key={location.pathname}
          initial={{ y: 8 }}
          animate={{ y: 0 }}
          transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
          style={{ opacity: 1 }}
        >
          <Outlet />
        </motion.div>
      </main>
    </div>
  );
}
