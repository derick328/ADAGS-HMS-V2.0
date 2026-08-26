import { useQuery } from '@tanstack/react-query';
import { fetchWardPatients } from '../../shared/api/admissions';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { useWardContext } from './WardContext';

/** The Dashboard/Ward Patients/sidebar-badges' shared source list — one fetch, several consumers. */
export function useWardPatients() {
  const { accessToken } = useAuth();
  const { wardId } = useWardContext();
  const canSee = usePermission('WARD_PATIENT_READ');

  return useQuery({
    queryKey: ['admissions', 'ward', wardId],
    queryFn: () => fetchWardPatients(wardId as string, accessToken as string),
    enabled: canSee && accessToken != null && wardId != null,
    refetchInterval: 15_000,
  });
}
