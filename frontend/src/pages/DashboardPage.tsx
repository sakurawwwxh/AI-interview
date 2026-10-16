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
  // 「还没取到」与「确实没有目标岗位」必须分开：否则首屏会把加载中显示成「尚未设置」，
  // 用户明明已设过岗位，却看到空状态文案。
  const [planLoading, setPlanLoading] = useState(true);
  useEffect(() => {
    growthApi.getPlan().then(setGrowthPlan).catch(() => setGrowthPlan(null)).finally(() => setPlanLoading(false));
    practiceApi.getSummary().then(setPracticeSummary).catch(() => setPracticeSummary(null));
  }, []);

  const steps = useMemo<Step[]>(() => {
    const firstAction = growthPlan?.actions?.[0];
    const hasTarget = Boolean(growthPlan?.targetRole);
    return [
      {
        order: '01',
        title: '确认目标岗位',
        detail: planLoading ? '正在读取当前目标…'
          : hasTarget ? `当前聚焦：${growthPlan?.targetRole}`
            : '添加一个岗位目标，让训练更有方向。',
        status: planLoading ? 'upcoming' : hasTarget ? 'done' : 'current',
        link: '/job-targets',
      },
      { order: '02', title: firstAction?.title || '完成一次针对性练习', detail: firstAction?.description || '从一场模拟面试或一组专项复练开始。', status: hasTarget ? 'current' : 'upcoming', link: firstAction?.link || '/practice' },
      { order: '03', title: '复盘并沉淀能力画像', detail: practiceSummary?.todoCount ? `还有 ${practiceSummary.todoCount} 道待复练题，完成后更新能力趋势。` : '每一次评分都会成为下一次训练的依据。', status: 'upcoming', link: '/interview-statistics' },
    ];
  }, [growthPlan, practiceSummary, planLoading]);

  const currentStep = steps.find((step) => step.status === 'current') || steps[1];
  const hasPractice = (practiceSummary?.todoCount ?? 0) > 0;
  const greeting = user?.displayName || user?.username || '同学';
  const muted = 'text-[var(--shell-muted)]';
  const todayLabel = new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' });

  return (
    <div className="mx-auto max-w-[1280px]">
      {/* 卷首栏 */}
      <header className="ui-panel reveal-up flex flex-col gap-4 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
        <div className={`flex flex-wrap items-center gap-2.5 text-sm ${muted}`}>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-teal-500/10 px-2.5 py-0.5 text-xs font-semibold tracking-wide text-teal-800 dark:text-teal-300">
            <span className="h-1.5 w-1.5 rounded-full bg-teal-500" />
            {todayLabel}
          </span>
          <span className="text-[var(--shell-subtle)]">·</span>
          <span>目标岗位</span>
          {planLoading ? (
            <span className="inline-block h-3.5 w-24 animate-pulse rounded bg-[var(--shell-hover)] align-middle"
              role="status" aria-label="正在读取目标岗位" />
          ) : (
            <span className="font-semibold text-[var(--shell-text)]">{growthPlan?.targetRole || '尚未设置'}</span>
          )}
          <span className="hidden text-[var(--shell-subtle)] sm:inline">·</span>
          <span className="hidden sm:inline">{greeting} 的训练空间</span>
        </div>
        <Link to="/job-targets" className="ui-btn-ghost !py-1.5 !text-xs">
          调整方向 <ArrowRight className="h-3.5 w-3.5" />
        </Link>
      </header>

      <div className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
        {/* 主刊：今日一件事 */}
        <section className="ui-panel-signal reveal-up delay-1 overflow-hidden px-6 py-10 sm:px-10">
          <p className="page-kicker">今日训练 · Briefing</p>
          <h1 className="page-title mt-4 max-w-3xl">
            今天，专注准备
            <span className="text-gradient-ink"> 一件事。</span>
          </h1>
          <p className={`mt-5 max-w-2xl text-[15px] leading-7 ${muted}`}>
            不必一次做完所有准备。让目标岗位、每一次回答和每一次复盘，形成清晰的前进路径。
          </p>

          <div className="mt-10 overflow-hidden rounded-2xl border border-[var(--shell-border)] bg-[var(--shell-hover)]/30">
            {steps.map((step, index) => (
              <Link
                key={step.order}
                to={step.link}
                className={`group grid grid-cols-[3.5rem_minmax(0,1fr)_auto] gap-3 border-b border-[var(--shell-border)] px-4 py-5 transition last:border-b-0 sm:grid-cols-[5rem_minmax(0,1fr)_auto] sm:gap-6 sm:px-6 ${
                  step.status === 'current'
                    ? 'bg-teal-500/[0.07] dark:bg-teal-400/[0.06]'
                    : 'hover:bg-[var(--shell-hover)]'
                }`}
                style={{ animationDelay: `${120 + index * 50}ms` }}
              >
                <span
                  className={`font-display pt-0.5 text-base font-semibold tabular-nums tracking-tight ${
                    step.status === 'current' ? 'text-teal-700 dark:text-teal-400' : 'text-[var(--shell-subtle)]'
                  }`}
                >
                  {step.order}
                </span>
                <div>
                  <div className="flex items-center gap-2">
                    <h2 className="text-base font-semibold tracking-tight text-[var(--shell-text)]">{step.title}</h2>
                    {step.status === 'done' && <Check className="h-4 w-4 text-emerald-500" />}
                    {step.status === 'current' && <CircleDotDashed className="h-4 w-4 text-teal-500" />}
                  </div>
                  <p className={`mt-1.5 max-w-xl text-sm leading-6 ${muted}`}>{step.detail}</p>
                </div>
                <ArrowRight className="mt-1 h-4 w-4 text-[var(--shell-subtle)] transition group-hover:translate-x-1 group-hover:text-teal-500" />
              </Link>
            ))}
          </div>

          <div className="mt-9 flex flex-wrap items-center gap-3">
            <Link to={currentStep.link} className="ui-btn-primary">
              {currentStep.title} <ArrowRight className="h-4 w-4" />
            </Link>
            <Link to="/upload" className={`inline-flex items-center gap-2 px-2 text-sm transition hover:text-[var(--shell-text)] ${muted}`}>
              <FileText className="h-4 w-4" /> 管理简历
            </Link>
          </div>
        </section>

        {/* 侧栏：教练随笔 */}
        <aside className="flex flex-col gap-4">
          <div className="ui-panel reveal-up delay-2 relative overflow-hidden p-6">
            <div className="pointer-events-none absolute -right-10 -top-10 h-32 w-32 rounded-full bg-teal-400/15 blur-2xl" />
            <div className="pointer-events-none absolute -bottom-8 left-8 h-24 w-24 rounded-full bg-amber-400/10 blur-2xl" />
            <p className="page-kicker">AI 教练随笔</p>
            <blockquote className="font-display mt-4 text-[1.35rem] font-semibold leading-9 tracking-[-0.02em] text-[var(--shell-text)]">
              {growthPlan?.headline || '先把一个答案讲清楚，再去追求更多答案。'}
            </blockquote>
            <p className={`mt-4 text-sm leading-7 ${muted}`}>
              {growthPlan?.actions?.[0]?.description || '开始训练后，系统会把你的真实表现转化为可执行的下一步。'}
            </p>
          </div>

          <div className="ui-panel reveal-up delay-3 p-6">
            <p className="page-kicker">本次聚焦</p>
            <div className="mt-4 flex items-start gap-3">
              <span className="mt-0.5 rounded-xl border border-teal-500/20 bg-teal-500/10 p-2.5 text-teal-700 dark:text-teal-300">
                <Target className="h-4 w-4" />
              </span>
              <div>
                <p className="text-sm font-semibold text-[var(--shell-text)]">
                  {hasPractice ? '优先完成错题复练' : '建立你的面试起点'}
                </p>
                <p className={`mt-1 text-sm leading-6 ${muted}`}>
                  {hasPractice
                    ? `待复练 ${practiceSummary?.todoCount} 题，用新答案验证提升。`
                    : '上传简历并设置目标岗位，生成第一份训练计划。'}
                </p>
              </div>
            </div>
          </div>

          <Link
            to={hasPractice ? '/practice' : '/job-targets'}
            className="group reveal-up delay-4 flex items-center justify-between rounded-2xl border border-teal-500/25 bg-gradient-to-r from-teal-500/12 via-teal-500/6 to-amber-500/5 px-5 py-4 transition hover:border-teal-500/40 hover:from-teal-500/18"
          >
            <span className="flex items-center gap-3 text-sm font-semibold text-[var(--shell-text)]">
              <Sparkles className="h-4 w-4 text-teal-600 dark:text-teal-400" />
              开始下一段训练
            </span>
            <ArrowRight className="h-4 w-4 text-[var(--shell-subtle)] transition group-hover:translate-x-1 group-hover:text-teal-500" />
          </Link>

          <Link to="/job-targets" className={`reveal-up delay-5 flex items-center gap-2 px-1 text-xs transition hover:text-[var(--shell-text)] ${muted}`}>
            <BriefcaseBusiness className="h-3.5 w-3.5" /> 查看岗位匹配
          </Link>
        </aside>
      </div>
    </div>
  );
}
