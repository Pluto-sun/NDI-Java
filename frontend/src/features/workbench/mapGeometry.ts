interface MapShape {
  adcode: string;
  path: string;
}

type Point = [number, number];
type Ring = Point[];

function rings(geometry: { type?: string; coordinates?: unknown } | undefined): Ring[] {
  if (!geometry || !Array.isArray(geometry.coordinates)) return [];
  const polygons =
    geometry.type === 'Polygon'
      ? [geometry.coordinates]
      : geometry.type === 'MultiPolygon'
        ? geometry.coordinates
        : [];
  return polygons.flatMap((polygon) => {
    if (!Array.isArray(polygon)) return [];
    return polygon.flatMap((ring) => {
      if (!Array.isArray(ring)) return [];
      const points = ring.filter(
        (point): point is Point =>
          Array.isArray(point) &&
          point.length >= 2 &&
          typeof point[0] === 'number' &&
          typeof point[1] === 'number' &&
          Number.isFinite(point[0]) &&
          Number.isFinite(point[1]),
      );
      return points.length >= 3 ? [points] : [];
    });
  });
}

/** 只计算 SVG 投影，区域数值仍来自受权限约束的业务接口。 */
export function mapShapes(geoJson: unknown): MapShape[] {
  if (!geoJson || typeof geoJson !== 'object' || !('features' in geoJson)) return [];
  const raw = (geoJson as { features?: unknown }).features;
  if (!Array.isArray(raw)) return [];
  const features = raw.map((feature) => {
    const properties = feature?.properties as { adcode?: string | number } | undefined;
    return {
      adcode: String(properties?.adcode ?? ''),
      rings: rings(feature?.geometry),
    };
  });
  const points = features.flatMap((feature) => feature.rings.flat());
  if (!points.length) return [];
  let minX = Infinity;
  let maxX = -Infinity;
  let minY = Infinity;
  let maxY = -Infinity;
  for (const [x, y] of points) {
    minX = Math.min(minX, x);
    maxX = Math.max(maxX, x);
    minY = Math.min(minY, y);
    maxY = Math.max(maxY, y);
  }
  const scale = Math.min(640 / Math.max(0.001, maxX - minX), 400 / Math.max(0.001, maxY - minY));
  const offsetX = (680 - (maxX - minX) * scale) / 2;
  const offsetY = (440 - (maxY - minY) * scale) / 2;
  const pointText = ([x, y]: Point) =>
    `${(offsetX + (x - minX) * scale).toFixed(1)},${(offsetY + (maxY - y) * scale).toFixed(1)}`;
  return features
    .filter((feature) => feature.adcode && feature.rings.length)
    .map((feature) => ({
      adcode: feature.adcode,
      path: feature.rings.map((ring) => `M${ring.map(pointText).join('L')}Z`).join(''),
    }));
}
