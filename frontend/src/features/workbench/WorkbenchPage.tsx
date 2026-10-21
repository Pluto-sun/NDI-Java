import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { projectsApi } from '../projects/api';
import type {
  MonthlyWorkbench,
  ReferenceData,
  StageTrendPoint,
  WorkbenchBreakdown,
  WorkbenchOverview,
} from '../projects/types';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { amount } from '../../shared/ui/format';
import { MetricBars } from './MetricBars';
import { ProjectMapCard } from './ProjectMapCard';
import { StageTrendChart, type StageTrendMetric } from './StageTrendChart';

export function WorkbenchPage({ reference }: { reference: ReferenceData }) {
  const [overview, setOverview] = useState<WorkbenchOverview | null>(null);
  const [breakdown, setBreakdown] = useState<WorkbenchBreakdown | null>(null);
  const [error, setError] = useState('');
  const [month, setMonth] = useState(() => {
    const date = new Date();
    date.setMonth(date.getMonth() - 1);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
  });
  const [fromMonth, setFromMonth] = useState(month);
  const [includeTerminated, setIncludeTerminated] = useState(false);
  const [selectedStages, setSelectedStages] = useState<string[]>(['SOLUTION_EXCHANGE']);
  const [trendMetric, setTrendMetric] = useState<StageTrendMetric>('projectCount');
  const [history, setHistory] = useState<MonthlyWorkbench | null>(null);
  const [trend, setTrend] = useState<StageTrendPoint[]>([]);
  const [loadedTrend, setLoadedTrend] = useState<{
    from: string;
    to: string;
    stages: string[];
  } | null>(null);
  const [historyError, setHistoryError] = useState('');
  const [trendError, setTrendError] = useState('');
  const [historyBusy, setHistoryBusy] = useState(false);
  useEffect(() => {
    let active = true;
    Promise.all([projectsApi.overview(), projectsApi.breakdown()])
      .then(([summary, groups]) => {
        if (active) {
          setOverview(summary);
          setBreakdown(groups);
        }
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '工作台加载失败');
      });
    return () => {
      active = false;
    };
  }, []);
  async function loadHistory() {
    setHistoryBusy(true);
    setHistoryError('');
    setTrendError('');
    setHistory(null);
    setTrend([]);
    setLoadedTrend(null);
    if (!selectedStages.length || fromMonth > month) {
      setHistoryError('请选择至少一个阶段，且起始月份不得晚于报表月份。');
      setHistoryBusy(false);
      return;
    }
    try {
      const snapshot = await projectsApi.monthly(month, includeTerminated);
      setHistory(snapshot);
      try {
        setTrend(await projectsApi.stageTrend(fromMonth, month, includeTerminated, selectedStages));
        setLoadedTrend({ from: fromMonth, to: month, stages: [...selectedStages] });
      } catch (cause) {
        setTrendError(cause instanceof Error ? cause.message : '阶段趋势加载失败');
      }
    } catch (cause) {
      setHistoryError(cause instanceof Error ? cause.message : '历史月报加载失败');
    } finally {
      setHistoryBusy(false);
    }
  }
  const stage = (code: string) =>
    reference.stages.find((item) => item.code === code)?.label ?? code;
  const trendMetrics: { value: StageTrendMetric; label: string }[] = [
    { value: 'projectCount', label: '项目数量' },
    { value: 'totalExpectedAmount', label: '项目总金额' },
    { value: 'network', label: '网络化金额' },
    { value: 'gewu', label: '格物金额' },
    { value: 'bigData', label: '大数据金额' },
    { value: 'wangwu', label: '万悟金额' },
  ];
  const categoryAmounts = history
    ? [
        { code: 'NETWORK', value: history.categoryTotals.network },
        { code: 'GEWU', value: history.categoryTotals.gewu },
        { code: 'BIG_DATA', value: history.categoryTotals.bigData },
        { code: 'WANGWU', value: history.categoryTotals.wangwu },
      ]
    : [];
  const largestCategory = Math.max(1, ...categoryAmounts.map((item) => item.value));
  return (
    <section>
      <div className="page-heading">
        <div>
          <span className="eyebrow">WORKBENCH</span>
          <h1>项目工作台</h1>
          <p>默认排除已终止项目，展示当前项目经营概览。</p>
        </div>
        <Link className="secondary-button" to="/projects">
          查看项目清单
        </Link>
      </div>
      {error && <Feedback tone="error">{error}</Feedback>}
      {!overview || !breakdown ? (
        !error && <Loading />
      ) : (
        <>
          <div className="kpi-grid">
            <div className="kpi">
              <span>项目总数</span>
              <strong>{overview.projectCount}</strong>
              <small>个有效项目</small>
            </div>
            <div className="kpi">
              <span>预计总金额</span>
              <strong>{amount(overview.totalExpectedAmount)}</strong>
              <small>万元</small>
            </div>
            <div className="kpi">
              <span>待我审批</span>
              <strong>{overview.approvals.pendingCount}</strong>
              <small>其中逾期 {overview.approvals.overdueCount} 条</small>
            </div>
          </div>
          <ProjectMapCard reference={reference} />
          <div className="detail-grid">
            <div className="panel">
              <h2>阶段分布</h2>
              <MetricBars
                items={overview.stages.map((item) => ({
                  key: item.stageCode,
                  label: stage(item.stageCode),
                  count: item.projectCount,
                  expectedAmount: item.expectedAmount,
                }))}
              />
            </div>
            <div className="panel">
              <h2>行业分布（前八项）</h2>
              <MetricBars
                items={breakdown.industries.slice(0, 8).map((item) => ({
                  key: item.industryCode,
                  label: item.industryName,
                  count: item.projectCount,
                  expectedAmount: item.expectedAmount,
                }))}
              />
            </div>
          </div>
          <div className="panel">
            <h2>地市分布</h2>
            <MetricBars
              items={breakdown.cities.map((item) => ({
                key: item.cityId,
                label: item.cityName,
                count: item.projectCount,
                expectedAmount: item.expectedAmount,
              }))}
            />
          </div>
          <div className="panel">
            <h2>历史月报与趋势</h2>
            <p className="subtle">
              每月 1 日生成上个月月末快照；趋势中的每个月都是当月月末状态，不跨月累加项目金额。
              跨月审批通过后，会修订对应历史月份。
            </p>
            <div className="filter-bar">
              <label>
                趋势起始月份
                <input
                  type="month"
                  value={fromMonth}
                  max={month}
                  onChange={(event) => setFromMonth(event.target.value)}
                />
              </label>
              <label>
                报表／趋势结束月份
                <input
                  type="month"
                  value={month}
                  min={fromMonth}
                  onChange={(event) => setMonth(event.target.value)}
                />
              </label>
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={includeTerminated}
                  onChange={(event) => {
                    setIncludeTerminated(event.target.checked);
                    if (!event.target.checked) {
                      setSelectedStages((items) => items.filter((item) => item !== 'TERMINATED'));
                    }
                  }}
                />
                含已终止项目
              </label>
              <button
                type="button"
                className="secondary-button"
                disabled={!month || !fromMonth || historyBusy}
                onClick={() => void loadHistory()}
              >
                {historyBusy ? '加载中…' : '查看月报与趋势'}
              </button>
            </div>
            <div className="stage-trend-filters">
              <fieldset>
                <legend>趋势阶段</legend>
                {reference.stages
                  .filter((item) => includeTerminated || item.code !== 'TERMINATED')
                  .map((item) => (
                    <button
                      key={item.code}
                      type="button"
                      aria-pressed={selectedStages.includes(item.code)}
                      onClick={() =>
                        setSelectedStages((items) =>
                          items.includes(item.code)
                            ? items.filter((code) => code !== item.code)
                            : [...items, item.code],
                        )
                      }
                    >
                      {item.label}
                    </button>
                  ))}
              </fieldset>
              <label>
                趋势指标
                <select
                  value={trendMetric}
                  onChange={(event) => setTrendMetric(event.target.value as StageTrendMetric)}
                >
                  {trendMetrics.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <p className="subtle">
              修改月份、阶段或终止口径后，请点击“查看月报与趋势”；指标切换会直接更新已加载图表。
            </p>
            {historyError && <Feedback tone="error">{historyError}</Feedback>}
            {trendError && <Feedback tone="error">趋势暂不可用：{trendError}</Feedback>}
            {history && (
              <>
                <div className="kpi-grid">
                  <div className="kpi">
                    <span>{history.month} 项目数</span>
                    <strong>{history.projectCount}</strong>
                    <small>个项目</small>
                  </div>
                  <div className="kpi">
                    <span>预计总金额</span>
                    <strong>{amount(history.totalExpectedAmount)}</strong>
                    <small>万元</small>
                  </div>
                </div>
                <div className="category-summary">
                  <h3>当月三化分类预计金额</h3>
                  <p className="subtle">分类金额合计不超过项目总金额，未归类金额不在下列四项中。</p>
                  <ol className="category-amount-bars">
                    {categoryAmounts.map((item) => (
                      <li key={item.code}>
                        <span>
                          {reference.categories.find((category) => category.code === item.code)
                            ?.label ?? item.code}
                        </span>
                        <div className="category-amount-track" aria-hidden="true">
                          <span style={{ width: `${(item.value / largestCategory) * 100}%` }} />
                        </div>
                        <strong>{amount(item.value)} 万元</strong>
                      </li>
                    ))}
                  </ol>
                </div>
                <div className="detail-grid">
                  <div>
                    <h3>当月阶段分布</h3>
                    <MetricBars
                      items={history.stages.map((item) => ({
                        key: item.stageCode,
                        label: stage(item.stageCode),
                        count: item.projectCount,
                        expectedAmount: item.expectedAmount,
                      }))}
                    />
                  </div>
                </div>
                <h3>月度阶段趋势</h3>
                {loadedTrend && (
                  <p className="subtle">
                    已加载 {loadedTrend.from} 至 {loadedTrend.to} 的月末状态。
                  </p>
                )}
                <StageTrendChart
                  points={trend}
                  stages={loadedTrend?.stages ?? []}
                  stageName={stage}
                  metric={trendMetric}
                  metricLabel={
                    trendMetrics.find((item) => item.value === trendMetric)?.label ?? trendMetric
                  }
                />
              </>
            )}
          </div>
        </>
      )}
    </section>
  );
}
