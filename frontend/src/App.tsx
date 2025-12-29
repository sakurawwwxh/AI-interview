import { BrowserRouter, Routes, Route, Navigate, useNavigate, useParams, useLocation } from 'react-router-dom';
import Layout from './components/Layout';
import UploadPage from './pages/UploadPage';
import HistoryList from './pages/HistoryPage';
import ResumeDetailPage from './pages/ResumeDetailPage';
import Interview from './pages/InterviewPage';
import { historyApi } from './api/history';
import { useState, useEffect } from 'react';
import type { ResumeAnalysisResponse, StorageInfo } from './types/resume';

// 上传页面包装器
function UploadPageWrapper() {
  const navigate = useNavigate();
  
  const handleAnalysisComplete = (_result: ResumeAnalysisResponse, storage: StorageInfo) => {
    // 直接跳转到详情页
    if (storage.resumeId) {
      navigate(`/history/${storage.resumeId}`);
    }
  };
  
  return <UploadPage onAnalysisComplete={handleAnalysisComplete} />;
}

// 历史记录列表包装器
function HistoryListWrapper() {
  const navigate = useNavigate();
  
  const handleSelectResume = (id: number) => {
    navigate(`/history/${id}`);
  };
  
  return <HistoryList onSelectResume={handleSelectResume} />;
}

// 简历详情包装器
function ResumeDetailWrapper() {
  const { resumeId } = useParams<{ resumeId: string }>();
  const navigate = useNavigate();
  
  if (!resumeId) {
    return <Navigate to="/history" replace />;
  }
  
  const handleBack = () => {
    navigate('/history');
  };
  
  const handleStartInterview = (resumeText: string, resumeId: number) => {
    navigate(`/interview/${resumeId}`, { state: { resumeText } });
  };
  
  return (
    <ResumeDetailPage
      resumeId={parseInt(resumeId, 10)}
      onBack={handleBack}
      onStartInterview={handleStartInterview}
    />
  );
}

// 模拟面试包装器
function InterviewWrapper() {
  const { resumeId } = useParams<{ resumeId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const [resumeText, setResumeText] = useState<string>('');
  const [loading, setLoading] = useState(true);
  
  useEffect(() => {
    // 优先从location state获取resumeText
    const stateText = (location.state as { resumeText?: string })?.resumeText;
    if (stateText) {
      setResumeText(stateText);
      setLoading(false);
    } else if (resumeId) {
      // 如果没有，从API获取简历详情
      historyApi.getResumeDetail(parseInt(resumeId, 10))
        .then(resume => {
          setResumeText(resume.resumeText);
          setLoading(false);
        })
        .catch(err => {
          console.error('获取简历文本失败', err);
          setLoading(false);
        });
    } else {
      setLoading(false);
    }
  }, [resumeId, location.state]);
  
  if (!resumeId) {
    return <Navigate to="/history" replace />;
  }
  
  const handleBack = () => {
    // 尝试返回详情页，如果失败则返回历史列表
    navigate(`/history/${resumeId}`, { replace: false });
  };
  
  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center">
          <div className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full mx-auto mb-4 animate-spin" />
          <p className="text-slate-500">加载中...</p>
        </div>
      </div>
    );
  }
  
  return (
    <Interview
      resumeText={resumeText}
      resumeId={parseInt(resumeId, 10)}
      onBack={handleBack}
    />
  );
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          {/* 默认重定向到上传页面 */}
          <Route index element={<Navigate to="/upload" replace />} />
          
          {/* 上传页面 */}
          <Route path="upload" element={<UploadPageWrapper />} />
          
          {/* 历史记录列表 */}
          <Route path="history" element={<HistoryListWrapper />} />
          
          {/* 简历详情 */}
          <Route path="history/:resumeId" element={<ResumeDetailWrapper />} />
          
          {/* 模拟面试 */}
          <Route path="interview/:resumeId" element={<InterviewWrapper />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
