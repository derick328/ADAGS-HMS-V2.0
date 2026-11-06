import { FeedbackState } from '../../shared/components/FeedbackState';
import { QueryState } from '../../shared/components/WorkspacePage';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { searchPriceCatalog } from '../../shared/api/priceCatalog';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatMoney } from '../../shared/format/money';

// Payer-aware lookup for the receptionist: shows the price that will actually
// be charged to a CASH patient. Explicitly not tied to an encounter — this
// screen is for over-the-counter questions ("how much is a chest X-ray?"),
// not for quoting a specific patient whose scheme might resolve differently
// (that's what the Billing screen's charge picker does).
export function PriceLookupPage() {
  const { accessToken } = useAuth();
  const canRead = usePermission('PRICE_CATALOG_READ');
  const [query, setQuery] = useState('');
  const [submitted, setSubmitted] = useState('');

  const { data: results, isLoading, isError, refetch } = useQuery({
    queryKey: ['price-catalog', 'lookup', submitted],
    queryFn: () => searchPriceCatalog(submitted, undefined, accessToken as string),
    enabled: accessToken != null && submitted.trim() !== '',
  });

  if (!canRead) {
    return <p style={{ color: 'var(--text-muted)' }}>You don't hold PRICE_CATALOG_READ.</p>;
  }

  return (
    <div className="staff-page-stack">
      <PageHeader title={<>Price check</>} description={<>Look up the cash price of any catalogue item — consultation, lab, procedure, pharmacy, or ward.</>} />

      <form className="clinical-filter-bar staff-search-bar"
        onSubmit={(e) => {
          e.preventDefault();
          setSubmitted(query.trim());
        }}
      >
        <input className="clinical-control"
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by item name or code"
          aria-label="Search catalogue items"
        />
        <button type="submit" className="btn btn-primary" disabled={query.trim() === ''}>
          Search
        </button>
      </form>

      {submitted === '' ? (
        <EmptyState message="Type an item name or code, then press Search." />
      ) : isLoading ? (
        <EmptyState message="Searching…" />
      ) : isError ? (
        <QueryState title="Unable to load prices" onRetry={() => void refetch()} />
      ) : !results || results.length === 0 ? (
        <EmptyState message={`No priced items match "${submitted}"`} />
      ) : (
        <div className="staff-table-card">
          <TableRegion label="Priced catalogue items"><table className="table">
            <caption className="sr-only">Priced catalogue items</caption>
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>Category</th>
                <th style={{ textAlign: 'right' }}>Cash price</th>
              </tr>
            </thead>
            <tbody>
              {results.map((item) => (
                <tr key={item.serviceItemId ?? item.code}>
                  <td className="tabular">{item.code ?? '—'}</td>
                  <td style={{ fontWeight: 'var(--weight-medium)' }}>{item.name ?? '—'}</td>
                  <td>{item.category ? item.category.toLowerCase() : '—'}</td>
                  <td className="money">{formatMoney(item.priceMinor, item.currencyCode)}</td>
                </tr>
              ))}
            </tbody>
          </table></TableRegion>
        </div>
      )}
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return <FeedbackState loading={message.startsWith('Loading') || message.startsWith('Searching')}>{message}</FeedbackState>;
}
