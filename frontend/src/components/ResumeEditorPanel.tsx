import { useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  Check,
  FilePenLine,
  History,
  Loader2,
  RotateCcw,
  Save,
  X,
} from 'lucide-react';
import { historyApi, type ResumeVersion } from '../api/history';
import { formatDateTime } from '../utils/date';

interface ResumeEditorPanelProps {
  resumeId: number;
  /** 当前生效的简历正文 */
  resumeText: string;
  /** 保存/恢复成功后回调（刷新详情） */
  onContentUpdated: () => Promise<void> | void;
  /** 外部预填（例如 JD 优化稿） */
  externalDraft?: { title: string; content: string } | null;
  onExternalDraftConsumed?: () => void;
}

const sourceLabel: Record<string, string> = {
  UPLOAD: '原始上传',
  EDIT: '手动编辑',
  RESTORE: '版本恢复',
};

/**
 * 简历正文编辑与版本管理：保存新建版本，可恢复历史版本。
 */
export default function ResumeEditorPanel({
  resumeId,
  resumeText,
  onContentUpdated,
  externalDraft,
  onExternalDraftConsumed,
}: ResumeEditorPanelProps) {
  const [versions, setVersions] = useState<ResumeVersion[]>([]);
  const [loadingVersions, setLoadingVersions] = useState(true);
  const [editing, setEditing] = useState(false);
  const [draftTitle, setDraftTitle] = useState('手动优化');
  const [draftContent, setDraftContent] = useState(resumeText);
  const [saving, setSaving] = useState(false);
  const [restoringId, setRestoringId] = useState<number | null>(null);
  const [message, setMessage] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);
  const [previewVersion, setPreviewVersion] = useState<ResumeVersion | null>(null);

  const loadVersions = async () => {
    setLoadingVersions(true);
    try {
      setVersions(await historyApi.getVersions(resumeId));
    } catch {
      setVersions([]);
    } finally {
      setLoadingVersions(false);
    }
  };

  useEffect(() => {
    loadVersions();
  }, [resumeId]);

  // 外部注入草稿（JD 优化）时自动进入编辑
  useEffect(() => {
    if (!externalDraft?.content) return;
    setDraftTitle(externalDraft.title || 'JD 优化版');
    setDraftContent(externalDraft.content);
    setEditing(true);
    setMessage({ type: 'ok', text: '已载入优化稿，确认后点击保存为新版本。' });
    onExternalDraftConsumed?.();
  }, [externalDraft, onExternalDraftConsumed]);

  // 非编辑态时同步最新正文
  useEffect(() => {
    if (!editing) {
      setDraftContent(resumeText);
    }
  }, [resumeText, editing]);

  const dirty = useMemo(
    () => editing && draftContent.trim() !== (resumeText || '').trim(),
    [editing, draftContent, resumeText],
  );

  const charCount = draftContent.length;
  const wordApprox = draftContent.trim() ? draftContent.trim().split(/\s+/).length : 0;

  const startEdit = () => {
    setDraftTitle('手动优化');
    setDraftContent(resumeText || '');
    setEditing(true);
    setMessage(null);
    setPreviewVersion(null);
  };

  const cancelEdit = () => {
    if (dirty && !window.confirm('有未保存的修改，确定放弃吗？')) return;
    setEditing(false);
    setDraftContent(resumeText || '');
    setMessage(null);
  };

  const save = async () => {
    if (!draftContent.trim()) {
      setMessage({ type: 'err', text: '简历正文不能为空' });
      return;
    }
    setSaving(true);
    setMessage(null);
    try {
      await historyApi.updateContent(resumeId, draftTitle.trim() || '手动优化', draftContent.trim());
      setEditing(false);
      setMessage({ type: 'ok', text: '已保存为新版本，后续分析与面试将使用最新正文。' });
      await Promise.all([onContentUpdated(), loadVersions()]);
    } catch (err) {
      setMessage({ type: 'err', text: err instanceof Error ? err.message : '保存失败，请稍后重试' });
    } finally {
      setSaving(false);
    }
  };

  const restore = async (version: ResumeVersion) => {
    if (version.id == null) return;
    if (!window.confirm(`确定恢复「v${version.versionNumber} · ${version.title}」吗？将另存为新版本。`)) return;
    setRestoringId(version.id);
    setMessage(null);
    try {
      await historyApi.restoreVersion(resumeId, version.id);
      setEditing(false);
      setPreviewVersion(null);
      setMessage({ type: 'ok', text: `已恢复版本 v${version.versionNumber}，并生成新快照。` });
      await Promise.all([onContentUpdated(), loadVersions()]);
    } catch (err) {
      setMessage({ type: 'err', text: err instanceof Error ? err.message : '恢复失败' });
    } finally {
      setRestoringId(null);
    }
  };

  const displayText = previewVersion?.content ?? resumeText;

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_20rem]">
      {/* 主编辑区 */}
      <section className="ui-panel overflow-hidden">
        <div className="flex flex-wrap items-start justify-between gap-4 border-b border-[var(--shell-border)] px-5 py-4 sm:px-6">
          <div>
            <p className="page-kicker text-primary-700 dark:text-primary-400">正文与版本</p>
            <h2 className="font-display mt-1 text-xl font-semibold tracking-tight text-[var(--shell-text)]">
              简历编辑
            </h2>
            <p className="mt-1 text-sm text-[var(--shell-muted)]">
              保存会新建版本；原始上传内容会保留在版本历史中。
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {!editing ? (
              <button type="button" onClick={startEdit} className="ui-btn-primary">
                <FilePenLine className="h-4 w-4" aria-hidden />
                编辑正文
              </button>
            ) : (
              <>
                <button type="button" onClick={cancelEdit} disabled={saving} className="ui-btn-ghost">
                  <X className="h-4 w-4" aria-hidden />
                  取消
                </button>
                <button
                  type="button"
                  onClick={save}
                  disabled={saving || !draftContent.trim()}
                  aria-busy={saving}
                  className="ui-btn-primary"
                >
                  {saving ? (
                    <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                  ) : (
                    <Save className="h-4 w-4" aria-hidden />
                  )}
                  {saving ? '保存中…' : '保存为新版本'}
                </button>
              </>
            )}
          </div>
        </div>

        {message && (
          <div
            role={message.type === 'err' ? 'alert' : 'status'}
            className={`mx-5 mt-4 flex items-start gap-2 rounded-xl border px-3 py-2.5 text-sm sm:mx-6 ${
              message.type === 'ok'
                ? 'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-800/50 dark:bg-emerald-900/20 dark:text-emerald-300'
                : 'border-red-200 bg-red-50 text-red-700 dark:border-red-800/50 dark:bg-red-900/20 dark:text-red-300'
            }`}
          >
            {message.type === 'ok' ? (
              <Check className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
            ) : (
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
            )}
            {message.text}
          </div>
        )}

        {editing ? (
          <div className="space-y-4 p-5 sm:p-6">
            <label className="block">
              <span className="mb-1.5 block text-sm font-medium text-[var(--shell-text)]">版本名称</span>
              <input
                value={draftTitle}
                maxLength={120}
                onChange={(e) => setDraftTitle(e.target.value)}
                placeholder="例如：针对 Java 后端的优化稿"
                className="ui-input"
              />
            </label>
            <label className="block">
              <span className="mb-1.5 flex items-center justify-between text-sm font-medium text-[var(--shell-text)]">
                <span>简历正文</span>
                <span className="font-normal tabular-nums text-[var(--shell-subtle)]">
                  {charCount.toLocaleString()} 字
                  {wordApprox > 0 ? ` · 约 ${wordApprox} 词` : ''}
                  {dirty ? ' · 未保存' : ''}
                </span>
              </span>
              <textarea
                value={draftContent}
                onChange={(e) => setDraftContent(e.target.value)}
                rows={18}
                className="ui-input min-h-[22rem] resize-y font-mono text-[13px] leading-6"
                placeholder="在此粘贴或编辑完整简历文本…"
                aria-label="简历正文"
              />
            </label>
            <p className="text-xs text-[var(--shell-muted)]">
              提示：保存后若需要更新 AI 分析，请到「简历分析」页重新分析。
            </p>
          </div>
        ) : (
          <div className="p-5 sm:p-6">
            {previewVersion && (
              <div className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-primary-200 bg-primary-50/80 px-3 py-2 text-sm text-primary-800 dark:border-primary-800 dark:bg-primary-900/30 dark:text-primary-200">
                <span>
                  正在预览 v{previewVersion.versionNumber} · {previewVersion.title}
                  {previewVersion.current ? '（当前）' : ''}
                </span>
                <button
                  type="button"
                  className="font-medium underline-offset-2 hover:underline"
                  onClick={() => setPreviewVersion(null)}
                >
                  回到当前正文
                </button>
              </div>
            )}
            <div className="max-h-[32rem] overflow-y-auto rounded-xl border border-[var(--shell-border)] bg-[var(--shell-hover)]/30 p-4">
              {displayText?.trim() ? (
                <pre className="whitespace-pre-wrap font-sans text-sm leading-7 text-[var(--shell-text)]">
                  {displayText}
                </pre>
              ) : (
                <p className="text-sm text-[var(--shell-muted)]">暂无正文内容，点击「编辑正文」开始填写。</p>
              )}
            </div>
          </div>
        )}
      </section>

      {/* 版本历史 */}
      <aside className="ui-panel flex max-h-[40rem] flex-col overflow-hidden xl:max-h-none">
        <div className="border-b border-[var(--shell-border)] px-5 py-4">
          <div className="flex items-center gap-2">
            <History className="h-4 w-4 text-primary-600 dark:text-primary-400" aria-hidden />
            <h3 className="font-semibold text-[var(--shell-text)]">版本历史</h3>
          </div>
          <p className="mt-1 text-xs text-[var(--shell-muted)]">点击可预览；恢复会生成新版本。</p>
        </div>
        <div className="flex-1 overflow-y-auto p-3 scrollbar-thin">
          {loadingVersions ? (
            <div className="flex justify-center py-10" role="status">
              <Loader2 className="h-5 w-5 animate-spin text-primary-500" aria-hidden />
            </div>
          ) : versions.length === 0 ? (
            <p className="px-2 py-8 text-center text-sm text-[var(--shell-muted)]">暂无版本记录</p>
          ) : (
            <ul className="space-y-2">
              {versions.map((version) => {
                const activePreview = previewVersion?.id === version.id
                  || (previewVersion == null && version.current);
                return (
                  <li key={version.id ?? `v-${version.versionNumber}`}>
                    <div
                      className={`rounded-xl border p-3 transition ${
                        activePreview
                          ? 'border-primary-400 bg-primary-50/80 dark:border-primary-700 dark:bg-primary-900/25'
                          : 'border-[var(--shell-border)] hover:bg-[var(--shell-hover)]'
                      }`}
                    >
                      <button
                        type="button"
                        className="w-full text-left"
                        onClick={() => setPreviewVersion(version.current ? null : version)}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="text-sm font-semibold text-[var(--shell-text)]">
                            v{version.versionNumber} · {version.title}
                          </span>
                          {version.current && (
                            <span className="shrink-0 rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-semibold text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300">
                              当前
                            </span>
                          )}
                        </div>
                        <p className="mt-1 text-xs text-[var(--shell-muted)]">
                          {sourceLabel[version.source] || version.source} · {formatDateTime(version.createdAt)}
                        </p>
                      </button>
                      {version.id != null && !version.current && (
                        <button
                          type="button"
                          onClick={() => restore(version)}
                          disabled={restoringId === version.id}
                          className="mt-2 inline-flex min-h-9 items-center gap-1.5 text-xs font-semibold text-primary-600 hover:text-primary-500 disabled:opacity-50 dark:text-primary-400"
                        >
                          {restoringId === version.id ? (
                            <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden />
                          ) : (
                            <RotateCcw className="h-3.5 w-3.5" aria-hidden />
                          )}
                          恢复此版本
                        </button>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </aside>
    </div>
  );
}
