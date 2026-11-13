import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { searchItems } from '../../shared/api/items';
import { fetchMedicinePackaging, proposeMedicinePackaging } from '../../shared/api/medicinePackaging';
import { fetchMedicinePurchaseCost, fetchMedicinePurchaseCostHistory, updateMedicinePurchaseCost } from '../../shared/api/medicinePurchaseCost';
import { fetchCatalogPriceHistory, type CatalogItem } from '../../shared/api/catalogImport';
import { formatMoney } from '../../shared/format/money';
import { formatDateTime } from '../../shared/format/date';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { useAuth } from '../../shared/auth/AuthProvider';
import '../pharmacy/StockInPage.css';

export function MedicationSettings({ catalogueItem, onClose }: { catalogueItem: CatalogItem; onClose: () => void }) {
  const { accessToken } = useAuth();
  const token = accessToken as string;
  const cache = useQueryClient();
  const [costUnit, setCostUnit] = useState('');
  const [cost, setCost] = useState('');
  const [packageUnit, setPackageUnit] = useState('');
  const [factor, setFactor] = useState('');
  const [presentation, setPresentation] = useState<'BLISTER_PACK' | 'OTHER'>('BLISTER_PACK');
  const [blisters, setBlisters] = useState('');
  const [perBlister, setPerBlister] = useState('');
  const [canSplit, setCanSplit] = useState(true);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const { data: matches, isLoading, isError } = useQuery({ queryKey: ['admin-medication-match', catalogueItem.code],
    queryFn: () => searchItems(catalogueItem.code, token), enabled: !!token });
  const { data: sellingHistory } = useQuery({ queryKey: ['catalog-price-history', catalogueItem.id],
    queryFn: () => fetchCatalogPriceHistory(catalogueItem.id, token), enabled: !!token });
  const medicine = matches?.find(item => item.code === catalogueItem.code && item.unit);
  const itemId = medicine?.id as string | undefined;
  const { data: packages } = useQuery({ queryKey: ['medicine-packaging', itemId],
    queryFn: () => fetchMedicinePackaging(itemId as string, token), enabled: !!itemId });
  const { data: defaultCost } = useQuery({ queryKey: ['medicine-purchase-cost', itemId],
    queryFn: () => fetchMedicinePurchaseCost(itemId as string, token), enabled: !!itemId });
  const { data: history } = useQuery({ queryKey: ['medicine-purchase-cost-history', itemId],
    queryFn: () => fetchMedicinePurchaseCostHistory(itemId as string, token), enabled: !!itemId });
  const options = [medicine?.unit, ...(packages ?? []).map(p => p.unit)].filter((unit): unit is string => !!unit);
  const blisterTotal = Number(blisters) * Number(perBlister);
  const saveCost = useMutation({ mutationFn: () => updateMedicinePurchaseCost(itemId as string, costUnit, Number(cost), token),
    onSuccess: () => { setCost(''); void cache.invalidateQueries({ queryKey: ['medicine-purchase-cost'] }); },
    onError: () => setError('Could not save the default purchase cost.') });
  const propose = useMutation({ mutationFn: () => proposeMedicinePackaging(itemId as string, {
    unit: presentation === 'BLISTER_PACK' ? `PACK_${blisterTotal}` : packageUnit.trim().toUpperCase(),
    quantityInBaseUnits: presentation === 'BLISTER_PACK' ? blisterTotal : Number(factor), canSplit, reason: reason.trim(),
    blistersPerPack: presentation === 'BLISTER_PACK' ? Number(blisters) : undefined,
    unitsPerBlister: presentation === 'BLISTER_PACK' ? Number(perBlister) : undefined,
  }, token), onSuccess: () => { setPackageUnit(''); setFactor(''); setBlisters(''); setPerBlister(''); setReason(''); setError('Proposal submitted for a second person to approve.');
    void cache.invalidateQueries({ queryKey: ['medicine-packaging'] }); },
    onError: () => setError('Could not submit packaging. Check that the unit is distinct and no proposal is pending.') });
  return <section className="stock-in-card" aria-label="Medication settings" style={{ marginTop: 'var(--space-4)' }}>
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}><h3>{catalogueItem.name} · medication settings</h3>
      <button type="button" className="btn btn-secondary" onClick={onClose}>Close</button></div>
    <p>Catalogue code: {catalogueItem.code} · Current CASH selling price: {catalogueItem.currentCashPriceMinor == null ? 'Unpriced' : formatMoney(catalogueItem.currentCashPriceMinor, 'TZS')}. Use Edit Price in the catalogue row to change what patients are charged.</p>
    {sellingHistory?.[0] && <p>Last selling price update: {formatDateTime(sellingHistory[0].changedAt)} by {sellingHistory[0].changedBy}. Full selling price history is available from the catalogue row.</p>}
    {isLoading && <FeedbackState loading>Loading medicine…</FeedbackState>}
    {isError && <p role="alert" className="form-error">Could not load the medicine catalogue.</p>}
    {!isLoading && !medicine && <p role="status">This catalogue entry has no classified medicine record. Packaging and purchase cost can be set after classification.</p>}
    {medicine && <>
      <p><strong>{medicine.name}</strong> · {medicine.strength || 'Strength unspecified'} · {medicine.form} · Base inventory unit: {medicine.unit}</p>
      <h4>Approved packaging</h4>
      <ul>{options.map(unit => { const p = packages?.find(p => p.unit === unit); return <li key={unit}>{unit} = {p?.factor ?? 1} {medicine.unit}{p?.blistersPerPack && p.unitsPerBlister ? ` (${p.blistersPerPack} blisters × ${p.unitsPerBlister} ${medicine.unit} per blister)` : ''}</li>; })}</ul>
      <form onSubmit={event => { event.preventDefault(); setError('');
        const invalidBlister = presentation === 'BLISTER_PACK' &&
          (!Number.isSafeInteger(Number(blisters)) || Number(blisters) < 1 || !Number.isSafeInteger(Number(perBlister)) || Number(perBlister) < 1 || !Number.isSafeInteger(blisterTotal) || blisterTotal < 2);
        const invalidOther = presentation === 'OTHER' &&
          (!/^[A-Z][A-Z0-9_]*$/.test(packageUnit.trim().toUpperCase()) || !Number.isSafeInteger(Number(factor)) || Number(factor) < 2);
        if (invalidBlister || invalidOther || !reason.trim()) {
          setError('Enter positive whole packaging counts and a reason.'); return;
        } propose.mutate(); }}>
        <h4>Packaging configuration</h4>
        <p>Enter the manufacturer’s counts. Each pack size is kept as its own approved version; the matching blister unit is proposed with a blister pack.</p>
        <div className="stock-in-grid"><label>Package type <select className="clinical-control" value={presentation} onChange={event => setPresentation(event.target.value as 'BLISTER_PACK' | 'OTHER')}>
          <option value="BLISTER_PACK">Pack containing blisters</option><option value="OTHER">Bottle, vial, ampoule, tube, or other</option>
          </select></label>
          {presentation === 'BLISTER_PACK' ? <>
            <label>Blisters per pack <input className="clinical-control" type="number" min="1" step="1" value={blisters} onChange={event => setBlisters(event.target.value)} /></label>
            <label>{medicine.unit} per blister <input className="clinical-control" type="number" min="1" step="1" value={perBlister} onChange={event => setPerBlister(event.target.value)} /></label>
          </> : <>
            <label>Package name <input className="clinical-control" value={packageUnit} onChange={event => setPackageUnit(event.target.value)} placeholder="BOTTLE_100 or VIAL_10" /></label>
            <label>{medicine.unit} per package <input className="clinical-control" type="number" min="2" step="1" value={factor} onChange={event => setFactor(event.target.value)} /></label>
          </>}
          <label>Reason <input className="clinical-control" value={reason} onChange={event => setReason(event.target.value)} /></label></div>
        {presentation === 'BLISTER_PACK' && <p className="stock-in-calculation" role="status">{blisters || '0'} blisters × {perBlister || '0'} {medicine.unit} per blister = <strong>{Number.isSafeInteger(blisterTotal) ? blisterTotal : 'Invalid'} {medicine.unit} per pack</strong></p>}
        <label><input type="checkbox" checked={canSplit} onChange={event => setCanSplit(event.target.checked)} /> Partial package dispensing allowed</label>
        <button className="btn btn-secondary" disabled={propose.isPending}>Submit for approval</button>
      </form>
      <form onSubmit={event => { event.preventDefault(); setError('');
        if (!options.includes(costUnit) || !/^(0|[1-9]\d*)$/.test(cost) || !Number.isSafeInteger(Number(cost))) {
          setError('Select an approved unit and enter a whole TZS amount.'); return;
        } saveCost.mutate(); }}>
        <h4>Default purchase cost</h4>
        <p>Procurement suggestion only. Supplier receipt costs can differ or be left unknown. Selling prices remain separate.</p>
        <p>Current: {defaultCost ? `${formatMoney(defaultCost.amountMinor, 'TZS')} per ${defaultCost.unit} · updated ${formatDateTime(defaultCost.updatedAt)} by ${defaultCost.updatedBy}` : 'Not configured'}</p>
        <div className="stock-in-grid"><label>Purchase unit <select className="clinical-control" value={costUnit} onChange={event => setCostUnit(event.target.value)}><option value="">Select unit</option>{options.map(unit => <option key={unit} value={unit}>{unit}</option>)}</select></label>
          <label>Default cost (TZS) <input className="clinical-control" type="number" min="0" step="1" value={cost} onChange={event => setCost(event.target.value)} /></label></div>
        <button className="btn btn-primary" disabled={saveCost.isPending}>Save default cost</button>
      </form>
      {!!history?.length && <details><summary>Purchase cost history</summary><ul>{history.map((entry, index) => <li key={`${entry.updatedAt}-${index}`}>{formatMoney(entry.amountMinor, 'TZS')} per {entry.unit} · {formatDateTime(entry.updatedAt)} · {entry.updatedBy}</li>)}</ul></details>}
    </>}
    {error && <p role="alert" className="form-error">{error}</p>}
  </section>;
}
