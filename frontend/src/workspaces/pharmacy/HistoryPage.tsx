import { FeedbackState } from '../../shared/components/FeedbackState';
import { QueryState } from '../../shared/components/WorkspacePage';
import { Tabs } from '../../shared/components/Tabs';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Combobox } from '../../shared/components/Combobox';
import { fetchMyDispenses, searchDispensesByPatient, type DispenseHistoryResponse } from '../../shared/api/dispenses';
import { searchPatients, type PatientSearchResult } from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDateTime } from '../../shared/format/date';

type Tab = 'mine' | 'lookup';

// 12-pharmacist.md §11: "My dispenses" (default) and "Patient lookup" —
// a patient returning with a question, or a doctor querying what was
// given, cannot be answered from a self-scoped list.
export function HistoryPage() {
  const [tab, setTab] = useState<Tab>('mine');

  return (
    <div className="staff-page-stack pharmacy-history-page">
      <PageHeader title={<>History</>} description={<>Review your dispensing activity or look up a patient’s medication history.</>} />

      <Tabs aria-label="History" variant="underline" value={tab} onChange={(value) => setTab(value as Tab)} items={[
        { id: 'mine', label: 'My dispenses', content: <MyDispensesTab /> },
        { id: 'lookup', label: 'Patient lookup', content: <PatientLookupTab /> },
      ]} />

    </div>
  );
}

function MyDispensesTab() {
  const { accessToken } = useAuth();
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['dispenses', 'mine'],
    queryFn: () => fetchMyDispenses(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 30_000,
  });

  return <DispensesTable rows={data ?? []} isLoading={isLoading} isError={isError} onRetry={() => void refetch()} emptyMessage="No dispenses recorded yet." />;
}

function PatientLookupTab() {
  const { accessToken } = useAuth();
  const [patientId, setPatientId] = useState<string | null>(null);
  const [patientLabel, setPatientLabel] = useState<string | null>(null);
  const [query, setQuery] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['dispenses', 'patient-search', query],
    queryFn: () => searchDispensesByPatient(query as string, accessToken as string),
    enabled: accessToken != null && !!query,
  });

  return (
    <div>
      <div className="pharmacy-history-search">
        <Combobox<PatientSearchResult>
          label="Search by patient"
          placeholder="Name or hospital number"
          search={(q) => searchPatients(q, accessToken as string)}
          getKey={(p) => p.id as string}
          renderOption={(p) => (
            <span>
              {p.fullName} <span style={{ color: 'var(--text-muted)' }}>· {p.hospitalNo}</span>
            </span>
          )}
          onSelect={(p) => {
            setPatientId(p.id as string);
            setPatientLabel(p.fullName ?? null);
            // Dispense search matches on patient name / hospital number
            // text (root CLAUDE.md "Modules": no cross-module entity
            // import, so this module can't take a patientId directly) —
            // the selected patient's own hospital number is a precise,
            // unambiguous query string.
            setQuery(p.hospitalNo ?? p.fullName ?? null);
          }}
          clearOnSelect={false}
        />
        {patientLabel && (
          <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginTop: 4 }}>
            Showing dispenses for {patientLabel}.{' '}
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                setPatientId(null);
                setPatientLabel(null);
                setQuery(null);
              }}
            >
              Clear
            </button>
          </div>
        )}
      </div>

      {patientId ? (
        <DispensesTable rows={data ?? []} isLoading={isLoading} isError={isError} onRetry={() => void refetch()} emptyMessage="No dispenses found for this patient." />
      ) : (
        <p style={{ color: 'var(--text-muted)' }}>Search by patient to look up their dispense history.</p>
      )}
    </div>
  );
}

function DispensesTable({ rows, isLoading, isError, onRetry, emptyMessage }: { rows: DispenseHistoryResponse[]; isLoading: boolean; isError: boolean; onRetry: () => void; emptyMessage: string }) {
  return (
    <div className="staff-table-card">
      {isLoading ? (
        <FeedbackState loading>Loading…</FeedbackState>
      ) : isError ? (
        <QueryState title="Unable to load dispensing history" onRetry={onRetry} />
      ) : rows.length === 0 ? (
        <FeedbackState>{emptyMessage}</FeedbackState>
      ) : (
        <TableRegion label="Dispenses"><table className="table">
          <caption className="sr-only">Dispenses</caption>
          <thead>
            <tr>
              <th>Patient</th>
              <th>Item</th>
              <th>Quantity</th>
              <th>Dispensed by</th>
              <th>Dispensed</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.dispenseId}>
                <td>
                  {row.patientName ?? 'Unknown patient'}
                  {row.hospitalNumber && <span style={{ color: 'var(--text-muted)' }}> · {row.hospitalNumber}</span>}
                </td>
                <td>{row.itemName}</td>
                <td className="tabular">{row.quantityDispensed}</td>
                <td>{row.dispensedByUsername}</td>
                <td className="tabular">{formatDateTime(row.dispensedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table></TableRegion>
      )}
    </div>
  );
}
