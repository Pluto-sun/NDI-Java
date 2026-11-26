import { useBlockingOperation } from '../../shared/ui/useBlockingOperation';
import { downloadWorkbook } from '../../shared/imports/importWizard';
import { validateExcelUpload } from '../../shared/imports/limits';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { requestAt, requestBlobAt } from '../../shared/api/http';
import { Feedback } from '../../shared/ui/Feedback';
import { ListPagination } from '../../shared/ui/ListPagination';
import { Modal } from '../../shared/ui/Modal';
import { Select } from '../../shared/ui/Select';
import { LiveAgentImportTabs } from './LiveAgentImportTabs';

interface Row {
  key: string;
  sourceRow: number;
  province: string;
  agent: string;
  month: string;
  amount: string;
  screenshot: boolean;
  status: 'READY' | 'FAILED' | 'SKIPPED';
  message: string;
  warning: string;
}
interface Preview {
  sheet: string;
  sheets: string[];
  rows: Row[];
  fingerprint: string;
}

const root = '/api/v1/capability/agents-live/token-import';
const labels = { READY: '通过', FAILED: '失败', SKIPPED: '跳过' };
const pageSize = 10;

export function MonthlyTokenImportDialog({ onSwitch }: { onSwitch: (monthly: boolean) => void }) {
  const navigate = useNavigate();
  const [year, setYear] = useState(() =>
    new Intl.DateTimeFormat('en', { year: 'numeric', timeZone: 'Asia/Shanghai' }).format(
      new Date(),
    ),
  );
  const [file, setFile] = useState<File>();
  const [sheet, setSheet] = useState('');
  const [sheets, setSheets] = useState<string[]>([]);
  const [preview, setPreview] = useState<Preview>();
  const [selected, setSelected] = useState<string[]>([]);
  const [filter, setFilter] = useState('ALL');
  const [page, setPage] = useState(0);
  const blocking = useBlockingOperation();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [imported, setImported] = useState<number>();

  async function submit(mode: 'preview' | 'confirm' | 'export') {
    if (!file || busy) return;
    const workbook = file;
    if (mode === 'export') await perform();
    else
      await blocking.run(
        mode === 'confirm' ? '正在确认导入…' : '正在读取并预检月度 Token…',
        perform,
      );

    async function perform(signal?: AbortSignal) {
      setBusy(true);
      setError('');
      setImported(undefined);
      const form = new FormData();
      form.append('file', workbook);
      form.append('year', year);
      if (sheet) form.append('sheet', sheet);
      try {
        validateExcelUpload(workbook);
        if (mode === 'export') {
          if (!preview) return;
          form.set('sheet', preview.sheet);
          form.append('fingerprint', preview.fingerprint);
          downloadWorkbook(
            await requestBlobAt(root, '/failed-rows/original', {
              method: 'POST',
              body: form,
              signal,
            }),
            '月度 Token 导入预检失败原始数据.xlsx',
          );
        } else if (mode === 'confirm') {
          form.append(
            'selection',
            new Blob([JSON.stringify(selected)], { type: 'application/json' }),
          );
          const saved = await requestAt<number>(root, '/confirm', {
            method: 'POST',
            body: form,
            signal,
          });
          setImported(saved);
          setPreview(undefined);
          setSelected([]);
          setFile(undefined);
          setSheet('');
          setSheets([]);
        } else {
          setPreview(undefined);
          setSelected([]);
          const result = await requestAt<Preview>(root, '/preview', {
            method: 'POST',
            body: form,
            signal,
          });
          setPreview(result);
          setSheets(result.sheets);
          setSheet(result.sheet);
          setSelected(result.rows.filter((row) => row.status === 'READY').map((row) => row.key));
          setFilter('ALL');
          setPage(0);
        }
      } catch (cause) {
        if (signal?.reason?.name === 'AbortError') return;
        setError(
          cause instanceof Error
            ? cause.message
            : mode === 'export'
              ? '导出失败，请重试'
              : '导入失败，请重试',
        );
      } finally {
        if (signal?.reason?.name !== 'AbortError') setBusy(false);
      }
    }
  }

  const ready = preview?.rows.filter((row) => row.status === 'READY') ?? [];
  const rows = preview?.rows.filter((row) => filter === 'ALL' || filter === row.status) ?? [];
  return (
    <Modal
      title="Excel 月度 Token 导入"
      busy={busy}
      onClose={() => navigate('/capability/agents-live')}
      renderHeading={(pending) => (
        <LiveAgentImportTabs monthly disabled={pending} onChange={onSwitch} />
      )}
    >
      {blocking.overlay}
      <div className="import-wizard" aria-busy={busy}>
        <p className="subtle">
          上传原有横表，文件最大 20
          MB。填写统计年份。按省分、智能体名称和应用项目名称匹配已有记录，也可在 Excel
          中填写「系统ID」。
        </p>
        {error && <Feedback tone="error">{error}</Feedback>}
        {imported !== undefined && (
          <Feedback tone="success">已处理 {imported} 条月度记录。</Feedback>
        )}
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void submit('preview');
          }}
        >
          <div className="form-grid">
            <label>
              统计年份
              <input
                type="number"
                required
                min={1900}
                max={9999}
                disabled={busy}
                value={year}
                onChange={(event) => {
                  setYear(event.target.value);
                  setPreview(undefined);
                }}
              />
            </label>
            <label>
              Excel 文件
              <input
                type="file"
                accept=".xlsx"
                required
                disabled={busy}
                key={imported ?? 'file'}
                onChange={(event) => {
                  setFile(event.currentTarget.files?.[0]);
                  setPreview(undefined);
                  setSheets([]);
                  setSheet('');
                  setImported(undefined);
                }}
              />
            </label>
            {sheets.length > 0 && (
              <label>
                工作表
                <Select
                  value={sheet}
                  disabled={busy}
                  onChange={(event) => {
                    setSheet(event.target.value);
                    setPreview(undefined);
                    setSelected([]);
                    setPreview(undefined);
                  }}
                >
                  {sheets.map((name) => (
                    <option key={name}>{name}</option>
                  ))}
                </Select>
              </label>
            )}
          </div>
          <button type="submit" className="primary-button" disabled={busy || !file}>
            开始预检
          </button>
        </form>
        {preview && (
          <>
            {preview.rows.some((row) => row.status === 'FAILED') && (
              <div className="action-row">
                <button
                  type="button"
                  className="secondary-button"
                  disabled={busy}
                  onClick={() => void submit('export')}
                >
                  导出预检失败原始数据
                </button>
              </div>
            )}
            <div className="action-row" role="group" aria-label="月度 Token 预检状态">
              {Object.entries({ ALL: '全部', ...labels }).map(([key, label]) => (
                <button
                  type="button"
                  key={key}
                  className={filter === key ? 'primary-button' : 'secondary-button'}
                  disabled={busy}
                  aria-pressed={filter === key}
                  onClick={() => {
                    setFilter(key);
                    setPage(0);
                  }}
                >
                  {label}{' '}
                  {key === 'ALL'
                    ? preview.rows.length
                    : preview.rows.filter((row) => row.status === key).length}
                </button>
              ))}
            </div>
            <div className="table-scroll">
              <table aria-label="月度 Token 预检结果">
                <thead>
                  <tr>
                    <th scope="col">选中</th>
                    <th scope="col">Excel 行</th>
                    <th scope="col">省分 / 智能体</th>
                    <th scope="col">月份</th>
                    <th scope="col">用量（万）</th>
                    <th scope="col">截图</th>
                    <th scope="col">预检结果</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.slice(page * pageSize, (page + 1) * pageSize).map((row) => (
                    <tr key={row.key}>
                      <td>
                        <input
                          type="checkbox"
                          aria-label={`第 ${row.sourceRow} 行 ${row.month}`}
                          disabled={busy || row.status !== 'READY'}
                          checked={selected.includes(row.key)}
                          onChange={(event) =>
                            setSelected(
                              event.target.checked
                                ? [...selected, row.key]
                                : selected.filter((key) => key !== row.key),
                            )
                          }
                        />
                      </td>
                      <td>{row.sourceRow}</td>
                      <td>{[row.province, row.agent].filter(Boolean).join(' / ') || '—'}</td>
                      <td>{row.month}</td>
                      <td>{row.amount || '—'}</td>
                      <td>{row.screenshot ? '有' : '—'}</td>
                      <td>
                        {labels[row.status]} · {row.message}
                        {row.warning && <div className="subtle">{row.warning}</div>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <ListPagination
              label="预检结果"
              page={page}
              totalPages={Math.ceil(rows.length / pageSize)}
              totalElements={rows.length}
              onPageChange={setPage}
              disabled={busy}
            />
            <div className="dialog-actions">
              <button
                type="button"
                className="primary-button"
                disabled={busy || selected.length === 0}
                onClick={() => void submit('confirm')}
              >
                确认导入 {selected.length} 条
              </button>
            </div>
          </>
        )}
      </div>
    </Modal>
  );
}
