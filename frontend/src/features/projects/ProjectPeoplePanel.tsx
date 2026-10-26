import { useEffect, useState, type FormEvent } from 'react';
import type { ProjectDetail, ProjectResponsiblePerson, ProjectUser } from './types';
import { projectsApi } from './api';
import { Modal } from '../../shared/ui/Modal';
import { Select } from '../../shared/ui/Select';
import { Feedback } from '../../shared/ui/Feedback';

export const responsibilityLabels: Record<string, string> = {
  PROVINCE_LEADER: '省分负责人',
  CITY_MANAGER: '地市客户经理',
  SUPPORT_EXPERT: '产品单位支撑专家',
};

/** 职责与账号关联是项目责任信息，不参与权限授予。 */
export function ProjectPeoplePanel({
  project,
  editable,
  onSaved,
}: {
  project: ProjectDetail;
  editable: boolean;
  onSaved: (project: ProjectDetail) => void;
}) {
  const [open, setOpen] = useState(false);
  const [rows, setRows] = useState<ProjectResponsiblePerson[]>([]);
  const [accounts, setAccounts] = useState<ProjectUser[]>([]);
  const [leaders, setLeaders] = useState<ProjectUser[]>([]);
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    if (!open) return;
    let active = true;
    setLoading(true);
    setError('');
    Promise.all([
      projectsApi.peopleAccounts(project.id, search, false),
      projectsApi.peopleAccounts(project.id, search, true),
    ])
      .then(([all, province]) => {
        if (active) {
          setAccounts(all);
          setLeaders(province);
        }
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '账号目录加载失败');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [open, project.id, search]);
  function change(index: number, patch: Partial<ProjectResponsiblePerson>) {
    setRows((current) => current.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      onSaved(await projectsApi.configurePeople(project.id, project.versionNo, rows));
      setOpen(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '人员配置保存失败');
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="panel project-people-panel" aria-label="项目负责人信息">
      <div className="panel-top">
        <h2>项目负责人</h2>
        {editable && (
          <button
            type="button"
            className="secondary-button"
            onClick={() => {
              setRows(project.responsiblePersons.map((person) => ({ ...person })));
              setKeyword('');
              setSearch('');
              setError('');
              setOpen(true);
            }}
          >
            配置人员
          </button>
        )}
      </div>
      <p className="subtle">
        至少一位负责人；职责和账号关联均可不配置，不授予查看、编辑或审批权限。
      </p>
      <ul className="project-person-list">
        {project.responsiblePersons.map((person) => (
          <li key={person.id}>
            <strong>{person.name}</strong>
            <span className="person-role">
              {person.responsibility ? responsibilityLabels[person.responsibility] : '未分配职责'}
            </span>
            <small>
              {person.userId
                ? `关联账号：${person.accountName ?? '账号不可用'}${person.accountEnabled ? '' : '（已停用）'}`
                : '未关联账号'}
            </small>
          </li>
        ))}
      </ul>
      {open && (
        <Modal title="配置项目负责人" busy={busy} onClose={() => setOpen(false)}>
          <h2 tabIndex={-1}>人员职责与账号关联</h2>
          <p>每人至多一种职责，省分负责人至多一位。省分负责人只能关联本省已启用的省分角色账号。</p>
          <form
            className="filter-bar"
            onSubmit={(event) => {
              event.preventDefault();
              setSearch(keyword.trim());
            }}
          >
            <label>
              搜索系统账号
              <input
                maxLength={100}
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                placeholder="按系统账号姓名搜索"
              />
            </label>
            <button className="secondary-button" disabled={loading || busy}>
              查找账号
            </button>
          </form>
          <form onSubmit={save}>
            {error && <Feedback tone="error">{error}</Feedback>}
            {loading && <p role="status">正在加载可关联账号…</p>}
            {rows.map((row, index) => {
              const choices = row.responsibility === 'PROVINCE_LEADER' ? leaders : accounts;
              return (
                <fieldset
                  className="project-person-editor"
                  key={row.id ?? `new-${index}`}
                  disabled={busy}
                >
                  <legend>负责人 {index + 1}</legend>
                  <div className="form-grid">
                    <label>
                      姓名
                      <input
                        required
                        maxLength={100}
                        value={row.name}
                        onChange={(event) => change(index, { name: event.target.value })}
                      />
                    </label>
                    <label>
                      项目职责
                      <Select
                        value={row.responsibility ?? ''}
                        onChange={(event) =>
                          change(index, {
                            responsibility: (event.target.value ||
                              null) as ProjectResponsiblePerson['responsibility'],
                          })
                        }
                      >
                        <option value="">未分配</option>
                        {Object.entries(responsibilityLabels).map(([code, label]) => (
                          <option
                            key={code}
                            value={code}
                            disabled={
                              code === 'PROVINCE_LEADER' &&
                              rows.some(
                                (person, i) => i !== index && person.responsibility === code,
                              )
                            }
                          >
                            {label}
                          </option>
                        ))}
                      </Select>
                    </label>
                    <label>
                      关联系统账号
                      <Select
                        disabled={loading}
                        value={row.userId ?? ''}
                        onChange={(event) =>
                          change(index, {
                            userId: event.target.value || null,
                            accountName:
                              choices.find((user) => user.id === event.target.value)?.displayName ??
                              null,
                            accountEnabled: !!event.target.value,
                          })
                        }
                      >
                        <option value="">不关联账号</option>
                        {row.userId && !choices.some((user) => user.id === row.userId) && (
                          <option value={row.userId}>
                            {row.accountName ?? '原关联账号'}（请核对有效性）
                          </option>
                        )}
                        {choices.map((user) => (
                          <option
                            key={user.id}
                            value={user.id}
                            disabled={rows.some(
                              (person, i) => i !== index && person.userId === user.id,
                            )}
                          >
                            {user.displayName} · {user.id.slice(0, 8)}
                          </option>
                        ))}
                      </Select>
                    </label>
                    <button
                      type="button"
                      className="text-button"
                      disabled={rows.length <= 1}
                      onClick={() => setRows((current) => current.filter((_, i) => i !== index))}
                    >
                      移除负责人 {index + 1}
                    </button>
                  </div>
                </fieldset>
              );
            })}
            <button
              type="button"
              className="secondary-button"
              disabled={busy || rows.length >= 100}
              onClick={() =>
                setRows((current) => [
                  ...current,
                  {
                    id: null,
                    name: '',
                    responsibility: null,
                    userId: null,
                    accountName: null,
                    accountEnabled: false,
                  },
                ])
              }
            >
              添加负责人
            </button>
            <div className="form-actions">
              <button
                type="button"
                className="secondary-button"
                disabled={busy}
                onClick={() => setOpen(false)}
              >
                取消
              </button>
              <button className="primary-button" disabled={busy || loading || !rows.length}>
                {busy ? '保存中…' : '保存人员配置'}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </section>
  );
}
