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
