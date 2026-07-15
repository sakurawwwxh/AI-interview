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

// 统计卡片组件
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
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="bg-white dark:bg-slate-800 rounded-xl p-6 shadow-sm border border-slate-100 dark:border-slate-700"
    >
      <div className="flex items-center gap-4">
        <div className={`p-3 rounded-lg ${color}`}>
          <Icon className="w-6 h-6 text-white" />
        </div>
        <div>
            <p className="text-sm text-slate-500 dark:text-slate-400">{label}</p>
            <p className="text-2xl font-bold text-slate-800 dark:text-white">
                {value}{suffix &&
                <span className="text-base font-normal text-slate-400 dark:text-slate-500 ml-1">{suffix}</span>}
          </p>
        </div>
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
      setStats({
        totalCount: allInterviews.length,
        completedCount: evaluated.length,
        averageScore: evaluated.length > 0 ? Math.round(totalScore / evaluated.length) : 0,
      });
    } catch (err) {
      console.error('加载面试记录失败', err);
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
      className="w-full"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
    >
      {/* 头部 */}
      <div className="flex justify-between items-start mb-8 flex-wrap gap-6">
        <div>
          <motion.h1
              className="text-2xl font-bold text-slate-800 dark:text-white flex items-center gap-3"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
          >
            <Users className="w-7 h-7 text-primary-500" />
            面试记录
          </motion.h1>
          <motion.p
              className="text-slate-500 dark:text-slate-400 mt-1"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.1 }}
          >
            查看和管理所有模拟面试记录
          </motion.p>
        </div>

        <motion.div
            className="flex items-center gap-3 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-600 rounded-xl px-4 py-2.5 min-w-[280px] focus-within:border-primary-500 focus-within:ring-2 focus-within:ring-primary-100 dark:focus-within:ring-primary-900/30 transition-all"
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
        >
          <Search className="w-5 h-5 text-slate-400" />
          <input
            type="text"
            placeholder="搜索简历名称..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="flex-1 outline-none text-slate-700 dark:text-slate-200 placeholder:text-slate-400 bg-transparent"
          />
        </motion.div>
      </div>

      {/* 统计卡片 */}
      {stats && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          <StatCard
            icon={Users}
            label="面试总数"
            value={stats.totalCount}
            color="bg-primary-500"
          />
          <StatCard
            icon={CheckCircle}
            label="已完成"
            value={stats.completedCount}
            color="bg-emerald-500"
          />
          <StatCard
            icon={TrendingUp}
            label="平均分数"
            value={stats.averageScore}
            suffix="分"
            color="bg-primary-600"
          />
        </div>
      )}

      {/* 加载状态 */}
      {loading && (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
        </div>
      )}

      {/* 空状态 */}
      {!loading && filteredInterviews.length === 0 && (
        <motion.div
            className="text-center py-20 bg-white dark:bg-slate-800 rounded-2xl shadow-sm border border-slate-100 dark:border-slate-700"
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
        >
            <Users className="w-16 h-16 text-slate-300 dark:text-slate-600 mx-auto mb-4"/>
            <h3 className="text-xl font-semibold text-slate-700 dark:text-slate-300 mb-2">暂无面试记录</h3>
            <p className="text-slate-500 dark:text-slate-400">开始一次模拟面试后，记录将显示在这里</p>
        </motion.div>
      )}

      {/* 表格 */}
      {!loading && filteredInterviews.length > 0 && (
        <motion.div
            className="bg-white dark:bg-slate-800 rounded-xl shadow-sm border border-slate-100 dark:border-slate-700 overflow-hidden"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          <table className="w-full">
              <thead className="bg-slate-50 dark:bg-slate-700/50 border-b border-slate-100 dark:border-slate-600">
              <tr>
                  <th className="text-left px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">关联简历</th>
                  <th className="text-left px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">题目数</th>
                  <th className="text-left px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">状态</th>
                  <th className="text-left px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">得分</th>
                  <th className="text-left px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">创建时间</th>
                  <th className="text-right px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">操作</th>
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
                    className={`border-b border-slate-50 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700/50 cursor-pointer transition-colors group ${
                      highlightSessionId === interview.sessionId
                        ? 'bg-primary-50/80 dark:bg-primary-900/20 ring-1 ring-inset ring-primary-200 dark:ring-primary-800'
                        : ''
                    }`}
                  >
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <FileText className="w-5 h-5 text-slate-400" />
                        <div>
                            <p className="font-medium text-slate-800 dark:text-white">{interview.resumeFilename}</p>
                            <p className="text-xs text-slate-400 dark:text-slate-500">#{interview.sessionId.slice(-8)}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <span
                          className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-lg text-sm">
                        {interview.totalQuestions} 题
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2">
                        <StatusIcon interview={interview} />
                          <span
                            className="text-sm text-slate-600 dark:text-slate-300 max-w-[12rem] truncate"
                            title={isEvaluateFailed(interview) ? (interview.evaluateError || '评估失败') : getStatusText(interview)}
                          >
                          {getStatusText(interview)}
                        </span>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      {isEvaluateCompleted(interview) && interview.overallScore !== null ? (
                        <div className="flex items-center gap-3">
                            <div className="w-16 h-2 bg-slate-100 dark:bg-slate-700 rounded-full overflow-hidden">
                            <motion.div
                              className={`h-full ${getScoreColor(interview.overallScore)} rounded-full`}
                              initial={{ width: 0 }}
                              animate={{ width: `${interview.overallScore}%` }}
                              transition={{ duration: 0.8, delay: index * 0.05 }}
                            />
                          </div>
                            <span className="font-bold text-slate-800 dark:text-white">{interview.overallScore}</span>
                        </div>
                      ) : isEvaluating(interview) || (isCompletedStatus(interview.status) && !isEvaluateFailed(interview) && !isEvaluateCompleted(interview)) ? (
                          <div className="flex items-center gap-2 min-w-[7rem]">
                            <div className="w-16 h-2 bg-slate-100 dark:bg-slate-700 rounded-full overflow-hidden">
                              <motion.div
                                className="h-full bg-primary-500 rounded-full"
                                initial={{ width: 0 }}
                                animate={{ width: `${resolveEvaluatePercent(interview) ?? 5}%` }}
                                transition={{ duration: 0.4 }}
                              />
                            </div>
                            <span className="text-primary-500 dark:text-primary-400 text-sm tabular-nums">
                              {resolveEvaluatePercent(interview) ?? 5}%
                            </span>
                          </div>
                      ) : isEvaluateFailed(interview) ? (
                          <span className="text-red-500 dark:text-red-400 text-sm"
                                title={interview.evaluateError}>失败</span>
                      ) : (
                          <span className="text-slate-400 dark:text-slate-500">-</span>
                      )}
                    </td>
                      <td className="px-6 py-4 text-sm text-slate-500 dark:text-slate-400">
                      {formatDate(interview.createdAt)}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        {/* 评估失败：一键重试 */}
                        {isEvaluateFailed(interview) && (
                          <button
                            onClick={(e) => handleRetryEvaluation(interview.sessionId, e)}
                            disabled={retryingSessionId === interview.sessionId}
                            className="p-2 text-slate-400 hover:text-amber-600 hover:bg-amber-50 dark:hover:bg-amber-900/30 rounded-lg transition-colors disabled:opacity-50"
                            title={interview.evaluateError ? `重新评估：${interview.evaluateError}` : '重新评估'}
                          >
                            {retryingSessionId === interview.sessionId ? (
                              <Loader2 className="w-4 h-4 animate-spin" />
                            ) : (
                              <RotateCcw className="w-4 h-4" />
                            )}
                          </button>
                        )}
                        {/* 导出按钮 */}
                        {isEvaluateCompleted(interview) && (
                          <button
                            onClick={(e) => handleExport(interview.sessionId, e)}
                            disabled={exporting === interview.sessionId}
                            className="p-2 text-slate-400 hover:text-primary-500 hover:bg-primary-50 dark:hover:bg-primary-900/30 rounded-lg transition-colors disabled:opacity-50"
                            title="导出PDF"
                          >
                            {exporting === interview.sessionId ? (
                              <Loader2 className="w-4 h-4 animate-spin" />
                            ) : (
                              <Download className="w-4 h-4" />
                            )}
                          </button>
                        )}
                        {/* 删除按钮 */}
                        <button
                          onClick={(e) => handleDeleteClick(interview, e)}
                          disabled={deletingSessionId === interview.sessionId}
                          className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/30 rounded-lg transition-colors disabled:opacity-50"
                          title="删除"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                          <ChevronRight
                              className="w-5 h-5 text-slate-300 dark:text-slate-600 group-hover:text-primary-500 group-hover:translate-x-1 transition-all"/>
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
