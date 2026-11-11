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
  const [provinceId, setProvinceId] = useState<string | null>(null);
  const province = data.provinces.find((region) => region.id === provinceId);
  const boundaryCode = province ? province.adcode : '100000';
  const rankLabel = province ? '地市排名' : '省分排名';
  const openProvince = (id: string | null) => {
    setProvinceId(id);
    setActiveCode(null);
    setZoom(1);
    setPan({ x: 0, y: 0 });
  };
  const [geoJson, setGeoJson] = useState<unknown>(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const [zoom, setZoom] = useState(1);
  const [activeCode, setActiveCode] = useState<string | null>(null);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const drag = useRef<{ x: number; y: number; panX: number; panY: number } | null>(null);
  useEffect(() => {
    let active = true;
    setError('');
    setGeoJson(null);
    if (!boundaryCode) return;
    projectsApi
      .mapBoundary(boundaryCode)
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
  }, [retry, boundaryCode]);
  const shapes = useMemo(() => mapShapes(geoJson, !province), [geoJson, province]);
  const labels = useMemo(() => mapLabels(shapes), [shapes]);
  const value = (region: Dashboard['provinces'][number]) =>
    metric === 'amount' ? region.summary.totalAmount : region.summary.projectCount;
  const rows = (
    province?.cities ??
    data.provinces.filter(
      (item) =>
        !['710000', '810000', '820000'].includes(item.adcode ?? '') &&
        !['台湾', '香港', '澳门'].includes(canonical(item.name)),
    )
  )
    .slice()
    .sort((a, b) => value(b) - value(a) || a.name.localeCompare(b.name));
  const total = rows.reduce((sum, row) => sum + value(row), 0);
  const maximum = Math.max(1, ...rows.map(value));
  const byName = new Map(rows.map((row) => [canonical(row.name), row]));
  const byCode = new Map(rows.map((row) => [row.adcode, row]));
  const rowFor = (shape: (typeof shapes)[number]) =>
    byCode.get(shape.adcode) ?? byName.get(canonical(shape.name));
  const activeShape = shapes.find((shape) => shape.adcode === activeCode);
  const activeRow = activeShape ? rowFor(activeShape) : undefined;
  const activeValue = activeRow
    ? `${amount(value(activeRow))} ${metric === 'amount' ? '万元' : '个'}`
    : '无数据或不在授权范围';
  return (
    <div className="dashboard-region-grid">
      <section className="panel dashboard-map" aria-label={`驾驶舱项目区域分布${stageLabel}`}>
        <div className="dashboard-map-heading">
          <h2>项目区域分布{province ? `（${province.name}）` : stageLabel}</h2>
          <span>{metric === 'amount' ? '项目金额（万元）' : '项目数（个）'}</span>
        </div>
        <div className="action-row" aria-label="驾驶舱地图操作">
          {province && (
            <button type="button" className="secondary-button" onClick={() => openProvince(null)}>
              返回全国
            </button>
          )}
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
              setActiveCode(null);
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
        <div className="dashboard-map-canvas">
          <div className="dashboard-map-readout" role="status">
            {activeShape ? (
              <>
                <strong>{activeShape.name}</strong>
                <span>{activeValue}</span>
              </>
            ) : (
              <span>{province ? '选择地市查看数据' : '选择省份查看地市分布'}</span>
            )}
          </div>
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
              role="group"
              aria-label={province ? '地市项目分布地图' : '省分项目分布地图'}
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
                const row = rowFor(shape);
                const ratio = row ? value(row) / maximum : 0;
                return (
                  <g
                    key={shape.adcode}
                    className={`dashboard-map-province ${activeCode === shape.adcode ? 'is-active' : ''}`}
                    onPointerEnter={() => {
                      if (!drag.current) setActiveCode(shape.adcode);
                    }}
                    role={!province && row ? 'button' : undefined}
                    tabIndex={!province && row ? 0 : undefined}
                    aria-label={!province && row ? `${row.name}地市分布` : undefined}
                    onClick={() =>
                      !province && row && zoom === 1
                        ? openProvince(row.id)
                        : setActiveCode(shape.adcode)
                    }
                    onKeyDown={(event) => {
                      if (!province && row && (event.key === 'Enter' || event.key === ' ')) {
                        event.preventDefault();
                        openProvince(row.id);
                      }
                    }}
                  >
                    <path
                      d={shape.path}
                      fill={
                        ratio > 0.67
                          ? 'var(--red)'
                          : ratio > 0.33
                            ? '#df6b76'
                            : ratio > 0
                              ? '#efb6bd'
                              : '#ebe7e9'
                      }
                      stroke="white"
                      strokeWidth="1"
                      vectorEffect="non-scaling-stroke"
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
                {labels.map((label) => (
                  <g key={label.adcode}>
                    {label.origin && (
                      <line
                        x1={label.origin[0]}
                        y1={label.origin[1]}
                        x2={label.x}
                        y2={label.y}
                        className="dashboard-map-callout"
                      />
                    )}
                    <text
                      x={label.x}
                      y={label.y}
                      textAnchor="middle"
                      dominantBaseline="central"
                      className={`dashboard-map-label ${activeCode === label.adcode ? 'is-active' : ''}`}
                    >
                      {label.name}
                    </text>
                  </g>
                ))}
              </g>
            </svg>
          </div>
        </div>
        <div className="dashboard-map-scale" aria-label="地图颜色图例">
          <span>
            <i className="map-no-data" />
            无数据或无权限
          </span>
          <span>
            低 <i className="map-scale-light" />
            <i className="map-scale-medium" />
            <i className="map-scale-dark" /> 高
          </span>
        </div>
        <p className="subtle">
          {metric === 'amount' ? '项目金额（万元）' : '项目数（个）'} · 颜色越深数值越高。
        </p>
      </section>
      <section className="panel dashboard-ranking" aria-label={`${rankLabel}${stageLabel}`}>
        <h2>
          {rankLabel}
          {stageLabel}
        </h2>
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
                <th>{province ? '地市' : '省份'}</th>
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
                  <th scope="row">
                    {province ? (
                      row.name
                    ) : (
                      <button
                        type="button"
                        className="text-button"
                        onClick={() => openProvince(row.id)}
                      >
                        {row.name}
                      </button>
                    )}
                  </th>
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
