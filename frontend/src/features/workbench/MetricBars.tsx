import { amount } from '../../shared/ui/format';

interface MetricItem {
  key: string;
  label: string;
  count: number;
  expectedAmount: number;
}

/** 可视长度仅用于比较，数值和单位始终以文字呈现。 */
export function MetricBars({ items }: { items: MetricItem[] }) {
  if (items.length === 0) return <div className="empty-state">暂无统计数据</div>;
  const maximum = Math.max(1, ...items.map((item) => item.count));
  return (
    <ol className="metric-bars">
      {items.map((item) => (
        <li key={item.key}>
          <div className="metric-bars-heading">
            <span>{item.label}</span>
            <strong>{item.count} 个</strong>
          </div>
          <div className="metric-bars-track" aria-hidden="true">
            <span style={{ width: `${(item.count / maximum) * 100}%` }} />
          </div>
          <small>{amount(item.expectedAmount)} 万元</small>
        </li>
      ))}
    </ol>
  );
}
