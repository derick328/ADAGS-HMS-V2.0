import "./ResultEntryPage.css";
import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../shared/api/client';
import { fetchResultEntryForm } from '../../shared/api/labOrders';
import {
  fetchCriticalResultNotificationsByResult,
  type CriticalResultNotificationResponse,
} from '../../shared/api/criticalResultNotifications';
import { fetchLabResultDraft, saveLabResultDraft, submitLabResultDraft, uploadMachineOutput, uploadLabReportPdf, fetchDraftMachineOutputUrl, type LabResultResponse } from '../../shared/api/labResults';
import { useAuth } from '../../shared/auth/AuthProvider';
import { ResultDetail } from './MyHistoryPage';
import { parseBc10Csv, type Bc10Preview } from './bc10Csv';
import { approvedParameterInsertIndex } from './manualParameters';
import type { SpecimenQuality } from '../../shared/api/specimens';

// 15-lab-technician.md §5.2: flag is computed server-side, never typed —
// the override control exists so a technician can contest an implausible
// flag with a stated reason, not so they can set the flag directly.
const OVERRIDE_OPTIONS = ['NORMAL', 'HIGH', 'LOW', 'CRITICAL'] as const;
const QUALITY_OPTIONS: { value: SpecimenQuality; label: string }[] = [
  { value: 'ADEQUATE', label: 'Adequate' },
  { value: 'HAEMOLYSED', label: 'Haemolysed' },
  { value: 'CLOTTED', label: 'Clotted' },
  { value: 'INSUFFICIENT_VOLUME', label: 'Insufficient volume' },
  { value: 'WRONG_CONTAINER', label: 'Wrong container' },
  { value: 'MISLABELLED', label: 'Mislabelled' },
  { value: 'DELAYED_TRANSPORT', label: 'Delayed transport' },
];

const FBP_PERIPHERAL_SMEAR_NAME = 'Full Blood Picture (FBP) / + Peripheral Smear';

export function hasFbpPeripheralSmearParameters(testName?: string): boolean {
  return testName?.trim().replace(/\s+/g, ' ').toLowerCase() === FBP_PERIPHERAL_SMEAR_NAME.toLowerCase();
}

const parameterRowSchema = z
  .object({
    parameterId: z.string().uuid(),
    resultValue: z.string().refine((v) => v.trim() === '' || Number.isFinite(Number(v)), 'Enter a numeric value'),
    overrideFlag: z.union([z.enum(OVERRIDE_OPTIONS), z.literal('')]).optional(),
    overrideReason: z.string().optional(),
  })
  .refine((v) => !v.overrideFlag || (v.overrideReason && v.overrideReason.trim().length > 0), {
    message: 'Enter a reason for overriding the flag',
    path: ['overrideReason'],
  });

const schema = z.object({
  parameters: z.array(parameterRowSchema),
  sampleType: z.string().trim().min(1, 'Enter the sample type'),
  specimenQuality: z.enum(['ADEQUATE', 'HAEMOLYSED', 'CLOTTED', 'INSUFFICIENT_VOLUME', 'WRONG_CONTAINER', 'MISLABELLED', 'DELAYED_TRANSPORT']),
  specimenQualityNote: z.string().optional(),
  sampleTypeCorrectionReason: z.string().optional(),
  methodology: z.string().optional(),
  reagentsUsedNote: z.string().optional(),
  findings: z.string().optional(),
  interpretation: z.string().optional(),
  referenceRangeNote: z.string().optional(),
  conclusion: z.string().optional(),
  remarks: z.string().optional(),
}).superRefine((value, context) => {
  if (value.specimenQuality !== 'ADEQUATE' && !value.specimenQualityNote?.trim()) {
    context.addIssue({ code: z.ZodIssueCode.custom, path: ['specimenQualityNote'], message: 'Explain the quality issue' });
  }
  const hasParameterValue = value.parameters.some(row => row.resultValue.trim() !== '');
  if (!hasParameterValue && !value.findings?.trim()) {
    context.addIssue({ code: z.ZodIssueCode.custom, path: ['findings'], message: 'Enter a parameter or narrative findings' });
  }
});
type Values = z.infer<typeof schema>;

export function ResultEntryPage() {
  const { labOrderId } = useParams<{ labOrderId: string }>();
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [savedVersion, setSavedVersion] = useState(0);
  const [savedAt, setSavedAt] = useState<string | null>(null);
  const [showImport, setShowImport] = useState(false);
  const [importMessage, setImportMessage] = useState<string | null>(null);
  const [importPreview, setImportPreview] = useState<Bc10Preview | null>(null);
  const [selectedParameterId, setSelectedParameterId] = useState('');
  const [machineOutput, setMachineOutput] = useState<{ objectKey: string; fileName: string } | null>(null);
  const [labReport, setLabReport] = useState<{ objectKey: string; fileName: string } | null>(null);
  const [machinePreviewUrl, setMachinePreviewUrl] = useState<string | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [released, setReleased] = useState<LabResultResponse | null>(null);

  const { data: form, isLoading, isError: formError } = useQuery({
    queryKey: ['lab-orders', 'result-entry-form', labOrderId],
    queryFn: () => fetchResultEntryForm(labOrderId as string, accessToken as string),
    enabled: accessToken != null && labOrderId != null,
  });

  const { data: draft, isError: draftError } = useQuery({
    queryKey: ['lab-orders', 'result-draft', labOrderId],
    queryFn: () => fetchLabResultDraft(labOrderId as string, accessToken as string),
    enabled: !!accessToken && !!labOrderId,
  });

  const {
    register,
    control,
    handleSubmit,
    getValues,
    reset,
    setError,
    watch,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { parameters: [] } });
  const { fields, insert, remove, replace } = useFieldArray({ control, name: 'parameters' });
  const watchedParameters = watch('parameters');
  const showParameters = hasFbpPeripheralSmearParameters(form?.labTestName);
  const availableParameters = (form?.parameters ?? [])
    .filter(definition => !watchedParameters?.some(row => row.parameterId === definition.parameterId))
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));

  function addApprovedParameter() {
    if (!form || !selectedParameterId) return;
    const index = approvedParameterInsertIndex(form.parameters ?? [], watchedParameters ?? [], selectedParameterId);
    if (index == null) return;
    insert(index, { parameterId: selectedParameterId, resultValue: '', overrideFlag: '', overrideReason: '' });
    setSelectedParameterId('');
  }

  useEffect(() => {
    if (!form || draft) return;
    reset({
      parameters: showParameters ? [...(form.parameters ?? [])].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)).map((p) => ({ parameterId: p.parameterId as string, resultValue: '', overrideFlag: '', overrideReason: '' })) : [],
      sampleType: form.sampleType ?? '',
      specimenQuality: form.specimenQuality ?? 'ADEQUATE',
      specimenQualityNote: form.specimenQualityNote ?? '',
      sampleTypeCorrectionReason: '',
      methodology: '',
      reagentsUsedNote: '',
      findings: '',
      interpretation: '',
      referenceRangeNote: '',
      conclusion: '',
      remarks: '',
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form, showParameters]);

  useEffect(() => {
    if (!draft || !form) return;
    if (draft.status === 'DRAFT') {
      const savedValues = draft.payload as Partial<Values> & { singleValue?: string; singleValueUnit?: string };
      const legacyFinding = [savedValues.singleValue, savedValues.singleValueUnit].filter(Boolean).join(' ');
      reset({ ...savedValues, parameters: showParameters ? savedValues.parameters ?? [] : [], findings: savedValues.findings || legacyFinding,
        sampleType: savedValues.sampleType ?? form.sampleType ?? '',
        specimenQuality: savedValues.specimenQuality ?? form.specimenQuality ?? 'ADEQUATE',
        specimenQualityNote: savedValues.specimenQualityNote ?? form.specimenQualityNote ?? '',
        sampleTypeCorrectionReason: savedValues.sampleTypeCorrectionReason ?? '' } as Values);
      const saved = draft.payload.machineOutput as { objectKey: string; fileName: string } | null;
      setMachineOutput(saved ?? null);
      setLabReport((draft.payload.labReport as { objectKey: string; fileName: string } | null) ?? null);
      setSavedVersion(draft.version);
      setSavedAt(draft.updatedAt);
    }
  }, [draft, form, reset, showParameters]);

  const save = useMutation({
    mutationFn: (values: Values) => saveLabResultDraft(labOrderId as string, savedVersion,
      { ...values, parameters: showParameters ? values.parameters : [], machineOutput, labReport }, accessToken as string),
    onSuccess: (saved) => {
      setSavedVersion(saved.version);
      setSavedAt(saved.updatedAt);
      queryClient.setQueryData(['lab-orders', 'result-draft', labOrderId], saved);
    },
  });

  const submit = useMutation({
    mutationFn: async (values: Values) => {
      const saved = await saveLabResultDraft(labOrderId as string, savedVersion,
        { ...values, parameters: showParameters ? values.parameters : [], machineOutput, labReport }, accessToken as string);
      setSavedVersion(saved.version);
      return submitLabResultDraft(labOrderId as string, saved.version, {
        specimenId: form?.specimenId as string,
        methodology: values.methodology || undefined,
        reagentsUsedNote: values.reagentsUsedNote || undefined,
        findings: values.findings || undefined,
        interpretation: values.interpretation || undefined,
        referenceRangeNote: values.referenceRangeNote || undefined,
        conclusion: values.conclusion || undefined,
        resultType: showParameters && values.parameters.some(parameter => parameter.resultValue.trim() !== '') ? 'NUMERIC' : 'NARRATIVE',
        remarks: values.remarks || undefined,
        machineOutputObjectKey: machineOutput?.objectKey,
        labReportObjectKey: labReport?.objectKey,
        parameterValues: (showParameters ? values.parameters : []).filter((p) => p.resultValue.trim() !== '').map((p) => ({
          parameterId: p.parameterId,
          resultValue: Number(p.resultValue),
          overrideFlag: p.overrideFlag || undefined,
          overrideReason: p.overrideReason || undefined,
        })),
      }, accessToken as string);
    },
    onSuccess: (result) => { setReleased(result); queryClient.invalidateQueries({ queryKey: ['lab-orders', 'result-draft', labOrderId] }); },
  });

  async function onFileChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    setUploadError(null);
    if (file.type !== 'application/pdf' || file.size > 10 * 1024 * 1024) {
      setUploadError('Choose a PDF no larger than 10 MB.');
      return;
    }
    setUploading(true);
    try {
      const { objectKey } = await uploadMachineOutput(labOrderId as string, file, accessToken as string);
      setMachineOutput({ objectKey, fileName: file.name });
      setMachinePreviewUrl(URL.createObjectURL(file));
    } catch {
      setUploadError('Upload failed — try again.');
    } finally {
      setUploading(false);
    }
  }

  async function onReportChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    setUploadError(null);
    if (file.type !== 'application/pdf' || file.size > 10 * 1024 * 1024) {
      setUploadError('Choose a PDF no larger than 10 MB.');
      return;
    }
    setUploading(true);
    try {
      const { objectKey } = await uploadLabReportPdf(labOrderId as string, file, accessToken as string);
      setLabReport({ objectKey, fileName: file.name });
    } catch { setUploadError('Report upload failed.'); }
    finally { setUploading(false); }
  }

  useEffect(() => () => {
    if (machinePreviewUrl) URL.revokeObjectURL(machinePreviewUrl);
  }, [machinePreviewUrl]);

  const { data: savedMachinePreview } = useQuery({
    queryKey: ['lab-orders', labOrderId, 'machine-output', machineOutput?.objectKey],
    queryFn: () => fetchDraftMachineOutputUrl(labOrderId as string, machineOutput?.objectKey as string, accessToken as string),
    enabled: !!labOrderId && !!accessToken && !!machineOutput && !machinePreviewUrl,
    staleTime: 10 * 60 * 1000,
  });
  const machineOutputMissing = form?.producesMachineOutput && !machineOutput;
  const sampleTypeChanged = !!form && watch('sampleType')?.trim() !== (form.sampleType ?? '').trim();

  if (released) {
    return <ReleasedConfirmation result={released} onDone={() => navigate('/lab')} />;
  }
  if (draft?.status === 'SUBMITTED' && draft.submittedResultId) {
    return <ResultDetail resultId={draft.submittedResultId} onBack={() => navigate('/lab')} />;
  }

  return (
    <div>
      <PageHeader title={<>Result entry</>}>{form && (
          <p style={{ color: 'var(--text-secondary)', margin: '4px 0 0' }}>
            {form.patientName} · {form.hospitalNumber} · {form.labTestName} · {form.accessionNo}
          </p>
        )}</PageHeader>

      {formError || draftError ? (
        <FeedbackState>Could not load the result entry form or saved draft. Check access and try again.</FeedbackState>
      ) : isLoading || !form ? (
        <FeedbackState loading>Loading…</FeedbackState>
      ) : (
        <form
          onSubmit={handleSubmit((values) => {
            if (machineOutputMissing) return;
            if (values.specimenQuality !== 'ADEQUATE') {
              setError('specimenQuality', { message: 'A sample with a quality issue cannot be released. Arrange recollection.' });
              return;
            }
            if (values.sampleType.trim() !== (form.sampleType ?? '').trim() && !values.sampleTypeCorrectionReason?.trim()) {
              setError('sampleTypeCorrectionReason', { message: 'Explain the sample type correction' });
              return;
            }
            if (window.confirm('Submit and lock this result? It will be released to the ordering clinician and further changes require an amendment.')) submit.mutate(values);
          })}
          noValidate
          style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)', maxWidth: 900 }}
        >
          {savedAt && <p role="status">Draft saved — Not yet released to doctor. Last saved {new Date(savedAt).toLocaleString()}.</p>}
          <h2>Specimen and Method Information</h2>
          <div className="result-entry-fields">
            <div>
              <label htmlFor="sampleType">Sample type</label>
              <input id="sampleType" className="clinical-control" {...register('sampleType')} />
              {errors.sampleType && <div className="field-error">{errors.sampleType.message}</div>}
            </div>
            <div>
              <label htmlFor="specimenQuality">Sample quality</label>
              <select id="specimenQuality" className="clinical-control" {...register('specimenQuality')}>
                {QUALITY_OPTIONS.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
              </select>
              {errors.specimenQuality && <div className="field-error">{errors.specimenQuality.message}</div>}
            </div>
            <div>
              <label htmlFor="specimenQualityNote">Sample quality note</label>
              <textarea id="specimenQualityNote" className="clinical-control" rows={2} {...register('specimenQualityNote')} />
              {errors.specimenQualityNote && <div className="field-error">{errors.specimenQualityNote.message}</div>}
            </div>
            {sampleTypeChanged && <div>
              <label htmlFor="sampleTypeCorrectionReason">Reason for sample type correction</label>
              <textarea id="sampleTypeCorrectionReason" className="clinical-control" rows={2} {...register('sampleTypeCorrectionReason')} />
              {errors.sampleTypeCorrectionReason && <div className="field-error">{errors.sampleTypeCorrectionReason.message}</div>}
            </div>}
          </div>
          <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>Specimen corrections are saved with the result. A sample with a quality issue cannot be released.</p>
          <div className="result-entry-fields">
            <TextField label="Methodology" name="methodology" register={register} />
            <TextField label="Reagents used" name="reagentsUsedNote" register={register} />
            <div><TextField label="Findings" name="findings" register={register} />
              {errors.findings && <div className="field-error">{errors.findings.message}</div>}</div>
            <TextField label="Interpretation" name="interpretation" register={register} />
            <TextField label="Reference range note" name="referenceRangeNote" register={register} />
            <TextField label="Conclusion" name="conclusion" register={register} />
          </div>

          {showParameters && <>
          <h2>Parameters</h2>
          <div
            style={{
              background: 'var(--bg-surface)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-lg)',
              overflow: 'hidden',
            }}
          >
            <TableRegion label="Result parameters"><table className="table">
              <caption className="sr-only">Result parameters</caption>
              <thead>
                <tr>
                  <th>Parameter</th>
                  <th>Result</th>
                  <th>Unit</th>
                  <th>Reference range</th>
                  <th>Flag</th>
                  <th>Method</th>
                  <th>Override</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {fields.map((field, index) => {
                  const definition = form.parameters?.find(parameter => parameter.parameterId === field.parameterId);
                  const rowValue = watchedParameters?.[index];
                  return (
                    <tr key={field.id}>
                      <td>
                        <div style={{ fontWeight: 'var(--weight-medium)' }}>{definition?.name}</div>
                        <div style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>{definition?.code}</div>
                      </td>
                      <td>
                        <input className="clinical-control"
                          type="text"
                          inputMode="decimal"
                          {...register(`parameters.${index}.resultValue` as const)}
                          style={{ width: 100, padding: 'var(--space-1) var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}
                        />
                        {errors.parameters?.[index]?.resultValue && (
                          <div className="field-error">{errors.parameters[index]?.resultValue?.message}</div>
                        )}
                      </td>
                      <td>{definition?.unit || '—'}</td>
                      <td className="tabular" style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
                        {definition?.normalLow != null && definition?.normalHigh != null
                          ? `${definition.normalLow} – ${definition.normalHigh}`
                          : '—'}
                      </td>
                      <td>Computed on submission</td>
                      <td>{definition?.method || '—'}</td>
                      <td>
                        <select className="clinical-control"
                          {...register(`parameters.${index}.overrideFlag` as const)}
                          style={{ padding: 'var(--space-1)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}
                        >
                          <option value="">Computed</option>
                          {OVERRIDE_OPTIONS.map((o) => (
                            <option key={o} value={o}>
                              {o}
                            </option>
                          ))}
                        </select>
                        {rowValue?.overrideFlag && (
                          <input aria-label="Reason for override" className="clinical-control"
                            type="text"
                            placeholder="Reason for override"
                            {...register(`parameters.${index}.overrideReason` as const)}
                            style={{ display: 'block', marginTop: 4, width: 160, padding: 'var(--space-1) var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}
                          />
                        )}
                        {errors.parameters?.[index]?.overrideReason && (
                          <div className="field-error">{errors.parameters[index]?.overrideReason?.message}</div>
                        )}
                      </td>
                      <td>
                        <button type="button" className="btn btn-ghost" aria-label={`Remove ${definition?.name ?? 'parameter'} row`}
                          onClick={() => {
                            if (rowValue?.resultValue?.trim() && !window.confirm('Remove this entered measurement?')) return;
                            remove(index);
                          }}>Remove</button>
                      </td>
                    </tr>
                  );
                })}
                {fields.length === 0 && (
                  <tr>
                    <td colSpan={8} style={{ color: 'var(--text-muted)', textAlign: 'center', padding: 'var(--space-4)' }}>
                      {form.parameters?.length ? 'No parameter rows selected. Add an approved parameter below.' : 'This test has no coded parameters. Record narrative findings below.'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table></TableRegion>
          </div>

          <div className="result-parameter-actions">
            <button type="button" className="btn" onClick={() => { replace([]); setSelectedParameterId(''); }}
              disabled={fields.length === 0 || !!watchedParameters?.some(row => row.resultValue?.trim() || row.overrideFlag)}>
              Choose rows manually
            </button>
            <label htmlFor="approvedParameter">Approved parameter</label>
            <select id="approvedParameter" className="clinical-control" value={selectedParameterId}
              onChange={event => setSelectedParameterId(event.target.value)} disabled={availableParameters.length === 0}>
              <option value="">{availableParameters.length ? 'Choose a parameter' : 'All approved parameters shown'}</option>
              {availableParameters.map(parameter => <option key={parameter.parameterId} value={parameter.parameterId}>
                {parameter.name} ({parameter.code})
              </option>)}
            </select>
            <button type="button" className="btn" onClick={addApprovedParameter} disabled={!selectedParameterId}>Add Parameter</button>
            <button type="button" className="btn" onClick={() => setShowImport(true)}>Import BC-10 CSV</button>
          </div>
          <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
            Enter measurements directly in the Result column. Added rows use approved test definitions and remain in catalogue order.
          </p>
          {showImport && <div>
            <label htmlFor="bc10File">BC-10 CSV file</label>
            <input id="bc10File" type="file" accept=".csv,text/csv" onChange={async (event) => {
              const file = event.target.files?.[0];
              if (!file) return;
              if (file.size > 2_000_000) { setImportMessage('CSV exceeds the 2 MB import limit.'); return; }
              const parsed = parseBc10Csv(await file.text());
              setImportPreview(parsed.supported ? parsed.preview : null);
              setImportMessage(parsed.supported
                ? `Previewed ${parsed.preview.measurements.length} measurements. Values cannot be applied until analyser units are verified.`
                : parsed.reason);
            }} />
            {importMessage && <p role="status">{importMessage}</p>}
            {importPreview && <div>
              <p>Export patient ID: {importPreview.patientId || 'Not recorded'} · Drawn: {importPreview.drawDate || 'Not recorded'} {importPreview.drawTime} · Background: {importPreview.background || 'Not recorded'}</p>
              <p>Order hospital number: {form.hospitalNumber || 'Not recorded'}. Confirm patient identity against the specimen before using an analyser export.</p>
              <TableRegion label="BC-10 import preview"><table className="table">
                <thead><tr><th>Source code</th><th>Value</th><th>Catalogue parameter</th><th>Status</th></tr></thead>
                <tbody>{importPreview.measurements.map(measurement => {
                  const definition = form.parameters?.find(parameter => parameter.code?.toUpperCase() === measurement.sourceCode.toUpperCase());
                  const status = measurement.value == null ? 'Invalid value' : !definition ? 'Unmatched code' : 'Matched code; unit unverified';
                  return <tr key={measurement.sourceCode}><td>{measurement.sourceCode}</td><td>{measurement.rawValue || '—'}</td>
                    <td>{definition?.name || '—'}</td><td>{status}</td></tr>;
                })}</tbody>
              </table></TableRegion>
              {importPreview.unmatchedColumns.length > 0 && <p>Other columns: {importPreview.unmatchedColumns.join(', ')}</p>}
            </div>}
          </div>}
          </>}
          <h2>Additional Documentation</h2>
          <TextField label="Remarks" name="remarks" register={register} />
          <h2>Attachments</h2>
          <div>
            <label htmlFor="labReportFile">Optional laboratory report PDF (10 MB maximum)</label>
            <input id="labReportFile" type="file" accept="application/pdf,.pdf" onChange={onReportChange} disabled={uploading} />
            {labReport && <p role="status">Attached: {labReport.fileName}</p>}
          </div>
          <div>
              <label style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>
                Machine output PDF {form.producesMachineOutput && <span style={{ color: 'var(--critical)' }}>(required)</span>}
              </label>
              <input type="file" accept="application/pdf,.pdf" onChange={onFileChange} disabled={uploading} />
              {uploading && <div style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>Uploading…</div>}
              {machineOutput && <div style={{ color: 'var(--green-600)', fontSize: 'var(--text-sm)' }}>Attached: {machineOutput.fileName}</div>}
              {(machinePreviewUrl || savedMachinePreview?.url) && <iframe title="Machine output preview" src={machinePreviewUrl || savedMachinePreview?.url} style={{ display: 'block', width: '100%', maxWidth: 640, height: 400, marginTop: 12 }} />}
              {uploadError && <div className="field-error">{uploadError}</div>}
            </div>

          {submit.error && (
            <div className="form-error" role="alert">
              {submit.error instanceof ApiError ? submit.error.body?.detail ?? submit.error.message : 'Something went wrong.'}
            </div>
          )}
          {machineOutputMissing && (
            <div className="form-error" role="alert">
              Attach the machine output before submitting.
            </div>
          )}

          {save.error && <div className="form-error" role="alert">Could not save draft. Reload if another session changed it.</div>}
          <div style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
            <button type="button" className="btn" disabled={save.isPending || submit.isPending || uploading}
              onClick={() => save.mutate(getValues())}>{save.isPending ? 'Saving…' : 'Save Draft'}</button>
            <button type="submit" className="btn btn-primary" disabled={submit.isPending || save.isPending || uploading}>
              {submit.isPending ? 'Submitting…' : 'Submit & Lock Result'}
            </button>
            <button type="button" className="btn" onClick={() => navigate('/lab')}>Cancel</button>
          </div>
        </form>
      )}
    </div>
  );
}

function TextField({
  label,
  name,
  register,
}: {
  label: string;
  name: keyof Values;
  register: ReturnType<typeof useForm<Values>>['register'];
}) {
  return (
    <div>
      <label htmlFor={name} style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>
        {label}
      </label>
      <textarea className="clinical-control"
        id={name}
        rows={2}
        {...register(name)}
        style={{ width: '100%', padding: 'var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}
      />
    </div>
  );
}

// §7: "any parameter flag = C on release -> immediate push notification...
// tech's screen shows acknowledgement status so they know it landed."
function ReleasedConfirmation({ result, onDone }: { result: LabResultResponse; onDone: () => void }) {
  const { accessToken } = useAuth();
  const hasCritical = (result.criticalNotificationIds ?? []).length > 0;

  const { data: notifications } = useQuery({
    queryKey: ['critical-result-notifications', 'by-result', result.id],
    queryFn: () => fetchCriticalResultNotificationsByResult(result.id as string, accessToken as string),
    enabled: hasCritical && accessToken != null,
    refetchInterval: 5_000,
  });

  return (
    <>
    <div
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-6)',
        maxWidth: 520,
        textAlign: 'center',
      }}
    >
      <h2 style={{ color: 'var(--green-600)', margin: '0 0 var(--space-2)' }}>Result released</h2>
      <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>The ordering doctor can see this result now.</p>

      {hasCritical && (
        <div
          style={{
            marginTop: 'var(--space-4)',
            padding: 'var(--space-3)',
            background: 'var(--critical-bg)',
            borderRadius: 'var(--radius)',
            textAlign: 'left',
          }}
        >
          <div style={{ color: 'var(--critical)', fontWeight: 'var(--weight-medium)', marginBottom: 4 }}>
            Critical value — doctor notified
          </div>
          {(notifications ?? []).map((n: CriticalResultNotificationResponse) => (
            <div key={n.id} style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
              {n.acknowledgedAt ? 'Acknowledged' : n.escalatedAt ? 'Escalated to clinical lead' : 'Awaiting acknowledgement…'}
            </div>
          ))}
        </div>
      )}

      <button type="button" className="btn btn-primary" onClick={onDone} style={{ marginTop: 'var(--space-4)' }}>
        Back to Test Queue
      </button>
    </div>
    <ResultDetail resultId={result.id as string} onBack={onDone} />
    </>
  );
}
