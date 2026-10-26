import { useCallback, useEffect, useState } from 'react';
import { adminApi, type AdminAccount, type AdminConfiguration } from './api';
import type { Page } from '../../shared/api/http';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { Modal } from '../../shared/ui/Modal';
import { Select } from '../../shared/ui/Select';

/** 全量省份占用信息与账号分页分离，避免分页造成重复分配。 */
export function ReviewerProvincesPage() {
  const [configuration, setConfiguration] = useState<AdminConfiguration | null>(null);
  const [accounts, setAccounts] = useState<Page<AdminAccount> | null>(null);
  const [owners, setOwners] = useState<Awaited<ReturnType<typeof adminApi.owners>>>([]);
  const [unassigned, setUnassigned] = useState<
    Awaited<ReturnType<typeof adminApi.handoverPending>>
  >([]);
  const [page, setPage] = useState(0),
    [search, setSearch] = useState(''),
    [query, setQuery] = useState('');
  const [error, setError] = useState(''),
    [notice, setNotice] = useState(''),
    [busy, setBusy] = useState(false);
  const [editor, setEditor] = useState<AdminAccount | null>(null),
    [ids, setIds] = useState<string[]>([]);
  const [transfer, setTransfer] = useState(false),
    [targets, setTargets] = useState<AdminAccount[]>([]),
    [target, setTarget] = useState('');
  const [targetSearch, setTargetSearch] = useState('');
  const load = useCallback(async () => {
    const [c, a, o, u] = await Promise.all([
      adminApi.configuration(),
      adminApi.accounts(query, '', page, 'INTERFACE_REVIEWER'),
      adminApi.owners(),
      adminApi.handoverPending(),
    ]);
    setConfiguration(c);
    setAccounts(a);
    setOwners(o);
    setUnassigned(u);
  }, [page, query]);
  useEffect(() => {
    load().catch((c) => setError(c.message));
  }, [load]);
  useEffect(() => {
    if (!transfer) return;
    let active = true;
    adminApi
      .accounts(targetSearch, 'true', 0, 'INTERFACE_REVIEWER')
      .then((a) => {
        if (active) setTargets(a.items.filter((u) => u.id !== editor?.id));
      })
      .catch((c) => setError(c.message));
    return () => {
      active = false;
    };
  }, [transfer, targetSearch, editor?.id]);
  function open(user: AdminAccount, isTransfer = false) {
    setEditor(user);
    setIds(isTransfer ? [] : user.reviewerProvinceIds);
    setTransfer(isTransfer);
    setTarget('');
    setTargetSearch('');
    setError('');
  }
  async function save() {
    if (!editor) return;
    setBusy(true);
    setError('');
    try {
      if (transfer) {
        const next = targets.find((u) => u.id === target);
        if (!next) throw new Error('请选择接任接口人');
        await adminApi.transfer(editor, next, ids);
      } else
        await adminApi.saveReviewerMappings([
          { reviewerId: editor.id, version: editor.version, provinceIds: ids },
        ]);
      setEditor(null);
      setNotice(transfer ? '范围及相关待审申请已交接' : '负责范围已保存');
      await load();
    } catch (c) {
      setError(c instanceof Error ? c.message : '保存失败');
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">REVIEWER SCOPE</span>
          <h1>接口人范围</h1>
          <p>每省仅由一名接口人负责。范围变更同步交接相关待审申请，审批期限保持不变。</p>
        </div>
      </div>
      {error && !editor && <Feedback tone="error">{error}</Feedback>}
      {notice && <Feedback tone="success">{notice}</Feedback>}
      {unassigned.length > 0 && (
        <Feedback tone="error">
          有 {unassigned.length}{' '}
          条待审申请等待接口人交接。请为对应省份配置接口人，保存时将一并交接：
          {[
            ...new Set(
              unassigned.map(
                (u) =>
                  configuration?.provinces.find((p) => p.id === u.provinceId)?.name || u.provinceId,
              ),
            ),
          ].join('、')}
        </Feedback>
      )}
      <form
        className="filter-bar"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          setQuery(search);
        }}
      >
        <label>
          账号或姓名
          <input value={search} onChange={(e) => setSearch(e.target.value)} />
        </label>
        <button type="submit" className="primary-button">
          查询
        </button>
      </form>
      {!accounts ? (
        <Loading />
      ) : (
        <section className="panel table-panel">
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>姓名 / 登录名</th>
                  <th>负责省份</th>
                  <th>账号状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {accounts.items.map((user) => (
                  <tr key={user.id}>
                    <td>
                      {user.displayName}
                      <br />
                      <small>{user.loginName}</small>
                    </td>
                    <td>
                      {user.reviewerProvinceIds
                        .map((id) => configuration?.provinces.find((p) => p.id === id)?.name)
                        .join('、') || '未分配'}
                    </td>
                    <td>{user.enabled ? '已启用' : '已停用'}</td>
                    <td>
                      <div className="admin-row-actions">
                        <button
                          className="text-button"
                          disabled={!user.enabled}
                          onClick={() => open(user)}
                        >
                          配置
                        </button>
                        <button
                          className="text-button"
                          disabled={!user.reviewerProvinceIds.length}
                          onClick={() => open(user, true)}
                        >
                          交接
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="admin-pager">
            <button
              className="secondary-button"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              上一页
            </button>
            <span>第 {page + 1} 页</span>
            <button
              className="secondary-button"
              disabled={page + 1 >= accounts.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              下一页
            </button>
          </div>
        </section>
      )}
      {editor && (
        <Modal
          title={transfer ? '交接负责省份及待审申请' : '配置接口人范围'}
          onClose={() => setEditor(null)}
          busy={busy}
        >
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void save();
            }}
          >
            <p>
              {editor.displayName} · {editor.loginName}
            </p>
            {error && <Feedback tone="error">{error}</Feedback>}
            {transfer && (
              <>
                <label>
                  搜索接任接口人
                  <input
                    value={targetSearch}
                    onChange={(e) => {
                      setTargetSearch(e.target.value);
                      setTarget('');
                    }}
                    placeholder="输入姓名或登录名"
                  />
                </label>
                <label>
                  接任接口人
                  <Select value={target} onChange={(e) => setTarget(e.target.value)}>
                    <option value="">请选择</option>
                    {targets.map((u) => (
                      <option key={u.id} value={u.id}>
                        {u.displayName}（{u.loginName}）
                      </option>
                    ))}
                  </Select>
                </label>
                <p className="subtle">
                  选择交接省份；只交接接口人节点的待审申请，保留审批期限与原始材料。
                </p>
              </>
            )}
            <div className="province-choice-grid">
              {configuration?.provinces
                .filter((p) => !transfer || editor.reviewerProvinceIds.includes(p.id))
                .map((p) => {
                  const owner = owners.find((o) => o.provinceId === p.id);
                  const disabled = !transfer && !!owner && owner.reviewerId !== editor.id;
                  return (
                    <label
                      key={p.id}
                      className={disabled ? 'province-choice disabled' : 'province-choice'}
                    >
                      <input
                        type="checkbox"
                        disabled={disabled || busy}
                        checked={ids.includes(p.id)}
                        onChange={(e) =>
                          setIds((old) =>
                            e.target.checked ? [...old, p.id] : old.filter((id) => id !== p.id),
                          )
                        }
                      />
                      <span>
                        {p.name}
                        <small>
                          {owner ? `已绑定：${owner.displayName}（${owner.loginName}）` : '未分配'}
                        </small>
                      </span>
                    </label>
                  );
                })}
            </div>
            <div className="dialog-actions">
              <button
                type="button"
                className="secondary-button"
                onClick={() => setEditor(null)}
                disabled={busy}
              >
                取消
              </button>
              <button
                className="primary-button"
                disabled={busy || (transfer && (!ids.length || !target))}
              >
                {' '}
                {busy ? '保存中…' : transfer ? '确认交接' : '保存配置'}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </>
  );
}
