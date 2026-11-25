import { useState } from 'react';
import { formatMoney } from '../scheduling-cockpit/overview';
import type { AnnualOverview, Region } from './annualOverview';

const pageSize = 6;
const metrics = ['签约数', '计收数', '数量差距', '签约金额', '计收金额', '金额差距'];

function Amount({ value }: { value: string }) {
  return <td title={formatMoney(value, '万') + ' 万元'}>{formatMoney(value)}</td>;
}

export function RevenueRanking({ regions }: { regions: Region[] }) {
  const ranked = regions
    .filter((row) => row.hasRevenue && (row.geographic || row.organization))
    .slice(0, 10);
  const maximum = Math.max(1, ...ranked.map((row) => Number(row.total.total)));
  return (
    <table className="sc-data rc-ranking">
      <thead>
        <tr>
          <th scope="col">排名</th>
          <th scope="col">省分 / 组织</th>
          <th scope="col">累计计收金额</th>
        </tr>
      </thead>
      <tbody>
        {ranked.map((row, index) => (
          <tr key={row.key}>
            <td>{index + 1}</td>
            <th scope="row" title={row.name}>
              {row.name}
            </th>
            <td title={formatMoney(row.total.total, '万') + ' 万元'}>
              <div className="rc-rank-value">
                <span className="rc-rank-track" aria-hidden="true">
                  <i
                    style={{ width: Math.max(0, (Number(row.total.total) / maximum) * 100) + '%' }}
                  />
                </span>
                <span>{formatMoney(row.total.total)}</span>
              </div>
            </td>
          </tr>
        ))}
        {!ranked.length && (
          <tr>
            <td colSpan={3}>本年度暂无可排名的计收数据</td>
          </tr>
        )}
      </tbody>
    </table>
  );
}

export function RevenueDetails({ data }: { data: AnnualOverview }) {
  const [page, setPage] = useState(0);
  const pages = Math.max(1, Math.ceil(data.regions.length / pageSize));
  const active = Math.min(page, pages - 1);
  const visible = data.regions.slice(active * pageSize, (active + 1) * pageSize);
  return (
    <>
      <table className="sc-data rc-details">
        <thead>
          <tr>
            <th rowSpan={2} scope="col">
              省分 / 组织
            </th>
            <th colSpan={13} scope="colgroup">
              三化计收（亿元）
            </th>
            <th colSpan={6} scope="colgroup">
              格物签约与计收差距（个 / 亿元）
            </th>
          </tr>
          <tr>
            {data.months.map(({ month }) => (
              <th scope="col" key={month}>
                {month}月
              </th>
            ))}
            <th scope="col">累计</th>
            {metrics.map((metric) => (
              <th key={metric} scope="col">
                {metric}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {visible.map((row) => (
            <tr key={row.key}>
              <th scope="row" title={row.name}>
                {row.name}
              </th>
              {row.months.map((month) =>
                month.occurred ? (
                  <Amount key={month.month} value={month.money.total} />
                ) : (
                  <td key={month.month} className="rc-future" title="未发生">
                    —
                  </td>
                ),
              )}
              <Amount value={row.total.total} />
              {row.gewu ? (
                <>
                  <td>{row.gewu.signedCount}</td>
                  <td>{row.gewu.recognizedCount}</td>
                  <td>{row.gewu.countGap}</td>
                  <Amount value={row.gewu.signedAmount} />
                  <Amount value={row.gewu.recognizedAmount} />
                  <Amount value={row.gewu.amountGap} />
                </>
              ) : (
                metrics.map((metric) => <td key={metric} />)
              )}
            </tr>
          ))}
          {!visible.length && (
            <tr>
              <td colSpan={20}>当前可见范围内暂无年度数据</td>
            </tr>
          )}
        </tbody>
      </table>
      {pages > 1 && (
        <div className="sc-table-footer">
          <span>{data.regions.length} 个归属</span>
          <nav aria-label="计收明细分页">
            <button
              type="button"
              aria-label="上一页"
              disabled={active === 0}
              onClick={() => setPage(active - 1)}
            >
              ‹
            </button>
            <span aria-live="polite">
              {active + 1} / {pages}
            </span>
            <button
              type="button"
              aria-label="下一页"
              disabled={active === pages - 1}
              onClick={() => setPage(active + 1)}
            >
              ›
            </button>
          </nav>
        </div>
      )}
    </>
  );
}
