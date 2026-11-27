import { useEffect, useState } from 'react';
import { ColumnChoices, moveColumn } from '../../shared/list-columns/ColumnChoices';
import { listColumnsApi, type ListId } from '../../shared/list-columns/api';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { Select } from '../../shared/ui/Select';

type Catalog = Awaited<ReturnType<typeof listColumnsApi.manage>>;

export default function ListColumnAdministrationPage() {
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [listId, setListId] = useState<ListId>('REVENUE');
  const [draft, setDraft] = useState<string[]>([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    listColumnsApi
      .manage()
      .then((result) => {
        if (!active) return;
        setCatalog(result);
        setDraft(result.lists.find(({ id }) => id === 'REVENUE')?.fixedFields ?? []);
      })
      .catch((cause) => {
        if (active) setError((cause as Error).message);
      });
    return () => {
      active = false;
    };
  }, []);

  if (!catalog) return error ? <Feedback tone="error">{error}</Feedback> : <Loading />;
  const list = catalog.lists.find(({ id }) => id === listId);
  if (!list) return null;
  const fixed = draft.flatMap((id) => list.fields.find((field) => field.id === id) ?? []);
  const remaining = list.fields.filter(({ id }) => !draft.includes(id));
  const changed = draft.join('|') !== list.fixedFields.join('|');
  async function submit() {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const result = await listColumnsApi.save(listId, draft);
      setCatalog(
        (current) =>
          current && {
            ...current,
            lists: current.lists.map((item) =>
              item.id === listId ? { ...item, ...result } : item,
            ),
          },
      );
      setDraft(result.fixedFields);
      setNotice('固定列配置已保存。');
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel list-column-admin" aria-label="列表列配置">
      <label className="column-list-select">
        选择列表
        <Select
          value={listId}
          disabled={busy}
          onChange={(event) => {
            const id = event.target.value as ListId;
            setListId(id);
            setDraft(catalog.lists.find((item) => item.id === id)?.fixedFields ?? []);
            setNotice('');
            setError('');
          }}
        >
          {catalog.lists.map(({ id, title }) => (
            <option key={id} value={id}>
              {title}
            </option>
          ))}
        </Select>
      </label>
      <p className="column-settings-help">
        每个列表最多固定 {catalog.maxFixed} 列。用户打开列表时，固定列按下方顺序显示在左侧。
      </p>
      <div className="column-admin-columns">
        <section aria-label="可固定的业务列">
          <h2 className="column-section-title">可固定的业务列</h2>
          {remaining.length > 0 && (
            <ColumnChoices
              fields={remaining}
              selected={() => false}
              toggle={(id) => {
                setNotice('');
                if (busy) return;
                if (draft.length >= catalog.maxFixed) {
                  setError(`最多固定 ${catalog.maxFixed} 列，请先取消一列。`);
                  return;
                }
                setError('');
                setDraft([...draft, id]);
              }}
            />
          )}
        </section>
        <section aria-label="固定列顺序">
          <h2 className="column-section-title">固定列顺序（{draft.length}）</h2>
          {fixed.length ? (
            <ColumnChoices
              fields={fixed}
              selected={() => true}
              toggle={(id) => {
                if (busy) return;
                setNotice('');
                setError('');
                setDraft(draft.filter((item) => item !== id));
              }}
              move={(from, to) => {
                if (!busy) setDraft(moveColumn(draft, from, to));
              }}
            />
          ) : (
            <p className="subtle">尚未固定业务列。</p>
          )}
        </section>
      </div>
      {error && <Feedback tone="error">{error}</Feedback>}
      {notice && <Feedback tone="success">{notice}</Feedback>}
      <div className="column-settings-actions">
        <button
          type="button"
          className="secondary-button"
          disabled={busy || !draft.length}
          onClick={() => {
            setDraft([]);
            setError('');
            setNotice('');
          }}
        >
          清空固定列
        </button>
        <button
          type="button"
          className="primary-button"
          disabled={!changed || busy}
          onClick={() => void submit()}
        >
          {busy ? '正在保存…' : '保存'}
        </button>
      </div>
    </section>
  );
}
