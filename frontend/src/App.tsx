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
        <Route path="users" element={<ComingSoon title="Users & roles" />} />
        <Route path="facilities" element={<ComingSoon title="Facilities" />} />
        <Route path="audit-log" element={<ComingSoon title="Audit log" />} />
      </Route>
      <Route path="*" element={<Navigate to="/admin" replace />} />
    </Routes>
  );
}
