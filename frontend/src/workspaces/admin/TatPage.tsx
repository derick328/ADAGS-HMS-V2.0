import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { Activity, Clock3, FlaskConical, Hospital, Hourglass, Pill, RotateCcw, Stethoscope, Users } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { ChartCard } from '../../shared/components/charts/ChartCard';
import { KpiCard } from '../../shared/components/charts/KpiCard';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useAdminFacilityId } from '../../shared/auth/useFacilityScope';
import { usePermission } from '../../shared/auth/usePermission';
import { fetchTatActivity, fetchTatDepartments, fetchTatOverview, fetchTatTimezone, fetchTatTrend, fetchTatWaiting, type TatTrend } from '../../shared/api/tat';
import { formatTatDuration, formatTatTimestamp, tatLocalDate } from '../../shared/format/tat';
import { TatDepartmentDrilldown } from './TatDepartmentDrilldown';
import './TatPage.css';

type Preset = 'TODAY' | 'YESTERDAY' | 'LAST_7' | 'LAST_30' | 'CUSTOM';
const WORKFLOWS = ['TRIAGE', 'DOCTOR', 'LABORATORY', 'PHARMACY'];
const STATUSES = ['WAITING', 'IN_PROGRESS', 'AWAITING_RESULTS', 'AWAITING_PAYMENT', 'AWAITING_AUTHORIZATION', 'COMPLETED', 'CANCELLED', 'INCOMPLETE'];
const label = (value?: string) => value ? value.toLowerCase().replaceAll('_', ' ').replace(/^./, c => c.toUpperCase()) : 'Not recorded';
const shortId = (value?: string) => value ? value.slice(0, 8).toUpperCase() : '—';
const statusTone = (value?: string) => value === 'COMPLETED' ? 'badge-success' : value === 'WAITING' ? 'badge-info' : value?.startsWith('AWAITING_') ? 'badge-warning' : value === 'CANCELLED' ? 'badge-neutral' : 'badge-info';
function offsetDay(date: string, delta: number): string { const value = new Date(`${date}T12:00:00Z`); value.setUTCDate(value.getUTCDate() + delta); return value.toISOString().slice(0, 10); }

function WaitingTrend({ rows }: { rows: TatTrend[] }) {
  const points = useMemo(() => {
    const days = new Map<string, { total: number; count: number }>();
    rows.forEach(row => { if (!row.day || row.averageWaitingSeconds == null || !row.sampleCount) return; const current = days.get(row.day) ?? { total: 0, count: 0 }; current.total += row.averageWaitingSeconds * row.sampleCount; current.count += row.sampleCount; days.set(row.day, current); });
    return [...days].sort(([a], [b]) => a.localeCompare(b)).map(([day, value]) => ({ day, seconds: value.total / value.count, count: value.count }));
  }, [rows]);
  if (!points.length) return <p className="tat-empty">No measured waits in this period.</p>;
  const width = 620, height = 230, left = 48, right = 18, top = 20, bottom = 42;
  const max = Math.max(60, ...points.map(p => p.seconds));
  const x = (index: number) => left + index * (width - left - right) / Math.max(1, points.length - 1);
  const y = (seconds: number) => height - bottom - seconds / max * (height - top - bottom);
  const path = points.map((p, i) => `${i ? 'L' : 'M'} ${x(i)} ${y(p.seconds)}`).join(' ');
  return <figure className="tat-trend">
    <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Average waiting time by day or week, in minutes">
      {[0, .5, 1].map((fraction) => <g key={fraction}><line x1={left} x2={width - right} y1={y(max * fraction)} y2={y(max * fraction)} className="chart-gridline" /><text x={left - 8} y={y(max * fraction) + 4} textAnchor="end" className="chart-axis-text">{Math.round(max * fraction / 60)}m</text></g>)}
      <path d={path} fill="none" stroke="var(--green-600)" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
      {points.map((point, index) => <g key={point.day}><circle cx={x(index)} cy={y(point.seconds)} r="5" fill="var(--green-600)" tabIndex={0} aria-label={`${point.day}: ${formatTatDuration(point.seconds)}, ${point.count} services`}><title>{point.day} · {formatTatDuration(point.seconds)} · {point.count} services</title></circle>{(points.length <= 8 || index % Math.ceil(points.length / 8) === 0 || index === points.length - 1) && <text x={x(index)} y={height - 13} textAnchor="middle" className="chart-axis-text">{point.day.slice(5)}</text>}</g>)}
    </svg>
    <figcaption className="sr-only">{points.map(point => `${point.day}: ${formatTatDuration(point.seconds)} from ${point.count} services`).join('; ')}</figcaption>
  </figure>;
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
  const [selectedDepartment, setSelectedDepartment] = useState('');
  const [, setTick] = useState(0);
  const timezoneQuery = useQuery({ queryKey: ['tat', 'timezone', facilityId], queryFn: () => fetchTatTimezone(facilityId as string, accessToken as string), enabled: Boolean(accessToken && facilityId && canRead) });
  const timezone = timezoneQuery.data?.timezone || 'Africa/Dar_es_Salaam';
  const today = tatLocalDate(new Date(), timezone);
  const range = preset === 'CUSTOM' ? { from: customFrom, to: customTo } : preset === 'YESTERDAY' ? { from: offsetDay(today, -1), to: offsetDay(today, -1) } : { from: offsetDay(today, preset === 'LAST_7' ? -6 : preset === 'LAST_30' ? -29 : 0), to: today };
  const ready = Boolean(accessToken && facilityId && canRead && range.from && range.to && range.from <= range.to);
  const params = { facilityId: facilityId as string, from: range.from, to: range.to, workflowType: workflowType || undefined };
  const periodDays = ready ? Math.round((Date.parse(`${range.to}T00:00:00Z`) - Date.parse(`${range.from}T00:00:00Z`)) / 86_400_000) + 1 : 0;
  const previousRange = { from: ready ? offsetDay(range.from, -periodDays) : '', to: ready ? offsetDay(range.from, -1) : '' };
  const overview = useQuery({ queryKey: ['tat', 'overview', facilityId, range.from, range.to], queryFn: () => fetchTatOverview(params, accessToken as string), enabled: ready });
  const previousOverview = useQuery({ queryKey: ['tat', 'overview', facilityId, previousRange.from, previousRange.to], queryFn: () => fetchTatOverview({ ...params, ...previousRange }, accessToken as string), enabled: ready });
  const departments = useQuery({ queryKey: ['tat', 'departments', params], queryFn: () => fetchTatDepartments(params, accessToken as string), enabled: ready });
  const trend = useQuery({ queryKey: ['tat', 'trend', params], queryFn: () => fetchTatTrend(params, accessToken as string), enabled: ready });
  const waiting = useQuery({ queryKey: ['tat', 'waiting', facilityId, workflowType], queryFn: () => fetchTatWaiting(facilityId as string, workflowType || undefined, accessToken as string), enabled: Boolean(accessToken && facilityId && canRead), refetchInterval: 60_000 });
  const activity = useQuery({ queryKey: ['tat', 'activity', params, status, page], queryFn: () => fetchTatActivity({ ...params, status: status || undefined, page }, accessToken as string), enabled: ready });
  useEffect(() => { const timer = setInterval(() => setTick(value => value + 1), 30_000); return () => clearInterval(timer); }, []);
  const elapsedSinceFetch = waiting.dataUpdatedAt ? Math.max(0, Math.floor((Date.now() - waiting.dataUpdatedAt) / 1000)) : 0;
  const rows = departments.data ?? [];
  const measuredRows = rows.filter(row => row.averageWaitingSeconds != null);
  const maxWait = Math.max(1, ...measuredRows.map(row => row.averageWaitingSeconds ?? 0));
  const reset = () => { setPreset('TODAY'); setCustomFrom(''); setCustomTo(''); setWorkflowType(''); setStatus(''); setPage(0); setSelectedDepartment(''); };
  if (!canRead) return <p>Turnaround analytics access is required.</p>;
  return <div className="analytics-page tat-page">
    <PageHeader title={<>Turnaround time analytics</>} description={<>Monitor patient waiting times, service efficiency and operational performance. Completed outpatient visit averages exclude inpatient encounters.</>} />
    <div className="tat-context"><Hospital size={16} aria-hidden="true" /> {facilityId ? `Facility scope · ${shortId(facilityId)}` : 'Select a facility scope'} <span>· Times shown in {timezone.replaceAll('_', ' ')}</span></div>
    <div className="clinical-filter-bar tat-filters">
      <label>Period<select className="clinical-control" value={preset} onChange={e => { setPreset(e.target.value as Preset); setPage(0); setSelectedDepartment(''); }}><option value="TODAY">Today</option><option value="YESTERDAY">Yesterday</option><option value="LAST_7">Last 7 days</option><option value="LAST_30">Last 30 days</option><option value="CUSTOM">Custom range</option></select></label>
      {preset === 'CUSTOM' && <><label>From<input type="date" className="clinical-control" value={customFrom} onChange={e => { setCustomFrom(e.target.value); setPage(0); setSelectedDepartment(''); }} /></label><label>To<input type="date" className="clinical-control" value={customTo} onChange={e => { setCustomTo(e.target.value); setPage(0); setSelectedDepartment(''); }} /></label></>}
      <label>Department<select className="clinical-control" value={workflowType} onChange={e => { setWorkflowType(e.target.value); setPage(0); setSelectedDepartment(''); }}><option value="">All departments</option>{WORKFLOWS.map(value => <option key={value} value={value}>{label(value)}</option>)}</select></label>
      <label>Workflow status<select className="clinical-control" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}><option value="">All statuses</option>{STATUSES.map(value => <option key={value} value={value}>{label(value)}</option>)}</select></label>
      <button type="button" className="btn btn-secondary" onClick={reset}><RotateCcw size={16} aria-hidden="true" /> Reset</button>
      <span className="tat-filter-context">{ready ? `${range.from} – ${range.to}` : 'Choose a valid period'}</span>
    </div>
    {!ready && <p className="tat-notice">Select a valid date range and facility to view analytics.</p>}
    {overview.isError && <div className="tat-notice" role="alert">Overview could not be loaded. <button className="link-button" onClick={() => overview.refetch()}>Retry</button></div>}
    <section aria-label="Primary metrics"><div className="tat-section-heading"><h2>Visit overview</h2><span>Selected arrival period</span></div><div className="kpi-row kpi-row--four">
      <KpiCard label="Average visit duration" value={overview.isLoading ? 'Loading…' : formatTatDuration(overview.data?.averageVisitSeconds)} basis="Arrival to confirmed departure for completed outpatient visits" icon={Clock3} />
      <KpiCard label="Average recorded waiting" value={overview.isLoading ? 'Loading…' : formatTatDuration(overview.data?.averageRecordedWaitingSeconds)} basis="Measured wait intervals per outpatient visit; overlaps counted once" icon={Hourglass} />
      <KpiCard label="Confirmed departures" value={overview.isLoading ? 'Loading…' : overview.data?.completedEncounters ?? 'Not recorded'} basis="Physical departures recorded for arrivals in this period" icon={Users} />
      <KpiCard label="Currently waiting" value={overview.isLoading ? 'Loading…' : overview.data?.currentlyWaiting ?? 'Not recorded'} basis="Eligible active encounters; first 50 listed below" icon={Activity} />
    </div></section>
    {previousOverview.data && <p className="analytics-basis tat-comparison">Previous equal length period ({previousRange.from}–{previousRange.to}): visit {formatTatDuration(previousOverview.data.averageVisitSeconds)} · recorded wait {formatTatDuration(previousOverview.data.averageRecordedWaitingSeconds)}.</p>}
    <section aria-label="Department metrics"><div className="tat-section-heading"><h2>Service measures</h2><span>Recorded events only</span></div><div className="kpi-row kpi-row--four">
      <KpiCard label="Triage waiting time" value={overview.isLoading ? 'Loading…' : formatTatDuration(overview.data?.triageWaitingSeconds)} basis="Queue entry to assessment start" icon={Activity} />
      <KpiCard label="Doctor waiting time" value={overview.isLoading ? 'Loading…' : formatTatDuration(overview.data?.doctorWaitingSeconds)} basis="Consultation ready to doctor start" icon={Stethoscope} />
      <KpiCard label="Laboratory turnaround" value={overview.isLoading ? 'Loading…' : formatTatDuration(overview.data?.laboratoryTatSeconds)} basis="Authorized order to result release" icon={FlaskConical} />
      <KpiCard label="Pharmacy turnaround" value={overview.isLoading ? 'Loading…' : formatTatDuration(overview.data?.pharmacyTatSeconds)} basis="Authorized line to completed dispense" icon={Pill} />
    </div></section>
    <div className="analytics-row">
      <ChartCard title="Waiting time by department" basis="Average measured ready-to-start wait in the selected arrival period" isLoading={departments.isLoading} isError={departments.isError} onRetry={() => departments.refetch()} isEmpty={measuredRows.length === 0} emptyMessage="No measured waits in this period."><div className="tat-bars">{measuredRows.map(row => <div key={row.workflowType} className="tat-bar-row"><div><strong>{label(row.workflowType)}</strong><span className="tabular">{formatTatDuration(row.averageWaitingSeconds)}</span></div><div className="tat-bar-track"><div style={{ width: `${Math.max(2, (row.averageWaitingSeconds ?? 0) / maxWait * 100)}%` }} /></div></div>)}</div></ChartCard>
      <ChartCard title="Waiting time trends" basis="Weighted average measured wait by service start date; weekly for periods over 30 days" isLoading={trend.isLoading} isError={trend.isError} onRetry={() => trend.refetch()} isEmpty={(trend.data ?? []).length === 0} emptyMessage="No measured waits in this period."><WaitingTrend rows={trend.data ?? []} /></ChartCard>
    </div>
    <section className="tat-table-panel"><div className="tat-section-heading"><h2>Department analysis</h2><span>Completed and measured service events</span></div>{departments.isLoading ? <p className="tat-empty" role="status">Loading department analysis…</p> : departments.isError ? <p className="tat-empty" role="alert">Department analysis could not be loaded. <button className="link-button" onClick={() => departments.refetch()}>Retry</button></p> : rows.length === 0 ? <p className="tat-empty">No department services in this period.</p> : <TableRegion label="Department turnaround statistics"><table className="table tat-table"><thead><tr><th>Department</th><th>Completed</th><th>Average wait</th><th>Median wait</th><th>P90 wait</th><th>Longest wait</th><th>Average service</th><th>Average total TAT</th></tr></thead><tbody>{rows.map(row => <tr key={row.workflowType}><td><button type="button" className="link-button" aria-expanded={selectedDepartment === row.workflowType} onClick={() => setSelectedDepartment(selectedDepartment === row.workflowType ? '' : row.workflowType ?? '')}>{label(row.workflowType)}</button></td><td>{row.completedServices}</td><td>{formatTatDuration(row.averageWaitingSeconds)}</td><td>{formatTatDuration(row.medianWaitingSeconds)}</td><td>{formatTatDuration(row.p90WaitingSeconds)}</td><td>{formatTatDuration(row.longestWaitingSeconds)}</td><td>{formatTatDuration(row.averageServiceSeconds)}</td><td>{formatTatDuration(row.averageTatSeconds)}</td></tr>)}</tbody></table></TableRegion>}{selectedDepartment && ready && <TatDepartmentDrilldown key={`${facilityId}-${range.from}-${range.to}-${selectedDepartment}`} workflowType={selectedDepartment} period={{ facilityId: facilityId as string, from: range.from, to: range.to }} timezone={timezone} token={accessToken as string} />}</section>
    <section className="tat-table-panel"><div className="tat-section-heading"><div><h2>Currently waiting</h2><p>Wait starts at the recorded queue ready time. Durations update every 30 seconds.</p></div><span>{waiting.data?.length ?? 0} listed</span></div>{waiting.isLoading ? <p className="tat-empty" role="status">Loading current waiting list…</p> : waiting.isError ? <p className="tat-empty" role="alert">Current waiting list could not be loaded. <button className="link-button" onClick={() => waiting.refetch()}>Retry</button></p> : !waiting.data?.length ? <p className="tat-empty">No patients are currently waiting for an eligible service.</p> : <TableRegion label="Patients currently waiting"><table className="table tat-table"><thead><tr><th>Patient</th><th>Encounter</th><th>Department</th><th>Queue entry</th><th>Waiting</th><th>Status</th></tr></thead><tbody>{waiting.data.map(row => <tr key={`${row.workflowType}-${row.referenceId}`}><td><strong>{row.patientName ?? row.hospitalNo ?? 'Unknown patient'}</strong><small>{row.hospitalNo}</small></td><td title={row.encounterId}>#{shortId(row.encounterId)}</td><td>{label(row.workflowType)}</td><td>{formatTatTimestamp(row.readyAt, timezone)}</td><td className="tat-duration">{formatTatDuration(row.currentWaitingSeconds == null ? null : row.currentWaitingSeconds + elapsedSinceFetch)}</td><td><span className={`badge ${statusTone(row.status)}`}>{label(row.status)}</span></td></tr>)}</tbody></table></TableRegion>}</section>
    <section className="tat-table-panel"><div className="tat-section-heading"><div><h2>Workflow activity</h2><p>Source records for arrivals in the selected period. Unmeasured durations remain “Not recorded”.</p></div></div>{activity.isLoading ? <p className="tat-empty" role="status">Loading workflow activity…</p> : activity.isError ? <p className="tat-empty" role="alert">Workflow activity could not be loaded. <button className="link-button" onClick={() => activity.refetch()}>Retry</button></p> : !activity.data?.length ? <p className="tat-empty">No workflow activity matches these filters.</p> : <TableRegion label="Workflow activity"><table className="table tat-table"><thead><tr><th>Patient</th><th>Encounter</th><th>Department</th><th>Status</th><th>Waiting</th><th>Service</th></tr></thead><tbody>{activity.data.map(row => <tr key={`${row.encounterId}-${row.stage?.workflowType}-${row.stage?.referenceId}`}><td><strong>{row.patientName ?? row.hospitalNo ?? 'Unknown patient'}</strong><small>{row.hospitalNo}</small></td><td title={row.encounterId}>#{shortId(row.encounterId)}</td><td>{label(row.stage?.workflowType)}</td><td><span className={`badge ${statusTone(row.stage?.status)}`}>{label(row.stage?.status)}</span></td><td>{formatTatDuration(row.stage?.waitingSeconds)}</td><td>{formatTatDuration(row.stage?.serviceSeconds)}</td></tr>)}</tbody></table></TableRegion>}<div className="tat-pagination"><button type="button" className="btn btn-secondary" disabled={page === 0 || activity.isLoading} onClick={() => setPage(value => value - 1)}>Previous</button><span>Page {page + 1}</span><button type="button" className="btn btn-secondary" disabled={activity.isLoading || (activity.data ?? []).length < 50} onClick={() => setPage(value => value + 1)}>Next</button></div></section>
  </div>;
}
