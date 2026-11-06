import { Checkbox } from '../../shared/components/Checkbox';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { QueryState } from '../../shared/components/WorkspacePage';
import { TableRegion } from '../../shared/components/TableRegion';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type CSSProperties } from 'react';
import { ApiError } from '../../shared/api/client';
import { requestInvoiceCancellation } from '../../shared/api/billing';
import { fetchOpenEncounterForPatient } from '../../shared/api/encounters';
import {
  createInvoice,
  fetchInvoiceLines,
  issueInvoice,
  searchInvoices,
  type InvoiceResponse,
} from '../../shared/api/invoices';
import { fetchActivePaymentBypasses } from '../../shared/api/paymentBypasses';
import { recordPayment, type PaymentMethod } from '../../shared/api/payments';
import { searchPriceCatalog, type PriceCatalogItemResponse } from '../../shared/api/priceCatalog';
import { fetchPatientById, searchPatients, type PatientSearchResult } from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDateTime } from '../../shared/format/date';
import { formatMoney } from '../../shared/format/money';
import { BOOTSTRAP_FACILITY_ID } from '../../shared/facility';

const PAYMENT_METHODS: PaymentMethod[] = ['CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'MOBILE_MONEY', 'BANK_TRANSFER', 'CHEQUE'];

export function BillingPage() {
  const { accessToken } = useAuth();
  const canRead = usePermission('INVOICE_READ');
  const [tab, setTab] = useState<'invoices' | 'create' | 'payment'>('invoices');
  const [patientQuery, setPatientQuery] = useState('');
  const [submittedPatientQuery, setSubmittedPatientQuery] = useState('');
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<string | null>(null);
  // Pending bills first: the list shows outstanding invoices with no search
  // needed; untick to see settled/void too.
  const [outstandingOnly, setOutstandingOnly] = useState(true);
  // Set when "Record payment" is pressed on an invoice — hands the patient
  // straight to the payment panel so the cashier doesn't search again.
  const [paymentPatientId, setPaymentPatientId] = useState<string | null>(null);

  const { data: patientMatches, isLoading: searchingPatients, isError: patientSearchError, refetch: retryPatientSearch } = useQuery({
    queryKey: ['patients', 'search', submittedPatientQuery],
    queryFn: () => searchPatients(submittedPatientQuery, accessToken as string),
    enabled: accessToken != null && submittedPatientQuery.trim() !== '',
  });
  const filterPatientId = patientMatches?.[0]?.id;

  const { data: invoices, isLoading, isError: invoicesError, refetch: retryInvoices } = useQuery({
    queryKey: ['invoices', filterPatientId, outstandingOnly],
    queryFn: () => searchInvoices({ patientId: filterPatientId, outstandingOnly }, accessToken as string),
    enabled: accessToken != null && canRead && (!submittedPatientQuery || !!filterPatientId),
    refetchInterval: 15_000,
  });

  return (
    <div className="staff-page-stack">
      <PageHeader title={<>Billing</>} description={<>Find invoices, review balances, and record patient payments.</>}><div className="staff-page-actions" role="group" aria-label="Billing views">
          <button type="button" aria-pressed={tab === 'invoices'} className={tab === 'invoices' ? 'btn btn-primary' : 'btn btn-secondary'} onClick={() => setTab('invoices')}>
            {outstandingOnly ? 'Pending bills' : 'Invoices'}
          </button>
          <button type="button" aria-pressed={tab === 'create'} className={tab === 'create' ? 'btn btn-primary' : 'btn btn-secondary'} onClick={() => setTab('create')}>
            Create invoice
          </button>
          <button type="button" aria-pressed={tab === 'payment'} className={tab === 'payment' ? 'btn btn-primary' : 'btn btn-secondary'} onClick={() => setTab('payment')}>
            Record payment
          </button>
        </div></PageHeader>

      {tab === 'invoices' && (
        <>
          <form className="clinical-filter-bar staff-search-bar"
            onSubmit={(e) => {
              e.preventDefault();
              setSubmittedPatientQuery(patientQuery.trim());
            }}
          >
            <input aria-label="Filter by patient name, hospital number, or phone" className="clinical-control"
              value={patientQuery}
              onChange={(e) => setPatientQuery(e.target.value)}
              placeholder="Filter by patient name, hospital number, or phone"
            />
            <button type="submit" className="btn btn-secondary">
              Filter
            </button>
            {submittedPatientQuery && (
              <button type="button" className="btn btn-ghost" onClick={() => { setPatientQuery(''); setSubmittedPatientQuery(''); }}>
                Clear
              </button>
            )}
            <Checkbox  className="clinical-choice" checked={outstandingOnly} onChange={(e) => setOutstandingOnly(e.target.checked)} label={<>Outstanding only</>} />
          </form>

          <div className="staff-table-card">
            {searchingPatients ? (
              <EmptyState message="Searching patients…" />
            ) : patientSearchError ? (
              <QueryState title="Unable to search patients" onRetry={() => void retryPatientSearch()} />
            ) : isLoading ? (
              <EmptyState message="Loading…" />
            ) : invoicesError ? (
              <QueryState title="Unable to load invoices" onRetry={() => void retryInvoices()} />
            ) : !invoices || invoices.length === 0 ? (
              <EmptyState
                message={
                  submittedPatientQuery
                    ? 'No matching invoices for this patient.'
                    : outstandingOnly
                      ? 'No pending bills — every invoice is settled.'
                      : 'No invoices yet.'
                }
              />
            ) : (
              <TableRegion label="Invoices"><table className="table">
                <caption className="sr-only">Invoices</caption>
                <thead>
                  <tr>
                    <th>Number</th>
                    <th>Patient</th>
                    <th>State</th>
                    <th>Created</th>
                    <th>Outstanding</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {invoices.map((inv) => (
                    <tr key={inv.id} className={inv.id === selectedInvoiceId ? 'row-selected' : ''}>
                      <td style={{ fontWeight: 'var(--weight-medium)' }}>{inv.invoiceNumber ?? <span className="badge badge-neutral">draft</span>}</td>
                      <td><InvoicePatientName patientId={inv.patientId} /></td>
                      <td><InvoiceStateBadge state={inv.state} /></td>
                      <td style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>{formatDateTime(inv.createdAt)}</td>
                      <td className="money">{formatMoney(inv.outstandingTotalMinor, inv.currencyCode)}</td>
                      <td><button type="button" className="btn btn-ghost" aria-label={`View invoice ${inv.invoiceNumber ?? 'draft'}`} onClick={() => setSelectedInvoiceId(inv.id ?? null)}>View</button></td>
                    </tr>
                  ))}
                </tbody>
              </table></TableRegion>
            )}
          </div>

          {selectedInvoiceId && (
            <InvoiceDetailPanel
              invoiceId={selectedInvoiceId}
              onRecordPayment={(patientId) => {
                setPaymentPatientId(patientId);
                setTab('payment');
              }}
            />
          )}
        </>
      )}

      {tab === 'create' && <CreateInvoiceForm onCreated={() => setTab('invoices')} />}
      {tab === 'payment' && <RecordPaymentPanel initialPatientId={paymentPatientId} />}
    </div>
  );
}

function InvoicePatientName({ patientId }: { patientId: string | undefined }) {
  const { accessToken } = useAuth();
  const { data: patient, isError } = useQuery({
    queryKey: ['patient', patientId],
    queryFn: () => fetchPatientById(patientId as string, accessToken as string),
    enabled: accessToken != null && patientId != null,
    staleTime: 60_000,
  });

  return <span>{patient?.fullName ?? (isError ? 'Name unavailable' : '…')}</span>;
}

function InvoiceStateBadge({ state }: { state: InvoiceResponse['state'] }) {
  const map: Record<string, string> = {
    DRAFT: 'badge-neutral',
    ISSUED: 'badge-warning',
    PATIENT_PORTION_SETTLED: 'badge-success',
    CLOSED: 'badge-success',
    CANCELLATION_PENDING: 'badge-warning',
    VOID: 'badge-neutral',
    RECEIVABLE_POST_DISCHARGE: 'badge-critical',
  };
  return <span className={`badge ${map[state ?? ''] ?? 'badge-neutral'}`}>{(state ?? '').replaceAll('_', ' ').toLowerCase()}</span>;
}

function InvoiceDetailPanel({
  invoiceId,
  onRecordPayment,
}: {
  invoiceId: string;
  onRecordPayment: (patientId: string) => void;
}) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const canCreate = usePermission('INVOICE_CREATE');
  const canCancel = usePermission('INVOICE_CANCEL_REQUEST');
  const [cancelReason, setCancelReason] = useState('');
  const [showCancelForm, setShowCancelForm] = useState(false);

  const { data: invoice } = useQuery({
    queryKey: ['invoices', invoiceId],
    queryFn: () => searchInvoices({}, accessToken as string).then((all) => all.find((i) => i.id === invoiceId)),
    enabled: accessToken != null,
  });
  const { data: lines } = useQuery({
    queryKey: ['invoices', invoiceId, 'lines'],
    queryFn: () => fetchInvoiceLines(invoiceId, accessToken as string),
    enabled: accessToken != null,
  });

  const issue = useMutation({
    mutationFn: () => issueInvoice(invoiceId, { expectedVersion: invoice?.version as number }, accessToken as string),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['invoices'] }),
  });
  const cancel = useMutation({
    mutationFn: () => requestInvoiceCancellation(invoiceId, { reason: cancelReason }, accessToken as string),
    onSuccess: () => {
      setShowCancelForm(false);
      setCancelReason('');
      queryClient.invalidateQueries({ queryKey: ['invoices'] });
    },
  });

  return (
    <div className="staff-form-card invoice-detail-card" style={{ ...cardStyle, marginTop: 'var(--space-4)' }}>
      <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>
        {invoice?.invoiceNumber ?? 'Draft invoice'}
      </h2>
      <TableRegion label="Invoice lines"><table className="table" style={{ marginBottom: 'var(--space-3)' }}>
        <caption className="sr-only">Invoice lines</caption>
        <thead>
          <tr><th>Item</th><th>Qty</th><th>Gross</th><th>Remaining</th></tr>
        </thead>
        <tbody>
          {(lines ?? []).map((l) => (
            <tr key={l.id}>
              <td>{l.serviceItemName}</td>
              <td>{l.quantity}</td>
              <td className="money">{formatMoney(l.grossAmountMinor, l.currencyCode)}</td>
              <td className="money">{formatMoney(l.remainingMinor, l.currencyCode)}</td>
            </tr>
          ))}
        </tbody>
      </table></TableRegion>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-2)' }}>
        {invoice?.state === 'DRAFT' && canCreate && (
          <button type="button" className="btn btn-primary" disabled={issue.isPending} onClick={() => issue.mutate()}>
            Issue invoice
          </button>
        )}
        {invoice?.state !== 'DRAFT' &&
          invoice?.state !== 'VOID' &&
          (invoice?.outstandingTotalMinor ?? 0) > 0 &&
          invoice?.patientId && (
            <button type="button" className="btn btn-primary" onClick={() => onRecordPayment(invoice.patientId as string)}>
              Record payment
            </button>
          )}
        {invoice?.state !== 'DRAFT' && invoice?.state !== 'VOID' && invoice?.state !== 'CANCELLATION_PENDING' && canCancel && (
          <button type="button" className="btn btn-danger" onClick={() => setShowCancelForm((v) => !v)}>
            {showCancelForm ? 'Cancel' : 'Request cancellation'}
          </button>
        )}
      </div>

      {showCancelForm && (
        <div style={{ marginTop: 'var(--space-3)' }}>
          <label htmlFor="cancel-reason" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>
            Reason (required — routes to admin for approval)
          </label>
          <textarea className="clinical-control" id="cancel-reason" rows={2} value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} style={fieldStyle} />
          <button
            type="button"
            className="btn btn-danger"
            style={{ marginTop: 'var(--space-2)' }}
            disabled={cancelReason.trim() === '' || cancel.isPending}
            onClick={() => cancel.mutate()}
          >
            Submit request
          </button>
        </div>
      )}
    </div>
  );
}

function CreateInvoiceForm({ onCreated }: { onCreated: () => void }) {
  const { accessToken } = useAuth();
  const [patientQuery, setPatientQuery] = useState('');
  const [patientResults, setPatientResults] = useState<PatientSearchResult[]>([]);
  const [selectedPatient, setSelectedPatient] = useState<PatientSearchResult | null>(null);
  const [catalogQuery, setCatalogQuery] = useState('');
  const [catalogResults, setCatalogResults] = useState<PriceCatalogItemResponse[]>([]);
  const [pickedLines, setPickedLines] = useState<{ item: PriceCatalogItemResponse; quantity: number }[]>([]);
  const [dueDate, setDueDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [notes, setNotes] = useState('');
  const [useEncounter, setUseEncounter] = useState(true);

  const { data: openEncounter } = useQuery({
    queryKey: ['open-encounter', selectedPatient?.id],
    queryFn: () => fetchOpenEncounterForPatient(selectedPatient?.id as string, accessToken as string),
    enabled: accessToken != null && selectedPatient != null,
  });

  const create = useMutation({
    mutationFn: () =>
      createInvoice(
        {
          patientId: selectedPatient?.id as string,
          encounterId: useEncounter ? (openEncounter?.id ?? undefined) : undefined,
          facilityId: BOOTSTRAP_FACILITY_ID,
          dueDate,
          notes: notes || undefined,
          lines: pickedLines.map((l) => ({ serviceItemCode: l.item.code as string, quantity: l.quantity })),
        },
        accessToken as string,
      ),
    onSuccess: onCreated,
  });

  async function runPatientSearch() {
    if (!patientQuery.trim()) return;
    setPatientResults(await searchPatients(patientQuery.trim(), accessToken as string));
  }

  async function runCatalogSearch() {
    if (!catalogQuery.trim()) return;
    setCatalogResults(await searchPriceCatalog(catalogQuery.trim(), useEncounter ? openEncounter?.id : undefined, accessToken as string));
  }

  return (
    <div className="staff-form-card invoice-form-card" style={cardStyle}>
      <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>Create invoice</h2>

      {!selectedPatient ? (
        <div>
          <label htmlFor="invoice-patient-search" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>Patient</label>
          <div className="staff-search-row">
            <input id="invoice-patient-search" className="clinical-control" value={patientQuery} onChange={(e) => setPatientQuery(e.target.value)} style={{ ...fieldStyle, marginTop: 0 }} />
            <button type="button" className="btn btn-secondary" onClick={runPatientSearch}>Search</button>
          </div>
          {patientResults.length > 0 && (
            <ul style={{ listStyle: 'none', padding: 0, marginTop: 'var(--space-2)' }}>
              {patientResults.map((p) => (
                <li key={p.id}>
                  <button type="button" className="btn btn-ghost" onClick={() => setSelectedPatient(p)}>
                    {p.fullName} — {p.hospitalNo}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : (
        <>
          <p>
            Patient: <strong>{selectedPatient.fullName}</strong> ({selectedPatient.hospitalNo}){' '}
            <button type="button" className="btn btn-ghost" onClick={() => setSelectedPatient(null)}>change</button>
          </p>

          {openEncounter && (
            <Checkbox  className="clinical-choice" checked={useEncounter} onChange={(e) => setUseEncounter(e.target.checked)} label={<>Attach to open encounter ({openEncounter.state?.toLowerCase()})</>} />
          )}

          <div className="staff-form-grid">
            <div>
              <label htmlFor="invoice-due-date" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>Due date</label>
              <input id="invoice-due-date" className="clinical-control" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} style={fieldStyle} />
            </div>
            <div style={{ flex: 1 }}>
              <label htmlFor="invoice-notes" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>Notes (optional)</label>
              <input id="invoice-notes" className="clinical-control" value={notes} onChange={(e) => setNotes(e.target.value)} style={fieldStyle} />
            </div>
          </div>

          <label htmlFor="invoice-catalog-search" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>Add line item</label>
          <div className="staff-search-row">
            <input id="invoice-catalog-search" className="clinical-control" value={catalogQuery} onChange={(e) => setCatalogQuery(e.target.value)} placeholder="Search the catalogue…" style={{ ...fieldStyle, marginTop: 0 }} />
            <button type="button" className="btn btn-secondary" onClick={runCatalogSearch}>Search</button>
          </div>
          {catalogResults.length > 0 && (
            <ul style={{ listStyle: 'none', padding: 0, marginBottom: 'var(--space-3)' }}>
              {catalogResults.map((item) => (
                <li key={item.serviceItemId} style={{ display: 'flex', justifyContent: 'space-between', padding: 'var(--space-1) 0' }}>
                  <span>{item.name} — {formatMoney(item.priceMinor, item.currencyCode)}</span>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    onClick={() => {
                      setPickedLines((prev) => [...prev, { item, quantity: 1 }]);
                      setCatalogResults([]);
                      setCatalogQuery('');
                    }}
                  >
                    Add
                  </button>
                </li>
              ))}
            </ul>
          )}

          {pickedLines.length > 0 && (
            <TableRegion label="Picked line items"><table className="table" style={{ marginBottom: 'var(--space-3)' }}>
              <caption className="sr-only">Picked line items</caption>
              <thead><tr><th>Item</th><th>Qty</th><th>Unit price</th><th /></tr></thead>
              <tbody>
                {pickedLines.map((l, i) => (
                  <tr key={i}>
                    <td>{l.item.name}</td>
                    <td>
                      <input className="clinical-control"
                        type="number"
                        min={1}
                        value={l.quantity}
                        onChange={(e) =>
                          setPickedLines((prev) => prev.map((p, idx) => (idx === i ? { ...p, quantity: Number(e.target.value) } : p)))
                        }
                        style={{ width: 60, font: 'inherit' }}
                      />
                    </td>
                    <td className="money">{formatMoney(l.item.priceMinor, l.item.currencyCode)}</td>
                    <td>
                      <button type="button" className="btn btn-ghost" onClick={() => setPickedLines((prev) => prev.filter((_, idx) => idx !== i))}>
                        Remove
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table></TableRegion>
          )}

          {create.error && <div className="form-error">Something went wrong creating the invoice.</div>}

          <button type="button" className="btn btn-primary" disabled={pickedLines.length === 0 || create.isPending} onClick={() => create.mutate()}>
            {create.isPending ? 'Saving…' : 'Save as draft'}
          </button>
        </>
      )}
    </div>
  );
}

function RecordPaymentPanel({ initialPatientId }: { initialPatientId?: string | null }) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [patientQuery, setPatientQuery] = useState('');
  const [patientResults, setPatientResults] = useState<PatientSearchResult[]>([]);
  const [selectedPatient, setSelectedPatient] = useState<PatientSearchResult | null>(null);
  const [ticked, setTicked] = useState<Record<string, boolean>>({});
  const [method, setMethod] = useState<PaymentMethod>('CASH');
  const [reference, setReference] = useState('');

  // Seeded when the cashier pressed "Record payment" on an invoice — skips
  // the patient search entirely.
  const { data: seedPatient } = useQuery({
    queryKey: ['patient', 'payment-seed', initialPatientId],
    queryFn: () => fetchPatientById(initialPatientId as string, accessToken as string),
    enabled: accessToken != null && initialPatientId != null,
  });
  useEffect(() => {
    if (seedPatient?.id && seedPatient.id !== selectedPatient?.id) {
      setSelectedPatient(seedPatient);
      setTicked({});
    }
  }, [seedPatient, selectedPatient?.id]);

  const { data: invoices } = useQuery({
    queryKey: ['invoices', 'payable', selectedPatient?.id],
    queryFn: () => searchInvoices({ patientId: selectedPatient?.id, outstandingOnly: true }, accessToken as string),
    enabled: accessToken != null && selectedPatient != null,
  });

  const { data: activeBypasses } = useQuery({
    queryKey: ['payment-bypasses', 'active'],
    queryFn: () => fetchActivePaymentBypasses(accessToken as string),
    enabled: accessToken != null,
  });

  const invoiceLineQueries = useQuery({
    queryKey: ['invoices', 'payable-lines', invoices?.map((i) => i.id)],
    queryFn: async () => {
      const entries = await Promise.all(
        (invoices ?? []).map(async (inv) => ({
          invoice: inv,
          lines: await fetchInvoiceLines(inv.id as string, accessToken as string),
        })),
      );
      return entries;
    },
    enabled: accessToken != null && !!invoices && invoices.length > 0,
  });

  const pickableRows = (invoiceLineQueries.data ?? []).flatMap(({ invoice, lines }) =>
    lines.filter((l) => (l.remainingMinor ?? 0) > 0).map((l) => ({ invoice, line: l })),
  );

  const tickedRows = pickableRows.filter((r) => ticked[r.line.id as string]);
  const totalMinor = tickedRows.reduce((sum, r) => sum + (r.line.remainingMinor ?? 0), 0);

  const submit = useMutation({
    mutationFn: () =>
      recordPayment(
        {
          patientId: selectedPatient?.id as string,
          method,
          reference: method === 'CASH' ? undefined : reference,
          idempotencyKey: crypto.randomUUID(),
          allocations: tickedRows.map((r) => ({ invoiceLineId: r.line.id as string, amountMinor: r.line.remainingMinor as number })),
        },
        accessToken as string,
      ),
    onSuccess: () => {
      setTicked({});
      queryClient.invalidateQueries({ queryKey: ['invoices'] });
      queryClient.invalidateQueries({ queryKey: ['cashier-shift'] });
      // Paying a prescription/lab-order line flips its authorization_id
      // server-side, so the pharmacy and lab queues need to re-render
      // that row into the "ready" bucket immediately. Without this,
      // same-tab flows (staff cross-checking on one machine) wait the
      // full 5s poll for a state change they just performed.
      queryClient.invalidateQueries({ queryKey: ['prescriptions', 'queue'] });
      queryClient.invalidateQueries({ queryKey: ['lab-orders', 'queue'] });
    },
  });

  async function runPatientSearch() {
    if (!patientQuery.trim()) return;
    setPatientResults(await searchPatients(patientQuery.trim(), accessToken as string));
  }

  function hasActiveBypass(encounterId: string | undefined) {
    return !!encounterId && (activeBypasses ?? []).some((b) => b.encounterId === encounterId);
  }

  return (
    <div className="staff-form-card invoice-form-card" style={cardStyle}>
      <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>Record payment</h2>
      <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
        Select the invoice lines this payment covers. The amount updates automatically.
      </p>

      {!selectedPatient ? (
        <div>
          <div className="staff-search-row">
            <input aria-label="Search patients…" className="clinical-control" value={patientQuery} onChange={(e) => setPatientQuery(e.target.value)} placeholder="Search patients…" style={{ ...fieldStyle, marginTop: 0 }} />
            <button type="button" className="btn btn-secondary" onClick={runPatientSearch}>Search</button>
          </div>
          {patientResults.length > 0 && (
            <ul style={{ listStyle: 'none', padding: 0, marginTop: 'var(--space-2)' }}>
              {patientResults.map((p) => (
                <li key={p.id}>
                  <button type="button" className="btn btn-ghost" onClick={() => setSelectedPatient(p)}>
                    {p.fullName} — {p.hospitalNo}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : (
        <>
          <p>
            Patient: <strong>{selectedPatient.fullName}</strong> ({selectedPatient.hospitalNo}){' '}
            <button type="button" className="btn btn-ghost" onClick={() => setSelectedPatient(null)}>change</button>
          </p>

          {pickableRows.length === 0 ? (
            <EmptyState message="No outstanding lines for this patient." />
          ) : (
            <TableRegion label="Payable lines"><table className="table" style={{ marginBottom: 'var(--space-3)' }}>
              <caption className="sr-only">Payable lines</caption>
              <thead><tr><th /><th>Invoice</th><th>Item</th><th>Remaining</th></tr></thead>
              <tbody>
                {pickableRows.map((r) => (
                  <tr key={r.line.id}>
                    <td>
                      <Checkbox
                        aria-label={`Select ${r.line.serviceItemName ?? "invoice line"} for payment`}
                        checked={!!ticked[r.line.id as string]}
                        onChange={(e) => setTicked((prev) => ({ ...prev, [r.line.id as string]: e.target.checked }))}
                      />
                    </td>
                    <td>{r.invoice.invoiceNumber ?? r.invoice.id?.slice(0, 8)}</td>
                    <td>
                      {r.line.serviceItemName}
                      {hasActiveBypass(r.invoice.encounterId) && (
                        <span className="badge badge-warning" style={{ marginLeft: 'var(--space-2)' }}>bypass active</span>
                      )}
                    </td>
                    <td className="money">{formatMoney(r.line.remainingMinor, r.line.currencyCode)}</td>
                  </tr>
                ))}
              </tbody>
            </table></TableRegion>
          )}

          <div className="staff-payment-grid">
            <div>
              <label htmlFor="payment-method" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>Method</label>
              <select id="payment-method" className="clinical-control" value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)} style={fieldStyle}>
                {PAYMENT_METHODS.map((m) => (
                  <option key={m} value={m}>{m.replaceAll('_', ' ').toLowerCase()}</option>
                ))}
              </select>
            </div>
            {method !== 'CASH' && (
              <div>
                <label htmlFor="payment-reference" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>Reference</label>
                <input id="payment-reference" className="clinical-control" value={reference} onChange={(e) => setReference(e.target.value)} style={fieldStyle} />
              </div>
            )}
            <div style={{ marginLeft: 'auto', textAlign: 'right' }}>
              <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>Total</div>
              <div className="tabular" style={{ fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-bold)' }}>
                {formatMoney(totalMinor, pickableRows[0]?.line.currencyCode)}
              </div>
            </div>
          </div>

          {submit.error && <div className="form-error">{paymentErrorMessage(submit.error)}</div>}

          <button
            type="button"
            className="btn btn-primary"
            disabled={tickedRows.length === 0 || (method !== 'CASH' && reference.trim() === '') || submit.isPending}
            onClick={() => submit.mutate()}
          >
            {submit.isPending ? 'Recording…' : `Take payment — ${formatMoney(totalMinor, pickableRows[0]?.line.currencyCode)}`}
          </button>
        </>
      )}
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return <FeedbackState loading={message.startsWith('Loading') || message.startsWith('Searching')}>{message}</FeedbackState>;
}

// Surface the real server reason instead of a catch-all. The most common
// cause is the cashier not having opened their shift (PostPaymentService
// requires an open CashierShift for the acting user).
function paymentErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (/cashier shift/i.test(error.message)) {
      return `${error.message} Open your shift from the "Shift" page, then take the payment again.`;
    }
    if (error.status === 409) {
      return `${error.message} Reload the pending bills and try again — a line may have changed.`;
    }
    return error.message;
  }
  return 'The payment could not be recorded. Reload the pending bills and try again.';
}

const cardStyle: CSSProperties = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
  borderRadius: 'var(--radius-lg)',
  padding: 'var(--space-4)',
};

const fieldStyle: CSSProperties = {
  width: '100%',
  marginTop: 'var(--space-1)',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
