import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { projectsApi } from '../projects/api';
import { inactivityApi, type InactivityTriggerResult } from '../projects/inactivityApi';
import { ProjectInactivityAuditPanel } from '../projects/ProjectInactivityAuditPanel';
import type { ProjectSummary } from '../projects/types';
import type { Page } from '../../shared/api/http';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { refreshNotificationCounts } from '../approvals/notificationEvents';

const levels = [
  { days: 30, label: '30 天：通知省分负责人' },
  { days: 33, label: '33 天：通知接口人' },
  { days: 36, label: '36 天：通知集团' },
];

export function InactivityAdministrationPage() {
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [query, setQuery] = useState({ projectName: '', projectCode: '', page: 0 });
  const [projects, setProjects] = useState<Page<ProjectSummary> | null>(null);
  const [selected, setSelected] = useState<ProjectSummary | null>(null);
  const [days, setDays] = useState(30);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<InactivityTriggerResult | null>(null);
  const [revision, setRevision] = useState(0);
  const summary = useRef<HTMLDivElement>(null);
  const confirmation = useRef<HTMLHeadingElement>(null);
  const submitButton = useRef<HTMLButtonElement>(null);
  const inFlight = useRef(false);
  const wasConfirming = useRef(false);
  useEffect(() => {
    if (error) summary.current?.focus();
  }, [error]);
  useEffect(() => {
    if (confirming) confirmation.current?.focus();
    else if (wasConfirming.current) submitButton.current?.focus();
    // 确认面板关闭后恢复发起按钮焦点，不因项目选择或轮询抢走焦点。
    wasConfirming.current = confirming;
  }, [confirming]);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setProjects(null);
    setError('');
    projectsApi
      .list({ ...query, includeTerminated: false })
      .then((data) => {
        if (active) setProjects(data);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '项目查询失败，请重新查询');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [query]);
  function clearSelection() {
    setSelected(null);
    setResult(null);
    setConfirming(false);
  }
  async function trigger() {
    if (!selected || inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError('');
    setResult(null);
    try {
      setResult(await inactivityApi.trigger(selected.id, days));
      setConfirming(false);
      setRevision((value) => value + 1);
      refreshNotificationCounts();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '督办触发失败，请核对项目状态后重试');
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }
  return (
    <section className="inactivity-admin">
      <div className="page-heading">
        <div>
          <span className="eyebrow">REMINDER ADMINISTRATION</span>
          <h1>督办管理</h1>
          <p>管理员手动演练超时提醒。该操作会发送真实站内通知，但不修改项目实际最近变动时间。</p>
        </div>
      </div>
      {error && (
        <div ref={summary} tabIndex={-1}>
          <Feedback tone="error">{error}</Feedback>
        </div>
      )}
      <form
        className="filter-bar"
        aria-label="查询督办项目"
        onSubmit={(event) => {
          event.preventDefault();
          clearSelection();
          setQuery({ projectName: name, projectCode: code, page: 0 });
        }}
      >
        <label>
          项目名称
          <input
            value={name}
            maxLength={200}
            disabled={busy}
            onChange={(event) => setName(event.target.value)}
          />
        </label>
        <label>
          项目编号
          <input
            value={code}
            maxLength={80}
            disabled={busy}
            onChange={(event) => setCode(event.target.value)}
          />
        </label>
        <button className="secondary-button" disabled={busy || loading}>
          查询督办项目
        </button>
      </form>
      {loading ? (
        <Loading />
      ) : (
        <section className="panel">
          <h2>选择演练对象</h2>
          <label>
            测试项目
            <select
              disabled={busy}
              value={selected?.id ?? ''}
              onChange={(event) => {
                setSelected(projects?.items.find((item) => item.id === event.target.value) ?? null);
                setResult(null);
                setConfirming(false);
                setError('');
              }}
            >
              <option value="">请选择本页项目</option>
              {projects?.items.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.projectCode} · {item.projectName}
                </option>
              ))}
            </select>
          </label>
          {!projects?.items.length && (
            <p className="empty-state">暂无符合条件的未终止项目，请调整查询条件。</p>
          )}
          {projects && (
            <div className="pager">
              <span>
                共 {projects.totalElements} 个 · 第 {query.page + 1} 页
              </span>
              <button
                type="button"
                className="secondary-button"
                disabled={busy || query.page === 0}
                onClick={() => {
                  clearSelection();
                  setQuery({ ...query, page: query.page - 1 });
                }}
              >
                上一页项目
              </button>
              <button
                type="button"
                className="secondary-button"
                disabled={busy || query.page + 1 >= projects.totalPages}
                onClick={() => {
                  clearSelection();
                  setQuery({ ...query, page: query.page + 1 });
                }}
              >
                下一页项目
              </button>
            </div>
          )}
          <label>
            模拟未变动天数
            <select
              disabled={busy}
              value={days}
              onChange={(event) => {
                setDays(Number(event.target.value));
                setConfirming(false);
                setResult(null);
              }}
            >
              {levels.map((level) => (
                <option key={level.days} value={level.days}>
                  {level.label}
                </option>
              ))}
            </select>
          </label>
          <p className="subtle">
            仅触发选定层级，不补发其他层级；同一项目周期已处理或已发送的层级不会重复发送。已终止项目只读。
          </p>
          <button
            ref={submitButton}
            type="button"
            className="primary-button"
            disabled={!selected || busy || confirming}
            onClick={() => {
              setConfirming(true);
              setResult(null);
              setError('');
            }}
          >
            手动发起督办
          </button>
        </section>
      )}
      {confirming && selected && (
        <section className="panel" aria-labelledby="trigger-confirmation">
          <h2 id="trigger-confirmation" ref={confirmation} tabIndex={-1}>
            确认发送督办
          </h2>
          <p>
            {selected.projectCode} · {selected.projectName}
          </p>
          <p>
            {levels.find((level) => level.days === days)?.label}
            。确认后将立即尝试发送真实站内通知，不能撤回。
          </p>
          <div className="form-actions">
            <button
              type="button"
              className="secondary-button"
              disabled={busy}
              onClick={() => {
                setConfirming(false);
              }}
            >
              取消发送
            </button>
            <button
              type="button"
              className="primary-button"
              disabled={busy}
              onClick={() => void trigger()}
            >
              {busy ? '正在发送…' : '确认发送'}
            </button>
          </div>
        </section>
      )}
      {result && (
        <Feedback tone={result.delivered ? 'success' : 'muted'}>
          {result.delivered
            ? `督办已发送，接收人数：${result.recipients}。`
            : result.status === 'NO_RECIPIENTS'
              ? '本次未发送：未找到符合条件的接收人。请检查项目省分负责人、接口人范围或集团账号。'
              : '本次未发送：同一周期的该层级提醒已发送，或督办已处理。'}
        </Feedback>
      )}
      {selected && (
        <>
          <p>
            <Link to={`/projects/${selected.id}`}>查看项目详情</Link>
          </p>
          <ProjectInactivityAuditPanel
            key={selected.id}
            projectId={selected.id}
            revision={revision}
          />
        </>
      )}
    </section>
  );
}
