export const NOTIFICATION_COUNTS_CHANGED = 'ndi:notification-counts-changed';

/** 通知相关业务完成后主动刷新导航，其他设备的变化由轮询补充。 */
export function refreshNotificationCounts() {
  window.dispatchEvent(new Event(NOTIFICATION_COUNTS_CHANGED));
}
