import { useEffect, useMemo, useState } from 'react';
import { BriefcaseBusiness, Check, ChevronRight, Loader2, Plus, Target, Trash2 } from 'lucide-react';
import { jobTargetApi, type JobTarget } from '../api/jobTarget';

const empty = { title: '', company: '', jobDescription: '' };

function jdSummary(description: string) {
  const normalized = description.replace(/\s+/g, ' ').trim();
  return normalized.length > 180 ? `${normalized.slice(0, 180)}…` : normalized;
}

export default function JobTargetsPage() {
  const [targets, setTargets] = useState<JobTarget[]>([]);
  const [form, setForm] = useState(empty);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const load = () => jobTargetApi.list().then(setTargets).catch(() => setError('岗位目标加载失败，请稍后重试。'));
  useEffect(() => { load(); }, []);

  const activeTarget = useMemo(() => targets.find((target) => target.active), [targets]);
  const create = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      await jobTargetApi.create(form);
      setForm(empty);
      await load();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '保存失败，请稍后重试。');
    } finally {
      setSaving(false);
    }
  };

  const activate = async (id: number) => {
    try { await jobTargetApi.activate(id); await load(); } catch (requestError) { setError(requestError instanceof Error ? requestError.message : '切换当前岗位失败。'); }
  };

  const remove = async (id: number) => {
    if (!confirm('删除该岗位目标？')) return;
    try { await jobTargetApi.remove(id); await load(); } catch (requestError) { setError(requestError instanceof Error ? requestError.message : '删除失败，请稍后重试。'); }
  };

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <section className="grid overflow-hidden border border-[var(--shell-border)] bg-[var(--shell-panel)] xl:grid-cols-[1.2fr_0.8fr]">
        <div className="p-6 sm:p-8">
          <div className="flex items-start justify-between gap-5">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary-600 dark:text-primary-400">训练方向</p>
              <h1 className="mt-2 text-3xl font-semibold tracking-tight text-[var(--shell-text)]">让每次练习都对准一个岗位。</h1>
              <p className="mt-3 max-w-xl text-sm leading-6 text-[var(--shell-muted)]">当前岗位会作为简历优化、面试出题和能力复盘的共同上下文。选择一个最想拿下的机会，再开始训练。</p>
            </div>
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary-600 text-white"><Target className="h-5 w-5" /></div>
          </div>
          <div className="mt-8 border-t border-[var(--shell-border)] pt-5">
            <p className="text-xs font-semibold uppercase tracking-[0.16em] text-[var(--shell-subtle)]">当前聚焦</p>
            {activeTarget ? (
              <div className="mt-3 flex items-center justify-between gap-4">
                <div><p className="text-lg font-semibold text-[var(--shell-text)]">{activeTarget.title}</p><p className="mt-1 text-sm text-[var(--shell-muted)]">{activeTarget.company || '未填写公司'} · 已用于后续训练</p></div>
                <span className="rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-semibold text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300">正在使用</span>
              </div>
            ) : <p className="mt-3 text-sm text-[var(--shell-muted)]">尚未设置。先粘贴一份 JD，让系统知道你正在准备什么。</p>}
          </div>
        </div>
        <div className="border-t border-[var(--shell-border)] bg-[var(--shell-hover)] p-6 xl:border-l xl:border-t-0 sm:p-8">
          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-[var(--shell-subtle)]">使用方式</p>
          <div className="mt-5 space-y-5">
            {['保存目标岗位与 JD', '上传或编辑简历', '开始针对性模拟与复练'].map((item, index) => <div key={item} className="flex gap-3"><span className="text-sm font-semibold text-primary-600 dark:text-primary-400">0{index + 1}</span><p className="text-sm leading-6 text-[var(--shell-muted)]">{item}</p></div>)}
          </div>
        </div>
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <section className="border border-[var(--shell-border)] bg-[var(--shell-panel)] p-5 sm:p-7">
          <div className="flex items-center justify-between gap-4 border-b border-[var(--shell-border)] pb-4"><div><h2 className="text-lg font-semibold text-[var(--shell-text)]">岗位清单</h2><p className="mt-1 text-sm text-[var(--shell-muted)]">保留多个方向，但一次只聚焦一个。</p></div><span className="text-sm tabular-nums text-[var(--shell-muted)]">{targets.length} 个目标</span></div>
          <div className="divide-y divide-[var(--shell-border)]">
            {targets.map((target) => (
              <article key={target.id} className="group py-5">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold text-[var(--shell-text)]">{target.title}</h3>{target.active && <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300">当前目标</span>}</div><p className="mt-1 text-sm text-[var(--shell-muted)]">{target.company || '未填写公司'}</p></div>
                  <div className="flex shrink-0 items-center gap-1"><button type="button" onClick={() => activate(target.id)} disabled={target.active} className="rounded-md px-2.5 py-1.5 text-xs font-semibold text-primary-600 hover:bg-primary-50 disabled:cursor-default disabled:opacity-40 dark:text-primary-400 dark:hover:bg-primary-500/10"><Check className="mr-1 inline h-3.5 w-3.5" />设为当前</button><button type="button" onClick={() => remove(target.id)} className="rounded-md p-1.5 text-[var(--shell-subtle)] hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-500/10" aria-label={`删除 ${target.title}`}><Trash2 className="h-4 w-4" /></button></div>
                </div>
                <p className="mt-3 max-w-3xl text-sm leading-6 text-[var(--shell-muted)]">{jdSummary(target.jobDescription)}</p>
              </article>
            ))}
            {!targets.length && <div className="py-12 text-center"><BriefcaseBusiness className="mx-auto h-8 w-8 text-[var(--shell-subtle)]" /><p className="mt-3 text-sm font-medium text-[var(--shell-text)]">还没有岗位目标</p><p className="mt-1 text-sm text-[var(--shell-muted)]">在右侧粘贴第一份 JD，建立训练方向。</p></div>}
          </div>
        </section>

        <section className="border border-[var(--shell-border)] bg-[var(--shell-panel)] p-5 sm:p-6">
          <div className="flex items-center gap-2"><Plus className="h-4 w-4 text-primary-600 dark:text-primary-400" /><h2 className="text-lg font-semibold text-[var(--shell-text)]">新增岗位目标</h2></div>
          <p className="mt-2 text-sm leading-6 text-[var(--shell-muted)]">尽量粘贴完整 JD。职责与要求越具体，后续训练越贴近真实面试。</p>
          <form onSubmit={create} className="mt-6 space-y-4">
            <label className="block"><span className="mb-2 block text-sm font-medium text-[var(--shell-text)]">岗位名称</span><input required maxLength={120} value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} placeholder="例如：Java 后端工程师" className="w-full rounded-lg border border-[var(--shell-border)] bg-[var(--shell-bg)] px-3 py-2.5 text-sm text-[var(--shell-text)] outline-none placeholder:text-[var(--shell-subtle)] focus:border-primary-500 focus:ring-2 focus:ring-primary-500/15" /></label>
            <label className="block"><span className="mb-2 block text-sm font-medium text-[var(--shell-text)]">公司 <span className="font-normal text-[var(--shell-subtle)]">可选</span></span><input maxLength={120} value={form.company} onChange={(event) => setForm({ ...form, company: event.target.value })} placeholder="例如：某科技公司" className="w-full rounded-lg border border-[var(--shell-border)] bg-[var(--shell-bg)] px-3 py-2.5 text-sm text-[var(--shell-text)] outline-none placeholder:text-[var(--shell-subtle)] focus:border-primary-500 focus:ring-2 focus:ring-primary-500/15" /></label>
            <label className="block"><span className="mb-2 block text-sm font-medium text-[var(--shell-text)]">职位描述</span><textarea required maxLength={30000} value={form.jobDescription} onChange={(event) => setForm({ ...form, jobDescription: event.target.value })} placeholder="粘贴职责、任职要求和加分项…" className="min-h-44 w-full resize-y rounded-lg border border-[var(--shell-border)] bg-[var(--shell-bg)] px-3 py-2.5 text-sm leading-6 text-[var(--shell-text)] outline-none placeholder:text-[var(--shell-subtle)] focus:border-primary-500 focus:ring-2 focus:ring-primary-500/15" /></label>
            {error && <p className="text-sm text-red-600 dark:text-red-300">{error}</p>}
            <button disabled={saving} className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-primary-700 disabled:opacity-60">{saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <ChevronRight className="h-4 w-4" />}{saving ? '正在保存…' : '保存并用于训练'}</button>
          </form>
        </section>
      </div>
    </div>
  );
}