import {
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type SelectHTMLAttributes,
} from 'react';
import { createPortal } from 'react-dom';

/** 保留原生表单、校验及变更事件，使用页面内选项替代操作系统菜单。 */
export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  const control = useRef<HTMLSelectElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const id = useId();
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const [position, setPosition] = useState({ left: 0, top: 0, width: 200, maxHeight: 280 });
  const options = control.current ? Array.from(control.current.options) : [];

  const close = () => {
    setOpen(false);
    control.current?.focus();
  };
  function show() {
    if (props.disabled || !control.current) return;
    const rect = control.current.getBoundingClientRect();
    const below = innerHeight - rect.bottom - 12;
    const height = Math.min(280, Math.max(below, rect.top - 12));
    const width = Math.min(innerWidth - 24, Math.max(220, rect.width));
    setPosition({
      left: Math.max(12, Math.min(rect.left, innerWidth - width - 12)),
      top: below >= Math.min(180, height) ? rect.bottom + 5 : Math.max(12, rect.top - height - 5),
      width,
      maxHeight: height,
    });
    setActive(control.current.selectedIndex);
    setOpen(true);
  }
  function choose(index: number) {
    const element = control.current;
    if (!element || element.disabled || !element.options[index] || element.options[index].disabled)
      return;
    element.value = element.options[index].value;
    element.dispatchEvent(new Event('change', { bubbles: true }));
    close();
  }
  useLayoutEffect(() => {
    if (!open) return;
    panel.current?.showPopover?.();
    panel.current?.querySelector('[data-active="true"]')?.scrollIntoView({ block: 'nearest' });
    return () => {
      panel.current?.hidePopover?.();
    };
  }, [open]);
  useEffect(() => {
    if (!open) return;
    panel.current?.querySelector('[data-active="true"]')?.scrollIntoView({ block: 'nearest' });
  }, [active, open]);
  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => {
      if (!panel.current?.contains(event.target as Node) && event.target !== control.current)
        setOpen(false);
    };
    const resize = () => setOpen(false);
    document.addEventListener('pointerdown', outside, true);
    window.addEventListener('resize', resize);
    return () => {
      document.removeEventListener('pointerdown', outside, true);
      window.removeEventListener('resize', resize);
    };
  }, [open]);

  return (
    <>
      <select
        {...props}
        ref={control}
        className={`themed-select ${props.className ?? ''}`}
        onPointerDown={(event) => {
          props.onPointerDown?.(event);
          if (!event.defaultPrevented && event.pointerType === 'touch') {
            event.preventDefault();
            control.current?.focus();
            if (open) close();
            else show();
          }
        }}
        aria-expanded={open}
        aria-controls={open ? id : undefined}
        aria-activedescendant={open ? `${id}-${active}` : undefined}
        onMouseDown={(event) => {
          props.onMouseDown?.(event);
          if (!event.defaultPrevented) {
            event.preventDefault();
            control.current?.focus();
            if (open) close();
            else show();
          }
        }}
        onKeyDown={(event) => {
          props.onKeyDown?.(event);
          if (event.defaultPrevented) return;
          if (event.key === 'Escape' && open) {
            event.preventDefault();
            event.stopPropagation();
            close();
          } else if (event.key === 'Tab') setOpen(false);
          else if (['ArrowDown', 'ArrowUp', 'Home', 'End', ' ', 'Enter'].includes(event.key)) {
            event.preventDefault();
            if (!open) {
              show();
              return;
            }
            if (event.key === 'Enter' || event.key === ' ') choose(active);
            else {
              const enabled = options
                .map((option, index) => (option.disabled ? -1 : index))
                .filter((index) => index >= 0);
              const current = enabled.indexOf(active);
              setActive(
                event.key === 'Home'
                  ? enabled[0]
                  : event.key === 'End'
                    ? enabled.at(-1)!
                    : (enabled[
                        Math.max(
                          0,
                          Math.min(
                            enabled.length - 1,
                            current + (event.key === 'ArrowDown' ? 1 : -1),
                          ),
                        )
                      ] ?? active),
              );
            }
          } else if (event.key.length === 1 && !event.ctrlKey && !event.metaKey) {
            event.preventDefault();
            if (!open) show();
            const found = options.findIndex(
              (option) =>
                !option.disabled &&
                option.label.toLocaleLowerCase().startsWith(event.key.toLocaleLowerCase()),
            );
            if (found >= 0) setActive(found);
          }
        }}
        onChange={(event) => {
          props.onChange?.(event);
          setOpen(false);
        }}
        onBlur={(event) => {
          props.onBlur?.(event);
        }}
      />
      {open &&
        createPortal(
          <div
            ref={panel}
            id={id}
            role="listbox"
            aria-label="可选项"
            popover="manual"
            className="select-options"
            style={{ ...position }}
            onMouseDown={(event) => event.preventDefault()}
          >
            {options.map((option, index) => (
              <div
                key={`${option.value}:${index}`}
                id={`${id}-${index}`}
                role="option"
                aria-selected={control.current?.value === option.value}
                aria-disabled={option.disabled}
                data-active={active === index}
                className="select-option"
                onPointerMove={() => {
                  if (!option.disabled) setActive(index);
                }}
                onClick={() => choose(index)}
              >
                <span>{option.label}</span>
                <span aria-hidden="true">{control.current?.value === option.value ? '✓' : ''}</span>
              </div>
            ))}
          </div>,
          control.current?.closest('dialog') ?? document.body,
        )}
    </>
  );
}
