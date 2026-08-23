import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchPatientById } from '../../shared/api/patients';
import {
  searchCompletedAssessments,
  type CompletedTriageAssessmentResponse,
} from '../../shared/api/triage';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { dayRange, formatDateTime, todayLocalDate } from '../../shared/format/date';

// 06-triage.md §1: "Completed Assessments" — read-only. §7.12 ("this
// nurse's work only, or the whole shift's?") is unresolved in the spec
// itself; the brief resolves it as both, so this screen shows two
// sections against the same date filter rather than picking one. "Whole
// shift" is implemented as "the whole selected day" — no shift/roster
// boundary is wired to this screen (work_shift exists elsewhere in the
// system but nothing connects it here), so a calendar day is the closest
// honest stand-in, not a guess at shift start/end times.
//
// Printable per assessment prints exactly the fields this screen already
// has — chief complaint, symptoms, allergies, comorbidities, mode of
// ambulation, pain/fall/infection risk, pregnancy status, priority and its
// reason, notes, who/when. Deliberately NOT included: the vitals recorded
// alongside it. VitalSigns has no read endpoint anywhere in the backend —
// only ever written, during triage submission and ward rounds — so
// showing them here would mean building new backend surface for a print
// nicety rather than reporting what already exists, same reasoning as the
// arrival-mode and on-duty-status gaps flagged earlier in this build.
export function CompletedAssessmentsPage() {
  const canRead = usePermission('TRIAGE_HISTORY_READ');
  const [selectedDate, setSelectedDate] = useState(todayLocalDate());
  const [printing, setPrinting] = useState<CompletedTriageAssessmentResponse | null>(null);

  if (!canRead) {
    return <p style={{ color: 'var(--text-muted)' }}>You don't hold TRIAGE_HISTORY_READ.</p>;
  }

  const { from, to } = dayRange(selectedDate);

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)', display: 'flex', alignItems: 'baseline', gap: 'var(--space-3)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>Completed Assessments</h1>
      </header>

      <div style={{ marginBottom: 'var(--space-4)' }}>
        <label style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 'var(--space-1)' }}>Date</label>
        <input
          type="date"
          value={selectedDate}
          onChange={(e) => setSelectedDate(e.target.value)}
          max={todayLocalDate()}
          style={{ padding: 'var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}
        />
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
        <AssessmentSection
          title="My assessments"
          ownOnly
          assessedFrom={from}
          assessedTo={to}
          showAssessor={false}
          onPrint={setPrinting}
        />
        <AssessmentSection
          title="Whole shift"
          ownOnly={false}
          assessedFrom={from}
          assessedTo={to}
          showAssessor
          onPrint={setPrinting}
        />
      </div>

      {printing && <PrintOverlay assessment={printing} onClose={() => setPrinting(null)} />}
    </div>
  );
}

function AssessmentSection({
  title,
  ownOnly,
  assessedFrom,
  assessedTo,
  showAssessor,
  onPrint,
}: {
  title: string;
  ownOnly: boolean;
  assessedFrom: string;
  assessedTo: string;
  showAssessor: boolean;
  onPrint: (assessment: CompletedTriageAssessmentResponse) => void;
}) {
  const { accessToken } = useAuth();

  const { data: assessments, isLoading } = useQuery({
    queryKey: ['completed-assessments', ownOnly, assessedFrom, assessedTo],
    queryFn: () => searchCompletedAssessments({ ownOnly, assessedFrom, assessedTo }, accessToken as string),
    enabled: accessToken != null,
  });

  return (
    <section style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
      <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', margin: 0, padding: 'var(--space-4) var(--space-4) 0' }}>
        {title}
      </h2>
      {isLoading ? (
        <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>Loading…</p>
      ) : !assessments || assessments.length === 0 ? (
        <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>No assessments for this date.</p>
      ) : (
        <table className="table">
          <caption className="sr-only">{title}</caption>
          <thead>
            <tr>
              <th>Patient</th>
              <th>Chief complaint</th>
              <th>Priority</th>
              {showAssessor && <th>Assessed by</th>}
              <th>Assessed at</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {assessments.map((a) => (
              <AssessmentRow key={a.id} assessment={a} showAssessor={showAssessor} onPrint={() => onPrint(a)} />
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function AssessmentRow({
  assessment,
  showAssessor,
  onPrint,
}: {
  assessment: CompletedTriageAssessmentResponse;
  showAssessor: boolean;
  onPrint: () => void;
}) {
  const { accessToken } = useAuth();
  const { data: patient } = useQuery({
    queryKey: ['patient', assessment.patientId],
    queryFn: () => fetchPatientById(assessment.patientId as string, accessToken as string),
    enabled: accessToken != null && assessment.patientId != null,
    staleTime: 60_000,
  });

  const isImmediate = assessment.priority === 'IMMEDIATE';

  return (
    <tr className={isImmediate ? 'row-critical' : undefined}>
      <td style={{ fontWeight: 'var(--weight-medium)' }}>
        {patient?.fullName ?? '…'}
        {patient?.hospitalNo && <span style={{ color: 'var(--text-muted)', fontWeight: 'var(--weight-normal)' }}> · {patient.hospitalNo}</span>}
      </td>
      <td>{assessment.chiefComplaint}</td>
      <td>
        <span className={isImmediate ? 'badge badge-critical' : 'badge badge-neutral'}>{assessment.priority}</span>
      </td>
      {showAssessor && <td>{assessment.assessedByUsername ?? '—'}</td>}
      <td style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>{formatDateTime(assessment.assessedAt)}</td>
      <td style={{ width: 1, whiteSpace: 'nowrap' }}>
        <button type="button" className="btn btn-secondary" onClick={onPrint}>
          Print
        </button>
      </td>
    </tr>
  );
}

function PrintOverlay({ assessment, onClose }: { assessment: CompletedTriageAssessmentResponse; onClose: () => void }) {
  const { accessToken } = useAuth();
  const { data: patient } = useQuery({
    queryKey: ['patient', assessment.patientId],
    queryFn: () => fetchPatientById(assessment.patientId as string, accessToken as string),
    enabled: accessToken != null && assessment.patientId != null,
  });

  // Fires once this overlay's content has actually rendered, so
  // window.print() captures the real detail rather than a still-loading
  // placeholder.
  useEffect(() => {
    if (patient) {
      window.print();
    }
  }, [patient]);

  return (
    <div
      role="dialog"
      aria-modal="true"
      style={{ position: 'fixed', inset: 0, background: 'rgba(0, 0, 0, 0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 }}
    >
      <div style={{ background: 'var(--bg-surface)', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-lg)', padding: 'var(--space-4)', maxWidth: 560, width: '90%', maxHeight: '85vh', overflowY: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
          <h2 style={{ marginTop: 0, fontSize: 'var(--text-lg)' }}>Triage assessment</h2>
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Close
          </button>
        </div>
        <p style={{ color: 'var(--text-secondary)' }}>
          {patient?.fullName ?? '…'} · {patient?.hospitalNo} · assessed {formatDateTime(assessment.assessedAt)}
          {assessment.assessedByUsername ? ` by ${assessment.assessedByUsername}` : ''}
        </p>
        <DetailRow label="Priority" value={assessment.priority} />
        {assessment.priority === 'IMMEDIATE' && (
          <DetailRow
            label="Immediate reason"
            value={assessment.immediateReason === 'OTHER' ? assessment.immediateReasonOtherText : assessment.immediateReason}
          />
        )}
        <DetailRow label="Chief complaint" value={assessment.chiefComplaint} />
        <DetailRow label="Current symptoms" value={assessment.currentSymptoms} />
        <div style={{ padding: 'var(--space-2)', background: 'var(--warning-bg)', borderRadius: 'var(--radius-sm)', margin: 'var(--space-2) 0' }}>
          <DetailRow label="Allergies" value={assessment.allergies} />
        </div>
        <DetailRow label="Comorbidities" value={assessment.comorbidities} />
        <DetailRow label="Mode of ambulation" value={assessment.modeOfAmbulation} />
        <DetailRow label="Pain" value={assessment.hasPain ? `${assessment.painScore}/10 at ${assessment.painLocation}` : 'None'} />
        <DetailRow label="Fall risk" value={assessment.fallRisk ? `Yes (score ${assessment.fallScore})` : 'No'} />
        <DetailRow label="Infection risk" value={assessment.infectionRisk ? 'Yes' : 'No'} />
        {assessment.pregnancyStatus && <DetailRow label="Pregnancy status" value={assessment.pregnancyStatus} />}
        {assessment.notes && <DetailRow label="Notes" value={assessment.notes} />}
        <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)', marginTop: 'var(--space-3)' }}>
          Vitals recorded at triage are not shown here — no read endpoint for them exists yet.
        </p>
      </div>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <p style={{ margin: 'var(--space-1) 0' }}>
      <strong>{label}:</strong> {value ?? '—'}
    </p>
  );
}
