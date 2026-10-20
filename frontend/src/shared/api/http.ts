const API_ROOT = '/api/v1/projects';
const ACTOR_KEY = 'ndi.local.actor';

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
  const actorId = getActorId();
  const headers = new Headers(init.headers);
  if (actorId) headers.set('X-NDI-Actor-Id', actorId);
  if (init.body && !(init.body instanceof FormData))
    headers.set('Content-Type', 'application/json');

  let response: Response;
  try {
    response = await fetch(`${API_ROOT}${path}`, { ...init, headers });
  } catch {
    throw new ApiError('NETWORK_ERROR', '无法连接 Java 服务，请确认 8080 端口已启动。', 0);
  }
  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;
  if (!response.ok || !body || body.code !== 'OK') {
    throw new ApiError(
      body?.code ?? 'HTTP_ERROR',
      body?.message ?? `请求失败（${response.status}）`,
      response.status,
    );
  }
  return body.data;
}

export function jsonBody(value: unknown): Pick<RequestInit, 'body'> {
  return { body: JSON.stringify(value) };
}
