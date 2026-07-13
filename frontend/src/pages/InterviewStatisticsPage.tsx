import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertCircle, Award, BarChart3, BookOpenCheck, Loader2, TrendingDown, TrendingUp } from 'lucide-react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import RadarChart from '../components/RadarChart';
import { historyApi, InterviewStatistics } from '../api/history';
import { getErrorMessage } from '../api/request';

function formatShortDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '-' : `${date.getMonth() + 1}/${date.getDate()}`;
}

function ScoreCard({ label, value, hint, icon: Icon, tone }: { label: string; value: string | number; hint: string; icon: typeof Award; tone: string }) {
  return <div className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800"><div className="flex items-start justify-between gap-4"><div><p className="text-sm text-slate-500 dark:text-slate-400">{label}</p><p className="mt-2 text-3xl font-bold text-slate-800 dark:text-white">{value}</p><p className="mt-2 text-xs text-slate-400">{hint}</p></div><div className={`rounded-xl p-3 ${tone}`}><Icon className="h-5 w-5 text-white" /></div></div></div>;
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

  useEffect(() => {
    loadStatistics();
  }, [loadStatistics]);

  const trendData = useMemo(() => (statistics?.scoreTrend ?? []).map((item, index) => ({ ...item, label: `第 ${index + 1} 次`, date: formatShortDate(item.completedAt) })), [statistics]);
  const radarData = useMemo(() => (statistics?.abilityScores ?? []).map((item) => ({ subject: item.category, score: item.averageScore, fullMark: 100 })), [statistics]);

  if (loading) return <div className="flex min-h-[50vh] items-center justify-center"><Loader2 className="h-8 w-8 animate-spin text-primary-500" /></div>;
  if (error || !statistics) return <div className="flex min-h-[50vh] flex-col items-center justify-center rounded-2xl border border-red-100 bg-white p-8 text-center dark:border-red-900/40 dark:bg-slate-800"><AlertCircle className="h-10 w-10 text-red-500" /><p className="mt-4 font-medium text-slate-700 dark:text-slate-200">{error ?? '暂时无法读取统计数据'}</p><button onClick={loadStatistics} className="mt-5 rounded-xl bg-primary-600 px-4 py-2 text-sm font-semibold text-white">重试</button></div>;
  if (statistics.completedInterviewCount === 0) return <div className="flex min-h-[50vh] flex-col items-center justify-center rounded-2xl border border-slate-100 bg-white p-8 text-center shadow-sm dark:border-slate-700 dark:bg-slate-800"><BarChart3 className="h-12 w-12 text-primary-500" /><h1 className="mt-4 text-xl font-bold text-slate-800 dark:text-white">还没有可分析的面试记录</h1><p className="mt-2 text-sm text-slate-500">完成并生成至少一份面试报告后，这里会展示能力雷达和分数趋势。</p></div>;

  const change = statistics.scoreChange;
  const changeText = change === null ? '-' : `${change >= 0 ? '+' : ''}${change} 分`;
  return <div className="space-y-6"><div className="flex items-center justify-between gap-3"><div className="flex items-center gap-3"><div className="rounded-xl bg-primary-100 p-2.5 text-primary-600 dark:bg-primary-900/40"><BarChart3 className="h-6 w-6" /></div><div><h1 className="text-2xl font-bold text-slate-800 dark:text-white">面试能力统计</h1><p className="mt-1 text-sm text-slate-500">基于当前账号已评分的面试记录。</p></div></div><Link to="/practice" className="rounded-xl bg-primary-600 px-4 py-2 text-sm font-semibold text-white">去错题复练</Link></div><div className="grid grid-cols-1 gap-4 md:grid-cols-3"><ScoreCard label="已完成面试" value={`${statistics.completedInterviewCount} 次`} hint="已生成评分报告的会话" icon={BookOpenCheck} tone="bg-primary-500" /><ScoreCard label="平均分" value={`${statistics.averageScore} 分`} hint="全部已评分会话的平均表现" icon={Award} tone="bg-indigo-500" /><ScoreCard label="最近变化" value={changeText} hint={change === null ? '完成第二次面试后可比较变化' : `最近得分 ${statistics.latestScore ?? '-'} 分`} icon={change !== null && change < 0 ? TrendingDown : TrendingUp} tone={change !== null && change < 0 ? 'bg-amber-500' : 'bg-emerald-500'} /></div><div className="grid grid-cols-1 gap-6 xl:grid-cols-2"><section className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800"><h2 className="font-semibold text-slate-800 dark:text-white">能力雷达</h2><p className="mt-1 text-sm text-slate-500">按题型汇总的平均分。</p><RadarChart data={radarData} height={340} /></section><section className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800"><h2 className="font-semibold text-slate-800 dark:text-white">分数趋势</h2><p className="mt-1 text-sm text-slate-500">每次完成评分后的总分变化。</p><div className="mt-4 h-[340px]"><ResponsiveContainer width="100%" height="100%"><LineChart data={trendData}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="label" /><YAxis domain={[0, 100]} /><Tooltip labelFormatter={(_, payload) => payload[0]?.payload?.date ?? ''} /><Line type="monotone" dataKey="score" stroke="#6366f1" strokeWidth={3} dot={{ r: 4 }} /></LineChart></ResponsiveContainer></div></section></div><div className="grid gap-6 xl:grid-cols-3"><section className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800"><div className="flex items-center justify-between"><h2 className="font-semibold text-slate-800 dark:text-white">优先补强</h2><Link to="/practice" className="text-sm font-medium text-primary-600">去复练</Link></div><div className="mt-4 space-y-3">{statistics.weaknesses.map((item, index) => <Link key={item.category} to={`/practice?status=TODO&category=${encodeURIComponent(item.category)}`} className="flex items-center justify-between rounded-xl bg-slate-50 px-4 py-3 transition hover:bg-primary-50 dark:bg-slate-700/40"><div><p className="font-medium">{index + 1}. {item.category}</p><p className="text-xs text-slate-400">基于 {item.questionCount} 道题</p></div><span className="font-bold text-amber-600">{item.averageScore}</span></Link>)}</div></section><section className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800 xl:col-span-2"><h2 className="font-semibold text-slate-800 dark:text-white">能力明细</h2><div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{statistics.abilityScores.map((item) => <div key={item.category} className="rounded-xl border border-slate-100 p-4 dark:border-slate-700"><div className="flex justify-between gap-3"><span className="font-medium">{item.category}</span><span className="font-bold text-primary-600">{item.averageScore}</span></div><div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-700"><div className="h-full rounded-full bg-primary-500" style={{ width: `${item.averageScore}%` }} /></div><p className="mt-2 text-xs text-slate-400">{item.questionCount} 道已评分题目</p></div>)}</div></section></div></div>;
}
