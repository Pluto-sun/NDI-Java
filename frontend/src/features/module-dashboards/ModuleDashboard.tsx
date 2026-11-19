import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { requestAt } from '../../shared/api/http';
import { useLiveQuery } from '../../shared/api/useLiveQuery';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import './module-dashboard.css';

type Module = 'scheduling' | 'revenue' | 'capability';
const views = [
  { key: 'gewu', label: '格物部署清单', metric: '部署项数量', endpoint: 'gewu-deployments' },
  { key: 'wangwu', label: '万悟部署情况', metric: '部署记录总数', endpoint: 'wangwu' },
  {
    key: 'agents-live',
    label: '已上线智能体',
    metric: '上线智能体记录数',
    endpoint: 'agents-live',
  },
  {
    key: 'agents-development',
    label: '开发中智能体',
    metric: '开发中智能体台账总数',
    endpoint: 'agents-development',
  },
];
type Metric = { label: string; count: number; amount: string | null };
type Summary = { count: number; amount: string | null; categories: Metric[]; provinces: Metric[] };

function formatAmount(amount: string | null) {
  if (amount === null) return '—';
  const [integer, decimal] = amount.split('.');
  return integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (decimal ? '.' + decimal : '');
}

export function DashboardControls({ module }: { module: Module }) {
  const [params, setParams] = useSearchParams();
  const update = (field: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(field, value);
    else next.delete(field);
    setParams(next, { replace: true });
  };
  if (module === 'capability') {
    const selected = views.find(({ key }) => key === params.get('view')) ?? views[0];
    return (
      <div className="module-dashboard-views" role="group" aria-label="能力指标视图">
        {views.map(({ key, label }) => (
          <button
            type="button"
            key={key}
            aria-pressed={selected.key === key}
            onClick={() => update('view', key)}
          >
            {label}
          </button>
        ))}
      </div>
    );
  }
  const from = params.get('from') ?? '';
  const to = params.get('to') ?? '';
  const title = module === 'scheduling' ? '排产月份' : '月账期';
  return (
    <div className="module-dashboard-months" role="group" aria-label={title + '范围'}>
      <span>{title}</span>
      <input
        type="month"
        min="1900-01"
        max="9999-12"
        aria-label={title + '起始月'}
        value={from}
        onChange={(event) => update('from', event.target.value)}
      />
      <span aria-hidden="true">至</span>
      <input
        type="month"
        min="1900-01"
        max="9999-12"
        aria-label={title + '结束月'}
        value={to}
        onChange={(event) => update('to', event.target.value)}
      />
    </div>
  );
}

export function ModuleDashboard({ module }: { module: Module }) {
  const [params] = useSearchParams();
  const from = params.get('from') ?? '';
  const to = params.get('to') ?? '';
  const view = views.find(({ key }) => key === params.get('view')) ?? views[0];
  const capability = module === 'capability';
  const scheduling = module === 'scheduling';
  const root = capability
    ? '/api/v1/capability/' + view.endpoint
    : scheduling
      ? '/api/v1/production/plans'
      : '/api/v1/revenue/entries';
  const query = capability ? '' : '?' + new URLSearchParams({ from, to }).toString();
  const invalid = !capability && !!from && !!to && from > to;
  const load = useCallback(
    (signal: AbortSignal) => requestAt<Summary>(root, '/dashboard' + query, { signal }),
    [root, query],
  );
  const { data, error, busy } = useLiveQuery(load, '驾驶舱读取失败', !invalid);
  const unit = scheduling ? '万元' : '元';
  const amountLabel = scheduling ? '预计总排产金额' : '认定金额合计';
  const countLabel = capability ? view.metric : scheduling ? '排产条目总数' : '计收台账记录总数';
  if (invalid) return <Feedback tone="error">起始月须早于或等于结束月。</Feedback>;
  if (!data) return error ? <Feedback tone="error">{error}</Feedback> : <Loading />;
  return (
    <div className="module-dashboard" aria-busy={busy}>
      {error && <Feedback tone="error">{error}</Feedback>}
      <div className="register-metrics" aria-label="当前经营指标">
        <article className="register-metric register-metric-total">
          <div>
            <span>{countLabel}</span>
            <strong data-testid="dashboard-total-count">
              {data.count.toLocaleString('zh-CN')}
            </strong>
          </div>
          {!capability && (
            <div>
              <span>
                {amountLabel}（{unit}）
              </span>
              <strong data-testid="dashboard-total-amount">{formatAmount(data.amount)}</strong>
            </div>
          )}
        </article>
        {data.categories.map((category) => (
          <article className="register-metric" key={category.label}>
            <h2>{category.label}</h2>
            <div className="register-metric-values">
              <div>
                <span>条目数</span>
                <strong>{category.count.toLocaleString('zh-CN')}</strong>
              </div>
              <div>
                <span>金额（{unit}）</span>
                <strong>{formatAmount(category.amount)}</strong>
              </div>
            </div>
          </article>
        ))}
      </div>
      {scheduling && <p className="subtle">同一条目涉及多个产品时，计入各对应产品。</p>}
      <section className="panel register-region" aria-labelledby="register-region-title">
        <h2 id="register-region-title">省分分布</h2>
        <table aria-label="省分分布">
          <thead>
            <tr>
              <th scope="col">省分</th>
              <th scope="col">{capability ? '数量' : '条目数'}</th>
              {!capability && (
                <th scope="col">
                  {amountLabel}（{unit}）
                </th>
              )}
            </tr>
          </thead>
          <tbody>
            {data.provinces.map((province) => (
              <tr key={province.label}>
                <th scope="row">{province.label}</th>
                <td>{province.count.toLocaleString('zh-CN')}</td>
                {!capability && <td>{formatAmount(province.amount)}</td>}
              </tr>
            ))}
            {!data.provinces.length && (
              <tr>
                <td colSpan={capability ? 2 : 3}>当前范围内暂无记录</td>
              </tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  );
}
