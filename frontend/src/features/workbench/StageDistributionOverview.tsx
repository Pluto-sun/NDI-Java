import { DashboardDonut } from './DashboardDonut';
import { dashboardCategories, dashboardProducts, type DashboardSummary } from './dashboardApi';

export function StageDistributionOverview({
  summary,
  stageLabel,
}: {
  summary: DashboardSummary;
  stageLabel: string;
}) {
  return (
    <div className="dashboard-distribution-grid">
      <section className="panel">
        <h2>项目分布概览{stageLabel}</h2>
        <DashboardDonut
          label="项目总数"
          total={summary.projectCount}
          unit="个"
          items={dashboardCategories.map(({ label, key }) => ({
            label,
            value: summary[key].count,
          }))}
        />
        <p className="subtle">
          总项目数按项目去重。同一项目可计入多个类别，占比按类别参与次数计算。
        </p>
      </section>
      <section className="panel">
        <h2>产品分布概览{stageLabel}</h2>
        <div className="dashboard-product-grid">
          <div>
            <h3>按项目数</h3>
            <DashboardDonut
              label="项目总数"
              total={summary.projectCount}
              unit="个"
              items={dashboardProducts.map(({ label, key }) => ({
                label,
                value: summary[key].count,
              }))}
            />
          </div>
          <div>
            <h3>按产品金额</h3>
            <DashboardDonut
              label="产品分类金额"
              total={dashboardProducts.reduce((sum, { key }) => sum + summary[key].amount, 0)}
              unit="万元"
              items={dashboardProducts.map(({ label, key }) => ({
                label,
                value: summary[key].amount,
              }))}
            />
          </div>
        </div>
        <p className="subtle">同一项目可计入多个产品，项目数占比按产品参与次数计算。</p>
      </section>
    </div>
  );
}
