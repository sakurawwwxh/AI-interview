import {useEffect, useState, useRef, useTransition, useMemo} from 'react';
import {motion, AnimatePresence} from 'framer-motion';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import {knowledgeBaseApi, type KnowledgeBaseItem, type SortOption} from '../api/knowledgebase';
import {ragChatApi, type RagChatSessionListItem} from '../api/ragChat';
import {formatDateOnly} from '../utils/date';
import ConfirmDialog from '../components/ConfirmDialog';

interface KnowledgeBaseQueryPageProps {
  onBack: () => void;
  onUpload: () => void;
}

interface Message {
  id?: number;
  type: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

// 分类分组结构
interface CategoryGroup {
  name: string;
  items: KnowledgeBaseItem[];
  isExpanded: boolean;
}

export default function KnowledgeBaseQueryPage({ onBack, onUpload }: KnowledgeBaseQueryPageProps) {
  // 知识库状态
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseItem[]>([]);
  const [selectedKbIds, setSelectedKbIds] = useState<Set<number>>(new Set());
  const [loadingList, setLoadingList] = useState(true);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<{ id: number; name: string } | null>(null);

  // 搜索和排序状态
  const [searchKeyword, setSearchKeyword] = useState('');
  const [sortBy, setSortBy] = useState<SortOption>('time');
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(new Set(['未分类']));

  // 会话状态
  const [sessions, setSessions] = useState<RagChatSessionListItem[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [currentSessionTitle, setCurrentSessionTitle] = useState<string>('');
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [sessionDeleteConfirm, setSessionDeleteConfirm] = useState<{ id: number; title: string } | null>(null);

  // 消息状态
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(false);

  // refs
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const isUserScrollingRef = useRef(false);
  const rafRef = useRef<number>();
  const scrollTimeoutRef = useRef<NodeJS.Timeout>();

  const [isPending, startTransition] = useTransition();

  useEffect(() => {
    loadKnowledgeBases();
    loadSessions();
  }, []);

  // 排序变化时重新加载
  useEffect(() => {
    if (!searchKeyword) {
      loadKnowledgeBases();
    }
  }, [sortBy]);

  // 智能滚动检测
  useEffect(() => {
    const container = messagesContainerRef.current;
    if (!container) return;

    const handleScroll = () => {
      const { scrollTop, scrollHeight, clientHeight } = container;
      const isNearBottom = scrollHeight - scrollTop - clientHeight < 100;
      isUserScrollingRef.current = !isNearBottom;

      if (isNearBottom) {
        clearTimeout(scrollTimeoutRef.current);
        scrollTimeoutRef.current = setTimeout(() => {
          isUserScrollingRef.current = false;
        }, 1000);
      }
    };

    container.addEventListener('scroll', handleScroll, { passive: true });
    return () => {
      container.removeEventListener('scroll', handleScroll);
      if (scrollTimeoutRef.current) {
        clearTimeout(scrollTimeoutRef.current);
      }
    };
  }, []);

  // 智能滚动到底部
  useEffect(() => {
    if (!isUserScrollingRef.current && !isPending) {
      requestAnimationFrame(() => {
        messagesEndRef.current?.scrollIntoView({
          behavior: 'smooth',
          block: 'end'
        });
      });
    }
  }, [messages, isPending]);

  const loadKnowledgeBases = async () => {
    setLoadingList(true);
    try {
      const list = await knowledgeBaseApi.getAllKnowledgeBases(sortBy);
      setKnowledgeBases(list);
    } catch (err) {
      console.error('加载知识库列表失败', err);
    } finally {
      setLoadingList(false);
    }
  };

  // 搜索知识库
  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      loadKnowledgeBases();
      return;
    }
    setLoadingList(true);
    try {
      const list = await knowledgeBaseApi.search(searchKeyword.trim());
      setKnowledgeBases(list);
    } catch (err) {
      console.error('搜索知识库失败', err);
    } finally {
      setLoadingList(false);
    }
  };

  // 按分类分组知识库
  const groupedKnowledgeBases = useMemo((): CategoryGroup[] => {
    const groups: Map<string, KnowledgeBaseItem[]> = new Map();

    knowledgeBases.forEach(kb => {
      const category = kb.category || '未分类';
      if (!groups.has(category)) {
        groups.set(category, []);
      }
      groups.get(category)!.push(kb);
    });

    // 转换为数组并排序（未分类放最后）
    const result: CategoryGroup[] = [];
    const sortedCategories = Array.from(groups.keys()).sort((a, b) => {
      if (a === '未分类') return 1;
      if (b === '未分类') return -1;
      return a.localeCompare(b);
    });

    sortedCategories.forEach(name => {
      result.push({
        name,
        items: groups.get(name)!,
        isExpanded: expandedCategories.has(name),
      });
    });

    return result;
  }, [knowledgeBases, expandedCategories]);

  // 切换分类展开状态
  const toggleCategory = (category: string) => {
    setExpandedCategories(prev => {
      const next = new Set(prev);
      if (next.has(category)) {
        next.delete(category);
      } else {
        next.add(category);
      }
      return next;
    });
  };

  const loadSessions = async () => {
    setLoadingSessions(true);
    try {
      const list = await ragChatApi.listSessions();
      setSessions(list);
    } catch (err) {
      console.error('加载会话列表失败', err);
    } finally {
      setLoadingSessions(false);
    }
  };

  const handleToggleKb = (kbId: number) => {
    setSelectedKbIds(prev => {
      const newSet = new Set(prev);
      if (newSet.has(kbId)) {
        newSet.delete(kbId);
      } else {
        newSet.add(kbId);
      }
      // 切换知识库时重置当前会话
      if (newSet.size !== prev.size && currentSessionId) {
        setCurrentSessionId(null);
        setCurrentSessionTitle('');
        setMessages([]);
      }
      return newSet;
    });
  };

  const handleNewSession = () => {
    setCurrentSessionId(null);
    setCurrentSessionTitle('');
    setMessages([]);
  };

  const handleLoadSession = async (sessionId: number) => {
    try {
      const detail = await ragChatApi.getSessionDetail(sessionId);
      setCurrentSessionId(detail.id);
      setCurrentSessionTitle(detail.title);
      setSelectedKbIds(new Set(detail.knowledgeBases.map(kb => kb.id)));
      setMessages(detail.messages.map(m => ({
        id: m.id,
        type: m.type,
        content: m.content,
        timestamp: new Date(m.createdAt),
      })));
    } catch (err) {
      console.error('加载会话失败', err);
    }
  };

  const handleDeleteSession = async () => {
    if (!sessionDeleteConfirm) return;
    try {
      await ragChatApi.deleteSession(sessionDeleteConfirm.id);
      await loadSessions();
      if (currentSessionId === sessionDeleteConfirm.id) {
        handleNewSession();
      }
      setSessionDeleteConfirm(null);
    } catch (err) {
      console.error('删除会话失败', err);
    }
  };

  const formatMarkdown = (text: string): string => {
    if (!text) return '';

    return text
      .replace(/\\n/g, '\n')
      .replace(/^(#{1,6})([^\s#\n])/gm, '$1 $2')
      .replace(/(^|\n)(\s*\d+)\.(?=\S)/g, '$1$2. ')
      .replace(/(^|\n)(\s*[-*])(?=\S)/g, '$1$2 ')
      .replace(/([^\n])\s*(\d+\.\s+)/g, '$1\n\n$2')
      .replace(/([。！？）:：])\s*([-*])\s*/g, '$1\n\n$2 ')
      .replace(/([^\n])\s+([-*])\s+/g, '$1\n\n$2 ')
      .replace(/\*\*：/g, '**： ')
      .replace(/([^\n])\s*(#{1,6}\s+[^\n]+)/g, '$1\n\n$2')
      .replace(/\n{3,}/g, '\n\n');
  };

  const handleSubmitQuestion = async () => {
    if (!question.trim() || selectedKbIds.size === 0 || loading) return;

    const userQuestion = question.trim();
    setQuestion('');
    setLoading(true);

    // 如果没有当前会话，先创建
    let sessionId = currentSessionId;
    if (!sessionId) {
      try {
        const session = await ragChatApi.createSession(Array.from(selectedKbIds));
        sessionId = session.id;
        setCurrentSessionId(sessionId);
        setCurrentSessionTitle(session.title);
      } catch (err) {
        console.error('创建会话失败', err);
        setLoading(false);
        return;
      }
    }

    // 添加用户消息
    const userMessage: Message = {
      type: 'user',
      content: userQuestion,
      timestamp: new Date(),
    };
    setMessages(prev => [...prev, userMessage]);

    // 创建助手消息占位
    const assistantMessage: Message = {
      type: 'assistant',
      content: '',
      timestamp: new Date(),
    };
    setMessages(prev => [...prev, assistantMessage]);

    let fullContent = '';
    const updateAssistantMessage = (content: string) => {
      setMessages(prev => {
        const newMessages = [...prev];
        const lastIndex = newMessages.length - 1;
        if (lastIndex >= 0 && newMessages[lastIndex].type === 'assistant') {
          newMessages[lastIndex] = {
            ...newMessages[lastIndex],
            content: content,
          };
        }
        return newMessages;
      });
    };

    try {
      await ragChatApi.sendMessageStream(
        sessionId,
        userQuestion,
        (chunk: string) => {
          fullContent += chunk;
          if (rafRef.current) {
            cancelAnimationFrame(rafRef.current);
          }
          rafRef.current = requestAnimationFrame(() => {
            startTransition(() => {
              updateAssistantMessage(fullContent);
            });
          });
        },
        () => {
          setLoading(false);
          loadSessions(); // 刷新会话列表
          setTimeout(() => {
            isUserScrollingRef.current = false;
            messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
          }, 100);
        },
        (error: Error) => {
          console.error('流式查询失败:', error);
          updateAssistantMessage(fullContent || error.message || '回答失败，请重试');
          setLoading(false);
        }
      );
    } catch (err) {
      console.error('发起流式查询失败:', err);
      updateAssistantMessage(err instanceof Error ? err.message : '回答失败，请重试');
      setLoading(false);
    }
  };

  const handleDeleteKbClick = (id: number, name: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setDeleteConfirm({ id, name });
  };

  const handleDeleteKbConfirm = async () => {
    if (!deleteConfirm) return;

    const { id } = deleteConfirm;
    setDeletingId(id);
    try {
      await knowledgeBaseApi.deleteKnowledgeBase(id);
      await loadKnowledgeBases();
      if (selectedKbIds.has(id)) {
        setSelectedKbIds(prev => {
          const newSet = new Set(prev);
          newSet.delete(id);
          return newSet;
        });
        setMessages([]);
      }
      setDeleteConfirm(null);
    } catch (err) {
      alert(err instanceof Error ? err.message : '删除失败，请稍后重试');
    } finally {
      setDeletingId(null);
    }
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  const formatTimeAgo = (dateStr: string): string => {
    const date = new Date(dateStr);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return '刚刚';
    if (minutes < 60) return `${minutes} 分钟前`;
    if (hours < 24) return `${hours} 小时前`;
    if (days < 7) return `${days} 天前`;
    return formatDateOnly(dateStr);
  };

  return (
    <div className="max-w-7xl mx-auto pt-8 pb-10">
      {/* 头部 */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-3xl font-bold text-slate-900 mb-2">知识库问答</h1>
          <p className="text-slate-500">选择知识库，向 AI 提问</p>
        </div>
        <div className="flex gap-3">
          <motion.button
            onClick={onUpload}
            className="px-5 py-2.5 border border-slate-200 rounded-xl text-slate-600 font-medium hover:bg-slate-50 transition-all"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
          >
            上传知识库
          </motion.button>
          <motion.button
            onClick={onBack}
            className="px-5 py-2.5 border border-slate-200 rounded-xl text-slate-600 font-medium hover:bg-slate-50 transition-all"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
          >
            返回
          </motion.button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* 左侧：知识库列表 + 会话历史 */}
        <div className="lg:col-span-1 space-y-6">
          {/* 知识库列表 */}
          <div className="bg-white rounded-2xl p-5 shadow-sm">
            <h2 className="text-lg font-semibold text-slate-800 mb-4">知识库</h2>

            {/* 搜索框 */}
            <div className="flex gap-2 mb-3">
              <input
                type="text"
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
                placeholder="搜索知识库..."
                className="flex-1 px-3 py-1.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-primary-500"
              />
              <button
                onClick={handleSearch}
                className="px-3 py-1.5 text-sm bg-primary-500 text-white rounded-lg hover:bg-primary-600"
              >
                搜索
              </button>
            </div>

            {/* 排序选择 */}
            <div className="flex items-center gap-2 mb-3">
              <span className="text-xs text-slate-500">排序:</span>
              <select
                value={sortBy}
                onChange={(e) => {
                  setSortBy(e.target.value as SortOption);
                  setSearchKeyword('');
                }}
                className="flex-1 px-2 py-1 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-primary-500"
              >
                <option value="time">上传时间</option>
                <option value="size">文件大小</option>
                <option value="access">访问次数</option>
                <option value="question">提问次数</option>
              </select>
            </div>

            {loadingList ? (
              <div className="text-center py-6">
                <motion.div
                  className="w-6 h-6 border-2 border-primary-500 border-t-transparent rounded-full mx-auto"
                  animate={{ rotate: 360 }}
                  transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
                />
              </div>
            ) : knowledgeBases.length === 0 ? (
              <div className="text-center py-6 text-slate-500">
                <p className="mb-3 text-sm">{searchKeyword ? '未找到匹配的知识库' : '暂无知识库'}</p>
                {!searchKeyword && (
                  <button onClick={onUpload} className="text-primary-500 hover:text-primary-600 font-medium text-sm">
                    立即上传
                  </button>
                )}
              </div>
            ) : (
              <div className="space-y-2 max-h-80 overflow-y-auto">
                {groupedKnowledgeBases.map((group) => (
                  <div key={group.name} className="border border-slate-100 rounded-lg overflow-hidden">
                    {/* 分类标题 */}
                    <button
                      onClick={() => toggleCategory(group.name)}
                      className="w-full flex items-center justify-between px-3 py-2 bg-slate-50 hover:bg-slate-100 transition-colors"
                    >
                      <div className="flex items-center gap-2">
                        <svg
                          className={`w-4 h-4 text-slate-400 transition-transform ${group.isExpanded ? 'rotate-90' : ''}`}
                          viewBox="0 0 24 24"
                          fill="none"
                        >
                          <path d="M9 18L15 12L9 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                        <span className="font-medium text-slate-700 text-sm">{group.name}</span>
                      </div>
                      <span className="text-xs text-slate-400">{group.items.length}</span>
                    </button>

                    {/* 分类下的知识库列表 */}
                    <AnimatePresence>
                      {group.isExpanded && (
                        <motion.div
                          initial={{ height: 0, opacity: 0 }}
                          animate={{ height: 'auto', opacity: 1 }}
                          exit={{ height: 0, opacity: 0 }}
                          transition={{ duration: 0.2 }}
                          className="overflow-hidden"
                        >
                          <div className="p-2 space-y-1">
                            {group.items.map((kb) => (
                              <div
                                key={kb.id}
                                onClick={() => handleToggleKb(kb.id)}
                                className={`p-2 rounded-lg cursor-pointer transition-all ${
                                  selectedKbIds.has(kb.id)
                                    ? 'bg-primary-50 border border-primary-500'
                                    : 'bg-white hover:bg-slate-50 border border-transparent'
                                }`}
                              >
                                <div className="flex items-center justify-between gap-2">
                                  <div className="flex items-center gap-2 flex-1 min-w-0">
                                    <input
                                      type="checkbox"
                                      checked={selectedKbIds.has(kb.id)}
                                      onChange={() => handleToggleKb(kb.id)}
                                      onClick={(e) => e.stopPropagation()}
                                      className="w-4 h-4 text-primary-500 rounded focus:ring-primary-500"
                                    />
                                    <span className="font-medium text-slate-800 text-xs truncate">{kb.name}</span>
                                  </div>
                                  <button
                                    onClick={(e) => handleDeleteKbClick(kb.id, kb.name, e)}
                                    disabled={deletingId === kb.id}
                                    className="p-1 text-slate-400 hover:text-red-500 rounded transition-colors"
                                  >
                                    <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none">
                                      <path d="M3 6H5H21M8 6V4C8 3.46957 8.21071 2.96086 8.58579 2.58579C8.96086 2.21071 9.46957 2 10 2H14C14.5304 2 15.0391 2.21071 15.4142 2.58579C15.7893 2.96086 16 3.46957 16 4V6M19 6V20C19 20.5304 18.7893 21.0391 18.4142 21.4142C18.0391 21.7893 17.5304 22 17 22H7C6.46957 22 5.96086 21.7893 5.58579 21.4142C5.21071 21.0391 5 20.5304 5 20V6H19Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                                    </svg>
                                  </button>
                                </div>
                                <p className="text-xs text-slate-400 mt-0.5 ml-6">{formatFileSize(kb.fileSize)}</p>
                              </div>
                            ))}
                          </div>
                        </motion.div>
                      )}
                    </AnimatePresence>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* 会话历史 */}
          <div className="bg-white rounded-2xl p-5 shadow-sm">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-slate-800">对话历史</h2>
              <motion.button
                onClick={handleNewSession}
                disabled={selectedKbIds.size === 0}
                className="p-2 text-primary-500 hover:bg-primary-50 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                title="新建对话"
              >
                <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
                  <path d="M12 5V19M5 12H19" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </motion.button>
            </div>

            {loadingSessions ? (
              <div className="text-center py-6">
                <motion.div
                  className="w-6 h-6 border-2 border-primary-500 border-t-transparent rounded-full mx-auto"
                  animate={{ rotate: 360 }}
                  transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
                />
              </div>
            ) : sessions.length === 0 ? (
              <div className="text-center py-6 text-slate-400 text-sm">
                暂无对话历史
              </div>
            ) : (
              <div className="space-y-2 max-h-72 overflow-y-auto">
                {sessions.map((session) => (
                  <div
                    key={session.id}
                    onClick={() => handleLoadSession(session.id)}
                    className={`p-3 rounded-lg cursor-pointer transition-all group ${
                      currentSessionId === session.id
                        ? 'bg-primary-50 border border-primary-500'
                        : 'bg-slate-50 hover:bg-slate-100 border border-transparent'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex-1 min-w-0">
                        <p className="font-medium text-slate-800 text-sm truncate">{session.title}</p>
                        <p className="text-xs text-slate-500 mt-1">
                          {session.messageCount} 条消息 · {formatTimeAgo(session.updatedAt)}
                        </p>
                      </div>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setSessionDeleteConfirm({ id: session.id, title: session.title });
                        }}
                        className="p-1 text-slate-400 hover:text-red-500 rounded opacity-0 group-hover:opacity-100 transition-all"
                      >
                        <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none">
                          <path d="M3 6H5H21M8 6V4C8 3.46957 8.21071 2.96086 8.58579 2.58579C8.96086 2.21071 9.46957 2 10 2H14C14.5304 2 15.0391 2.21071 15.4142 2.58579C15.7893 2.96086 16 3.46957 16 4V6M19 6V20C19 20.5304 18.7893 21.0391 18.4142 21.4142C18.0391 21.7893 17.5304 22 17 22H7C6.46957 22 5.96086 21.7893 5.58579 21.4142C5.21071 21.0391 5 20.5304 5 20V6H19Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* 右侧：问答区域 */}
        <div className="lg:col-span-3">
          <div className="bg-white rounded-2xl shadow-sm flex flex-col h-[calc(100vh-12rem)]">
            {selectedKbIds.size > 0 ? (
              <>
                {/* 会话信息 */}
                <div className="p-5 border-b border-slate-200">
                  <h2 className="text-lg font-semibold text-slate-800">
                    {currentSessionTitle || (selectedKbIds.size === 1
                      ? knowledgeBases.find(kb => kb.id === Array.from(selectedKbIds)[0])?.name || '新对话'
                      : `${selectedKbIds.size} 个知识库 - 新对话`)}
                  </h2>
                  <div className="flex flex-wrap gap-2 mt-2">
                    {Array.from(selectedKbIds).map(kbId => {
                      const kb = knowledgeBases.find(k => k.id === kbId);
                      return kb ? (
                        <span key={kbId} className="px-2 py-1 bg-primary-50 text-primary-600 text-xs rounded-full">
                          {kb.name}
                        </span>
                      ) : null;
                    })}
                  </div>
                </div>

                {/* 消息列表 */}
                <div
                  ref={messagesContainerRef}
                  className="flex-1 overflow-y-auto p-5 space-y-4"
                >
                  {messages.length === 0 ? (
                    <div className="text-center py-12 text-slate-400">
                      <svg className="w-16 h-16 mx-auto mb-4 opacity-50" viewBox="0 0 24 24" fill="none">
                        <path d="M21 15C21 15.5304 20.7893 16.0391 20.4142 16.4142C20.0391 16.7893 19.5304 17 19 17H7L3 21V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H19C19.5304 3 20.0391 3.21071 20.4142 3.58579C20.7893 3.96086 21 4.46957 21 5V15Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                      </svg>
                      <p>开始提问吧！</p>
                    </div>
                  ) : (
                    <AnimatePresence>
                      {messages.map((msg, index) => (
                        <motion.div
                          key={index}
                          initial={{ opacity: 0, y: 10 }}
                          animate={{ opacity: 1, y: 0 }}
                          className={`flex ${msg.type === 'user' ? 'justify-end' : 'justify-start'}`}
                        >
                          <div
                            className={`max-w-[85%] rounded-2xl p-4 shadow-sm ${
                              msg.type === 'user'
                                ? 'bg-primary-600 text-white'
                                : 'bg-white border border-slate-100 text-slate-800'
                            }`}
                          >
                            {msg.type === 'user' ? (
                              <p className="whitespace-pre-wrap leading-relaxed">{msg.content}</p>
                            ) : (
                              <div className="prose prose-slate max-w-none
                                prose-headings:text-slate-900 prose-headings:font-bold prose-headings:mb-3 prose-headings:mt-6
                                prose-p:leading-7 prose-p:text-slate-700 prose-p:mb-4
                                prose-strong:text-slate-900 prose-strong:font-bold
                                prose-ul:my-4 prose-ol:my-4
                                prose-li:my-2 prose-li:leading-7
                                prose-code:bg-slate-100 prose-code:text-primary-600 prose-code:px-1.5 prose-code:py-0.5 prose-code:rounded-md prose-code:before:content-none prose-code:after:content-none
                                marker:text-primary-500 marker:font-bold">
                                <ReactMarkdown remarkPlugins={[remarkGfm, remarkBreaks]}>
                                  {formatMarkdown(msg.content)}
                                </ReactMarkdown>
                                {loading && index === messages.length - 1 && (
                                  <span className="inline-block w-0.5 h-5 bg-primary-500 ml-1 animate-pulse" />
                                )}
                              </div>
                            )}
                          </div>
                        </motion.div>
                      ))}
                    </AnimatePresence>
                  )}
                  <div ref={messagesEndRef} />
                </div>

                {/* 输入区域 */}
                <div className="p-5 border-t border-slate-200">
                  <div className="flex gap-3">
                    <input
                      type="text"
                      value={question}
                      onChange={(e) => setQuestion(e.target.value)}
                      onKeyPress={(e) => e.key === 'Enter' && !e.shiftKey && handleSubmitQuestion()}
                      placeholder="输入您的问题..."
                      className="flex-1 px-4 py-3 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                      disabled={loading}
                    />
                    <motion.button
                      onClick={handleSubmitQuestion}
                      disabled={!question.trim() || selectedKbIds.size === 0 || loading}
                      className="px-6 py-3 bg-primary-500 text-white rounded-xl font-medium hover:bg-primary-600 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                      whileHover={{ scale: loading ? 1 : 1.02 }}
                      whileTap={{ scale: loading ? 1 : 0.98 }}
                    >
                      发送
                    </motion.button>
                  </div>
                </div>
              </>
            ) : (
              <div className="flex-1 flex items-center justify-center text-slate-400">
                <div className="text-center">
                  <svg className="w-16 h-16 mx-auto mb-4 opacity-50" viewBox="0 0 24 24" fill="none">
                    <path d="M21 15C21 15.5304 20.7893 16.0391 20.4142 16.4142C20.0391 16.7893 19.5304 17 19 17H7L3 21V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H19C19.5304 3 20.0391 3.21071 20.4142 3.58579C20.7893 3.96086 21 4.46957 21 5V15Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                  <p>请先选择一个知识库</p>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* 知识库删除确认对话框 */}
      <ConfirmDialog
        open={deleteConfirm !== null}
        title="删除知识库"
        message={deleteConfirm ? `确定要删除知识库"${deleteConfirm.name}"吗？删除后无法恢复。` : ''}
        confirmText="确定删除"
        cancelText="取消"
        confirmVariant="danger"
        loading={deletingId !== null}
        onConfirm={handleDeleteKbConfirm}
        onCancel={() => setDeleteConfirm(null)}
      />

      {/* 会话删除确认对话框 */}
      <ConfirmDialog
        open={sessionDeleteConfirm !== null}
        title="删除对话"
        message={sessionDeleteConfirm ? `确定要删除对话"${sessionDeleteConfirm.title}"吗？删除后无法恢复。` : ''}
        confirmText="确定删除"
        cancelText="取消"
        confirmVariant="danger"
        loading={false}
        onConfirm={handleDeleteSession}
        onCancel={() => setSessionDeleteConfirm(null)}
      />
    </div>
  );
}
