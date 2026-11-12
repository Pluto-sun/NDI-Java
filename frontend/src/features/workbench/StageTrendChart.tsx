import { useState } from 'react';
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
  interactive = false,
  monthSources = {},
  caption = '各月月末项目阶段指标明细',
}: {
  points: StageTrendPoint[];
  stages: string[];
  stageName: (code: string) => string;
  metric: StageTrendMetric;
  metricLabel: string;
  caption?: string;
  interactive?: boolean;
  monthSources?: Record<string, string>;
}) {
  const [hidden, setHidden] = useState<string[]>([]);
  if (!points.length || !stages.length) return <div className="empty-state">暂无趋势数据</div>;
  const months = [...new Set(points.map((point) => point.month))];
  const indexed = new Map(points.map((point) => [`${point.month}/${point.stageCode}`, point]));
  const metricAt = (month: string, stage: string) => {
    const point = indexed.get(`${month}/${stage}`);
    return point ? value(point, metric) : 0;
  };
  const missing = (month: string) => monthSources[month]?.startsWith('缺快照');
  const visible = stages.filter((stage) => !interactive || !hidden.includes(stage));
  const observedMaximum = Math.max(
    1,
    ...points
      .filter((point) => visible.includes(point.stageCode) && !missing(point.month))
      .map((point) => value(point, metric)),
  );
  const maximum =
    interactive && metric === 'projectCount' ? Math.ceil(observedMaximum / 4) * 4 : observedMaximum;
  const tickFormat = new Intl.NumberFormat('zh-CN', {
    notation: 'compact',
    maximumFractionDigits: 1,
  });
  const ticks = interactive ? Array.from({ length: 5 }, (_, i) => (maximum * i) / 4) : [0, maximum];
  const x = (index: number) =>
    48 + (months.length === 1 ? 280 : (index / (months.length - 1)) * 544);
  const y = (number: number) => 182 - (number / maximum) * 142;
  const formatted = (number: number) =>
    metric === 'projectCount' ? `${number} 个` : `${amount(number)} 万元`;

  const table = (
    <div className="stage-trend-table-wrap">
      <table className="stage-trend-table">
        <caption>
          {caption}：{metricLabel}
        </caption>
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
              <th scope="row">
                {month} {monthSources[month]}
              </th>
              {stages.map((stage) => {
                return (
                  <td key={stage}>{missing(month) ? '—' : formatted(metricAt(month, stage))}</td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
  return (
    <div className="stage-trend-result">
      <div className="stage-trend-legend">
        {stages.map((stage) => {
          const className = `stage-trend-key stage-trend-${stage.toLowerCase()}`;
          return interactive ? (
            <button
              key={stage}
              type="button"
              className={className}
              aria-pressed={visible.includes(stage)}
              onClick={() =>
                setHidden((current) =>
                  current.includes(stage)
                    ? current.filter((item) => item !== stage)
                    : [...current, stage],
                )
              }
            >
              {stageName(stage)}
            </button>
          ) : (
            <span key={stage} className={className}>
              {stageName(stage)}
            </span>
          );
        })}
      </div>
      <div className="stage-trend-chart-scroll">
        <svg viewBox="0 0 640 220" className="stage-trend-svg" aria-hidden="true">
          {ticks.map((tick) => (
            <g key={tick}>
              <line
                x1="48"
                x2="592"
                y1={y(tick)}
                y2={y(tick)}
                className={tick === 0 ? 'stage-trend-axis' : 'stage-trend-grid'}
              />
              {interactive && (
                <text x="42" y={y(tick)} textAnchor="end" dominantBaseline="central">
                  {tickFormat.format(tick)}
                </text>
              )}
            </g>
          ))}
          <text x="48" y="28">
            {interactive ? metricLabel : `最大值 ${formatted(maximum)}`}
          </text>
          <text x="48" y="208">
            {months[0]}
          </text>
          <text x="592" y="208" textAnchor="end">
            {months[months.length - 1]}
          </text>
          {visible.map((stage) => (
            <g key={stage} className={`stage-trend-line stage-trend-${stage.toLowerCase()}`}>
              <path
                d={months
                  .map((month, index) =>
                    missing(month)
                      ? ''
                      : `${index === 0 || missing(months[index - 1]) ? 'M' : 'L'}${x(index)},${y(metricAt(month, stage))}`,
                  )
                  .join(' ')}
              />
              {(interactive || months.length === 1) &&
                months.map(
                  (month, index) =>
                    !missing(month) && (
                      <circle
                        key={month}
                        cx={x(index)}
                        cy={y(metricAt(month, stage))}
                        r="3"
                        fill="currentColor"
                      >
                        <title>
                          {month} {stageName(stage)} {formatted(metricAt(month, stage))}
                        </title>
                      </circle>
                    ),
                )}
            </g>
          ))}
        </svg>
      </div>
      {Object.entries(monthSources).some(([, source]) => source) && (
        <ul className="stage-trend-legend">
          {Object.entries(monthSources)
            .filter(([, source]) => source)
            .map(([month, source]) => (
              <li key={month}>
                {month} {source}
              </li>
            ))}
        </ul>
      )}
      {interactive ? (
        <details className="stage-trend-data">
          <summary>查看趋势明细</summary>
          {table}
        </details>
      ) : (
        table
      )}
    </div>
  );
}
