import { useEffect, useId, useRef, useState } from 'react';

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
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const optionsId = useId();
  const [open, setOpen] = useState(false);
  useEffect(() => {
    if (disabledReason) {
      setOpen(false);
      return;
    }
    if (!open) return;
    const closeOutside = (event: PointerEvent) => {
      if (event.target instanceof Node && !root.current?.contains(event.target)) setOpen(false);
    };
    document.addEventListener('pointerdown', closeOutside, true);
    return () => document.removeEventListener('pointerdown', closeOutside, true);
  }, [disabledReason, open]);

  const expanded = open && !disabledReason;
  const selection =
    values.length === 0
      ? '全部'
      : values.length === 1
        ? (options.find((option) => option.value === values[0])?.label ?? '已选 1 项')
        : `已选 ${values.length} 项`;

  return (
    <div className="multi-select-field">
      <span>{label}</span>
      <div
        ref={root}
        className="multi-select"
        onBlur={(event) => {
          // 点击选项名称时可能短暂失焦，由外部点击和明确的焦点转移负责收起。
          const next = event.relatedTarget;
          if (
            next instanceof Element &&
            next !== document.body &&
            !event.currentTarget.contains(next)
          )
            setOpen(false);
        }}
        onKeyDown={(event) => {
          if (event.key !== 'Escape' || !expanded) return;
          event.stopPropagation();
          setOpen(false);
          trigger.current?.focus();
        }}
      >
        <button
          ref={trigger}
          type="button"
          className="filter-select-control themed-select"
          disabled={!!disabledReason}
          aria-label={label}
          aria-expanded={expanded}
          aria-controls={expanded ? optionsId : undefined}
          onClick={() => setOpen(!expanded)}
        >
          {disabledReason || selection}
        </button>
        {expanded && (
          <fieldset id={optionsId} className="multi-select-options" aria-label={label}>
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
                <span>{option.label}</span>
              </label>
            ))}
          </fieldset>
        )}
      </div>
    </div>
  );
}
