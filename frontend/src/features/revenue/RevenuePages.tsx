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
export { RevenueDetailPage } from './RevenueDetailPage';
const message = (e: unknown) => (e instanceof Error ? e.message : '操作失败，请稍后重试');
export function RevenueListPage() {
  const [params, setParams] = useSearchParams();
  const query = params.toString();
  const [draft, setDraft] = useState(() => Object.fromEntries(params));
  const [reference, setReference] = useState<Reference | null>(null),
    [data, setData] = useState<Listing | null>(null),
    [error, setError] = useState('');
  useEffect(() => {
    let active = true;
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
      });
    return () => {
      active = false;
    };
  }, [query]);
  const change = (field: string, value: string) => setDraft((old) => ({ ...old, [field]: value }));
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">REVENUE MANAGEMENT</span>
          <h1>计收列表</h1>
          <p>月度来源明细与生效认定分别保留，金额单位：元。</p>
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
        className="filter-bar"
        onSubmit={(e) => {
          e.preventDefault();
          const next = new URLSearchParams();
          Object.entries(draft)
            .filter(([k, v]) => k !== 'page' && v)
            .forEach(([k, v]) => next.set(k, v));
          setParams(next);
        }}
      >
        <label>
          月账期
          <input
            type="month"
            value={draft.month ?? ''}
            onChange={(e) => change('month', e.target.value)}
          />
        </label>
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
          </Select>
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
        <label>
          项目／客户／合同／条款
          <input
            value={draft.search ?? ''}
            maxLength={300}
            onChange={(e) => change('search', e.target.value)}
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
      {data ? (
        <>
          <div className="revenue-summary">
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
            <div>
              <span>生效认定金额（元）</span>
              <strong>{money(data.summary.recognizedAmount)}</strong>
            </div>
          </div>
          <div className="panel table-scroll">
            <table>
              <thead>
                <tr>
                  {[
                    '月账期',
                    '省份',
                    '地市／组织',
                    '来源项目',
                    '客户',
                    '合同编号',
                    '条款编号',
                    '条款金额（元）',
                    '来源本月计收（元）',
                    '认定金额（元）',
                    '办理状态',
                    '操作',
                  ].map((t) => (
                    <th key={t}>{t}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {data.items.map((e) => (
                  <tr key={e.id}>
                    <td>{e.month}</td>
                    <td>{e.source['省份名称']}</td>
                    <td>{e.source['地市/组织名称'] || '—'}</td>
                    <td>{e.source['项目名称'] || '—'}</td>
                    <td>{e.source['客户名称'] || '—'}</td>
                    <td>{e.contractNo}</td>
                    <td>{e.clauseNo}</td>
                    <td>{money(e.clauseAmount)}</td>
                    <td>{money(e.sourceAmount)}</td>
                    <td>{money(e.recognition?.amount)}</td>
                    <td>
                      <span className="status-badge">{stateLabels[e.state]}</span>
                    </td>
                    <td>
                      <Link
                        to={`/revenue/${e.id}?from=${encodeURIComponent('/revenue' + (query ? '?' + query : ''))}`}
                      >
                        详情
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {data.items.length === 0 && <p className="empty-state">暂无计收数据</p>}
          </div>
          <div className="pagination">
            <button
              className="secondary-button"
              disabled={data.page === 0}
              onClick={() => {
                const p = new URLSearchParams(params);
                p.set('page', String(data.page - 1));
                setParams(p);
              }}
            >
              上一页
            </button>
            <span>
              第 {data.page + 1} / {Math.max(1, data.totalPages)} 页，共 {data.totalElements} 条
            </span>
            <button
              className="secondary-button"
              disabled={data.page + 1 >= data.totalPages}
              onClick={() => {
                const p = new URLSearchParams(params);
                p.set('page', String(data.page + 1));
                setParams(p);
              }}
            >
              下一页
            </button>
          </div>
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
    <>
      <div className="page-heading">
        <h1>新增计收</h1>
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
          <p>仅填写来源字段；认定结果由省市提交审核或接口人认定。金额单位：元。</p>
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
    </>
  );
}
export function ImportRevenuePage() {
  return (
    <ImportWizardPage
      config={{
        api: revenueImportApi,
        title: '计收',
        amountUnit: '元',
        backTo: '/revenue/new',
        amountFields: ['条款金额', '产品本月计收不含税额/标品含税收入'],
        rowTitleFields: ['项目名称', '合同编号', '条款编号'],
        rowLocationFields: ['省份名称', '地市/组织名称'],
        noDuplicateMessage: '同月账期、合同编号、条款编号唯一；同名项目允许多条明细。',
        uploadHint: '仅导入33个来源字段，不导入认定结果。金额单位为元。',
        previewHint: '重复唯一键须修正；确认只导入选中的通过行，其余放弃并结束任务。',
      }}
    />
  );
}
