/**
 * 面试答题草稿：localStorage 兜底 + 与后端暂存 API 配合。
 * key: interview-draft:{sessionId}:{questionIndex}
 */

const PREFIX = 'interview-draft:';

export interface InterviewDraft {
  answer: string;
  updatedAt: number;
}

function key(sessionId: string, questionIndex: number): string {
  return `${PREFIX}${sessionId}:${questionIndex}`;
}

/** 读取草稿；无内容返回 null */
export function loadInterviewDraft(sessionId: string, questionIndex: number): InterviewDraft | null {
  try {
    const raw = localStorage.getItem(key(sessionId, questionIndex));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as InterviewDraft;
    if (!parsed || typeof parsed.answer !== 'string') return null;
    return parsed;
  } catch {
    return null;
  }
}

/** 写入草稿（空串则删除） */
export function saveInterviewDraft(sessionId: string, questionIndex: number, answer: string): void {
  try {
    if (!answer.trim()) {
      localStorage.removeItem(key(sessionId, questionIndex));
      return;
    }
    const payload: InterviewDraft = { answer, updatedAt: Date.now() };
    localStorage.setItem(key(sessionId, questionIndex), JSON.stringify(payload));
  } catch {
    // 配额满等场景忽略
  }
}

/** 提交成功后清理当前题草稿 */
export function clearInterviewDraft(sessionId: string, questionIndex: number): void {
  try {
    localStorage.removeItem(key(sessionId, questionIndex));
  } catch {
    // ignore
  }
}

/** 清理该会话全部草稿（交卷后调用） */
export function clearSessionDrafts(sessionId: string): void {
  try {
    const prefix = `${PREFIX}${sessionId}:`;
    const toRemove: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (k && k.startsWith(prefix)) {
        toRemove.push(k);
      }
    }
    toRemove.forEach((k) => localStorage.removeItem(k));
  } catch {
    // ignore
  }
}
