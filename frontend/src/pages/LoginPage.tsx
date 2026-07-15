import { useState } from 'react';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowRight, CheckCircle2, Mic2, Sparkles, Target } from 'lucide-react';
import { authApi } from '../api/auth';
import { useAuthStore } from '../stores/authStore';

const trainingHighlights = [
  '从简历分析开始，快速定位准备方向',
  '模拟面试后获得针对性的复练任务',
  '用能力趋势记录每一次真实提升',
];

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const login = useAuthStore((s) => s.login);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const notice = (location.state as { message?: string } | null)?.message;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) return;

    setLoading(true);
    setError('');
    try {
      const res = await authApi.login(username.trim(), password);
      login(res.accessToken, res.refreshToken, res.user);
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="auth-mesh relative min-h-screen overflow-hidden px-4 py-8 text-white sm:px-6 lg:px-8">
      <div className="pointer-events-none absolute inset-0 opacity-40" style={{ backgroundImage: 'radial-gradient(rgba(255,255,255,0.06) 1px, transparent 1px)', backgroundSize: '24px 24px' }} />
      <div className="relative mx-auto grid min-h-[calc(100vh-4rem)] max-w-6xl items-center gap-10 lg:grid-cols-[1.15fr_0.85fr] lg:gap-16">
        <section className="hidden lg:block">
          <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.45 }}>
            <div className="inline-flex items-center gap-2 rounded-full border border-teal-300/20 bg-teal-400/10 px-3.5 py-1.5 text-sm font-medium text-teal-100">
              <Sparkles className="h-4 w-4" />
              面向真实岗位的面试训练
            </div>
            <h1 className="mt-7 max-w-xl text-5xl font-bold leading-[1.1] tracking-tight">
              把下一场面试，
              <span className="mt-1 block text-gradient-accent">准备得更有底气。</span>
            </h1>
            <p className="mt-6 max-w-lg text-lg leading-8 text-slate-300/95">
              从简历、目标岗位到模拟问答与错题复练，把零散准备变成看得见的成长闭环。
            </p>

            <div className="mt-10 grid max-w-xl grid-cols-3 gap-3">
              {[
                { icon: Mic2, title: '模拟面试', desc: '按岗位生成练习', tone: 'text-teal-200' },
                { icon: Target, title: '精准复练', desc: '优先补强薄弱项', tone: 'text-cyan-200' },
                { icon: Sparkles, title: '能力画像', desc: '持续追踪提升', tone: 'text-sky-200' },
              ].map(({ icon: Icon, title, desc, tone }) => (
                <div key={title} className="rounded-2xl border border-white/[0.08] bg-white/[0.04] p-4 backdrop-blur-md transition hover:border-teal-400/25 hover:bg-white/[0.07]">
                  <Icon className={`h-5 w-5 ${tone}`} />
                  <p className="mt-4 text-sm font-semibold">{title}</p>
                  <p className="mt-1 text-xs leading-5 text-slate-400">{desc}</p>
                </div>
              ))}
            </div>

            <ul className="mt-9 space-y-3 text-sm text-slate-300">
              {trainingHighlights.map((item) => (
                <li key={item} className="flex items-center gap-3">
                  <CheckCircle2 className="h-4 w-4 shrink-0 text-teal-300" />
                  {item}
                </li>
              ))}
            </ul>
          </motion.div>
        </section>

        <motion.section
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45, delay: 0.06 }}
          className="w-full"
        >
          <div className="glass-card p-6 sm:p-8">
            <div className="mb-8 text-center">
              <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-teal-400 to-teal-700 shadow-lg shadow-teal-500/30">
                <Mic2 className="h-7 w-7 text-white" />
              </div>
              <p className="text-sm font-medium text-teal-200/90">欢迎回来</p>
              <h2 className="mt-1 text-2xl font-bold tracking-tight text-white">登录面试训练台</h2>
              <p className="mt-2 text-sm text-slate-400">继续你的面试准备与成长计划</p>
            </div>

            {notice && (
              <div role="status" className="mb-5 rounded-xl border border-emerald-400/20 bg-emerald-400/10 px-3 py-2.5 text-sm text-emerald-200">
                {notice}
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label htmlFor="login-username" className="mb-1.5 block text-sm font-medium text-slate-200">用户名</label>
                <input
                  id="login-username"
                  type="text"
                  autoComplete="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="请输入用户名"
                  className="w-full rounded-xl border border-white/10 bg-slate-950/60 px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-teal-400/60 focus:ring-4 focus:ring-teal-500/15"
                  disabled={loading}
                  required
                />
              </div>
              <div>
                <label htmlFor="login-password" className="mb-1.5 block text-sm font-medium text-slate-200">密码</label>
                <input
                  id="login-password"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="请输入密码"
                  className="w-full rounded-xl border border-white/10 bg-slate-950/60 px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-teal-400/60 focus:ring-4 focus:ring-teal-500/15"
                  disabled={loading}
                  required
                />
              </div>

              {error && (
                <div role="alert" className="rounded-xl border border-red-400/25 bg-red-400/10 px-3 py-2.5 text-sm text-red-200">
                  {error}
                </div>
              )}

              <motion.button
                type="submit"
                disabled={loading || !username.trim() || !password.trim()}
                className="mt-2 flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-teal-500 to-cyan-600 px-4 py-3 font-semibold text-white shadow-lg shadow-teal-600/25 transition hover:from-teal-400 hover:to-cyan-500 disabled:cursor-not-allowed disabled:opacity-60"
                whileHover={{ scale: loading ? 1 : 1.01 }}
                whileTap={{ scale: loading ? 1 : 0.99 }}
              >
                {loading ? (
                  <>
                    <motion.span className="h-4 w-4 rounded-full border-2 border-white/30 border-t-white" animate={{ rotate: 360 }} transition={{ duration: 1, repeat: Infinity, ease: 'linear' }} />
                    登录中…
                  </>
                ) : (
                  <>
                    登录并继续
                    <ArrowRight className="h-4 w-4" />
                  </>
                )}
              </motion.button>
            </form>

            <p className="mt-6 text-center text-sm text-slate-400">
              还没有账号？{' '}
              <Link to="/register" className="font-semibold text-teal-300 transition hover:text-teal-200">
                立即注册
              </Link>
            </p>
          </div>
        </motion.section>
      </div>
    </main>
  );
}
