import { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertCircle, ArrowRightLeft, CheckCircle2, CloudCog, Database, Loader2, RefreshCw, RotateCw, ShieldCheck, TriangleAlert, UserRound, type LucideIcon } from 'lucide-react';
import PageHeader from '../components/PageHeader';
import { difyAdminApi, type DifySyncLog, type DifySyncStatus, type DifySyncStatusItem } from '../api/dify';
import { getErrorMessage } from '../api/request';
import { adminApi, type UserListItem } from '../api/admin';
import ConfirmDialog from '../components/ConfirmDialog';
import { useAuthStore } from '../stores/authStore';

/**
 * 同步失败时后端会存下原始 Java 异常（含内网 IP:端口、英文堆栈片段），
 * 直接展示既难读也暴露内网信息。这里做一次可读化映射并隐藏地址。
 */
function formatSyncError(raw?: string | null): string {
  if (!raw) {
    return '—';
  }
  const masked = raw.replace(/\b\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?\b/g, '（内网地址已隐藏）');
  const summary = /timed out|timeout/i.test(raw) ? '连接超时'
    : /UnknownHost|Name or service not known/i.test(raw) ? '域名解析失败'
      : /401|403|Unauthorized|Forbidden/i.test(raw) ? '鉴权失败（请检查 Dify API Key）'
        : '';
  const body = masked.length > 180 ? `${masked.slice(0, 180)}…` : masked;
  return summary ? `${summary}：${body}` : body;
}

const statusMeta: Record<DifySyncStatus, { label: string; className: string }> = {
  SYNCED: { label: '已同步', className: 'bg-emerald-500/10 text-emerald-700 ring-emerald-600/15 dark:text-emerald-300' },
  SUCCESS: { label: '成功', className: 'bg-emerald-500/10 text-emerald-700 ring-emerald-600/15 dark:text-emerald-300' },
  PENDING: { label: '待同步', className: 'bg-amber-500/10 text-amber-700 ring-amber-600/15 dark:text-amber-300' },
  FAILED: { label: '失败', className: 'bg-red-500/10 text-red-700 ring-red-600/15 dark:text-red-300' },
};

const summaryCards: Array<{ label: string; key: 'total' | 'synced' | 'pending' | 'failed'; icon: LucideIcon; tone: string }> = [
  { label: '已管理文档', key: 'total', icon: Database, tone: 'text-teal-700 dark:text-teal-300' },
  { label: '已同步', key: 'synced', icon: CheckCircle2, tone: 'text-emerald-700 dark:text-emerald-300' },
  { label: '待处理', key: 'pending', icon: RefreshCw, tone: 'text-amber-700 dark:text-amber-300' },
  { label: '同步失败', key: 'failed', icon: AlertCircle, tone: 'text-red-700 dark:text-red-300' },
];

const formatTime = (value: string | null) => value
  ? new Date(value).toLocaleString('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
  : '尚未同步';

function StatusBadge({ status }: { status: DifySyncStatus | null }) {
  const meta = status ? statusMeta[status] : statusMeta.PENDING;
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${meta.className}`}>{meta.label}</span>;
}

export default function DifyManagementPage() {
  const user = useAuthStore((state) => state.user);
  const [statuses, setStatuses] = useState<DifySyncStatusItem[]>([]);
  const [logs, setLogs] = useState<DifySyncLog[]>([]);
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [syncingKnowledgeBaseId, setSyncingKnowledgeBaseId] = useState<number | null>(null);
  const [selectedLogKnowledgeBaseId, setSelectedLogKnowledgeBaseId] = useState<number | null>(null);
  const [ownerDialogItem, setOwnerDialogItem] = useState<DifySyncStatusItem | null>(null);
  const [targetOwnerId, setTargetOwnerId] = useState('');
  const [transferring, setTransferring] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async (showRefresh = false) => {
    if (showRefresh) setRefreshing(true);
    else setLoading(true);
    setError(null);
    try {
      const [nextStatuses, nextLogs, nextUsers] = await Promise.all([
        difyAdminApi.getSyncStatus(),
        difyAdminApi.getSyncLogs(selectedLogKnowledgeBaseId ?? undefined),
        adminApi.getUsers({ page: 0, size: 100 }),
      ]);
      setStatuses(nextStatuses);
      setLogs(nextLogs);
      setUsers(nextUsers.items);
    } catch (loadError) {
      setError(getErrorMessage(loadError));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [selectedLogKnowledgeBaseId]);

  useEffect(() => { load(); }, [load]);

  const summary = useMemo(() => ({
    total: statuses.length,
    synced: statuses.filter((item) => item.syncStatus === 'SYNCED' || item.syncStatus === 'SUCCESS').length,
    pending: statuses.filter((item) => item.syncStatus === 'PENDING' || !item.syncStatus).length,
    failed: statuses.filter((item) => item.syncStatus === 'FAILED').length,
  }), [statuses]);

  const syncNow = async () => {
    if (!window.confirm('将从 Dify 拉取最新文档并更新本地知识库，确定现在同步吗？')) return;
    setSyncing(true);
    setError(null);
    setNotice(null);
    try {
      await difyAdminApi.syncNow();
      await load(true);
      setNotice('已完成从 Dify 拉取最新变更。');
    } catch (syncError) {
      setError(getErrorMessage(syncError));
    } finally {
      setSyncing(false);
    }
  };

  const syncKnowledgeBase = async (item: DifySyncStatusItem) => {
    const action = item.syncStatus === 'FAILED' ? '重试同步' : '同步到 Dify';
    if (!window.confirm(`确定${action}「${item.name}」吗？本地文件会覆盖 Dify 中对应的文档内容。`)) return;

    setSyncingKnowledgeBaseId(item.knowledgeBaseId);
    setError(null);
    setNotice(null);
    try {
      await difyAdminApi.syncKnowledgeBaseToDify(item.knowledgeBaseId);
      setNotice(`「${item.name}」已提交到 Dify 同步队列，完成后可刷新查看状态。`);
    } catch (syncError) {
      setError(getErrorMessage(syncError));
    } finally {
      setSyncingKnowledgeBaseId(null);
    }
  };

  const openOwnerDialog = (item: DifySyncStatusItem) => {
    setOwnerDialogItem(item);
    setTargetOwnerId(item.ownerUserId ? String(item.ownerUserId) : '');
    setActionError(null);
  };

  const closeOwnerDialog = () => {
    if (transferring) return;
    setOwnerDialogItem(null);
    setActionError(null);
  };

  const transferOwner = async () => {
    if (!ownerDialogItem) return;
    const nextOwnerId = Number(targetOwnerId);
    if (!Number.isInteger(nextOwnerId) || nextOwnerId <= 0) {
      setActionError('请选择目标用户。');
      return;
    }

    setTransferring(true);
    setActionError(null);
    try {
      await difyAdminApi.transferKnowledgeBaseOwner(ownerDialogItem.knowledgeBaseId, nextOwnerId);
      const targetUser = users.find((item) => item.id === nextOwnerId);
      setOwnerDialogItem(null);
      setNotice(`已将「${ownerDialogItem.name}」的本地 RAG 归属转交给 ${targetUser?.username || '目标用户'}。`);
      await load(true);
    } catch (transferError) {
      setActionError(getErrorMessage(transferError));
    } finally {
      setTransferring(false);
    }
  };

  const selectedLogKnowledgeBase = statuses.find((item) => item.knowledgeBaseId === selectedLogKnowledgeBaseId);

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <PageHeader
        kicker="管理员控制台"
        title="Dify 知识库管理"
        description="Dify 使用共享服务密钥与数据集；知识库归属控制本地 RAG 的可见范围，Dify 调用始终只对管理员开放。"
        actions={(
          <>
            <button type="button" onClick={() => load(true)} disabled={refreshing || syncing} className="ui-btn-secondary">
              <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} aria-hidden />刷新状态
            </button>
            <button type="button" onClick={syncNow} disabled={refreshing || syncing} className="ui-btn-primary">
              {syncing ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <CloudCog className="h-4 w-4" aria-hidden />}
              {syncing ? '同步中…' : '从 Dify 拉取'}
            </button>
          </>
        )}
      />

      <section className="grid gap-4 lg:grid-cols-[1.35fr_1fr]" aria-label="访问策略与同步概况">
        <div className="ui-panel relative overflow-hidden p-5 sm:p-6">
          <div aria-hidden className="absolute -right-10 -top-12 h-36 w-36 rounded-full bg-teal-500/10 blur-3xl" />
          <div className="relative flex gap-4">
            <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-teal-500/10 text-teal-700 dark:text-teal-300"><ShieldCheck className="h-5 w-5" aria-hidden /></span>
            <div>
              <p className="text-sm font-semibold text-[var(--shell-text)]">共享数据集，管理员受控</p>
              <p className="mt-1.5 max-w-xl text-sm leading-6 text-[var(--shell-muted)]">当前会话管理员为 <strong className="font-semibold text-[var(--shell-text)]">{user?.username || '—'}</strong>。可以在下方转交知识库归属或单独推送文档；Dify 入口始终仅对管理员开放。</p>
            </div>
          </div>
          <div className="relative mt-5 flex flex-wrap gap-2 border-t border-[var(--shell-border)] pt-4 text-xs text-[var(--shell-muted)]">
            <span className="rounded-lg bg-[var(--shell-hover)] px-2.5 py-1.5">普通用户：本地 RAG</span>
            <span className="rounded-lg bg-[var(--shell-hover)] px-2.5 py-1.5">管理员：Dify 工作流</span>
            <span className="rounded-lg bg-[var(--shell-hover)] px-2.5 py-1.5">密钥：仅服务端保存</span>
          </div>
        </div>

        <div className="ui-panel p-5 sm:p-6">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-amber-500/10 text-amber-700 dark:text-amber-300"><TriangleAlert className="h-5 w-5" aria-hidden /></span>
            <div>
              <p className="text-sm font-semibold text-[var(--shell-text)]">多用户扩展提示</p>
              <p className="mt-1 text-sm leading-6 text-[var(--shell-muted)]">若以后让普通用户直接使用 Dify，需要采用独立数据集/应用或继续经本地 RAG 鉴权；共享数据集不能仅靠这个页面实现数据隔离。</p>
            </div>
          </div>
        </div>
      </section>

      {error && (
        <div role="alert" className="flex items-start gap-3 rounded-2xl border border-red-500/20 bg-red-500/5 px-4 py-3 text-sm text-red-700 dark:text-red-300">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <div><p className="font-semibold">无法获取 Dify 状态</p><p className="mt-0.5">{error}</p></div>
        </div>
      )}

      {notice && (
        <div role="status" className="flex items-start gap-3 rounded-2xl border border-emerald-500/20 bg-emerald-500/5 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300">
          <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <p>{notice}</p>
        </div>
      )}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="同步统计">
        {summaryCards.map(({ label, key, icon: MetricIcon, tone }) => {
          return (
            <div key={key} className="ui-panel p-5">
              <div className="flex items-center justify-between"><span className="text-sm text-[var(--shell-muted)]">{label}</span><MetricIcon className={`h-4 w-4 ${tone}`} aria-hidden /></div>
              <p className="mt-4 text-3xl font-semibold tracking-tight text-[var(--shell-text)]">{loading ? '—' : summary[key]}</p>
            </div>
          );
        })}
      </section>

      <section className="ui-panel overflow-hidden" aria-labelledby="dify-document-status-title">
        <div className="flex items-center justify-between border-b border-[var(--shell-border)] px-5 py-4 sm:px-6">
          <div><h2 id="dify-document-status-title" className="font-semibold text-[var(--shell-text)]">文档同步状态</h2><p className="mt-1 text-sm text-[var(--shell-muted)]">本地知识库与 Dify 文档的当前对应关系</p></div>
        </div>
        {loading ? (
          <div className="flex min-h-52 items-center justify-center"><Loader2 className="h-7 w-7 animate-spin text-teal-600" aria-label="加载中" /></div>
        ) : statuses.length === 0 ? (
          <div className="flex min-h-52 flex-col items-center justify-center px-6 text-center"><Database className="h-8 w-8 text-[var(--shell-subtle)]" aria-hidden /><p className="mt-3 text-sm font-medium text-[var(--shell-text)]">暂无可管理的知识库</p><p className="mt-1 text-sm text-[var(--shell-muted)]">上传文档后会在这里显示其 Dify 同步状态。</p></div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[940px] text-left text-sm">
              <thead className="bg-[var(--shell-hover)]/60 text-xs text-[var(--shell-muted)]"><tr><th className="px-5 py-3 font-medium sm:px-6">知识库</th><th className="px-5 py-3 font-medium">本地归属</th><th className="px-5 py-3 font-medium">状态</th><th className="px-5 py-3 font-medium">最近同步</th><th className="px-5 py-3 font-medium">异常信息</th><th className="px-5 py-3 text-right font-medium sm:px-6">操作</th></tr></thead>
              <tbody className="divide-y divide-[var(--shell-border)]">
                {statuses.map((item) => <tr key={item.knowledgeBaseId} className="align-top"><td className="px-5 py-4 font-medium text-[var(--shell-text)] sm:px-6"><p>{item.name}</p><p className="mt-1 text-xs font-normal text-[var(--shell-subtle)]">ID #{item.knowledgeBaseId}</p></td><td className="px-5 py-4"><div className="inline-flex items-center gap-1.5 text-xs text-[var(--shell-muted)]"><UserRound className="h-3.5 w-3.5" aria-hidden />{item.ownerUsername || '未归属'}</div></td><td className="px-5 py-4"><StatusBadge status={item.syncStatus} /></td><td className="whitespace-nowrap px-5 py-4 text-xs text-[var(--shell-muted)]">{formatTime(item.lastSyncTime)}</td><td className="max-w-56 px-5 py-4 text-xs leading-5 text-red-700 dark:text-red-300">{formatSyncError(item.errorMessage)}</td><td className="px-5 py-4 sm:px-6"><div className="flex justify-end gap-2"><button type="button" onClick={() => syncKnowledgeBase(item)} disabled={syncingKnowledgeBaseId !== null} className="ui-btn-secondary !min-h-8 !px-2.5 !py-1 !text-xs">{syncingKnowledgeBaseId === item.knowledgeBaseId ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden /> : <RotateCw className="h-3.5 w-3.5" aria-hidden />}{item.syncStatus === 'FAILED' ? '重试同步' : '推送 Dify'}</button><button type="button" onClick={() => openOwnerDialog(item)} disabled={syncingKnowledgeBaseId !== null} className="ui-btn-secondary !min-h-8 !px-2.5 !py-1 !text-xs"><ArrowRightLeft className="h-3.5 w-3.5" aria-hidden />转交</button><button type="button" onClick={() => setSelectedLogKnowledgeBaseId(item.knowledgeBaseId)} className="ui-btn-secondary !min-h-8 !px-2.5 !py-1 !text-xs">记录</button></div></td></tr>)}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="ui-panel overflow-hidden" aria-labelledby="dify-log-title">
        <div className="flex items-center justify-between gap-4 border-b border-[var(--shell-border)] px-5 py-4 sm:px-6"><div><h2 id="dify-log-title" className="font-semibold text-[var(--shell-text)]">最近同步记录</h2>{selectedLogKnowledgeBase && <p className="mt-1 text-sm text-[var(--shell-muted)]">仅显示「{selectedLogKnowledgeBase.name}」的记录</p>}</div>{selectedLogKnowledgeBaseId !== null && <button type="button" onClick={() => setSelectedLogKnowledgeBaseId(null)} className="ui-btn-secondary !min-h-8 !px-2.5 !py-1 !text-xs">查看全部</button>}</div>
        {logs.length === 0 && !loading ? <p className="px-6 py-10 text-center text-sm text-[var(--shell-muted)]">暂无同步记录</p> : (
          <div className="divide-y divide-[var(--shell-border)]">{logs.map((log) => <div key={log.id} className="flex gap-3 px-5 py-4 sm:px-6"><StatusBadge status={log.syncStatus} /><div className="min-w-0 flex-1"><p className="text-sm text-[var(--shell-text)]">{log.syncDirection === 'TO_DIFY' ? '本地 → Dify' : 'Dify → 本地'} · {log.syncAction} · 知识库 #{log.knowledgeBaseId ?? '—'}</p>{log.errorMessage && <p className="mt-1 break-words text-xs leading-5 text-red-700 dark:text-red-300">{formatSyncError(log.errorMessage)}</p>}</div><time className="shrink-0 text-xs text-[var(--shell-subtle)]">{formatTime(log.createdAt)}</time></div>)}</div>
        )}
      </section>

      <ConfirmDialog
        open={ownerDialogItem !== null}
        title="转交知识库归属"
        message={ownerDialogItem ? `将「${ownerDialogItem.name}」交给其他用户后，原归属用户将无法再通过本地 RAG 查询该知识库。Dify 仍为管理员专用。` : ''}
        confirmText="确认转交"
        onConfirm={transferOwner}
        onCancel={closeOwnerDialog}
        loading={transferring}
        customContent={(
          <div className="mt-4">
            <label htmlFor="knowledgebase-owner" className="mb-2 block text-sm font-medium text-[var(--shell-text)]">新的归属用户</label>
            <select id="knowledgebase-owner" value={targetOwnerId} onChange={(event) => setTargetOwnerId(event.target.value)} disabled={transferring} className="w-full rounded-xl border border-[var(--shell-border)] bg-[var(--shell-surface)] px-3 py-2.5 text-sm text-[var(--shell-text)] outline-none focus:border-teal-500">
              <option value="">请选择用户</option>
              {users.map((item) => <option key={item.id} value={item.id}>{item.displayName || item.username}（{item.username}）</option>)}
            </select>
            {actionError && <p className="mt-2 text-sm text-red-700 dark:text-red-300">{actionError}</p>}
          </div>
        )}
      />
    </div>
  );
}
