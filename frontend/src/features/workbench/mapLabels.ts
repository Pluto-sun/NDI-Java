import type { mapShapes } from './mapGeometry';

const shortName = (name: string) =>
  name.replace(/(特别行政区|维吾尔自治区|壮族自治区|回族自治区|自治区|省|市)$/, '');

/** 面积较小且相邻的区域使用图下标签，避免跨省引线遮挡地图。 */
export const compactMapRegions = new Set(['110000', '120000', '310000', '810000', '820000']);

/** 标签在原省域附近小幅避让，不再向远处展开。 */
export function mapLabels(shapes: ReturnType<typeof mapShapes>) {
  const placed: { x: number; y: number; halfWidth: number }[] = [];
  return shapes
    .filter((shape) => shape.labelX > 0 && shape.name && !compactMapRegions.has(shape.adcode))
    .map((shape) => {
      const name = shortName(shape.name);
      const halfWidth = name.length * 5.3 + 3;
      const candidates: [number, number][] = [[0, 0]];
      for (let radius = 4; radius <= 32; radius += 4) {
        for (let angle = 0; angle < 16; angle++) {
          candidates.push([
            radius * Math.cos((angle * Math.PI) / 8),
            radius * Math.sin((angle * Math.PI) / 8),
          ]);
        }
      }
      const positions = candidates.map(([x, y]) => ({
        x: Math.max(halfWidth + 8, Math.min(672 - halfWidth, shape.labelX + x)),
        y: Math.max(14, Math.min(426, shape.labelY + y)),
        halfWidth,
      }));
      const position =
        positions.find((candidate) =>
          placed.every(
            (other) =>
              Math.abs(candidate.x - other.x) >= candidate.halfWidth + other.halfWidth ||
              Math.abs(candidate.y - other.y) >= 18,
          ),
        ) ?? positions[0];
      placed.push(position);
      return { ...position, adcode: shape.adcode, name };
    });
}
