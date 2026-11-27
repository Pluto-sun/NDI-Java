import { useLedgerScroll } from '../../shared/ui/useLedgerScroll';
import { RecognitionForm } from './RevenueDetailPage';
import { MonthMultiSelect, MultiSelect } from '../../shared/ui/MultiSelect';
import { filterPage, useLedgerFilterDraft } from '../../shared/ui/ledgerFilters';
import { PersonalizedTable } from '../../shared/list-columns/PersonalizedTable';
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
import { ImportWizardPage } from '../../shared/imports/ImportWizardPage';
import { RevenueFieldsForm } from './RevenueFieldsForm';
import {
  revenueApi,
  revenueImportApi,
  stateLabels,
  money,
  type Reference,
  type Listing,
  type Entry,
} from './api';
import './revenue.css';
import { ListPagination } from '../../shared/ui/ListPagination';
export { RevenueDetailPage } from './RevenueDetailPage';
const message = (e: unknown) => (e instanceof Error ? e.message : '操作失败，请稍后重试');

export function RevenueListPage({ admin, accountId }: { admin: boolean; accountId: string }) {
  const [params, setParams] = useSearchParams();
  const query = params.toString();
  const draft = useLedgerFilterDraft(query);
  const [reference, setReference] = useState<Reference | null>(null),
    [data, setData] = useState<Listing | null>(null),
    [error, setError] = useState(''),
    [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  const [recognizing, setRecognizing] = useState<Entry | null>(null);
  const [notice, setNotice] = useState('');
  const listScroll = useLedgerScroll(!loading && !!data);
  const batch = useBatchDelete(
    admin,
    'REVENUE',
    query,
    data?.items ?? [],
    () => {
      if (data?.page && data.items.length) setParams(filterPage(params, 0));
      else setReload((value) => value + 1);
    },
    {
      count: loading || error ? 0 : (data?.totalElements ?? 0),
      filters: ['month', 'feeMonth', 'province', 'search', 'organization', 'state', 'category'],
      load: revenueApi.list,
    },
  );
  useEffect(() => {
    let active = true;
    setLoading(true);
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
      {notice && <Feedback tone="success">{notice}</Feedback>}
      <form
        className="filter-bar revenue-list-filters"
        aria-label="计收筛选"
        onSubmit={(e) => {
          e.preventDefault();
          setParams(draft.apply());
        }}
      >
        <MonthMultiSelect
          label="费用账期"
          values={[...new Set([...draft.values('month'), ...draft.values('feeMonth')])]}
          onChange={(values) => {
            draft.change('feeMonth', []);
            draft.change('month', values);
          }}
        />
        <MultiSelect
          label="三化分类"
          values={draft.values('category')}
          options={['网络化', '数字化', '智能化'].map((label) => ({ value: label, label }))}
          onChange={(values) => draft.change('category', values)}
        />
        <MultiSelect
          label="省份／具体组织"
          values={draft.values('province')}
          options={[
            ...(reference?.filterProvinces ?? reference?.provinces ?? []).map(({ id, name }) => ({
              value: id,
              label: name,
            })),
            ...(reference?.specialSources === false
              ? []
              : [
                  { value: 'ORGANIZATION', label: '具体组织' },
                  { value: 'DS_TECH', label: '数科' },
                  { value: 'UNASSIGNED', label: '未归属' },
                ]),
          ]}
          onChange={(values) => draft.change('province', values)}
        />
        <label>
          地市／组织
          <input
            value={draft.text('organization')}
            maxLength={300}
            placeholder="输入地市或组织名称"
            onChange={(event) => draft.change('organization', event.target.value)}
          />
        </label>
        <MultiSelect
          label="办理状态"
          values={draft.values('state')}
          options={Object.entries(stateLabels).map(([value, label]) => ({ value, label }))}
          onChange={(values) => draft.change('state', values)}
        />
        <label className="revenue-search">
          项目／客户／合同／条款
          <input
            value={draft.text('search')}
            maxLength={300}
            placeholder="输入项目、客户、合同或条款关键词"
            onChange={(e) => draft.change('search', e.target.value)}
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
              draft.reset();
              setParams({});
            }}
          >
            重置
          </button>
        </div>
      </form>
      {recognizing && reference && (
        <RecognitionForm
          entry={recognizing}
          reference={reference}
          onClose={() => setRecognizing(null)}
          onSaved={() => {
            setRecognizing(null);
            setNotice('认定申请已提交');
            setReload((current) => current + 1);
          }}
        />
      )}
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
            <PersonalizedTable
              list="REVENUE"
              accountId={accountId}
              label="计收明细清单"
              className="revenue-list-table"
              scrollClassName="revenue-list-scroll"
              scrollLabel="计收明细横向滚动区域"
              rows={data.items}
              scrollRef={listScroll.table}
              actionWidth={data.items.some((entry) => entry.canRecognize) ? 132 : 76}
              selection={admin ? batch : undefined}
              columns={[
                {
                  id: 'fee-month',
                  label: '费用账期',
                  width: 90,
                  cell: (e) => revenueText(e.feeMonth),
                },
                {
                  id: 'province',
                  label: '省份',
                  width: 90,
                  cell: (e) => revenueText(e.source['省份名称']),
                  title: (e) => (!e.source['省份名称'] ? '未归属' : undefined),
                },
                {
                  id: 'city',
                  label: '地市／组织',
                  width: 150,
                  cell: (e) => revenueText(e.source['地市/组织名称']),
                },
                {
                  id: 'source-project',
                  label: '来源项目',
                  width: 240,
                  cellClassName: 'revenue-project-name',
                  cell: (e) => revenueText(e.source['项目名称']),
                },
                {
                  id: 'category',
                  label: '三化分类',
                  width: 88,
                  cell: (e) => revenueText(e.source['三化分类']),
                },
                {
                  id: 'customer',
                  label: '客户',
                  width: 160,
                  cell: (e) => revenueText(e.source['客户名称'] || e.source['自然客户名称']),
                },
                {
                  id: 'contract',
                  label: '合同编号',
                  width: 150,
                  cell: (e) => revenueText(e.contractNo),
                },
                {
                  id: 'clause',
                  label: '条款编号',
                  width: 72,
                  cell: (e) => revenueText(e.clauseNo),
                },
                {
                  id: 'clause-amount',
                  label: '条款金额（元）',
                  width: 200,
                  className: 'revenue-numeric',
                  cell: (e) => money(e.clauseAmount),
                },
                {
                  id: 'source-amount',
                  label: '来源本月计收（元）',
                  width: 200,
                  className: 'revenue-numeric',
                  cell: (e) => money(e.sourceAmount),
                },
                {
                  id: 'recognized-amount',
                  label: '认定金额（元）',
                  width: 200,
                  className: 'revenue-numeric',
                  cellClassName: 'revenue-recognized-amount',
                  cell: (e) => money(e.recognition?.amount),
                },
                {
                  id: 'reported-project',
                  label: '通报项目名称',
                  width: 220,
                  cell: (e) => revenueText(e.recognition?.reportedProjectName),
                },
                {
                  id: 'integrated-province',
                  label: '对应省分（集成）',
                  width: 135,
                  cell: (e) => revenueText(integratedAreaName(e.recognition, reference)),
                },
                {
                  id: 'state',
                  label: '办理状态',
                  width: 120,
                  cell: (e) => (
                    <span
                      className={`status-badge revenue-state revenue-state-${e.state.toLowerCase()}`}
                    >
                      {stateLabels[e.state]}
                    </span>
                  ),
                },
              ]}
              actions={(e) => (
                <div className="admin-row-actions">
                  <Link
                    className="secondary-button compact-button"
                    title={`查看计收详情：${e.source['项目名称'] || e.contractNo || e.feeMonth || '未填写费用账期'}`}
                    onClick={listScroll.remember}
                    to={`/revenue/${e.id}?from=${encodeURIComponent('/revenue' + (query ? '?' + query : ''))}`}
                  >
                    详情
                  </Link>
                  {e.canRecognize && (
                    <button
                      type="button"
                      className="secondary-button compact-button"
                      disabled={loading || !reference}
                      onClick={() => {
                        setNotice('');
                        setRecognizing(e);
                      }}
                    >
                      认定
                    </button>
                  )}
                </div>
              )}
            />
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
                setParams(filterPage(params, page));
              }}
            />
            <p className="revenue-list-note">
              未认定与已认定均包含各自的待审记录，待审核不单独累加。计收认定由有办理权限的接口人发起，指定集团审核人审核。
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
