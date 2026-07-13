import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { BarChart3, Gauge, Loader2, LockKeyhole, Save, ShieldCheck, UserRound } from 'lucide-react';
import { aiUsageApi, type AiUsageDTO } from '../api/aiUsage';
import { authApi } from '../api/auth';
import { getErrorMessage } from '../api/request';
import { practiceApi, type PracticeSummary } from '../api/practice';
import { growthApi, type GrowthPlan } from '../api/growth';
import { userAiConfigApi, type UserAiConfig } from '../api/userAiConfig';
import { useAuthStore } from '../stores/authStore';

const OPENAI_CHAT_PROTOCOL = 'OPENAI_CHAT_COMPLETIONS';

const AI_PROVIDER_TEMPLATES = [
  { name: 'DashScope（阿里云百炼）', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', modelHint: '例如 qwen-plus' },
  { name: 'DeepSeek', baseUrl: 'https://api.deepseek.com', modelHint: '例如 deepseek-chat' },
  { name: 'OpenAI', baseUrl: 'https://api.openai.com/v1', modelHint: '例如 gpt-4.1-mini' },
  { name: 'OpenRouter', baseUrl: 'https://openrouter.ai/api/v1', modelHint: '例如 openai/gpt-4.1-mini' },
  { name: '智谱 AI', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', modelHint: '例如 glm-4-flash' },
  { name: '硅基流动', baseUrl: 'https://api.siliconflow.cn/v1', modelHint: '例如 Qwen/Qwen3-8B' },
  { name: 'Ollama（本地）', baseUrl: 'http://localhost:11434/v1', modelHint: '例如 qwen3:8b' },
] as const;

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
  const [practiceSummary, setPracticeSummary] = useState<PracticeSummary | null>(null);
  const [growthPlan, setGrowthPlan] = useState<GrowthPlan | null>(null);
  const [aiConfig, setAiConfig] = useState<UserAiConfig | null>(null);
  const [aiProvider, setAiProvider] = useState<string>(AI_PROVIDER_TEMPLATES[0].name);
  const [aiBaseUrl, setAiBaseUrl] = useState<string>(AI_PROVIDER_TEMPLATES[0].baseUrl);
  const [aiModel, setAiModel] = useState('');
  const [aiProtocol, setAiProtocol] = useState(OPENAI_CHAT_PROTOCOL);
  const [aiKey, setAiKey] = useState('');
  const [aiFallback, setAiFallback] = useState(true);
  const [aiMessage, setAiMessage] = useState<string | null>(null);
  const [testingAi, setTestingAi] = useState(false);
  const [savingAi, setSavingAi] = useState(false);
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

  useEffect(() => {
    practiceApi.getSummary().then(setPracticeSummary).catch(() => setPracticeSummary(null));
  }, []);

  useEffect(() => {
    growthApi.getPlan().then(setGrowthPlan).catch(() => setGrowthPlan(null));
  }, []);

  useEffect(() => { userAiConfigApi.get().then(config => { setAiConfig(config); if (config) { setAiProvider(config.provider); setAiBaseUrl(config.baseUrl); setAiModel(config.model); setAiFallback(config.fallbackToPlatform); } }).catch(() => {}); }, []);

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

  const aiPayload = () => ({ provider: aiProvider, baseUrl: aiBaseUrl.trim(), model: aiModel.trim(), apiKey: aiKey.trim(), fallbackToPlatform: aiFallback });
  const currentProviderTemplate = AI_PROVIDER_TEMPLATES.find((item) => item.name === aiProvider);
  const selectAiProvider = (provider: string) => {
    if (provider === 'CUSTOM') {
      setAiProvider('自定义 OpenAI 兼容服务');
      setAiBaseUrl('');
      setAiModel('');
      return;
    }
    const template = AI_PROVIDER_TEMPLATES.find((item) => item.name === provider);
    if (!template) return;
    setAiProvider(template.name);
    setAiBaseUrl(template.baseUrl);
    setAiModel('');
    setAiProtocol(OPENAI_CHAT_PROTOCOL);
  };
  const testAi = async () => { if (!aiKey.trim()) { setAiMessage('请输入密钥后再测试连接'); return; } setTestingAi(true); setAiMessage(null); try { await userAiConfigApi.test(aiPayload()); setAiMessage('连接成功，模型可用'); } catch (error) { setAiMessage(getErrorMessage(error)); } finally { setTestingAi(false); } };
  const saveAi = async () => { if (!aiKey.trim()) { setAiMessage('为保护密钥，更新配置时请重新输入密钥'); return; } setSavingAi(true); setAiMessage(null); try { const saved = await userAiConfigApi.save(aiPayload()); setAiConfig(saved); setAiKey(''); setAiMessage('已加密保存，将优先使用你的模型'); } catch (error) { setAiMessage(getErrorMessage(error)); } finally { setSavingAi(false); } };
  const deleteAi = async () => { await userAiConfigApi.remove(); setAiConfig(null); setAiKey(''); setAiMessage('已移除个人模型配置，将使用平台默认模型'); };

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

      {growthPlan && <section className="rounded-2xl border border-primary-100 bg-gradient-to-br from-primary-50 to-indigo-50 p-6 dark:border-primary-900/50 dark:from-primary-950/30 dark:to-indigo-950/20"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-sm font-semibold text-primary-600">本周训练计划</p><h2 className="mt-1 text-xl font-bold text-slate-800 dark:text-white">{growthPlan.headline}</h2><p className="mt-1 text-sm text-slate-500">{growthPlan.targetRole ? `目标岗位：${growthPlan.targetRole}` : '设置目标岗位后，可获得更有针对性的训练。'}</p></div><Link to="/practice" className="rounded-xl bg-primary-600 px-4 py-2 text-sm font-semibold text-white">进入复练</Link></div><div className="mt-5 grid gap-3 md:grid-cols-3">{growthPlan.actions.map((action, index) => <Link key={`${action.title}-${index}`} to={action.link} className="rounded-xl bg-white/90 p-4 shadow-sm transition hover:-translate-y-0.5 dark:bg-slate-800/90"><div className="flex items-center justify-between gap-2"><span className={`text-xs font-semibold ${action.priority === 'HIGH' ? 'text-red-500' : 'text-amber-600'}`}>{action.priority === 'HIGH' ? '优先处理' : '建议完成'}</span>{action.score != null && <span className="text-sm font-bold text-primary-600">{action.score} 分</span>}</div><h3 className="mt-2 font-semibold text-slate-800 dark:text-white">{index + 1}. {action.title}</h3><p className="mt-1 text-sm leading-5 text-slate-500 dark:text-slate-400">{action.description}</p></Link>)}</div></section>}

      <section className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
        <div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="font-semibold text-slate-800 dark:text-white">我的 AI 模型</h2><p className="mt-1 text-sm text-slate-500">选择厂商后会自动填入兼容地址；模型名称和 API Key 由你填写，密钥仅加密保存且不会回显。</p></div>{aiConfig && <button onClick={deleteAi} className="text-sm text-red-500">移除配置</button>}</div>
        <div className="mt-5 grid gap-3 md:grid-cols-2"><label className="text-sm text-slate-600 dark:text-slate-300">厂商<select value={AI_PROVIDER_TEMPLATES.some((item) => item.name === aiProvider) ? aiProvider : 'CUSTOM'} onChange={event => selectAiProvider(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2.5 dark:border-slate-600 dark:bg-slate-900"><option value="CUSTOM">自定义 OpenAI 兼容服务</option>{AI_PROVIDER_TEMPLATES.map((item) => <option key={item.name} value={item.name}>{item.name}</option>)}</select></label><label className="text-sm text-slate-600 dark:text-slate-300">消息协议<select value={aiProtocol} onChange={event => setAiProtocol(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2.5 dark:border-slate-600 dark:bg-slate-900"><option value={OPENAI_CHAT_PROTOCOL}>OpenAI 兼容 · Chat Completions</option><option disabled value="ANTHROPIC_MESSAGES">Anthropic · Messages（暂未接入）</option><option disabled value="GEMINI_GENERATE_CONTENT">Gemini · generateContent（暂未接入）</option></select></label><label className="text-sm text-slate-600 dark:text-slate-300">模型名称<input required value={aiModel} onChange={event => setAiModel(event.target.value)} maxLength={160} placeholder={currentProviderTemplate?.modelHint ?? '例如 your-model-name'} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2.5 dark:border-slate-600 dark:bg-slate-900" /></label><label className="text-sm text-slate-600 dark:text-slate-300">Base URL<input required value={aiBaseUrl} onChange={event => setAiBaseUrl(event.target.value)} maxLength={500} placeholder="OpenAI 兼容 Base URL" className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2.5 dark:border-slate-600 dark:bg-slate-900" /></label></div>
        <div className="mt-3 flex flex-wrap items-center gap-3"><input type="password" value={aiKey} onChange={event => setAiKey(event.target.value)} autoComplete="off" placeholder={aiConfig ? `已保存 ${aiConfig.apiKeyMasked}；输入新密钥以更新` : '输入你的 API Key'} className="min-w-72 flex-1 rounded-xl border border-slate-200 px-3 py-2.5 dark:border-slate-600 dark:bg-slate-900" /><label className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300"><input type="checkbox" checked={aiFallback} onChange={event => setAiFallback(event.target.checked)} />失败时回退平台模型</label></div>
        {aiMessage && <p className={`mt-3 text-sm ${aiMessage.includes('成功') || aiMessage.includes('保存') ? 'text-emerald-600' : 'text-red-500'}`}>{aiMessage}</p>}<div className="mt-4 flex gap-3"><button disabled={testingAi} onClick={testAi} className="rounded-xl border border-primary-300 px-4 py-2 text-sm font-semibold text-primary-600 disabled:opacity-60">{testingAi ? '测试中…' : '测试连接'}</button><button disabled={savingAi} onClick={saveAi} className="rounded-xl bg-primary-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60">{savingAi ? '保存中…' : '加密保存并启用'}</button></div>
      </section>

      <div className="grid gap-6 md:grid-cols-3">
        <section className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800"><div className="flex items-center gap-3"><div className="rounded-xl bg-primary-100 p-2.5 text-primary-600 dark:bg-primary-900/40 dark:text-primary-300"><Gauge className="h-5 w-5" /></div><div><h2 className="font-semibold text-slate-800 dark:text-white">今日 AI 用量</h2><p className="text-sm text-slate-500 dark:text-slate-400">按账号独立统计</p></div></div>{loadingUsage ? <div className="flex h-28 items-center justify-center"><Loader2 className="h-6 w-6 animate-spin text-primary-500" /></div> : usage ? <div className="mt-6"><div className="flex items-end justify-between gap-4"><span className="text-3xl font-bold text-slate-800 dark:text-white">{usagePercent}%</span><span className="text-sm text-slate-500 dark:text-slate-400">{usage.dailyTokens.toLocaleString()} / {usage.dailyLimit.toLocaleString()} tokens</span></div><div className="mt-3 h-3 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-700"><div className={`h-full rounded-full ${usagePercent >= 100 ? 'bg-red-500' : usagePercent >= 80 ? 'bg-amber-500' : 'bg-primary-500'}`} style={{ width: `${usagePercent}%` }} /></div><p className="mt-3 text-sm text-slate-500 dark:text-slate-400">{formatResetTime(usage.secondsUntilReset)} · 今日 {usage.dailyRequestCount} 次请求</p></div> : <p className="mt-6 text-sm text-slate-500 dark:text-slate-400">暂时无法读取用量数据。</p>}</section>
        <Link to="/interview-statistics" className="group rounded-2xl border border-slate-100 bg-white p-6 shadow-sm transition hover:-translate-y-0.5 hover:border-primary-200 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-primary-700"><div className="flex items-center gap-3"><div className="rounded-xl bg-indigo-100 p-2.5 text-indigo-600 dark:bg-indigo-900/40 dark:text-indigo-300"><BarChart3 className="h-5 w-5" /></div><div><h2 className="font-semibold text-slate-800 dark:text-white">成长档案</h2><p className="text-sm text-slate-500 dark:text-slate-400">来自已评分面试的能力画像</p></div></div><p className="mt-6 text-sm leading-6 text-slate-600 dark:text-slate-300">查看能力雷达、分数趋势和优先补强方向。统计数据仍由面试模块计算，确保指标与面试记录一致。</p><p className="mt-5 text-sm font-semibold text-primary-600 transition group-hover:translate-x-1 dark:text-primary-400">查看能力统计 →</p></Link>
        <Link to="/practice" className="group rounded-2xl border border-slate-100 bg-white p-6 shadow-sm transition hover:-translate-y-0.5 hover:border-primary-200 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-primary-700"><div className="flex items-center gap-3"><div className="rounded-xl bg-amber-100 p-2.5 text-amber-600 dark:bg-amber-900/40 dark:text-amber-300"><BarChart3 className="h-5 w-5" /></div><div><h2 className="font-semibold text-slate-800 dark:text-white">错题复练</h2><p className="text-sm text-slate-500 dark:text-slate-400">把低分题练成掌握点</p></div></div><div className="mt-6 flex items-end justify-between"><div><p className="text-3xl font-bold text-slate-800 dark:text-white">{practiceSummary?.todoCount ?? '-'}</p><p className="text-sm text-slate-500">待复练题目</p></div><p className="text-sm font-semibold text-primary-600 transition group-hover:translate-x-1">开始复练 →</p></div>{practiceSummary?.averageImprovement != null && <p className="mt-3 text-sm text-emerald-600">平均提升 {practiceSummary.averageImprovement >= 0 ? '+' : ''}{practiceSummary.averageImprovement} 分</p>}</Link>
      </div>
    </div>
  );
}
