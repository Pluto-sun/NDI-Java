import { MonthMultiSelect, MultiSelect } from '../../shared/ui/MultiSelect';
import { filterPage, useLedgerFilterDraft, locationOptions } from '../../shared/ui/ledgerFilters';
import {
  PersonalizedTable,
  type BusinessColumn,
} from '../../shared/list-columns/PersonalizedTable';
import { useLiveQuery } from '../../shared/api/useLiveQuery';
import { useLedgerScroll } from '../../shared/ui/useLedgerScroll';
import { useBatchDelete } from '../../shared/ui/useBatchDelete';
import './production.css';
import { ListPagination } from '../../shared/ui/ListPagination';
import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import type { Page } from '../../shared/api/http';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { Modal } from '../../shared/ui/Modal';
import { ImportWizardPage } from '../../shared/imports/ImportWizardPage';
import { productionApi, productionImportApi, categories, type Plan, type Reference } from './api';
import { PlanForm } from './PlanForm';
import { expandScientificAmount, plainAmount } from './amounts';

export function ProductionListPage({ admin, accountId }: { admin: boolean; accountId: string }) {
  const [params, setParams] = useSearchParams();
  const [reference, setReference] = useState<Reference | null>(null),
    [result, setResult] = useState<Page<Plan> | null>(null);
  const [error, setError] = useState(''),
    [notice, setNotice] = useState(''),
    [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false);
  const [action, setAction] = useState<Plan | null>(null);
  const query = params.toString();
  const draft = useLedgerFilterDraft(query);
  const cities = reference?.cities ?? [];
  const cityFilter = locationOptions(draft.values('provinceId'), cities, draft.values('cityId'));
  const listScroll = useLedgerScroll(!loading && !!result);
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
  const batch = useBatchDelete(
    admin,
    'PRODUCTION',
    query,
    result?.items ?? [],
    async () => {
      if (result?.page) setParams(filterPage(params, 0));
      else await load();
    },
    {
      count: loading || error ? 0 : (result?.totalElements ?? 0),
      filters: ['month', 'provinceId', 'cityId', 'search', 'category'],
      load: productionApi.list,
    },
  );
  function paginate(page: number) {
    setParams(filterPage(params, page));
  }
  async function remove() {
    if (!action) return;
    setBusy(true);
    try {
      await productionApi.delete(action);
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
          setParams(draft.apply());
        }}
      >
        <MonthMultiSelect
          label="排产月份"
          values={draft.values('month')}
          onChange={(values) => draft.change('month', values)}
        />
        <MultiSelect
          label="省份"
          values={draft.values('provinceId')}
          options={(reference?.provinces ?? []).map(({ id, name }) => ({ value: id, label: name }))}
          onChange={(values) => draft.provinces(values, cities)}
        />
        <MultiSelect
          label="地市"
          {...cityFilter}
          onChange={(values) => draft.change('cityId', values)}
        />
        <MultiSelect
          label="三化类别"
          values={draft.values('category')}
          options={categories.map(({ code, label }) => ({ value: code, label }))}
          onChange={(values) => draft.change('category', values)}
        />
        <label className="production-search">
          项目 / 客户 / 接口人
          <input
            value={draft.text('search')}
            maxLength={300}
            onChange={(e) => draft.change('search', e.target.value)}
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
              draft.reset();
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
          <PersonalizedTable
            list="PRODUCTION"
            accountId={accountId}
            label="排产计划清单"
            className="production-table"
            scrollRef={listScroll.table}
            scrollClassName="production-list-scroll"
            scrollLabel="排产明细横向滚动区域"
            rows={result?.items ?? []}
            actionWidth={reference?.writable ? 128 : 72}
            selection={admin ? batch : undefined}
            columns={[
              { id: 'reviewer', label: '接口人', width: 96, cell: (plan) => plan.reviewerName },
              {
                id: 'month',
                label: '排产月份',
                width: 100,
                cellClassName: 'production-month',
                cell: (plan) => plan.planMonth,
              },
              {
                id: 'location',
                label: '省份 / 地市',
                width: 120,
                cell: (plan) => (
                  <>
                    {plan.provinceName}
                    <small>{plan.cityName}</small>
                  </>
                ),
              },
              {
                id: 'project',
                label: '项目名称 / 客户名称',
                width: 300,
                cell: (plan) => (
                  <>
                    <strong>{plan.projectName}</strong>
                    {plan.customerName && <small>{plan.customerName}</small>}
                  </>
                ),
              },
              {
                id: 'category',
                label: '三化类别',
                width: 112,
                cell: (plan) => (
                  <div className="production-tags">
                    {categories
                      .filter((c) => plan.categories.includes(c.code))
                      .map((c) => (
                        <span key={c.code}>{c.label}</span>
                      ))}
                  </div>
                ),
              },
              ...categories.map<BusinessColumn<Plan>>((c) => ({
                id: c.code,
                label: c.label,
                width: 140,
                className: 'numeric',
                heading: (
                  <>
                    {c.label}
                    <small>总排产金额（万元）</small>
                  </>
                ),
                cell: (plan) => plainAmount(plan[c.amount] ?? '—'),
              })),
              {
                id: 'state',
                label: '项目状态',
                width: 100,
                cell: (plan) => plan.projectStatus || '—',
              },
              {
                id: 'introduction',
                label: '产品简介',
                width: 240,
                cellClassName: 'production-remark',
                cell: (plan) => plan.productIntroduction || '—',
              },
              {
                id: 'remark',
                label: '备注',
                width: 200,
                cellClassName: 'production-remark',
                cell: (plan) => plan.remark || '—',
              },
            ]}
            actions={(plan) => (
              <div className="admin-row-actions">
                <Link
                  className="secondary-button compact-button"
                  to={{
                    pathname: `/scheduling/${plan.id}`,
                    search: new URLSearchParams({
                      ...(query ? { from: query } : {}),
                      ...(plan.writable ? { mode: 'edit' } : {}),
                    }).toString(),
                  }}
                  state={listScroll.state}
                  onClick={listScroll.remember}
                >
                  {plan.writable ? '编辑' : '详情'}
                </Link>
                {plan.writable && (
                  <button
                    type="button"
                    className="secondary-button compact-button danger"
                    onClick={() => {
                      setError('');
                      setAction(plan);
                    }}
                  >
                    删除
                  </button>
                )}
              </div>
            )}
          />
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
      {action && (
        <Modal title="删除排产" busy={busy} onClose={() => setAction(null)}>
          {error && <Feedback tone="error">{error}</Feedback>}
          <p>
            确认删除「{action.projectName}」在 {action.planMonth} 的排产记录？
          </p>
          <div className="form-actions">
            <button className="secondary-button" onClick={() => setAction(null)} disabled={busy}>
              取消
            </button>
            <button className="primary-button" onClick={() => void remove()} disabled={busy}>
              确认删除
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
function useProductionListReturn() {
  const { state } = useLocation();
  const [params] = useSearchParams();
  const listSearch = params.get('from') ?? state?.listSearch;
  return typeof listSearch === 'string' && listSearch
    ? '/scheduling?' + listSearch.replace(/^\?/, '')
    : '/scheduling';
}

export function ProductionDetailPage() {
  const { id = '' } = useParams();
  const backTo = useProductionListReturn();
  const [params, setParams] = useSearchParams();
  const [editSnapshot, setEditSnapshot] = useState<Plan | null>(null);
  const editMode = params.get('mode') === 'edit';
  const setMode = useCallback(
    (edit: boolean) => {
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          if (edit) next.set('mode', 'edit');
          else next.delete('mode');
          return next;
        },
        { replace: true },
      );
    },
    [setParams],
  );
  function endEdit() {
    setEditSnapshot(null);
    setMode(false);
  }
  const [notice, setNotice] = useState('');
  const load = useCallback((signal: AbortSignal) => productionApi.detail(id, signal), [id]);
  const loadReference = useCallback(() => productionApi.reference(), []);
  const result = useLiveQuery(load, '排产详情加载失败');
  const plan = result.data;
  const editing = editMode && plan?.writable ? (editSnapshot ?? plan) : null;
  const reference = useLiveQuery(loadReference, '排产组织目录加载失败', !!editing);
  useEffect(() => {
    if (!editMode) {
      setEditSnapshot(null);
    } else if (plan?.writable) {
      setEditSnapshot((current) => current ?? plan);
    } else if (plan) {
      setMode(false);
    }
  }, [editMode, plan, setMode]);
  const basic = plan && [
    ['接口人', plan.reviewerName],
    ['排产月份', plan.planMonth],
    ['省份', plan.provinceName],
    ['地市', plan.cityName],
    ['项目名称', plan.projectName],
    ['客户名称', plan.customerName],
    ['项目状态', plan.projectStatus],
  ];
  return (
    <section className="ledger-detail-page">
      <div className="page-heading ledger-detail-actions">
        <Link className="secondary-button" to={backTo}>
          返回列表
        </Link>
        {plan?.writable && !editing && (
          <button
            className="secondary-button"
            onClick={() => {
              setNotice('');
              setEditSnapshot(plan);
              setMode(true);
            }}
          >
            编辑
          </button>
        )}
      </div>
      <div className="ledger-detail-body">
        {result.error && <Feedback tone="error">{result.error}</Feedback>}
        {notice && <Feedback tone="success">{notice}</Feedback>}
        {!plan && result.busy && <Loading />}
        {plan && basic && (
          <section className="panel ledger-detail">
            <div className="page-heading">
              <h2>{editing ? '编辑排产' : '基本信息'}</h2>
            </div>
            {editing ? (
              reference.error ? (
                <>
                  <Feedback tone="error">{reference.error}</Feedback>
                  <button type="button" className="secondary-button" onClick={endEdit}>
                    取消
                  </button>
                </>
              ) : reference.data ? (
                <PlanForm
                  reference={reference.data}
                  initial={editing}
                  onCancel={endEdit}
                  onSave={async (values) => {
                    await productionApi.update(editing, values);
                    await result.refresh();
                    endEdit();
                    setNotice('排产记录已更新');
                  }}
                />
              ) : (
                <Loading />
              )
            ) : (
              <>
                <dl className="facts">
                  {basic.map(([label, value]) => (
                    <div key={label}>
                      <dt>{label}</dt>
                      <dd>{value || '—'}</dd>
                    </div>
                  ))}
                </dl>
                <section className="production-amount-section" aria-label="产品金额">
                  <h3>产品金额（万元）</h3>
                  <div className="production-product-amounts">
                    {categories.map((product) => (
                      <section key={product.code} className="production-product-amount">
                        <h4>{product.label}</h4>
                        <dl className="production-product-facts">
                          {product.fields.map((key) => (
                            <div key={key}>
                              <dt>
                                {key.endsWith('OrderAmount')
                                  ? '政企运营平台预计下单金额'
                                  : '数智业务管理系统预计计收金额'}
                              </dt>
                              <dd>{plainAmount(plan[key] ?? '—')}</dd>
                            </div>
                          ))}
                          <div className="production-product-total">
                            <dt>总排产金额</dt>
                            <dd>{plainAmount(plan[product.amount] ?? '—')}</dd>
                          </div>
                        </dl>
                      </section>
                    ))}
                  </div>
                </section>
                <dl className="facts production-supplemental">
                  <div>
                    <dt>产品简介</dt>
                    <dd>{plan.productIntroduction || '—'}</dd>
                  </div>
                  <div>
                    <dt>备注</dt>
                    <dd>{plan.remark || '—'}</dd>
                  </div>
                </dl>
              </>
            )}
          </section>
        )}
      </div>
    </section>
  );
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
    <div className="form-page">
      <div className="page-heading">
        <div>
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
    </div>
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
