import { MonthPicker } from '../../shared/ui/MonthPicker';
import { useBatchDelete } from '../../shared/ui/useBatchDelete';
import './production.css';
import { ListPagination } from '../../shared/ui/ListPagination';
import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import type { Page } from '../../shared/api/http';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { Select } from '../../shared/ui/Select';
import { Modal } from '../../shared/ui/Modal';
import { ImportWizardPage } from '../../shared/imports/ImportWizardPage';
import { productionApi, productionImportApi, categories, type Plan, type Reference } from './api';
import { PlanForm } from './PlanForm';
import { expandScientificAmount, plainAmount } from './amounts';

export function ProductionListPage({ admin }: { admin: boolean }) {
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
    setError('');
  }, [query]);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setDraft(Object.fromEntries(new URLSearchParams(query)));
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
  }, [load, query]);
  const batch = useBatchDelete(admin, 'PRODUCTION', query, result?.items ?? [], async () => {
    if (result?.page) setParams({ ...Object.fromEntries(params), page: '0' });
    else await load();
  });
  const columnWidths = [
    96,
    100,
    120,
    300,
    112,
    140,
    140,
    140,
    140,
    100,
    240,
    200,
    ...(reference?.writable ? [88] : []),
  ];
  const tableWidth = columnWidths.reduce((total, width) => total + width, admin ? 44 : 0);
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
      <div className="page-heading production-list-heading">
        <div>
          <p>按月份维护产品排产，金额单位：万元。</p>
        </div>
        {reference?.writable && (
          <div className="action-row">
            <Link
              className="secondary-button"
              to="/scheduling/import"
              state={{ listSearch: query }}
            >
              排产导入
            </Link>
            <Link className="primary-button" to="/scheduling/new" state={{ listSearch: query }}>
              新增排产
            </Link>
          </div>
        )}
      </div>
      {error && !action && <Feedback tone="error">{error}</Feedback>}
      {notice && <Feedback tone="success">{notice}</Feedback>}
      <form
        className="filter-bar production-filters"
        aria-label="排产筛选"
        onSubmit={(e) => {
          e.preventDefault();
          const next = new URLSearchParams();
          Object.entries(draft)
            .filter(([key, value]) => key !== 'page' && value)
            .forEach(([key, value]) => next.set(key, value));
          setParams(next);
        }}
      >
        <div className="month-filter-field">
          排产月份
          <MonthPicker
            aria-label="排产月份"
            value={draft.month ?? ''}
            onChange={(selectedMonth) => change('month', selectedMonth)}
          />
        </div>
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
        <label className="production-search">
          项目 / 客户 / 接口人
          <input
            value={draft.search ?? ''}
            maxLength={300}
            onChange={(e) => change('search', e.target.value)}
            placeholder="输入关键词"
          />
        </label>
        <div className="production-filter-actions">
          <button className="primary-button" disabled={loading}>
            查询
          </button>
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
        </div>
      </form>
      {loading ? (
        <Loading />
      ) : (
        <section
          className={`panel table-panel production-list-panel ${reference?.writable ? 'production-list-writable' : ''}`}
        >
          <div className="production-list-toolbar">
            <h2>
              排产明细 <span>{result?.totalElements ?? 0} 条</span>
            </h2>
            <span>左右滚动查看全部字段（金额单位：万元）</span>
          </div>
          {batch.toolbar}
          <div
            className="table-scroll production-list-scroll sticky-actions-scroll"
            role="region"
            aria-label="排产明细横向滚动区域"
            tabIndex={0}
          >
            <table
              className="production-table"
              aria-label="排产计划清单"
              style={{
                width: tableWidth,
                minWidth: tableWidth,
              }}
            >
              <colgroup>
                {admin && <col style={{ width: 44 }} />}
                {columnWidths.map((width, index) => (
                  <col key={index} style={{ width }} />
                ))}
              </colgroup>
              <thead>
                <tr>
                  {batch.head}
                  <th>接口人</th>
                  <th>排产月份</th>
                  <th>省份 / 地市</th>
                  <th>项目名称 / 客户名称</th>
                  <th>三化类别</th>
                  {categories.map((c) => (
                    <th key={c.code} className="numeric">
                      {c.label}
                      <small>总排产金额（万元）</small>
                    </th>
                  ))}
                  <th>项目状态</th>
                  <th>产品简介</th>
                  <th>备注</th>
                  {reference?.writable && <th className="sticky-list-action">操作</th>}
                </tr>
              </thead>
              <tbody>
                {result?.items.map((plan) => (
                  <tr key={plan.id}>
                    {batch.cell(plan)}
                    <td>{plan.reviewerName}</td>
                    <td className="production-month">{plan.planMonth}</td>
                    <td>
                      {plan.provinceName}
                      <small>{plan.cityName}</small>
                    </td>
                    <td>
                      <strong>{plan.projectName}</strong>
                      {plan.customerName && <small>{plan.customerName}</small>}
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
                        {plainAmount(plan[c.amount] ?? '—')}
                      </td>
                    ))}
                    <td>{plan.projectStatus || '—'}</td>
                    <td className="production-remark">{plan.productIntroduction || '—'}</td>
                    <td className="production-remark">{plan.remark || '—'}</td>
                    {reference?.writable && (
                      <td className="sticky-list-action">
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
          <ListPagination
            label="排产计划"
            disabled={loading}
            page={result?.page ?? 0}
            totalPages={result?.totalPages ?? 0}
            totalElements={result?.totalElements ?? 0}
            onPageChange={paginate}
          />
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
function useProductionListReturn() {
  const { state } = useLocation();
  return typeof state?.listSearch === 'string' && state.listSearch
    ? '/scheduling?' + state.listSearch
    : '/scheduling';
}

export function NewProductionPage() {
  const backTo = useProductionListReturn();
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
          <p>填写项目和产品金额，分类按金额自动识别。</p>
        </div>
        <Link className="secondary-button" to={backTo}>
          返回排产计划
        </Link>
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
            onCancel={() => navigate(backTo)}
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
const amountLabels: Record<string, string> = {
  '工业专网排产（万元）': '工业专网产品收入排产',
  '格物平台排产（万元）': '格物工业互联网平台-政企运营平台预计下单金额',
  '格物工业互联网平台-数智业务管理系统预计计收金额':
    '格物工业互联网平台-数智业务管理系统预计计收金额',
  '工业大数据排产（万元）': '工业大数据平台-政企运营平台预计下单金额',
  '工业大数据平台-数智业务管理系统预计计收金额': '工业大数据平台-数智业务管理系统预计计收金额',
  '万悟平台排产（万元）': '万悟工业智能体平台-政企运营平台预计下单金额',
  '万悟工业智能体平台-数智业务管理系统预计计收金额':
    '万悟工业智能体平台-数智业务管理系统预计计收金额',
};

export function ImportProductionPage() {
  const backTo = useProductionListReturn();
  const [reference, setReference] = useState<Reference>();
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    productionApi
      .reference()
      .then((result) => {
        if (active) setReference(result);
      })
      .catch((cause) => {
        if (active) setError(cause.message);
      });
    return () => {
      active = false;
    };
  }, []);
  if (!reference || !reference.writable) {
    return (
      <>
        <Link className="secondary-button" to={backTo}>
          返回排产计划
        </Link>
        {error || reference ? (
          <Feedback tone="error">{error || '当前账号仅可查看排产计划。'}</Feedback>
        ) : (
          <Loading />
        )}
      </>
    );
  }
  return (
    <ImportWizardPage
      config={{
        api: productionImportApi,
        title: '排产',
        backTo,
        fieldOptions: (field, values) => {
          if (field === '省分') return ['', ...reference.provinces.map((item) => item.name)];
          if (field !== '地市') return undefined;
          const province = reference.provinces.find((item) => item.name === values['省分']);
          return [
            '',
            ...reference.cities
              .filter((city) => city.provinceId === province?.id)
              .map((city) => city.name),
          ];
        },
        changeRowField: (values, field, value) =>
          field === '省分' ? { ...values, 省分: value, 地市: '' } : { ...values, [field]: value },
        entryHint: '从排产计划列表开始导入 Excel。',
        formatAmount: expandScientificAmount,
        hiddenRowFields: ['三化类别'],
        amountFields: Object.keys(amountLabels),
        requireAmountMapping: true,
        fieldLabel: (field) => amountLabels[field] ?? field,
        noDuplicateMessage: '未发现重复排产',
        uploadHint: '至少填写一项大于 0 的排产金额，三化类别按金额识别。',
        previewHint: '同月份、同省市、同项目与客户只允许一条排产记录。',
      }}
    />
  );
}
