import { useEffect, useRef, useState } from 'react';
import { projectsApi } from '../projects/api';
import type { AttributionWorkbench, ReferenceData } from '../projects/types';
import { amount, businessDate } from '../../shared/ui/format';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { StageTrendChart, type StageTrendMetric } from './StageTrendChart';
import { MetricBars } from './MetricBars';

const metrics: { value: StageTrendMetric; label: string }[] = [
  { value: 'projectCount', label: '项目数量' },
  { value: 'totalExpectedAmount', label: '项目总金额' },
  { value: 'network', label: '网络化金额' },
  { value: 'gewu', label: '格物金额' },
  { value: 'bigData', label: '大数据金额' },
  { value: 'wangwu', label: '万悟金额' },
];

/** 所属月份每项目仅一份；与下方历史月末快照保持独立筛选和结果。 */
export function AttributionWorkbenchPanel({ reference }: { reference: ReferenceData }) {
  const currentMonth = businessDate(new Date().toISOString()).slice(0, 7);
  const [from, setFrom] = useState(`${currentMonth.slice(0, 4)}-01`);
  const [to, setTo] = useState(currentMonth);
  const [includeTerminated, setIncludeTerminated] = useState(false);
  const [stages, setStages] = useState<string[]>([]);
  const [provinceId, setProvinceId] = useState('');
  const [cityId, setCityId] = useState('');
  const [metric, setMetric] = useState<StageTrendMetric>('projectCount');
  const [result, setResult] = useState<AttributionWorkbench | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const generation = useRef(0);
  async function load() {
    const request = ++generation.current;
    setError('');
    setResult(null);
    if (!from || !to || from > to) {
      setBusy(false);
      setError('请选择有效的所属月份区间，起始月份不得晚于结束月份。');
      return;
    }
    setBusy(true);
    try {
      const data = await projectsApi.attribution({
        from,
        to,
        includeTerminated,
        stages,
        provinceId,
        cityId,
      });
      if (request === generation.current) setResult(data);
    } catch (cause) {
      if (request === generation.current)
        setError(cause instanceof Error ? cause.message : '所属月份统计加载失败');
    } finally {
      if (request === generation.current) setBusy(false);
    }
  }
  useEffect(() => {
    void load();
    return () => {
      generation.current += 1;
    };
  }, []);
  const stageName = (code: string) =>
    reference.stages.find((item) => item.code === code)?.label ?? code;
  const loadedStages = [...new Set(result?.trend.map((point) => point.stageCode) ?? [])];
  const stageTotals = loadedStages.map((code) => ({
    key: code,
    label: stageName(code),
    count: result!.trend
      .filter((point) => point.stageCode === code)
      .reduce((sum, point) => sum + point.projectCount, 0),
    expectedAmount: result!.trend
      .filter((point) => point.stageCode === code)
      .reduce((sum, point) => sum + point.totalExpectedAmount, 0),
  }));
  return (
    <section className="panel" aria-labelledby="attribution-title">
      <h2 id="attribution-title">项目所属月份区间统计</h2>
      <p className="subtle">
        每个项目只归属一个月份，区间内不重复累计。阶段推进获批后按发生月份归属，回退或终止获批后归入审批当月；待审、驳回和撤销不移动月份。
      </p>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void load();
        }}
      >
        <div className="filter-bar">
          <label>
            所属月份起始
            <input
              type="month"
              value={from}
              max={to}
              required
              onChange={(event) => setFrom(event.target.value)}
            />
          </label>
          <label>
            所属月份结束
            <input
              type="month"
              value={to}
              min={from}
              required
              onChange={(event) => setTo(event.target.value)}
            />
          </label>
          <label>
            归属省分
            <select
              value={provinceId}
              onChange={(event) => {
                setProvinceId(event.target.value);
                setCityId('');
              }}
            >
              <option value="">全部授权省分</option>
              {reference.provinces.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            归属地市
            <select value={cityId} onChange={(event) => setCityId(event.target.value)}>
              <option value="">全部授权地市</option>
              {reference.cities
                .filter((item) => !provinceId || item.provinceId === provinceId)
                .map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
            </select>
          </label>
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={includeTerminated}
              onChange={(event) => {
                setIncludeTerminated(event.target.checked);
                if (!event.target.checked)
                  setStages((current) => current.filter((code) => code !== 'TERMINATED'));
              }}
            />
            区间统计含已终止项目
          </label>
          <button className="primary-button" type="submit" disabled={busy}>
            {busy ? '查询中…' : '查询所属月份统计'}
          </button>
        </div>
        <div className="map-stage-filter" role="group" aria-label="所属月份统计阶段">
          <span>阶段组合（未勾选时统计全部阶段）</span>
          {reference.stages.map((item) => (
            <label className="checkbox-label" key={item.code}>
              <input
                type="checkbox"
                checked={stages.includes(item.code)}
                onChange={(event) => {
                  const checked = event.target.checked;
                  setStages((current) =>
                    checked
                      ? [...current, item.code]
                      : current.filter((code) => code !== item.code),
                  );
                  if (checked && item.code === 'TERMINATED') setIncludeTerminated(true);
                }}
              />
              {item.label}
            </label>
          ))}
        </div>
      </form>
      {busy && <Loading />}
      {error && (
        <Feedback tone="error">
          {error}
          <button type="button" className="inline-link" onClick={() => void load()}>
            重试所属月份统计
          </button>
        </Feedback>
      )}
      {result && (
        <>
          <p className="subtle">
            已加载 {result.from} 至 {result.to} · {result.includeTerminated ? '包含' : '排除'}
            已终止项目 · 当前唯一所属月份口径，非月末存量快照
          </p>
          <div className="kpi-grid">
            <div className="kpi">
              <span>区间项目数量</span>
              <strong>{result.projectCount}</strong>
              <small>每项目仅计一次</small>
            </div>
            <div className="kpi">
              <span>区间项目总金额</span>
              <strong>{amount(result.totalExpectedAmount)}</strong>
              <small>万元</small>
            </div>
            {(['network', 'gewu', 'bigData', 'wangwu'] as const).map((code) => (
              <div className="kpi" key={code}>
                <span>{metrics.find((item) => item.value === code)?.label}</span>
                <strong>{amount(result.categoryTotals[code])}</strong>
                <small>万元</small>
              </div>
            ))}
          </div>
          <div className="detail-grid">
            <div>
              <h3>区间阶段分布</h3>
              <MetricBars items={stageTotals} />
            </div>
            <div>
              <h3>区间省分分布</h3>
              <MetricBars
                items={result.provinces.map((item) => ({
                  key: item.id,
                  label: item.name,
                  count: item.projectCount,
                  expectedAmount: item.expectedAmount,
                }))}
              />
            </div>
            <div>
              <h3>区间地市分布</h3>
              <MetricBars
                items={result.cities.map((item) => ({
                  key: item.id,
                  label: item.name,
                  count: item.projectCount,
                  expectedAmount: item.expectedAmount,
                }))}
              />
            </div>
          </div>
          <label>
            所属月份趋势指标
            <select
              value={metric}
              onChange={(event) => setMetric(event.target.value as StageTrendMetric)}
            >
              {metrics.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </select>
          </label>
          <StageTrendChart
            points={result.trend}
            stages={loadedStages}
            stageName={stageName}
            metric={metric}
            metricLabel={metrics.find((item) => item.value === metric)!.label}
            caption="按项目所属月份的阶段指标明细"
          />
        </>
      )}
    </section>
  );
}
