import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { projectsApi } from '../projects/api';
import type { ReferenceData, WorkbenchBreakdown, WorkbenchOverview } from '../projects/types';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { amount } from '../../shared/ui/format';

export function WorkbenchPage({ reference }: { reference: ReferenceData }) {
  const [overview, setOverview] = useState<WorkbenchOverview | null>(null);
  const [breakdown, setBreakdown] = useState<WorkbenchBreakdown | null>(null);
  const [error, setError] = useState('');
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
          <div className="detail-grid">
            <div className="panel">
              <h2>阶段分布</h2>
              <div className="metric-list">
                {overview.stages.map((item) => (
                  <div key={item.stageCode}>
                    <span>{stage(item.stageCode)}</span>
                    <strong>{item.projectCount} 个</strong>
                    <small>{amount(item.expectedAmount)} 万元</small>
                  </div>
                ))}
              </div>
            </div>
            <div className="panel">
              <h2>行业分布</h2>
              <div className="metric-list">
                {breakdown.industries.slice(0, 8).map((item) => (
                  <div key={item.industryCode}>
                    <span>{item.industryName}</span>
                    <strong>{item.projectCount} 个</strong>
                    <small>{amount(item.expectedAmount)} 万元</small>
                  </div>
                ))}
              </div>
              {breakdown.industries.length === 0 && <div className="empty-state">暂无统计数据</div>}
            </div>
          </div>
          <div className="panel">
            <h2>地市分布</h2>
            <div className="metric-list">
              {breakdown.cities.map((item) => (
                <div key={item.cityId}>
                  <span>{item.cityName}</span>
                  <strong>{item.projectCount} 个</strong>
                  <small>{amount(item.expectedAmount)} 万元</small>
                </div>
              ))}
            </div>
            {breakdown.cities.length === 0 && <div className="empty-state">暂无统计数据</div>}
          </div>
        </>
      )}
    </section>
  );
}
