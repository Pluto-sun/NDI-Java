import { Link } from 'react-router-dom';
import type { ProjectMapView } from '../projects/types';
import { amount } from '../../shared/ui/format';
import { metricText, type MapMetric, type MapRegion } from './mapMetrics';

export function MapRegionList({
  map,
  authorized,
  visible,
  selected,
  metric,
  onSelectionChange,
  onOpen,
}: {
  map: ProjectMapView;
  authorized: MapRegion[];
  visible: MapRegion[];
  selected: Set<string>;
  metric: MapMetric;
  onSelectionChange: (ids: string[]) => void;
  onOpen: (region: MapRegion) => void;
}) {
  function toggle(id: string) {
    onSelectionChange(
      selected.has(id)
        ? [...selected].filter((selectedId) => selectedId !== id)
        : [...selected, id],
    );
  }

  return (
    <div className="map-regions">
      <h3>{map.level === 'NATIONAL' ? '省分' : '地市'}统计</h3>
      <details className="map-region-filter">
        <summary>
          筛选区域（{selected.size}/{authorized.length}）
        </summary>
        <div className="map-region-filter-actions">
          <button
            type="button"
            onClick={() => onSelectionChange(authorized.map((item) => item.id))}
          >
            全选
          </button>
          <button type="button" onClick={() => onSelectionChange([])}>
            清空
          </button>
        </div>
        <div className="map-region-filter-options">
          {authorized.map((region) => (
            <label key={region.id}>
              <input
                type="checkbox"
                checked={selected.has(region.id)}
                onChange={() => toggle(region.id)}
              />
              {region.name}
            </label>
          ))}
        </div>
      </details>
      {visible.length === 0 ? (
        <p className="subtle">请选择至少一个区域。</p>
      ) : (
        <ol>
          {visible.map((region) => (
            <li key={region.id}>
              {map.level === 'NATIONAL' ? (
                <button type="button" onClick={() => onOpen(region)}>
                  <span>{region.name}</span>
                  <strong>{metricText(region, metric)}</strong>
                </button>
              ) : map.month ? (
                <div className="map-region-readonly">
                  <span>{region.name}</span>
                  <strong>{metricText(region, metric)}</strong>
                </div>
              ) : (
                <Link to={`/projects?cityId=${encodeURIComponent(region.id)}`}>
                  <span>{region.name}</span>
                  <strong>{metricText(region, metric)}</strong>
                </Link>
              )}
              <small>
                {region.projectCount} 个项目 · 总金额 {amount(region.expectedAmount)} 万元
              </small>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
