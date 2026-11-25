import { useState, type MouseEvent } from 'react';

export const schedulingCockpitPath = '/workbench/scheduling';
export const revenueCockpitPath = '/workbench/revenue';
const windows = new Map<string, Window>();

export function CockpitLink({
  path,
  windowName,
  label,
}: {
  path: string;
  windowName: string;
  label: string;
}) {
  const [blocked, setBlocked] = useState(false);

  function open(event: MouseEvent<HTMLAnchorElement>) {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey)
      return;
    event.preventDefault();

    const cockpit = windows.get(windowName);
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
    windows.set(windowName, target);
    try {
      if (target.location.href === 'about:blank') target.location.replace(path);
    } catch {
      // 用户可能已将原标签页导航到其他站点。仍可直接使用链接打开驾驶舱。
      windows.delete(windowName);
      setBlocked(true);
      return;
    }
    target.focus();
    setBlocked(false);
  }

  return (
    <>
      <a href={path} target={windowName} onClick={open}>
        {label}
      </a>
      {blocked && (
        <div className="nav-popup-fallback" role="status">
          浏览器阻止了新标签页。
          <a href={path} target={windowName}>
            打开{label}
          </a>
        </div>
      )}
    </>
  );
}
