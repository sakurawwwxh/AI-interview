/**
 * 把文本里的 `反引号` 片段渲染成 <code>。
 *
 * 题目与参考答案正文由 AI 生成，常带 markdown 行内代码标记；直接当纯文本渲染会把
 * 反引号原样显示出来（例如 `LinkedHashMap`）。这里只处理行内片段，不引入块级元素，
 * 因此可以安全地放进 <h1>/<h2> 这类标题里。
 */
export default function InlineCode({ text }: { text: string }) {
  if (!text.includes('`')) {
    return <>{text}</>;
  }
  const segments = text.split('`');
  return (
    <>
      {segments.map((segment, index) =>
        index % 2 === 1 ? (
          <code
            key={index}
            className="rounded bg-[var(--shell-hover)] px-1 py-0.5 font-mono text-[0.92em] text-[var(--shell-text)]"
          >
            {segment}
          </code>
        ) : (
          <span key={index}>{segment}</span>
        ),
      )}
    </>
  );
}
