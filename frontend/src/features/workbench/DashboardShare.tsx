import { percent } from './dashboardApi';

/** 数值与轨道分列，填充宽度始终相对于完整轨道计算。 */
export function DashboardShare({ value, total }: { value: number; total: number }) {
  const ratio = total > 0 ? Math.max(0, Math.min(100, (value / total) * 100)) : 0;
  return (
    <div className="dashboard-share">
      <span className="dashboard-share-track" aria-hidden="true">
        <span className="dashboard-share-fill" style={{ width: `${ratio}%` }} />
      </span>
      <small>{percent(value, total)}</small>
    </div>
  );
}
