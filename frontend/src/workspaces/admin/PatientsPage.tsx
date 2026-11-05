import { Checkbox } from '../../shared/components/Checkbox';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState, type CSSProperties, type ReactNode } from 'react';
import { searchInvoices, type InvoiceResponse } from '../../shared/api/invoices';
import {
  fetchPatientById,
  fetchRegisteredPatientCount,
  listRecentPatients,
  searchPatients,
  type PatientListResult,
  type PatientResponse,
  type PatientSearchResult,
} from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatMoney } from '../../shared/format/money';

// 18-admin.md §3.1: identity + hospital number + invoice history, no
// clinical (R2). Landing view puts three registration counts (today /
// month / year) across the top, then the always-visible filter row
// (search + payer + date range + outstanding-only), then the results
// table.
//
// Opens on the recent-registrations list (newest first) — a product
// deviation from 05-receptionist.md §5.1's "no list-all" line, restored
// by request so admin can see who receptionists registered without
// having to type a name first. The same date/payer filters drive both
// the list and, once a patient is picked, their invoice history — one
// filter panel, two stages, no reapply. Search still overrides the
// list when submitted.
export function PatientsPage() {
  const { accessToken } = useAuth();
  const canReadInvoices = usePermission('INVOICE_READ');

  const [query, setQuery] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [payerFilter, setPayerFilter] = useState<PayerFilter>('ALL');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [outstandingOnly, setOutstandingOnly] = useState(false);

  const filtersActive = payerFilter !== 'ALL' || from !== '' || to !== '' || outstandingOnly;
  const searchActive = submittedQuery.trim() !== '';

  const { data: searchResults, isLoading: searchLoading, isError: searchError } = useQuery({
    queryKey: ['patients', 'search', submittedQuery],
    queryFn: () => searchPatients(submittedQuery, accessToken as string),
    enabled: accessToken != null && searchActive,
  });

  const { data: recentResults, isLoading: recentLoading, isError: recentError } = useQuery({
    queryKey: ['patients', 'recent', from, to, payerFilter],
    queryFn: () =>
      listRecentPatients(
        {
          from: from ? `${from}T00:00:00Z` : undefined,
          to: to ? `${to}T23:59:59Z` : undefined,
          payer: payerFilter === 'ALL' ? undefined : payerFilter,
          pageSize: 50,
        },
        accessToken as string,
      ),
    enabled: accessToken != null && !searchActive,
  });

  const listRows: PatientListRow[] = searchActive
    ? (searchResults ?? []).map(fromSearchResult)
    : (recentResults?.items ?? []).map(fromListResult);
  const listLoading = searchActive ? searchLoading : recentLoading;
  const listError = searchActive ? searchError : recentError;

  const { data: selected } = useQuery({
    queryKey: ['patients', 'detail', selectedId],
    queryFn: () => fetchPatientById(selectedId as string, accessToken as string),
    enabled: accessToken != null && selectedId != null,
  });

  return (
    <div>
      <PageHeader title={<>Patients</>} description={<>Identity, hospital number, and invoice history. Clinical detail is restricted to clinical workspaces.</>} />

      <PatientSummary accessToken={accessToken} />

      <section
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-lg)',
          padding: 'var(--space-4)',
          marginBottom: 'var(--space-4)',
        }}
      >
        <h2 style={{ margin: '0 0 var(--space-3)', fontSize: 'var(--text-base)', fontWeight: 'var(--weight-medium)' }}>
          Search & filters
        </h2>

        <form className="clinical-filter-bar"
          onSubmit={(e) => {
            e.preventDefault();
            setSelectedId(null);
            setSubmittedQuery(query.trim());
          }}
          style={{ display: 'flex', gap: 'var(--space-2)', marginBottom: 'var(--space-3)', flexWrap: 'wrap' }}
        >
          <input className="clinical-control"
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by name, hospital number, or phone"
            aria-label="Search patients"
            style={{
              flex: 1,
              minWidth: 240,
              maxWidth: 420,
              padding: 'var(--space-2) var(--space-3)',
              border: '1px solid var(--border-strong)',
              borderRadius: 'var(--radius)',
              font: 'inherit',
            }}
          />
          <button type="submit" className="btn btn-primary" disabled={query.trim() === ''}>
            Search
          </button>
          {searchActive && (
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                setQuery('');
                setSubmittedQuery('');
                setSelectedId(null);
              }}
            >
              Clear search
            </button>
          )}
        </form>

        <div
          style={{
            display: 'flex',
            gap: 'var(--space-3)',
            flexWrap: 'wrap',
            alignItems: 'flex-end',
          }}
        >
          <div>
            <label htmlFor="payer-filter" style={{ display: 'block', fontSize: 'var(--text-xs)' }}>
              Payment method
            </label>
            <select className="clinical-control"
              id="payer-filter"
              value={payerFilter}
              onChange={(e) => setPayerFilter(e.target.value as PayerFilter)}
              style={filterFieldStyle}
            >
              {PAYER_OPTIONS.map((o) => (
                <option key={o.key} value={o.key}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="invoices-from" style={{ display: 'block', fontSize: 'var(--text-xs)' }}>
              From
            </label>
            <input className="clinical-control"
              id="invoices-from"
              type="date"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              style={filterFieldStyle}
            />
          </div>
          <div>
            <label htmlFor="invoices-to" style={{ display: 'block', fontSize: 'var(--text-xs)' }}>
              To
            </label>
            <input className="clinical-control"
              id="invoices-to"
              type="date"
              value={to}
              onChange={(e) => setTo(e.target.value)}
              style={filterFieldStyle}
            />
          </div>
          <Checkbox  className="clinical-choice" checked={outstandingOnly} onChange={(e) => setOutstandingOnly(e.target.checked)} label={<>Outstanding only</>} />
          {filtersActive && (
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                setPayerFilter('ALL');
                setFrom('');
                setTo('');
                setOutstandingOnly(false);
              }}
              style={{ fontSize: 'var(--text-sm)' }}
            >
              Clear filters
            </button>
          )}
        </div>

        <p style={{ margin: 'var(--space-2) 0 0', fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>
          Payment method and date filters apply to the recent-registrations list below, and to a
          selected patient's invoice history. Outstanding-only applies to invoice history only.
        </p>
      </section>

      <div
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-lg)',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'baseline',
            padding: 'var(--space-3) var(--space-4)',
            borderBottom: '1px solid var(--border)',
            gap: 'var(--space-2)',
            flexWrap: 'wrap',
          }}
        >
          <h2 style={{ margin: 0, fontSize: 'var(--text-base)', fontWeight: 'var(--weight-medium)' }}>
            {searchActive ? `Search results for "${submittedQuery}"` : 'Recent registrations'}
          </h2>
          {!listLoading && !listError && (
            <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
              {listRows.length} {listRows.length === 1 ? 'patient' : 'patients'}
              {!searchActive && listRows.length >= 50 && ' (showing 50 most recent)'}
            </span>
          )}
        </div>
        {listLoading ? (
          <EmptyState message={searchActive ? 'Searching…' : 'Loading…'} />
        ) : listError ? (
          <EmptyState message="Couldn't reach the patient index. Try again." />
        ) : listRows.length === 0 ? (
          <EmptyState
            message={
              searchActive
                ? `No patients match "${submittedQuery}"`
                : filtersActive
                  ? 'No registrations match the current filters.'
                  : 'No patients registered yet.'
            }
          />
        ) : (
          <TableRegion><table className="table">
            <caption className="sr-only">
              {searchActive ? 'Patient search results' : 'Recently registered patients'}
            </caption>
            <thead>
              <tr>
                <th>Hospital no.</th>
                <th>Name</th>
                <th>Date of birth</th>
                <th>Phone</th>
                {!searchActive && <th>Payment method</th>}
                {!searchActive && <th>Registered</th>}
              </tr>
            </thead>
            <tbody>
              {listRows.map((patient) => (
                <tr
                  key={patient.id}
                  onClick={() => setSelectedId(patient.id ?? null)}
                  style={{ cursor: 'pointer' }}
                  className={patient.id === selectedId ? 'row-selected' : ''}
                >
                  <td>{patient.hospitalNo ?? '—'}</td>
                  <td style={{ fontWeight: 'var(--weight-medium)' }}>{patient.fullName ?? '—'}</td>
                  <td>
                    {patient.dateOfBirth ?? '—'}
                    {patient.dobIsEstimated && (
                      <span className="badge badge-neutral" style={{ marginLeft: 'var(--space-2)' }}>
                        estimated
                      </span>
                    )}
                  </td>
                  <td>{patient.phone ?? '—'}</td>
                  {!searchActive && (
                    <td>
                      {patient.insuranceSchemeName ?? patient.insuranceSchemeCode ?? (
                        <span style={{ color: 'var(--text-muted)' }}>Cash</span>
                      )}
                    </td>
                  )}
                  {!searchActive && <td>{formatRegisteredAt(patient.createdAt)}</td>}
                </tr>
              ))}
            </tbody>
          </table></TableRegion>
        )}
      </div>

      {selected && (
        <PatientDetailCard
          patient={selected}
          showInvoices={canReadInvoices}
          accessToken={accessToken}
          payerFilter={payerFilter}
          from={from}
          to={to}
          outstandingOnly={outstandingOnly}
        />
      )}
    </div>
  );
}

function PatientSummary({ accessToken }: { accessToken: string | null }) {
  const { todayRange, monthRange, yearRange } = useMemo(makeRanges, []);

  const today = useQuery({
    queryKey: ['patients', 'count', todayRange.from, todayRange.to],
    queryFn: () => fetchRegisteredPatientCount(todayRange.from, todayRange.to, accessToken as string),
    enabled: accessToken != null,
  });
  const month = useQuery({
    queryKey: ['patients', 'count', monthRange.from, monthRange.to],
    queryFn: () => fetchRegisteredPatientCount(monthRange.from, monthRange.to, accessToken as string),
    enabled: accessToken != null,
  });
  const year = useQuery({
    queryKey: ['patients', 'count', yearRange.from, yearRange.to],
    queryFn: () => fetchRegisteredPatientCount(yearRange.from, yearRange.to, accessToken as string),
    enabled: accessToken != null,
  });

  return (
    <section className="clinical-grid"
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
        gap: 'var(--space-3)',
        marginBottom: 'var(--space-4)',
      }}
    >
      <SummaryTile label="Registered today" value={formatCount(today.data)} loading={today.isLoading} />
      <SummaryTile label="Registered this month" value={formatCount(month.data)} loading={month.isLoading} />
      <SummaryTile label="Registered this year" value={formatCount(year.data)} loading={year.isLoading} />
    </section>
  );
}

function SummaryTile({ label, value, loading }: { label: string; value: string; loading: boolean }) {
  return (
    <div
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-3) var(--space-4)',
      }}
    >
      <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>{label}</div>
      <div style={{ marginTop: 'var(--space-1)', fontSize: 'var(--text-2xl)', fontWeight: 'var(--weight-medium)' }}>
        {loading ? '…' : value}
      </div>
    </div>
  );
}

function PatientDetailCard({
  patient,
  showInvoices,
  accessToken,
  payerFilter,
  from,
  to,
  outstandingOnly,
}: {
  patient: PatientResponse;
  showInvoices: boolean;
  accessToken: string | null;
  payerFilter: PayerFilter;
  from: string;
  to: string;
  outstandingOnly: boolean;
}) {
  return (
    <div style={{ display: 'grid', gap: 'var(--space-4)', marginTop: 'var(--space-4)' }}>
      <div
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-lg)',
          padding: 'var(--space-4)',
          maxWidth: 480,
        }}
      >
        <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>
          {patient.fullName}
          {!patient.active && (
            <span className="badge badge-neutral" style={{ marginLeft: 'var(--space-2)' }}>
              inactive
            </span>
          )}
        </h2>
        <dl style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', margin: 0 }}>
          <DetailRow label="Hospital no." value={patient.hospitalNo} />
          <DetailRow
            label="Date of birth"
            value={
              patient.dateOfBirth
                ? `${patient.dateOfBirth}${patient.dobIsEstimated ? ' (estimated)' : ''}`
                : undefined
            }
          />
          <DetailRow label="Gender" value={patient.gender} />
          <DetailRow label="Marital status" value={patient.maritalStatus} />
          <DetailRow label="National ID" value={patient.nationalId} />
          <DetailRow label="Phone" value={patient.phone} />
          <DetailRow label="Email" value={patient.email} />
        </dl>
      </div>

      {showInvoices && patient.id && accessToken && (
        <PatientInvoiceHistory
          patientId={patient.id}
          accessToken={accessToken}
          payerFilter={payerFilter}
          from={from}
          to={to}
          outstandingOnly={outstandingOnly}
        />
      )}
    </div>
  );
}

function PatientInvoiceHistory({
  patientId,
  accessToken,
  payerFilter,
  from,
  to,
  outstandingOnly,
}: {
  patientId: string;
  accessToken: string;
  payerFilter: PayerFilter;
  from: string;
  to: string;
  outstandingOnly: boolean;
}) {
  const { data: invoices, isLoading, isError } = useQuery({
    queryKey: ['patients', 'invoices', patientId, from, to, outstandingOnly],
    queryFn: () =>
      searchInvoices(
        {
          patientId,
          createdFrom: from ? `${from}T00:00:00Z` : undefined,
          createdTo: to ? `${to}T23:59:59Z` : undefined,
          outstandingOnly: outstandingOnly || undefined,
        },
        accessToken,
      ),
    enabled: accessToken != null,
  });

  const rows = (invoices ?? []).filter((r) =>
    payerFilter === 'ALL' ? true : r.payerCategory === payerFilter,
  );
  const totalOutstanding = rows.reduce((sum, r) => sum + (r.outstandingTotalMinor ?? 0), 0);
  const totalBilled = rows.reduce((sum, r) => sum + (r.grossTotalMinor ?? 0), 0);
  const currency = rows.find((r) => r.currencyCode)?.currencyCode ?? 'TZS';
  const hasAnyFilter = payerFilter !== 'ALL' || from !== '' || to !== '' || outstandingOnly;

  return (
    <div
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-4)',
      }}
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'baseline',
          marginBottom: 'var(--space-3)',
          gap: 'var(--space-2)',
          flexWrap: 'wrap',
        }}
      >
        <h3 style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--weight-medium)', margin: 0 }}>
          Invoice history
          {hasAnyFilter && (
            <span
              className="badge badge-neutral"
              style={{ marginLeft: 'var(--space-2)', fontSize: 'var(--text-xs)' }}
            >
              filtered
            </span>
          )}
        </h3>
        {!isLoading && rows.length > 0 && (
          <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
            {rows.length} invoice{rows.length === 1 ? '' : 's'} · billed{' '}
            <span className="money">{formatMoney(totalBilled, currency)}</span> ·{' '}
            <span
              className="money"
              style={{ color: totalOutstanding > 0 ? 'var(--warning)' : undefined }}
            >
              {formatMoney(totalOutstanding, currency)}
            </span>{' '}
            outstanding
          </div>
        )}
      </div>

      {isLoading ? (
        <FeedbackState loading>Loading…</FeedbackState>
      ) : isError ? (
        <p style={{ color: 'var(--text-muted)', margin: 0 }}>Couldn't load invoices for this patient.</p>
      ) : rows.length === 0 ? (
        <p style={{ color: 'var(--text-muted)', margin: 0 }}>
          {(invoices?.length ?? 0) > 0
            ? 'No invoices match the current filters.'
            : 'No invoices on file.'}
        </p>
      ) : (
        <TableRegion><table className="table">
          <thead>
            <tr>
              <th>Invoice</th>
              <th>Payer</th>
              <th>State</th>
              <th>Created</th>
              <th style={{ textAlign: 'right' }}>Gross</th>
              <th style={{ textAlign: 'right' }}>Outstanding</th>
            </tr>
          </thead>
          <tbody>
            {rows
              .slice()
              .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
              .map((invoice) => (
                <InvoiceRow key={invoice.id} invoice={invoice} />
              ))}
          </tbody>
        </table></TableRegion>
      )}
    </div>
  );
}

function InvoiceRow({ invoice }: { invoice: InvoiceResponse }) {
  const isOutstanding = (invoice.outstandingTotalMinor ?? 0) > 0;
  return (
    <tr>
      <td title={invoice.id}>{invoice.invoiceNumber ?? (invoice.id ? invoice.id.slice(0, 8) : '—')}</td>
      <td>{invoice.payerCategory ?? '—'}</td>
      <td>
        <span className={`badge ${invoiceStateClass(invoice.state)}`}>{formatState(invoice.state)}</span>
      </td>
      <td>{formatCreatedAt(invoice.createdAt)}</td>
      <td className="money" style={{ textAlign: 'right' }}>
        {formatMoney(invoice.grossTotalMinor, invoice.currencyCode)}
      </td>
      <td
        className="money"
        style={{ textAlign: 'right', color: isOutstanding ? 'var(--warning)' : undefined }}
      >
        {formatMoney(invoice.outstandingTotalMinor, invoice.currencyCode)}
      </td>
    </tr>
  );
}

function invoiceStateClass(state: InvoiceResponse['state']): string {
  switch (state) {
    case 'CLOSED':
    case 'PATIENT_PORTION_SETTLED':
      return 'badge-success';
    case 'VOID':
      return 'badge-neutral';
    case 'CANCELLATION_PENDING':
      return 'badge-warning';
    case 'RECEIVABLE_POST_DISCHARGE':
      return 'badge-warning';
    default:
      return 'badge-neutral';
  }
}

function formatState(state: InvoiceResponse['state']): string {
  if (!state) return '—';
  return state.replaceAll('_', ' ').toLowerCase();
}

function formatCreatedAt(value: string | undefined): string {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en-TZ', {
    dateStyle: 'medium',
    timeZone: 'Africa/Dar_es_Salaam',
  }).format(new Date(value));
}

// Shared shape between search results (§5.1 columns) and the recent list
// (adds createdAt + payment method). Optional fields on either side stay
// optional here so a search row without a createdAt still renders.
type PatientListRow = {
  id: string | undefined;
  hospitalNo: string | null | undefined;
  fullName: string | null | undefined;
  dateOfBirth: string | null | undefined;
  dobIsEstimated: boolean | undefined;
  phone: string | null | undefined;
  createdAt: string | null | undefined;
  insuranceSchemeCode: string | null | undefined;
  insuranceSchemeName: string | null | undefined;
};

function fromSearchResult(r: PatientSearchResult): PatientListRow {
  return {
    id: r.id,
    hospitalNo: r.hospitalNo,
    fullName: r.fullName,
    dateOfBirth: r.dateOfBirth,
    dobIsEstimated: r.dobIsEstimated,
    phone: r.phone,
    createdAt: undefined,
    insuranceSchemeCode: undefined,
    insuranceSchemeName: undefined,
  };
}

function fromListResult(r: PatientListResult): PatientListRow {
  return {
    id: r.id,
    hospitalNo: r.hospitalNo,
    fullName: r.fullName,
    dateOfBirth: r.dateOfBirth,
    dobIsEstimated: r.dobIsEstimated,
    phone: r.phone,
    createdAt: r.createdAt,
    insuranceSchemeCode: r.insuranceSchemeCode,
    insuranceSchemeName: r.insuranceSchemeName,
  };
}

function formatRegisteredAt(value: string | null | undefined): string {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en-TZ', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Africa/Dar_es_Salaam',
  }).format(new Date(value));
}

function formatCount(value: number | undefined): string {
  if (value == null) return '—';
  return new Intl.NumberFormat('en-TZ').format(value);
}

function makeRanges(): {
  todayRange: { from: string; to: string };
  monthRange: { from: string; to: string };
  yearRange: { from: string; to: string };
} {
  const now = new Date();
  const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
  const startOfYear = new Date(now.getFullYear(), 0, 1);
  const toIso = (d: Date) => d.toISOString();
  return {
    todayRange: { from: toIso(startOfDay), to: toIso(now) },
    monthRange: { from: toIso(startOfMonth), to: toIso(now) },
    yearRange: { from: toIso(startOfYear), to: toIso(now) },
  };
}

type PayerFilter = 'ALL' | NonNullable<InvoiceResponse['payerCategory']>;

const PAYER_OPTIONS: { key: PayerFilter; label: string }[] = [
  { key: 'ALL', label: 'All payers' },
  { key: 'CASH', label: 'Cash' },
  { key: 'NHIF', label: 'NHIF' },
  { key: 'PRIVATE_SCHEME', label: 'Private insurer' },
  { key: 'CORPORATE', label: 'Corporate' },
  { key: 'STAFF', label: 'Staff' },
];

const filterFieldStyle: CSSProperties = {
  padding: 'var(--space-1) var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
  fontSize: 'var(--text-sm)',
};

function DetailRow({ label, value }: { label: string; value: string | undefined | null }) {
  return (
    <>
      <dt style={{ fontWeight: 'var(--weight-medium)' }}>{label}</dt>
      <dd style={{ margin: '0 0 var(--space-2)' }}>{value ?? '—'}</dd>
    </>
  );
}

function EmptyState({ message }: { message: ReactNode }) {
  return (
    <div style={{ padding: 'var(--space-10) var(--space-4)', textAlign: 'center', color: 'var(--text-muted)' }}>
      {message}
    </div>
  );
}
