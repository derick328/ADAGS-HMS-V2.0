import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useState, type CSSProperties } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ApiError } from '../../shared/api/client';
import {
  createUser,
  endRoleAssignment,
  fetchRoleAssignments,
  fetchRoles,
  fetchUsers,
  grantRoleAssignment,
  setUserPassword,
  type RoleAssignmentResponse,
  type UserResponse,
} from '../../shared/api/users';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { fetchClinicCategories, fetchClinicCatalogue, fetchUserClinics, updateUserClinics, type ClinicAssignmentUpdate } from '../../shared/api/departments';
import { ClinicAssignmentFields } from './ClinicAssignmentFields';

// 18-admin.md §4: user accounts and their scoped, time-bounded role
// assignments — "shown as a list with add and end-date actions, not a
// single dropdown." The fuller nurse-onboarding form (§4.2: DOB, license
// number, ward scope, employment type) needs a Staff entity that doesn't
// exist in the backend yet; this covers what UserController actually does
// today — create a login and grant/end scoped roles.
const SCOPE_TYPES = ['GLOBAL', 'FACILITY', 'DEPARTMENT', 'WARD', 'CLINIC', 'STORE'] as const;

const createUserSchema = z.object({
  unitIds: z.array(z.string()).optional(), primaryUnitId: z.string().nullable().optional(), hodUnitId: z.string().nullable().optional(),
  username: z.string().min(1, 'Enter a username'),
  password: z.string().min(8, 'Enter a password of at least 8 characters'),
});
type CreateUserValues = z.infer<typeof createUserSchema>;

const grantRoleSchema = z
  .object({
    roleCode: z.string().min(1, 'Choose a role'),
    scopeType: z.enum(SCOPE_TYPES),
    scopeId: z.string().optional(),
    validFrom: z.string().optional(),
    validTo: z.string().optional(),
  })
  .refine((v) => v.scopeType === 'GLOBAL' || (v.scopeId ?? '').trim() !== '', {
    message: 'Enter which facility/department/ward/clinic/store this role applies to',
    path: ['scopeId'],
  });
type GrantRoleValues = z.infer<typeof grantRoleSchema>;

export function UsersPage() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const canManageUsers = usePermission('USER_MANAGE');
  const canAssignRoles = usePermission('ROLE_ASSIGN');
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const categories = useQuery({ queryKey: ['clinic-categories'], queryFn: () => fetchClinicCategories(accessToken as string), enabled: !!accessToken && canManageUsers });
  const clinics = useQuery({ queryKey: ['clinic-catalogue'], queryFn: () => fetchClinicCatalogue(accessToken as string), enabled: !!accessToken && canManageUsers });

  const { data: users, isLoading } = useQuery({
    queryKey: ['users'],
    queryFn: () => fetchUsers(accessToken as string),
    enabled: accessToken != null && canManageUsers,
  });

  const create = useMutation({
    mutationFn: (values: CreateUserValues) => createUser(values, accessToken as string),
    onSuccess: (user) => {
      setShowCreateForm(false);
      queryClient.invalidateQueries({ queryKey: ['users'] });
      setSelectedUserId(user.id ?? null);
    },
  });

  const selectedUser = users?.find((u) => u.id === selectedUserId);

  return (
    <div>
      <PageHeader title={<>Users / add nurse / staff</>}>{canManageUsers && (
          <button type="button" className="btn btn-primary" onClick={() => setShowCreateForm((v) => !v)}>
            {showCreateForm ? 'Cancel' : 'Add user'}
          </button>
        )}</PageHeader>

      {showCreateForm && (
        <CreateUserForm
          isSubmitting={create.isPending}
          error={create.error ? errorMessage(create.error) : null}
          categories={categories.data ?? []} units={clinics.data ?? []}
          onSubmit={(values) => {
            const currentHod = clinics.data?.find(u => u.id === values.hodUnitId)?.hodUsername;
            if (!currentHod || window.confirm(`Replace ${currentHod} as Head of Department? The previous appointment remains in history.`)) create.mutate(values);
          }}
        />
      )}

      <div
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-lg)',
          overflow: 'hidden',
          marginBottom: 'var(--space-4)',
        }}
      >
        {isLoading ? (
          <EmptyState message="Loading…" />
        ) : !users || users.length === 0 ? (
          <EmptyState message="No users yet." />
        ) : (
          <TableRegion label="Users"><table className="table">
            <caption className="sr-only">Users</caption>
            <thead>
              <tr>
                <th>Username</th>
                <th>Status</th>
                <th>Password</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr
                  key={user.id}
                  onClick={() => setSelectedUserId(user.id ?? null)}
                  style={{ cursor: 'pointer' }}
                  className={user.id === selectedUserId ? 'row-selected' : ''}
                >
                  <td><button type="button" className="workspace-name" onClick={() => setSelectedUserId(user.id ?? null)}>{user.username}</button></td>
                  <td>
                    <span className={user.status === 'ACTIVE' ? 'badge badge-success' : 'badge badge-neutral'}>
                      {(user.status ?? '').toLowerCase()}
                    </span>
                  </td>
                  <td>
                    {user.mustChangePassword && <span className="badge badge-warning">admin reset needed</span>}
                  </td>
                  <td>{formatDate(user.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table></TableRegion>
        )}
      </div>

      {selectedUser && (
        <><UserClinicPanel user={selectedUser} categories={categories.data ?? []} units={clinics.data ?? []} accessToken={accessToken as string} /><RoleAssignmentsPanel user={selectedUser} canAssignRoles={canAssignRoles} canManageUsers={canManageUsers} accessToken={accessToken as string} /></>
      )}
    </div>
  );
}


function UserClinicPanel({ user, categories, units, accessToken }: {
  user: UserResponse; categories: import('../../shared/api/departments').DepartmentCategory[];
  units: import('../../shared/api/departments').ClinicUnit[]; accessToken: string;
}) {
  const queryClient = useQueryClient();
  const current = useQuery({ queryKey: ['user-clinics', user.id], queryFn: () => fetchUserClinics(user.id as string, accessToken) });
  const [value, setValue] = useState<ClinicAssignmentUpdate>({ unitIds: [], primaryUnitId: null, hodUnitId: null });
  const onChange = useCallback((next: ClinicAssignmentUpdate) => setValue(next), []);
  const save = useMutation({ mutationFn: (next: ClinicAssignmentUpdate) => updateUserClinics(user.id as string, next, accessToken), onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['user-clinics', user.id] }); queryClient.invalidateQueries({ queryKey: ['clinic-catalogue'] }); } });
  return <section style={formStyle}>
    <h2>Clinic assignments — {user.username}</h2>
    {current.isLoading ? <p role="status">Loading clinic assignments…</p> : current.isError ? <p role="alert">Clinic assignments could not be loaded.</p> :
      <ClinicAssignmentFields key={user.id + JSON.stringify(current.data) + units.length} categories={categories} units={units} initial={current.data} onChange={onChange} />}
    {save.error && <div className="form-error">{errorMessage(save.error)}</div>}
    <button type="button" className="btn btn-primary" disabled={save.isPending || !current.data} onClick={() => {
      const previousHod = current.data?.find(a => a.hod)?.departmentId;
      const vacateHodUnitIds = previousHod && previousHod !== value.hodUnitId ? [previousHod] : [];
      const changing = value.hodUnitId && units.find(u => u.id === value.hodUnitId)?.hodUserId !== user.id && units.find(u => u.id === value.hodUnitId)?.hodUserId;
      if (vacateHodUnitIds.length && !window.confirm('Record a vacancy for the previous HOD clinic? This action is retained in history.')) return;
      if (!changing || window.confirm('Replace the current Head of Department? The previous appointment will be retained in history.')) save.mutate({ ...value, vacateHodUnitIds });
    }}>{save.isPending ? 'Saving…' : 'Save clinic assignments'}</button>
  </section>;
}

function RoleAssignmentsPanel({
  user,
  canAssignRoles,
  canManageUsers,
  accessToken,
}: {
  user: UserResponse;
  canAssignRoles: boolean;
  canManageUsers: boolean;
  accessToken: string;
}) {
  const queryClient = useQueryClient();
  const [showGrantForm, setShowGrantForm] = useState(false);
  const [showPasswordForm, setShowPasswordForm] = useState(false);
  const [passwordChangedFor, setPasswordChangedFor] = useState<string | null>(null);

  const { data: assignments, isLoading } = useQuery({
    queryKey: ['users', user.id, 'role-assignments'],
    queryFn: () => fetchRoleAssignments(user.id as string, accessToken),
    enabled: canAssignRoles,
  });

  const { data: roles } = useQuery({
    queryKey: ['roles'],
    queryFn: () => fetchRoles(accessToken),
    enabled: canAssignRoles && showGrantForm,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['users', user.id, 'role-assignments'] });

  const grant = useMutation({
    mutationFn: (values: GrantRoleValues) =>
      grantRoleAssignment(
        user.id as string,
        {
          roleCode: values.roleCode,
          scopeType: values.scopeType,
          scopeId: values.scopeType === 'GLOBAL' ? undefined : values.scopeId,
          validFrom: values.validFrom ? new Date(values.validFrom).toISOString() : undefined,
          validTo: values.validTo ? new Date(values.validTo).toISOString() : undefined,
        },
        accessToken,
      ),
    onSuccess: () => {
      setShowGrantForm(false);
      invalidate();
    },
  });

  const end = useMutation({
    mutationFn: (assignmentId: string) => endRoleAssignment(user.id as string, assignmentId, accessToken),
    onSuccess: invalidate,
  });

  return (
    <section
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'baseline',
          justifyContent: 'space-between',
          padding: 'var(--space-4) var(--space-4) 0',
        }}
      >
        <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', margin: 0 }}>
          Role assignments — {user.username}
        </h2>
        {canAssignRoles && (
          <button type="button" className="btn btn-secondary" onClick={() => setShowGrantForm((v) => !v)}>
            {showGrantForm ? 'Cancel' : 'Grant a role'}
          </button>
        )}
      </div>

      {showGrantForm && roles && (
        <div style={{ padding: '0 var(--space-4)' }}>
          <GrantRoleForm
            roles={roles}
            isSubmitting={grant.isPending}
            error={grant.error ? errorMessage(grant.error) : null}
            onSubmit={(values) => grant.mutate(values)}
          />
        </div>
      )}

      <div style={{ padding: 'var(--space-4)' }}>
        {canManageUsers && (
          <div style={{ marginBottom: 'var(--space-4)' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setShowPasswordForm((value) => !value)}>
              {showPasswordForm ? 'Cancel' : 'Change password'}
            </button>
            {passwordChangedFor === user.id && <p role="status">Password changed for {user.username}.</p>}
            {showPasswordForm && <SetPasswordForm key={user.id} user={user} accessToken={accessToken} onSuccess={() => {
              setPasswordChangedFor(user.id ?? null);
              setShowPasswordForm(false);
            }} />}
          </div>
        )}
        {!canAssignRoles ? (
          <p style={{ color: 'var(--text-muted)' }}>You don't hold ROLE_ASSIGN — assignments are hidden.</p>
        ) : isLoading ? (
          <FeedbackState loading>Loading…</FeedbackState>
        ) : !assignments || assignments.length === 0 ? (
          <FeedbackState>No role assignments yet.</FeedbackState>
        ) : (
          <TableRegion><table className="table">
            <caption className="sr-only">Role assignments for {user.username}</caption>
            <thead>
              <tr>
                <th>Role</th>
                <th>Scope</th>
                <th>Valid from</th>
                <th>Valid to</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {assignments.map((assignment) => (
                <AssignmentRow key={assignment.id} assignment={assignment} onEnd={() => end.mutate(assignment.id as string)} isEnding={end.isPending} />
              ))}
            </tbody>
          </table></TableRegion>
        )}
      </div>
    </section>
  );
}

function AssignmentRow({
  assignment,
  onEnd,
  isEnding,
}: {
  assignment: RoleAssignmentResponse;
  onEnd: () => void;
  isEnding: boolean;
}) {
  const ended = assignment.revokedAt != null;
  const expired = !ended && assignment.validTo != null && new Date(assignment.validTo) < new Date();
  return (
    <tr>
      <td style={{ fontWeight: 'var(--weight-medium)' }}>{assignment.roleCode}</td>
      <td>
        {assignment.scopeType}
        {assignment.scopeId && <span style={{ color: 'var(--text-muted)' }}> · {assignment.scopeId.slice(0, 8)}</span>}
      </td>
      <td>{formatDate(assignment.validFrom)}</td>
      <td>{assignment.validTo ? formatDate(assignment.validTo) : '—'}</td>
      <td>
        {ended ? (
          <span className="badge badge-neutral">ended {formatDate(assignment.revokedAt)}</span>
        ) : expired ? (
          <span className="badge badge-warning">expired</span>
        ) : (
          <span className="badge badge-success">active</span>
        )}
      </td>
      <td>
        {!ended && (
          <button type="button" className="btn btn-ghost" onClick={onEnd} disabled={isEnding}>
            End
          </button>
        )}
      </td>
    </tr>
  );
}

function CreateUserForm({
  categories, units, isSubmitting,
  error,
  onSubmit,
}: {
  categories: import('../../shared/api/departments').DepartmentCategory[]; units: import('../../shared/api/departments').ClinicUnit[];
  isSubmitting: boolean;
  error: string | null;
  onSubmit: (values: CreateUserValues) => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CreateUserValues>({ resolver: zodResolver(createUserSchema) });

  const [clinicValue, setClinicValue] = useState<ClinicAssignmentUpdate>({ unitIds: [], primaryUnitId: null, hodUnitId: null });
  const onClinicChange = useCallback((value: ClinicAssignmentUpdate) => setClinicValue(value), []);
  return (
    <form onSubmit={handleSubmit(values => onSubmit({ ...values, ...clinicValue }))} noValidate style={formStyle}>
      <div>
        <label htmlFor="new-user-username" style={labelStyle}>
          Username
        </label>
        <input className="clinical-control" id="new-user-username" {...register('username')} style={fieldStyle} />
        {errors.username && <span className="field-error">{errors.username.message}</span>}
      </div>
      <div>
        <label htmlFor="new-user-password" style={labelStyle}>
          Password
        </label>
        <input className="clinical-control" id="new-user-password" type="password" autoComplete="new-password" {...register('password')} style={fieldStyle} />
        {errors.password && <span className="field-error">{errors.password.message}</span>}
        <p style={{ margin: '4px 0 0', fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
          This password remains valid until an admin changes it.
        </p>
      </div>
      <ClinicAssignmentFields categories={categories} units={units} onChange={onClinicChange} />
      {error && <div className="form-error">{error}</div>}
      <button type="submit" className="btn btn-primary" disabled={isSubmitting} style={{ alignSelf: 'flex-start' }}>
        {isSubmitting ? 'Creating…' : 'Create user'}
      </button>
    </form>
  );
}

const setPasswordSchema = z.object({
  password: z.string().min(8, 'Enter a password of at least 8 characters'),
  confirmPassword: z.string().min(1, 'Confirm the password'),
}).refine((values) => values.password === values.confirmPassword, {
  message: 'Passwords do not match', path: ['confirmPassword'],
});

function SetPasswordForm({ user, accessToken, onSuccess }: {
  user: UserResponse;
  accessToken: string;
  onSuccess: () => void;
}) {
  const { register, handleSubmit, reset, formState: { errors } } = useForm<z.infer<typeof setPasswordSchema>>({
    resolver: zodResolver(setPasswordSchema),
  });
  const change = useMutation({
    mutationFn: (values: z.infer<typeof setPasswordSchema>) =>
      setUserPassword(user.id as string, { password: values.password }, accessToken),
    onSuccess: () => { reset(); onSuccess(); },
  });
  return (
    <form onSubmit={handleSubmit((values) => change.mutate(values))} noValidate style={{ ...formStyle, marginTop: 'var(--space-3)' }}>
      <h3 style={{ margin: 0 }}>Change password for {user.username}</h3>
      <div>
        <label htmlFor="set-user-password" style={labelStyle}>New password</label>
        <input className="clinical-control" id="set-user-password" type="password" autoComplete="new-password" {...register('password')} style={fieldStyle} />
        {errors.password && <span className="field-error">{errors.password.message}</span>}
      </div>
      <div>
        <label htmlFor="confirm-user-password" style={labelStyle}>Confirm password</label>
        <input className="clinical-control" id="confirm-user-password" type="password" autoComplete="new-password" {...register('confirmPassword')} style={fieldStyle} />
        {errors.confirmPassword && <span className="field-error">{errors.confirmPassword.message}</span>}
      </div>
      {change.error && <div className="form-error">{errorMessage(change.error)}</div>}
      <button type="submit" className="btn btn-primary" disabled={change.isPending} style={{ alignSelf: 'flex-start' }}>
        {change.isPending ? 'Changing…' : 'Save password'}
      </button>
    </form>
  );
}

function GrantRoleForm({
  roles,
  isSubmitting,
  error,
  onSubmit,
}: {
  roles: { code?: string; name?: string }[];
  isSubmitting: boolean;
  error: string | null;
  onSubmit: (values: GrantRoleValues) => void;
}) {
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<GrantRoleValues>({ resolver: zodResolver(grantRoleSchema), defaultValues: { scopeType: 'GLOBAL' } });
  const scopeType = watch('scopeType');

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate style={{ ...formStyle, marginTop: 0, marginBottom: 'var(--space-4)' }}>
      <div>
        <label htmlFor="grant-role-code" style={labelStyle}>
          Role
        </label>
        <select className="clinical-control" id="grant-role-code" {...register('roleCode')} style={fieldStyle} defaultValue="">
          <option value="" disabled>
            Choose a role
          </option>
          {roles.map((role) => (
            <option key={role.code} value={role.code}>
              {role.name}
            </option>
          ))}
        </select>
        {errors.roleCode && <span className="field-error">{errors.roleCode.message}</span>}
      </div>
      <div>
        <label htmlFor="grant-scope-type" style={labelStyle}>
          Scope
        </label>
        <select className="clinical-control" id="grant-scope-type" {...register('scopeType')} style={fieldStyle}>
          {SCOPE_TYPES.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
      </div>
      {scopeType !== 'GLOBAL' && (
        <div>
          <label htmlFor="grant-scope-id" style={labelStyle}>
            {scopeType.charAt(0) + scopeType.slice(1).toLowerCase()} ID
          </label>
          <input className="clinical-control" id="grant-scope-id" {...register('scopeId')} style={fieldStyle} placeholder="UUID" />
          {errors.scopeId && <span className="field-error">{errors.scopeId.message}</span>}
        </div>
      )}
      <div>
        <label htmlFor="grant-valid-from" style={labelStyle}>
          Valid from (optional — defaults to now)
        </label>
        <input className="clinical-control" id="grant-valid-from" type="date" {...register('validFrom')} style={fieldStyle} />
      </div>
      <div>
        <label htmlFor="grant-valid-to" style={labelStyle}>
          Valid to (optional — a locum's end date)
        </label>
        <input className="clinical-control" id="grant-valid-to" type="date" {...register('validTo')} style={fieldStyle} />
      </div>
      {error && <div className="form-error">{error}</div>}
      <button type="submit" className="btn btn-primary" disabled={isSubmitting} style={{ alignSelf: 'flex-start' }}>
        {isSubmitting ? 'Granting…' : 'Grant role'}
      </button>
    </form>
  );
}

function EmptyState({ message }: { message: string }) {
  return <FeedbackState loading={message.startsWith('Loading') || message.startsWith('Searching')}>{message}</FeedbackState>;
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 409) return 'That username is already taken.';
    if (error.status === 400) return "That role can't be granted — a role assignment cannot be granted to yourself, or a value was invalid.";
  }
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

function formatDate(value: string | undefined | null): string {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en-TZ', { dateStyle: 'medium', timeZone: 'Africa/Dar_es_Salaam' }).format(
    new Date(value),
  );
}

const formStyle: CSSProperties = {
  marginBottom: 'var(--space-4)',
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
  borderRadius: 'var(--radius-lg)',
  padding: 'var(--space-4)',
  maxWidth: 480,
  display: 'flex',
  flexDirection: 'column',
  gap: 'var(--space-3)',
};

const labelStyle: CSSProperties = { display: 'block', fontSize: 'var(--text-sm)' };

const fieldStyle: CSSProperties = {
  width: '100%',
  marginTop: 'var(--space-1)',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
