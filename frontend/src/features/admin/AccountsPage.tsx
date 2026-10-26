import { Select } from '../../shared/ui/Select';
import { useCallback, useEffect, useRef, useState } from 'react';
import { adminApi, type AdminAccount, type AdminConfiguration } from './api';
import { AccountEditor } from './AccountEditor';
import { PasswordResetForm } from './PasswordResetForm';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import type { Page } from '../../shared/api/http';
import { Modal } from '../../shared/ui/Modal';

type Action =
  { type: 'edit'; user: AdminAccount | null } | { type: 'reset' | 'enabled'; user: AdminAccount };

export function AccountsPage({
  myId,
  onReferenceChanged,
}: {
  myId: string;
  onReferenceChanged: () => Promise<void>;
}) {
  const [configuration, setConfiguration] = useState<AdminConfiguration | null>(null);
  const [result, setResult] = useState<Page<AdminAccount> | null>(null);
  const [search, setSearch] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [enabled, setEnabled] = useState('');
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [action, setAction] = useState<Action | null>(null);
  const errorSummary = useRef<HTMLDivElement>(null);
  const confirmationTitle = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (error) errorSummary.current?.focus();
  }, [error]);
  useEffect(() => {
    if (action?.type === 'enabled') confirmationTitle.current?.focus();
  }, [action]);
  const load = useCallback(async () => {
    const accounts = await adminApi.accounts(appliedSearch, enabled, page);
    setResult(accounts);
  }, [appliedSearch, enabled, page]);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    Promise.all([adminApi.configuration(), adminApi.accounts(appliedSearch, enabled, page)])
      .then(([config, accounts]) => {
        if (active) {
          setConfiguration(config);
          setResult(accounts);
        }
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '管理数据加载失败');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [appliedSearch, enabled, page]);

  async function complete(message: string) {
    setAction(null);
    setNotice(message);
    try {
      await load();
      await onReferenceChanged();
    } catch (cause) {
      setError(`操作已成功，但刷新失败：${cause instanceof Error ? cause.message : '请重新加载'}`);
    }
  }
  function begin(next: Action) {
    setAction(next);
    setNotice('');
    setError('');
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">ADMINISTRATION</span>
          <h1>账号与组织</h1>
          <p>维护账号、角色和省市范围。管理员不参与业务审批。</p>
        </div>
        <button
          className="primary-button"
          disabled={!configuration || busy}
          onClick={() => begin({ type: 'edit', user: null })}
        >
          新建账号
        </button>
      </div>
      {error && (
        <div ref={errorSummary} tabIndex={-1}>
          <Feedback tone="error">
            {error}
            <button className="text-button" type="button" onClick={() => window.location.reload()}>
              重新加载
            </button>
          </Feedback>
        </div>
      )}
      {notice && <Feedback tone="success">{notice}</Feedback>}
      <p className="subtle">
        省市采用预置行政区划目录，配置账号时从目录选择，不再允许自行新增省市。
      </p>
      <form
        className="filter-bar"
        onSubmit={(event) => {
          event.preventDefault();
          setPage(0);
          setAppliedSearch(search);
        }}
      >
        <label>
          搜索账号
          <input
            maxLength={100}
            placeholder="手机号、姓名或邮箱"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
        <label>
          账号状态
          <Select
            value={enabled}
            onChange={(event) => {
              setEnabled(event.target.value);
              setPage(0);
            }}
          >
            <option value="">全部状态</option>
            <option value="true">已启用</option>
            <option value="false">已停用</option>
          </Select>
        </label>
        <button className="secondary-button" disabled={loading}>
          查询账号
        </button>
      </form>
      {loading ? (
        <Loading />
      ) : (
        result && (
          <section className="panel table-panel" aria-label="账号列表">
            <p className="subtle">表格支持左右滚动，查看完整归属和账号操作。</p>
            <div
              className="table-scroll"
              role="region"
              aria-label="可横向滚动的账号表格"
              tabIndex={0}
            >
              <table className="admin-accounts">
                <thead>
                  <tr>
                    <th>账号与姓名</th>
                    <th>角色与归属</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {result.items.map((user) => (
                    <tr key={user.id}>
                      <td>
                        <strong>{user.displayName}</strong>
                        <br />
                        {user.loginName}
                        <br />
                        <span className="subtle">{user.email ?? '未填写邮箱'}</span>
                      </td>
                      <td>
                        {user.roleCodes
                          .map(
                            (role) =>
                              configuration?.roles.find((item) => item.code === role)?.label ??
                              role,
                          )
                          .join('、')}
                        <br />
                        <span className="subtle">
                          {user.roleCodes.includes('INTERFACE_REVIEWER')
                            ? user.reviewerProvinceIds
                                .map(
                                  (id) =>
                                    configuration?.provinces.find((province) => province.id === id)
                                      ?.name ?? '省分不可用',
                                )
                                .join('、') || '未配置负责范围'
                            : user.provinceId
                              ? (configuration?.provinces.find(
                                  (province) => province.id === user.provinceId,
                                )?.name ?? '归属省分不可用')
                              : user.roleCodes.includes('INTERFACE_REVIEWER')
                                ? '未配置负责范围'
                                : '全国'}
                          {user.cityId
                            ? ` / ${configuration?.cities.find((city) => city.id === user.cityId)?.name ?? '归属地市不可用'}`
                            : ''}
                        </span>
                      </td>
                      <td>
                        {user.enabled ? '已启用' : '已停用'}
                        {!user.passwordInitialized && (
                          <>
                            <br />
                            <span className="subtle">尚未设置密码</span>
                          </>
                        )}
                      </td>
                      <td>
                        <div className="admin-row-actions">
                          <button
                            className="text-button"
                            type="button"
                            disabled={busy}
                            onClick={() => begin({ type: 'edit', user })}
                          >
                            编辑账号
                          </button>
                          <button
                            className="text-button"
                            type="button"
                            disabled={busy || user.id === myId}
                            title={user.id === myId ? '不能停用自己' : undefined}
                            onClick={() => begin({ type: 'enabled', user })}
                          >
                            {user.enabled ? '停用账号' : '启用账号'}
                          </button>
                          <button
                            className="text-button"
                            type="button"
                            disabled={busy}
                            onClick={() => begin({ type: 'reset', user })}
                          >
                            重置密码
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {result.items.length === 0 && (
                    <tr>
                      <td colSpan={4}>没有符合条件的账号。</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <div className="pager admin-pager">
              <span>
                共 {result.totalElements} 个账号 · 第 {result.page + 1} 页
              </span>
              <button
                className="secondary-button"
                type="button"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                上一页
              </button>
              <button
                className="secondary-button"
                type="button"
                disabled={page + 1 >= result.totalPages}
                onClick={() => setPage(page + 1)}
              >
                下一页
              </button>
            </div>
          </section>
        )
      )}
      {configuration && action?.type === 'edit' && (
        <Modal title={action.user ? '编辑账号' : '创建账号'} onClose={() => setAction(null)}>
          <AccountEditor
            key={action.user?.id ?? 'new'}
            user={action.user}
            configuration={configuration}
            onSaved={() =>
              complete(action.user ? '账号已更新，角色或范围变化时原会话已撤销。' : '账号已创建。')
            }
            onCancel={() => setAction(null)}
          />
        </Modal>
      )}
      {action?.type === 'reset' && (
        <Modal title="设置账号密码" onClose={() => setAction(null)}>
          <PasswordResetForm
            key={action.user.id}
            user={action.user}
            isSelf={action.user.id === myId}
            onSaved={() => complete('密码已重置，所有旧会话已失效。')}
            onCancel={() => setAction(null)}
          />
        </Modal>
      )}
      {action?.type === 'enabled' && (
        <Modal title="确认账号状态变更" busy={busy} onClose={() => setAction(null)}>
          <section className="panel form-panel admin-editor" aria-label="确认账号状态变更">
            <h2 ref={confirmationTitle} tabIndex={-1}>
              确认{action.user.enabled ? '停用' : '启用'}账号
            </h2>
            <p>
              {action.user.displayName}（{action.user.loginName}）。
              {action.user.enabled
                ? '停用将立即撤销该账号的全部会话，不能再登录或参与业务操作。'
                : '启用不会恢复已经撤销的会话，账号本人需要重新登录。'}
            </p>
            <div className="form-actions">
              <button
                className="secondary-button"
                type="button"
                disabled={busy}
                onClick={() => setAction(null)}
              >
                取消变更
              </button>
              <button
                className="primary-button"
                type="button"
                disabled={busy}
                onClick={async () => {
                  setBusy(true);
                  setError('');
                  try {
                    await adminApi.enabled(action.user);
                    await complete(
                      action.user.enabled
                        ? '账号已停用，旧会话已失效。'
                        : '账号已启用，请重新登录。',
                    );
                  } catch (cause) {
                    setError(cause instanceof Error ? cause.message : '账号状态更新失败');
                  } finally {
                    setBusy(false);
                  }
                }}
              >
                {busy ? '正在更新…' : '确认变更'}
              </button>
            </div>
          </section>
        </Modal>
      )}
    </>
  );
}
