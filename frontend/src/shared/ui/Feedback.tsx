import type { ReactNode } from 'react';

export function Feedback({
  children,
  tone = 'muted',
}: {
  children: ReactNode;
  tone?: 'muted' | 'error' | 'success';
}) {
  return (
    <div className={`feedback feedback-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  );
}

export function Loading() {
  return <Feedback>正在加载…</Feedback>;
}
