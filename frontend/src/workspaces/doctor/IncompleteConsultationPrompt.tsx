import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { ConsultationRecordResponse } from '../../shared/api/clinical';
import { fetchIncompleteConsultations } from '../../shared/api/clinical';
import { fetchEncounterById, searchEncounters } from '../../shared/api/encounters';
import { fetchPatientById } from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDateTime, formatWaitingSince } from '../../shared/format/date';

// 09-doctor-incomplete-consultations.md §3/§4. Shown once per session on
// login — modelled here as "once per DoctorShell mount," since the shell
// only remounts on a fresh sign-in, never on in-app navigation between its
// own routes. ⚠ §4's single most important constraint: never a hard block
// (dismissible, no gating of any other action) and suppressed entirely —
// not just softened — when an IMMEDIATE patient is in the queue.
export function IncompleteConsultationPrompt() {
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const navigate = useNavigate();
  const canSee = usePermission('CONSULTATION_CREATE');
  const [dismissed, setDismissed] = useState(false);

  const { data: incomplete } = useQuery({
    queryKey: ['consultation-records', 'incomplete'],
    queryFn: () => fetchIncompleteConsultations(accessToken as string),
    enabled: canSee && accessToken != null,
    staleTime: 60_000,
  });

  const { data: queue } = useQuery({
    queryKey: ['encounters', 'search', { states: ['AWAITING_CONSULT'] }],
    queryFn: () => searchEncounters({ states: ['AWAITING_CONSULT'] }, accessToken as string),
    enabled: canSee && accessToken != null,
    staleTime: 60_000,
  });
  const hasImmediateWaiting = (queue ?? []).some((e) => e.assignedProviderId === me?.userId && e.type === 'EMERGENCY');

  const shouldShow = canSee && !dismissed && !hasImmediateWaiting && (incomplete?.length ?? 0) > 0;

  if (!shouldShow) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="incomplete-prompt-title"
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0, 0, 0, 0.4)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 100,
      }}
    >
      <div
        style={{
          background: 'var(--bg-surface)',
          borderRadius: 'var(--radius-lg)',
          boxShadow: 'var(--shadow-lg)',
          padding: 'var(--space-4)',
          maxWidth: 560,
          width: '90%',
          maxHeight: '80vh',
          overflowY: 'auto',
        }}
      >
        <h2 id="incomplete-prompt-title" style={{ marginTop: 0 }}>
          {incomplete!.length} consultation{incomplete!.length === 1 ? '' : 's'} {incomplete!.length === 1 ? 'is' : 'are'} incomplete
        </h2>
        <p style={{ color: 'var(--text-secondary)' }}>
          These patients were seen but their records were never finalized. Please complete them.
        </p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', margin: 'var(--space-4) 0' }}>
          {incomplete!.map((record) => (
            <IncompleteRow
              key={record.id}
              record={record}
              onGoToConsultation={(encounterId) => {
                setDismissed(true);
                navigate(`/doctor/consultation/${encounterId}`);
              }}
            />
          ))}
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-2)' }}>
          <button type="button" className="btn btn-secondary" onClick={() => setDismissed(true)}>
            Later
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => {
              setDismissed(true);
              navigate(`/doctor/consultation/${incomplete![0].encounterId}`);
            }}
          >
            Go to first
          </button>
        </div>
      </div>
    </div>
  );
}

// §5.1's required-to-finalize free-text fields, read straight off the
// record already fetched for the modal. provisional_diagnosis is also
// required but isn't on ConsultationRecord itself (it lives as a primary
// Diagnosis row on the encounter) — omitted here rather than adding a
// per-row diagnosis fetch just for this summary line; "Complete" always
// opens the real consultation screen, which enforces the full requirement.
function missingRequiredFields(record: ConsultationRecordResponse): string[] {
  const fields: string[] = [];
  if (!record.historyOfPresentingIllness?.trim()) fields.push('history of presenting illness');
  if (!record.physicalExamination?.trim()) fields.push('examination');
  if (!record.treatmentPlan?.trim()) fields.push('treatment plan');
  return fields;
}

function IncompleteRow({
  record,
  onGoToConsultation,
}: {
  record: ConsultationRecordResponse;
  onGoToConsultation: (encounterId: string) => void;
}) {
  const { accessToken } = useAuth();
  const encounterId = record.encounterId as string;

  const { data: encounter } = useQuery({
    queryKey: ['encounters', encounterId],
    queryFn: () => fetchEncounterById(encounterId, accessToken as string),
    enabled: accessToken != null,
    staleTime: 60_000,
  });
  const { data: patient } = useQuery({
    queryKey: ['patients', encounter?.patientId],
    queryFn: () => fetchPatientById(encounter!.patientId as string, accessToken as string),
    enabled: accessToken != null && !!encounter?.patientId,
    staleTime: 60_000,
  });

  const missing = missingRequiredFields(record);

  return (
    <div style={{ borderBottom: '1px solid var(--border)', paddingBottom: 'var(--space-3)' }}>
      <div style={{ fontWeight: 'var(--weight-medium)' }}>
        {patient?.fullName ?? 'Loading…'} · {patient?.hospitalNo} · seen {formatDateTime(record.createdAt)} ·{' '}
        {formatWaitingSince(record.createdAt)} ago
      </div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 4 }}>
        <span style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
          Missing: {missing.length > 0 ? missing.join(', ') : 'a diagnosis or disposition'}
        </span>
        <button type="button" className="btn btn-secondary" onClick={() => onGoToConsultation(encounterId)}>
          Complete
        </button>
      </div>
    </div>
  );
}
