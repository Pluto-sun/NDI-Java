import { useId } from 'react';
import { amount } from '../../shared/ui/format';
import { dashboardCategories, percent, type Dashboard } from './dashboardApi';

const colors = ['var(--red)', '#df6b76', '#efb6bd', '#875374', '#a4a8b0'];
export function DashboardDonut({
  items,
  label,
  total,
  unit,
}: {
  items: { label: string; value: number }[];
  label: string;
  total: number;
  unit: string;
}) {
  const sum = items.reduce((value, item) => value + item.value, 0);
  let offset = 0;
  return (
    <div className="dashboard-donut">
      <svg viewBox="0 0 200 200" role="img" aria-label={`${label} ${amount(total)} ${unit}`}>
        <circle cx="100" cy="100" r="76" fill="none" stroke="var(--line)" strokeWidth="30" />
        {items.map((item, index) => {
          const share = sum ? item.value / sum : 0;
          const start = offset;
          offset += share;
          return (
            <circle
              key={item.label}
              cx="100"
              cy="100"
              r="76"
              pathLength="100"
              fill="none"
              stroke={colors[index % colors.length]}
              strokeWidth="30"
              strokeDasharray={`${share * 100} ${100 - share * 100}`}
              strokeDashoffset={-start * 100}
              transform="rotate(-90 100 100)"
            >
              <title>
                {item.label}：{amount(item.value)} {unit}（{percent(item.value, sum)}）
              </title>
            </circle>
          );
        })}
        <text x="100" y="86" textAnchor="middle" className="donut-caption">
          {label}
        </text>
        <text x="100" y="115" textAnchor="middle" className="donut-total">
          {amount(total)}
        </text>
        <text x="100" y="137" textAnchor="middle" className="donut-caption">
          {unit}
        </text>
      </svg>
      <ul className="dashboard-legend">
        {items.map((item, index) => (
          <li key={item.label}>
            <i style={{ background: colors[index % colors.length] }} aria-hidden="true" />
            <span>{item.label}</span>
            <strong>{amount(item.value)}</strong>
            <small>{percent(item.value, sum)}</small>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function DashboardTrend({
  data,
  metric,
  stacked,
}: {
  data: Dashboard;
  metric: 'amount' | 'count';
  stacked: boolean;
}) {
  const titleId = useId();
  const groups = data.trend;
  const maximum = Math.max(
    1,
    ...groups.map((point) => {
      const values = dashboardCategories.map((item) => point.summary[item.key][metric]);
      return stacked ? values.reduce((a, b) => a + b, 0) : Math.max(...values);
    }),
  );
  const countMax = Math.max(
    4,
    Math.ceil(Math.max(0, ...groups.map((point) => point.summary.projectCount)) / 4) * 4,
  );
  const width = Math.max(680, groups.length * 72);
  const plot = width - 100,
    step = plot / Math.max(1, groups.length);
  const line = groups
    .map(
      (point, index) =>
        `${55 + step * (index + 0.5)},${170 - (point.summary.projectCount / countMax) * 140}`,
    )
    .join(' ');
  return (
    <>
      <div className="dashboard-chart-legend">
        {dashboardCategories.map((item, index) => (
          <span key={item.key}>
            <i style={{ background: colors[index] }} />
            {item.label}
          </span>
        ))}
        <span>○ 合计项目数（右轴）</span>
      </div>
      <div className="dashboard-chart-scroll">
        <svg
          viewBox={`0 0 ${width} 215`}
          role="img"
          aria-labelledby={titleId}
          style={{ minWidth: 680 }}
        >
          <title id={titleId}>
            逐月三化{metric === 'amount' ? '金额' : '分类项目数量'}与合计项目数趋势
          </title>
          {[0, 1, 2, 3, 4].map((index) => (
            <g key={index}>
              <line
                x1="50"
                x2={width - 45}
                y1={170 - index * 35}
                y2={170 - index * 35}
                stroke="var(--line)"
              />
              <text x="44" y={174 - index * 35} textAnchor="end" className="chart-label">
                {amount((maximum * index) / 4)}
              </text>
              <text x={width - 38} y={174 - index * 35} className="chart-label">
                {Math.round((countMax * index) / 4)}
              </text>
            </g>
          ))}
          {groups.map((point, index) => {
            let base = 0;
            return (
              <g key={point.month}>
                {dashboardCategories.map((item, categoryIndex) => {
                  const value = point.summary[item.key][metric],
                    height = (value / maximum) * 140;
                  const y = 170 - (stacked ? base + height : height);
                  base += height;
                  return (
                    <rect
                      key={item.key}
                      x={55 + step * index + (stacked ? step * 0.2 : categoryIndex * step * 0.24)}
                      y={y}
                      width={stacked ? step * 0.6 : step * 0.2}
                      height={height}
                      rx="1"
                      fill={colors[categoryIndex]}
                    >
                      <title>
                        {point.month} {item.label}：{amount(value)}{' '}
                        {metric === 'amount' ? '万元' : '个'}
                      </title>
                    </rect>
                  );
                })}
                <text
                  x={55 + step * (index + 0.5)}
                  y="197"
                  textAnchor="middle"
                  className="chart-label"
                >
                  {point.month}
                </text>
              </g>
            );
          })}
          <polyline points={line} fill="none" stroke="var(--red-dark)" strokeWidth="2" />
          {groups.map((point, index) => (
            <circle
              key={point.month}
              cx={55 + step * (index + 0.5)}
              cy={170 - (point.summary.projectCount / countMax) * 140}
              r="3"
              fill="white"
              stroke="var(--red-dark)"
            >
              <title>
                {point.month} 合计 {point.summary.projectCount} 个项目
              </title>
            </circle>
          ))}
        </svg>
      </div>
      <details className="dashboard-data-table">
        <summary>查看趋势明细数据</summary>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>月份</th>
                {dashboardCategories.map((item) => (
                  <th key={item.key}>{item.label}</th>
                ))}
                <th>合计项目数</th>
              </tr>
            </thead>
            <tbody>
              {groups.map((point) => (
                <tr key={point.month}>
                  <th>{point.month}</th>
                  {dashboardCategories.map((item) => (
                    <td key={item.key}>{amount(point.summary[item.key][metric])}</td>
                  ))}
                  <td>{point.summary.projectCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </>
  );
}
