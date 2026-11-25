import { useLedgerScroll } from '../../shared/ui/useLedgerScroll';
import { TruncatedText } from '../../shared/ui/TruncatedText';
import { useBatchDelete } from '../../shared/ui/useBatchDelete';
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom';
import { jsonBody, requestAt, type Page } from '../../shared/api/http';
import { useLiveQuery } from '../../shared/api/useLiveQuery';
import { ImportWizardPage } from '../../shared/imports/ImportWizardPage';
import { tabularImportApi } from '../../shared/imports/tabularImportApi';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { ListPagination } from '../../shared/ui/ListPagination';
import { Modal } from '../../shared/ui/Modal';
import { Select } from '../../shared/ui/Select';
import './gewu-deployments.css';

const root = '/api/v1/capability/gewu-deployments';
const api = <T,>(path: string, init?: RequestInit) => requestAt<T>(root, path, init);
const title = '已部署格物项目清单';
const primary = [
  '接口人',
  '省份',
  '地市',
  '客户名称',
  '项目名称',
  '制造类型',
  '国民经济行业分类',
  '所属行业',
  '部署版本',
  '上一周期是否已接设备',
];
const counters = ['物模型总数', '接入设备总数量', '协议数量', '日均上报数据量'];
const optional = [...counters, '设备接入日期', '是否最新工具包', '是否可升级工具包', '备注'];
const columns = [
  '项目名称',
  '客户名称',
  '省份',
  '地市',
  '接口人',
  ...primary.slice(5),
  ...optional,
];

interface Input {
  provinceId: string;
  cityId: string;
  fields: Record<string, string>;
}
interface Deployment extends Input {
  id: string;
  version: number;
}
interface Reference {
  provinces: { id: string; name: string }[];
  cities: { id: string; provinceId: string; name: string }[];
  options: Record<string, string[]>;
}

function LocationFields({
  value,
  reference,
  onChange,
  required,
}: {
  value: Pick<Input, 'provinceId' | 'cityId'>;
  reference: Reference;
  onChange: (location: Pick<Input, 'provinceId' | 'cityId'>) => void;
  required?: boolean;
}) {
  return (
    <>
      <label>
        省份
        <Select
          required={required}
          value={value.provinceId}
          onChange={(event) => onChange({ provinceId: event.target.value, cityId: '' })}
        >
          <option value="">{required ? '请选择省份' : '全部省份'}</option>
          {reference.provinces.map((item) => (
            <option key={item.id} value={item.id}>
              {item.name}
            </option>
          ))}
        </Select>
      </label>
      <label>
        地市
        <Select
          required={required}
          value={value.cityId}
          disabled={required && !value.provinceId}
          onChange={(event) =>
            onChange({ provinceId: value.provinceId, cityId: event.target.value })
          }
        >
          <option value="">{required ? '请选择地市' : '全部地市'}</option>
          {reference.cities
            .filter((item) => !value.provinceId || item.provinceId === value.provinceId)
            .map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
        </Select>
      </label>
    </>
  );
}

export function GewuDeploymentsPage({ admin }: { admin: boolean }) {
  const [params, setParams] = useSearchParams();
  const query = params.toString();
  const [draft, setDraft] = useState(() => Object.fromEntries(params));
  const [editor, setEditor] = useState<'new' | null>(null);
  const [deleting, setDeleting] = useState<Deployment | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => setDraft(Object.fromEntries(new URLSearchParams(query))), [query]);
  const load = useCallback(
    (signal: AbortSignal) => api<Page<Deployment>>(`?${query}`, { signal }),
    [query],
  );
  const loadReference = useCallback(
    (signal: AbortSignal) => api<Reference>('/reference-data', { signal }),
    [],
  );
  const listing = useLiveQuery(load, '部署清单加载失败');
  const reference = useLiveQuery(loadReference, '组织目录加载失败');
  const data = listing.data;
  const ref = reference.data;
  const listScroll = useLedgerScroll(!!data);
  const batch = useBatchDelete(admin, 'GEWU', query, data?.items ?? [], async () => {
    if (data?.page) setParams({ ...Object.fromEntries(params), page: '0' });
    else await listing.refresh();
  });
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setParams(
      Object.fromEntries(
        Object.entries(draft).filter(([key, value]) => key !== 'page' && value.trim()),
      ),
    );
  }

  return (
    <>
      <div className="page-heading gewu-page-heading">
        <div>
          <p>查看各地部署版本、设备接入情况和工具包状态。</p>
        </div>
        <div className="gewu-actions">
          <Link className="secondary-button" to="/capability/gewu/import">
            Excel 导入
          </Link>
          <button className="primary-button" disabled={!ref} onClick={() => setEditor('new')}>
            新增部署
          </button>
        </div>
      </div>
      {reference.error && <Feedback tone="error">{reference.error}</Feedback>}
      {listing.error && <Feedback tone="error">{listing.error}</Feedback>}
      {ref && (
        <form className="filter-bar gewu-filters" aria-label="格物部署筛选" onSubmit={submit}>
          <LocationFields
            value={{ provinceId: draft.provinceId ?? '', cityId: draft.cityId ?? '' }}
            reference={ref}
            onChange={(location) => setDraft((current) => ({ ...current, ...location }))}
          />
          <label>
            部署版本
            <input
              value={draft.deploymentVersion ?? ''}
              maxLength={100}
              onChange={(event) => setDraft({ ...draft, deploymentVersion: event.target.value })}
            />
          </label>
          <label>
            上一周期设备接入
            <Select
              value={draft.connected ?? ''}
              onChange={(event) => setDraft({ ...draft, connected: event.target.value })}
            >
              <option value="">全部</option>
              <option>是</option>
              <option>否</option>
            </Select>
          </label>
          <label>
            关键词
            <input
              placeholder="项目、客户或接口人"
              maxLength={300}
              value={draft.search ?? ''}
              onChange={(event) => setDraft({ ...draft, search: event.target.value })}
            />
          </label>
          <div className="gewu-actions">
            <button className="primary-button" disabled={listing.busy}>
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
      )}
      {!data && listing.busy && <Loading />}
      {data && (
        <section className="panel gewu-register" aria-busy={listing.busy} aria-label="格物部署记录">
          <div className="gewu-register-heading">
            <h2>
              部署记录 <small>{data.totalElements} 条</small>
            </h2>
            <span>左右滚动查看设备和工具包信息</span>
          </div>
          {batch.toolbar}
          <div
            ref={listScroll.table}
            className="table-scroll gewu-scroll sticky-actions-scroll"
            role="region"
            aria-label="格物部署明细横向滚动区域"
            tabIndex={0}
          >
            <table className="two-line-register" aria-label={title}>
              <colgroup>
                {admin && <col style={{ width: 44 }} />}
                {columns.map((field) => (
                  <col
                    key={field}
                    style={{
                      width:
                        field === '项目名称'
                          ? 220
                          : field === '客户名称' || field === '备注'
                            ? 190
                            : field === '省份' || field === '地市' || field === '接口人'
                              ? 100
                              : 150,
                    }}
                  />
                ))}
                <col style={{ width: admin ? 128 : 76 }} />
              </colgroup>
              <thead>
                <tr>
                  {batch.head}
                  {columns.map((field) => (
                    <th
                      scope="col"
                      className={counters.includes(field) ? 'gewu-numeric' : undefined}
                      key={field}
                    >
                      {field}
                    </th>
                  ))}
                  <th scope="col" className="sticky-list-action">
                    操作
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((item) => (
                  <tr key={item.id}>
                    {batch.cell(item)}
                    {columns.map((field) => (
                      <td
                        key={field}
                        className={counters.includes(field) ? 'gewu-numeric' : undefined}
                      >
                        <TruncatedText twoLines text={item.fields[field] || '—'} />
                      </td>
                    ))}
                    <td className="sticky-list-action">
                      <div className="gewu-actions">
                        <Link
                          className="secondary-button compact-button"
                          to={`/capability/gewu/${item.id}`}
                          state={listScroll.state}
                          onClick={listScroll.remember}
                        >
                          详情
                        </Link>
                        {admin && (
                          <button
                            type="button"
                            className="secondary-button compact-button danger"
                            onClick={() => {
                              setError('');
                              setDeleting(item);
                            }}
                          >
                            删除
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!data.items.length && <p className="empty-state">暂无符合筛选条件的部署记录</p>}
          <ListPagination
            label={title}
            page={data.page}
            totalElements={data.totalElements}
            totalPages={data.totalPages}
            disabled={listing.busy}
            onPageChange={(page) => {
              const next = new URLSearchParams(params);
              next.set('page', String(page));
              setParams(next);
            }}
          />
        </section>
      )}
      {editor && ref && (
        <DeploymentEditor
          reference={ref}
          item={null}
          onClose={() => setEditor(null)}
          onSaved={() => {
            setEditor(null);
            void listing.refresh();
          }}
        />
      )}
      {admin && deleting && (
        <Modal title="删除部署记录" busy={busy} onClose={() => setDeleting(null)}>
          <p>删除「{deleting.fields['项目名称']}」的这套部署记录？</p>
          {error && <Feedback tone="error">{error}</Feedback>}
          <div className="dialog-actions">
            <button className="secondary-button" disabled={busy} onClick={() => setDeleting(null)}>
              取消
            </button>
            <button
              className="primary-button"
              disabled={busy}
              onClick={async () => {
                setBusy(true);
                setError('');
                try {
                  await api(`/${deleting.id}?version=${deleting.version}`, { method: 'DELETE' });
                  setDeleting(null);
                  if (data?.items.length === 1 && data.page > 0) {
                    const next = new URLSearchParams(params);
                    next.set('page', String(data.page - 1));
                    setParams(next);
                  } else void listing.refresh();
                } catch (cause) {
                  setError(cause instanceof Error ? cause.message : '删除失败');
                } finally {
                  setBusy(false);
                }
              }}
            >
              删除部署
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}

export function GewuDeploymentDetailPage() {
  const { id = '' } = useParams();
  const { state } = useLocation();
  const [editing, setEditing] = useState<Deployment | null>(null);
  const load = useCallback((signal: AbortSignal) => api<Deployment>(`/${id}`, { signal }), [id]);
  const loadReference = useCallback(
    (signal: AbortSignal) => api<Reference>('/reference-data', { signal }),
    [],
  );
  const result = useLiveQuery(load, '部署详情加载失败');
  const reference = useLiveQuery(loadReference, '组织目录加载失败');
  const item = result.data;
  return (
    <>
      <div className="page-heading">
        <Link
          className="secondary-button"
          to={{ pathname: '/capability/gewu', search: state?.listSearch ?? '' }}
        >
          返回列表
        </Link>
      </div>
      {(result.error || reference.error) && (
        <Feedback tone="error">{result.error || reference.error}</Feedback>
      )}
      {!item && result.busy && <Loading />}
      {item && (
        <section className="panel ledger-detail">
          <div className="page-heading">
            <h2>{editing ? '编辑格物部署' : '部署信息'}</h2>
            {!editing && (
              <button
                className="secondary-button"
                disabled={!reference.data}
                onClick={() => setEditing(item)}
              >
                编辑
              </button>
            )}
          </div>
          {editing && reference.data ? (
            <DeploymentEditor
              reference={reference.data}
              item={editing}
              inline
              onClose={() => setEditing(null)}
              onSaved={() => {
                setEditing(null);
                void result.refresh();
              }}
            />
          ) : (
            <dl className="facts">
              {[...primary, ...optional].map((field) => (
                <div key={field}>
                  <dt>{field}</dt>
                  <dd>{item.fields[field] || '—'}</dd>
                </div>
              ))}
            </dl>
          )}
        </section>
      )}
    </>
  );
}

function DeploymentEditor({
  reference,
  item,
  inline = false,
  onClose,
  onSaved,
}: {
  reference: Reference;
  item: Deployment | null;
  inline?: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [input, setInput] = useState<Input>(item ?? { provinceId: '', cityId: '', fields: {} });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  function fieldInput(field: string) {
    const required = primary.includes(field);
    const value = input.fields[field] ?? '';
    const options = reference.options[field];
    const change = (value: string) =>
      setInput((current) => ({ ...current, fields: { ...current.fields, [field]: value } }));
    return (
      <label key={field} className={field === '备注' ? 'span-two' : undefined}>
        {field}
        {required ? '' : '（选填）'}
        {field === '备注' ? (
          <textarea
            rows={2}
            maxLength={2000}
            value={value}
            onChange={(event) => change(event.target.value)}
          />
        ) : options ? (
          <Select
            required={required}
            value={value}
            onChange={(event) => change(event.target.value)}
          >
            <option value="">请选择</option>
            {options.map((option) => (
              <option key={option}>{option}</option>
            ))}
          </Select>
        ) : (
          <input
            required={required}
            value={value}
            onChange={(event) => change(event.target.value)}
            type={field === '设备接入日期' ? 'date' : 'text'}
            inputMode={counters.includes(field) ? 'numeric' : undefined}
            pattern={counters.includes(field) ? '[0-9]+' : undefined}
            maxLength={300}
          />
        )}
      </label>
    );
  }
  const form = (
    <form
      aria-busy={busy}
      onSubmit={async (event) => {
        event.preventDefault();
        setBusy(true);
        setError('');
        try {
          await api(item ? `/${item.id}` : '', {
            method: item ? 'PUT' : 'POST',
            ...jsonBody(item ? { version: item.version, deployment: input } : input),
          });
          onSaved();
        } catch (cause) {
          setError(cause instanceof Error ? cause.message : '保存失败');
        } finally {
          setBusy(false);
        }
      }}
    >
      <fieldset className="gewu-editor-group">
        <legend>部署信息</legend>
        <div className="form-grid">
          <LocationFields
            value={input}
            reference={reference}
            required
            onChange={(location) => setInput({ ...input, ...location })}
          />
          {primary.filter((field) => field !== '省份' && field !== '地市').map(fieldInput)}
        </div>
      </fieldset>
      <fieldset className="gewu-editor-group">
        <legend>设备接入和工具包</legend>
        <div className="form-grid">{optional.map(fieldInput)}</div>
      </fieldset>
      {error && <Feedback tone="error">{error}</Feedback>}
      <div className="dialog-actions">
        <button className="secondary-button" type="button" disabled={busy} onClick={onClose}>
          取消
        </button>
        <button className="primary-button" disabled={busy}>
          {busy ? '保存中…' : '保存部署'}
        </button>
      </div>
    </form>
  );
  return inline ? (
    form
  ) : (
    <Modal title="新增格物部署" onClose={onClose} busy={busy}>
      {form}
    </Modal>
  );
}

const importApi = tabularImportApi(root);
const loadImportReference = (signal: AbortSignal) => api<Reference>('/reference-data', { signal });
export function ImportGewuDeploymentsPage() {
  const { data: reference, error } = useLiveQuery(loadImportReference, '组织目录加载失败');
  if (error) return <Feedback tone="error">{error}</Feedback>;
  if (!reference) return <Loading />;
  return (
    <ImportWizardPage
      config={{
        api: importApi,
        title: '格物部署',
        backTo: '/capability/gewu',
        amountUnit: '',
        amountFields: [],
        rowTitleFields: ['项目名称', '客户名称', '部署版本'],
        rowLocationFields: ['省份', '地市'],
        noDuplicateMessage: '同一项目的多套部署可分别登记。',
        uploadHint: '上传 Excel 后确认字段映射，再预检省市归属及必填信息。',
        previewHint: '确认后仅导入选中的通过行。',
        fieldOptions: (field, values) => {
          if (field === '省份') return ['', ...reference.provinces.map(({ name }) => name)];
          if (field === '地市') {
            const province = reference.provinces.find(({ name }) => name === values['省份']);
            return [
              '',
              ...reference.cities
                .filter(({ provinceId }) => provinceId === province?.id)
                .map(({ name }) => name),
            ];
          }
          const options = reference.options[field];
          return options ? ['', ...options] : undefined;
        },
        changeRowField: (values, field, value) =>
          field === '省份' && value !== values[field]
            ? { ...values, [field]: value, 地市: '' }
            : { ...values, [field]: value },
      }}
    />
  );
}
