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
  return response.json() as Promise<T>;
}

/** Shared fetch wrapper for authenticated JSON endpoints — every admin data module builds on this. */
export function authFetch<T>(path: string, accessToken: string, init?: RequestInit): Promise<T> {
  return fetch(path, {
    ...init,
    credentials: 'include',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  }).then(parseJsonOrThrow<T>);
}

export function login(body: LoginRequest): Promise<LoginResponse> {
  return fetch('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(body),
  }).then(parseJsonOrThrow<LoginResponse>);
}

export function refresh(): Promise<TokenResponse> {
  return fetch('/auth/refresh', { method: 'POST', credentials: 'include' }).then(
    parseJsonOrThrow<TokenResponse>,
  );
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
  const response = await fetch(path, {
    ...init,
    credentials: 'include',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  });
  if (!response.ok) {
    throw await toApiError(response);
  }
}

export function changePassword(body: ChangePasswordRequest, accessToken: string): Promise<void> {
  return authFetchVoid('/me/change-password', accessToken, { method: 'POST', body: JSON.stringify(body) });
}
