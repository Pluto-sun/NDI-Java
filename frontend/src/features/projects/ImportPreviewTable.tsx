import { useState } from 'react';
import type { ImportBatch } from './types';
import { nameAccepted, rowErrors, sourceCells, type ImportRow } from './importWizard';

const rowHeight = 104,
  overscan = 6;
/** 固定行高窗口化渲染，千行预检也保持滚动响应；完整内容在行弹窗读取。 */
export function ImportPreviewTable({
  batch,
  selected,
  onSelect,
  onEdit,
  onName,
  disabled,
}: {
  batch: ImportBatch;
  selected: Set<number>;
  onSelect: (value: Set<number>) => void;
  onEdit: (row: ImportRow) => void;
  onName: (row: ImportRow) => void;
  disabled: boolean;
}) {
  const [scrollTop, setScrollTop] = useState(0);
  const start = Math.max(0, Math.floor(Math.max(0, scrollTop - 42) / rowHeight) - overscan);
  const end = Math.min(batch.rows.length, start + 20);
  return (
    <div
      className="table-scroll import-preview-scroll"
      tabIndex={0}
      aria-label="预检结果，可滚动"
      onScroll={(event) => setScrollTop(event.currentTarget.scrollTop)}
    >
      <table
        className="import-preview-table"
        aria-label="项目导入预检结果"
        aria-rowcount={batch.rows.length + 1}
      >
        <thead>
          <tr>
            <th>选择</th>
            <th>原行号</th>
            <th>项目与归属</th>
            <th>预检结果</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {start > 0 && (
            <tr aria-hidden="true">
              <td colSpan={5} style={{ height: start * rowHeight, padding: 0, border: 0 }} />
            </tr>
          )}
          {batch.rows.slice(start, end).map((row, index) => {
            const cells = sourceCells(row.sourceCellsJson),
              errors = rowErrors(row.errorsJson);
            const pending = !nameAccepted(row.nameReview);
            return (
              <tr
                key={row.rowNumber}
                aria-rowindex={start + index + 2}
                style={{ height: rowHeight }}
              >
                <td>
                  <input
                    type="checkbox"
                    aria-label={`选择第 ${row.rowNumber} 行`}
                    checked={selected.has(row.rowNumber)}
                    disabled={disabled || row.status !== 'READY'}
                    onChange={(event) => {
                      const next = new Set(selected);
                      if (event.target.checked) next.add(row.rowNumber);
                      else next.delete(row.rowNumber);
                      onSelect(next);
                    }}
                  />
                </td>
                <td>{row.rowNumber}</td>
                <td>
                  <strong className="import-cell-clamp">
                    {cells['项目名称'] || '未填写项目名称'}
                  </strong>
                  <small>
                    {cells['省分']} / {cells['地市']}
                  </small>
                  <small>{cells['项目总金额（万）'] || '未填金额'} 万元</small>
                </td>
                <td>
                  <span
                    className={
                      row.status === 'FAILED'
                        ? 'import-status-error'
                        : pending
                          ? 'import-status-warning'
                          : 'import-status-success'
                    }
                  >
                    {row.status === 'FAILED'
                      ? '字段校验不通过'
                      : pending
                        ? '待确认相似项目'
                        : row.status === 'READY'
                          ? '通过预检'
                          : row.status === 'IMPORTED'
                            ? '已导入'
                            : row.status === 'SKIPPED'
                              ? '已跳过'
                              : '失败'}
                  </span>
                  <small className="import-cell-clamp">
                    {errors.length
                      ? errors.join('；')
                      : row.nameReview?.level !== 'NONE' && row.nameReview
                        ? `最高相似度 ${(row.nameReview.score * 100).toFixed(1)}%${row.nameReview.confirmed ? ' · 已确认' : ''}`
                        : '未发现相似项目'}
                  </small>
                </td>
                <td>
                  <div className="import-row-actions">
                    <button
                      type="button"
                      className="text-button"
                      disabled={disabled}
                      onClick={() => onEdit(row)}
                    >
                      查看／修正第 {row.rowNumber} 行
                    </button>
                    {row.nameReview && row.nameReview.level !== 'NONE' && (
                      <button
                        type="button"
                        className="text-button"
                        disabled={disabled || row.status !== 'READY'}
                        onClick={() => onName(row)}
                      >
                        处理相似项目
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
          {end < batch.rows.length && (
            <tr aria-hidden="true">
              <td
                colSpan={5}
                style={{ height: (batch.rows.length - end) * rowHeight, padding: 0, border: 0 }}
              />
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
