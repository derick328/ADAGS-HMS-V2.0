import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type CSSProperties } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ApiError, changePassword } from '../../shared/api/client';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';

// Backed by GET /me (identity, roles/scopes, permissions) and
// POST /me/change-password (admin-only and requires the current password).
// Passwords for other users are managed on the Users page. No MFA-enrolment endpoint
// exists yet (18-admin.md §12 flags MFA as mandatory for ADMIN).
export function ProfilePage() {
  const { data: me, isLoading } = useMe();

  return (
    <div>
      <PageHeader title={<>Profile</>} />

      {isLoading || !me ? (
        <FeedbackState loading>Loading…</FeedbackState>
      ) : (
        <>
          {me.mustChangePassword && (
            <p
              role="alert"
              style={{
                marginTop: 0,
                padding: 'var(--space-3)',
                background: 'var(--warning-bg)',
                color: 'var(--warning)',
                borderRadius: 'var(--radius)',
                maxWidth: 480,
              }}
            >
              Your password was set at install time — change it below before continuing.
            </p>
          )}

          <section
            style={{
              background: 'var(--bg-surface)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-lg)',
              padding: 'var(--space-4)',
              maxWidth: 480,
              marginBottom: 'var(--space-4)',
            }}
          >
            <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>Account</h2>
            <dl style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', margin: 0 }}>
              <dt style={{ fontWeight: 'var(--weight-medium)' }}>Username</dt>
              <dd style={{ margin: '0 0 var(--space-2)' }}>{me.username}</dd>
              <dt style={{ fontWeight: 'var(--weight-medium)' }}>User ID</dt>
              <dd style={{ margin: 0, fontFamily: 'var(--font-mono)', fontSize: 'var(--text-xs)' }}>{me.userId}</dd>
            </dl>
          </section>

          <ChangePasswordSection />

          <section
            style={{
              background: 'var(--bg-surface)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-lg)',
              overflow: 'hidden',
              marginBottom: 'var(--space-4)',
            }}
          >
            <h2
              style={{
                fontSize: 'var(--text-lg)',
                fontWeight: 'var(--weight-medium)',
                margin: 0,
                padding: 'var(--space-4) var(--space-4) 0',
              }}
            >
              Role assignments
            </h2>
            {!me.scopes || me.scopes.length === 0 ? (
              <FeedbackState>No role assignments.</FeedbackState>
            ) : (
              <TableRegion label="Role assignments"><table className="table">
                <caption className="sr-only">Role assignments</caption>
                <thead>
                  <tr>
                    <th>Role</th>
                    <th>Scope</th>
                    <th>Valid from</th>
                    <th>Valid to</th>
                  </tr>
                </thead>
                <tbody>
                  {me.scopes.map((scope, i) => (
                    <tr key={i}>
                      <td style={{ fontWeight: 'var(--weight-medium)' }}>{scope.roleCode}</td>
                      <td>
                        {scope.scopeType}
                        {scope.scopeId && (
                          <span style={{ color: 'var(--text-muted)' }}> · {scope.scopeId.slice(0, 8)}</span>
                        )}
                      </td>
                      <td>{formatDate(scope.validFrom)}</td>
                      <td>{scope.validTo ? formatDate(scope.validTo) : <span className="badge badge-success">ongoing</span>}</td>
                    </tr>
                  ))}
                </tbody>
              </table></TableRegion>
            )}
          </section>

          <section
            style={{
              background: 'var(--bg-surface)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-lg)',
              padding: 'var(--space-4)',
            }}
          >
            <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>
              Permissions ({me.permissions?.length ?? 0})
            </h2>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-1)' }}>
              {(me.permissions ?? []).map((code) => (
                <span key={code} className="badge badge-neutral">
                  {code}
                </span>
              ))}
            </div>
          </section>
        </>
      )}
    </div>
  );
}

const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password'),
    newPassword: z.string().min(8, 'Enter a new password of at least 8 characters'),
    confirmPassword: z.string().min(1, 'Re-enter the new password'),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    message: "That doesn't match the new password",
    path: ['confirmPassword'],
  });
type ChangePasswordValues = z.infer<typeof changePasswordSchema>;

function ChangePasswordSection() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [succeeded, setSucceeded] = useState(false);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ChangePasswordValues>({ resolver: zodResolver(changePasswordSchema) });

  const submit = useMutation({
    mutationFn: (values: ChangePasswordValues) =>
      changePassword({ currentPassword: values.currentPassword, newPassword: values.newPassword }, accessToken as string),
    onSuccess: () => {
      reset();
      setSucceeded(true);
      queryClient.invalidateQueries({ queryKey: ['me'] });
    },
  });

  return (
    <section
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-4)',
        maxWidth: 480,
        marginBottom: 'var(--space-4)',
      }}
    >
      <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>Change password</h2>
      <form
        onSubmit={handleSubmit((values) => {
          setSucceeded(false);
          submit.mutate(values);
        })}
        noValidate
        style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}
      >
        <div>
          <label htmlFor="current-password" style={labelStyle}>
            Current password
          </label>
          <input className="clinical-control" id="current-password" type="password" {...register('currentPassword')} style={fieldStyle} />
          {errors.currentPassword && <span className="field-error">{errors.currentPassword.message}</span>}
        </div>
        <div>
          <label htmlFor="new-password" style={labelStyle}>
            New password
          </label>
          <input className="clinical-control" id="new-password" type="password" {...register('newPassword')} style={fieldStyle} />
          {errors.newPassword && <span className="field-error">{errors.newPassword.message}</span>}
        </div>
        <div>
          <label htmlFor="confirm-password" style={labelStyle}>
            Confirm new password
          </label>
          <input className="clinical-control" id="confirm-password" type="password" {...register('confirmPassword')} style={fieldStyle} />
          {errors.confirmPassword && <span className="field-error">{errors.confirmPassword.message}</span>}
        </div>
        {submit.error && <div className="form-error">{changePasswordErrorMessage(submit.error)}</div>}
        {succeeded && !submit.error && (
          <div style={{ color: 'var(--green-600)', fontSize: 'var(--text-sm)' }}>Password changed.</div>
        )}
        <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
          {submit.isPending ? 'Changing…' : 'Change password'}
        </button>
      </form>
    </section>
  );
}

function changePasswordErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 400) {
    return 'Current password is incorrect, or the new password is too short.';
  }
  return 'Something went wrong. Try again.';
}

function formatDate(value: string | undefined): string {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en-TZ', { dateStyle: 'medium', timeZone: 'Africa/Dar_es_Salaam' }).format(
    new Date(value),
  );
}

const labelStyle: CSSProperties = { display: 'block', fontSize: 'var(--text-sm)' };

const fieldStyle: CSSProperties = {
  width: '100%',
  marginTop: 'var(--space-1)',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
