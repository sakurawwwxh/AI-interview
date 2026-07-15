import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertCircle, ArrowRight, BarChart3, BookOpenCheck, ChevronRight, Loader2, Sparkles, TrendingDown, TrendingUp } from 'lucide-react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { historyApi, InterviewStatistics } from '../api/history';
import { getErrorMessage } from '../api/request';

function formatShortDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '-' : `${date.getMonth() + 1}/${date.getDate()}`;
}

function scoreLabel(score: number) {
  if (score >= 85) return '表现稳定';
  if (score >= 70) return '正在建立优势';
  if (score >= 60) return '已有基础，值得继续打磨';
  return '先从最薄弱处开始补强';
}

function scoreTone(score: number) {
  if (score >= 80) return 'bg-emerald-500';
  if (score >= 60) return 'bg-primary-600';
  return 'bg-amber-500';
}

export default function InterviewStatisticsPage() {
  const [statistics, setStatistics] = useState<InterviewStatistics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadStatistics = useCallback(() => {
    setLoading(true);
    setError(null);
    historyApi.getInterviewStatistics()
      .then(setStatistics)
      .catch((requestError) => setError(getErrorMessage(requestError)))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { loadStatistics(); }, [loadStatistics]);

  const trendData = useMemo(() => (statistics?.scoreTrend ?? []).map((item, index) => ({ ...item, label: `第 ${index + 1} 次`, date: formatShortDate(item.completedAt) })), [statistics]);
  const abilities = useMemo(() => [...(statistics?.abilityScores ?? [])].sort((a, b) => a.averageScore - b.averageScore), [statistics]);
  const priority = statistics?.weaknesses?.[0] ?? abilities[0];

  if (loading) return <div className="flex min-h-[52vh] items-center justify-center"><Loader2 className="h-7 w-7 animate-spin text-primary-600" /></div>;
  if (error || !statistics) return <section className="flex min-h-[52vh] flex-col items-center justify-center border border-red-200 bg-[var(--shell-panel)] p-8 text-center dark:border-red-500/25"><AlertCircle className="h-9 w-9 text-red-600" /><h1 className="mt-4 text-lg font-semibold text-[var(--shell-text)]">能力画像暂时不可用</h1><p className="mt-2 max-w-md text-sm leading-6 text-[var(--shell-muted)]">{error ?? '暂时无法读取统计数据，请稍后重试。'}</p><button onClick={loadStatistics} className="mt-5 rounded-lg bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white">重新加载</button></section>;
  if (statistics.completedInterviewCount === 0) return (
    <div className="mx-auto max-w-6xl space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-5 border-b border-[var(--shell-border)] pb-6">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary-600 dark:text-primary-400">成长档案</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight text-[var(--shell-text)]">能力画像</h1>
          <p className="mt-2 text-sm text-[var(--shell-muted)]">这里会沉淀每一次真实作答留下的能力信号，而不是一份开始前的问卷。</p>
        </div>
        <span className="rounded-full border border-[var(--shell-border)] px-3 py-1.5 text-xs font-medium text-[var(--shell-muted)]">尚未建立训练档案</span>
      </header>

      <section className="overflow-hidden border border-[var(--shell-border)] bg-[var(--shell-panel)]">
        <div className="grid lg:grid-cols-[1.15fr_0.85fr]">
          <div className="p-7 sm:p-9">
            <p className="text-xs font-semibold uppercase tracking-[0.16em] text-[var(--shell-subtle)]">从第一段回答开始</p>
            <h2 className="mt-4 max-w-2xl text-3xl font-semibold leading-tight tracking-tight text-[var(--shell-text)]">完成一次模拟面试，<br />让训练有迹可循。</h2>
            <p className="mt-4 max-w-xl text-sm leading-7 text-[var(--shell-muted)]">系统会根据你的真实回答生成第一份基线：哪些能力已具优势、哪些该优先补强，以及下一次练习该从哪里开始。</p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Link to="/history" className="inline-flex items-center gap-2 rounded-lg bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-primary-700">选择简历并开始<ArrowRight className="h-4 w-4" /></Link>
              <Link to="/upload" className="inline-flex items-center gap-2 rounded-lg border border-[var(--shell-border)] px-4 py-2.5 text-sm font-semibold text-[var(--shell-text)] transition hover:bg-[var(--shell-hover)]">先上传简历<ChevronRight className="h-4 w-4" /></Link>
            </div>
            <div className="mt-9 grid border-t border-[var(--shell-border)] pt-6 sm:grid-cols-3">
              {[
                ['01', '选择训练材料', '从已上传的简历中发起一次面试'],
                ['02', '完成真实作答', '围绕岗位与经历进行针对性回答'],
                ['03', '获得成长基线', '查看能力分布、趋势和复练建议'],
              ].map(([step, title, description], index) => (
                <div key={step} className={'pr-5 ' + (index > 0 ? 'mt-5 border-t border-[var(--shell-border)] pt-5 sm:mt-0 sm:border-l sm:border-t-0 sm:pl-5 sm:pt-0' : '')}>
                  <p className="text-xs font-semibold tracking-[0.14em] text-primary-600 dark:text-primary-400">{step}</p>
                  <p className="mt-2 text-sm font-semibold text-[var(--shell-text)]">{title}</p>
                  <p className="mt-1.5 text-xs leading-5 text-[var(--shell-muted)]">{description}</p>
                </div>
              ))}
            </div>
          </div>

          <aside className="border-t border-[var(--shell-border)] bg-[#111827] p-7 text-slate-100 lg:border-l lg:border-t-0 sm:p-9">
            <div className="flex items-center gap-2 text-primary-300"><Sparkles className="h-4 w-4" /><p className="text-xs font-semibold uppercase tracking-[0.16em]">评分后可见</p></div>
            <h3 className="mt-4 text-xl font-semibold tracking-tight">你的第一份成长复盘</h3>
            <p className="mt-2 text-sm leading-6 text-slate-400">不是一个空泛总分，而是一份可以直接指导下一步练习的训练记录。</p>
            <div className="mt-7 space-y-4 border-y border-white/10 py-5">
              {[
                ['表达呈现', '能力分数与关键反馈'],
                ['专业深度', '优势与待提升维度'],
                ['训练路径', '可直接进入的专项复练'],
              ].map(([title, description]) => (
                <div key={title} className="flex items-center gap-3">
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-white/10 bg-white/5 text-xs font-semibold text-primary-300">—</span>
                  <div><p className="text-sm font-medium text-slate-100">{title}</p><p className="mt-0.5 text-xs text-slate-400">{description}</p></div>
                </div>
              ))}
            </div>
            <Link to="/job-targets" className="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-primary-300 transition hover:text-primary-200">先设定目标岗位<ChevronRight className="h-4 w-4" /></Link>
          </aside>
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-3">
        {[
          [BookOpenCheck, '基线能力图谱', '每个能力维度都来自已评分的真实回答，而非预设标签。'],
          [BarChart3, '可追踪的变化', '通过多次面试的得分趋势，确认训练是否真正带来提升。'],
          [Sparkles, '下一步训练建议', '把薄弱项直接转成专项复练，让每次努力都有方向。'],
        ].map(([Icon, title, description]) => {
          const CardIcon = Icon as typeof BookOpenCheck;
          return <article key={title as string} className="border border-[var(--shell-border)] bg-[var(--shell-panel)] p-5"><CardIcon className="h-5 w-5 text-primary-600 dark:text-primary-400" /><h3 className="mt-4 text-sm font-semibold text-[var(--shell-text)]">{title as string}</h3><p className="mt-2 text-sm leading-6 text-[var(--shell-muted)]">{description as string}</p></article>;
        })}
      </section>
    </div>
  );

  const change = statistics.scoreChange;
  const ChangeIcon = change !== null && change < 0 ? TrendingDown : TrendingUp;
  const changeText = change === null ? '还需一次训练' : `${change >= 0 ? '+' : ''}${change} 分`;

  return <div className="mx-auto max-w-6xl space-y-6">
    <header className="flex flex-wrap items-end justify-between gap-5 border-b border-[var(--shell-border)] pb-6"><div><p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary-600 dark:text-primary-400">成长复盘</p><h1 className="mt-2 text-3xl font-semibold tracking-tight text-[var(--shell-text)]">能力画像</h1><p className="mt-2 text-sm text-[var(--shell-muted)]">不只是分数：把每次回答留下的信号，变成下一次训练的方向。</p></div><Link to="/practice" className="inline-flex items-center gap-2 rounded-lg bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-primary-700">开始专项复练 <ArrowRight className="h-4 w-4" /></Link></header>

    <section className="grid overflow-hidden border border-[var(--shell-border)] bg-[var(--shell-panel)] lg:grid-cols-[1.15fr_0.85fr]">
      <div className="p-6 sm:p-8"><p className="text-xs font-semibold uppercase tracking-[0.16em] text-[var(--shell-subtle)]">当前综合表现</p><div className="mt-4 flex items-end gap-4"><span className="text-6xl font-semibold leading-none tracking-[-0.06em] text-[var(--shell-text)]">{statistics.averageScore}</span><span className="mb-1 text-sm font-medium text-[var(--shell-muted)]">/ 100 分</span></div><p className="mt-3 text-base font-medium text-[var(--shell-text)]">{scoreLabel(statistics.averageScore)}</p><p className="mt-1 text-sm leading-6 text-[var(--shell-muted)]">基于 {statistics.completedInterviewCount} 次已评分模拟面试汇总。</p><div className="mt-7 flex flex-wrap gap-x-8 gap-y-4 border-t border-[var(--shell-border)] pt-5"><div><p className="text-xs text-[var(--shell-subtle)]">最近得分</p><p className="mt-1 text-lg font-semibold text-[var(--shell-text)]">{statistics.latestScore ?? '—'} 分</p></div><div className="border-l border-[var(--shell-border)] pl-8"><p className="text-xs text-[var(--shell-subtle)]">相对上次</p><p className="mt-1 inline-flex items-center gap-1 text-lg font-semibold text-[var(--shell-text)]"><ChangeIcon className={`h-4 w-4 ${change !== null && change < 0 ? 'text-amber-500' : 'text-emerald-500'}`} />{changeText}</p></div></div></div>
      <div className="border-t border-[var(--shell-border)] bg-[var(--shell-hover)] p-6 lg:border-l lg:border-t-0 sm:p-8"><div className="flex items-center gap-2 text-primary-600 dark:text-primary-400"><Sparkles className="h-4 w-4" /><p className="text-xs font-semibold uppercase tracking-[0.16em]">本次训练建议</p></div>{priority ? <><h2 className="mt-4 text-xl font-semibold text-[var(--shell-text)]">优先补强「{priority.category}」</h2><p className="mt-2 text-sm leading-6 text-[var(--shell-muted)]">该维度当前平均 {priority.averageScore} 分，来自 {priority.questionCount} 道已评分题目。集中复练，比平均用力更有效。</p><Link to={`/practice?status=TODO&category=${encodeURIComponent(priority.category)}`} className="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-primary-600 dark:text-primary-400">查看对应复练题 <ChevronRight className="h-4 w-4" /></Link></> : <p className="mt-4 text-sm text-[var(--shell-muted)]">再积累更多评分结果，系统会给出稳定建议。</p>}</div>
    </section>

    <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
      <section className="border border-[var(--shell-border)] bg-[var(--shell-panel)] p-5 sm:p-6"><div className="flex items-start justify-between gap-4"><div><p className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--shell-subtle)]">训练轨迹</p><h2 className="mt-1 text-lg font-semibold text-[var(--shell-text)]">分数的变化，比单次高低更重要。</h2><p className="mt-1 text-sm text-[var(--shell-muted)]">按完成评分时间排列。</p></div><BookOpenCheck className="h-5 w-5 text-[var(--shell-subtle)]" /></div><div className="mt-5 h-[280px]"><ResponsiveContainer width="100%" height="100%"><LineChart data={trendData} margin={{ left: -18, right: 8, top: 8, bottom: 0 }}><CartesianGrid stroke="currentColor" opacity={0.08} vertical={false} /><XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fill: '#94a3b8', fontSize: 12 }} /><YAxis domain={[0, 100]} tickLine={false} axisLine={false} tick={{ fill: '#94a3b8', fontSize: 12 }} /><Tooltip labelFormatter={(_, payload) => payload[0]?.payload?.date ?? ''} /><Line type="monotone" dataKey="score" stroke="#2563eb" strokeWidth={2.5} dot={{ r: 3, fill: '#2563eb' }} activeDot={{ r: 5 }} /></LineChart></ResponsiveContainer></div></section>
      <section className="border border-[var(--shell-border)] bg-[var(--shell-panel)] p-5 sm:p-6"><p className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--shell-subtle)]">能力分布</p><h2 className="mt-1 text-lg font-semibold text-[var(--shell-text)]">把注意力放在最有价值的差距上。</h2><div className="mt-5 space-y-5">{abilities.map((item, index) => <div key={item.category} className="group"><div className="flex items-center justify-between gap-4"><div className="flex min-w-0 items-center gap-3"><span className="w-5 text-xs font-semibold text-[var(--shell-subtle)]">{String(index + 1).padStart(2, '0')}</span><span className="truncate text-sm font-medium text-[var(--shell-text)]">{item.category}</span></div><span className="text-sm font-semibold text-[var(--shell-text)]">{item.averageScore}</span></div><div className="ml-8 mt-2 h-1.5 overflow-hidden rounded-full bg-[var(--shell-hover)]"><div className={`h-full rounded-full ${scoreTone(item.averageScore)}`} style={{ width: `${item.averageScore}%` }} /></div><p className="ml-8 mt-1.5 text-xs text-[var(--shell-muted)]">{item.questionCount} 道已评分题目</p></div>)}</div></section>
    </div>

    <section className="border border-[var(--shell-border)] bg-[var(--shell-panel)]"><div className="flex flex-wrap items-center justify-between gap-4 border-b border-[var(--shell-border)] p-5 sm:px-6"><div><p className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--shell-subtle)]">训练队列</p><h2 className="mt-1 text-lg font-semibold text-[var(--shell-text)]">从薄弱项开始，逐个建立优势。</h2></div><Link to="/practice" className="text-sm font-semibold text-primary-600 dark:text-primary-400">查看全部复练</Link></div><div className="divide-y divide-[var(--shell-border)]">{statistics.weaknesses.map((item, index) => <Link key={item.category} to={`/practice?status=TODO&category=${encodeURIComponent(item.category)}`} className="flex items-center justify-between gap-5 p-5 transition hover:bg-[var(--shell-hover)] sm:px-6"><div className="flex min-w-0 items-center gap-4"><span className="text-sm font-semibold text-[var(--shell-subtle)]">0{index + 1}</span><div><p className="text-sm font-semibold text-[var(--shell-text)]">{item.category}</p><p className="mt-1 text-xs text-[var(--shell-muted)]">当前平均 {item.averageScore} 分 · {item.questionCount} 道已评分题目</p></div></div><span className="inline-flex shrink-0 items-center gap-1 text-sm font-semibold text-primary-600 dark:text-primary-400">去复练 <ArrowRight className="h-4 w-4" /></span></Link>)}</div></section>
  </div>;
}