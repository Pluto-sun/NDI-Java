import { downloadBlob } from '../../shared/util/downloadAttachment';
import { PersonalizedTable } from '../../shared/list-columns/PersonalizedTable';
import { MultiSelect } from '../../shared/ui/MultiSelect';
import { TruncatedText } from '../../shared/ui/TruncatedText';
import { Select } from '../../shared/ui/Select';
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { projectsApi } from './api';
import type { ExportTask, ProjectSummary, ReferenceData } from './types';
import type { Page } from '../../shared/api/http';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { amount } from '../../shared/ui/format';
import { ProjectExportFieldPicker } from './ProjectExportFieldPicker';
import { Modal } from '../../shared/ui/Modal';
import { useBatchDelete } from '../../shared/ui/useBatchDelete';
import { ListPagination } from '../../shared/ui/ListPagination';

const productShortNames: Record<string, string> = {
  NETWORK: '5G',
  GEWU: '格物',
  WANGWU: '万悟',
  BIG_DATA: '大数据',
};

const emptyAdvanced = {
  signingSubjectCode: '',
  responsiblePersonName: '',
  keyword: '',
  opportunityDateStart: '',
  opportunityDateEnd: '',
};

export function ProjectsPage({
  reference,
  userId,
  admin,
}: {
  reference: ReferenceData;
  userId: string;
  admin: boolean;
}) {
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();
  const draftKey = `ndi.project-list-draft:${userId}:/projects${location.search}`;
  const [savedDraft] = useState<Record<string, unknown> | null>(() => {
    try {
      const value: unknown = JSON.parse(sessionStorage.getItem(draftKey) ?? 'null');
      return value && typeof value === 'object' && !Array.isArray(value)
        ? (value as Record<string, unknown>)
        : null;
    } catch {
      return null;
    }
  });
  const draftString = (key: string, fallback: string) =>
    typeof savedDraft?.[key] === 'string' ? String(savedDraft[key]) : fallback;
  const legacyCityId = searchParams.get('cityId')?.split(',').find(Boolean);
  const legacyProvinceId = legacyCityId
    ? reference.cities.find((city) => city.id === legacyCityId)?.provinceId
    : undefined;
  const query = useMemo(() => {
    const selected = (field: string, legacy: string) => [
      ...new Set(
        [...searchParams.getAll(field), ...searchParams.getAll(legacy)]
          .flatMap((value) => value.split(','))
          .filter(Boolean),
      ),
    ];
    const provinceIds = selected('provinceIds', 'provinceId');
    const cityIds = selected('cityIds', 'cityId');
    if (!provinceIds.length && legacyProvinceId) provinceIds.push(legacyProvinceId);
    return {
      productCodes: searchParams.getAll('productCodes'),
      page: Math.max(0, Math.min(100000, Number(searchParams.get('page')) || 0)),
      projectName: searchParams.get('projectName') ?? '',
      stage: searchParams.get('stage') ?? '',
      projectCode: searchParams.get('projectCode') ?? '',
      industryCode: searchParams.get('industryCode') ?? '',
      provinceIds,
      cityIds,
      ...(Object.fromEntries(
        Object.keys(emptyAdvanced).map((key) => [key, searchParams.get(key) ?? '']),
      ) as typeof emptyAdvanced),
    };
  }, [searchParams, legacyProvinceId]);
  function setQuery(value: typeof query | ((current: typeof query) => typeof query)) {
    const next = typeof value === 'function' ? value(query) : value;
    const params = new URLSearchParams();
    Object.entries(next).forEach(([key, value]) => {
      if (Array.isArray(value)) value.forEach((id) => params.append(key, id));
      else if (value !== '' && !(key === 'page' && value === 0)) params.set(key, String(value));
    });
    setSearchParams(params);
  }
  const [name, setName] = useState(draftString('projectName', query.projectName));
  const [stage, setStage] = useState(draftString('stage', query.stage));
  const [projectCode, setProjectCode] = useState(draftString('projectCode', query.projectCode));
  const [industryCode, setIndustryCode] = useState(draftString('industryCode', query.industryCode));
  const draftLocations = (key: string, fallback: string[]) => {
    const value = savedDraft?.[key];
    return Array.isArray(value) && value.every((id) => typeof id === 'string') ? value : fallback;
  };
  const [productCodes, setProductCodes] = useState(
    draftLocations('productCodes', query.productCodes),
  );
  const [provinceIds, setProvinceIds] = useState(draftLocations('provinceIds', query.provinceIds));
  const [cityIds, setCityIds] = useState(draftLocations('cityIds', query.cityIds));
  const provinces = reference.provinces.filter((item) =>
    reference.readableProvinceIds.includes(item.id),
  );
  const cities =
    provinceIds.length === 1
      ? reference.cities.filter(
          (item) =>
            item.provinceId === provinceIds[0] && reference.readableCityIds.includes(item.id),
        )
      : [];
  const selectedCityIds = cityIds.filter((id) => cities.some((item) => item.id === id));
  const [advanced, setAdvanced] = useState({
    ...emptyAdvanced,
    ...Object.fromEntries(
      Object.keys(emptyAdvanced).map((key) => [
        key,
        draftString(key, query[key as keyof typeof emptyAdvanced]),
      ]),
    ),
  });
  const lastAppliedQuery = useRef(query);
  const [advancedOpen, setAdvancedOpen] = useState(
    savedDraft?.advancedOpen === true ||
      !!projectCode ||
      !!industryCode ||
      Object.entries(advanced).some(([key, value]) => key !== 'signingSubjectCode' && value),
  );
  const invalidDateRange =
    !!advanced.opportunityDateStart &&
    !!advanced.opportunityDateEnd &&
    advanced.opportunityDateStart > advanced.opportunityDateEnd;
  useLayoutEffect(() => {
    if (lastAppliedQuery.current === query) return;
    lastAppliedQuery.current = query;
    setName(query.projectName);
    setProjectCode(query.projectCode);
    setStage(query.stage);
    setIndustryCode(query.industryCode);
    setProvinceIds(query.provinceIds);
    setProductCodes(query.productCodes);
    setCityIds(query.cityIds);
    setAdvanced({
      ...emptyAdvanced,
      ...Object.fromEntries(
        Object.keys(emptyAdvanced).map((key) => [key, query[key as keyof typeof emptyAdvanced]]),
      ),
    });
  }, [query]);
  const [reload, setReload] = useState(0);
  const [data, setData] = useState<Page<ProjectSummary> | null>(null);
  const [exportTask, setExportTask] = useState<ExportTask | null>(null);
  const [exportFields, setExportFields] = useState<string[] | null>(null);
  const [exportOpen, setExportOpen] = useState(false);
  const [exportReady, setExportReady] = useState(false);
  const currentFields = useRef(exportFields);
  currentFields.current = exportFields;
  const currentQuery = useRef(query);
  currentQuery.current = query;
  const [exportBusy, setExportBusy] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(true);
  const batch = useBatchDelete(
    admin,
    'PROJECTS',
    searchParams.toString(),
    [],
    () => {
      if (query.page) setQuery({ ...query, page: 0 });
      else setReload((value) => value + 1);
    },
    {
      count: busy || error ? 0 : (data?.totalElements ?? 0),
      filters: [
        'projectCode',
        'projectName',
        'stage',
        'industryCode',
        'provinceIds',
        'provinceId',
        'cityIds',
        'cityId',
        'productCodes',
        'signingSubjectCode',
        'responsiblePersonName',
        'keyword',
        'opportunityDateStart',
        'opportunityDateEnd',
      ],
      load: (params) =>
        projectsApi.list(
          { ...query, page: Number(params.get('page')), includeTerminated: true },
          Number(params.get('size')),
        ),
    },
  );
  const returnTo = `/projects${location.search}`;
  useLayoutEffect(() => {
    if (busy || !data) return;
    const scrollKey = `ndi.project-list-scroll:${returnTo}`;
    const savedScroll = sessionStorage.getItem(scrollKey);
    if (savedScroll === null) return;
    sessionStorage.removeItem(scrollKey);
    window.scrollTo(0, Number(savedScroll) || 0);
  }, [busy, data, returnTo]);

  useEffect(() => {
    let active = true;
    setBusy(true);
    // 新查询不继续展示上一组条件的下载入口。
    setExportTask(null);
    projectsApi
      .list({ ...query, includeTerminated: true })
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
  }, [query, reload]);

  useEffect(() => {
    setExportTask(null);
  }, [exportFields]);

  useEffect(() => {
    if (!exportTask || !['PENDING', 'RUNNING'].includes(exportTask.status)) return;
    let active = true;
    const taskQuery = currentQuery.current;
    const taskFields = currentFields.current;
    const timer = window.setInterval(() => {
      void projectsApi
        .exportTask(exportTask.id)
        .then((result) => {
          if (active && currentQuery.current === taskQuery && currentFields.current === taskFields)
            setExportTask(result);
        })
        .catch((cause) => {
          if (active && currentQuery.current === taskQuery && currentFields.current === taskFields)
            setError(cause instanceof Error ? cause.message : '导出状态获取失败');
        });
    }, 2000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [exportTask]);

  async function exportCurrent() {
    if (!exportFields?.length) return;
    const appliedQuery = query;
    const appliedFields = exportFields;
    setExportBusy(true);
    setError('');
    try {
      const { page: _page, ...filter } = query;
      void _page;
      const result = await projectsApi.exportProjects(
        { ...filter, includeTerminated: true },
        appliedFields,
      );
      if (currentQuery.current === appliedQuery && currentFields.current === appliedFields) {
        setExportTask(result);
        setExportOpen(false);
      }
    } catch (cause) {
      if (currentQuery.current === appliedQuery && currentFields.current === appliedFields)
        setError(cause instanceof Error ? cause.message : '导出提交失败');
    } finally {
      setExportBusy(false);
    }
  }

  async function download() {
    if (!exportTask) return;
    const appliedQuery = query;
    const appliedFields = exportFields;
    try {
      const file = await projectsApi.exportDownload(exportTask.id);
      if (currentQuery.current === appliedQuery && currentFields.current === appliedFields)
        downloadBlob(file, `projects-${exportTask.id}.csv`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'CSV 下载失败');
    }
  }

  const label = (code: string) =>
    reference.stages.find((item) => item.code === code)?.label ?? code;
  return (
    <section>
      <div className="page-heading project-stage-heading">
        <div>
          <p>查看权限范围内的项目主档，按编号、名称、行业、归属和阶段定位。</p>
        </div>
      </div>
      <form
        className="filter-bar project-list-filters"
        aria-label="项目筛选"
        onSubmit={(event) => {
          event.preventDefault();
          if (invalidDateRange) {
            event.currentTarget
              .querySelector<HTMLInputElement>('input[name="opportunityDateEnd"]')
              ?.focus();
            return;
          }
          setQuery({
            page: 0,
            projectName: name,
            stage,
            projectCode,
            industryCode,
            productCodes,
            provinceIds,
            cityIds: selectedCityIds,
            ...advanced,
          });
        }}
      >
        <label className="project-name-filter">
          项目名称
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="输入项目名称"
          />
        </label>
        <label>
          当前阶段
          <Select value={stage} onChange={(event) => setStage(event.target.value)}>
            <option value="">全部阶段</option>
            {reference.stages.map((item) => (
              <option key={item.code} value={item.code}>
                {item.label}
              </option>
            ))}
          </Select>
        </label>
        <label className="project-signing-subject">
          签约主体
          <Select
            value={advanced.signingSubjectCode}
            onChange={(event) =>
              setAdvanced({ ...advanced, signingSubjectCode: event.target.value })
            }
          >
            <option value="">全部签约主体</option>
            <option value="PROVINCE_SIGN">省分签约</option>
            <option value="DS_SIGN">数科签约</option>
          </Select>
        </label>
        <MultiSelect
          label="归属省分"
          values={provinceIds}
          options={provinces.map((item) => ({ value: item.id, label: item.name }))}
          onChange={(ids) => {
            setProvinceIds(ids);
            setCityIds((previous) =>
              ids.length === 1
                ? previous.filter((id) =>
                    reference.cities.some((city) => city.id === id && city.provinceId === ids[0]),
                  )
                : [],
            );
          }}
        />
        <MultiSelect
          label="归属地市／区县"
          values={selectedCityIds}
          disabledReason={provinceIds.length === 1 ? undefined : '请先单选省分'}
          options={cities.map((item) => ({
            value: item.id,
            label: item.legacyMunicipalityCity ? item.name + '（未细分区县）' : item.name,
          }))}
          onChange={setCityIds}
        />
        <MultiSelect
          label="产品类别"
          values={productCodes}
          options={reference.categories.map(({ code, label }) => ({ value: code, label }))}
          onChange={setProductCodes}
        />
        <div className="inline-actions project-filter-actions">
          <button type="submit" className="primary-button">
            查询
          </button>
          <button
            type="button"
            className="secondary-button"
            onClick={() => {
              sessionStorage.removeItem(draftKey);
              setName('');
              setProjectCode('');
              setStage('');
              setIndustryCode('');
              setProvinceIds([]);
              setCityIds([]);
              setAdvanced(emptyAdvanced);
              setQuery({
                page: 0,
                projectName: '',
                stage: '',
                projectCode: '',
                industryCode: '',
                productCodes: [],
                provinceIds: [],
                cityIds: [],
                ...emptyAdvanced,
              });
            }}
          >
            重置筛选
          </button>
        </div>
        <details
          className="project-advanced-filters"
          open={advancedOpen}
          onToggle={(event) => setAdvancedOpen(event.currentTarget.open)}
        >
          <summary>更多筛选条件</summary>
          <div className="form-grid">
            <label>
              项目编号
              <input
                value={projectCode}
                onChange={(event) => setProjectCode(event.target.value)}
                placeholder="输入项目编号"
              />
            </label>
            <label>
              项目日期起
              <input
                type="date"
                min="1000-01-01"
                max="9999-12-31"
                aria-invalid={invalidDateRange}
                aria-describedby="project-date-hint"
                value={advanced.opportunityDateStart}
                onChange={(event) =>
                  setAdvanced({ ...advanced, opportunityDateStart: event.target.value })
                }
              />
            </label>
            <label>
              项目日期止
              <input
                type="date"
                name="opportunityDateEnd"
                min="1000-01-01"
                max="9999-12-31"
                aria-invalid={invalidDateRange}
                aria-describedby="project-date-hint"
                value={advanced.opportunityDateEnd}
                onChange={(event) =>
                  setAdvanced({ ...advanced, opportunityDateEnd: event.target.value })
                }
              />
            </label>
            <p
              id="project-date-hint"
              className="subtle span-two"
              role={invalidDateRange ? 'alert' : undefined}
            >
              {invalidDateRange
                ? '项目日期起不得晚于项目日期止。'
                : '按 Asia/Shanghai 自然日筛选；项目日期只在创建、导入或审批通过时更新，旧日期未恢复的项目不纳入日期范围查询。'}
            </p>
            <label>
              编号或名称关键字
              <input
                maxLength={200}
                value={advanced.keyword}
                onChange={(event) => setAdvanced({ ...advanced, keyword: event.target.value })}
              />
            </label>
            <label>
              所属行业
              <Select
                value={industryCode}
                onChange={(event) => setIndustryCode(event.target.value)}
              >
                <option value="">全部行业</option>
                {reference.industries.map((item) => (
                  <option key={item.code} value={item.code}>
                    {item.label}
                  </option>
                ))}
              </Select>
            </label>
            <label>
              项目负责人
              <input
                maxLength={100}
                value={advanced.responsiblePersonName}
                onChange={(event) =>
                  setAdvanced({ ...advanced, responsiblePersonName: event.target.value })
                }
                placeholder="按负责人姓名查找，不区分职责"
              />
            </label>
          </div>
        </details>
      </form>
      {exportOpen && (
        <Modal title="配置项目导出" busy={exportBusy} onClose={() => setExportOpen(false)}>
          <h2 tabIndex={-1}>导出字段设置</h2>
          <ProjectExportFieldPicker
            selected={exportFields}
            onChange={setExportFields}
            onReady={setExportReady}
          />
          {error && <Feedback tone="error">{error}</Feedback>}
          <div className="form-actions">
            <button
              type="button"
              className="secondary-button"
              disabled={exportBusy}
              onClick={() => setExportOpen(false)}
            >
              取消
            </button>
            <button
              type="button"
              className="primary-button"
              disabled={exportBusy || !exportReady || !exportFields?.length}
              onClick={() => void exportCurrent()}
            >
              {exportBusy ? '提交中…' : '生成导出文件'}
            </button>
          </div>
        </Modal>
      )}
      <div className="action-row">
        <button
          type="button"
          className="secondary-button"
          onClick={() => {
            setError('');
            setExportReady(false);
            setExportOpen(true);
          }}
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
      {batch.toolbar}
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
          <PersonalizedTable
            list="PROJECTS"
            accountId={userId}
            label="项目清单"
            className="project-list-table"
            scrollLabel="项目清单表格，可横向滚动"
            rows={data?.items ?? []}
            columns={[
              {
                id: 'name',
                label: '项目名称',
                width: 280,
                cell: (project) => <TruncatedText text={project.projectName} />,
              },
              {
                id: 'location',
                label: '归属',
                width: 190,
                cell: (project) => (
                  <TruncatedText
                    text={`${reference.provinces.find((item) => item.id === project.provinceId)?.name ?? '未匹配省分'} / ${reference.cities.find((item) => item.id === project.cityId)?.name ?? '未匹配地市'}`}
                  />
                ),
              },
              {
                id: 'customer',
                label: '客户',
                width: 170,
                cell: (project) => <TruncatedText text={project.customerName || '未填写'} />,
              },
              {
                id: 'stage',
                label: '阶段',
                width: 100,
                cell: (project) => (
                  <span className={`stage stage-${project.currentStageCode.toLowerCase()}`}>
                    {label(project.currentStageCode)}
                  </span>
                ),
              },
              {
                id: 'category',
                label: '类别',
                width: 140,
                cell: (project) => (
                  <TruncatedText
                    text={
                      project.categoryAmounts
                        .map(({ categoryCode }) => productShortNames[categoryCode] ?? categoryCode)
                        .join('、') || '—'
                    }
                  />
                ),
              },
              {
                id: 'signing-subject',
                label: '签约主体',
                width: 110,
                cell: (project) =>
                  project.signingSubjectCode === 'DS_SIGN' ? '数科签约' : '省分签约',
              },
              {
                id: 'total',
                label: '预计总金额',
                width: 112,
                cellClassName: 'number',
                cell: (project) => <TruncatedText text={amount(project.totalAmount)} />,
              },
              {
                id: 'owners',
                label: '项目负责人',
                width: 175,
                cell: (project) => (
                  <TruncatedText
                    text={
                      project.responsiblePersons?.map((person) => person.name).join('；') || '—'
                    }
                  />
                ),
              },
              {
                id: 'month',
                label: '项目所属月份',
                width: 120,
                cell: (project) => project.attributionMonth?.slice(0, 7) ?? '—',
              },
              {
                id: 'industry',
                label: '行业',
                width: 165,
                cell: (project) => (
                  <TruncatedText
                    text={
                      reference.industries.find((item) => item.code === project.industryCode)
                        ?.label ??
                      project.industryCode ??
                      '未填写'
                    }
                  />
                ),
              },
            ]}
            actions={(project) => (
              <Link
                className="secondary-button compact-button"
                aria-label={`详情：${project.projectName}`}
                to={`/projects/${project.id}`}
                state={{ projectListReturnTo: returnTo }}
                onClick={() => {
                  sessionStorage.setItem(
                    `ndi.project-list-scroll:${returnTo}`,
                    String(window.scrollY),
                  );
                  sessionStorage.setItem(
                    draftKey,
                    JSON.stringify({
                      projectName: name,
                      stage,
                      projectCode,
                      industryCode,
                      productCodes,
                      provinceIds,
                      cityIds: selectedCityIds,
                      ...advanced,
                      advancedOpen,
                    }),
                  );
                }}
              >
                详情
              </Link>
            )}
          />
          {data?.items.length === 0 && <div className="empty-state">暂无符合条件的项目</div>}
          <ListPagination
            label="项目列表"
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 0}
            totalElements={data?.totalElements ?? 0}
            onPageChange={(page) => setQuery((current) => ({ ...current, page }))}
          />
        </div>
      )}
    </section>
  );
}
