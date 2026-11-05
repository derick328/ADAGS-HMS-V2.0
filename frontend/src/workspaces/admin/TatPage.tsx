import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { ChartCard } from '../../shared/components/charts/ChartCard';
import { KpiCard } from '../../shared/components/charts/KpiCard';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useAdminFacilityId } from '../../shared/auth/useFacilityScope';
import { usePermission } from '../../shared/auth/usePermission';
import { fetchTatActivity, fetchTatDepartments, fetchTatOverview, fetchTatTimezone, fetchTatTrend, fetchTatWaiting } from '../../shared/api/tat';
import { formatTatDuration, tatLocalDate } from '../../shared/format/tat';

type Preset = 'TODAY' | 'YESTERDAY' | 'LAST_7' | 'LAST_30' | 'CUSTOM';
const WORKFLOWS = ['TRIAGE', 'DOCTOR', 'LABORATORY', 'PHARMACY'];

function offsetDay(date: string, delta: number): string {
  const value = new Date(`${date}T12:00:00Z`);
  value.setUTCDate(value.getUTCDate() + delta);
  return value.toISOString().slice(0, 10);
}

export function TatPage() {
  const { accessToken } = useAuth();
  const facilityId = useAdminFacilityId();
  const canRead = usePermission('TAT_ANALYTICS_READ');
  const [preset, setPreset] = useState<Preset>('TODAY');
  const [customFrom, setCustomFrom] = useState('');
  const [customTo, setCustomTo] = useState('');
  const [workflowType, setWorkflowType] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [tick, setTick] = useState(0);

  const { data: timezone } = useQuery({
    queryKey: ['tat', 'timezone', facilityId],
    queryFn: () => fetchTatTimezone(facilityId as string, accessToken as string),
    enabled: Boolean(accessToken && facilityId && canRead),
  });
  const today = tatLocalDate(new Date(), timezone?.timezone || 'Africa/Dar_es_Salaam');
  const range = preset === 'CUSTOM'
    ? { from: customFrom, to: customTo }
    : preset === 'YESTERDAY' ? { from: offsetDay(today, -1), to: offsetDay(today, -1) }
      : { from: offsetDay(today, preset === 'LAST_7' ? -6 : preset === 'LAST_30' ? -29 : 0), to: today };
  const ready = Boolean(accessToken && facilityId && canRead && range.from && range.to && range.from <= range.to);
  const params = { facilityId: facilityId as string, from: range.from, to: range.to, workflowType: workflowType || undefined };
  const periodDays = ready ? Math.round((Date.parse(`${range.to}T00:00:00Z`) - Date.parse(`${range.from}T00:00:00Z`)) / 86_400_000) + 1 : 0;
  const previousRange = { from: ready ? offsetDay(range.from, -periodDays) : '', to: ready ? offsetDay(range.from, -1) : '' };

  const overview = useQuery({ queryKey: ['tat', 'overview', facilityId, range.from, range.to],
    queryFn: () => fetchTatOverview(params, accessToken as string), enabled: ready });
  const previousOverview = useQuery({ queryKey: ['tat', 'overview', facilityId, previousRange.from, previousRange.to],
    queryFn: () => fetchTatOverview({ ...params, ...previousRange }, accessToken as string), enabled: ready });
  const departments = useQuery({ queryKey: ['tat', 'departments', params],
    queryFn: () => fetchTatDepartments(params, accessToken as string), enabled: ready });
  const trend = useQuery({ queryKey: ['tat', 'trend', params],
    queryFn: () => fetchTatTrend(params, accessToken as string), enabled: ready });
  const waiting = useQuery({ queryKey: ['tat', 'waiting', facilityId, workflowType],
    queryFn: () => fetchTatWaiting(facilityId as string, workflowType || undefined, accessToken as string),
    enabled: Boolean(accessToken && facilityId && canRead), refetchInterval: 60_000 });
  const activity = useQuery({ queryKey: ['tat', 'activity', params, status, page],
    queryFn: () => fetchTatActivity({ ...params, status: status || undefined, page }, accessToken as string), enabled: ready });

  useEffect(() => { const timer = setInterval(() => setTick((value) => value + 1), 30_000); return () => clearInterval(timer); }, []);
  const elapsedSinceFetch = Math.max(0, Math.floor((Date.now() - waiting.dataUpdatedAt) / 1000));
  const rows = departments.data ?? [];
  const maxWait = Math.max(1, ...rows.map((row) => row.averageWaitingSeconds ?? 0));
  const trendRows = trend.data ?? [];
  const maxTrend = Math.max(1, ...trendRows.map((row) => row.averageWaitingSeconds ?? 0));
  void tick;

  if (!canRead) return <p>Turnaround analytics access is required.</p>;
  return (
    <div className="analytics-page">
      <PageHeader title={<>Turnaround Time</>} description={<>Operational waiting and service times from recorded workflow events. Inpatient visits are excluded from the outpatient visit average.</>} />
      <div className="clinical-filter-bar" style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap', alignItems: 'end' }}>
        <label>Period <select className="clinical-control" value={preset} onChange={(e) => setPreset(e.target.value as Preset)}>
          <option value="TODAY">Today</option><option value="YESTERDAY">Yesterday</option>
          <option value="LAST_7">Last 7 days</option><option value="LAST_30">Last 30 days</option>
          <option value="CUSTOM">Custom</option>
        </select></label>
        {preset === 'CUSTOM' && <><label>From <input type="date" className="clinical-control" value={customFrom} onChange={(e) => setCustomFrom(e.target.value)} /></label>
          <label>To <input type="date" className="clinical-control" value={customTo} onChange={(e) => setCustomTo(e.target.value)} /></label></>}
        <label>Department <select className="clinical-control" value={workflowType} onChange={(e) => setWorkflowType(e.target.value)}>
          <option value="">All</option>{WORKFLOWS.map((value) => <option key={value} value={value}>{value}</option>)}
        </select></label>
        <label>Workflow status <select className="clinical-control" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
          <option value="">All</option>{['WAITING', 'IN_PROGRESS', 'AWAITING_RESULTS', 'AWAITING_PAYMENT', 'AWAITING_AUTHORIZATION', 'COMPLETED', 'CANCELLED', 'INCOMPLETE'].map((value) => <option key={value} value={value}>{value.replaceAll('_', ' ')}</option>)}
        </select></label>
      </div>
      {!ready && <p>Select a valid period and facility to view analytics.</p>}
      {overview.isError && <p role="alert">Turnaround overview could not be loaded.</p>}
      <div className="kpi-row kpi-row--four">
        <KpiCard label="Average visit" value={formatTatDuration(overview.data?.averageVisitSeconds)} basis="Completed outpatient encounters; arrival to documented exit" />
        <KpiCard label="Average recorded wait" value={formatTatDuration(overview.data?.averageRecordedWaitingSeconds)} basis="Union of measured waiting intervals per outpatient encounter; overlaps counted once" />
        <KpiCard label="Completed encounters" value={overview.data?.completedEncounters ?? '—'} basis="Documented terminal encounter transitions in selected arrival period" />
        <KpiCard label="Currently waiting" value={overview.data?.currentlyWaiting ?? '—'} basis="Unique active encounters eligible for a service now; first 50 listed below" />
      </div>
      <p className="analytics-basis">Previous equal-length period ({previousRange.from}–{previousRange.to}): average visit {formatTatDuration(previousOverview.data?.averageVisitSeconds)}; average recorded wait {formatTatDuration(previousOverview.data?.averageRecordedWaitingSeconds)}. The same measurement basis applies to both periods.</p>
      <div className="kpi-row kpi-row--four">
        <KpiCard label="Triage wait" value={formatTatDuration(overview.data?.triageWaitingSeconds)} basis="Queue entry to explicit assessment start" />
        <KpiCard label="Doctor wait" value={formatTatDuration(overview.data?.doctorWaitingSeconds)} basis="Consultation ready to doctor start" />
        <KpiCard label="Laboratory TAT" value={formatTatDuration(overview.data?.laboratoryTatSeconds)} basis="Authorized order to result release" />
        <KpiCard label="Pharmacy TAT" value={formatTatDuration(overview.data?.pharmacyTatSeconds)} basis="Authorized line to complete dispense" />
      </div>
      <div className="analytics-row">
        <ChartCard title="Waiting time by department" basis="Average completed ready-to-start waits, selected arrival period" isLoading={departments.isLoading} isError={departments.isError} isEmpty={rows.length === 0} emptyMessage="No measured waits in this period.">
          <div>{rows.map((row) => <div key={row.workflowType} style={{ marginBottom: 'var(--space-3)' }}>
            <strong>{row.workflowType}</strong> · {formatTatDuration(row.averageWaitingSeconds)}
            <div style={{ height: 8, background: 'var(--green-100)', borderRadius: 'var(--radius)' }}>
              <div style={{ width: `${Math.max(0, (row.averageWaitingSeconds ?? 0) / maxWait * 100)}%`, height: '100%', background: 'var(--green-600)', borderRadius: 'var(--radius)' }} />
            </div>
          </div>)}</div>
        </ChartCard>
        <ChartCard title="Daily waiting trend" basis="Average completed wait by service start date; exact values below" isLoading={trend.isLoading} isError={trend.isError} isEmpty={trendRows.length === 0} emptyMessage="No measured waits in this period.">
          <div style={{ maxHeight: 300, overflowY: 'auto' }}>{trendRows.map((row) => <div key={`${row.day}-${row.workflowType}`} style={{ marginBottom: 'var(--space-2)' }}>
            <span>{row.day} · {row.workflowType}: {formatTatDuration(row.averageWaitingSeconds)} ({row.sampleCount} services)</span>
            <div style={{ height: 6, background: 'var(--green-100)' }}><div style={{ width: `${(row.averageWaitingSeconds ?? 0) / maxTrend * 100}%`, height: '100%', background: 'var(--green-600)' }} /></div>
          </div>)}</div>
        </ChartCard>
      </div>
      <section><h2>Department analysis</h2><TableRegion label="Department turnaround statistics"><table className="table"><thead><tr>
        <th>Department</th><th>Completed</th><th>Average wait</th><th>Median wait</th><th>P90 wait</th><th>Longest wait</th><th>Average service</th><th>Average total TAT</th>
      </tr></thead><tbody>{rows.map((row) => <tr key={row.workflowType}><td>{row.workflowType}</td><td>{row.completedServices}</td>
        <td>{formatTatDuration(row.averageWaitingSeconds)}</td><td>{formatTatDuration(row.medianWaitingSeconds)}</td>
        <td>{formatTatDuration(row.p90WaitingSeconds)}</td><td>{formatTatDuration(row.longestWaitingSeconds)}</td>
        <td>{formatTatDuration(row.averageServiceSeconds)}</td><td>{formatTatDuration(row.averageTatSeconds)}</td></tr>)}</tbody></table></TableRegion>
        {rows.length === 0 && <p>No completed department services in this period.</p>}
      </section>
      <section><h2>Currently Waiting</h2><p>Duration begins at the recorded queue-ready time. This list refreshes every minute.</p>
        {waiting.isError && <p role="alert">Current waiting list could not be loaded.</p>}
        <TableRegion label="Patients currently waiting"><table className="table"><thead><tr><th>Patient</th><th>Encounter</th><th>Department</th><th>Ready at</th><th>Waiting</th><th>Status</th></tr></thead>
          <tbody>{(waiting.data ?? []).map((row) => <tr key={`${row.workflowType}-${row.referenceId}`}><td>{row.patientName ?? row.hospitalNo}</td>
            <td>{row.encounterId}</td><td>{row.workflowType}</td><td>{row.readyAt ? new Intl.DateTimeFormat('en-TZ', { timeZone: timezone?.timezone || 'Africa/Dar_es_Salaam', dateStyle: 'short', timeStyle: 'short' }).format(new Date(row.readyAt)) : '—'}</td>
            <td>{formatTatDuration((row.currentWaitingSeconds ?? 0) + elapsedSinceFetch)}</td><td>{row.status}</td></tr>)}</tbody></table></TableRegion>
        {(waiting.data ?? []).length === 0 && <p>No patients are currently waiting for an eligible service.</p>}
      </section>
      <section><h2>Workflow activity</h2><p>Source records in the selected arrival period. Missing start times remain unmeasured.</p>
        {activity.isError && <p role="alert">Workflow activity could not be loaded.</p>}
        <TableRegion label="Workflow activity"><table className="table"><thead><tr><th>Patient</th><th>Encounter</th><th>Department</th><th>Status</th><th>Waiting</th><th>Service</th></tr></thead>
          <tbody>{(activity.data ?? []).map((row) => <tr key={`${row.encounterId}-${row.stage?.workflowType}-${row.stage?.referenceId}`}>
            <td>{row.patientName ?? row.hospitalNo}</td><td>{row.encounterId}</td><td>{row.stage?.workflowType}</td>
            <td>{row.stage?.status}</td><td>{formatTatDuration(row.stage?.waitingSeconds)}</td><td>{formatTatDuration(row.stage?.serviceSeconds)}</td>
          </tr>)}</tbody></table></TableRegion>
        {(activity.data ?? []).length === 0 && !activity.isLoading && <p>No workflow activity matches these filters.</p>}
        <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
          <button type="button" className="btn btn-secondary" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button>
          <span>Page {page + 1}</span>
          <button type="button" className="btn btn-secondary" disabled={(activity.data ?? []).length < 50} onClick={() => setPage((value) => value + 1)}>Next</button>
        </div>
      </section>
    </div>
  );
}
