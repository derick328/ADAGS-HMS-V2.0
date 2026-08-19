package com.adags.modules.reporting;

import com.adags.modules.billing.application.ChargePostingCommand;
import com.adags.modules.billing.application.ChargePostingResult;
import com.adags.modules.billing.application.ChargePostingService;
import com.adags.modules.billing.application.OpenCashierShiftService;
import com.adags.modules.billing.application.PostPaymentCommand;
import com.adags.modules.billing.application.PostPaymentService;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PaymentMethod;
import com.adags.modules.reporting.application.DepartmentContribution;
import com.adags.modules.reporting.application.DepartmentContributionService;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 18-admin.md / 02-billing-architecture.md §13: revenue (cash collected
// + insurance billed) minus pharmacy direct cost, by department.
// Consumables and attributable-salary costs are not captured (no
// department link on lab_stock_request or staff) and so are excluded —
// contributionMinor here is an upper bound, proven by comparing it
// against the same numbers computed by hand from the fixture.
class DepartmentContributionServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private DepartmentContributionService departmentContributionService;
    @Autowired
    private ChargePostingService chargePostingService;
    @Autowired
    private PostPaymentService postPaymentService;
    @Autowired
    private OpenCashierShiftService openCashierShiftService;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE dispense_line, dispense, prescription_line, prescription_order,
                                 payment_allocation, payment, cashier_shift, service_authorization, invoice_line,
                                 invoice, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void revenueMinusPharmacyCostIsAttributedToTheEncountersDepartment() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID[] encounterAndDepartment = insertFixtureEncounter(patientId);
        UUID encounterId = encounterAndDepartment[0];
        UUID departmentId = encounterAndDepartment[1];

        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        ChargePostingResult posted = chargePostingService.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null,
                "CONSULT_GENERAL", 1, "TriageCompleted", UUID.randomUUID(), admin));
        postPaymentService.recordPayment(new PostPaymentCommand(patientId, PaymentMethod.CASH, null,
                "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(posted.invoiceLineId(), 1_000_000)), admin));

        insertFixtureDispense(encounterId, admin, 10, 5_000); // 50,000 pharmacy cost

        List<DepartmentContribution> lines = departmentContributionService.generate(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), BOOTSTRAP_FACILITY_ID);

        DepartmentContribution line = lines.stream().filter(l -> l.departmentId().equals(departmentId)).findFirst().orElseThrow();
        assertThat(line.cashCollectedMinor()).isEqualTo(1_000_000);
        assertThat(line.insuranceBilledMinor()).isEqualTo(0);
        assertThat(line.pharmacyCostMinor()).isEqualTo(50_000);
        assertThat(line.contributionMinor()).isEqualTo(950_000);
    }

    @Test
    void departmentsWithNoActivityInThePeriodAreOmitted() {
        List<DepartmentContribution> lines = departmentContributionService.generate(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), BOOTSTRAP_FACILITY_ID);

        assertThat(lines).isEmpty();
    }

    private void insertFixtureDispense(UUID encounterId, UUID admin, int quantity, long unitCostMinor) {
        UUID prescriptionOrderId = jdbcTemplate.queryForObject("""
                        INSERT INTO prescription_order (encounter_id, patient_id, facility_id, prescribed_by)
                        VALUES (:encounterId, (SELECT patient_id FROM encounter WHERE id = :encounterId),
                                :facilityId, :prescribedBy)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("encounterId", encounterId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("prescribedBy", admin),
                UUID.class);
        UUID prescriptionLineId = jdbcTemplate.queryForObject("""
                        INSERT INTO prescription_line (prescription_order_id, item_id, quantity_prescribed,
                                                        dosage_instructions, invoice_line_id, quantity_dispensed)
                        VALUES (:orderId, :itemId, :quantity, 'As directed', :invoiceLineId, :quantity)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("orderId", prescriptionOrderId)
                        .addValue("itemId", UUID.randomUUID())
                        .addValue("quantity", quantity)
                        .addValue("invoiceLineId", UUID.randomUUID()),
                UUID.class);
        UUID dispenseId = jdbcTemplate.queryForObject("""
                        INSERT INTO dispense (prescription_line_id, dispensed_by, idempotency_key, quantity_dispensed)
                        VALUES (:lineId, :dispensedBy, :idemKey, :quantity)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("lineId", prescriptionLineId)
                        .addValue("dispensedBy", admin)
                        .addValue("idemKey", "idem-" + UUID.randomUUID())
                        .addValue("quantity", quantity),
                UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO dispense_line (dispense_id, item_id, batch_id, quantity, unit_cost_amount_minor)
                        VALUES (:dispenseId, :itemId, :batchId, :quantity, :unitCostMinor)
                        """,
                new MapSqlParameterSource()
                        .addValue("dispenseId", dispenseId)
                        .addValue("itemId", UUID.randomUUID())
                        .addValue("batchId", UUID.randomUUID())
                        .addValue("quantity", quantity)
                        .addValue("unitCostMinor", unitCostMinor));
    }

    private UUID insertFixturePatient() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
    }

    private UUID[] insertFixtureEncounter(UUID patientId) {
        UUID departmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, code, name)
                        VALUES (:facilityId, :code, 'Test Department')
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);

        UUID encounterId = jdbcTemplate.queryForObject("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', 'AWAITING_PAYMENT', 'CASH',
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", departmentId),
                UUID.class);
        return new UUID[] {encounterId, departmentId};
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
