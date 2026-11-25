import { useState, type MouseEvent } from 'react';

export const schedulingCockpitPath = '/workbench/scheduling';
const windowName = 'ndi-scheduling-cockpit';
let cockpit: Window | null = null;

export function SchedulingCockpitLink() {
  const [blocked, setBlocked] = useState(false);

  function open(event: MouseEvent<HTMLAnchorElement>) {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey)
      return;
    event.preventDefault();

    if (cockpit && !cockpit.closed) {
      cockpit.focus();
      return;
    }
    // 同源具名窗口使主页面刷新后仍可找到原标签页。
    const target = window.open('', windowName);
    if (!target) {
      setBlocked(true);
      return;
    }
    cockpit = target;
    try {
      if (target.location.href === 'about:blank') target.location.replace(schedulingCockpitPath);
    } catch {
      // 用户可能已将原标签页导航到其他站点。仍可直接使用链接打开驾驶舱。
      cockpit = null;
      setBlocked(true);
      return;
    }
    target.focus();
    setBlocked(false);
  }

  return (
    <>
      <a href={schedulingCockpitPath} target={windowName} onClick={open}>
        排产驾驶舱
      </a>
      {blocked && (
        <div className="nav-popup-fallback" role="status">
          浏览器阻止了新标签页。
          <a href={schedulingCockpitPath} target={windowName}>
            打开排产驾驶舱
          </a>
        </div>
      )}
    </>
  );
}
