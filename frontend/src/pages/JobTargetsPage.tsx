import { useEffect, useState } from 'react';
import { BriefcaseBusiness, Check, Plus, Trash2 } from 'lucide-react';
import { jobTargetApi, type JobTarget } from '../api/jobTarget';

const empty = { title: '', company: '', jobDescription: '' };

export default function JobTargetsPage() {
  const [targets, setTargets] = useState<JobTarget[]>([]);
  const [form, setForm] = useState(empty);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const load = () => jobTargetApi.list().then(setTargets).catch(() => setError('岗位目标加载失败，请重试'));
  useEffect(() => { load(); }, []);
  const create = async (event: React.FormEvent) => {
    event.preventDefault(); setSaving(true); setError('');
    try { await jobTargetApi.create(form); setForm(empty); load(); } catch (e) { setError(e instanceof Error ? e.message : '保存失败'); } finally { setSaving(false); }
  };
  return <div className="max-w-5xl mx-auto space-y-6">
    <div><h1 className="text-2xl font-bold text-slate-900 dark:text-white flex items-center gap-2"><BriefcaseBusiness className="text-primary-500" />目标岗位</h1><p className="mt-1 text-slate-500">保存 JD，匹配简历并用于生成更贴近岗位的面试题。</p></div>
    <form onSubmit={create} className="rounded-2xl bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 p-5 space-y-4">
      <div className="grid sm:grid-cols-2 gap-3"><input required maxLength={120} value={form.title} onChange={e => setForm({...form, title:e.target.value})} placeholder="岗位名称，例如 Java 后端工程师" className="input" /><input maxLength={120} value={form.company} onChange={e => setForm({...form, company:e.target.value})} placeholder="公司（可选）" className="input" /></div>
      <textarea required maxLength={30000} value={form.jobDescription} onChange={e => setForm({...form, jobDescription:e.target.value})} placeholder="粘贴完整 JD，包括职责、要求和加分项" className="input min-h-36 resize-y" />
      {error && <p className="text-sm text-red-500">{error}</p>}<button disabled={saving} className="px-4 py-2 bg-primary-500 text-white rounded-xl disabled:opacity-60 flex gap-2 items-center"><Plus className="w-4 h-4" />{saving ? '保存中…' : '保存岗位目标'}</button>
    </form>
    <div className="grid md:grid-cols-2 gap-4">{targets.map(target => <article key={target.id} className="rounded-2xl bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 p-5"><div className="flex justify-between gap-3"><div><h2 className="font-semibold text-slate-900 dark:text-white">{target.title}</h2><p className="text-sm text-slate-500">{target.company || '未填写公司'}</p></div>{target.active && <span className="text-xs px-2 py-1 h-fit bg-emerald-100 text-emerald-700 rounded-full">当前目标</span>}</div><p className="mt-3 text-sm text-slate-600 dark:text-slate-300 whitespace-pre-wrap line-clamp-5">{target.jobDescription}</p><div className="mt-4 flex gap-3"><button onClick={async () => { await jobTargetApi.activate(target.id); load(); }} className="text-sm text-primary-600 flex items-center gap-1"><Check className="w-4 h-4" />设为当前</button><button onClick={async () => { if (confirm('删除该岗位目标？')) { await jobTargetApi.remove(target.id); load(); } }} className="text-sm text-red-500 flex items-center gap-1"><Trash2 className="w-4 h-4" />删除</button></div></article>)}</div>
    {!targets.length && <div className="text-center py-12 text-slate-500">还没有岗位目标，先粘贴一份 JD 开始匹配。</div>}
  </div>;
}
