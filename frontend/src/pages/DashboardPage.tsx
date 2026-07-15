import { Link } from 'react-router-dom';
import { ArrowRight, BriefcaseBusiness, Check, CircleDotDashed, FileText, Sparkles, Target } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { growthApi, type GrowthPlan } from '../api/growth';
import { practiceApi, type PracticeSummary } from '../api/practice';
import { useAuthStore } from '../stores/authStore';

type Step = { order: string; title: string; detail: string; status: 'done' | 'current' | 'upcoming'; link: string };

export default function DashboardPage() {
  const user = useAuthStore((state) => state.user);
  const [growthPlan, setGrowthPlan] = useState<GrowthPlan | null>(null);
  const [practiceSummary, setPracticeSummary] = useState<PracticeSummary | null>(null);
  useEffect(() => { growthApi.getPlan().then(setGrowthPlan).catch(() => setGrowthPlan(null)); practiceApi.getSummary().then(setPracticeSummary).catch(() => setPracticeSummary(null)); }, []);

  const steps = useMemo<Step[]>(() => {
    const firstAction = growthPlan?.actions?.[0];
    return [
      { order: '01', title: '确认目标岗位', detail: growthPlan?.targetRole ? `当前聚焦：${growthPlan.targetRole}` : '添加一个岗位目标，让训练更有方向。', status: growthPlan?.targetRole ? 'done' : 'current', link: '/job-targets' },
      { order: '02', title: firstAction?.title || '完成一次针对性练习', detail: firstAction?.description || '从一场模拟面试或一组专项复练开始。', status: growthPlan?.targetRole ? 'current' : 'upcoming', link: firstAction?.link || '/practice' },
      { order: '03', title: '复盘并沉淀能力画像', detail: practiceSummary?.todoCount ? `还有 ${practiceSummary.todoCount} 道待复练题，完成后更新能力趋势。` : '每一次评分都会成为下一次训练的依据。', status: 'upcoming', link: '/interview-statistics' },
    ];
  }, [growthPlan, practiceSummary]);

  const currentStep = steps.find((step) => step.status === 'current') || steps[1];
  const hasPractice = (practiceSummary?.todoCount ?? 0) > 0;
  const greeting = user?.displayName || user?.username || '同学';
  const muted = 'text-[var(--shell-muted)]';

  return <div className="mx-auto max-w-[1280px]">
    <header className="flex flex-col gap-5 border-b border-[var(--shell-border)] pb-6 sm:flex-row sm:items-center sm:justify-between">
      <div className={`flex items-center gap-3 text-sm ${muted}`}><span>当前目标岗位</span><span className="font-medium text-[var(--shell-text)]">{growthPlan?.targetRole || '尚未设置'}</span><span className="hidden sm:inline">/</span><span className="hidden sm:inline">{greeting} 的训练空间</span></div>
      <Link to="/job-targets" className="inline-flex w-fit items-center gap-1.5 rounded-md border border-[var(--shell-border)] px-3 py-1.5 text-xs text-[var(--shell-muted)] transition hover:bg-[var(--shell-hover)] hover:text-[var(--shell-text)]">调整方向 <ArrowRight className="h-3.5 w-3.5" /></Link>
    </header>

    <div className="grid gap-0 xl:grid-cols-[minmax(0,1fr)_21rem]">
      <section className="border-b border-[var(--shell-border)] py-12 xl:border-b-0 xl:border-r xl:pr-14">
        <p className="text-sm font-medium text-blue-600 dark:text-blue-400">今日训练</p>
        <h1 className="mt-4 max-w-3xl text-[2.25rem] font-semibold tracking-[-0.055em] text-[var(--shell-text)] sm:text-5xl">今天，专注准备一件事。</h1>
        <p className={`mt-5 max-w-2xl text-[15px] leading-7 ${muted}`}>不必一次做完所有准备。让目标岗位、每一次回答和每一次复盘，形成清晰的前进路径。</p>
        <div className="mt-12 border-y border-[var(--shell-border)]">
          {steps.map((step) => <Link key={step.order} to={step.link} className="group grid grid-cols-[3.5rem_minmax(0,1fr)_auto] gap-3 border-b border-[var(--shell-border)] py-5 last:border-b-0 sm:grid-cols-[5rem_minmax(0,1fr)_auto] sm:gap-6">
            <span className={`pt-0.5 text-sm tabular-nums ${step.status === 'current' ? 'text-blue-600 dark:text-blue-400' : 'text-[var(--shell-subtle)]'}`}>{step.order}</span>
            <div><div className="flex items-center gap-2"><h2 className="text-base font-medium text-[var(--shell-text)]">{step.title}</h2>{step.status === 'done' && <Check className="h-4 w-4 text-emerald-500" />}{step.status === 'current' && <CircleDotDashed className="h-4 w-4 text-blue-500" />}</div><p className={`mt-1.5 max-w-xl text-sm leading-6 ${muted}`}>{step.detail}</p></div>
            <ArrowRight className="mt-1 h-4 w-4 text-[var(--shell-subtle)] transition group-hover:translate-x-1 group-hover:text-blue-500" />
          </Link>)}
        </div>
        <div className="mt-9 flex flex-wrap items-center gap-4"><Link to={currentStep.link} className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-500">{currentStep.title} <ArrowRight className="h-4 w-4" /></Link><Link to="/upload" className={`inline-flex items-center gap-2 px-1 text-sm transition hover:text-[var(--shell-text)] ${muted}`}><FileText className="h-4 w-4" /> 管理简历</Link></div>
      </section>

      <aside className="py-10 xl:pl-10 xl:pt-12">
        <div className="border-b border-[var(--shell-border)] pb-8"><p className="text-xs font-medium uppercase tracking-[0.16em] text-[var(--shell-subtle)]">AI 教练随笔</p><blockquote className="mt-5 text-xl font-medium leading-9 tracking-[-0.025em] text-[var(--shell-text)]">{growthPlan?.headline || '先把一个答案讲清楚，再去追求更多答案。'}</blockquote><p className={`mt-4 text-sm leading-7 ${muted}`}>{growthPlan?.actions?.[0]?.description || '开始训练后，系统会把你的真实表现转化为可执行的下一步。'}</p></div>
        <div className="border-b border-[var(--shell-border)] py-8"><p className="text-xs font-medium uppercase tracking-[0.16em] text-[var(--shell-subtle)]">本次聚焦</p><div className="mt-4 flex items-start gap-3"><span className="mt-0.5 rounded-md border border-blue-500/20 bg-blue-500/10 p-2 text-blue-600 dark:text-blue-300"><Target className="h-4 w-4" /></span><div><p className="text-sm font-medium text-[var(--shell-text)]">{hasPractice ? '优先完成错题复练' : '建立你的面试起点'}</p><p className={`mt-1 text-sm leading-6 ${muted}`}>{hasPractice ? `待复练 ${practiceSummary?.todoCount} 题，用新答案验证提升。` : '上传简历并设置目标岗位，生成第一份训练计划。'}</p></div></div></div>
        <Link to={hasPractice ? '/practice' : '/job-targets'} className="group mt-8 flex items-center justify-between rounded-md border border-[var(--shell-border)] bg-[var(--shell-hover)] px-4 py-4 transition hover:border-blue-500/40 hover:bg-blue-500/[0.06]"><span className="flex items-center gap-3 text-sm font-medium text-[var(--shell-text)]"><Sparkles className="h-4 w-4 text-blue-500" /> 开始下一段训练</span><ArrowRight className="h-4 w-4 text-[var(--shell-subtle)] transition group-hover:translate-x-1 group-hover:text-blue-500" /></Link>
        <Link to="/job-targets" className={`mt-5 flex items-center gap-2 text-xs transition hover:text-[var(--shell-text)] ${muted}`}><BriefcaseBusiness className="h-3.5 w-3.5" /> 查看岗位匹配</Link>
      </aside>
    </div>
  </div>;
}