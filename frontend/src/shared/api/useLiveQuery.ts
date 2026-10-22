import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from './http';

/** 当前态数据定时读取；页面隐藏时暂停，离开时取消，不并发叠加刷新。 */
export function useLiveQuery<T>(load: (signal: AbortSignal) => Promise<T>, failureMessage: string) {
  const [data, setData] = useState<T | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  const [updatedAt, setUpdatedAt] = useState<string | null>(null);
  const refreshRef = useRef<() => Promise<void>>(async () => {});
  const refresh = useCallback(() => refreshRef.current(), []);

  useEffect(() => {
    setData(null);
    setUpdatedAt(null);
    setError('');
    let active = true;
    let inFlight = false;
    let controller: AbortController | null = null;
    async function run() {
      if (!active || inFlight) return;
      inFlight = true;
      controller = new AbortController();
      setBusy(true);
      try {
        const result = await load(controller.signal);
        if (!active) return;
        setData(result);
        setError('');
        setUpdatedAt(new Date().toISOString());
      } catch (cause) {
        if (active) {
          // 旧值仅用于临时网络失败；权限失效时不能继续展示缓存的业务数据。
          if (cause instanceof ApiError && (cause.status === 401 || cause.status === 403)) {
            setData(null);
            setUpdatedAt(null);
          }
          setError(cause instanceof Error ? cause.message : failureMessage);
        }
      } finally {
        if (active) setBusy(false);
        inFlight = false;
      }
    }
    refreshRef.current = run;
    const onVisible = () => {
      if (document.visibilityState === 'visible') void run();
    };
    void run();
    const timer = window.setInterval(onVisible, 30_000);
    window.addEventListener('focus', onVisible);
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      active = false;
      controller?.abort();
      window.clearInterval(timer);
      window.removeEventListener('focus', onVisible);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [load, failureMessage]);

  return { data, busy, error, updatedAt, refresh };
}
