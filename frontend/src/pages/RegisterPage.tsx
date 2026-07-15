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

  return (
    <main className="relative min-h-screen overflow-hidden bg-slate-950 px-4 py-8 text-white sm:px-6 lg:px-8">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_14%_18%,rgba(99,102,241,0.30),transparent_28%),radial-gradient(circle_at_84%_74%,rgba(34,211,238,0.16),transparent_24%)]" />
      <div className="relative mx-auto grid min-h-[calc(100vh-4rem)] max-w-6xl items-center gap-10 lg:grid-cols-[1.1fr_0.9fr] lg:gap-16">
        <section className="hidden lg:block">
          <div className="inline-flex items-center gap-2 rounded-full border border-primary-300/20 bg-primary-400/10 px-3 py-1.5 text-sm font-medium text-primary-100"><Sparkles className="h-4 w-4" />从准备到进步的训练空间</div>
          <h1 className="mt-6 max-w-xl text-5xl font-bold leading-[1.12] tracking-tight">从第一份简历开始，<span className="block bg-gradient-to-r from-primary-200 via-indigo-100 to-cyan-200 bg-clip-text text-transparent">让准备更有方向。</span></h1>
          <p className="mt-6 max-w-lg text-lg leading-8 text-slate-300">创建账号后，就可以把目标岗位、模拟面试和能力提升放在同一个成长档案里。</p>
          <div className="mt-10 rounded-3xl border border-white/10 bg-white/[0.06] p-6 backdrop-blur-sm"><p className="text-sm font-semibold text-white">注册后即可开始</p><ol className="mt-5 space-y-4 text-sm text-slate-300"><li className="flex gap-3"><span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary-400/20 text-xs font-bold text-primary-100">1</span>上传简历并获得 AI 分析</li><li className="flex gap-3"><span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary-400/20 text-xs font-bold text-primary-100">2</span>开始与目标岗位匹配的模拟面试</li><li className="flex gap-3"><span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary-400/20 text-xs font-bold text-primary-100">3</span>通过错题复练持续提升</li></ol></div>
          <ul className="mt-8 space-y-3 text-sm text-slate-300">{accountHighlights.map((item) => <li key={item} className="flex items-center gap-3"><CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-300" />{item}</li>)}</ul>
        </section>

        <motion.section initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4 }} className="w-full">
          <div className="rounded-3xl border border-white/10 bg-slate-900/75 p-6 shadow-2xl shadow-black/30 backdrop-blur-xl sm:p-8">
            <div className="mb-8 text-center"><div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-primary-400 to-primary-600 shadow-lg shadow-primary-500/30"><UserPlus className="h-7 w-7 text-white" /></div><p className="text-sm font-medium text-primary-200">开始你的训练</p><h2 className="mt-1 text-2xl font-bold tracking-tight text-white">创建 AI 面试账号</h2><p className="mt-2 text-sm text-slate-400">填写基础信息，马上开始准备</p></div>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div><label htmlFor="register-username" className="mb-1.5 block text-sm font-medium text-slate-200">用户名</label><input id="register-username" type="text" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="3–20 个字符" className="w-full rounded-xl border border-slate-600 bg-slate-800/90 px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-primary-400 focus:ring-4 focus:ring-primary-500/20" disabled={loading} minLength={3} maxLength={20} required /></div>
              <div><label htmlFor="register-password" className="mb-1.5 block text-sm font-medium text-slate-200">密码</label><input id="register-password" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="6–32 个字符" className="w-full rounded-xl border border-slate-600 bg-slate-800/90 px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-primary-400 focus:ring-4 focus:ring-primary-500/20" disabled={loading} minLength={6} maxLength={32} required /></div>
              <div><label htmlFor="register-email" className="mb-1.5 block text-sm font-medium text-slate-200">邮箱</label><input id="register-email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="请输入邮箱" className="w-full rounded-xl border border-slate-600 bg-slate-800/90 px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-primary-400 focus:ring-4 focus:ring-primary-500/20" disabled={loading} required /></div>
              {error && <div role="alert" className="rounded-xl border border-red-400/25 bg-red-400/10 px-3 py-2.5 text-sm text-red-200">{error}</div>}
              <motion.button type="submit" disabled={loading || !username.trim() || !password.trim() || !email.trim()} className="mt-2 flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-primary-500 to-indigo-500 px-4 py-3 font-semibold text-white shadow-lg shadow-primary-500/20 transition hover:from-primary-400 hover:to-indigo-400 disabled:cursor-not-allowed disabled:opacity-60" whileHover={{ scale: loading ? 1 : 1.01 }} whileTap={{ scale: loading ? 1 : 0.99 }}>{loading ? <><motion.span className="h-4 w-4 rounded-full border-2 border-white/30 border-t-white" animate={{ rotate: 360 }} transition={{ duration: 1, repeat: Infinity, ease: 'linear' }} />注册中…</> : <>创建账号并开始<ArrowRight className="h-4 w-4" /></>}</motion.button>
            </form>
            <p className="mt-6 text-center text-sm text-slate-400">已有账号？{' '}<Link to="/login" className="font-semibold text-primary-300 transition hover:text-primary-200">返回登录</Link></p>
          </div>
        </motion.section>
      </div>
    </main>
  );
}