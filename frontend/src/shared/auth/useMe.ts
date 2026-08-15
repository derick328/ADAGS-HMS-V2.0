import { useQuery } from '@tanstack/react-query';
import { fetchMe } from '../api/client';
import { useAuth } from './AuthProvider';

export function useMe() {
  const { accessToken, status } = useAuth();
  return useQuery({
    queryKey: ['me', accessToken],
    queryFn: () => fetchMe(accessToken as string),
    enabled: status === 'authenticated' && accessToken != null,
    staleTime: 60_000,
  });
}
