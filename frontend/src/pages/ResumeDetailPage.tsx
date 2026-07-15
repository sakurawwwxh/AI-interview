import {useCallback, useEffect, useState} from 'react';
import {useLocation} from 'react-router-dom';
import {AnimatePresence, motion} from 'framer-motion';
import {historyApi, InterviewDetail, ResumeDetail} from '../api/history';
import {jobTargetApi, JobMatch, JobTarget} from '../api/jobTarget';
import AnalysisPanel from '../components/AnalysisPanel';
import InterviewPanel from '../components/InterviewPanel';
import InterviewDetailPanel from '../components/InterviewDetailPanel';
import ResumeEditorPanel from '../components/ResumeEditorPanel';
import {formatDateOnly} from '../utils/date';
import {CheckSquare, ChevronLeft, Clock, Download, FilePenLine, MessageSquare, Mic, Target} from 'lucide-react';

interface ResumeDetailPageProps {
  resumeId: number;
  onBack: () => void;
  /** 可选 jobTargetId：从 JD 匹配结果一键开面时带入 */
  onStartInterview: (resumeText: string, resumeId: number, jobTargetId?: number) => void;
}

type TabType = 'analysis' | 'edit' | 'interview';
type DetailViewType = 'list' | 'interviewDetail';

export default function ResumeDetailPage({ resumeId, onBack, onStartInterview }: ResumeDetailPageProps) {
  const location = useLocation();
  const [resume, setResume] = useState<ResumeDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<TabType>('analysis');
  const [exporting, setExporting] = useState<string | null>(null);
  const [[, direction], setPage] = useState([0, 0]);
  const [detailView, setDetailView] = useState<DetailViewType>('list');
  const [selectedInterview, setSelectedInterview] = useState<InterviewDetail | null>(null);
  const [loadingInterview, setLoadingInterview] = useState(false);
  const [reanalyzing, setReanalyzing] = useState(false);
  const [jobTargets, setJobTargets] = useState<JobTarget[]>([]);
  const [jobTargetId, setJobTargetId] = useState<number | undefined>();
  const [jobMatch, setJobMatch] = useState<JobMatch | null>(null);
  const [matching, setMatching] = useState(false);
  /** 从 JD 匹配「应用优化稿」注入编辑器的草稿 */
  const [externalDraft, setExternalDraft] = useState<{ title: string; content: string } | null>(null);

  // 静默加载数据（用于轮询）
  const loadResumeDetailSilent = useCallback(async () => {
    try {
      const data = await historyApi.getResumeDetail(resumeId);
      setResume(data);
    } catch (err) {
      console.error('加载简历详情失败', err);
    }
  }, [resumeId]);

  const loadResumeDetail = useCallback(async () => {
    setLoading(true);
    try {
      const data = await historyApi.getResumeDetail(resumeId);
      setResume(data);
    } catch (err) {
      console.error('加载简历详情失败', err);
    } finally {
      setLoading(false);
    }
  }, [resumeId]);

  useEffect(() => {
    loadResumeDetail();
  }, [loadResumeDetail]);

  useEffect(() => {
    jobTargetApi.list().then(items => {
      setJobTargets(items);
      setJobTargetId(items.find(item => item.active)?.id);
    }).catch(() => {});
  }, [resumeId]);

  // 轮询：当分析状态为待处理时，每5秒刷新一次
  // 待处理判断：显式的 PENDING/PROCESSING 状态，或状态未定义且无分析结果
  useEffect(() => {
    const isProcessing = resume && (
      resume.analyzeStatus === 'PENDING' ||
      resume.analyzeStatus === 'PROCESSING' ||
      (resume.analyzeStatus === undefined && (!resume.analyses || resume.analyses.length === 0))
    );

    if (isProcessing && !loading) {
      const timer = setInterval(() => {
        loadResumeDetailSilent();
      }, 5000);

      return () => clearInterval(timer);
    }
  }, [resume, loading, loadResumeDetailSilent]);

  // 重新分析
  const handleReanalyze = async () => {
    try {
      setReanalyzing(true);
      await historyApi.reanalyze(resumeId);
      await loadResumeDetailSilent();
    } catch (err) {
      console.error('重新分析失败', err);
    } finally {
      setReanalyzing(false);
    }
  };

  const handleMatch = async () => {
    if (!jobTargetId) return;
    setMatching(true);
    try {
      setJobMatch(await jobTargetApi.match(jobTargetId, resumeId));
    } catch (err) {
      alert(err instanceof Error ? err.message : 'JD 匹配失败');
    } finally {
      setMatching(false);
    }
  };

  /** 将 JD 优化稿带入「简历编辑」页签 */
  const handleApplyOptimization = () => {
    if (!jobMatch?.optimizedResumeText) return;
    const target = jobTargets.find((item) => item.id === jobTargetId);
    setExternalDraft({
      title: `${target?.title || 'JD'} 优化版`,
      content: jobMatch.optimizedResumeText,
    });
    handleTabChange('edit');
  };

  // 检查是否需要自动打开面试详情
  useEffect(() => {
    const viewInterview = (location.state as { viewInterview?: string })?.viewInterview;
    if (viewInterview && resume) {
      // 切换到面试标签页
      setActiveTab('interview');
      // 加载并显示面试详情
      const loadAndViewInterview = async () => {
        setLoadingInterview(true);
        try {
          const detail = await historyApi.getInterviewDetail(viewInterview);
          setSelectedInterview(detail);
          setDetailView('interviewDetail');
        } catch (err) {
          console.error('加载面试详情失败', err);
        } finally {
          setLoadingInterview(false);
        }
      };
      loadAndViewInterview();
    }
  }, [location.state, resume]);

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

  const tabOrder: TabType[] = ['analysis', 'edit', 'interview'];
  const handleTabChange = (tab: TabType) => {
    const newPage = tabOrder.indexOf(tab);
    const oldPage = tabOrder.indexOf(activeTab);
    setPage([newPage, newPage > oldPage ? 1 : -1]);
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
              className="w-12 h-12 border-4 border-slate-200 dark:border-slate-600 border-t-primary-500 rounded-full"
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
    { id: 'analysis' as const, label: '简历分析', icon: CheckSquare },
    { id: 'edit' as const, label: '简历编辑', icon: FilePenLine },
    { id: 'interview' as const, label: '面试记录', icon: MessageSquare, count: resume.interviews?.length || 0 },
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
            className="w-10 h-10 bg-white dark:bg-slate-800 rounded-xl flex items-center justify-center text-slate-500 hover:bg-slate-50 dark:hover:bg-slate-700 hover:text-slate-700 dark:hover:text-slate-300 transition-all shadow-sm"
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
          >
            <ChevronLeft className="w-5 h-5" />
          </motion.button>
          <div>
              <h2 className="text-xl font-bold text-slate-900 dark:text-white">
              {detailView === 'interviewDetail' ? `面试详情 #${selectedInterview?.sessionId?.slice(-6) || ''}` : resume.filename}
            </h2>
              <p className="text-sm text-slate-500 dark:text-slate-400 flex items-center gap-1.5">
              <Clock className="w-4 h-4" />
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
              className="px-5 py-2.5 border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 rounded-xl text-slate-600 dark:text-slate-300 font-medium hover:bg-slate-50 transition-all disabled:opacity-50 flex items-center gap-2"
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              <Download className="w-4 h-4" />
              {exporting === selectedInterview.sessionId ? '导出中...' : '导出 PDF'}
            </motion.button>
          )}
          {detailView !== 'interviewDetail' && (
            <>
              <button
                type="button"
                onClick={() => handleTabChange('edit')}
                className="ui-btn-ghost"
              >
                <FilePenLine className="h-4 w-4" aria-hidden />
                编辑简历
              </button>
              <motion.button
                type="button"
                onClick={() => onStartInterview(resume.resumeText, resumeId)}
                className="ui-btn-primary"
                whileHover={{ scale: 1.02, y: -1 }}
                whileTap={{ scale: 0.98 }}
              >
                <Mic className="h-4 w-4" aria-hidden />
                开始模拟面试
              </motion.button>
            </>
          )}
        </div>
      </div>

      {/* 岗位匹配：始终在详情主区可见（非面试详情时） */}
      {detailView !== 'interviewDetail' && activeTab === 'analysis' && (
        <section className="ui-panel mb-6 p-5 sm:p-6">
          <h3 className="flex items-center gap-2 font-semibold text-[var(--shell-text)]">
            <Target className="h-4 w-4 text-primary-500" aria-hidden />
            岗位 JD 匹配
          </h3>
          <div className="mt-3 flex flex-wrap gap-2">
            <select
              value={jobTargetId ?? ''}
              onChange={(e) => setJobTargetId(e.target.value ? Number(e.target.value) : undefined)}
              className="ui-input min-w-0 flex-1"
            >
              <option value="">选择岗位目标</option>
              {jobTargets.map((target) => (
                <option key={target.id} value={target.id}>{target.title}</option>
              ))}
            </select>
            <button
              type="button"
              disabled={!jobTargetId || matching}
              onClick={handleMatch}
              className="ui-btn-primary"
            >
              {matching ? '分析中…' : '开始匹配'}
            </button>
          </div>
          {!jobTargets.length && (
            <p className="mt-3 text-xs text-[var(--shell-muted)]">先在「目标岗位」中保存一份 JD。</p>
          )}
          {jobMatch && (
            <div className="mt-4 text-sm">
              <div className="font-semibold text-primary-600 dark:text-primary-400">匹配度 {jobMatch.score}/100</div>
              <p className="mt-1 text-[var(--shell-muted)]">{jobMatch.summary}</p>
              <p className="mt-2 text-emerald-600">匹配：{jobMatch.matchedSkills.join('、') || '暂无'}</p>
              <p className="mt-1 text-amber-600">待补齐：{jobMatch.missingSkills.join('、') || '暂无'}</p>
              {jobMatch.optimizationSuggestions.length > 0 && (
                <div className="mt-3 space-y-2">
                  {jobMatch.optimizationSuggestions.map((item, index) => (
                    <div key={`${item.section}-${index}`} className="rounded-lg bg-[var(--shell-hover)] p-3 text-xs">
                      <b>{item.section}</b>
                      <span className="ml-2 text-[var(--shell-muted)]">{item.issue}</span>
                      <p className="mt-1 text-[var(--shell-text)]">{item.recommendation}</p>
                      {item.proposedText && (
                        <p className="mt-1 whitespace-pre-wrap text-primary-600">建议文案：{item.proposedText}</p>
                      )}
                    </div>
                  ))}
                </div>
              )}
              <div className="mt-3 flex flex-wrap gap-2">
                {jobMatch.optimizedResumeText && (
                  <button type="button" onClick={handleApplyOptimization} className="ui-btn-primary">
                    应用优化稿并编辑
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => onStartInterview(resume.resumeText, resumeId, jobTargetId)}
                  className="ui-btn-ghost"
                >
                  <Mic className="h-3.5 w-3.5" aria-hidden />
                  用该岗位开始模拟面试
                </button>
              </div>
              <p className="mt-2 text-xs text-[var(--shell-muted)]">
                应用优化稿后请在「简历编辑」中确认并保存为新版本。
              </p>
            </div>
          )}
        </section>
      )}

      {/* 标签页切换 - 仅在非面试详情时显示 */}
      {detailView !== 'interviewDetail' && (
          <div className="bg-white dark:bg-slate-800 rounded-2xl p-2 mb-6 inline-flex gap-1">
          {tabs.map((tab) => (
            <motion.button
              key={tab.id}
              onClick={() => handleTabChange(tab.id)}
              className={`relative px-6 py-3 rounded-xl font-medium flex items-center gap-2 transition-colors
                ${activeTab === tab.id ? 'text-primary-600 dark:text-primary-400' : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200'}`}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              {activeTab === tab.id && (
                <motion.div
                  layoutId="activeTab"
                  className="absolute inset-0 bg-primary-50 dark:bg-primary-900 rounded-xl"
                  transition={{ type: "spring", bounce: 0.2, duration: 0.6 }}
                />
              )}
              <span className="relative z-10 flex items-center gap-2">
                <tab.icon className="w-5 h-5" />
                {tab.label}
                {tab.count !== undefined && tab.count > 0 && (
                    <span
                        className="px-2 py-0.5 bg-primary-100 dark:bg-primary-900 text-primary-600 dark:text-primary-400 text-xs rounded-full">{tab.count}</span>
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
              {activeTab === 'analysis' && (
                <AnalysisPanel
                  analysis={latestAnalysis}
                  analyzeStatus={resume.analyzeStatus}
                  analyzeError={resume.analyzeError}
                  onExport={handleExportAnalysisPdf}
                  exporting={exporting === 'analysis'}
                  onReanalyze={handleReanalyze}
                  reanalyzing={reanalyzing}
                />
              )}
              {activeTab === 'edit' && (
                <ResumeEditorPanel
                  resumeId={resumeId}
                  resumeText={resume.resumeText || ''}
                  onContentUpdated={loadResumeDetail}
                  externalDraft={externalDraft}
                  onExternalDraftConsumed={() => setExternalDraft(null)}
                />
              )}
              {activeTab === 'interview' && (
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
