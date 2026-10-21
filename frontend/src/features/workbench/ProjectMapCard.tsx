import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { projectsApi } from '../projects/api';
import type { ProjectMapView, ReferenceData } from '../projects/types';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { mapShapes } from './mapGeometry';
import { MAP_METRICS, metricText, metricValue, type MapMetric, type MapRegion } from './mapMetrics';
import { MapRegionList } from './MapRegionList';

type Viewport = { x: number; y: number; width: number; height: number };

const DEFAULT_VIEWPORT: Viewport = { x: 0, y: 0, width: 680, height: 440 };

function previousMonth() {
  const date = new Date();
  date.setDate(1);
  date.setMonth(date.getMonth() - 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

export function ProjectMapCard({ reference }: { reference: ReferenceData }) {
  const navigate = useNavigate();
  const [provinceId, setProvinceId] = useState<string | null>(null);
  const [stage, setStage] = useState('');
  const [month, setMonth] = useState('');
  const [metric, setMetric] = useState<MapMetric>('expectedAmount');
  const [includeTerminated, setIncludeTerminated] = useState(false);
  const [selectedRegionIds, setSelectedRegionIds] = useState<string[]>([]);
  const [viewport, setViewport] = useState<Viewport>(DEFAULT_VIEWPORT);
  const [map, setMap] = useState<ProjectMapView | null>(null);
  const [geoJson, setGeoJson] = useState<unknown>(null);
  const [error, setError] = useState('');
  const [boundaryError, setBoundaryError] = useState('');
  const [loading, setLoading] = useState(true);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setMap(null);
    setError('');
    void projectsApi
      .map(provinceId, includeTerminated, stage, month)
      .then((result) => {
        if (active) setMap(result);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '地图统计加载失败');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [provinceId, includeTerminated, stage, month, refreshKey]);

  useEffect(() => {
    setSelectedRegionIds(
      map?.regions.filter((region) => region.authorized).map((region) => region.id) ?? [],
    );
  }, [map]);
  useEffect(() => {
    setViewport(DEFAULT_VIEWPORT);
  }, [map?.mapAdcode]);

  useEffect(() => {
    let active = true;
    setGeoJson(null);
    setBoundaryError('');
    if (map?.mapAdcode) {
      void projectsApi
        .mapBoundary(map.mapAdcode)
        .then(async (boundary) => {
          if (active) setGeoJson(JSON.parse(await boundary.text()) as unknown);
        })
        .catch((cause) => {
          if (active) setBoundaryError(cause instanceof Error ? cause.message : '地图边界加载失败');
        });
    }
    return () => {
      active = false;
    };
  }, [map?.mapAdcode]);

  const shapes = useMemo(() => mapShapes(geoJson), [geoJson]);
  const byAdcode = useMemo(
    () =>
      new Map(
        map?.regions.filter((region) => region.adcode).map((region) => [region.adcode, region]) ??
          [],
      ),
    [map],
  );
  const authorized = useMemo(() => map?.regions.filter((region) => region.authorized) ?? [], [map]);
  const selected = useMemo(() => new Set(selectedRegionIds), [selectedRegionIds]);
  const visible = useMemo(
    () =>
      authorized
        .filter((region) => selected.has(region.id))
        .sort(
          (left, right) =>
            metricValue(right, metric) - metricValue(left, metric) ||
            left.name.localeCompare(right.name),
        ),
    [authorized, selected, metric],
  );
  const maximum = Math.max(1, ...visible.map((region) => metricValue(region, metric)));
  const metricLabel = MAP_METRICS.find((item) => item.value === metric)?.label ?? '项目总金额';

  function openRegion(region: MapRegion) {
    if (!region.authorized || !map) return;
    if (map.level === 'NATIONAL') setProvinceId(region.id);
    else if (!map.month) void navigate(`/projects?cityId=${encodeURIComponent(region.id)}`);
  }

  function shade(region: MapRegion | undefined) {
    if (!region?.authorized) return 'map-shape-unavailable';
    if (!selected.has(region.id)) return 'map-shape-unselected';
    const value = metricValue(region, metric);
    if (value === 0) return 'map-shape-zero';
    if (value / maximum > 0.67) return 'map-shape-high';
    if (value / maximum > 0.33) return 'map-shape-medium';
    return 'map-shape-low';
  }

  function zoom(factor: number) {
    setViewport((current) => {
      const width = Math.max(170, Math.min(DEFAULT_VIEWPORT.width, current.width * factor));
      const height = (width / DEFAULT_VIEWPORT.width) * DEFAULT_VIEWPORT.height;
      return {
        x: Math.max(
          0,
          Math.min(DEFAULT_VIEWPORT.width - width, current.x + (current.width - width) / 2),
        ),
        y: Math.max(
          0,
          Math.min(DEFAULT_VIEWPORT.height - height, current.y + (current.height - height) / 2),
        ),
        width,
        height,
      };
    });
  }

  function pan(horizontal: number, vertical: number) {
    setViewport((current) => ({
      ...current,
      x: Math.max(
        0,
        Math.min(
          DEFAULT_VIEWPORT.width - current.width,
          current.x + horizontal * current.width * 0.2,
        ),
      ),
      y: Math.max(
        0,
        Math.min(
          DEFAULT_VIEWPORT.height - current.height,
          current.y + vertical * current.height * 0.2,
        ),
      ),
    }));
  }

  return (
    <section className="panel map-panel" aria-labelledby="project-map-title">
      <div className="map-heading">
        <div>
          <h2 id="project-map-title">项目区域分布</h2>
          <p className="subtle">
            {month ? `${month} 月末快照` : '当前项目主档'} · 默认排除已终止项目
          </p>
        </div>
        {provinceId && (
          <button type="button" className="secondary-button" onClick={() => setProvinceId(null)}>
            返回全国
          </button>
        )}
      </div>
      <div className="filter-bar">
        <label>
          统计月份
          <input
            type="month"
            value={month}
            max={previousMonth()}
            onChange={(event) => setMonth(event.target.value)}
          />
        </label>
        {month && (
          <button type="button" className="secondary-button" onClick={() => setMonth('')}>
            查看当前主档
          </button>
        )}
        <label>
          项目阶段
          <select
            value={stage}
            onChange={(event) => {
              setStage(event.target.value);
              if (event.target.value === 'TERMINATED') setIncludeTerminated(true);
            }}
          >
            <option value="">全部阶段</option>
            {reference.stages.map((item) => (
              <option key={item.code} value={item.code}>
                {item.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          着色与排行指标
          <select value={metric} onChange={(event) => setMetric(event.target.value as MapMetric)}>
            {MAP_METRICS.map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
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
              if (!event.target.checked && stage === 'TERMINATED') setStage('');
            }}
          />
          含已终止项目
        </label>
      </div>
      {month && (
        <p className="subtle">历史月份仅查询已生成的月末快照；地市历史统计不跳转当前项目清单。</p>
      )}
      {error && (
        <Feedback tone="error">
          {error}{' '}
          <button
            type="button"
            className="inline-link"
            onClick={() => setRefreshKey((key) => key + 1)}
          >
            重试
          </button>
        </Feedback>
      )}
      {loading && <Loading />}
      {map && !error && (
        <>
          <p className="subtle">
            {map.level === 'NATIONAL' ? '全国' : map.province?.name} ·
            全国地图点击省份下钻；当前地市统计可进入项目清单。
          </p>
          <p className="subtle">
            颜色越深表示{metricLabel}越高；灰色表示无权限或未勾选，具体数值见区域清单。
          </p>
          {boundaryError && <Feedback>图形暂不可用：{boundaryError}。区域清单仍可使用。</Feedback>}
          {!map.mapAdcode && <Feedback>该组织尚未匹配地图编码，请使用区域清单。</Feedback>}
          <div className="map-layout">
            <div>
              <div className="map-controls" role="group" aria-label="地图视图控制">
                <button
                  type="button"
                  aria-label="放大地图"
                  disabled={viewport.width <= 170}
                  onClick={() => zoom(0.8)}
                >
                  ＋
                </button>
                <button
                  type="button"
                  aria-label="缩小地图"
                  disabled={viewport.width >= 680}
                  onClick={() => zoom(1.25)}
                >
                  －
                </button>
                <button
                  type="button"
                  aria-label="地图上移"
                  disabled={viewport.height >= 440}
                  onClick={() => pan(0, -1)}
                >
                  ↑
                </button>
                <button
                  type="button"
                  aria-label="地图左移"
                  disabled={viewport.width >= 680}
                  onClick={() => pan(-1, 0)}
                >
                  ←
                </button>
                <button
                  type="button"
                  aria-label="地图右移"
                  disabled={viewport.width >= 680}
                  onClick={() => pan(1, 0)}
                >
                  →
                </button>
                <button
                  type="button"
                  aria-label="地图下移"
                  disabled={viewport.height >= 440}
                  onClick={() => pan(0, 1)}
                >
                  ↓
                </button>
                <button
                  type="button"
                  className="map-reset"
                  disabled={viewport.width === 680}
                  onClick={() => setViewport(DEFAULT_VIEWPORT)}
                >
                  复位
                </button>
              </div>
              <div className="map-canvas" aria-busy={loading}>
                {shapes.length ? (
                  <svg
                    viewBox={`${viewport.x} ${viewport.y} ${viewport.width} ${viewport.height}`}
                    role="group"
                    aria-label="项目区域分布地图"
                  >
                    <title>项目区域分布地图</title>
                    {shapes.map((shape) => {
                      const region = byAdcode.get(shape.adcode);
                      const operable =
                        region?.authorized && (map.level === 'NATIONAL' || !map.month);
                      return (
                        <path
                          key={shape.adcode}
                          d={shape.path}
                          fillRule="evenodd"
                          className={shade(region)}
                          role={operable ? 'button' : undefined}
                          tabIndex={operable ? 0 : -1}
                          aria-label={
                            operable && region
                              ? `${region.name}：${metricText(region, metric)}`
                              : undefined
                          }
                          onClick={() => region && openRegion(region)}
                          onKeyDown={(event) => {
                            if (
                              operable &&
                              region &&
                              (event.key === 'Enter' || event.key === ' ')
                            ) {
                              event.preventDefault();
                              openRegion(region);
                            }
                          }}
                        />
                      );
                    })}
                  </svg>
                ) : (
                  <div className="empty-state">地图图形暂不可用，右侧清单可继续查看。</div>
                )}
              </div>
            </div>
            <MapRegionList
              map={map}
              authorized={authorized}
              visible={visible}
              selected={selected}
              metric={metric}
              onSelectionChange={setSelectedRegionIds}
              onOpen={openRegion}
            />
          </div>
        </>
      )}
    </section>
  );
}
