import {useCallback, useEffect, useRef, useState} from 'react';
import {AnimatePresence, motion} from 'framer-motion';
import {EvaluateStatus, historyApi, InterviewItem} from '../api/history';
import {formatDate} from '../utils/date';
import DeleteConfirmDialog from '../components/DeleteConfirmDialog';
import {
  AlertCircle,
  CheckCircle,
  ChevronRight,
  Clock,
  Download,
  FileText,
  Loader2,
  PlayCircle,
  RefreshCw,
  RotateCcw,
  Search,
  Trash2,
  TrendingUp,
  Users,
} from 'lucide-react';
import { interviewApi } from '../api/interview';
import { getErrorMessage } from '../api/request';
import PageHeader from '../components/PageHeader';

interface InterviewHistoryPageProps {
  onBack: () => void;
  onViewInterview: (sessionId: string, resumeId?: number) => void;
  /** 刚交卷的会话 ID，列表中高亮显示 */
  highlightSessionId?: string;
}

interface InterviewWithResume extends InterviewItem {
  resumeId: number;
  resumeFilename: string;
  evaluateStatus?: EvaluateStatus;
  evaluateError?: string;
}

interface InterviewStats {
  totalCount: number;
  completedCount: number;
  averageScore: number;
}

// 统计卡片：对齐 Studio shell 令牌
function StatCard({
  icon: Icon,
  label,
  value,
  suffix,
  color,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: number | string;
  suffix?: string;
  color: string;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      className="ui-stat-card"
    >
      <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${color}`}>
        <Icon className="h-5 w-5 text-white" aria-hidden />
      </div>
      <div className="min-w-0">
        <p className="text-sm text-[var(--shell-muted)]">{label}</p>
        <p className="mt-0.5 text-2xl font-semibold tabular-nums tracking-tight text-[var(--shell-text)]">
          {value}
          {suffix && (
            <span className="ml-1 text-base font-normal text-[var(--shell-subtle)]">{suffix}</span>
          )}
        </p>
      </div>
    </motion.div>
  );
}

// 判断是否为已完成状态（包括 COMPLETED 和 EVALUATED）
function isCompletedStatus(status: string): boolean {
  return status === 'COMPLETED' || status === 'EVALUATED';
}

// 判断评估是否完成
function isEvaluateCompleted(interview: InterviewWithResume): boolean {
  // 如果 evaluateStatus 存在且为 COMPLETED，则评估已完成
  if (interview.evaluateStatus === 'COMPLETED') return true;
  // 向后兼容：如果 status 为 EVALUATED，也认为评估已完成
  if (interview.status === 'EVALUATED') return true;
  return false;
}

// 判断是否正在评估中
function isEvaluating(interview: InterviewWithResume): boolean {
  return interview.evaluateStatus === 'PENDING' || interview.evaluateStatus === 'PROCESSING';
}

// 判断评估是否失败
function isEvaluateFailed(interview: InterviewWithResume): boolean {
  return interview.evaluateStatus === 'FAILED';
}

// 状态图标
function StatusIcon({ interview }: { interview: InterviewWithResume }) {
  // 评估失败
  if (isEvaluateFailed(interview)) {
      return <AlertCircle className="w-4 h-4 text-red-500 dark:text-red-400"/>;
  }
  // 正在评估
  if (isEvaluating(interview)) {
      return <RefreshCw className="w-4 h-4 text-primary-500 dark:text-primary-400 animate-spin"/>;
  }
  // 评估完成
  if (isEvaluateCompleted(interview)) {
      return <CheckCircle className="w-4 h-4 text-green-500 dark:text-green-400"/>;
  }
  // 面试进行中
  if (interview.status === 'IN_PROGRESS') {
      return <PlayCircle className="w-4 h-4 text-primary-500 dark:text-primary-400"/>;
  }
  // 面试已完成但评估未开始
  if (isCompletedStatus(interview.status)) {
      return <Clock className="w-4 h-4 text-yellow-500 dark:text-yellow-400"/>;
  }
  // 已创建
    return <Clock className="w-4 h-4 text-yellow-500 dark:text-yellow-400"/>;
}

// 状态文本（表格内保持简短，完整错误用 title 展示）
function getStatusText(interview: InterviewWithResume): string {
  if (isEvaluateFailed(interview)) {
    return '评估失败';
  }
  if (isEvaluating(interview)) {
    const pct = resolveEvaluatePercent(interview);
    if (interview.evaluateStatus === 'PROCESSING') {
      return pct != null ? `评估中 ${pct}%` : '评估中（生成报告）';
    }
    return pct != null && pct > 0 ? `等待评估 ${pct}%` : '等待评估';
  }
  if (isEvaluateCompleted(interview)) {
    return '已完成';
  }
  if (interview.status === 'IN_PROGRESS') {
    return '进行中';
  }
  if (isCompletedStatus(interview.status)) {
    return '已提交，等待评估';
  }
  return '已创建';
}

/** 解析评估进度百分比；完成固定 100，处理中缺省为 5 */
function resolveEvaluatePercent(interview: InterviewWithResume): number | null {
  if (isEvaluateCompleted(interview)) return 100;
  if (!isEvaluating(interview) && !isCompletedStatus(interview.status)) return null;
  if (typeof interview.evaluateProgress === 'number') {
    return Math.min(100, Math.max(0, interview.evaluateProgress));
  }
  if (interview.evaluateStatus === 'PROCESSING') return 5;
  if (interview.evaluateStatus === 'PENDING' || isCompletedStatus(interview.status)) return 0;
  return null;
}

// 获取分数颜色
function getScoreColor(score: number): string {
  if (score >= 80) return 'bg-green-500';
  if (score >= 60) return 'bg-yellow-500';
  return 'bg-red-500';
}

export default function InterviewHistoryPage({
  onBack: _onBack,
  onViewInterview,
  highlightSessionId,
}: InterviewHistoryPageProps) {
  const [interviews, setInterviews] = useState<InterviewWithResume[]>([]);
  const [stats, setStats] = useState<InterviewStats | null>(null);
  const [loading, setLoading] = useState(true);
  // 同上：接口挂了不能伪装成"暂无面试记录"
  const [loadError, setLoadError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [deletingSessionId, setDeletingSessionId] = useState<string | null>(null);
  const [deleteItem, setDeleteItem] = useState<InterviewWithResume | null>(null);
  const [exporting, setExporting] = useState<string | null>(null);
  const [retryingSessionId, setRetryingSessionId] = useState<string | null>(null);
  const pollingRef = useRef<number | null>(null);

  const loadAllInterviews = useCallback(async (isPolling = false) => {
    if (!isPolling) {
      setLoading(true);
    }
    try {
      // 一次接口拉齐列表，避免「简历 × 详情」N+1
      const allInterviews: InterviewWithResume[] = (await historyApi.getInterviewHistory()).map(item => ({
        ...item,
        resumeId: item.resumeId,
        resumeFilename: item.resumeFilename,
      }));

      setInterviews(allInterviews);

      // 计算统计信息（只统计评估已完成的面试）
      const evaluated = allInterviews.filter(i => isEvaluateCompleted(i));
      const totalScore = evaluated.reduce((sum, i) => sum + (i.overallScore || 0), 0);
      setLoadError(null);
      setStats({
        totalCount: allInterviews.length,
        completedCount: evaluated.length,
        averageScore: evaluated.length > 0 ? Math.round(totalScore / evaluated.length) : 0,
      });
    } catch (err) {
      console.error('加载面试记录失败', err);
      setLoadError(err instanceof Error ? err.message : '请稍后重试');
    } finally {
      if (!isPolling) {
        setLoading(false);
      }
    }
  }, []);

  // 高亮刚交卷的行并滚入视野
  useEffect(() => {
    if (!highlightSessionId || loading) return;
    const row = document.querySelector(`[data-session-id="${highlightSessionId}"]`);
    row?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [highlightSessionId, loading, interviews]);

  // 初始加载
  useEffect(() => {
    loadAllInterviews();
  }, [loadAllInterviews]);

  // 轮询检查评估状态
  useEffect(() => {
    // 检查是否有正在评估的面试
    const hasEvaluating = interviews.some(i => isEvaluating(i));

    if (hasEvaluating) {
      // 启动轮询
      pollingRef.current = window.setInterval(() => {
        loadAllInterviews(true);
      }, 3000); // 每3秒轮询一次
    } else {
      // 停止轮询
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
    }

    return () => {
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
    };
  }, [interviews, loadAllInterviews]);

  const handleDeleteClick = (interview: InterviewWithResume, e: React.MouseEvent) => {
    e.stopPropagation();
    setDeleteItem(interview);
  };

  const handleDeleteConfirm = async () => {
    if (!deleteItem) return;

    setDeletingSessionId(deleteItem.sessionId);
    try {
      await historyApi.deleteInterview(deleteItem.sessionId);
      await loadAllInterviews();
      setDeleteItem(null);
    } catch (err) {
      alert(err instanceof Error ? err.message : '删除失败，请稍后重试');
    } finally {
      setDeletingSessionId(null);
    }
  };

  const handleExport = async (sessionId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setExporting(sessionId);
    try {
      const blob = await historyApi.exportInterviewPdf(sessionId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `面试报告_${sessionId.slice(-8)}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      alert('导出失败，请重试');
    } finally {
      setExporting(null);
    }
  };

  /** 评估失败后重新入队 */
  const handleRetryEvaluation = async (sessionId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setRetryingSessionId(sessionId);
    try {
      await interviewApi.retryEvaluation(sessionId);
      await loadAllInterviews(true);
    } catch (err) {
      alert(getErrorMessage(err) || '重新评估失败，请稍后重试');
    } finally {
      setRetryingSessionId(null);
    }
  };

  const filteredInterviews = interviews.filter(interview =>
    interview.resumeFilename.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <motion.div
      className="mx-auto w-full max-w-6xl space-y-6"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
    >
      <PageHeader
        kicker="训练记录"
        title="面试记录"
        description="查看评估进度、导出报告，并从任意一场继续复盘。"
        actions={
          <label className="ui-search">
            <Search className="h-4 w-4 shrink-0 text-[var(--shell-subtle)]" aria-hidden />
            <input
              type="search"
              placeholder="搜索简历名称…"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              aria-label="搜索简历名称"
            />
          </label>
        }
      />

      {stats && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <StatCard icon={Users} label="面试总数" value={stats.totalCount} color="bg-primary-600" />
          <StatCard icon={CheckCircle} label="已完成" value={stats.completedCount} color="bg-emerald-500" />
          <StatCard icon={TrendingUp} label="平均分数" value={stats.averageScore} suffix="分" color="bg-teal-700" />
        </div>
      )}

      {loading && (
        <div className="ui-panel flex min-h-64 items-center justify-center" role="status" aria-live="polite">
          <Loader2 className="h-7 w-7 animate-spin text-primary-500" aria-hidden />
          <span className="sr-only">加载面试记录</span>
        </div>
      )}

      {!loading && loadError && (
            <div className="flex min-h-64 flex-col items-center justify-center p-8 text-center">
              <p className="text-sm font-medium text-red-600 dark:text-red-400">加载失败：{loadError}</p>
              <button type="button" onClick={() => window.location.reload()} className="mt-4 text-sm font-semibold text-primary-600 dark:text-primary-400">
                重新加载
              </button>
            </div>
      )}
      {!loading && !loadError && filteredInterviews.length === 0 && (
        <motion.div
          className="ui-panel px-6 py-16 text-center"
          initial={{ opacity: 0, scale: 0.98 }}
          animate={{ opacity: 1, scale: 1 }}
        >
          <Users className="mx-auto h-12 w-12 text-[var(--shell-subtle)]" aria-hidden />
          <h3 className="mt-4 text-lg font-semibold text-[var(--shell-text)]">
            {searchTerm ? '没有匹配的记录' : '暂无面试记录'}
          </h3>
          <p className="mt-2 text-sm text-[var(--shell-muted)]">
            {searchTerm ? '换个关键词试试。' : '从简历库选择一份材料，开始第一场模拟面试。'}
          </p>
        </motion.div>
      )}

      {!loading && filteredInterviews.length > 0 && (
        <motion.div
          className="ui-panel overflow-x-auto"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.08 }}
        >
          <table className="w-full min-w-[720px]">
            <thead className="border-b border-[var(--shell-border)] bg-[var(--shell-hover)]/50">
              <tr>
                <th className="px-5 py-3.5 text-left text-sm font-medium text-[var(--shell-muted)] sm:px-6">关联简历</th>
                <th className="px-5 py-3.5 text-left text-sm font-medium text-[var(--shell-muted)] sm:px-6">题目数</th>
                <th className="px-5 py-3.5 text-left text-sm font-medium text-[var(--shell-muted)] sm:px-6">状态</th>
                <th className="px-5 py-3.5 text-left text-sm font-medium text-[var(--shell-muted)] sm:px-6">得分</th>
                <th className="px-5 py-3.5 text-left text-sm font-medium text-[var(--shell-muted)] sm:px-6">创建时间</th>
                <th className="px-5 py-3.5 text-right text-sm font-medium text-[var(--shell-muted)] sm:px-6">操作</th>
              </tr>
            </thead>
            <tbody>
              <AnimatePresence>
                {filteredInterviews.map((interview, index) => (
                  <motion.tr
                    key={interview.sessionId}
                    data-session-id={interview.sessionId}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: index * 0.05 }}
                    onClick={() => onViewInterview(interview.sessionId, interview.resumeId)}
                    className={`group cursor-pointer border-b border-[var(--shell-border)] transition-colors hover:bg-[var(--shell-hover)] ${
                      highlightSessionId === interview.sessionId
                        ? 'bg-primary-50/80 ring-1 ring-inset ring-primary-200 dark:bg-primary-900/20 dark:ring-primary-800'
                        : ''
                    }`}
                  >
                    <td className="px-5 py-4 sm:px-6">
                      <div className="flex items-center gap-3">
                        <FileText className="h-5 w-5 shrink-0 text-[var(--shell-subtle)]" aria-hidden />
                        <div className="min-w-0">
                          <p className="truncate font-medium text-[var(--shell-text)]">{interview.resumeFilename}</p>
                          <p className="text-xs text-[var(--shell-subtle)]">#{interview.sessionId.slice(-8)}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-5 py-4 sm:px-6">
                      <span className="inline-flex items-center rounded-lg bg-[var(--shell-hover)] px-2.5 py-1 text-sm text-[var(--shell-muted)]">
                        {interview.totalQuestions} 题
                      </span>
                    </td>
                    <td className="px-5 py-4 sm:px-6">
                      <div className="flex items-center gap-2">
                        <StatusIcon interview={interview} />
                        <span
                          className="max-w-[12rem] truncate text-sm text-[var(--shell-muted)]"
                          title={isEvaluateFailed(interview) ? (interview.evaluateError || '评估失败') : getStatusText(interview)}
                        >
                          {getStatusText(interview)}
                        </span>
                      </div>
                    </td>
                    <td className="px-5 py-4 sm:px-6">
                      {isEvaluateCompleted(interview) && interview.overallScore !== null ? (
                        <div className="flex items-center gap-3">
                          <div className="h-2 w-16 overflow-hidden rounded-full bg-[var(--shell-hover)]">
                            <motion.div
                              className={`h-full rounded-full ${getScoreColor(interview.overallScore)}`}
                              initial={{ width: 0 }}
                              animate={{ width: `${interview.overallScore}%` }}
                              transition={{ duration: 0.8, delay: index * 0.05 }}
                            />
                          </div>
                          <span className="font-semibold tabular-nums text-[var(--shell-text)]">{interview.overallScore}</span>
                        </div>
                      ) : isEvaluating(interview) || (isCompletedStatus(interview.status) && !isEvaluateFailed(interview) && !isEvaluateCompleted(interview)) ? (
                        <div className="flex min-w-[7rem] items-center gap-2">
                          <div className="h-2 w-16 overflow-hidden rounded-full bg-[var(--shell-hover)]">
                            <motion.div
                              className="h-full rounded-full bg-primary-500"
                              initial={{ width: 0 }}
                              animate={{ width: `${resolveEvaluatePercent(interview) ?? 5}%` }}
                              transition={{ duration: 0.4 }}
                            />
                          </div>
                          <span className="text-sm tabular-nums text-primary-600 dark:text-primary-400">
                            {resolveEvaluatePercent(interview) ?? 5}%
                          </span>
                        </div>
                      ) : isEvaluateFailed(interview) ? (
                        <span className="text-sm text-red-500 dark:text-red-400" title={interview.evaluateError}>失败</span>
                      ) : (
                        <span className="text-[var(--shell-subtle)]">–</span>
                      )}
                    </td>
                    <td className="px-5 py-4 text-sm text-[var(--shell-muted)] sm:px-6">
                      {formatDate(interview.createdAt)}
                    </td>
                    <td className="px-5 py-4 text-right sm:px-6">
                      <div className="flex items-center justify-end gap-1">
                        {isEvaluateFailed(interview) && (
                          <button
                            type="button"
                            onClick={(e) => handleRetryEvaluation(interview.sessionId, e)}
                            disabled={retryingSessionId === interview.sessionId}
                            className="ui-icon-btn-warn"
                            title={interview.evaluateError ? `重新评估：${interview.evaluateError}` : '重新评估'}
                            aria-label="重新评估"
                          >
                            {retryingSessionId === interview.sessionId ? (
                              <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                            ) : (
                              <RotateCcw className="h-4 w-4" aria-hidden />
                            )}
                          </button>
                        )}
                        {isEvaluateCompleted(interview) && (
                          <button
                            type="button"
                            onClick={(e) => handleExport(interview.sessionId, e)}
                            disabled={exporting === interview.sessionId}
                            className="ui-icon-btn-accent"
                            title="导出 PDF"
                            aria-label="导出 PDF"
                          >
                            {exporting === interview.sessionId ? (
                              <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                            ) : (
                              <Download className="h-4 w-4" aria-hidden />
                            )}
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={(e) => handleDeleteClick(interview, e)}
                          disabled={deletingSessionId === interview.sessionId}
                          className="ui-icon-btn-danger"
                          title="删除"
                          aria-label="删除面试记录"
                        >
                          <Trash2 className="h-4 w-4" aria-hidden />
                        </button>
                        <ChevronRight
                          className="h-5 w-5 text-[var(--shell-subtle)] transition group-hover:translate-x-0.5 group-hover:text-primary-500"
                          aria-hidden
                        />
                      </div>
                    </td>
                  </motion.tr>
                ))}
              </AnimatePresence>
            </tbody>
          </table>
        </motion.div>
      )}

      {/* 删除确认对话框 */}
      <DeleteConfirmDialog
        open={deleteItem !== null}
        item={deleteItem ? { id: deleteItem.id, sessionId: deleteItem.sessionId } : null}
        itemType="面试记录"
        loading={deletingSessionId !== null}
        onConfirm={handleDeleteConfirm}
        onCancel={() => setDeleteItem(null)}
      />
    </motion.div>
  );
}
