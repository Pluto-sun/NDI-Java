import { TruncatedText } from '../../shared/ui/TruncatedText';
import { downloadAttachment } from '../../shared/util/downloadAttachment';
import { useBatchDelete } from '../../shared/ui/useBatchDelete';
import { useCallback, useState, type FormEvent } from 'react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom';
import { jsonBody, requestAt, type Page } from '../../shared/api/http';
import { useLiveQuery } from '../../shared/api/useLiveQuery';
import { ImportWizardPage } from '../../shared/imports/ImportWizardPage';
import { tabularImportApi } from '../../shared/imports/tabularImportApi';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { ListPagination } from '../../shared/ui/ListPagination';
import { Modal } from '../../shared/ui/Modal';
import { Select } from '../../shared/ui/Select';
import { LiveAgentImportTabs } from './LiveAgentImportTabs';
import { MonthlyTokenImportDialog } from './MonthlyTokenImportDialog';
import { sha256Hex } from '../../shared/util/crypto';
import './gewu-deployments.css';

export const intelligenceTitles = {
  wangwu: '万悟部署情况',
  'agents-live': '已上线智能体指标追踪',
  'agents-development': '开发中智能体指标追踪',
};
type Kind = keyof typeof intelligenceTitles;
type Fields = Record<string, string>;
interface Entry {
  id: string;
  provinceId: string;
  fields: Fields;
  version: number;
  updatedAt: string;
}
interface Reference {
  provinces: { id: string; name: string }[];
  fields: string[];
  required: string[];
  options: Record<string, string[]>;
  months: string[];
}
interface Token {
  id: string;
  month: string;
  amount: string;
  screenshotId: string | null;
}

const base = (kind: Kind) => '/capability/' + kind;
const root = (kind: Kind) => '/api/v1' + base(kind);
const errorText = (error: unknown) => (error instanceof Error ? error.message : '操作失败，请重试');
const dateFormat = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});
const timestamp = (value: string) => dateFormat.format(new Date(value));

export function IntelligencePages({
  kind,
  mode,
  admin = false,
}: {
  kind: Kind;
  mode: 'list' | 'detail' | 'import';
  admin?: boolean;
}) {
  const [params, setParams] = useSearchParams();
  const showMonthlyImport = kind === 'agents-live' && params.get('type') === 'monthly-token';
  const switchImport = (monthly: boolean) =>
    setParams(monthly ? { type: 'monthly-token' } : {}, { replace: true });
  const load = useCallback(
    (signal: AbortSignal) => requestAt<Reference>(root(kind), '/reference-data', { signal }),
    [kind],
  );
  const reference = useLiveQuery(load, '组织字典加载失败');
  if (reference.error) return <Feedback tone="error">{reference.error}</Feedback>;
  const ref = reference.data;
  if (!ref) return <Loading />;
  if (mode === 'import' && showMonthlyImport)
    return <MonthlyTokenImportDialog onSwitch={switchImport} />;
  if (mode === 'import')
    return (
      <ImportWizardPage
        renderHeading={
          kind === 'agents-live'
            ? (busy) => (
                <LiveAgentImportTabs monthly={false} disabled={busy} onChange={switchImport} />
              )
            : undefined
        }
        config={{
          api: tabularImportApi(root(kind), { exportFailedRows: true }),
          title: intelligenceTitles[kind],
          backTo: base(kind),
          amountFields: [],
          rowLocationFields: ['省分'],
          rowTitleFields: ['具体名称', '智能体名称', '接口人'],
          uploadHint: '选择 Excel 工作表并核对映射。月份请填写 YYYY-MM。',
          noDuplicateMessage: '字段校验通过',
          previewHint: '确认后导入选中的通过行。',
          fieldOptions: (field) =>
            field === '省分'
              ? ['', ...ref.provinces.map((item) => item.name)]
              : ref.options[field]
                ? ['', ...ref.options[field]]
                : undefined,
        }}
      />
    );
  if (mode === 'detail') return <RecordDetail kind={kind} reference={ref} />;
  return <RecordList kind={kind} reference={ref} admin={admin} />;
}

function RecordList({
  kind,
  reference,
  admin,
}: {
  kind: Kind;
  reference: Reference;
  admin: boolean;
}) {
  const [params, setParams] = useSearchParams();
  const query = params.toString();
  const [editor, setEditor] = useState<Entry | 'new' | null>(null);
  const load = useCallback(
    (signal: AbortSignal) => requestAt<Page<Entry>>(root(kind), '?' + query, { signal }),
    [kind, query],
  );
  const listing = useLiveQuery(load, '清单加载失败');
  const data = listing.data;
  const register = {
    wangwu: 'WANGWU',
    'agents-live': 'AGENTS_LIVE',
    'agents-development': 'AGENTS_DEVELOPMENT',
  } as const;
  const batch = useBatchDelete(admin, register[kind], query, data?.items ?? [], async () => {
    if (data?.page) setParams({ ...Object.fromEntries(params), page: '0' });
    else await listing.refresh();
  });
  const listFields = reference.fields.filter((field) => field !== '智能体功能简述');
  return (
    <>
      <div className="page-heading gewu-page-heading">
        <div>
          <p>
            {
              {
                wangwu: '查看平台部署地点、进度和版本。',
                'agents-live': '维护智能体上线与计收信息，按月登记 Token 用量。',
                'agents-development': '查看智能体所属环节、开发进度和计收状态。',
              }[kind]
            }
          </p>
        </div>
        <div className="gewu-actions">
          <Link className="secondary-button" to={base(kind) + '/import'}>
            Excel 导入
          </Link>
          <button className="primary-button" onClick={() => setEditor('new')}>
            新增记录
          </button>
        </div>
      </div>
      <form
        key={query}
        className="filter-bar gewu-filters"
        aria-label={`${intelligenceTitles[kind]}筛选`}
        onSubmit={(event) => {
          event.preventDefault();
          setParams(
            Object.fromEntries(
              [...new FormData(event.currentTarget)]
                .map(([key, value]) => [key, String(value).trim()])
                .filter(([, value]) => value),
            ),
          );
        }}
      >
        <label>
          接口人
          <input name="reviewer" maxLength={300} defaultValue={params.get('reviewer') ?? ''} />
        </label>
        {kind === 'wangwu' ? (
          <>
            <label>
              部署地点
              <input name="place" maxLength={4000} defaultValue={params.get('place') ?? ''} />
            </label>
            <label>
              部署进度
              <Select name="progress" defaultValue={params.get('progress') ?? ''}>
                <option value="">全部</option>
                {reference.options['部署进度'].map((option) => (
                  <option key={option}>{option}</option>
                ))}
              </Select>
            </label>
          </>
        ) : (
          <label>
            省分
            <Select name="provinceId" defaultValue={params.get('provinceId') ?? ''}>
              <option value="">全部</option>
              {reference.provinces.map((province) => (
                <option key={province.id} value={province.id}>
                  {province.name}
                </option>
              ))}
            </Select>
          </label>
        )}
        <div className="gewu-actions">
          <button className="primary-button">查询</button>
          <button
            type="button"
            className="secondary-button"
            onClick={() => {
              if (query) setParams({});
              else document.querySelector<HTMLFormElement>('.gewu-filters')?.reset();
            }}
          >
            重置
          </button>
        </div>
      </form>
      {listing.error && <Feedback tone="error">{listing.error}</Feedback>}
      {!data && listing.busy && <Loading />}
      {data && (
        <section className="panel gewu-register" aria-busy={listing.busy}>
          <div className="gewu-register-heading">
            <h2>
              {kind === 'wangwu' ? '部署记录' : '智能体记录'} <small>{data.totalElements} 条</small>
            </h2>
            <span>左右滚动查看完整字段</span>
          </div>
          {batch.toolbar}
          <div
            className="table-scroll gewu-scroll sticky-actions-scroll"
            tabIndex={0}
            role="region"
            aria-label="能力指标明细"
          >
            <table className="two-line-register" aria-label={intelligenceTitles[kind]}>
              <colgroup>
                {admin && <col style={{ width: 44 }} />}
                {listFields.map((field) => (
                  <col
                    key={field}
                    style={{
                      width: ['具体名称', '智能体名称', '应用项目名称', '最终客户名称'].includes(
                        field,
                      )
                        ? 220
                        : field.length > 8
                          ? 180
                          : 128,
                    }}
                  />
                ))}
                <col style={{ width: 180 }} />
                <col style={{ width: 72 }} />
              </colgroup>
              <thead>
                <tr>
                  {batch.head}
                  {listFields.map((field) => (
                    <th key={field} scope="col">
                      {field}
                    </th>
                  ))}
                  <th scope="col">更新时间</th>
                  <th scope="col" className="sticky-list-action">
                    操作
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((item) => (
                  <tr key={item.id}>
                    {batch.cell(item)}
                    {listFields.map((field) => (
                      <td key={field}>
                        <TruncatedText twoLines text={display(field, item.fields[field])} />
                      </td>
                    ))}
                    <td>
                      <TruncatedText twoLines text={timestamp(item.updatedAt)} />
                    </td>
                    <td className="sticky-list-action">
                      <div className="gewu-actions">
                        <button
                          type="button"
                          className="text-button"
                          onClick={() => setEditor(item)}
                        >
                          编辑
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!data.items.length && <p className="empty-state">暂无符合条件的记录</p>}
          <ListPagination
            label={intelligenceTitles[kind]}
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            disabled={listing.busy}
            onPageChange={(page) =>
              setParams(new URLSearchParams({ ...Object.fromEntries(params), page: String(page) }))
            }
          />
        </section>
      )}
      {editor && (
        <RecordEditor
          kind={kind}
          reference={reference}
          entry={editor === 'new' ? undefined : editor}
          detailsSearch={query ? '?' + query : ''}
          onClose={() => setEditor(null)}
          onSaved={() => {
            setEditor(null);
            void listing.refresh();
          }}
        />
      )}
    </>
  );
}

function display(field: string, value?: string) {
  return value ? value + (field === '开发进度' ? '%' : '') : '—';
}

function RecordDetail({ kind, reference }: { kind: Kind; reference: Reference }) {
  const { id } = useParams();
  const location = useLocation();
  const [edit, setEdit] = useState(false);
  const load = useCallback(
    (signal: AbortSignal) => requestAt<Entry>(root(kind), '/' + id, { signal }),
    [kind, id],
  );
  const result = useLiveQuery(load, '详情读取失败');
  const entry = result.data;
  return (
    <>
      <div className="page-heading">
        <Link
          className="secondary-button"
          to={{ pathname: base(kind), search: location.state?.listSearch ?? '' }}
        >
          返回列表
        </Link>
      </div>
      {result.error && <Feedback tone="error">{result.error}</Feedback>}
      {!entry && result.busy && <Loading />}
      {entry && (
        <>
          <section className="panel">
            <div className="page-heading">
              <h2>基本信息</h2>
              <button className="secondary-button" onClick={() => setEdit(true)}>
                编辑
              </button>
            </div>
            <dl className="facts">
              {reference.fields.map((field) => (
                <div key={field}>
                  <dt>{field}</dt>
                  <dd>{display(field, entry.fields[field])}</dd>
                </div>
              ))}
              <div>
                <dt>更新时间（北京时间）</dt>
                <dd>{timestamp(entry.updatedAt)}</dd>
              </div>
            </dl>
          </section>
          {kind === 'agents-live' && <TokenPanel agent={entry.id} onUpdated={result.refresh} />}
          {edit && (
            <RecordEditor
              kind={kind}
              reference={reference}
              entry={entry}
              onClose={() => setEdit(false)}
              onSaved={() => {
                setEdit(false);
                void result.refresh();
              }}
            />
          )}
        </>
      )}
    </>
  );
}

function RecordEditor({
  kind,
  reference,
  entry,
  detailsSearch,
  onClose,
  onSaved,
}: {
  kind: Kind;
  reference: Reference;
  entry?: Entry;
  detailsSearch?: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fields = Object.fromEntries(
      [...new FormData(event.currentTarget)].map(([key, value]) => [key, String(value)]),
    );
    const input = { provinceId: fields['省分'], fields };
    setBusy(true);
    setError('');
    try {
      await requestAt(root(kind), entry ? '/' + entry.id : '', {
        method: entry ? 'PUT' : 'POST',
        ...jsonBody(entry ? { version: entry.version, entry: input } : input),
      });
      onSaved();
    } catch (cause) {
      setError(errorText(cause));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal title={entry ? '编辑记录' : '新增记录'} busy={busy} onClose={onClose}>
      <form onSubmit={save}>
        <fieldset className="gewu-editor-group">
          <legend>{kind === 'wangwu' ? '部署信息' : '智能体信息'}</legend>
          <div className="form-grid">
            {reference.fields.map((field) => {
              const required = reference.required.includes(field);
              const value = entry?.fields[field] ?? '';
              return (
                <label key={field}>
                  {field}
                  {field === '省分' ? (
                    <Select name={field} required defaultValue={entry?.provinceId ?? ''}>
                      <option value="">请选择</option>
                      {reference.provinces.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name}
                        </option>
                      ))}
                    </Select>
                  ) : reference.options[field] ? (
                    <Select name={field} required={required} defaultValue={value}>
                      <option value="">请选择</option>
                      {reference.options[field].map((option) => (
                        <option key={option}>{option}</option>
                      ))}
                    </Select>
                  ) : field.includes('简述') || field.includes('组件') ? (
                    <textarea
                      name={field}
                      required={required}
                      defaultValue={value}
                      maxLength={4000}
                    />
                  ) : (
                    <input
                      name={field}
                      required={required}
                      defaultValue={value}
                      maxLength={4000}
                      type={
                        reference.months.includes(field)
                          ? 'month'
                          : field === '版本更新日期'
                            ? 'date'
                            : field === '开发进度'
                              ? 'number'
                              : 'text'
                      }
                      min={field === '开发进度' ? 0 : undefined}
                      max={field === '开发进度' ? 100 : undefined}
                      step={field === '开发进度' ? '0.0001' : undefined}
                    />
                  )}
                </label>
              );
            })}
          </div>
        </fieldset>
        {error && <Feedback tone="error">{error}</Feedback>}
        <div className="dialog-actions">
          {entry && detailsSearch !== undefined && (
            <Link
              className="text-button"
              to={base(kind) + '/' + entry.id}
              state={{ listSearch: detailsSearch }}
            >
              {kind === 'agents-live' ? '月度 Token 记录' : '查看记录'}
            </Link>
          )}
          <button type="button" className="secondary-button" onClick={onClose} disabled={busy}>
            取消
          </button>
          <button className="primary-button" disabled={busy}>
            保存记录
          </button>
        </div>
      </form>
    </Modal>
  );
}

function TokenPanel({ agent, onUpdated }: { agent: string; onUpdated: () => Promise<void> }) {
  const endpoint = '/' + agent + '/tokens';
  const [editor, setEditor] = useState<Token | 'new' | null>(null);
  const [uploadTarget, setUploadTarget] = useState<Token | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [preview, setPreview] = useState<string | null>(null);
  const load = useCallback(
    (signal: AbortSignal) => requestAt<Token[]>(root('agents-live'), endpoint, { signal }),
    [endpoint],
  );
  const result = useLiveQuery(load, '月度 Token 读取失败');
  const request = <T,>(path: string, init?: RequestInit) =>
    requestAt<T>(root('agents-live'), endpoint + path, init);
  function refresh() {
    void result.refresh();
    void onUpdated();
  }
  function close() {
    setError('');
    setEditor(null);
    setUploadTarget(null);
  }
  const token = editor === 'new' ? null : editor;
  const screenshot = (id: string) => '/' + id + '/screenshot';
  return (
    <section className="panel" style={{ marginTop: 16 }}>
      <div className="page-heading">
        <h2>月度 Token 记录</h2>
        <button
          className="secondary-button"
          onClick={() => {
            setError('');
            setEditor('new');
          }}
        >
          新增月度记录
        </button>
      </div>
      {result.error && <Feedback tone="error">{result.error}</Feedback>}
      {error && !editor && !uploadTarget && <Feedback tone="error">{error}</Feedback>}
      <div className="table-scroll">
        <table aria-label="月度 Token 记录" style={{ minWidth: 520 }}>
          <thead>
            <tr>
              <th>统计月份</th>
              <th>Token 量（万）</th>
              <th>截图</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {result.data?.map((item) => (
              <tr key={item.id}>
                <td>{item.month}</td>
                <td>{item.amount}</td>
                <td>
                  <div className="gewu-actions">
                    {item.screenshotId &&
                      ['查看', '下载'].map((action) => (
                        <button
                          key={action}
                          className="text-button"
                          onClick={async () => {
                            setError('');
                            try {
                              const { downloadUrl, attachmentUrl } = await request<{
                                downloadUrl: string;
                                attachmentUrl: string;
                              }>(screenshot(item.id));
                              if (action === '查看') setPreview(downloadUrl);
                              else downloadAttachment(attachmentUrl);
                            } catch (cause) {
                              setError(errorText(cause));
                            }
                          }}
                        >
                          {action}截图
                        </button>
                      ))}
                    <button
                      className="text-button"
                      onClick={() => {
                        setError('');
                        setUploadTarget(item);
                      }}
                    >
                      {item.screenshotId ? '替换截图' : '上传截图'}
                    </button>
                  </div>
                </td>
                <td>
                  <button
                    className="text-button"
                    onClick={() => {
                      setError('');
                      setEditor(item);
                    }}
                  >
                    编辑
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {result.data?.length === 0 && <p className="empty-state">暂无月度记录</p>}
      {editor && (
        <Modal title={token ? '编辑月度记录' : '新增月度记录'} busy={busy} onClose={close}>
          <form
            onSubmit={async (event) => {
              event.preventDefault();
              const values = new FormData(event.currentTarget);
              setBusy(true);
              setError('');
              try {
                await request('', {
                  method: 'POST',
                  ...jsonBody({
                    id: token?.id,
                    month: values.get('month'),
                    amount: values.get('amount'),
                  }),
                });
                close();
                refresh();
              } catch (cause) {
                setError(errorText(cause));
              } finally {
                setBusy(false);
              }
            }}
          >
            <div className="form-grid">
              <label>
                统计月份
                <input type="month" name="month" required defaultValue={token?.month} />
              </label>
              <label>
                Token 量（万）
                <input
                  type="number"
                  min="0"
                  step="0.0001"
                  name="amount"
                  required
                  defaultValue={token?.amount}
                />
              </label>
            </div>
            {error && <Feedback tone="error">{error}</Feedback>}
            <div className="dialog-actions">
              <button className="primary-button" disabled={busy}>
                保存月度记录
              </button>
            </div>
          </form>
        </Modal>
      )}
      {uploadTarget && (
        <Modal title="上传月度截图" busy={busy} onClose={close}>
          <form
            onSubmit={async (event) => {
              event.preventDefault();
              const file = new FormData(event.currentTarget).get('file');
              if (!(file instanceof File) || !file.size) return;
              setBusy(true);
              setError('');
              try {
                const bytes = await file.arrayBuffer();
                const intent = await request<{
                  documentId: string;
                  uploadUrl: string;
                  method: string;
                  requiredHeaders: Record<string, string>;
                }>(screenshot(uploadTarget.id), {
                  method: 'POST',
                  ...jsonBody({
                    originalName: file.name,
                    mediaType: file.type,
                    byteSize: file.size,
                    sha256Hex: sha256Hex(bytes),
                  }),
                });
                const uploaded = await fetch(intent.uploadUrl, {
                  method: intent.method,
                  headers: intent.requiredHeaders,
                  body: bytes,
                });
                if (!uploaded.ok) throw new Error('截图上传失败');
                await request(screenshot(uploadTarget.id) + '/' + intent.documentId, {
                  method: 'POST',
                });
                close();
                refresh();
              } catch (cause) {
                setError(errorText(cause));
              } finally {
                setBusy(false);
              }
            }}
          >
            <label>
              截图
              <input type="file" name="file" accept="image/png,image/jpeg" required />
            </label>
            {error && <Feedback tone="error">{error}</Feedback>}
            <div className="dialog-actions">
              <button className="primary-button" disabled={busy}>
                保存截图
              </button>
            </div>
          </form>
        </Modal>
      )}
      {preview && (
        <Modal title="月度截图" onClose={() => setPreview(null)}>
          <img src={preview} alt="月度 Token 截图" style={{ maxWidth: '100%' }} />
        </Modal>
      )}
    </section>
  );
}
