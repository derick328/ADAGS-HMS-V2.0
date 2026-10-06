import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState, type CSSProperties } from 'react';
import { ApiError } from '../../shared/api/client';
import {
  activateTheatre,
  createTheatre,
  deactivateTheatre,
  fetchAllTheatresByFacility,
  type TheatreResponse,
} from '../../shared/api/theatres';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDateTime } from '../../shared/format/date';

// 18-admin.md §2 "Facility & settings" — theatres are the physical
// resource an admin defines so doctors can schedule against them (root
// CLAUDE.md "Facility"). Gated on THEATRE_MANAGE (V50) at facility scope;
// the read gate on GET /api/theatres also accepts THEATRE_MANAGE so the
// admin can see what they administer.
//
// Facility selection is derived from the admin's own FACILITY-scoped
// role assignments — no facility-list endpoint exists yet, and an admin
// only manages the facilities they are actually scoped to. A GLOBAL-only
// admin will see a note to grant themselves a facility scope first.

const inputStyle: CSSProperties = {
  width: '100%',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};

export function TheatresPage() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const canManage = usePermission('THEATRE_MANAGE');
  const { data: me } = useMe();

  // Every FACILITY-scoped role assignment the admin holds becomes a
  // manageable facility. De-dupe on scopeId — an admin holding two roles
  // at the same facility (ADMIN + ACCOUNTANT, say) should see it once.
  const facilityOptions = useMemo(() => {
    const seen = new Set<string>();
    const rows: { scopeId: string; roleCode: string }[] = [];
    for (const s of me?.scopes ?? []) {
      if (s.scopeType === 'FACILITY' && s.scopeId && !seen.has(s.scopeId)) {
        seen.add(s.scopeId);
        rows.push({ scopeId: s.scopeId, roleCode: s.roleCode ?? '' });
      }
    }
    return rows;
  }, [me?.scopes]);

  // GLOBAL-scoped admins (SUPER_ADMIN/ADMIN@GLOBAL) satisfy any facility
  // gate via scope walk-up (AuthorizationService.withinScope), so they
  // should be able to manage any facility — but there is no facility-list
  // endpoint yet. Falling back to a UUID text field is uglier than a
  // dropdown, and only surfaces for the rare global-admin path.
  const hasGlobalScope = (me?.scopes ?? []).some((s) => s.scopeType === 'GLOBAL');

  const [facilityId, setFacilityId] = useState<string>('');
  const [globalFacilityInput, setGlobalFacilityInput] = useState<string>(
    // Single-facility installs default to the seeded facility id (V1
    // seeds "Main facility" at this UUID) so a global admin doesn't have
    // to hunt it down before they can do anything.
    '00000000-0000-0000-0000-000000000001',
  );
  const activeFacilityId =
    facilityId ||
    facilityOptions[0]?.scopeId ||
    (hasGlobalScope ? globalFacilityInput.trim() : '');

  const {
    data: theatres,
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ['admin-theatres', activeFacilityId],
    queryFn: () => fetchAllTheatresByFacility(activeFacilityId, accessToken as string),
    enabled: accessToken != null && !!activeFacilityId && canManage,
  });

  const invalidateBoth = () => {
    queryClient.invalidateQueries({ queryKey: ['admin-theatres', activeFacilityId] });
    // Doctor's picker shares this key on facility id — reactivating a
    // theatre must make it selectable on the schedule-surgery form
    // without asking the doctor to reload.
    queryClient.invalidateQueries({ queryKey: ['theatres', activeFacilityId] });
  };

  const [newName, setNewName] = useState('');
  const create = useMutation({
    mutationFn: () =>
      createTheatre({ facilityId: activeFacilityId, name: newName.trim() }, accessToken as string),
    onSuccess: () => {
      setNewName('');
      invalidateBoth();
    },
  });

  const activate = useMutation({
    mutationFn: (id: string) => activateTheatre(id, accessToken as string),
    onSuccess: invalidateBoth,
  });

  const deactivate = useMutation({
    mutationFn: (id: string) => deactivateTheatre(id, accessToken as string),
    onSuccess: invalidateBoth,
  });

  const togglingId =
    activate.isPending ? activate.variables ?? null : deactivate.isPending ? deactivate.variables ?? null : null;

  const canSubmit = !!activeFacilityId && newName.trim().length > 0 && !create.isPending;

  return (
    <div>
      <PageHeader title={<>Theatres</>} description={<>Define the operating theatres this facility runs. Doctors book against these when scheduling surgery.</>} />

      {!canManage ? (
        <p className="form-error">You need THEATRE_MANAGE at facility scope to see this page.</p>
      ) : facilityOptions.length === 0 && !hasGlobalScope ? (
        <p style={{ color: 'var(--text-muted)' }}>
          You don't hold a facility-scoped role yet. Grant yourself an admin role at the target facility from{' '}
          <strong>Users</strong> first.
        </p>
      ) : (
        <>
          {facilityOptions.length === 0 && hasGlobalScope && (
            <section
              style={{
                marginBottom: 'var(--space-4)',
                padding: 'var(--space-3)',
                background: 'var(--bg-surface)',
                border: '1px solid var(--border)',
                borderRadius: 'var(--radius-lg)',
                maxWidth: 560,
              }}
            >
              <label style={{ display: 'block', fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginBottom: 4 }}>
                Facility ID
              </label>
              <input aria-label="Facility UUID" className="clinical-control"
                value={globalFacilityInput}
                onChange={(e) => setGlobalFacilityInput(e.target.value)}
                placeholder="Facility UUID"
                style={inputStyle}
              />
              <p style={{ margin: '4px 0 0', fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>
                You hold a GLOBAL role, so you can manage any facility's theatres — enter its UUID. The default is the
                seeded main facility.
              </p>
            </section>
          )}

          {facilityOptions.length > 1 && (
            <section
              style={{
                marginBottom: 'var(--space-4)',
                padding: 'var(--space-3)',
                background: 'var(--bg-surface)',
                border: '1px solid var(--border)',
                borderRadius: 'var(--radius-lg)',
                maxWidth: 480,
              }}
            >
              <label style={{ display: 'block', fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginBottom: 4 }}>
                Facility
              </label>
              <select className="clinical-control"
                value={activeFacilityId}
                onChange={(e) => setFacilityId(e.target.value)}
                style={inputStyle}
              >
                {facilityOptions.map((o) => (
                  <option key={o.scopeId} value={o.scopeId}>
                    {o.scopeId}
                    {o.roleCode ? ` · ${o.roleCode}` : ''}
                  </option>
                ))}
              </select>
            </section>
          )}

          <section
            style={{
              marginBottom: 'var(--space-5)',
              padding: 'var(--space-4)',
              background: 'var(--bg-surface)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-lg)',
              maxWidth: 640,
            }}
          >
            <h2 style={{ margin: 0, fontSize: 'var(--text-base)', fontWeight: 'var(--weight-medium)' }}>
              Add a theatre
            </h2>
            <p style={{ margin: '4px 0 var(--space-3)', color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
              The doctor's schedule-surgery form only offers <strong>Major</strong> and <strong>Minor</strong> theatres by
              name. Extra theatres you add here stay visible to admins and can be picked from other surgery flows.
            </p>
            <form className="clinical-filter-bar"
              onSubmit={(event) => {
                event.preventDefault();
                if (canSubmit) create.mutate();
              }}
              noValidate
              style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'flex-end', flexWrap: 'wrap' }}
            >
              <div style={{ flex: 1, minWidth: 240 }}>
                <label style={{ display: 'block', fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginBottom: 4 }}>
                  Theatre name
                </label>
                <input aria-label="e.g. Major Theatre, Minor Theatre, Endoscopy Suite" className="clinical-control"
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  placeholder="e.g. Major Theatre, Minor Theatre, Endoscopy Suite"
                  style={inputStyle}
                />
              </div>
              <button type="submit" className="btn btn-primary" disabled={!canSubmit}>
                {create.isPending ? 'Adding…' : 'Add theatre'}
              </button>
            </form>
            {create.isError && (
              <p className="form-error" style={{ marginTop: 'var(--space-2)' }}>
                {errorMessage(create.error)}
              </p>
            )}
          </section>

          <section>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 'var(--space-2)', marginBottom: 'var(--space-2)' }}>
              <h2 style={{ margin: 0, fontSize: 'var(--text-base)', fontWeight: 'var(--weight-medium)' }}>
                Current theatres
              </h2>
              <span style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>
                Only <strong>active</strong> theatres appear in the doctor's schedule-surgery picker.
              </span>
            </div>
            {isLoading ? (
              <FeedbackState loading>Loading theatres…</FeedbackState>
            ) : isError ? (
              <p className="form-error">{errorMessage(error)}</p>
            ) : (theatres ?? []).length === 0 ? (
              <FeedbackState>No theatres yet. Add Major Theatre and Minor Theatre first — those are the two the doctor form uses.</FeedbackState>
            ) : (
              <TheatreTable
                rows={theatres ?? []}
                togglingId={togglingId}
                onActivate={(id) => activate.mutate(id)}
                onDeactivate={(id) => deactivate.mutate(id)}
                toggleError={activate.error ?? deactivate.error ?? null}
              />
            )}
          </section>
        </>
      )}
    </div>
  );
}

function TheatreTable({
  rows,
  togglingId,
  onActivate,
  onDeactivate,
  toggleError,
}: {
  rows: TheatreResponse[];
  togglingId: string | null;
  onActivate: (id: string) => void;
  onDeactivate: (id: string) => void;
  toggleError: unknown;
}) {
  return (
    <>
      <TableRegion><table className="table" style={{ maxWidth: 720 }}>
        <thead>
          <tr>
            <th>Name</th>
            <th>Status</th>
            <th>Created</th>
            <th style={{ textAlign: 'right' }}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((t) => {
            const isInactive = t.active === false;
            const isBusy = togglingId === t.id;
            return (
              <tr key={t.id}>
                <td>{t.name}</td>
                <td>
                  {isInactive ? (
                    <span className="badge badge-neutral">Inactive</span>
                  ) : (
                    <span className="badge badge-success">Active</span>
                  )}
                </td>
                <td>{t.createdAt ? formatDateTime(t.createdAt) : '—'}</td>
                <td style={{ textAlign: 'right' }}>
                  {isInactive ? (
                    <button
                      type="button"
                      className="btn btn-primary"
                      onClick={() => t.id && onActivate(t.id)}
                      disabled={!t.id || isBusy}
                    >
                      {isBusy ? 'Activating…' : 'Activate'}
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="btn btn-ghost"
                      onClick={() => t.id && onDeactivate(t.id)}
                      disabled={!t.id || isBusy}
                      title="Hides this theatre from the doctor's schedule-surgery picker. Existing surgery orders keep pointing at it."
                    >
                      {isBusy ? 'Deactivating…' : 'Deactivate'}
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table></TableRegion>
      {toggleError != null && <p className="form-error">{errorMessage(toggleError)}</p>}
    </>
  );
}

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.body?.detail ?? err.message;
  if (err instanceof Error) return err.message;
  return 'Something went wrong. Try again.';
}
