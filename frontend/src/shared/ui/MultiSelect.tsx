import { useEffect, useId, useRef, useState } from 'react';
import { useAnchoredPanel } from './useAnchoredPanel';

export type FilterOption = { value: string; label: string };

export function MultiSelect({
  label,
  values,
  options,
  onChange,
  disabledReason,
  optionsForSearch,
}: {
  label: string;
  values: string[];
  options: FilterOption[];
  onChange: (values: string[]) => void;
  disabledReason?: string;
  optionsForSearch?: (term: string) => FilterOption[];
}) {
  const panel = useRef<HTMLFieldSetElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const optionsId = useId();
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [active, setActive] = useState(0);
  const expanded = open && !disabledReason;
  function close() {
    setOpen(false);
    setSearch('');
    setActive(0);
  }
  useEffect(() => {
    if (disabledReason) close();
  }, [disabledReason]);
  useEffect(() => {
    if (expanded) input.current?.focus();
  }, [expanded]);
  useAnchoredPanel(expanded, trigger, panel, close, 216, 310);

  const selection = values
    .map((value) => options.find((item) => item.value === value)?.label ?? value)
    .join('；');
  const term = search.trim().toLocaleLowerCase();
  const candidates = optionsForSearch?.(term) ?? options;
  const visible = optionsForSearch
    ? candidates
    : candidates.filter(
        (option) =>
          option.label.toLocaleLowerCase().includes(term) ||
          option.value.toLocaleLowerCase().includes(term),
      );
  const activeIndex = Math.min(active, visible.length - 1);
  function toggle(value: string) {
    onChange(values.includes(value) ? values.filter((item) => item !== value) : [...values, value]);
  }
  function move(index: number, focusOption: boolean) {
    if (!visible.length) return;
    const next = (index + visible.length) % visible.length;
    setActive(next);
    const option = document.getElementById(`${optionsId}-${next}`);
    if (focusOption) option?.querySelector('input')?.focus({ preventScroll: true });
    const choices = option?.parentElement;
    if (option && choices) {
      const row = option.getBoundingClientRect();
      const frame = choices.getBoundingClientRect();
      if (row.top < frame.top) choices.scrollTop += row.top - frame.top;
      if (row.bottom > frame.bottom) choices.scrollTop += row.bottom - frame.bottom;
    }
  }

  return (
    <div className="multi-select-field">
      <span>{label}</span>
      <div
        className="multi-select"
        onBlur={(event) => {
          const next = event.relatedTarget;
          if (
            next instanceof Element &&
            next !== document.body &&
            !event.currentTarget.contains(next)
          )
            close();
        }}
        onKeyDown={(event) => {
          if (!expanded || event.nativeEvent.isComposing || event.keyCode === 229) return;
          if (event.key === 'Escape') {
            event.preventDefault();
            event.stopPropagation();
            close();
            trigger.current?.focus();
            return;
          }
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            move(
              activeIndex < 0
                ? event.key === 'ArrowDown'
                  ? 0
                  : visible.length - 1
                : activeIndex + (event.key === 'ArrowDown' ? 1 : -1),
              true,
            );
          } else if (event.key === 'Enter' && event.target === input.current) {
            event.preventDefault();
            if (visible[activeIndex]) toggle(visible[activeIndex].value);
          } else if (
            event.key === 'Enter' &&
            (event.target as HTMLElement).matches('input[type=checkbox]')
          ) {
            event.preventDefault();
            (event.target as HTMLElement).click();
          }
        }}
      >
        <button
          ref={trigger}
          type="button"
          className="filter-select-control themed-select"
          disabled={!!disabledReason}
          aria-label={label}
          aria-description={selection || undefined}
          title={selection || disabledReason || undefined}
          aria-expanded={expanded}
          aria-controls={expanded ? optionsId : undefined}
          onKeyDown={(event) => {
            if (!expanded && ['ArrowDown', 'ArrowUp'].includes(event.key)) {
              event.preventDefault();
              setOpen(true);
            }
          }}
          onClick={() => (expanded ? close() : setOpen(true))}
        >
          <span className="multi-select-selection">{disabledReason || selection || '全部'}</span>
        </button>
        {expanded && (
          <fieldset
            ref={panel}
            id={optionsId}
            className="multi-select-options searchable-options"
            aria-label={label}
            popover="manual"
          >
            <div className="multi-select-search-row">
              <input
                ref={input}
                type="text"
                role="combobox"
                aria-haspopup="dialog"
                aria-label={`搜索${label}`}
                aria-expanded="true"
                aria-controls={optionsId + '-options'}
                placeholder={optionsForSearch ? '输入年份或月份' : '输入关键词'}
                value={search}
                onChange={(event) => {
                  setSearch(event.target.value);
                  setActive(0);
                }}
              />
              <button
                type="button"
                className="text-button"
                disabled={!search}
                aria-label="清空搜索"
                onClick={() => {
                  setSearch('');
                  setActive(0);
                  input.current?.focus();
                }}
              >
                ×
              </button>
            </div>
            <div className="multi-select-selection-bar">
              <span aria-live="polite">{selection || '尚未选择'}</span>
              <button
                type="button"
                className="text-button"
                disabled={!values.length}
                onClick={() => onChange([])}
              >
                清空已选
              </button>
            </div>
            <div id={optionsId + '-options'} role="dialog" aria-label={`${label}选项`}>
              {visible.map((option, index) => {
                const selected = values.includes(option.value);
                return (
                  <label
                    key={option.value}
                    id={`${optionsId}-${index}`}
                    className={`multi-select-option${selected ? ' selected' : ''}${index === activeIndex ? ' active' : ''}`}
                  >
                    <input
                      type="checkbox"
                      checked={selected}
                      onFocus={() => setActive(index)}
                      onChange={() => toggle(option.value)}
                    />
                    <span>{option.label}</span>
                    {selected && (
                      <span className="multi-select-tick" aria-hidden="true">
                        ✓
                      </span>
                    )}
                  </label>
                );
              })}
              {!visible.length && <p className="multi-select-empty">无匹配选项</p>}
            </div>
          </fieldset>
        )}
      </div>
    </div>
  );
}

export function MonthMultiSelect({
  label,
  values,
  onChange,
}: {
  label: string;
  values: string[];
  onChange: (values: string[]) => void;
}) {
  const currentYear = Number(
    new Intl.DateTimeFormat('zh-CN', { year: 'numeric', timeZone: 'Asia/Shanghai' })
      .formatToParts(new Date())
      .find(({ type }) => type === 'year')?.value,
  );
  const initialYears = Array.from({ length: 6 }, (_, index) => currentYear + 1 - index);
  const months = (term: string) => {
    const text = term.replace(/^(\d{4})(\d{2})$/, '$1-$2');
    const typed = Number(text.match(/^\d{4}/)?.[0]);
    const years = [
      ...new Set([
        ...(typed >= 1900 && typed <= 9999 ? [typed] : []),
        ...initialYears,
        ...values
          .map((value) => Number(value.slice(0, 4)))
          .filter((year) => year >= 1900 && year <= 9999),
      ]),
    ];
    return years
      .flatMap((year) =>
        Array.from({ length: 12 }, (_, index) => {
          const value = `${year}-${String(12 - index).padStart(2, '0')}`;
          return { value, label: value };
        }),
      )
      .filter(({ value }) => value.includes(text));
  };
  return (
    <MultiSelect
      label={label}
      values={values.map((value) => value.replace(/^(\d{4})(\d{2})$/, '$1-$2'))}
      options={months('')}
      onChange={onChange}
      optionsForSearch={months}
    />
  );
}
