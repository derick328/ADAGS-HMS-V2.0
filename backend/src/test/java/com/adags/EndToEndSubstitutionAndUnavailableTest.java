package com.adags;

import com.adags.modules.billing.application.OpenCashierShiftService;
import com.adags.modules.billing.application.PostPaymentCommand;
import com.adags.modules.billing.application.PostPaymentService;
import com.adags.modules.billing.domain.PaymentMethod;
import com.adags.modules.encounters.application.OpenEncounterCommand;
import com.adags.modules.encounters.application.OpenEncounterService;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.inventory.application.ActivateItemService;
import com.adags.modules.inventory.application.ClassifyItemCommand;
import com.adags.modules.inventory.application.ClassifyItemService;
import com.adags.modules.inventory.application.CreateItemCommand;
import com.adags.modules.inventory.application.CreateItemService;
import com.adags.modules.inventory.application.RestockCommand;
import com.adags.modules.inventory.application.RestockService;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.RegulatorySchedule;
import com.adags.modules.patients.application.RegisterPatientCommand;
import com.adags.modules.patients.application.RegisterPatientService;
import com.adags.modules.patients.domain.NextOfKin;
import com.adags.modules.patients.domain.Patient;
import com.adags.modules.pharmacy.application.DispenseCommand;
import com.adags.modules.pharmacy.application.DispenseLineInput;
import com.adags.modules.pharmacy.application.DispenseService;
import com.adags.modules.pharmacy.application.MarkUnavailableService;
import com.adags.modules.pharmacy.application.PlacePrescriptionCommand;
import com.adags.modules.pharmacy.application.PlacePrescriptionService;
import com.adags.modules.pharmacy.application.ProposeSubstitutionCommand;
import com.adags.modules.pharmacy.application.ProposeSubstitutionService;
import com.adags.modules.pharmacy.application.ReviewSubstitutionCommand;
import com.adags.modules.pharmacy.application.ReviewSubstitutionService;
import com.adags.modules.pharmacy.application.SubstitutionReviewResult;
import com.adags.modules.pharmacy.domain.ItemNotDispensableException;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineNotOpenException;
import com.adags.modules.pharmacy.domain.PrescriptionLineStatus;
import com.adags.modules.pharmacy.domain.SubstitutionAlreadyReviewedException;
import com.adags.modules.pharmacy.domain.SubstitutionNotAllowedException;
import com.adags.modules.pharmacy.domain.SubstitutionProposal;
import com.adags.modules.pharmacy.domain.SubstitutionSelfReviewNotAllowedException;
import com.adags.modules.pharmacy.domain.SubstitutionStatus;
import com.adags.shared.kernel.Money;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 5, pharmacy remaining pieces item 1: out-of-stock and
// substitution. docs/spec/12-pharmacist.md §5.
class EndToEndSubstitutionAndUnavailableTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private RegisterPatientService registerPatientService;
    @Autowired
    private OpenEncounterService openEncounterService;
    @Autowired
    private OpenCashierShiftService openCashierShiftService;
    @Autowired
    private PostPaymentService postPaymentService;
    @Autowired
    private CreateItemService createItemService;
    @Autowired
    private ActivateItemService activateItemService;
    @Autowired
    private ClassifyItemService classifyItemService;
    @Autowired
    private RestockService restockService;
    @Autowired
    private PlacePrescriptionService placePrescriptionService;
    @Autowired
    private DispenseService dispenseService;
    @Autowired
    private MarkUnavailableService markUnavailableService;
    @Autowired
    private ProposeSubstitutionService proposeSubstitutionService;
    @Autowired
    private ReviewSubstitutionService reviewSubstitutionService;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE substitution_proposal, dispense_line, dispense, prescription_line, prescription_order,
                                 stock_balance, stock_movement, stock_batch, item, store, supplier,
                                 payment_allocation, payment, cashier_shift, service_authorization, invoice_line,
                                 invoice, encounter_event, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM role_assignment WHERE role_id = (SELECT id FROM role WHERE code = 'DOCTOR')",
                new MapSqlParameterSource());
    }

    @Test
    void markingAnUnpaidLineUnavailableVoidsItsChargeAutomatically() {
        UUID admin = bootstrapAdminId();
        PrescriptionLine line = prescribe("AMX500", 30, false, admin);

        PrescriptionLine updated = markUnavailableService.markUnavailable(line.id(), "out of stock, supplier delayed", admin);

        assertThat(updated.status()).isEqualTo(PrescriptionLineStatus.UNAVAILABLE);
        assertThat(updated.statusReason()).isEqualTo("out of stock, supplier delayed");
        String invoiceState = jdbcTemplate.queryForObject("""
                        SELECT i.state FROM invoice i JOIN invoice_line il ON il.invoice_id = i.id WHERE il.id = :id
                        """,
                new MapSqlParameterSource("id", line.invoiceLineId()), String.class);
        assertThat(invoiceState).isEqualTo("VOID");
    }

    @Test
    void markingAnAlreadyPaidLineUnavailableLeavesTheChargeForTheAccountant() {
        UUID admin = bootstrapAdminId();
        PrescriptionLine line = prescribe("AMX500", 30, false, admin);
        payLine(line.invoiceLineId(), patientIdFor(line), admin);

        PrescriptionLine updated = markUnavailableService.markUnavailable(line.id(), "batch recalled", admin);

        assertThat(updated.status()).isEqualTo(PrescriptionLineStatus.UNAVAILABLE);
        String invoiceState = jdbcTemplate.queryForObject("""
                        SELECT i.state FROM invoice i JOIN invoice_line il ON il.invoice_id = i.id WHERE il.id = :id
                        """,
                new MapSqlParameterSource("id", line.invoiceLineId()), String.class);
        assertThat(invoiceState).as("left settled — refund is an accountant-led path this codebase doesn't automate")
                .isEqualTo("PATIENT_PORTION_SETTLED");
    }

    @Test
    void cannotMarkAnAlreadyCompletedLineUnavailable() {
        UUID admin = bootstrapAdminId();
        PrescriptionLine line = prescribe("AMX500", 10, false, admin);
        payLine(line.invoiceLineId(), patientIdFor(line), admin);
        UUID itemId = line.itemId();
        UUID batchId = onlyBatchIdFor(itemId);
        dispenseService.dispense(new DispenseCommand(line.id(), List.of(new DispenseLineInput(batchId, 10, null)),
                admin, "idem-" + UUID.randomUUID(), null, null));

        assertThatThrownBy(() -> markUnavailableService.markUnavailable(line.id(), "too late", admin))
                .isInstanceOf(PrescriptionLineNotOpenException.class);
    }

    @Test
    void proposingASubstitutionIsRejectedWhenThePrescriberDidNotAllowIt() {
        UUID admin = bootstrapAdminId();
        PrescriptionLine line = prescribe("AMX500", 30, false, admin);
        UUID alternative = createFixtureItem("AMX250", admin);

        assertThatThrownBy(() -> proposeSubstitutionService.propose(
                new ProposeSubstitutionCommand(line.id(), alternative, "smaller pack available", admin)))
                .isInstanceOf(SubstitutionNotAllowedException.class);
    }

    @Test
    void proposingASubstitutionToAControlledItemIsRejected() {
        UUID admin = bootstrapAdminId();
        PrescriptionLine line = prescribe("AMX500", 30, true, admin);
        Item controlled = createItemService.create(new CreateItemCommand("MOR10", "Morphine sulfate 10mg tablet",
                "OPIOID", "TABLET", "10mg", "TABLET", 0, admin));
        classifyItemService.classify(new ClassifyItemCommand(controlled.id(), RegulatorySchedule.CONTROLLED,
                "narcotic list", admin));

        assertThatThrownBy(() -> proposeSubstitutionService.propose(
                new ProposeSubstitutionCommand(line.id(), controlled.id(), "trying to substitute a narcotic", admin)))
                .isInstanceOf(ItemNotDispensableException.class);
    }

    @Test
    void theProposerCannotReviewTheirOwnProposal() {
        UUID admin = bootstrapAdminId();
        PrescriptionLine line = prescribe("AMX500", 30, true, admin);
        UUID alternative = createFixtureItem("AMX250", admin);
        SubstitutionProposal proposal = proposeSubstitutionService.propose(
                new ProposeSubstitutionCommand(line.id(), alternative, "smaller pack available", admin));

        assertThatThrownBy(() -> reviewSubstitutionService.review(
                new ReviewSubstitutionCommand(proposal.id(), SubstitutionStatus.APPROVED, admin, null)))
                .isInstanceOf(SubstitutionSelfReviewNotAllowedException.class);
    }

    @Test
    void rejectingASubstitutionLeavesTheOriginalLineUntouched() {
        UUID admin = bootstrapAdminId();
        UUID doctor = insertFixtureDoctor();
        PrescriptionLine line = prescribe("AMX500", 30, true, admin);
        UUID alternative = createFixtureItem("AMX250", admin);
        SubstitutionProposal proposal = proposeSubstitutionService.propose(
                new ProposeSubstitutionCommand(line.id(), alternative, "smaller pack available", admin));

        SubstitutionReviewResult result = reviewSubstitutionService.review(
                new ReviewSubstitutionCommand(proposal.id(), SubstitutionStatus.REJECTED, doctor, "keep original brand"));

        assertThat(result.proposal().status()).isEqualTo(SubstitutionStatus.REJECTED);
        assertThat(result.cancelledLine()).isNull();
        assertThat(result.replacementLine()).isNull();

        PrescriptionLine unchanged = prescriptionLineFindById(line.id());
        assertThat(unchanged.status()).isEqualTo(PrescriptionLineStatus.AWAITING_DISPENSE);
    }

    @Test
    void reviewingAnAlreadyReviewedProposalIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID doctor = insertFixtureDoctor();
        PrescriptionLine line = prescribe("AMX500", 30, true, admin);
        UUID alternative = createFixtureItem("AMX250", admin);
        SubstitutionProposal proposal = proposeSubstitutionService.propose(
                new ProposeSubstitutionCommand(line.id(), alternative, "smaller pack available", admin));
        reviewSubstitutionService.review(
                new ReviewSubstitutionCommand(proposal.id(), SubstitutionStatus.REJECTED, doctor, "no"));

        assertThatThrownBy(() -> reviewSubstitutionService.review(
                new ReviewSubstitutionCommand(proposal.id(), SubstitutionStatus.APPROVED, doctor, null)))
                .isInstanceOf(SubstitutionAlreadyReviewedException.class);
    }

    @Test
    void approvingASubstitutionCancelsTheOriginalVoidsItsUnpaidChargeAndPostsAReplacement() {
        UUID admin = bootstrapAdminId();
        UUID doctor = insertFixtureDoctor();
        PrescriptionLine line = prescribe("AMX500", 30, true, admin);
        UUID alternative = createFixtureItem("AMX250", admin);
        insertFixtureServiceItemAndPrice("AMX250", 3_000, admin);
        SubstitutionProposal proposal = proposeSubstitutionService.propose(
                new ProposeSubstitutionCommand(line.id(), alternative, "smaller pack available", admin));

        SubstitutionReviewResult result = reviewSubstitutionService.review(
                new ReviewSubstitutionCommand(proposal.id(), SubstitutionStatus.APPROVED, doctor, "fine by me"));

        assertThat(result.proposal().status()).isEqualTo(SubstitutionStatus.APPROVED);
        assertThat(result.cancelledLine().status()).isEqualTo(PrescriptionLineStatus.CANCELLED);
        assertThat(result.replacementLine().itemId()).isEqualTo(alternative);
        assertThat(result.replacementLine().quantityPrescribed()).isEqualTo(30);
        assertThat(result.replacementLine().invoiceLineId()).isNotEqualTo(line.invoiceLineId());

        String originalInvoiceState = jdbcTemplate.queryForObject("""
                        SELECT i.state FROM invoice i JOIN invoice_line il ON il.invoice_id = i.id WHERE il.id = :id
                        """,
                new MapSqlParameterSource("id", line.invoiceLineId()), String.class);
        assertThat(originalInvoiceState).isEqualTo("VOID");

        long replacementAmount = jdbcTemplate.queryForObject(
                "SELECT gross_minor FROM invoice_line WHERE id = :id",
                new MapSqlParameterSource("id", result.replacementLine().invoiceLineId()), Long.class);
        assertThat(replacementAmount).isEqualTo(3_000L * 30);
    }

    @Test
    void approvingASubstitutionAfterPartialDispensingIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID doctor = insertFixtureDoctor();
        PrescriptionLine line = prescribe("AMX500", 30, true, admin);
        payLine(line.invoiceLineId(), patientIdFor(line), admin);
        UUID batchId = onlyBatchIdFor(line.itemId());
        dispenseService.dispense(new DispenseCommand(line.id(), List.of(new DispenseLineInput(batchId, 10, null)),
                admin, "idem-" + UUID.randomUUID(), null, null));

        UUID alternative = createFixtureItem("AMX250", admin);
        SubstitutionProposal proposal = proposeSubstitutionService.propose(
                new ProposeSubstitutionCommand(line.id(), alternative, "wrong strength ordered", admin));

        assertThatThrownBy(() -> reviewSubstitutionService.review(
                new ReviewSubstitutionCommand(proposal.id(), SubstitutionStatus.APPROVED, doctor, "fine")))
                .isInstanceOf(SubstitutionNotAllowedException.class);
    }

    private PrescriptionLine prescribe(String itemCode, int quantity, boolean substitutionAllowed, UUID admin) {
        UUID itemId = createFixtureItem(itemCode, admin);
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        restockService.restock(new RestockCommand(itemId, storeId, "BATCH-1", LocalDate.now().plusYears(1), 100,
                new Money(50000, "TZS"), supplierId, "DN-" + UUID.randomUUID(), null, admin));
        insertFixtureServiceItemAndPrice(itemCode, 2_000, admin);

        Patient patient = registerPatientService.register(
                patientCommand("Test" + UUID.randomUUID().toString().substring(0, 6), "Patient", admin));
        Encounter encounter = openEncounterService.checkIn(
                new OpenEncounterCommand(patient.id(), insertFixtureDepartment(), null, admin));
        jdbcTemplate.update("""
                INSERT INTO consultation_pregnancy_assessment (encounter_id, status, recorded_by)
                VALUES (:encounterId, 'NO', :actorId)
                """, new MapSqlParameterSource().addValue("encounterId", encounter.id()).addValue("actorId", admin));
        return placePrescriptionService.placeOrder(new PlacePrescriptionCommand(
                encounter.id(), itemId, quantity, "TAKE ONE TABLET THREE TIMES A DAY", substitutionAllowed, admin,
                "ORAL", 1.0, "TABLET", com.adags.modules.pharmacy.domain.PrescriptionFrequency.OD, quantity));
    }

    private UUID patientIdFor(PrescriptionLine line) {
        return jdbcTemplate.queryForObject("""
                        SELECT po.patient_id FROM prescription_order po
                        JOIN prescription_line pl ON pl.prescription_order_id = po.id
                        WHERE pl.id = :lineId
                        """,
                new MapSqlParameterSource("lineId", line.id()), UUID.class);
    }

    private UUID onlyBatchIdFor(UUID itemId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM stock_batch WHERE item_id = :itemId", new MapSqlParameterSource("itemId", itemId), UUID.class);
    }

    private PrescriptionLine prescriptionLineFindById(UUID id) {
        return jdbcTemplate.queryForObject("""
                        SELECT status, quantity_dispensed FROM prescription_line WHERE id = :id
                        """,
                new MapSqlParameterSource("id", id),
                (rs, rowNum) -> new PrescriptionLine(id, null, null, 0, rs.getInt("quantity_dispensed"), null, null,
                        PrescriptionLineStatus.valueOf(rs.getString("status")), false, null,
                        null, null, null, null, null, null));
    }

    private void payLine(UUID invoiceLineId, UUID patientId, UUID admin) {
        long outstanding = jdbcTemplate.queryForObject(
                "SELECT patient_portion_minor FROM invoice_line WHERE id = :id",
                new MapSqlParameterSource("id", invoiceLineId), Long.class);
        ensureCashierShiftOpen(admin);
        postPaymentService.recordPayment(new PostPaymentCommand(
                patientId, PaymentMethod.CASH, null, "idem-pay-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(invoiceLineId, outstanding)), admin));
    }

    private void ensureCashierShiftOpen(UUID admin) {
        Integer openShifts = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cashier_shift WHERE opened_by = :admin AND status = 'OPEN'",
                new MapSqlParameterSource("admin", admin), Integer.class);
        if (openShifts == 0) {
            openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        }
    }

    private UUID createFixtureItem(String code, UUID admin) {
        Item item = createItemService.create(new CreateItemCommand(code, code + " tablet", "ANTIBIOTIC",
                "TABLET", "500mg", "TABLET", 100, admin));
        activateItemService.activate(item.id());
        return item.id();
    }

    private void insertFixtureServiceItemAndPrice(String code, long priceMinor, UUID createdBy) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM service_item WHERE code = :code", new MapSqlParameterSource("code", code), Integer.class);
        if (existing > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO service_item (code, name, category) VALUES (:code, :code, 'PHARMACY')",
                new MapSqlParameterSource("code", code));
        UUID serviceItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM service_item WHERE code = :code", new MapSqlParameterSource("code", code), UUID.class);
        UUID priceListId = jdbcTemplate.queryForObject(
                "SELECT id FROM price_list WHERE payer_category = 'CASH'", new MapSqlParameterSource(), UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, created_by)
                        VALUES (:priceListId, :serviceItemId, :amountMinor, :createdBy)
                        """,
                new MapSqlParameterSource()
                        .addValue("priceListId", priceListId)
                        .addValue("serviceItemId", serviceItemId)
                        .addValue("amountMinor", priceMinor)
                        .addValue("createdBy", createdBy));
    }

    private UUID insertFixtureStore() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO store (name, type, facility_id) VALUES ('Main Pharmacy', 'MAIN_PHARMACY', :facilityId)
                        RETURNING id
                        """,
                new MapSqlParameterSource("facilityId", BOOTSTRAP_FACILITY_ID), UUID.class);
    }

    private UUID insertFixtureSupplier() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO supplier (code, name) VALUES (:code, 'Test Supplier') RETURNING id",
                new MapSqlParameterSource("code", "SUP-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }

    private UUID insertFixtureDepartment() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, category_id, code, name) VALUES (:facilityId, (SELECT id FROM department_category WHERE code = 'GENERAL_CLINICAL'), :code, :code)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }

    private UUID insertFixtureDoctor() {
        UUID userId = jdbcTemplate.queryForObject("""
                        INSERT INTO app_user (username, password_hash) VALUES (:username, 'x') RETURNING id
                        """,
                new MapSqlParameterSource("username", "test-doctor-" + UUID.randomUUID()), UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO role_assignment (user_id, role_id, scope_type, scope_id)
                        SELECT :userId, id, 'GLOBAL', NULL FROM role WHERE code = 'DOCTOR'
                        """,
                new MapSqlParameterSource("userId", userId));
        return userId;
    }

    private RegisterPatientCommand patientCommand(String givenName, String familyName, UUID actor) {
        return new RegisterPatientCommand(
                givenName, null, familyName, LocalDate.of(1990, 4, 12), null, null,
                "FEMALE", "SINGLE", null, "+255700" + (100000 + Math.abs((givenName + familyName + UUID.randomUUID()).hashCode() % 900000)),
                null, null,
                new NextOfKin(null, null, "Next Kin", "SIBLING", null, "+255711000000", false),
                List.of(), null, BOOTSTRAP_FACILITY_ID, actor, null);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
