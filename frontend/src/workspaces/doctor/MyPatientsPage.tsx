import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { searchEncounters, type EncounterResponse } from '../../shared/api/encounters';
import { fetchPatientById, searchPatients, type PatientSearchResult } from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';

// 08-doctor-navigation.md §3. Two lists — Active (this doctor's open
// encounters) and All (search-first) — that hand off to the dedicated
// PatientPage / EncounterDetailPage flow rather than rendering the whole
// clinical record inline. Everything clinically detailed lives on those
// pages; this one exists to help the doctor pick which patient they want.
//
// §3.1: Active is every non-terminal encounter assigned to this doctor,
// grouped visibly by state so a patient sent home to wait for labs
// (AWAITING_RESULTS) is one glance away — that's the state a doctor
// most often forgets and can no longer find on the queue.
export function MyPatientsPage() {
  const [tab, setTab] = useState<'active' | 'all'>('active');
  const navigate = useNavigate();

  const openPatient = (patientId: string) => navigate(`/doctor/patients/${patientId}`);

  return (
    <div>
      <h1 style={{ fontSize: 'var(--text-xl)' }}>My Patients</h1>
      <nav style={{ display: 'flex', gap: 'var(--space-1)', borderBottom: '1px solid var(--border)', margin: 'var(--space-3) 0' }}>
        {(['active', 'all'] as const).map((key) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            style={{
              padding: 'var(--space-2) var(--space-3)',
              border: 'none',
              background: 'none',
              font: 'inherit',
              cursor: 'pointer',
              fontWeight: tab === key ? 'var(--weight-medium)' : 'var(--weight-normal)',
              boxShadow: tab === key ? 'inset 0 -2px 0 var(--green-signal)' : 'none',
            }}
          >
            {key === 'active' ? 'Active' : 'All'}
          </button>
        ))}
      </nav>
      {tab === 'active' ? <ActiveTab onSelect={openPatient} /> : <AllTab onSelect={openPatient} />}
    </div>
  );
}

type StateFilter = 'ALL' | 'AWAITING_CONSULT' | 'IN_CONSULT' | 'AWAITING_RESULTS' | 'OTHER';

const STATE_FILTERS: { key: StateFilter; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'AWAITING_CONSULT', label: 'Awaiting consult' },
  { key: 'IN_CONSULT', label: 'In consult' },
  { key: 'AWAITING_RESULTS', label: 'Awaiting results' },
  { key: 'OTHER', label: 'Other' },
];

function ActiveTab({ onSelect }: { onSelect: (patientId: string) => void }) {
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const [stateFilter, setStateFilter] = useState<StateFilter>('ALL');

  const { data: encounters, isLoading } = useQuery({
    queryKey: ['encounters', 'search', { excludeTerminal: true }],
    queryFn: () => searchEncounters({ excludeTerminal: true }, accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 30_000,
  });
  const mine = useMemo(
    () => (encounters ?? []).filter((e) => e.assignedProviderId === me?.userId),
    [encounters, me?.userId],
  );

  const counts = useMemo(() => bucketByState(mine), [mine]);
  const filtered = useMemo(() => {
    if (stateFilter === 'ALL') return mine;
    if (stateFilter === 'OTHER')
      return mine.filter(
        (e) => e.state !== 'AWAITING_CONSULT' && e.state !== 'IN_CONSULT' && e.state !== 'AWAITING_RESULTS',
      );
    return mine.filter((e) => e.state === stateFilter);
  }, [mine, stateFilter]);

  if (isLoading) return <p style={{ color: 'var(--text-muted)' }}>Loading…</p>;

  return (
    <div>
      <div
        style={{
          display: 'flex',
          gap: 'var(--space-1)',
          flexWrap: 'wrap',
          marginBottom: 'var(--space-3)',
        }}
      >
        {STATE_FILTERS.map((f) => (
          <button
            key={f.key}
            type="button"
            onClick={() => setStateFilter(f.key)}
            className={stateFilter === f.key ? 'btn btn-secondary' : 'btn btn-ghost'}
            style={{ fontSize: 'var(--text-sm)' }}
          >
            {f.label}
            <span className="badge badge-neutral" style={{ marginLeft: 'var(--space-1)' }}>
              {counts[f.key] ?? 0}
            </span>
          </button>
        ))}
      </div>

      {filtered.length === 0 ? (
        <p style={{ color: 'var(--text-muted)' }}>
          {mine.length === 0
            ? 'No active patients right now.'
            : `No patients in "${STATE_FILTERS.find((f) => f.key === stateFilter)?.label}".`}
        </p>
      ) : (
        <table className="table">
          <tbody>
            {filtered.map((e) => (
              <tr key={e.id}>
                <td>
                  <ActivePatientRow encounter={e} onSelect={onSelect} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function bucketByState(rows: EncounterResponse[]): Record<StateFilter, number> {
  const counts: Record<StateFilter, number> = {
    ALL: rows.length,
    AWAITING_CONSULT: 0,
    IN_CONSULT: 0,
    AWAITING_RESULTS: 0,
    OTHER: 0,
  };
  for (const r of rows) {
    if (r.state === 'AWAITING_CONSULT') counts.AWAITING_CONSULT++;
    else if (r.state === 'IN_CONSULT') counts.IN_CONSULT++;
    else if (r.state === 'AWAITING_RESULTS') counts.AWAITING_RESULTS++;
    else counts.OTHER++;
  }
  return counts;
}

function stateBadgeClass(state: EncounterResponse['state']): string {
  switch (state) {
    case 'IN_CONSULT':
      return 'badge-success';
    case 'AWAITING_RESULTS':
      return 'badge-warning';
    case 'AWAITING_CONSULT':
      return 'badge-neutral';
    default:
      return 'badge-neutral';
  }
}

function formatState(state: EncounterResponse['state']): string {
  if (!state) return '—';
  return state.replaceAll('_', ' ').toLowerCase();
}

function ActivePatientRow({
  encounter,
  onSelect,
}: {
  encounter: EncounterResponse;
  onSelect: (patientId: string) => void;
}) {
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const { data: patient } = useQuery({
    queryKey: ['patients', encounter.patientId],
    queryFn: () => fetchPatientById(encounter.patientId as string, accessToken as string),
    enabled: accessToken != null,
    staleTime: 60_000,
  });
  const isInConsult = encounter.state === 'IN_CONSULT';
  const isAwaitingResults = encounter.state === 'AWAITING_RESULTS';
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-2)' }}>
      <button
        type="button"
        className="btn btn-ghost"
        onClick={() => onSelect(encounter.patientId as string)}
        style={{ textAlign: 'left', flex: 1 }}
      >
        {patient?.fullName ?? 'Loading…'}
        {patient?.hospitalNo && <> · {patient.hospitalNo}</>}
        {' · '}
        <span className={`badge ${stateBadgeClass(encounter.state)}`}>{formatState(encounter.state)}</span>
      </button>
      {isInConsult && (
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => navigate(`/doctor/consultation/${encounter.id}`)}
        >
          Continue
        </button>
      )}
      {isAwaitingResults && (
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => navigate(`/doctor/patients/${encounter.patientId}/encounters/${encounter.id}`)}
          title="Open encounter to review results as they come in"
        >
          Open encounter
        </button>
      )}
    </div>
  );
}

function AllTab({ onSelect }: { onSelect: (patientId: string) => void }) {
  const { accessToken } = useAuth();
  const [query, setQuery] = useState('');
  const { data: results, isLoading } = useQuery({
    queryKey: ['patients', 'search', query],
    queryFn: () => searchPatients(query, accessToken as string),
    enabled: accessToken != null && query.trim().length >= 2,
  });

  return (
    <div>
      <input
        placeholder="Search by name, hospital number, or phone…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        style={{ width: '100%', padding: 'var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', marginBottom: 'var(--space-3)' }}
      />
      {query.trim().length < 2 ? (
        <p style={{ color: 'var(--text-muted)' }}>Search for a patient to see their history.</p>
      ) : isLoading ? (
        <p style={{ color: 'var(--text-muted)' }}>Searching…</p>
      ) : (results ?? []).length === 0 ? (
        <p style={{ color: 'var(--text-muted)' }}>No matches.</p>
      ) : (
        <table className="table">
          <tbody>
            {(results as PatientSearchResult[]).map((p) => (
              <tr key={p.id}>
                <td>
                  <button type="button" className="btn btn-ghost" onClick={() => onSelect(p.id as string)} style={{ textAlign: 'left' }}>
                    {p.fullName} · {p.hospitalNo}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
