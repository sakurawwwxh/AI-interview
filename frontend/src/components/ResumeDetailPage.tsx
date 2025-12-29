import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, Radar } from 'recharts';
import { historyApi, ResumeDetail, InterviewItem, InterviewDetail } from '../api/history';

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

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString('zh-CN');
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
                ? `完成于 ${formatDate(selectedInterview?.completedAt || selectedInterview?.createdAt || '')}`
                : `上传于 ${formatDate(resume.uploadedAt)}`
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
          <InterviewDetailPanel 
            interview={selectedInterview}
          />
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

// 简历分析面板
function AnalysisPanel({ analysis, onExport, exporting }: { analysis: any, onExport: () => void, exporting: boolean }) {
  if (!analysis) {
    return (
      <div className="bg-white rounded-2xl p-12 text-center">
        <div className="w-16 h-16 mx-auto mb-6 bg-slate-100 rounded-full flex items-center justify-center">
          <svg className="w-8 h-8 text-slate-400" viewBox="0 0 24 24" fill="none">
            <path d="M3 3V21H21" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M18 9L12 15L9 12L3 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </div>
        <h3 className="text-xl font-semibold text-slate-700 mb-2">暂无分析数据</h3>
        <p className="text-slate-500">请等待 AI 完成简历分析</p>
      </div>
    );
  }

  // 准备雷达图数据
  // 后端返回的数据中，各维度分数直接在analysis对象上，不在scoreDetail中
  const projectScore = analysis.projectScore || 0;
  const skillMatchScore = analysis.skillMatchScore || 0;
  const contentScore = analysis.contentScore || 0;
  const structureScore = analysis.structureScore || 0;
  const expressionScore = analysis.expressionScore || 0;
  
  const radarData = [
    {
      subject: '项目经验',
      score: projectScore,
      fullMark: 40
    },
    {
      subject: '技能匹配',
      score: skillMatchScore,
      fullMark: 20
    },
    {
      subject: '内容完整性',
      score: contentScore,
      fullMark: 15
    },
    {
      subject: '结构清晰度',
      score: structureScore,
      fullMark: 15
    },
    {
      subject: '表达专业性',
      score: expressionScore,
      fullMark: 10
    }
  ];

  // 按优先级分类建议
  const suggestions = analysis.suggestions || [];
  const highPriority = suggestions.filter((s: any) => s.priority === '高');
  const mediumPriority = suggestions.filter((s: any) => s.priority === '中');
  const lowPriority = suggestions.filter((s: any) => s.priority === '低');

  const getPriorityColor = (priority: string) => {
    switch (priority) {
      case '高':
        return 'bg-red-50 border-red-200 text-red-700';
      case '中':
        return 'bg-amber-50 border-amber-200 text-amber-700';
      case '低':
        return 'bg-blue-50 border-blue-200 text-blue-700';
      default:
        return 'bg-slate-50 border-slate-200 text-slate-700';
    }
  };

  const getPriorityBadgeColor = (priority: string) => {
    switch (priority) {
      case '高':
        return 'bg-red-500 text-white';
      case '中':
        return 'bg-amber-500 text-white';
      case '低':
        return 'bg-blue-500 text-white';
      default:
        return 'bg-slate-500 text-white';
    }
  };

  const getCategoryColor = (category: string) => {
    const colors: Record<string, string> = {
      '项目': 'bg-purple-100 text-purple-700',
      '技能': 'bg-indigo-100 text-indigo-700',
      '内容': 'bg-emerald-100 text-emerald-700',
      '格式': 'bg-pink-100 text-pink-700',
      '结构': 'bg-cyan-100 text-cyan-700',
      '表达': 'bg-orange-100 text-orange-700'
    };
    return colors[category] || 'bg-slate-100 text-slate-700';
  };

  return (
    <div className="space-y-6">
      {/* 核心评价和雷达图 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 核心评价 */}
        <motion.div 
          className="bg-white rounded-2xl p-6"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
        >
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-2 text-slate-500">
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
                <path d="M3 3V21H21" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M18 9L12 15L9 12L3 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              <span className="font-semibold">核心评价</span>
            </div>
            <motion.button
              onClick={onExport}
              disabled={exporting}
              className="px-4 py-2 border border-slate-200 bg-white rounded-lg text-slate-600 text-sm font-medium hover:bg-slate-50 transition-all disabled:opacity-50 flex items-center gap-2"
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none">
                <path d="M21 15V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V15" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <polyline points="7,10 12,15 17,10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <line x1="12" y1="15" x2="12" y2="3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              {exporting ? '导出中...' : '导出分析报告'}
            </motion.button>
          </div>

          <div className="bg-gradient-to-br from-emerald-50 to-green-50 rounded-xl p-6">
            <p className="text-lg text-slate-800 leading-relaxed mb-6">
              {analysis.summary || '候选人具备扎实的技术基础，有大型项目架构经验。'}
            </p>

            <div className="grid grid-cols-2 gap-4 mb-4">
              <div className="bg-white rounded-xl p-5">
                <span className="text-sm font-semibold text-emerald-600 block mb-2">总分</span>
                <span className="text-4xl font-bold text-slate-900">{analysis.overallScore || 0}</span>
                <span className="text-sm text-slate-500">/ 100</span>
              </div>
              <div className="bg-white rounded-xl p-5">
                <span className="text-sm font-semibold text-emerald-600 block mb-2">分析时间</span>
                <span className="text-sm text-slate-700">
                  {analysis.analyzedAt 
                    ? new Date(analysis.analyzedAt).toLocaleString('zh-CN', { 
                        year: 'numeric', 
                        month: '2-digit', 
                        day: '2-digit',
                        hour: '2-digit',
                        minute: '2-digit'
                      })
                    : '-'}
                </span>
              </div>
            </div>

            {/* 优势标签 */}
            {analysis.strengths && analysis.strengths.length > 0 && (
              <div className="bg-white rounded-xl p-4">
                <span className="text-sm font-semibold text-emerald-600 block mb-3">优势亮点</span>
                <div className="flex flex-wrap gap-2">
                  {analysis.strengths.map((s: string, i: number) => (
                    <span key={i} className="px-3 py-1.5 bg-emerald-100 text-emerald-700 border border-emerald-200 rounded-lg text-sm font-medium">
                      {s}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        </motion.div>

        {/* 多维度评分雷达图 */}
        <motion.div 
          className="bg-white rounded-2xl p-6"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          <div className="flex items-center gap-2 text-slate-500 mb-6">
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
              <circle cx="12" cy="12" r="6" stroke="currentColor" strokeWidth="2"/>
              <circle cx="12" cy="12" r="2" fill="currentColor"/>
            </svg>
            <span className="font-semibold">多维度评分</span>
          </div>

          <div className="h-80">
            <ResponsiveContainer width="100%" height="100%">
              <RadarChart data={radarData}>
                <PolarGrid stroke="#e2e8f0" />
                <PolarAngleAxis 
                  dataKey="subject" 
                  tick={{ fill: '#64748b', fontSize: 12, fontWeight: 500 }}
                />
                <PolarRadiusAxis 
                  angle={90} 
                  domain={[0, 40]}
                  tick={{ fill: '#94a3b8', fontSize: 10 }}
                />
                <Radar
                  name="得分"
                  dataKey="score"
                  stroke="#6366f1"
                  fill="#6366f1"
                  fillOpacity={0.6}
                  strokeWidth={2}
                />
                <Tooltip 
                  contentStyle={{ 
                    backgroundColor: '#fff', 
                    border: '1px solid #e2e8f0',
                    borderRadius: '12px',
                    boxShadow: '0 4px 12px rgba(0,0,0,0.1)'
                  }}
                  formatter={(value: number | undefined) => [`${value ?? 0} 分`, '得分']}
                />
              </RadarChart>
            </ResponsiveContainer>
          </div>

          {/* 维度得分详情 */}
          <div className="mt-4 grid grid-cols-2 gap-3">
            <div className="bg-slate-50 rounded-lg p-3">
              <div className="text-xs text-slate-500 mb-1">项目经验</div>
              <div className="flex items-center gap-2">
                <div className="flex-1 h-2 bg-slate-200 rounded-full overflow-hidden">
                  <motion.div 
                    className="h-full bg-purple-500 rounded-full"
                    initial={{ width: 0 }}
                    animate={{ width: `${(projectScore / 40) * 100}%` }}
                    transition={{ duration: 0.8, delay: 0.3 }}
                  />
                </div>
                <span className="text-sm font-semibold text-slate-700 w-8 text-right">
                  {projectScore}/40
                </span>
              </div>
            </div>
            <div className="bg-slate-50 rounded-lg p-3">
              <div className="text-xs text-slate-500 mb-1">技能匹配</div>
              <div className="flex items-center gap-2">
                <div className="flex-1 h-2 bg-slate-200 rounded-full overflow-hidden">
                  <motion.div 
                    className="h-full bg-indigo-500 rounded-full"
                    initial={{ width: 0 }}
                    animate={{ width: `${(skillMatchScore / 20) * 100}%` }}
                    transition={{ duration: 0.8, delay: 0.4 }}
                  />
                </div>
                <span className="text-sm font-semibold text-slate-700 w-8 text-right">
                  {skillMatchScore}/20
                </span>
              </div>
            </div>
            <div className="bg-slate-50 rounded-lg p-3">
              <div className="text-xs text-slate-500 mb-1">内容完整性</div>
              <div className="flex items-center gap-2">
                <div className="flex-1 h-2 bg-slate-200 rounded-full overflow-hidden">
                  <motion.div 
                    className="h-full bg-emerald-500 rounded-full"
                    initial={{ width: 0 }}
                    animate={{ width: `${(contentScore / 15) * 100}%` }}
                    transition={{ duration: 0.8, delay: 0.5 }}
                  />
                </div>
                <span className="text-sm font-semibold text-slate-700 w-8 text-right">
                  {contentScore}/15
                </span>
              </div>
            </div>
            <div className="bg-slate-50 rounded-lg p-3">
              <div className="text-xs text-slate-500 mb-1">结构清晰度</div>
              <div className="flex items-center gap-2">
                <div className="flex-1 h-2 bg-slate-200 rounded-full overflow-hidden">
                  <motion.div 
                    className="h-full bg-cyan-500 rounded-full"
                    initial={{ width: 0 }}
                    animate={{ width: `${(structureScore / 15) * 100}%` }}
                    transition={{ duration: 0.8, delay: 0.6 }}
                  />
                </div>
                <span className="text-sm font-semibold text-slate-700 w-8 text-right">
                  {structureScore}/15
                </span>
              </div>
            </div>
            <div className="bg-slate-50 rounded-lg p-3 col-span-2">
              <div className="text-xs text-slate-500 mb-1">表达专业性</div>
              <div className="flex items-center gap-2">
                <div className="flex-1 h-2 bg-slate-200 rounded-full overflow-hidden">
                  <motion.div 
                    className="h-full bg-orange-500 rounded-full"
                    initial={{ width: 0 }}
                    animate={{ width: `${(expressionScore / 10) * 100}%` }}
                    transition={{ duration: 0.8, delay: 0.7 }}
                  />
                </div>
                <span className="text-sm font-semibold text-slate-700 w-8 text-right">
                  {expressionScore}/10
                </span>
              </div>
            </div>
          </div>
        </motion.div>
      </div>

      {/* 改进建议 - 按优先级分类 */}
      <motion.div 
        className="bg-white rounded-2xl p-6"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.3 }}
      >
        <div className="flex items-center gap-2 text-slate-500 mb-6">
          <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
            <polyline points="9,12 11,14 15,10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <span className="font-semibold">改进建议</span>
          <span className="text-sm text-slate-400">({suggestions.length} 条)</span>
        </div>

        <div className="space-y-6">
          {/* 高优先级 */}
          {highPriority.length > 0 && (
            <div>
              <div className="flex items-center gap-2 mb-4">
                <span className="px-3 py-1 bg-red-100 text-red-700 rounded-full text-sm font-semibold">
                  高优先级 ({highPriority.length})
                </span>
                <div className="flex-1 h-px bg-red-100"></div>
              </div>
              <div className="space-y-3">
                {highPriority.map((s: any, i: number) => (
                  <motion.div 
                    key={`high-${i}`}
                    className={`p-4 rounded-xl border-2 ${getPriorityColor('高')}`}
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: 0.4 + i * 0.1 }}
                  >
                    <div className="flex items-start gap-3 mb-2">
                      <span className={`px-2 py-0.5 rounded text-xs font-semibold ${getPriorityBadgeColor('高')}`}>
                        高
                      </span>
                      <span className={`px-2 py-0.5 rounded text-xs font-medium ${getCategoryColor(s.category || '其他')}`}>
                        {s.category || '其他'}
                      </span>
                    </div>
                    <div className="mb-2">
                      <p className="font-semibold text-slate-900 mb-1">{s.issue || '问题描述'}</p>
                      <p className="text-sm leading-relaxed">{s.recommendation || s}</p>
                    </div>
                  </motion.div>
                ))}
              </div>
            </div>
          )}

          {/* 中优先级 */}
          {mediumPriority.length > 0 && (
            <div>
              <div className="flex items-center gap-2 mb-4">
                <span className="px-3 py-1 bg-amber-100 text-amber-700 rounded-full text-sm font-semibold">
                  中优先级 ({mediumPriority.length})
                </span>
                <div className="flex-1 h-px bg-amber-100"></div>
              </div>
              <div className="space-y-3">
                {mediumPriority.map((s: any, i: number) => (
                  <motion.div 
                    key={`medium-${i}`}
                    className={`p-4 rounded-xl border-2 ${getPriorityColor('中')}`}
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: 0.5 + i * 0.1 }}
                  >
                    <div className="flex items-start gap-3 mb-2">
                      <span className={`px-2 py-0.5 rounded text-xs font-semibold ${getPriorityBadgeColor('中')}`}>
                        中
                      </span>
                      <span className={`px-2 py-0.5 rounded text-xs font-medium ${getCategoryColor(s.category || '其他')}`}>
                        {s.category || '其他'}
                      </span>
                    </div>
                    <div className="mb-2">
                      <p className="font-semibold text-slate-900 mb-1">{s.issue || '问题描述'}</p>
                      <p className="text-sm leading-relaxed">{s.recommendation || s}</p>
                    </div>
                  </motion.div>
                ))}
              </div>
            </div>
          )}

          {/* 低优先级 */}
          {lowPriority.length > 0 && (
            <div>
              <div className="flex items-center gap-2 mb-4">
                <span className="px-3 py-1 bg-blue-100 text-blue-700 rounded-full text-sm font-semibold">
                  低优先级 ({lowPriority.length})
                </span>
                <div className="flex-1 h-px bg-blue-100"></div>
              </div>
              <div className="space-y-3">
                {lowPriority.map((s: any, i: number) => (
                  <motion.div 
                    key={`low-${i}`}
                    className={`p-4 rounded-xl border-2 ${getPriorityColor('低')}`}
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: 0.6 + i * 0.1 }}
                  >
                    <div className="flex items-start gap-3 mb-2">
                      <span className={`px-2 py-0.5 rounded text-xs font-semibold ${getPriorityBadgeColor('低')}`}>
                        低
                      </span>
                      <span className={`px-2 py-0.5 rounded text-xs font-medium ${getCategoryColor(s.category || '其他')}`}>
                        {s.category || '其他'}
                      </span>
                    </div>
                    <div className="mb-2">
                      <p className="font-semibold text-slate-900 mb-1">{s.issue || '问题描述'}</p>
                      <p className="text-sm leading-relaxed">{s.recommendation || s}</p>
                    </div>
                  </motion.div>
                ))}
              </div>
            </div>
          )}

          {suggestions.length === 0 && (
            <div className="text-center py-8 text-slate-500">暂无改进建议</div>
          )}
        </div>
      </motion.div>
    </div>
  );
}

// 面试记录面板
function InterviewPanel({ 
  interviews, 
  onStartInterview, 
  onViewInterview, 
  onExportInterview,
  exporting,
  loadingInterview 
}: { 
  interviews: InterviewItem[], 
  onStartInterview: () => void, 
  onViewInterview: (sessionId: string) => void,
  onExportInterview: (sessionId: string) => void,
  exporting: string | null,
  loadingInterview: boolean
}) {
  const formatDate = (dateStr: string) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' });
  };

  const getScoreColor = (score: number) => {
    if (score >= 85) return 'bg-emerald-100 text-emerald-600';
    if (score >= 70) return 'bg-amber-100 text-amber-600';
    return 'bg-red-100 text-red-600';
  };

  // 准备图表数据
  const chartData = interviews
    .filter(i => i.overallScore !== null)
    .map((interview, index) => ({
      name: formatDate(interview.createdAt),
      score: interview.overallScore || 0,
      index: index + 1
    }))
    .reverse();

  if (interviews.length === 0) {
    return (
      <div className="bg-white rounded-2xl p-12 text-center">
        <div className="w-16 h-16 mx-auto mb-6 bg-slate-100 rounded-full flex items-center justify-center">
          <svg className="w-8 h-8 text-slate-400" viewBox="0 0 24 24" fill="none">
            <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M19 10v2a7 7 0 0 1-14 0v-2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </div>
        <h3 className="text-xl font-semibold text-slate-700 mb-2">暂无面试记录</h3>
        <p className="text-slate-500 mb-6">开始模拟面试，获取专业评估</p>
        <motion.button
          onClick={onStartInterview}
          className="px-6 py-3 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-medium shadow-lg shadow-primary-500/30"
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
        >
          开始模拟面试
        </motion.button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 面试表现趋势图 */}
      {chartData.length > 0 && (
        <motion.div 
          className="bg-white rounded-2xl p-6"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-2">
              <svg className="w-5 h-5 text-primary-500" viewBox="0 0 24 24" fill="none">
                <path d="M23 6L13.5 15.5L8.5 10.5L1 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M17 6H23V12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              <span className="font-semibold text-slate-800">面试表现趋势</span>
            </div>
            <span className="text-sm text-slate-500">共 {chartData.length} 场练习</span>
          </div>
          
          <div className="h-48">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis 
                  dataKey="name" 
                  axisLine={false}
                  tickLine={false}
                  tick={{ fill: '#94a3b8', fontSize: 12 }}
                />
                <YAxis 
                  domain={[0, 100]}
                  axisLine={false}
                  tickLine={false}
                  tick={{ fill: '#94a3b8', fontSize: 12 }}
                />
                <Tooltip 
                  contentStyle={{ 
                    backgroundColor: '#fff', 
                    border: '1px solid #e2e8f0',
                    borderRadius: '12px',
                    boxShadow: '0 4px 12px rgba(0,0,0,0.1)'
                  }}
                  formatter={(value) => [`${value} 分`, '得分']}
                />
                <Line 
                  type="monotone" 
                  dataKey="score" 
                  stroke="#6366f1" 
                  strokeWidth={3}
                  dot={{ fill: '#6366f1', strokeWidth: 2, r: 5 }}
                  activeDot={{ r: 8, fill: '#6366f1' }}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </motion.div>
      )}

      {/* 历史面试场次 */}
      <motion.div 
        className="bg-white rounded-2xl p-6"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
      >
        <div className="flex items-center justify-between mb-6">
          <span className="font-semibold text-slate-800">历史面试场次</span>
        </div>

        <div className="space-y-4">
          {interviews.map((interview, index) => (
            <motion.div
              key={interview.id}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: index * 0.1 }}
              onClick={() => onViewInterview(interview.sessionId)}
              className="flex items-center gap-4 p-4 bg-slate-50 rounded-xl hover:bg-slate-100 cursor-pointer transition-colors group"
            >
              {/* 得分 */}
              <div className={`w-14 h-14 rounded-full flex items-center justify-center font-bold text-lg ${
                interview.overallScore !== null 
                  ? getScoreColor(interview.overallScore)
                  : 'bg-slate-100 text-slate-400'
              }`}>
                {interview.overallScore ?? '-'}
              </div>

              {/* 信息 */}
              <div className="flex-1 min-w-0">
                <p className="font-medium text-slate-800 truncate">
                  模拟面试 #{interviews.length - index}
                </p>
                <div className="flex items-center gap-4 text-sm text-slate-500">
                  <span className="flex items-center gap-1">
                    <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none">
                      <rect x="3" y="4" width="18" height="18" rx="2" ry="2" stroke="currentColor" strokeWidth="2"/>
                      <line x1="16" y1="2" x2="16" y2="6" stroke="currentColor" strokeWidth="2"/>
                      <line x1="8" y1="2" x2="8" y2="6" stroke="currentColor" strokeWidth="2"/>
                      <line x1="3" y1="10" x2="21" y2="10" stroke="currentColor" strokeWidth="2"/>
                    </svg>
                    {formatDate(interview.createdAt)}
                  </span>
                  <span className="flex items-center gap-1">
                    <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none">
                      <path d="M21 15C21 15.5304 20.7893 16.0391 20.4142 16.4142C20.0391 16.7893 19.5304 17 19 17H7L3 21V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H19C19.5304 3 20.0391 3.21071 20.4142 3.58579C20.7893 3.96086 21 4.46957 21 5V15Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                    {interview.totalQuestions} 题
                  </span>
                </div>
              </div>

              {/* 导出按钮 */}
              <motion.button
                onClick={(e) => { e.stopPropagation(); onExportInterview(interview.sessionId); }}
                disabled={exporting === interview.sessionId}
                className="px-3 py-2 text-slate-400 hover:text-primary-500 hover:bg-white rounded-lg transition-all opacity-0 group-hover:opacity-100"
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
              >
                <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
                  <path d="M21 15V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V15" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  <polyline points="7,10 12,15 17,10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  <line x1="12" y1="15" x2="12" y2="3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </motion.button>

              {/* 箭头 */}
              <svg className="w-5 h-5 text-slate-300 group-hover:text-primary-500 group-hover:translate-x-1 transition-all flex-shrink-0" viewBox="0 0 24 24" fill="none">
                <polyline points="9,18 15,12 9,6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </motion.div>
          ))}
        </div>

        {loadingInterview && (
          <div className="fixed inset-0 bg-black/20 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 flex items-center gap-4">
              <motion.div 
                className="w-8 h-8 border-3 border-slate-200 border-t-primary-500 rounded-full"
                animate={{ rotate: 360 }}
                transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
              />
              <span className="text-slate-600">加载面试详情...</span>
            </div>
          </div>
        )}
      </motion.div>
    </div>
  );
}

// 面试详情面板
function InterviewDetailPanel({ interview }: { interview: InterviewDetail }) {
  const [expandedQuestions, setExpandedQuestions] = useState<Set<number>>(new Set([0]));

  const toggleQuestion = (index: number) => {
    setExpandedQuestions(prev => {
      const newSet = new Set(prev);
      if (newSet.has(index)) {
        newSet.delete(index);
      } else {
        newSet.add(index);
      }
      return newSet;
    });
  };

  const getScoreColor = (score: number) => {
    if (score >= 80) return 'text-emerald-600';
    if (score >= 60) return 'text-amber-600';
    return 'text-red-500';
  };

  // 计算圆环进度
  const scorePercent = interview.overallScore !== null ? (interview.overallScore / 100) * 100 : 0;
  const circumference = 2 * Math.PI * 54; // r=54
  const strokeDashoffset = circumference - (scorePercent / 100) * circumference;

  return (
    <motion.div 
      className="space-y-6"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
    >
      {/* 评分卡片 - 紫色渐变 */}
      <div className="bg-gradient-to-br from-violet-600 via-purple-600 to-indigo-700 rounded-2xl p-8 text-white">
        <div className="flex flex-col items-center text-center">
          {/* 圆环进度条 */}
          <div className="relative w-32 h-32 mb-6">
            <svg className="w-32 h-32 transform -rotate-90" viewBox="0 0 120 120">
              {/* 背景圆环 */}
              <circle
                cx="60"
                cy="60"
                r="54"
                stroke="rgba(255,255,255,0.2)"
                strokeWidth="8"
                fill="none"
              />
              {/* 进度圆环 */}
              <motion.circle
                cx="60"
                cy="60"
                r="54"
                stroke="white"
                strokeWidth="8"
                fill="none"
                strokeLinecap="round"
                strokeDasharray={circumference}
                initial={{ strokeDashoffset: circumference }}
                animate={{ strokeDashoffset }}
                transition={{ duration: 1.5, ease: "easeOut" }}
              />
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <motion.span 
                className="text-4xl font-bold"
                initial={{ opacity: 0, scale: 0.5 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ delay: 0.5 }}
              >
                {interview.overallScore ?? '-'}
              </motion.span>
              <span className="text-sm text-white/70">总分</span>
            </div>
          </div>
          
          <h3 className="text-2xl font-bold mb-3">面试评估</h3>
          <p className="text-white/90 max-w-2xl leading-relaxed">
            {interview.overallFeedback || '表现良好，展示了扎实的技术基础。'}
          </p>
        </div>
      </div>

      {/* 表现优势 */}
      {interview.strengths && interview.strengths.length > 0 && (
        <motion.div 
          className="bg-white rounded-2xl p-6 shadow-sm"
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
        >
          <h4 className="font-semibold text-emerald-600 mb-4 flex items-center gap-2">
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <polyline points="22,4 12,14.01 9,11.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            表现优势
          </h4>
          <ul className="space-y-3">
            {interview.strengths.map((s: string, i: number) => (
              <li key={i} className="text-slate-700 flex items-start gap-3">
                <span className="w-2 h-2 bg-primary-500 rounded-full mt-2 flex-shrink-0"></span>
                <span>{s}</span>
              </li>
            ))}
          </ul>
        </motion.div>
      )}

      {/* 改进建议 */}
      {interview.improvements && interview.improvements.length > 0 && (
        <motion.div 
          className="bg-white rounded-2xl p-6 shadow-sm"
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          <h4 className="font-semibold text-amber-600 mb-4 flex items-center gap-2">
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
              <line x1="12" y1="8" x2="12" y2="12" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
              <line x1="12" y1="16" x2="12.01" y2="16" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
            </svg>
            改进建议
          </h4>
          <ul className="space-y-3">
            {interview.improvements.map((s: string, i: number) => (
              <li key={i} className="text-slate-700 flex items-start gap-3">
                <span className="w-2 h-2 bg-amber-500 rounded-full mt-2 flex-shrink-0"></span>
                <span>{s}</span>
              </li>
            ))}
          </ul>
        </motion.div>
      )}

      {/* 问答记录详情 */}
      <div>
        <h4 className="font-semibold text-slate-800 mb-4 flex items-center gap-2">
          <svg className="w-5 h-5 text-primary-500" viewBox="0 0 24 24" fill="none">
            <path d="M21 15C21 15.5304 20.7893 16.0391 20.4142 16.4142C20.0391 16.7893 19.5304 17 19 17H7L3 21V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H19C19.5304 3 20.0391 3.21071 20.4142 3.58579C20.7893 3.96086 21 4.46957 21 5V15Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          问答记录详情
        </h4>
        
        <div className="space-y-4">
          {interview.answers?.map((answer, idx) => (
            <motion.div 
              key={idx}
              className="bg-white rounded-2xl shadow-sm overflow-hidden"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.1 + idx * 0.05 }}
            >
              {/* 问题头部 - 可点击展开 */}
              <div 
                className="px-5 py-4 flex items-center justify-between cursor-pointer hover:bg-slate-50 transition-colors"
                onClick={() => toggleQuestion(idx)}
              >
                <div className="flex items-center gap-3">
                  <span className="w-8 h-8 bg-slate-100 text-slate-600 rounded-lg flex items-center justify-center text-sm font-semibold">
                    {answer.questionIndex + 1}
                  </span>
                  <span className="px-3 py-1 bg-primary-50 text-primary-600 text-xs font-medium rounded-full">
                    {answer.category || '综合'}
                  </span>
                  <span className={`font-semibold ${getScoreColor(answer.score)}`}>
                    得分: {answer.score}
                  </span>
                </div>
                <motion.svg 
                  className="w-5 h-5 text-slate-400"
                  animate={{ rotate: expandedQuestions.has(idx) ? 180 : 0 }}
                  transition={{ duration: 0.2 }}
                  viewBox="0 0 24 24" 
                  fill="none"
                >
                  <polyline points="6,9 12,15 18,9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </motion.svg>
              </div>
              
              {/* 问题内容 */}
              <div className="px-5 pb-2">
                <p className="text-slate-800 font-medium leading-relaxed">{answer.question}</p>
              </div>

              {/* 展开内容 */}
              <AnimatePresence>
                {expandedQuestions.has(idx) && (
                  <motion.div
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: 'auto', opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    transition={{ duration: 0.3 }}
                    className="overflow-hidden"
                  >
                    <div className="px-5 pb-5 space-y-4">
                      {/* 你的回答 */}
                      <div className="bg-slate-50 rounded-xl p-4">
                        <p className="text-sm text-slate-500 mb-2 flex items-center gap-1">
                          <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none">
                            <path d="M21 15C21 15.5304 20.7893 16.0391 20.4142 16.4142C20.0391 16.7893 19.5304 17 19 17H7L3 21V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H19C19.5304 3 20.0391 3.21071 20.4142 3.58579C20.7893 3.96086 21 4.46957 21 5V15Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                          </svg>
                          你的回答
                        </p>
                        <p className={`leading-relaxed ${
                          !answer.userAnswer || answer.userAnswer === '不知道' 
                            ? 'text-red-500 font-medium' 
                            : 'text-slate-700'
                        }`}>
                          "{answer.userAnswer || '(未回答)'}"</p>
                      </div>

                      {/* AI 深度评价 */}
                      {answer.feedback && (
                        <div>
                          <p className="text-sm text-slate-600 mb-2 flex items-center gap-2 font-medium">
                            <svg className="w-4 h-4 text-primary-500" viewBox="0 0 24 24" fill="none">
                              <path d="M3 3V21H21" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                              <path d="M18 9L12 15L9 12L3 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                            </svg>
                            AI 深度评价
                          </p>
                          <p className="text-slate-700 leading-relaxed pl-6">{answer.feedback}</p>
                        </div>
                      )}

                      {/* 参考答案 */}
                      {answer.referenceAnswer && (
                        <div className="bg-slate-50 rounded-xl p-4 border border-slate-100">
                          <p className="text-sm text-slate-600 mb-3 flex items-center gap-2 font-medium">
                            <svg className="w-4 h-4 text-primary-500" viewBox="0 0 24 24" fill="none">
                              <rect x="3" y="3" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="2"/>
                              <path d="M9 12H15" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                              <path d="M12 9V15" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                            </svg>
                            参考答案
                          </p>
                          <div className="text-slate-700 leading-relaxed whitespace-pre-line">{answer.referenceAnswer}</div>
                        </div>
                      )}

                      {/* 关键要点 */}
                      {answer.keyPoints && answer.keyPoints.length > 0 && (
                        <div className="pt-2">
                          <p className="text-xs text-amber-600 flex items-center gap-1">
                            <svg className="w-3 h-3" viewBox="0 0 24 24" fill="none">
                              <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
                              <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                              <line x1="12" y1="17" x2="12.01" y2="17" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                            </svg>
                            提示：点击参考答案中的关键词可查看详细文档
                          </p>
                        </div>
                      )}
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.div>
          ))}
        </div>
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
