import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { projectsApi } from './api';
import type { ProjectSummary, ReferenceData } from './types';
import type { Page } from '../../shared/api/http';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { amount, dateTime } from '../../shared/ui/format';

export function ProjectsPage({
  reference,
  canCreate,
}: {
  reference: ReferenceData;
  canCreate: boolean;
}) {
  const [name, setName] = useState('');
  const [stage, setStage] = useState('');
  const [query, setQuery] = useState({ page: 0, projectName: '', stage: '' });
  const [data, setData] = useState<Page<ProjectSummary> | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(true);

  useEffect(() => {
    let active = true;
    setBusy(true);
    projectsApi
      .list(query)
      .then((result) => {
        if (active) {
          setData(result);
          setError('');
        }
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '项目加载失败');
      })
      .finally(() => {
        if (active) setBusy(false);
      });
    return () => {
      active = false;
    };
  }, [query]);

  const label = (code: string) =>
    reference.stages.find((item) => item.code === code)?.label ?? code;
  return (
    <section>
      <div className="page-heading">
        <div>
          <span className="eyebrow">PROJECTS</span>
          <h1>项目列表</h1>
          <p>查看权限范围内的项目主档，按名称和阶段快速定位。</p>
        </div>
        {canCreate && (
          <Link className="primary-button" to="/projects/new">
            新建项目
          </Link>
        )}
      </div>
      <form
        className="filter-bar"
        onSubmit={(event) => {
          event.preventDefault();
          setQuery({ page: 0, projectName: name, stage });
        }}
      >
        <label>
          项目名称
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="输入项目名称"
          />
        </label>
        <label>
          当前阶段
          <select value={stage} onChange={(event) => setStage(event.target.value)}>
            <option value="">全部阶段</option>
            {reference.stages.map((item) => (
              <option key={item.code} value={item.code}>
                {item.label}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="secondary-button">
          查询
        </button>
      </form>
      {error && <Feedback tone="error">{error}</Feedback>}
      {busy ? (
        <Loading />
      ) : (
        <div className="panel table-panel">
          <div className="panel-top">
            <strong>项目清单</strong>
            <span>共 {data?.totalElements ?? 0} 个项目</span>
          </div>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>项目编号 / 名称</th>
                  <th>客户</th>
                  <th>行业</th>
                  <th>阶段</th>
                  <th>预计总金额</th>
                  <th>创建时间</th>
                </tr>
              </thead>
              <tbody>
                {data?.items.map((project) => (
                  <tr key={project.id}>
                    <td>
                      <span className="subtle">{project.projectCode}</span>
                      <Link className="table-link" to={`/projects/${project.id}`}>
                        {project.projectName}
                      </Link>
                    </td>
                    <td>{project.customerName}</td>
                    <td>
                      {reference.industries.find((item) => item.code === project.industryCode)
                        ?.label ?? project.industryCode}
                    </td>
                    <td>
                      <span className={`stage stage-${project.currentStageCode.toLowerCase()}`}>
                        {label(project.currentStageCode)}
                      </span>
                    </td>
                    <td className="number">{amount(project.totalAmount)}</td>
                    <td>{dateTime(project.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {data?.items.length === 0 && <div className="empty-state">暂无符合条件的项目</div>}
          <div className="pager">
            <span>
              第 {(data?.page ?? 0) + 1} / {Math.max(data?.totalPages ?? 0, 1)} 页
            </span>
            <div>
              <button
                type="button"
                className="secondary-button"
                disabled={!data || data.page === 0}
                onClick={() => setQuery((current) => ({ ...current, page: current.page - 1 }))}
              >
                上一页
              </button>
              <button
                type="button"
                className="secondary-button"
                disabled={!data || data.page + 1 >= data.totalPages}
                onClick={() => setQuery((current) => ({ ...current, page: current.page + 1 }))}
              >
                下一页
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
