import { useState } from 'react';
import Layout from './components/Layout';
import UploadPage from './components/UploadPage';
import HistoryList from './components/HistoryList';
import ResumeDetailPage from './components/ResumeDetailPage';
import Interview from './components/Interview';
import type { ResumeAnalysisResponse, StorageInfo } from './types/resume';

type PageType = 'upload' | 'history';
type ViewType = 'main' | 'result' | 'detail' | 'interview';

function App() {
  const [currentPage, setCurrentPage] = useState<PageType>('upload');
  const [currentView, setCurrentView] = useState<ViewType>('main');
  const [analysisResult, setAnalysisResult] = useState<ResumeAnalysisResponse | null>(null);
  const [storageInfo, setStorageInfo] = useState<StorageInfo | null>(null);
  const [selectedResumeId, setSelectedResumeId] = useState<number | null>(null);
  const [interviewResumeText, setInterviewResumeText] = useState<string>('');
  const [interviewResumeId, setInterviewResumeId] = useState<number | undefined>(undefined);

  const handleNavigate = (page: PageType) => {
    setCurrentPage(page);
    setCurrentView('main');
    setSelectedResumeId(null);
  };

  const handleAnalysisComplete = (result: ResumeAnalysisResponse, storage: StorageInfo) => {
    setAnalysisResult(result);
    setStorageInfo(storage);
    // 直接跳转到详情页
    if (storage.resumeId) {
      setSelectedResumeId(storage.resumeId);
      setCurrentPage('history');
      setCurrentView('detail');
    }
  };

  const handleSelectResume = (id: number) => {
    setSelectedResumeId(id);
    setCurrentView('detail');
  };

  const handleBackToList = () => {
    setCurrentView('main');
    setSelectedResumeId(null);
  };

  const handleStartInterview = (resumeText: string, resumeId: number) => {
    setInterviewResumeText(resumeText);
    setInterviewResumeId(resumeId);
    setCurrentView('interview');
  };

  const handleInterviewBack = () => {
    if (selectedResumeId) {
      setCurrentView('detail');
    } else {
      setCurrentView('main');
      setCurrentPage('upload');
    }
  };

  // 渲染上传页面内容
  const renderUploadContent = () => {
    if (currentView === 'interview') {
      return (
        <Interview
          resumeText={interviewResumeText || analysisResult?.originalText || ''}
          resumeId={interviewResumeId || storageInfo?.resumeId}
          onBack={handleInterviewBack}
        />
      );
    }
    
    return (
      <UploadPage onAnalysisComplete={handleAnalysisComplete} />
    );
  };

  // 渲染历史记录页面内容
  const renderHistoryContent = () => {
    if (currentView === 'interview') {
      return (
        <Interview
          resumeText={interviewResumeText}
          resumeId={interviewResumeId}
          onBack={handleInterviewBack}
        />
      );
    }

    if (currentView === 'detail' && selectedResumeId) {
      return (
        <ResumeDetailPage
          resumeId={selectedResumeId}
          onBack={handleBackToList}
          onStartInterview={handleStartInterview}
        />
      );
    }

    return (
      <HistoryList onSelectResume={handleSelectResume} />
    );
  };

  return (
    <Layout currentPage={currentPage} onNavigate={handleNavigate}>
      {currentPage === 'upload' && renderUploadContent()}
      {currentPage === 'history' && renderHistoryContent()}
    </Layout>
  );
}

export default App;
