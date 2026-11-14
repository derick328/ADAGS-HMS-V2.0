import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import * as api from '../api/client';

type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

interface AuthContextValue {
  accessToken: string | null;
  status: AuthStatus;
  login: (username: string, password: string) => Promise<string | null>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [accessTokenExpiresAt, setAccessTokenExpiresAt] = useState<string | null>(null);
  const [status, setStatus] = useState<AuthStatus>('loading');

  useEffect(() => {
    // Silent refresh on load: the refresh cookie (if any) survives a page
    // reload even though the in-memory access token does not.
    api
      .refresh()
      .then((token) => {
        setAccessToken(token.accessToken ?? null);
        setAccessTokenExpiresAt(token.accessTokenExpiresAt ?? null);
        setStatus('authenticated');
      })
      .catch(() => setStatus('unauthenticated'));
  }, []);

  // Swap an expired access token for a fresh one off the refresh cookie.
  const renewAccessToken = useCallback(async (): Promise<string | null> => {
    try {
      const token = await api.refresh();
      const next = token.accessToken ?? null;
      setAccessToken(next);
      setAccessTokenExpiresAt(token.accessTokenExpiresAt ?? null);
      setStatus(next ? 'authenticated' : 'unauthenticated');
      return next;
    } catch {
      setAccessToken(null);
      setAccessTokenExpiresAt(null);
      setStatus('unauthenticated');
      queryClient.clear();
      return null;
    }
  }, [queryClient]);

  // Backstop: any request that still 401s (e.g. the laptop was asleep) gets
  // one transparent retry with a freshly-minted token.
  useEffect(() => {
    api.setAccessTokenRefresher(renewAccessToken);
    return () => api.setAccessTokenRefresher(null);
  }, [renewAccessToken]);

  // Proactive: renew well inside the 15-minute access-token TTL so a long
  // workflow (billing, a slow triage assessment) never crosses the line.
  useEffect(() => {
    if (status !== 'authenticated') return;
    const id = window.setInterval(() => void renewAccessToken(), 12 * 60 * 1000);
    return () => window.clearInterval(id);
  }, [status, renewAccessToken]);

  // The server caps the final access token at the session's 30-minute
  // deadline. Check it at expiry so an idle open tab returns to login too.
  useEffect(() => {
    if (status !== 'authenticated' || !accessTokenExpiresAt) return;
    const delay = Math.max(0, new Date(accessTokenExpiresAt).getTime() - Date.now() + 100);
    const id = window.setTimeout(() => void renewAccessToken(), delay);
    return () => window.clearTimeout(id);
  }, [status, accessTokenExpiresAt, renewAccessToken]);

  const login = useCallback(async (username: string, password: string) => {
    const response = await api.login({ username, password });
    // Wipe every cached query before the new identity takes effect. Shared
    // keys like ['me'] are keyed by identity, not token, so without this the
    // previous user's cached data (their username, permissions, queues)
    // would show to whoever logs in next until each query happened to
    // refetch — the cross-user bleed behind "glory sees Friday's name".
    queryClient.clear();
    setAccessToken(response.accessToken ?? null);
    setAccessTokenExpiresAt(response.accessTokenExpiresAt ?? null);
    setStatus('authenticated');
    return response.accessToken ?? null;
  }, [queryClient]);

  const logout = useCallback(async () => {
    // Flip to signed-out and drop the cache immediately, before awaiting the
    // server. Awaiting /auth/logout first left the previous user's screen and
    // identity up for however long that round-trip took (the 5-10s hang
    // between sign-out and the next sign-in). The server revocation is
    // best-effort — the refresh cookie is cleared by the response and the
    // access token expires on its own TTL regardless.
    const token = accessToken;
    setAccessToken(null);
    setAccessTokenExpiresAt(null);
    setStatus('unauthenticated');
    queryClient.clear();
    if (token) {
      try {
        await api.logout(token);
      } catch {
        // Already signed out locally; a failed revoke must not block the UI.
      }
    }
  }, [accessToken, queryClient]);

  return (
    <AuthContext.Provider value={{ accessToken, status, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
