import { useId, useRef, useState } from 'react';

export function MonthPicker({
  value,
  onChange,
  min,
  max,
  required = false,
  'aria-label': label,
}: {
  value: string;
  onChange: (value: string) => void;
  min?: string;
  max?: string;
  required?: boolean;
  'aria-label': string;
}) {
  const root = useRef<HTMLDetailsElement>(null);
  const trigger = useRef<HTMLElement>(null);
  const id = useId();
  const [year, setYear] = useState('');
  const minimum = min || '1000-01';
  const maximum = max || '9999-12';
  const activeYear = Number(year);
  const monthValue = (month: number) => `${year}-${String(month).padStart(2, '0')}`;
  const close = () => {
    if (root.current) root.current.open = false;
    trigger.current?.focus();
  };
  const choose = (month: string) => {
    onChange(month);
    close();
  };
  return (
    <details
      ref={root}
      className="month-picker"
      onBlur={(event) => {
        if (event.relatedTarget && !event.currentTarget.contains(event.relatedTarget))
          event.currentTarget.open = false;
      }}
      onKeyDown={(event) => {
        if (event.key === 'Escape' && root.current?.open) {
          event.stopPropagation();
          close();
        }
      }}
    >
      <summary
        ref={trigger}
        className="filter-select-control themed-select"
        aria-label={label}
        aria-description={value || '未选择月份'}
        aria-controls={id}
        onClick={() => {
          if (root.current?.open) return;
          const selected =
            value.slice(0, 4) ||
            new Intl.DateTimeFormat('en', {
              year: 'numeric',
              timeZone: 'Asia/Shanghai',
            }).format(new Date());
          setYear(
            String(
              Math.max(
                Number(minimum.slice(0, 4)),
                Math.min(Number(maximum.slice(0, 4)), Number(selected)),
              ),
            ),
          );
        }}
      >
        {value || '选择月份'}
      </summary>
      <div className="month-picker-panel" id={id} role="group" aria-label={label + '选项'}>
        <div className="month-picker-year">
          <button
            type="button"
            className="secondary-button"
            aria-label="上一年"
            disabled={activeYear <= Number(minimum.slice(0, 4))}
            onClick={() => setYear(String(activeYear - 1))}
          >
            ‹
          </button>
          <input
            type="number"
            aria-label="年份"
            value={year}
            inputMode="numeric"
            onChange={(event) => setYear(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') event.preventDefault();
            }}
          />
          <button
            type="button"
            className="secondary-button"
            aria-label="下一年"
            disabled={activeYear >= Number(maximum.slice(0, 4))}
            onClick={() => setYear(String(activeYear + 1))}
          >
            ›
          </button>
        </div>
        <div className="month-picker-grid">
          {Array.from({ length: 12 }, (_, index) => index + 1).map((month) => (
            <button
              type="button"
              key={month}
              className="secondary-button"
              disabled={
                !/^\d{4}$/.test(year) || monthValue(month) < minimum || monthValue(month) > maximum
              }
              aria-pressed={value === monthValue(month)}
              onClick={() => choose(monthValue(month))}
            >
              {month}月
            </button>
          ))}
        </div>
        {!required && (
          <button
            type="button"
            className="text-button"
            disabled={!value}
            onClick={() => choose('')}
          >
            清空
          </button>
        )}
      </div>
    </details>
  );
}
