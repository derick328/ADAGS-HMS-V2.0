import { useQuery } from '@tanstack/react-query';
import { fetchPatientVisits, type PatientVisit, type VisitInvoice } from '../../shared/api/patientVisits';
import { useAdminFacilityId } from '../../shared/auth/useFacilityScope';
import { formatMoney } from '../../shared/format/money';
import './PatientVisitsPanel.css';

const date = (value?: string) => value ? new Intl.DateTimeFormat('en-TZ', { dateStyle: 'medium', timeZone: 'Africa/Dar_es_Salaam' }).format(new Date(value)) : '—';
const sum = (invoices: VisitInvoice[], key: 'grossMinor' | 'patientPaidMinor' | 'patientOutstandingMinor' | 'insuranceOutstandingMinor') =>
  invoices.filter(i => i.state !== 'VOID' && i.state !== 'DRAFT').reduce((total, i) => total + i[key], 0);
function paymentStatus(invoices: VisitInvoice[]) {
  if (!invoices.length || invoices.every(i => i.state === 'DRAFT')) return 'Not yet billed';
  const active = invoices.filter(i => i.state !== 'VOID' && i.state !== 'DRAFT');
  if (!active.length) return 'Voided';
  const due = sum(active, 'patientOutstandingMinor');
  const paid = sum(active, 'patientPaidMinor');
  const insurance = sum(active, 'insuranceOutstandingMinor');
  if (due > 0) return paid > 0 ? 'Partially paid' : 'Unpaid';
  if (insurance > 0) return 'Insurance pending';
  return 'Paid';
}
function badge(status: string) {
  return status === 'Paid' ? 'badge-success' : status === 'Unpaid' || status === 'Partially paid' ? 'badge-warning' : status === 'Insurance pending' ? 'badge-info' : 'badge-neutral';
}
function amount(value: number, currency = 'TZS') { return formatMoney(value, currency); }
function VisitRow({ visit }: { visit: PatientVisit }) {
  const invoices = visit.invoices;
  const currency = invoices[0]?.currencyCode ?? 'TZS';
  const status = paymentStatus(invoices);
  return <details className="admin-visit-row"><summary>
    <span><strong>{date(visit.visitedAt)}</strong><small>{visit.department} · {visit.clinician ?? 'Clinician unassigned'}</small></span>
    <span>{visit.status.replaceAll('_', ' ').toLowerCase()}</span>
    <span>{invoices.map(i => i.number).filter(Boolean).join(', ') || 'No invoice number'}</span>
    <span className="money">{amount(sum(invoices, 'grossMinor'), currency)} billed</span>
    <span className="money">{amount(sum(invoices, 'patientPaidMinor'), currency)} paid</span>
    <span className="money">{amount(sum(invoices, 'patientOutstandingMinor'), currency)} patient due</span>
    <span className={`badge ${badge(status)}`}>{status}</span>
  </summary><div className="admin-visit-detail">
    {!invoices.length ? <p>No invoices recorded for this visit.</p> : invoices.map(invoice => <section key={invoice.id}>
      <h4>{invoice.number ?? 'Draft invoice'} <span className={`badge ${invoice.state === 'VOID' ? 'badge-neutral' : 'badge-info'}`}>{invoice.state.replaceAll('_', ' ').toLowerCase()}</span></h4>
      <p>Gross {amount(invoice.grossMinor, invoice.currencyCode)} · Patient portion {amount(invoice.patientPortionMinor, invoice.currencyCode)} · Insurance portion {amount(invoice.insurancePortionMinor, invoice.currencyCode)}</p>
      <p>Patient paid {amount(invoice.patientPaidMinor, invoice.currencyCode)} · Patient outstanding {amount(invoice.patientOutstandingMinor, invoice.currencyCode)} · Insurance obligation {amount(invoice.insuranceOutstandingMinor, invoice.currencyCode)}</p>
      {invoice.payments.length === 0 ? <p>No payment transactions allocated to this invoice.</p> : <div className="admin-visit-table"><table className="table"><thead><tr><th>Payment date</th><th>Method</th><th>Amount allocated</th><th>Status</th><th>Payment reference</th></tr></thead><tbody>{invoice.payments.map(p => <tr key={p.id}><td>{date(p.receivedAt)}</td><td>{p.method.replaceAll('_', ' ')}</td><td className="money">{amount(p.amountMinor, invoice.currencyCode)}</td><td>{p.status.replaceAll('_', ' ')}</td><td>{p.reference || 'No reference recorded'}</td></tr>)}</tbody></table></div>}
    </section>)}
  </div></details>;
}
export function PatientVisitsPanel({ patientId, accessToken }: { patientId: string; accessToken: string }) {
  const facilityId = useAdminFacilityId();
  const visits = useQuery({ queryKey: ['patient-visits', facilityId, patientId], queryFn: () => fetchPatientVisits(patientId, facilityId!, accessToken), enabled: !!facilityId });
  if (!facilityId) return <p>Choose a facility to view visits.</p>;
  if (visits.isLoading) return <p>Loading visit and payment history…</p>;
  if (visits.isError) return <p role="alert">Visit and payment history could not be loaded.</p>;
  const rows = visits.data ?? [];
  const terminal = new Set(['CLOSED', 'DISCHARGED', 'REFERRED_OUT', 'DECEASED', 'LEFT_WITHOUT_BEING_SEEN', 'ABANDONED_UNPAID']);
  const current = rows.find(v => !terminal.has(v.status));
  const previous = rows.find(v => v.encounterId !== current?.encounterId);
  const historical = rows.filter(v => v.encounterId !== current?.encounterId)
    .reduce((total, v) => total + sum(v.invoices, 'patientOutstandingMinor'), 0);
  const currentInvoices = current?.invoices ?? [];
  const currency = currentInvoices[0]?.currencyCode ?? 'TZS';
  const completed = rows.filter(v => ['CLOSED', 'DISCHARGED'].includes(v.status)).length;
  return <section className="admin-visit-panel">
    <h3>Visit and payment summary</h3>
    <div className="admin-visit-stats">
      <div><span>Total hospital visits</span><strong>{rows.length}</strong></div>
      <div><span>Total billed across visits</span><strong className="money">{amount(rows.reduce((total, v) => total + sum(v.invoices, 'grossMinor'), 0), currency)}</strong></div>
      <div><span>Total paid across visits</span><strong className="money">{amount(rows.reduce((total, v) => total + sum(v.invoices, 'patientPaidMinor'), 0), currency)}</strong></div>
      <div><span>Completed visits</span><strong>{completed}</strong></div>
      <div><span>Previous visit</span><strong>{date(previous?.visitedAt)}</strong></div>
      <div><span>Current encounter</span><strong>{current?.status.replaceAll('_', ' ') ?? 'None'}</strong></div>
      <div><span>Current payment status</span><strong className={`badge ${badge(paymentStatus(currentInvoices))}`}>{current ? paymentStatus(currentInvoices) : 'No active visit'}</strong></div>
      <div><span>Current visit billed</span><strong className="money">{amount(sum(currentInvoices, 'grossMinor'), currency)}</strong></div>
      <div><span>Current visit paid</span><strong className="money">{amount(sum(currentInvoices, 'patientPaidMinor'), currency)}</strong></div>
      <div><span>Current patient balance</span><strong className="money">{amount(sum(currentInvoices, 'patientOutstandingMinor'), currency)}</strong></div>
      <div><span>Current insurance obligation</span><strong className="money">{amount(sum(currentInvoices, 'insuranceOutstandingMinor'), currency)}</strong></div>
      <div><span>Previous visit patient balance</span><strong className="money">{amount(historical, currency)}</strong></div>
    </div>
    <details className="admin-visits"><summary>Previous Visits · {rows.length}</summary>
      {rows.length === 0 ? <p>No valid visits recorded.</p> : rows.map(v => <VisitRow key={v.encounterId} visit={v} />)}
    </details>
  </section>;
}
