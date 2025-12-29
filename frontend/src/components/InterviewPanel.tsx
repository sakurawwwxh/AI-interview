import { useMemo } from 'react';
import { motion } from 'framer-motion';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { formatDateOnly } from '../utils/date';
import { getScoreColor } from '../utils/score';
import type { InterviewItem } from '../api/history';

interface InterviewPanelProps {
  interviews: InterviewItem[];
  onStartInterview: () => void;
  onViewInterview: (sessionId: string) => void;
  onExportInterview: (sessionId: string) => void;
  exporting: string | null;
  loadingInterview: boolean;
}

/**
 * 面试记录面板组件
 */
export default function InterviewPanel({
  interviews,
  onStartInterview,
  onViewInterview,
  onExportInterview,
  exporting,
  loadingInterview
}: InterviewPanelProps) {
  // 准备图表数据
  const chartData = useMemo(() => {
    return interviews
      .filter(i => i.overallScore !== null)
      .map((interview) => ({
        name: formatDateOnly(interview.createdAt),
        score: interview.overallScore || 0,
        index: interviews.length - interviews.indexOf(interview)
      }))
      .reverse();
  }, [interviews]);

  if (interviews.length === 0) {
    return (
      <div className="bg-white rounded-2xl p-12 text-center">
        <div className="w-16 h-16 mx-auto mb-6 bg-slate-100 rounded-full flex items-center justify-center">
          <svg className="w-8 h-8 text-slate-400" viewBox="0 0 24 24" fill="none">
            <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M19 10v2a7 7 0 0 1-14 0v-2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </div>
        <h3 className="text-xl font-semibold text-slate-700 mb-2">暂无面试记录</h3>
        <p className="text-slate-500 mb-6">开始模拟面试，获取专业评估</p>
        <motion.button
          onClick={onStartInterview}
          className="px-6 py-3 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-medium shadow-lg shadow-primary-500/30"
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
        >
          开始模拟面试
        </motion.button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 面试表现趋势图 */}
      {chartData.length > 0 && (
        <motion.div 
          className="bg-white rounded-2xl p-6"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-2">
              <svg className="w-5 h-5 text-primary-500" viewBox="0 0 24 24" fill="none">
                <path d="M23 6L13.5 15.5L8.5 10.5L1 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M17 6H23V12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              <span className="font-semibold text-slate-800">面试表现趋势</span>
            </div>
            <span className="text-sm text-slate-500">共 {chartData.length} 场练习</span>
          </div>
          
          <div className="h-48">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis 
                  dataKey="name" 
                  axisLine={false}
                  tickLine={false}
                  tick={{ fill: '#94a3b8', fontSize: 12 }}
                />
                <YAxis 
                  domain={[0, 100]}
                  axisLine={false}
                  tickLine={false}
                  tick={{ fill: '#94a3b8', fontSize: 12 }}
                />
                <Tooltip 
                  contentStyle={{ 
                    backgroundColor: '#fff', 
                    border: '1px solid #e2e8f0',
                    borderRadius: '12px',
                    boxShadow: '0 4px 12px rgba(0,0,0,0.1)'
                  }}
                  formatter={(value) => [`${value} 分`, '得分']}
                />
                <Line 
                  type="monotone" 
                  dataKey="score" 
                  stroke="#6366f1" 
                  strokeWidth={3}
                  dot={{ fill: '#6366f1', strokeWidth: 2, r: 5 }}
                  activeDot={{ r: 8, fill: '#6366f1' }}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </motion.div>
      )}

      {/* 历史面试场次 */}
      <motion.div 
        className="bg-white rounded-2xl p-6"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
      >
        <div className="flex items-center justify-between mb-6">
          <span className="font-semibold text-slate-800">历史面试场次</span>
        </div>

        <div className="space-y-4">
          {interviews.map((interview, index) => (
            <InterviewItemCard
              key={interview.id}
              interview={interview}
              index={index}
              total={interviews.length}
              exporting={exporting === interview.sessionId}
              onView={() => onViewInterview(interview.sessionId)}
              onExport={() => onExportInterview(interview.sessionId)}
            />
          ))}
        </div>

        {loadingInterview && (
          <div className="fixed inset-0 bg-black/20 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 flex items-center gap-4">
              <motion.div 
                className="w-8 h-8 border-3 border-slate-200 border-t-primary-500 rounded-full"
                animate={{ rotate: 360 }}
                transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
              />
              <span className="text-slate-600">加载面试详情...</span>
            </div>
          </div>
        )}
      </motion.div>
    </div>
  );
}

// 面试项卡片组件
function InterviewItemCard({
  interview,
  index,
  total,
  exporting,
  onView,
  onExport
}: {
  interview: InterviewItem;
  index: number;
  total: number;
  exporting: boolean;
  onView: () => void;
  onExport: () => void;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, x: -20 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay: index * 0.1 }}
      onClick={onView}
      className="flex items-center gap-4 p-4 bg-slate-50 rounded-xl hover:bg-slate-100 cursor-pointer transition-colors group"
    >
      {/* 得分 */}
      <div className={`w-14 h-14 rounded-full flex items-center justify-center font-bold text-lg ${
        interview.overallScore !== null 
          ? getScoreColor(interview.overallScore, [85, 70])
          : 'bg-slate-100 text-slate-400'
      }`}>
        {interview.overallScore ?? '-'}
      </div>

      {/* 信息 */}
      <div className="flex-1 min-w-0">
        <p className="font-medium text-slate-800 truncate">
          模拟面试 #{total - index}
        </p>
        <div className="flex items-center gap-4 text-sm text-slate-500">
          <span className="flex items-center gap-1">
            <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2" stroke="currentColor" strokeWidth="2"/>
              <line x1="16" y1="2" x2="16" y2="6" stroke="currentColor" strokeWidth="2"/>
              <line x1="8" y1="2" x2="8" y2="6" stroke="currentColor" strokeWidth="2"/>
              <line x1="3" y1="10" x2="21" y2="10" stroke="currentColor" strokeWidth="2"/>
            </svg>
            {formatDateOnly(interview.createdAt)}
          </span>
          <span className="flex items-center gap-1">
            <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none">
              <path d="M21 15C21 15.5304 20.7893 16.0391 20.4142 16.4142C20.0391 16.7893 19.5304 17 19 17H7L3 21V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H19C19.5304 3 20.0391 3.21071 20.4142 3.58579C20.7893 3.96086 21 4.46957 21 5V15Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            {interview.totalQuestions} 题
          </span>
        </div>
      </div>

      {/* 导出按钮 */}
      <motion.button
        onClick={(e) => { e.stopPropagation(); onExport(); }}
        disabled={exporting}
        className="px-3 py-2 text-slate-400 hover:text-primary-500 hover:bg-white rounded-lg transition-all opacity-0 group-hover:opacity-100"
        whileHover={{ scale: 1.05 }}
        whileTap={{ scale: 0.95 }}
      >
        <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
          <path d="M21 15V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V15" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          <polyline points="7,10 12,15 17,10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          <line x1="12" y1="15" x2="12" y2="3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
      </motion.button>

      {/* 箭头 */}
      <svg className="w-5 h-5 text-slate-300 group-hover:text-primary-500 group-hover:translate-x-1 transition-all flex-shrink-0" viewBox="0 0 24 24" fill="none">
        <polyline points="9,18 15,12 9,6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
      </svg>
    </motion.div>
  );
}

