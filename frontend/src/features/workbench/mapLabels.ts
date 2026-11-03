import type { mapShapes } from './mapGeometry';

const shortName = (name: string) =>
  name.replace(/(特别行政区|维吾尔自治区|壮族自治区|回族自治区|自治区|省|市)$/, '');

/** 全国图的密集区域优先向海侧展开，其余标签按包围盒避让。 */
export function mapLabels(shapes: ReturnType<typeof mapShapes>) {
  const offsets: Record<string, [number, number]> = {
    '110000': [-28, -22],
    '120000': [30, -5],
    '310000': [38, 0],
    '320000': [18, -12],
    '330000': [30, 12],
    '810000': [32, 4],
    '820000': [-18, 23],
  };
  const placed: { x: number; y: number; halfWidth: number }[] = [];
  return shapes
    .filter((shape) => shape.labelX > 0 && shape.name)
    .map((shape) => {
      const name = shortName(shape.name);
      const halfWidth = name.length * 6 + 4;
      const [dx, dy] = offsets[shape.adcode] ?? [0, 0];
      const candidates: [number, number][] = [[dx, dy]];
      for (let radius = 18; radius <= 180; radius += 18) {
        for (let angle = 0; angle < 8; angle++) {
          candidates.push([
            dx + radius * Math.cos((angle * Math.PI) / 4),
            dy + radius * Math.sin((angle * Math.PI) / 4),
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
      return {
        ...position,
        adcode: shape.adcode,
        name,
        anchorX: shape.labelX,
        anchorY: shape.labelY,
        displaced: Math.hypot(position.x - shape.labelX, position.y - shape.labelY) > 8,
      };
    });
}
