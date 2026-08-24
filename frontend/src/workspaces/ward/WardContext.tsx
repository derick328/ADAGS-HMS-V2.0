import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchWardById } from '../../shared/api/wards';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useWardIds } from '../../shared/auth/useWardScope';

interface WardContextValue {
  wardId: string | null;
  wardIds: string[];
  setWardId: (id: string) => void;
  wardName: string | null;
}

const WardContext = createContext<WardContextValue | null>(null);

/**
 * 17-ward-nurse.md §3: "Scope the ward patient list — never all wards."
 * Almost every ward nurse holds exactly one WARD-scoped RoleAssignment, but
 * the model allows more than one (covering two wards on a quiet night, for
 * instance) — this picks the first by default and exposes a switcher rather
 * than assuming one.
 */
export function WardProvider({ children }: { children: ReactNode }) {
  const { accessToken } = useAuth();
  const wardIds = useWardIds();
  const [selected, setSelected] = useState<string | null>(null);

  useEffect(() => {
    if (!selected && wardIds.length > 0) {
      setSelected(wardIds[0]);
    }
  }, [wardIds, selected]);

  const wardId = selected && wardIds.includes(selected) ? selected : (wardIds[0] ?? null);

  const { data: ward } = useQuery({
    queryKey: ['wards', wardId],
    queryFn: () => fetchWardById(wardId as string, accessToken as string),
    enabled: accessToken != null && wardId != null,
    staleTime: 60_000,
  });

  const value = useMemo<WardContextValue>(
    () => ({ wardId, wardIds, setWardId: setSelected, wardName: ward?.name ?? null }),
    [wardId, wardIds, ward?.name],
  );

  return <WardContext.Provider value={value}>{children}</WardContext.Provider>;
}

export function useWardContext(): WardContextValue {
  const context = useContext(WardContext);
  if (!context) {
    throw new Error('useWardContext must be used within a WardProvider');
  }
  return context;
}
