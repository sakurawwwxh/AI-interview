import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { BarChart3, Gauge, Loader2, LockKeyhole, Save, ShieldCheck, UserRound } from 'lucide-react';
import { aiUsageApi, type AiUsageDTO } from '../api/aiUsage';
import { authApi } from '../api/auth';
import { getErrorMessage } from '../api/request';
import { useAuthStore } from '../stores/authStore';

function formatResetTime(seconds: number) {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return `${hours} 小时 ${minutes} 分钟后重置`;
}

export default function ProfilePage() {
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);
  const updateUser = useAuthStore((state) => state.updateUser);
  const logout = useAuthStore((state) => state.logout);
  const [usage, setUsage] = useState<AiUsageDTO | null>(null);
  const [loadingUsage, setLoadingUsage] = useState(true);
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [profileMessage, setProfileMessage] = useState<string | null>(null);
  const [passwordMessage, setPasswordMessage] = useState<string | null>(null);
  const [savingProfile, setSavingProfile] = useState(false);
  const [savingPassword, setSavingPassword] = useState(false);

  useEffect(() => {
    setDisplayName(user?.displayName || user?.username || '');
    setEmail(user?.email || '');
  }, [user]);

  useEffect(() => {
    aiUsageApi.getUsage()
      .then(setUsage)
      .catch(() => setUsage(null))
      .finally(() => setLoadingUsage(false));
  }, []);

  const usagePercent = usage && usage.dailyLimit > 0
    ? Math.min(100, Math.round((usage.dailyTokens / usage.dailyLimit) * 100))
    : 0;
  const roleLabel = user?.role === 'ADMIN' ? '管理员' : '普通用户';

  const submitProfile = async (event: FormEvent) => {
    event.preventDefault();
    setProfileMessage(null);
    setSavingProfile(true);
    try {
      const updated = await authApi.updateProfile(displayName.trim(), email.trim());
      updateUser(updated);
      setProfileMessage('资料已保存');
    } catch (error) {
      setProfileMessage(getErrorMessage(error));
    } finally {
      setSavingProfile(false);
    }
  };

  const submitPassword = async (event: FormEvent) => {
    event.preventDefault();
    setPasswordMessage(null);
    if (newPassword.length < 6 || newPassword.length > 32) {
      setPasswordMessage('新密码长度应为 6-32 个字符');
      return;
    }
    setSavingPassword(true);
    try {
      await authApi.changePassword(currentPassword, newPassword);
      logout();
      navigate('/login', { replace: true, state: { message: '密码已修改，请重新登录' } });
    } catch (error) {
      setPasswordMessage(getErrorMessage(error));
    } finally {
      setSavingPassword(false);
    }
  };

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div>
        <p className="text-sm font-medium text-primary-600 dark:text-primary-400">账户与成长</p>
        <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-800 dark:text-white">个人中心</h1>
        <p className="mt-2 text-slate-500 dark:text-slate-400">管理资料与登录安全，并查看 AI 使用情况和面试成长记录。</p>
      </div>

      <section className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-primary-500 to-indigo-500 text-white shadow-lg shadow-primary-500/20"><UserRound className="h-8 w-8" /></div>
          <div className="min-w-0 flex-1"><h2 className="truncate text-xl font-bold text-slate-800 dark:text-white">{user?.displayName || user?.username || '当前用户'}</h2><p className="mt-1 text-sm text-slate-500 dark:text-slate-400">登录名：{user?.username ?? '-'}</p></div>
          <div className="inline-flex items-center gap-2 self-start rounded-full bg-emerald-50 px-3 py-1.5 text-sm font-medium text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300 sm:self-auto"><ShieldCheck className="h-4 w-4" />{roleLabel}</div>
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <form onSubmit={submitProfile} className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
          <h2 className="font-semibold text-slate-800 dark:text-white">基本资料</h2>
          <div className="mt-5 space-y-4"><label className="block text-sm font-medium text-slate-600 dark:text-slate-300">显示名<input required maxLength={50} value={displayName} onChange={(event) => setDisplayName(event.target.value)} className="mt-1.5 w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-slate-800 outline-none focus:border-primary-500 dark:border-slate-600 dark:bg-slate-900 dark:text-white" /></label><label className="block text-sm font-medium text-slate-600 dark:text-slate-300">邮箱<input required type="email" value={email} onChange={(event) => setEmail(event.target.value)} className="mt-1.5 w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-slate-800 outline-none focus:border-primary-500 dark:border-slate-600 dark:bg-slate-900 dark:text-white" /></label></div>
          {profileMessage && <p className={`mt-4 text-sm ${profileMessage === '资料已保存' ? 'text-emerald-600' : 'text-red-500'}`}>{profileMessage}</p>}
          <button disabled={savingProfile} className="mt-5 inline-flex items-center gap-2 rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60"><Save className="h-4 w-4" />{savingProfile ? '保存中…' : '保存资料'}</button>
        </form>

        <form onSubmit={submitPassword} className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
          <div className="flex items-center gap-3"><div className="rounded-xl bg-amber-100 p-2.5 text-amber-600 dark:bg-amber-900/40 dark:text-amber-300"><LockKeyhole className="h-5 w-5" /></div><div><h2 className="font-semibold text-slate-800 dark:text-white">修改密码</h2><p className="text-sm text-slate-500 dark:text-slate-400">修改后会退出所有已登录设备。</p></div></div>
          <div className="mt-5 space-y-4"><label className="block text-sm font-medium text-slate-600 dark:text-slate-300">当前密码<input required type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} className="mt-1.5 w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-slate-800 outline-none focus:border-primary-500 dark:border-slate-600 dark:bg-slate-900 dark:text-white" /></label><label className="block text-sm font-medium text-slate-600 dark:text-slate-300">新密码<input required type="password" minLength={6} maxLength={32} autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} className="mt-1.5 w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-slate-800 outline-none focus:border-primary-500 dark:border-slate-600 dark:bg-slate-900 dark:text-white" /></label></div>
          {passwordMessage && <p className="mt-4 text-sm text-red-500">{passwordMessage}</p>}
          <button disabled={savingPassword} className="mt-5 inline-flex items-center gap-2 rounded-xl bg-slate-800 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60 dark:bg-slate-100 dark:text-slate-900"><LockKeyhole className="h-4 w-4" />{savingPassword ? '提交中…' : '修改密码'}</button>
        </form>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <section className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800"><div className="flex items-center gap-3"><div className="rounded-xl bg-primary-100 p-2.5 text-primary-600 dark:bg-primary-900/40 dark:text-primary-300"><Gauge className="h-5 w-5" /></div><div><h2 className="font-semibold text-slate-800 dark:text-white">今日 AI 用量</h2><p className="text-sm text-slate-500 dark:text-slate-400">按账号独立统计</p></div></div>{loadingUsage ? <div className="flex h-28 items-center justify-center"><Loader2 className="h-6 w-6 animate-spin text-primary-500" /></div> : usage ? <div className="mt-6"><div className="flex items-end justify-between gap-4"><span className="text-3xl font-bold text-slate-800 dark:text-white">{usagePercent}%</span><span className="text-sm text-slate-500 dark:text-slate-400">{usage.dailyTokens.toLocaleString()} / {usage.dailyLimit.toLocaleString()} tokens</span></div><div className="mt-3 h-3 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-700"><div className={`h-full rounded-full ${usagePercent >= 100 ? 'bg-red-500' : usagePercent >= 80 ? 'bg-amber-500' : 'bg-primary-500'}`} style={{ width: `${usagePercent}%` }} /></div><p className="mt-3 text-sm text-slate-500 dark:text-slate-400">{formatResetTime(usage.secondsUntilReset)} · 今日 {usage.dailyRequestCount} 次请求</p></div> : <p className="mt-6 text-sm text-slate-500 dark:text-slate-400">暂时无法读取用量数据。</p>}</section>
        <Link to="/interview-statistics" className="group rounded-2xl border border-slate-100 bg-white p-6 shadow-sm transition hover:-translate-y-0.5 hover:border-primary-200 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-primary-700"><div className="flex items-center gap-3"><div className="rounded-xl bg-indigo-100 p-2.5 text-indigo-600 dark:bg-indigo-900/40 dark:text-indigo-300"><BarChart3 className="h-5 w-5" /></div><div><h2 className="font-semibold text-slate-800 dark:text-white">成长档案</h2><p className="text-sm text-slate-500 dark:text-slate-400">来自已评分面试的能力画像</p></div></div><p className="mt-6 text-sm leading-6 text-slate-600 dark:text-slate-300">查看能力雷达、分数趋势和优先补强方向。统计数据仍由面试模块计算，确保指标与面试记录一致。</p><p className="mt-5 text-sm font-semibold text-primary-600 transition group-hover:translate-x-1 dark:text-primary-400">查看能力统计 →</p></Link>
      </div>
    </div>
  );
}
