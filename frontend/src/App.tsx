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
import { ConsultationPage } from './workspaces/doctor/ConsultationPage';
import { DoctorShell } from './workspaces/doctor/DoctorShell';
import { HospitalCataloguePage } from './workspaces/doctor/HospitalCataloguePage';
import { MyPatientsPage } from './workspaces/doctor/MyPatientsPage';
import { MyQueuePage } from './workspaces/doctor/MyQueuePage';
import { ProfilePage as DoctorProfilePage } from './workspaces/triage/ProfilePage';
import { RequestHistoryPage } from './workspaces/doctor/RequestHistoryPage';
import { AcceptSamplePage } from './workspaces/lab/AcceptSamplePage';
import { LabCataloguePage } from './workspaces/lab/LabCataloguePage';
import { LabTechShell } from './workspaces/lab/LabTechShell';
import { MyHistoryPage } from './workspaces/lab/MyHistoryPage';
import { ResultEntryPage } from './workspaces/lab/ResultEntryPage';
import { StockRequestsPage } from './workspaces/lab/StockRequestsPage';
import { TestQueuePage } from './workspaces/lab/TestQueuePage';
import { ProfilePage as LabProfilePage } from './workspaces/triage/ProfilePage';
import { DashboardPage as PharmacyDashboardPage } from './workspaces/pharmacy/DashboardPage';
import { DispensePage } from './workspaces/pharmacy/DispensePage';
import { HistoryPage as PharmacyHistoryPage } from './workspaces/pharmacy/HistoryPage';
import { LabStockRequestsPage as PharmacyLabStockRequestsPage } from './workspaces/pharmacy/LabStockRequestsPage';
import { PharmacyItemsPage } from './workspaces/pharmacy/PharmacyItemsPage';
import { PharmacyShell } from './workspaces/pharmacy/PharmacyShell';
import { StockPage as PharmacyStockPage } from './workspaces/pharmacy/StockPage';
import { ProfilePage as PharmacyProfilePage } from './workspaces/triage/ProfilePage';
import { DashboardPage as WardDashboardPage } from './workspaces/ward/DashboardPage';
import { MyHistoryPage as WardMyHistoryPage } from './workspaces/ward/MyHistoryPage';
import { PatientCareHubPage } from './workspaces/ward/PatientCareHubPage';
import { ShiftHandoverPage } from './workspaces/ward/ShiftHandoverPage';
import { SurgeryBillsPage } from './workspaces/ward/SurgeryBillsPage';
import { SurgeryEpisodePage } from './workspaces/ward/SurgeryEpisodePage';
import { SurgeryQueuePage } from './workspaces/ward/SurgeryQueuePage';
import { WardBillsPage } from './workspaces/ward/WardBillsPage';
import { WardNurseShell } from './workspaces/ward/WardNurseShell';
import { WardPatientsPage } from './workspaces/ward/WardPatientsPage';
import { ProfilePage as WardProfilePage } from './workspaces/triage/ProfilePage';

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
      <Route
        path="/doctor"
        element={
          <RequireAuth>
            <DoctorShell />
          </RequireAuth>
        }
      >
        <Route index element={<MyQueuePage />} />
        <Route path="consultation/:encounterId" element={<ConsultationPage />} />
        <Route path="patients" element={<MyPatientsPage />} />
        <Route path="catalogue" element={<HospitalCataloguePage />} />
        <Route path="request-history" element={<RequestHistoryPage />} />
        <Route path="profile" element={<DoctorProfilePage />} />
      </Route>
      <Route
        path="/lab"
        element={
          <RequireAuth>
            <LabTechShell />
          </RequireAuth>
        }
      >
        <Route index element={<TestQueuePage />} />
        <Route path="accept/:labOrderId" element={<AcceptSamplePage />} />
        <Route path="result-entry/:labOrderId" element={<ResultEntryPage />} />
        <Route path="history" element={<MyHistoryPage />} />
        <Route path="catalogue" element={<LabCataloguePage />} />
        <Route path="stock-requests" element={<StockRequestsPage />} />
        <Route path="profile" element={<LabProfilePage />} />
      </Route>
      <Route
        path="/pharmacy"
        element={
          <RequireAuth>
            <PharmacyShell />
          </RequireAuth>
        }
      >
        <Route index element={<PharmacyDashboardPage />} />
        <Route path="dispense/:prescriptionLineId" element={<DispensePage />} />
        <Route path="history" element={<PharmacyHistoryPage />} />
        <Route path="stock" element={<PharmacyStockPage />} />
        <Route path="items" element={<PharmacyItemsPage />} />
        <Route path="lab-stock-requests" element={<PharmacyLabStockRequestsPage />} />
        <Route path="profile" element={<PharmacyProfilePage />} />
      </Route>
      <Route
        path="/ward-nurse"
        element={
          <RequireAuth>
            <WardNurseShell />
          </RequireAuth>
        }
      >
        <Route index element={<WardDashboardPage />} />
        <Route path="patients" element={<WardPatientsPage />} />
        <Route path="patients/:admissionId" element={<PatientCareHubPage />} />
        <Route path="surgery" element={<SurgeryQueuePage />} />
        <Route path="surgery/:surgeryOrderId" element={<SurgeryEpisodePage />} />
        <Route path="handover" element={<ShiftHandoverPage />} />
        <Route path="history" element={<WardMyHistoryPage />} />
        <Route path="ward-bills" element={<WardBillsPage />} />
        <Route path="surgery-bills" element={<SurgeryBillsPage />} />
        <Route path="profile" element={<WardProfilePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/admin" replace />} />
    </Routes>
  );
}
