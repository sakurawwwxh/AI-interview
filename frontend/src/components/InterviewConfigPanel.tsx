import {AnimatePresence, motion} from 'framer-motion';
import {Sparkles} from 'lucide-react';
import type {InterviewSession, InterviewTemplateConfig} from '../types/interview';
import {interviewTemplates} from '../constants/interviewTemplates';
import type {JobTarget} from '../api/jobTarget';
import {scoreTemplateAgainstJd} from '../utils/templateRecommend';

interface InterviewConfigPanelProps {
  questionCount: number;
  onQuestionCountChange: (count: number) => void;
  template: InterviewTemplateConfig;
  onTemplateChange: (template: InterviewTemplateConfig) => void;
  onStart: () => void;
  isCreating: boolean;
  checkingUnfinished: boolean;
  unfinishedSession: InterviewSession | null;
  onContinueUnfinished: () => void;
  onStartNew: () => void;
  resumeText: string;
  onBack: () => void;
  error?: string;
  jobTargets: JobTarget[];
  jobTargetId?: number;
  onJobTargetChange: (id?: number) => void;
  /** 当前岗位推荐结果说明（可选） */
  templateRecommendReason?: string | null;
  /** 一键按 JD 推荐模板 */
  onRecommendTemplate?: () => void;
}

/**
 * 面试配置面板组件
 */
export default function InterviewConfigPanel({
  questionCount,
  onQuestionCountChange,
  template,
  onTemplateChange,
  onStart,
  isCreating,
  checkingUnfinished,
  unfinishedSession,
  onContinueUnfinished,
  onStartNew,
  resumeText,
  onBack,
  error,
  jobTargets,
  jobTargetId,
  onJobTargetChange,
  templateRecommendReason,
  onRecommendTemplate,
}: InterviewConfigPanelProps) {
  const questionCounts = [6, 8, 10, 12, 15];
  const selectedJob = jobTargets.find((j) => j.id === jobTargetId);
  const canRecommend = Boolean(selectedJob?.jobDescription?.trim());

  return (
      <motion.div
      className="max-w-2xl mx-auto"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
    >
          <div
              className="bg-white dark:bg-slate-800 rounded-2xl p-8 shadow-sm dark:shadow-slate-900/50 border border-slate-100 dark:border-slate-700">
              <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-6 flex items-center gap-3">
                  <div
                      className="w-10 h-10 bg-primary-100 dark:bg-primary-900/50 rounded-xl flex items-center justify-center">
                      <svg className="w-5 h-5 text-primary-600 dark:text-primary-400" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
              <circle cx="12" cy="12" r="6" stroke="currentColor" strokeWidth="2"/>
              <circle cx="12" cy="12" r="2" fill="currentColor"/>
            </svg>
          </div>
          面试配置
        </h2>

        {/* 未完成面试提示 */}
        <AnimatePresence>
          {checkingUnfinished && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="mb-6 p-4 bg-primary-50 dark:bg-primary-900/30 border border-primary-200 dark:border-primary-800 rounded-xl text-primary-700 dark:text-primary-400 text-sm text-center"
            >
              <div className="flex items-center justify-center gap-2">
                  <motion.div
                  className="w-4 h-4 border-2 border-primary-500 border-t-transparent rounded-full"
                  animate={{ rotate: 360 }}
                  transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
                />
                正在检查是否有未完成的面试...
              </div>
            </motion.div>
          )}

          {unfinishedSession && !checkingUnfinished && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="mb-6 p-5 bg-gradient-to-r from-amber-50 to-orange-50 dark:from-amber-900/30 dark:to-orange-900/30 border-2 border-amber-200 dark:border-amber-800 rounded-xl"
            >
              <div className="flex items-start gap-3 mb-4">
                  <div
                      className="w-8 h-8 bg-amber-100 dark:bg-amber-900/50 rounded-lg flex items-center justify-center flex-shrink-0">
                      <svg className="w-5 h-5 text-amber-600 dark:text-amber-400" viewBox="0 0 24 24" fill="none">
                    <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M2 17L12 22L22 17" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M2 12L12 17L22 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </div>
                <div className="flex-1">
                    <h3 className="font-semibold text-amber-900 dark:text-amber-300 mb-1">检测到未完成的模拟面试</h3>
                    <p className="text-sm text-amber-700 dark:text-amber-400">
                    已完成 {unfinishedSession.currentQuestionIndex} / {unfinishedSession.totalQuestions} 题
                  </p>
                </div>
              </div>
              <div className="flex gap-3">
                <motion.button
                  onClick={onContinueUnfinished}
                  className="flex-1 px-4 py-2.5 bg-amber-500 text-white rounded-lg font-medium hover:bg-amber-600 transition-colors"
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                >
                  继续完成
                </motion.button>
                <motion.button
                  onClick={onStartNew}
                  className="flex-1 px-4 py-2.5 bg-white dark:bg-slate-700 border border-amber-300 dark:border-amber-700 text-amber-700 dark:text-amber-400 rounded-lg font-medium hover:bg-amber-50 dark:hover:bg-amber-900/30 transition-colors"
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                >
                  开始新的
                </motion.button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        <div className="space-y-6">
          <div>
              <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-3">
              题目数量
            </label>
            <div className="grid grid-cols-5 gap-3">
              {questionCounts.map((count) => (
                <motion.button
                  key={count}
                  onClick={() => onQuestionCountChange(count)}
                  className={`px-4 py-3 rounded-xl font-medium transition-all ${
                    questionCount === count
                      ? 'bg-primary-500 text-white shadow-lg shadow-primary-500/25'
                        : 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-600'
                  }`}
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                >
                  {count}
                </motion.button>
              ))}
            </div>
          </div>

          <div>
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300">面试模板</label>
              <button
                type="button"
                disabled={!canRecommend || !onRecommendTemplate}
                onClick={onRecommendTemplate}
                className="inline-flex items-center gap-1.5 rounded-lg border border-primary-200 bg-primary-50 px-3 py-1.5 text-xs font-medium text-primary-700 transition hover:bg-primary-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-primary-800 dark:bg-primary-900/30 dark:text-primary-300 dark:hover:bg-primary-900/50"
                title={canRecommend ? '根据所选岗位 JD 推荐最匹配的模板' : '请先选择目标岗位'}
              >
                <Sparkles className="h-3.5 w-3.5" />
                按岗位推荐模板
              </button>
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              {interviewTemplates.map((item) => {
                const preview = selectedJob?.jobDescription
                  ? scoreTemplateAgainstJd(item, selectedJob.jobDescription)
                  : null;
                const isRecommended = Boolean(templateRecommendReason && template.id === item.id);
                return (
                <motion.button
                  key={item.id}
                  type="button"
                  onClick={() => onTemplateChange(item)}
                  className={`rounded-xl border p-4 text-left transition-all ${
                    template.id === item.id
                      ? 'border-primary-500 bg-primary-50 shadow-sm dark:bg-primary-900/30'
                      : 'border-slate-200 bg-slate-50 hover:border-primary-300 dark:border-slate-600 dark:bg-slate-700/50'
                  }`}
                  whileHover={{ y: -1 }}
                >
                  <span className="flex items-center gap-2">
                    <span className="block font-semibold text-slate-800 dark:text-white">{item.name}</span>
                    {isRecommended && (
                      <span className="rounded-full bg-primary-500/15 px-1.5 py-0.5 text-[10px] font-medium text-primary-600 dark:text-primary-300">
                        推荐
                      </span>
                    )}
                  </span>
                  <span className="mt-1 block text-xs text-slate-500 dark:text-slate-400">
                    基础 {item.difficultyDistribution.basic}% · 进阶 {item.difficultyDistribution.advanced}% · 专家 {item.difficultyDistribution.expert}%
                  </span>
                  {preview && preview.score > 0 && (
                    <span className="mt-1 block text-[11px] text-slate-400">匹配分 {Math.round(preview.score)}</span>
                  )}
                </motion.button>
                );
              })}
            </div>
            <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
              当前模板：{template.questionTypes.map(item => `${item.type} ${item.weight}%`).join(' · ')}；每个主问题追问 {template.followUpCount} 次。
            </p>
            {templateRecommendReason && (
              <p className="mt-2 flex items-start gap-1.5 text-xs text-primary-600 dark:text-primary-300">
                <Sparkles className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                {templateRecommendReason}
              </p>
            )}
          </div>

          <div>
            <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-3">目标岗位（可选）</label>
            <select value={jobTargetId ?? ''} onChange={event => onJobTargetChange(event.target.value ? Number(event.target.value) : undefined)} className="w-full px-4 py-3 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-200">
              <option value="">综合面试（不指定 JD）</option>
              {jobTargets.map(item => <option key={item.id} value={item.id}>{item.title}{item.company ? ` · ${item.company}` : ''}{item.active ? '（当前）' : ''}</option>)}
            </select>
            {!jobTargets.length && <p className="mt-2 text-xs text-slate-500">可在“目标岗位”中粘贴 JD，生成更有针对性的题目。</p>}
            {canRecommend && (
              <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
                已选岗位后，可用上方「按岗位推荐模板」自动匹配，或切换岗位时自动推荐一次。
              </p>
            )}
          </div>

          <div className="mb-6">
              <label
                  className="block text-sm font-semibold text-slate-600 dark:text-slate-400 mb-3">简历预览（前500字）</label>
              <textarea
              value={resumeText.substring(0, 500) + (resumeText.length > 500 ? '...' : '')}
              readOnly
              className="w-full h-32 p-4 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-600 rounded-xl text-slate-600 dark:text-slate-400 text-sm resize-none"
            />
          </div>

          <AnimatePresence>
            {error && (
              <motion.div
                initial={{ opacity: 0, y: -10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                className="mb-6 p-4 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-800 rounded-xl text-red-600 dark:text-red-400 text-sm"
              >
                ⚠️ {error}
              </motion.div>
            )}
          </AnimatePresence>

            <div className="flex justify-center gap-4">
                <motion.button
              onClick={onBack}
              className="px-6 py-3 border border-slate-200 dark:border-slate-600 rounded-xl text-slate-600 dark:text-slate-300 font-medium hover:bg-slate-50 dark:hover:bg-slate-700 transition-all"
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              ← 返回
            </motion.button>
            <motion.button
              onClick={onStart}
              disabled={isCreating}
              className="px-8 py-3 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-semibold shadow-lg shadow-primary-500/25 hover:shadow-xl transition-all disabled:opacity-60 disabled:cursor-not-allowed flex items-center gap-2"
              whileHover={{ scale: isCreating ? 1 : 1.02, y: isCreating ? 0 : -1 }}
              whileTap={{ scale: isCreating ? 1 : 0.98 }}
            >
              {isCreating ? (
                <>
                    <motion.span
                    className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full"
                    animate={{ rotate: 360 }}
                    transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
                  />
                  正在生成题目...
                </>
              ) : (
                <>
                  开始面试 →
                </>
              )}
            </motion.button>
          </div>
        </div>
      </div>
    </motion.div>
  );
}
