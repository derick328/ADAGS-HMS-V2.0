import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
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
  const [selectedPatientId, setSelectedPatientId] = useState<string | null>(null);
  const { accessToken } = useAuth();

  const { data: results, isLoading } = useQuery({
    queryKey: ['patients', 'search', submittedQuery],
    queryFn: () => searchPatients(submittedQuery, accessToken as string),
    enabled: accessToken != null && submittedQuery.trim() !== '',
  });

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>Patient Records</h1>
        <p style={{ margin: 'var(--space-1) 0 0', color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
          Financial and visit history only — no clinical content is shown to this role.
        </p>
      </header>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          setSubmittedQuery(query.trim());
          setSelectedPatientId(null);
        }}
        style={{ display: 'flex', gap: 'var(--space-2)', marginBottom: 'var(--space-4)' }}
      >
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by name, hospital number, phone, or national ID"
          style={{
            flex: 1,
            maxWidth: 420,
            padding: 'var(--space-2) var(--space-3)',
            border: '1px solid var(--border-strong)',
            borderRadius: 'var(--radius)',
            font: 'inherit',
          }}
        />
        <button type="submit" className="btn btn-primary" disabled={!canSearch || query.trim() === ''}>
          Search
        </button>
      </form>

      {!canSearch ? (
        <p style={{ color: 'var(--text-muted)' }}>You don't hold PATIENT_SEARCH.</p>
      ) : submittedQuery === '' ? (
        <EmptyState message="Search for a patient to see their record." />
      ) : isLoading ? (
        <EmptyState message="Searching…" />
      ) : !results || results.length === 0 ? (
        <EmptyState message={`No patients match "${submittedQuery}"`} />
      ) : (
        <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden', marginBottom: 'var(--space-4)' }}>
          <table className="table">
            <caption className="sr-only">Patient search results</caption>
            <thead>
              <tr><th>Hospital no.</th><th>Name</th><th>Phone</th></tr>
            </thead>
            <tbody>
              {results.map((p) => (
                <tr key={p.id} onClick={() => setSelectedPatientId(p.id ?? null)} style={{ cursor: 'pointer' }} className={p.id === selectedPatientId ? 'row-selected' : ''}>
                  <td>{p.hospitalNo}</td>
                  <td style={{ fontWeight: 'var(--weight-medium)' }}>{p.fullName}</td>
                  <td>{formatPhone(p.phone)}</td>
                </tr>
              ))}
            </tbody>
          </table>
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
        <section style={sectionStyle}>
          <h2 style={sectionTitleStyle}>Financial summary</h2>
          <div style={{ display: 'flex', gap: 'var(--space-6)', padding: 'var(--space-4)' }}>
            <SummaryStat label="Total billed" value={formatMoney(totalBilledMinor, invoices?.[0]?.currencyCode)} />
            <SummaryStat label="Total paid" value={formatMoney(totalPaidMinor, invoices?.[0]?.currencyCode)} />
            <SummaryStat label="Outstanding" value={formatMoney(totalOutstandingMinor, invoices?.[0]?.currencyCode)} warn={totalOutstandingMinor > 0} />
          </div>
        </section>
      )}

      {canReadVisitHistory && (
        <section style={sectionStyle}>
          <h2 style={sectionTitleStyle}>Visit history</h2>
          {!history || history.length === 0 ? (
            <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>No visits recorded.</p>
          ) : (
            <table className="table">
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
            </table>
          )}
        </section>
      )}

      {canReadFinancial && (
        <section style={sectionStyle}>
          <h2 style={sectionTitleStyle}>Invoices</h2>
          {!invoices || invoices.length === 0 ? (
            <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>No invoices.</p>
          ) : (
            <table className="table">
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
            </table>
          )}
        </section>
      )}
    </div>
  );
}

function PatientHeaderCard({ patient }: { patient: PatientResponse | undefined }) {
  if (!patient) return null;
  return (
    <div style={{ ...sectionStyle, padding: 'var(--space-4)' }}>
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
  return (
    <div style={{ padding: 'var(--space-10) var(--space-4)', textAlign: 'center', color: 'var(--text-muted)', background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)' }}>
      {message}
    </div>
  );
}

const sectionStyle = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
  borderRadius: 'var(--radius-lg)',
  overflow: 'hidden',
} as const;

const sectionTitleStyle = {
  fontSize: 'var(--text-lg)',
  fontWeight: 'var(--weight-medium)',
  margin: 0,
  padding: 'var(--space-4) var(--space-4) 0',
} as const;
