import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
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

function ActiveTab({ onSelect }: { onSelect: (patientId: string) => void }) {
  const { accessToken } = useAuth();
  const { data: me } = useMe();

  const { data: encounters, isLoading } = useQuery({
    queryKey: ['encounters', 'search', { excludeTerminal: true }],
    queryFn: () => searchEncounters({ excludeTerminal: true }, accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 30_000,
  });
  const mine = (encounters ?? []).filter((e) => e.assignedProviderId === me?.userId);

  if (isLoading) return <p style={{ color: 'var(--text-muted)' }}>Loading…</p>;
  if (mine.length === 0) return <p style={{ color: 'var(--text-muted)' }}>No active patients right now.</p>;

  return (
    <table className="table">
      <tbody>
        {mine.map((e) => (
          <tr key={e.id}>
            <td>
              <ActivePatientRow encounter={e} onSelect={onSelect} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function ActivePatientRow({ encounter, onSelect }: { encounter: EncounterResponse; onSelect: (patientId: string) => void }) {
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const { data: patient } = useQuery({
    queryKey: ['patients', encounter.patientId],
    queryFn: () => fetchPatientById(encounter.patientId as string, accessToken as string),
    enabled: accessToken != null,
    staleTime: 60_000,
  });
  // IN_CONSULT rows carry a Continue action alongside the view button —
  // the patient page is a scan-and-select surface, not the consultation
  // workspace itself.
  const isInConsult = encounter.state === 'IN_CONSULT';
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-2)' }}>
      <button type="button" className="btn btn-ghost" onClick={() => onSelect(encounter.patientId as string)} style={{ textAlign: 'left', flex: 1 }}>
        {patient?.fullName ?? 'Loading…'} · {patient?.hospitalNo} · <span className="badge badge-neutral">{encounter.state}</span>
      </button>
      {isInConsult && (
        <button type="button" className="btn btn-primary" onClick={() => navigate(`/doctor/consultation/${encounter.id}`)}>
          Continue
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
