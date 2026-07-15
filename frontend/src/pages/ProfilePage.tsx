import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ArrowRight,
  BarChart3,
  Gauge,
  Loader2,
  LockKeyhole,
  Save,
  ShieldCheck,
  Sparkles,
  UserRound,
} from 'lucide-react';
import { aiUsageApi, type AiUsageDTO } from '../api/aiUsage';
import { authApi } from '../api/auth';
import { growthApi, type GrowthPlan } from '../api/growth';
import { practiceApi, type PracticeSummary } from '../api/practice';
import { getErrorMessage } from '../api/request';
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

const inputClassName = 'mt-1.5 w-full rounded-md border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-primary-500 focus:ring-4 focus:ring-primary-500/10 dark:border-white/[0.12] dark:bg-[#17191d] dark:text-white';
const cardClassName = 'rounded-lg border border-slate-200/80 bg-white dark:border-white/[0.08] dark:bg-[#202328]';

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
    aiUsageApi.getUsage().then(setUsage).catch(() => setUsage(null)).finally(() => setLoadingUsage(false));
    practiceApi.getSummary().then(setPracticeSummary).catch(() => setPracticeSummary(null));
    growthApi.getPlan().then(setGrowthPlan).catch(() => setGrowthPlan(null));
    userAiConfigApi.get().then((config) => {
      setAiConfig(config);
      if (!config) return;
      setAiProvider(config.provider);
      setAiBaseUrl(config.baseUrl);
      setAiModel(config.model);
      setAiFallback(config.fallbackToPlatform);
    }).catch(() => {});
  }, []);

  const usagePercent = usage && usage.dailyLimit > 0
    ? Math.min(100, Math.round((usage.dailyTokens / usage.dailyLimit) * 100))
    : 0;
  const roleLabel = user?.role === 'ADMIN' ? '管理员' : '普通用户';
  const currentProviderTemplate = AI_PROVIDER_TEMPLATES.find((item) => item.name === aiProvider);
  const isKnownProvider = AI_PROVIDER_TEMPLATES.some((item) => item.name === aiProvider);

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
      setPasswordMessage('新密码长度应为 6–32 个字符');
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

  const aiPayload = () => ({
    provider: aiProvider,
    baseUrl: aiBaseUrl.trim(),
    model: aiModel.trim(),
    apiKey: aiKey.trim(),
    fallbackToPlatform: aiFallback,
  });

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

  const testAi = async () => {
    if (!aiKey.trim()) {
      setAiMessage('请输入密钥后再测试连接');
      return;
    }
    setTestingAi(true);
    setAiMessage(null);
    try {
      await userAiConfigApi.test(aiPayload());
      setAiMessage('连接成功，模型可用');
    } catch (error) {
      setAiMessage(getErrorMessage(error));
    } finally {
      setTestingAi(false);
    }
  };

  const saveAi = async () => {
    if (!aiKey.trim()) {
      setAiMessage('为保护密钥，更新配置时请重新输入密钥');
      return;
    }
    setSavingAi(true);
    setAiMessage(null);
    try {
      const saved = await userAiConfigApi.save(aiPayload());
      setAiConfig(saved);
      setAiKey('');
      setAiMessage('已加密保存，将优先使用你的模型');
    } catch (error) {
      setAiMessage(getErrorMessage(error));
    } finally {
      setSavingAi(false);
    }
  };

  const deleteAi = async () => {
    try {
      await userAiConfigApi.remove();
      setAiConfig(null);
      setAiKey('');
      setAiMessage('已移除个人模型配置，将使用平台默认模型');
    } catch (error) {
      setAiMessage(getErrorMessage(error));
    }
  };

  return (
    <div className="mx-auto max-w-6xl space-y-6 pb-8">
      <section className="relative border-y border-white/[0.08] px-0 py-8 text-white">
        <div className="hidden" />
        <div className="hidden" />
        <div className="relative flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex min-w-0 items-center gap-4">
            <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-2xl bg-white/15 text-white ring-1 ring-white/20">
              <UserRound className="h-8 w-8" />
            </div>
            <div className="min-w-0">
              <p className="text-sm font-medium text-primary-200">账户与成长</p>
              <h1 className="mt-1 truncate text-2xl font-bold tracking-tight sm:text-3xl">你好，{user?.displayName || user?.username || '同学'}</h1>
              <p className="mt-2 text-sm text-slate-300">在这里管理账户、AI 模型与本周训练节奏。</p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <span className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-2 text-sm text-slate-100 ring-1 ring-white/15"><ShieldCheck className="h-4 w-4 text-emerald-300" />{roleLabel}</span>
            <Link to="/interview-statistics" className="inline-flex items-center gap-2 rounded-xl bg-white px-4 py-2.5 text-sm font-semibold text-slate-900 transition hover:bg-primary-50">成长档案<ArrowRight className="h-4 w-4" /></Link>
          </div>
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-3">
        <div className={`${cardClassName} p-5`}>
          <div className="flex items-center justify-between"><span className="text-sm font-medium text-slate-500 dark:text-slate-400">今日 AI 用量</span><span className="rounded-lg bg-primary-50 p-2 text-primary-600 dark:bg-primary-900/30 dark:text-primary-300"><Gauge className="h-4 w-4" /></span></div>
          {loadingUsage ? <div className="flex h-16 items-center"><Loader2 className="h-5 w-5 animate-spin text-primary-500" /></div> : usage ? <><div className="mt-5 flex items-end justify-between"><span className="text-3xl font-bold text-slate-800 dark:text-white">{usagePercent}%</span><span className="text-xs text-slate-500">{usage.dailyRequestCount} 次请求</span></div><div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-700"><div className={`h-full rounded-full ${usagePercent >= 100 ? 'bg-red-500' : usagePercent >= 80 ? 'bg-amber-500' : 'bg-primary-500'}`} style={{ width: `${usagePercent}%` }} /></div><p className="mt-3 text-xs text-slate-500">{usage.dailyTokens.toLocaleString()} / {usage.dailyLimit.toLocaleString()} tokens · {formatResetTime(usage.secondsUntilReset)}</p></> : <p className="mt-5 text-sm text-slate-500">暂时无法读取用量数据。</p>}
        </div>
        <Link to="/practice" className={`${cardClassName} group p-5 transition hover:-translate-y-0.5 hover:border-amber-200 hover:shadow-md dark:hover:border-amber-800`}>
          <div className="flex items-center justify-between"><span className="text-sm font-medium text-slate-500 dark:text-slate-400">错题复练</span><span className="rounded-lg bg-amber-50 p-2 text-amber-600 dark:bg-amber-900/30 dark:text-amber-300"><Sparkles className="h-4 w-4" /></span></div>
          <div className="mt-5 flex items-end justify-between"><div><span className="text-3xl font-bold text-slate-800 dark:text-white">{practiceSummary?.todoCount ?? '–'}</span><p className="mt-1 text-xs text-slate-500">待复练题目</p></div><ArrowRight className="h-5 w-5 text-slate-400 transition group-hover:translate-x-1 group-hover:text-amber-500" /></div>
          <p className="mt-3 text-xs text-slate-500">{practiceSummary?.averageImprovement != null ? `平均提升 ${practiceSummary.averageImprovement >= 0 ? '+' : ''}${practiceSummary.averageImprovement} 分` : '从低分题开始建立复练闭环'}</p>
        </Link>
        <Link to="/interview-statistics" className={`${cardClassName} group p-5 transition hover:-translate-y-0.5 hover:border-primary-200 hover:shadow-md dark:hover:border-primary-800`}>
          <div className="flex items-center justify-between"><span className="text-sm font-medium text-slate-500 dark:text-slate-400">能力画像</span><span className="rounded-lg bg-primary-50 p-2 text-primary-600 dark:bg-primary-900/30 dark:text-primary-300"><BarChart3 className="h-4 w-4" /></span></div>
          <div className="mt-5 flex items-end justify-between"><div><p className="text-lg font-bold text-slate-800 dark:text-white">查看成长趋势</p><p className="mt-1 text-xs text-slate-500">能力雷达、分数趋势与补强建议</p></div><ArrowRight className="h-5 w-5 text-slate-400 transition group-hover:translate-x-1 group-hover:text-primary-500" /></div>
        </Link>
      </section>

      {growthPlan && <section className="border-y border-primary-100 bg-transparent py-6 dark:border-white/[0.08]"><div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between"><div><p className="text-sm font-semibold text-primary-600 dark:text-primary-300">本周训练计划</p><h2 className="mt-1 text-xl font-bold text-slate-800 dark:text-white">{growthPlan.headline}</h2><p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{growthPlan.targetRole ? `目标岗位：${growthPlan.targetRole}` : '设置目标岗位后，可获得更有针对性的训练。'}</p></div><Link to="/practice" className="inline-flex w-fit items-center gap-2 rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-primary-700">开始复练<ArrowRight className="h-4 w-4" /></Link></div><div className="mt-5 grid gap-3 md:grid-cols-3">{growthPlan.actions.map((action, index) => <Link key={`${action.title}-${index}`} to={action.link} className="border-b border-slate-200 py-4 transition hover:bg-slate-50 dark:border-white/[0.08] dark:hover:bg-white/[0.03]"><div className="flex items-center justify-between gap-2"><span className={`text-xs font-semibold ${action.priority === 'HIGH' ? 'text-red-500' : 'text-amber-600'}`}>{action.priority === 'HIGH' ? '优先处理' : '建议完成'}</span>{action.score != null && <span className="text-sm font-bold text-primary-600">{action.score} 分</span>}</div><h3 className="mt-2 font-semibold text-slate-800 dark:text-white">{index + 1}. {action.title}</h3><p className="mt-1 text-sm leading-5 text-slate-500 dark:text-slate-400">{action.description}</p></Link>)}</div></section>}

      <section className="grid gap-6 xl:grid-cols-12">
        <div className="space-y-6 xl:col-span-5">
          <form onSubmit={submitProfile} className={`${cardClassName} p-6`}>
            <div className="flex items-center gap-3"><span className="rounded-xl bg-primary-50 p-2.5 text-primary-600 dark:bg-primary-900/30 dark:text-primary-300"><UserRound className="h-5 w-5" /></span><div><h2 className="font-semibold text-slate-800 dark:text-white">基本资料</h2><p className="text-sm text-slate-500">登录名不可修改，昵称可随时更新。</p></div></div>
            <div className="mt-5 space-y-4"><label className="block text-sm font-medium text-slate-700 dark:text-slate-200">显示名称<input required maxLength={50} value={displayName} onChange={(event) => setDisplayName(event.target.value)} className={inputClassName} /></label><label className="block text-sm font-medium text-slate-700 dark:text-slate-200">邮箱<input required type="email" value={email} onChange={(event) => setEmail(event.target.value)} className={inputClassName} /></label></div>
            {profileMessage && <p className={`mt-4 text-sm ${profileMessage === '资料已保存' ? 'text-emerald-600' : 'text-red-500'}`}>{profileMessage}</p>}
            <button disabled={savingProfile} className="mt-5 inline-flex items-center gap-2 rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-60"><Save className="h-4 w-4" />{savingProfile ? '保存中…' : '保存资料'}</button>
          </form>

          <form onSubmit={submitPassword} className={`${cardClassName} p-6`}>
            <div className="flex items-start gap-3"><span className="rounded-xl bg-amber-50 p-2.5 text-amber-600 dark:bg-amber-900/30 dark:text-amber-300"><LockKeyhole className="h-5 w-5" /></span><div><h2 className="font-semibold text-slate-800 dark:text-white">账户安全</h2><p className="text-sm text-slate-500">修改密码后会退出所有已登录设备。</p></div></div>
            <div className="mt-5 space-y-4"><label className="block text-sm font-medium text-slate-700 dark:text-slate-200">当前密码<input required type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} className={inputClassName} /></label><label className="block text-sm font-medium text-slate-700 dark:text-slate-200">新密码<input required type="password" minLength={6} maxLength={32} autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} className={inputClassName} /></label></div>
            {passwordMessage && <p className="mt-4 text-sm text-red-500">{passwordMessage}</p>}
            <button disabled={savingPassword} className="mt-5 inline-flex items-center gap-2 rounded-xl bg-slate-800 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"><LockKeyhole className="h-4 w-4" />{savingPassword ? '提交中…' : '修改密码'}</button>
          </form>
        </div>

        <section className={`${cardClassName} p-6 xl:col-span-7`}>
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between"><div className="flex items-start gap-3"><span className="rounded-xl bg-violet-50 p-2.5 text-violet-600 dark:bg-violet-900/30 dark:text-violet-300"><Sparkles className="h-5 w-5" /></span><div><h2 className="font-semibold text-slate-800 dark:text-white">我的 AI 模型</h2><p className="mt-1 max-w-xl text-sm leading-6 text-slate-500">选择厂商后自动填入兼容地址；模型名称和 API Key 由你填写。密钥仅加密保存且不会回显。</p></div></div>{aiConfig && <button type="button" onClick={deleteAi} className="w-fit text-sm font-medium text-red-500 transition hover:text-red-600">移除配置</button>}</div>

          <div className="mt-6 grid gap-4 md:grid-cols-2"><label className="block text-sm font-medium text-slate-700 dark:text-slate-200">厂商<select value={isKnownProvider ? aiProvider : 'CUSTOM'} onChange={(event) => selectAiProvider(event.target.value)} className={inputClassName}><option value="CUSTOM">自定义 OpenAI 兼容服务</option>{AI_PROVIDER_TEMPLATES.map((item) => <option key={item.name} value={item.name}>{item.name}</option>)}</select></label><label className="block text-sm font-medium text-slate-700 dark:text-slate-200">消息协议<select value={aiProtocol} onChange={(event) => setAiProtocol(event.target.value)} className={inputClassName}><option value={OPENAI_CHAT_PROTOCOL}>OpenAI 兼容 · Chat Completions</option><option disabled value="ANTHROPIC_MESSAGES">Anthropic · Messages（暂未接入）</option><option disabled value="GEMINI_GENERATE_CONTENT">Gemini · generateContent（暂未接入）</option></select></label><label className="block text-sm font-medium text-slate-700 dark:text-slate-200">模型名称<input required value={aiModel} onChange={(event) => setAiModel(event.target.value)} maxLength={160} placeholder={currentProviderTemplate?.modelHint ?? '例如 your-model-name'} className={inputClassName} /></label><label className="block text-sm font-medium text-slate-700 dark:text-slate-200">Base URL<input required value={aiBaseUrl} onChange={(event) => setAiBaseUrl(event.target.value)} maxLength={500} placeholder="OpenAI 兼容 Base URL" className={inputClassName} /></label></div>

          <div className="mt-4 rounded-xl bg-slate-50 p-4 dark:bg-slate-900/70"><label className="flex cursor-pointer items-start gap-3 text-sm text-slate-600 dark:text-slate-300"><input type="checkbox" checked={aiFallback} onChange={(event) => setAiFallback(event.target.checked)} className="mt-0.5 h-4 w-4 rounded border-slate-300 text-primary-600 focus:ring-primary-500" /><span><span className="font-medium text-slate-700 dark:text-slate-200">失败时回退平台模型</span><span className="mt-0.5 block text-xs leading-5 text-slate-500">只有个人服务在重试后仍不可用时才会回退，不会覆盖你的日常选择。</span></span></label></div>

          <label className="mt-4 block text-sm font-medium text-slate-700 dark:text-slate-200">API Key<input type="password" value={aiKey} onChange={(event) => setAiKey(event.target.value)} autoComplete="off" placeholder={aiConfig ? `已保存 ${aiConfig.apiKeyMasked}；输入新密钥以更新` : '输入你的 API Key'} className={inputClassName} /></label>
          {aiMessage && <p className={`mt-4 text-sm ${aiMessage.includes('成功') || aiMessage.includes('保存') || aiMessage.includes('移除') ? 'text-emerald-600' : 'text-red-500'}`}>{aiMessage}</p>}
          <div className="mt-5 flex flex-wrap gap-3"><button type="button" disabled={testingAi} onClick={testAi} className="rounded-xl border border-primary-200 px-4 py-2.5 text-sm font-semibold text-primary-700 transition hover:bg-primary-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-primary-800 dark:text-primary-300 dark:hover:bg-primary-900/20">{testingAi ? '测试中…' : '测试连接'}</button><button type="button" disabled={savingAi} onClick={saveAi} className="inline-flex items-center gap-2 rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-60"><Save className="h-4 w-4" />{savingAi ? '保存中…' : '加密保存并启用'}</button></div>
        </section>
      </section>
    </div>
  );
}
