import { MonthPicker } from '../../shared/ui/MonthPicker';
import { useState } from 'react';

export interface MonthRange {
  from: string;
  to: string;
}

/** 各区域维护独立的输入与已应用月份，避免修改其他区域的筛选。 */
export function DashboardMonthRange({
  label,
  value,
  onApply,
  busy = false,
}: {
  label: string;
  value: MonthRange;
  onApply: (value: MonthRange) => void;
  busy?: boolean;
}) {
  const [draft, setDraft] = useState(value);
  return (
    <form
      className="dashboard-inline-filters"
      aria-label={`${label}时间筛选`}
      onSubmit={(event) => {
        event.preventDefault();
        if (draft.from && draft.to && draft.from <= draft.to) onApply({ ...draft });
      }}
    >
      <div className="month-filter-field">
        {label}月份起始
        <MonthPicker
          aria-label={label + '月份起始'}
          min="1000-01"
          max={draft.to}
          required
          value={draft.from}
          onChange={(selectedMonth) => setDraft({ ...draft, from: selectedMonth })}
        />
      </div>
      <div className="month-filter-field">
        {label}月份结束
        <MonthPicker
          aria-label={label + '月份结束'}
          min={draft.from}
          max="9999-12"
          required
          value={draft.to}
          onChange={(selectedMonth) => setDraft({ ...draft, to: selectedMonth })}
        />
      </div>
      <button className="secondary-button" disabled={busy}>
        应用{label}月份
      </button>
    </form>
  );
}
