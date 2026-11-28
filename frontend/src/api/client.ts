// 서버 호출은 모두 여기를 거친다 (연동 가이드 '프론트를 고치는 방식' 1번).
// - Authorization: Bearer <액세스 토큰>을 자동으로 붙인다
// - 액세스 토큰이 만료(401)되면 리프레시 토큰으로 한 번 재발급하고 다시 요청한다
// - 에러는 서버 응답 {code, message}를 ApiError로 던지고, message를 토스트로 띄운다
//   (화면에서 직접 보여 줄 때는 { quiet: true }로 토스트를 끈다)
// 개발 중에는 vite 프록시가 /api를 localhost:8080으로 넘긴다.

const TOKEN_KEY = 'anytime-auth';
const BASE = '/api';

export interface Tokens { accessToken: string; refreshToken: string }

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message); }
}

export const tokens = {
  get(): Tokens | null {
    try { const raw = localStorage.getItem(TOKEN_KEY); return raw ? JSON.parse(raw) : null; } catch { return null; }
  },
  set(t: Tokens) { localStorage.setItem(TOKEN_KEY, JSON.stringify({ accessToken: t.accessToken, refreshToken: t.refreshToken })); },
  clear() { localStorage.removeItem(TOKEN_KEY); },
};

/** store.tsx가 토스트 함수를 등록한다. 리프레시까지 실패하면 onLoggedOut이 불린다. */
let onError: (message: string) => void = () => {};
let onLoggedOut: () => void = () => {};
export function setApiHandlers(h: { onError?: (message: string) => void; onLoggedOut?: () => void }) {
  if (h.onError) onError = h.onError;
  if (h.onLoggedOut) onLoggedOut = h.onLoggedOut;
}

interface Opts { quiet?: boolean; auth?: boolean }

// 동시에 여러 요청이 만료돼도 재발급은 한 번만 한다.
let refreshing: Promise<boolean> | null = null;
function refresh(): Promise<boolean> {
  const t = tokens.get();
  if (!t) return Promise.resolve(false);
  refreshing ??= fetch(`${BASE}/auth/refresh`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: t.refreshToken }),
  })
    .then(async (res) => {
      if (!res.ok) { tokens.clear(); onLoggedOut(); return false; }
      tokens.set(await res.json());
      return true;
    })
    .catch(() => false)
    .finally(() => { refreshing = null; });
  return refreshing;
}

/** STOMP 등 외부 클라이언트가 토큰 만료 시 단일 비행(single-flight) 리프레시를 재사용한다. */
export const refreshAccessToken = (): Promise<boolean> => refresh();

async function request<T>(method: string, path: string, body?: unknown, opts: Opts = {}, retried = false): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const t = opts.auth === false ? null : tokens.get();
  if (t) headers.Authorization = `Bearer ${t.accessToken}`;

  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    const err = new ApiError(0, 'NETWORK', '서버에 연결하지 못했어요');
    if (!opts.quiet) onError(err.message);
    throw err;
  }

  if (res.status === 401 && t && !retried && (await refresh())) {
    return request<T>(method, path, body, opts, true);
  }
  if (!res.ok) {
    const data = await res.json().catch(() => null) as { code?: string; message?: string } | null;
    const err = new ApiError(res.status, data?.code ?? 'UNKNOWN', data?.message ?? '잠시 후 다시 시도해 주세요');
    if (!opts.quiet) onError(err.message);
    throw err;
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  get: <T>(path: string, opts?: Opts) => request<T>('GET', path, undefined, opts),
  post: <T>(path: string, body?: unknown, opts?: Opts) => request<T>('POST', path, body, opts),
  put: <T>(path: string, body?: unknown, opts?: Opts) => request<T>('PUT', path, body, opts),
  patch: <T>(path: string, body?: unknown, opts?: Opts) => request<T>('PATCH', path, body, opts),
  delete: <T>(path: string, opts?: Opts) => request<T>('DELETE', path, undefined, opts),
};
