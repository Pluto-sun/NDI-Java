import {
  flexRender,
  functionalUpdate,
  getCoreRowModel,
  useReactTable,
  type ColumnSizingState,
  type ColumnDef,
} from '@tanstack/react-table';
import {
  useCallback,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
  type RefObject,
} from 'react';
import { createPortal } from 'react-dom';
import { useLiveQuery } from '../api/useLiveQuery';
import { Feedback } from '../ui/Feedback';
import { Modal } from '../ui/Modal';
import { listColumnsApi, type Field, type ListId } from './api';
import { ColumnChoices, moveColumn } from './ColumnChoices';
import {
  defaultPreferences,
  maxWidth,
  minWidth,
  orderedIds,
  readPreferences,
  type Preferences,
} from './preferences';

export type BusinessColumn<Row> = Field & {
  width: number;
  cell: (row: Row) => ReactNode;
  heading?: ReactNode;
  className?: string;
  cellClassName?: string;
  title?: (row: Row) => string | undefined;
};

type TableProps<Row> = {
  list: ListId;
  accountId: string;
  label: string;
  columns: BusinessColumn<Row>[];
  rows: Row[];
  className: string;
  scrollClassName?: string;
  scrollLabel: string;
  scrollRef?: RefObject<HTMLDivElement | null>;
  selection?: { head: ReactNode; cell: (row: Row) => ReactNode };
  actions: (row: Row) => ReactNode;
  actionWidth?: number;
};

export function PersonalizedTable<Row extends { id: string }>(props: TableProps<Row>) {
  const storageKey = `ndi.list-columns:${props.accountId}:${props.list}`;
  return <ColumnTable key={storageKey} {...props} storageKey={storageKey} />;
}

function ColumnTable<Row extends { id: string }>({
  storageKey,
  list,
  label,
  columns,
  rows,
  className,
  scrollClassName,
  scrollLabel,
  scrollRef,
  selection,
  actions,
  actionWidth = 76,
}: TableProps<Row> & { storageKey: string }) {
  const [preferences, setPreferences] = useState(() => readPreferences(storageKey));
  const [draft, setDraft] = useState<Preferences | null>(null);
  const [storageError, setStorageError] = useState('');
  const load = useCallback((signal: AbortSignal) => listColumnsApi.read(list, signal), [list]);
  const { data, error, refresh } = useLiveQuery(load, '无法加载固定列配置');
  const ownScroll = useRef<HTMLDivElement>(null);
  const container = scrollRef ?? ownScroll;
  const [viewport, setViewport] = useState(0);
  const [guideAnchor, setGuideAnchor] = useState<{
    x: number;
    top: number;
    height: number;
    left: number;
    right: number;
  } | null>(null);
  function anchorGuide(handle: HTMLElement) {
    const cell = handle.closest('th')?.getBoundingClientRect();
    const frame = container.current?.getBoundingClientRect();
    if (!cell || !frame) return;
    const top = Math.max(0, cell.top);
    setGuideAnchor({
      x: cell.right,
      top,
      height: Math.max(0, Math.min(frame.bottom, window.innerHeight) - top),
      left: frame.left,
      right: frame.right,
    });
  }
  useLayoutEffect(() => {
    const element = container.current;
    if (!element) return;
    const measure = () => setViewport(element.clientWidth);
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    measure();
    return () => observer.disconnect();
  }, [container]);

  function save(next: Preferences) {
    setPreferences(next);
    try {
      localStorage.setItem(storageKey, JSON.stringify(next));
      setStorageError('');
    } catch {
      setStorageError('浏览器无法保存列设置。本次设置在离开页面后可能丢失。');
    }
  }

  const ids = columns.map(({ id }) => id);
  const fixed = (data?.fixedFields ?? []).filter((id) => ids.includes(id));
  const movable = orderedIds(ids, preferences).filter((id) => !fixed.includes(id));
  const shown = fixed.length || movable.some((id) => !preferences.hidden.includes(id));
  const leftWidth = selection ? 44 : 0;
  // 固定区最多使用可用业务宽度的 55%，为滚动列和操作留出空间。
  const available = Math.max(0, (viewport || 960) - leftWidth - actionWidth);
  const pinCap = Math.max(
    40,
    Math.floor(Math.min(640, available * 0.55) / Math.max(1, fixed.length)),
  );
  const widths: ColumnSizingState = Object.fromEntries(
    columns.map(({ id, width }) => {
      const maximum = fixed.includes(id) ? Math.min(maxWidth, pinCap) : maxWidth;
      return [id, Math.min(maximum, preferences.widths[id] ?? width)];
    }),
  );
  const lookup = new Map(columns.map((column) => [column.id, column]));
  const table = useReactTable<Row>({
    data: rows,
    columns: columns.map<ColumnDef<Row>>((column) => ({
      id: column.id,
      cell: ({ row }) => column.cell(row.original),
      size: column.width,
      minSize: fixed.includes(column.id) ? Math.min(minWidth, pinCap) : minWidth,
      maxSize: fixed.includes(column.id) ? Math.min(maxWidth, pinCap) : maxWidth,
    })),
    getRowId: (row) => row.id,
    getCoreRowModel: getCoreRowModel(),
    columnResizeMode: 'onEnd',
    state: {
      columnOrder: [...fixed, ...movable],
      columnVisibility: Object.fromEntries(
        movable.map((id, index) => [
          id,
          !preferences.hidden.includes(id) || (!shown && index === 0),
        ]),
      ),
      columnSizing: widths,
    },
    onColumnSizingChange: (update) => {
      const resized = functionalUpdate(update, widths);
      const changed = Object.entries(resized).flatMap(([id, requested]) => {
        const definition = table.getColumn(id)?.columnDef;
        if (!definition) return [];
        const width = Math.max(
          definition.minSize ?? minWidth,
          Math.min(definition.maxSize ?? maxWidth, requested),
        );
        return widths[id] !== width ? [[id, width] as const] : [];
      });
      if (changed.length)
        save({ ...preferences, widths: { ...preferences.widths, ...Object.fromEntries(changed) } });
    },
  });
  const visible = table.getVisibleLeafColumns();
  let left = leftWidth;
  const offsets = new Map<string, number>();
  for (const column of visible) {
    if (!fixed.includes(column.id)) break;
    offsets.set(column.id, left);
    left += column.getSize();
  }
  const width =
    leftWidth + visible.reduce((sum, column) => sum + column.getSize(), 0) + actionWidth;
  const resizeInfo = table.getState().columnSizingInfo;
  const resizing = resizeInfo.isResizingColumn
    ? table.getColumn(resizeInfo.isResizingColumn)
    : null;
  const guideDelta = resizing
    ? Math.max(
        resizing.columnDef.minSize ?? minWidth,
        Math.min(
          resizing.columnDef.maxSize ?? maxWidth,
          resizing.getSize() + (resizeInfo.deltaOffset ?? 0),
        ),
      ) - resizing.getSize()
    : 0;
  const draftOrder = draft ? orderedIds(ids, draft) : [];
  const draftMovable = draftOrder.filter((id) => !fixed.includes(id));
  const canApply = fixed.length > 0 || draftMovable.some((id) => !draft?.hidden.includes(id));
  const fields = (keys: string[]) => keys.flatMap((key) => lookup.get(key) ?? []);

  return (
    <>
      <div className="column-table-tools">
        {(error || storageError) && <Feedback tone="error">{error || storageError}</Feedback>}
        {error && (
          <button
            type="button"
            className="secondary-button compact-button"
            onClick={() => void refresh()}
          >
            重试
          </button>
        )}
        <button
          type="button"
          className="secondary-button compact-button"
          disabled={!data}
          onClick={() => {
            setDraft({
              ...preferences,
              hidden: shown
                ? preferences.hidden
                : preferences.hidden.filter((id) => id !== movable[0]),
            });
          }}
        >
          列设置
        </button>
      </div>
      <div
        ref={container}
        className={`table-scroll sticky-actions-scroll ${scrollClassName ?? ''}`}
        role="region"
        tabIndex={0}
        aria-label={scrollLabel}
      >
        <table
          className={`${className} personalized-table${selection ? ' personalized-selection' : ''}`}
          aria-label={label}
          style={{ width, minWidth: width, visibility: data ? undefined : 'hidden' }}
        >
          <colgroup>
            {selection && <col style={{ width: leftWidth }} />}
            {visible.map((column) => (
              <col key={column.id} style={{ width: column.getSize() }} />
            ))}
            <col style={{ width: actionWidth }} />
          </colgroup>
          <thead>
            <tr>
              {selection?.head}
              {table
                .getFlatHeaders()
                .filter((header) => header.column.getIsVisible())
                .map((header) => {
                  const id = header.column.id;
                  const column = lookup.get(id)!;
                  const size = header.column.getSize();
                  const low = header.column.columnDef.minSize!;
                  const high = header.column.columnDef.maxSize!;
                  return (
                    <th
                      scope="col"
                      key={id}
                      data-column-id={id}
                      aria-label={column.label}
                      className={[column.className, offsets.has(id) ? 'sticky-business-column' : '']
                        .filter(Boolean)
                        .join(' ')}
                      style={{ insetInlineStart: offsets.get(id) }}
                    >
                      <span className="column-heading" title={column.label}>
                        {column.heading ?? column.label}
                      </span>
                      <button
                        type="button"
                        className="column-resize"
                        aria-label={`调整${column.label}列宽`}
                        onMouseDown={(event) => {
                          if (event.button !== 0) return;
                          event.preventDefault();
                          anchorGuide(event.currentTarget);
                          header.getResizeHandler()(event);
                        }}
                        onTouchStart={(event) => {
                          anchorGuide(event.currentTarget);
                          header.getResizeHandler()(event);
                        }}
                        onKeyDown={(event) => {
                          if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
                          event.preventDefault();
                          const next = Math.max(
                            low,
                            Math.min(high, size + (event.key === 'ArrowLeft' ? -10 : 10)),
                          );
                          if (size !== next)
                            save({ ...preferences, widths: { ...preferences.widths, [id]: next } });
                        }}
                      />
                    </th>
                  );
                })}
              <th scope="col" className="sticky-list-action">
                操作
              </th>
            </tr>
          </thead>
          <tbody>
            {table.getRowModel().rows.map((row) => (
              <tr key={row.id}>
                {selection?.cell(row.original)}
                {row.getVisibleCells().map((cell) => {
                  const id = cell.column.id;
                  const column = lookup.get(id)!;
                  return (
                    <td
                      key={id}
                      data-column-id={id}
                      title={column.title?.(row.original)}
                      className={[
                        column.className,
                        column.cellClassName,
                        offsets.has(id) ? 'sticky-business-column' : '',
                      ]
                        .filter(Boolean)
                        .join(' ')}
                      style={{ insetInlineStart: offsets.get(id) }}
                    >
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  );
                })}
                <td className="sticky-list-action">{actions(row.original)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {resizing &&
        guideAnchor &&
        createPortal(
          <span
            className="column-resize-guide"
            aria-hidden="true"
            style={{
              left: Math.max(
                guideAnchor.left,
                Math.min(guideAnchor.right, guideAnchor.x + guideDelta),
              ),
              top: guideAnchor.top,
              height: guideAnchor.height,
            }}
          />,
          document.body,
        )}
      {draft && (
        <Modal title="列设置" onClose={() => setDraft(null)}>
          <p className="column-settings-help">选择要显示的列。拖动或使用上移、下移调整顺序。</p>
          {fixed.length > 0 && (
            <section aria-label="管理员固定列">
              <h3 className="column-section-title">固定列</h3>
              <ColumnChoices fields={fields(fixed)} selected={() => true} />
            </section>
          )}
          {draftMovable.length > 0 && (
            <section aria-label="个人可调整的列">
              <h3 className="column-section-title">个人列</h3>
              <ColumnChoices
                fields={fields(draftMovable)}
                selected={(id) => !draft.hidden.includes(id)}
                toggle={(id) =>
                  setDraft({
                    ...draft,
                    hidden: draft.hidden.includes(id)
                      ? draft.hidden.filter((field) => field !== id)
                      : [...draft.hidden, id],
                  })
                }
                move={(from, to) => {
                  const next = moveColumn(draftMovable, from, to);
                  let index = 0;
                  setDraft({
                    ...draft,
                    order: draftOrder.map((id) => (fixed.includes(id) ? id : next[index++])),
                  });
                }}
              />
            </section>
          )}
          {!canApply && <Feedback tone="error">至少显示一个业务列。</Feedback>}
          <div className="column-settings-actions">
            <button
              type="button"
              className="text-button"
              onClick={() => setDraft(defaultPreferences)}
            >
              恢复默认
            </button>
            <button type="button" className="secondary-button" onClick={() => setDraft(null)}>
              取消
            </button>
            <button
              type="button"
              className="primary-button"
              disabled={!canApply}
              onClick={() => {
                save(draft);
                setDraft(null);
              }}
            >
              应用
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
