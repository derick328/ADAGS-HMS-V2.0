import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { fetchOpenEncounterForPatient } from '../../shared/api/encounters';
import { grantAdminWaiver } from '../../shared/api/paymentBypasses';
import { searchPatients } from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';

// 02-billing-architecture.md §8: ADMIN_WAIVER — "charity, staff benefit,
// ministry directive." An admin grants this directly against a patient's
// open encounter, no second actor (unlike CLINICIAN_AUTHORISED, the other
// new source this session added to the enum: that one needs a clinician to
// see and act on a request, which needs a Doctor workspace that doesn't
// exist yet — so it has no granting UI anywhere yet, deliberately).
//
// Search-first, same reasoning as PatientsPage: no browse-all list.
export function PaymentBypassWaiverPage() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('');
  const [selectedPatientId, setSelectedPatientId] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [granted, setGranted] = useState(false);

  const { data: results, isLoading, isError } = useQuery({
    queryKey: ['patients', 'search', submittedQuery],
    queryFn: () => searchPatients(submittedQuery, accessToken as string),
    enabled: accessToken != null && submittedQuery.trim() !== '',
  });

  const { data: openEncounter, isLoading: encounterLoading } = useQuery({
    queryKey: ['open-encounter', selectedPatientId],
    queryFn: () => fetchOpenEncounterForPatient(selectedPatientId as string, accessToken as string),
    enabled: accessToken != null && selectedPatientId != null,
  });

  const grant = useMutation({
    mutationFn: () => grantAdminWaiver({ encounterId: openEncounter?.id as string, reason }, accessToken as string),
    onSuccess: () => {
      setGranted(true);
      setReason('');
      queryClient.invalidateQueries({ queryKey: ['payment-bypasses', 'active'] });
    },
  });

  function selectPatient(patientId: string) {
    setSelectedPatientId(patientId);
    setGranted(false);
    grant.reset();
  }

  return (
    <div>
      <PageHeader title={<>Payment bypass waivers</>} description={<>Charity, staff benefit, or ministry directive — unblocks payment for this patient's current visit. Scope
          is the whole encounter, same as an emergency bypass.</>} />

      <form className="clinical-filter-bar"
        onSubmit={(e) => {
          e.preventDefault();
          setSelectedPatientId(null);
          setSubmittedQuery(query.trim());
        }}
        style={{ display: 'flex', gap: 'var(--space-2)', marginBottom: 'var(--space-4)' }}
      >
        <input className="clinical-control"
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by name, hospital number, or phone"
          aria-label="Search patients"
          style={{ flex: 1, maxWidth: 420, padding: 'var(--space-2) var(--space-3)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}
        />
        <button type="submit" className="btn btn-primary" disabled={query.trim() === ''}>
          Search
        </button>
      </form>

      <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
        {submittedQuery === '' ? (
          <EmptyState message="Search for a patient by name, hospital number, or phone." />
        ) : isLoading ? (
          <EmptyState message="Searching…" />
        ) : isError ? (
          <EmptyState message="Couldn't reach the patient index. Try again." />
        ) : !results || results.length === 0 ? (
          <EmptyState message={`No patients match "${submittedQuery}"`} />
        ) : (
          <TableRegion label="Patient search results"><table className="table">
            <caption className="sr-only">Patient search results</caption>
            <thead>
              <tr>
                <th>Hospital no.</th>
                <th>Name</th>
                <th>Phone</th>
              </tr>
            </thead>
            <tbody>
              {results.map((patient) => (
                <tr
                  key={patient.id}
                  onClick={() => selectPatient(patient.id as string)}
                  style={{ cursor: 'pointer' }}
                  className={patient.id === selectedPatientId ? 'row-selected' : ''}
                >
                  <td>{patient.hospitalNo ?? '—'}</td>
                  <td style={{ fontWeight: 'var(--weight-medium)' }}>{patient.fullName ?? '—'}</td>
                  <td>{patient.phone ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table></TableRegion>
        )}
      </div>

      {selectedPatientId && (
        <div style={{ marginTop: 'var(--space-4)', background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)', maxWidth: 480 }}>
          {encounterLoading ? (
            <FeedbackState loading>Loading…</FeedbackState>
          ) : !openEncounter ? (
            <p style={{ color: 'var(--text-muted)' }}>This patient has no open encounter — a waiver applies to a visit, not the patient generally.</p>
          ) : granted ? (
            <p style={{ color: 'var(--green-600)' }}>Waiver granted. Payment is now unblocked for this encounter.</p>
          ) : (
            <>
              <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>Grant a waiver</h2>
              <label htmlFor="waiver-reason" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>
                Reason (charity, staff benefit, ministry directive, or free text)
              </label>
              <textarea className="clinical-control"
                id="waiver-reason"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                rows={3}
                style={{ width: '100%', marginTop: 'var(--space-1)', padding: 'var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}
              />
              {grant.error && <div className="form-error">Something went wrong. Try again.</div>}
              <button
                type="button"
                className="btn btn-primary"
                style={{ marginTop: 'var(--space-3)' }}
                disabled={reason.trim() === '' || grant.isPending}
                onClick={() => grant.mutate()}
              >
                {grant.isPending ? 'Granting…' : 'Grant waiver'}
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return <FeedbackState loading={message.startsWith('Loading') || message.startsWith('Searching')}>{message}</FeedbackState>;
}
