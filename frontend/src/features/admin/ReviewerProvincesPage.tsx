import { useCallback, useEffect, useRef, useState } from 'react';
import { adminApi, type AdminAccount, type AdminConfiguration } from './api';
import type { Page } from '../../shared/api/http';
import { Feedback, Loading } from '../../shared/ui/Feedback';

type PendingMapping = { reviewerId: string; version: number; provinceIds: string[] };

/** 编辑集与当前分页分离，切换页面不丢失尚未保存的映射。 */
export function ReviewerProvincesPage() {
  const [configuration, setConfiguration] = useState<AdminConfiguration | null>(null);
  const [accounts, setAccounts] = useState<Page<AdminAccount> | null>(null);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [pending, setPending] = useState<Record<string, PendingMapping>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const errorSummary = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (error) errorSummary.current?.focus();
  }, [error]);
  const load = useCallback(async () => {
    const [config, users] = await Promise.all([
      adminApi.configuration(),
      adminApi.accounts(query, 'true', page, 'INTERFACE_REVIEWER'),
    ]);
    setConfiguration(config);
    setAccounts(users);
  }, [page, query]);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    Promise.all([
      adminApi.configuration(),
      adminApi.accounts(query, 'true', page, 'INTERFACE_REVIEWER'),
    ])
      .then(([config, users]) => {
        if (active) {
          setConfiguration(config);
          setAccounts(users);
        }
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '接口人范围加载失败');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [page, query]);
  const changed = Object.values(pending);
  function toggle(user: AdminAccount, provinceId: string, checked: boolean) {
    const original = pending[user.id] ?? {
      reviewerId: user.id,
      version: user.version,
      provinceIds: user.reviewerProvinceIds,
    };
    const ids = checked
      ? [...original.provinceIds, provinceId]
      : original.provinceIds.filter((id) => id !== provinceId);
    setPending((previous) => {
      const next = { ...previous };
      const same =
        ids.length === user.reviewerProvinceIds.length &&
        ids.every((id) => user.reviewerProvinceIds.includes(id));
      if (same && original.version === user.version) delete next[user.id];
      else next[user.id] = { ...original, provinceIds: ids };
      return next;
    });
    setNotice('');
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">REVIEWER SCOPE</span>
          <h1>接口人范围</h1>
          <p>按接口人配置一个或多个负责省份。未配置时没有项目权限；范围变化会撤销其全部旧会话。</p>
        </div>
      </div>
      {error && (
        <div ref={errorSummary} tabIndex={-1}>
          <Feedback tone="error">
            {error}。版本冲突时请先核对账号是否已被其他管理员修改，再放弃旧编辑并刷新。
          </Feedback>
        </div>
      )}
      {notice && <Feedback tone="success">{notice}</Feedback>}
      <form
        className="filter-bar"
        onSubmit={(event) => {
          event.preventDefault();
          setQuery(search);
          setPage(0);
        }}
      >
        <label>
          搜索接口人
          <input
            maxLength={100}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
        <button className="secondary-button" disabled={loading || saving}>
          查询接口人
        </button>
      </form>
      <section className="panel reviewer-save-bar">
        <p>
          当前有 {changed.length}{' '}
          位接口人的范围尚未保存。切换分页或查询条件会保留编辑；关闭页面前请保存。
        </p>
        <div className="form-actions">
          <button
            className="secondary-button"
            type="button"
            disabled={saving}
            onClick={async () => {
              if (changed.length && !window.confirm('放弃所有尚未保存的范围修改并重新读取？'))
                return;
              setPending({});
              setError('');
              setLoading(true);
              try {
                await load();
              } catch (cause) {
                setError(cause instanceof Error ? cause.message : '刷新失败');
              } finally {
                setLoading(false);
              }
            }}
          >
            放弃修改并刷新
          </button>
          <button
            className="primary-button"
            type="button"
            disabled={saving || loading || changed.length === 0 || changed.length > 200}
            onClick={async () => {
              setSaving(true);
              setError('');
              setNotice('');
              try {
                await adminApi.saveReviewerMappings(changed);
                setPending({});
                setNotice('负责省份已保存，范围变化的接口人需要重新登录。');
                await load();
              } catch (cause) {
                setError(cause instanceof Error ? cause.message : '保存失败');
              } finally {
                setSaving(false);
              }
            }}
          >
            {saving ? '正在保存…' : '批量保存范围'}
          </button>
        </div>
        {changed.length > 200 && (
          <Feedback tone="error">每批最多修改 200 位接口人，请分批保存。</Feedback>
        )}
      </section>
      {loading ? (
        <Loading />
      ) : (
        accounts?.items.map((user) => (
          <section
            className="panel reviewer-scope-card"
            key={user.id}
            aria-label={`${user.displayName}的负责省份`}
          >
            <h2>{user.displayName}</h2>
            <p>
              {user.loginName} · {pending[user.id] ? '有未保存修改' : '已保存'}
            </p>
            {user.roleCodes.includes('SYSTEM_ADMIN') ? (
              <Feedback>管理员不参与业务审批，不能配置接口人负责范围。</Feedback>
            ) : (
              <fieldset disabled={saving} className="change-fieldset">
                <legend>负责省份</legend>
                <div className="admin-role-options">
                  {configuration?.provinces.map((province) => (
                    <label className="check-label" key={province.id}>
                      <input
                        type="checkbox"
                        checked={(
                          pending[user.id]?.provinceIds ?? user.reviewerProvinceIds
                        ).includes(province.id)}
                        onChange={(event) => toggle(user, province.id, event.target.checked)}
                      />
                      {province.name}
                    </label>
                  ))}
                </div>
              </fieldset>
            )}
          </section>
        ))
      )}
      {!loading && accounts?.items.length === 0 && (
        <Feedback>没有符合条件的已启用接口人，请先在账号与组织中创建或启用账号。</Feedback>
      )}
      {accounts && (
        <div className="pager admin-pager">
          <span>
            共 {accounts.totalElements} 位接口人 · 第 {page + 1} 页
          </span>
          <button
            className="secondary-button"
            type="button"
            disabled={page === 0 || loading || saving}
            onClick={() => setPage(page - 1)}
          >
            上一页
          </button>
          <button
            className="secondary-button"
            type="button"
            disabled={page + 1 >= accounts.totalPages || loading || saving}
            onClick={() => setPage(page + 1)}
          >
            下一页
          </button>
        </div>
      )}
    </>
  );
}
