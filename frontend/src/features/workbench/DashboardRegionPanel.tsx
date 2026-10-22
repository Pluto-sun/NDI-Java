import { useEffect, useMemo, useState } from 'react';
import { projectsApi } from '../projects/api';
import { Feedback } from '../../shared/ui/Feedback';
import { amount } from '../../shared/ui/format';
import { mapShapes } from './mapGeometry';
import { percent, type Dashboard } from './dashboardApi';

const canonical = (name: string) =>
  name.replace(/(特别行政区|维吾尔自治区|壮族自治区|回族自治区|自治区|省|市)$/, '');
export function DashboardRegionPanel({
  data,
  metric,
  stageLabel,
}: {
  data: Dashboard;
  metric: 'amount' | 'count';
  stageLabel: string;
}) {
  const [geoJson, setGeoJson] = useState<unknown>(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setError('');
    projectsApi
      .mapBoundary('100000')
      .then(async (boundary) => {
        const parsed: unknown = JSON.parse(await boundary.text());
        if (active) setGeoJson(parsed);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '地图边界读取失败');
      });
    return () => {
      active = false;
    };
  }, [retry]);
  const shapes = useMemo(() => mapShapes(geoJson), [geoJson]);
  const value = (region: Dashboard['provinces'][number]) =>
    metric === 'amount' ? region.summary.totalAmount : region.summary.projectCount;
  const rows = [...data.provinces].sort(
    (a, b) => value(b) - value(a) || a.name.localeCompare(b.name),
  );
  const total = metric === 'amount' ? data.current.totalAmount : data.current.projectCount;
  const maximum = Math.max(1, ...rows.map(value));
  const byName = new Map(rows.map((row) => [canonical(row.name), row]));
  return (
    <div className="dashboard-region-grid">
      <section className="panel dashboard-map" aria-label={`驾驶舱项目区域分布${stageLabel}`}>
        <h2>项目区域分布{stageLabel}</h2>
        {error && (
          <Feedback>
            图形暂不可用：{error}。右侧排名仍可使用。
            <button className="text-button" onClick={() => setRetry((current) => current + 1)}>
              重试地图
            </button>
          </Feedback>
        )}
        <svg viewBox="0 0 680 440" role="img" aria-label="省分项目分布地图，具体数据见省分排名表">
          {shapes.map((shape) => {
            const row = byName.get(canonical(shape.name));
            const ratio = row ? value(row) / maximum : 0;
            return (
              <g key={shape.adcode}>
                <path
                  d={shape.path}
                  fill={
                    ratio > 0.67
                      ? 'var(--red)'
                      : ratio > 0.33
                        ? '#df6b76'
                        : ratio > 0
                          ? '#efb6bd'
                          : '#eee9eb'
                  }
                  stroke="white"
                  strokeWidth=".7"
                >
                  <title>
                    {shape.name}：
                    {row
                      ? `${amount(value(row))} ${metric === 'amount' ? '万元' : '个'}`
                      : '无数据或不在授权范围'}
                  </title>
                </path>
                {shape.labelX > 0 && (
                  <text
                    x={shape.labelX}
                    y={shape.labelY}
                    textAnchor="middle"
                    className="dashboard-map-label"
                  >
                    {canonical(shape.name)}
                  </text>
                )}
              </g>
            );
          })}
        </svg>
        <p className="subtle">
          {metric === 'amount' ? '项目金额（万元）' : '项目数（个）'} ·
          颜色越深数值越高；灰色为无数据或无权限。全部数值见右侧排名。
        </p>
      </section>
      <section className="panel dashboard-ranking" aria-label={`省分排名${stageLabel}`}>
        <h2>省分排名{stageLabel}</h2>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>排名</th>
                <th>省份</th>
                <th>项目数（个）</th>
                <th>项目金额（万元）</th>
                <th>{metric === 'amount' ? '金额' : '数量'}占比</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, index) => (
                <tr key={row.id}>
                  <td>
                    <span className={`rank-badge rank-${index + 1}`}>{index + 1}</span>
                  </td>
                  <th>{row.name}</th>
                  <td>{row.summary.projectCount}</td>
                  <td>{amount(row.summary.totalAmount)}</td>
                  <td>
                    <div className="dashboard-share">
                      <span style={{ width: percent(value(row), total) }} />
                      <small>{percent(value(row), total)}</small>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!rows.length && <p className="empty-state">此筛选条件下暂无项目</p>}
      </section>
    </div>
  );
}
