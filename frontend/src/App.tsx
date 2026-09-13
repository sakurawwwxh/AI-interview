import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import AdminRoute from './components/AdminRoute';
import { useAuthStore } from './stores/authStore';
import { useEffect, useState, Suspense, lazy } from 'react';
import { historyApi } from './api/history';
import type { UploadKnowledgeBaseResponse } from './api/knowledgebase';

// Lazy load components
const LoginPage = lazy(() => import('./pages/LoginPage'));
const RegisterPage = lazy(() => import('./pages/RegisterPage'));
const UploadPage = lazy(() => import('./pages/UploadPage'));
const HistoryList = lazy(() => import('./pages/HistoryPage'));
const ResumeDetailPage = lazy(() => import('./pages/ResumeDetailPage'));
const Interview = lazy(() => import('./pages/InterviewPage'));
const InterviewHistoryPage = lazy(() => import('./pages/InterviewHistoryPage'));
const InterviewStatisticsPage = lazy(() => import('./pages/InterviewStatisticsPage'));
const ProfilePage = lazy(() => import('./pages/ProfilePage'));
const PracticeCenterPage = lazy(() => import('./pages/PracticeCenterPage'));
const PracticeSessionPage = lazy(() => import('./pages/PracticeSessionPage'));
const AdminUsersPage = lazy(() => import('./pages/AdminUsersPage'));
const DifyManagementPage = lazy(() => import('./pages/DifyManagementPage'));
const KnowledgeBaseQueryPage = lazy(() => import('./pages/KnowledgeBaseQueryPage'));
const KnowledgeBaseUploadPage = lazy(() => import('./pages/KnowledgeBaseUploadPage'));
const KnowledgeBaseManagePage = lazy(() => import('./pages/KnowledgeBaseManagePage'));
const JobTargetsPage = lazy(() => import('./pages/JobTargetsPage'));
const DashboardPage = lazy(() => import('./pages/DashboardPage'));

// 路由懒加载占位：可被读屏识别
const Loading = () => (
  <div className="flex min-h-[50vh] items-center justify-center" role="status" aria-live="polite" aria-busy="true">
    <div className="h-10 w-10 animate-spin rounded-full border-3 border-slate-200 border-t-primary-500" aria-hidden />
    <span className="sr-only">页面加载中</span>
  </div>
);

// 上传页面包装器
function UploadPageWrapper() {
  const navigate = useNavigate();

  const handleUploadComplete = (resumeId: number) => {
    // 异步模式：上传成功后跳转到简历库，让用户在列表中查看分析状态
    navigate('/history', { state: { newResumeId: resumeId } });
  };

  return <UploadPage onUploadComplete={handleUploadComplete} />;
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

  const handleStartInterview = (resumeText: string, resumeId: number, jobTargetId?: number) => {
    // 仅传 id + 可选岗位；正文在 InterviewWrapper 同步 state / API 加载，避免大文本 state 异常
    const text = (resumeText || '').trim();
    navigate(`/interview/${resumeId}`, {
      state: text ? { resumeText: text, jobTargetId } : { jobTargetId },
    });
  };

  return (
    <ResumeDetailPage
      resumeId={parseInt(resumeId, 10)}
      onBack={handleBack}
      onStartInterview={handleStartInterview}
    />
  );
}

// 模拟面试包装器：同步读 location.state，避免 effect 竞态导致一直转圈/空白
function InterviewWrapper() {
  const { resumeId } = useParams<{ resumeId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const locationState = (location.state as { resumeText?: string; jobTargetId?: number } | null) ?? null;

  // 首次渲染即用导航 state，刷新时再走 API
  const [resumeText, setResumeText] = useState(() => locationState?.resumeText?.trim() || '');
  const [loading, setLoading] = useState(() => !locationState?.resumeText?.trim());
  const [loadError, setLoadError] = useState<string | null>(null);
  const initialJobTargetId = locationState?.jobTargetId;

  useEffect(() => {
    let cancelled = false;
    const stateText = ((location.state as { resumeText?: string } | null)?.resumeText || '').trim();

    if (stateText) {
      setResumeText(stateText);
      setLoadError(null);
      setLoading(false);
      return () => {
        cancelled = true;
      };
    }

    if (!resumeId) {
      setLoading(false);
      return () => {
        cancelled = true;
      };
    }

    setLoading(true);
    setLoadError(null);
    historyApi
      .getResumeDetail(parseInt(resumeId, 10))
      .then((resume) => {
        if (cancelled) return;
        const text = (resume.resumeText || '').trim();
        setResumeText(text);
        setLoadError(text ? null : '该简历没有可用文本，无法开始面试');
        setLoading(false);
      })
      .catch((err) => {
        console.error('获取简历文本失败', err);
        if (cancelled) return;
        setLoadError('加载简历失败，请返回后重试');
        setLoading(false);
      });

    return () => {
      cancelled = true;
    };
    // location.key：每次导航（含同 resumeId 再次开面）都重新对齐 state
  }, [resumeId, location.key]);

  if (!resumeId) {
    return <Navigate to="/history" replace />;
  }

  const handleBack = () => {
    navigate(`/history/${resumeId}`, { replace: false });
  };

  const handleInterviewComplete = (sessionId?: string) => {
    navigate('/interviews', { state: { highlightSessionId: sessionId } });
  };

  if (loading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center" role="status" aria-live="polite" aria-busy="true">
        <div className="text-center">
          <div className="mx-auto mb-4 h-10 w-10 animate-spin rounded-full border-3 border-slate-200 border-t-primary-500" aria-hidden />
          <p className="text-sm text-[var(--shell-muted)]">正在准备面试…</p>
        </div>
      </div>
    );
  }

  if (loadError || !resumeText) {
    return (
      <div className="ui-panel mx-auto max-w-lg px-6 py-12 text-center">
        <p className="text-base font-semibold text-[var(--shell-text)]">无法开始模拟面试</p>
        <p className="mt-2 text-sm text-[var(--shell-muted)]">{loadError || '缺少简历文本'}</p>
        <button type="button" onClick={handleBack} className="ui-btn-primary mt-6">
          返回简历详情
        </button>
      </div>
    );
  }

  return (
    <Interview
      key={`${resumeId}-${location.key}`}
      resumeText={resumeText}
      resumeId={parseInt(resumeId, 10)}
      initialJobTargetId={initialJobTargetId}
      onBack={handleBack}
      onInterviewComplete={handleInterviewComplete}
    />
  );
}

function App() {
  // 初始化时从 localStorage 恢复登录态
  const loadFromStorage = useAuthStore((s) => s.loadFromStorage);
  useEffect(() => {
    loadFromStorage();
  }, [loadFromStorage]);

  return (
    <BrowserRouter>
      <Suspense fallback={<Loading />}>
        <Routes>
          {/* 公开路由（不套 Layout） */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* 受保护路由（套 Layout + ProtectedRoute） */}
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <Layout />
              </ProtectedRoute>
            }
          >
            {/* 默认重定向到上传页面 */}
            <Route index element={<DashboardPage />} />

            {/* 上传页面 */}
            <Route path="upload" element={<UploadPageWrapper />} />

            {/* 历史记录列表（简历库） */}
            <Route path="history" element={<HistoryListWrapper />} />

            {/* 简历详情 */}
            <Route path="history/:resumeId" element={<ResumeDetailWrapper />} />

            {/* 面试记录列表 */}
            <Route path="interviews" element={<InterviewHistoryWrapper />} />

            <Route path="interview-statistics" element={<InterviewStatisticsPage />} />

            <Route path="profile" element={<ProfilePage />} />

            <Route path="admin/users" element={<AdminRoute><AdminUsersPage /></AdminRoute>} />
            <Route path="admin/dify" element={<AdminRoute><DifyManagementPage /></AdminRoute>} />

            <Route path="practice" element={<PracticeCenterPage />} />
            <Route path="practice/:taskId" element={<PracticeSessionPage />} />
            <Route path="job-targets" element={<JobTargetsPage />} />

            {/* 模拟面试 */}
            <Route path="interview/:resumeId" element={<InterviewWrapper />} />

            {/* 知识库管理 */}
            <Route path="knowledgebase" element={<KnowledgeBaseManagePageWrapper />} />

            {/* 知识库上传 */}
            <Route path="knowledgebase/upload" element={<KnowledgeBaseUploadPageWrapper />} />

            {/* 问答助手（知识库聊天） */}
            <Route path="knowledgebase/chat" element={<KnowledgeBaseQueryPageWrapper />} />
          </Route>
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

// 面试记录页面包装器
function InterviewHistoryWrapper() {
  const navigate = useNavigate();
  const location = useLocation();
  const highlightSessionId = (location.state as { highlightSessionId?: string } | null)?.highlightSessionId;

  const handleBack = () => {
    navigate('/upload');
  };

  const handleViewInterview = async (sessionId: string, resumeId?: number) => {
    if (resumeId) {
      // 如果有简历ID，跳转到简历详情页的面试详情
      navigate(`/history/${resumeId}`, {
        state: { viewInterview: sessionId }
      });
    } else {
      // 否则尝试从面试详情中获取简历ID
      try {
        await historyApi.getInterviewDetail(sessionId);
        // 面试详情中没有简历ID，需要从其他地方获取
        // 暂时跳转到历史记录列表
        navigate('/history');
      } catch {
        navigate('/history');
      }
    }
  };

  return (
    <InterviewHistoryPage
      onBack={handleBack}
      onViewInterview={handleViewInterview}
      highlightSessionId={highlightSessionId}
    />
  );
}

// 知识库管理页面包装器
function KnowledgeBaseManagePageWrapper() {
  const navigate = useNavigate();

  const handleUpload = () => {
    navigate('/knowledgebase/upload');
  };

  const handleChat = () => {
    navigate('/knowledgebase/chat');
  };

  return <KnowledgeBaseManagePage onUpload={handleUpload} onChat={handleChat} />;
}

// 知识库问答页面包装器
function KnowledgeBaseQueryPageWrapper() {
  const navigate = useNavigate();
  const location = useLocation();
  const isChatMode = location.pathname === '/knowledgebase/chat';

  const handleBack = () => {
    if (isChatMode) {
      navigate('/knowledgebase');
    } else {
      navigate('/upload');
    }
  };

  const handleUpload = () => {
    navigate('/knowledgebase/upload');
  };

  return <KnowledgeBaseQueryPage onBack={handleBack} onUpload={handleUpload} />;
}

// 知识库上传页面包装器
function KnowledgeBaseUploadPageWrapper() {
  const navigate = useNavigate();

  const handleUploadComplete = (_result: UploadKnowledgeBaseResponse) => {
    // 上传完成后返回管理页面
    navigate('/knowledgebase');
  };

  const handleBack = () => {
    navigate('/knowledgebase');
  };

  return <KnowledgeBaseUploadPage onUploadComplete={handleUploadComplete} onBack={handleBack} />;
}

export default App;
