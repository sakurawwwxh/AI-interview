import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { historyApi, ResumeDetail, InterviewItem } from '../api/history';

interface ResumeDetailPageProps {
  resumeId: number;
  onBack: () => void;
  onStartInterview: (resumeText: string, resumeId: number) => void;
}

type TabType = 'analysis' | 'interview';

export default function ResumeDetailPage({ resumeId, onBack, onStartInterview }: ResumeDetailPageProps) {
  const [resume, setResume] = useState<ResumeDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<TabType>('analysis');
  const [exporting, setExporting] = useState(false);
  const [[page, direction], setPage] = useState([0, 0]);

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

  const handleExportPdf = async () => {
    setExporting(true);
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
      setExporting(false);
    }
  };

  const handleTabChange = (tab: TabType) => {
    const newPage = tab === 'analysis' ? 0 : 1;
    setPage([newPage, newPage > page ? 1 : -1]);
    setActiveTab(tab);
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
            onClick={onBack}
            className="w-10 h-10 bg-white rounded-xl flex items-center justify-center text-slate-500 hover:bg-slate-50 hover:text-slate-700 transition-all shadow-sm"
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
              <polyline points="15,18 9,12 15,6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </motion.button>
          <div>
            <h2 className="text-xl font-bold text-slate-900">{resume.filename}</h2>
            <p className="text-sm text-slate-500 flex items-center gap-1.5">
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
                <polyline points="12,6 12,12 16,14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              上传于 {formatDate(resume.uploadedAt)}
            </p>
          </div>
        </div>
        
        <div className="flex gap-3">
          <motion.button
            onClick={handleExportPdf}
            disabled={exporting}
            className="px-5 py-2.5 border border-slate-200 bg-white rounded-xl text-slate-600 font-medium hover:bg-slate-50 transition-all disabled:opacity-50"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
          >
            {exporting ? '导出中...' : '导出 PDF'}
          </motion.button>
          <motion.button
            onClick={() => onStartInterview(resume.resumeText, resumeId)}
            className="px-5 py-2.5 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-medium shadow-lg shadow-primary-500/30 hover:shadow-xl transition-all"
            whileHover={{ scale: 1.02, y: -1 }}
            whileTap={{ scale: 0.98 }}
          >
            开始模拟面试
          </motion.button>
        </div>
      </div>

      {/* 标签页切换 */}
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

      {/* 内容区域 - 带滑动动画 */}
      <div className="relative overflow-hidden">
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
              <AnalysisPanel analysis={latestAnalysis} />
            ) : (
              <InterviewPanel 
                interviews={resume.interviews || []} 
                onStartInterview={() => onStartInterview(resume.resumeText, resumeId)}
              />
            )}
          </motion.div>
        </AnimatePresence>
      </div>
    </motion.div>
  );
}

// 简历分析面板
function AnalysisPanel({ analysis }: { analysis: any }) {
  if (!analysis) {
    return (
      <div className="bg-white rounded-2xl p-12 text-center">
        <div className="text-6xl mb-6">📊</div>
        <h3 className="text-xl font-semibold text-slate-700 mb-2">暂无分析数据</h3>
        <p className="text-slate-500">请等待 AI 完成简历分析</p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      {/* 核心评价 */}
      <motion.div 
        className="bg-white rounded-2xl p-6 lg:col-span-2"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
      >
        <div className="flex items-center gap-2 text-slate-500 mb-6">
          <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
            <path d="M3 3V21H21" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M18 9L12 15L9 12L3 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <span className="font-semibold">核心评价</span>
        </div>

        <div className="bg-gradient-to-br from-emerald-50 to-green-50 rounded-xl p-6">
          <p className="text-lg text-slate-800 leading-relaxed mb-6">
            {analysis.summary || '候选人具备扎实的技术基础，有大型项目架构经验。'}
          </p>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="bg-white rounded-xl p-5">
              <span className="text-sm font-semibold text-emerald-600 block mb-2">匹配度评分</span>
              <span className="text-4xl font-bold text-slate-900">{analysis.overallScore}%</span>
            </div>
            <div className="bg-white rounded-xl p-5">
              <span className="text-sm font-semibold text-emerald-600 block mb-3">技能标签</span>
              <div className="flex flex-wrap gap-2">
                {analysis.strengths?.slice(0, 4).map((s: string, i: number) => (
                  <span key={i} className="px-3 py-1.5 bg-slate-100 border border-slate-200 rounded-lg text-sm text-slate-600">
                    {s.length > 10 ? s.substring(0, 10) + '...' : s}
                  </span>
                )) || (
                  <>
                    <span className="px-3 py-1.5 bg-slate-100 border border-slate-200 rounded-lg text-sm text-slate-600">Java</span>
                    <span className="px-3 py-1.5 bg-slate-100 border border-slate-200 rounded-lg text-sm text-slate-600">Spring</span>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
      </motion.div>

      {/* 改进建议 */}
      <motion.div 
        className="bg-white rounded-2xl p-6 lg:col-span-2"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
      >
        <div className="flex items-center gap-2 text-slate-500 mb-6">
          <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
            <polyline points="9,12 11,14 15,10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <span className="font-semibold">改进建议</span>
        </div>

        <div className="space-y-3">
          {analysis.suggestions?.map((s: any, i: number) => (
            <motion.div 
              key={i}
              className="flex items-start gap-4 p-4 bg-slate-50 rounded-xl border border-slate-100"
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.3 + i * 0.1 }}
            >
              <span className="w-7 h-7 bg-primary-500 text-white rounded-lg flex items-center justify-center text-sm font-semibold flex-shrink-0">
                {i + 1}
              </span>
              <span className="text-slate-600">{s.recommendation || s}</span>
            </motion.div>
          )) || (
            <div className="text-center py-8 text-slate-500">暂无改进建议</div>
          )}
        </div>
      </motion.div>
    </div>
  );
}

// 面试记录面板
function InterviewPanel({ interviews, onStartInterview }: { interviews: InterviewItem[], onStartInterview: () => void }) {
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
    .reverse(); // 按时间正序

  if (interviews.length === 0) {
    return (
      <div className="bg-white rounded-2xl p-12 text-center">
        <div className="text-6xl mb-6">🎙️</div>
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
          <button className="text-sm text-primary-500 flex items-center gap-1 hover:text-primary-600">
            <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none">
              <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            筛选
          </button>
        </div>

        <div className="space-y-4">
          {interviews.map((interview, index) => (
            <motion.div
              key={interview.id}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: index * 0.1 }}
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
                      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
                      <polyline points="12,6 12,12 16,14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                    {interview.totalQuestions} 题
                  </span>
                </div>
              </div>

              {/* 箭头 */}
              <svg className="w-5 h-5 text-slate-300 group-hover:text-primary-500 group-hover:translate-x-1 transition-all flex-shrink-0" viewBox="0 0 24 24" fill="none">
                <polyline points="9,18 15,12 9,6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </motion.div>
          ))}
        </div>
      </motion.div>
    </div>
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
