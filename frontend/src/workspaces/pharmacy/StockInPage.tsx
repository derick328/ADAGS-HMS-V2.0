import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Combobox } from '../../shared/components/Combobox';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { TableRegion } from '../../shared/components/TableRegion';
import { ApiError } from '../../shared/api/client';
import { searchItems, type ItemSearchResultResponse } from '../../shared/api/items';
import { fetchMedicinePackaging, type MedicinePackaging } from '../../shared/api/medicinePackaging';
import { confirmStockReceipt, type StockReceipt, type StockReceiptLineRequest } from '../../shared/api/stockReceipts';
import { fetchMyPharmacyStore } from '../../shared/api/stores';
import { createSupplier, fetchSuppliers } from '../../shared/api/suppliers';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatMoney } from '../../shared/format/money';
import { calculateDeliveryLine } from './packagingMath';
import './StockInPage.css';

interface DeliveryLine {
  key: string;
  item: ItemSearchResultResponse | null;
  unit: string;
  factor: number;
  ordered: string;
  received: string;
  accepted: string;
  rejected: string;
  batch: string;
  expiry: string;
  unitCost: string;
  discrepancyReason: string;
  notes: string;
}

const freshLine = (): DeliveryLine => ({ key: crypto.randomUUID(), item: null, unit: '', factor: 0,
  ordered: '', received: '', accepted: '', rejected: '', batch: '', expiry: '', unitCost: '',
  discrepancyReason: '', notes: '' });

function facilityDate(offset = 0): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Africa/Dar_es_Salaam', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date(Date.now() + offset * 86_400_000));
  const part = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

function whole(value: string, allowZero = false): number | null {
  if (!/^\d+$/.test(value)) return null;
  const result = Number(value);
  return Number.isSafeInteger(result) && (allowZero ? result >= 0 : result > 0) ? result : null;
}

export function StockInPage() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [supplierChoice, setSupplierChoice] = useState('');
  const [newSupplierName, setNewSupplierName] = useState('');
  const [invoiceNumber, setInvoiceNumber] = useState('');
  const [deliveryNoteNumber, setDeliveryNoteNumber] = useState('');
  const [invoiceDate, setInvoiceDate] = useState(facilityDate());
  const [deliveryDate, setDeliveryDate] = useState(facilityDate());
  const [notes, setNotes] = useState('');
  const [lines, setLines] = useState<DeliveryLine[]>([freshLine()]);
  const [reviewing, setReviewing] = useState(false);
  const [error, setError] = useState('');
  const [key, setKey] = useState(crypto.randomUUID());
  const [confirmed, setConfirmed] = useState<StockReceipt | null>(null);

  const { data: store } = useQuery({ queryKey: ['stores', 'mine-pharmacy'],
    queryFn: () => fetchMyPharmacyStore(accessToken as string), enabled: accessToken != null });
  const { data: suppliers } = useQuery({ queryKey: ['suppliers'],
    queryFn: () => fetchSuppliers(accessToken as string), enabled: accessToken != null });

  const updateLine = (key: string, change: Partial<DeliveryLine>) => {
    setLines((current) => current.map((line) => line.key === key ? { ...line, ...change } : line));
    setReviewing(false);
    setError('');
    setKey(crypto.randomUUID());
  };

  function validate(): StockReceiptLineRequest[] | null {
    if (!store || !supplierChoice || supplierChoice === '__new__' && !newSupplierName.trim() ||
        !invoiceNumber.trim() || !deliveryNoteNumber.trim() || !invoiceDate || !deliveryDate) {
      setError('Complete the supplier, invoice, delivery note, dates, and receiving store.');
      return null;
    }
    const result: StockReceiptLineRequest[] = [];
    for (const [index, line] of lines.entries()) {
      const received = whole(line.received, true);
      const accepted = whole(line.accepted, true);
      const rejected = whole(line.rejected, true);
      const ordered = line.ordered ? whole(line.ordered) : null;
      const cost = whole(line.unitCost, true);
      const missing = ordered == null || received == null ? 0 : Math.max(ordered - received, 0);
      const discrepancy = (rejected ?? 0) > 0 || missing > 0 || ordered != null && received != null && received > ordered;
      let calculationValid = true;
      try {
        calculateDeliveryLine(accepted ?? -1, line.factor, cost ?? -1);
        calculateDeliveryLine(rejected ?? -1, line.factor, cost ?? -1);
      } catch { calculationValid = false; }
      if (!line.item || !line.unit || line.factor < 1 || received == null ||
          received === 0 && (ordered == null || ordered === 0) || accepted == null ||
          rejected == null || accepted + rejected !== received || cost == null ||
          !line.batch.trim() || !line.expiry || line.expiry <= facilityDate() ||
          line.ordered && ordered == null || discrepancy && !line.discrepancyReason.trim() ||
          !calculationValid) {
        setError(`Check medicine line ${index + 1}: unit, quantities, batch, future expiry, cost, or discrepancy reason is invalid.`);
        return null;
      }
      result.push({ itemId: line.item.id as string, purchaseUnit: line.unit,
        orderedQuantity: ordered ?? undefined, receivedQuantity: received,
        acceptedQuantity: accepted, rejectedQuantity: rejected,
        batchNumber: line.batch.trim(), expiryDate: line.expiry,
        unitPurchaseCostMinor: cost, discrepancyReason: line.discrepancyReason.trim() || undefined,
        notes: line.notes.trim() || undefined });
    }
    const total = result.reduce((sum, line) => sum + line.receivedQuantity * line.unitPurchaseCostMinor, 0);
    if (!Number.isSafeInteger(total)) {
      setError('The total delivery value exceeds the supported range.');
      return null;
    }
    setError('');
    return result;
  }

  const confirm = useMutation({
    mutationFn: async () => {
      const valid = validate();
      if (!valid || !store) throw new Error('Please correct the receiving form.');
      const supplierId = supplierChoice === '__new__'
        ? (await createSupplier({ name: newSupplierName.trim() }, accessToken as string)).id as string
        : supplierChoice;
      return confirmStockReceipt({ supplierId, storeId: store.id as string,
        invoiceNumber: invoiceNumber.trim(), deliveryNoteNumber: deliveryNoteNumber.trim(),
        invoiceDate, deliveryDate, notes: notes.trim() || undefined,
        idempotencyKey: key, lines: valid }, accessToken as string);
    },
    onSuccess: (receipt) => {
      setConfirmed(receipt);
      queryClient.invalidateQueries({ queryKey: ['stock-reports'] });
      queryClient.invalidateQueries({ queryKey: ['stock-receipts'] });
      queryClient.invalidateQueries({ queryKey: ['suppliers'] });
    },
  });

  if (confirmed) return <div className="stock-in-card" role="status">
    <h2>Delivery confirmed</h2>
    <p>Receipt <strong>{confirmed.receiptNumber}</strong> contains {confirmed.lines.length} medicine lines.
      {' '}Accepted stock value: {formatMoney(confirmed.totalAcceptedCostMinor, confirmed.currencyCode)}.</p>
    <button type="button" className="btn btn-primary" onClick={() => {
      setConfirmed(null); setLines([freshLine()]); setReviewing(false); setKey(crypto.randomUUID());
      setInvoiceNumber(''); setDeliveryNoteNumber(''); setNotes('');
    }}>New delivery</button>
  </div>;

  return <div className="stock-in-page">
    <div className="stock-in-heading"><h2>New delivery</h2><p>Record what was physically received. Only accepted stock becomes available.</p></div>
    <section className="stock-in-card" aria-label="Supplier information">
      <h3>Supplier and delivery</h3>
      <div className="stock-in-grid">
        <div><Combobox label="Supplier" placeholder="Search supplier name" minChars={1}
          search={async (query) => (suppliers ?? []).filter((supplier) =>
            supplier.name?.toLowerCase().includes(query.toLowerCase()))}
          getKey={(supplier) => supplier.id as string}
          renderOption={(supplier) => supplier.name}
          onSelect={(supplier) => { setSupplierChoice(supplier.id as string); setReviewing(false); setKey(crypto.randomUUID()); }} />
          <p className="stock-in-selected">Selected: {supplierChoice === '__new__' ? 'New supplier' :
            (suppliers ?? []).find((supplier) => supplier.id === supplierChoice)?.name ?? 'None'}</p>
          <button type="button" className="btn btn-ghost" onClick={() => { setSupplierChoice('__new__'); setReviewing(false); setKey(crypto.randomUUID()); }}>
            Add a new supplier</button>
        </div>
        {supplierChoice === '__new__' && <label>New supplier name <input className="clinical-control" value={newSupplierName} onChange={(e) => setNewSupplierName(e.target.value)} /></label>}
        <label>Invoice number <input className="clinical-control" value={invoiceNumber} onChange={(e) => setInvoiceNumber(e.target.value)} /></label>
        <label>Delivery note number <input className="clinical-control" value={deliveryNoteNumber} onChange={(e) => setDeliveryNoteNumber(e.target.value)} /></label>
        <label>Invoice date <input className="clinical-control" type="date" max={facilityDate()} value={invoiceDate} onChange={(e) => setInvoiceDate(e.target.value)} /></label>
        <label>Delivery date <input className="clinical-control" type="date" max={facilityDate()} value={deliveryDate} onChange={(e) => setDeliveryDate(e.target.value)} /></label>
        <label>Receiving store <input className="clinical-control" value={store?.name ?? 'Loading…'} readOnly /></label>
        <label>Notes <input className="clinical-control" value={notes} onChange={(e) => setNotes(e.target.value)} /></label>
      </div>
    </section>

    <section className="stock-in-card" aria-label="Medicines">
      <div className="stock-in-section-heading"><h3>Medicines</h3><span>{lines.length} line{lines.length === 1 ? '' : 's'}</span></div>
      {lines.map((line, index) => <MedicineLine key={line.key} line={line} index={index}
        token={accessToken as string} update={(change) => updateLine(line.key, change)}
        remove={() => { setLines((current) => current.filter((row) => row.key !== line.key)); setReviewing(false); setKey(crypto.randomUUID()); }}
        canRemove={lines.length > 1} />)}
      <button type="button" className="btn btn-secondary" onClick={() => { setLines((current) => [...current, freshLine()]); setReviewing(false); setKey(crypto.randomUUID()); }}>+ Add another medicine</button>
    </section>

    {error && <div className="form-error" role="alert">{error}</div>}
    {confirm.error && <div className="form-error" role="alert">
      {confirm.error instanceof ApiError ? confirm.error.message : confirm.error.message}
    </div>}

    {!reviewing ? <button type="button" className="btn btn-primary" onClick={() => { if (validate()) setReviewing(true); }}>Review delivery</button> :
      <section className="stock-in-card" aria-label="Receiving summary">
        <h3>Review and confirm</h3>
        <p>{suppliers?.find((s) => s.id === supplierChoice)?.name ?? newSupplierName} · Invoice {invoiceNumber} · Delivery note {deliveryNoteNumber}</p>
        <TableRegion label="Receiving summary"><table className="table"><thead><tr><th>Medicine</th><th>Ordered</th><th>Received</th><th>Accepted base units</th><th>Rejected</th><th>Missing</th><th>Discrepancy reason</th><th>Accepted cost</th></tr></thead><tbody>
          {lines.map((line) => <tr key={line.key}><td>{line.item?.name}</td><td>{line.ordered || '—'}</td><td>{line.received} {line.unit}</td>
            <td>{Number(line.accepted) * line.factor} {line.item?.unit}</td><td>{line.rejected} {line.unit}</td>
            <td>{line.ordered ? Math.max(Number(line.ordered) - Number(line.received), 0) : '—'}</td>
            <td>{line.discrepancyReason || '—'}</td>
            <td>{formatMoney(Number(line.accepted) * Number(line.unitCost), 'TZS')}</td></tr>)}
        </tbody></table></TableRegion>
        <p className="stock-in-total">Accepted stock value: {formatMoney(lines.reduce((sum, line) => sum + Number(line.accepted) * Number(line.unitCost), 0), 'TZS')}</p>
        <div className="stock-in-actions"><button type="button" className="btn btn-secondary" onClick={() => setReviewing(false)}>Back to edit</button>
          <button type="button" className="btn btn-primary" onClick={() => confirm.mutate()} disabled={confirm.isPending}>{confirm.isPending ? 'Confirming…' : 'Confirm stock in'}</button></div>
      </section>}
  </div>;
}

function MedicineLine({ line, index, token, update, remove, canRemove }: {
  line: DeliveryLine; index: number; token: string; update: (change: Partial<DeliveryLine>) => void;
  remove: () => void; canRemove: boolean;
}) {
  const { data: packages, isLoading } = useQuery({ queryKey: ['medicine-packaging', line.item?.id],
    queryFn: () => fetchMedicinePackaging(line.item?.id as string, token), enabled: !!line.item?.id && !!token });
  const baseUnit = line.item?.unit ?? 'unit';
  const options: MedicinePackaging[] = line.item ? [{ id: null, itemId: line.item.id as string,
    unit: baseUnit, factor: 1, canSplit: true, version: 1, status: 'APPROVED' }, ...(packages ?? [])] : [];
  return <div className="stock-in-line">
    <div className="stock-in-section-heading"><h4>Medicine {index + 1}</h4>{canRemove && <button type="button" className="btn btn-ghost" onClick={remove}>Remove medicine</button>}</div>
    <Combobox<ItemSearchResultResponse> label="Medicine" placeholder="Search formulary"
      search={(query) => searchItems(query, token)} getKey={(item) => item.id as string}
      renderOption={(item) => <span>{item.name} {item.strength} {item.selectable === false && ` — ${item.unselectableReason}`}</span>}
      isOptionDisabled={(item) => item.selectable === false}
      onSelect={(item) => update({ item, unit: item.unit ?? '', factor: item.unit ? 1 : 0 })} />
    {line.item && <p className="stock-in-selected">Selected: {line.item.name} · {line.item.strength} · base unit {baseUnit}</p>}
    <div className="stock-in-grid">
      <label>Purchase unit <select className="clinical-control" value={line.unit} disabled={!line.item || isLoading}
        onChange={(e) => { const selected = options.find((option) => option.unit === e.target.value);
          update({ unit: e.target.value, factor: selected?.factor ?? 0 }); }}>
        {!line.unit && <option value="">Select unit</option>}
        {options.map((option) => <option key={option.id ?? 'base'} value={option.unit}>{option.unit} · {option.factor} {baseUnit}</option>)}
      </select></label>
      <label>Ordered quantity (optional) <input className="clinical-control" type="number" min="1" value={line.ordered} onChange={(e) => update({ ordered: e.target.value })} /></label>
      <label>Physically received <input className="clinical-control" type="number" min="0" value={line.received} onChange={(e) => update({ received: e.target.value })} /></label>
      <label>Accepted <input className="clinical-control" type="number" min="0" value={line.accepted} onChange={(e) => update({ accepted: e.target.value })} /></label>
      <label>Rejected or damaged <input className="clinical-control" type="number" min="0" value={line.rejected} onChange={(e) => update({ rejected: e.target.value })} /></label>
      <label>Batch number <input className="clinical-control" value={line.batch} onChange={(e) => update({ batch: e.target.value })} /></label>
      <label>Expiry date <input className="clinical-control" type="date" min={facilityDate(1)} value={line.expiry} onChange={(e) => update({ expiry: e.target.value })} /></label>
      <label>Purchase cost per {line.unit || 'unit'} (TZS minor units) <input className="clinical-control" type="number" min="0" value={line.unitCost} onChange={(e) => update({ unitCost: e.target.value })} /></label>
      <label>Discrepancy reason <input className="clinical-control" value={line.discrepancyReason} onChange={(e) => update({ discrepancyReason: e.target.value })} /></label>
      <label>Notes <input className="clinical-control" value={line.notes} onChange={(e) => update({ notes: e.target.value })} /></label>
    </div>
    <p className="stock-in-calculation">Accepted stock: <strong>{(whole(line.accepted, true) ?? 0) * line.factor} {baseUnit}</strong>
      {' '}· Accepted cost: <strong>{formatMoney((whole(line.accepted, true) ?? 0) * (whole(line.unitCost, true) ?? 0), 'TZS')}</strong></p>
    {!line.item && <FeedbackState>Select a catalogue medicine to see its approved purchase units.</FeedbackState>}
  </div>;
}
