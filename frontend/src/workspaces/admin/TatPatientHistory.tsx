import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Clock3, DoorOpen, Hourglass, MapPin } from 'lucide-react';
import { fetchEncounterTat, fetchPatientTatVisits } from '../../shared/api/tat';
import { useAdminFacilityId } from '../../shared/auth/useFacilityScope';
import { usePermission } from '../../shared/auth/usePermission';
import { formatTatDuration, formatTatTimestamp, tatElapsedSeconds } from '../../shared/format/tat';
import './TatPatientHistory.css';

const label = (value?: string) => value ? value.replaceAll('_', ' ').toLowerCase().replace(/^./, c => c.toUpperCase()) : 'Not recorded';
const shortId = (value?: string) => value ? value.slice(0, 8).toUpperCase() : '—';

export function TatPatientHistory({ patientId, accessToken }: { patientId: string; accessToken: string }) {
  const facilityId = useAdminFacilityId();
  const canRead = usePermission('TAT_ANALYTICS_READ');
  const [selectedEncounterId, setSelectedEncounterId] = useState('');
  const visits = useQuery({ queryKey: ['tat', 'patient', facilityId, patientId], queryFn: () => fetchPatientTatVisits(facilityId as string, patientId, accessToken), enabled: Boolean(canRead && facilityId) });
  const selected = selectedEncounterId || visits.data?.[0]?.encounterId || '';
  const journey = useQuery({ queryKey: ['tat', 'encounter', facilityId, selected], queryFn: () => fetchEncounterTat(facilityId as string, selected, accessToken), enabled: Boolean(canRead && facilityId && selected) });
  const timezone = journey.data?.timezone || 'Africa/Dar_es_Salaam';
  const stages = journey.data?.stages ?? [];
  if (!canRead) return null;
  return <section className="analytics-card patient-journey">
    <header className="analytics-card-head"><div><h2>Turnaround time history</h2><p className="analytics-basis">A recorded journey through care. Missing timestamps and durations remain unmeasured.</p></div></header>
    {visits.isLoading && <p className="journey-message" role="status">Loading encounter history…</p>}
    {visits.isError && <p className="journey-message" role="alert">Turnaround history could not be loaded. <button type="button" className="link-button" onClick={() => visits.refetch()}>Retry</button></p>}
    {(visits.data ?? []).length === 0 && !visits.isLoading && !visits.isError && <p className="journey-message">No encounters are recorded for this patient in this facility.</p>}
    {!!visits.data?.length && <div className="journey-body">
      <label className="journey-selector">Encounter<select className="clinical-control" value={selected} onChange={e => setSelectedEncounterId(e.target.value)}>{visits.data.map(visit => <option key={visit.encounterId} value={visit.encounterId}>{formatTatTimestamp(visit.arrivedAt, timezone)} · {label(visit.type)} · {label(visit.state)} · #{shortId(visit.encounterId)}</option>)}</select></label>
      {journey.isLoading && <p className="journey-message" role="status">Loading patient journey…</p>}
      {journey.isError && <p className="journey-message" role="alert">Selected encounter turnaround could not be loaded. <button type="button" className="link-button" onClick={() => journey.refetch()}>Retry</button></p>}
      {journey.data?.visit && <><div className="journey-summary">
        <div><Clock3 size={19} aria-hidden="true" /><span>Total visit duration</span><strong>{formatTatDuration(journey.data.visit.visitSeconds)}</strong></div>
        <div><Hourglass size={19} aria-hidden="true" /><span>Recorded waiting</span><strong>{formatTatDuration(journey.data.totalRecordedWaitingSeconds)}</strong></div>
        <div><MapPin size={19} aria-hidden="true" /><span>Arrival</span><strong>{formatTatTimestamp(journey.data.visit.arrivedAt, timezone)}</strong></div>
        <div><DoorOpen size={19} aria-hidden="true" /><span>Confirmed departure</span><strong>{journey.data.visit.endedAt ? formatTatTimestamp(journey.data.visit.endedAt, timezone) : 'Departure not recorded'}</strong></div>
      </div>{journey.data.visit.timestampAnomaly && <p className="journey-anomaly" role="alert">Event timestamps are out of order; affected durations are omitted.</p>}
      <div className="journey-timeline"><h3>Department timeline</h3>{['TRIAGE', 'DOCTOR', 'LABORATORY', 'PHARMACY'].map((workflow, index) => { const items = stages.filter(stage => stage.workflowType === workflow); return <details key={workflow} className="journey-stage" open={items.length > 0 && index === 0}><summary><span className="journey-stage-index">{index + 1}</span><span className="journey-stage-name">{label(workflow)}<small>{items.length} {items.length === 1 ? 'service' : 'services'}</small></span><span className={`badge ${items.length ? 'badge-info' : 'badge-neutral'}`}>{items.length ? 'Recorded' : 'No record'}</span></summary>{items.length === 0 && <p className="journey-stage-empty">Turnaround time was not recorded for this stage.</p>}{items.map(stage => <div key={stage.referenceId} className="journey-service"><div className="journey-service-head"><strong>{label(stage.status)}</strong><span title={stage.referenceId}>Service #{shortId(stage.referenceId)}</span></div><dl><div><dt>Ordered / entered queue</dt><dd>{formatTatTimestamp(stage.sourceAt, timezone)}</dd></div><div><dt>Ready / authorized</dt><dd>{formatTatTimestamp(stage.readyAt, timezone)}</dd></div><div><dt>Service started</dt><dd>{formatTatTimestamp(stage.startedAt, timezone)}</dd></div>{stage.receivedAt && <div><dt>Sample received</dt><dd>{formatTatTimestamp(stage.receivedAt, timezone)}</dd></div>}{stage.enteredAt && <div><dt>Result entered</dt><dd>{formatTatTimestamp(stage.enteredAt, timezone)}</dd></div>}{stage.verifiedAt && <div><dt>Result verified</dt><dd>{formatTatTimestamp(stage.verifiedAt, timezone)}</dd></div>}<div><dt>Completed / released</dt><dd>{formatTatTimestamp(stage.completedAt, timezone)}</dd></div><div><dt>Waiting</dt><dd>{formatTatDuration(stage.waitingSeconds)}</dd></div><div><dt>Service</dt><dd>{formatTatDuration(stage.serviceSeconds)}</dd></div><div><dt>Total stage TAT</dt><dd>{formatTatDuration(stage.totalTatSeconds)}</dd></div>{(workflow === 'LABORATORY' || workflow === 'PHARMACY') && <div><dt>Authorization wait</dt><dd>{formatTatDuration(tatElapsedSeconds(stage.sourceAt, stage.readyAt))}</dd></div>}{workflow === 'LABORATORY' && <><div><dt>Sample processing to result entry</dt><dd>{formatTatDuration(tatElapsedSeconds(stage.startedAt, stage.enteredAt))}</dd></div><div><dt>Verification wait after entry</dt><dd>{formatTatDuration(tatElapsedSeconds(stage.enteredAt, stage.verifiedAt))}</dd></div></>}</dl>{stage.timestampAnomaly && <p className="journey-anomaly" role="alert">Event timestamps are out of order; affected durations are omitted.</p>}</div>)}</details>; })}</div></>}
    </div>}
  </section>;
}
