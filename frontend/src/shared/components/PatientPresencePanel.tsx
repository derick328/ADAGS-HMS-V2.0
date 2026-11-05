import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { confirmDeparture, correctDeparture, fetchPresence, fetchVisitSummaries, type Presence } from '../api/presence';
import { useAuth } from '../auth/AuthProvider';
import { useAdminFacilityId } from '../auth/useFacilityScope';
import { usePermission } from '../auth/usePermission';
import { formatDateTime } from '../format/date';
import { Modal } from './Modal';
import { FeedbackState } from './FeedbackState';

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
 const visits = useQuery({ queryKey: ['here-now', facilityId, patientId, departed],
  queryFn: () => fetchPresence(facilityId!, accessToken!, patientId, departed), enabled: !!facilityId && !!accessToken, refetchInterval: 30_000 });
 const summary = useQuery({ queryKey: ['visit-summary', facilityId, patientId],
  queryFn: () => fetchVisitSummaries(facilityId!, [patientId!], accessToken!), enabled: !!facilityId && !!accessToken && !!patientId });
 const mutation = useMutation({ mutationFn: () => confirmDeparture(selected!.encounterId!, { facilityId: facilityId!, outcome, note }, accessToken!),
  onSuccess: () => { setSelected(null); client.invalidateQueries({ queryKey: ['here-now'] }); client.invalidateQueries({ queryKey: ['tat'] }); } });
 const correctionMutation = useMutation({ mutationFn: () => correctDeparture(correction!.encounterId!, { facilityId: facilityId!, departureId: correction!.departureId!, reason }, accessToken!),
  onSuccess: () => { setCorrection(null); client.invalidateQueries({ queryKey: ['here-now'] }); client.invalidateQueries({ queryKey: ['tat'] }); } });
 const s = summary.data?.[0];
 return <section className="analytics-card">
  <h2>{patientId ? 'Patient visits and facility presence' : 'Here now · departure tracking'}</h2>
  <p>Care stages indicate expected presence. Physical departure requires confirmation. Up to 200 encounters are shown.</p>
  {s && <p>{(s.totalVisits ?? 0) > 1 ? 'Returning patient' : 'First visit'} · Total recorded visits: {s.totalVisits} · Previous visits: {Math.max(0, (s.totalVisits ?? 0) - 1)} · Completed visits: {s.completedVisits}<br />
   First visit: {formatDateTime(s.firstVisit)} · Most recent: {formatDateTime(s.latestVisit)} · {s.activeEncounter ? 'Open encounter recorded' : 'No open encounter'}</p>}
  {!patientId && <label><input type="checkbox" checked={departed} onChange={e => setDeparted(e.target.checked)} /> Show recorded departures</label>}
  {!facilityId && <p>Select a facility scope to view presence.</p>}
  {visits.isLoading && <FeedbackState loading>Loading presence…</FeedbackState>}
  {visits.isError && <p role="alert">Facility presence could not be loaded.</p>}
  <div style={{ overflowX: 'auto' }}><table className="table"><thead><tr>
   <th>Patient</th><th>Hospital number</th><th>Visit</th><th>Current stage</th><th>Encounter state</th><th>Facility presence</th><th>Arrival / departure</th><th>Action</th>
  </tr></thead><tbody>{visits.data?.map(v => <tr key={v.encounterId}>
   <td>{v.patientName}</td><td>{v.hospitalNo}</td><td>{v.visitNumber === 1 ? 'First visit' : `Revisit · ${v.visitNumber}`}</td>
   <td>{v.stage}</td><td>{v.state?.replaceAll('_', ' ').toLowerCase()}</td><td><span className="badge badge-info">{v.presence}</span></td>
   <td>{formatDateTime(v.arrivedAt)}<br />{v.departedAt ? `Left: ${formatDateTime(v.departedAt)}` : 'Departure unconfirmed'}</td>
   <td>{canConfirm && !v.departedAt && <button type="button" className="btn btn-secondary" onClick={() => { setSelected(v); setOutcome('CARE_COMPLETE'); setNote(''); mutation.reset(); }}>Confirm departure</button>}{canCorrect && v.departureId && <button className="btn btn-secondary" onClick={() => { setCorrection(v); setReason(''); correctionMutation.reset(); }}>Correct mistaken departure</button>}</td>
  </tr>)}</tbody></table></div>
  {visits.data?.length === 0 && <p>No matching encounters.</p>}
  <Modal open={!!selected} onClose={() => setSelected(null)} title="Confirm physical departure" footer={<button className="btn btn-primary" disabled={mutation.isPending} onClick={() => mutation.mutate()}>Record departure now</button>}>
   <p>{selected?.patientName} · This records departure at the current server time. Clinical records and outstanding balances are preserved.</p>
   <label>Departure outcome<select className="clinical-control" value={outcome} onChange={e => setOutcome(e.target.value)}>
    <option value="CARE_COMPLETE">Care completed</option><option value="FOLLOW_UP_PENDING">Away — follow-up pending</option><option value="REFERRED_OUT">Referred out</option><option value="LEFT_WITHOUT_BEING_SEEN">Left without being seen</option><option value="ABANDONED_UNPAID">Existing abandoned encounter</option>
   </select></label>
   <label>Optional departure note<textarea className="clinical-control" maxLength={2000} value={note} onChange={e => setNote(e.target.value)} /></label>
   {mutation.isError && <p role="alert">{mutation.error.message}</p>}
  </Modal>
  <Modal open={!!correction} onClose={() => setCorrection(null)} title="Correct mistaken departure" footer={<button className="btn btn-primary" disabled={!reason.trim() || correctionMutation.isPending} onClick={() => correctionMutation.mutate()}>Record correction</button>}>
   <p>The original event remains in the audit history. This correction withdraws the departure confirmation.</p>
   <label>Reason<textarea className="clinical-control" maxLength={2000} value={reason} onChange={e => setReason(e.target.value)} /></label>
   {correctionMutation.isError && <p role="alert">{correctionMutation.error.message}</p>}
  </Modal>
 </section>;
}
