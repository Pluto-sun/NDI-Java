import { revenueAmount } from './format';
import type { AnnualOverview, Region } from './annualOverview';

const metrics = ['签约数', '计收数', '数量差距', '签约金额', '计收金额', '金额差距'];

function Amount({ value }: { value: string }) {
  return <td title={revenueAmount(value) + ' 万元'}>{revenueAmount(value)}</td>;
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
            <td title={revenueAmount(row.total.total) + ' 万元'}>
              <div className="rc-rank-value">
                <span className="rc-rank-track" aria-hidden="true">
                  <i
                    style={{ width: Math.max(0, (Number(row.total.total) / maximum) * 100) + '%' }}
                  />
                </span>
                <span>{revenueAmount(row.total.total)}</span>
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
  return (
    <div className="rc-detail-scroll" role="region" aria-label="计收明细滚动区域" tabIndex={0}>
      <table className="sc-data rc-details">
        <thead>
          <tr>
            <th rowSpan={2} scope="col">
              省分 / 组织
            </th>
            <th colSpan={13} scope="colgroup">
              三化计收（万元）
            </th>
            <th colSpan={6} scope="colgroup">
              格物签约与计收差距（个 / 万元）
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
          {data.regions.map((row) => (
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
          {!data.regions.length && (
            <tr>
              <td colSpan={20}>当前可见范围内暂无年度数据</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
