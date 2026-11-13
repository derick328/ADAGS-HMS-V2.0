// Keep login navigation and authenticated page loads on the same workspace.
export function landingRouteFor(roleCodes: string[], permissions: string[]): string {
  const roles = new Set(roleCodes);
  const perms = new Set(permissions);

  if (roles.has('LAB_TECHNICIAN')) return '/lab';
  if (roles.has('PHARMACIST')) return '/pharmacy';
  if (roles.has('WARD_NURSE') || roles.has('NURSE')) return '/ward-nurse';
  if (roles.has('CLINICAL_OFFICER') || roles.has('DOCTOR') || roles.has('SPECIALIST_DOCTOR')) return '/doctor';
  if (roles.has('TRIAGE_NURSE')) return '/triage';
  if (roles.has('RECEPTIONIST')) return '/receptionist';
  if (roles.has('ADMIN')) return '/admin';
  if (roles.has('ACCOUNTANT') && perms.has('PACKAGING_APPROVE')) return '/admin/packaging-approvals';

  // Custom roles may carry workspace permissions without a standard role code.
  if (perms.has('LAB_QUEUE_READ') || perms.has('SPECIMEN_ACCEPT') || perms.has('LAB_RESULT_ENTER')) return '/lab';
  if (perms.has('DISPENSE_CREATE') || perms.has('PRESCRIPTION_QUEUE_READ')) return '/pharmacy';
  if (perms.has('WARD_PATIENT_READ') || perms.has('MEDICATION_ADMINISTER')) return '/ward-nurse';
  if (perms.has('CONSULTATION_CREATE') || perms.has('PRESCRIPTION_CREATE')) return '/doctor';
  if (perms.has('VITALS_CREATE') && !perms.has('WARD_PATIENT_READ')) return '/triage';
  if (perms.has('PAYMENT_CREATE') || perms.has('PATIENT_REGISTER')) return '/receptionist';
  if (perms.has('USER_MANAGE')) return '/admin';
  if (perms.has('PACKAGING_APPROVE')) return '/admin/packaging-approvals';

  return '/access-pending';
}
