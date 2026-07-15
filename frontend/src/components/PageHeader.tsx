import type { ReactNode } from 'react';

interface PageHeaderProps {
  /** 卷首小标签，如「训练资料」 */
  kicker: string;
  title: string;
  description?: string;
  /** 右侧操作区（搜索、主 CTA 等） */
  actions?: ReactNode;
  className?: string;
}

/**
 * 列表/业务页统一页眉：kicker + 标题 + 说明 + 操作区
 */
export default function PageHeader({
  kicker,
  title,
  description,
  actions,
  className = '',
}: PageHeaderProps) {
  return (
    <header
      className={`flex flex-col gap-5 border-b border-[var(--shell-border)] pb-6 sm:flex-row sm:items-end sm:justify-between ${className}`}
    >
      <div className="min-w-0">
        <p className="page-kicker text-primary-700 dark:text-primary-400">{kicker}</p>
        <h1 className="page-title mt-2">{title}</h1>
        {description && (
          <p className="mt-2 max-w-2xl text-sm leading-6 text-[var(--shell-muted)]">{description}</p>
        )}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap items-center gap-3">{actions}</div>}
    </header>
  );
}
