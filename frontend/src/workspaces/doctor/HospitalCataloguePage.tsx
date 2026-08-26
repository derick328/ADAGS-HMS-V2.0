import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { searchPriceCatalog } from '../../shared/api/priceCatalog';
import { fetchCurrentStock } from '../../shared/api/stockReports';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatMoney } from '../../shared/format/money';

// 08-doctor-navigation.md §4. Read-only — PRICE_CATALOG_READ only, no
// pricing action anywhere on this screen (§4.3: "Doctors read; they do not
// price"). Payer defaults CASH per §4.2; no encounter context to default
// to here since the catalogue is reached from the sidebar, not from inside
// a specific consultation — 07-doctor.md's own catalogue-inside-consultation
// case (defaulting to that patient's payer) is the price-catalog search the
// Prescription/Lab tabs already run with `encounterId` set.
//
// temporarily_unavailable + expected-return-date for lab tests has no
// backing column in ServiceItem/PriceCatalogItemResponse yet — omitted
// rather than fabricated; noted as a gap.
export function HospitalCataloguePage() {
  const { accessToken } = useAuth();
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<'ALL' | 'CONSULTATION' | 'LAB' | 'PROCEDURE' | 'PHARMACY' | 'WARD'>('ALL');

  const { data: results, isLoading } = useQuery({
    queryKey: ['price-catalog', 'search', query],
    queryFn: () => searchPriceCatalog(query, undefined, accessToken as string),
    enabled: accessToken != null && query.trim().length >= 2,
  });

  const { data: stock } = useQuery({
    queryKey: ['stock-reports', 'current-stock'],
    queryFn: () => fetchCurrentStock(accessToken as string),
    enabled: accessToken != null,
    staleTime: 30_000,
  });
  const stockByItemCode = useMemo(() => {
    const map: Record<string, number> = {};
    (stock ?? []).forEach((row) => {
      if (!row.itemCode) return;
      map[row.itemCode] = (map[row.itemCode] ?? 0) + (row.quantity ?? 0);
    });
    return map;
  }, [stock]);

  const filtered = (results ?? []).filter((r) => category === 'ALL' || r.category === category);

  return (
    <div>
      <h1 style={{ fontSize: 'var(--text-xl)' }}>Hospital Catalogue</h1>

      <div style={{ display: 'flex', gap: 'var(--space-3)', margin: 'var(--space-3) 0' }}>
        <input
          placeholder="Search procedures, medicines, lab tests…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          style={{ flex: 1, padding: 'var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)' }}
        />
        <select
          value={category}
          onChange={(e) => setCategory(e.target.value as typeof category)}
          style={{ padding: 'var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)' }}
        >
          <option value="ALL">All categories</option>
          <option value="CONSULTATION">Consultation</option>
          <option value="LAB">Lab</option>
          <option value="PROCEDURE">Procedure</option>
          <option value="PHARMACY">Pharmacy</option>
          <option value="WARD">Ward</option>
        </select>
        {/* Payer: cash-only price list exists today (BillingFacadeImpl's NHIF
            fallback resolves the same way, per its own comment) — a live
            payer toggle here would offer choices that all price identically,
            so this stays fixed rather than implying an insurance price list
            that doesn't exist yet. */}
        <span className="badge badge-neutral" style={{ alignSelf: 'center' }}>
          Payer: Cash
        </span>
      </div>

      {query.trim().length < 2 ? (
        <p style={{ color: 'var(--text-muted)' }}>Type at least 2 characters to search.</p>
      ) : isLoading ? (
        <p style={{ color: 'var(--text-muted)' }}>Searching…</p>
      ) : filtered.length === 0 ? (
        <p style={{ color: 'var(--text-muted)' }}>No matches.</p>
      ) : (
        <table className="table">
          <tbody>
            {filtered.map((item) => {
              const qty = item.code ? stockByItemCode[item.code] : undefined;
              return (
                <tr key={item.serviceItemId}>
                  <td>
                    <div style={{ fontWeight: 'var(--weight-medium)' }}>{item.name}</div>
                    <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>{item.category}</div>
                    {item.category === 'PHARMACY' && (
                      <div style={{ marginTop: 4 }}>
                        {qty == null ? (
                          <span className="badge badge-neutral">Stock unknown</span>
                        ) : qty <= 0 ? (
                          <span className="badge badge-critical">Out of stock</span>
                        ) : qty < 10 ? (
                          <span className="badge badge-warning">Low stock — {qty} units</span>
                        ) : (
                          <span className="badge badge-success">In stock — {qty} units</span>
                        )}
                      </div>
                    )}
                  </td>
                  <td className="money">{formatMoney(item.priceMinor, item.currencyCode)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}
