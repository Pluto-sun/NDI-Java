import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ApiError } from '../../shared/api/http';
import { MonthPicker } from '../../shared/ui/MonthPicker';
import { businessDate } from '../../shared/ui/format';
import { CockpitChart } from './CockpitChart';
import { CockpitMap, ProvinceMonthly, ProvinceRanking } from './CockpitRegions';
import { fetchOverview, formatMoney, formatRate, type Overview } from './overview';
import monitor from './assets/monitor.svg';
import calendar from './assets/calendar.svg';
import database from './assets/database.svg';
import coins from './assets/coins.svg';
import chart from './assets/chart-no-axes-column-increasing.svg';
import clipboard from './assets/clipboard-check.svg';
import scaleIcon from './assets/scale.svg';
import gauge from './assets/gauge.svg';
import radio from './assets/radio-tower.svg';
import layers from './assets/layers.svg';
import brain from './assets/brain-circuit.svg';
import { CockpitBoard, Panel } from '../../shared/ui/CockpitBoard';

const title = '工业互联网“三化”排产与计收监控驾驶舱';
const monthPattern = /^(?:19\d{2}|[2-9]\d{3})-(?:0[1-9]|1[0-2])$/;
const scopeNames = { NATIONAL: '全国', PROVINCE: '本账号可见省分', CITY: '本账号可见地市' };
const categoryIcons = { 网络化: radio, 数字化: layers, 智能化: brain } as const;

function Metrics({ result }: { result: Overview }) {
  const { total } = result;
  const values = [
    {
      icon: database,
      label: '累计排产金额',
      value: formatMoney(total.planned),
      unit: '亿元',
      full: total.planned,
    },
    {
      icon: coins,
      label: '累计计收金额',
      value: formatMoney(total.recognized),
      unit: '亿元',
      full: total.recognized,
    },
    {
      icon: chart,
      label: '排产项目数',
      value: result.plannedProjects.toLocaleString('zh-CN'),
      unit: '个',
    },
    {
      icon: clipboard,
      label: '计收项目数',
      value: result.recognizedProjects.toLocaleString('zh-CN'),
      unit: '个',
    },
    {
      icon: scaleIcon,
      label: '排产计收差额',
      value: formatMoney(total.gap),
      unit: '亿元',
      full: total.gap,
    },
    { icon: gauge, label: '兑现率', value: formatRate(total.rate), unit: '' },
  ];
  return (
    <section className="sc-metrics" aria-label="累计指标">
      {values.map(({ icon, label, value, unit, full }) => (
        <div className="sc-metric" key={label}>
          <div className="sc-metric-icon">
            <img src={icon} alt="" width="30" height="30" />
          </div>
          <div className="sc-metric-body">
            <h2>{label}</h2>
            <div
              className="sc-metric-reading"
              title={full === undefined ? value : formatMoney(full, '万') + ' 万元'}
            >
              <strong>{value}</strong>
              <span>{unit}</span>
            </div>
          </div>
        </div>
      ))}
    </section>
  );
}

function Categories({ categories }: { categories: Overview['categories'] }) {
  return (
    <div className="sc-categories">
      {categories.map(({ name, amounts }) => (
        <article key={name}>
          <div className="sc-category-title">
            <span>
              <img
                src={categoryIcons[name as keyof typeof categoryIcons]}
                width="24"
                height="24"
                alt=""
              />
            </span>
            <h3>{name}</h3>
          </div>
          <dl>
            <div>
              <dt>排产</dt>
              <dd title={formatMoney(amounts.planned, '万') + ' 万元'}>
                {formatMoney(amounts.planned)}
              </dd>
            </div>
            <div>
              <dt>计收</dt>
              <dd title={formatMoney(amounts.recognized, '万') + ' 万元'}>
                {formatMoney(amounts.recognized)}
              </dd>
            </div>
            <div>
              <dt>兑现率</dt>
              <dd>{formatRate(amounts.rate)}</dd>
            </div>
          </dl>
        </article>
      ))}
    </div>
  );
}

export default function SchedulingCockpit() {
  const [search, setSearch] = useSearchParams();
  const now = businessDate(new Date().toISOString()).slice(0, 7);
  const from = search.get('from') ?? now.slice(0, 4) + '-01';
  const to = search.get('to') ?? now;
  const valid = monthPattern.test(from) && monthPattern.test(to) && from <= to;
  const [view, setView] = useState<{ key: string; data?: Overview; error?: string }>({ key: '' });
  const [attempt, setAttempt] = useState(0);
  const key = from + ':' + to + ':' + attempt;

  useEffect(() => {
    if (!valid) return;
    const controller = new AbortController();
    fetchOverview(from, to, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setView({ key, data });
      })
      .catch((cause) => {
        if (controller.signal.aborted) return;
        const error =
          cause instanceof ApiError && cause.status === 403
            ? '当前账号没有查看排产驾驶舱的权限。'
            : cause instanceof Error
              ? cause.message
              : '驾驶舱数据查询失败。';
        setView({ key, error });
      });
    return () => controller.abort();
  }, [from, to, key, valid]);

  const result = valid && view.key === key ? view.data : undefined;
  const error = !valid
    ? '请输入有效的月份范围，起始月份不得晚于结束月份。'
    : view.key === key
      ? view.error
      : undefined;

  function changeMonth(field: 'from' | 'to', value: string) {
    setSearch(
      { from: field === 'from' ? value : from, to: field === 'to' ? value : to },
      { replace: true },
    );
  }

  return (
    <CockpitBoard label="排产驾驶舱" testId="scheduling-board" height={960}>
      <header className="sc-header">
        <img src={monitor} alt="" width="32" height="32" />
        <h1>{title}</h1>
        <span className="sc-active-view">总体概览</span>
        <div className="sc-period" aria-label="统计月份范围">
          <img src={calendar} alt="" width="18" height="18" />
          <MonthPicker
            portal
            required
            min="1900-01"
            max={monthPattern.test(to) ? to : undefined}
            aria-label="起始月份"
            value={from}
            onChange={(value) => changeMonth('from', value)}
          />
          <span>至</span>
          <MonthPicker
            portal
            required
            min={monthPattern.test(from) ? from : '1900-01'}
            aria-label="结束月份"
            value={to}
            onChange={(value) => changeMonth('to', value)}
          />
        </div>
      </header>
      {result ? (
        <>
          <Metrics result={result} />
          <div className="sc-middle">
            <Panel title="三化排产／计收概览" note="累计 · 亿元">
              <Categories categories={result.categories} />
            </Panel>
            <Panel
              title={
                (result.productionScope === 'NATIONAL' && result.revenueScope === 'NATIONAL'
                  ? '全国'
                  : '') + '三化排产计收区域分布'
              }
              note="累计计收金额 · 亿元"
            >
              <CockpitMap
                provinces={result.provinces}
                nationwide={result.revenueScope === 'NATIONAL'}
              />
            </Panel>
            <Panel title="月度排产与计收趋势" className="sc-trend-panel">
              <CockpitChart months={result.months} />
              <div className="sc-panel-note">排产月份 / 费用账期</div>
            </Panel>
          </div>
          <div className="sc-bottom">
            <Panel title="省分排产计收 TOP 10" note="按累计计收排序">
              <ProvinceRanking provinces={result.provinces} />
              <div className="sc-panel-note">金额单位 · 亿元</div>
            </Panel>
            <Panel title="各省三化月度排产与计收情况" note="金额单位 · 万元">
              <ProvinceMonthly key={key} overview={result} />
            </Panel>
            <Panel title="月度增量分析" note="较上月 · 亿元">
              <CockpitChart months={result.months} increments />
              <div className="sc-panel-note">月度金额较上月的变化，可为负值</div>
            </Panel>
          </div>
          <footer className="sc-footer">
            排产按排产月份，计收按费用账期 · 排产范围：{scopeNames[result.productionScope]} ·
            计收范围：{scopeNames[result.revenueScope]}
            {result.empty && ' · 所选时间范围内暂无数据'}
          </footer>
        </>
      ) : (
        <div className="sc-status" role={error ? 'alert' : 'status'}>
          <p>{error || '正在加载总体概览…'}</p>
          {valid && error && (
            <button
              type="button"
              className="secondary-button"
              onClick={() => setAttempt(attempt + 1)}
            >
              重试
            </button>
          )}
        </div>
      )}
    </CockpitBoard>
  );
}
