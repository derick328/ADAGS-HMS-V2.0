import { useQuery } from '@tanstack/react-query';
import { fetchMe } from '../api/client';
import { useAuth } from './AuthProvider';

export function useMe() {
  const { accessToken, status } = useAuth();
  return useQuery({
    // Keyed by a stable identity, never the access token. The token rotates
    // (proactive 12-min renewal, and the 401 retry in client.ts), and keying
    // the cache by it meant every rotation discarded /me, blanked every
    // usePermission(), and refetched the whole permission-gated subtree —
    // which, when a token had just expired, fed a refresh→rekey→refetch→401
    // loop that visibly blinked the workspace. The current token still rides
    // the request via the queryFn closure; it must not be the cache key.
    queryKey: ['me'],
    queryFn: () => fetchMe(accessToken as string),
    enabled: status === 'authenticated' && accessToken != null,
    staleTime: 60_000,
  });
}
