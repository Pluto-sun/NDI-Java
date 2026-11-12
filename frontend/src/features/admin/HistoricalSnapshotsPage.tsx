import { useState } from 'react';
import { Link } from 'react-router-dom';
import { requestAt } from '../../shared/api/http';
import { ImportWizardPage } from '../../shared/imports/ImportWizardPage';
import { tabularImportApi } from '../../shared/imports/tabularImportApi';
import { Feedback } from '../../shared/ui/Feedback';
import { Modal } from '../../shared/ui/Modal';

const root = '/api/v1/admin/historical-snapshots';
const api = tabularImportApi(root);
const route = '/admin/historical-snapshots';

export function HistoricalSnapshotWizard() {
  return (
    <ImportWizardPage
      config={{
        api,
        title: '历史快照',
        backTo: route,
        amountFields: ['月末项目总金额'],
        rowTitleFields: ['系统项目编号', '快照月份', '月末阶段'],
        rowLocationFields: ['月末归属省分', '地市'],
        uploadHint: '仅导入已结束月份。四项产品金额均须填写，未使用的产品填 0。',
        noDuplicateMessage: '已通过快照校验',
        previewHint:
          '相同快照会跳过写入。覆盖冲突须在修正行时填入预检提示的修订号。导入后可另行确认整月完整。',
      }}
    />
  );
}

export function HistoricalSnapshotsPage() {
  const [month, setMonth] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [revisions, setRevisions] = useState<Record<string, string | number | null>[]>();
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>历史快照导入</h1>
          <p>补录已结束月份的项目月末状态。</p>
        </div>
        <Link className="primary-button" to={route + '/import'}>
          Excel 导入
        </Link>
      </div>
      <form
        className="filter-bar"
        onSubmit={(event) => {
          event.preventDefault();
          setConfirming(true);
          setError('');
          setMessage('');
        }}
      >
        <label>
          快照月份
          <input
            type="month"
            required
            value={month}
            onChange={(event) => setMonth(event.target.value)}
          />
        </label>
        <button className="secondary-button" disabled={busy}>
          确认整月完整
        </button>
      </form>
      <p className="subtle">
        补录默认为部分数据。确认整月完整后，该月份才会作为整月总量进入驾驶舱趋势。
      </p>
      {message && <Feedback tone="success">{message}</Feedback>}
      {error && !confirming && <Feedback tone="error">{error}</Feedback>}
      <section className="panel">
        <h2>旧规则修订排查</h2>
        <p>列出审批通过月份之前曾被回改的快照，以及修订记录中保存的恢复依据。</p>
        <button
          className="secondary-button"
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            setError('');
            try {
              setRevisions(await requestAt(root, '/old-revisions'));
            } catch (cause) {
              setError(cause instanceof Error ? cause.message : '排查失败');
            } finally {
              setBusy(false);
            }
          }}
        >
          查询受影响快照
        </button>
        {revisions &&
          (revisions.length ? (
            <ul>
              {revisions.map((row) => (
                <li key={`${row.project_code}/${row.month_start}/${row.revision_no}`}>
                  <details>
                    <summary>
                      {row.project_code} · {row.month_start} · 修订 {row.revision_no}
                    </summary>
                    <pre className="table-scroll">{JSON.stringify(row, null, 2)}</pre>
                  </details>
                </li>
              ))}
            </ul>
          ) : (
            <p>没有找到符合旧规则的历史修订。</p>
          ))}
      </section>
      {confirming && (
        <Modal title="确认整月快照完整" busy={busy} onClose={() => setConfirming(false)}>
          <p>
            请确认 {month} 的全部项目快照已经核对完成。确认后，该月没有符合条件的项目时会显示 0。
          </p>
          {error && <Feedback tone="error">{error}</Feedback>}
          <div className="dialog-actions">
            <button
              className="secondary-button"
              disabled={busy}
              onClick={() => setConfirming(false)}
            >
              取消
            </button>
            <button
              className="primary-button"
              disabled={busy}
              onClick={async () => {
                setBusy(true);
                setError('');
                try {
                  await requestAt(root, '/complete?' + new URLSearchParams({ month }), {
                    method: 'POST',
                  });
                  setConfirming(false);
                  setMessage(`${month} 已标记为整月完整。`);
                } catch (cause) {
                  setError(cause instanceof Error ? cause.message : '标记失败');
                } finally {
                  setBusy(false);
                }
              }}
            >
              确认完整
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
