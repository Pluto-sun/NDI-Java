import type { StageTrendPoint } from '../projects/types';
import { amount } from '../../shared/ui/format';

export type StageTrendMetric =
  'projectCount' | 'totalExpectedAmount' | 'network' | 'gewu' | 'bigData' | 'wangwu';

function value(point: StageTrendPoint, metric: StageTrendMetric) {
  if (metric === 'projectCount') return point.projectCount;
  if (metric === 'totalExpectedAmount') return point.totalExpectedAmount;
  return point.categoryTotals[metric];
}

/** 图形用于观察走势，表格始终提供完整的月份、阶段与数值。 */
export function StageTrendChart({
  points,
  stages,
  stageName,
  metric,
  metricLabel,
}: {
  points: StageTrendPoint[];
  stages: string[];
  stageName: (code: string) => string;
  metric: StageTrendMetric;
  metricLabel: string;
}) {
  if (!points.length || !stages.length) return <div className="empty-state">暂无趋势数据</div>;
  const months = [...new Set(points.map((point) => point.month))];
  const indexed = new Map(points.map((point) => [`${point.month}/${point.stageCode}`, point]));
  const metricAt = (month: string, stage: string) => {
    const point = indexed.get(`${month}/${stage}`);
    return point ? value(point, metric) : 0;
  };
  const maximum = Math.max(1, ...points.map((point) => value(point, metric)));
  const x = (index: number) =>
    48 + (months.length === 1 ? 280 : (index / (months.length - 1)) * 544);
  const y = (number: number) => 182 - (number / maximum) * 142;
  const formatted = (number: number) =>
    metric === 'projectCount' ? `${number} 个` : `${amount(number)} 万元`;

  return (
    <div className="stage-trend-result">
      <div className="stage-trend-legend">
        {stages.map((stage) => (
          <span key={stage} className={`stage-trend-key stage-trend-${stage.toLowerCase()}`}>
            {stageName(stage)}
          </span>
        ))}
      </div>
      <svg viewBox="0 0 640 220" className="stage-trend-svg" aria-hidden="true">
        <line x1="48" x2="592" y1="182" y2="182" className="stage-trend-axis" />
        <line x1="48" x2="592" y1="40" y2="40" className="stage-trend-grid" />
        <text x="48" y="28">
          最大值 {formatted(maximum)}
        </text>
        <text x="48" y="208">
          {months[0]}
        </text>
        <text x="592" y="208" textAnchor="end">
          {months[months.length - 1]}
        </text>
        {stages.map((stage) => (
          <g key={stage} className={`stage-trend-line stage-trend-${stage.toLowerCase()}`}>
            <polyline
              points={months
                .map((month, index) => `${x(index)},${y(metricAt(month, stage))}`)
                .join(' ')}
            />
            {months.length === 1 && (
              <circle cx={x(0)} cy={y(metricAt(months[0], stage))} r="5" fill="currentColor" />
            )}
          </g>
        ))}
      </svg>
      <div className="stage-trend-table-wrap">
        <table className="stage-trend-table">
          <caption>各月月末项目阶段指标明细：{metricLabel}</caption>
          <thead>
            <tr>
              <th scope="col">月份</th>
              {stages.map((stage) => (
                <th key={stage} scope="col">
                  {stageName(stage)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {months.map((month) => (
              <tr key={month}>
                <th scope="row">{month}</th>
                {stages.map((stage) => {
                  return <td key={stage}>{formatted(metricAt(month, stage))}</td>;
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
