import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { projectsApi } from '../projects/api';
import type {
  MonthlyWorkbench,
  ReferenceData,
  TrendPoint,
  WorkbenchBreakdown,
  WorkbenchOverview,
} from '../projects/types';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { amount } from '../../shared/ui/format';
import { ApiError } from '../../shared/api/http';
import { MetricBars } from './MetricBars';
import { ProjectMapCard } from './ProjectMapCard';

export function WorkbenchPage({ reference }: { reference: ReferenceData }) {
  const [overview, setOverview] = useState<WorkbenchOverview | null>(null);
  const [breakdown, setBreakdown] = useState<WorkbenchBreakdown | null>(null);
  const [error, setError] = useState('');
  const [month, setMonth] = useState(() => {
    const date = new Date();
    date.setMonth(date.getMonth() - 1);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
  });
  const [includeTerminated, setIncludeTerminated] = useState(false);
  const [history, setHistory] = useState<MonthlyWorkbench | null>(null);
  const [trend, setTrend] = useState<TrendPoint[]>([]);
  const [historyError, setHistoryError] = useState('');
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
    setHistory(null);
    setTrend([]);
    try {
      const snapshot = await projectsApi.monthly(month, includeTerminated);
      setHistory(snapshot);
      for (let offset = 5; offset >= 0; offset -= 1) {
        const start = new Date(`${month}-01T12:00:00`);
        start.setMonth(start.getMonth() - offset);
        const from = `${start.getFullYear()}-${String(start.getMonth() + 1).padStart(2, '0')}`;
        try {
          setTrend(await projectsApi.trend(from, month, includeTerminated));
          break;
        } catch (cause) {
          if (
            !(cause instanceof ApiError) ||
            cause.code !== 'PROJECT_SNAPSHOT_NOT_READY' ||
            offset === 0
          )
            throw cause;
        }
      }
    } catch (cause) {
      setHistoryError(cause instanceof Error ? cause.message : '历史月报加载失败');
    } finally {
      setHistoryBusy(false);
    }
  }
  const stage = (code: string) =>
    reference.stages.find((item) => item.code === code)?.label ?? code;
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
              每月 1 日生成上个月月末快照；跨月审批通过后的修订计入相应历史月份。
            </p>
            <div className="filter-bar">
              <label>
                报表月份
                <input
                  type="month"
                  value={month}
                  onChange={(event) => setMonth(event.target.value)}
                />
              </label>
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={includeTerminated}
                  onChange={(event) => setIncludeTerminated(event.target.checked)}
                />
                含已终止项目
              </label>
              <button
                type="button"
                className="secondary-button"
                disabled={!month || historyBusy}
                onClick={() => void loadHistory()}
              >
                {historyBusy ? '加载中…' : '查看月报'}
              </button>
            </div>
            {historyError && <Feedback tone="error">{historyError}</Feedback>}
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
                  <div>
                    <h3>近六个月趋势</h3>
                    <MetricBars
                      items={trend.map((item) => ({
                        key: item.month,
                        label: item.month,
                        count: item.projectCount,
                        expectedAmount: item.totalExpectedAmount,
                      }))}
                    />
                  </div>
                </div>
              </>
            )}
          </div>
        </>
      )}
    </section>
  );
}
