import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarDays, DoorOpen, History, Users } from 'lucide-react';
import { confirmDeparture, correctDeparture, fetchPresence, fetchVisitSummaries, type Presence } from '../api/presence';
import { useAuth } from '../auth/AuthProvider';
import { useAdminFacilityId } from '../auth/useFacilityScope';
import { usePermission } from '../auth/usePermission';
import { formatDateTime } from '../format/date';
import { Modal } from './Modal';
import { FeedbackState } from './FeedbackState';
import { TableRegion } from './TableRegion';
import './PatientPresencePanel.css';

const readable = (value?: string) => value ? (/[a-z]/.test(value) ? value : value.replaceAll('_', ' ').toLowerCase().replace(/^./, c => c.toUpperCase())) : 'Not recorded';
function presenceBadge(value?: string) {
  if (!value) return 'badge-neutral';
  if (value.toUpperCase().includes('LEFT') || value.toUpperCase().includes('DEPARTED')) return 'badge-neutral';
  if (value.toUpperCase().includes('REVIEW')) return 'badge-warning';
  return 'badge-info';
}

export function PatientPresencePanel({ patientId }: { patientId?: string }) {
 const { accessToken } = useAuth();
 const facilityId = useAdminFacilityId();
 const client = useQueryClient();
 const canCorrect = usePermission('ENCOUNTER_CLOSE_ADMIN');
 const [correction, setCorrection] = useState<Presence | null>(null);
 const [reason, setReason] = useState('');
 const canConfirm = usePermission('ENCOUNTER_OPEN');
 const [departed, setDeparted] = useState(false);
 const [selected, setSelected] = useState<Presence | null>(null);
 const [outcome, setOutcome] = useState('CARE_COMPLETE');
 const [note, setNote] = useState('');
 const visits = useQuery({ queryKey: ['here-now', facilityId, patientId, departed], queryFn: () => fetchPresence(facilityId!, accessToken!, patientId, departed), enabled: !!facilityId && !!accessToken, refetchInterval: 30_000 });
 const summary = useQuery({ queryKey: ['visit-summary', facilityId, patientId], queryFn: () => fetchVisitSummaries(facilityId!, [patientId!], accessToken!), enabled: !!facilityId && !!accessToken && !!patientId });
 const mutation = useMutation({ mutationFn: () => confirmDeparture(selected!.encounterId!, { facilityId: facilityId!, outcome, note }, accessToken!), onSuccess: () => { setSelected(null); client.invalidateQueries({ queryKey: ['here-now'] }); client.invalidateQueries({ queryKey: ['tat'] }); } });
 const correctionMutation = useMutation({ mutationFn: () => correctDeparture(correction!.encounterId!, { facilityId: facilityId!, departureId: correction!.departureId!, reason }, accessToken!), onSuccess: () => { setCorrection(null); client.invalidateQueries({ queryKey: ['here-now'] }); client.invalidateQueries({ queryKey: ['tat'] }); } });
 const s = summary.data?.[0];
 const current = visits.data?.[0];
 return <section className="analytics-card presence-panel">
  <header className="analytics-card-head"><div><h2>{patientId ? 'Patient visits and facility presence' : 'Here now · departure tracking'}</h2><p className="analytics-basis">Care stages indicate expected presence. Physical departure requires confirmation. Up to 200 encounters are shown.</p></div>{!patientId && <label className="presence-toggle"><input type="checkbox" checked={departed} onChange={e => setDeparted(e.target.checked)} /> Show recorded departures</label>}</header>
  {s && <div className="presence-summary"><div className="presence-summary-top"><span className={`badge ${(s.totalVisits ?? 0) > 1 ? 'badge-info' : 'badge-success'}`}>{(s.totalVisits ?? 0) > 1 ? 'Returning patient' : 'First visit'}</span>{current && <span className={`badge ${presenceBadge(current.presence)}`}>{readable(current.presence)}</span>}</div><div className="presence-summary-grid"><div><Users size={18} aria-hidden="true" /><span>Total visits</span><strong>{s.totalVisits ?? '—'}</strong></div><div><History size={18} aria-hidden="true" /><span>Previous visits</span><strong>{Math.max(0, (s.totalVisits ?? 0) - 1)}</strong></div><div><DoorOpen size={18} aria-hidden="true" /><span>Completed visits</span><strong>{s.completedVisits ?? '—'}</strong></div><div><CalendarDays size={18} aria-hidden="true" /><span>Current encounter</span><strong className="presence-summary-status">{s.activeEncounter ? 'Open encounter' : 'No open encounter'}</strong></div></div><p>First visit <strong>{formatDateTime(s.firstVisit)}</strong><span aria-hidden="true"> · </span>Most recent <strong>{formatDateTime(s.latestVisit)}</strong></p></div>}
  {!facilityId && <p className="presence-message">Select a facility scope to view presence.</p>}
  {visits.isLoading && <FeedbackState loading>Loading presence…</FeedbackState>}
  {visits.isError && <p className="presence-message" role="alert">Facility presence could not be loaded. <button type="button" className="link-button" onClick={() => visits.refetch()}>Retry</button></p>}
  {!visits.isLoading && !visits.isError && visits.data?.length === 0 && <p className="presence-message">No matching encounters.</p>}
  {!!visits.data?.length && <TableRegion label="Patient visits and facility presence"><table className="table presence-table"><thead><tr><th>Patient</th><th>Hospital number</th><th>Visit</th><th>Current stage</th><th>Encounter state</th><th>Facility presence</th><th>Arrival / departure</th><th>Action</th></tr></thead><tbody>{visits.data.map(v => <tr key={v.encounterId}><td><strong>{v.patientName}</strong></td><td className="tabular">{v.hospitalNo}</td><td><span className={`badge ${v.visitNumber === 1 ? 'badge-success' : 'badge-info'}`}>{v.visitNumber === 1 ? 'First visit' : `Revisit · ${v.visitNumber}`}</span></td><td>{readable(v.stage)}</td><td><span className="badge badge-neutral">{readable(v.state)}</span></td><td><span className={`badge ${presenceBadge(v.presence)}`}>{readable(v.presence)}</span></td><td><span>{formatDateTime(v.arrivedAt)}</span><small>{v.departedAt ? `Left: ${formatDateTime(v.departedAt)}` : 'Departure unconfirmed'}</small></td><td><div className="presence-actions">{canConfirm && !v.departedAt && <button type="button" className="btn btn-secondary" onClick={() => { setSelected(v); setOutcome('CARE_COMPLETE'); setNote(''); mutation.reset(); }}>Confirm departure</button>}{canCorrect && v.departureId && <button type="button" className="btn btn-secondary" onClick={() => { setCorrection(v); setReason(''); correctionMutation.reset(); }}>Correct departure</button>}</div></td></tr>)}</tbody></table></TableRegion>}
  <Modal open={!!selected} onClose={() => setSelected(null)} title="Confirm physical departure" description="Record when the patient has physically left the facility." footer={<><button type="button" className="btn btn-secondary" onClick={() => setSelected(null)}>Cancel</button><button type="button" className="btn btn-primary" disabled={mutation.isPending} onClick={() => mutation.mutate()}>{mutation.isPending ? 'Recording…' : 'Confirm departure'}</button></>}>
    <div className="presence-dialog-form"><p><strong>{selected?.patientName}</strong> · Departure is recorded at the current server time. Clinical records and outstanding balances are preserved.</p><label htmlFor="departure-outcome">Departure outcome<select id="departure-outcome" className="clinical-control" value={outcome} onChange={e => setOutcome(e.target.value)}><option value="CARE_COMPLETE">Care completed</option><option value="FOLLOW_UP_PENDING">Away — follow-up pending</option><option value="REFERRED_OUT">Referred out</option><option value="LEFT_WITHOUT_BEING_SEEN">Left without being seen</option><option value="ABANDONED_UNPAID">Existing abandoned encounter</option></select></label><label htmlFor="departure-note"><span>Departure note <small>(optional)</small></span><textarea id="departure-note" className="clinical-control" rows={3} maxLength={2000} value={note} onChange={e => setNote(e.target.value)} /></label>{mutation.isError && <p className="form-error" role="alert">{mutation.error.message}</p>}</div>
  </Modal>
  <Modal open={!!correction} onClose={() => setCorrection(null)} title="Correct mistaken departure" footer={<><button type="button" className="btn btn-secondary" onClick={() => setCorrection(null)}>Cancel</button><button type="button" className="btn btn-primary" disabled={!reason.trim() || correctionMutation.isPending} onClick={() => correctionMutation.mutate()}>{correctionMutation.isPending ? 'Recording…' : 'Record correction'}</button></>}><div className="presence-dialog-form"><p>The original event remains in the audit history. This correction withdraws the departure confirmation.</p><label htmlFor="departure-correction-reason">Reason<textarea id="departure-correction-reason" className="clinical-control" rows={3} maxLength={2000} value={reason} onChange={e => setReason(e.target.value)} /></label>{correctionMutation.isError && <p className="form-error" role="alert">{correctionMutation.error.message}</p>}</div></Modal>
 </section>;
}
