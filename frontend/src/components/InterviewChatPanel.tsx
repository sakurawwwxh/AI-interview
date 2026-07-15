import {useMemo, useRef} from 'react';
import {motion} from 'framer-motion';
import {Virtuoso, type VirtuosoHandle} from 'react-virtuoso';
import type {InterviewQuestion, InterviewSession} from '../types/interview';
import {Clock3, Send, User} from 'lucide-react';

interface Message {
  type: 'interviewer' | 'user';
  content: string;
  category?: string;
  questionIndex?: number;
  isFollowUp?: boolean;
}

interface InterviewChatPanelProps {
  session: InterviewSession;
  currentQuestion: InterviewQuestion | null;
  messages: Message[];
  answer: string;
  onAnswerChange: (answer: string) => void;
  onSubmit: () => void;
  elapsedSeconds: number;
  onCompleteEarly: () => void;
  isSubmitting: boolean;
  showCompleteConfirm: boolean;
  onShowCompleteConfirm: (show: boolean) => void;
}

/**
 * 面试聊天面板组件
 */
export default function InterviewChatPanel({
  session,
  currentQuestion,
  messages,
  answer,
  onAnswerChange,
  onSubmit,
  elapsedSeconds,
  // onCompleteEarly, // 暂时未使用
  isSubmitting,
  // showCompleteConfirm, // 暂时未使用
  onShowCompleteConfirm
}: InterviewChatPanelProps) {
  const virtuosoRef = useRef<VirtuosoHandle>(null);

  const progress = useMemo(() => {
    if (!session || !currentQuestion) return 0;
    // 追问的 questionIndex 可能 >= totalQuestions，进度按已答主问题数计算并封顶 100%
    const answeredCount = currentQuestion.isFollowUp
      ? currentQuestion.parentQuestionIndex != null
        ? currentQuestion.parentQuestionIndex + 1
        : session.currentQuestionIndex
      : currentQuestion.questionIndex + 1;
    return Math.min(100, (answeredCount / session.totalQuestions) * 100);
  }, [session, currentQuestion]);

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
      onSubmit();
    }
  };

  const formattedElapsed = `${String(Math.floor(elapsedSeconds / 60)).padStart(2, '0')}:${String(elapsedSeconds % 60).padStart(2, '0')}`;

  return (
    <div className="mx-auto flex h-[calc(100vh-200px)] max-w-4xl flex-col">
      {/* 进度卷首条 */}
      <div className="ui-panel mb-4 p-5 sm:p-6">
        <div className="mb-3 flex items-center justify-between gap-3">
          <div>
            <p className="page-kicker text-primary-700 dark:text-primary-400">
              {currentQuestion?.isFollowUp ? '追问' : '主问题'}
            </p>
            <p className="mt-1 text-sm font-semibold text-[var(--shell-text)]">
              {currentQuestion?.isFollowUp
                ? '请针对上题补充细节'
                : `第 ${currentQuestion ? currentQuestion.questionIndex + 1 : 0} / ${session.totalQuestions} 题`}
            </p>
          </div>
          <div className="flex items-center gap-3 text-sm tabular-nums text-[var(--shell-muted)]">
            <span className="inline-flex min-h-9 items-center gap-1.5 rounded-full border border-[var(--shell-border)] px-3">
              <Clock3 className="h-4 w-4" aria-hidden />
              {formattedElapsed}
            </span>
            <span className="font-semibold text-primary-600 dark:text-primary-400">{Math.round(progress)}%</span>
          </div>
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-[var(--shell-hover)]" role="progressbar" aria-valuenow={Math.round(progress)} aria-valuemin={0} aria-valuemax={100}>
          <motion.div
            className="h-full rounded-full bg-gradient-to-r from-teal-600 via-teal-500 to-amber-400"
            initial={false}
            animate={{ width: `${progress}%` }}
            transition={{ duration: 0.28 }}
          />
        </div>
      </div>

      {/* 对话区 */}
      <div className="ui-panel flex min-h-0 flex-1 flex-col overflow-hidden">
        <Virtuoso
          ref={virtuosoRef}
          data={messages}
          initialTopMostItemIndex={messages.length - 1}
          followOutput="smooth"
          className="flex-1"
          itemContent={(_index, msg) => (
            <div className="px-5 pb-4 first:pt-6 sm:px-6">
              <MessageBubble message={msg} />
            </div>
          )}
        />

        <div className="border-t border-[var(--shell-border)] bg-[var(--shell-hover)]/40 p-4">
          <div className="flex flex-col gap-3 sm:flex-row">
            <textarea
              value={answer}
              onChange={(e) => onAnswerChange(e.target.value)}
              onKeyDown={handleKeyPress}
              placeholder="输入你的回答…（Ctrl / ⌘ + Enter 提交）"
              className="ui-input min-h-[5.5rem] flex-1 resize-none"
              rows={3}
              disabled={isSubmitting}
              aria-label="面试回答"
            />
            <div className="flex shrink-0 flex-row gap-2 sm:flex-col">
              <button
                type="button"
                onClick={onSubmit}
                disabled={!answer.trim() || isSubmitting}
                aria-busy={isSubmitting}
                className="ui-btn-primary flex-1 sm:min-w-[7.5rem]"
              >
                {isSubmitting ? (
                  <>
                    <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" aria-hidden />
                    提交中
                  </>
                ) : (
                  <>
                    <Send className="h-4 w-4" aria-hidden />
                    提交
                  </>
                )}
              </button>
              <button
                type="button"
                onClick={() => onShowCompleteConfirm(true)}
                disabled={isSubmitting}
                className="ui-btn-ghost flex-1 sm:min-w-[7.5rem]"
              >
                提前交卷
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// 消息气泡组件
function MessageBubble({ message }: { message: Message }) {
  if (message.type === 'interviewer') {
    return (
      <div className="flex items-start gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-teal-500/15 text-teal-700 dark:text-teal-300">
          <User className="h-4 w-4" aria-hidden />
        </div>
        <div className="min-w-0 flex-1">
          <div className="mb-1.5 flex flex-wrap items-center gap-2">
            <span className="text-sm font-semibold text-[var(--shell-text)]">面试官</span>
            {message.isFollowUp && (
              <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700 dark:bg-amber-900/40 dark:text-amber-300">
                追问
              </span>
            )}
            {message.category && (
              <span className="rounded-full bg-primary-50 px-2 py-0.5 text-xs text-primary-700 dark:bg-primary-900/30 dark:text-primary-300">
                {message.category}
              </span>
            )}
          </div>
          {/* 题干用衬线，像卷面问题 */}
          <div className="rounded-2xl rounded-tl-md border border-[var(--shell-border)] bg-[var(--shell-hover)]/50 p-4 leading-relaxed text-[var(--shell-text)]">
            <p className="font-display text-[15px] font-medium tracking-tight sm:text-base">{message.content}</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-start justify-end gap-3">
      <div className="max-w-[80%] flex-1">
        <div className="rounded-2xl rounded-tr-md bg-gradient-to-br from-teal-600 to-teal-800 p-4 leading-relaxed text-white shadow-md shadow-teal-800/15">
          {message.content}
        </div>
      </div>
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[var(--shell-hover)] text-[var(--shell-muted)]">
        <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" aria-hidden>
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          <circle cx="12" cy="7" r="4" stroke="currentColor" strokeWidth="2" />
        </svg>
      </div>
    </div>
  );
}
