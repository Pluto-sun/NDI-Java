import { useEffect, useRef, useState, type ReactNode } from 'react';

export function Feedback({
  children,
  tone = 'muted',
}: {
  children: ReactNode;
  tone?: 'muted' | 'error' | 'success';
}) {
  if (tone === 'success') return <SuccessNotice>{children}</SuccessNotice>;
  return (
    <div className={`feedback feedback-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  );
}

export function Loading() {
  return <Feedback>正在加载…</Feedback>;
}

/** 结果提示独立计时，持续性错误和加载状态仍由 Feedback 保留。 */
function SuccessNotice({ children }: { children: ReactNode }) {
  const [remaining, setRemaining] = useState(5_000);
  const paused = useRef({ hover: false, focus: false });
  const message = children;
  useEffect(() => {
    setRemaining(5_000);
  }, [message]);
  const visible = remaining > 0;
  useEffect(() => {
    if (!visible) return;
    let previous = performance.now();
    const timer = window.setInterval(() => {
      const now = performance.now(),
        elapsed = now - previous;
      previous = now;
      if (!paused.current.hover && !paused.current.focus)
        setRemaining((value) => Math.max(0, value - elapsed));
    }, 100);
    return () => window.clearInterval(timer);
  }, [visible]);
  if (remaining <= 0) return null;
  return (
    <div
      className="feedback feedback-success transient-notice"
      role="status"
      onMouseEnter={() => {
        paused.current.hover = true;
      }}
      onMouseLeave={() => {
        paused.current.hover = false;
      }}
      onFocusCapture={() => {
        paused.current.focus = true;
      }}
      onBlurCapture={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) paused.current.focus = false;
      }}
    >
      <span>{children}</span>
      <span className="notice-countdown" aria-hidden="true">
        {Math.ceil(remaining / 1000)}s
      </span>
      <button
        type="button"
        className="text-button"
        aria-label="关闭成功提示"
        onClick={() => setRemaining(0)}
      >
        关闭
      </button>
    </div>
  );
}
