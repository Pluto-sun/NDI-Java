import { lazy, Suspense, useCallback, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import type { ReferenceData } from '../projects/types';
import { useLiveQuery } from '../../shared/api/useLiveQuery';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { amount, businessDate, dateTime } from '../../shared/ui/format';
import { ProjectStageTabs, PROJECT_STAGE_TABS } from '../../shared/ui/ProjectStageTabs';
import {
  dashboardApi,
  dashboardCategories,
  dashboardProducts,
  percent,
  type DashboardFilter,
  type DashboardSummary,
} from './dashboardApi';
import { DashboardDonut, DashboardTrend, Sparkline } from './DashboardCharts';
import { DashboardFilters, type ClassificationFilter } from './DashboardFilters';
import { DashboardRegionPanel } from './DashboardRegionPanel';
import { DashboardActivities } from './DashboardActivities';
import './dashboard.css';

const AdvancedWorkbench = lazy(() =>
  import('./AdvancedWorkbench').then((module) => ({ default: module.AdvancedWorkbench })),
);
function comparison(current: number, previous: number) {
  if (!previous) return current ? '上期为 0，不计算增幅' : '较上期持平';
  const delta = ((current - previous) / previous) * 100;
  return `较上期 ${delta > 0 ? '+' : ''}${delta.toFixed(1)}%`;
}
function totalValues(summary: DashboardSummary) {
  return [
    summary.projectCount,
    summary.network.count,
    summary.digital.count,
    summary.intelligent.count,
    summary.provinceCount,
  ];
}

export function WorkbenchPage({ reference }: { reference: ReferenceData }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const stage = PROJECT_STAGE_TABS.some((item) => item.code === searchParams.get('stage'))
    ? searchParams.get('stage')!
    : '';
  const stageLabel = stage
    ? `（${PROJECT_STAGE_TABS.find((item) => item.code === stage)?.label}阶段）`
    : '';
  const currentMonth = businessDate(new Date().toISOString()).slice(0, 7);
  const [from, setFrom] = useState(`${currentMonth.slice(0, 4)}-01`),
    [to, setTo] = useState(currentMonth);
  const [range, setRange] = useState({ from, to });
  const [includeTerminated, setIncludeTerminated] = useState(false);
  const [regionFilter, setRegionFilter] = useState<ClassificationFilter>({
    category: '',
    product: '',
  });
  const [trendFilter, setTrendFilter] = useState<ClassificationFilter>({
    category: '',
    product: '',
  });
  const [regionMetric, setRegionMetric] = useState<'amount' | 'count'>('amount');
  const [trendMetric, setTrendMetric] = useState<'amount' | 'count'>('amount');
  const [advanced, setAdvanced] = useState(false);
  const filter = useMemo<DashboardFilter>(
    () => ({ ...range, stage, includeTerminated }),
    [range, stage, includeTerminated],
  );
  const load = useCallback((signal: AbortSignal) => dashboardApi.view(filter, signal), [filter]);
  const loadRegions = useCallback(
    (signal: AbortSignal) => dashboardApi.view({ ...filter, ...regionFilter }, signal),
    [filter, regionFilter],
  );
  const loadTrend = useCallback(
    (signal: AbortSignal) => dashboardApi.view({ ...filter, ...trendFilter }, signal),
    [filter, trendFilter],
  );
  const main = useLiveQuery(load, '驾驶舱加载失败');
  const hasRegionFilter = !!(regionFilter.category || regionFilter.product);
  const hasTrendFilter = !!(trendFilter.category || trendFilter.product);
  const regionQuery = useLiveQuery(loadRegions, '区域分布加载失败', hasRegionFilter);
  const trendQuery = useLiveQuery(loadTrend, '趋势分析加载失败', hasTrendFilter);
  // 默认筛选共用一次聚合结果，只有独立分类条件才额外请求。
  const regions = hasRegionFilter ? regionQuery : main;
  const trend = hasTrendFilter ? trendQuery : main;
  const data = main.data;
  const stageName = (code: string) =>
    reference.stages.find((item) => item.code === code)?.label ?? code;
  const cards = data
    ? [
        {
          label: '全国项目数',
          value: `${data.current.projectCount}`,
          unit: '个',
          detail: '权限范围内去重项目数',
        },
        ...dashboardCategories.map((item) => ({
          label: `${item.label}项目数及总金额`,
          value: `${data.current[item.key].count}`,
          unit: '个',
          detail: `${amount(data.current[item.key].amount)} 万元`,
        })),
        {
          label: '覆盖省份',
          value: `${data.current.provinceCount}`,
          unit: '个',
          detail: '有项目的省份',
        },
      ]
    : [];
  const currentValues = data ? totalValues(data.current) : [],
    previousValues = data ? totalValues(data.previous) : [];
  return (
    <section className="project-dashboard">
      <div className="page-heading dashboard-page-heading">
        <div>
          <span className="eyebrow">PROJECT OPERATIONS</span>
          <h1>项目管理驾驶舱</h1>
          <p>工业互联网三化项目经营驾驶舱 · {stage ? stageName(stage) : '全部阶段'}</p>
        </div>
        <Link className="secondary-button" to={`/projects${stage ? `?stage=${stage}` : ''}`}>
          查看项目清单
        </Link>
      </div>
      <ProjectStageTabs
        value={stage}
        onChange={(value) => setSearchParams(value ? { stage: value } : {})}
      />
      <div className="dashboard-toolbar">
        <form
          className="dashboard-inline-filters"
          onSubmit={(event) => {
            event.preventDefault();
            if (from && to && from <= to) setRange({ from, to });
          }}
        >
          <label>
            所属月份起始
            <input
              type="month"
              value={from}
              max={to}
              min="1000-01"
              required
              onChange={(event) => setFrom(event.target.value)}
            />
          </label>
          <label>
            所属月份结束
            <input
              type="month"
              value={to}
              min={from}
              required
              onChange={(event) => setTo(event.target.value)}
            />
          </label>
          <button className="primary-button" disabled={main.busy}>
            应用月份
          </button>
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={includeTerminated}
              onChange={(event) => setIncludeTerminated(event.target.checked)}
            />
            含已终止项目
          </label>
        </form>
        <button
          className="secondary-button"
          disabled={main.busy || regions.busy || trend.busy}
          onClick={() => {
            void main.refresh();
            if (hasRegionFilter) void regionQuery.refresh();
            if (hasTrendFilter) void trendQuery.refresh();
          }}
        >
          刷新驾驶舱
        </button>
      </div>
      <p className="subtle dashboard-scope">
        {range.from} 至 {range.to} ·
        项目唯一所属月份，区间不重复累计；较上期为前一个等长月份区间。每 30 秒刷新。
        {main.updatedAt && ` 更新于 ${dateTime(main.updatedAt)}`}
      </p>
      {main.error && (
        <Feedback tone="error">
          {main.error}
          {data && '，当前保留上次成功数据。'}
        </Feedback>
      )}
      {!data ? (
        !main.error && <Loading />
      ) : (
        <>
          <div className="dashboard-kpis">
            {cards.map((card, index) => (
              <article className="dashboard-kpi" key={card.label}>
                <span>{card.label}</span>
                <div className="dashboard-kpi-value">
                  <strong>{card.value}</strong>
                  <small>{card.unit}</small>
                </div>
                <b>{card.detail}</b>
                <div className="dashboard-kpi-footer">
                  <small>
                    {index === 4
                      ? `较上期 ${currentValues[index] - previousValues[index] > 0 ? '+' : ''}${currentValues[index] - previousValues[index]} 个`
                      : comparison(currentValues[index], previousValues[index])}
                  </small>
                  <Sparkline
                    values={data.trend.map((point) => totalValues(point.summary)[index])}
                  />
                </div>
                {index > 0 && index < 4 && (
                  <small>
                    {comparison(
                      data.current[dashboardCategories[index - 1].key].amount,
                      data.previous[dashboardCategories[index - 1].key].amount,
                    )}
                    （金额）
                  </small>
                )}
              </article>
            ))}
          </div>
          <div className="dashboard-distribution-grid">
            <section className="panel">
              <h2>{stage ? `项目分布概览${stageLabel}` : '项目金额分布概览'}</h2>
              <DashboardDonut
                label={stage ? '项目总数' : '项目总金额'}
                total={stage ? data.current.projectCount : data.current.totalAmount}
                unit={stage ? '个' : '万元'}
                items={[
                  ...dashboardCategories.map((item) => ({
                    label: item.label,
                    value: data.current[item.key][stage ? 'count' : 'amount'],
                  })),
                  ...(!stage &&
                  data.current.totalAmount >
                    data.current.network.amount +
                      data.current.digital.amount +
                      data.current.intelligent.amount
                    ? [
                        {
                          label: '未分配分类金额',
                          value:
                            data.current.totalAmount -
                            data.current.network.amount -
                            data.current.digital.amount -
                            data.current.intelligent.amount,
                        },
                      ]
                    : []),
                ]}
              />
              <p className="subtle">
                {stage
                  ? '总项目数去重；类别分别计数，同一项目可重复参与，占比按类别参与次数计算。'
                  : '分类预计金额与未分配金额共同构成项目总金额；金额单位为万元。'}
              </p>
            </section>
            {stage ? (
              <section className="panel">
                <h2>产品分布概览{stageLabel}</h2>
                <div className="dashboard-product-grid">
                  <div>
                    <h3>按项目数</h3>
                    <DashboardDonut
                      label="项目总数"
                      total={data.current.projectCount}
                      unit="个"
                      items={dashboardProducts.map((item) => ({
                        label: item.label,
                        value: data.current[item.key].count,
                      }))}
                    />
                  </div>
                  <div>
                    <h3>按产品金额</h3>
                    <DashboardDonut
                      label="产品分类金额"
                      total={dashboardProducts.reduce(
                        (sum, item) => sum + data.current[item.key].amount,
                        0,
                      )}
                      unit="万元"
                      items={dashboardProducts.map((item) => ({
                        label: item.label,
                        value: data.current[item.key].amount,
                      }))}
                    />
                  </div>
                </div>
                <p className="subtle">
                  产品项目数允许重复，占比按产品参与次数计算；数字化项目数已在格物与大数据之间去重。
                </p>
              </section>
            ) : (
              <section className="panel">
                <h2>阶段分布概览（项目数／金额）</h2>
                <div className="dashboard-category-stages">
                  {dashboardCategories.map((item) => (
                    <article key={item.key}>
                      <header>
                        <h3>
                          {item.label} <small>（{item.product}）</small>
                        </h3>
                        <strong>
                          {data.current[item.key].count} 个 <span>｜</span>{' '}
                          {amount(data.current[item.key].amount)} 万元
                        </strong>
                      </header>
                      <table>
                        <thead>
                          <tr>
                            <th>项目阶段</th>
                            <th>项目数</th>
                            <th>金额（万元）</th>
                            <th>金额占比</th>
                          </tr>
                        </thead>
                        <tbody>
                          {data.stages.map((point) => (
                            <tr key={point.stageCode}>
                              <th>
                                <span
                                  className={`stage-chip stage-${point.stageCode.toLowerCase()}`}
                                >
                                  {stageName(point.stageCode)}
                                </span>
                              </th>
                              <td>{point.summary[item.key].count}</td>
                              <td>{amount(point.summary[item.key].amount)}</td>
                              <td>
                                <div className="dashboard-share">
                                  <span
                                    style={{
                                      width: percent(
                                        point.summary[item.key].amount,
                                        data.current[item.key].amount,
                                      ),
                                    }}
                                  />
                                  <small>
                                    {percent(
                                      point.summary[item.key].amount,
                                      data.current[item.key].amount,
                                    )}
                                  </small>
                                </div>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </article>
                  ))}
                </div>
              </section>
            )}
          </div>
        </>
      )}
      <div className="dashboard-section-toolbar">
        <h2>区域经营分布{stageLabel}</h2>
        <DashboardFilters
          value={regionFilter}
          onChange={setRegionFilter}
          metric={regionMetric}
          onMetricChange={setRegionMetric}
        />
      </div>
      {hasRegionFilter && regions.error && <Feedback tone="error">{regions.error}</Feedback>}
      {regions.busy && !regions.data && <Loading />}
      {regions.data && (
        <DashboardRegionPanel data={regions.data} metric={regionMetric} stageLabel={stageLabel} />
      )}
      <section className="panel dashboard-trend">
        <div className="dashboard-panel-heading">
          <h2>趋势分析{stageLabel}</h2>
          <DashboardFilters
            value={trendFilter}
            onChange={setTrendFilter}
            metric={trendMetric}
            onMetricChange={setTrendMetric}
          />
        </div>
        <p className="subtle">
          {range.from} 至 {range.to} · 左轴：
          {trendMetric === 'amount' ? '三化金额（万元）' : '分类项目数（可重复）'}
          ；右轴：去重项目数；各月按唯一所属月份计数，不是月末存量。
        </p>
        {hasTrendFilter && trend.error && <Feedback tone="error">{trend.error}</Feedback>}
        {trend.busy && !trend.data && <Loading />}
        {trend.data && <DashboardTrend data={trend.data} metric={trendMetric} stacked={!!stage} />}
      </section>
      <DashboardActivities
        key={`${filter.from}:${filter.to}`}
        filter={filter}
        stageLabel={stageLabel}
      />
      <div className="dashboard-advanced">
        <button
          className="secondary-button"
          aria-expanded={advanced}
          onClick={() => setAdvanced((value) => !value)}
        >
          {advanced ? '收起高级分析' : '展开高级分析：历史月报、地图下钻与行业分析'}
        </button>
        {advanced && (
          <Suspense fallback={<Loading />}>
            <AdvancedWorkbench reference={reference} />
          </Suspense>
        )}
      </div>
    </section>
  );
}
