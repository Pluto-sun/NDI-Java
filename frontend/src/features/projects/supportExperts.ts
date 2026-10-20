export function parseSupportExperts(value: string): string[] {
  const names = value
    .split(/[,，、;；\n]+/)
    .map((name) => name.trim())
    .filter(Boolean);
  if (names.some((name) => name.length > 100) || new Set(names).size !== names.length) {
    throw new Error('支撑专家姓名不能超过 100 字，也不能重复。');
  }
  return names;
}
