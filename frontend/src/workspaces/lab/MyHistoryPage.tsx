import { FeedbackState } from '../../shared/components/FeedbackState';
import { Tabs } from '../../shared/components/Tabs';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Combobox } from '../../shared/components/Combobox';
import { fetchLabResult, fetchMyLabResults, searchLabResults, type LabResultDetailResponse } from '../../shared/api/labResults';
import { searchPatients, type PatientSearchResult } from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDateTime } from '../../shared/format/date';

type Tab = 'mine' | 'lookup';

// 15-lab-technician.md §9: My results (this technician's own completed
// work) and Patient lookup (by patient or accession, across every
// technician) — both feed the same detail/print view.
export function MyHistoryPage() {
  const [tab, setTab] = useState<Tab>('mine');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  if (selectedId) {
    return <ResultDetail resultId={selectedId} onBack={() => setSelectedId(null)} />;
  }

  return (
    <div>
      <PageHeader title={<>My history</>} description={<>Review released results or find a report by patient or accession number.</>} />

      <Tabs aria-label="History" variant="underline" value={tab} onChange={(value) => setTab(value as Tab)} unmountInactivePanels items={[
        { id: 'mine', label: 'My results', content: <MyResultsTab onSelect={setSelectedId} /> },
        { id: 'lookup', label: 'Patient lookup', content: <PatientLookupTab onSelect={setSelectedId} /> },
      ]} />

    </div>
  );
}

function MyResultsTab({ onSelect }: { onSelect: (id: string) => void }) {
  const { accessToken } = useAuth();
  const { data, isLoading } = useQuery({
    queryKey: ['lab-results', 'mine'],
    queryFn: () => fetchMyLabResults(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 30_000,
  });

  return <ResultsTable rows={data ?? []} isLoading={isLoading} emptyMessage="No results entered yet." onSelect={onSelect} />;
}

function PatientLookupTab({ onSelect }: { onSelect: (id: string) => void }) {
  const { accessToken } = useAuth();
  const [patientId, setPatientId] = useState<string | null>(null);
  const [patientLabel, setPatientLabel] = useState<string | null>(null);
  const [accessionNo, setAccessionNo] = useState('');
  const [submittedAccession, setSubmittedAccession] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['lab-results', 'search', patientId, submittedAccession],
    queryFn: () => searchLabResults({ patientId: patientId ?? undefined, accessionNo: submittedAccession ?? undefined }, accessToken as string),
    enabled: accessToken != null && (patientId != null || submittedAccession != null),
  });

  return (
    <div>
      <div style={{ display: 'flex', gap: 'var(--space-4)', alignItems: 'flex-end', marginBottom: 'var(--space-4)', flexWrap: 'wrap' }}>
        <div style={{ width: '100%', maxWidth: 320 }}>
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
              setSubmittedAccession(null);
            }}
            clearOnSelect={false}
          />
          {patientLabel && (
            <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginTop: 4 }}>
              Showing results for {patientLabel}.{' '}
              <button type="button" className="btn btn-ghost" onClick={() => { setPatientId(null); setPatientLabel(null); }}>
                Clear
              </button>
            </div>
          )}
        </div>

        <form className="clinical-filter-bar"
          onSubmit={(e) => {
            e.preventDefault();
            setPatientId(null);
            setSubmittedAccession(accessionNo.trim() || null);
          }}
          style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'flex-end' }}
        >
          <div>
            <label htmlFor="accession-search" style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>
              Search by accession number
            </label>
            <input className="clinical-control"
              id="accession-search"
              value={accessionNo}
              onChange={(e) => setAccessionNo(e.target.value)}
              style={{ padding: 'var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}
            />
          </div>
          <button type="submit" className="btn btn-secondary">
            Search
          </button>
        </form>
      </div>

      {patientId || submittedAccession ? (
        <ResultsTable rows={data ?? []} isLoading={isLoading} emptyMessage="No results found." onSelect={onSelect} />
      ) : (
        <p style={{ color: 'var(--text-muted)' }}>Search by patient or accession number to look up a result.</p>
      )}
    </div>
  );
}

function ResultsTable({
  rows,
  isLoading,
  emptyMessage,
  onSelect,
}: {
  rows: LabResultDetailResponse[];
  isLoading: boolean;
  emptyMessage: string;
  onSelect: (id: string) => void;
}) {
  return (
    <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
      {isLoading ? (
        <FeedbackState loading>Loading…</FeedbackState>
      ) : rows.length === 0 ? (
        <FeedbackState>{emptyMessage}</FeedbackState>
      ) : (
        <TableRegion label="Lab results"><table className="table">
          <caption className="sr-only">Lab results</caption>
          <thead>
            <tr>
              <th>Patient</th>
              <th>Test</th>
              <th>Accession</th>
              <th>Released</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id} onClick={() => onSelect(row.id as string)} style={{ cursor: 'pointer' }}>
                <td>
                  {row.patientName}
                  {row.amendsId && <span className="badge badge-warning" style={{ marginLeft: 'var(--space-2)' }}>amended</span>}
                </td>
                <td>{row.labTestName}</td>
                <td className="tabular" style={{ fontFamily: 'var(--font-mono)' }}>
                  {row.accessionNo}
                </td>
                <td className="tabular">{formatDateTime(row.releasedAt)}</td>
                <td>
                  <button type="button" className="btn btn-ghost" onClick={(e) => { e.stopPropagation(); onSelect(row.id as string); }}>
                    View
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table></TableRegion>
      )}
    </div>
  );
}

function ResultDetail({ resultId, onBack }: { resultId: string; onBack: () => void }) {
  const { accessToken } = useAuth();
  const { data: result, isLoading } = useQuery({
    queryKey: ['lab-results', resultId],
    queryFn: () => fetchLabResult(resultId, accessToken as string),
    enabled: accessToken != null,
  });

  if (isLoading || !result) {
    return <FeedbackState loading>Loading…</FeedbackState>;
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 'var(--space-4)' }}>
        <button type="button" className="btn btn-ghost" onClick={onBack}>
          ← Back
        </button>
        <button type="button" className="btn btn-secondary" onClick={() => window.print()}>
          Print report
        </button>
      </div>

      {result.amendsId && (
        <div className="form-error" role="alert" style={{ background: 'var(--warning-bg)', color: 'var(--warning)', padding: 'var(--space-3)', borderRadius: 'var(--radius)', marginBottom: 'var(--space-4)' }}>
          This result supersedes an earlier version released {formatDateTime(result.amendsOriginalEnteredAt)}.
          {result.amendmentReason && <> Reason: {result.amendmentReason}</>}
        </div>
      )}

      <section style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)', marginBottom: 'var(--space-4)' }}>
        <dl className="clinical-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-2)', fontSize: 'var(--text-sm)', margin: 0 }}>
          <div>
            <dt style={{ color: 'var(--text-muted)' }}>Patient</dt>
            <dd style={{ margin: 0, fontWeight: 'var(--weight-medium)' }}>{result.patientName} · {result.hospitalNumber}</dd>
          </div>
          <div>
            <dt style={{ color: 'var(--text-muted)' }}>Test</dt>
            <dd style={{ margin: 0, fontWeight: 'var(--weight-medium)' }}>{result.labTestName}</dd>
          </div>
          <div>
            <dt style={{ color: 'var(--text-muted)' }}>Accession</dt>
            <dd style={{ margin: 0, fontFamily: 'var(--font-mono)' }}>{result.accessionNo}</dd>
          </div>
          <div>
            <dt style={{ color: 'var(--text-muted)' }}>Specimen</dt>
            <dd style={{ margin: 0 }}>{result.specimenType}</dd>
          </div>
          <div>
            <dt style={{ color: 'var(--text-muted)' }}>Entered by</dt>
            <dd style={{ margin: 0 }}>{result.enteredByName} · {formatDateTime(result.enteredAt)}</dd>
          </div>
          <div>
            <dt style={{ color: 'var(--text-muted)' }}>Released</dt>
            <dd style={{ margin: 0 }}>{formatDateTime(result.releasedAt)}</dd>
          </div>
          {result.verifiedByName && (
            <div>
              <dt style={{ color: 'var(--text-muted)' }}>Verified by</dt>
              <dd style={{ margin: 0 }}>{result.verifiedByName} · {formatDateTime(result.verifiedAt)}</dd>
            </div>
          )}
        </dl>
      </section>

      <section style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden', marginBottom: 'var(--space-4)' }}>
        <TableRegion label="Parameter values"><table className="table">
          <caption className="sr-only">Parameter values</caption>
          <thead>
            <tr>
              <th>Parameter</th>
              <th>Value</th>
              <th>Unit</th>
              <th>Reference range</th>
              <th>Flag</th>
            </tr>
          </thead>
          <tbody>
            {(result.parameterValues ?? []).map((p, i) => (
              <tr key={i} className={p.flag === 'CRITICAL' ? 'row-critical' : p.flag === 'HIGH' || p.flag === 'LOW' ? 'row-warning' : ''}>
                <td>{p.parameterName}</td>
                <td className="tabular">{p.resultValue}</td>
                <td>{p.unit}</td>
                <td className="tabular">{p.normalLow != null && p.normalHigh != null ? `${p.normalLow} – ${p.normalHigh}` : '—'}</td>
                <td>
                  <span className={`badge ${p.flag === 'CRITICAL' ? 'badge-critical' : p.flag === 'NORMAL' ? 'badge-success' : 'badge-warning'}`}>
                    {p.flag}
                    {p.flagOverridden && ' (overridden)'}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table></TableRegion>
      </section>

      <section style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)', fontSize: 'var(--text-sm)' }}>
        <DetailField label="Methodology" value={result.methodology} />
        <DetailField label="Findings" value={result.findings} />
        <DetailField label="Interpretation" value={result.interpretation} />
        <DetailField label="Reference range note" value={result.referenceRangeNote} />
        <DetailField label="Conclusion" value={result.conclusion} />
      </section>
    </div>
  );
}

function DetailField({ label, value }: { label: string; value?: string | null }) {
  if (!value) return null;
  return (
    <div style={{ marginBottom: 'var(--space-2)' }}>
      <span style={{ color: 'var(--text-muted)' }}>{label}: </span>
      {value}
    </div>
  );
}
