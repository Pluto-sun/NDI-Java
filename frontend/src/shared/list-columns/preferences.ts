import type { ColumnSizingState } from '@tanstack/react-table';

export type Preferences = { order: string[]; hidden: string[]; widths: ColumnSizingState };

export const defaultPreferences: Preferences = { order: [], hidden: [], widths: {} };
export const minWidth = 72;
export const maxWidth = 520;

function stringIds(value: unknown): string[] {
  return Array.isArray(value)
    ? [...new Set(value.filter((id): id is string => typeof id === 'string'))]
    : [];
}

export function readPreferences(key: string): Preferences {
  try {
    const data = JSON.parse(localStorage.getItem(key) ?? 'null');
    if (!data || typeof data !== 'object') return defaultPreferences;
    const widths = Object.fromEntries(
      Object.entries(data.widths ?? {}).flatMap(([id, width]) =>
        typeof width === 'number' && Number.isFinite(width)
          ? [[id, Math.max(minWidth, Math.min(maxWidth, width))]]
          : [],
      ),
    );
    return { order: stringIds(data.order), hidden: stringIds(data.hidden), widths };
  } catch {
    return defaultPreferences;
  }
}

export function orderedIds(ids: string[], preferences: Preferences): string[] {
  const existing = preferences.order.filter((id) => ids.includes(id));
  return [...existing, ...ids.filter((id) => !existing.includes(id))];
}
