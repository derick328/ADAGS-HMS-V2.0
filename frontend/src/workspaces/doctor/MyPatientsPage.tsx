import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { fetchAllergiesByPatient, fetchConsultationRecordsByEncounter, fetchDiagnosesByEncounter } from '../../shared/api/clinical';
import { fetchEncountersByPatient, searchEncounters, type EncounterResponse } from '../../shared/api/encounters';
import { fetchLabOrdersByEncounter } from '../../shared/api/labOrders';
import { fetchPatientById, searchPatients, type PatientSearchResult } from '../../shared/api/patients';
import { fetchPrescriptionsByEncounter } from '../../shared/api/prescriptions';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { formatDate, formatDateTime } from '../../shared/format/date';

// 08-doctor-navigation.md §3. Two views, not one list — Active (bounded,
// open encounters assigned to this doctor) and All (search-first, never a
// default result set). §3.4's access boundary ("no browse-all-patients")
// is naturally satisfied here: All only ever returns rows PATIENT_SEARCH
// itself returns, and the detail view reads whatever the doctor's granted
// permissions allow — no separate scoping layer exists yet to enforce
// "patients this doctor has actually treated" beyond that, so a doctor
// with PATIENT_SEARCH can technically open any patient's timeline. Noted
// as a gap: true break-glass/care-relationship scoping (root CLAUDE.md
// "Authorisation") isn't wired for this screen in this phase.
export function MyPatientsPage() {
  const [tab, setTab] = useState<'active' | 'all'>('active');
  const [selectedPatientId, setSelectedPatientId] = useState<string | null>(null);

  if (selectedPatientId) {
    return <PatientDetailView patientId={selectedPatientId} onBack={() => setSelectedPatientId(null)} />;
  }

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
      {tab === 'active' ? <ActiveTab onSelect={setSelectedPatientId} /> : <AllTab onSelect={setSelectedPatientId} />}
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
  const { data: patient } = useQuery({
    queryKey: ['patients', encounter.patientId],
    queryFn: () => fetchPatientById(encounter.patientId as string, accessToken as string),
    enabled: accessToken != null,
    staleTime: 60_000,
  });
  return (
    <button type="button" className="btn btn-ghost" onClick={() => onSelect(encounter.patientId as string)} style={{ textAlign: 'left' }}>
      {patient?.fullName ?? 'Loading…'} · {patient?.hospitalNo} · <span className="badge badge-neutral">{encounter.state}</span>
    </button>
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

function PatientDetailView({ patientId, onBack }: { patientId: string; onBack: () => void }) {
  const { accessToken } = useAuth();
  const { data: patient } = useQuery({
    queryKey: ['patients', patientId],
    queryFn: () => fetchPatientById(patientId, accessToken as string),
    enabled: accessToken != null,
  });
  const { data: allergies } = useQuery({
    queryKey: ['allergies', patientId],
    queryFn: () => fetchAllergiesByPatient(patientId, accessToken as string),
    enabled: accessToken != null,
  });
  const { data: encounters } = useQuery({
    queryKey: ['encounters', 'by-patient', patientId],
    queryFn: () => fetchEncountersByPatient(patientId, accessToken as string),
    enabled: accessToken != null,
  });

  return (
    <div>
      <button type="button" className="btn btn-ghost" onClick={onBack} style={{ marginBottom: 'var(--space-3)' }}>
        ← Back
      </button>

      <h1 style={{ fontSize: 'var(--text-xl)', marginBottom: 4 }}>
        {patient?.fullName} · {patient?.gender?.[0]} · {patient?.hospitalNo}
      </h1>

      {/* root CLAUDE.md "Safety": allergies pinned to the header, every status, never collapsed. */}
      <div style={{ marginBottom: 'var(--space-4)' }}>
        {(allergies ?? []).length === 0 ? (
          <div style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>No known allergies.</div>
        ) : (
          <div style={{ background: 'var(--critical-bg)', color: 'var(--critical)', borderRadius: 'var(--radius)', padding: 'var(--space-2) var(--space-3)', fontSize: 'var(--text-sm)' }}>
            ⚠ Allergies: {allergies!.map((a) => `${a.description} (${a.confirmationStatus}, ${formatDate(a.recordedAt)})`).join(' · ')}
          </div>
        )}
      </div>

      <h2 style={{ fontSize: 'var(--text-base)' }}>Encounters</h2>
      {(encounters ?? []).length === 0 ? (
        <p style={{ color: 'var(--text-muted)' }}>No encounters on record.</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
          {encounters!.map((e) => (
            <EncounterTimelineRow key={e.id} encounter={e} />
          ))}
        </div>
      )}
    </div>
  );
}

function EncounterTimelineRow({ encounter }: { encounter: EncounterResponse }) {
  const { accessToken } = useAuth();
  const [expanded, setExpanded] = useState(false);

  const { data: notes } = useQuery({
    queryKey: ['consultation-records', 'by-encounter', encounter.id],
    queryFn: () => fetchConsultationRecordsByEncounter(encounter.id as string, accessToken as string),
    enabled: accessToken != null && expanded,
  });
  const { data: diagnoses } = useQuery({
    queryKey: ['diagnoses', encounter.id],
    queryFn: () => fetchDiagnosesByEncounter(encounter.id as string, accessToken as string),
    enabled: accessToken != null && expanded,
  });
  const { data: labs } = useQuery({
    queryKey: ['lab-orders', 'by-encounter', encounter.id],
    queryFn: () => fetchLabOrdersByEncounter(encounter.id as string, accessToken as string),
    enabled: accessToken != null && expanded,
  });
  const { data: rx } = useQuery({
    queryKey: ['prescriptions', 'by-encounter', encounter.id],
    queryFn: () => fetchPrescriptionsByEncounter(encounter.id as string, accessToken as string),
    enabled: accessToken != null && expanded,
  });

  return (
    <div style={{ border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        style={{ width: '100%', textAlign: 'left', padding: 'var(--space-3)', border: 'none', background: 'var(--bg-surface)', cursor: 'pointer', font: 'inherit' }}
      >
        {expanded ? '▼' : '▶'} {formatDate(encounter.createdAt)} · {encounter.type} · <span className="badge badge-neutral">{encounter.state}</span>
      </button>
      {expanded && (
        <div style={{ padding: 'var(--space-3)', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          <div>
            <strong>Consultation</strong>
            {(notes ?? []).length === 0 ? (
              <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>No note yet.</p>
            ) : (
              notes!.map((n) => (
                <div key={n.id} style={{ fontSize: 'var(--text-sm)', marginTop: 4 }}>
                  <span className="badge badge-neutral">{n.status}</span> {formatDateTime(n.completedAt ?? n.createdAt)}
                  <div>HPI: {n.historyOfPresentingIllness || '—'}</div>
                  <div>Exam: {n.physicalExamination || '—'}</div>
                  <div>Plan: {n.treatmentPlan || '—'}</div>
                </div>
              ))
            )}
          </div>
          <div>
            <strong>Diagnosis</strong>
            {(diagnoses ?? []).length === 0 ? (
              <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>None recorded.</p>
            ) : (
              <ul style={{ margin: '4px 0', paddingLeft: 18, fontSize: 'var(--text-sm)' }}>
                {diagnoses!.map((d) => (
                  <li key={d.id}>
                    {d.icd10Code} {d.icd10TitleSnapshot} {d.isPrimary && <span className="badge badge-signal">Primary</span>}
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div>
            <strong>Prescription</strong>
            {(rx ?? []).length === 0 ? (
              <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>None.</p>
            ) : (
              <ul style={{ margin: '4px 0', paddingLeft: 18, fontSize: 'var(--text-sm)' }}>
                {rx!.map((r) => (
                  <li key={r.prescriptionLineId}>
                    {r.itemName} × {r.quantityPrescribed} — <span className="badge badge-neutral">{r.status}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div>
            <strong>Lab</strong>
            {(labs ?? []).length === 0 ? (
              <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>None.</p>
            ) : (
              <ul style={{ margin: '4px 0', paddingLeft: 18, fontSize: 'var(--text-sm)' }}>
                {labs!.map((l) => (
                  <li key={l.id}>
                    {l.labTestName} — <span className="badge badge-neutral">{l.status}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
