import { Checkbox } from '../../shared/components/Checkbox';
import { PageHeader } from '../../shared/components/PageHeader';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useState, type CSSProperties } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { finalizeConsultation } from '../../shared/api/clinical';
import { ApiError } from '../../shared/api/client';
import { fetchEncounterById } from '../../shared/api/encounters';
import { fetchPatientById } from '../../shared/api/patients';
import { searchPriceCatalog, type PriceCatalogItemResponse } from '../../shared/api/priceCatalog';
import { fetchActiveSurgeryOrders, scheduleSurgery, type SurgeryUrgency } from '../../shared/api/surgeryOrders';
import { fetchTheatresByFacility } from '../../shared/api/theatres';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { Combobox } from '../../shared/components/Combobox';
import { todayLocalDate } from '../../shared/format/date';
import { SurgeryScheduleCalendar } from './SurgeryScheduleCalendar';

// 07-doctor.md §7's "Schedule surgery" disposition. Reached from
// ConsultationPage's disposition dropdown; on success it also finalizes
// the consultation record (backend ScheduleSurgeryService creates the
// order + posts the procedure charge but does NOT itself close the
// consultation — that's on this page, in one submit, so a doctor who
// booked the theatre isn't left with a still-open note to close
// separately).
//
// Draft persistence: the whole form is mirrored to localStorage keyed by
// encounter id. A doctor waiting on the physical consent form can hit
// "Save draft", walk away, log out, and come back later — the browser
// keeps the draft. The consultation itself must stay OPEN for the doctor
// to reach this page again, so submit (which finalizes) only fires when
// consent is actually in hand.

type DraftShape = {
  procedure: PriceCatalogItemResponse | null;
  surgeryType: string;
  urgency: SurgeryUrgency;
  anesthesiaType: string;
  scheduledDate: string;
  startTime: string;
  endTime: string;
  theatreId: string;
  estimatedDurationMinutes: string;
  preOpNotes: string;
  consentObtained: boolean;
  consentDocumentName: string;
};

const DRAFT_KEY_PREFIX = 'doctor.scheduleSurgery.draft.';

function loadDraft(encounterId: string | undefined): Partial<DraftShape> {
  if (!encounterId) return {};
  try {
    const raw = localStorage.getItem(DRAFT_KEY_PREFIX + encounterId);
    return raw ? (JSON.parse(raw) as Partial<DraftShape>) : {};
  } catch {
    return {};
  }
}

function clearDraft(encounterId: string | undefined) {
  if (!encounterId) return;
  try {
    localStorage.removeItem(DRAFT_KEY_PREFIX + encounterId);
  } catch {
    /* ignore */
  }
}

// Combines two HH:MM time inputs into the backend's free-text time_slot:
// "09:00–11:00" when both are set, "09:00" for a single time.
function composeTimeSlot(start: string, end: string): string {
  const s = start.trim();
  const e = end.trim();
  if (s && e) return `${s}–${e}`;
  return s;
}

export function ScheduleSurgeryPage() {
  const { encounterId } = useParams<{ encounterId: string }>();
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const navigate = useNavigate();
  const today = todayLocalDate();

  const initial = loadDraft(encounterId);

  const [selectedProcedure, setSelectedProcedure] = useState<PriceCatalogItemResponse | null>(initial.procedure ?? null);
  const [surgeryType, setSurgeryType] = useState(initial.surgeryType ?? '');
  const [urgency, setUrgency] = useState<SurgeryUrgency>(initial.urgency ?? 'ELECTIVE');
  const [anesthesiaType, setAnesthesiaType] = useState(initial.anesthesiaType ?? 'GENERAL');
  const [scheduledDate, setScheduledDate] = useState(initial.scheduledDate ?? '');
  const [startTime, setStartTime] = useState(initial.startTime ?? '');
  const [endTime, setEndTime] = useState(initial.endTime ?? '');
  const [theatreId, setTheatreId] = useState(initial.theatreId ?? '');
  const [estimatedDurationMinutes, setEstimatedDurationMinutes] = useState(initial.estimatedDurationMinutes ?? '60');
  const [preOpNotes, setPreOpNotes] = useState(initial.preOpNotes ?? '');
  const [consentObtained, setConsentObtained] = useState(initial.consentObtained ?? false);
  const [consentDocumentName, setConsentDocumentName] = useState(initial.consentDocumentName ?? '');
  const [draftSavedAt, setDraftSavedAt] = useState<Date | null>(null);

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

  const {
    data: theatres,
    isLoading: theatresLoading,
    isError: theatresError,
    error: theatresErrorObj,
  } = useQuery({
    queryKey: ['theatres', encounter?.facilityId],
    queryFn: () => fetchTheatresByFacility(encounter!.facilityId as string, accessToken as string),
    enabled: accessToken != null && !!encounter?.facilityId,
  });
  const theatresDenied =
    theatresErrorObj instanceof ApiError &&
    (theatresErrorObj.status === 401 || theatresErrorObj.status === 403);
  // Only Major and Minor are offered here — V74 seeds those two per
  // facility. Any other theatres an admin has added stay pickable via the
  // admin surface but are filtered out of this doctor-facing form so the
  // choice matches how clinicians talk ("book major" / "book minor").
  const majorMinorTheatres = (theatres ?? []).filter(
    (t) => t.active !== false && /^(major|minor)( theatre)?$/i.test((t.name ?? '').trim()),
  );

  // The doctor's own active surgeries, for the calendar overview and the
  // selected-day detail. /api/surgery-orders returns every active order
  // (bounded, small — 17-ward-nurse.md §10); we keep the ones this doctor
  // scheduled, same client-side filter as My Scheduled Surgeries.
  const { data: activeOrders } = useQuery({
    queryKey: ['surgery-orders', 'active'],
    queryFn: () => fetchActiveSurgeryOrders(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 30_000,
  });
  const mySurgeries = (activeOrders ?? []).filter((o) => o.scheduledBy === me?.userId);

  const composedTimeSlot = composeTimeSlot(startTime, endTime);

  const canSubmit =
    !!selectedProcedure &&
    !!surgeryType.trim() &&
    !!anesthesiaType.trim() &&
    !!scheduledDate &&
    !!startTime &&
    !!theatreId &&
    consentObtained;

  const canSaveDraft =
    !!selectedProcedure ||
    !!surgeryType.trim() ||
    !!scheduledDate ||
    !!startTime ||
    !!theatreId ||
    !!preOpNotes.trim() ||
    !!consentDocumentName ||
    consentObtained;

  const saveDraft = () => {
    if (!encounterId) return;
    const draft: DraftShape = {
      procedure: selectedProcedure,
      surgeryType,
      urgency,
      anesthesiaType,
      scheduledDate,
      startTime,
      endTime,
      theatreId,
      estimatedDurationMinutes,
      preOpNotes,
      consentObtained,
      consentDocumentName,
    };
    try {
      localStorage.setItem(DRAFT_KEY_PREFIX + encounterId, JSON.stringify(draft));
      setDraftSavedAt(new Date());
    } catch {
      /* localStorage full or unavailable — the fields are still in memory */
    }
  };

  // Auto-save every 15 seconds while there is anything worth saving. This
  // is the "walked away for the consent form" safety net — closing the tab
  // by accident doesn't cost the doctor their work.
  useEffect(() => {
    if (!encounterId || !canSaveDraft) return;
    const t = window.setInterval(saveDraft, 15_000);
    return () => window.clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    encounterId,
    canSaveDraft,
    selectedProcedure,
    surgeryType,
    urgency,
    anesthesiaType,
    scheduledDate,
    startTime,
    endTime,
    theatreId,
    estimatedDurationMinutes,
    preOpNotes,
    consentObtained,
    consentDocumentName,
  ]);

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
          timeSlot: composedTimeSlot,
          theatreId,
          estimatedDurationMinutes: Number(estimatedDurationMinutes) || undefined,
          preOpNotes: preOpNotes.trim() || undefined,
          consentObtained,
          consentDocumentKey: consentDocumentName.trim() || undefined,
        },
        accessToken as string,
      );
      await finalizeConsultation(
        encounterId as string,
        { disposition: 'CONSULT_COMPLETE' },
        accessToken as string,
      );
    },
    onSuccess: () => {
      clearDraft(encounterId);
      navigate('/doctor');
    },
  });

  const missing: string[] = [];
  if (!selectedProcedure) missing.push('procedure');
  if (!surgeryType.trim()) missing.push('surgery type');
  if (!scheduledDate) missing.push('scheduled date');
  if (!startTime) missing.push('start time');
  if (!theatreId) missing.push('theatre');
  if (!consentObtained) missing.push('consent');

  return (
    <div style={{ maxWidth: 720 }}>
      <PageHeader title={<>Schedule surgery</>}><button
          type="button"
          className="btn btn-ghost"
          onClick={() => navigate(`/doctor/consultation/${encounterId}`)}
          style={{ marginBottom: 'var(--space-2)' }}
        >
          ← Back to consultation
        </button>{patient && (
          <p style={{ color: 'var(--text-secondary)', margin: '4px 0 0' }}>
            {patient.fullName} · {patient.hospitalNo}
          </p>
        )}</PageHeader>

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
          <input className="clinical-control" value={surgeryType} onChange={(e) => setSurgeryType(e.target.value)} style={inputStyle} />
        </Field>

        <div className="clinical-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
          <Field label="Urgency">
            <select className="clinical-control" value={urgency} onChange={(e) => setUrgency(e.target.value as SurgeryUrgency)} style={inputStyle}>
              <option value="ELECTIVE">Elective</option>
              <option value="URGENT">Urgent</option>
              <option value="IMMEDIATE">Immediate (emergency)</option>
            </select>
          </Field>
          <Field label="Anaesthesia">
            <select className="clinical-control" value={anesthesiaType} onChange={(e) => setAnesthesiaType(e.target.value)} style={inputStyle}>
              <option value="GENERAL">General</option>
              <option value="REGIONAL">Regional</option>
              <option value="SPINAL">Spinal</option>
              <option value="LOCAL">Local</option>
              <option value="SEDATION">Sedation</option>
            </select>
          </Field>
        </div>

        <Field
          label="Pick a date"
          hint="Green days already have a surgery of yours. Select a day to see its times below, or use the date field."
        >
          <SurgeryScheduleCalendar
            surgeries={mySurgeries}
            selectedDate={scheduledDate}
            minDate={today}
            onSelectDate={setScheduledDate}
          />
        </Field>

        <div className="clinical-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: 'var(--space-3)' }}>
          <Field label="Scheduled date">
            <input className="clinical-control"
              type="date"
              min={today}
              value={scheduledDate}
              onChange={(e) => setScheduledDate(e.target.value)}
              style={inputStyle}
            />
          </Field>
          <Field label="Start time">
            <input className="clinical-control"
              type="time"
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
              style={inputStyle}
            />
          </Field>
          <Field label="End time (optional)">
            <input className="clinical-control"
              type="time"
              value={endTime}
              onChange={(e) => setEndTime(e.target.value)}
              style={inputStyle}
            />
          </Field>
          <Field label="Duration (min)">
            <input className="clinical-control"
              type="number"
              min={1}
              value={estimatedDurationMinutes}
              onChange={(e) => setEstimatedDurationMinutes(e.target.value)}
              style={inputStyle}
            />
          </Field>
        </div>
        {composedTimeSlot && (
          <p style={{ margin: '-8px 0 0', fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>
            Slot recorded as <strong>{composedTimeSlot}</strong>.
          </p>
        )}

        <Field label="Theatre" hint="Only Major and Minor are bookable from here.">
          {theatresLoading ? (
            <span style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>Loading theatres…</span>
          ) : theatresError ? (
            <span style={{ color: 'var(--critical)', fontSize: 'var(--text-sm)' }}>
              {theatresDenied
                ? 'Your account isn\'t allowed to schedule surgeries. Ask an admin to assign you the specialist doctor role.'
                : 'Could not load theatres. Try again in a moment.'}
            </span>
          ) : majorMinorTheatres.length === 0 ? (
            <span style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
              No Major/Minor theatre configured for this facility. Ask an admin to add one.
            </span>
          ) : (
            <select className="clinical-control" value={theatreId} onChange={(e) => setTheatreId(e.target.value)} style={inputStyle}>
              <option value="">Select a theatre…</option>
              {majorMinorTheatres.map((t) => (
                <option key={t.id} value={t.id as string}>
                  {t.name}
                </option>
              ))}
            </select>
          )}
        </Field>

        <Field label="Pre-op notes (optional)">
          <textarea className="clinical-control" rows={3} value={preOpNotes} onChange={(e) => setPreOpNotes(e.target.value)} style={inputStyle} />
        </Field>

        <Field
          label="Consent document"
          hint="Attach the signed consent form. Only the file name is recorded — keep the physical/scanned copy in the patient's file."
        >
          <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center', flexWrap: 'wrap' }}>
            <input
              type="file"
              accept=".pdf,.jpg,.jpeg,.png"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) setConsentDocumentName(f.name);
              }}
              style={{ font: 'inherit' }}
            />
            {consentDocumentName && (
              <>
                <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
                  Attached: <strong>{consentDocumentName}</strong>
                </span>
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={() => setConsentDocumentName('')}
                  style={{ padding: '2px 8px', fontSize: 'var(--text-sm)' }}
                >
                  Remove
                </button>
              </>
            )}
          </div>
        </Field>

        <Checkbox  className="clinical-choice" checked={consentObtained} onChange={(e) => setConsentObtained(e.target.checked)} label={<><span>
            <strong>Consent obtained</strong> — the backend refuses to schedule without this. Leave unchecked to save a draft while you wait for the signed form.
          </span></>} />

        {submit.error && (
          <div className="form-error">
            {submit.error instanceof ApiError ? submit.error.body?.detail ?? submit.error.message : 'Something went wrong. Try again.'}
          </div>
        )}

        <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'center', flexWrap: 'wrap' }}>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={!canSubmit || submit.isPending}
          >
            {submit.isPending ? 'Scheduling…' : 'Schedule surgery & finalize consultation'}
          </button>
          <button
            type="button"
            className="btn btn-secondary"
            disabled={!canSaveDraft}
            onClick={saveDraft}
          >
            Save draft
          </button>
          {draftSavedAt && (
            <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
              Draft saved {draftSavedAt.toLocaleTimeString()} — kept in this browser
            </span>
          )}
          {!draftSavedAt && (initial.surgeryType || initial.procedure) && (
            <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
              Restored earlier draft for this encounter.
            </span>
          )}
        </div>
        {!canSubmit && missing.length > 0 && (
          <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
            Still needed to schedule: <strong>{missing.join(', ')}</strong>.
            {!consentObtained && ' Use Save draft in the meantime.'}
          </p>
        )}
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
