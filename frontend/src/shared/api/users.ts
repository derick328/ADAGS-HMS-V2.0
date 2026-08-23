import { authFetch, authFetchVoid } from './client';
import type { components } from './types';

export type UserResponse = components['schemas']['UserResponse'];
export type CreateUserRequest = components['schemas']['CreateUserRequest'];
export type RoleResponse = components['schemas']['RoleResponse'];
export type RoleAssignmentResponse = components['schemas']['RoleAssignmentResponse'];
export type GrantRoleAssignmentRequest = components['schemas']['GrantRoleAssignmentRequest'];

export function fetchUsers(accessToken: string): Promise<UserResponse[]> {
  return authFetch('/api/users', accessToken);
}

export function createUser(body: CreateUserRequest, accessToken: string): Promise<UserResponse> {
  return authFetch('/api/users', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

export function fetchRoles(accessToken: string): Promise<RoleResponse[]> {
  return authFetch('/api/roles', accessToken);
}

export function fetchRoleAssignments(userId: string, accessToken: string): Promise<RoleAssignmentResponse[]> {
  return authFetch(`/api/users/${userId}/role-assignments`, accessToken);
}

export function grantRoleAssignment(
  userId: string,
  body: GrantRoleAssignmentRequest,
  accessToken: string,
): Promise<RoleAssignmentResponse> {
  return authFetch(`/api/users/${userId}/role-assignments`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function endRoleAssignment(userId: string, assignmentId: string, accessToken: string): Promise<void> {
  return authFetchVoid(`/api/users/${userId}/role-assignments/${assignmentId}/end`, accessToken, { method: 'POST' });
}
