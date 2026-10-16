import { useState } from 'react';
import { knowledgeBaseApi } from '../api/knowledgebase';
import type { UploadKnowledgeBaseResponse } from '../api/knowledgebase';
import FileUploadCard from '../components/FileUploadCard';

interface KnowledgeBaseUploadPageProps {
  onUploadComplete: (result: UploadKnowledgeBaseResponse) => void;
  onBack: () => void;
}

export default function KnowledgeBaseUploadPage({ onUploadComplete, onBack }: KnowledgeBaseUploadPageProps) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');

  const handleUpload = async (file: File, name?: string) => {
    setUploading(true);
    setError('');

    try {
      const data = await knowledgeBaseApi.uploadKnowledgeBase(file, name);
      onUploadComplete(data);
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : '上传失败，请重试';
      setError(errorMessage);
      setUploading(false);
    }
  };

  return (
    <FileUploadCard
      title="上传知识库"
      subtitle="上传文档，AI 将基于知识库内容回答您的问题"
      accept=".pdf,.doc,.docx,.txt,.md"
      formatHint="支持 PDF、DOCX、DOC、TXT、MD"
      maxSizeHint="最大 50MB"
      uploading={uploading}
      uploadButtonText="开始上传"
      selectButtonText="选择文件"
      showNameInput={true}
      nameLabel="知识库名称（可选）"
      namePlaceholder="留空则使用文件名"
      steps={[
        ['01', '上传文档', '原文保存到对象存储'],
        ['02', '切片与向量化', '按语义分块并写入向量索引'],
        ['03', '开始问答', '在知识问答里带来源检索作答'],
      ]}
      footnote="文件仅用于知识库检索与问答，不会用于其它用途。上传成功后可在知识库管理中继续维护。"
      error={error}
      onUpload={handleUpload}
      onBack={onBack}
    />
  );
}
