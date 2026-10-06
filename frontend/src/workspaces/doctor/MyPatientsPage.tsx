import { Search, Users, Clock, Stethoscope, FileCheck } from 'lucide-react';
import { Tabs } from '../../shared/components/Tabs';
import { Input } from '../../shared/components/Input';
import { Badge } from '../../shared/components/Badge';
import { EmptyState } from '../../shared/components/EmptyState';
import { WorkspacePage, QueryState } from '../../shared/components/WorkspacePage';
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
  const navigate = useNavigate();
  const openPatient = (patientId: string) => navigate(`/doctor/patients/${patientId}`);
  return <WorkspacePage title="My patients" description="Follow your active encounters or search for a patient's history.">
    <Tabs aria-label="Patient lists" variant="underline" unmountInactivePanels items={[
      { id: 'active', label: 'Active', icon: Stethoscope, content: <ActiveTab onSelect={openPatient} /> },
      { id: 'all', label: 'All patients', icon: Users, content: <AllTab onSelect={openPatient} /> },
    ]} />
  </WorkspacePage>;
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
  const { data: me, isPending: profilePending, isError: profileError, refetch: retryProfile } = useMe();
  const [stateFilter, setStateFilter] = useState<StateFilter>('ALL');

  const { data: encounters, isPending, isError, refetch } = useQuery({
    queryKey: ['encounters', 'search', { excludeTerminal: true }],
    queryFn: () => searchEncounters({ excludeTerminal: true }, accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 30_000,
  });
  const mine = useMemo(
    () => (encounters ?? []).filter((e) => me?.userId != null && e.assignedProviderId === me.userId),
    [encounters, me],
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

  if (isError || profileError) return <QueryState title="Unable to load active patients" description="Retry to load your assigned encounters and current counts." onRetry={() => { void refetch(); void retryProfile(); }} />;
  if (isPending || profilePending) return <QueryState loading title="Loading your patients…" description="Retrieving your assigned encounters." />;

  return (
    <div>
      <div className="workspace-filters" role="group" aria-label="Filter by encounter status">
        {STATE_FILTERS.map((f) => (
          <button
            key={f.key}
            aria-pressed={stateFilter === f.key}
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

      <div className="workspace-panel">
      {filtered.length === 0 ? (
        <EmptyState role="status" icon={Users}
          title={mine.length === 0 ? 'No active patients right now' : 'No patients with this status'}
          description={mine.length === 0 ? 'Your assigned open encounters will appear here. Use All patients to search patient history.' : 'Choose another status to see your other active encounters.'}
          secondaryAction={mine.length ? { label: 'Show all active patients', onClick: () => setStateFilter('ALL') } : undefined} />
      ) : (
        <ul className="workspace-list" aria-label="Active patient encounters">
          {filtered.map((encounter) => <li key={encounter.id}><ActivePatientRow encounter={encounter} onSelect={onSelect} /></li>)}
        </ul>
      )}
      </div>
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

function formatState(state: EncounterResponse['state']): string {
  if (!state) return '—';
  return state.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (letter) => letter.toUpperCase());
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
  const { data: patient, isError, refetch } = useQuery({
    queryKey: ['patients', encounter.patientId],
    queryFn: () => fetchPatientById(encounter.patientId as string, accessToken as string),
    enabled: accessToken != null,
    staleTime: 60_000,
  });
  const isInConsult = encounter.state === 'IN_CONSULT';
  const isAwaitingResults = encounter.state === 'AWAITING_RESULTS';
  return (
    <div className="workspace-row">
      <div className="workspace-identity">
        <button type="button" className="workspace-name" disabled={!encounter.patientId} onClick={() => onSelect(encounter.patientId as string)}>
          {patient?.fullName ?? (isError ? 'Patient details unavailable' : 'Loading patient…')}
        </button>
        <p className="workspace-meta">Hospital number: {patient?.hospitalNo ?? 'Unavailable'}</p>
        <p className="workspace-meta">Encounter ID: {encounter.id ?? 'Unavailable'}</p>
        {isError && <button type="button" className="btn btn-ghost" onClick={() => void refetch()}>Retry patient details</button>}
      </div>
      <Badge variant={isInConsult ? 'success' : isAwaitingResults ? 'warning' : 'neutral'} leadingIcon={isInConsult ? Stethoscope : isAwaitingResults ? Clock : FileCheck}>{formatState(encounter.state)}</Badge>
      <div className="workspace-actions">
      <button type="button" className="btn btn-secondary" disabled={!encounter.patientId} onClick={() => onSelect(encounter.patientId as string)}>View patient</button>
      {isInConsult && (
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => navigate(`/doctor/consultation/${encounter.id}`)}
        >
          Continue consultation
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
    </div>
  );
}

function AllTab({ onSelect }: { onSelect: (patientId: string) => void }) {
  const { accessToken } = useAuth();
  const [query, setQuery] = useState('');
  const { data: results, isPending, isError, refetch } = useQuery({
    queryKey: ['patients', 'search', query],
    queryFn: () => searchPatients(query, accessToken as string),
    enabled: accessToken != null && query.trim().length >= 2,
  });

  return (
    <div className="workspace-panel">
      <div className="workspace-search"><Input label="Find a patient" leadingIcon={Search}
        placeholder="Name, hospital number, or phone" value={query} onChange={(event) => setQuery(event.target.value)} /></div>
      {query.trim().length < 2 ? (
        <EmptyState icon={Search} title="Search patient history" description="Enter at least 2 characters to find a patient by name, hospital number, or phone." />
      ) : isError ? (
        <QueryState title="Unable to search patients" description="Try your search again." onRetry={() => void refetch()} />
      ) : isPending ? (
        <QueryState loading title="Searching patients…" />
      ) : !results?.length ? (
        <EmptyState role="status" icon={Search} title="No matching patients" description="Check the spelling or try a hospital number or phone number." />
      ) : (
        <ul className="workspace-list" aria-label="Patient search results">
          {(results as PatientSearchResult[]).map((patient) => <li key={patient.id} className="workspace-row">
            <div className="workspace-identity">
              <button type="button" className="workspace-name" onClick={() => onSelect(patient.id as string)}>{patient.fullName}</button>
              <p className="workspace-meta">Hospital number: {patient.hospitalNo ?? 'Unavailable'}</p>
            </div>
            <div className="workspace-actions"><button type="button" className="btn btn-secondary" onClick={() => onSelect(patient.id as string)}>View patient</button></div>
          </li>)}
        </ul>
      )}
    </div>
  );
}
