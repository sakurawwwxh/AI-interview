import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowRight, CheckCircle2, Sparkles, UserPlus } from 'lucide-react';
import { authApi } from '../api/auth';
import { useAuthStore } from '../stores/authStore';

const accountHighlights = [
  '建立专属的简历、岗位和面试训练档案',
  '每次评分都会沉淀为可复练的成长记录',
  '可在个人中心配置自己的 AI 模型服务',
];

export default function RegisterPage() {
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim() || !email.trim()) return;

    setLoading(true);
    setError('');
    try {
      const res = await authApi.register(username.trim(), password, email.trim());
      login(res.accessToken, res.refreshToken, res.user);
      navigate('/upload', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : '注册失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  const fieldClass =
    'w-full rounded-xl border border-white/10 bg-slate-950/60 px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-teal-400/60 focus:ring-4 focus:ring-teal-500/15';

  return (
    <main className="auth-mesh relative min-h-screen overflow-hidden px-4 py-8 text-white sm:px-6 lg:px-8">
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.35]"
        style={{ backgroundImage: 'radial-gradient(rgba(255,255,255,0.07) 1px, transparent 1px)', backgroundSize: '22px 22px' }}
      />
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.04]"
        style={{ backgroundImage: 'repeating-linear-gradient(-18deg, transparent, transparent 40px, rgba(94,234,212,0.5) 40px, rgba(94,234,212,0.5) 41px)' }}
      />
      <div className="relative mx-auto grid min-h-[calc(100vh-4rem)] max-w-6xl items-center gap-10 lg:grid-cols-[1.15fr_0.85fr] lg:gap-16">
        <section className="hidden lg:block">
          <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}>
            <div className="inline-flex items-center gap-2 rounded-full border border-teal-300/25 bg-teal-400/10 px-3.5 py-1.5 text-sm font-medium text-teal-100">
              <Sparkles className="h-4 w-4 text-amber-200" />
              从准备到进步的训练空间
            </div>
            <h1 className="font-display mt-8 max-w-xl text-[3.25rem] font-semibold leading-[1.08] tracking-[-0.03em]">
              从第一份简历开始，
              <span className="mt-2 block text-gradient-accent">让准备更有方向。</span>
            </h1>
            <p className="mt-6 max-w-lg text-lg leading-8 text-slate-300/95">
              创建账号后，就可以把目标岗位、模拟面试和能力提升放在同一个成长档案里。
            </p>
            <div className="mt-10 rounded-3xl border border-white/[0.08] bg-white/[0.035] p-6 backdrop-blur-md">
              <p className="text-sm font-semibold tracking-tight text-white">注册后即可开始</p>
              <ol className="mt-5 space-y-4 text-sm text-slate-300">
                {['上传简历并获得 AI 分析', '开始与目标岗位匹配的模拟面试', '通过错题复练持续提升'].map((text, i) => (
                  <li key={text} className="flex gap-3">
                    <span className="font-display flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-teal-400/15 text-xs font-bold text-teal-100">
                      {i + 1}
                    </span>
                    {text}
                  </li>
                ))}
              </ol>
            </div>
            <ul className="mt-8 space-y-3.5 text-sm text-slate-300">
              {accountHighlights.map((item) => (
                <li key={item} className="flex items-center gap-3">
                  <CheckCircle2 className="h-4 w-4 shrink-0 text-teal-300" />
                  {item}
                </li>
              ))}
            </ul>
          </motion.div>
        </section>

        <motion.section initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5, delay: 0.08, ease: [0.22, 1, 0.36, 1] }} className="w-full">
          <div className="glass-card relative overflow-hidden p-6 sm:p-8">
            <div className="pointer-events-none absolute inset-x-8 top-0 h-px bg-gradient-to-r from-transparent via-teal-300/50 to-transparent" />
            <div className="mb-8 text-center">
              <div className="relative mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-teal-400 via-teal-600 to-teal-900 shadow-lg shadow-teal-500/30">
                <UserPlus className="h-7 w-7 text-white" />
                <span className="absolute -right-1 -top-1 h-2.5 w-2.5 rounded-full bg-amber-400 ring-2 ring-[#0c1412]" />
              </div>
              <p className="text-sm font-medium tracking-wide text-teal-200/90">开始你的训练</p>
              <h2 className="font-display mt-1.5 text-2xl font-semibold tracking-tight text-white">创建面试训练账号</h2>
              <p className="mt-2 text-sm text-slate-400">填写基础信息，马上开始准备</p>
            </div>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label htmlFor="register-username" className="mb-1.5 block text-sm font-medium text-slate-200">用户名</label>
                <input id="register-username" type="text" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="3–20 个字符" className={fieldClass} disabled={loading} minLength={3} maxLength={20} required />
              </div>
              <div>
                <label htmlFor="register-password" className="mb-1.5 block text-sm font-medium text-slate-200">密码</label>
                <input id="register-password" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="6–32 个字符" className={fieldClass} disabled={loading} minLength={6} maxLength={32} required />
              </div>
              <div>
                <label htmlFor="register-email" className="mb-1.5 block text-sm font-medium text-slate-200">邮箱</label>
                <input id="register-email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="请输入邮箱" className={fieldClass} disabled={loading} required />
              </div>
              {error && <div role="alert" className="rounded-xl border border-red-400/25 bg-red-400/10 px-3 py-2.5 text-sm text-red-200">{error}</div>}
              <motion.button
                type="submit"
                disabled={loading || !username.trim() || !password.trim() || !email.trim()}
                className="mt-2 flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-teal-500 via-teal-600 to-cyan-700 px-4 py-3.5 font-semibold text-white shadow-lg shadow-teal-700/30 transition hover:from-teal-400 hover:via-teal-500 hover:to-cyan-600 disabled:cursor-not-allowed disabled:opacity-60"
                whileHover={{ scale: loading ? 1 : 1.01 }}
                whileTap={{ scale: loading ? 1 : 0.99 }}
              >
                {loading ? (
                  <>
                    <motion.span className="h-4 w-4 rounded-full border-2 border-white/30 border-t-white" animate={{ rotate: 360 }} transition={{ duration: 1, repeat: Infinity, ease: 'linear' }} />
                    注册中…
                  </>
                ) : (
                  <>
                    创建账号并开始
                    <ArrowRight className="h-4 w-4" />
                  </>
                )}
              </motion.button>
            </form>
            <p className="mt-6 text-center text-sm text-slate-400">
              已有账号？{' '}
              <Link to="/login" className="font-semibold text-teal-300 transition hover:text-teal-200">
                返回登录
              </Link>
            </p>
          </div>
        </motion.section>
      </div>
    </main>
  );
}
