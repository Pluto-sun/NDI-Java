import { revenueApi } from '../revenue/api';
import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { projectsApi } from '../projects/api';
import type { NotificationCounts } from '../projects/types';
import { NOTIFICATION_COUNTS_CHANGED } from './notificationEvents';

type Snapshot = { actorId: string; counts: NotificationCounts | null; failed: boolean };

export function useNotificationCounts(actorId: string) {
  const { pathname } = useLocation();
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  useEffect(() => {
    let active = true;
    let inFlight = false;
    let queued = false;
    const controller = new AbortController();
    async function refresh() {
      if (!active) return;
      if (inFlight) {
        queued = true;
        return;
      }
      inFlight = true;
      try {
        const [project, revenue] = await Promise.all([
          projectsApi.notificationCounts(controller.signal),
          revenueApi.counts(controller.signal),
        ]);
        const counts = {
          ...project,
          count: project.count + revenue.unread,
          approvalUnreadCount: project.approvalUnreadCount + revenue.unread,
          pendingApprovalCount: project.pendingApprovalCount + revenue.pending,
        };
        if (active) setSnapshot({ actorId, counts, failed: false });
      } catch {
        if (active) setSnapshot({ actorId, counts: null, failed: true });
      } finally {
        inFlight = false;
        if (active && queued) {
          queued = false;
          void refresh();
        }
      }
    }
    const onVisible = () => {
      if (document.visibilityState === 'visible') void refresh();
    };
    const onChanged = () => {
      void refresh();
    };
    void refresh();
    const timer = window.setInterval(onVisible, 30_000);
    window.addEventListener('focus', onVisible);
    document.addEventListener('visibilitychange', onVisible);
    window.addEventListener(NOTIFICATION_COUNTS_CHANGED, onChanged);
    return () => {
      active = false;
      controller.abort();
      window.clearInterval(timer);
      window.removeEventListener('focus', onVisible);
      document.removeEventListener('visibilitychange', onVisible);
      window.removeEventListener(NOTIFICATION_COUNTS_CHANGED, onChanged);
    };
  }, [actorId, pathname]);
  return snapshot?.actorId === actorId ? snapshot : { actorId, counts: null, failed: false };
}
