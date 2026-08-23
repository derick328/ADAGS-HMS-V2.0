import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { fetchPatientById, searchPatients, type PatientResponse } from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';

// 18-admin.md §3.1: the Patients screen splits — admin keeps identity and
// hospital number, loses everything clinical (R2). It also keeps invoice
// history, payment history, balances, and spending aggregates, but the
// backend has no per-patient financial endpoint yet (PatientController only
// exposes search/getById, and PATIENT_READ_FINANCIAL — already in the
// permission catalogue — is granted but unconsumed by any route). Rather
// than fabricate numbers, this screen shows the identity half only and
// says so, until that endpoint exists.
//
// §3.1 ⚠ also requires search-first with no browse-all list — an unbounded
// patient list is the exfiltration surface it calls out. Nothing fetches
// until a query is submitted.
export function PatientsPage() {
  const { accessToken } = useAuth();
  const [query, setQuery] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const { data: results, isLoading, isError } = useQuery({
    queryKey: ['patients', 'search', submittedQuery],
    queryFn: () => searchPatients(submittedQuery, accessToken as string),
    enabled: accessToken != null && submittedQuery.trim() !== '',
  });

  const { data: selected } = useQuery({
    queryKey: ['patients', 'detail', selectedId],
    queryFn: () => fetchPatientById(selectedId as string, accessToken as string),
    enabled: accessToken != null && selectedId != null,
  });

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>Patients</h1>
        <p style={{ margin: 'var(--space-1) 0 0', color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
          Identity and hospital number only — financial history isn't available from the backend yet.
        </p>
      </header>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          setSelectedId(null);
          setSubmittedQuery(query.trim());
        }}
        style={{ display: 'flex', gap: 'var(--space-2)', marginBottom: 'var(--space-4)' }}
      >
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by name, hospital number, or phone"
          aria-label="Search patients"
          style={{
            flex: 1,
            maxWidth: 420,
            padding: 'var(--space-2) var(--space-3)',
            border: '1px solid var(--border-strong)',
            borderRadius: 'var(--radius)',
            font: 'inherit',
          }}
        />
        <button type="submit" className="btn btn-primary" disabled={query.trim() === ''}>
          Search
        </button>
      </form>

      <div
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-lg)',
          overflow: 'hidden',
        }}
      >
        {submittedQuery === '' ? (
          <EmptyState message="Search for a patient by name, hospital number, or phone." />
        ) : isLoading ? (
          <EmptyState message="Searching…" />
        ) : isError ? (
          <EmptyState message="Couldn't reach the patient index. Try again." />
        ) : !results || results.length === 0 ? (
          <EmptyState message={`No patients match "${submittedQuery}"`} />
        ) : (
          <table className="table">
            <caption className="sr-only">Patient search results</caption>
            <thead>
              <tr>
                <th>Hospital no.</th>
                <th>Name</th>
                <th>Date of birth</th>
                <th>Phone</th>
              </tr>
            </thead>
            <tbody>
              {results.map((patient) => (
                <tr
                  key={patient.id}
                  onClick={() => setSelectedId(patient.id ?? null)}
                  style={{ cursor: 'pointer' }}
                  className={patient.id === selectedId ? 'row-selected' : ''}
                >
                  <td>{patient.hospitalNo ?? '—'}</td>
                  <td style={{ fontWeight: 'var(--weight-medium)' }}>{patient.fullName ?? '—'}</td>
                  <td>
                    {patient.dateOfBirth ?? '—'}
                    {patient.dobIsEstimated && (
                      <span className="badge badge-neutral" style={{ marginLeft: 'var(--space-2)' }}>
                        estimated
                      </span>
                    )}
                  </td>
                  <td>{patient.phone ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {selected && <PatientDetailCard patient={selected} />}
    </div>
  );
}

function PatientDetailCard({ patient }: { patient: PatientResponse }) {
  return (
    <div
      style={{
        marginTop: 'var(--space-4)',
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-4)',
        maxWidth: 480,
      }}
    >
      <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>
        {patient.fullName}
        {!patient.active && (
          <span className="badge badge-neutral" style={{ marginLeft: 'var(--space-2)' }}>
            inactive
          </span>
        )}
      </h2>
      <dl style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', margin: 0 }}>
        <DetailRow label="Hospital no." value={patient.hospitalNo} />
        <DetailRow
          label="Date of birth"
          value={patient.dateOfBirth ? `${patient.dateOfBirth}${patient.dobIsEstimated ? ' (estimated)' : ''}` : undefined}
        />
        <DetailRow label="Gender" value={patient.gender} />
        <DetailRow label="Marital status" value={patient.maritalStatus} />
        <DetailRow label="National ID" value={patient.nationalId} />
        <DetailRow label="Phone" value={patient.phone} />
        <DetailRow label="Email" value={patient.email} />
      </dl>
      <p style={{ marginTop: 'var(--space-3)', marginBottom: 0, fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
        Invoice history, payment history, balances, and spending aggregates aren't shown here — the backend doesn't
        expose a per-patient financial summary yet.
      </p>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string | undefined | null }) {
  return (
    <>
      <dt style={{ fontWeight: 'var(--weight-medium)' }}>{label}</dt>
      <dd style={{ margin: '0 0 var(--space-2)' }}>{value ?? '—'}</dd>
    </>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div style={{ padding: 'var(--space-10) var(--space-4)', textAlign: 'center', color: 'var(--text-muted)' }}>
      {message}
    </div>
  );
}
