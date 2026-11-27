import { useState } from 'react';
import {
  amountDescription,
  formatMoney,
  formatRate,
  monthLabel,
  type OverviewMonth,
} from './overview';

const ticks = 6;
function tickStep(span: number) {
  if (span <= 0) return 1;
  const ideal = span / ticks;
  const magnitude = 10 ** Math.floor(Math.log10(ideal));
  const step = [1, 2, 2.5, 5, 10].find((factor) => factor * magnitude >= ideal) ?? 10;
  return Math.max(0.01, step * magnitude);
}

export function CockpitChart({
  months,
  increments = false,
}: {
  months: OverviewMonth[];
  increments?: boolean;
}) {
  const [active, setActive] = useState<string | null>(null);
  const points = (increments ? months.filter((row) => row.plannedIncrease !== null) : months).map(
    (row) => ({
      row,
      planned: Number(increments ? row.plannedIncrease : row.amounts.planned) / 10000,
      recognized: Number(increments ? row.recognizedIncrease : row.amounts.recognized) / 10000,
      rate: row.amounts.rate === null ? null : Number(row.amounts.rate),
    }),
  );
  const width = increments ? 433 : 860;
  const height = increments ? 223 : 318;
  const left = increments ? 39 : 43;
  const right = width - (increments ? 9 : 50);
  const top = increments ? 14 : 24;
  const bottom = height - 29;
  const limits = points.reduce(
    ({ min, max, minRate, maxRate }, point) => ({
      min: Math.min(min, point.planned, point.recognized),
      max: Math.max(max, point.planned, point.recognized),
      minRate: Math.min(minRate, point.rate ?? 0),
      maxRate: Math.max(maxRate, point.rate ?? 0),
    }),
    { min: 0, max: 0, minRate: 0, maxRate: 100 },
  );
  const { min: rawMin, max: rawMax } = limits;
  const step = tickStep(rawMax - rawMin);
  const min = Math.floor(rawMin / step) * step;
  const max = rawMax || rawMin ? Math.ceil(rawMax / step) * step : step;
  const rateMin = Math.floor(limits.minRate / 25) * 25;
  const rateMax = Math.ceil(limits.maxRate / 25) * 25;
  const y = (amount: number) => bottom - ((amount - min) / (max - min)) * (bottom - top);
  const rateY = (rate: number) =>
    bottom - ((rate - rateMin) / (rateMax - rateMin)) * (bottom - top);
  const slot = (right - left) / Math.max(1, points.length);
  const bar = Math.min(slot * 0.31, increments ? 20 : 22);
  const chartTicks = Array.from(
    { length: Math.round((max - min) / step) + 1 },
    (_, i) => min + i * step,
  );
  const yZero = y(0);
  const crossesYear = points.at(0)?.row.month.slice(0, 4) !== points.at(-1)?.row.month.slice(0, 4);
  const labelEvery = Math.ceil(points.length / (increments ? 10 : 14));
  const activePoint = points.find((point) => point.row.month === active);

  const label = (row: OverviewMonth) =>
    increments
      ? `${row.month} · 排产增量 ${formatMoney(row.plannedIncrease!, '万')} 万元 · 计收增量 ${formatMoney(row.recognizedIncrease!, '万')} 万元`
      : row.month + ' · ' + amountDescription(row.amounts);

  return (
    <>
      <div className="sc-chart-legend" aria-hidden="true">
        <span>
          <i className="sc-series-plan" />
          {increments ? '排产增量' : '排产金额'}
        </span>
        <span>
          <i className={increments ? 'sc-series-rate' : 'sc-series-revenue'} />
          {increments ? '计收增量' : '计收金额'}
        </span>
        {!increments && (
          <span>
            <i className="sc-series-rate" />
            兑现率
          </span>
        )}
      </div>
      {points.length === 0 ? (
        <p className="sc-empty-chart">区间内没有可比较的上月。</p>
      ) : (
        <div className="sc-chart">
          <svg
            viewBox={`0 0 ${width} ${height}`}
            role="group"
            aria-label={increments ? '月度增量柱状图' : '月度金额柱状图与兑现率折线图'}
          >
            {!increments && (
              <>
                <text x={0} y={10}>
                  金额（亿元）
                </text>
                <text x={width - 2} y={10} textAnchor="end">
                  兑现率
                </text>
              </>
            )}
            {chartTicks.map((value, i) => (
              <g key={i}>
                <line
                  x1={left}
                  x2={right}
                  y1={y(value)}
                  y2={y(value)}
                  className={Math.abs(value / step) < 0.00001 ? 'sc-zero-line' : 'sc-gridline'}
                />
                <text x={left - 6} y={y(value) + 3} textAnchor="end">
                  {value.toLocaleString('zh-CN', { maximumFractionDigits: 2 })}
                </text>
              </g>
            ))}
            {!increments &&
              Array.from({ length: 6 }, (_, i) => rateMin + (i * (rateMax - rateMin)) / 5).map(
                (value) => (
                  <text key={value} x={right + 7} y={rateY(value) + 3}>
                    {+value.toFixed(1)}%
                  </text>
                ),
              )}
            {!increments && (
              <path
                d={points
                  .map((point, i) =>
                    point.rate === null
                      ? ''
                      : `${i && points[i - 1].rate !== null ? 'L' : 'M'}${left + slot * (i + 0.5)},${rateY(point.rate)}`,
                  )
                  .join(' ')}
                className="sc-rate-line"
              />
            )}
            {points.map(({ row, planned, recognized, rate }, i) => {
              const x = left + slot * (i + 0.5);
              const bars = [
                {
                  value: planned,
                  amount: row.amounts.planned,
                  x: x - bar - 1,
                  color: 'sc-series-plan',
                },
                {
                  value: recognized,
                  amount: row.amounts.recognized,
                  x: x + 1,
                  color: increments ? 'sc-series-rate' : 'sc-series-revenue',
                },
              ];
              return (
                <g
                  key={row.month}
                  tabIndex={0}
                  aria-label={label(row)}
                  onPointerEnter={() => setActive(row.month)}
                  onPointerLeave={() => setActive(null)}
                  onFocus={() => setActive(row.month)}
                  onBlur={() => setActive(null)}
                >
                  <rect
                    x={left + slot * i}
                    y={top}
                    width={slot}
                    height={bottom - top}
                    fill={active === row.month ? '#e9edf6' : 'transparent'}
                    fillOpacity={0.55}
                    className="sc-chart-hit"
                  />
                  {bars.map(({ value, amount, x: barX, color }) => (
                    <g key={color} pointerEvents="none">
                      <rect
                        x={barX}
                        y={Math.min(y(value), yZero)}
                        width={bar}
                        height={Math.abs(y(value) - yZero)}
                        rx="2"
                        className={color}
                      />
                      {!increments && points.length <= 12 && value > 0 && yZero - y(value) > 23 && (
                        <text
                          className="sc-bar-label"
                          x={barX + bar / 2}
                          y={yZero - 11}
                          textAnchor="middle"
                        >
                          {formatMoney(amount)}
                        </text>
                      )}
                    </g>
                  ))}
                  {!increments && rate !== null && (
                    <>
                      <circle
                        cx={x}
                        cy={rateY(rate)}
                        r={points.length > 24 ? 2 : 4}
                        className="sc-rate-point"
                      />
                      {points.length <= 12 && (
                        <text
                          className="sc-rate-label"
                          x={x}
                          y={rate < 0 ? rateY(rate) - 10 : Math.min(rateY(rate) - 10, yZero - 25)}
                          textAnchor="middle"
                        >
                          {formatRate(row.amounts.rate)}
                        </text>
                      )}
                    </>
                  )}
                  {(i % labelEvery === 0 || (i === points.length - 1 && labelEvery === 1)) && (
                    <text x={x} y={height - 9} textAnchor="middle">
                      {monthLabel(row.month, crossesYear)}
                    </text>
                  )}
                </g>
              );
            })}
          </svg>
          {activePoint && (
            <div className="sc-chart-tooltip" role="tooltip">
              {label(activePoint.row)}
            </div>
          )}
        </div>
      )}
    </>
  );
}
