import { useState } from 'react';
import { jsonBody, requestAt } from '../api/http';
import { Feedback } from './Feedback';
import { Modal } from './Modal';

type RecordVersion = { id: string; version: number };
type Register = 'REVENUE' | 'PRODUCTION' | 'GEWU' | 'WANGWU' | 'AGENTS_LIVE' | 'AGENTS_DEVELOPMENT';

export function useBatchDelete(
  admin: boolean,
  register: Register,
  query: string,
  rows: readonly RecordVersion[],
  reload: () => Promise<void> | void,
) {
  const [selection, setSelection] = useState<{ query: string; records: RecordVersion[] }>({
    query,
    records: [],
  });
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const selected = selection.query === query ? selection.records : [];
  const records = selected.filter(({ id }) => rows.some((row) => row.id === id));
  const select = (next: RecordVersion[]) => setSelection({ query, records: next });
  async function remove() {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const count = await requestAt<number>('/api/v1/admin/record-deletions', `/${register}`, {
        method: 'POST',
        ...jsonBody({ records: records.map(({ id, version }) => ({ id, version })) }),
      });
      select([]);
      setConfirm(false);
      setNotice(`已删除 ${count} 条记录`);
      await reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '批量删除失败，请重试');
    } finally {
      setBusy(false);
    }
  }

  const head = admin && (
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
    admin && (
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
      <button
        type="button"
        className="secondary-button"
        disabled={busy || !records.length}
        onClick={() => {
          setError('');
          setConfirm(true);
        }}
      >
        批量删除{records.length ? `（${records.length}）` : ''}
      </button>
      {notice && <Feedback tone="success">{notice}</Feedback>}
      {!confirm && error && <Feedback tone="error">{error}</Feedback>}
      {confirm && (
        <Modal title="确认批量删除" busy={busy} onClose={() => setConfirm(false)}>
          <p>确认从列表删除选中的 {records.length} 条记录？</p>
          {error && <Feedback tone="error">{error}</Feedback>}
          <div className="dialog-actions">
            <button
              type="button"
              className="secondary-button"
              disabled={busy}
              onClick={() => setConfirm(false)}
            >
              取消
            </button>
            <button
              type="button"
              className="primary-button"
              disabled={busy || !records.length}
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
