import type { components } from './types';

export type LoginRequest = components['schemas']['LoginRequest'];
export type LoginResponse = components['schemas']['LoginResponse'];
export type TokenResponse = components['schemas']['TokenResponse'];
export type MeResponse = components['schemas']['MeResponse'];
export type ChangePasswordRequest = components['schemas']['ChangePasswordRequest'];

// Spring's ProblemDetail body — `detail` plus whatever extra properties a
// given exception handler attaches (candidates, outstandingAmountMinor,
// itemId, ...). Untyped by design: which properties exist depends on which
// handler fired, and there is no generated schema for it to key off.
export type ProblemDetailBody = { title?: string; detail?: string; status?: number } & Record<string, unknown>;

export class ApiError extends Error {
  constructor(
    public status: number,
    public body: ProblemDetailBody | null = null,
  ) {
    super(body?.detail ?? `Request failed with status ${status}`);
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as ProblemDetailBody;
    return new ApiError(response.status, body);
  } catch {
    return new ApiError(response.status);
  }
}

async function parseJsonOrThrow<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw await toApiError(response);
  }
  // A handful of endpoints return 200 with an empty body as their documented
  // "found nothing, not an error" outcome (fetchOwnTriageDraft, fetchOpenEncounterForPatient
  // — see their own doc comments). response.json() throws SyntaxError on an
  // empty body instead of resolving null, which left every one of those
  // callers' react-query state permanently pending.
  const text = await response.text();
  return (text === '' ? null : JSON.parse(text)) as T;
}

// Registered by AuthProvider. When a request comes back 401 (the 15-minute
// access token expired mid-workflow — common on the billing/payment screen,
// which involves a lot of clicking before the final POST), swap in a fresh
// token via the refresh cookie and retry the request once. Writes are all
// idempotency-keyed, so the retry is safe.
let refreshAccessToken: (() => Promise<string | null>) | null = null;
export function setAccessTokenRefresher(fn: (() => Promise<string | null>) | null): void {
  refreshAccessToken = fn;
}

async function authedFetch(path: string, accessToken: string, init?: RequestInit): Promise<Response> {
  const build = (token: string): RequestInit => ({
    ...init,
    credentials: 'include',
    headers: {
      Authorization: `Bearer ${token}`,
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  });

  const response = await fetch(path, build(accessToken));
  if (response.status !== 401 || !refreshAccessToken) return response;

  const fresh = await refreshAccessToken().catch(() => null);
  return fresh ? fetch(path, build(fresh)) : response;
}

/** Shared fetch wrapper for authenticated JSON endpoints — every admin data module builds on this. */
export function authFetch<T>(path: string, accessToken: string, init?: RequestInit): Promise<T> {
  return authedFetch(path, accessToken, init).then(parseJsonOrThrow<T>);
}

export function login(body: LoginRequest): Promise<LoginResponse> {
  return fetch('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(body),
  }).then(parseJsonOrThrow<LoginResponse>);
}

// The refresh token is single-use: /auth/refresh consumes it and rotates in a
// new pair. Two refreshes racing (the mount effect under React StrictMode, the
// proactive interval, and a 401 retry can all fire within the same tick) would
// have the second present an already-consumed token and get a 401 — logging
// the user out mid-workflow. Collapse concurrent callers onto one in-flight
// request so the rotation happens exactly once.
let inFlightRefresh: Promise<TokenResponse> | null = null;
export function refresh(): Promise<TokenResponse> {
  if (inFlightRefresh) return inFlightRefresh;
  inFlightRefresh = fetch('/auth/refresh', { method: 'POST', credentials: 'include' })
    .then(parseJsonOrThrow<TokenResponse>)
    .finally(() => {
      inFlightRefresh = null;
    });
  return inFlightRefresh;
}

export async function logout(accessToken: string): Promise<void> {
  await fetch('/auth/logout', {
    method: 'POST',
    credentials: 'include',
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}

export function fetchMe(accessToken: string): Promise<MeResponse> {
  return fetch('/me', {
    headers: { Authorization: `Bearer ${accessToken}` },
    credentials: 'include',
  }).then(parseJsonOrThrow<MeResponse>);
}

/** Like authFetch, but for endpoints that respond with no body (204/200-empty) — parseJsonOrThrow would throw on those. */
export async function authFetchVoid(path: string, accessToken: string, init?: RequestInit): Promise<void> {
  const response = await authedFetch(path, accessToken, init);
  if (!response.ok) {
    throw await toApiError(response);
  }
}

export function changePassword(body: ChangePasswordRequest, accessToken: string): Promise<void> {
  return authFetchVoid('/me/change-password', accessToken, { method: 'POST', body: JSON.stringify(body) });
}
