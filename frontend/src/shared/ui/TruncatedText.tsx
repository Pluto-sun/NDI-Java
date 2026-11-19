import { useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

export function TruncatedText({
  text,
  children,
  twoLines = false,
}: {
  text: string;
  children?: ReactNode;
  twoLines?: boolean;
}) {
  const [position, setPosition] = useState<{ left: number; bottom?: number; top?: number } | null>(
    null,
  );
  function show(element: HTMLElement) {
    if (element.scrollWidth <= element.clientWidth && element.scrollHeight <= element.clientHeight)
      return;
    const bounds = element.getBoundingClientRect();
    setPosition({
      left: Math.max(8, Math.min(bounds.left, window.innerWidth - 320)),
      ...(bounds.top < 130
        ? { top: bounds.bottom + 4 }
        : { bottom: window.innerHeight - bounds.top + 4 }),
    });
  }
  return (
    <span
      className={twoLines ? 'cell-text cell-text-two' : 'cell-text'}
      tabIndex={children ? undefined : 0}
      onMouseEnter={(event) => show(event.currentTarget)}
      onMouseLeave={(event) => {
        if (!event.currentTarget.contains(document.activeElement)) setPosition(null);
      }}
      onFocus={(event) => show(event.currentTarget)}
      onBlur={() => setPosition(null)}
      onKeyDown={(event) => {
        if (event.key === 'Escape') setPosition(null);
      }}
    >
      {children ?? text}
      {position &&
        createPortal(
          <span aria-hidden="true" className="cell-tooltip" style={position}>
            {text}
          </span>,
          document.body,
        )}
    </span>
  );
}
