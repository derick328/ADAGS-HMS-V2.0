import { Navigate, Route, Routes } from 'react-router-dom';
import { LoginPage } from './pages/LoginPage';
import { RequireAuth } from './shared/auth/RequireAuth';
import { AdminDashboard } from './workspaces/admin/AdminDashboard';
import { AdminShell } from './workspaces/admin/AdminShell';
import { ComingSoon } from './workspaces/admin/ComingSoon';
import { ExpenseAnalyticsPage } from './workspaces/admin/ExpenseAnalyticsPage';
import { ExpensesCategoryPage } from './workspaces/admin/ExpensesCategoryPage';
import { PatientsPage } from './workspaces/admin/PatientsPage';
import { PaymentBypassWaiverPage } from './workspaces/admin/PaymentBypassWaiverPage';
import { PricingPage } from './workspaces/admin/PricingPage';
import { ProfilePage } from './workspaces/admin/ProfilePage';
import { RevenuePage } from './workspaces/admin/RevenuePage';
import { UsersPage } from './workspaces/admin/UsersPage';
import { BillingPage as ReceptionistBillingPage } from './workspaces/receptionist/BillingPage';
import { CashierShiftPage } from './workspaces/receptionist/CashierShiftPage';
import { ExpectedReturnsPage } from './workspaces/receptionist/ExpectedReturnsPage';
import { PatientIntakePage } from './workspaces/receptionist/PatientIntakePage';
import { PatientRecordsPage } from './workspaces/receptionist/PatientRecordsPage';
import { ProfilePage as ReceptionistProfilePage } from './workspaces/receptionist/ProfilePage';
import { ReceptionistDashboard } from './workspaces/receptionist/ReceptionistDashboard';
import { ReceptionistShell } from './workspaces/receptionist/ReceptionistShell';
import { CompletedAssessmentsPage } from './workspaces/triage/CompletedAssessmentsPage';
import { PatientQueuePage } from './workspaces/triage/PatientQueuePage';
import { ProfilePage as TriageProfilePage } from './workspaces/triage/ProfilePage';
import { TriageAssessmentFormPage } from './workspaces/triage/TriageAssessmentFormPage';
import { TriageNurseShell } from './workspaces/triage/TriageNurseShell';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/admin"
        element={
          <RequireAuth>
            <AdminShell />
          </RequireAuth>
        }
      >
        <Route index element={<AdminDashboard />} />
        <Route path="pricing" element={<PricingPage />} />
        <Route path="revenue" element={<RevenuePage />} />
        <Route path="payment-bypass-waivers" element={<PaymentBypassWaiverPage />} />
        <Route path="patients" element={<PatientsPage />} />
        <Route path="users" element={<UsersPage />} />
        <Route
          path="expenses/medication-pharmacy"
          element={<ExpensesCategoryPage category="Medication & pharmacy" />}
        />
        <Route path="expenses/medications" element={<ExpensesCategoryPage category="Medications" />} />
        <Route path="expenses/salaries" element={<ExpensesCategoryPage category="Salaries" />} />
        <Route path="expenses/utilities" element={<ExpensesCategoryPage category="Utilities" />} />
        <Route path="expenses/maintenance" element={<ExpensesCategoryPage category="Maintenance" />} />
        <Route path="expenses/equipment" element={<ExpensesCategoryPage category="Equipment" />} />
        <Route path="expenses/other" element={<ExpensesCategoryPage category="Other" />} />
        <Route path="expense-analytics" element={<ExpenseAnalyticsPage />} />
        <Route path="audit-log" element={<ComingSoon title="Audit log" />} />
        <Route path="facility-settings" element={<ComingSoon title="Facility & settings" />} />
        <Route path="profile" element={<ProfilePage />} />
      </Route>
      <Route
        path="/receptionist"
        element={
          <RequireAuth>
            <ReceptionistShell />
          </RequireAuth>
        }
      >
        <Route index element={<ReceptionistDashboard />} />
        <Route path="patient-intake" element={<PatientIntakePage />} />
        <Route path="billing" element={<ReceptionistBillingPage />} />
        <Route path="expected-returns" element={<ExpectedReturnsPage />} />
        <Route path="patient-records" element={<PatientRecordsPage />} />
        <Route path="shift" element={<CashierShiftPage />} />
        <Route path="profile" element={<ReceptionistProfilePage />} />
      </Route>
      <Route
        path="/triage"
        element={
          <RequireAuth>
            <TriageNurseShell />
          </RequireAuth>
        }
      >
        <Route index element={<PatientQueuePage />} />
        <Route path="assess/:encounterId" element={<TriageAssessmentFormPage />} />
        <Route path="completed-assessments" element={<CompletedAssessmentsPage />} />
        <Route path="profile" element={<TriageProfilePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/admin" replace />} />
    </Routes>
  );
}
