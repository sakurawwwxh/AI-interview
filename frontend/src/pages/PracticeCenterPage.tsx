import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { AlertCircle, ArrowRight, CheckCircle2, CircleDashed, Loader2, RotateCcw, Sparkles } from 'lucide-react';
import { getErrorMessage } from '../api/request';
import { practiceApi, PracticeSummary, PracticeTaskItem, PracticeTaskStatus } from '../api/practice';
import PageHeader from '../components/PageHeader';

const labels: Record<PracticeTaskStatus, string> = {
  TODO: '待复练',
  COMPLETED: '已完成',
  IGNORED: '已忽略',
};

export default function PracticeCenterPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [tasks, setTasks] = useState<PracticeTaskItem[]>([]);
  const [summary, setSummary] = useState<PracticeSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const status = (searchParams.get('status') as PracticeTaskStatus | null) ?? 'TODO';
  const category = searchParams.get('category') ?? '';

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [taskPage, taskSummary] = await Promise.all([
        practiceApi.getTasks({ status, category: category || undefined, size: 50 }),
        practiceApi.getSummary(),
      ]);
      setTasks(taskPage.items);
      setSummary(taskSummary);
    } catch (loadError) {
      setError(getErrorMessage(loadError));
    } finally {
      setLoading(false);
    }
  }, [status, category]);

  useEffect(() => {
    load();
  }, [load]);

  const categories = useMemo(
    () => Array.from(new Set(tasks.map((task) => task.category).filter(Boolean))),
    [tasks],
  );

  const changeFilter = (nextStatus: PracticeTaskStatus, nextCategory = category) => {
    const next = new URLSearchParams();
    next.set('status', nextStatus);
    if (nextCategory) next.set('category', nextCategory);
    setSearchParams(next);
  };

  const restore = async (task: PracticeTaskItem) => {
    await practiceApi.updateStatus(task.id, 'TODO');
    load();
  };

  const summaryItems = [
    ['待复练', summary?.todoCount ?? '-', 'text-amber-600 dark:text-amber-400'],
    ['已完成', summary?.completedCount ?? '-', 'text-emerald-600 dark:text-emerald-400'],
    ['本周完成', summary?.completedThisWeek ?? '-', 'text-primary-600 dark:text-primary-400'],
    [
      '平均提升',
      summary?.averageImprovement == null
        ? '-'
        : `${summary.averageImprovement >= 0 ? '+' : ''}${summary.averageImprovement} 分`,
      'text-primary-600 dark:text-primary-400',
    ],
  ];

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        kicker="成长训练"
        title="错题复练"
        description="把低分题重新答好，看到真实、可积累的提升。"
        actions={
          <Link
            to="/interview-statistics"
            className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-primary-600 transition hover:text-primary-500 dark:text-primary-400"
          >
            查看能力统计
            <ArrowRight className="h-4 w-4" aria-hidden />
          </Link>
        }
      />

      <section className="ui-panel grid grid-cols-2 divide-x divide-y divide-[var(--shell-border)] overflow-hidden sm:grid-cols-4 sm:divide-y-0">
        {summaryItems.map(([label, value, tone]) => (
          <div key={String(label)} className="px-4 py-5 sm:px-5">
            <p className="text-xs text-[var(--shell-muted)]">{label}</p>
            <p className={`mt-2 text-2xl font-semibold tabular-nums ${tone}`}>{value}</p>
          </div>
        ))}
      </section>

      <section className="ui-panel overflow-hidden">
        <div className="flex flex-wrap items-center gap-x-1 gap-y-2 border-b border-[var(--shell-border)] px-3 py-3 sm:px-4">
          {(['TODO', 'COMPLETED', 'IGNORED'] as PracticeTaskStatus[]).map((item) => (
            <button
              key={item}
              type="button"
              onClick={() => changeFilter(item)}
              className={`min-h-11 rounded-xl px-3 text-sm transition ${
                status === item
                  ? 'bg-[var(--shell-active)] font-medium text-primary-700 dark:text-primary-300'
                  : 'text-[var(--shell-muted)] hover:bg-[var(--shell-hover)] hover:text-[var(--shell-text)]'
              }`}
            >
              {labels[item]}
            </button>
          ))}
          {categories.length > 0 && (
            <span className="mx-2 hidden h-4 w-px bg-[var(--shell-border)] sm:block" aria-hidden />
          )}
          {categories.map((item) => (
            <button
              key={item}
              type="button"
              onClick={() => changeFilter(status, category === item ? '' : item)}
              className={`min-h-11 rounded-xl px-3 text-sm transition ${
                category === item
                  ? 'bg-[var(--shell-active)] text-primary-700 dark:text-primary-300'
                  : 'text-[var(--shell-muted)] hover:bg-[var(--shell-hover)]'
              }`}
            >
              {item}
            </button>
          ))}
        </div>

        {loading ? (
          <div className="flex min-h-64 items-center justify-center" role="status" aria-live="polite">
            <Loader2 className="h-7 w-7 animate-spin text-primary-500" aria-hidden />
            <span className="sr-only">加载复练任务</span>
          </div>
        ) : error ? (
          <div className="flex min-h-64 flex-col items-center justify-center p-8 text-center">
            <AlertCircle className="h-9 w-9 text-red-500" aria-hidden />
            <p className="mt-3 text-sm text-[var(--shell-text)]">{error}</p>
            <button type="button" onClick={load} className="ui-btn-primary mt-4">
              重试
            </button>
          </div>
        ) : tasks.length === 0 ? (
          <div className="flex min-h-72 flex-col items-center justify-center px-6 text-center">
            <Sparkles className="h-9 w-9 text-primary-500" aria-hidden />
            <h2 className="mt-4 text-lg font-semibold text-[var(--shell-text)]">
              {status === 'TODO' ? '暂时没有待复练错题' : '暂无对应任务'}
            </h2>
            <p className="mt-2 max-w-md text-sm leading-6 text-[var(--shell-muted)]">
              完成一次带评分的模拟面试后，低于 60 分的问题会自动在这里出现。
            </p>
            <Link
              to="/history"
              className="mt-5 inline-flex min-h-11 items-center gap-2 text-sm font-medium text-primary-600 dark:text-primary-400"
            >
              去开始模拟面试
              <ArrowRight className="h-4 w-4" aria-hidden />
            </Link>
          </div>
        ) : (
          <div className="divide-y divide-[var(--shell-border)]">
            {tasks.map((task) => (
              <article
                key={task.id}
                className="group grid gap-5 p-5 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:px-6"
              >
                <div>
                  <div className="flex flex-wrap items-center gap-3">
                    <span className="text-xs text-[var(--shell-muted)]">{task.category || '综合'}</span>
                    <span className="h-3 w-px bg-[var(--shell-border)]" aria-hidden />
                    <span className="text-xs text-[var(--shell-muted)]">已练 {task.attemptCount} 次</span>
                  </div>
                  <h2 className="mt-3 text-base font-medium leading-7 text-[var(--shell-text)]">{task.question}</h2>
                  <div className="mt-3 flex flex-wrap gap-x-5 gap-y-1 text-sm text-[var(--shell-muted)]">
                    <span>
                      原分 <b className="ml-1 text-amber-600 dark:text-amber-400">{task.originalScore}</b>
                    </span>
                    <span>
                      最近{' '}
                      <b className="ml-1 text-primary-600 dark:text-primary-400">{task.lastScore ?? '-'}</b>
                    </span>
                    {task.improvement != null && (
                      <span className={task.improvement >= 0 ? 'text-emerald-600' : 'text-red-500'}>
                        {task.improvement >= 0 ? '+' : ''}
                        {task.improvement} 分
                      </span>
                    )}
                  </div>
                </div>
                {task.status === 'IGNORED' ? (
                  <button type="button" onClick={() => restore(task)} className="ui-btn-ghost w-fit">
                    <RotateCcw className="h-4 w-4" aria-hidden />
                    恢复
                  </button>
                ) : (
                  <Link to={`/practice/${task.id}`} className="ui-btn-primary w-fit">
                    {task.status === 'COMPLETED' ? (
                      <CheckCircle2 className="h-4 w-4" aria-hidden />
                    ) : (
                      <CircleDashed className="h-4 w-4" aria-hidden />
                    )}
                    {task.status === 'COMPLETED' ? '再次练习' : '开始复练'}
                    <ArrowRight className="h-4 w-4" aria-hidden />
                  </Link>
                )}
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
