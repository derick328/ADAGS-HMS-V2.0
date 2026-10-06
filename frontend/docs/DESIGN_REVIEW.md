# Frontend design review

## Scope and findings

Reviewed the route inventory in `src/App.tsx`, all seven workspace shells, page
markup, and existing shared components. Existing local changes were retained.
The primary gaps were disconnected inline styles, undersized native controls,
inconsistent page headings, fixed-width clinical layouts, unwrapped filter rows,
non-scrollable tables, and bare loading/empty messages. Most role shells had no
mobile layout. The login header also shared a horizontal row with the form on
small screens.

## Pages improved

All workspaces inherit the shared responsive shell, green brand identity,
content boundary, focus treatment, typography, button sizing, and status palette.
Native controls and tables were updated in the actual page components.

| Workspace | Pages and workflows |
| --- | --- |
| Doctor | My queue, My patients, patient history, encounter detail, consultation/prescribing/lab requests, hospital catalogue, request history, surgery scheduling, scheduled surgeries, procedure items, profile |
| Reception and billing | Dashboard, patient intake/registration, billing/invoices/payments, expected returns, patient records, price lookup, cashier shift, profile |
| Triage | Patient queue, assessment form, emergency reason dialog, completed assessments, profile |
| Laboratory | Test queue, sample acceptance, result entry, result history/lookup, lab catalogue, stock requests, profile |
| Pharmacy | Dashboard, dispensing queue, dispensing, history/lookup, stock and restocking, item/price proposals, lab stock requests, profile |
| Ward nursing | Dashboard, ward patients, patient care hub (overview, MAR, vitals, wound care, status/bed/discharge), surgery queue/episode/checklist/anaesthesia/items/vitals, shift handover, history, ward bills, surgery bills, profile |
| Administration | Dashboard, pricing, revenue/cancellations, payment bypass waivers, patients, users/roles, theatres, medication/pharmacy expenses, all expense categories, salaries, expense analytics, profile, existing audit/settings placeholders |
| Public | Sign-in layout and application browser title |

## Shared components

- Added `PageHeader`, `WorkspaceBrand`, `TableRegion`, `FormField`, `FeedbackState`, `FormSection`, and `MetricCard`.
- Updated `WorkspacePage` and `QueueShell` to use consistent headings and feedback.
- Updated Button, Input, Select, Tabs, Table, Card, and Modal styling.
- Reused Badge, EmptyState, and QueryState; updated shared tokens and native
  `.btn`, `.badge`, `.table`, `.clinical-control`, and filter styles.
- Lab/pharmacy history, pharmacy stock/item proposals, and the ward care hub use
  keyboard-accessible Tabs while retaining the original unmount-on-switch behavior.
- Triage's IMMEDIATE confirmation now uses Modal focus trapping and short-screen
  scrolling. Its required reason and confirmation action are unchanged.
- FormField connects native controls to labels/errors without replacing refs or
  form handlers. Patient and staff result rows now have keyboard-operable buttons.
- Clinical tables retain all columns and scroll within labelled keyboard-focusable
  regions. Critical and selected row colors remain visible on hover.

No route definitions, permission checks, API clients, clinical schemas, patient
records, or transaction actions were changed by this design pass. Real API data
was used for browser checks; no fixture data was added to product screens.

## Form, login, and dashboard refinement

- Triage has 11 semantic field groups, full-width complaint/history sections,
  three-column vitals at wide widths, styled clinical choices, clear labels,
  validation and keyboard focus. All sections collapse appropriately on mobile.
- Registration has six sections with responsive identity/address grids.
- Labelled native checkboxes/radios now use the existing shared controls across
  workspaces, preserving registration refs, values and change handlers.
- Login uses a bounded branded panel, aligned form, readable type and compact
  mobile layout. Authentication and password visibility behavior are preserved.
- Admin uses existing API financial categories, permission-gated navigation
  cards, clear attention items and retry feedback. No simulated data or charts.
- Surgery episode navigation uses shared accessible tabs and failed requests
  display retry feedback instead of an indefinite loading message.

## Browser coverage and limits

Headless Chrome against the production frontend, at 1440×1000 and 390×1000.
64 distinct URLs were visited at both widths (including two developer galleries
and the unavailable surgery episode). All seven role accounts authenticated
successfully. Static role routes and
available encounter-specific pages were checked. No unintended page overflow or
JavaScript runtime exceptions were recorded. Screenshots of representative login,
admin, patient, triage, registration, catalogue and stock screens were reviewed.
All API writes were blocked in the review browser; no clinical or financial
records were submitted or fabricated. Local review accounts were disabled and
their role assignments expired after review.

The triage form was opened on an existing awaiting-triage encounter. Connected
vital-field labels, danger-sign dialog focus, preselected reasons, Escape dismissal,
and clearing the cancelled danger selection passed at both widths. Registration
was expanded and all six sections checked at both widths. Lab history keyboard tab
navigation passed during the earlier review.

The following populated workflows could not be fully previewed:

- Doctor consultation: no eligible active consultation for the review account;
  opening it can create clinical records, so it was inspected in source.
- Lab sample acceptance and result entry: no local lab orders.
- Pharmacy dispensing form: no local prescription lines.
- Ward patient care hub: no local admissions.
- Ward surgery episode: route attempted using an existing surgery; populated
  episode content was not available to the scoped review account. The retryable
  error state was verified at both widths.

These pages and their subcomponents received source/shared-style review. Existing
admin audit-log and facility-settings placeholders were styled but remain
placeholders; no unsupported feature was invented. Developer component galleries
were source-reviewed, with checkbox and modal previews in this pass and additional
primitive previews in the earlier pass.

## Validation

- TypeScript and Vite production build passed.
- Oxlint passed with 27 existing-pattern warnings and no errors.
- `git diff --check` passed.
- Desktop/mobile overflow and representative screenshot checks passed.
- Existing large-bundle warning remains; no route splitting was introduced.

## Complete route ledger

“Browser” means desktop and mobile layout checked, not a full transaction test.
All product routes below received page or shared-component improvements. Developer
galleries inherit the updated primitives. Parameterized routes were tested only
with existing records where available.

| Route | Page | Review |
| --- | --- | --- |
| `/login` | LoginPage | Browser + source |
| `/dev/components` | ComponentsIndexPage | Source / shared components |
| `/dev/components/button` | ButtonStories | Source / shared components |
| `/dev/components/input` | InputStories | Source / shared components |
| `/dev/components/textarea` | TextareaStories | Source / shared components |
| `/dev/components/select` | SelectStories | Source / shared components |
| `/dev/components/checkbox` | CheckboxStories | Browser + source |
| `/dev/components/radio` | RadioStories | Source / shared components |
| `/dev/components/badge` | BadgeStories | Source / shared components |
| `/dev/components/card` | CardStories | Source / shared components |
| `/dev/components/skeleton` | SkeletonStories | Source / shared components |
| `/dev/components/emptystate` | EmptyStateStories | Source / shared components |
| `/dev/components/toast` | ToastStories | Source / shared components |
| `/dev/components/modal` | ModalStories | Browser + source |
| `/dev/components/tabs` | TabsStories | Source / shared components |
| `/dev/components/table` | TableStories | Source / shared components |
| `/dev/components/tooltip` | TooltipStories | Source / shared components |
| `/dev/shells/queue` | QueueShellStories | Source / shared components |
| `/admin` | AdminDashboard | Browser + source |
| `/admin/pricing` | PricingPage | Browser + source |
| `/admin/revenue` | RevenuePage | Browser + source |
| `/admin/payment-bypass-waivers` | PaymentBypassWaiverPage | Browser + source |
| `/admin/patients` | PatientsPage | Browser + source |
| `/admin/users` | UsersPage | Browser + source |
| `/admin/theatres` | TheatresPage | Browser + source |
| `/admin/expenses/medication-pharmacy` | MedicationPharmacyPage | Browser + source |
| `/admin/expenses/medications` | ExpensesCategoryPage | Browser + source |
| `/admin/expenses/salaries` | SalariesPage | Browser + source |
| `/admin/expenses/utilities` | ExpensesCategoryPage | Browser + source |
| `/admin/expenses/maintenance` | ExpensesCategoryPage | Browser + source |
| `/admin/expenses/equipment` | ExpensesCategoryPage | Browser + source |
| `/admin/expenses/other` | ExpensesCategoryPage | Browser + source |
| `/admin/expense-analytics` | ExpenseAnalyticsPage | Browser + source |
| `/admin/audit-log` | ComingSoon | Browser + source; existing placeholder |
| `/admin/facility-settings` | ComingSoon | Browser + source; existing placeholder |
| `/admin/profile` | ProfilePage | Browser + source |
| `/receptionist` | ReceptionistDashboard | Browser + source |
| `/receptionist/patient-intake` | PatientIntakePage | Browser + source |
| `/receptionist/billing` | ReceptionistBillingPage | Browser + source |
| `/receptionist/expected-returns` | ExpectedReturnsPage | Browser + source |
| `/receptionist/patient-records` | PatientRecordsPage | Browser + source |
| `/receptionist/price-lookup` | PriceLookupPage | Browser + source |
| `/receptionist/shift` | CashierShiftPage | Browser + source |
| `/receptionist/profile` | ReceptionistProfilePage | Browser + source |
| `/triage` | PatientQueuePage | Browser + source |
| `/triage/assess/:encounterId` | TriageAssessmentFormPage | Browser + source |
| `/triage/completed-assessments` | CompletedAssessmentsPage | Browser + source |
| `/triage/profile` | TriageProfilePage | Browser + source |
| `/doctor` | MyQueuePage | Browser + source |
| `/doctor/consultation/:encounterId` | ConsultationPage | Source / shared components |
| `/doctor/schedule-surgery/:encounterId` | ScheduleSurgeryPage | Browser + source |
| `/doctor/scheduled-surgeries` | MyScheduledSurgeriesPage | Browser + source |
| `/doctor/surgery/:surgeryOrderId/items` | DoctorSurgeryItemsPage | Browser + source |
| `/doctor/patients` | MyPatientsPage | Browser + source |
| `/doctor/patients/:patientId` | PatientPage | Browser + source |
| `/doctor/patients/:patientId/encounters/:encounterId` | EncounterDetailPage | Browser + source |
| `/doctor/catalogue` | HospitalCataloguePage | Browser + source |
| `/doctor/request-history` | RequestHistoryPage | Browser + source |
| `/doctor/profile` | DoctorProfilePage | Browser + source |
| `/lab` | TestQueuePage | Browser + source |
| `/lab/accept/:labOrderId` | AcceptSamplePage | Source / shared components |
| `/lab/result-entry/:labOrderId` | ResultEntryPage | Source / shared components |
| `/lab/history` | MyHistoryPage | Browser + source |
| `/lab/catalogue` | LabCataloguePage | Browser + source |
| `/lab/stock-requests` | StockRequestsPage | Browser + source |
| `/lab/profile` | LabProfilePage | Browser + source |
| `/pharmacy` | PharmacyDashboardPage | Browser + source |
| `/pharmacy/dispensing` | DispensingQueue | Browser + source |
| `/pharmacy/dispense/:prescriptionLineId` | DispensePage | Source / shared components |
| `/pharmacy/history` | PharmacyHistoryPage | Browser + source |
| `/pharmacy/stock` | PharmacyStockPage | Browser + source |
| `/pharmacy/items` | PharmacyItemsPage | Browser + source |
| `/pharmacy/lab-stock-requests` | PharmacyLabStockRequestsPage | Browser + source |
| `/pharmacy/profile` | PharmacyProfilePage | Browser + source |
| `/ward-nurse` | WardDashboardPage | Browser + source |
| `/ward-nurse/patients` | WardPatientsPage | Browser + source |
| `/ward-nurse/patients/:admissionId` | PatientCareHubPage | Source / shared components |
| `/ward-nurse/surgery` | SurgeryQueuePage | Browser + source |
| `/ward-nurse/surgery/:surgeryOrderId` | SurgeryEpisodePage | Browser attempted; populated episode unavailable; source reviewed |
| `/ward-nurse/handover` | ShiftHandoverPage | Browser + source |
| `/ward-nurse/history` | WardMyHistoryPage | Browser + source |
| `/ward-nurse/ward-bills` | WardBillsPage | Browser + source |
| `/ward-nurse/surgery-bills` | SurgeryBillsPage | Browser + source |
| `/ward-nurse/profile` | WardProfilePage | Browser + source |
