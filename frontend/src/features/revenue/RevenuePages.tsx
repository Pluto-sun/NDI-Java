import { MonthPicker } from '../../shared/ui/MonthPicker';
import { useBatchDelete } from '../../shared/ui/useBatchDelete';
import { revenueText } from './format';
import {
  changeSourceField,
  integratedAreaName,
  revenueProjectStatuses,
  sourceFieldOptions,
} from './sourceOptions';
import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { Select } from '../../shared/ui/Select';
import { ImportWizardPage } from '../../shared/imports/ImportWizardPage';
import { RevenueFieldsForm } from './RevenueFieldsForm';
import {
  revenueApi,
  revenueImportApi,
  stateLabels,
  money,
  type Reference,
  type Listing,
} from './api';
import './revenue.css';
import { ListPagination } from '../../shared/ui/ListPagination';
export { RevenueDetailPage } from './RevenueDetailPage';
const message = (e: unknown) => (e instanceof Error ? e.message : '操作失败，请稍后重试');

/** 表头、列宽与金额对齐使用同一份定义，避免调整列顺序时错位。 */
const revenueListColumns: { label: string; width: number; numeric?: boolean }[] = [
  { label: '费用账期', width: 90 },
  { label: '省份', width: 90 },
  { label: '地市／组织', width: 150 },
  { label: '来源项目', width: 240 },
  { label: '三化分类', width: 88 },
  { label: '客户', width: 160 },
  { label: '合同编号', width: 150 },
  { label: '条款编号', width: 72 },
  { label: '条款金额（元）', width: 200, numeric: true },
  { label: '来源本月计收（元）', width: 200, numeric: true },
  { label: '认定金额（元）', width: 200, numeric: true },
  { label: '通报项目名称', width: 220 },
  { label: '对应省分（集成）', width: 135 },
  { label: '办理状态', width: 120 },
  { label: '操作', width: 76 },
];

export function RevenueListPage({ admin }: { admin: boolean }) {
  const [params, setParams] = useSearchParams();
  const query = params.toString();
  const [draft, setDraft] = useState(() => Object.fromEntries(params));
  const [reference, setReference] = useState<Reference | null>(null),
    [data, setData] = useState<Listing | null>(null),
    [error, setError] = useState(''),
    [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  const batch = useBatchDelete(
    admin,
    'REVENUE',
    query,
    data?.items ?? [],
    () => {
      if (data?.page && data.items.length) setParams({ ...Object.fromEntries(params), page: '0' });
      else setReload((value) => value + 1);
    },
    {
      count: loading || error ? 0 : (data?.totalElements ?? 0),
      filters: ['month', 'province', 'search', 'organization', 'state', 'category'],
      load: revenueApi.list,
    },
  );
  useEffect(() => {
    let active = true;
    setLoading(true);
    setDraft(Object.fromEntries(new URLSearchParams(query)));
    Promise.all([revenueApi.reference(), revenueApi.list(new URLSearchParams(query))])
      .then(([r, d]) => {
        if (active) {
          setReference(r);
          setData(d);
          setError('');
        }
      })
      .catch((e) => {
        if (active) setError(message(e));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [query, reload]);
  const change = (field: string, value: string) => setDraft((old) => ({ ...old, [field]: value }));
  return (
    <>
      <div className="page-heading revenue-list-heading">
        <div>
          <p>按费用账期查看来源明细与认定结果，金额单位：元。</p>
        </div>
        {reference?.canCreate && (
          <div className="inline-actions">
            <Link className="secondary-button" to="/revenue/import">
              计收导入
            </Link>
            <Link className="primary-button" to="/revenue/new">
              新增计收
            </Link>
          </div>
        )}
      </div>
      {error && <Feedback tone="error">{error}</Feedback>}
      <form
        className="filter-bar revenue-list-filters"
        aria-label="计收筛选"
        onSubmit={(e) => {
          e.preventDefault();
          const next = new URLSearchParams();
          Object.entries(draft)
            .filter(([k, v]) => !['page', 'category'].includes(k) && v)
            .forEach(([k, v]) => next.set(k, v));
          const category = params.get('category');
          if (category) next.set('category', category);
          setParams(next);
        }}
      >
        <div className="month-filter-field">
          费用账期
          <MonthPicker
            aria-label="费用账期"
            value={draft.month ?? ''}
            onChange={(selectedMonth) => change('month', selectedMonth)}
          />
        </div>
        <label>
          省份／具体组织
          <Select value={draft.province ?? ''} onChange={(e) => change('province', e.target.value)}>
            <option value="">全部</option>
            {reference?.provinces.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
            <option value="ORGANIZATION">具体组织</option>
            <option value="DS_TECH">数科</option>
            <option value="UNASSIGNED">未归属</option>
          </Select>
        </label>
        <label>
          地市／组织
          <input
            value={draft.organization ?? ''}
            maxLength={300}
            placeholder="输入地市或组织名称"
            onChange={(event) => change('organization', event.target.value)}
          />
        </label>
        <label>
          办理状态
          <Select value={draft.state ?? ''} onChange={(e) => change('state', e.target.value)}>
            <option value="">全部状态</option>
            {Object.entries(stateLabels).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </Select>
        </label>
        <label className="revenue-search">
          项目／客户／合同／条款
          <input
            value={draft.search ?? ''}
            maxLength={300}
            placeholder="输入项目、客户、合同或条款关键词"
            onChange={(e) => change('search', e.target.value)}
          />
        </label>
        <div className="inline-actions revenue-filter-actions">
          <button className="primary-button" disabled={loading}>
            查询
          </button>
          <button
            type="button"
            className="secondary-button"
            onClick={() => {
              setDraft({});
              setParams(params.get('category') ? { category: params.get('category')! } : {});
            }}
          >
            重置
          </button>
        </div>
      </form>
      {data ? (
        <>
          <section className="revenue-summary" aria-label="当前筛选汇总" aria-busy={loading}>
            <div>
              <span>明细数</span>
              <strong>{data.summary.total}</strong>
            </div>
            <div>
              <span>未认定（含待审）</span>
              <strong>{data.summary.unrecognized}</strong>
            </div>
            <div>
              <span>已认定（含修改待审）</span>
              <strong>{data.summary.recognized}</strong>
            </div>
            <div>
              <span>待审核</span>
              <strong>{data.summary.pending}</strong>
            </div>
            <div className="revenue-summary-amount">
              <span>生效认定金额（元）</span>
              <strong>{money(data.summary.recognizedAmount)}</strong>
            </div>
          </section>
          <section
            className="panel revenue-list-panel"
            aria-labelledby="revenue-list-title"
            aria-busy={loading}
          >
            <div className="revenue-list-toolbar">
              <h2 id="revenue-list-title">
                计收明细 <span>{data.totalElements} 条</span>
              </h2>
              <span className="subtle" role="status">
                {loading ? '正在更新…' : '左右滚动查看全部字段（金额单位：元）'}
              </span>
            </div>
            {batch.toolbar}
            <div
              className="table-scroll revenue-list-scroll sticky-actions-scroll"
              role="region"
              aria-label="计收明细横向滚动区域"
              tabIndex={0}
            >
              <table
                className="revenue-list-table"
                aria-label="计收明细清单"
                style={{
                  minWidth: revenueListColumns.reduce(
                    (total, column) => total + column.width,
                    admin ? 44 : 0,
                  ),
                }}
              >
                <colgroup>
                  {admin && <col style={{ width: 44 }} />}
                  {revenueListColumns.map((column) => (
                    <col key={column.label} style={{ width: column.width }} />
                  ))}
                </colgroup>
                <thead>
                  <tr>
                    {batch.head}
                    {revenueListColumns.map((column) => (
                      <th
                        key={column.label}
                        scope="col"
                        className={
                          column.label === '操作'
                            ? 'sticky-list-action'
                            : column.numeric
                              ? 'revenue-numeric'
                              : undefined
                        }
                      >
                        {column.label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((e) => (
                    <tr key={e.id}>
                      {batch.cell(e)}
                      <td>{revenueText(e.feeMonth)}</td>
                      <td title={!e.source['省份名称'] ? '未归属' : undefined}>
                        {revenueText(e.source['省份名称'])}
                      </td>
                      <td>{revenueText(e.source['地市/组织名称'])}</td>
                      <td className="revenue-project-name">{revenueText(e.source['项目名称'])}</td>
                      <td>{revenueText(e.source['三化分类'])}</td>
                      <td>{revenueText(e.source['客户名称'] || e.source['自然客户名称'])}</td>
                      <td>{revenueText(e.contractNo)}</td>
                      <td>{revenueText(e.clauseNo)}</td>
                      <td className="revenue-numeric">{money(e.clauseAmount)}</td>
                      <td className="revenue-numeric">{money(e.sourceAmount)}</td>
                      <td className="revenue-numeric revenue-recognized-amount">
                        {money(e.recognition?.amount)}
                      </td>
                      <td>{revenueText(e.recognition?.reportedProjectName)}</td>
                      <td>{revenueText(integratedAreaName(e.recognition, reference))}</td>
                      <td>
                        <span
                          className={`status-badge revenue-state revenue-state-${e.state.toLowerCase()}`}
                        >
                          {stateLabels[e.state]}
                        </span>
                      </td>
                      <td className="sticky-list-action">
                        <Link
                          className="secondary-button compact-button"
                          title={`查看计收详情：${e.source['项目名称'] || e.contractNo || e.feeMonth || '未填写费用账期'}`}
                          to={`/revenue/${e.id}?from=${encodeURIComponent('/revenue' + (query ? '?' + query : ''))}`}
                        >
                          详情
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {data.items.length === 0 && (
              <div className="empty-state">
                <strong>暂无符合条件的计收明细</strong>
                <p>请调整筛选条件，或重置后查看全部记录。</p>
              </div>
            )}
            <ListPagination
              label="计收列表"
              disabled={loading}
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              onPageChange={(page) => {
                const next = new URLSearchParams(params);
                next.set('page', String(page));
                setParams(next);
              }}
            />
            <p className="revenue-list-note">
              未认定与已认定均包含各自的待审记录，待审核不单独累加。未归属记录仅全国范围账号可查看，由接口人发起认定，集团审核。
            </p>
          </section>
        </>
      ) : (
        !error && <Loading />
      )}
    </>
  );
}
export function NewRevenuePage() {
  const navigate = useNavigate();
  const [reference, setReference] = useState<Reference | null>(null),
    [values, setValues] = useState<Record<string, string>>({}),
    [error, setError] = useState(''),
    [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    revenueApi
      .reference()
      .then((r) => {
        if (active) setReference(r);
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, []);
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const entry = await revenueApi.create(values);
      navigate(`/revenue/${entry.id}`);
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="form-page">
      <div className="page-heading">
        <div className="inline-actions">
          <Link className="secondary-button" to="/revenue">
            返回计收列表
          </Link>
          {reference?.canCreate && (
            <Link className="primary-button" to="/revenue/import">
              计收导入
            </Link>
          )}
        </div>
      </div>
      {error && <Feedback tone="error">{error}</Feedback>}
      {reference?.canCreate ? (
        <form className="panel form-panel" onSubmit={submit}>
          <p>
            新增可选择数字化或智能化。网络化由系统管理员通过 Excel
            导入。认定由接口人发起并指定集团审核人。金额单位为元。
          </p>
          <p className="subtle">
            月账期、费用账期、合同编号、条款编号、业务号码/项目编号、自然客户名称、产品编码七项组合唯一；条款编号、自然客户名称和产品编码留空或
            -- 时按空字符串参与判重。
          </p>
          <RevenueFieldsForm
            reference={reference}
            values={values}
            onChange={(k, v) => setValues((old) => ({ ...old, [k]: v }))}
          />
          <div className="dialog-actions">
            <button className="primary-button" disabled={busy}>
              {busy ? '保存中…' : '创建计收明细'}
            </button>
          </div>
        </form>
      ) : reference ? (
        <Feedback tone="error">当前账号不能创建或导入计收。</Feedback>
      ) : (
        !error && <Loading />
      )}
    </div>
  );
}
export function ImportRevenuePage() {
  const [reference, setReference] = useState<Reference | null>(null);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true;
    setError('');
    revenueApi
      .reference()
      .then((value) => {
        if (active) setReference(value);
      })
      .catch((cause) => {
        if (active) setError(message(cause));
      });
    return () => {
      active = false;
    };
  }, [revision]);
  if (error)
    return (
      <Feedback tone="error">
        {error}
        <button className="text-button" onClick={() => setRevision((value) => value + 1)}>
          重新加载导入字典
        </button>
      </Feedback>
    );
  if (!reference) return <Loading />;
  return (
    <ImportWizardPage
      config={{
        api: revenueImportApi,
        title: '计收',
        amountUnit: '元',
        emptyValueLabel: '--',
        backTo: '/revenue',
        fieldOptions: (field, values) =>
          field === '对应省分（集成）'
            ? [
                '',
                ...reference.provinces.map((province) => province.name),
                ...reference.integratedOrganizations,
              ]
            : field === '项目状态'
              ? ['', ...revenueProjectStatuses]
              : sourceFieldOptions(field, values, reference),
        changeRowField: (values, field, value) =>
          changeSourceField(values, field, value, reference),
        amountFields: ['条款金额', '产品本月计收不含税额/标品含税收入', '认定金额'],
        rowTitleFields: ['项目名称', '自然客户名称', '合同编号', '条款编号'],
        rowLocationFields: ['省份名称', '地市/组织名称'],
        noDuplicateMessage:
          '月账期、费用账期、合同编号、条款编号、业务号码/项目编号、自然客户名称、产品编码七项组合唯一；条款编号、自然客户名称和产品编码留空或 -- 时按空字符串参与判重，同名项目允许多条明细。',
        uploadHint:
          '金额以元填写，系统四舍五入到两位小数，负金额可用于冲减。网络化仅管理员可导入，须填写舍入后非零的认定金额和对应省分（集成）；合同编号、通报项目名称可留空。标准省份的网络化可留空地市，具体组织须填组织全称。数字化和智能化直接认定时须填写通报项目名称。',
        previewHint: '金额依次显示条款金额、来源计收金额和认定金额，入库时四舍五入到分。',
      }}
    />
  );
}
