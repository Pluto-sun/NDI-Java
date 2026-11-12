import { amount } from '../../shared/ui/format';
import { percent } from './dashboardApi';

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
