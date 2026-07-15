import {useCallback, useEffect, useState} from 'react';
import {useLocation} from 'react-router-dom';
import {AnimatePresence, motion} from 'framer-motion';
import {historyApi, InterviewDetail, ResumeDetail, ResumeVersion} from '../api/history';
import {jobTargetApi, JobMatch, JobTarget} from '../api/jobTarget';
import AnalysisPanel from '../components/AnalysisPanel';
import InterviewPanel from '../components/InterviewPanel';
import InterviewDetailPanel from '../components/InterviewDetailPanel';
import {formatDateOnly} from '../utils/date';
import {CheckSquare, ChevronLeft, Clock, Download, FilePenLine, MessageSquare, Mic, Target} from 'lucide-react';

interface ResumeDetailPageProps {
  resumeId: number;
  onBack: () => void;
  /** 可选 jobTargetId：从 JD 匹配结果一键开面时带入 */
  onStartInterview: (resumeText: string, resumeId: number, jobTargetId?: number) => void;
}

type TabType = 'analysis' | 'interview';
type DetailViewType = 'list' | 'interviewDetail';

export default function ResumeDetailPage({ resumeId, onBack, onStartInterview }: ResumeDetailPageProps) {
  const location = useLocation();
  const [resume, setResume] = useState<ResumeDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<TabType>('analysis');
  const [exporting, setExporting] = useState<string | null>(null);
  const [[page, direction], setPage] = useState([0, 0]);
  const [detailView, setDetailView] = useState<DetailViewType>('list');
  const [selectedInterview, setSelectedInterview] = useState<InterviewDetail | null>(null);
  const [loadingInterview, setLoadingInterview] = useState(false);
  const [reanalyzing, setReanalyzing] = useState(false);
  const [editing, setEditing] = useState(false);
  const [draftTitle, setDraftTitle] = useState('手动优化');
  const [draftContent, setDraftContent] = useState('');
  const [versions, setVersions] = useState<ResumeVersion[]>([]);
  const [savingContent, setSavingContent] = useState(false);
  const [jobTargets, setJobTargets] = useState<JobTarget[]>([]);
  const [jobTargetId, setJobTargetId] = useState<number | undefined>();
  const [jobMatch, setJobMatch] = useState<JobMatch | null>(null);
  const [matching, setMatching] = useState(false);

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
    historyApi.getVersions(resumeId).then(setVersions).catch(() => {});
    jobTargetApi.list().then(items => { setJobTargets(items); setJobTargetId(items.find(item => item.active)?.id); }).catch(() => {});
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

  const handleSaveContent = async () => {
    if (!draftContent.trim()) return;
    setSavingContent(true);
    try {
      await historyApi.updateContent(resumeId, draftTitle || '手动优化', draftContent);
      setEditing(false);
      await Promise.all([loadResumeDetail(), historyApi.getVersions(resumeId).then(setVersions)]);
    } catch (err) { alert(err instanceof Error ? err.message : '保存简历版本失败'); } finally { setSavingContent(false); }
  };

  const handleMatch = async () => {
    if (!jobTargetId) return;
    setMatching(true);
    try { setJobMatch(await jobTargetApi.match(jobTargetId, resumeId)); } catch (err) { alert(err instanceof Error ? err.message : 'JD 匹配失败'); } finally { setMatching(false); }
  };

  const handleApplyOptimization = () => {
    if (!jobMatch?.optimizedResumeText) return;
    const target = jobTargets.find(item => item.id === jobTargetId);
    setDraftTitle(`${target?.title || 'JD'} 优化版`);
    setDraftContent(jobMatch.optimizedResumeText);
    setEditing(true);
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
            <motion.button
              onClick={() => onStartInterview(resume.resumeText, resumeId)}
              className="px-5 py-2.5 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-medium shadow-lg shadow-primary-500/25 hover:shadow-xl transition-all flex items-center gap-2"
              whileHover={{ scale: 1.02, y: -1 }}
              whileTap={{ scale: 0.98 }}
            >
              <Mic className="w-4 h-4" />
              开始模拟面试
            </motion.button>
          )}
        </div>
      </div>

      {detailView !== 'interviewDetail' && (
        <section className="mb-6 grid gap-4 lg:grid-cols-2">
          <div className="rounded-2xl border border-slate-100 dark:border-slate-700 bg-white dark:bg-slate-800 p-5">
            <div className="flex items-center justify-between gap-3"><div><h3 className="font-semibold text-slate-900 dark:text-white flex gap-2 items-center"><FilePenLine className="w-4 h-4 text-primary-500" />简历编辑与版本</h3><p className="mt-1 text-xs text-slate-500">保存时会新建版本，原始上传内容始终保留。</p></div><button onClick={() => { setDraftContent(resume.resumeText); setEditing(!editing); }} className="text-sm text-primary-600">{editing ? '收起编辑' : '编辑简历'}</button></div>
            {editing && <div className="mt-4 space-y-3"><input value={draftTitle} maxLength={120} onChange={e => setDraftTitle(e.target.value)} className="w-full px-3 py-2 border rounded-lg dark:bg-slate-900 dark:border-slate-600" placeholder="版本名称" /><textarea value={draftContent} onChange={e => setDraftContent(e.target.value)} className="w-full min-h-56 p-3 border rounded-lg dark:bg-slate-900 dark:border-slate-600" /><button disabled={savingContent} onClick={handleSaveContent} className="px-4 py-2 rounded-lg bg-primary-500 text-white disabled:opacity-60">{savingContent ? '保存中…' : '保存为新版本'}</button></div>}
            <div className="mt-4 space-y-2 max-h-32 overflow-auto">{versions.map(version => <div key={version.id ?? 'original'} className="text-xs flex justify-between text-slate-500"><span>v{version.versionNumber} · {version.title}{version.current ? '（当前）' : ''}</span>{version.id && !version.current && <button className="text-primary-600" onClick={async () => { await historyApi.restoreVersion(resumeId, version.id!); await Promise.all([loadResumeDetail(), historyApi.getVersions(resumeId).then(setVersions)]); }}>恢复</button>}</div>)}</div>
          </div>
          <div className="rounded-2xl border border-slate-100 dark:border-slate-700 bg-white dark:bg-slate-800 p-5">
            <h3 className="font-semibold text-slate-900 dark:text-white flex gap-2 items-center"><Target className="w-4 h-4 text-primary-500" />岗位 JD 匹配</h3><div className="mt-3 flex gap-2"><select value={jobTargetId ?? ''} onChange={e => setJobTargetId(e.target.value ? Number(e.target.value) : undefined)} className="flex-1 min-w-0 px-3 py-2 border rounded-lg dark:bg-slate-900 dark:border-slate-600"><option value="">选择岗位目标</option>{jobTargets.map(target => <option key={target.id} value={target.id}>{target.title}</option>)}</select><button disabled={!jobTargetId || matching} onClick={handleMatch} className="px-3 py-2 rounded-lg bg-primary-500 text-white disabled:opacity-60">{matching ? '分析中…' : '开始匹配'}</button></div>
            {!jobTargets.length && <p className="mt-3 text-xs text-slate-500">先在“目标岗位”中保存一份 JD。</p>}
            {jobMatch && (
              <div className="mt-4 text-sm">
                <div className="font-semibold text-primary-600">匹配度 {jobMatch.score}/100</div>
                <p className="mt-1 text-slate-600 dark:text-slate-300">{jobMatch.summary}</p>
                <p className="mt-2 text-emerald-600">匹配：{jobMatch.matchedSkills.join('、') || '暂无'}</p>
                <p className="mt-1 text-amber-600">待补齐：{jobMatch.missingSkills.join('、') || '暂无'}</p>
                {jobMatch.optimizationSuggestions.length > 0 && (
                  <div className="mt-3 space-y-2">
                    {jobMatch.optimizationSuggestions.map((item, index) => (
                      <div key={`${item.section}-${index}`} className="rounded-lg bg-slate-50 p-3 text-xs dark:bg-slate-900">
                        <b>{item.section}</b>
                        <span className="ml-2 text-slate-500">{item.issue}</span>
                        <p className="mt-1 text-slate-600 dark:text-slate-300">{item.recommendation}</p>
                        {item.proposedText && (
                          <p className="mt-1 whitespace-pre-wrap text-primary-600">建议文案：{item.proposedText}</p>
                        )}
                      </div>
                    ))}
                  </div>
                )}
                <div className="mt-3 flex flex-wrap gap-2">
                  <button
                    onClick={handleApplyOptimization}
                    className="rounded-lg bg-primary-500 px-3 py-2 text-sm font-medium text-white"
                  >
                    应用优化稿到编辑器
                  </button>
                  <button
                    type="button"
                    onClick={() => onStartInterview(resume.resumeText, resumeId, jobTargetId)}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-primary-300 bg-primary-50 px-3 py-2 text-sm font-medium text-primary-700 transition hover:bg-primary-100 dark:border-primary-700 dark:bg-primary-900/30 dark:text-primary-300"
                  >
                    <Mic className="h-3.5 w-3.5" />
                    用该岗位开始模拟面试
                  </button>
                </div>
                <p className="mt-1 text-xs text-slate-500">
                  应用优化稿后仍需保存才会新建版本；开面将带上当前岗位并自动推荐模板。
                </p>
              </div>
            )}
          </div>
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
              {activeTab === 'analysis' ? (
                <AnalysisPanel
                  analysis={latestAnalysis}
                  analyzeStatus={resume.analyzeStatus}
                  analyzeError={resume.analyzeError}
                  onExport={handleExportAnalysisPdf}
                  exporting={exporting === 'analysis'}
                  onReanalyze={handleReanalyze}
                  reanalyzing={reanalyzing}
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
