import './PreviousMedicalHistory.css';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { LabResultAttachments } from '../../shared/components/LabResultAttachments';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { fetchConsultationRecordsByEncounter, fetchDiagnosesByEncounter } from '../../shared/api/clinical';
import { fetchEncountersByPatient, type EncounterResponse } from '../../shared/api/encounters';
import { fetchLabOrdersByEncounter } from '../../shared/api/labOrders';
import { fetchLabResultsByOrder } from '../../shared/api/labResults';
import { fetchPrescriptionsByEncounter } from '../../shared/api/prescriptions';
import { fetchTriageHistoryByEncounter } from '../../shared/api/triage';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDateTime } from '../../shared/format/date';

export function PreviousMedicalHistory({ patientId, currentEncounterId }: { patientId?: string; currentEncounterId: string }) {
  const { accessToken } = useAuth();
  const canSee = usePermission('PATIENT_READ_VISIT_HISTORY');
  const encounters = useQuery({ queryKey: ['encounters', 'by-patient', patientId],
    queryFn: () => fetchEncountersByPatient(patientId!, accessToken!), enabled: !!patientId && !!accessToken && canSee });
  if (!canSee || !patientId) return null;
  const current = encounters.data?.find(e => e.id === currentEncounterId);
  const prior = (encounters.data ?? []).filter(e => e.id !== currentEncounterId && e.state !== 'CANCELLED_IN_ERROR' &&
    (!current?.createdAt || (e.createdAt ?? '') <= current.createdAt)).sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
  return <section className="history-panel" aria-label="Previous medical history">
    <h2>Previous medical history</h2>
    {encounters.isLoading ? <FeedbackState loading>Loading previous visits…</FeedbackState> :
      encounters.isError ? <FeedbackState>Previous visits could not be loaded.</FeedbackState> :
      prior.length === 0 ? <FeedbackState>No earlier visits on record.</FeedbackState> :
      prior.map(e => <HistoryEncounter key={e.id} encounter={e} />)}
  </section>;
}

function HistoryEncounter({ encounter }: { encounter: EncounterResponse }) {
  const [open, setOpen] = useState(false);
  const { accessToken } = useAuth();
  const id = encounter.id!;
  const triage = useQuery({ queryKey: ['triage-history', id], queryFn: () => fetchTriageHistoryByEncounter(id, accessToken!), enabled: open && !!accessToken });
  const notes = useQuery({ queryKey: ['consultation-records', 'by-encounter', id], queryFn: () => fetchConsultationRecordsByEncounter(id, accessToken!), enabled: open && !!accessToken });
  const diagnoses = useQuery({ queryKey: ['diagnoses', id], queryFn: () => fetchDiagnosesByEncounter(id, accessToken!), enabled: open && !!accessToken });
  const medicines = useQuery({ queryKey: ['prescriptions', 'by-encounter', id], queryFn: () => fetchPrescriptionsByEncounter(id, accessToken!), enabled: open && !!accessToken });
  const labs = useQuery({ queryKey: ['lab-orders', 'by-encounter', id], queryFn: () => fetchLabOrdersByEncounter(id, accessToken!), enabled: open && !!accessToken });
  const loading = triage.isLoading || notes.isLoading || diagnoses.isLoading || medicines.isLoading || labs.isLoading;
  const error = triage.isError || notes.isError || diagnoses.isError || medicines.isError || labs.isError;
  const fields = [
    ['Chief complaint / presenting history', 'historyOfPresentingIllness'], ['Past medical history', 'pastMedicalHistory'],
    ['Past surgical history', 'pastSurgicalHistory'], ['Comorbidities', 'comorbidities'], ['Family and social history', 'familySocialHistory'],
    ['Examination findings', 'physicalExamination'], ['Consultation notes', 'clinicalNotes'], ['Treatment plan', 'treatmentPlan'],
  ] as const;
  return <details className="history-encounter" open={open} onToggle={event => setOpen(event.currentTarget.open)}>
    <summary><strong>{formatDateTime(encounter.createdAt)}</strong><span>{encounter.clinicianName ?? 'Clinician unassigned'} · {(encounter.state ?? '').replaceAll('_', ' ').toLowerCase()}</span></summary>
    {open && <div className="history-encounter-content">
      {loading ? <FeedbackState loading>Loading clinical record…</FeedbackState> : error ? <FeedbackState>Clinical history could not be loaded or access was denied.</FeedbackState> : <>
        <h3>Chief complaint</h3>
        <p>{triage.data?.[0]?.chiefComplaint || 'None recorded.'}</p>
        <h3>Consultation</h3>
        {(notes.data ?? []).length === 0 ? <p>No consultation note recorded.</p> : notes.data?.map(note => <article key={note.id} className="history-note">
          <p className="history-muted">Clinician ID: {note.authorId ?? 'Unavailable'} · {note.status?.toLowerCase()}</p>
          {fields.map(([label, key]) => note[key] ? <div key={key}><strong>{label}</strong><p>{note[key]}</p></div> : null)}
        </article>)}
        <h3>Diagnoses</h3>
        {(diagnoses.data ?? []).length === 0 ? <p>None recorded.</p> : <ul>{diagnoses.data?.map(d => <li key={d.id}>{d.icd10Code} {d.icd10TitleSnapshot}{d.freeTextClarifier ? ` — ${d.freeTextClarifier}` : ''}{d.isPrimary ? ' (primary)' : ''}</li>)}</ul>}
        <h3>Prescribed medicines</h3>
        {(medicines.data ?? []).length === 0 ? <p>None recorded.</p> : <ul>{medicines.data?.map(m => <li key={m.prescriptionLineId}><strong>{m.itemName}</strong> · {m.dosageInstructions ?? `${m.doseAmount ?? '—'} ${m.doseUnit ?? ''} ${m.route ?? ''} ${m.frequency ?? ''} for ${m.durationDays ?? '—'} days`} · prescribed {m.quantityPrescribed ?? '—'}, dispensed {m.quantityDispensed ?? 0}</li>)}</ul>}
        <h3>Laboratory requests</h3>
        {(labs.data ?? []).length === 0 ? <p>None recorded.</p> : labs.data?.map(order => <div key={order.id}><strong>{order.labTestName}</strong> · {order.status}{order.id && <ReleasedResults orderId={order.id} />}</div>)}
      </>}
    </div>}
  </details>;
}

function ReleasedResults({ orderId }: { orderId: string }) {
  const { accessToken } = useAuth();
  const results = useQuery({ queryKey: ['lab-results', orderId], queryFn: () => fetchLabResultsByOrder(orderId, accessToken!), enabled: !!accessToken });
  const released = results.data?.filter(r => r.releasedAt) ?? [];
  return results.isLoading ? <p>Loading results…</p> : results.isError ? <p>Results unavailable.</p> : released.length === 0 ? <p>No released results.</p> :
    <ul>{released.map(r => <li key={r.id}><span>Released {formatDateTime(r.releasedAt)} · verification: {r.verificationState ?? '—'}</span>
      {r.singleValue && <p>Value: {r.singleValue} {r.singleValueUnit}</p>}
      {r.findings && <p>{r.findings}</p>}
      {r.interpretation && <p>{r.interpretation}</p>}
      {r.parameterValues?.length ? <div className="history-table-scroll"><table className="table"><thead><tr><th>Parameter</th><th>Value</th><th>Reference range</th></tr></thead><tbody>{r.parameterValues.map(v => <tr key={v.parameterId}><td>{v.parameterName}</td><td>{v.resultValue} {v.unit}</td><td>{v.normalLow ?? '—'} – {v.normalHigh ?? '—'}</td></tr>)}</tbody></table></div> : null}
      <LabResultAttachments resultId={r.id!} machineOutputObjectKey={r.machineOutputObjectKey} labReportObjectKey={r.labReportObjectKey} />
    </li>)}</ul>;
}
