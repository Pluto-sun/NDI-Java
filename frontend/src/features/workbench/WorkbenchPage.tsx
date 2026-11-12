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
  type DashboardFilter,
} from './dashboardApi';
import { StageTrendChart } from './StageTrendChart';
import { Select } from '../../shared/ui/Select';
import { DashboardMonthRange } from './DashboardMonthRange';
import { DashboardFilters, type ClassificationFilter } from './DashboardFilters';
import { DashboardRegionPanel } from './DashboardRegionPanel';
import { DashboardActivities } from './DashboardActivities';
import { DashboardShare } from './DashboardShare';
import { StageDistributionOverview } from './StageDistributionOverview';
import './dashboard.css';

const AdvancedWorkbench = lazy(() =>
  import('./AdvancedWorkbench').then((module) => ({ default: module.AdvancedWorkbench })),
);

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
  const [regionRange, setRegionRange] = useState({ from, to });
  const [trendRange, setTrendRange] = useState({ from, to });
  const [includeTerminated, setIncludeTerminated] = useState(false);
  const activityFilter = useMemo(
    () => ({ from: `${currentMonth.slice(0, 4)}-01`, to: currentMonth, stage, includeTerminated }),
    [currentMonth, stage, includeTerminated],
  );
  const [regionFilter, setRegionFilter] = useState<ClassificationFilter>({
    category: '',
    product: '',
  });
  const [trendProduct, setTrendProduct] =
    useState<(typeof dashboardProducts)[number]['code']>('GEWU');
  const [regionMetric, setRegionMetric] = useState<'amount' | 'count'>('amount');
  const [trendMetric, setTrendMetric] = useState<'amount' | 'count'>('amount');
  const [advanced, setAdvanced] = useState(false);
  const filter = useMemo<DashboardFilter>(
    () => ({ ...range, stage, includeTerminated }),
    [range, stage, includeTerminated],
  );
  const load = useCallback(
    (signal: AbortSignal) => dashboardApi.view({ ...filter, part: 'OVERVIEW' }, signal),
    [filter],
  );
  const loadRegions = useCallback(
    (signal: AbortSignal) =>
      dashboardApi.view(
        { ...regionRange, stage, includeTerminated, ...regionFilter, part: 'REGIONS' },
        signal,
      ),
    [regionRange, stage, includeTerminated, regionFilter],
  );
  const loadTrend = useCallback(
    (signal: AbortSignal) =>
      dashboardApi.view(
        { ...trendRange, stage, includeTerminated, product: trendProduct, part: 'TREND' },
        signal,
      ),
    [trendRange, stage, includeTerminated, trendProduct],
  );
  const main = useLiveQuery(load, '驾驶舱加载失败');
  const regions = useLiveQuery(loadRegions, '区域分布加载失败');
  const trend = useLiveQuery(loadTrend, '趋势分析加载失败');
  const data = main.data;
  const stageName = (code: string) =>
    reference.stages.find((item) => item.code === code)?.label ?? code;
  const cards = data
    ? [
        {
          label: '全国项目数及总金额',
          count: data.current.projectCount,
          total: data.current.totalAmount,
        },
        ...dashboardCategories.map((item) => ({
          label: `${item.label}项目数及总金额`,
          count: data.current[item.key].count,
          total: data.current[item.key].amount,
        })),
      ]
    : [];
  const trendStages = stage
    ? [stage]
    : PROJECT_STAGE_TABS.filter((item) => item.code).map((item) => item.code);
  const monthSources = Object.fromEntries(
    (trend.data?.trend ?? []).map((month) => [
      month.month,
      month.source === 'LIVE'
        ? '当月实时'
        : month.source === 'PARTIAL'
          ? '缺快照（部分补录）'
          : month.source === 'MISSING'
            ? '缺快照'
            : '',
    ]),
  );
  const trendPoints = trend.data?.trend.flatMap((month) =>
    trendStages.map((stageCode) => {
      const product = month.productStages?.find((item) => item.stageCode === stageCode);
      return {
        month: month.month,
        stageCode,
        projectCount: product?.count ?? 0,
        totalExpectedAmount: product?.amount ?? 0,
        categoryTotals: { network: 0, gewu: 0, bigData: 0, wangwu: 0 },
      };
    }),
  );
  return (
    <section className="project-dashboard">
      <div className="page-heading dashboard-page-heading project-stage-heading">
        <div>
          <div className="title-with-stage">
            <h1>项目管理驾驶舱</h1>
            <ProjectStageTabs
              value={stage}
              onChange={(value) => setSearchParams(value ? { stage: value } : {})}
            />
          </div>
          <p>工业互联网三化项目经营驾驶舱 · {stage ? stageName(stage) : '全部阶段'}</p>
        </div>
        <Link className="secondary-button" to={`/projects${stage ? `?stage=${stage}` : ''}`}>
          查看项目清单
        </Link>
      </div>
      <section className="dashboard-overview" aria-label="经营总览">
        <div className="dashboard-toolbar">
          <form
            className="dashboard-inline-filters"
            aria-label="经营总览时间筛选"
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
              void regions.refresh();
              void trend.refresh();
            }}
          >
            刷新驾驶舱
          </button>
        </div>
        <p className="subtle dashboard-scope">
          {range.from} 至 {range.to} ·
          项目唯一所属月份，区间不重复累计；本时间范围仅控制经营总览。每 30 秒刷新。
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
            <div className="dashboard-kpis" aria-label="当前经营指标">
              {cards.map((card) => (
                <article className="dashboard-kpi" key={card.label}>
                  <span>{card.label}</span>
                  <div className="dashboard-kpi-pair">
                    <div>
                      <small>项目数（个）</small>
                      <strong>{card.count}</strong>
                    </div>
                    <div>
                      <small>项目总金额（万元）</small>
                      <strong>{amount(card.total)}</strong>
                    </div>
                  </div>
                </article>
              ))}
            </div>
            {stage ? (
              <StageDistributionOverview summary={data.current} stageLabel={stageLabel} />
            ) : (
              <section className="panel dashboard-stage-distribution">
                <h2>阶段分布概览（项目数／金额）</h2>
                <div className="dashboard-category-stages">
                  {dashboardProducts.map((item) => (
                    <article key={item.code}>
                      <header>
                        <h3>{item.label}</h3>
                      </header>
                      <table aria-label={`${item.label}阶段分布`}>
                        <thead>
                          <tr>
                            <th>阶段</th>
                            <th>项目数</th>
                            <th>金额（万元）</th>
                            <th>金额占比</th>
                          </tr>
                        </thead>
                        <tbody>
                          {data.stages.map((point) => (
                            <tr key={point.stageCode}>
                              <th scope="row">
                                <span
                                  className={`stage-chip stage-${point.stageCode.toLowerCase()}`}
                                >
                                  {stageName(point.stageCode)}
                                </span>
                              </th>
                              <td>{point.summary[item.key].count}</td>
                              <td>{amount(point.summary[item.key].amount)}</td>
                              <td>
                                <DashboardShare
                                  value={point.summary[item.key].amount}
                                  total={data.current[item.key].amount}
                                />
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
          </>
        )}
      </section>
      <div className="dashboard-section-toolbar">
        <h2>区域经营分布{stageLabel}</h2>
        <DashboardMonthRange
          label="区域"
          value={regionRange}
          onApply={setRegionRange}
          busy={regions.busy}
        />
        <DashboardFilters
          value={regionFilter}
          onChange={setRegionFilter}
          metric={regionMetric}
          onMetricChange={setRegionMetric}
        />
      </div>
      {regions.error && <Feedback tone="error">{regions.error}</Feedback>}
      {regions.busy && !regions.data && <Loading />}
      {regions.data && (
        <DashboardRegionPanel data={regions.data} metric={regionMetric} stageLabel={stageLabel} />
      )}
      <section className="panel dashboard-trend">
        <div className="dashboard-panel-heading">
          <h2>趋势分析{stageLabel}</h2>
          <DashboardMonthRange
            label="趋势"
            value={trendRange}
            onApply={setTrendRange}
            busy={trend.busy}
          />
          <div className="dashboard-trend-controls">
            <label>
              产品类别
              <Select
                value={trendProduct}
                onChange={(event) => setTrendProduct(event.target.value as typeof trendProduct)}
              >
                {dashboardProducts.map((item) => (
                  <option key={item.code} value={item.code}>
                    {item.label}
                  </option>
                ))}
              </Select>
            </label>
            <label>
              指标
              <Select
                value={trendMetric}
                onChange={(event) => setTrendMetric(event.target.value as typeof trendMetric)}
              >
                <option value="amount">项目金额</option>
                <option value="count">项目数量</option>
              </Select>
            </label>
          </div>
        </div>
        {trend.error && <Feedback tone="error">{trend.error}</Feedback>}
        {trend.busy && !trendPoints && <Loading />}
        {trendPoints && (
          <StageTrendChart
            key={stage}
            points={trendPoints}
            stages={trendStages}
            stageName={stageName}
            metric={trendMetric === 'amount' ? 'totalExpectedAmount' : 'projectCount'}
            metricLabel={trendMetric === 'amount' ? '项目金额（万元）' : '项目数量（个）'}
            caption="月末快照趋势"
            monthSources={monthSources}
            interactive
          />
        )}
      </section>
      <DashboardActivities filter={activityFilter} stageLabel={stageLabel} />
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
