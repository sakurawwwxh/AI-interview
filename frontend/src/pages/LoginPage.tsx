import { useState } from 'react';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowRight, CheckCircle2, Eye, EyeOff, Mic2, Sparkles, Target } from 'lucide-react';
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
  const [showPassword, setShowPassword] = useState(false);
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
      {/* 点阵网格 */}
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.35]"
        style={{
          backgroundImage: 'radial-gradient(rgba(255,255,255,0.07) 1px, transparent 1px)',
          backgroundSize: '22px 22px',
        }}
      />
      {/* 斜向编辑线 */}
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.04]"
        style={{
          backgroundImage: 'repeating-linear-gradient(-18deg, transparent, transparent 40px, rgba(94,234,212,0.5) 40px, rgba(94,234,212,0.5) 41px)',
        }}
      />

      <div className="relative mx-auto grid min-h-[calc(100vh-4rem)] max-w-6xl items-center gap-10 lg:grid-cols-[1.15fr_0.85fr] lg:gap-16">
        <section className="hidden lg:block">
          <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}>
            <div className="inline-flex items-center gap-2 rounded-full border border-teal-300/25 bg-teal-400/10 px-3.5 py-1.5 text-sm font-medium text-teal-100">
              <Sparkles className="h-4 w-4 text-amber-200" />
              面向真实岗位的面试训练
            </div>

            <h1 className="font-display mt-8 max-w-xl text-[3.25rem] font-semibold leading-[1.08] tracking-[-0.03em]">
              把下一场面试，
              <span className="mt-2 block text-gradient-accent">准备得更有底气。</span>
            </h1>

            <p className="mt-6 max-w-lg text-lg leading-8 text-slate-300/95">
              从简历、目标岗位到模拟问答与错题复练，把零散准备变成看得见的成长闭环。
            </p>

            <div className="mt-11 grid max-w-xl grid-cols-3 gap-3">
              {[
                { icon: Mic2, title: '模拟面试', desc: '按岗位生成练习', tone: 'text-teal-200' },
                { icon: Target, title: '精准复练', desc: '优先补强薄弱项', tone: 'text-amber-200' },
                { icon: Sparkles, title: '能力画像', desc: '持续追踪提升', tone: 'text-cyan-200' },
              ].map(({ icon: Icon, title, desc, tone }, i) => (
                <motion.div
                  key={title}
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.12 + i * 0.06, duration: 0.4 }}
                  className="rounded-2xl border border-white/[0.08] bg-white/[0.035] p-4 backdrop-blur-md transition hover:border-teal-400/30 hover:bg-white/[0.06]"
                >
                  <Icon className={`h-5 w-5 ${tone}`} />
                  <p className="mt-4 text-sm font-semibold tracking-tight">{title}</p>
                  <p className="mt-1 text-xs leading-5 text-slate-400">{desc}</p>
                </motion.div>
              ))}
            </div>

            <ul className="mt-10 space-y-3.5 text-sm text-slate-300">
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
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.08, ease: [0.22, 1, 0.36, 1] }}
          className="w-full"
        >
          <div className="glass-card relative overflow-hidden p-6 sm:p-8">
            {/* 卡片顶缘光 */}
            <div className="pointer-events-none absolute inset-x-8 top-0 h-px bg-gradient-to-r from-transparent via-teal-300/50 to-transparent" />

            <div className="mb-8 text-center">
              <div className="relative mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-teal-400 via-teal-600 to-teal-900 shadow-lg shadow-teal-500/30">
                <Mic2 className="h-7 w-7 text-white" />
                <span className="absolute -right-1 -top-1 h-2.5 w-2.5 rounded-full bg-amber-400 ring-2 ring-[#0c1412]" />
              </div>
              <p className="text-sm font-medium tracking-wide text-teal-200/90">欢迎回来</p>
              <h2 className="font-display mt-1.5 text-2xl font-semibold tracking-tight text-white">
                登录面试训练台
              </h2>
              <p className="mt-2 text-sm text-slate-400">继续你的面试准备与成长计划</p>
            </div>

            {notice && (
              <div role="status" className="mb-5 rounded-xl border border-emerald-400/20 bg-emerald-400/10 px-3 py-2.5 text-sm text-emerald-200">
                {notice}
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label htmlFor="login-username" className="mb-1.5 block text-sm font-medium text-slate-200">
                  用户名
                </label>
                <input
                  id="login-username"
                  type="text"
                  autoComplete="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="请输入用户名"
                  className="w-full rounded-xl border border-white/10 bg-slate-950/55 px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-teal-400/60 focus:ring-4 focus:ring-teal-500/15"
                  disabled={loading}
                  required
                />
              </div>
              <div>
                <label htmlFor="login-password" className="mb-1.5 block text-sm font-medium text-slate-200">
                  密码
                </label>
                <div className="relative">
                  <input
                    id="login-password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="请输入密码"
                    className="w-full rounded-xl border border-white/10 bg-slate-950/55 py-3 pl-4 pr-12 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-teal-400/60 focus:ring-4 focus:ring-teal-500/15"
                    disabled={loading}
                    required
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    className="absolute right-1.5 top-1/2 inline-flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-lg text-slate-400 transition hover:bg-white/5 hover:text-slate-200"
                    aria-label={showPassword ? '隐藏密码' : '显示密码'}
                    aria-pressed={showPassword}
                    tabIndex={0}
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" aria-hidden /> : <Eye className="h-4 w-4" aria-hidden />}
                  </button>
                </div>
              </div>

              {error && (
                <div role="alert" aria-live="assertive" className="rounded-xl border border-red-400/25 bg-red-400/10 px-3 py-2.5 text-sm text-red-200">
                  {error}
                </div>
              )}

              <motion.button
                type="submit"
                disabled={loading || !username.trim() || !password.trim()}
                aria-busy={loading}
                className="mt-2 flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-teal-500 via-teal-600 to-cyan-700 px-4 py-3.5 font-semibold text-white shadow-lg shadow-teal-700/30 transition duration-200 hover:from-teal-400 hover:via-teal-500 hover:to-cyan-600 disabled:cursor-not-allowed disabled:opacity-60"
                whileHover={{ scale: loading ? 1 : 1.01 }}
                whileTap={{ scale: loading ? 1 : 0.99 }}
              >
                {loading ? (
                  <>
                    <motion.span
                      className="h-4 w-4 rounded-full border-2 border-white/30 border-t-white"
                      animate={{ rotate: 360 }}
                      transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
                    />
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
