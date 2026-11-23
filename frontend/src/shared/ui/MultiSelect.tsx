export function MultiSelect({
  label,
  values,
  options,
  onChange,
  disabledReason,
}: {
  label: string;
  values: string[];
  options: { value: string; label: string }[];
  onChange: (values: string[]) => void;
  disabledReason?: string;
}) {
  return (
    <div className="multi-select-field">
      <span>{label}</span>
      {disabledReason ? (
        <button
          type="button"
          className="filter-select-control themed-select"
          disabled
          aria-label={label}
        >
          {disabledReason}
        </button>
      ) : (
        <details
          className="multi-select"
          onBlur={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget))
              event.currentTarget.open = false;
          }}
          onKeyDown={(event) => {
            if (event.key !== 'Escape' || !event.currentTarget.open) return;
            event.stopPropagation();
            event.currentTarget.open = false;
            event.currentTarget.querySelector('summary')?.focus();
          }}
        >
          <summary className="filter-select-control themed-select" aria-label={label}>
            {values.length === 0
              ? '全部'
              : values.length === 1
                ? (options.find((option) => option.value === values[0])?.label ?? '已选 1 项')
                : `已选 ${values.length} 项`}
          </summary>
          <fieldset className="multi-select-options" aria-label={label}>
            <button
              type="button"
              className="text-button"
              disabled={!values.length}
              onClick={() => onChange([])}
            >
              清空
            </button>
            {options.map((option) => (
              <label key={option.value}>
                <input
                  type="checkbox"
                  checked={values.includes(option.value)}
                  onChange={(event) =>
                    onChange(
                      event.target.checked
                        ? [...values, option.value]
                        : values.filter((value) => value !== option.value),
                    )
                  }
                />
                {option.label}
              </label>
            ))}
          </fieldset>
        </details>
      )}
    </div>
  );
}
