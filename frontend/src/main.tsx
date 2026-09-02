import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App.tsx';
import { ApiError } from './shared/api/client.ts';
import './index.css';
import { AuthProvider } from './shared/auth/AuthProvider.tsx';

// Defaults, not per-hook overrides: without these every query would
// dutifully retry three times on a 401/403 through authFetch — which
// also refreshes the access token on each 401 (see client.ts) — turning
// a single permission denial into ~7 requests plus enough auth-provider
// re-renders to visibly blink the page. Auth errors are never fixed by
// retrying; surface them once and stop.
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
          return false;
        }
        return failureCount < 2;
      },
      refetchOnWindowFocus: false,
    },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
