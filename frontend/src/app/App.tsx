import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { projectsApi } from '../features/projects/api';
import type { ProjectUser, ReferenceData } from '../features/projects/types';
import { ActorPicker } from '../features/local-auth/ActorPicker';
import { Feedback, Loading } from '../shared/ui/Feedback';
import { getActorId, setActorId } from '../shared/api/http';

const WorkbenchPage = lazy(() =>
  import('../features/workbench/WorkbenchPage').then((module) => ({
    default: module.WorkbenchPage,
  })),
);
const ProjectsPage = lazy(() =>
  import('../features/projects/ProjectsPage').then((module) => ({ default: module.ProjectsPage })),
);
const CreateProjectPage = lazy(() =>
  import('../features/projects/CreateProjectPage').then((module) => ({
    default: module.CreateProjectPage,
  })),
);
const ImportProjectsPage = lazy(() =>
  import('../features/projects/ImportProjectsPage').then((module) => ({
    default: module.ImportProjectsPage,
  })),
);
const ProjectDetailPage = lazy(() =>
  import('../features/projects/ProjectDetailPage').then((module) => ({
    default: module.ProjectDetailPage,
  })),
);
const ApprovalsPage = lazy(() =>
  import('../features/approvals/ApprovalsPage').then((module) => ({
    default: module.ApprovalsPage,
  })),
);
const ApprovalDetailPage = lazy(() =>
  import('../features/approvals/ApprovalsPage').then((module) => ({
    default: module.ApprovalDetailPage,
  })),
);
const NotificationsPage = lazy(() =>
  import('../features/approvals/NotificationsPage').then((module) => ({
    default: module.NotificationsPage,
  })),
);

export function App() {
  const [actors, setActors] = useState<ProjectUser[]>([]);
  const [me, setMe] = useState<ProjectUser | null>(null);
  const [reference, setReference] = useState<ReferenceData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadAccount = useCallback(async (id: string) => {
    setActorId(id);
    setError('');
    try {
      const [user, dictionaries] = await Promise.all([
        projectsApi.me(),
        projectsApi.referenceData(),
      ]);
      setMe(user);
      setReference(dictionaries);
    } catch (cause) {
      setMe(null);
      setReference(null);
      setError(cause instanceof Error ? cause.message : '账号加载失败');
    }
  }, []);

  useEffect(() => {
    let active = true;
    projectsApi
      .localActors()
      .then(async (users) => {
        if (!active) return;
        setActors(users);
        const id = getActorId();
        if (id && users.some((user) => user.id === id)) await loadAccount(id);
        else setActorId(null);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '账号列表加载失败');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [loadAccount]);

  if (loading)
    return (
      <main className="entry">
        <Loading />
      </main>
    );
  if (!me || !reference)
    return (
      <ActorPicker
        actors={actors}
        error={error}
        onSelect={loadAccount}
        onRetry={() => window.location.reload()}
      />
    );

  const canCreate =
    !me.roleCodes.includes('SYSTEM_ADMIN') &&
    me.roleCodes.some((role) =>
      [
        'CITY_REPORTER',
        'PROVINCE_REPORTER',
        'PROVINCE_DIGITAL_LEADER',
        'PROVINCE_INTELLIGENT_LEADER',
      ].includes(role),
    );
  const canImport =
    canCreate || me.roleCodes.some((role) => ['INTERFACE_REVIEWER', 'SYSTEM_ADMIN'].includes(role));
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        跳到主内容
      </a>
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">
            N
          </span>
          <span>
            NDI<small>项目管理</small>
          </span>
        </div>
        <nav aria-label="项目管理导航">
          <NavLink to="/workbench">项目工作台</NavLink>
          <NavLink to="/projects" end>
            项目列表
          </NavLink>
          <NavLink to="/approvals">审批待办</NavLink>
          <NavLink to="/notifications">消息通知</NavLink>
          {canCreate && <NavLink to="/projects/new">新建项目</NavLink>}
          {canImport && <NavLink to="/projects/import">Excel 导入</NavLink>}
        </nav>
        <div className="sidebar-foot">
          项目管理独立切片
          <br />
          排产、计收、能力指标待后续接入
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <span className="topbar-title">工业三化运营管理平台</span>
          <div className="account">
            <span>{me.displayName}</span>
            <button
              type="button"
              className="text-button"
              onClick={() => {
                setActorId(null);
                setMe(null);
                setReference(null);
              }}
            >
              切换测试账号
            </button>
          </div>
        </header>
        <main className="content" id="main-content">
          <div className="dev-banner">本地联调模式 · 当前账号由开发环境选择，不是生产登录</div>
          <Suspense fallback={<Loading />}>
            <Routes>
              <Route path="/" element={<Navigate to="/workbench" replace />} />
              <Route path="/workbench" element={<WorkbenchPage reference={reference} />} />
              <Route
                path="/projects"
                element={<ProjectsPage reference={reference} canCreate={canCreate} />}
              />
              <Route
                path="/projects/new"
                element={
                  canCreate ? (
                    <CreateProjectPage me={me} reference={reference} />
                  ) : (
                    <Navigate to="/projects" replace />
                  )
                }
              />
              <Route
                path="/projects/import"
                element={canImport ? <ImportProjectsPage /> : <Navigate to="/projects" replace />}
              />
              <Route
                path="/projects/:id"
                element={<ProjectDetailPage reference={reference} me={me} />}
              />
              <Route path="/approvals" element={<ApprovalsPage reference={reference} />} />
              <Route path="/notifications" element={<NotificationsPage />} />
              <Route
                path="/approvals/:id"
                element={<ApprovalDetailPage me={me} reference={reference} />}
              />
              <Route
                path="*"
                element={<Feedback tone="error">页面不存在，请从左侧导航重新进入。</Feedback>}
              />
            </Routes>
          </Suspense>
        </main>
      </div>
    </div>
  );
}
