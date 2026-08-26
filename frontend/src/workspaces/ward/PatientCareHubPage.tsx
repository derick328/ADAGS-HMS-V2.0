import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { fetchAllergiesByPatient } from '../../shared/api/clinical';
import { fetchAdmissionById } from '../../shared/api/admissions';
import { useAuth } from '../../shared/auth/AuthProvider';
import { StatusBadge } from './DashboardPage';
import { formatDateTime } from '../../shared/format/date';
import { PatientCareHubOverviewTab } from './PatientCareHubOverviewTab';
import { PatientCareHubMarTab } from './PatientCareHubMarTab';
import { PatientCareHubVitalsTab } from './PatientCareHubVitalsTab';
import { PatientCareHubWoundTab } from './PatientCareHubWoundTab';
import { PatientCareHubStatusTab } from './PatientCareHubStatusTab';

type TabKey = 'overview' | 'mar' | 'vitals' | 'wound' | 'status';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'overview', label: 'Overview' },
  { key: 'mar', label: 'MAR' },
  { key: 'vitals', label: 'Vitals' },
  { key: 'wound', label: 'Wound care' },
  { key: 'status', label: 'Status & bed' },
];

// 17-ward-nurse.md screen 2/3 combined: one per-patient hub with tabs, same
// "one page, internal tabs" shape ConsultationPage uses for the doctor.
// Allergies render here, in the page header, outside every tab — root
// CLAUDE.md "Safety": inline and unmissable, never behind a tab.
export function PatientCareHubPage() {
  const { admissionId } = useParams<{ admissionId: string }>();
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = (searchParams.get('tab') as TabKey | null) ?? 'overview';
  const [tab, setTab] = useState<TabKey>(TABS.some((t) => t.key === initialTab) ? initialTab : 'overview');

  const { data: admission, isLoading } = useQuery({
    queryKey: ['admissions', admissionId],
    queryFn: () => fetchAdmissionById(admissionId as string, accessToken as string),
    enabled: accessToken != null && admissionId != null,
  });

  const { data: allergies } = useQuery({
    queryKey: ['allergies', admission?.patientId],
    queryFn: () => fetchAllergiesByPatient(admission?.patientId as string, accessToken as string),
    enabled: accessToken != null && admission?.patientId != null,
  });

  const activeAllergies = useMemo(
    () => (allergies ?? []).filter((a) => a.confirmationStatus !== 'REFUTED'),
    [allergies],
  );

  if (isLoading || !admission || !admissionId) {
    return <p style={{ color: 'var(--text-muted)' }}>Loading…</p>;
  }

  return (
    <div>
      <button type="button" className="btn btn-ghost" style={{ marginBottom: 'var(--space-3)', paddingLeft: 0 }} onClick={() => navigate('/ward-nurse/patients')}>
        ← Ward Patients
      </button>

      <header style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', marginBottom: 'var(--space-3)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>Patient care hub</h1>
        <StatusBadge status={admission.wardStatus} />
      </header>

      <dl style={{ display: 'flex', gap: 'var(--space-5)', fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', margin: '0 0 var(--space-4)' }}>
        <div>
          <dt style={{ color: 'var(--text-muted)' }}>Admitted</dt>
          <dd style={{ margin: 0 }}>{formatDateTime(admission.admittedAt)}</dd>
        </div>
        <div>
          <dt style={{ color: 'var(--text-muted)' }}>Ward class</dt>
          <dd style={{ margin: 0 }}>{admission.orderedWardClass}</dd>
        </div>
        {admission.dischargeOrderedAt && (
          <div>
            <dt style={{ color: 'var(--text-muted)' }}>Discharge ordered</dt>
            <dd style={{ margin: 0 }}>{formatDateTime(admission.dischargeOrderedAt)}</dd>
          </div>
        )}
      </dl>

      <AllergyBanner allergies={activeAllergies} />

      <nav
        aria-label="Patient care hub tabs"
        style={{ display: 'flex', gap: 'var(--space-1)', borderBottom: '1px solid var(--border)', marginBottom: 'var(--space-4)' }}
      >
        {TABS.map((t) => {
          const isActive = t.key === tab;
          return (
            <button
              key={t.key}
              type="button"
              onClick={() => {
                setTab(t.key);
                setSearchParams(t.key === 'overview' ? {} : { tab: t.key }, { replace: true });
              }}
              style={{
                padding: 'var(--space-2) var(--space-3)',
                border: 'none',
                background: 'none',
                font: 'inherit',
                cursor: 'pointer',
                color: isActive ? 'var(--text-primary)' : 'var(--text-muted)',
                fontWeight: isActive ? 'var(--weight-medium)' : 'var(--weight-normal)',
                boxShadow: isActive ? 'inset 0 -2px 0 var(--green-signal)' : 'none',
              }}
            >
              {t.label}
            </button>
          );
        })}
      </nav>

      {tab === 'overview' && <PatientCareHubOverviewTab admission={admission} onNavigateTab={(t) => setTab(t as TabKey)} />}
      {tab === 'mar' && <PatientCareHubMarTab admission={admission} />}
      {tab === 'vitals' && <PatientCareHubVitalsTab admission={admission} />}
      {tab === 'wound' && <PatientCareHubWoundTab admission={admission} />}
      {tab === 'status' && <PatientCareHubStatusTab admission={admission} />}
    </div>
  );
}

function AllergyBanner({ allergies }: { allergies: { description?: string; reaction?: string; severity?: string; confirmationStatus?: string }[] }) {
  if (allergies.length === 0) {
    return (
      <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)', marginBottom: 'var(--space-4)' }}>
        No known allergies recorded.
      </div>
    );
  }
  return (
    <div
      role="alert"
      style={{
        background: 'var(--critical-bg)',
        color: 'var(--critical)',
        borderRadius: 'var(--radius)',
        padding: 'var(--space-2) var(--space-3)',
        marginBottom: 'var(--space-4)',
        fontWeight: 'var(--weight-medium)',
      }}
    >
      ⚠ Allergies:{' '}
      {allergies
        .map((a) => {
          const detail = [a.reaction, a.severity].filter(Boolean).join(', ');
          return `${a.description}${detail ? ` — ${detail}` : ''}${a.confirmationStatus ? ` (${a.confirmationStatus})` : ''}`;
        })
        .join('; ')}
    </div>
  );
}
