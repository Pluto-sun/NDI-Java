import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { projectsApi } from './api';
import type { ExportTask, ProjectSummary, ReferenceData } from './types';
import type { Page } from '../../shared/api/http';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { amount, dateTime } from '../../shared/ui/format';

const emptyAdvanced = {
  signingSubjectCode: '',
  provinceLeaderName: '',
  cityAccountManagerName: '',
  productSupportExpertName: '',
  keyword: '',
};

export function ProjectsPage({
  reference,
  canCreate,
}: {
  reference: ReferenceData;
  canCreate: boolean;
}) {
  const [searchParams] = useSearchParams();
  const initialCityId = searchParams.get('cityId') ?? '';
  const [name, setName] = useState('');
  const [stage, setStage] = useState('');
  const [projectCode, setProjectCode] = useState('');
  const [industryCode, setIndustryCode] = useState('');
  const [cityId, setCityId] = useState(initialCityId);
  const [includeTerminated, setIncludeTerminated] = useState(true);
  const [advanced, setAdvanced] = useState(emptyAdvanced);
  const [query, setQuery] = useState({
    page: 0,
    projectName: '',
    stage: '',
    projectCode: '',
    industryCode: '',
    cityId: initialCityId,
    includeTerminated: true,
    ...emptyAdvanced,
  });
  const [data, setData] = useState<Page<ProjectSummary> | null>(null);
  const [exportTask, setExportTask] = useState<ExportTask | null>(null);
  const currentQuery = useRef(query);
  currentQuery.current = query;
  const [exportBusy, setExportBusy] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(true);

  useEffect(() => {
    let active = true;
    setBusy(true);
    // 新查询不继续展示上一组条件的下载入口。
    setExportTask(null);
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

  useEffect(() => {
    if (!exportTask || !['PENDING', 'RUNNING'].includes(exportTask.status)) return;
    let active = true;
    const taskQuery = currentQuery.current;
    const timer = window.setInterval(() => {
      void projectsApi
        .exportTask(exportTask.id)
        .then((result) => {
          if (active && currentQuery.current === taskQuery) setExportTask(result);
        })
        .catch((cause) => {
          if (active && currentQuery.current === taskQuery)
            setError(cause instanceof Error ? cause.message : '导出状态获取失败');
        });
    }, 2000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [exportTask]);

  async function exportCurrent() {
    const appliedQuery = query;
    setExportBusy(true);
    setError('');
    try {
      const { page: _page, ...filter } = query;
      void _page;
      const result = await projectsApi.exportProjects(filter);
      if (currentQuery.current === appliedQuery) setExportTask(result);
    } catch (cause) {
      if (currentQuery.current === appliedQuery)
        setError(cause instanceof Error ? cause.message : '导出提交失败');
    } finally {
      setExportBusy(false);
    }
  }

  async function download() {
    if (!exportTask) return;
    const appliedQuery = query;
    try {
      const result = await projectsApi.exportDownload(exportTask.id);
      if (currentQuery.current === appliedQuery) window.location.assign(result.url);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '下载链接获取失败');
    }
  }

  const label = (code: string) =>
    reference.stages.find((item) => item.code === code)?.label ?? code;
  return (
    <section>
      <div className="page-heading">
        <div>
          <span className="eyebrow">PROJECTS</span>
          <h1>项目列表</h1>
          <p>查看权限范围内的项目主档，按编号、名称、行业、地市和阶段定位。</p>
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
          setQuery({
            page: 0,
            projectName: name,
            stage,
            projectCode,
            industryCode,
            cityId,
            includeTerminated,
            ...advanced,
          });
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
          项目编号
          <input
            value={projectCode}
            onChange={(event) => setProjectCode(event.target.value)}
            placeholder="输入项目编号"
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
        <label>
          所属行业
          <select value={industryCode} onChange={(event) => setIndustryCode(event.target.value)}>
            <option value="">全部行业</option>
            {reference.industries.map((item) => (
              <option key={item.code} value={item.code}>
                {item.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          归属地市
          <select value={cityId} onChange={(event) => setCityId(event.target.value)}>
            <option value="">全部地市</option>
            {reference.cities.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={includeTerminated}
            onChange={(event) => setIncludeTerminated(event.target.checked)}
          />
          含已终止项目
        </label>
        <details className="project-advanced-filters">
          <summary>更多筛选条件</summary>
          <div className="form-grid">
            <label>
              编号或名称关键字
              <input
                maxLength={200}
                value={advanced.keyword}
                onChange={(event) => setAdvanced({ ...advanced, keyword: event.target.value })}
              />
            </label>
            <label>
              签约主体
              <select
                value={advanced.signingSubjectCode}
                onChange={(event) =>
                  setAdvanced({ ...advanced, signingSubjectCode: event.target.value })
                }
              >
                <option value="">全部签约主体</option>
                <option value="PROVINCE_SIGN">省分签约</option>
                <option value="DS_SIGN">数科签约</option>
              </select>
            </label>
            <label>
              省分负责人
              <input
                maxLength={100}
                value={advanced.provinceLeaderName}
                onChange={(event) =>
                  setAdvanced({ ...advanced, provinceLeaderName: event.target.value })
                }
                placeholder="按当前目录姓名查找"
              />
            </label>
            <label>
              地市客户经理
              <input
                maxLength={100}
                value={advanced.cityAccountManagerName}
                onChange={(event) =>
                  setAdvanced({ ...advanced, cityAccountManagerName: event.target.value })
                }
              />
            </label>
            <label>
              产品单位支撑专家
              <input
                maxLength={100}
                value={advanced.productSupportExpertName}
                onChange={(event) =>
                  setAdvanced({ ...advanced, productSupportExpertName: event.target.value })
                }
              />
            </label>
          </div>
        </details>
        <button type="submit" className="secondary-button">
          查询
        </button>
        <button
          type="button"
          className="text-button"
          onClick={() => {
            setName('');
            setProjectCode('');
            setStage('');
            setIndustryCode('');
            setCityId('');
            setIncludeTerminated(true);
            setAdvanced(emptyAdvanced);
            setQuery({
              page: 0,
              projectName: '',
              stage: '',
              projectCode: '',
              industryCode: '',
              cityId: '',
              includeTerminated: true,
              ...emptyAdvanced,
            });
          }}
        >
          重置筛选
        </button>
      </form>
      <div className="action-row">
        <button
          type="button"
          className="secondary-button"
          onClick={() => void exportCurrent()}
          disabled={exportBusy}
        >
          {exportBusy ? '提交中…' : '按当前条件导出 CSV'}
        </button>
        {exportTask && (
          <span role="status">
            导出任务：{exportTask.status}
            {exportTask.status === 'COMPLETED' ? ` · ${exportTask.rowCount} 条` : ''}
            {exportTask.errorMessage ? ` · ${exportTask.errorMessage}` : ''}
          </span>
        )}
        {exportTask?.status === 'COMPLETED' && (
          <button type="button" className="primary-button" onClick={() => void download()}>
            下载 CSV
          </button>
        )}
      </div>
      <p className="subtle">导出使用已查询的条件；修改筛选后请先点击“查询”。</p>
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
