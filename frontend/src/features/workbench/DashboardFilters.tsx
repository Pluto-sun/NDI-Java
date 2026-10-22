import { dashboardProducts } from './dashboardApi';

export interface ClassificationFilter {
  category: string;
  product: string;
}
export function DashboardFilters({
  value,
  onChange,
  metric,
  onMetricChange,
}: {
  value: ClassificationFilter;
  onChange: (value: ClassificationFilter) => void;
  metric: 'amount' | 'count';
  onMetricChange: (value: 'amount' | 'count') => void;
}) {
  const available = dashboardProducts.filter(
    (item) =>
      !value.category ||
      (value.category === 'NETWORK' && item.code === 'NETWORK') ||
      (value.category === 'DIGITAL' && ['GEWU', 'BIG_DATA'].includes(item.code)) ||
      (value.category === 'INTELLIGENT' && item.code === 'WANGWU'),
  );
  return (
    <div className="dashboard-inline-filters">
      <label>
        三化类别
        <select
          value={value.category}
          onChange={(event) => onChange({ category: event.target.value, product: '' })}
        >
          <option value="">全部</option>
          <option value="NETWORK">网络化</option>
          <option value="DIGITAL">数字化</option>
          <option value="INTELLIGENT">智能化</option>
        </select>
      </label>
      <label>
        产品
        <select
          value={value.product}
          onChange={(event) => onChange({ ...value, product: event.target.value })}
        >
          <option value="">全部</option>
          {available.map((item) => (
            <option key={item.code} value={item.code}>
              {item.label}
            </option>
          ))}
        </select>
      </label>
      <label>
        指标维度
        <select
          value={metric}
          onChange={(event) => onMetricChange(event.target.value as 'amount' | 'count')}
        >
          <option value="amount">项目金额（万元）</option>
          <option value="count">项目数（个）</option>
        </select>
      </label>
    </div>
  );
}
