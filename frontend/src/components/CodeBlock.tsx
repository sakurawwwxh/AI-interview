import { useState, lazy, Suspense } from 'react';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { Check, Copy } from 'lucide-react';

// Load only the languages shown by the application instead of the full Prism bundle.
const SyntaxHighlighter = lazy(async () => {
  const [
    { default: PrismLight },
    { default: java },
    { default: javascript },
    { default: typescript },
    { default: json },
    { default: sql },
    { default: bash },
    { default: yaml },
    { default: markup },
    { default: css },
  ] = await Promise.all([
    import('react-syntax-highlighter/dist/esm/prism-light'),
    import('react-syntax-highlighter/dist/esm/languages/prism/java'),
    import('react-syntax-highlighter/dist/esm/languages/prism/javascript'),
    import('react-syntax-highlighter/dist/esm/languages/prism/typescript'),
    import('react-syntax-highlighter/dist/esm/languages/prism/json'),
    import('react-syntax-highlighter/dist/esm/languages/prism/sql'),
    import('react-syntax-highlighter/dist/esm/languages/prism/bash'),
    import('react-syntax-highlighter/dist/esm/languages/prism/yaml'),
    import('react-syntax-highlighter/dist/esm/languages/prism/markup'),
    import('react-syntax-highlighter/dist/esm/languages/prism/css'),
  ]);
  PrismLight.registerLanguage('java', java);
  PrismLight.registerLanguage('javascript', javascript);
  PrismLight.registerLanguage('typescript', typescript);
  PrismLight.registerLanguage('json', json);
  PrismLight.registerLanguage('sql', sql);
  PrismLight.registerLanguage('bash', bash);
  PrismLight.registerLanguage('yaml', yaml);
  PrismLight.registerLanguage('markup', markup);
  PrismLight.registerLanguage('css', css);
  PrismLight.alias('javascript', ['js', 'jsx']);
  PrismLight.alias('typescript', ['ts', 'tsx']);
  PrismLight.alias('bash', ['sh', 'shell']);
  PrismLight.alias('yaml', ['yml']);
  PrismLight.alias('markup', ['html', 'xml']);
  return { default: PrismLight };
});

interface CodeBlockProps {
  language?: string;
  children: string;
}

export default function CodeBlock({ language, children }: CodeBlockProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(children);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('复制失败:', err);
    }
  };

  // 清理代码内容
  const code = children?.trim() || '';

  return (
    <div className="relative group my-3">
      {/* 语言标签和复制按钮 */}
      <div className="flex items-center justify-between px-4 py-2 bg-slate-700 rounded-t-xl border-b border-slate-600">
        <span className="text-xs text-slate-400 font-mono">
          {language || 'code'}
        </span>
        <button
          onClick={handleCopy}
          className="flex items-center gap-1.5 px-2 py-1 text-xs text-slate-400 hover:text-white hover:bg-slate-600 rounded transition-colors"
          title="复制代码"
        >
          {copied ? (
            <>
              <Check className="w-3.5 h-3.5 text-green-400" />
              <span className="text-green-400">已复制</span>
            </>
          ) : (
            <>
              <Copy className="w-3.5 h-3.5" />
              <span>复制</span>
            </>
          )}
        </button>
      </div>

      {/* 代码内容 */}
      <div className="bg-[#282c34] rounded-b-xl text-sm leading-6">
        <Suspense fallback={
          <div className="p-4 text-slate-400 font-mono text-xs">Loading code...</div>
        }>
          <SyntaxHighlighter
            language={language || 'text'}
            style={oneDark}
            customStyle={{
              margin: 0,
              borderTopLeftRadius: 0,
              borderTopRightRadius: 0,
              borderBottomLeftRadius: '0.75rem',
              borderBottomRightRadius: '0.75rem',
              fontSize: '0.875rem',
              lineHeight: '1.5',
            }}
            showLineNumbers={code.split('\n').length > 3}
            wrapLines
          >
            {code}
          </SyntaxHighlighter>
        </Suspense>
      </div>
    </div>
  );
}
