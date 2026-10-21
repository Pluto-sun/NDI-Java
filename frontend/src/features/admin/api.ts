import { jsonBody, requestAt, type Page } from '../../shared/api/http';

export interface AdminAccount {
  id: string;
  loginName: string;
  displayName: string;
  email: string | null;
  provinceId: string | null;
  cityId: string | null;
  enabled: boolean;
  passwordInitialized: boolean;
  version: number;
  roleCodes: string[];
  reviewerProvinceIds: string[];
}
export interface AccountValues {
  loginName: string;
  displayName: string;
  email: string;
  roleCodes: string[];
  provinceId: string | null;
  cityId: string | null;
}
export interface AdminConfiguration {
  roles: { code: string; label: string; scope: string }[];
  provinces: { id: string; name: string }[];
  cities: { id: string; provinceId: string; name: string }[];
}
const root = '/api/v1/admin';
export const adminApi = {
  configuration: () => requestAt<AdminConfiguration>(root, '/configuration'),
  accounts: (search: string, enabled: string, page: number, role?: string) => {
    const query = new URLSearchParams({ search, page: String(page), size: '20' });
    if (enabled) query.set('enabled', enabled);
    if (role) query.set('role', role);
    return requestAt<Page<AdminAccount>>(root, `/users?${query}`);
  },
  create: (account: AccountValues, password: string) =>
    requestAt<AdminAccount>(root, '/users', { method: 'POST', ...jsonBody({ account, password }) }),
  update: (user: AdminAccount, account: AccountValues) =>
    requestAt<AdminAccount>(root, `/users/${user.id}`, {
      method: 'PUT',
      ...jsonBody({ version: user.version, account }),
    }),
  enabled: (user: AdminAccount) =>
    requestAt<AdminAccount>(root, `/users/${user.id}/enabled`, {
      method: 'PATCH',
      ...jsonBody({ version: user.version, enabled: !user.enabled }),
    }),
  reset: (user: AdminAccount, newPassword: string) =>
    requestAt<AdminAccount>(root, `/users/${user.id}/reset-password`, {
      method: 'POST',
      ...jsonBody({ version: user.version, newPassword }),
    }),
  createCity: (provinceId: string, name: string) =>
    requestAt(root, '/cities', { method: 'POST', ...jsonBody({ provinceId, name }) }),
  saveReviewerMappings: (
    reviewers: { reviewerId: string; version: number; provinceIds: string[] }[],
  ) =>
    requestAt<AdminAccount[]>(root, '/reviewer-province-maps', {
      method: 'PUT',
      ...jsonBody({ reviewers }),
    }),
};
