import { clearCsrfToken, jsonBody, requestAt } from '../../shared/api/http';
import type { ProjectUser } from '../projects/types';

export type AuthenticationMode = 'SESSION' | 'LOCAL_ACTOR';
export interface AccountProfile extends ProjectUser {
  loginName: string;
  email: string | null;
}
const root = '/api/v1/auth';
export const authApi = {
  mode: () => requestAt<{ mode: AuthenticationMode }>(root, '/mode'),
  me: () => requestAt<AccountProfile>(root, '/me'),
  async login(loginName: string, password: string) {
    const user = await requestAt<AccountProfile>(root, '/sessions', {
      method: 'POST',
      ...jsonBody({ loginName, password }),
    });
    clearCsrfToken();
    return user;
  },
  async logout() {
    await requestAt(root, '/sessions/current', { method: 'DELETE' });
    clearCsrfToken();
  },
  update: (displayName: string, email: string) =>
    requestAt<AccountProfile>(root, '/me', { method: 'PUT', ...jsonBody({ displayName, email }) }),
  async changePassword(currentPassword: string, newPassword: string) {
    await requestAt(root, '/password', {
      method: 'POST',
      ...jsonBody({ currentPassword, newPassword }),
    });
    clearCsrfToken();
  },
};
