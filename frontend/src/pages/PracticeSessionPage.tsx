import { FormEvent, useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { AlertCircle, ArrowLeft, CheckCircle2, Loader2, Send, XCircle } from 'lucide-react';
import { getErrorMessage } from '../api/request';
import { practiceApi, PracticeAttempt, PracticeTaskDetail } from '../api/practice';

export default function PracticeSessionPage() {
  const { taskId } = useParams<{ taskId: string }>();
  const navigate = useNavigate();
  const [task, setTask] = useState<PracticeTaskDetail | null>(null);
  const [answer, setAnswer] = useState('');
  const [result, setResult] = useState<PracticeAttempt | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!taskId) return;
    setLoading(true); setError(null);
    try { setTask(await practiceApi.getTask(Number(taskId))); } catch (loadError) { setError(getErrorMessage(loadError)); } finally { setLoading(false); }
  }, [taskId]);
  useEffect(() => { load(); }, [load]);

  const submit = async (event: FormEvent) => {
    event.preventDefault(); if (!task || !answer.trim()) return;
    setSubmitting(true); setError(null);
    try {
      const response = await practiceApi.submitAttempt(task.id, answer.trim());
      setResult(response.attempt); await load();
    } catch (submitError) { setError(getErrorMessage(submitError)); } finally { setSubmitting(false); }
  };
  const ignore = async () => { if (!task) return; await practiceApi.updateStatus(task.id, 'IGNORED'); navigate('/practice'); };

  if (loading) return <div className="flex min-h-[50vh] items-center justify-center"><Loader2 className="h-8 w-8 animate-spin text-primary-500" /></div>;
  if (error && !task) return <div className="flex min-h-[50vh] flex-col items-center justify-center"><AlertCircle className="h-10 w-10 text-red-500" /><p className="mt-3">{error}</p><button onClick={load} className="mt-4 rounded-xl bg-primary-600 px-4 py-2 text-white">重试</button></div>;
  if (!task) return null;
  const passed = result?.score != null && result.score >= 60;

  return <div className="mx-auto max-w-4xl space-y-6"><Link to="/practice" className="inline-flex items-center gap-2 text-sm font-medium text-slate-500 hover:text-primary-600"><ArrowLeft className="h-4 w-4" />返回复练中心</Link><div className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800"><div className="flex items-center justify-between gap-4"><span className="rounded-full bg-primary-50 px-3 py-1 text-sm font-medium text-primary-700">{task.category || '综合'}</span><span className="text-sm text-slate-400">原分 {task.originalScore}</span></div><h1 className="mt-5 text-xl font-bold leading-8 text-slate-800 dark:text-white">{task.question}</h1><p className="mt-3 text-sm text-slate-500">请独立作答。提交前不会显示原反馈或参考答案。</p></div>{!result ? <form onSubmit={submit} className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800"><label className="text-sm font-semibold text-slate-700 dark:text-slate-200">你的复练答案</label><textarea required value={answer} onChange={(event) => setAnswer(event.target.value)} rows={10} maxLength={10000} placeholder="从核心概念、原理、实践场景和边界条件展开回答…" className="mt-3 w-full rounded-xl border border-slate-200 bg-white p-4 text-slate-800 outline-none focus:border-primary-500 dark:border-slate-600 dark:bg-slate-900 dark:text-white" />{error && <p className="mt-3 text-sm text-red-500">{error}</p>}<div className="mt-5 flex flex-wrap gap-3"><button disabled={submitting || !answer.trim()} className="inline-flex items-center gap-2 rounded-xl bg-primary-600 px-5 py-2.5 font-semibold text-white disabled:opacity-60">{submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}{submitting ? 'AI 评分中…' : '提交并获取评分'}</button><button type="button" onClick={ignore} disabled={submitting} className="rounded-xl border border-slate-200 px-5 py-2.5 text-sm font-medium text-slate-600">暂时忽略</button></div></form> : <div className="space-y-6"><section className={`rounded-2xl border p-6 ${passed ? 'border-emerald-200 bg-emerald-50/60' : 'border-amber-200 bg-amber-50/60'}`}><div className="flex items-center gap-3">{passed ? <CheckCircle2 className="h-8 w-8 text-emerald-600" /> : <XCircle className="h-8 w-8 text-amber-600" />}<div><h2 className="text-xl font-bold">{passed ? '本题已完成' : '再练一次会更好'}</h2><p className="text-sm">本次得分 {result.score} 分，相比原分 {result.score - task.originalScore >= 0 ? '+' : ''}{result.score - task.originalScore} 分。</p></div></div><p className="mt-5 whitespace-pre-wrap leading-7 text-slate-700">{result.feedback}</p></section><section className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800"><h2 className="font-bold text-slate-800 dark:text-white">回顾与参考</h2><div className="mt-4 space-y-4 text-sm leading-7"><div><p className="font-semibold text-slate-600">原回答反馈</p><p className="mt-1 text-slate-600 dark:text-slate-300">{task.originalFeedback || '暂无原反馈'}</p></div><div><p className="font-semibold text-slate-600">参考答案</p><p className="mt-1 whitespace-pre-wrap text-slate-600 dark:text-slate-300">{task.referenceAnswer || '暂无参考答案'}</p></div>{task.keyPoints.length > 0 && <div><p className="font-semibold text-slate-600">关键点</p><ul className="mt-1 list-disc space-y-1 pl-5 text-slate-600 dark:text-slate-300">{task.keyPoints.map((point) => <li key={point}>{point}</li>)}</ul></div>}</div></section><div className="flex gap-3"><button onClick={() => { setResult(null); setAnswer(''); }} className="rounded-xl border border-slate-200 px-5 py-2.5 font-medium">再次作答</button><Link to="/practice" className="rounded-xl bg-primary-600 px-5 py-2.5 font-semibold text-white">返回复练中心</Link></div></div>}</div>;
}
