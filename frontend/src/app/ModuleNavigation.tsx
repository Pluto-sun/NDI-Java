import { useState, type ReactNode } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { NotificationNavLinks } from '../features/approvals/NotificationNavLinks';

function MenuGroup({
  title,
  active,
  children,
}: {
  title: string;
  active: boolean;
  children: ReactNode;
}) {
  const [expanded, setExpanded] = useState(true);
  return (
    <section className={`nav-module${active ? ' nav-module-active' : ''}`}>
      <button
        className="nav-module-toggle"
        aria-expanded={expanded}
        onClick={() => setExpanded((value) => !value)}
      >
        <span>{title}</span>
        <span aria-hidden="true">{expanded ? '⌄' : '›'}</span>
      </button>
      {expanded && <div className="nav-module-children">{children}</div>}
    </section>
  );
}
export function ModuleNavigation({
  actorId,
  canCreate,
  canImport,
  admin,
}: {
  actorId: string;
  canCreate: boolean;
  canImport: boolean;
  admin: boolean;
}) {
  const { pathname } = useLocation();
  return (
    <nav aria-label="模块导航">
      <MenuGroup title="驾驶舱" active={pathname.startsWith('/workbench')}>
        <NavLink to="/workbench" end>
          项目管理驾驶舱
        </NavLink>
        <NavLink to="/workbench/revenue">计收驾驶舱</NavLink>
        <NavLink to="/workbench/scheduling">排产驾驶舱</NavLink>
        <NavLink to="/workbench/capability">能力指标驾驶舱</NavLink>
      </MenuGroup>
      <MenuGroup title="项目管理" active={pathname.startsWith('/projects')}>
        <NavLink to="/projects" end>
          项目列表
        </NavLink>
        {(canCreate || canImport) && <NavLink to="/projects/new">项目创建</NavLink>}
      </MenuGroup>
      <MenuGroup title="计收管理" active={pathname === '/revenue'}>
        <NavLink to="/revenue">计收台账</NavLink>
      </MenuGroup>
      <MenuGroup title="排产管理" active={pathname === '/scheduling'}>
        <NavLink to="/scheduling">排产计划</NavLink>
      </MenuGroup>
      <MenuGroup title="能力指标管理" active={pathname === '/capability'}>
        <NavLink to="/capability">能力指标</NavLink>
      </MenuGroup>
      <MenuGroup title="消息与审批" active={/^\/(notifications|approvals)/.test(pathname)}>
        <NotificationNavLinks actorId={actorId} />
      </MenuGroup>
      <MenuGroup title="个人中心" active={pathname === '/account' || pathname.startsWith('/admin')}>
        <NavLink to="/account">个人设置</NavLink>
        {admin && (
          <>
            <NavLink to="/admin/accounts">账号与组织</NavLink>
            <NavLink to="/admin/reviewer-provinces">接口人范围</NavLink>
            <NavLink to="/admin/inactivity">督办管理</NavLink>
            <NavLink to="/admin/backups">备份管理</NavLink>
          </>
        )}
      </MenuGroup>
    </nav>
  );
}
