import { useId } from 'react';
import { NavLink } from 'react-router-dom';
import { useNotificationCounts } from './useNotificationCounts';

export function NotificationNavLinks({ actorId }: { actorId: string }) {
  const { counts, failed } = useNotificationCounts(actorId);
  const id = useId();
  const unavailable = failed ? '数量暂不可用，请稍后重试' : '数量正在加载';
  const messages = counts
    ? `未读消息 ${counts.count} 条，其中未读审批通知 ${counts.approvalUnreadCount} 条、待处理督办 ${counts.inactivityPendingCount} 项`
    : unavailable;
  const approvals = counts ? `待审批申请 ${counts.pendingApprovalCount} 项` : unavailable;
  const text = (value: number | undefined) =>
    value === undefined ? (failed ? '!' : '…') : value > 99 ? '99+' : String(value);
  return (
    <>
      <NavLink className="nav-with-count" to="/approvals" aria-describedby={`${id}-approvals`}>
        审批待办
        <span className="nav-count" aria-hidden="true" title={approvals}>
          {text(counts?.pendingApprovalCount)}
        </span>
      </NavLink>
      <NavLink className="nav-with-count" to="/notifications" aria-describedby={`${id}-messages`}>
        消息通知
        <span className="nav-count" aria-hidden="true" title={messages}>
          {text(counts?.count)}
        </span>
      </NavLink>
      <span id={`${id}-approvals`} className="sr-only">
        {approvals}
      </span>
      <span id={`${id}-messages`} className="sr-only">
        {messages}
      </span>
      <span className="sr-only" role="status" aria-live="polite" aria-atomic="true">
        {counts ? `${approvals}；${messages}` : `通知${unavailable}`}
      </span>
    </>
  );
}
