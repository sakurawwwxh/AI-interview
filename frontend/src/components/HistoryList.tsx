import {useEffect, useState} from 'react';
import {AnimatePresence, motion} from 'framer-motion';
import {historyApi, ResumeListItem} from '../api/history';
import ConfirmDialog from './ConfirmDialog';
import {getScoreColor} from '../utils/score';
import {formatDate} from '../utils/date';
import {
  Search,
  FileText,
  CheckCircle2,
  Trash2,
  ChevronRight
} from 'lucide-react';

interface HistoryListProps {
  onSelectResume: (id: number) => void;
}

export default function HistoryList({ onSelectResume }: HistoryListProps) {
  const [resumes, setResumes] = useState<ResumeListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<{ id: number; filename: string } | null>(null);

  useEffect(() => {
    loadResumes();
  }, []);

  const loadResumes = async () => {
    setLoading(true);
    try {
      const data = await historyApi.getResumes();
      setResumes(data);
    } catch (err) {
      console.error('加载历史记录失败', err);
    } finally {
      setLoading(false);
    }
  };



  const handleDeleteClick = (id: number, filename: string, e: React.MouseEvent) => {
    e.stopPropagation(); // 阻止触发行点击事件
    setDeleteConfirm({ id, filename });
  };
  
  const handleDeleteConfirm = async () => {
    if (!deleteConfirm) return;
    
    const { id } = deleteConfirm;
    setDeletingId(id);
    try {
      await historyApi.deleteResume(id);
      // 重新加载列表
      await loadResumes();
      setDeleteConfirm(null);
    } catch (err) {
      alert(err instanceof Error ? err.message : '删除失败，请稍后重试');
    } finally {
      setDeletingId(null);
    }
  };

  const filteredResumes = resumes.filter(resume =>
    resume.filename.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <motion.div 
      className="w-full"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
    >
      {/* 头部 */}
      <div className="flex justify-between items-start mb-10 flex-wrap gap-6">
        <div>
          <motion.h1 
            className="text-4xl font-bold text-slate-900 mb-2"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
          >
            简历库
          </motion.h1>
          <motion.p 
            className="text-slate-500"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.1 }}
          >
            管理您已分析过的所有简历及面试记录
          </motion.p>
        </div>
        
        <motion.div 
          className="flex items-center gap-3 bg-white border border-slate-200 rounded-xl px-4 py-3 min-w-[280px] focus-within:border-primary-500 focus-within:ring-2 focus-within:ring-primary-100 transition-all"
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
        >
          <Search className="w-5 h-5 text-slate-400" />
          <input
            type="text"
            placeholder="搜索简历..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="flex-1 outline-none text-slate-700 placeholder:text-slate-400"
          />
        </motion.div>
      </div>

      {/* 加载状态 */}
      {loading && (
        <div className="text-center py-20">
          <motion.div 
            className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full mx-auto mb-4"
            animate={{ rotate: 360 }}
            transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
          />
          <p className="text-slate-500">加载中...</p>
        </div>
      )}

      {/* 空状态 */}
      {!loading && filteredResumes.length === 0 && (
        <motion.div 
          className="text-center py-20 bg-white rounded-2xl"
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
        >
          <div className="text-6xl mb-6">📄</div>
          <h3 className="text-xl font-semibold text-slate-700 mb-2">暂无简历记录</h3>
          <p className="text-slate-500">上传简历开始您的第一次 AI 面试分析</p>
        </motion.div>
      )}

      {/* 表格 */}
      {!loading && filteredResumes.length > 0 && (
        <motion.div 
          className="bg-white rounded-2xl shadow-sm overflow-hidden"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          <table className="w-full">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-100">
                <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 uppercase tracking-wide">简历名称</th>
                <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 uppercase tracking-wide">上传日期</th>
                <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 uppercase tracking-wide">AI 评分</th>
                <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 uppercase tracking-wide">面试状态</th>
                <th className="w-20"></th>
              </tr>
            </thead>
            <tbody>
              <AnimatePresence>
                {filteredResumes.map((resume, index) => (
                  <motion.tr
                    key={resume.id}
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: index * 0.05 }}
                    onClick={() => onSelectResume(resume.id)}
                    className="border-b border-slate-100 last:border-0 hover:bg-slate-50 cursor-pointer transition-colors group"
                  >
                    <td className="px-6 py-5">
                      <div className="flex items-center gap-4">
                        <div className="w-10 h-10 bg-primary-50 rounded-xl flex items-center justify-center text-primary-500">
                          <FileText className="w-5 h-5" />
                        </div>
                        <span className="font-medium text-slate-800">{resume.filename}</span>
                      </div>
                    </td>
                    <td className="px-6 py-5 text-slate-500">{formatDate(resume.uploadedAt)}</td>
                    <td className="px-6 py-5">
                      {resume.latestScore !== undefined ? (
                        <div className="flex items-center gap-3">
                          <div className="w-20 h-2 bg-slate-100 rounded-full overflow-hidden">
                            <motion.div 
                              className={`h-full ${getScoreColor(resume.latestScore).split(' ')[0]} rounded-full`}
                              initial={{ width: 0 }}
                              animate={{ width: `${resume.latestScore}%` }}
                              transition={{ duration: 0.8, delay: index * 0.05 }}
                            />
                          </div>
                          <span className="font-bold text-slate-800">{resume.latestScore}</span>
                        </div>
                      ) : (
                        <span className="text-slate-400">-</span>
                      )}
                    </td>
                    <td className="px-6 py-5">
                      {resume.interviewCount > 0 ? (
                        <span className="inline-flex items-center gap-1.5 px-3 py-1 bg-emerald-50 text-emerald-600 rounded-full text-sm font-medium">
                          <CheckCircle2 className="w-4 h-4" />
                          已完成
                        </span>
                      ) : (
                        <span className="inline-flex px-3 py-1 bg-slate-100 text-slate-500 rounded-full text-sm">待面试</span>
                      )}
                    </td>
                    <td className="px-4">
                      <div className="flex items-center gap-2">
                        <button
                          onClick={(e) => handleDeleteClick(resume.id, resume.filename, e)}
                          disabled={deletingId === resume.id}
                          className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                          title="删除简历"
                        >
                          {deletingId === resume.id ? (
                            <motion.div
                              className="w-5 h-5 border-2 border-red-500 border-t-transparent rounded-full"
                              animate={{ rotate: 360 }}
                              transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
                            />
                          ) : (
                            <Trash2 className="w-5 h-5" />
                          )}
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
      <ConfirmDialog
        open={deleteConfirm !== null}
        title="删除简历"
        message={
          deleteConfirm ? (
            <>
              <p className="mb-2">确定要删除简历 <strong>"{deleteConfirm.filename}"</strong> 吗？</p>
              <p className="text-sm text-slate-500 mb-2">删除后将同时删除：</p>
              <ul className="text-sm text-slate-500 list-disc list-inside mb-2">
                <li>简历评价记录</li>
                <li>所有模拟面试记录</li>
              </ul>
              <p className="text-sm font-semibold text-red-600">此操作不可恢复！</p>
            </>
          ) : ''
        }
        confirmText="确定删除"
        cancelText="取消"
        confirmVariant="danger"
        loading={deletingId !== null}
        onConfirm={handleDeleteConfirm}
        onCancel={() => setDeleteConfirm(null)}
      />
    </motion.div>
  );
}
