import { revenueAmount, revenueCardAmount } from './format';
import { CockpitBoard, Panel } from '../../shared/ui/CockpitBoard';
import { useLiveQuery } from '../../shared/api/useLiveQuery';
import { CockpitMap } from '../scheduling-cockpit/CockpitRegions';
import { formatRate } from '../scheduling-cockpit/overview';
import monitor from '../scheduling-cockpit/assets/monitor.svg';
import database from '../scheduling-cockpit/assets/database.svg';
import calendar from './assets/calendar-days.svg';
import network from '../scheduling-cockpit/assets/radio-tower.svg';
import digital from '../scheduling-cockpit/assets/layers.svg';
import intelligent from '../scheduling-cockpit/assets/brain-circuit.svg';
import provinces from './assets/map-pin.svg';
import { RevenueTrend } from './RevenueTrend';
import { RevenueDetails, RevenueRanking } from './RevenueTables';
import { loadAnnualOverview, type AnnualOverview, type Gewu } from './annualOverview';
import './revenue-cockpit.css';

const mapPalette = [
  { below: 120000, label: '< 120,000', color: '#f8d3d6' },
  { below: 250000, label: '120,000–250,000', color: '#f3aab1' },
  { below: 450000, label: '250,000–450,000', color: '#e17685' },
  { below: 700000, label: '450,000–700,000', color: '#d44358' },
  { below: Infinity, label: '≥ 700,000', color: '#b92034' },
];

const count = (value: number) => value.toLocaleString('zh-CN');
const scope = { NATIONAL: '全国', PROVINCE: '可见省分', CITY: '本账号地市' };

function Metrics({ data }: { data: AnnualOverview }) {
  const metrics = [
    {
      label: (data.nationalRevenue ? '全国' : '可见范围') + '三化累计计收金额',
      icon: database,
      size: 30,
      value: revenueCardAmount(data.total.total),
    },
    { label: '网络化累计计收金额', icon: network, value: revenueCardAmount(data.total.network) },
    { label: '数字化累计计收金额', icon: digital, value: revenueCardAmount(data.total.digital) },
    {
      label: '智能化累计计收金额',
      icon: intelligent,
      value: revenueCardAmount(data.total.intelligent),
    },
    {
      label: '本月计收金额',
      icon: calendar,
      value: revenueCardAmount(data.months[data.currentMonth - 1].money.total),
    },
    { label: '覆盖省份数', icon: provinces, value: count(data.coveredProvinces), unit: '个' },
  ];
  return (
    <section className="sc-metrics" aria-label="年度计收指标">
      {metrics.map(({ label, icon, value, unit = '千万元', size = 24 }) => (
        <div className="sc-metric" key={label}>
          <div className="sc-metric-icon">
            <img
              className={icon === database ? undefined : 'rc-light-icon'}
              src={icon}
              alt=""
              width={size}
              height={size}
            />
          </div>
          <div className="sc-metric-body">
            <h2>{label}</h2>
            <div className="sc-metric-reading" title={value + ' ' + unit}>
              <strong>{value}</strong>
              <span>{unit}</span>
            </div>
          </div>
        </div>
      ))}
    </section>
  );
}

function GewuComparisons({ value }: { value: Gewu }) {
  const metrics = [
    {
      label: '项目数量差距',
      signed: value.signedCount,
      recognized: value.recognizedCount,
      gap: count(value.countGap),
      count: true,
      rate: value.countGapRate,
    },
    {
      label: '项目金额差距',
      signed: value.signedAmount,
      recognized: value.recognizedAmount,
      gap: revenueAmount(value.amountGap),
      count: false,
      rate: value.amountGapRate,
    },
  ];
  return (
    <div className="rc-gewu">
      {metrics.map((metric) => {
        const maximum = Math.max(1, Number(metric.signed), Number(metric.recognized));
        const unit = metric.count ? '个' : '万元';
        return (
          <article key={metric.label}>
            <div className="rc-gewu-series">
              <h3>{metric.label}</h3>
              <dl>
                {[metric.signed, metric.recognized].map((amount, index) => (
                  <div key={index}>
                    <dt>{index === 0 ? '格物签约' : '格物计收'}</dt>
                    <dd>
                      <span className="rc-gewu-track" aria-hidden="true">
                        <i style={{ width: (Number(amount) / maximum) * 100 + '%' }} />
                      </span>
                      <strong>
                        {typeof amount === 'number' ? count(amount) : revenueAmount(amount)}{' '}
                        <small>{unit}</small>
                      </strong>
                    </dd>
                  </div>
                ))}
              </dl>
            </div>
            <div className="rc-gap">
              <span>差距</span>
              <strong>
                {metric.gap} <small>{unit}</small>
              </strong>
              <span>{formatRate(metric.rate)}</span>
            </div>
          </article>
        );
      })}
    </div>
  );
}

export default function RevenueCockpit() {
  const { data, error, refresh } = useLiveQuery(loadAnnualOverview, '计收驾驶舱数据加载失败。');
  return (
    <CockpitBoard label="计收驾驶舱" testId="revenue-board" className="rc-board" height={944}>
      <header className="sc-header">
        <img src={monitor} alt="" width={32} height={32} />
        <h1>工业“三化”计收与格物经营驾驶舱</h1>
        {data && (
          <div className="rc-year">
            <strong>统计年度 {data.year} 年</strong>
            <span>费用账期</span>
          </div>
        )}
      </header>
      {data ? (
        <>
          <Metrics data={data} />
          <div className="rc-middle">
            <Panel
              title="各省三化累计计收金额分布"
              note="累计计收金额 · 万元"
              className="rc-map-panel"
            >
              <CockpitMap
                provinces={data.regions
                  .filter((row) => row.geographic && row.hasRevenue)
                  .map((row) => ({
                    adcode: row.adcode,
                    name: row.name,
                    total: { planned: '0', recognized: row.total.total, gap: '0', rate: null },
                  }))}
                nationwide={data.nationalRevenue}
                mapLabel="三化累计计收省分地图"
                palette={mapPalette}
                mapAmountDivisor={1}
                describe={(row) => '累计计收 ' + revenueAmount(row.total.recognized) + ' 万元'}
              />
            </Panel>
            <div className="rc-analysis">
              <Panel title="1–12 月三化计收趋势" note="万元">
                <RevenueTrend months={data.months} />
              </Panel>
              <Panel title="格物签约与计收差距分析">
                <GewuComparisons value={data.gewu} />
              </Panel>
            </div>
          </div>
          <div className="rc-bottom">
            <Panel title="累计计收金额 TOP 10" note="万元">
              <RevenueRanking regions={data.regions} />
            </Panel>
            <Panel title="各省各月份计收及格物差距明细">
              <RevenueDetails data={data} />
            </Panel>
          </div>
          <footer className="sc-footer rc-footer">
            <span>
              按费用账期与生效认定统计　计收范围 {data.nationalRevenue ? '全国' : '当前可见范围'}
              　格物项目范围 {scope[data.projectScope]}
            </span>
            {error && <span role="status">{error}</span>}
            {data.conflicts.length > 0 && (
              <details>
                <summary>集成归属冲突 {data.conflicts.length} 个合同</summary>
                <div className="rc-conflicts">
                  <p>冲突合同的签约数据在主归属省分统计一次。</p>
                  {data.conflicts.map((item) => (
                    <p key={item.contract}>
                      {item.contract} · {item.regions.join('、')}
                    </p>
                  ))}
                </div>
              </details>
            )}
          </footer>
        </>
      ) : (
        <div className="sc-status" role={error ? 'alert' : 'status'}>
          <p>{error || '正在加载年度总览…'}</p>
          {error && (
            <button className="secondary-button" onClick={() => void refresh()}>
              重试
            </button>
          )}
        </div>
      )}
    </CockpitBoard>
  );
}
