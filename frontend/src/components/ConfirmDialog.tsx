import { AnimatePresence, motion } from 'framer-motion';
import { useEffect, useId, useRef } from 'react';

export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: string | React.ReactNode;
  confirmText?: string;
  cancelText?: string;
  confirmVariant?: 'danger' | 'primary' | 'warning';
  onConfirm: () => void;
  onCancel: () => void;
  loading?: boolean;
  customContent?: React.ReactNode;
  hideButtons?: boolean;
}

/**
 * 确认对话框：支持 Esc 关闭、焦点回到触发前元素、基础焦点陷阱。
 */
export default function ConfirmDialog({
  open,
  title,
  message,
  confirmText = '确定',
  cancelText = '取消',
  confirmVariant = 'primary',
  onConfirm,
  onCancel,
  loading = false,
  customContent,
  hideButtons = false,
}: ConfirmDialogProps) {
  const titleId = useId();
  const descId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  const previouslyFocused = useRef<HTMLElement | null>(null);

  // 打开时记录焦点；关闭时还原
  useEffect(() => {
    if (!open) return;

    previouslyFocused.current = document.activeElement as HTMLElement | null;
    // 优先聚焦取消按钮，避免误触危险操作
    const focusable = panelRef.current?.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    );
    const first = focusable?.[0];
    // 下一帧再聚焦，避免动画帧抢焦点
    const t = window.setTimeout(() => first?.focus(), 20);

    return () => {
      window.clearTimeout(t);
      previouslyFocused.current?.focus?.();
    };
  }, [open]);

  // Esc 关闭
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !loading) {
        e.preventDefault();
        onCancel();
        return;
      }
      // 简易焦点陷阱：Tab 循环在对话框内
      if (e.key !== 'Tab' || !panelRef.current) return;
      const nodes = panelRef.current.querySelectorAll<HTMLElement>(
        'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      );
      if (!nodes.length) return;
      const first = nodes[0];
      const last = nodes[nodes.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, loading, onCancel]);

  const variantStyles = {
    danger: 'bg-gradient-to-r from-red-500 to-red-600 hover:from-red-600 hover:to-red-700',
    primary: 'bg-gradient-to-r from-primary-500 to-primary-600 hover:from-primary-600 hover:to-primary-700',
    warning: 'bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-600 hover:to-orange-600',
  };

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
            onClick={() => !loading && onCancel()}
            className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm"
            aria-hidden="true"
          />

          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <motion.div
              ref={panelRef}
              role="dialog"
              aria-modal="true"
              aria-labelledby={titleId}
              aria-describedby={descId}
              initial={{ opacity: 0, scale: 0.96, y: 12 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.96, y: 12 }}
              transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
              onClick={(e) => e.stopPropagation()}
              className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl dark:bg-slate-800"
            >
              <h3 id={titleId} className="mb-4 text-xl font-bold text-slate-900 dark:text-white">
                {title}
              </h3>

              <div id={descId} className="mb-6 text-slate-600 dark:text-slate-300">
                {typeof message === 'string' ? (
                  message && <p className="whitespace-pre-line">{message}</p>
                ) : (
                  message
                )}
                {customContent}
              </div>

              {!hideButtons && (
                <div className="flex justify-end gap-3">
                  <button
                    type="button"
                    onClick={onCancel}
                    disabled={loading}
                    className="min-h-11 rounded-xl border border-slate-200 px-5 py-2.5 font-medium text-slate-600 transition duration-200 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
                  >
                    {cancelText}
                  </button>
                  <button
                    type="button"
                    onClick={onConfirm}
                    disabled={loading}
                    className={`min-h-11 rounded-xl px-5 py-2.5 font-semibold text-white shadow-lg transition duration-200 disabled:cursor-not-allowed disabled:opacity-50 ${variantStyles[confirmVariant]}`}
                  >
                    {loading ? (
                      <span className="inline-flex items-center gap-2">
                        <span
                          className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white"
                          aria-hidden
                        />
                        处理中…
                      </span>
                    ) : (
                      confirmText
                    )}
                  </button>
                </div>
              )}
            </motion.div>
          </div>
        </>
      )}
    </AnimatePresence>
  );
}
