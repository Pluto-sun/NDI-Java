import './importWizard.css';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Modal } from '../ui/Modal';
import { Select } from '../ui/Select';
import { Feedback } from '../ui/Feedback';
import { dateTime } from '../ui/format';
import { ImportWizardProvider, useImportWizard, type ImportWizardConfig } from './context';
import type { ImportBatch, ImportConfiguration, ImportTask } from './types';
import { ImportFieldMapping } from './ImportFieldMapping';
import { ImportPreviewTable } from './ImportPreviewTable';
import { ImportRowDialog } from './ImportRowDialog';
import {
  chooseSheet,
  missingMappings,
  downloadWorkbook,
  nameAccepted,
  selectedFor,
  type ImportRow,
} from './importWizard';

const emptyConfiguration: ImportConfiguration = {
  sheetName: null,
  headerRowNo: null,
  fieldMapping: {},
};
export function ImportWizardPage({ config }: { config: ImportWizardConfig }) {
  return (
    <ImportWizardProvider value={config}>
      <ImportWizardBody />
    </ImportWizardProvider>
  );
}
function ImportWizardBody() {
  const config = useImportWizard();
  const projectsApi = config.api;
  const navigate = useNavigate();
  const wizardBody = useRef<HTMLDivElement>(null);
  const [task, setTask] = useState<ImportTask | null>(null);
  const current = useRef<ImportTask | null>(null);
  const [configuration, setConfiguration] = useState<ImportConfiguration>(emptyConfiguration);
  const configRef = useRef(configuration);
  configRef.current = configuration;
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const selectedRef = useRef(selected);
  selectedRef.current = selected;
  const [pending, setPending] = useState<ImportTask | null>(null);
  const [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [saving, setSaving] = useState(0),
    [error, setError] = useState('');
  const [completed, setCompleted] = useState<ImportBatch | null>(null);
  const [editor, setEditor] = useState<{ row: ImportRow; mode: 'edit' | 'name' } | null>(null);
  const queue = useRef<Promise<unknown>>(Promise.resolve());
  const mounted = useRef(true);
  const saveFailed = useRef(false);
  function install(value: ImportTask) {
    current.current = value;
    setTask(value);
    setConfiguration(value.configuration);
    setSelected(selectedFor(value));
  }
  useEffect(() => {
    // 每次读取独立失效，避免 StrictMode 的旧请求在继续任务后覆盖恢复界面。
    let active = true;
    mounted.current = true;
    projectsApi
      .importTask()
      .then((value) => {
        if (active) setPending(value);
      })
      .catch((cause) => {
        if (active) {
          saveFailed.current = true;
          setError(cause instanceof Error ? cause.message : '任务加载失败');
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      mounted.current = false;
    };
  }, []);
  useEffect(() => {
    if (loading) return;
    const frame = requestAnimationFrame(() =>
      wizardBody.current?.querySelector<HTMLElement>('h2[tabindex="-1"]')?.focus(),
    );
    return () => cancelAnimationFrame(frame);
  }, [loading, task?.step, pending?.id, completed?.batchId]);
  // 串行提交小型状态请求，版本号只取最新已保存值；切换页面后请求仍可完成。
  function persist(config: ImportConfiguration, selection: Set<number>, step?: number) {
    setSaving((count) => count + 1);
    const job = queue.current.then(async () => {
      const live = current.current;
      if (!live) throw new Error('请先上传文件');
      const result = await projectsApi.saveImportTask(
        live.id,
        live.version,
        step ?? live.step,
        config,
        Array.from(selection),
      );
      current.current = result;
      saveFailed.current = false;
      if (mounted.current) setTask(result);
      return result;
    });
    queue.current = job
      .catch((cause) => {
        saveFailed.current = true;
        if (mounted.current) setError(cause instanceof Error ? cause.message : '暂存失败，请重试');
      })
      .finally(() => {
        if (mounted.current) setSaving((count) => count - 1);
      });
    return job;
  }
  function changeConfiguration(value: ImportConfiguration) {
    setConfiguration(value);
    configRef.current = value;
    setError('');
    void persist(value, selectedRef.current).catch(() => {});
  }
  function changeSelection(value: Set<number>) {
    setSelected(value);
    selectedRef.current = value;
    void persist(configRef.current, value).catch(() => {});
  }
  async function action(work: () => Promise<void>) {
    setBusy(true);
    setError('');
    try {
      await queue.current;
      await work();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '操作失败，请重试');
    } finally {
      setBusy(false);
    }
  }
  async function upload(file: File) {
    await action(async () => {
      const result = await projectsApi.uploadImportTask(
        file,
        current.current?.version,
        current.current?.id,
      );
      install(result);
      const first = result.inspection.sheets.find((sheet) => sheet.headerCandidates.length);
      if (first) {
        const config = chooseSheet(result, first.name);
        setConfiguration(config);
        configRef.current = config;
        const saved = await persist(config, new Set());
        setTask(saved);
      }
    });
  }
  async function move(step: number) {
    await action(async () => {
      const result = await persist(configRef.current, selectedRef.current, step);
      install(result);
    });
  }
  async function close() {
    await queue.current;
    if (saveFailed.current) {
      setError('暂存尚未成功，请重试保存后关闭。');
      return;
    }
    navigate(config.backTo);
  }
  async function startOver() {
    if (!pending) return;
    await action(async () => {
      await projectsApi.discardImportTask(pending.version, pending.id);
      setPending(null);
      current.current = null;
      setTask(null);
      setConfiguration(emptyConfiguration);
      setSelected(new Set());
    });
  }
  async function confirm() {
    if (!current.current?.batch) return;
    await action(async () => {
      const result = await projectsApi.confirmImport(
        current.current!.batch!.batchId,
        Array.from(selectedRef.current),
      );
      if (result.status === 'COMPLETED') {
        setCompleted(result);
        setTask(null);
        current.current = null;
      } else {
        setError('导入仍在处理中，请刷新任务查看结果。');
        setTask((live) => (live ? { ...live, batch: result } : live));
      }
    });
  }
  function batchUpdated(batch: ImportBatch) {
    const live = current.current;
    if (!live) return;
    const updated = { ...live, batch };
    current.current = updated;
    setTask(updated);
    const next = new Set(
      Array.from(selectedRef.current).filter((number) =>
        batch.rows.some((row) => row.rowNumber === number && row.status === 'READY'),
      ),
    );
    const edited = editor && batch.rows.find((row) => row.rowNumber === editor.row.rowNumber);
    if (edited?.status === 'READY') next.add(edited.rowNumber);
    changeSelection(next);
  }
  const step = task?.step ?? 1;
  const batch = task?.batch;
  const sheet = task?.inspection.sheets.find((sheet) => sheet.name === configuration.sheetName);
  const missing = task ? missingMappings(task, configuration) : [];
  const canConfirm =
    !!batch &&
    batch.status === 'PREVIEWED' &&
    selected.size > 0 &&
    batch.rows
      .filter((row) => selected.has(row.rowNumber))
      .every((row) => row.status === 'READY' && nameAccepted(row.nameReview));
  return (
    <section className="import-entry">
      <h1>{config.title}导入</h1>
      <p>从{config.title}新增页面开始导入 Excel。</p>
      <Modal title={`Excel ${config.title}导入`} busy={busy} onClose={() => void close()}>
        <div className="import-wizard" ref={wizardBody} aria-busy={busy}>
          {error && (
            <Feedback tone="error">
              {error}
              <button
                type="button"
                className="text-button"
                disabled={busy || saving > 0}
                onClick={() =>
                  void action(async () => {
                    const result = await projectsApi.importTask();
                    saveFailed.current = false;
                    setPending(result);
                    if (!result) {
                      current.current = null;
                      setTask(null);
                    }
                  })
                }
              >
                重新读取任务
              </button>
              {task && (
                <button
                  type="button"
                  className="text-button"
                  disabled={busy || saving > 0}
                  onClick={() =>
                    void action(async () => {
                      await persist(configRef.current, selectedRef.current);
                    })
                  }
                >
                  重试暂存
                </button>
              )}
            </Feedback>
          )}
          {loading ? (
            <p role="status">正在读取未完成任务…</p>
          ) : pending ? (
            <section aria-label="恢复导入任务">
              <h2 tabIndex={-1}>存在未完成的导入任务</h2>
              <p>
                {pending.fileName} · 上次停留在第 {pending.step} 步
              </p>
              <p className="subtle">
                {dateTime(pending.updatedAt)}。继续可恢复原步骤和修改；从头导入会放弃该任务。
              </p>
              <div className="dialog-actions">
                <button
                  className="secondary-button"
                  disabled={busy || pending.batch?.status === 'CONFIRMING'}
                  onClick={() => void startOver()}
                >
                  从头导入
                </button>
                <button
                  className="primary-button"
                  disabled={busy}
                  onClick={() => {
                    install(pending);
                    setPending(null);
                  }}
                >
                  继续完成导入
                </button>
              </div>
            </section>
          ) : completed ? (
            <section aria-label="导入完成">
              <h2 tabIndex={-1}>导入任务已完成</h2>
              <p>
                成功导入 {completed.importedRows} 行；放弃 {completed.skippedRows} 行；失败{' '}
                {completed.failedRows} 行。
              </p>
              <p className="subtle">本次任务已结束，不再续导。下一次导入请重新选择文件。</p>
              {completed.failedRows > 0 && !!projectsApi.failedImportRows && (
                <button
                  className="secondary-button"
                  onClick={() =>
                    void action(async () =>
                      downloadWorkbook(
                        await projectsApi.failedImportRows!(completed.batchId),
                        `${config.title}导入失败行.xlsx`,
                      ),
                    )
                  }
                >
                  下载失败行
                </button>
              )}
              <div className="dialog-actions">
                <button className="secondary-button" onClick={() => void close()}>
                  返回{config.title}创建
                </button>
                <button
                  className="primary-button"
                  onClick={() => {
                    setCompleted(null);
                    setConfiguration(emptyConfiguration);
                    setSelected(new Set());
                  }}
                >
                  开始新的导入
                </button>
              </div>
            </section>
          ) : (
            <>
              <ol className="import-stepper" aria-label="导入步骤">
                {['上传文件与工作表', '字段映射', '预检与导入'].map((label, index) => (
                  <li key={label} aria-current={step === index + 1 ? 'step' : undefined}>
                    <span>{index + 1}</span>
                    {label}
                  </li>
                ))}
              </ol>
              <p className="import-save-state" role="status">
                {saving
                  ? '正在自动暂存…'
                  : task
                    ? `已暂存 · ${dateTime(task.updatedAt)}`
                    : '上传后自动暂存；每次只保留一项未完成任务。'}
              </p>
              {step === 1 && (
                <section aria-label="上传与工作表">
                  <h2 tabIndex={-1}>第一步：上传文件与确定工作表</h2>
                  <div className="action-row">
                    <label>
                      Excel 文件
                      <input
                        type="file"
                        accept=".xlsx"
                        disabled={busy || saving > 0}
                        onChange={(event) => {
                          const file = event.target.files?.[0];
                          if (file) void upload(file);
                          event.target.value = '';
                        }}
                      />
                    </label>
                    <button
                      className="secondary-button"
                      disabled={busy}
                      onClick={() =>
                        void action(async () =>
                          downloadWorkbook(
                            await projectsApi.importTemplate(),
                            `${config.title}导入模板.xlsx`,
                          ),
                        )
                      }
                    >
                      下载导入模板
                    </button>
                  </div>
                  <p className="subtle">
                    支持 .xlsx，文件最大 10 MB，每次最多 1000 行。{config.uploadHint}
                  </p>
                  {task && (
                    <>
                      <p>
                        已上传：<strong>{task.fileName}</strong>
                      </p>
                      <div className="form-grid">
                        <label>
                          工作表
                          <Select
                            value={configuration.sheetName ?? ''}
                            disabled={busy}
                            onChange={(event) =>
                              changeConfiguration(chooseSheet(task, event.target.value))
                            }
                          >
                            <option value="">请选择工作表</option>
                            {task.inspection.sheets.map((sheet) => (
                              <option
                                key={sheet.name}
                                value={sheet.name}
                                disabled={!sheet.headerCandidates.length}
                              >
                                {sheet.name}
                              </option>
                            ))}
                          </Select>
                        </label>
                        <label>
                          表头行
                          <Select
                            value={configuration.headerRowNo ?? ''}
                            disabled={busy}
                            onChange={(event) =>
                              changeConfiguration(
                                chooseSheet(
                                  task,
                                  configuration.sheetName!,
                                  Number(event.target.value),
                                ),
                              )
                            }
                          >
                            <option value="">请选择表头行</option>
                            {sheet?.headerCandidates.map((row) => (
                              <option key={row.rowNo} value={row.rowNo}>
                                第 {row.rowNo} 行 · {row.headers.slice(0, 3).join('、')}
                              </option>
                            ))}
                          </Select>
                        </label>
                      </div>
                    </>
                  )}
                </section>
              )}
              {step === 2 && task && (
                <ImportFieldMapping
                  task={task}
                  configuration={configuration}
                  disabled={busy}
                  onChange={changeConfiguration}
                />
              )}
              {step === 3 && batch && (
                <section aria-label="预检与导入">
                  <h2 tabIndex={-1}>第三步：预检与导入</h2>
                  <div className="panel-top">
                    <strong>
                      总计 {batch.totalRows} 行 · 字段通过 {batch.validRows} 行 · 失败{' '}
                      {batch.failedRows} 行
                    </strong>
                    <span>已选 {selected.size} 行</span>
                  </div>
                  <p className="subtle">
                    {config.previewHint} 确认导入后本次任务结束，其余行放弃。
                  </p>
                  {batch.status === 'PREVIEWED' ? (
                    <>
                      <div className="action-row">
                        <button
                          className="text-button"
                          disabled={busy}
                          onClick={() =>
                            changeSelection(
                              new Set(
                                batch.rows
                                  .filter((row) => row.status === 'READY')
                                  .map((row) => row.rowNumber),
                              ),
                            )
                          }
                        >
                          选择全部通过行
                        </button>
                        <button
                          className="text-button"
                          disabled={busy}
                          onClick={() => changeSelection(new Set())}
                        >
                          清空选择
                        </button>
                      </div>
                      <ImportPreviewTable
                        batch={batch}
                        selected={selected}
                        disabled={busy}
                        onSelect={changeSelection}
                        onEdit={(row) => setEditor({ row, mode: 'edit' })}
                        onName={(row) => setEditor({ row, mode: 'name' })}
                      />
                    </>
                  ) : (
                    <>
                      <p role="status">
                        确认导入正在处理，不能修改或开启新任务。刷新可查看结果；超过 15
                        分钟的中断由系统自动恢复。
                      </p>
                      <button
                        className="secondary-button"
                        disabled={busy}
                        onClick={() =>
                          void action(async () => {
                            const result = await projectsApi.importBatch(batch.batchId);
                            if (result.status === 'COMPLETED') {
                              setCompleted(result);
                              setTask(null);
                              current.current = null;
                            } else batchUpdated(result);
                          })
                        }
                      >
                        刷新导入结果
                      </button>
                    </>
                  )}
                </section>
              )}
              {step === 2 && missing.length > 0 && (
                <Feedback tone="error">请先映射必填字段：{missing.join('、')}</Feedback>
              )}
              <footer className="import-wizard-footer">
                <div className="action-row">
                  {step > 1 && (
                    <button
                      type="button"
                      className="secondary-button"
                      disabled={busy || saving > 0 || batch?.status === 'CONFIRMING'}
                      onClick={() => void move(step - 1)}
                    >
                      上一步
                    </button>
                  )}
                  {step < 3 ? (
                    <button
                      type="button"
                      className="primary-button"
                      disabled={
                        busy ||
                        saving > 0 ||
                        !task ||
                        !configuration.sheetName ||
                        !configuration.headerRowNo ||
                        (step === 2 && missing.length > 0)
                      }
                      onClick={() => void move(step + 1)}
                    >
                      {busy ? '处理中…' : step === 1 ? '下一步：字段映射' : '下一步：开始预检'}
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="primary-button"
                      disabled={busy || saving > 0 || !canConfirm}
                      onClick={() => void confirm()}
                    >
                      {busy ? '正在导入…' : `确认导入 ${selected.size} 行并结束任务`}
                    </button>
                  )}
                </div>
              </footer>
              {step === 3 && !canConfirm && selected.size > 0 && batch?.status === 'PREVIEWED' && (
                <p className="subtle">请先处理所选行的相似项目提示，或取消选择相关行。</p>
              )}
            </>
          )}
        </div>
      </Modal>
      {editor && batch && (
        <ImportRowDialog
          key={`${editor.row.rowNumber}-${editor.mode}`}
          batchId={batch.batchId}
          row={editor.row}
          mode={editor.mode}
          onClose={() => setEditor(null)}
          onSaved={batchUpdated}
        />
      )}
      <Link to={config.backTo} className="text-button">
        返回{config.title}创建
      </Link>
    </section>
  );
}
