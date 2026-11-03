import { ListPagination } from '../ui/ListPagination';
import { useImportWizard } from './context';
import { useRef, useState } from 'react';
import type { ImportBatch } from './types';
import { nameAccepted, rowErrors, sourceCells, type ImportRow } from './importWizard';

const pageSize = 10;
/** 按状态筛选后分页；选择集合仍覆盖所有页，原始行号保持不变。 */
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
  const config = useImportWizard();
  const [filter, setFilter] = useState('ALL');
  const [page, setPage] = useState(0);
  const scroll = useRef<HTMLDivElement>(null);
  const passed = (row: ImportRow) => row.status === 'READY' && nameAccepted(row.nameReview);
  const passedCount = batch.rows.filter(passed).length;
  const rows = batch.rows.filter(
    (row) => filter === 'ALL' || (filter === 'PASSED' ? passed(row) : !passed(row)),
  );
  const pages = Math.ceil(rows.length / pageSize);
  const safePage = Math.min(page, Math.max(0, pages - 1));
  const start = safePage * pageSize;
  const changePage = (value: number) => {
    setPage(value);
    if (scroll.current) scroll.current.scrollTop = 0;
  };
  return (
    <>
      <div className="import-preview-toolbar">
        <div className="import-status-filters" role="group" aria-label="预检状态筛选">
          {[
            { key: 'ALL', label: '全部', count: batch.rows.length },
            { key: 'PASSED', label: '预检通过', count: passedCount },
            { key: 'FAILED', label: '失败', count: batch.rows.length - passedCount },
          ].map((item) => (
            <button
              key={item.key}
              type="button"
              className="secondary-button"
              aria-label={item.label}
              aria-pressed={filter === item.key}
              disabled={disabled}
              onClick={() => {
                setFilter(item.key);
                changePage(0);
              }}
            >
              {item.label}
              <span>{item.count}</span>
            </button>
          ))}
        </div>
        <span className="subtle">每页 10 条，筛选及翻页保留已选行</span>
      </div>
      <p className="subtle">失败筛选包含字段错误及待确认的相似项目；修正或确认后重新归类。</p>
      <div
        className="table-scroll import-preview-scroll"
        ref={scroll}
        tabIndex={0}
        aria-label="预检结果，可滚动"
      >
        <table
          className="import-preview-table"
          aria-label={`${config.title}导入预检结果`}
          aria-rowcount={rows.length + 1}
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
            {rows.slice(start, start + pageSize).map((row, index) => {
              const cells = sourceCells(row.sourceCellsJson),
                errors = rowErrors(row.errorsJson);
              const pending = !nameAccepted(row.nameReview);
              return (
                <tr key={row.rowNumber} aria-rowindex={start + index + 2}>
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
                      {(config.rowTitleFields ?? ['项目名称'])
                        .map((field) => cells[field])
                        .filter(Boolean)
                        .join(' · ') || '未填写标识'}
                    </strong>
                    <small>
                      {(config.rowLocationFields ?? ['省分', '地市'])
                        .map((field) => cells[field] || '—')
                        .join(' / ')}
                    </small>
                    <small>
                      {config.amountFields
                        .map((field) => cells[field])
                        .filter((value) => value?.trim())
                        .join(' / ') || '未填金额'}{' '}
                      {config.amountUnit ?? '万元'}
                    </small>
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
                          : config.noDuplicateMessage}
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
          </tbody>
        </table>
      </div>
      {!rows.length && <div className="empty-state">暂无符合筛选条件的数据</div>}
      <ListPagination
        label={`${config.title}导入预检`}
        page={safePage}
        totalPages={pages}
        totalElements={rows.length}
        onPageChange={changePage}
        disabled={disabled}
      />
    </>
  );
}
