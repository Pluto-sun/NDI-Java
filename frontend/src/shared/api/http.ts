const API_ROOT = '/api/v1/projects';
const ACTOR_KEY = 'ndi.local.actor';
let sessionMode = false;
let csrfToken: Promise<string> | null = null;
export const SESSION_EXPIRED_EVENT = 'ndi:session-expired';

export function configureSessionMode(enabled: boolean) {
  sessionMode = enabled;
  clearCsrfToken();
  if (enabled) setActorId(null);
}
export function clearCsrfToken() {
  csrfToken = null;
}

function notifyExpired(status: number, code?: string) {
  if (
    sessionMode &&
    status === 401 &&
    ['AUTH_UNAUTHENTICATED', 'IDENTITY_SESSION_INVALID'].includes(code ?? '')
  ) {
    clearCsrfToken();
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
  }
}

async function getCsrfToken(): Promise<string> {
  if (!csrfToken) {
    csrfToken = requestAt<{ token: string }>('/api/v1/auth', '/csrf')
      .then((data) => data.token)
      .catch((error: unknown) => {
        csrfToken = null;
        throw error;
      });
  }
  return csrfToken;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

interface ApiEnvelope<T> {
  code: string;
  message: string;
  data: T;
  requestId: string;
}
interface ProblemBody {
  code?: string;
  detail?: string;
  title?: string;
}

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number,
  ) {
    super(message);
  }
}

export function getActorId(): string | null {
  return sessionStorage.getItem(ACTOR_KEY);
}

export function setActorId(actorId: string | null): void {
  if (actorId) sessionStorage.setItem(ACTOR_KEY, actorId);
  else sessionStorage.removeItem(ACTOR_KEY);
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  return requestAt(API_ROOT, path, init);
}

export async function requestAt<T>(
  root: string,
  path: string,
  init: RequestInit = {},
  retryCsrf = true,
): Promise<T> {
  const actorId = getActorId();
  const headers = new Headers(init.headers);
  if (actorId && !sessionMode) headers.set('X-NDI-Actor-Id', actorId);
  const mutating = !['GET', 'HEAD', 'OPTIONS'].includes((init.method ?? 'GET').toUpperCase());
  if (sessionMode && mutating) headers.set('X-XSRF-TOKEN', await getCsrfToken());
  if (init.body && !(init.body instanceof FormData))
    headers.set('Content-Type', 'application/json');

  let response: Response;
  try {
    response = await fetch(`${root}${path}`, { ...init, headers, credentials: 'same-origin' });
  } catch {
    throw new ApiError('NETWORK_ERROR', '无法连接 Java 服务，请确认 8080 端口已启动。', 0);
  }
  const body = (await response.json().catch(() => null)) as (ApiEnvelope<T> & ProblemBody) | null;
  if (!response.ok || !body || body.code !== 'OK') {
    if (sessionMode && mutating && retryCsrf && body?.code === 'AUTH_CSRF_INVALID') {
      clearCsrfToken();
      return requestAt(root, path, init, false);
    }
    notifyExpired(response.status, body?.code);
    throw new ApiError(
      body?.code ?? 'HTTP_ERROR',
      body?.message ?? body?.detail ?? body?.title ?? `请求失败（${response.status}）`,
      response.status,
    );
  }
  return body.data;
}

export async function requestBlob(path: string): Promise<Blob> {
  const actorId = getActorId();
  const headers = new Headers();
  if (actorId && !sessionMode) headers.set('X-NDI-Actor-Id', actorId);
  let response: Response;
  try {
    response = await fetch(`${API_ROOT}${path}`, { headers, credentials: 'same-origin' });
  } catch {
    throw new ApiError('NETWORK_ERROR', '无法连接 Java 服务，请确认 8080 端口已启动。', 0);
  }
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ProblemBody | null;
    notifyExpired(response.status, body?.code);
    throw new ApiError(
      body?.code ?? 'HTTP_ERROR',
      body?.detail ?? body?.title ?? `下载失败（${response.status}）`,
      response.status,
    );
  }
  return response.blob();
}

export function jsonBody(value: unknown): Pick<RequestInit, 'body'> {
  return { body: JSON.stringify(value) };
}
