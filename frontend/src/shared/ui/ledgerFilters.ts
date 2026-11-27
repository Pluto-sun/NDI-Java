import { useEffect, useState } from 'react';

export function filterValues(params: URLSearchParams, name: string) {
  return [
    ...new Set(
      params
        .getAll(name)
        .flatMap((value) => value.split(','))
        .map((value) => value.trim())
        .filter(Boolean),
    ),
  ];
}

export function replaceFilter(params: URLSearchParams, name: string, values: string | string[]) {
  const next = new URLSearchParams(params);
  next.delete(name);
  for (const value of typeof values === 'string' ? [values] : values)
    if (value.trim()) next.append(name, value.trim());
  return next;
}

export function filterPage(params: URLSearchParams, page: number) {
  return replaceFilter(params, 'page', page ? String(page) : '');
}

export function useLedgerFilterDraft(query: string) {
  const [draft, setDraft] = useState(() => new URLSearchParams(query));
  useEffect(() => setDraft(new URLSearchParams(query)), [query]);
  return {
    text: (name: string) => draft.get(name) ?? '',
    values: (name: string) => filterValues(draft, name),
    change: (name: string, value: string | string[]) =>
      setDraft((current) => {
        if (Array.isArray(value)) return replaceFilter(current, name, value);
        const next = new URLSearchParams(current);
        next.set(name, value);
        return next;
      }),
    provinces: (values: string[], cities: { id: string; provinceId: string }[]) =>
      setDraft((current) => {
        const currentCities = filterValues(current, 'cityId');
        const next = replaceFilter(current, 'provinceId', values);
        return replaceFilter(
          next,
          'cityId',
          values.length === 1
            ? currentCities.filter((id) =>
                cities.some((city) => city.id === id && city.provinceId === values[0]),
              )
            : [],
        );
      }),
    apply: () =>
      new URLSearchParams(
        [...filterPage(draft, 0)].flatMap(([key, value]) =>
          value.trim() ? [[key, value.trim()]] : [],
        ),
      ),
    reset: () => setDraft(new URLSearchParams()),
  };
}

export function locationOptions<T extends { id: string; provinceId: string; name: string }>(
  provinces: string[],
  cities: T[],
  selected: string[],
) {
  const province = provinces.length === 1 ? provinces[0] : undefined;
  const allowed = province ? cities.filter((city) => city.provinceId === province) : [];
  return {
    options: allowed.map(({ id, name }) => ({ value: id, label: name })),
    values: selected.filter((id) => allowed.some((city) => city.id === id)),
    disabledReason: province ? undefined : '请先单选省份',
  };
}
