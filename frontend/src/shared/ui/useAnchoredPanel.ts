import { useLayoutEffect, useRef, type RefObject } from 'react';

/** 让浮层跟随触发框，并在触发框离开滚动区域时关闭。 */
export function useAnchoredPanel(
  open: boolean,
  anchor: RefObject<HTMLElement | null>,
  panel: RefObject<HTMLElement | null>,
  onClose: () => void,
  minimumWidth: number,
  maximumHeight = 280,
) {
  const close = useRef(onClose);
  close.current = onClose;
  useLayoutEffect(() => {
    const trigger = anchor.current;
    const popup = panel.current;
    if (!open || !trigger || !popup) return;
    let frame = 0;
    const viewport = window.visualViewport;
    function position() {
      if (!trigger || !popup || !trigger.isConnected || !popup.isConnected) {
        close.current();
        return;
      }
      const rect = trigger.getBoundingClientRect();
      const leftEdge = viewport?.offsetLeft ?? 0;
      const topEdge = viewport?.offsetTop ?? 0;
      const rightEdge = leftEdge + (viewport?.width ?? document.documentElement.clientWidth);
      const bottomEdge = topEdge + (viewport?.height ?? document.documentElement.clientHeight);
      if (
        !rect.width ||
        !rect.height ||
        rect.bottom <= topEdge ||
        rect.top >= bottomEdge ||
        rect.right <= leftEdge ||
        rect.left >= rightEdge
      ) {
        close.current();
        return;
      }
      const gap = 5;
      const edge = 8;
      const above = Math.max(0, rect.top - topEdge - gap - edge);
      const below = Math.max(0, bottomEdge - rect.bottom - gap - edge);
      const opensAbove = below < Math.min(180, popup.scrollHeight) && above > below;
      const available = opensAbove ? above : below;
      const width = Math.min(rightEdge - leftEdge - 2 * edge, Math.max(rect.width, minimumWidth));
      const left = Math.max(leftEdge + edge, Math.min(rect.left, rightEdge - width - edge));
      Object.assign(popup.style, {
        position: 'fixed',
        inset: 'auto',
        margin: '0',
        boxSizing: 'border-box',
        left: left + 'px',
        top: opensAbove ? 'auto' : rect.bottom + gap + 'px',
        bottom: opensAbove ? window.innerHeight - rect.top + gap + 'px' : 'auto',
        width: width + 'px',
        maxHeight: Math.min(maximumHeight, available) + 'px',
      });
    }
    const schedule = (event?: Event) => {
      if (event?.target instanceof Node && popup.contains(event.target)) return;
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(position);
    };
    const outside = (event: PointerEvent) => {
      if (
        event.target instanceof Node &&
        !trigger.contains(event.target) &&
        !popup.contains(event.target)
      )
        close.current();
    };
    popup.showPopover?.();
    position();
    const intersection = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) close.current();
      else schedule();
    });
    intersection.observe(trigger);
    const resize = new ResizeObserver(() => schedule());
    resize.observe(trigger);
    resize.observe(popup);
    window.addEventListener('scroll', schedule, true);
    window.addEventListener('resize', schedule);
    viewport?.addEventListener('scroll', schedule);
    viewport?.addEventListener('resize', schedule);
    document.addEventListener('pointerdown', outside, true);
    return () => {
      cancelAnimationFrame(frame);
      intersection.disconnect();
      resize.disconnect();
      window.removeEventListener('scroll', schedule, true);
      window.removeEventListener('resize', schedule);
      viewport?.removeEventListener('scroll', schedule);
      viewport?.removeEventListener('resize', schedule);
      document.removeEventListener('pointerdown', outside, true);
      if (popup.hidePopover && popup.matches(':popover-open')) popup.hidePopover();
    };
  }, [open, anchor, panel, minimumWidth, maximumHeight]);
}
