import { useEffect, useRef, useState } from 'react';
import { backupApi, type BackupTask } from './backupApi';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { dateTime } from '../../shared/ui/format';
import type { Page } from '../../shared/api/http';

const statuses: Record<BackupTask['status'], string> = {
  PENDING: '等待处理',
  RUNNING: '正在归档',
  SUCCEEDED: '已完成',
  FAILED: '失败',
};
const errors: Record<string, string> = {
  RESTORED: '该任务来自恢复快照，已中止；需要时请重新创建备份。',
  INTERRUPTED: '工作进程中断，请核查存储后重新发起。',
  ACTOR_REVOKED: '发起人的管理员权限已失效，任务未执行。',
  LIMIT_EXCEEDED: '超过归档容量或运行预算，请调整配置。',
  INTEGRITY_FAILED: '对象大小或摘要不符，请核查资料。',
  STORAGE_FAILED: '数据库或对象存储读取失败，请核查连接和对象完整性。',
  UNSUPPORTED_SCHEMA: '存在不支持的数据库结构，或备份期间发生结构变更。',
  BUSY: '备份引擎繁忙，请稍后重新发起。',
  INTERNAL_ERROR: '任务异常，请联系管理员核查日志。',
};

export function BackupsPage({ localDevelopment }: { localDevelopment: boolean }) {
  const [records, setRecords] = useState<Page<BackupTask> | null>(null);
  const [enabled, setEnabled] = useState(false);
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [downloading, setDownloading] = useState<string | null>(null);
  const requestId = useRef<string | null>(null);
  const submitting = useRef(false);
  const confirmation = useRef<HTMLHeadingElement>(null);
  const errorSummary = useRef<HTMLDivElement>(null);
  const createButton = useRef<HTMLButtonElement>(null);
  const previousConfirmation = useRef(false);
  const mounted = useRef(false);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  useEffect(() => {
    if (confirming) confirmation.current?.focus();
    else if (previousConfirmation.current) createButton.current?.focus();
    previousConfirmation.current = confirming;
  }, [confirming]);
  useEffect(() => {
    if (error) errorSummary.current?.focus();
  }, [error]);
  useEffect(() => {
    let active = true;
    let fetching = false;
    let timer: number | undefined;
    const controller = new AbortController();
    setLoading(true);
    async function load() {
      if (fetching || !active) return;
      fetching = true;
      if (timer) window.clearTimeout(timer);
      try {
        const result = await backupApi.list(page, controller.signal);
        if (active) {
          setRecords(result.records);
          setEnabled(result.enabled);
          setError('');
          if (
            result.records.items.some((item) => ['PENDING', 'RUNNING'].includes(item.status)) &&
            document.visibilityState === 'visible'
          )
            timer = window.setTimeout(() => void load(), 5000);
        }
      } catch (cause) {
        if (active) setError(cause instanceof Error ? cause.message : '备份列表加载失败');
      } finally {
        fetching = false;
        if (active) setLoading(false);
      }
    }
    const visible = () => {
      if (document.visibilityState === 'visible') void load();
    };
    void load();
    window.addEventListener('focus', visible);
    document.addEventListener('visibilitychange', visible);
    return () => {
      active = false;
      controller.abort();
      window.clearTimeout(timer);
      window.removeEventListener('focus', visible);
      document.removeEventListener('visibilitychange', visible);
    };
  }, [page, revision]);
  async function submit() {
    if (submitting.current || !requestId.current) return;
    submitting.current = true;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const task = await backupApi.create(requestId.current);
      if (!mounted.current) return;
      setNotice(`备份任务已受理：${task.backupCode}。请等待状态变为已完成。`);
      requestId.current = null;
      setConfirming(false);
      setPage(0);
      setRevision((value) => value + 1);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '提交失败，请刷新记录后重试原请求');
    } finally {
      submitting.current = false;
      setBusy(false);
    }
  }
  async function download(task: BackupTask) {
    setDownloading(task.id);
    setError('');
    setNotice('正在校验归档，请稍候。');
    try {
      if (localDevelopment && (task.archiveBytes ?? 0) > 50 * 1024 * 1024)
        throw new Error(
          '开发态大于 50 MiB 的归档请通过带开发身份的命令行下载；正式会话使用浏览器流式下载，不受此内存限制。',
        );
      await backupApi.check(task.id);
      if (!mounted.current) return;
      const link = document.createElement('a');
      let objectUrl: string | null = null;
      if (localDevelopment) {
        const blob = await backupApi.blob(task.id);
        if (!mounted.current) return;
        objectUrl = URL.createObjectURL(blob);
        link.href = objectUrl;
      } else link.href = backupApi.url(task.id);
      link.download = `ndi-backup-${task.id}.zip`;
      document.body.append(link);
      link.click();
      link.remove();
      if (objectUrl) window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60000);
      setNotice('归档已校验，下载请求已提交；请检查浏览器下载列表并妥善保管文件。');
    } catch (cause) {
      setNotice('');
      setError(cause instanceof Error ? cause.message : '下载失败，请核查备份文件');
    } finally {
      setDownloading(null);
    }
  }
  const activeTask = records?.items.some((item) => ['PENDING', 'RUNNING'].includes(item.status));
  return (
    <section className="backup-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">BACKUP ADMINISTRATION</span>
          <h1>备份管理</h1>
          <p>
            成对归档数据库与 MinIO 正式资料。归档包含敏感业务数据和密码摘要，须按生产库级别保管。
          </p>
        </div>
        <button
          ref={createButton}
          type="button"
          className="primary-button"
          disabled={!enabled || busy || loading || activeTask || confirming}
          onClick={() => {
            requestId.current = crypto.randomUUID();
            setConfirming(true);
            setNotice('');
            setError('');
          }}
        >
          创建备份
        </button>
      </div>
      {error && (
        <div ref={errorSummary} tabIndex={-1}>
          <Feedback tone="error">{error}</Feedback>
        </div>
      )}
      {notice && <Feedback tone={downloading ? 'muted' : 'success'}>{notice}</Feedback>}
      {!loading && !enabled && (
        <Feedback>
          服务端尚未启用备份。请配置受限持久化目录并设置 NDI_BACKUP_ENABLED=true；历史记录仍可查看。
        </Feedback>
      )}
      {confirming && (
        <section className="panel" aria-labelledby="backup-confirm-title">
          <h2 ref={confirmation} id="backup-confirm-title" tabIndex={-1}>
            确认创建完整备份
          </h2>
          <p>
            将读取完整数据库和已完成的正式对象。备份期间请勿发布数据库结构迁移；该操作不恢复或覆盖业务数据。
          </p>
          <p>网络异常后请先刷新记录，重试会沿用本次请求编号。取消页面确认不会撤销已受理的任务。</p>
          <div className="form-actions">
            <button
              type="button"
              className="secondary-button"
              disabled={busy}
              onClick={() => {
                setConfirming(false);
                requestId.current = null;
              }}
            >
              取消创建
            </button>
            <button
              type="button"
              className="primary-button"
              disabled={busy}
              onClick={() => void submit()}
            >
              {busy ? '正在提交…' : '确认创建'}
            </button>
          </div>
        </section>
      )}
      <div className="form-actions">
        <button
          type="button"
          className="secondary-button"
          disabled={loading}
          onClick={() => setRevision((value) => value + 1)}
        >
          刷新备份记录
        </button>
        <span className="subtle">待处理任务在页面可见时每 5 秒刷新；失败不会自动重建。</span>
      </div>
      {loading ? (
        <Loading />
      ) : (
        records && (
          <>
            <p role="status" aria-live="polite" aria-atomic="true" className="subtle">
              当前页 {records.items.length} 条备份：
              {records.items.filter((item) => item.status === 'PENDING').length} 条等待处理，
              {records.items.filter((item) => item.status === 'RUNNING').length} 条正在归档，
              {records.items.filter((item) => item.status === 'SUCCEEDED').length} 条已完成，
              {records.items.filter((item) => item.status === 'FAILED').length} 条失败。
            </p>
            <ol className="backup-records" aria-label="备份记录">
              {records.items.map((item) => (
                <li className="panel" key={item.id}>
                  <h2>{item.backupCode}</h2>
                  <p>
                    状态：<strong>{statuses[item.status]}</strong>
                  </p>
                  <dl className="backup-meta">
                    <div>
                      <dt>申请时间</dt>
                      <dd>{dateTime(item.requestedAt)}</dd>
                    </div>
                    <div>
                      <dt>快照时间</dt>
                      <dd>{item.snapshotAt ? dateTime(item.snapshotAt) : '—'}</dd>
                    </div>
                    <div>
                      <dt>完成时间</dt>
                      <dd>{item.finishedAt ? dateTime(item.finishedAt) : '—'}</dd>
                    </div>
                    <div>
                      <dt>归档大小</dt>
                      <dd>
                        {item.archiveBytes === null
                          ? '—'
                          : `${(item.archiveBytes / 1024 / 1024).toFixed(2)} MiB`}
                      </dd>
                    </div>
                    <div>
                      <dt>数据表／正式对象</dt>
                      <dd>
                        {item.tableCount ?? '—'}／{item.objectCount ?? '—'}
                      </dd>
                    </div>
                  </dl>
                  {item.sha256 && (
                    <p className="backup-checksum">
                      SHA-256：<code>{item.sha256}</code>
                    </p>
                  )}
                  {item.status === 'FAILED' && (
                    <p>
                      失败原因：{errors[item.errorCode ?? ''] ?? '未知异常，请联系管理员核查。'}
                    </p>
                  )}
                  {item.status === 'SUCCEEDED' && (
                    <button
                      type="button"
                      className="secondary-button"
                      disabled={downloading !== null}
                      onClick={() => void download(item)}
                    >
                      {downloading === item.id ? '正在校验下载…' : '下载归档'}
                    </button>
                  )}
                </li>
              ))}
            </ol>
            {!records.items.length && <p className="empty-state">暂无备份记录</p>}
            <div className="pager admin-pager">
              <span>
                共 {records.totalElements} 条 · 第 {page + 1} 页
              </span>
              <button
                type="button"
                className="secondary-button"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                上一页备份
              </button>
              <button
                type="button"
                className="secondary-button"
                disabled={page + 1 >= records.totalPages}
                onClick={() => setPage(page + 1)}
              >
                下一页备份
              </button>
            </div>
          </>
        )
      )}
    </section>
  );
}
