import { useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import '../../features/scheduling-cockpit/scheduling-cockpit.css';

export function CockpitBoard({
  label,
  testId,
  height,
  className = '',
  children,
}: {
  label: string;
  testId: string;
  height: number;
  className?: string;
  children: ReactNode;
}) {
  const viewport = useRef<HTMLElement>(null);
  const [zoom, setZoom] = useState(1);
  useLayoutEffect(() => {
    const element = viewport.current!;
    const resize = () =>
      setZoom(Math.min(element.clientWidth / 1680, element.clientHeight / height));
    resize();
    const observer = new ResizeObserver(resize);
    observer.observe(element);
    return () => observer.disconnect();
  }, [height]);

  return (
    <main ref={viewport} className="sc-viewport" aria-label={label}>
      <div
        className={'sc-board ' + className}
        data-testid={testId}
        style={{ height, transform: `translate(-50%, -50%) scale(${zoom})` }}
      >
        {children}
      </div>
    </main>
  );
}

export function Panel({
  title,
  note,
  children,
  className = '',
}: {
  title: string;
  note?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={'sc-panel ' + className} aria-label={title}>
      <header>
        <h2>{title}</h2>
        {note && <span>{note}</span>}
      </header>
      {children}
    </section>
  );
}
