import { useBlockingOperation } from './useBlockingOperation';
import { useState } from 'react';
import { jsonBody, requestAt, type Page } from '../api/http';
import { Feedback } from './Feedback';
import { Modal } from './Modal';

type RecordVersion = { id: string; version: number };
type Register =
  'PROJECTS' | 'REVENUE' | 'PRODUCTION' | 'GEWU' | 'WANGWU' | 'AGENTS_LIVE' | 'AGENTS_DEVELOPMENT';

type FilteredList = {
  count: number;
  filters: readonly string[];
  load: (query: URLSearchParams) => Promise<Page<RecordVersion>>;
};

async function readFilteredRecords(query: string, list: FilteredList): Promise<RecordVersion[]> {
  const params = new URLSearchParams(query);
  params.set('size', '100');
  const records = new Map<string, RecordVersion>();
  for (let page = 0; ; page++) {
    params.set('page', String(page));
    const result = await list.load(params);
    if (result.totalElements !== list.count || result.page !== page || !result.items.length)
      throw new Error('筛选结果已发生变化，请刷新列表后重试。');
    for (const { id, version } of result.items) records.set(id, { id, version });
    if (page + 1 >= result.totalPages) break;
  }
  if (records.size !== list.count) throw new Error('筛选结果已发生变化，请刷新列表后重试。');
  return [...records.values()];
}

export function useBatchDelete(
  admin: boolean,
  register: Register,
  query: string,
  rows: readonly RecordVersion[],
  reload: () => Promise<void> | void,
  filtered?: FilteredList,
) {
  const blocking = useBlockingOperation();
  const selectable = admin && register !== 'PROJECTS';
  const [selection, setSelection] = useState<{ query: string; records: RecordVersion[] }>({
    query,
    records: [],
  });
  const [confirm, setConfirm] = useState<'selected' | 'filtered' | null>(null);
  const [snapshot, setSnapshot] = useState<{ query: string; records: RecordVersion[] } | null>(
    null,
  );
  const filterParams = new URLSearchParams(query);
  const hasFilters = filtered?.filters.some((field) =>
    filterParams.getAll(field).some((value) => value.trim()),
  );
  const filteredRecords = snapshot?.query === query ? snapshot.records : [];
  async function previewFiltered() {
    if (!filtered || !hasFilters || !filtered.count) return;
    setBusy(true);
    setError('');
    try {
      setSnapshot({ query, records: await readFilteredRecords(query, filtered) });
      setConfirm('filtered');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '无法读取筛选结果');
    } finally {
      setBusy(false);
    }
  }
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const selected = selection.query === query ? selection.records : [];
  const records = selected.filter(({ id }) => rows.some((row) => row.id === id));
  const select = (next: RecordVersion[]) => setSelection({ query, records: next });
  const toRemove = confirm === 'filtered' ? filteredRecords : records;
  async function remove() {
    if (!toRemove.length) return;
    let removed = false;
    await blocking.run('正在批量删除记录…', async (signal) => {
      setBusy(true);
      setError('');
      setNotice('');
      try {
        const result = await requestAt<number | { deleted: number; skippedPending: number }>(
          '/api/v1/admin/record-deletions',
          `/${register}${confirm === 'filtered' ? '/filtered' : ''}`,
          {
            method: 'POST',
            signal,
            ...jsonBody({ records: toRemove }),
          },
        );
        select([]);
        setConfirm(null);
        const { deleted, skippedPending } =
          typeof result === 'number' ? { deleted: result, skippedPending: 0 } : result;
        setNotice(
          `已删除 ${deleted} 条记录${skippedPending ? `，保留 ${skippedPending} 条待审批记录` : ''}`,
        );
        removed = true;
      } catch (cause) {
        if (signal.reason?.name !== 'AbortError')
          setError(cause instanceof Error ? cause.message : '批量删除失败，请重试');
      } finally {
        if (signal.reason?.name !== 'AbortError') setBusy(false);
      }
    });
    if (removed) {
      try {
        await reload();
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : '删除完成，列表刷新失败');
      }
    }
  }

  const head = selectable && (
    <th scope="col" className="batch-select-cell">
      <input
        type="checkbox"
        aria-label="选择本页全部记录"
        disabled={busy || !rows.length}
        checked={!!rows.length && records.length === rows.length}
        onChange={(event) =>
          select(event.target.checked ? rows.map(({ id, version }) => ({ id, version })) : [])
        }
      />
    </th>
  );
  const cell = (row: RecordVersion) =>
    selectable && (
      <td className="batch-select-cell">
        <input
          type="checkbox"
          aria-label={`选择记录 ${rows.findIndex(({ id }) => id === row.id) + 1}`}
          disabled={busy}
          checked={records.some(({ id }) => id === row.id)}
          onChange={(event) =>
            select(
              event.target.checked
                ? [...records, { id: row.id, version: row.version }]
                : records.filter(({ id }) => id !== row.id),
            )
          }
        />
      </td>
    );
  const toolbar = admin && (
    <div className="batch-delete-toolbar">
      {blocking.overlay}
      {selectable && (
        <button
          type="button"
          className="secondary-button"
          disabled={busy || !records.length}
          onClick={() => {
            setError('');
            setConfirm('selected');
          }}
        >
          批量删除{records.length ? `（${records.length}）` : ''}
        </button>
      )}
      {filtered && (
        <button
          type="button"
          className="secondary-button"
          disabled={busy || !hasFilters || !filtered.count}
          title={hasFilters ? undefined : '请先应用筛选条件'}
          onClick={() => void previewFiltered()}
        >
          删除筛选结果
        </button>
      )}
      {notice && <Feedback tone="success">{notice}</Feedback>}
      {!confirm && error && <Feedback tone="error">{error}</Feedback>}
      {confirm && (
        <Modal
          title={confirm === 'filtered' ? '确认删除筛选结果' : '确认批量删除'}
          busy={busy}
          onClose={() => setConfirm(null)}
        >
          <p>
            {confirm === 'filtered'
              ? `当前筛选共匹配 ${toRemove.length} 条记录，范围包含其他页。`
              : `确认从列表删除选中的 ${toRemove.length} 条记录？`}
          </p>
          {confirm === 'filtered' && (
            <p>
              {register === 'PROJECTS' || register === 'REVENUE'
                ? '待审批条目将保留，其余条目将删除。'
                : '确认删除以上范围内的全部记录？'}
            </p>
          )}
          {error && <Feedback tone="error">{error}</Feedback>}
          <div className="dialog-actions">
            <button
              type="button"
              className="secondary-button"
              disabled={busy}
              onClick={() => setConfirm(null)}
            >
              取消
            </button>
            <button
              type="button"
              className="primary-button"
              disabled={busy || !toRemove.length}
              onClick={() => void remove()}
            >
              确认删除
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
  return { head, cell, toolbar };
}
