import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertCircle, ChevronRight, FileText, Loader2, Plus, Search, Trash2 } from 'lucide-react';
import { historyApi, ResumeListItem } from '../api/history';
import DeleteConfirmDialog from '../components/DeleteConfirmDialog';
import PageHeader from '../components/PageHeader';
import { formatDateOnly } from '../utils/date';

interface HistoryListProps {
  onSelectResume: (id: number) => void;
}

function statusMeta(resume: ResumeListItem) {
  if (resume.analyzeStatus === 'FAILED') {
    return { label: '分析失败', className: 'bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-300' };
  }
  if (resume.analyzeStatus === 'PROCESSING') {
    return { label: '正在分析', className: 'bg-primary-50 text-primary-700 dark:bg-primary-500/10 dark:text-primary-300' };
  }
  if (resume.analyzeStatus === 'PENDING' || resume.latestScore === undefined) {
    return { label: '等待分析', className: 'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300' };
  }
  return { label: '分析完成', className: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300' };
}

function scoreTone(score?: number) {
  if (score === undefined) return 'bg-slate-300 dark:bg-slate-600';
  if (score >= 80) return 'bg-emerald-500';
  if (score >= 60) return 'bg-primary-600';
  return 'bg-amber-500';
}

export default function HistoryList({ onSelectResume }: HistoryListProps) {
  const [resumes, setResumes] = useState<ResumeListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<{ id: number; filename: string } | null>(null);

  const loadResumes = async () => {
    setLoading(true);
    setError('');
    try {
      setResumes(await historyApi.getResumes());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '简历库加载失败，请稍后重试。');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadResumes();
  }, []);

  const filteredResumes = useMemo(
    () => resumes.filter((resume) => resume.filename.toLowerCase().includes(searchTerm.toLowerCase())),
    [resumes, searchTerm],
  );
  const completedCount = useMemo(
    () => resumes.filter((resume) => resume.latestScore !== undefined).length,
    [resumes],
  );

  const confirmDelete = async () => {
    if (!deleteConfirm) return;
    setDeletingId(deleteConfirm.id);
    try {
      await historyApi.deleteResume(deleteConfirm.id);
      await loadResumes();
      setDeleteConfirm(null);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '删除失败，请稍后重试。');
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        kicker="训练资料"
        title="简历库"
        description="查看分析状态、进入详情，从任意一份简历继续模拟面试。"
        actions={
          <Link to="/upload" className="ui-btn-primary">
            <Plus className="h-4 w-4" aria-hidden />
            上传简历
          </Link>
        }
      />

      <section className="ui-panel px-5 py-4 sm:flex sm:items-center sm:justify-between sm:px-6">
        <div className="flex gap-7">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.12em] text-[var(--shell-subtle)]">资料数量</p>
            <p className="mt-1 text-2xl font-semibold tabular-nums text-[var(--shell-text)]">{resumes.length}</p>
          </div>
          <div className="border-l border-[var(--shell-border)] pl-7">
            <p className="text-xs font-semibold uppercase tracking-[0.12em] text-[var(--shell-subtle)]">已完成分析</p>
            <p className="mt-1 text-2xl font-semibold tabular-nums text-primary-600 dark:text-primary-400">{completedCount}</p>
          </div>
        </div>
        <p className="mt-4 text-sm text-[var(--shell-muted)] sm:mt-0">点击任意一行查看简历、报告和面试记录。</p>
      </section>

      <section className="ui-panel overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[var(--shell-border)] p-5 sm:px-6">
          <div>
            <h2 className="text-lg font-semibold text-[var(--shell-text)]">资料列表</h2>
            <p className="mt-1 text-sm text-[var(--shell-muted)]">按文件名快速检索你的训练资料。</p>
          </div>
          <label className="ui-search sm:w-72">
            <Search className="h-4 w-4 shrink-0 text-[var(--shell-subtle)]" aria-hidden />
            <input
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              placeholder="搜索简历"
              aria-label="搜索简历"
            />
          </label>
        </div>

        {loading ? (
          <div className="flex min-h-72 items-center justify-center" role="status" aria-live="polite">
            <Loader2 className="h-6 w-6 animate-spin text-primary-600" aria-hidden />
            <span className="sr-only">加载简历列表</span>
          </div>
        ) : error ? (
          <div className="flex min-h-72 flex-col items-center justify-center p-6 text-center">
            <AlertCircle className="h-8 w-8 text-red-600" aria-hidden />
            <p className="mt-3 text-sm text-[var(--shell-muted)]">{error}</p>
            <button type="button" onClick={loadResumes} className="mt-4 text-sm font-semibold text-primary-600 dark:text-primary-400">
              重新加载
            </button>
          </div>
        ) : !filteredResumes.length ? (
          <div className="flex min-h-72 flex-col items-center justify-center p-6 text-center">
            <FileText className="h-9 w-9 text-[var(--shell-subtle)]" aria-hidden />
            <p className="mt-4 font-medium text-[var(--shell-text)]">
              {resumes.length ? '没有匹配的简历' : '还没有上传简历'}
            </p>
            <p className="mt-1 text-sm text-[var(--shell-muted)]">
              {resumes.length ? '换一个关键词试试。' : '上传一份 PDF、Word 或 TXT 简历，开始建立训练档案。'}
            </p>
            {!resumes.length && (
              <Link to="/upload" className="ui-btn-primary mt-5">
                上传第一份简历
              </Link>
            )}
          </div>
        ) : (
          <div className="divide-y divide-[var(--shell-border)]">
            {filteredResumes.map((resume) => {
              const status = statusMeta(resume);
              return (
                <article
                  key={resume.id}
                  onClick={() => onSelectResume(resume.id)}
                  className="group grid cursor-pointer gap-4 p-5 transition hover:bg-[var(--shell-hover)] sm:grid-cols-[minmax(0,1.5fr)_130px_120px_150px_auto] sm:items-center sm:px-6"
                >
                  <div className="flex min-w-0 items-center gap-3">
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary-50 text-primary-600 dark:bg-primary-500/10 dark:text-primary-400">
                      <FileText className="h-5 w-5" aria-hidden />
                    </div>
                    <div className="min-w-0">
                      <p className="truncate font-medium text-[var(--shell-text)]">{resume.filename}</p>
                      <p className="mt-1 text-xs text-[var(--shell-muted)]">
                        上传于 {formatDateOnly(resume.uploadedAt)} · 已访问 {resume.accessCount} 次
                      </p>
                    </div>
                  </div>
                  <div>
                    <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${status.className}`}>
                      {status.label}
                    </span>
                  </div>
                  <div>
                    <p className="text-xs text-[var(--shell-subtle)]">AI 评分</p>
                    <p className="mt-1 text-sm font-semibold text-[var(--shell-text)]">
                      {resume.latestScore === undefined ? '—' : `${resume.latestScore} 分`}
                    </p>
                    {resume.latestScore !== undefined && (
                      <div className="mt-2 h-1 w-20 overflow-hidden rounded-full bg-[var(--shell-hover)]">
                        <div
                          className={`h-full rounded-full ${scoreTone(resume.latestScore)}`}
                          style={{ width: `${resume.latestScore}%` }}
                        />
                      </div>
                    )}
                  </div>
                  <div>
                    <p className="text-xs text-[var(--shell-subtle)]">模拟面试</p>
                    <p className="mt-1 text-sm font-semibold text-[var(--shell-text)]">
                      {resume.interviewCount ? `${resume.interviewCount} 场` : '尚未开始'}
                    </p>
                  </div>
                  <div className="flex items-center justify-end gap-1">
                    <button
                      type="button"
                      aria-label={`删除 ${resume.filename}`}
                      onClick={(event) => {
                        event.stopPropagation();
                        setDeleteConfirm({ id: resume.id, filename: resume.filename });
                      }}
                      disabled={deletingId === resume.id}
                      className="ui-icon-btn-danger"
                    >
                      {deletingId === resume.id ? (
                        <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                      ) : (
                        <Trash2 className="h-4 w-4" aria-hidden />
                      )}
                    </button>
                    <ChevronRight
                      className="h-5 w-5 text-[var(--shell-subtle)] transition group-hover:translate-x-0.5 group-hover:text-primary-600"
                      aria-hidden
                    />
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>

      <DeleteConfirmDialog
        open={deleteConfirm !== null}
        item={deleteConfirm}
        itemType="简历"
        loading={deletingId !== null}
        onConfirm={confirmDelete}
        onCancel={() => setDeleteConfirm(null)}
        customMessage={
          deleteConfirm ? (
            <>
              <p>
                确定要删除 <strong>{deleteConfirm.filename}</strong> 吗？
              </p>
              <p className="mt-2 text-sm text-slate-500">
                对应的分析记录和模拟面试记录也会被删除，此操作无法恢复。
              </p>
            </>
          ) : undefined
        }
      />
    </div>
  );
}
