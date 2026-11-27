import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';

/** 列表内容加载后恢复纵向位置及表格横向位置。 */
export function useLedgerScroll(ready: boolean) {
  const { pathname, search } = useLocation();
  const key = `ndi.ledger-scroll:${pathname}${search}`;
  const table = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!ready) return;
    const saved = sessionStorage.getItem(key);
    if (!saved) return;
    const frame = requestAnimationFrame(() => {
      const [top, left, innerTop = 0] = saved.split(',').map(Number);
      if (table.current) table.current.scrollTo(left, innerTop);
      window.scrollTo(0, top);
      sessionStorage.removeItem(key);
    });
    return () => cancelAnimationFrame(frame);
  }, [key, ready]);

  return {
    table,
    remember: () =>
      sessionStorage.setItem(
        key,
        `${window.scrollY},${table.current?.scrollLeft ?? 0},${table.current?.scrollTop ?? 0}`,
      ),
    state: { listSearch: search },
  };
}
