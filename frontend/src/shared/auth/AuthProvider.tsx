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
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [status, setStatus] = useState<AuthStatus>('loading');

  useEffect(() => {
    // Silent refresh on load: the refresh cookie (if any) survives a page
    // reload even though the in-memory access token does not.
    api
      .refresh()
      .then((token) => {
        setAccessToken(token.accessToken ?? null);
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
      setStatus(next ? 'authenticated' : 'unauthenticated');
      return next;
    } catch {
      setAccessToken(null);
      setStatus('unauthenticated');
      return null;
    }
  }, []);

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

  const login = useCallback(async (username: string, password: string) => {
    const response = await api.login({ username, password });
    setAccessToken(response.accessToken ?? null);
    setStatus('authenticated');
    return response.accessToken ?? null;
  }, []);

  const logout = useCallback(async () => {
    if (accessToken) {
      await api.logout(accessToken);
    }
    setAccessToken(null);
    setStatus('unauthenticated');
  }, [accessToken]);

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
