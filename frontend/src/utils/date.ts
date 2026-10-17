/**
 * 日期格式化工具函数
 */

/**
 * 格式化日期为中文格式
 * @param dateStr 日期字符串
 * @param options 格式化选项
 * @returns 格式化后的日期字符串
 */
export function formatDate(
  dateStr: string | null | undefined,
  options?: {
    year?: 'numeric' | '2-digit';
    month?: 'numeric' | '2-digit';
    day?: 'numeric' | '2-digit';
    hour?: '2-digit';
    minute?: '2-digit';
  }
): string {
  if (!dateStr) return '-';
  
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return '-';
  
  const defaultOptions: Intl.DateTimeFormatOptions = {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    ...options
  };

  // 带时分时必须走 toLocaleString：toLocaleDateString 按规范只保留日期字段，
  // 传进来的 hour/minute 会被静默丢弃（formatDateTime 因此一直只显示日期）。
  if (options?.hour !== undefined || options?.minute !== undefined) {
    return date.toLocaleString('zh-CN', defaultOptions);
  }
  return date.toLocaleDateString('zh-CN', defaultOptions);
}

/**
 * 格式化日期时间（包含时分）
 */
export function formatDateTime(dateStr: string | null | undefined): string {
  return formatDate(dateStr, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
}

/**
 * 格式化日期（仅日期部分）
 */
export function formatDateOnly(dateStr: string | null | undefined): string {
  return formatDate(dateStr, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  });
}

