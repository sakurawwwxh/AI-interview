import { useEffect, useState, useCallback } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { historyApi, InterviewItem } from '../api/history';
import { formatDate } from '../utils/date';
import DeleteConfirmDialog from '../components/DeleteConfirmDialog';
import {
  Users,
  Search,
  Download,
  Trash2,
  ChevronRight,
  CheckCircle,
  Clock,
  PlayCircle,
  TrendingUp,
  FileText,
  Loader2,
} from 'lucide-react';

interface InterviewHistoryPageProps {
  onBack: () => void;
  onViewInterview: (sessionId: string, resumeId?: number) => void;
}

interface InterviewWithResume extends InterviewItem {
  resumeId: number;
  resumeFilename: string;
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
      className="bg-white rounded-xl p-6 shadow-sm border border-slate-100"
    >
      <div className="flex items-center gap-4">
        <div className={`p-3 rounded-lg ${color}`}>
          <Icon className="w-6 h-6 text-white" />
        </div>
        <div>
          <p className="text-sm text-slate-500">{label}</p>
          <p className="text-2xl font-bold text-slate-800">
            {value}{suffix && <span className="text-base font-normal text-slate-400 ml-1">{suffix}</span>}
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

// 状态图标
function StatusIcon({ status }: { status: string }) {
  if (isCompletedStatus(status)) {
    return <CheckCircle className="w-4 h-4 text-green-500" />;
  }
  switch (status) {
    case 'IN_PROGRESS':
      return <PlayCircle className="w-4 h-4 text-blue-500" />;
    default:
      return <Clock className="w-4 h-4 text-yellow-500" />;
  }
}

// 状态文本
function getStatusText(status: string): string {
  if (isCompletedStatus(status)) {
    return '已完成';
  }
  switch (status) {
    case 'IN_PROGRESS':
      return '进行中';
    default:
      return '已创建';
  }
}

// 获取分数颜色
function getScoreColor(score: number): string {
  if (score >= 80) return 'bg-green-500';
  if (score >= 60) return 'bg-yellow-500';
  return 'bg-red-500';
}

export default function InterviewHistoryPage({ onBack: _onBack, onViewInterview }: InterviewHistoryPageProps) {
  const [interviews, setInterviews] = useState<InterviewWithResume[]>([]);
  const [stats, setStats] = useState<InterviewStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [deletingSessionId, setDeletingSessionId] = useState<string | null>(null);
  const [deleteItem, setDeleteItem] = useState<InterviewWithResume | null>(null);
  const [exporting, setExporting] = useState<string | null>(null);

  const loadAllInterviews = useCallback(async () => {
    setLoading(true);
    try {
      const resumes = await historyApi.getResumes();
      const allInterviews: InterviewWithResume[] = [];

      for (const resume of resumes) {
        const detail = await historyApi.getResumeDetail(resume.id);
        if (detail.interviews && detail.interviews.length > 0) {
          detail.interviews.forEach(interview => {
            allInterviews.push({
              ...interview,
              resumeId: resume.id,
              resumeFilename: resume.filename
            });
          });
        }
      }

      // 按创建时间倒序排序
      allInterviews.sort((a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );

      setInterviews(allInterviews);

      // 计算统计信息
      const completed = allInterviews.filter(i => isCompletedStatus(i.status));
      const totalScore = completed.reduce((sum, i) => sum + (i.overallScore || 0), 0);
      setStats({
        totalCount: allInterviews.length,
        completedCount: completed.length,
        averageScore: completed.length > 0 ? Math.round(totalScore / completed.length) : 0,
      });
    } catch (err) {
      console.error('加载面试记录失败', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAllInterviews();
  }, [loadAllInterviews]);

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
            className="text-2xl font-bold text-slate-800 flex items-center gap-3"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
          >
            <Users className="w-7 h-7 text-primary-500" />
            面试记录
          </motion.h1>
          <motion.p
            className="text-slate-500 mt-1"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.1 }}
          >
            查看和管理所有模拟面试记录
          </motion.p>
        </div>

        <motion.div
          className="flex items-center gap-3 bg-white border border-slate-200 rounded-xl px-4 py-2.5 min-w-[280px] focus-within:border-primary-500 focus-within:ring-2 focus-within:ring-primary-100 transition-all"
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
        >
          <Search className="w-5 h-5 text-slate-400" />
          <input
            type="text"
            placeholder="搜索简历名称..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="flex-1 outline-none text-slate-700 placeholder:text-slate-400"
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
            color="bg-indigo-500"
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
          className="text-center py-20 bg-white rounded-2xl shadow-sm border border-slate-100"
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
        >
          <Users className="w-16 h-16 text-slate-300 mx-auto mb-4" />
          <h3 className="text-xl font-semibold text-slate-700 mb-2">暂无面试记录</h3>
          <p className="text-slate-500">开始一次模拟面试后，记录将显示在这里</p>
        </motion.div>
      )}

      {/* 表格 */}
      {!loading && filteredInterviews.length > 0 && (
        <motion.div
          className="bg-white rounded-xl shadow-sm border border-slate-100 overflow-hidden"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          <table className="w-full">
            <thead className="bg-slate-50 border-b border-slate-100">
              <tr>
                <th className="text-left px-6 py-4 text-sm font-medium text-slate-600">关联简历</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-slate-600">题目数</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-slate-600">状态</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-slate-600">得分</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-slate-600">创建时间</th>
                <th className="text-right px-6 py-4 text-sm font-medium text-slate-600">操作</th>
              </tr>
            </thead>
            <tbody>
              <AnimatePresence>
                {filteredInterviews.map((interview, index) => (
                  <motion.tr
                    key={interview.sessionId}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: index * 0.05 }}
                    onClick={() => onViewInterview(interview.sessionId, interview.resumeId)}
                    className="border-b border-slate-50 hover:bg-slate-50 cursor-pointer transition-colors group"
                  >
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <FileText className="w-5 h-5 text-slate-400" />
                        <div>
                          <p className="font-medium text-slate-800">{interview.resumeFilename}</p>
                          <p className="text-xs text-slate-400">#{interview.sessionId.slice(-8)}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <span className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-slate-100 text-slate-600 rounded-lg text-sm">
                        {interview.totalQuestions} 题
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2">
                        <StatusIcon status={interview.status} />
                        <span className="text-sm text-slate-600">
                          {getStatusText(interview.status)}
                        </span>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      {isCompletedStatus(interview.status) && interview.overallScore !== null ? (
                        <div className="flex items-center gap-3">
                          <div className="w-16 h-2 bg-slate-100 rounded-full overflow-hidden">
                            <motion.div
                              className={`h-full ${getScoreColor(interview.overallScore)} rounded-full`}
                              initial={{ width: 0 }}
                              animate={{ width: `${interview.overallScore}%` }}
                              transition={{ duration: 0.8, delay: index * 0.05 }}
                            />
                          </div>
                          <span className="font-bold text-slate-800">{interview.overallScore}</span>
                        </div>
                      ) : (
                        <span className="text-slate-400">-</span>
                      )}
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-500">
                      {formatDate(interview.createdAt)}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        {/* 导出按钮 */}
                        {isCompletedStatus(interview.status) && (
                          <button
                            onClick={(e) => handleExport(interview.sessionId, e)}
                            disabled={exporting === interview.sessionId}
                            className="p-2 text-slate-400 hover:text-primary-500 hover:bg-primary-50 rounded-lg transition-colors disabled:opacity-50"
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
                          className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors disabled:opacity-50"
                          title="删除"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                        <ChevronRight className="w-5 h-5 text-slate-300 group-hover:text-primary-500 group-hover:translate-x-1 transition-all" />
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
