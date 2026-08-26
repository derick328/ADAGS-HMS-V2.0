import { useQuery } from '@tanstack/react-query';
import { fetchLabOrdersByEncounter } from '../../shared/api/labOrders';
import { fetchPrescriptionsByEncounter } from '../../shared/api/prescriptions';
import { fetchVitalSignsByEncounter } from '../../shared/api/vitalSigns';
import { fetchMar } from '../../shared/api/medicationAdministration';
import { fetchWoundCare } from '../../shared/api/woundCare';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDateTime } from '../../shared/format/date';
import type { AdmissionResponse } from '../../shared/api/admissions';

const panelStyle: React.CSSProperties = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
  borderRadius: 'var(--radius-lg)',
  padding: 'var(--space-4)',
};
const panelTitleStyle: React.CSSProperties = { fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 };

// 17-ward-nurse.md screen 2: "Aggregated view: vitals history, MAR, wound
// notes, doctor's ward prescriptions and lab requests" — the at-a-glance
// summary; each panel's "See all" jumps to the tab with the full record.
export function PatientCareHubOverviewTab({
  admission,
  onNavigateTab,
}: {
  admission: AdmissionResponse;
  onNavigateTab: (tab: string) => void;
}) {
  const { accessToken } = useAuth();

  const { data: vitals } = useQuery({
    queryKey: ['vital-signs', admission.encounterId],
    queryFn: () => fetchVitalSignsByEncounter(admission.encounterId as string, accessToken as string),
    enabled: accessToken != null && admission.encounterId != null,
  });
  const wardVitals = (vitals ?? [])
    .filter((v) => v.context === 'WARD_ROUNDS')
    .sort((a, b) => new Date(b.occurredAt ?? 0).getTime() - new Date(a.occurredAt ?? 0).getTime());
  const latestVital = wardVitals[0];

  const { data: mar } = useQuery({
    queryKey: ['mar', admission.id],
    queryFn: () => fetchMar(admission.id as string, accessToken as string),
    enabled: accessToken != null && admission.id != null,
  });
  const marRows = mar ?? [];
  const now = Date.now();
  const overdue = marRows.filter((m) => m.status === 'DUE' && new Date(m.scheduledAt ?? 0).getTime() < now).length;
  const given = marRows.filter((m) => m.status === 'GIVEN').length;

  const { data: wounds } = useQuery({
    queryKey: ['wound-care', admission.id],
    queryFn: () => fetchWoundCare(admission.id as string, accessToken as string),
    enabled: accessToken != null && admission.id != null,
  });
  const latestWound = (wounds ?? []).sort(
    (a, b) => new Date(b.recordedAt ?? 0).getTime() - new Date(a.recordedAt ?? 0).getTime(),
  )[0];

  const { data: prescriptions } = useQuery({
    queryKey: ['prescriptions', 'by-encounter', admission.encounterId],
    queryFn: () => fetchPrescriptionsByEncounter(admission.encounterId as string, accessToken as string),
    enabled: accessToken != null && admission.encounterId != null,
  });
  const { data: labOrders } = useQuery({
    queryKey: ['lab-orders', 'by-encounter', admission.encounterId],
    queryFn: () => fetchLabOrdersByEncounter(admission.encounterId as string, accessToken as string),
    enabled: accessToken != null && admission.encounterId != null,
  });

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-4)' }}>
      <section style={panelStyle}>
        <h2 style={panelTitleStyle}>Latest vitals</h2>
        {!latestVital ? (
          <p style={{ color: 'var(--text-muted)', margin: 0 }}>No ward-rounds vitals recorded yet.</p>
        ) : (
          <>
            <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)', marginBottom: 'var(--space-2)' }}>
              {formatDateTime(latestVital.occurredAt)}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 'var(--space-2)', fontSize: 'var(--text-sm)' }}>
              {Object.entries(latestVital.readings ?? {}).map(([k, v]) => (
                <div key={k}>
                  <div style={{ color: 'var(--text-muted)' }}>{k}</div>
                  <div className="tabular" style={{ fontWeight: 'var(--weight-medium)' }}>{v}</div>
                </div>
              ))}
              {latestVital.avpu && (
                <div>
                  <div style={{ color: 'var(--text-muted)' }}>AVPU</div>
                  <div style={{ fontWeight: 'var(--weight-medium)' }}>{latestVital.avpu}</div>
                </div>
              )}
            </div>
          </>
        )}
        <button type="button" className="btn btn-ghost" style={{ marginTop: 'var(--space-3)', paddingLeft: 0 }} onClick={() => onNavigateTab('vitals')}>
          See all vitals →
        </button>
      </section>

      <section style={panelStyle}>
        <h2 style={panelTitleStyle}>MAR</h2>
        <div style={{ display: 'flex', gap: 'var(--space-4)', fontSize: 'var(--text-sm)' }}>
          <div>
            <div style={{ color: 'var(--text-muted)' }}>Given</div>
            <div style={{ fontWeight: 'var(--weight-medium)', fontSize: 'var(--text-lg)' }}>{given}</div>
          </div>
          <div>
            <div style={{ color: 'var(--text-muted)' }}>Overdue</div>
            <div style={{ fontWeight: 'var(--weight-medium)', fontSize: 'var(--text-lg)', color: overdue > 0 ? 'var(--critical)' : undefined }}>
              {overdue}
            </div>
          </div>
        </div>
        <button type="button" className="btn btn-ghost" style={{ marginTop: 'var(--space-3)', paddingLeft: 0 }} onClick={() => onNavigateTab('mar')}>
          Open MAR →
        </button>
      </section>

      <section style={panelStyle}>
        <h2 style={panelTitleStyle}>Latest wound note</h2>
        {!latestWound ? (
          <p style={{ color: 'var(--text-muted)', margin: 0 }}>No wound care recorded yet.</p>
        ) : (
          <>
            <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)', marginBottom: 'var(--space-1)' }}>
              {formatDateTime(latestWound.recordedAt)}
            </div>
            <p style={{ margin: 0, fontSize: 'var(--text-sm)' }}>{latestWound.appearance}</p>
            {latestWound.signsOfInfection && <span className="badge badge-critical" style={{ marginTop: 'var(--space-2)' }}>Signs of infection</span>}
          </>
        )}
        <div>
          <button type="button" className="btn btn-ghost" style={{ marginTop: 'var(--space-3)', paddingLeft: 0 }} onClick={() => onNavigateTab('wound')}>
            Open wound care →
          </button>
        </div>
      </section>

      <section style={panelStyle}>
        <h2 style={panelTitleStyle}>Doctor's orders</h2>
        <div style={{ fontSize: 'var(--text-sm)', marginBottom: 'var(--space-2)' }}>
          <strong>Prescriptions</strong>
          {(prescriptions ?? []).length === 0 ? (
            <p style={{ color: 'var(--text-muted)', margin: '4px 0' }}>None.</p>
          ) : (
            <ul style={{ margin: '4px 0', paddingLeft: 'var(--space-4)' }}>
              {(prescriptions ?? []).slice(0, 5).map((p) => (
                <li key={p.prescriptionLineId}>
                  {p.itemName} — <span className="badge badge-neutral">{p.status}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div style={{ fontSize: 'var(--text-sm)' }}>
          <strong>Lab requests</strong>
          {(labOrders ?? []).length === 0 ? (
            <p style={{ color: 'var(--text-muted)', margin: '4px 0' }}>None.</p>
          ) : (
            <ul style={{ margin: '4px 0', paddingLeft: 'var(--space-4)' }}>
              {(labOrders ?? []).slice(0, 5).map((o) => (
                <li key={o.id}>
                  {o.labTestName} — <span className="badge badge-neutral">{o.status}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>
    </div>
  );
}
