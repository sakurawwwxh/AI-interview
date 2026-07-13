import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { AlertCircle, CheckCircle2, ChevronRight, CircleDashed, Loader2, RotateCcw, Sparkles } from 'lucide-react';
import { getErrorMessage } from '../api/request';
import { practiceApi, PracticeSummary, PracticeTaskItem, PracticeTaskStatus } from '../api/practice';

const labels: Record<PracticeTaskStatus, string> = { TODO: '待复练', COMPLETED: '已完成', IGNORED: '已忽略' };

export default function PracticeCenterPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [tasks, setTasks] = useState<PracticeTaskItem[]>([]);
  const [summary, setSummary] = useState<PracticeSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const status = (searchParams.get('status') as PracticeTaskStatus | null) ?? 'TODO';
  const category = searchParams.get('category') ?? '';

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      const [taskPage, taskSummary] = await Promise.all([
        practiceApi.getTasks({ status, category: category || undefined, size: 50 }), practiceApi.getSummary(),
      ]);
      setTasks(taskPage.items); setSummary(taskSummary);
    } catch (loadError) {
      setError(getErrorMessage(loadError));
    } finally { setLoading(false); }
  }, [status, category]);

  useEffect(() => { load(); }, [load]);
  const categories = useMemo(() => Array.from(new Set(tasks.map((task) => task.category).filter(Boolean))), [tasks]);
  const changeFilter = (nextStatus: PracticeTaskStatus, nextCategory = category) => {
    const next = new URLSearchParams(); next.set('status', nextStatus); if (nextCategory) next.set('category', nextCategory); setSearchParams(next);
  };
  const restore = async (task: PracticeTaskItem) => { await practiceApi.updateStatus(task.id, 'TODO'); load(); };

  return <div className="mx-auto max-w-6xl space-y-6">
    <div className="flex flex-wrap items-end justify-between gap-4"><div><p className="text-sm font-semibold text-primary-600">成长训练</p><h1 className="mt-1 text-3xl font-bold text-slate-800 dark:text-white">错题复练</h1><p className="mt-2 text-slate-500">把面试中的低分题重新答好，看到真实提升。</p></div><Link to="/interview-statistics" className="text-sm font-semibold text-primary-600">查看能力统计</Link></div>
    <div className="grid grid-cols-2 gap-4 md:grid-cols-4">{[
      ['待复练', summary?.todoCount ?? '-', 'text-amber-600'], ['已完成', summary?.completedCount ?? '-', 'text-emerald-600'], ['本周完成', summary?.completedThisWeek ?? '-', 'text-primary-600'], ['平均提升', summary?.averageImprovement == null ? '-' : `${summary.averageImprovement >= 0 ? '+' : ''}${summary.averageImprovement} 分`, 'text-indigo-600'],
    ].map(([label, value, tone]) => <div key={String(label)} className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800"><p className="text-sm text-slate-500">{label}</p><p className={`mt-2 text-2xl font-bold ${tone}`}>{value}</p></div>)}</div>
    <div className="flex flex-wrap gap-2 rounded-2xl border border-slate-100 bg-white p-3 dark:border-slate-700 dark:bg-slate-800">{(['TODO', 'COMPLETED', 'IGNORED'] as PracticeTaskStatus[]).map((item) => <button key={item} onClick={() => changeFilter(item)} className={`rounded-xl px-4 py-2 text-sm font-medium ${status === item ? 'bg-primary-600 text-white' : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-700'}`}>{labels[item]}</button>)}{categories.map((item) => <button key={item} onClick={() => changeFilter(status, category === item ? '' : item)} className={`rounded-xl px-3 py-2 text-sm ${category === item ? 'bg-indigo-100 text-indigo-700' : 'text-slate-500 hover:bg-slate-100'}`}>{item}</button>)}</div>
    {loading ? <div className="flex min-h-64 items-center justify-center"><Loader2 className="h-8 w-8 animate-spin text-primary-500" /></div> : error ? <div className="flex min-h-64 flex-col items-center justify-center rounded-2xl border border-red-100 bg-white p-8 text-center"><AlertCircle className="h-10 w-10 text-red-500" /><p className="mt-3 text-slate-700">{error}</p><button onClick={load} className="mt-4 rounded-xl bg-primary-600 px-4 py-2 text-white">重试</button></div> : tasks.length === 0 ? <div className="flex min-h-64 flex-col items-center justify-center rounded-2xl border border-slate-100 bg-white p-8 text-center dark:border-slate-700 dark:bg-slate-800"><Sparkles className="h-12 w-12 text-primary-500" /><h2 className="mt-4 text-xl font-bold text-slate-800 dark:text-white">{status === 'TODO' ? '暂时没有待复练错题' : '暂无对应任务'}</h2><p className="mt-2 text-sm text-slate-500">完成一次带评分的模拟面试后，低于 60 分的题会自动出现在这里。</p></div> : <div className="space-y-3">{tasks.map((task) => <div key={task.id} className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800"><div className="flex flex-col gap-4 sm:flex-row sm:items-center"><div className="flex-1"><div className="flex flex-wrap items-center gap-2"><span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs text-slate-600">{task.category || '综合'}</span><span className="text-xs text-slate-400">已练 {task.attemptCount} 次</span></div><h2 className="mt-3 line-clamp-2 font-semibold text-slate-800 dark:text-white">{task.question}</h2><div className="mt-3 flex items-center gap-4 text-sm"><span>原分 <b className="text-amber-600">{task.originalScore}</b></span><span>最近 <b className="text-primary-600">{task.lastScore ?? '-'}</b></span>{task.improvement != null && <span className={task.improvement >= 0 ? 'text-emerald-600' : 'text-red-500'}>{task.improvement >= 0 ? '+' : ''}{task.improvement} 分</span>}</div></div>{task.status === 'IGNORED' ? <button onClick={() => restore(task)} className="inline-flex items-center gap-2 rounded-xl border border-slate-200 px-4 py-2 text-sm font-medium"><RotateCcw className="h-4 w-4" />恢复</button> : <Link to={`/practice/${task.id}`} className="inline-flex items-center gap-2 rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white">{task.status === 'COMPLETED' ? <CheckCircle2 className="h-4 w-4" /> : <CircleDashed className="h-4 w-4" />}{task.status === 'COMPLETED' ? '再次练习' : '开始复练'}<ChevronRight className="h-4 w-4" /></Link>}</div></div>)}</div>}
  </div>;
}
