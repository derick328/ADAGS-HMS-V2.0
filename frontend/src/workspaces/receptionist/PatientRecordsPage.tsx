import { FeedbackState } from '../../shared/components/FeedbackState';
import { QueryState } from '../../shared/components/WorkspacePage';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { searchInvoices } from '../../shared/api/invoices';
import { fetchPatientById, fetchVisitHistory, searchPatients, type PatientResponse } from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDate, formatDateTime } from '../../shared/format/date';
import { formatMoney } from '../../shared/format/money';
import { formatPhone } from '../../shared/format/phone';

// 05-receptionist.md §1.1 ⚠1: search-first, no default result set. Per
// patient: VisitHistoryView (encounter date/type/state/department/
// attending provider/disposition and invoice status only — no clinical
// narrative, no diagnoses, no lab results, ever), financial summary, full
// invoice list. usePermission gates are visibility-only, per the frontend
// contract; the server enforces the actual restriction.
export function PatientRecordsPage() {
  const canSearch = usePermission('PATIENT_SEARCH');
  const [query, setQuery] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('');
  // Deep-linked from Patient Intake's "View patient record" — a direct link
  // to one specific record the receptionist just interacted with, not a
  // browse. Still gated behind PATIENT_READ_DEMOGRAPHIC server-side same as
  // any other lookup here.
  const [searchParams] = useSearchParams();
  const [selectedPatientId, setSelectedPatientId] = useState<string | null>(searchParams.get('patientId'));
  const { accessToken } = useAuth();

  const { data: results, isLoading, isError, refetch } = useQuery({
    queryKey: ['patients', 'search', submittedQuery],
    queryFn: () => searchPatients(submittedQuery, accessToken as string),
    enabled: accessToken != null && submittedQuery.trim() !== '',
  });

  return (
    <div className="staff-page-stack">
      <PageHeader title={<>Patient records</>} description={<>Financial and visit history only — no clinical content is shown to this role.</>} />

      <form className="clinical-filter-bar staff-search-bar"
        onSubmit={(e) => {
          e.preventDefault();
          setSubmittedQuery(query.trim());
          setSelectedPatientId(null);
        }}
      >
        <input aria-label="Search by name, hospital number, phone, or national ID" className="clinical-control"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by name, hospital number, phone, or national ID"
        />
        <button type="submit" className="btn btn-primary" disabled={!canSearch || query.trim() === ''}>
          Search
        </button>
      </form>

      {!canSearch ? (
        <p style={{ color: 'var(--text-muted)' }}>You don't hold PATIENT_SEARCH.</p>
      ) : submittedQuery === '' ? (
        selectedPatientId ? null : <EmptyState message="Search for a patient to see their record." />
      ) : isLoading ? (
        <EmptyState message="Searching…" />
      ) : isError ? (
        <QueryState title="Unable to search patient records" onRetry={() => void refetch()} />
      ) : !results || results.length === 0 ? (
        <EmptyState message={`No patients match "${submittedQuery}"`} />
      ) : (
        <div className="staff-table-card">
          <TableRegion label="Patient search results"><table className="table">
            <caption className="sr-only">Patient search results</caption>
            <thead>
              <tr><th>Hospital no.</th><th>Name</th><th>Phone</th></tr>
            </thead>
            <tbody>
              {results.map((p) => (
                <tr key={p.id} className={p.id === selectedPatientId ? 'row-selected' : ''}>
                  <td>{p.hospitalNo}</td>
                  <td><button type="button" className="workspace-name" onClick={() => setSelectedPatientId(p.id ?? null)}>{p.fullName}</button></td>
                  <td>{formatPhone(p.phone)}</td>
                </tr>
              ))}
            </tbody>
          </table></TableRegion>
        </div>
      )}

      {selectedPatientId && <PatientRecordDetail patientId={selectedPatientId} />}
    </div>
  );
}

function PatientRecordDetail({ patientId }: { patientId: string }) {
  const { accessToken } = useAuth();
  const canReadFinancial = usePermission('PATIENT_READ_FINANCIAL');
  const canReadVisitHistory = usePermission('PATIENT_READ_VISIT_HISTORY');

  const { data: patient } = useQuery({
    queryKey: ['patient', patientId],
    queryFn: () => fetchPatientById(patientId, accessToken as string),
    enabled: accessToken != null,
  });

  const { data: history } = useQuery({
    queryKey: ['patient', patientId, 'visit-history'],
    queryFn: () => fetchVisitHistory(patientId, accessToken as string),
    enabled: accessToken != null && canReadVisitHistory,
  });

  const { data: invoices } = useQuery({
    queryKey: ['invoices', patientId],
    queryFn: () => searchInvoices({ patientId }, accessToken as string),
    enabled: accessToken != null && canReadFinancial,
  });

  const totalBilledMinor = (invoices ?? []).reduce((sum, i) => sum + (i.grossTotalMinor ?? 0), 0);
  const totalOutstandingMinor = (invoices ?? []).reduce((sum, i) => sum + (i.outstandingTotalMinor ?? 0), 0);
  const totalPaidMinor = totalBilledMinor - totalOutstandingMinor;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
      <PatientHeaderCard patient={patient} />

      {canReadFinancial && (
        <section className="staff-data-section">
          <h2>Financial summary</h2>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-6)', padding: 'var(--space-4)' }}>
            <SummaryStat label="Total billed" value={formatMoney(totalBilledMinor, invoices?.[0]?.currencyCode)} />
            <SummaryStat label="Total paid" value={formatMoney(totalPaidMinor, invoices?.[0]?.currencyCode)} />
            <SummaryStat label="Outstanding" value={formatMoney(totalOutstandingMinor, invoices?.[0]?.currencyCode)} warn={totalOutstandingMinor > 0} />
          </div>
        </section>
      )}

      {canReadVisitHistory && (
        <section className="staff-data-section">
          <h2>Visit history</h2>
          {!history || history.length === 0 ? (
            <FeedbackState>No visits recorded.</FeedbackState>
          ) : (
            <TableRegion label="Visit history"><table className="table">
              <caption className="sr-only">Visit history</caption>
              <thead>
                <tr><th>Date</th><th>Type</th><th>State</th><th>Department</th><th>Provider</th><th>Invoice</th></tr>
              </thead>
              <tbody>
                {history.map((h) => (
                  <tr key={h.encounterId}>
                    <td style={{ fontSize: 'var(--text-sm)' }}>{formatDateTime(h.encounterDate)}</td>
                    <td>{h.encounterType}</td>
                    <td>
                      <span className="badge badge-info">{(h.encounterState ?? '').replaceAll('_', ' ').toLowerCase()}</span>
                    </td>
                    <td>{h.departmentName}</td>
                    <td>{h.attendingProviderName ?? '—'}</td>
                    <td>
                      {h.invoiceState ? (
                        <>
                          <span className="badge badge-neutral">{h.invoiceState.replaceAll('_', ' ').toLowerCase()}</span>{' '}
                          <span className="money">{formatMoney(h.outstandingTotalMinor, h.currencyCode)}</span>
                        </>
                      ) : (
                        '—'
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table></TableRegion>
          )}
        </section>
      )}

      {canReadFinancial && (
        <section className="staff-data-section">
          <h2>Invoices</h2>
          {!invoices || invoices.length === 0 ? (
            <FeedbackState>No invoices.</FeedbackState>
          ) : (
            <TableRegion label="Invoices"><table className="table">
              <caption className="sr-only">Invoices</caption>
              <thead>
                <tr><th>Number</th><th>State</th><th>Created</th><th>Outstanding</th></tr>
              </thead>
              <tbody>
                {invoices.map((inv) => (
                  <tr key={inv.id}>
                    <td style={{ fontWeight: 'var(--weight-medium)' }}>{inv.invoiceNumber ?? <span className="badge badge-neutral">draft</span>}</td>
                    <td>{(inv.state ?? '').replaceAll('_', ' ').toLowerCase()}</td>
                    <td style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>{formatDate(inv.createdAt)}</td>
                    <td className="money">{formatMoney(inv.outstandingTotalMinor, inv.currencyCode)}</td>
                  </tr>
                ))}
              </tbody>
            </table></TableRegion>
          )}
        </section>
      )}
    </div>
  );
}

function PatientHeaderCard({ patient }: { patient: PatientResponse | undefined }) {
  if (!patient) return null;
  return (
    <div className="staff-record-identity">
      <h2 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)' }}>{patient.fullName}</h2>
      <p style={{ margin: 'var(--space-1) 0 0', color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
        {patient.hospitalNo} · {formatDate(patient.dateOfBirth)}
        {patient.dobIsEstimated ? ' (estimated)' : ''} · {patient.gender} · {formatPhone(patient.phone)}
      </p>
    </div>
  );
}

function SummaryStat({ label, value, warn }: { label: string; value: string; warn?: boolean }) {
  return (
    <div>
      <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>{label}</div>
      <div className="tabular" style={{ fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-bold)', color: warn ? 'var(--warning)' : 'inherit' }}>
        {value}
      </div>
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return <FeedbackState loading={message.startsWith('Loading') || message.startsWith('Searching')}>{message}</FeedbackState>;
}
