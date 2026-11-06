import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { fetchEncounterTat, fetchTatDepartmentUsers, fetchTatUserPatients } from '../../shared/api/tat';
import { TableRegion } from '../../shared/components/TableRegion';
import { formatTatDuration, formatTatTimestamp } from '../../shared/format/tat';

type Period = { facilityId: string; from: string; to: string };
const label = (value?: string) => value ? value.toLowerCase().replaceAll('_', ' ').replace(/^./, c => c.toUpperCase()) : 'Not recorded';

export function TatDepartmentDrilldown({ workflowType, period, timezone, token }: {
  workflowType: string; period: Period; timezone: string; token: string;
}) {
  const [userId, setUserId] = useState('');
  const [patientEncounterId, setPatientEncounterId] = useState('');
  const [page, setPage] = useState(0);
  const users = useQuery({ queryKey: ['tat', 'department-users', period, workflowType], queryFn: () => fetchTatDepartmentUsers({ ...period, workflowType }, token) });
  const patients = useQuery({ queryKey: ['tat', 'user-patients', period, workflowType, userId, page], queryFn: () => fetchTatUserPatients({ ...period, workflowType, userId, page }, token), enabled: Boolean(userId) });
  const journey = useQuery({ queryKey: ['tat', 'encounter', period.facilityId, patientEncounterId], queryFn: () => fetchEncounterTat(period.facilityId, patientEncounterId, token), enabled: Boolean(patientEncounterId) });
  const selectedUser = users.data?.find(user => user.userId === userId);
  const selectedPatient = patients.data?.find(patient => patient.encounterId === patientEncounterId);
  const selectedStages = journey.data?.stages?.filter(stage => stage.workflowType === workflowType) ?? [];
  return <div className="tat-drilldown">
    <div className="tat-section-heading"><h3>{label(workflowType)} users</h3><span>{period.from} – {period.to}</span></div>
    {users.isLoading ? <p className="tat-empty" role="status">Loading user summaries…</p> : users.isError ? <p className="tat-empty" role="alert">User summaries could not be loaded. <button className="link-button" onClick={() => users.refetch()}>Retry</button></p> : !users.data?.length ? <p className="tat-empty">No service users were recorded for this department and period.</p> :
      <TableRegion label={`${label(workflowType)} user turnaround summaries`}><table className="table tat-table"><thead><tr><th>User</th><th>Patients</th><th>Services</th><th>Completed</th><th>Average wait</th><th>Average service</th><th>Average total TAT</th></tr></thead><tbody>{users.data.map(user => <tr key={user.userId}><td><button type="button" className="link-button" aria-expanded={userId === user.userId} onClick={() => { setUserId(userId === user.userId ? '' : user.userId); setPatientEncounterId(''); setPage(0); }}>{user.userName}</button></td><td>{user.patients}</td><td>{user.services}</td><td>{user.completedServices}</td><td>{formatTatDuration(user.averageWaitingSeconds)}</td><td>{formatTatDuration(user.averageServiceSeconds)}</td><td>{formatTatDuration(user.averageTatSeconds)}</td></tr>)}</tbody></table></TableRegion>}
    {userId && <div className="tat-drilldown-level"><div className="tat-section-heading"><h3>Patients seen by {selectedUser?.userName ?? 'selected user'}</h3><span>One row per encounter</span></div>
      {patients.isLoading ? <p className="tat-empty" role="status">Loading patient summaries…</p> : patients.isError ? <p className="tat-empty" role="alert">Patient summaries could not be loaded. <button className="link-button" onClick={() => patients.refetch()}>Retry</button></p> : !patients.data?.length ? <p className="tat-empty">No patient services found.</p> :
        <TableRegion label="Patient turnaround summaries"><table className="table tat-table"><thead><tr><th>Patient</th><th>First service</th><th>Services</th><th>Completed</th><th>Average wait</th><th>Average service</th><th>Average total TAT</th></tr></thead><tbody>{patients.data.map(patient => <tr key={patient.encounterId}><td><button type="button" className="link-button" aria-expanded={patientEncounterId === patient.encounterId} onClick={() => setPatientEncounterId(patientEncounterId === patient.encounterId ? '' : patient.encounterId)}>{patient.patientName}</button><small>{patient.hospitalNo}</small></td><td>{formatTatTimestamp(patient.firstServiceAt, timezone)}</td><td>{patient.services}</td><td>{patient.completedServices}</td><td>{formatTatDuration(patient.averageWaitingSeconds)}</td><td>{formatTatDuration(patient.averageServiceSeconds)}</td><td>{formatTatDuration(patient.averageTatSeconds)}</td></tr>)}</tbody></table></TableRegion>}
      <div className="tat-pagination"><button type="button" className="btn btn-secondary" disabled={page === 0 || patients.isLoading} onClick={() => { setPage(value => value - 1); setPatientEncounterId(''); }}>Previous</button><span>Page {page + 1}</span><button type="button" className="btn btn-secondary" disabled={patients.isLoading || (patients.data?.length ?? 0) < 50} onClick={() => { setPage(value => value + 1); setPatientEncounterId(''); }}>Next</button></div>
      {patientEncounterId && <div className="tat-drilldown-level"><div className="tat-section-heading"><h3>{selectedPatient?.patientName ?? 'Patient'} · {label(workflowType)} TAT</h3></div>
        {journey.isLoading ? <p className="tat-empty" role="status">Loading patient TAT…</p> : journey.isError ? <p className="tat-empty" role="alert">Patient TAT could not be loaded. <button className="link-button" onClick={() => journey.refetch()}>Retry</button></p> : <><p>Visit duration: <strong>{formatTatDuration(journey.data?.visit?.visitSeconds)}</strong> · Recorded visit waiting: <strong>{formatTatDuration(journey.data?.totalRecordedWaitingSeconds)}</strong></p>{selectedStages.map(stage => <div className="tat-service-summary" key={stage.referenceId}><strong>{label(stage.status)}</strong><dl><div><dt>Queue ready</dt><dd>{formatTatTimestamp(stage.readyAt, timezone)}</dd></div><div><dt>Started</dt><dd>{formatTatTimestamp(stage.startedAt, timezone)}</dd></div><div><dt>Completed</dt><dd>{formatTatTimestamp(stage.completedAt, timezone)}</dd></div><div><dt>Waiting</dt><dd>{formatTatDuration(stage.waitingSeconds)}</dd></div><div><dt>Service</dt><dd>{formatTatDuration(stage.serviceSeconds)}</dd></div><div><dt>Total TAT</dt><dd>{formatTatDuration(stage.totalTatSeconds)}</dd></div></dl>{stage.timestampAnomaly && <p role="alert">Timestamps are out of order; affected durations are omitted.</p>}</div>)}</>}
      </div>}
    </div>}
  </div>;
}
