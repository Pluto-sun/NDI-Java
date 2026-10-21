import type { ProjectMapView } from '../projects/types';
import { amount } from '../../shared/ui/format';

export type MapRegion = ProjectMapView['regions'][number];
export type MapMetric =
  'projectCount' | 'expectedAmount' | 'network' | 'gewu' | 'bigData' | 'wangwu';

export const MAP_METRICS: { value: MapMetric; label: string }[] = [
  { value: 'expectedAmount', label: '项目总金额' },
  { value: 'projectCount', label: '项目数量' },
  { value: 'network', label: '网络化金额' },
  { value: 'gewu', label: '格物金额' },
  { value: 'bigData', label: '大数据金额' },
  { value: 'wangwu', label: '万悟金额' },
];

export function metricValue(region: MapRegion, metric: MapMetric) {
  if (metric === 'projectCount') return region.projectCount;
  if (metric === 'expectedAmount') return region.expectedAmount;
  return region.categoryTotals[metric];
}

export function metricText(region: MapRegion, metric: MapMetric) {
  const value = metricValue(region, metric);
  return metric === 'projectCount' ? `${value} 个` : `${amount(value)} 万元`;
}
