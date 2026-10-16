import React from 'react'
import ReactDOM from 'react-dom/client'
import { MotionConfig } from 'framer-motion'
import App from './App'
import './index.css'

// 初始化深色模式（避免页面闪烁）
(function initTheme() {
    const stored = localStorage.getItem('theme');
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const isDark = stored === 'dark' || (!stored && prefersDark);
    if (isDark) {
        document.documentElement.classList.add('dark');
    }
})();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    {/* reducedMotion="user"：跟随系统「减少动效」偏好——
        位移/缩放类动画关闭，透明度与颜色过渡保留（有助于理解状态变化）。 */}
    <MotionConfig reducedMotion="user">
      <App />
    </MotionConfig>
  </React.StrictMode>,
)
