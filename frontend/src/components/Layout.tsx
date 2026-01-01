import {Link, Outlet, useLocation} from 'react-router-dom';
import {motion} from 'framer-motion';
import {
  Layers,
  Upload,
  BookOpen,
  Briefcase,
  Users,
  FileText,
  MessageSquare
} from 'lucide-react';

// Layout不再需要children prop，使用Outlet渲染子路由
interface LayoutProps {}

interface NavItem {
  id: string;
  path: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
}

interface NavGroup {
  id: string;
  title: string;
  icon: React.ComponentType<{ className?: string }>;
  items: NavItem[];
}

export default function Layout({}: LayoutProps) {
  const location = useLocation();
  const currentPath = location.pathname;
  
  // 按业务模块组织的导航项
  const navGroups: NavGroup[] = [
    {
      id: 'career',
      title: '简历与面试',
      icon: Briefcase,
      items: [
        { id: 'upload', path: '/upload', label: '简历分析', icon: Upload },
        { id: 'interviews', path: '/interviews', label: '面试记录', icon: Users },
        { id: 'resumes', path: '/history', label: '简历库', icon: FileText },
      ],
    },
    {
      id: 'knowledge',
      title: '知识增强',
      icon: Layers,
      items: [
        { id: 'knowledgebase', path: '/knowledgebase/upload', label: '知识库上传', icon: BookOpen },
        { id: 'chat', path: '/knowledgebase/chat', label: '问答助手', icon: MessageSquare },
      ],
    },
  ];

  // 判断当前页面是否匹配导航项
  const isActive = (path: string) => {
    if (path === '/upload') {
      return currentPath === '/upload' || currentPath === '/';
    }
    return currentPath.startsWith(path);
  };

  // 判断分组是否包含激活项
  const isGroupActive = (group: NavGroup) => {
    return group.items.some(item => isActive(item.path));
  };

  return (
    <div className="flex min-h-screen bg-gradient-to-br from-slate-50 to-indigo-50">
      {/* 左侧边栏 */}
      <aside className="w-64 bg-white border-r border-slate-200 p-6 fixed h-screen left-0 top-0 z-50 overflow-y-auto">
        {/* Logo */}
        <Link to="/upload" className="flex items-center gap-3 mb-8">
          <div className="w-10 h-10 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center text-white">
            <Layers className="w-5 h-5" />
          </div>
          <span className="text-xl font-bold text-primary-600 tracking-tight">AI Interview</span>
        </Link>

        {/* 导航菜单 - 按模块分组 */}
        <nav className="space-y-6">
          {navGroups.map((group) => {
            const groupActive = isGroupActive(group);
            return (
              <div key={group.id} className="space-y-2">
                {/* 分组标题 */}
                <div className={`flex items-center gap-2 px-3 py-2 mb-2 ${
                  groupActive ? 'text-primary-600' : 'text-slate-400'
                }`}>
                  <group.icon className="w-5 h-5" />
                  <span className="text-base font-semibold uppercase tracking-wider">
                    {group.title}
                  </span>
                </div>
                {/* 分组下的导航项 */}
                {group.items.map((item) => {
                  const active = isActive(item.path);
                  return (
                    <Link
                      key={item.id}
                      to={item.path}
                      className={`w-full flex items-center gap-3 px-4 py-2.5 rounded-lg transition-all duration-200 text-left ml-2
                        ${active 
                          ? 'bg-gradient-to-r from-primary-50 to-primary-100 text-primary-600 font-medium shadow-sm' 
                          : 'text-slate-500 hover:bg-slate-50 hover:text-slate-700'
                        }`}
                    >
                      <item.icon className="w-5 h-5 flex-shrink-0" />
                      <span className="text-sm">{item.label}</span>
                    </Link>
                  );
                })}
              </div>
            );
          })}
        </nav>
      </aside>

      {/* 主内容区 */}
      <main className="flex-1 ml-64 p-10 min-h-screen overflow-y-auto">
        <motion.div
          key={currentPath}
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -20 }}
          transition={{ duration: 0.3 }}
        >
          <Outlet />
        </motion.div>
      </main>
    </div>
  );
}
