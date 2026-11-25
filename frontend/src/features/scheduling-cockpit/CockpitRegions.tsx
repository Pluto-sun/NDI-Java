import { Fragment, useEffect, useState } from 'react';
import { projectsApi } from '../projects/api';
import { mapShapes } from '../workbench/mapGeometry';
import {
  amountDescription,
  formatMoney,
  formatRate,
  monthLabel,
  type Amounts,
  type Overview,
  type OverviewProvince,
} from './overview';

const bands = [
  { below: 0.2, label: '< 0.20', color: '#f8d3d6' },
  { below: 0.5, label: '0.20–0.49', color: '#f3aab1' },
  { below: 1, label: '0.50–0.99', color: '#e17685' },
  { below: 1.5, label: '1.00–1.49', color: '#d44358' },
  { below: Infinity, label: '≥ 1.50', color: '#b92034' },
];

const coastLabels: Record<string, [number, number]> = {
  '110000': [23, -12],
  '120000': [29, 7],
  '310000': [30, 10],
};

type MapProvince = Pick<OverviewProvince, 'adcode' | 'name' | 'total'>;

export function CockpitMap({
  provinces,
  nationwide,
  mapLabel,
  describe = (region) => amountDescription(region.total),
  palette = bands,
}: {
  provinces: MapProvince[];
  nationwide: boolean;
  mapLabel?: string;
  describe?: (region: MapProvince) => string;
  palette?: typeof bands;
}) {
  const [shapes, setShapes] = useState<ReturnType<typeof mapShapes>>([]);
  const [boundary, setBoundary] = useState<'loading' | 'ready' | 'error'>('loading');
  const [active, setActive] = useState<MapProvince | null>(null);
  useEffect(() => {
    let current = true;
    projectsApi
      .mapBoundary('100000')
      .then(async (blob) => mapShapes(JSON.parse(await blob.text()), true))
      .then((result) => {
        if (!current) return;
        setShapes(result);
        setBoundary(result.length ? 'ready' : 'error');
      })
      .catch(() => {
        if (current) setBoundary('error');
      });
    return () => {
      current = false;
    };
  }, []);
  const byCode = new Map(provinces.map((province) => [province.adcode, province]));
  const represented = new Set(shapes.map((shape) => shape.adcode));
  const offMap = provinces.filter(
    (row) => !row.adcode || (boundary === 'ready' && !represented.has(row.adcode)),
  );
  const label = active ? active.name + ' · ' + describe(active) : '';
  return (
    <>
      <div className="sc-map">
        {boundary === 'ready' ? (
          <svg
            viewBox="14 0 652 440"
            role="group"
            aria-label={mapLabel ?? (nationwide ? '全国' : '可见范围') + '省分排产计收地图'}
          >
            {shapes.map((shape) => {
              const row = byCode.get(shape.adcode);
              const amount = Number(row?.total.recognized ?? 0) / 10000;
              const accessible = row ? row.name + ' · ' + describe(row) : shape.name;
              return (
                <path
                  key={shape.adcode}
                  d={shape.path}
                  aria-label={accessible}
                  tabIndex={row ? 0 : undefined}
                  fill={
                    row || nationwide
                      ? palette.find((band) => amount < band.below)?.color
                      : '#e5ebf4'
                  }
                  fillRule="evenodd"
                  onPointerEnter={() => setActive(row ?? null)}
                  onPointerLeave={() => setActive(null)}
                  onFocus={() => setActive(row ?? null)}
                  onBlur={() => setActive(null)}
                />
              );
            })}
            {shapes
              .filter((shape) => shape.labelX && shape.labelY)
              .map((shape) => {
                const shift = coastLabels[shape.adcode];
                const x = shape.labelX + (shift?.[0] ?? 0);
                const y = shape.labelY + (shift?.[1] ?? 0);
                return (
                  <g key={shape.adcode} aria-hidden="true">
                    {shift && (
                      <line
                        x1={shape.labelX}
                        y1={shape.labelY}
                        x2={x - 10}
                        y2={y - 3}
                        className="sc-map-label-line"
                      />
                    )}
                    <text x={x} y={y}>
                      {shape.name.replace(/(?:维吾尔|壮族|回族)?自治区$|[省市]$/g, '')}
                    </text>
                  </g>
                );
              })}
          </svg>
        ) : (
          <p>
            {boundary === 'loading' ? '正在加载省界' : '省界暂不可用，统计数据可在下方表格查看。'}
          </p>
        )}
        {active && (
          <div className="sc-chart-tooltip" role="tooltip">
            {label}
          </div>
        )}
      </div>
      <div className="sc-map-legend">
        <strong>累计计收金额</strong>
        {palette.map((band) => (
          <span key={band.label}>
            <i style={{ background: band.color }} />
            {band.label}
          </span>
        ))}
      </div>
      {offMap.length > 0 && (
        <div className="sc-map-note" title={offMap.map((row) => row.name).join('、')}>
          未上图归属详见月度表
        </div>
      )}
    </>
  );
}

export function ProvinceRanking({ provinces }: { provinces: OverviewProvince[] }) {
  const ranked = provinces.filter((row) => row.province).slice(0, 10);
  return (
    <table className="sc-data sc-ranking">
      <thead>
        <tr>
          {['排名', '省分', '排产', '计收', '兑现率', '差额'].map((title) => (
            <th key={title} scope="col">
              {title}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {ranked.map(({ key, name, total }, i) => (
          <tr key={key}>
            <td>{String(i + 1).padStart(2, '0')}</td>
            <th scope="row" title={name}>
              {name}
            </th>
            <td title={formatMoney(total.planned, '万') + ' 万元'}>{formatMoney(total.planned)}</td>
            <td title={formatMoney(total.recognized, '万') + ' 万元'}>
              {formatMoney(total.recognized)}
            </td>
            <td>{formatRate(total.rate)}</td>
            <td title={formatMoney(total.gap, '万') + ' 万元'}>{formatMoney(total.gap)}</td>
          </tr>
        ))}
        {!ranked.length && (
          <tr>
            <td colSpan={6}>区间内暂无省分记录</td>
          </tr>
        )}
      </tbody>
    </table>
  );
}

const pageSize = 5;
const monthlyLabels = ['排产', '计收', '差额'];
const cumulativeLabels = ['排产', '计收', '兑现率'];

function MonthlyCells({ value, cumulative = false }: { value: Amounts; cumulative?: boolean }) {
  return (
    <>
      <td title={formatMoney(value.planned, '万')}>{formatMoney(value.planned, '万')}</td>
      <td title={formatMoney(value.recognized, '万')}>{formatMoney(value.recognized, '万')}</td>
      <td title={cumulative ? formatRate(value.rate) : formatMoney(value.gap, '万')}>
        {cumulative ? formatRate(value.rate) : formatMoney(value.gap, '万')}
      </td>
    </>
  );
}

export function ProvinceMonthly({ overview }: { overview: Overview }) {
  const [page, setPage] = useState(0);
  const { months, provinces, total, productionScope, revenueScope } = overview;
  const pages = Math.max(1, Math.ceil(provinces.length / pageSize));
  const visiblePage = Math.min(page, pages - 1);
  const recent = months.slice(-3);
  const crossesYear = months[0].month.slice(0, 4) !== months[months.length - 1].month.slice(0, 4);
  const current = provinces.slice(visiblePage * pageSize, (visiblePage + 1) * pageSize);
  return (
    <>
      <table className="sc-data sc-monthly">
        <thead>
          <tr>
            <th rowSpan={2} scope="col">
              省分
            </th>
            {recent.map(({ month }) => (
              <th key={month} colSpan={3} scope="colgroup">
                {monthLabel(month, crossesYear)}
              </th>
            ))}
            <th colSpan={3} scope="colgroup">
              区间累计
            </th>
          </tr>
          <tr>
            {recent.map(({ month }) => (
              <Fragment key={month}>
                {monthlyLabels.map((label) => (
                  <th key={label} scope="col">
                    {label}
                  </th>
                ))}
              </Fragment>
            ))}
            {cumulativeLabels.map((label) => (
              <th key={label} scope="col">
                {label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          <tr className="sc-grand-total">
            <th scope="row">
              {productionScope === 'NATIONAL' && revenueScope === 'NATIONAL' ? '全国' : '可见合计'}
            </th>
            {recent.map(({ month, amounts }) => (
              <MonthlyCells key={month} value={amounts} />
            ))}
            <MonthlyCells value={total} cumulative />
          </tr>
          {current.map((row) => (
            <tr key={row.key}>
              <th scope="row" title={row.name}>
                {row.name}
              </th>
              {row.months.map((amounts, i) => (
                <MonthlyCells key={recent[i].month} value={amounts} />
              ))}
              <MonthlyCells value={row.total} cumulative />
            </tr>
          ))}
        </tbody>
      </table>
      <div className="sc-table-footer">
        <span>展示区间最后 {recent.length} 个月及区间累计</span>
        {pages > 1 && (
          <nav aria-label="省分月度表分页">
            <button
              type="button"
              aria-label="省分月度表上一页"
              disabled={visiblePage === 0}
              onClick={() => setPage(visiblePage - 1)}
            >
              ‹
            </button>
            <span aria-live="polite">
              {visiblePage + 1} / {pages}
            </span>
            <button
              type="button"
              aria-label="省分月度表下一页"
              disabled={visiblePage + 1 === pages}
              onClick={() => setPage(visiblePage + 1)}
            >
              ›
            </button>
          </nav>
        )}
      </div>
    </>
  );
}
