import { ChangeEvent, DragEvent, KeyboardEvent, useCallback, useId, useState } from 'react';
import { AlertCircle, ArrowLeft, CheckCircle2, FileText, Loader2, UploadCloud, X } from 'lucide-react';

export interface FileUploadCardProps {
  title: string;
  subtitle: string;
  accept: string;
  formatHint: string;
  maxSizeHint: string;
  uploading?: boolean;
  uploadButtonText?: string;
  selectButtonText?: string;
  showNameInput?: boolean;
  namePlaceholder?: string;
  nameLabel?: string;
  error?: string;
  onFileSelect?: (file: File) => void;
  onUpload: (file: File, name?: string) => void;
  onBack?: () => void;
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function FileUploadCard({
  title,
  subtitle,
  accept,
  formatHint,
  maxSizeHint,
  uploading = false,
  uploadButtonText = '开始处理',
  selectButtonText = '选择文件',
  showNameInput = false,
  namePlaceholder = '留空则使用文件名',
  nameLabel = '名称（可选）',
  error,
  onFileSelect,
  onUpload,
  onBack,
}: FileUploadCardProps) {
  const inputId = useId();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [name, setName] = useState('');

  const chooseFile = useCallback((file?: File) => {
    if (!file) return;
    setSelectedFile(file);
    onFileSelect?.(file);
  }, [onFileSelect]);

  const handleDrop = useCallback((event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragOver(false);
    chooseFile(event.dataTransfer.files[0]);
  }, [chooseFile]);

  const handleChange = useCallback((event: ChangeEvent<HTMLInputElement>) => {
    chooseFile(event.target.files?.[0]);
  }, [chooseFile]);

  const openPicker = () => document.getElementById(inputId)?.click();
  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      openPicker();
    }
  };

  return (
    <div className="mx-auto max-w-6xl py-2">
      <div className="mb-8 flex items-start gap-4 border-b border-[var(--shell-border)] pb-6">
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-blue-600 text-white shadow-sm">
          <UploadCloud className="h-5 w-5" />
        </div>
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-blue-600 dark:text-blue-400">训练资料</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight text-[var(--shell-text)]">{title}</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-[var(--shell-muted)]">{subtitle}</p>
        </div>
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_280px]">
        <section className="border border-[var(--shell-border)] bg-[var(--shell-panel)] p-5 sm:p-7">
          <div
            role="button"
            tabIndex={0}
            onKeyDown={handleKeyDown}
            onDragOver={(event) => { event.preventDefault(); setDragOver(true); }}
            onDragLeave={(event) => { event.preventDefault(); setDragOver(false); }}
            onDrop={handleDrop}
            onClick={openPicker}
            className={`group relative min-h-[300px] cursor-pointer border border-dashed p-6 transition-colors sm:p-10 ${dragOver ? 'border-blue-500 bg-blue-50/70 dark:bg-blue-500/10' : 'border-[var(--shell-border)] bg-[var(--shell-hover)] hover:border-blue-400 hover:bg-blue-50/40 dark:hover:bg-blue-500/5'}`}
          >
            <input id={inputId} type="file" className="hidden" accept={accept} onChange={handleChange} disabled={uploading} />
            {selectedFile ? (
              <div className="mx-auto flex max-w-lg flex-col items-center text-center">
                <div className="mb-5 flex h-14 w-14 items-center justify-center rounded-xl bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300">
                  <FileText className="h-6 w-6" />
                </div>
                <p className="max-w-full truncate text-lg font-semibold text-[var(--shell-text)]">{selectedFile.name}</p>
                <p className="mt-1 text-sm text-[var(--shell-muted)]">已选择 · {formatFileSize(selectedFile.size)}</p>
                <div className="mt-6 flex items-center gap-3">
                  <button type="button" onClick={(event) => { event.stopPropagation(); openPicker(); }} className="rounded-lg border border-[var(--shell-border)] px-3 py-2 text-sm font-medium text-[var(--shell-text)] hover:bg-[var(--shell-hover)]">更换文件</button>
                  <button type="button" onClick={(event) => { event.stopPropagation(); setSelectedFile(null); setName(''); }} className="inline-flex items-center gap-1 rounded-lg px-3 py-2 text-sm font-medium text-red-600 hover:bg-red-50 dark:hover:bg-red-500/10"><X className="h-4 w-4" />移除</button>
                </div>
              </div>
            ) : (
              <div className="mx-auto flex max-w-lg flex-col items-center text-center">
                <div className="mb-5 flex h-14 w-14 items-center justify-center rounded-xl border border-[var(--shell-border)] bg-[var(--shell-panel)] text-blue-600 dark:text-blue-400">
                  <UploadCloud className="h-6 w-6" />
                </div>
                <h2 className="text-lg font-semibold text-[var(--shell-text)]">拖入文件，或从设备中选择</h2>
                <p className="mt-2 text-sm text-[var(--shell-muted)]">{formatHint} · {maxSizeHint}</p>
                <button type="button" onClick={(event) => { event.stopPropagation(); openPicker(); }} className="mt-6 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-blue-700">{selectButtonText}</button>
              </div>
            )}
          </div>

          {showNameInput && selectedFile && (
            <div className="mt-5 border-t border-[var(--shell-border)] pt-5">
              <label htmlFor={`${inputId}-name`} className="mb-2 block text-sm font-medium text-[var(--shell-text)]">{nameLabel}</label>
              <input id={`${inputId}-name`} value={name} onChange={(event) => setName(event.target.value)} placeholder={namePlaceholder} disabled={uploading} className="w-full rounded-lg border border-[var(--shell-border)] bg-[var(--shell-bg)] px-3 py-2.5 text-sm text-[var(--shell-text)] outline-none transition placeholder:text-[var(--shell-subtle)] focus:border-blue-500 focus:ring-2 focus:ring-blue-500/15" />
            </div>
          )}

          {error && <div className="mt-5 flex items-start gap-2 border border-red-200 bg-red-50 px-3 py-3 text-sm text-red-700 dark:border-red-500/25 dark:bg-red-500/10 dark:text-red-300"><AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />{error}</div>}

          <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-[var(--shell-border)] pt-5">
            {onBack ? <button type="button" onClick={onBack} className="inline-flex items-center gap-1.5 rounded-lg px-2 py-2 text-sm font-medium text-[var(--shell-muted)] hover:text-[var(--shell-text)]"><ArrowLeft className="h-4 w-4" />返回</button> : <span />}
            <button type="button" disabled={!selectedFile || uploading} onClick={() => selectedFile && onUpload(selectedFile, name.trim() || undefined)} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-45">
              {uploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />}
              {uploading ? '正在处理…' : uploadButtonText}
            </button>
          </div>
        </section>

        <aside className="border border-[var(--shell-border)] bg-[var(--shell-panel)] p-5">
          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-[var(--shell-subtle)]">处理流程</p>
          <ol className="mt-5 space-y-5">
            {[
              ['01', '上传资料', '安全保存原始文件'],
              ['02', 'AI 提取内容', '识别经历、技能与项目'],
              ['03', '开始训练', '生成贴合背景的面试题'],
            ].map(([number, heading, detail], index) => (
              <li key={number} className="flex gap-3">
                <span className={`mt-0.5 text-xs font-semibold ${index === 0 ? 'text-blue-600 dark:text-blue-400' : 'text-[var(--shell-subtle)]'}`}>{number}</span>
                <div><p className="text-sm font-medium text-[var(--shell-text)]">{heading}</p><p className="mt-1 text-xs leading-5 text-[var(--shell-muted)]">{detail}</p></div>
              </li>
            ))}
          </ol>
          <div className="mt-7 border-t border-[var(--shell-border)] pt-4 text-xs leading-5 text-[var(--shell-muted)]">文件仅用于你的简历分析和训练资料库。上传成功后可在对应模块继续管理。</div>
        </aside>
      </div>
    </div>
  );
}