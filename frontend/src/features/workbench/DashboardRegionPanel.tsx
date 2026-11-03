import { useEffect, useMemo, useRef, useState } from 'react';
import { projectsApi } from '../projects/api';
import { Feedback } from '../../shared/ui/Feedback';
import { amount } from '../../shared/ui/format';
import { mapShapes } from './mapGeometry';
import type { Dashboard } from './dashboardApi';
import { DashboardShare } from './DashboardShare';
import { mapLabels } from './mapLabels';

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
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const drag = useRef<{ x: number; y: number; panX: number; panY: number } | null>(null);
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
  const labels = useMemo(() => mapLabels(shapes), [shapes]);
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
        <div className="action-row" aria-label="驾驶舱地图操作">
          <button
            type="button"
            className="text-button"
            disabled={zoom >= 3}
            onClick={() => setZoom((value) => Math.min(3, value + 0.25))}
          >
            放大地图
          </button>
          <button
            type="button"
            className="text-button"
            disabled={zoom <= 1}
            onClick={() => setZoom((value) => Math.max(1, value - 0.25))}
          >
            缩小地图
          </button>
          <button
            type="button"
            className="text-button"
            onClick={() => {
              setZoom(1);
              setPan({ x: 0, y: 0 });
            }}
          >
            重置地图
          </button>
          <small className="subtle">{Math.round(zoom * 100)}% · 放大后可拖动或使用方向键移动</small>
        </div>
        {error && (
          <Feedback>
            图形暂不可用：{error}。右侧排名仍可使用。
            <button className="text-button" onClick={() => setRetry((current) => current + 1)}>
              重试地图
            </button>
          </Feedback>
        )}
        <div
          className="dashboard-map-scroll"
          tabIndex={0}
          role="region"
          aria-label="省分地图，可横向滚动"
        >
          <svg
            style={{
              touchAction: zoom > 1 ? 'none' : 'pan-y',
              cursor: zoom > 1 ? 'grab' : 'default',
            }}
            viewBox={`${340 - 340 / zoom + pan.x} ${220 - 220 / zoom + pan.y} ${680 / zoom} ${440 / zoom}`}
            role="img"
            aria-label="省分项目分布地图，具体数据见省分排名表"
            tabIndex={0}
            onKeyDown={(event) => {
              const step = 25 / zoom;
              if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) {
                event.preventDefault();
                setPan((value) => ({
                  x:
                    value.x +
                    (event.key === 'ArrowLeft' ? -step : event.key === 'ArrowRight' ? step : 0),
                  y:
                    value.y +
                    (event.key === 'ArrowUp' ? -step : event.key === 'ArrowDown' ? step : 0),
                }));
              }
            }}
            onPointerDown={(event) => {
              if (zoom <= 1) return;
              drag.current = { x: event.clientX, y: event.clientY, panX: pan.x, panY: pan.y };
              event.currentTarget.setPointerCapture(event.pointerId);
            }}
            onPointerMove={(event) => {
              if (!drag.current) return;
              const scale = 680 / event.currentTarget.getBoundingClientRect().width / zoom;
              setPan({
                x: drag.current.panX - (event.clientX - drag.current.x) * scale,
                y: drag.current.panY - (event.clientY - drag.current.y) * scale,
              });
            }}
            onPointerUp={() => {
              drag.current = null;
            }}
            onPointerCancel={() => {
              drag.current = null;
            }}
          >
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
                </g>
              );
            })}
            <g className="dashboard-map-labels" aria-hidden="true">
              {labels
                .filter((label) => label.displaced)
                .map((label) => (
                  <path
                    key={label.adcode}
                    className="dashboard-map-leader"
                    d={`M${label.anchorX},${label.anchorY}L${label.x},${label.y}`}
                  />
                ))}
              {labels.map((label) => (
                <g key={label.adcode}>
                  <text
                    x={label.x}
                    y={label.y}
                    textAnchor="middle"
                    dominantBaseline="central"
                    className="dashboard-map-label"
                  >
                    {label.name}
                  </text>
                </g>
              ))}
            </g>
          </svg>
        </div>
        <p className="subtle">
          {metric === 'amount' ? '项目金额（万元）' : '项目数（个）'} ·
          颜色越深数值越高；灰色为无数据或无权限。密集标签通过引导线标注，窄屏可横向滚动。全部数值见省分排名。
        </p>
      </section>
      <section className="panel dashboard-ranking" aria-label={`省分排名${stageLabel}`}>
        <h2>省分排名{stageLabel}</h2>
        <div className="table-scroll">
          <table>
            <colgroup>
              {[10, 15, 16, 25, 34].map((width, index) => (
                <col key={index} style={{ width: `${width}%` }} />
              ))}
            </colgroup>
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
                    <DashboardShare value={value(row)} total={total} />
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
