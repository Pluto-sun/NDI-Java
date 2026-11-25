import { Select } from '../ui/Select';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Modal } from '../ui/Modal';
import { Feedback } from '../ui/Feedback';
import { useImportWizard } from './context';
import type { ImportBatch } from './types';
import { sourceCells, rowErrors, type ImportRow } from './importWizard';

export function ImportRowDialog({
  batchId,
  row,
  fieldOrder,
  hiddenFields = [],
  mode,
  onClose,
  onSaved,
}: {
  batchId: string;
  row: ImportRow;
  fieldOrder: string[];
  hiddenFields?: string[];
  mode: 'edit' | 'name';
  onClose: () => void;
  onSaved: (batch: ImportBatch) => void;
}) {
  const config = useImportWizard();
  const { api: projectsApi } = config;
  const [cells, setCells] = useState(() => {
    const values = sourceCells(row.sourceCellsJson);
    const format = config.formatAmount;
    if (format)
      for (const key of config.amountFields)
        if (Object.hasOwn(values, key)) values[key] = format(values[key]);
    return values;
  });
  const mappedFields = new Set(fieldOrder);
  const fields = [
    ...fieldOrder.filter((field) => Object.hasOwn(cells, field)),
    ...Object.keys(cells)
      .filter((field) => !mappedFields.has(field))
      .sort((left, right) => left.localeCompare(right, 'zh-CN', { numeric: true })),
  ].filter((field) => !hiddenFields.includes(field) && !config.hiddenRowFields?.includes(field));
  const [reason, setReason] = useState(row.nameReview?.reason ?? '');
  const [confirmed, setConfirmed] = useState(row.nameReview?.confirmed ?? false);
  const [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      let result: ImportBatch;
      if (mode === 'name')
        result = await projectsApi.decideImportName!(
          batchId,
          row.rowNumber,
          row.revisionNo,
          reason,
          row.nameReview!.fingerprint,
        );
      else {
        const before = sourceCells(row.sourceCellsJson);
        const changes = Object.fromEntries(
          Object.entries(cells).filter(([key, value]) => value !== before[key]),
        );
        if (!Object.keys(changes).length) {
          setError('请先修改至少一个字段。');
          return;
        }
        result = await projectsApi.reviseImportRow(batchId, row.rowNumber, row.revisionNo, changes);
      }
      onSaved(result);
      onClose();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '保存失败，请重试');
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title={mode === 'name' ? `第 ${row.rowNumber} 行相似项目确认` : `修正第 ${row.rowNumber} 行`}
      busy={busy}
      onClose={onClose}
    >
      <h2 tabIndex={-1}>{mode === 'name' ? '核对相似项目' : '查看与修正导入数据'}</h2>
      <p>
        {cells['项目名称'] || config.emptyValueLabel} · Excel 原行号 {row.rowNumber}
      </p>
      {error && <Feedback tone="error">{error}</Feedback>}
      <form onSubmit={(event) => void submit(event)}>
        {mode === 'name' && row.nameReview ? (
          <>
            <p className="subtle">
              {row.nameReview.level === 'STRONG'
                ? '与已有或文件内项目高度相似，请修改名称，或填写至少 5 字的差异说明。'
                : '存在相似项目，请核对并确认不是同一项目。'}
            </p>
            <ul className="import-name-matches">
              {row.nameReview.matches.map((match, index) => (
                <li key={index}>
                  <strong>{match.projectName}</strong>
                  <span>{(match.score * 100).toFixed(1)}%</span>
                  <small>
                    {match.sourceRowNumber
                      ? `本文件第 ${match.sourceRowNumber} 行`
                      : match.projectCode}
                  </small>
                  {match.projectId && (
                    <Link to={`/projects/${match.projectId}`} target="_blank" rel="noreferrer">
                      查看已有项目 ↗
                    </Link>
                  )}
                </li>
              ))}
            </ul>
            <label>
              差异说明{row.nameReview.level === 'STRONG' ? '（必填，至少 5 字）' : '（选填）'}
              <textarea
                value={reason}
                minLength={row.nameReview.level === 'STRONG' ? 5 : undefined}
                required={row.nameReview.level === 'STRONG'}
                maxLength={1000}
                rows={3}
                onChange={(event) => setReason(event.target.value)}
              />
            </label>
            <label className="check-label">
              <input
                type="checkbox"
                checked={confirmed}
                onChange={(event) => setConfirmed(event.target.checked)}
                required
              />
              确认不是同一项目
            </label>
          </>
        ) : (
          <>
            {rowErrors(row.errorsJson).length > 0 && (
              <Feedback tone="error">{rowErrors(row.errorsJson).join('；')}</Feedback>
            )}
            <div className="form-grid import-row-fields">
              {fields.map((field) => {
                const value = config.displayCell?.(field, cells[field]) ?? cells[field];
                const multiple = config.multipleFieldOptions?.(field);
                const selected = multiple
                  ? value
                      .split(/[,，、;；\n]+/)
                      .map((item) => item.trim())
                      .filter(Boolean)
                      .map((item) => multiple.find(({ label }) => label === item)?.code ?? item)
                  : [];
                const options = config.fieldOptions?.(field, cells);
                return (
                  <label key={field}>
                    {config.fieldLabel?.(field) ?? field}
                    {multiple ? (
                      <select
                        multiple
                        value={selected}
                        onChange={(event) => {
                          const value = Array.from(
                            event.currentTarget.selectedOptions,
                            (option) => option.value,
                          ).join('；');
                          setCells((current) => ({ ...current, [field]: value }));
                        }}
                      >
                        {selected
                          .filter((item) => !multiple.some(({ code }) => code === item))
                          .map((item) => (
                            <option key={item} value={item}>
                              {item}（原值）
                            </option>
                          ))}
                        {multiple.map(({ code, label }) => (
                          <option key={code} value={code}>
                            {label}
                          </option>
                        ))}
                      </select>
                    ) : options ? (
                      <Select
                        value={value}
                        onChange={(event) =>
                          setCells(
                            (current) =>
                              config.changeRowField?.(current, field, event.target.value) ?? {
                                ...current,
                                [field]: event.target.value,
                              },
                          )
                        }
                      >
                        {!options.includes(value) && (
                          <option value={value}>
                            {value || config.emptyValueLabel || '请选择'}
                          </option>
                        )}
                        {options.map((option) => (
                          <option key={option} value={option}>
                            {option || config.emptyValueLabel || '未填写'}
                          </option>
                        ))}
                      </Select>
                    ) : (
                      <textarea
                        aria-label={config.fieldLabel?.(field) ?? field}
                        placeholder={config.emptyValueLabel}
                        rows={
                          field.includes('内容') || field.includes('进展') || field === '下一步动作'
                            ? 3
                            : 1
                        }
                        value={config.emptyValueLabel && !value.trim() ? '' : value}
                        maxLength={['首条项目进展', '下一步动作'].includes(field) ? 10000 : 4000}
                        onChange={(event) =>
                          setCells(
                            (current) =>
                              config.changeRowField?.(current, field, event.target.value) ?? {
                                ...current,
                                [field]: event.target.value,
                              },
                          )
                        }
                      />
                    )}
                  </label>
                );
              })}
            </div>
          </>
        )}
        <div className="dialog-actions">
          <button type="button" className="secondary-button" disabled={busy} onClick={onClose}>
            取消
          </button>
          <button className="primary-button" disabled={busy || (mode === 'name' && !confirmed)}>
            {busy ? '保存中…' : mode === 'name' ? '确认并记录' : '保存并重新校验'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
