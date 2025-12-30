import {useEffect, useState} from 'react';
import {AnimatePresence, motion} from 'framer-motion';
import {historyApi, InterviewDetail, ResumeDetail} from '../api/history';
import AnalysisPanel from '../components/AnalysisPanel';
import InterviewPanel from '../components/InterviewPanel';
import InterviewDetailPanel from '../components/InterviewDetailPanel';
import {formatDateOnly} from '../utils/date';

interface ResumeDetailPageProps {
  resumeId: number;
  onBack: () => void;
  onStartInterview: (resumeText: string, resumeId: number) => void;
}

type TabType = 'analysis' | 'interview';
type DetailViewType = 'list' | 'interviewDetail';

export default function ResumeDetailPage({ resumeId, onBack, onStartInterview }: ResumeDetailPageProps) {
  const [resume, setResume] = useState<ResumeDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<TabType>('analysis');
  const [exporting, setExporting] = useState<string | null>(null);
  const [[page, direction], setPage] = useState([0, 0]);
  const [detailView, setDetailView] = useState<DetailViewType>('list');
  const [selectedInterview, setSelectedInterview] = useState<InterviewDetail | null>(null);
  const [loadingInterview, setLoadingInterview] = useState(false);

  useEffect(() => {
    loadResumeDetail();
  }, [resumeId]);

  const loadResumeDetail = async () => {
    setLoading(true);
    try {
      const data = await historyApi.getResumeDetail(resumeId);
      setResume(data);
    } catch (err) {
      console.error('加载简历详情失败', err);
    } finally {
      setLoading(false);
    }
  };

  const handleExportAnalysisPdf = async () => {
    setExporting('analysis');
    try {
      const blob = await historyApi.exportAnalysisPdf(resumeId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `简历分析报告_${resume?.filename || resumeId}.pdf`;
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

  const handleExportInterviewPdf = async (sessionId: string) => {
    setExporting(sessionId);
    try {
      const blob = await historyApi.exportInterviewPdf(sessionId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `面试报告_${sessionId}.pdf`;
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

  const handleViewInterview = async (sessionId: string) => {
    setLoadingInterview(true);
    try {
      const detail = await historyApi.getInterviewDetail(sessionId);
      setSelectedInterview(detail);
      setDetailView('interviewDetail');
    } catch (err) {
      alert('加载面试详情失败');
    } finally {
      setLoadingInterview(false);
    }
  };

  const handleBackToInterviewList = () => {
    setDetailView('list');
    setSelectedInterview(null);
  };

  const handleDeleteInterview = async (sessionId: string) => {
    // 删除后重新加载简历详情
    await loadResumeDetail();
    // 如果删除的是当前查看的面试，返回列表
    if (selectedInterview?.sessionId === sessionId) {
      setDetailView('list');
      setSelectedInterview(null);
    }
  };

  const handleTabChange = (tab: TabType) => {
    const newPage = tab === 'analysis' ? 0 : 1;
    setPage([newPage, newPage > page ? 1 : -1]);
    setActiveTab(tab);
    setDetailView('list');
    setSelectedInterview(null);
  };

  const slideVariants = {
    enter: (direction: number) => ({
      x: direction > 0 ? 300 : -300,
      opacity: 0,
    }),
    center: {
      x: 0,
      opacity: 1,
    },
    exit: (direction: number) => ({
      x: direction < 0 ? 300 : -300,
      opacity: 0,
    }),
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <motion.div 
          className="w-12 h-12 border-4 border-slate-200 border-t-primary-500 rounded-full"
          animate={{ rotate: 360 }}
          transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
        />
      </div>
    );
  }

  if (!resume) {
    return (
      <div className="text-center py-20">
        <p className="text-red-500 mb-4">加载失败，请返回重试</p>
        <button onClick={onBack} className="px-6 py-2 bg-primary-500 text-white rounded-lg">返回列表</button>
      </div>
    );
  }

  const latestAnalysis = resume.analyses?.[0];
  const tabs = [
    { id: 'analysis' as const, label: '简历分析', icon: AnalysisIcon },
    { id: 'interview' as const, label: '面试记录', icon: InterviewIcon, count: resume.interviews?.length || 0 },
  ];

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="w-full"
    >
      {/* 顶部导航栏 */}
      <div className="flex justify-between items-center mb-8 flex-wrap gap-4">
        <div className="flex items-center gap-4">
          <motion.button 
            onClick={detailView === 'interviewDetail' ? handleBackToInterviewList : onBack}
            className="w-10 h-10 bg-white rounded-xl flex items-center justify-center text-slate-500 hover:bg-slate-50 hover:text-slate-700 transition-all shadow-sm"
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
              <polyline points="15,18 9,12 15,6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </motion.button>
          <div>
            <h2 className="text-xl font-bold text-slate-900">
              {detailView === 'interviewDetail' ? `面试详情 #${selectedInterview?.sessionId?.slice(-6) || ''}` : resume.filename}
            </h2>
            <p className="text-sm text-slate-500 flex items-center gap-1.5">
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
                <polyline points="12,6 12,12 16,14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              {detailView === 'interviewDetail' 
                ? `完成于 ${formatDateOnly(selectedInterview?.completedAt || selectedInterview?.createdAt || '')}`
                : `上传于 ${formatDateOnly(resume.uploadedAt)}`
              }
            </p>
          </div>
        </div>
        
        <div className="flex gap-3">
          {detailView === 'interviewDetail' && selectedInterview && (
            <motion.button
              onClick={() => handleExportInterviewPdf(selectedInterview.sessionId)}
              disabled={exporting === selectedInterview.sessionId}
              className="px-5 py-2.5 border border-slate-200 bg-white rounded-xl text-slate-600 font-medium hover:bg-slate-50 transition-all disabled:opacity-50 flex items-center gap-2"
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none">
                <path d="M21 15V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V15" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <polyline points="7,10 12,15 17,10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <line x1="12" y1="15" x2="12" y2="3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              {exporting === selectedInterview.sessionId ? '导出中...' : '导出 PDF'}
            </motion.button>
          )}
          {detailView !== 'interviewDetail' && (
            <motion.button
              onClick={() => onStartInterview(resume.resumeText, resumeId)}
              className="px-5 py-2.5 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-medium shadow-lg shadow-primary-500/30 hover:shadow-xl transition-all flex items-center gap-2"
              whileHover={{ scale: 1.02, y: -1 }}
              whileTap={{ scale: 0.98 }}
            >
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none">
                <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M19 10v2a7 7 0 0 1-14 0v-2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <line x1="12" y1="19" x2="12" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <line x1="8" y1="23" x2="16" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              开始模拟面试
            </motion.button>
          )}
        </div>
      </div>

      {/* 标签页切换 - 仅在非面试详情时显示 */}
      {detailView !== 'interviewDetail' && (
        <div className="bg-white rounded-2xl p-2 mb-6 inline-flex gap-1">
          {tabs.map((tab) => (
            <motion.button
              key={tab.id}
              onClick={() => handleTabChange(tab.id)}
              className={`relative px-6 py-3 rounded-xl font-medium flex items-center gap-2 transition-colors
                ${activeTab === tab.id ? 'text-primary-600' : 'text-slate-500 hover:text-slate-700'}`}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              {activeTab === tab.id && (
                <motion.div
                  layoutId="activeTab"
                  className="absolute inset-0 bg-primary-50 rounded-xl"
                  transition={{ type: "spring", bounce: 0.2, duration: 0.6 }}
                />
              )}
              <span className="relative z-10 flex items-center gap-2">
                <tab.icon className="w-5 h-5" />
                {tab.label}
                {tab.count !== undefined && tab.count > 0 && (
                  <span className="px-2 py-0.5 bg-primary-100 text-primary-600 text-xs rounded-full">{tab.count}</span>
                )}
              </span>
            </motion.button>
          ))}
        </div>
      )}

      {/* 内容区域 */}
      <div className="relative overflow-hidden">
        {detailView === 'interviewDetail' && selectedInterview ? (
          <InterviewDetailPanel interview={selectedInterview} />
        ) : (
          <AnimatePresence initial={false} custom={direction} mode="wait">
            <motion.div
              key={activeTab}
              custom={direction}
              variants={slideVariants}
              initial="enter"
              animate="center"
              exit="exit"
              transition={{ type: "spring", stiffness: 300, damping: 30 }}
            >
              {activeTab === 'analysis' ? (
                <AnalysisPanel 
                  analysis={latestAnalysis} 
                  onExport={handleExportAnalysisPdf}
                  exporting={exporting === 'analysis'}
                />
              ) : (
                <InterviewPanel 
                  interviews={resume.interviews || []} 
                  onStartInterview={() => onStartInterview(resume.resumeText, resumeId)}
                  onViewInterview={handleViewInterview}
                  onExportInterview={handleExportInterviewPdf}
                  onDeleteInterview={handleDeleteInterview}
                  exporting={exporting}
                  loadingInterview={loadingInterview}
                />
              )}
            </motion.div>
          </AnimatePresence>
        )}
      </div>
    </motion.div>
  );
}

// Icons
function AnalysisIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none">
      <path d="M9 11L12 14L22 4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
      <path d="M21 12V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H16" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
}

function InterviewIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none">
      <path d="M21 15C21 15.5304 20.7893 16.0391 20.4142 16.4142C20.0391 16.7893 19.5304 17 19 17H7L3 21V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H19C19.5304 3 20.0391 3.21071 20.4142 3.58579C20.7893 3.96086 21 4.46957 21 5V15Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
}
