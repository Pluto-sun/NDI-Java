import { matchPath } from 'react-router-dom';
import { projectsApi } from '../features/projects/api';
import { productionApi } from '../features/production/api';
import { revenueApi } from '../features/revenue/api';
import type { ProjectUser } from '../features/projects/types';
import { requestAt } from '../shared/api/http';
import { pageTitle } from './pageTitle';

export const home = '/workbench';
const logoutKey = 'ndi.logout-destination';

export function rememberLogout(userId: string, path: string) {
  sessionStorage.setItem(logoutKey, JSON.stringify({ userId, path }));
}

export function takeLogoutDestination(): { userId: string; path: string } | null {
  const stored = sessionStorage.getItem(logoutKey);
  sessionStorage.removeItem(logoutKey);
  if (!stored) return null;
  try {
    const value = JSON.parse(stored);
    return typeof value?.userId === 'string' && typeof value.path === 'string' ? value : null;
  } catch {
    return null;
  }
}

export function projectEntryPermissions(user: ProjectUser) {
  const admin = user.roleCodes.includes('SYSTEM_ADMIN');
  const canCreate =
    !admin &&
    user.roleCodes.some((role) =>
      [
        'CITY_REPORTER',
        'GROUP_REVIEWER',
        'INTERFACE_REVIEWER',
        'PROVINCE_REPORTER',
        'PROVINCE_DIGITAL_LEADER',
        'PROVINCE_INTELLIGENT_LEADER',
      ].includes(role),
    );
  return {
    canCreate,
    canImport: canCreate || admin || user.roleCodes.includes('INTERFACE_REVIEWER'),
  };
}

export async function resumeDestination(user: ProjectUser, path: string) {
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('\\')) return home;
  const { pathname } = new URL(path, window.location.origin);
  if (!pageTitle(pathname)) return home;
  if (pathname.startsWith('/admin/') && !user.roleCodes.includes('SYSTEM_ADMIN')) return home;
  const { canCreate, canImport } = projectEntryPermissions(user);
  try {
    if (pathname === '/projects') await projectsApi.list({ page: 0 });
    if (pathname === '/projects/new') return canCreate || canImport ? path : home;
    if (pathname === '/projects/import') {
      if (!canImport) return home;
      await projectsApi.importTask();
    } else if (pathname === '/scheduling/new' || pathname === '/scheduling/import') {
      if (!(await productionApi.reference()).writable) return home;
    } else if (pathname === '/scheduling') {
      await productionApi.reference();
    } else if (pathname === '/revenue/new' || pathname === '/revenue/import') {
      if (!(await revenueApi.reference()).canCreate) return home;
    } else if (pathname === '/revenue') {
      await revenueApi.reference();
    } else {
      const project = matchPath('/projects/:id', pathname)?.params.id;
      const approval = matchPath('/approvals/:id', pathname)?.params.id;
      const revenue = matchPath('/revenue/:id', pathname)?.params.id;
      const capability = matchPath('/capability/:kind/:id?', pathname)?.params;
      if (project) await projectsApi.detail(project);
      if (approval) await projectsApi.approval(approval);
      if (revenue) await revenueApi.detail(revenue);
      if (capability?.kind) {
        const kind = capability.kind === 'gewu' ? 'gewu-deployments' : capability.kind;
        await requestAt(
          '/api/v1/capability/' + kind,
          capability.id && capability.id !== 'import' ? '/' + capability.id : '/reference-data',
        );
      }
    }
    return path;
  } catch {
    return home;
  }
}
