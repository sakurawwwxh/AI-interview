import { motion } from 'framer-motion';
import type { InterviewReport, InterviewSession } from '../types/interview';

interface InterviewReportPanelProps {
  report: InterviewReport;
  session: InterviewSession | null;
  onBack: () => void;
}

/**
 * 面试报告面板组件
 */
export default function InterviewReportPanel({ report, session, onBack }: InterviewReportPanelProps) {
  return (
    <motion.div 
      className="max-w-4xl mx-auto space-y-6"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
    >
      {/* 总分卡片 */}
      <ScoreCard 
        score={report.overallScore}
        totalQuestions={session?.totalQuestions || 0}
      />

      {/* 分类得分 */}
      <CategoryScoresSection categoryScores={report.categoryScores} />

      {/* 总体评价 */}
      <OverallFeedbackSection feedback={report.overallFeedback} />

      {/* 优势与改进 */}
      <StrengthsAndImprovementsSection 
        strengths={report.strengths}
        improvements={report.improvements}
      />

      {/* 问题详情 */}
      <QuestionDetailsSection questionDetails={report.questionDetails} />

      {/* 参考答案 */}
      <ReferenceAnswersSection referenceAnswers={report.referenceAnswers} />

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
}

// 分数卡片组件
function ScoreCard({ score, totalQuestions }: { score: number; totalQuestions: number }) {
  return (
    <div className="bg-gradient-to-br from-primary-500 to-primary-600 rounded-2xl p-8 text-white text-center">
      <motion.div 
        className="w-28 h-28 mx-auto mb-4 bg-white/20 backdrop-blur rounded-full flex items-center justify-center"
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{ delay: 0.2, type: "spring", stiffness: 200 }}
      >
        <span className="text-5xl font-bold">{score}</span>
      </motion.div>
      <h2 className="text-2xl font-bold mb-2">面试评估报告</h2>
      <p className="text-white/80">共完成 {totalQuestions} 道面试题目</p>
    </div>
  );
}

// 分类得分组件
function CategoryScoresSection({ categoryScores }: { categoryScores: Array<{ category: string; score: number }> }) {
  return (
    <div className="bg-white rounded-2xl p-6">
      <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
        <svg className="w-5 h-5 text-primary-500" viewBox="0 0 24 24" fill="none">
          <path d="M18 20V10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          <path d="M12 20V4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          <path d="M6 20V14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
        分类得分
      </h3>
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
        {categoryScores.map((cat, idx) => (
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
  );
}

// 总体评价组件
function OverallFeedbackSection({ feedback }: { feedback: string }) {
  return (
    <div className="bg-white rounded-2xl p-6">
      <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
        <svg className="w-5 h-5 text-primary-500" viewBox="0 0 24 24" fill="none">
          <path d="M14 2H6C5.46957 2 4.96086 2.21071 4.58579 2.58579C4.21071 2.96086 4 3.46957 4 4V20C4 20.5304 4.21071 21.0391 4.58579 21.4142C4.96086 21.7893 5.46957 22 6 22H18C18.5304 22 19.0391 21.7893 19.4142 21.4142C19.7893 21.0391 20 20.5304 20 20V8L14 2Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          <polyline points="14,2 14,8 20,8" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          <line x1="16" y1="13" x2="8" y2="13" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          <line x1="16" y1="17" x2="8" y2="17" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
        总体评价
      </h3>
      <p className="text-slate-600 leading-relaxed bg-slate-50 p-4 rounded-xl">{feedback}</p>
    </div>
  );
}

// 优势与改进组件
function StrengthsAndImprovementsSection({
  strengths,
  improvements
}: {
  strengths: string[];
  improvements: string[];
}) {
  return (
    <div className="grid md:grid-cols-2 gap-6">
      <motion.div 
        className="bg-emerald-50 rounded-2xl p-6 border border-emerald-100"
        initial={{ opacity: 0, x: -20 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ delay: 0.3 }}
      >
        <h3 className="text-lg font-semibold text-emerald-800 mb-4 flex items-center gap-2">
          <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <polyline points="22,4 12,14.01 9,11.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          你的优势
        </h3>
        <ul className="space-y-3">
          {strengths.map((s, idx) => (
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
        <h3 className="text-lg font-semibold text-amber-800 mb-4 flex items-center gap-2">
          <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
            <path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          改进建议
        </h3>
        <ul className="space-y-3">
          {improvements.map((s, idx) => (
            <li key={idx} className="flex items-start gap-2 text-amber-700">
              <span className="text-amber-500 mt-0.5">→</span>
              <span>{s}</span>
            </li>
          ))}
        </ul>
      </motion.div>
    </div>
  );
}

// 问题详情组件
function QuestionDetailsSection({ questionDetails }: { questionDetails: Array<{
  questionIndex: number;
  question: string;
  category: string;
  score: number;
  userAnswer: string;
  feedback: string;
}> }) {
  return (
    <div className="bg-white rounded-2xl p-6">
      <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
        <svg className="w-5 h-5 text-primary-500" viewBox="0 0 24 24" fill="none">
          <path d="M9 5H7C5.89543 5 5 5.89543 5 7V19C5 20.1046 5.89543 21 7 21H17C18.1046 21 19 20.1046 19 19V7C19 5.89543 18.1046 5 17 5H15" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          <rect x="9" y="3" width="6" height="4" rx="1" stroke="currentColor" strokeWidth="2"/>
          <line x1="9" y1="12" x2="15" y2="12" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
          <line x1="9" y1="16" x2="15" y2="16" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        </svg>
        问题详情与评分
      </h3>
      <div className="space-y-4">
        {questionDetails.map((q, idx) => (
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
  );
}

// 参考答案组件
function ReferenceAnswersSection({ referenceAnswers }: { referenceAnswers: Array<{
  questionIndex: number;
  question: string;
  referenceAnswer: string;
  keyPoints: string[];
}> }) {
  return (
    <div className="bg-white rounded-2xl p-6">
      <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
        <svg className="w-5 h-5 text-primary-500" viewBox="0 0 24 24" fill="none">
          <path d="M4 19.5A2.5 2.5 0 016.5 17H20" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          <path d="M6.5 2H20V22H6.5A2.5 2.5 0 014 19.5V4.5A2.5 2.5 0 016.5 2z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
        参考答案
      </h3>
      <div className="space-y-4">
        {referenceAnswers.map((ref, idx) => (
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
  );
}

