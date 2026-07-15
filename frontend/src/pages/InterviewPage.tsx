import {useEffect, useRef, useState} from 'react';
import {AnimatePresence, motion} from 'framer-motion';
import {AlertTriangle, CheckCircle2, Loader2} from 'lucide-react';
import {interviewApi} from '../api/interview';
import ConfirmDialog from '../components/ConfirmDialog';
import InterviewConfigPanel from '../components/InterviewConfigPanel';
import InterviewChatPanel from '../components/InterviewChatPanel';
import type {InterviewQuestion, InterviewSession} from '../types/interview';
import type {InterviewTemplateConfig} from '../types/interview';
import {interviewTemplates} from '../constants/interviewTemplates';
import {jobTargetApi, type JobTarget} from '../api/jobTarget';
import {recommendTemplate} from '../utils/templateRecommend';

type InterviewStage = 'config' | 'interview' | 'submitted';

interface Message {
  type: 'interviewer' | 'user';
  content: string;
  category?: string;
  questionIndex?: number;
  isFollowUp?: boolean;
}

interface InterviewProps {
  resumeText: string;
  resumeId?: number;
  onBack: () => void;
  /** 交卷完成回调，可携带 sessionId 便于记录页高亮 */
  onInterviewComplete: (sessionId?: string) => void;
}

export default function Interview({ resumeText, resumeId, onBack, onInterviewComplete }: InterviewProps) {
  const [stage, setStage] = useState<InterviewStage>('config');
  const [questionCount, setQuestionCount] = useState(8);
  const [template, setTemplate] = useState<InterviewTemplateConfig>(interviewTemplates[0]);
  const [session, setSession] = useState<InterviewSession | null>(null);
  const [currentQuestion, setCurrentQuestion] = useState<InterviewQuestion | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [answer, setAnswer] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [isCreating, setIsCreating] = useState(false);
  const [checkingUnfinished, setCheckingUnfinished] = useState(false);
  const [unfinishedSession, setUnfinishedSession] = useState<InterviewSession | null>(null);
  const [showCompleteConfirm, setShowCompleteConfirm] = useState(false);
  const [forceCreateNew, setForceCreateNew] = useState(false);
  const [jobTargets, setJobTargets] = useState<JobTarget[]>([]);
  const [jobTargetId, setJobTargetId] = useState<number | undefined>();
  const [usingDefaultQuestions, setUsingDefaultQuestions] = useState(false);
  const [templateRecommendReason, setTemplateRecommendReason] = useState<string | null>(null);
  const questionStartedAtRef = useRef(performance.now());
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const timerRef = useRef<number | undefined>(undefined);
  const autoNavigateRef = useRef<number | undefined>(undefined);
  /** 用户是否手动改过模板；改过后不再因切换岗位自动覆盖 */
  const userPickedTemplateRef = useRef(false);

  useEffect(() => {
    if (!currentQuestion) return;
    questionStartedAtRef.current = performance.now();
    setElapsedSeconds(0);
    // 启动计时器
    timerRef.current = window.setInterval(() => {
      setElapsedSeconds(Math.floor((performance.now() - questionStartedAtRef.current) / 1000));
    }, 1000);
    return () => {
      if (timerRef.current) {
        window.clearInterval(timerRef.current);
        timerRef.current = undefined;
      }
    };
  }, [currentQuestion?.questionIndex]);

  // 提交期间暂停计时，防止 AI 往返延迟计入下一题
  useEffect(() => {
    if (isSubmitting && timerRef.current) {
      window.clearInterval(timerRef.current);
      timerRef.current = undefined;
    }
  }, [isSubmitting]);

  // 检查是否有未完成的面试（组件挂载时和resumeId变化时）
  useEffect(() => {
    if (resumeId) {
      checkUnfinishedSession();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resumeId]);

  // 交卷过渡页：约 2.5s 后自动进入面试记录
  useEffect(() => {
    if (stage !== 'submitted' || !session) return;
    autoNavigateRef.current = window.setTimeout(() => {
      onInterviewComplete(session.sessionId);
    }, 2500);
    return () => {
      if (autoNavigateRef.current) {
        window.clearTimeout(autoNavigateRef.current);
        autoNavigateRef.current = undefined;
      }
    };
  }, [stage, session, onInterviewComplete]);

  /** 标记出题来源并进入面试流程 */
  const markQuestionSource = (created: InterviewSession) => {
    setUsingDefaultQuestions(created.questionsSource === 'DEFAULT');
  };

  /** 交卷成功后进入过渡态，不再直接硬跳转 */
  const enterSubmittedStage = () => {
    setStage('submitted');
  };

  useEffect(() => {
    jobTargetApi.list().then(items => {
      setJobTargets(items);
      const active = items.find(item => item.active);
      if (active) {
        setJobTargetId(active.id);
        // 初始带出当前岗位时自动推荐一次模板
        const rec = recommendTemplate(active.jobDescription, interviewTemplates);
        if (rec) {
          setTemplate(rec.template);
          setTemplateRecommendReason(rec.reason);
        }
      }
    }).catch(() => {});
  }, []);

  /** 根据当前岗位 JD 推荐并应用模板 */
  const applyTemplateRecommendation = (targetId?: number, force = false) => {
    const target = jobTargets.find((j) => j.id === (targetId ?? jobTargetId));
    if (!target?.jobDescription?.trim()) {
      setTemplateRecommendReason(null);
      return;
    }
    if (!force && userPickedTemplateRef.current) {
      return;
    }
    const rec = recommendTemplate(target.jobDescription, interviewTemplates);
    if (rec) {
      setTemplate(rec.template);
      setTemplateRecommendReason(rec.reason);
      userPickedTemplateRef.current = false;
    }
  };

  const handleJobTargetChange = (id?: number) => {
    setJobTargetId(id);
    if (!id) {
      setTemplateRecommendReason(null);
      return;
    }
    // 切换岗位时自动推荐（用户未手动改模板时）
    userPickedTemplateRef.current = false;
    // jobTargets 已在 state，下一轮渲染前先用当前列表算
    const target = jobTargets.find((j) => j.id === id);
    if (target?.jobDescription) {
      const rec = recommendTemplate(target.jobDescription, interviewTemplates);
      if (rec) {
        setTemplate(rec.template);
        setTemplateRecommendReason(rec.reason);
      }
    }
  };

  const handleTemplateChange = (next: InterviewTemplateConfig) => {
    userPickedTemplateRef.current = true;
    setTemplate(next);
    // 手动选择时保留推荐文案仅当仍是推荐模板
    if (templateRecommendReason && next.id !== template.id) {
      const target = jobTargets.find((j) => j.id === jobTargetId);
      if (target) {
        const rec = recommendTemplate(target.jobDescription, interviewTemplates);
        if (rec && rec.template.id === next.id) {
          setTemplateRecommendReason(rec.reason);
        } else {
          setTemplateRecommendReason(null);
        }
      }
    }
  };

  const checkUnfinishedSession = async () => {
    if (!resumeId) return;

    setCheckingUnfinished(true);
    try {
      const foundSession = await interviewApi.findUnfinishedSession(resumeId);
      if (foundSession) {
        setUnfinishedSession(foundSession);
      }
    } catch (err) {
      console.error('检查未完成面试失败', err);
    } finally {
      setCheckingUnfinished(false);
    }
  };

  const handleContinueUnfinished = async () => {
    if (!unfinishedSession) return;
    setForceCreateNew(false);  // 重置强制创建标志
    await restoreSession(unfinishedSession);
    setUnfinishedSession(null);
  };

    const handleStartNew = () => {
    setUnfinishedSession(null);
    setForceCreateNew(true);  // 标记需要强制创建新会话
  };

  const loadCurrentQuestion = async (sessionToRestore: InterviewSession) => {
    const cachedQuestion = sessionToRestore.questions?.[sessionToRestore.currentQuestionIndex];
    if (cachedQuestion) return cachedQuestion;
    const response = await interviewApi.getCurrentQuestion(sessionToRestore.sessionId);
    return response.completed ? null : response.question ?? null;
  };

  const restoreSession = async (sessionToRestore: InterviewSession) => {
    setSession(sessionToRestore);
    markQuestionSource(sessionToRestore);

        // 恢复当前问题
    const currentQ = await loadCurrentQuestion(sessionToRestore);
    if (!currentQ) {
      setError('未获取到面试题目，请返回后重新开始面试');
      return;
    }
    if (currentQ) {
      setCurrentQuestion(currentQ);

        // 如果当前问题已有答案，显示在输入框中
      if (currentQ.userAnswer) {
        setAnswer(currentQ.userAnswer);
      }

        // 恢复消息历史
      const restoredMessages: Message[] = [];
      for (let i = 0; i <= sessionToRestore.currentQuestionIndex; i++) {
        const q = sessionToRestore.questions?.[i];
        if (!q) continue;
        restoredMessages.push({
          type: 'interviewer',
          content: q.question,
          category: q.category,
          questionIndex: i
        });
        if (q.userAnswer) {
          restoredMessages.push({
            type: 'user',
            content: q.userAnswer
          });
        }
      }
      if (restoredMessages.length === 0) {
        restoredMessages.push({
          type: 'interviewer',
          content: currentQ.question,
          category: currentQ.category,
          questionIndex: currentQ.questionIndex
        });
      }
      setMessages(restoredMessages);
    }

        setStage('interview');
  };

    const startInterview = async () => {
    setIsCreating(true);
    setError('');

        try {
      // 创建新面试（如果 forceCreateNew 为 true，则强制创建新会话）
      const newSession = await interviewApi.createSession({
        resumeText,
        questionCount,
        resumeId,
        forceCreate: forceCreateNew,
        template,
        jobTargetId,
      });

            // 重置强制创建标志
      setForceCreateNew(false);
      markQuestionSource(newSession);

            // 如果返回的是未完成的会话（currentQuestionIndex > 0 或已有答案），恢复它
      if (!newSession.questions?.length) {
        await restoreSession(newSession);
        return;
      }

            const hasProgress = newSession.currentQuestionIndex > 0 ||
                          newSession.questions.some(q => q.userAnswer) ||
                          newSession.status === 'IN_PROGRESS';

            if (hasProgress) {
        // 这是恢复的会话
        await restoreSession(newSession);
      } else {
        // 全新的会话
        setSession(newSession);

                if (newSession.questions.length > 0) {
          const firstQuestion = newSession.questions[0];
          setCurrentQuestion(firstQuestion);
          setMessages([{
            type: 'interviewer',
            content: firstQuestion.question,
            category: firstQuestion.category,
            questionIndex: 0
          }]);
        }

                setStage('interview');
      }
    } catch (err) {
      setError('创建面试失败，请重试');
      console.error(err);
      setForceCreateNew(false);  // 出错时也重置标志
    } finally {
      setIsCreating(false);
    }
  };

    const handleSubmitAnswer = async () => {
    if (!answer.trim() || !session || !currentQuestion) return;

    setIsSubmitting(true);

    const userMessage: Message = {
      type: 'user',
      content: answer
    };
    setMessages(prev => [...prev, userMessage]);

    try {
      const response = await interviewApi.submitAnswer({
        sessionId: session.sessionId,
        questionIndex: currentQuestion.questionIndex,
        answer: answer.trim(),
        answerDurationSeconds: Math.max(1, Math.floor((performance.now() - questionStartedAtRef.current) / 1000))
      });

      setSession(previous => previous ? {
        ...previous,
        totalQuestions: response.totalQuestions,
        currentQuestionIndex: response.currentIndex
      } : previous);

      setAnswer('');

      if (response.hasNextQuestion && response.nextQuestion) {
        setCurrentQuestion(response.nextQuestion);
        setMessages(prev => [...prev, {
          type: 'interviewer',
          content: response.nextQuestion!.question,
          category: response.nextQuestion!.category,
          questionIndex: response.nextQuestion!.questionIndex,
          isFollowUp: response.nextQuestion!.isFollowUp,
        }]);
      } else {
        // 面试已完成，进入评估过渡态
        enterSubmittedStage();
      }
    } catch (err) {
      setError('提交答案失败，请重试');
      console.error(err);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCompleteEarly = async () => {
    if (!session) return;

    setIsSubmitting(true);
    try {
      await interviewApi.completeInterview(session.sessionId);
      setShowCompleteConfirm(false);
      enterSubmittedStage();
    } catch (err) {
      setError('提前交卷失败，请重试');
      console.error(err);
    } finally {
      setIsSubmitting(false);
    }
  };

    // 配置界面
  const renderConfig = () => {
    return (
      <InterviewConfigPanel
        questionCount={questionCount}
        onQuestionCountChange={setQuestionCount}
        template={template}
        onTemplateChange={handleTemplateChange}
        onStart={startInterview}
        isCreating={isCreating}
        checkingUnfinished={checkingUnfinished}
        unfinishedSession={unfinishedSession}
        onContinueUnfinished={handleContinueUnfinished}
        onStartNew={handleStartNew}
        resumeText={resumeText}
        onBack={onBack}
        error={error}
        jobTargets={jobTargets}
        jobTargetId={jobTargetId}
        onJobTargetChange={handleJobTargetChange}
        templateRecommendReason={templateRecommendReason}
        onRecommendTemplate={() => applyTemplateRecommendation(jobTargetId, true)}
      />
    );
  };

    // 面试对话界面
  const renderInterview = () => {
    if (!session || !currentQuestion) return null;

    return (
      <InterviewChatPanel
        session={session}
        currentQuestion={currentQuestion}
        messages={messages}
        answer={answer}
        onAnswerChange={setAnswer}
        onSubmit={handleSubmitAnswer}
        elapsedSeconds={elapsedSeconds}
        onCompleteEarly={handleCompleteEarly}
        isSubmitting={isSubmitting}
        showCompleteConfirm={showCompleteConfirm}
        onShowCompleteConfirm={setShowCompleteConfirm}
      />
    );
  };

  const stageSubtitles = {
    config: '配置您的面试参数',
    interview: '认真回答每个问题，展示您的实力',
    submitted: '评估任务已在后台启动',
  };

  const renderSubmitted = () => (
    <div className="mx-auto max-w-lg rounded-2xl border border-slate-100 bg-white p-10 text-center shadow-sm dark:border-slate-700 dark:bg-slate-800">
      <div className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-full bg-emerald-50 dark:bg-emerald-900/30">
        <CheckCircle2 className="h-8 w-8 text-emerald-500" />
      </div>
      <h2 className="text-xl font-semibold text-slate-900 dark:text-white">已提交，正在生成评估报告</h2>
      <p className="mt-3 text-sm leading-6 text-slate-500 dark:text-slate-400">
        题目评分与综合反馈会在后台异步完成。完成后可在「面试记录」中查看分数与详细报告。
      </p>
      <div className="mt-6 inline-flex items-center gap-2 text-sm text-primary-600 dark:text-primary-400">
        <Loader2 className="h-4 w-4 animate-spin" />
        即将跳转到面试记录…
      </div>
      <button
        type="button"
        onClick={() => session && onInterviewComplete(session.sessionId)}
        className="mt-8 rounded-xl bg-primary-500 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-primary-600"
      >
        立即查看记录
      </button>
    </div>
  );

    return (
    <div className="pb-10">
      {/* 页面头部 */}
        <motion.div
        className="text-center mb-10"
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
      >
            <h1 className="text-3xl font-bold text-slate-900 dark:text-white mb-2 flex items-center justify-center gap-3">
          <div className="w-12 h-12 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center">
            <svg className="w-6 h-6 text-white" viewBox="0 0 24 24" fill="none">
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="M19 10v2a7 7 0 0 1-14 0v-2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <line x1="12" y1="19" x2="12" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <line x1="8" y1="23" x2="16" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          模拟面试
        </h1>
            <p className="text-slate-500 dark:text-slate-400">{stageSubtitles[stage]}</p>
      </motion.div>

      {usingDefaultQuestions && stage === 'interview' && (
        <div className="mx-auto mb-6 flex max-w-4xl items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-800/50 dark:bg-amber-900/20 dark:text-amber-200">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>AI 出题失败，本次已切换为备用题库。答题与评估流程不受影响，可稍后重试创建以获取个性化题目。</span>
        </div>
      )}

        <AnimatePresence mode="wait" initial={false}>
        {stage === 'config' && (
          <motion.div
            key="config"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            transition={{ duration: 0.3 }}
          >
            {renderConfig()}
          </motion.div>
        )}
        {stage === 'interview' && (
          <motion.div
            key="interview"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
          >
            {renderInterview()}
          </motion.div>
        )}
        {stage === 'submitted' && (
          <motion.div
            key="submitted"
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
          >
            {renderSubmitted()}
          </motion.div>
        )}
      </AnimatePresence>

        {/* 提前交卷确认对话框 */}
      <ConfirmDialog
        open={showCompleteConfirm}
        title="提前交卷"
        message="确定要提前交卷吗？未回答的问题将按0分计算。"
        confirmText="确定交卷"
        cancelText="取消"
        confirmVariant="warning"
        loading={isSubmitting}
        onConfirm={handleCompleteEarly}
        onCancel={() => setShowCompleteConfirm(false)}
      />
    </div>
  );
}
