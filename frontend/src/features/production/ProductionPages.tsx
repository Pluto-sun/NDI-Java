import './production.css';
import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import type { Page } from '../../shared/api/http';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { Select } from '../../shared/ui/Select';
import { Modal } from '../../shared/ui/Modal';
import { ImportWizardPage } from '../../shared/imports/ImportWizardPage';
import { productionApi, productionImportApi, categories, type Plan, type Reference } from './api';
import { PlanForm } from './PlanForm';

export function ProductionListPage() {
  const [params, setParams] = useSearchParams();
  const [draft, setDraft] = useState(() => Object.fromEntries(params));
  const [reference, setReference] = useState<Reference | null>(null),
    [result, setResult] = useState<Page<Plan> | null>(null);
  const [error, setError] = useState(''),
    [notice, setNotice] = useState(''),
    [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false);
  const [action, setAction] = useState<{ kind: 'edit' | 'delete'; plan: Plan } | null>(null);
  const query = params.toString();
  const load = useCallback(async () => {
    const request = new URLSearchParams(query);
    request.set('size', '20');
    const [r, p] = await Promise.all([productionApi.reference(), productionApi.list(request)]);
    setReference(r);
    setResult(p);
  }, [query]);
  useEffect(() => {
    let active = true;
    setLoading(true);
    load()
      .catch((c) => {
        if (active) setError(c.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [load]);
  function change(key: string, value: string) {
    setDraft((old) => ({ ...old, [key]: value, ...(key === 'provinceId' ? { cityId: '' } : {}) }));
  }
  function paginate(page: number) {
    const next = new URLSearchParams(params);
    next.set('page', String(page));
    setParams(next);
  }
  async function remove() {
    if (!action) return;
    setBusy(true);
    try {
      await productionApi.delete(action.plan);
      setAction(null);
      setNotice('排产记录已删除');
      await load();
    } catch (c) {
      setError(c instanceof Error ? c.message : '删除失败');
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">PRODUCTION PLAN</span>
          <h1>排产计划</h1>
          <p>按月份维护产品排产，金额单位：万元。</p>
        </div>
        {reference?.writable && (
          <Link className="primary-button" to="/scheduling/new">
            新增排产
          </Link>
        )}
      </div>
      {error && !action && <Feedback tone="error">{error}</Feedback>}
      {notice && <Feedback tone="success">{notice}</Feedback>}
      <form
        className="filter-bar production-filters"
        onSubmit={(e) => {
          e.preventDefault();
          const next = new URLSearchParams();
          Object.entries(draft)
            .filter(([key, value]) => key !== 'page' && value)
            .forEach(([key, value]) => next.set(key, value));
          setParams(next);
        }}
      >
        <label>
          排产月份
          <input
            type="month"
            value={draft.month ?? ''}
            onChange={(e) => change('month', e.target.value)}
          />
        </label>
        <label>
          省份
          <Select
            value={draft.provinceId ?? ''}
            onChange={(e) => change('provinceId', e.target.value)}
          >
            <option value="">全部省份</option>
            {reference?.provinces.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </Select>
        </label>
        <label>
          地市
          <Select value={draft.cityId ?? ''} onChange={(e) => change('cityId', e.target.value)}>
            <option value="">全部地市</option>
            {reference?.cities
              .filter((c) => !draft.provinceId || c.provinceId === draft.provinceId)
              .map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
          </Select>
        </label>
        <label>
          三化类别
          <Select value={draft.category ?? ''} onChange={(e) => change('category', e.target.value)}>
            <option value="">全部类别</option>
            {categories.map((c) => (
              <option key={c.code} value={c.code}>
                {c.label}
              </option>
            ))}
          </Select>
        </label>
        <label>
          项目 / 客户 / 接口人
          <input
            value={draft.search ?? ''}
            maxLength={300}
            onChange={(e) => change('search', e.target.value)}
            placeholder="输入关键词"
          />
        </label>
        <button className="primary-button">查询</button>
        <button
          type="button"
          className="secondary-button"
          onClick={() => {
            setDraft({});
            setParams({});
          }}
        >
          重置
        </button>
      </form>
      {loading ? (
        <Loading />
      ) : (
        <section className="panel table-panel">
          <div className="table-scroll">
            <table className="production-table">
              <thead>
                <tr>
                  <th>接口人</th>
                  <th>排产月份</th>
                  <th>省份 / 地市</th>
                  <th>项目名称 / 客户名称</th>
                  <th>三化类别</th>
                  {categories.map((c) => (
                    <th key={c.code}>
                      {c.field}
                      <small>万元</small>
                    </th>
                  ))}
                  <th>项目状态</th>
                  {reference?.writable && <th>操作</th>}
                </tr>
              </thead>
              <tbody>
                {result?.items.map((plan) => (
                  <tr key={plan.id}>
                    <td>{plan.reviewerName}</td>
                    <td>{plan.planMonth}</td>
                    <td>
                      {plan.provinceName}
                      <small>{plan.cityName}</small>
                    </td>
                    <td>
                      <strong>{plan.projectName}</strong>
                      <small>{plan.customerName}</small>
                    </td>
                    <td>
                      <div className="production-tags">
                        {categories
                          .filter((c) => plan.categories.includes(c.code))
                          .map((c) => (
                            <span key={c.code}>{c.label}</span>
                          ))}
                      </div>
                    </td>
                    {categories.map((c) => (
                      <td key={c.code} className="numeric">
                        {plan[c.amount] === null
                          ? '—'
                          : Number(plan[c.amount]).toLocaleString('zh-CN', {
                              minimumFractionDigits: 2,
                              maximumFractionDigits: 2,
                            })}
                      </td>
                    ))}
                    <td>{plan.projectStatus || '—'}</td>
                    {reference?.writable && (
                      <td>
                        <div className="admin-row-actions">
                          <button
                            className="text-button"
                            onClick={() => {
                              setError('');
                              setAction({ kind: 'edit', plan });
                            }}
                          >
                            编辑
                          </button>
                          <button
                            className="text-button danger"
                            onClick={() => {
                              setError('');
                              setAction({ kind: 'delete', plan });
                            }}
                          >
                            删除
                          </button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!result?.items.length && (
            <div className="empty-state">暂无排产记录，调整筛选条件或新增排产。</div>
          )}
          <div className="admin-pager">
            <span>共 {result?.totalElements ?? 0} 条</span>
            <button
              className="secondary-button"
              disabled={!result?.page}
              onClick={() => paginate((result?.page ?? 0) - 1)}
            >
              上一页
            </button>
            <span>第 {(result?.page ?? 0) + 1} 页</span>
            <button
              className="secondary-button"
              disabled={!result || result.page + 1 >= result.totalPages}
              onClick={() => paginate((result?.page ?? 0) + 1)}
            >
              下一页
            </button>
          </div>
        </section>
      )}
      {action && reference && (
        <Modal
          title={action.kind === 'edit' ? '编辑排产' : '删除排产'}
          busy={busy}
          onClose={() => setAction(null)}
        >
          {error && <Feedback tone="error">{error}</Feedback>}
          {action.kind === 'edit' ? (
            <PlanForm
              reference={reference}
              initial={action.plan}
              onCancel={() => setAction(null)}
              onSave={async (values) => {
                await productionApi.update(action.plan, values);
                setAction(null);
                setNotice('排产记录已更新');
                await load();
              }}
            />
          ) : (
            <>
              <p>
                确认删除「{action.plan.projectName}」在 {action.plan.planMonth} 的排产记录？
              </p>
              <div className="form-actions">
                <button
                  className="secondary-button"
                  onClick={() => setAction(null)}
                  disabled={busy}
                >
                  取消
                </button>
                <button className="primary-button" onClick={() => void remove()} disabled={busy}>
                  确认删除
                </button>
              </div>
            </>
          )}
        </Modal>
      )}
    </>
  );
}
export function NewProductionPage() {
  const navigate = useNavigate();
  const [reference, setReference] = useState<Reference | null>(null),
    [error, setError] = useState('');
  useEffect(() => {
    productionApi
      .reference()
      .then(setReference)
      .catch((c) => setError(c.message));
  }, []);
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">NEW PRODUCTION</span>
          <h1>新增排产</h1>
          <p>手工填写或通过 Excel 批量导入。</p>
        </div>
        {reference?.writable && (
          <Link className="secondary-button" to="/scheduling/import">
            排产导入
          </Link>
        )}
      </div>
      {error ? (
        <Feedback tone="error">{error}</Feedback>
      ) : !reference ? (
        <Loading />
      ) : !reference.writable ? (
        <Feedback tone="error">当前账号仅可查看排产计划。</Feedback>
      ) : (
        <section className="panel form-panel">
          <PlanForm
            reference={reference}
            onCancel={() => navigate('/scheduling')}
            onSave={async (values) => {
              await productionApi.create(values);
              navigate('/scheduling');
            }}
          />
        </section>
      )}
    </>
  );
}
export function ImportProductionPage() {
  return (
    <ImportWizardPage
      config={{
        api: productionImportApi,
        title: '排产',
        backTo: '/scheduling/new',
        amountFields: [
          '工业专网排产（万元）',
          '格物平台排产（万元）',
          '工业大数据排产（万元）',
          '万悟平台排产（万元）',
        ],
        noDuplicateMessage: '未发现重复排产',
        uploadHint: '三化类别可用分号分隔；至少填写一项排产金额。',
        previewHint: '同月份、同省市、同项目与客户只允许一条排产记录。',
      }}
    />
  );
}
