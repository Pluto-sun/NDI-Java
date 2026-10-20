import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { projectsApi } from './api';
import type { ImportBatch } from './types';
import { Feedback } from '../../shared/ui/Feedback';

function rowErrors(value: string): string[] {
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map(String) : ['错误信息无法解析'];
  } catch {
    return ['错误信息无法解析'];
  }
}

export function ImportProjectsPage() {
  const [file, setFile] = useState<File | null>(null);
  const [batch, setBatch] = useState<ImportBatch | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function preview(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file) return;
    setBusy(true);
    setError('');
    setBatch(null);
    try {
      setBatch(await projectsApi.previewImport(file));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '预检失败');
    } finally {
      setBusy(false);
    }
  }

  async function confirm() {
    if (!batch || !window.confirm(`确认导入 ${batch.validRows} 条通过预检的项目？失败行不会写入。`))
      return;
    setBusy(true);
    setError('');
    try {
      setBatch(await projectsApi.confirmImport(batch.batchId));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '导入失败');
    } finally {
      setBusy(false);
    }
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
        <h2>选择导入文件</h2>
        <p className="subtle">使用既定项目导入模板，文件不超过 10 MB。预检不会写入项目。</p>
        <form className="filter-bar" onSubmit={preview}>
          <label>
            Excel 文件
            <input
              type="file"
              accept=".xlsx"
              required
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
          </label>
          <button className="primary-button" disabled={busy || !file} type="submit">
            {busy ? '处理中…' : '开始预检'}
          </button>
        </form>
      </div>
      {error && <Feedback tone="error">{error}</Feedback>}
      {batch && (
        <div className="panel table-panel">
          <div className="panel-top">
            <strong>预检结果</strong>
            <span>
              总计 {batch.totalRows} 行 · 可导入 {batch.validRows} 行 · 失败 {batch.failedRows} 行
            </span>
          </div>
          <p className="subtle">
            批次 {batch.batchId} · 状态 {batch.status}
            {batch.status !== 'PREVIEWED' ? ` · 已导入 ${batch.importedRows} 行` : ''}
          </p>
          {batch.status === 'PREVIEWED' && (
            <button
              type="button"
              className="primary-button"
              disabled={busy || batch.validRows === 0}
              onClick={confirm}
            >
              确认导入可用行
            </button>
          )}
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Excel 行号</th>
                  <th>状态</th>
                  <th>校验结果</th>
                  <th>项目</th>
                </tr>
              </thead>
              <tbody>
                {batch.rows.map((row) => (
                  <tr key={row.rowNumber}>
                    <td>{row.rowNumber}</td>
                    <td>{row.status}</td>
                    <td>{rowErrors(row.errorsJson).join('；') || '通过'}</td>
                    <td>
                      {row.projectId && (
                        <Link className="inline-link" to={`/projects/${row.projectId}`}>
                          查看项目
                        </Link>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}
