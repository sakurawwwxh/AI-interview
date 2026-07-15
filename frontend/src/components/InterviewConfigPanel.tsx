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
      className="mx-auto max-w-2xl"
      initial={{ y: 12 }}
      animate={{ y: 0 }}
      transition={{ duration: 0.25 }}
    >
      <div className="ui-panel-signal p-6 sm:p-8">
        <div className="mb-6 flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-teal-500 to-teal-800 text-white shadow-md shadow-teal-700/20">
            <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" aria-hidden>
              <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2" />
              <circle cx="12" cy="12" r="6" stroke="currentColor" strokeWidth="2" />
              <circle cx="12" cy="12" r="2" fill="currentColor" />
            </svg>
          </div>
          <div>
            <p className="page-kicker text-primary-700 dark:text-primary-400">开场配置</p>
            <h2 className="font-display text-xl font-semibold tracking-tight text-[var(--shell-text)] sm:text-2xl">
              面试配置
            </h2>
          </div>
        </div>

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
            <label className="mb-3 block text-sm font-semibold text-[var(--shell-text)]">题目数量</label>
            <div className="grid grid-cols-5 gap-2 sm:gap-3">
              {questionCounts.map((count) => (
                <button
                  key={count}
                  type="button"
                  onClick={() => onQuestionCountChange(count)}
                  className={`min-h-11 rounded-xl font-medium tabular-nums transition ${
                    questionCount === count
                      ? 'bg-primary-600 text-white shadow-lg shadow-primary-600/20'
                      : 'bg-[var(--shell-hover)] text-[var(--shell-muted)] hover:bg-[var(--shell-active)] hover:text-[var(--shell-text)]'
                  }`}
                >
                  {count}
                </button>
              ))}
            </div>
          </div>

          <div>
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <label className="block text-sm font-semibold text-[var(--shell-text)]">面试模板</label>
              <button
                type="button"
                disabled={!canRecommend || !onRecommendTemplate}
                onClick={onRecommendTemplate}
                className="inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-primary-200 bg-primary-50 px-3 py-1.5 text-xs font-medium text-primary-700 transition hover:bg-primary-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-primary-800 dark:bg-primary-900/30 dark:text-primary-300 dark:hover:bg-primary-900/50"
                title={canRecommend ? '根据所选岗位 JD 推荐最匹配的模板' : '请先选择目标岗位'}
              >
                <Sparkles className="h-3.5 w-3.5" aria-hidden />
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
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => onTemplateChange(item)}
                    className={`rounded-xl border p-4 text-left transition ${
                      template.id === item.id
                        ? 'border-primary-500 bg-primary-50 shadow-sm dark:bg-primary-900/30'
                        : 'border-[var(--shell-border)] bg-[var(--shell-hover)]/50 hover:border-primary-300'
                    }`}
                  >
                    <span className="flex items-center gap-2">
                      <span className="block font-semibold text-[var(--shell-text)]">{item.name}</span>
                      {isRecommended && (
                        <span className="rounded-full bg-primary-500/15 px-1.5 py-0.5 text-[10px] font-medium text-primary-600 dark:text-primary-300">
                          推荐
                        </span>
                      )}
                    </span>
                    <span className="mt-1 block text-xs text-[var(--shell-muted)]">
                      基础 {item.difficultyDistribution.basic}% · 进阶 {item.difficultyDistribution.advanced}% · 专家{' '}
                      {item.difficultyDistribution.expert}%
                    </span>
                    {preview && preview.score > 0 && (
                      <span className="mt-1 block text-[11px] text-[var(--shell-subtle)]">
                        匹配分 {Math.round(preview.score)}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
            <p className="mt-3 text-sm text-[var(--shell-muted)]">
              当前模板：{template.questionTypes.map((item) => `${item.type} ${item.weight}%`).join(' · ')}
              ；每个主问题追问 {template.followUpCount} 次。
            </p>
            {templateRecommendReason && (
              <p className="mt-2 flex items-start gap-1.5 text-xs text-primary-600 dark:text-primary-300">
                <Sparkles className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
                {templateRecommendReason}
              </p>
            )}
          </div>

          <div>
            <label className="mb-3 block text-sm font-semibold text-[var(--shell-text)]">目标岗位（可选）</label>
            <select
              value={jobTargetId ?? ''}
              onChange={(event) => onJobTargetChange(event.target.value ? Number(event.target.value) : undefined)}
              className="ui-input"
            >
              <option value="">综合面试（不指定 JD）</option>
              {jobTargets.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.title}
                  {item.company ? ` · ${item.company}` : ''}
                  {item.active ? '（当前）' : ''}
                </option>
              ))}
            </select>
            {!jobTargets.length && (
              <p className="mt-2 text-xs text-[var(--shell-muted)]">可在「目标岗位」中粘贴 JD，生成更有针对性的题目。</p>
            )}
          </div>

          <div>
            <label className="mb-3 block text-sm font-semibold text-[var(--shell-muted)]">简历预览（前 500 字）</label>
            <textarea
              value={resumeText.substring(0, 500) + (resumeText.length > 500 ? '…' : '')}
              readOnly
              className="h-32 w-full resize-none rounded-xl border border-[var(--shell-border)] bg-[var(--shell-hover)]/40 p-4 text-sm text-[var(--shell-muted)]"
            />
          </div>

          <AnimatePresence>
            {error && (
              <motion.div
                initial={{ y: -6 }}
                animate={{ y: 0 }}
                exit={{ y: -6 }}
                role="alert"
                className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-300"
              >
                {error}
              </motion.div>
            )}
          </AnimatePresence>

          <div className="flex flex-wrap justify-center gap-3 pt-2">
            <button type="button" onClick={onBack} className="ui-btn-ghost min-w-[7rem]">
              返回
            </button>
            <button
              type="button"
              onClick={onStart}
              disabled={isCreating}
              aria-busy={isCreating}
              className="ui-btn-primary min-w-[10rem]"
            >
              {isCreating ? (
                <>
                  <span
                    className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white"
                    aria-hidden
                  />
                  正在生成题目…
                </>
              ) : (
                '开始面试'
              )}
            </button>
          </div>
        </div>
      </div>
    </motion.div>
  );
}
