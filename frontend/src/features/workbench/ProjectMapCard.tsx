import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { projectsApi } from '../projects/api';
import type { ProjectMapView, ReferenceData } from '../projects/types';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { amount } from '../../shared/ui/format';
import { mapShapes } from './mapGeometry';

type MapRegion = ProjectMapView['regions'][number];

export function ProjectMapCard({ reference }: { reference: ReferenceData }) {
  const navigate = useNavigate();
  const [provinceId, setProvinceId] = useState<string | null>(null);
  const [stage, setStage] = useState('');
  const [includeTerminated, setIncludeTerminated] = useState(false);
  const [map, setMap] = useState<ProjectMapView | null>(null);
  const [geoJson, setGeoJson] = useState<unknown>(null);
  const [error, setError] = useState('');
  const [boundaryError, setBoundaryError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    void projectsApi
      .map(provinceId, includeTerminated, stage)
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
  }, [provinceId, includeTerminated, stage]);

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
  const visible = useMemo(
    () =>
      map?.regions
        .filter((region) => region.authorized)
        .sort(
          (left, right) =>
            right.projectCount - left.projectCount || left.name.localeCompare(right.name),
        ) ?? [],
    [map],
  );
  const maximum = Math.max(1, ...visible.map((region) => region.projectCount));

  function openRegion(region: MapRegion) {
    if (!region.authorized || !map) return;
    if (map.level === 'NATIONAL') setProvinceId(region.id);
    else void navigate(`/projects?cityId=${encodeURIComponent(region.id)}`);
  }

  function shade(region: MapRegion | undefined) {
    if (!region?.authorized) return 'map-shape-unavailable';
    if (region.projectCount === 0) return 'map-shape-zero';
    if (region.projectCount / maximum > 0.67) return 'map-shape-high';
    if (region.projectCount / maximum > 0.33) return 'map-shape-medium';
    return 'map-shape-low';
  }

  return (
    <div className="panel map-panel">
      <div className="map-heading">
        <div>
          <h2>项目区域分布</h2>
          <p className="subtle">当前项目主档 · 默认排除已终止项目</p>
        </div>
        {provinceId && (
          <button type="button" className="secondary-button" onClick={() => setProvinceId(null)}>
            返回全国
          </button>
        )}
      </div>
      <div className="filter-bar">
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
      {error && <Feedback tone="error">{error}</Feedback>}
      {loading && <Loading />}
      {map && !error && (
        <>
          <p className="subtle">
            {map.level === 'NATIONAL' ? '全国' : map.province?.name} ·
            点击区域下钻，或使用右侧区域清单。
          </p>
          <p className="subtle">
            颜色越深表示项目越多；具体数量和金额见区域清单，灰色区域不在当前权限范围内。
          </p>
          {boundaryError && <Feedback>图形暂不可用：{boundaryError}。区域清单仍可使用。</Feedback>}
          {!map.mapAdcode && <Feedback>该组织尚未匹配地图编码，请使用区域清单。</Feedback>}
          <div className="map-layout">
            <div className="map-canvas" aria-busy={loading}>
              {shapes.length ? (
                <svg viewBox="0 0 680 440" role="group" aria-label="项目区域分布地图">
                  <title>项目区域分布地图</title>
                  {shapes.map((shape) => {
                    const region = byAdcode.get(shape.adcode);
                    return (
                      <path
                        key={shape.adcode}
                        d={shape.path}
                        fillRule="evenodd"
                        className={shade(region)}
                        role={region?.authorized ? 'button' : undefined}
                        tabIndex={region?.authorized ? 0 : -1}
                        aria-label={
                          region?.authorized
                            ? `${region.name}：${region.projectCount} 个项目，${amount(region.expectedAmount)} 万元`
                            : undefined
                        }
                        onClick={() => region && openRegion(region)}
                        onKeyDown={(event) => {
                          if (region && (event.key === 'Enter' || event.key === ' ')) {
                            event.preventDefault();
                            openRegion(region);
                          }
                        }}
                      />
                    );
                  })}
                </svg>
              ) : (
                <div className="empty-state">地图图形暂不可用，右侧清单可继续下钻。</div>
              )}
            </div>
            <div className="map-regions">
              <h3>{map.level === 'NATIONAL' ? '省分' : '地市'}统计</h3>
              <ol>
                {visible.map((region) => (
                  <li key={region.id}>
                    {map.level === 'NATIONAL' ? (
                      <button type="button" onClick={() => openRegion(region)}>
                        <span>{region.name}</span>
                        <strong>{region.projectCount} 个</strong>
                      </button>
                    ) : (
                      <Link to={`/projects?cityId=${encodeURIComponent(region.id)}`}>
                        <span>{region.name}</span>
                        <strong>{region.projectCount} 个</strong>
                      </Link>
                    )}
                    <small>{amount(region.expectedAmount)} 万元</small>
                  </li>
                ))}
              </ol>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
