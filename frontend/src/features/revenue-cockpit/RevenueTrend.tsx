import { useState } from 'react';
import { revenueAmount } from './format';
import type { Month } from './annualOverview';

const series = [
  { code: 'network', label: '网络化', color: '#bb233b' },
  { code: 'digital', label: '数字化', color: '#5898d1' },
  { code: 'intelligent', label: '智能化', color: '#48b6b6' },
] as const;
const lineColor = '#193954';

export function RevenueTrend({ months }: { months: Month[] }) {
  const [active, setActive] = useState<number | null>(null);
  const maximum = Math.max(
    0,
    ...months.map(({ money }) =>
      Math.max(
        Number(money.total),
        series.reduce((sum, item) => sum + Number(money[item.code]), 0),
      ),
    ),
  );
  const magnitude = maximum ? 10 ** Math.floor(Math.log10(maximum / 5)) : 1000;
  const interval = Math.max(0.01, Math.ceil(maximum / (5 * magnitude)) * magnitude || magnitude);
  const min = Math.min(0, ...months.map(({ money }) => Number(money.total)));
  const floor = Math.floor(min / interval) * interval;
  const ceiling = interval * 5;
  const ticks = Array.from({ length: 6 }, (_, index) => {
    const amount = interval * index;
    return { amount, label: amount.toLocaleString('zh-CN', { maximumFractionDigits: 2 }) };
  });
  const axisLeft = Math.max(54, ...ticks.map(({ label }) => label.length * 6 + 8));
  const x = (index: number) => axisLeft + ((index + 0.5) * (954 - axisLeft)) / months.length;
  const y = (amount: number) => 146 - ((amount - floor) / (ceiling - floor)) * 126;
  const path = months
    .map(({ occurred, money }, index) =>
      occurred
        ? `${index && months[index - 1].occurred ? 'L' : 'M'}${x(index)},${y(Number(money.total))}`
        : '',
    )
    .join(' ');
  const detail = (row: Month) =>
    row.occurred
      ? `${row.month}月 · ${series.map((item) => item.label + ' ' + revenueAmount(row.money[item.code])).join(' · ')} · 合计 ${revenueAmount(row.money.total)} 万元`
      : `${row.month}月 · 未发生`;
  return (
    <>
      <div className="rc-legend">
        {series.map((item) => (
          <span key={item.code}>
            <i style={{ background: item.color }} />
            {item.label}
          </span>
        ))}
        <span>
          <i style={{ background: lineColor }} />
          三化合计
        </span>
      </div>
      <div className="rc-trend sc-chart">
        <svg viewBox="0 0 966 176" role="group" aria-label="三化计收堆叠柱与合计折线图">
          {ticks.map(({ amount, label }) => (
            <g key={amount}>
              <line className="sc-gridline" x1={axisLeft} x2={954} y1={y(amount)} y2={y(amount)} />
              <text x={axisLeft - 5} y={y(amount) + 3} textAnchor="end">
                {label}
              </text>
            </g>
          ))}
          {months.map((row, index) => {
            let bottom = 0;
            return (
              <g
                key={row.month}
                tabIndex={0}
                aria-label={detail(row)}
                onFocus={() => setActive(index)}
                onBlur={() => setActive(null)}
                onPointerEnter={() => setActive(index)}
                onPointerLeave={() => setActive(null)}
              >
                <rect
                  className="sc-chart-hit"
                  x={x(index) - 29}
                  y={15}
                  width={58}
                  height={152}
                  fill={active === index ? '#e7edf8' : 'transparent'}
                />
                {row.occurred &&
                  series.map((item) => {
                    const value = Number(row.money[item.code]);
                    const top = bottom + value;
                    const rect = (
                      <rect
                        key={item.code}
                        x={x(index) - 13}
                        width={26}
                        y={Math.min(y(top), y(bottom))}
                        height={Math.abs(y(bottom) - y(top))}
                        fill={item.color}
                        pointerEvents="none"
                      />
                    );
                    bottom = top;
                    return rect;
                  })}
                <text
                  x={x(index)}
                  y={163}
                  textAnchor="middle"
                  className={row.occurred ? '' : 'rc-future'}
                >
                  {row.month}月
                </text>
              </g>
            );
          })}
          <path d={path} className="sc-rate-line rc-total-line" pointerEvents="none" />
          {months.map(
            (row, i) =>
              row.occurred && (
                <g key={row.month} pointerEvents="none">
                  <circle
                    cx={x(i)}
                    cy={y(Number(row.money.total))}
                    r="3"
                    className="sc-rate-point rc-total-line"
                  />
                  <text
                    x={x(i)}
                    y={y(Number(row.money.total)) - 8}
                    textAnchor="middle"
                    className="rc-total-label"
                  >
                    {revenueAmount(row.money.total)}
                  </text>
                </g>
              ),
          )}
        </svg>
        {active !== null && (
          <div className="sc-chart-tooltip" role="tooltip">
            {detail(months[active])}
          </div>
        )}
      </div>
    </>
  );
}
