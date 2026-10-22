import { Select } from '../../shared/ui/Select';
import { Fragment, useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { projectsApi } from './api';
import type { ImportBatch, ImportBatchListItem, ImportWorkbookInspection } from './types';
import type { Page } from '../../shared/api/http';
import { Feedback } from '../../shared/ui/Feedback';
import { dateTime } from '../../shared/ui/format';

function rowErrors(value: string): string[] {
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map(String) : ['错误信息无法解析'];
  } catch {
    return ['错误信息无法解析'];
  }
}

function statusLabel(status: string): string {
  return (
    {
      PREVIEWED: '待确认',
      CONFIRMING: '导入中',
      COMPLETED: '已完成',
      CANCELLED: '已取消',
      READY: '通过预检',
      FAILED: '校验或写入失败',
      IMPORTED: '已导入',
      SKIPPED: '已跳过',
    }[status] ?? status
  );
}

function sourceCells(value: string | null): Record<string, string> {
  if (!value) return {};
  try {
    const parsed: unknown = JSON.parse(value);
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return Object.fromEntries(
        Object.entries(parsed).filter(
          (entry): entry is [string, string] => typeof entry[1] === 'string',
        ),
      );
    }
  } catch {
    return {};
  }
  return {};
}

function downloadWorkbook(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function bestHeaderCandidate(sheet: ImportWorkbookInspection['sheets'][number]) {
  return (
    sheet.headerCandidates.find(
      (candidate) => candidate.headers.includes('省分') && candidate.headers.includes('项目名称'),
    ) ??
    [...sheet.headerCandidates].sort((left, right) => right.headers.length - left.headers.length)[0]
  );
}

export function ImportProjectsPage() {
  const [file, setFile] = useState<File | null>(null);
  const [inspection, setInspection] = useState<ImportWorkbookInspection | null>(null);
  const [selectedSheet, setSelectedSheet] = useState('');
  const [selectedHeaderRow, setSelectedHeaderRow] = useState(1);
  const [fieldMapping, setFieldMapping] = useState<Record<string, string>>({});
  const [batch, setBatch] = useState<ImportBatch | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [plans, setPlans] = useState<Page<ImportBatchListItem> | null>(null);
  const [plansLoading, setPlansLoading] = useState(true);
  const [planPage, setPlanPage] = useState(0);
  const [selectedRows, setSelectedRows] = useState<Set<number>>(new Set());
  const [editingRow, setEditingRow] = useState<number | null>(null);
  const [editedCells, setEditedCells] = useState<Record<string, string>>({});
  const readyRows = batch?.rows.filter((row) => row.status === 'READY') ?? [];
  const sheet = inspection?.sheets.find((item) => item.name === selectedSheet);
  const headerCandidate = sheet?.headerCandidates.find((item) => item.rowNo === selectedHeaderRow);

  function selectCandidate(sheetName: string, rowNo: number, source: ImportWorkbookInspection) {
    const headers =
      source.sheets
        .find((item) => item.name === sheetName)
        ?.headerCandidates.find((item) => item.rowNo === rowNo)?.headers ?? [];
    setSelectedSheet(sheetName);
    setSelectedHeaderRow(rowNo);
    const mapping = Object.fromEntries(
      source.fields.filter((field) => headers.includes(field)).map((field) => [field, field]),
    );
    if (
      !headers.includes('三化类别') &&
      headers.some((header) => /^三化类别[一二三四]$/.test(header))
    ) {
      mapping['三化类别'] = '__CATEGORY_SLOTS__';
    }
    setFieldMapping(mapping);
  }

  useEffect(() => {
    let active = true;
    setPlansLoading(true);
    projectsApi
      .importPlans(planPage)
      .then((result) => {
        if (active) setPlans(result);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '导入计划加载失败');
      })
      .finally(() => {
        if (active) setPlansLoading(false);
      });
    return () => {
      active = false;
    };
  }, [planPage]);

  async function refreshPlans() {
    try {
      setPlans(await projectsApi.importPlans(planPage));
    } catch {
      setError('操作已完成，但计划列表刷新失败。请稍后重新打开此页核对状态。');
    }
  }

  function displayBatch(result: ImportBatch) {
    setBatch(result);
    setEditingRow(null);
    setSelectedRows(
      new Set(result.rows.filter((row) => row.status === 'READY').map((row) => row.rowNumber)),
    );
  }

  function editRow(rowNumber: number) {
    const row = batch?.rows.find((item) => item.rowNumber === rowNumber);
    if (!row) return;
    setEditedCells(sourceCells(row.sourceCellsJson));
    setEditingRow(rowNumber);
    setError('');
  }

  async function saveRow(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const row = batch?.rows.find((item) => item.rowNumber === editingRow);
    if (!batch || !row) return;
    const before = sourceCells(row.sourceCellsJson);
    const changes = Object.fromEntries(
      Object.entries(editedCells).filter(([key, value]) => value !== before[key]),
    );
    if (Object.keys(changes).length === 0) {
      setError('请先修改至少一个字段。');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const result = await projectsApi.reviseImportRow(
        batch.batchId,
        row.rowNumber,
        row.revisionNo,
        changes,
      );
      setBatch(result);
      setEditingRow(null);
      setSelectedRows(
        (current) =>
          new Set(
            result.rows
              .filter((item) => item.status === 'READY')
              .map((item) => item.rowNumber)
              .filter((number) => current.has(number) || number === row.rowNumber),
          ),
      );
      await refreshPlans();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '导入行修正失败');
    } finally {
      setBusy(false);
    }
  }

  async function openPlan(id: string) {
    setBusy(true);
    setError('');
    try {
      displayBatch(await projectsApi.importBatch(id));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '导入计划读取失败');
    } finally {
      setBusy(false);
    }
  }

  async function preview(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file) return;
    setBusy(true);
    setError('');
    setBatch(null);
    try {
      const mapping = inspection
        ? Object.fromEntries(inspection.fields.map((field) => [field, fieldMapping[field] ?? '']))
        : {};
      const mappedColumns = Object.values(mapping).filter(Boolean);
      if (inspection && mappedColumns.length === 0) {
        setError('请至少映射一个业务字段。');
        return;
      }
      if (inspection && mappedColumns.length !== new Set(mappedColumns).size) {
        setError('同一 Excel 列不能映射给多个业务字段。');
        return;
      }
      displayBatch(
        await projectsApi.previewImport(
          file,
          inspection
            ? {
                sheetName: selectedSheet,
                headerRowNo: selectedHeaderRow,
                mapping,
              }
            : undefined,
        ),
      );
      await refreshPlans();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '预检失败');
    } finally {
      setBusy(false);
    }
  }

  async function inspectWorkbook() {
    if (!file) return;
    setBusy(true);
    setError('');
    try {
      const result = await projectsApi.inspectImport(file);
      setInspection(result);
      const preferred = result.sheets.find((item) => item.name === '导入数据') ?? result.sheets[0];
      const candidate = preferred && bestHeaderCandidate(preferred);
      if (preferred && candidate) selectCandidate(preferred.name, candidate.rowNo, result);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '工作表识别失败');
    } finally {
      setBusy(false);
    }
  }

  async function confirm() {
    if (
      !batch ||
      selectedRows.size === 0 ||
      !window.confirm(
        `确认导入选中的 ${selectedRows.size} 条项目？未选行会保留为“已跳过”，失败行不会写入。`,
      )
    )
      return;
    setBusy(true);
    setError('');
    try {
      displayBatch(await projectsApi.confirmImport(batch.batchId, [...selectedRows]));
      await refreshPlans();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '导入失败');
    } finally {
      setBusy(false);
    }
  }

  async function cancelPlan() {
    if (!batch || !window.confirm('取消这条导入计划？预检记录仍会保留，已导入项目不会删除。'))
      return;
    setBusy(true);
    setError('');
    try {
      displayBatch(await projectsApi.cancelImport(batch.batchId));
      await refreshPlans();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '取消导入计划失败');
    } finally {
      setBusy(false);
    }
  }

  async function resumePlan() {
    if (!batch) return;
    setBusy(true);
    setError('');
    try {
      displayBatch(await projectsApi.resumeImport(batch.batchId));
      await refreshPlans();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '导入恢复失败');
    } finally {
      setBusy(false);
    }
  }

  async function downloadTemplate() {
    setBusy(true);
    setError('');
    try {
      downloadWorkbook(await projectsApi.importTemplate(), '项目导入模板.xlsx');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '导入模板下载失败');
    } finally {
      setBusy(false);
    }
  }

  async function downloadFailedRows() {
    if (!batch) return;
    setBusy(true);
    setError('');
    try {
      downloadWorkbook(await projectsApi.failedImportRows(batch.batchId), '项目导入失败行.xlsx');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '失败行报告下载失败');
    } finally {
      setBusy(false);
    }
  }

  function toggleRow(rowNumber: number) {
    setSelectedRows((current) => {
      const next = new Set(current);
      if (next.has(rowNumber)) next.delete(rowNumber);
      else next.add(rowNumber);
      return next;
    });
  }

  return (
    <section>
      <div className="page-heading">
        <div>
          <span className="eyebrow">PROJECT IMPORT</span>
          <h1>Excel 导入项目</h1>
          <p>先预检，再确认写入。重复项目仅保留文件中的第一条；失败行不会进入主档。</p>
        </div>
        <Link className="secondary-button" to="/projects">
          返回项目列表
        </Link>
      </div>
      <div className="panel">
        <div className="panel-top">
          <h2>选择导入文件</h2>
          <button
            className="secondary-button"
            type="button"
            disabled={busy}
            onClick={() => void downloadTemplate()}
          >
            下载导入模板
          </button>
        </div>
        <p className="subtle">使用既定项目导入模板，文件不超过 10 MB。预检不会写入项目。</p>
        <form className="filter-bar" onSubmit={preview}>
          <label>
            Excel 文件
            <input
              type="file"
              accept=".xlsx"
              required
              disabled={busy}
              onChange={(event) => {
                setFile(event.target.files?.[0] ?? null);
                setInspection(null);
                setFieldMapping({});
                setBatch(null);
                setSelectedRows(new Set());
                setEditingRow(null);
                setError('');
              }}
            />
          </label>
          <button
            className="secondary-button"
            disabled={busy || !file}
            type="button"
            onClick={() => void inspectWorkbook()}
          >
            识别工作表与表头
          </button>
          <button className="primary-button" disabled={busy || !file} type="submit">
            {busy ? '处理中…' : '开始预检'}
          </button>
        </form>
        {inspection && (
          <div className="import-layout">
            <p className="subtle">
              已识别 {inspection.sheets.length}{' '}
              个工作表。选择表头行并核对字段；留空的必填字段可在预检后逐行修正。
            </p>
            <div className="filter-bar">
              <label>
                工作表
                <Select
                  value={selectedSheet}
                  onChange={(event) => {
                    const next = inspection.sheets.find((item) => item.name === event.target.value);
                    const candidate = next && bestHeaderCandidate(next);
                    if (next && candidate) selectCandidate(next.name, candidate.rowNo, inspection);
                  }}
                >
                  {inspection.sheets.map((item) => (
                    <option key={item.name} value={item.name}>
                      {item.name}
                    </option>
                  ))}
                </Select>
              </label>
              <label>
                表头行
                <Select
                  value={selectedHeaderRow}
                  onChange={(event) =>
                    selectCandidate(selectedSheet, Number(event.target.value), inspection)
                  }
                >
                  {sheet?.headerCandidates.map((item) => (
                    <option key={item.rowNo} value={item.rowNo}>
                      第 {item.rowNo} 行：{item.headers.slice(0, 3).join('、')}
                    </option>
                  ))}
                </Select>
              </label>
            </div>
            <div className="import-row-fields">
              {inspection.fields.map((field) => (
                <label key={field}>
                  {field}
                  <Select
                    value={fieldMapping[field] ?? ''}
                    onChange={(event) =>
                      setFieldMapping((current) => ({ ...current, [field]: event.target.value }))
                    }
                  >
                    <option value="">不映射，预检后修正</option>
                    {field === '三化类别' &&
                      headerCandidate?.headers.some((header) =>
                        /^三化类别[一二三四]$/.test(header),
                      ) && <option value="__CATEGORY_SLOTS__">合并三化类别一至四列</option>}
                    {headerCandidate?.headers.map((header) => (
                      <option key={header} value={header}>
                        {header}
                      </option>
                    ))}
                  </Select>
                </label>
              ))}
            </div>
          </div>
        )}
      </div>
      <div className="panel import-plans">
        <div className="panel-top">
          <strong>我的导入计划</strong>
          <span>{plans?.totalElements ?? 0} 条</span>
        </div>
        {plans?.items.map((plan) => (
          <div className="import-plan-item" key={plan.batchId}>
            <div>
              <strong>{plan.fileName}</strong>
              <small>
                {dateTime(plan.createdAt)} · {statusLabel(plan.status)} · 已导入 {plan.importedRows}
                /{plan.totalRows}
              </small>
            </div>
            <button
              className="secondary-button"
              type="button"
              disabled={busy}
              onClick={() => void openPlan(plan.batchId)}
            >
              查看计划
            </button>
          </div>
        ))}
        {plansLoading && <div className="empty-state">正在加载导入计划…</div>}
        {!plansLoading && plans?.items.length === 0 && (
          <div className="empty-state">暂无导入计划。</div>
        )}
        {(plans?.totalPages ?? 0) > 1 && (
          <div className="pager">
            <span>
              第 {planPage + 1} / {plans?.totalPages} 页
            </span>
            <div>
              <button
                className="secondary-button"
                type="button"
                disabled={busy || planPage === 0}
                onClick={() => setPlanPage(planPage - 1)}
              >
                上一页
              </button>
              <button
                className="secondary-button"
                type="button"
                disabled={busy || planPage + 1 >= (plans?.totalPages ?? 0)}
                onClick={() => setPlanPage(planPage + 1)}
              >
                下一页
              </button>
            </div>
          </div>
        )}
      </div>
      {error && <Feedback tone="error">{error}</Feedback>}
      {batch && (
        <div className="panel table-panel">
          <div className="panel-top">
            <strong>预检结果</strong>
            <span>
              总计 {batch.totalRows} 行 · 预检通过 {batch.validRows} 行 · 失败 {batch.failedRows} 行
              {batch.skippedRows > 0 ? ` · 已跳过 ${batch.skippedRows} 行` : ''}
            </span>
          </div>
          <p className="subtle">
            批次 {batch.batchId} · 状态 {statusLabel(batch.status)}
            {batch.status !== 'PREVIEWED' ? ` · 已导入 ${batch.importedRows} 行` : ''}
          </p>
          {batch.rows.some((row) => row.status === 'FAILED') && (
            <div className="action-row import-actions">
              <button
                className="secondary-button"
                type="button"
                disabled={busy}
                onClick={() => void downloadFailedRows()}
              >
                导出失败行
              </button>
            </div>
          )}
          {batch.status === 'PREVIEWED' && (
            <p className="subtle">
              预检失败的行可直接修正并重新校验；修正记录会保留。重复项目仍只保留首条。
            </p>
          )}
          {batch.status === 'CONFIRMING' && (
            <div className="action-row import-actions">
              <p className="subtle">
                确认中断超过 15 分钟后可手动恢复；系统也会定期自动重试。刷新计划可查看最新结果。
              </p>
              <button
                className="secondary-button"
                type="button"
                disabled={busy}
                onClick={() => void openPlan(batch.batchId)}
              >
                刷新状态
              </button>
              {batch.confirmedAt &&
                Date.now() - new Date(batch.confirmedAt).getTime() >= 15 * 60 * 1000 && (
                  <button
                    className="primary-button"
                    type="button"
                    disabled={busy}
                    onClick={() => void resumePlan()}
                  >
                    恢复导入
                  </button>
                )}
            </div>
          )}
          {batch.status === 'PREVIEWED' && (
            <div className="action-row import-actions">
              <button
                type="button"
                className="primary-button"
                disabled={busy || selectedRows.size === 0}
                onClick={() => void confirm()}
              >
                确认导入已选 {selectedRows.size} 行
              </button>
              <button
                type="button"
                className="secondary-button"
                disabled={busy}
                onClick={() => void cancelPlan()}
              >
                取消计划
              </button>
            </div>
          )}
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>
                    <label className="import-select-all">
                      <input
                        type="checkbox"
                        disabled={batch.status !== 'PREVIEWED' || readyRows.length === 0}
                        checked={readyRows.length > 0 && selectedRows.size === readyRows.length}
                        onChange={(event) =>
                          setSelectedRows(
                            event.target.checked
                              ? new Set(readyRows.map((row) => row.rowNumber))
                              : new Set(),
                          )
                        }
                      />
                      <span>全选</span>
                    </label>
                  </th>
                  <th>Excel 行号</th>
                  <th>状态</th>
                  <th>校验结果</th>
                  <th>项目 / 操作</th>
                </tr>
              </thead>
              <tbody>
                {batch.rows.map((row) => (
                  <Fragment key={row.rowNumber}>
                    <tr>
                      <td>
                        {row.status === 'READY' && batch.status === 'PREVIEWED' && (
                          <input
                            type="checkbox"
                            aria-label={`选择第 ${row.rowNumber} 行`}
                            checked={selectedRows.has(row.rowNumber)}
                            onChange={() => toggleRow(row.rowNumber)}
                          />
                        )}
                      </td>
                      <td>{row.rowNumber}</td>
                      <td>{statusLabel(row.status)}</td>
                      <td>{rowErrors(row.errorsJson).join('；') || '通过'}</td>
                      <td>
                        {row.projectId && (
                          <Link className="inline-link" to={`/projects/${row.projectId}`}>
                            查看项目
                          </Link>
                        )}
                        {batch.status === 'PREVIEWED' && row.sourceCellsJson && (
                          <button
                            className="text-button"
                            type="button"
                            disabled={busy}
                            onClick={() => editRow(row.rowNumber)}
                          >
                            修正第 {row.rowNumber} 行
                          </button>
                        )}
                      </td>
                    </tr>
                    {editingRow === row.rowNumber && (
                      <tr>
                        <td colSpan={5}>
                          <form
                            className="import-row-editor"
                            onSubmit={(event) => void saveRow(event)}
                          >
                            <div className="panel-top">
                              <strong>修正 Excel 第 {row.rowNumber} 行</strong>
                              <span>第 {row.revisionNo} 次修订</span>
                            </div>
                            <p className="subtle">
                              保存后立即重新预检；校验未通过时仍不会写入项目。
                            </p>
                            {error && <Feedback tone="error">{error}</Feedback>}
                            {rowErrors(row.errorsJson).length > 0 && (
                              <Feedback>
                                当前校验问题：{rowErrors(row.errorsJson).join('；')}
                              </Feedback>
                            )}
                            <div className="import-row-fields">
                              {Object.keys(editedCells)
                                .sort()
                                .map((header) => (
                                  <label key={header}>
                                    {header}
                                    <input
                                      value={editedCells[header]}
                                      maxLength={4000}
                                      onChange={(event) =>
                                        setEditedCells((current) => ({
                                          ...current,
                                          [header]: event.target.value,
                                        }))
                                      }
                                    />
                                  </label>
                                ))}
                            </div>
                            <div className="action-row">
                              <button className="primary-button" type="submit" disabled={busy}>
                                保存并重新预检
                              </button>
                              <button
                                className="secondary-button"
                                type="button"
                                disabled={busy}
                                onClick={() => setEditingRow(null)}
                              >
                                取消修正
                              </button>
                            </div>
                          </form>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}
