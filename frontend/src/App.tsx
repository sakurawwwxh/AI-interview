import { useState, useCallback, DragEvent, ChangeEvent } from 'react';
import { resumeApi } from './api/resume';
import type { ResumeAnalysisResponse } from './types/resume';
import Interview from './components/Interview';
import './App.css';

type AppState = 'upload' | 'loading' | 'result' | 'error' | 'interview';

function App() {
  const [state, setState] = useState<AppState>('upload');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [result, setResult] = useState<ResumeAnalysisResponse | null>(null);
  const [error, setError] = useState<string>('');

  const handleDragOver = useCallback((e: DragEvent) => {
    e.preventDefault();
    setDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e: DragEvent) => {
    e.preventDefault();
    setDragOver(false);
  }, []);

  const handleDrop = useCallback((e: DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    const files = e.dataTransfer.files;
    if (files.length > 0) {
      setSelectedFile(files[0]);
    }
  }, []);

  const handleFileChange = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      setSelectedFile(files[0]);
    }
  }, []);

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  const handleAnalyze = async () => {
    if (!selectedFile) return;

    setState('loading');
    setError('');

    try {
      const data = await resumeApi.uploadAndAnalyze(selectedFile);
      setResult(data);
      setState('result');
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : '分析失败，请重试';
      setError(errorMessage);
      setState('error');
    }
  };

  const handleReset = () => {
    setSelectedFile(null);
    setResult(null);
    setError('');
    setState('upload');
  };

  const getPriorityClass = (priority: string): string => {
    switch (priority) {
      case '高': return 'priority-high';
      case '中': return 'priority-medium';
      case '低': return 'priority-low';
      default: return '';
    }
  };

  const scoreLabels = {
    contentScore: { name: '内容完整性', max: 25 },
    structureScore: { name: '结构清晰度', max: 20 },
    skillMatchScore: { name: '技能匹配度', max: 25 },
    expressionScore: { name: '表达专业性', max: 15 },
    projectScore: { name: '项目经验', max: 15 },
  };

  return (
    <div className="app">
      <div className="header">
        <h1>🤖 AI智能面试官</h1>
        <p>上传您的简历，获取专业的AI评分和改进建议</p>
      </div>

      <div className="card">
        {state === 'upload' && (
          <>
            <div
              className={`upload-area ${dragOver ? 'dragover' : ''} ${selectedFile ? 'has-file' : ''}`}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => document.getElementById('fileInput')?.click()}
            >
              <div className="upload-icon">📄</div>
              <h3>点击或拖拽上传简历</h3>
              <p>支持 PDF、DOCX、DOC、TXT 格式，最大10MB</p>
            </div>
            <input
              type="file"
              id="fileInput"
              accept=".pdf,.doc,.docx,.txt"
              onChange={handleFileChange}
              style={{ display: 'none' }}
            />
            {selectedFile && (
              <div className="file-info">
                📎 {selectedFile.name} ({formatFileSize(selectedFile.size)})
              </div>
            )}
            <div className="btn-wrapper">
              <button
                className="btn btn-primary"
                disabled={!selectedFile}
                onClick={handleAnalyze}
              >
                开始分析
              </button>
            </div>
          </>
        )}

        {state === 'loading' && (
          <div className="loading">
            <div className="spinner"></div>
            <p>AI正在分析您的简历，请稍候...</p>
            <p style={{ fontSize: '12px', color: '#999', marginTop: '10px' }}>
              首次分析可能需要30秒左右
            </p>
          </div>
        )}

        {state === 'error' && (
          <div className="error-message">
            <p>{error || '分析过程中出现错误'}</p>
            <button className="btn btn-primary" style={{ marginTop: '15px' }} onClick={handleReset}>
              重新上传
            </button>
          </div>
        )}
      </div>

      {state === 'result' && result && (
        <div className="card">
          <div className="score-circle">
            <span className="score">{result.overallScore}</span>
          </div>
          <div className="score-label">综合评分（满分100分）</div>

          <div className="detail-scores">
            {Object.entries(scoreLabels).map(([key, config]) => (
              <div className="score-item" key={key}>
                <div className="label">{config.name}</div>
                <div className="value">
                  {result.scoreDetail[key as keyof typeof result.scoreDetail] || 0}/{config.max}
                </div>
              </div>
            ))}
          </div>

          <div className="summary">
            <div className="section-title">📝 简历总评</div>
            <p>{result.summary}</p>
          </div>

          <div className="strengths">
            <div className="section-title">✨ 亮点优势</div>
            {result.strengths.map((strength, index) => (
              <div className="strength-item" key={index}>
                <span className="strength-icon">✓</span>
                <span>{strength}</span>
              </div>
            ))}
          </div>

          <div className="suggestions">
            <div className="section-title">💡 改进建议</div>
            {result.suggestions.map((suggestion, index) => (
              <div className="suggestion-item" key={index}>
                <div className="suggestion-header">
                  <span className="suggestion-category">{suggestion.category}</span>
                  <span className={`suggestion-priority ${getPriorityClass(suggestion.priority)}`}>
                    {suggestion.priority}优先级
                  </span>
                </div>
                <div className="suggestion-issue">⚠️ {suggestion.issue}</div>
                <div className="suggestion-recommendation">💡 {suggestion.recommendation}</div>
              </div>
            ))}
          </div>

          <div className="btn-wrapper" style={{ display: 'flex', gap: '15px', justifyContent: 'center' }}>
            <button className="btn btn-primary" onClick={handleReset}>
              分析另一份简历
            </button>
            <button 
              className="btn btn-primary" 
              style={{ background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)' }}
              onClick={() => setState('interview')}
            >
              🎙️ 开始模拟面试
            </button>
          </div>
        </div>
      )}

      {state === 'interview' && result && (
        <Interview 
          resumeText={result.originalText} 
          onBack={handleReset}
        />
      )}
    </div>
  );
}

export default App;
