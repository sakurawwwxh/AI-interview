import { useCallback, useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { AlertCircle, Crown, Loader2, Save, Search, Shield, Trash2, UserRound, X } from 'lucide-react';
import { getErrorMessage } from '../api/request';
import { adminApi, type UserListItem, type UserPage, type UserRole } from '../api/admin';
import { useAuthStore } from '../stores/authStore';

type ModalState =
  | { type: 'role'; user: UserListItem; newRole: UserRole }
  | { type: 'quota'; user: UserListItem }
  | { type: 'delete'; user: UserListItem }
  | null;

export default function AdminUsersPage() {
  const currentUser = useAuthStore((s) => s.user);
  const [page, setPage] = useState<UserPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [keyword, setKeyword] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [currentPage, setCurrentPage] = useState(0);
  const [modal, setModal] = useState<ModalState>(null);
  const [quotaValue, setQuotaValue] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      setPage(await adminApi.getUsers({ keyword: keyword || undefined, page: currentPage, size: 20 }));
    } catch (loadError) { setError(getErrorMessage(loadError)); }
    finally { setLoading(false); }
  }, [keyword, currentPage]);

  useEffect(() => { load(); }, [load]);

  const search = (event: React.FormEvent) => {
    event.preventDefault();
    setKeyword(searchInput.trim());
    setCurrentPage(0);
  };

  const handleRoleChange = (user: UserListItem) => {
    const newRole: UserRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN';
    setActionError(null);
    setModal({ type: 'role', user, newRole });
  };

  const submitRoleChange = async () => {
    if (!modal || modal.type !== 'role') return;
    setSubmitting(true); setActionError(null);
    try {
      await adminApi.updateRole(modal.user.id, modal.newRole);
      closeModal();
      await load();
    } catch (submitError) { setActionError(getErrorMessage(submitError)); }
    finally { setSubmitting(false); }
  };

  const openQuotaModal = (user: UserListItem) => {
    setQuotaValue(String(user.dailyTokenQuota));
    setActionError(null);
    setModal({ type: 'quota', user });
  };

  const openDeleteModal = (user: UserListItem) => {
    setActionError(null);
    setModal({ type: 'delete', user });
  };

  const closeModal = () => {
    setModal(null);
    setActionError(null);
    setSubmitting(false);
  };

  const submitQuota = async () => {
    if (!modal || modal.type !== 'quota') return;
    const quota = Number(quotaValue);
    if (isNaN(quota) || quota < 0) { setActionError('请输入有效的非负数字'); return; }
    setSubmitting(true); setActionError(null);
    try {
      await adminApi.updateQuota(modal.user.id, quota);
      closeModal();
      await load();
    } catch (submitError) { setActionError(getErrorMessage(submitError)); }
    finally { setSubmitting(false); }
  };

  const submitDelete = async () => {
    if (!modal || modal.type !== 'delete') return;
    setSubmitting(true); setActionError(null);
    try {
      await adminApi.deleteUser(modal.user.id);
      closeModal();
      await load();
    } catch (submitError) { setActionError(getErrorMessage(submitError)); }
    finally { setSubmitting(false); }
  };

  const formatQuota = (quota: number) => quota >= 10000 ? `${(quota / 10000).toFixed(1)}万` : String(quota);

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div className="flex items-center gap-3">
        <div className="rounded-xl bg-primary-100 p-2.5 text-primary-600 dark:bg-primary-900/40">
          <Shield className="h-6 w-6" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-slate-800 dark:text-white">用户管理</h1>
          <p className="mt-1 text-sm text-slate-500">管理系统用户、角色与 Token 配额</p>
        </div>
      </div>

      <form onSubmit={search} className="flex gap-2">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="搜索用户名..."
            className="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm outline-none focus:border-primary-500 dark:border-slate-600 dark:bg-slate-800 dark:text-white"
          />
        </div>
        <button type="submit" className="rounded-xl bg-primary-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-primary-700">
          搜索
        </button>
      </form>

      {loading ? (
        <div className="flex min-h-64 items-center justify-center">
          <Loader2 className="h-8 w-8 animate-spin text-primary-500" />
        </div>
      ) : error ? (
        <div className="flex min-h-64 flex-col items-center justify-center rounded-2xl border border-red-100 bg-white p-8 text-center">
          <AlertCircle className="h-10 w-10 text-red-500" />
          <p className="mt-3 text-slate-700">{error}</p>
          <button onClick={load} className="mt-4 rounded-xl bg-primary-600 px-4 py-2 text-white">重试</button>
        </div>
      ) : !page || page.items.length === 0 ? (
        <div className="flex min-h-64 items-center justify-center rounded-2xl border border-slate-100 bg-white p-8 text-center dark:border-slate-700 dark:bg-slate-800">
          <p className="text-slate-500">没有找到用户</p>
        </div>
      ) : (
        <>
          <div className="overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100 text-left text-xs text-slate-500 dark:border-slate-700">
                  <th className="px-5 py-3 font-medium">用户名</th>
                  <th className="px-5 py-3 font-medium">邮箱</th>
                  <th className="px-5 py-3 font-medium">角色</th>
                  <th className="px-5 py-3 font-medium">Token 配额</th>
                  <th className="px-5 py-3 font-medium">注册时间</th>
                  <th className="px-5 py-3 text-right font-medium">操作</th>
                </tr>
              </thead>
              <tbody>
                {page.items.map((user) => {
                  const isSelf = currentUser?.id === user.id;
                  return (
                    <tr key={user.id} className="border-b border-slate-50 last:border-0 dark:border-slate-700/50">
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-slate-800 dark:text-white">{user.displayName || user.username}</span>
                          {isSelf && <span className="rounded bg-primary-100 px-1.5 py-0.5 text-xs text-primary-700">你</span>}
                        </div>
                        <p className="text-xs text-slate-400">{user.username}</p>
                      </td>
                      <td className="px-5 py-3 text-slate-600 dark:text-slate-300">{user.email}</td>
                      <td className="px-5 py-3">
                        {user.role === 'ADMIN' ? (
                          <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2.5 py-1 text-xs font-semibold text-amber-700">
                            <Crown className="h-3 w-3" />管理员
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600 dark:bg-slate-700 dark:text-slate-300">
                            <UserRound className="h-3 w-3" />普通用户
                          </span>
                        )}
                      </td>
                      <td className="px-5 py-3 text-slate-600 dark:text-slate-300">{formatQuota(user.dailyTokenQuota)}</td>
                      <td className="px-5 py-3 text-xs text-slate-400">
                        {new Date(user.createdAt).toLocaleDateString('zh-CN')}
                      </td>
                      <td className="px-5 py-3">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => handleRoleChange(user)}
                            disabled={isSelf}
                            title={isSelf ? '不能修改自己的角色' : '切换角色'}
                            className="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-600 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
                          >
                            {user.role === 'ADMIN' ? '降为用户' : '升为管理员'}
                          </button>
                          <button
                            onClick={() => openQuotaModal(user)}
                            title="修改配额"
                            className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-600 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
                          >
                            <Save className="h-3.5 w-3.5" />配额
                          </button>
                          <button
                            onClick={() => openDeleteModal(user)}
                            disabled={isSelf}
                            title={isSelf ? '不能删除自己' : '删除用户'}
                            className="rounded-lg border border-red-200 px-2.5 py-1 text-xs font-medium text-red-600 hover:bg-red-50 disabled:opacity-40 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-900/20"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {page.total > page.size && (
            <div className="flex items-center justify-between">
              <p className="text-sm text-slate-500">
                共 {page.total} 个用户，第 {page.page + 1} / {Math.ceil(page.total / page.size)} 页
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => setCurrentPage(page.page - 1)}
                  disabled={page.page === 0}
                  className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-600 disabled:opacity-40 dark:border-slate-600 dark:text-slate-300"
                >
                  上一页
                </button>
                <button
                  onClick={() => setCurrentPage(page.page + 1)}
                  disabled={(page.page + 1) * page.size >= page.total}
                  className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-600 disabled:opacity-40 dark:border-slate-600 dark:text-slate-300"
                >
                  下一页
                </button>
              </div>
            </div>
          )}
        </>
      )}

      {/* 模态框 */}
      <AnimatePresence>
        {modal && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
            onClick={closeModal}
          >
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl dark:bg-slate-800"
              onClick={(event) => event.stopPropagation()}
            >
              {modal.type === 'role' ? (
                <>
                  <div className="flex items-center justify-between">
                    <h2 className="text-lg font-bold text-slate-800 dark:text-white">修改角色</h2>
                    <button onClick={closeModal} className="text-slate-400 hover:text-slate-600"><X className="h-5 w-5" /></button>
                  </div>
                  <div className="mt-4 flex items-start gap-3 rounded-xl bg-amber-50 p-4 dark:bg-amber-900/20">
                    <Shield className="mt-0.5 h-5 w-5 flex-shrink-0 text-amber-500" />
                    <div>
                      <p className="text-sm font-medium text-amber-800 dark:text-amber-300">
                        将「{modal.user.displayName || modal.user.username}」的角色改为{modal.newRole === 'ADMIN' ? '管理员' : '普通用户'}？
                      </p>
                      <p className="mt-1 text-xs text-amber-600 dark:text-amber-400">
                        修改后该用户的登录状态将立即失效，需要重新登录才能生效新角色。
                      </p>
                    </div>
                  </div>
                  {actionError && <p className="mt-3 text-sm text-red-500">{actionError}</p>}
                  <div className="mt-6 flex justify-end gap-3">
                    <button onClick={closeModal} className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-medium text-slate-600 dark:border-slate-600 dark:text-slate-300">取消</button>
                    <button
                      onClick={submitRoleChange}
                      disabled={submitting}
                      className="inline-flex items-center gap-2 rounded-xl bg-primary-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
                    >
                      {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Shield className="h-4 w-4" />}
                      确认修改
                    </button>
                  </div>
                </>
              ) : modal.type === 'quota' ? (
                <>
                  <div className="flex items-center justify-between">
                    <h2 className="text-lg font-bold text-slate-800 dark:text-white">修改 Token 配额</h2>
                    <button onClick={closeModal} className="text-slate-400 hover:text-slate-600"><X className="h-5 w-5" /></button>
                  </div>
                  <p className="mt-2 text-sm text-slate-500">
                    用户「{modal.user.displayName || modal.user.username}」的每日 Token 配额
                  </p>
                  <div className="mt-4 flex items-center gap-2">
                    <input
                      type="number"
                      min={0}
                      value={quotaValue}
                      onChange={(event) => setQuotaValue(event.target.value)}
                      onKeyDown={(event) => event.key === 'Enter' && submitQuota()}
                      className="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm outline-none focus:border-primary-500 dark:border-slate-600 dark:bg-slate-900 dark:text-white"
                      autoFocus
                    />
                    <span className="text-sm text-slate-400">tokens/天</span>
                  </div>
                  {actionError && <p className="mt-3 text-sm text-red-500">{actionError}</p>}
                  <div className="mt-6 flex justify-end gap-3">
                    <button onClick={closeModal} className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-medium text-slate-600 dark:border-slate-600 dark:text-slate-300">取消</button>
                    <button
                      onClick={submitQuota}
                      disabled={submitting}
                      className="inline-flex items-center gap-2 rounded-xl bg-primary-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
                    >
                      {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                      保存
                    </button>
                  </div>
                </>
              ) : (
                <>
                  <div className="flex items-center justify-between">
                    <h2 className="text-lg font-bold text-slate-800 dark:text-white">删除用户</h2>
                    <button onClick={closeModal} className="text-slate-400 hover:text-slate-600"><X className="h-5 w-5" /></button>
                  </div>
                  <div className="mt-4 flex items-start gap-3 rounded-xl bg-red-50 p-4 dark:bg-red-900/20">
                    <AlertCircle className="mt-0.5 h-5 w-5 flex-shrink-0 text-red-500" />
                    <div>
                      <p className="text-sm font-medium text-red-800 dark:text-red-300">
                        确认删除用户「{modal.user.displayName || modal.user.username}」？
                      </p>
                      <p className="mt-1 text-xs text-red-600 dark:text-red-400">
                        此操作不可撤销。该用户的 refresh token 将被立即吊销，但业务数据（简历、面试记录等）不会被删除。
                      </p>
                    </div>
                  </div>
                  {actionError && <p className="mt-3 text-sm text-red-500">{actionError}</p>}
                  <div className="mt-6 flex justify-end gap-3">
                    <button onClick={closeModal} className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-medium text-slate-600 dark:border-slate-600 dark:text-slate-300">取消</button>
                    <button
                      onClick={submitDelete}
                      disabled={submitting}
                      className="inline-flex items-center gap-2 rounded-xl bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-60"
                    >
                      {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                      确认删除
                    </button>
                  </div>
                </>
              )}
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
