import { Navigate, Route, Routes } from 'react-router-dom';
import { LoginPage } from './pages/LoginPage';
import { RequireAuth } from './shared/auth/RequireAuth';
import { AdminDashboard } from './workspaces/admin/AdminDashboard';
import { AdminShell } from './workspaces/admin/AdminShell';
import { ComingSoon } from './workspaces/admin/ComingSoon';

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
        <Route path="pricing" element={<ComingSoon title="Pricing / price proposals" />} />
        <Route path="revenue" element={<ComingSoon title="Revenue / invoice cancellations" />} />
        <Route path="patients" element={<ComingSoon title="Patients" />} />
        <Route path="users" element={<ComingSoon title="Users / add nurse / staff" />} />
        <Route path="expenses/medication-pharmacy" element={<ComingSoon title="Medication & pharmacy" />} />
        <Route path="expenses/medications" element={<ComingSoon title="Medications" />} />
        <Route path="expenses/salaries" element={<ComingSoon title="Salaries" />} />
        <Route path="expenses/utilities" element={<ComingSoon title="Utilities" />} />
        <Route path="expenses/maintenance" element={<ComingSoon title="Maintenance" />} />
        <Route path="expenses/equipment" element={<ComingSoon title="Equipment" />} />
        <Route path="expenses/other" element={<ComingSoon title="Other" />} />
        <Route path="expense-analytics" element={<ComingSoon title="Expense analytics" />} />
        <Route path="audit-log" element={<ComingSoon title="Audit log" />} />
        <Route path="facility-settings" element={<ComingSoon title="Facility & settings" />} />
        <Route path="profile" element={<ComingSoon title="Profile" />} />
      </Route>
      <Route path="*" element={<Navigate to="/admin" replace />} />
    </Routes>
  );
}
