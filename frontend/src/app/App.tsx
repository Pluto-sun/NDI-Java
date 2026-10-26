import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { Link, Navigate, Route, Routes } from 'react-router-dom';
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
import { ModulePlaceholder } from '../shared/ui/ModulePlaceholder';

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
  const [actors, setActors] = useState<ProjectUser[]>([]);
  const [me, setMe] = useState<ProjectUser | null>(null);
  const [reference, setReference] = useState<ReferenceData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [mode, setMode] = useState<AuthenticationMode | null>(null);
  const [profile, setProfile] = useState<AccountProfile | null>(null);
  const [message, setMessage] = useState('');
  const [loggingOut, setLoggingOut] = useState(false);

  function clearAccount(notice = '') {
    setActorId(null);
    setMe(null);
    setProfile(null);
    setReference(null);
    setMessage(notice);
  }

  async function loadSession() {
    const user = await authApi.me();
    const dictionaries = await projectsApi.referenceData();
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
  if (!me || !reference)
    if (mode !== 'LOCAL_ACTOR')
      return (
        <LoginPage
          message={message}
          connectionError={error}
          onLogin={async (login, password) => {
            await authApi.login(login, password);
            await loadSession();
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
            NDI<small>运营管理平台</small>
          </span>
        </div>
        <ModuleNavigation
          actorId={me.id}
          canCreate={canCreate}
          canImport={canImport}
          admin={me.roleCodes.includes('SYSTEM_ADMIN')}
        />
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <span className="topbar-title">工业三化运营管理平台</span>
          <div className="account">
            <span>{me.displayName}</span>
            <button
              type="button"
              className="text-button"
              disabled={loggingOut}
              onClick={async () => {
                if (mode === 'LOCAL_ACTOR') {
                  clearAccount();
                  return;
                }
                setLoggingOut(true);
                setError('');
                try {
                  await authApi.logout();
                  clearAccount('已退出登录。');
                } catch (cause) {
                  setError(cause instanceof Error ? cause.message : '退出失败，请重试');
                } finally {
                  setLoggingOut(false);
                }
              }}
            >
              {mode === 'LOCAL_ACTOR' ? '切换测试账号' : loggingOut ? '正在退出…' : '退出登录'}
            </button>
          </div>
        </header>
        <main className="content" id="main-content">
          {error && <Feedback tone="error">{error}</Feedback>}
          <Suspense fallback={<Loading />}>
            <Routes>
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
                    />
                  }
                />
              )}
              <Route path="/" element={<Navigate to="/workbench" replace />} />
              <Route path="/workbench" element={<WorkbenchPage reference={reference} />} />
              <Route path="/workbench/revenue" element={<ModulePlaceholder title="计收驾驶舱" />} />
              <Route
                path="/workbench/scheduling"
                element={<ModulePlaceholder title="排产驾驶舱" />}
              />
              <Route
                path="/workbench/capability"
                element={<ModulePlaceholder title="能力指标驾驶舱" />}
              />
              <Route path="/revenue" element={<ModulePlaceholder title="计收管理" />} />
              <Route path="/scheduling" element={<ModulePlaceholder title="排产管理" />} />
              <Route path="/capability" element={<ModulePlaceholder title="能力指标管理" />} />
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
                          <h1>项目创建</h1>
                          <p>通过 Excel 批量导入项目。</p>
                        </div>
                        <Link className="primary-button" to="/projects/import">
                          项目导入
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
