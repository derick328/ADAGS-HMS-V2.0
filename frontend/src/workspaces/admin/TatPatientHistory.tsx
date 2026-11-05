import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { fetchEncounterTat, fetchPatientTatVisits } from '../../shared/api/tat';
import { useAdminFacilityId } from '../../shared/auth/useFacilityScope';
import { usePermission } from '../../shared/auth/usePermission';
import { formatTatDuration, formatTatTimestamp, tatElapsedSeconds } from '../../shared/format/tat';

export function TatPatientHistory({ patientId, accessToken }: { patientId: string; accessToken: string }) {
  const facilityId = useAdminFacilityId();
  const canRead = usePermission('TAT_ANALYTICS_READ');
  const [selectedEncounterId, setSelectedEncounterId] = useState('');
  const visits = useQuery({ queryKey: ['tat', 'patient', facilityId, patientId],
    queryFn: () => fetchPatientTatVisits(facilityId as string, patientId, accessToken),
    enabled: Boolean(canRead && facilityId) });
  const selected = selectedEncounterId || visits.data?.[0]?.encounterId || '';
  const journey = useQuery({ queryKey: ['tat', 'encounter', facilityId, selected],
    queryFn: () => fetchEncounterTat(facilityId as string, selected, accessToken),
    enabled: Boolean(canRead && facilityId && selected) });
  const timezone = journey.data?.timezone || 'Africa/Dar_es_Salaam';
  const stages = journey.data?.stages ?? [];

  if (!canRead) return null;
  return <section className="analytics-card" style={{ marginTop: 'var(--space-4)' }}>
    <h2>Turnaround Time</h2>
    {visits.isError && <p role="alert">Turnaround history could not be loaded.</p>}
    {(visits.data ?? []).length === 0 && !visits.isLoading && <p>No encounters are recorded for this patient in this facility.</p>}
    {(visits.data ?? []).length > 0 && <>
      <label>Encounter <select className="clinical-control" value={selected} onChange={(e) => setSelectedEncounterId(e.target.value)}>
        {visits.data?.map((visit) => <option key={visit.encounterId} value={visit.encounterId}>
          {formatTatTimestamp(visit.arrivedAt, timezone)} · {visit.state} · {visit.encounterId}
        </option>)}
      </select></label>
      {journey.isError && <p role="alert">Selected encounter turnaround could not be loaded.</p>}
      {journey.data?.visit && <p>
        <strong>Total visit:</strong> {formatTatDuration(journey.data.visit.visitSeconds)} ·
        <strong> Total recorded waiting:</strong> {formatTatDuration(journey.data.totalRecordedWaitingSeconds)} ·
        arrived {formatTatTimestamp(journey.data.visit.arrivedAt, timezone)} ·
        {journey.data.visit.endedAt ? ` completed ${formatTatTimestamp(journey.data.visit.endedAt, timezone)}` : ' active visit'}
        {journey.data.visit.timestampAnomaly && <span className="badge badge-warning">Timestamp anomaly</span>}
      </p>}
      {['TRIAGE', 'DOCTOR', 'LABORATORY', 'PHARMACY'].map((workflow) => {
        const items = stages.filter((stage) => stage.workflowType === workflow);
        return <details key={workflow} style={{ borderTop: '1px solid var(--border)', padding: 'var(--space-3) 0' }}>
          <summary><strong>{workflow}</strong> · {items.length} {items.length === 1 ? 'service' : 'services'}</summary>
          {items.length === 0 && <p>Turnaround time was not recorded for this stage.</p>}
          {items.map((stage) => <div key={stage.referenceId} style={{ padding: 'var(--space-3)', marginTop: 'var(--space-2)', background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius)' }}>
            <p><strong>{stage.status}</strong> · Service {stage.referenceId}</p>
            <dl style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 'var(--space-2)' }}>
              <div><dt>Ordered / entered queue</dt><dd>{formatTatTimestamp(stage.sourceAt, timezone)}</dd></div>
              <div><dt>Ready / authorized</dt><dd>{formatTatTimestamp(stage.readyAt, timezone)}</dd></div>
              <div><dt>Service started</dt><dd>{formatTatTimestamp(stage.startedAt, timezone)}</dd></div>
              {stage.receivedAt && <div><dt>Sample received</dt><dd>{formatTatTimestamp(stage.receivedAt, timezone)}</dd></div>}
              {stage.enteredAt && <div><dt>Result entered</dt><dd>{formatTatTimestamp(stage.enteredAt, timezone)}</dd></div>}
              {stage.verifiedAt && <div><dt>Result verified</dt><dd>{formatTatTimestamp(stage.verifiedAt, timezone)}</dd></div>}
              <div><dt>Completed / released</dt><dd>{formatTatTimestamp(stage.completedAt, timezone)}</dd></div>
              <div><dt>Waiting</dt><dd>{formatTatDuration(stage.waitingSeconds)}</dd></div>
              <div><dt>Service</dt><dd>{formatTatDuration(stage.serviceSeconds)}</dd></div>
              <div><dt>Total stage TAT</dt><dd>{formatTatDuration(stage.totalTatSeconds)}</dd></div>
              {(workflow === 'LABORATORY' || workflow === 'PHARMACY') &&
                <div><dt>Authorization wait</dt><dd>{formatTatDuration(tatElapsedSeconds(stage.sourceAt, stage.readyAt))}</dd></div>}
              {workflow === 'LABORATORY' && <>
                <div><dt>Sample processing to result entry</dt><dd>{formatTatDuration(tatElapsedSeconds(stage.startedAt, stage.enteredAt))}</dd></div>
                <div><dt>Verification wait after entry</dt><dd>{formatTatDuration(tatElapsedSeconds(stage.enteredAt, stage.verifiedAt))}</dd></div>
              </>}
            </dl>
            {stage.timestampAnomaly && <p role="alert">Event timestamps are out of order; affected durations are omitted.</p>}
          </div>)}
        </details>;
      })}
    </>}
  </section>;
}
