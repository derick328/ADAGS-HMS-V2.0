import { useMutation, useQuery } from '@tanstack/react-query';
import { useState, type CSSProperties } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { finalizeConsultation } from '../../shared/api/clinical';
import { ApiError } from '../../shared/api/client';
import { fetchEncounterById } from '../../shared/api/encounters';
import { fetchPatientById } from '../../shared/api/patients';
import { searchPriceCatalog, type PriceCatalogItemResponse } from '../../shared/api/priceCatalog';
import { scheduleSurgery, type SurgeryUrgency } from '../../shared/api/surgeryOrders';
import { fetchTheatresByFacility } from '../../shared/api/theatres';
import { useAuth } from '../../shared/auth/AuthProvider';
import { Combobox } from '../../shared/components/Combobox';

// 07-doctor.md §7's "Schedule surgery" disposition. Reached from
// ConsultationPage's disposition dropdown; on success it also finalizes
// the consultation record (backend ScheduleSurgeryService creates the
// order + posts the procedure charge but does NOT itself close the
// consultation — that's on this page, in one submit, so a doctor who
// booked the theatre isn't left with a still-open note to close
// separately).
export function ScheduleSurgeryPage() {
  const { encounterId } = useParams<{ encounterId: string }>();
  const { accessToken } = useAuth();
  const navigate = useNavigate();

  const [selectedProcedure, setSelectedProcedure] = useState<PriceCatalogItemResponse | null>(null);
  const [surgeryType, setSurgeryType] = useState('');
  const [urgency, setUrgency] = useState<SurgeryUrgency>('ELECTIVE');
  const [anesthesiaType, setAnesthesiaType] = useState('GENERAL');
  const [scheduledDate, setScheduledDate] = useState('');
  const [timeSlot, setTimeSlot] = useState('');
  const [theatreId, setTheatreId] = useState('');
  const [estimatedDurationMinutes, setEstimatedDurationMinutes] = useState('60');
  const [preOpNotes, setPreOpNotes] = useState('');
  const [consentObtained, setConsentObtained] = useState(false);

  const { data: encounter } = useQuery({
    queryKey: ['encounters', encounterId],
    queryFn: () => fetchEncounterById(encounterId as string, accessToken as string),
    enabled: accessToken != null && encounterId != null,
  });

  const { data: patient } = useQuery({
    queryKey: ['patients', encounter?.patientId],
    queryFn: () => fetchPatientById(encounter!.patientId as string, accessToken as string),
    enabled: accessToken != null && !!encounter?.patientId,
  });

  const { data: theatres, isLoading: theatresLoading, isError: theatresError } = useQuery({
    queryKey: ['theatres', encounter?.facilityId],
    queryFn: () => fetchTheatresByFacility(encounter!.facilityId as string, accessToken as string),
    enabled: accessToken != null && !!encounter?.facilityId,
  });
  const activeTheatres = (theatres ?? []).filter((t) => t.active !== false);

  const canSubmit =
    !!selectedProcedure &&
    !!surgeryType.trim() &&
    !!anesthesiaType.trim() &&
    !!scheduledDate &&
    !!timeSlot.trim() &&
    !!theatreId &&
    consentObtained;

  const submit = useMutation({
    // Schedule first (posts the procedure charge), then finalize the
    // consultation with CONSULT_COMPLETE — a scheduled surgery leaves
    // the consultation done and the patient's next stop is theatre.
    // Two calls, not a single transaction: if the second fails, the
    // surgery is booked but the consultation is still open, and the
    // doctor sees the error and can retry the finalize from the
    // consultation page. Beats losing a booked theatre slot to a
    // network blip mid-second-call.
    mutationFn: async () => {
      await scheduleSurgery(
        {
          encounterId: encounterId as string,
          procedureServiceItemCode: selectedProcedure!.code as string,
          surgeryType: surgeryType.trim(),
          urgency,
          anesthesiaType: anesthesiaType.trim(),
          scheduledDate,
          timeSlot: timeSlot.trim(),
          theatreId,
          estimatedDurationMinutes: Number(estimatedDurationMinutes) || undefined,
          preOpNotes: preOpNotes.trim() || undefined,
          consentObtained,
        },
        accessToken as string,
      );
      await finalizeConsultation(
        encounterId as string,
        { disposition: 'CONSULT_COMPLETE' },
        accessToken as string,
      );
    },
    onSuccess: () => navigate('/doctor'),
  });

  return (
    <div style={{ maxWidth: 720 }}>
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => navigate(`/doctor/consultation/${encounterId}`)}
          style={{ marginBottom: 'var(--space-2)' }}
        >
          ← Back to consultation
        </button>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)' }}>Schedule surgery</h1>
        {patient && (
          <p style={{ color: 'var(--text-secondary)', margin: '4px 0 0' }}>
            {patient.fullName} · {patient.hospitalNo}
          </p>
        )}
      </header>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (canSubmit) submit.mutate();
        }}
        noValidate
        style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}
      >
        <div>
          <Combobox<PriceCatalogItemResponse>
            label="Procedure"
            placeholder="Search the procedure catalogue…"
            search={(q) =>
              searchPriceCatalog(q, encounterId, accessToken as string).then((rows) =>
                rows.filter((r) => r.category === 'PROCEDURE'),
              )
            }
            getKey={(o) => o.serviceItemId as string}
            renderOption={(o) => (
              <span>
                {o.name} <span style={{ color: 'var(--text-muted)' }}>· {o.code}</span>
              </span>
            )}
            onSelect={(o) => setSelectedProcedure(o)}
          />
          {selectedProcedure && (
            <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginTop: 4 }}>
              Selected: <strong>{selectedProcedure.name}</strong> · {selectedProcedure.code}
            </div>
          )}
        </div>

        <Field label="Surgery type" hint="e.g. Laparoscopic cholecystectomy, Open reduction internal fixation">
          <input value={surgeryType} onChange={(e) => setSurgeryType(e.target.value)} style={inputStyle} />
        </Field>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
          <Field label="Urgency">
            <select value={urgency} onChange={(e) => setUrgency(e.target.value as SurgeryUrgency)} style={inputStyle}>
              <option value="ELECTIVE">Elective</option>
              <option value="URGENT">Urgent</option>
              <option value="IMMEDIATE">Immediate (emergency)</option>
            </select>
          </Field>
          <Field label="Anaesthesia">
            <select value={anesthesiaType} onChange={(e) => setAnesthesiaType(e.target.value)} style={inputStyle}>
              <option value="GENERAL">General</option>
              <option value="REGIONAL">Regional</option>
              <option value="SPINAL">Spinal</option>
              <option value="LOCAL">Local</option>
              <option value="SEDATION">Sedation</option>
            </select>
          </Field>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-3)' }}>
          <Field label="Scheduled date">
            <input
              type="date"
              min={new Date().toISOString().slice(0, 10)}
              value={scheduledDate}
              onChange={(e) => setScheduledDate(e.target.value)}
              style={inputStyle}
            />
          </Field>
          <Field label="Time slot" hint="e.g. 09:00 or 09:00–11:00">
            <input value={timeSlot} onChange={(e) => setTimeSlot(e.target.value)} style={inputStyle} />
          </Field>
          <Field label="Estimated duration (min)">
            <input
              type="number"
              min={1}
              value={estimatedDurationMinutes}
              onChange={(e) => setEstimatedDurationMinutes(e.target.value)}
              style={inputStyle}
            />
          </Field>
        </div>

        <Field label="Theatre">
          {theatresLoading ? (
            <span style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>Loading theatres…</span>
          ) : theatresError ? (
            <span style={{ color: 'var(--critical)', fontSize: 'var(--text-sm)' }}>
              Could not load theatres. Ask an admin to grant you SURGERY_ORDER_CREATE at facility scope.
            </span>
          ) : activeTheatres.length === 0 ? (
            <span style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
              No active theatres configured for this facility.
            </span>
          ) : (
            <select value={theatreId} onChange={(e) => setTheatreId(e.target.value)} style={inputStyle}>
              <option value="">Select a theatre…</option>
              {activeTheatres.map((t) => (
                <option key={t.id} value={t.id as string}>
                  {t.name}
                </option>
              ))}
            </select>
          )}
        </Field>

        <Field label="Pre-op notes (optional)">
          <textarea rows={3} value={preOpNotes} onChange={(e) => setPreOpNotes(e.target.value)} style={inputStyle} />
        </Field>

        <label
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: 'var(--space-3)',
            background: consentObtained ? 'var(--green-100)' : 'var(--bg-subtle)',
            borderRadius: 'var(--radius)',
            border: '1px solid var(--border)',
          }}
        >
          <input type="checkbox" checked={consentObtained} onChange={(e) => setConsentObtained(e.target.checked)} />
          <span>
            <strong>Consent obtained</strong> — the backend refuses to schedule without this.
          </span>
        </label>

        {submit.error && (
          <div className="form-error">
            {submit.error instanceof ApiError ? submit.error.body?.detail ?? submit.error.message : 'Something went wrong. Try again.'}
          </div>
        )}

        <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'center' }}>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={!canSubmit || submit.isPending}
          >
            {submit.isPending ? 'Scheduling…' : 'Schedule surgery & finalize consultation'}
          </button>
          {!canSubmit && (
            <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
              Complete every field and confirm consent to enable.
            </span>
          )}
        </div>
      </form>
    </div>
  );
}

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginBottom: 4 }}>
        {label}
      </label>
      {children}
      {hint && (
        <p style={{ margin: '4px 0 0', fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>
          {hint}
        </p>
      )}
    </div>
  );
}

const inputStyle: CSSProperties = {
  width: '100%',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
