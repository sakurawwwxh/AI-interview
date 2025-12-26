import { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { interviewApi } from '../api/interview';
import type { 
  InterviewSession, 
  InterviewQuestion,
  InterviewReport 
} from '../types/interview';

type InterviewStage = 'config' | 'interview' | 'loading-report' | 'report';

interface Message {
  type: 'interviewer' | 'user';
  content: string;
  category?: string;
  questionIndex?: number;
}

interface InterviewProps {
  resumeText: string;
  resumeId?: number;
  onBack: () => void;
}

export default function Interview({ resumeText, resumeId, onBack }: InterviewProps) {
  const [stage, setStage] = useState<InterviewStage>('config');
  const [questionCount, setQuestionCount] = useState(8);
  const [session, setSession] = useState<InterviewSession | null>(null);
  const [currentQuestion, setCurrentQuestion] = useState<InterviewQuestion | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [answer, setAnswer] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [report, setReport] = useState<InterviewReport | null>(null);
  const [error, setError] = useState('');
  const [isCreating, setIsCreating] = useState(false);
  
  const chatContainerRef = useRef<HTMLDivElement>(null);
  
  useEffect(() => {
    if (chatContainerRef.current) {
      chatContainerRef.current.scrollTop = chatContainerRef.current.scrollHeight;
    }
  }, [messages]);
  
  const startInterview = async () => {
    setIsCreating(true);
    setError('');
    
    try {
      const newSession = await interviewApi.createSession({
        resumeText,
        questionCount,
        resumeId
      });
      
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
    } catch (err) {
      setError('创建面试失败，请重试');
      console.error(err);
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
        answer: answer.trim()
      });
      
      setAnswer('');
      
      if (response.hasNextQuestion && response.nextQuestion) {
        setCurrentQuestion(response.nextQuestion);
        setMessages(prev => [...prev, {
          type: 'interviewer',
          content: response.nextQuestion!.question,
          category: response.nextQuestion!.category,
          questionIndex: response.nextQuestion!.questionIndex
        }]);
      } else {
        setStage('loading-report');
        await generateReport();
      }
    } catch (err) {
      setError('提交答案失败，请重试');
      console.error(err);
    } finally {
      setIsSubmitting(false);
    }
  };
  
  const generateReport = async () => {
    if (!session) return;
    
    try {
      const reportData = await interviewApi.getReport(session.sessionId);
      setReport(reportData);
      setStage('report');
    } catch (err) {
      setError('生成报告失败，请重试');
      setStage('interview');
      console.error(err);
    }
  };
  
  const getProgress = () => {
    if (!session || !currentQuestion) return 0;
    return ((currentQuestion.questionIndex + 1) / session.totalQuestions) * 100;
  };

  const questionCounts = [5, 8, 10, 12, 15];
  
  // 配置界面
  const renderConfig = () => (
    <motion.div 
      className="max-w-2xl mx-auto"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
    >
      <div className="bg-white rounded-2xl p-8 shadow-sm">
        <h2 className="text-2xl font-bold text-slate-900 mb-6 flex items-center gap-3">
          <span className="text-3xl">🎯</span> 面试配置
        </h2>
        
        <div className="mb-8">
          <label className="block text-sm font-semibold text-slate-600 mb-4">选择面试题目数量</label>
          <div className="flex gap-3 flex-wrap">
            {questionCounts.map(count => (
              <motion.button
                key={count}
                onClick={() => setQuestionCount(count)}
                className={`px-5 py-3 rounded-xl font-medium transition-all
                  ${questionCount === count 
                    ? 'bg-gradient-to-r from-primary-500 to-primary-600 text-white shadow-lg shadow-primary-500/30' 
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                whileHover={{ scale: 1.03 }}
                whileTap={{ scale: 0.97 }}
              >
                {count}题
              </motion.button>
            ))}
          </div>
        </div>
        
        <div className="mb-6">
          <label className="block text-sm font-semibold text-slate-600 mb-3">简历预览（前500字）</label>
          <textarea 
            value={resumeText.substring(0, 500) + (resumeText.length > 500 ? '...' : '')}
            readOnly
            className="w-full h-32 p-4 bg-slate-50 border border-slate-200 rounded-xl text-slate-600 text-sm resize-none"
          />
        </div>
        
        <p className="text-sm text-slate-500 mb-6">
          题目分布：项目经历(20%) + MySQL(20%) + Redis(20%) + Java基础/集合/并发(30%) + Spring(10%)
        </p>

        <AnimatePresence>
          {error && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="mb-6 p-4 bg-red-50 border border-red-200 rounded-xl text-red-600 text-sm"
            >
              ⚠️ {error}
            </motion.div>
          )}
        </AnimatePresence>
        
        <div className="flex justify-center gap-4">
          <motion.button 
            onClick={onBack}
            className="px-6 py-3 border border-slate-200 rounded-xl text-slate-600 font-medium hover:bg-slate-50 transition-all"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
          >
            ← 返回
          </motion.button>
          <motion.button 
            onClick={startInterview}
            disabled={isCreating}
            className="px-8 py-3 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-semibold shadow-lg shadow-primary-500/30 hover:shadow-xl disabled:opacity-60 disabled:cursor-not-allowed transition-all"
            whileHover={{ scale: 1.02, y: -1 }}
            whileTap={{ scale: 0.98 }}
          >
            {isCreating ? (
              <span className="flex items-center gap-2">
                <motion.span 
                  className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full"
                  animate={{ rotate: 360 }}
                  transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
                />
                正在生成题目...
              </span>
            ) : '开始面试 →'}
          </motion.button>
        </div>
      </div>
    </motion.div>
  );
  
  // 面试对话界面
  const renderInterview = () => (
    <motion.div 
      className="max-w-3xl mx-auto"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
    >
      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        {/* 进度条 */}
        <div className="px-6 pt-6">
          <div className="h-2 bg-slate-100 rounded-full overflow-hidden">
            <motion.div 
              className="h-full bg-gradient-to-r from-primary-500 to-primary-600 rounded-full"
              initial={{ width: 0 }}
              animate={{ width: `${getProgress()}%` }}
              transition={{ duration: 0.5 }}
            />
          </div>
          <p className="text-sm text-slate-500 mt-2 text-center">
            问题 {currentQuestion ? currentQuestion.questionIndex + 1 : 0} / {session?.totalQuestions || 0}
          </p>
        </div>
        
        {/* 对话区域 */}
        <div 
          ref={chatContainerRef}
          className="h-[400px] overflow-y-auto p-6 space-y-4 scrollbar-thin"
        >
          <AnimatePresence>
            {messages.map((msg, idx) => (
              <motion.div 
                key={idx}
                initial={{ opacity: 0, x: msg.type === 'interviewer' ? -20 : 20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.3 }}
                className={`flex gap-3 ${msg.type === 'user' ? 'justify-end' : ''}`}
              >
                {msg.type === 'interviewer' && (
                  <div className="w-10 h-10 bg-primary-100 rounded-xl flex items-center justify-center text-xl flex-shrink-0">
                    🤖
                  </div>
                )}
                <div className={`max-w-[80%] ${msg.type === 'user' ? 'order-first' : ''}`}>
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-sm font-medium text-slate-600">
                      {msg.type === 'interviewer' ? '面试官' : '我'}
                    </span>
                    {msg.category && (
                      <span className="px-2 py-0.5 bg-primary-100 text-primary-600 text-xs rounded-full">
                        {msg.category}
                      </span>
                    )}
                  </div>
                  <div className={`p-4 rounded-2xl ${
                    msg.type === 'interviewer' 
                      ? 'bg-slate-100 text-slate-800' 
                      : 'bg-gradient-to-r from-primary-500 to-primary-600 text-white'
                  }`}>
                    {msg.content}
                  </div>
                </div>
                {msg.type === 'user' && (
                  <div className="w-10 h-10 bg-emerald-100 rounded-xl flex items-center justify-center text-xl flex-shrink-0">
                    👤
                  </div>
                )}
              </motion.div>
            ))}
          </AnimatePresence>
        </div>
        
        {/* 输入区域 */}
        <div className="p-6 border-t border-slate-100">
          <div className="flex gap-4">
            <textarea
              value={answer}
              onChange={(e) => setAnswer(e.target.value)}
              placeholder="请输入你的回答..."
              disabled={isSubmitting}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && e.ctrlKey) {
                  handleSubmitAnswer();
                }
              }}
              className="flex-1 p-4 bg-slate-50 border border-slate-200 rounded-xl resize-none h-24 focus:outline-none focus:border-primary-400 focus:ring-2 focus:ring-primary-100 transition-all disabled:opacity-60"
            />
            <motion.button 
              onClick={handleSubmitAnswer}
              disabled={!answer.trim() || isSubmitting}
              className="px-6 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-semibold shadow-lg shadow-primary-500/30 disabled:opacity-50 disabled:cursor-not-allowed transition-all self-end h-12"
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              {isSubmitting ? '提交中...' : '提交回答'}
            </motion.button>
          </div>
          <p className="text-xs text-slate-400 text-center mt-3">按 Ctrl+Enter 快速提交</p>
        </div>
      </div>
    </motion.div>
  );
  
  // 加载报告
  const renderLoadingReport = () => (
    <motion.div 
      className="max-w-md mx-auto text-center py-20"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
    >
      <motion.div 
        className="w-16 h-16 border-4 border-slate-200 border-t-primary-500 rounded-full mx-auto mb-6"
        animate={{ rotate: 360 }}
        transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
      />
      <h3 className="text-xl font-semibold text-slate-800 mb-2">AI正在分析您的面试表现...</h3>
      <p className="text-slate-500">这可能需要30秒左右</p>
    </motion.div>
  );
  
  // 报告界面
  const renderReport = () => {
    if (!report) return null;
    
    return (
      <motion.div 
        className="max-w-4xl mx-auto space-y-6"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        {/* 总分卡片 */}
        <div className="bg-gradient-to-br from-primary-500 to-primary-600 rounded-2xl p-8 text-white text-center">
          <motion.div 
            className="w-28 h-28 mx-auto mb-4 bg-white/20 backdrop-blur rounded-full flex items-center justify-center"
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            transition={{ delay: 0.2, type: "spring", stiffness: 200 }}
          >
            <span className="text-5xl font-bold">{report.overallScore}</span>
          </motion.div>
          <h2 className="text-2xl font-bold mb-2">面试评估报告</h2>
          <p className="text-white/80">共完成 {session?.totalQuestions} 道面试题目</p>
        </div>

        {/* 分类得分 */}
        <div className="bg-white rounded-2xl p-6">
          <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
            <span>📊</span> 分类得分
          </h3>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
            {report.categoryScores.map((cat, idx) => (
              <motion.div 
                key={idx}
                className="bg-slate-50 rounded-xl p-4 text-center"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.1 * idx }}
              >
                <p className="text-sm text-slate-500 mb-1">{cat.category}</p>
                <p className="text-2xl font-bold text-slate-900">{cat.score}</p>
              </motion.div>
            ))}
          </div>
        </div>

        {/* 总体评价 */}
        <div className="bg-white rounded-2xl p-6">
          <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
            <span>📝</span> 总体评价
          </h3>
          <p className="text-slate-600 leading-relaxed bg-slate-50 p-4 rounded-xl">{report.overallFeedback}</p>
        </div>

        {/* 优势与改进 */}
        <div className="grid md:grid-cols-2 gap-6">
          <motion.div 
            className="bg-emerald-50 rounded-2xl p-6 border border-emerald-100"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.3 }}
          >
            <h3 className="text-lg font-semibold text-emerald-800 mb-4">✨ 你的优势</h3>
            <ul className="space-y-3">
              {report.strengths.map((s, idx) => (
                <li key={idx} className="flex items-start gap-2 text-emerald-700">
                  <span className="text-emerald-500 mt-0.5">✓</span>
                  <span>{s}</span>
                </li>
              ))}
            </ul>
          </motion.div>

          <motion.div 
            className="bg-amber-50 rounded-2xl p-6 border border-amber-100"
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.4 }}
          >
            <h3 className="text-lg font-semibold text-amber-800 mb-4">💡 改进建议</h3>
            <ul className="space-y-3">
              {report.improvements.map((s, idx) => (
                <li key={idx} className="flex items-start gap-2 text-amber-700">
                  <span className="text-amber-500 mt-0.5">→</span>
                  <span>{s}</span>
                </li>
              ))}
            </ul>
          </motion.div>
        </div>

        {/* 问题详情 */}
        <div className="bg-white rounded-2xl p-6">
          <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
            <span>📋</span> 问题详情与评分
          </h3>
          <div className="space-y-4">
            {report.questionDetails.map((q, idx) => (
              <motion.div 
                key={idx}
                className="border border-slate-100 rounded-xl p-5"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.1 * idx }}
              >
                <div className="flex items-center justify-between mb-3">
                  <span className="px-3 py-1 bg-primary-100 text-primary-600 text-sm rounded-full">{q.category}</span>
                  <span className="font-bold text-slate-800">{q.score}分</span>
                </div>
                <p className="font-medium text-slate-800 mb-2">Q{q.questionIndex + 1}: {q.question}</p>
                <div className="bg-slate-50 rounded-lg p-3 mb-2">
                  <p className="text-sm text-slate-500 mb-1">你的回答：</p>
                  <p className="text-slate-700">{q.userAnswer || '(未回答)'}</p>
                </div>
                <div className="bg-primary-50 rounded-lg p-3">
                  <p className="text-sm text-primary-600 mb-1">评价：</p>
                  <p className="text-slate-700">{q.feedback}</p>
                </div>
              </motion.div>
            ))}
          </div>
        </div>

        {/* 参考答案 */}
        <div className="bg-white rounded-2xl p-6">
          <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
            <span>📚</span> 参考答案
          </h3>
          <div className="space-y-4">
            {report.referenceAnswers.map((ref, idx) => (
              <div key={idx} className="border border-slate-100 rounded-xl p-5">
                <h4 className="font-medium text-slate-800 mb-3">Q{ref.questionIndex + 1}: {ref.question}</h4>
                <p className="text-slate-600 mb-3">{ref.referenceAnswer}</p>
                {ref.keyPoints.length > 0 && (
                  <div className="flex flex-wrap gap-2">
                    {ref.keyPoints.map((kp, kpIdx) => (
                      <span key={kpIdx} className="px-3 py-1 bg-slate-100 text-slate-600 text-sm rounded-lg">{kp}</span>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>

        {/* 返回按钮 */}
        <div className="text-center pb-10">
          <motion.button 
            onClick={onBack}
            className="px-10 py-4 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-semibold shadow-lg shadow-primary-500/30 hover:shadow-xl transition-all"
            whileHover={{ scale: 1.02, y: -2 }}
            whileTap={{ scale: 0.98 }}
          >
            返回首页
          </motion.button>
        </div>
      </motion.div>
    );
  };

  const stageSubtitles = {
    config: '配置您的面试参数',
    interview: '认真回答每个问题，展示您的实力',
    'loading-report': '正在生成评估报告...',
    report: '面试结束，查看您的表现'
  };
  
  return (
    <div className="pb-10">
      {/* 页面头部 */}
      <motion.div 
        className="text-center mb-10"
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <h1 className="text-3xl font-bold text-slate-900 mb-2 flex items-center justify-center gap-3">
          <span className="text-4xl">🎤</span> 模拟面试
        </h1>
        <p className="text-slate-500">{stageSubtitles[stage]}</p>
      </motion.div>
      
      <AnimatePresence mode="wait">
        {stage === 'config' && <motion.div key="config">{renderConfig()}</motion.div>}
        {stage === 'interview' && <motion.div key="interview">{renderInterview()}</motion.div>}
        {stage === 'loading-report' && <motion.div key="loading">{renderLoadingReport()}</motion.div>}
        {stage === 'report' && <motion.div key="report">{renderReport()}</motion.div>}
      </AnimatePresence>
    </div>
  );
}
