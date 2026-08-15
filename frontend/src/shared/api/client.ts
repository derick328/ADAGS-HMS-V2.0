import type { components } from './types';

export type LoginRequest = components['schemas']['LoginRequest'];
export type LoginResponse = components['schemas']['LoginResponse'];
export type TokenResponse = components['schemas']['TokenResponse'];
export type MeResponse = components['schemas']['MeResponse'];

export class ApiError extends Error {
  constructor(public status: number) {
    super(`Request failed with status ${status}`);
  }
}

async function parseJsonOrThrow<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new ApiError(response.status);
  }
  return response.json() as Promise<T>;
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
