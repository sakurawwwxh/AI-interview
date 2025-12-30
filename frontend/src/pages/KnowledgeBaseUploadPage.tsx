import {ChangeEvent, DragEvent, useCallback, useState} from 'react';
import {motion} from 'framer-motion';
import {knowledgeBaseApi} from '../api/knowledgebase';
import type {UploadKnowledgeBaseResponse} from '../api/knowledgebase';

interface KnowledgeBaseUploadPageProps {
  onUploadComplete: (result: UploadKnowledgeBaseResponse) => void;
  onBack: () => void;
}

type UploadState = 'idle' | 'uploading' | 'error';

export default function KnowledgeBaseUploadPage({ onUploadComplete, onBack }: KnowledgeBaseUploadPageProps) {
  const [state, setState] = useState<UploadState>('idle');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [knowledgeBaseName, setKnowledgeBaseName] = useState('');
  const [dragOver, setDragOver] = useState(false);
  const [error, setError] = useState('');

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
      setError('');
    }
  }, []);

  const handleFileChange = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      setSelectedFile(files[0]);
      setError('');
    }
  }, []);

  const handleUpload = async () => {
    if (!selectedFile) return;
    setState('uploading');
    setError('');

    try {
      const data = await knowledgeBaseApi.uploadKnowledgeBase(
        selectedFile,
        knowledgeBaseName.trim() || undefined
      );
      onUploadComplete(data);
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : '上传失败，请重试';
      setError(errorMessage);
      setState('error');
    }
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  return (
    <motion.div 
      className="max-w-3xl mx-auto pt-16"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
    >
      {/* 标题 */}
      <div className="text-center mb-12">
        <motion.h1 
          className="text-4xl md:text-5xl font-bold text-slate-900 mb-4 tracking-tight"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
        >
          上传知识库
        </motion.h1>
        <motion.p 
          className="text-lg text-slate-500"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.2 }}
        >
          上传文档，AI 将基于知识库内容回答您的问题
        </motion.p>
      </div>

      {/* 上传区域 */}
      <motion.div
        className={`relative bg-white rounded-2xl p-12 cursor-pointer transition-all duration-300
          ${dragOver ? 'scale-[1.02] shadow-xl' : 'shadow-lg hover:shadow-xl'}`}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.3 }}
      >
        <input
          type="file"
          id="file-upload"
          className="hidden"
          accept=".pdf,.doc,.docx,.txt,.md"
          onChange={handleFileChange}
          disabled={state === 'uploading'}
        />
        
        <label htmlFor="file-upload" className="cursor-pointer">
          <div className="text-center">
            {selectedFile ? (
              <div className="space-y-4">
                <div className="w-20 h-20 mx-auto bg-primary-100 rounded-full flex items-center justify-center">
                  <svg className="w-10 h-10 text-primary-600" viewBox="0 0 24 24" fill="none">
                    <path d="M14 2H6C5.46957 2 4.96086 2.21071 4.58579 2.58579C4.21071 2.96086 4 3.46957 4 4V20C4 20.5304 4.21071 21.0391 4.58579 21.4142C4.96086 21.7893 5.46957 22 6 22H18C18.5304 22 19.0391 21.7893 19.4142 21.4142C19.7893 21.0391 20 20.5304 20 20V8L14 2Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M14 2V8H20" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </div>
                <div>
                  <p className="font-semibold text-slate-800">{selectedFile.name}</p>
                  <p className="text-sm text-slate-500 mt-1">{formatFileSize(selectedFile.size)}</p>
                </div>
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    setSelectedFile(null);
                  }}
                  className="text-sm text-red-500 hover:text-red-600"
                >
                  重新选择
                </button>
              </div>
            ) : (
              <div className="space-y-4">
                <div className="w-20 h-20 mx-auto bg-slate-100 rounded-full flex items-center justify-center">
                  <svg className="w-10 h-10 text-slate-400" viewBox="0 0 24 24" fill="none">
                    <path d="M21 15V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V15" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <polyline points="7,10 12,15 17,10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <line x1="12" y1="15" x2="12" y2="3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </div>
                <div>
                  <p className="font-semibold text-slate-800 mb-1">拖拽文件到此处或点击上传</p>
                  <p className="text-sm text-slate-500">支持 PDF、DOCX、DOC、TXT、MD 格式，最大 50MB</p>
                </div>
              </div>
            )}
          </div>
        </label>
      </motion.div>

      {/* 知识库名称输入 */}
      {selectedFile && (
        <motion.div
          className="mt-6 bg-white rounded-2xl p-6 shadow-lg"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <label className="block text-sm font-semibold text-slate-700 mb-2">
            知识库名称（可选）
          </label>
          <input
            type="text"
            value={knowledgeBaseName}
            onChange={(e) => setKnowledgeBaseName(e.target.value)}
            placeholder="留空则使用文件名"
            className="w-full px-4 py-3 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            disabled={state === 'uploading'}
          />
        </motion.div>
      )}

      {/* 错误提示 */}
      {error && (
        <motion.div
          className="mt-6 p-4 bg-red-50 border border-red-200 rounded-xl text-red-600 text-sm"
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
        >
          {error}
        </motion.div>
      )}

      {/* 操作按钮 */}
      <div className="mt-8 flex gap-4 justify-center">
        <motion.button
          onClick={onBack}
          className="px-6 py-3 border border-slate-200 rounded-xl text-slate-600 font-medium hover:bg-slate-50 transition-all"
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
        >
          返回
        </motion.button>
        <motion.button
          onClick={handleUpload}
          disabled={!selectedFile || state === 'uploading'}
          className="px-8 py-3 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-semibold shadow-lg shadow-primary-500/30 hover:shadow-xl transition-all disabled:opacity-60 disabled:cursor-not-allowed flex items-center gap-2"
          whileHover={{ scale: state === 'uploading' ? 1 : 1.02 }}
          whileTap={{ scale: state === 'uploading' ? 1 : 0.98 }}
        >
          {state === 'uploading' ? (
            <>
              <motion.span 
                className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full"
                animate={{ rotate: 360 }}
                transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
              />
              上传中...
            </>
          ) : (
            <>
              开始上传
            </>
          )}
        </motion.button>
      </div>
    </motion.div>
  );
}

