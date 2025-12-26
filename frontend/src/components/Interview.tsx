import { useState, useRef, useEffect } from 'react';
import { interviewApi } from '../api/interview';
import type { 
  InterviewSession, 
  InterviewQuestion,
  InterviewReport 
} from '../types/interview';
import './Interview.css';

type InterviewStage = 'config' | 'interview' | 'loading-report' | 'report';

interface Message {
  type: 'interviewer' | 'user';
  content: string;
  category?: string;
  questionIndex?: number;
}

interface InterviewProps {
  resumeText: string;
  onBack: () => void;
}

export default function Interview({ resumeText, onBack }: InterviewProps) {
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
  
  // 自动滚动到底部
  useEffect(() => {
    if (chatContainerRef.current) {
      chatContainerRef.current.scrollTop = chatContainerRef.current.scrollHeight;
    }
  }, [messages]);
  
  // 开始面试
  const startInterview = async () => {
    setIsCreating(true);
    setError('');
    
    try {
      const newSession = await interviewApi.createSession({
        resumeText,
        questionCount
      });
      
      setSession(newSession);
      
      // 获取第一个问题
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
  
  // 提交答案
  const handleSubmitAnswer = async () => {
    if (!answer.trim() || !session || !currentQuestion) return;
    
    setIsSubmitting(true);
    
    // 添加用户消息
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
        // 添加下一个问题
        setMessages(prev => [...prev, {
          type: 'interviewer',
          content: response.nextQuestion!.question,
          category: response.nextQuestion!.category,
          questionIndex: response.nextQuestion!.questionIndex
        }]);
      } else {
        // 面试结束，生成报告
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
  
  // 生成报告
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
  
  // 计算进度
  const getProgress = () => {
    if (!session || !currentQuestion) return 0;
    return ((currentQuestion.questionIndex + 1) / session.totalQuestions) * 100;
  };
  
  // 渲染配置界面
  const renderConfig = () => (
    <div className="config-section">
      <h2>🎯 面试配置</h2>
      
      <div className="form-group">
        <label>选择面试题目数量</label>
        <div className="question-count-selector">
          {[5, 8, 10, 12, 15].map(count => (
            <button
              key={count}
              className={`count-btn ${questionCount === count ? 'active' : ''}`}
              onClick={() => setQuestionCount(count)}
            >
              {count}题
            </button>
          ))}
        </div>
      </div>
      
      <div className="form-group">
        <label>简历预览（前500字）</label>
        <textarea 
          value={resumeText.substring(0, 500) + (resumeText.length > 500 ? '...' : '')}
          readOnly
          style={{ background: '#f8f9fa' }}
        />
      </div>
      
      <p style={{ color: '#666', fontSize: '14px', marginBottom: '20px' }}>
        题目分布：项目经历(20%) + MySQL(20%) + Redis(20%) + Java基础/集合/并发(30%) + Spring(10%)
      </p>
      
      {error && (
        <div className="error-message" style={{ marginBottom: '20px' }}>
          {error}
        </div>
      )}
      
      <div className="btn-wrapper" style={{ display: 'flex', gap: '15px', justifyContent: 'center' }}>
        <button className="back-btn" onClick={onBack}>
          ← 返回
        </button>
        <button 
          className="btn btn-primary" 
          onClick={startInterview}
          disabled={isCreating}
        >
          {isCreating ? '正在生成题目...' : '开始面试 →'}
        </button>
      </div>
    </div>
  );
  
  // 渲染面试对话界面
  const renderInterview = () => (
    <div className="chat-section">
      <div className="progress-bar">
        <div className="progress-fill" style={{ width: `${getProgress()}%` }} />
      </div>
      <div className="progress-text">
        问题 {currentQuestion ? currentQuestion.questionIndex + 1 : 0} / {session?.totalQuestions || 0}
      </div>
      
      <div className="chat-container" ref={chatContainerRef}>
        {messages.map((msg, idx) => (
          <div key={idx} className={`message ${msg.type}`}>
            <div className="message-header">
              <span className="icon">
                {msg.type === 'interviewer' ? '🤖' : '👤'}
              </span>
              <span>{msg.type === 'interviewer' ? '面试官' : '我'}</span>
              {msg.category && (
                <span className="category-tag">{msg.category}</span>
              )}
            </div>
            <div className="message-content">{msg.content}</div>
          </div>
        ))}
      </div>
      
      <div className="answer-input">
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
        />
        <button 
          className="submit-btn"
          onClick={handleSubmitAnswer}
          disabled={!answer.trim() || isSubmitting}
        >
          {isSubmitting ? '提交中...' : '提交回答'}
        </button>
      </div>
      
      <p style={{ textAlign: 'center', fontSize: '12px', color: '#999', marginTop: '10px' }}>
        按 Ctrl+Enter 快速提交
      </p>
    </div>
  );
  
  // 渲染加载报告
  const renderLoadingReport = () => (
    <div className="loading-section">
      <div className="spinner"></div>
      <p>AI正在分析您的面试表现...</p>
      <p style={{ fontSize: '12px', color: '#999', marginTop: '10px' }}>
        这可能需要30秒左右
      </p>
    </div>
  );
  
  // 渲染报告
  const renderReport = () => {
    if (!report) return null;
    
    return (
      <div className="report-section">
        <div className="report-header">
          <div className="report-score">
            <span>{report.overallScore}</span>
          </div>
          <h2>面试评估报告</h2>
        </div>
        
        <div className="category-scores">
          {report.categoryScores.map((cat, idx) => (
            <div key={idx} className="category-item">
              <div className="name">{cat.category}</div>
              <div className="score">{cat.score}分</div>
            </div>
          ))}
        </div>
        
        <div className="report-block">
          <h3>📝 总体评价</h3>
          <div className="feedback-text">{report.overallFeedback}</div>
        </div>
        
        <div className="report-block">
          <h3>✨ 你的优势</h3>
          {report.strengths.map((s, idx) => (
            <div key={idx} className="list-item">
              <span className="icon">✓</span>
              <span>{s}</span>
            </div>
          ))}
        </div>
        
        <div className="report-block">
          <h3>💡 改进建议</h3>
          {report.improvements.map((s, idx) => (
            <div key={idx} className="list-item">
              <span className="icon">→</span>
              <span>{s}</span>
            </div>
          ))}
        </div>
        
        <div className="report-block">
          <h3>📋 问题详情与评分</h3>
          {report.questionDetails.map((q, idx) => (
            <div key={idx} className="question-detail">
              <div className="question-detail-header">
                <span className="category-tag">{q.category}</span>
                <span className="question-score">{q.score}分</span>
              </div>
              <div className="question-text">Q{q.questionIndex + 1}: {q.question}</div>
              <div className="answer-text">
                <strong>你的回答：</strong>{q.userAnswer || '(未回答)'}
              </div>
              <div className="feedback-inline">
                <strong>评价：</strong>{q.feedback}
              </div>
            </div>
          ))}
        </div>
        
        <div className="report-block">
          <h3>📚 参考答案</h3>
          {report.referenceAnswers.map((ref, idx) => (
            <div key={idx} className="reference-block">
              <h4>Q{ref.questionIndex + 1}: {ref.question}</h4>
              <p>{ref.referenceAnswer}</p>
              {ref.keyPoints.length > 0 && (
                <div className="key-points">
                  {ref.keyPoints.map((kp, kpIdx) => (
                    <span key={kpIdx} className="key-point">{kp}</span>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
        
        <div className="btn-wrapper">
          <button className="btn btn-primary" onClick={onBack}>
            返回首页
          </button>
        </div>
      </div>
    );
  };
  
  return (
    <div className="interview-page">
      <div className="interview-header">
        <h1>🎤 模拟面试</h1>
        <p>
          {stage === 'config' && '配置您的面试参数'}
          {stage === 'interview' && '认真回答每个问题，展示您的实力'}
          {stage === 'loading-report' && '正在生成评估报告...'}
          {stage === 'report' && '面试结束，查看您的表现'}
        </p>
      </div>
      
      {stage === 'config' && renderConfig()}
      {stage === 'interview' && renderInterview()}
      {stage === 'loading-report' && (
        <div className="chat-section">{renderLoadingReport()}</div>
      )}
      {stage === 'report' && renderReport()}
    </div>
  );
}
