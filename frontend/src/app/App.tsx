import { IntelligencePages, intelligenceTitles } from '../features/capabilities/IntelligencePages';
import {
  HistoricalSnapshotsPage,
  HistoricalSnapshotWizard,
} from '../features/admin/HistoricalSnapshotsPage';
import { BrandLogo } from '../shared/ui/BrandLogo';
import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { Link, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { projectsApi } from '../features/projects/api';
import type { ProjectUser, ReferenceData } from '../features/projects/types';
import { ActorPicker } from '../features/local-auth/ActorPicker';
import { Feedback, Loading } from '../shared/ui/Feedback';
import {
  ApiError,
  configureSessionMode,
  getActorId,
  setActorId,
  SESSION_EXPIRED_EVENT,
} from '../shared/api/http';
import { authApi, type AccountProfile, type AuthenticationMode } from '../features/auth/api';
import { LoginPage } from '../features/auth/LoginPage';
import { AccountSettingsPage } from '../features/auth/AccountSettingsPage';
import { ModuleNavigation } from './ModuleNavigation';
import { pageTitle } from './pageTitle';
import { ProjectStageTabs } from '../shared/ui/ProjectStageTabs';
import { Modal } from '../shared/ui/Modal';
import {
  home,
  projectEntryPermissions,
  rememberLogout,
  resumeDestination,
  takeLogoutDestination,
} from './sessionDestination';
import { DashboardControls, ModuleDashboard } from '../features/module-dashboards/ModuleDashboard';

const GewuDeploymentsPage = lazy(() =>
  import('../features/capabilities/GewuDeployments').then((module) => ({
    default: module.GewuDeploymentsPage,
  })),
);
const ImportGewuDeploymentsPage = lazy(() =>
  import('../features/capabilities/GewuDeployments').then((module) => ({
    default: module.ImportGewuDeploymentsPage,
  })),
);
const ProductionListPage = lazy(() =>
  import('../features/production/ProductionPages').then((module) => ({
    default: module.ProductionListPage,
  })),
);
const NewProductionPage = lazy(() =>
  import('../features/production/ProductionPages').then((module) => ({
    default: module.NewProductionPage,
  })),
);
const ImportProductionPage = lazy(() =>
  import('../features/production/ProductionPages').then((module) => ({
    default: module.ImportProductionPage,
  })),
);

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
const RevenueListPage = lazy(() =>
  import('../features/revenue/RevenuePages').then((m) => ({ default: m.RevenueListPage })),
);
const NewRevenuePage = lazy(() =>
  import('../features/revenue/RevenuePages').then((m) => ({ default: m.NewRevenuePage })),
);
const ImportRevenuePage = lazy(() =>
  import('../features/revenue/RevenuePages').then((m) => ({ default: m.ImportRevenuePage })),
);
const RevenueDetailPage = lazy(() =>
  import('../features/revenue/RevenuePages').then((m) => ({ default: m.RevenueDetailPage })),
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
const AccountsPage = lazy(() =>
  import('../features/admin/AccountsPage').then((module) => ({ default: module.AccountsPage })),
);
const BackupsPage = lazy(() =>
  import('../features/admin/BackupsPage').then((module) => ({ default: module.BackupsPage })),
);
const InactivityAdministrationPage = lazy(() =>
  import('../features/admin/InactivityAdministrationPage').then((module) => ({
    default: module.InactivityAdministrationPage,
  })),
);
const ReviewerProvincesPage = lazy(() =>
  import('../features/admin/ReviewerProvincesPage').then((module) => ({
    default: module.ReviewerProvincesPage,
  })),
);

export function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const [actors, setActors] = useState<ProjectUser[]>([]);
  const [me, setMe] = useState<ProjectUser | null>(null);
  const [reference, setReference] = useState<ReferenceData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [mode, setMode] = useState<AuthenticationMode | null>(null);
  const [profile, setProfile] = useState<AccountProfile | null>(null);
  const [message, setMessage] = useState('');
  const [loggingOut, setLoggingOut] = useState(false);
  const [confirmLogout, setConfirmLogout] = useState(false);
  const [loginTarget, setLoginTarget] = useState<string | null>(null);
  const [logoutError, setLogoutError] = useState('');

  function clearAccount(notice = '') {
    setActorId(null);
    setMe(null);
    setProfile(null);
    setReference(null);
    setMessage(notice);
  }

  async function loadSession(onAuthenticated?: (user: AccountProfile) => Promise<void>) {
    const user = await authApi.me();
    const dictionaries = await projectsApi.referenceData();
    await onAuthenticated?.(user);
    setProfile(user);
    setMe(user);
    setReference(dictionaries);
    setError('');
  }

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
      setProfile(await authApi.me());
    } catch (cause) {
      setMe(null);
      setReference(null);
      setError(cause instanceof Error ? cause.message : '账号加载失败');
    }
  }, []);

  useEffect(() => {
    let active = true;
    authApi
      .mode()
      .then(async ({ mode: selectedMode }) => {
        if (!active) return;
        configureSessionMode(selectedMode === 'SESSION');
        setMode(selectedMode);
        if (selectedMode === 'SESSION') {
          try {
            await loadSession();
          } catch (cause) {
            if (!(cause instanceof ApiError && cause.status === 401)) throw cause;
          }
          return;
        }
        const users = await projectsApi.localActors();
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

  useEffect(() => {
    const expired = () => {
      clearAccount('登录已失效，请重新登录。');
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  }, []);

  if (loading)
    return (
      <main className="entry">
        <Loading />
      </main>
    );
  if ((!me || !reference) && mode === 'SESSION' && location.pathname !== '/login') {
    return (
      <Navigate
        to="/login"
        replace
        state={{ returnTo: location.pathname + location.search + location.hash }}
      />
    );
  }
  if (!me || !reference)
    if (mode !== 'LOCAL_ACTOR')
      return (
        <LoginPage
          message={message}
          connectionError={error}
          onLogin={async (login, password) => {
            await authApi.login(login, password);
            await loadSession(async (user) => {
              const saved = takeLogoutDestination();
              if (saved)
                setLoginTarget(
                  saved.userId === user.id ? await resumeDestination(user, saved.path) : home,
                );
              else setLoginTarget(location.state?.returnTo ?? home);
            });
            setMessage('');
          }}
        />
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

  const { canCreate, canImport } = projectEntryPermissions(me);
  async function logout() {
    if (!me || loggingOut) return;
    setLoggingOut(true);
    setLogoutError('');
    try {
      await authApi.logout();
      rememberLogout(me.id, location.pathname + location.search + location.hash);
      setConfirmLogout(false);
      setLoginTarget(null);
      clearAccount('已退出登录。');
      navigate('/login', { replace: true, state: null });
    } catch (cause) {
      setLogoutError(cause instanceof Error ? cause.message : '退出失败，请重试');
    } finally {
      setLoggingOut(false);
    }
  }
  const title = pageTitle(location.pathname);
  const dashboardModule = [
    '/workbench/scheduling',
    '/workbench/revenue',
    '/workbench/capability',
  ].includes(location.pathname)
    ? (location.pathname.split('/')[2] as 'scheduling' | 'revenue' | 'capability')
    : null;
  const hasStages = ['/projects', '/workbench'].includes(location.pathname);
  const stage = new URLSearchParams(location.search).get('stage') ?? '';
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        跳到主内容
      </a>
      <aside className="sidebar">
        <BrandLogo />
        <ModuleNavigation
          actorId={me.id}
          canCreate={canCreate}
          canImport={canImport}
          admin={me.roleCodes.includes('SYSTEM_ADMIN')}
        />
      </aside>
      <div className="main-shell">
        <header className={dashboardModule ? 'topbar module-dashboard-topbar' : 'topbar'}>
          <div className="topbar-main">
            <div className="topbar-breadcrumb">
              <span className="topbar-title">工业三化运营管理平台</span>
              {title && (
                <>
                  <span aria-hidden="true">/</span>
                  <h1>{title}</h1>
                </>
              )}
            </div>
            {dashboardModule && <DashboardControls module={dashboardModule} />}
            {hasStages && (
              <ProjectStageTabs
                value={stage}
                onChange={(value) => {
                  const params = new URLSearchParams(location.search);
                  if (value) params.set('stage', value);
                  else params.delete('stage');
                  params.delete('page');
                  navigate({ pathname: location.pathname, search: params.toString() });
                }}
              />
            )}
          </div>
          <div className="account">
            <span>{me.displayName}</span>
            <button
              type="button"
              className="text-button"
              disabled={loggingOut}
              onClick={() => {
                if (mode === 'LOCAL_ACTOR') clearAccount();
                else {
                  setLogoutError('');
                  setConfirmLogout(true);
                }
              }}
            >
              {mode === 'LOCAL_ACTOR' ? '切换测试账号' : loggingOut ? '正在退出…' : '退出登录'}
            </button>
          </div>
        </header>
        {confirmLogout && (
          <Modal title="确认退出登录" busy={loggingOut} onClose={() => setConfirmLogout(false)}>
            <h2 tabIndex={-1}>退出当前账号？</h2>
            {logoutError && <Feedback tone="error">{logoutError}</Feedback>}
            <div className="form-actions">
              <button
                className="secondary-button"
                disabled={loggingOut}
                onClick={() => setConfirmLogout(false)}
              >
                取消
              </button>
              <button
                className="primary-button"
                disabled={loggingOut}
                onClick={() => void logout()}
              >
                {loggingOut ? '正在退出…' : '确认退出'}
              </button>
            </div>
          </Modal>
        )}
        <main className="content" id="main-content">
          {error && <Feedback tone="error">{error}</Feedback>}
          <Suspense fallback={<Loading />}>
            <Routes>
              {me.roleCodes.includes('SYSTEM_ADMIN') && (
                <>
                  <Route path="/admin/historical-snapshots" element={<HistoricalSnapshotsPage />} />
                  <Route
                    path="/admin/historical-snapshots/import"
                    element={<HistoricalSnapshotWizard />}
                  />
                </>
              )}
              <Route
                path="/admin/backups"
                element={
                  me.roleCodes.includes('SYSTEM_ADMIN') ? (
                    <BackupsPage localDevelopment={mode === 'LOCAL_ACTOR'} />
                  ) : (
                    <Navigate to="/projects" replace />
                  )
                }
              />
              <Route
                path="/admin/inactivity"
                element={
                  me.roleCodes.includes('SYSTEM_ADMIN') ? (
                    <InactivityAdministrationPage />
                  ) : (
                    <Navigate to="/projects" replace />
                  )
                }
              />
              <Route
                path="/admin/reviewer-provinces"
                element={
                  me.roleCodes.includes('SYSTEM_ADMIN') ? (
                    <ReviewerProvincesPage />
                  ) : (
                    <Navigate to="/projects" replace />
                  )
                }
              />
              <Route
                path="/admin/accounts"
                element={
                  me.roleCodes.includes('SYSTEM_ADMIN') ? (
                    <AccountsPage
                      myId={me.id}
                      onReferenceChanged={async () => {
                        setReference(await projectsApi.referenceData());
                        if (mode === 'SESSION') {
                          const updated = await authApi.me();
                          setMe(updated);
                          setProfile(updated);
                        }
                      }}
                    />
                  ) : (
                    <Navigate to="/projects" replace />
                  )
                }
              />
              {profile && (
                <Route
                  path="/account"
                  element={
                    <AccountSettingsPage
                      profile={profile}
                      onUpdated={(user) => {
                        setMe(user);
                        setProfile(user);
                      }}
                      onPasswordChanged={() => clearAccount('密码已修改，请重新登录。')}
                      onLoginNameChanged={() => clearAccount('登录账号已修改，请用新账号登录。')}
                    />
                  }
                />
              )}
              <Route path="/" element={<Navigate to="/workbench" replace />} />
              <Route path="/workbench" element={<WorkbenchPage reference={reference} />} />
              <Route
                path="/workbench/revenue"
                element={<ModuleDashboard key="revenue" module="revenue" />}
              />
              <Route
                path="/workbench/scheduling"
                element={<ModuleDashboard key="scheduling" module="scheduling" />}
              />
              <Route
                path="/workbench/capability"
                element={<ModuleDashboard key="capability" module="capability" />}
              />
              <Route
                path="/revenue"
                element={<RevenueListPage admin={me.roleCodes.includes('SYSTEM_ADMIN')} />}
              />
              <Route path="/revenue/new" element={<NewRevenuePage />} />
              <Route path="/revenue/import" element={<ImportRevenuePage />} />
              <Route path="/revenue/:id" element={<RevenueDetailPage me={me} />} />
              <Route
                path="/scheduling"
                element={<ProductionListPage admin={me.roleCodes.includes('SYSTEM_ADMIN')} />}
              />
              <Route path="/scheduling/new" element={<NewProductionPage />} />
              <Route path="/scheduling/import" element={<ImportProductionPage />} />
              <Route path="/capability" element={<Navigate replace to="/capability/gewu" />} />
              <Route
                path="/capability/gewu"
                element={<GewuDeploymentsPage admin={me.roleCodes.includes('SYSTEM_ADMIN')} />}
              />
              <Route path="/capability/gewu/import" element={<ImportGewuDeploymentsPage />} />
              {(Object.keys(intelligenceTitles) as (keyof typeof intelligenceTitles)[]).flatMap(
                (kind) => [
                  <Route
                    key={kind}
                    path={`/capability/${kind}`}
                    element={
                      <IntelligencePages
                        key={kind}
                        kind={kind}
                        mode="list"
                        admin={me.roleCodes.includes('SYSTEM_ADMIN')}
                      />
                    }
                  />,
                  <Route
                    key={kind + '-import'}
                    path={`/capability/${kind}/import`}
                    element={<IntelligencePages key={kind + '-import'} kind={kind} mode="import" />}
                  />,
                  <Route
                    key={kind + '-detail'}
                    path={`/capability/${kind}/:id`}
                    element={<IntelligencePages key={kind + '-detail'} kind={kind} mode="detail" />}
                  />,
                ],
              )}
              <Route
                path="/projects"
                element={
                  <ProjectsPage reference={reference} canCreate={canCreate} userId={me.id} />
                }
              />
              <Route
                path="/projects/new"
                element={
                  canCreate ? (
                    <CreateProjectPage me={me} reference={reference} canImport={canImport} />
                  ) : canImport ? (
                    <section>
                      <div className="page-heading">
                        <div>
                          <p>通过 Excel 批量导入项目。</p>
                        </div>
                        <Link className="primary-button" to="/projects/import">
                          项目导入
                        </Link>
                        <Link className="secondary-button" to="/projects">
                          返回项目列表
                        </Link>
                      </div>
                    </section>
                  ) : (
                    <Navigate to="/projects" replace />
                  )
                }
              />
              <Route
                path="/projects/import"
                element={
                  canImport ? (
                    <ImportProjectsPage reference={reference} />
                  ) : (
                    <Navigate to="/projects" replace />
                  )
                }
              />
              <Route
                path="/projects/:id"
                element={<ProjectDetailPage reference={reference} me={me} />}
              />
              <Route
                path="/login"
                element={<Navigate to={loginTarget ?? location.state?.returnTo ?? home} replace />}
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
