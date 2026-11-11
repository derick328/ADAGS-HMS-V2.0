package com.adags;

import com.adags.modules.billing.application.OpenCashierShiftService;
import com.adags.modules.billing.application.PostPaymentCommand;
import com.adags.modules.billing.application.PostPaymentService;
import com.adags.modules.billing.domain.BillingGate;
import com.adags.modules.billing.domain.BillingGateRejectedException;
import com.adags.modules.billing.domain.PaymentMethod;
import com.adags.modules.encounters.application.OpenEncounterCommand;
import com.adags.modules.encounters.application.OpenEncounterService;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.inventory.application.ActivateItemService;
import com.adags.modules.inventory.application.CreateItemCommand;
import com.adags.modules.inventory.application.CreateItemService;
import com.adags.modules.inventory.application.RestockCommand;
import com.adags.modules.inventory.application.RestockService;
import com.adags.modules.inventory.domain.BatchExpiredException;
import com.adags.modules.inventory.domain.InsufficientStockException;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.StockBatch;
import com.adags.modules.patients.application.RegisterPatientCommand;
import com.adags.modules.patients.application.RegisterPatientService;
import com.adags.modules.patients.domain.NextOfKin;
import com.adags.modules.patients.domain.Patient;
import com.adags.modules.pharmacy.application.DispenseCommand;
import com.adags.modules.pharmacy.application.DispenseContextService;
import com.adags.modules.pharmacy.application.DispenseLabelService;
import com.adags.modules.pharmacy.application.DispenseLineInput;
import com.adags.modules.pharmacy.application.DispenseResult;
import com.adags.modules.pharmacy.application.DispenseService;
import com.adags.modules.pharmacy.application.PlacePrescriptionCommand;
import com.adags.modules.pharmacy.application.PlacePrescriptionService;
import com.adags.modules.pharmacy.domain.DispenseExceedsPrescribedException;
import com.adags.modules.pharmacy.domain.MissingFefoOverrideReasonException;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineStatus;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 5, pharmacy build brief item 3 — dispensing. Extends the
// register -> triage -> pay -> ... path EndToEndBillingTest documents:
// register -> check-in -> prescribe (charge posted) -> pay -> [gate] ->
// dispense -> stock decremented. Nothing here is mocked. Skips triage/
// consultation entirely — PlacePrescriptionService only needs an encounter
// to exist (EncounterFacade.findContext), the same minimal dependency
// PlaceLabOrderService has.
class EndToEndPharmacyDispensingTest extends IntegrationTestSupport {

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
    private BillingGate billingGate;
    @Autowired
    private CreateItemService createItemService;
    @Autowired
    private ActivateItemService activateItemService;
    @Autowired
    private RestockService restockService;
    @Autowired
    private PlacePrescriptionService placePrescriptionService;
    @Autowired
    private DispenseService dispenseService;
    @Autowired
    private DispenseContextService dispenseContextService;
    @Autowired
    private DispenseLabelService dispenseLabelService;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE dispense_line, dispense, prescription_line, prescription_order,
                                 stock_balance, stock_movement, stock_batch, item, store, supplier,
                                 payment_allocation, payment, cashier_shift, service_authorization, invoice_line,
                                 invoice, encounter_event, allergy_record, outbox_event, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void dispensingIsRejectedUntilThePrescriptionChargeIsPaidThenSucceedsAndDecrementsStock() {
        UUID admin = bootstrapAdminId();
        Fixture fixture = setUpPrescription("AMX500", 100, admin);

        assertThat(billingGate.isSatisfied(fixture.prescriptionLine.invoiceLineId())).isFalse();
        assertThatThrownBy(() -> dispenseService.dispense(dispenseCommand(fixture, 30, admin)))
                .isInstanceOfSatisfying(BillingGateRejectedException.class,
                        ex -> assertThat(ex.invoiceLineId()).isEqualTo(fixture.prescriptionLine.invoiceLineId()));
        assertThat(countWhere("dispense", "prescription_line_id", fixture.prescriptionLine.id())).isZero();

        payFor(fixture, admin);
        assertThat(billingGate.isSatisfied(fixture.prescriptionLine.invoiceLineId())).isTrue();

        DispenseResult result = dispenseService.dispense(dispenseCommand(fixture, 30, admin));

        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).batchId()).isEqualTo(fixture.batchId);
        assertThat(result.prescriptionLine().status()).isEqualTo(PrescriptionLineStatus.COMPLETED);

        Integer balance = jdbcTemplate.queryForObject(
                "SELECT quantity FROM stock_balance WHERE batch_id = :batchId",
                new MapSqlParameterSource("batchId", fixture.batchId), Integer.class);
        assertThat(balance).isEqualTo(70);
    }

    @Test
    void oneBlisterPrescriptionCanBePartiallyDispensedAsBaseUnits() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem("BLISTER-AMX", admin);
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        StockBatch batch = restock(itemId, storeId, supplierId, "BLISTER-BATCH",
                LocalDate.now().plusYears(1), 30, admin);
        UUID approver = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (username, password_hash)
                VALUES (:name, 'test-hash') RETURNING id
                """, new MapSqlParameterSource("name", "blister-approver-" + UUID.randomUUID()), UUID.class);
        jdbcTemplate.update("""
                INSERT INTO medicine_packaging
                  (item_id, packaging_unit, quantity_in_base_units, can_split, version,
                   status, reason, proposed_by, approved_by, approved_at)
                VALUES (:itemId, 'BLISTER', 3, TRUE, 1, 'APPROVED',
                        'Three-capsule presentation', :admin, :approver, now())
                """, new MapSqlParameterSource().addValue("itemId", itemId)
                .addValue("admin", admin).addValue("approver", approver));
        insertFixtureServiceItemAndPrice("BLISTER-AMX", 200, admin);
        Patient patient = registerPatientService.register(patientCommand("Blister", "Patient", admin));
        Encounter encounter = openEncounterService.checkIn(
                new OpenEncounterCommand(patient.id(), insertFixtureDepartment(), null, admin));
        recordPregnancyStatus(encounter.id(), admin);
        PrescriptionLine line = placePrescriptionService.placeOrder(new PlacePrescriptionCommand(
                encounter.id(), itemId, 3, "As directed", false, admin,
                "ORAL", 1.0, "CAPSULE", null, null, 1, "BLISTER"));
        assertThat(line.quantityPrescribed()).isEqualTo(3);
        var snapshot = jdbcTemplate.queryForMap("""
                SELECT requested_quantity, requested_unit, conversion_factor FROM prescription_unit_snapshot
                WHERE prescription_line_id = :id
                """, new MapSqlParameterSource("id", line.id()));
        assertThat(snapshot.get("requested_quantity")).isEqualTo(1);
        assertThat(snapshot.get("requested_unit")).isEqualTo("BLISTER");
        assertThat(snapshot.get("conversion_factor")).isEqualTo(3);

        payLine(line.invoiceLineId(), patient.id(), admin);
        DispenseResult first = dispenseService.dispense(new DispenseCommand(line.id(),
                List.of(new DispenseLineInput(batch.id(), 2, null, "CAPSULE")),
                admin, "idem-" + UUID.randomUUID(), null, null));
        assertThat(first.dispense().quantityDispensed()).isEqualTo(2);
        assertThat(first.lines().getFirst().conversionFactor()).isEqualTo(1);
        assertThat(first.prescriptionLine().remaining()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT quantity FROM stock_balance WHERE batch_id = :id",
                new MapSqlParameterSource("id", batch.id()), Integer.class)).isEqualTo(28);
        DispenseResult second = dispenseService.dispense(new DispenseCommand(line.id(),
                List.of(new DispenseLineInput(batch.id(), 1, null, "CAPSULE")),
                admin, "idem-" + UUID.randomUUID(), null, null));
        assertThat(second.prescriptionLine().remaining()).isZero();
    }

    // §4.3: "Several lines per item, summing to quantity_dispensed" — 30
    // tablets, batch A holds 20, batch B holds 10.
    @Test
    void dispensingSplitsAcrossBatchesFefoOrderWhenOneBatchIsInsufficient() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem("AMX500", admin);
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        StockBatch earlier = restock(itemId, storeId, supplierId, "BATCH-EARLY", LocalDate.now().plusMonths(3), 20, admin);
        StockBatch later = restock(itemId, storeId, supplierId, "BATCH-LATE", LocalDate.now().plusMonths(9), 30, admin);
        insertFixtureServiceItemAndPrice("AMX500", 200, admin);
        PrescriptionLine line = prescribeAndPay(itemId, 30, admin);

        DispenseResult result = dispenseService.dispense(new DispenseCommand(line.id(),
                List.of(new DispenseLineInput(earlier.id(), 20, null), new DispenseLineInput(later.id(), 10, null)),
                admin, "idem-" + UUID.randomUUID(), null, null));

        assertThat(result.lines()).hasSize(2);
        assertThat(result.prescriptionLine().status()).isEqualTo(PrescriptionLineStatus.COMPLETED);

        Integer earlyBalance = jdbcTemplate.queryForObject("SELECT quantity FROM stock_balance WHERE batch_id = :id",
                new MapSqlParameterSource("id", earlier.id()), Integer.class);
        Integer lateBalance = jdbcTemplate.queryForObject("SELECT quantity FROM stock_balance WHERE batch_id = :id",
                new MapSqlParameterSource("id", later.id()), Integer.class);
        assertThat(earlyBalance).isZero();
        assertThat(lateBalance).isEqualTo(20);
    }

    // §5.3: picking the later-expiring batch while the earlier one still
    // has capacity is a FEFO deviation — requires a reason.
    @Test
    void deviatingFromFefoWithoutAReasonIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem("AMX500", admin);
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        StockBatch earlier = restock(itemId, storeId, supplierId, "BATCH-EARLY", LocalDate.now().plusMonths(3), 50, admin);
        StockBatch later = restock(itemId, storeId, supplierId, "BATCH-LATE", LocalDate.now().plusMonths(9), 50, admin);
        insertFixtureServiceItemAndPrice("AMX500", 200, admin);
        PrescriptionLine line = prescribeAndPay(itemId, 10, admin);

        assertThatThrownBy(() -> dispenseService.dispense(new DispenseCommand(line.id(),
                List.of(new DispenseLineInput(later.id(), 10, null)),
                admin, "idem-" + UUID.randomUUID(), null, null)))
                .isInstanceOf(MissingFefoOverrideReasonException.class);

        DispenseResult result = dispenseService.dispense(new DispenseCommand(line.id(),
                List.of(new DispenseLineInput(later.id(), 10, "near-expiry batch A reserved for ward stock")),
                admin, "idem-" + UUID.randomUUID(), null, null));
        assertThat(result.lines().get(0).batchId()).isEqualTo(later.id());
        assertThat(earlier.id()).isNotNull();
    }

    // §6.3: "Expired batches HARD-BLOCKED from dispensing, not flagged."
    @Test
    void anExpiredBatchIsHardBlockedFromDispensingAndExcludedFromTheFefoSuggestion() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem("AMX500", admin);
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        // stock_batch's CHECK (expiry_date > received_at::date) fires on
        // UPDATE as well as INSERT, so an already-valid row can't be aged
        // in place — insert directly via raw SQL with both received_at and
        // expiry_date already in the past instead, simulating a batch
        // nobody has flagged EXPIRED yet. The live expiry check in
        // InventoryFacade.decrementForDispense is exactly what this
        // proves, independent of the stored status column.
        UUID batchId = insertExpiredFixtureBatch(itemId, storeId, supplierId, admin);
        insertFixtureServiceItemAndPrice("AMX500", 200, admin);
        PrescriptionLine line = prescribeAndPay(itemId, 10, admin);

        assertThatThrownBy(() -> dispenseService.dispense(new DispenseCommand(line.id(),
                List.of(new DispenseLineInput(batchId, 10, "manual override, testing expiry")),
                admin, "idem-" + UUID.randomUUID(), null, null)))
                .isInstanceOf(BatchExpiredException.class);

        var context = dispenseContextService.build(line.id());
        assertThat(context.availableBatches()).as("expired batch excluded from FEFO availability").isEmpty();
    }

    // §4.4's three-way rule.
    @Test
    void partialDispensingLeavesTheRemainderVisibleAndASecondDispenseCompletesIt() {
        UUID admin = bootstrapAdminId();
        Fixture fixture = setUpPrescription("AMX500", 100, admin);
        payFor(fixture, admin);

        DispenseResult first = dispenseService.dispense(dispenseCommand(fixture, 12, admin));
        assertThat(first.prescriptionLine().status()).isEqualTo(PrescriptionLineStatus.PARTIALLY_DISPENSED);
        assertThat(first.prescriptionLine().remaining()).isEqualTo(18);

        DispenseResult second = dispenseService.dispense(new DispenseCommand(fixture.prescriptionLine.id(),
                List.of(new DispenseLineInput(fixture.batchId, 18, null)),
                admin, "idem-" + UUID.randomUUID(), null, null));
        assertThat(second.prescriptionLine().status()).isEqualTo(PrescriptionLineStatus.COMPLETED);
        assertThat(second.prescriptionLine().remaining()).isZero();
    }

    @Test
    void dispensingMoreThanPrescribedIsRejectedAsAHardBlock() {
        UUID admin = bootstrapAdminId();
        Fixture fixture = setUpPrescription("AMX500", 100, admin);
        payFor(fixture, admin);

        assertThatThrownBy(() -> dispenseService.dispense(dispenseCommand(fixture, 31, admin)))
                .isInstanceOf(DispenseExceedsPrescribedException.class);
    }

    // §5.4/§5.5: a double-tap must not decrement stock twice or chart two dispenses.
    @Test
    void aRepeatedIdempotencyKeyReturnsTheOriginalDispenseWithoutDecrementingTwice() {
        UUID admin = bootstrapAdminId();
        Fixture fixture = setUpPrescription("AMX500", 100, admin);
        payFor(fixture, admin);
        String idempotencyKey = "idem-" + UUID.randomUUID();

        DispenseResult first = dispenseService.dispense(new DispenseCommand(fixture.prescriptionLine.id(),
                List.of(new DispenseLineInput(fixture.batchId, 10, null)), admin, idempotencyKey, null, null));
        DispenseResult second = dispenseService.dispense(new DispenseCommand(fixture.prescriptionLine.id(),
                List.of(new DispenseLineInput(fixture.batchId, 10, null)), admin, idempotencyKey, null, null));

        assertThat(second.dispense().id()).isEqualTo(first.dispense().id());
        Integer dispenseCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM dispense WHERE idempotency_key = :key",
                new MapSqlParameterSource("key", idempotencyKey), Integer.class);
        assertThat(dispenseCount).isEqualTo(1);

        Integer balance = jdbcTemplate.queryForObject(
                "SELECT quantity FROM stock_balance WHERE batch_id = :batchId",
                new MapSqlParameterSource("batchId", fixture.batchId), Integer.class);
        assertThat(balance).as("decremented once, not twice").isEqualTo(90);
    }

    // root CLAUDE.md "Concurrency": two pharmacists dispensing from the
    // same batch simultaneously must not both succeed past available
    // quantity — mirrors StockBalanceConcurrencyTest's proof, now through
    // the whole DispenseService, not the repository directly.
    @Test
    void concurrentDispensesAgainstTheSameBatchNeverOversubscribeIt() throws Exception {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem("AMX500", admin);
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        StockBatch batch = restock(itemId, storeId, supplierId, "BATCH-1", LocalDate.now().plusYears(1), 10, admin);
        insertFixtureServiceItemAndPrice("AMX500", 100, admin);

        int racers = 5; // each prescribes+pays for 3, pool only has 10 -> at most 3 can fully succeed
        List<PrescriptionLine> lines = IntStream.range(0, racers)
                .mapToObj(i -> {
                    PrescriptionLine line = prescribeAndPay(itemId, 3, admin);
                    return line;
                })
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = lines.stream()
                    .map(line -> pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        try {
                            dispenseService.dispense(new DispenseCommand(line.id(),
                                    List.of(new DispenseLineInput(batch.id(), 3, null)),
                                    admin, "idem-" + UUID.randomUUID(), null, null));
                            return true;
                        } catch (InsufficientStockException ex) {
                            return false;
                        }
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            long successes = futures.stream().map(this::resolve).filter(Boolean::booleanValue).count();
            assertThat(successes).isEqualTo(3);
        } finally {
            pool.shutdown();
        }

        Integer finalBalance = jdbcTemplate.queryForObject(
                "SELECT quantity FROM stock_balance WHERE batch_id = :batchId",
                new MapSqlParameterSource("batchId", batch.id()), Integer.class);
        assertThat(finalBalance).as("never went negative").isEqualTo(1);
    }

    // root CLAUDE.md "Safety": every allergy record, every confirmation
    // status, rendered inline with the dispense context.
    @Test
    void dispenseContextReturnsEveryAllergyRegardlessOfConfirmationStatus() {
        UUID admin = bootstrapAdminId();
        Fixture fixture = setUpPrescription("AMX500", 100, admin);

        insertFixtureAllergy(fixture.patientId, "Penicillin", "UNCONFIRMED");
        insertFixtureAllergy(fixture.patientId, "Sulfa drugs", "CONFIRMED");
        insertFixtureAllergy(fixture.patientId, "Latex", "REFUTED");

        var context = dispenseContextService.build(fixture.prescriptionLine.id());

        assertThat(context.allergies()).hasSize(3);
        assertThat(context.allergies()).extracting(a -> a.confirmationStatus())
                .containsExactlyInAnyOrder("UNCONFIRMED", "CONFIRMED", "REFUTED");
    }

    // 12-pharmacist.md §4.6: patient name, hospital number, drug, dose
    // instructions, dispensed date, expiry, batch number — everything a
    // dosing-error-preventing label needs, with nothing invented.
    @Test
    void dispenseLabelCarriesPatientDrugAndBatchDetails() {
        UUID admin = bootstrapAdminId();
        Fixture fixture = setUpPrescription("AMX500", 100, admin);
        payFor(fixture, admin);
        DispenseResult result = dispenseService.dispense(dispenseCommand(fixture, 12, admin));

        var label = dispenseLabelService.generate(result.dispense().id());

        assertThat(label.hospitalNumber()).isNotBlank();
        assertThat(label.patientName()).contains("Amina").contains("Hassan");
        assertThat(label.drugName()).isEqualTo("Amoxicillin 500mg capsule");
        assertThat(label.doseInstructions()).isEqualTo("TAKE ONE TABLET THREE TIMES A DAY");
        assertThat(label.batchLines()).hasSize(1);
        assertThat(label.batchLines().get(0).batchNumber()).isEqualTo("BATCH-1");
    }

    // A single dispense that spans two batches carries both batch numbers
    // on the label — a partial recall of only one lot must still find every
    // patient who received it.
    @Test
    void dispenseLabelListsEveryBatchWhenTheDispenseSpansMoreThanOne() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem("AMX500", admin);
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        StockBatch earlier = restock(itemId, storeId, supplierId, "BATCH-EARLY", LocalDate.now().plusMonths(3), 20, admin);
        StockBatch later = restock(itemId, storeId, supplierId, "BATCH-LATE", LocalDate.now().plusMonths(9), 30, admin);
        insertFixtureServiceItemAndPrice("AMX500", 200, admin);
        PrescriptionLine line = prescribeAndPay(itemId, 30, admin);

        DispenseResult result = dispenseService.dispense(new DispenseCommand(line.id(),
                List.of(new DispenseLineInput(earlier.id(), 20, null), new DispenseLineInput(later.id(), 10, null)),
                admin, "idem-" + UUID.randomUUID(), null, null));

        var label = dispenseLabelService.generate(result.dispense().id());

        assertThat(label.batchLines()).extracting(l -> l.batchNumber())
                .containsExactlyInAnyOrder("BATCH-EARLY", "BATCH-LATE");
    }

    private Boolean resolve(Future<Boolean> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private record Fixture(UUID patientId, PrescriptionLine prescriptionLine, UUID batchId) {
    }

    private Fixture setUpPrescription(String itemCode, long priceMinor, UUID admin) {
        UUID itemId = createFixtureItem(itemCode, admin);
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        StockBatch batch = restock(itemId, storeId, supplierId, "BATCH-1", LocalDate.now().plusYears(1), 100, admin);
        insertFixtureServiceItemAndPrice(itemCode, priceMinor, admin);

        Patient patient = registerPatientService.register(patientCommand("Amina", "Hassan", admin));
        Encounter encounter = openEncounterService.checkIn(
                new OpenEncounterCommand(patient.id(), insertFixtureDepartment(), null, admin));
        recordPregnancyStatus(encounter.id(), admin);
        PrescriptionLine line = placePrescriptionService.placeOrder(new PlacePrescriptionCommand(
                encounter.id(), itemId, 30, "TAKE ONE TABLET THREE TIMES A DAY", false, admin,
                null, null, null, null, null));

        return new Fixture(patient.id(), line, batch.id());
    }

    private PrescriptionLine prescribeAndPay(UUID itemId, int quantity, UUID admin) {
        Patient patient = registerPatientService.register(
                patientCommand("Test" + UUID.randomUUID().toString().substring(0, 6), "Patient", admin));
        Encounter encounter = openEncounterService.checkIn(
                new OpenEncounterCommand(patient.id(), insertFixtureDepartment(), null, admin));
        recordPregnancyStatus(encounter.id(), admin);
        PrescriptionLine line = placePrescriptionService.placeOrder(new PlacePrescriptionCommand(
                encounter.id(), itemId, quantity, "TAKE ONE TABLET THREE TIMES A DAY", false, admin,
                null, null, null, null, null));
        payLine(line.invoiceLineId(), patient.id(), admin);
        return line;
    }

    private void payFor(Fixture fixture, UUID admin) {
        payLine(fixture.prescriptionLine.invoiceLineId(), fixture.patientId, admin);
    }

    private void recordPregnancyStatus(UUID encounterId, UUID actorId) {
        jdbcTemplate.update("""
                INSERT INTO consultation_pregnancy_assessment (encounter_id, status, recorded_by)
                VALUES (:encounterId, 'NO', :actorId)
                """, new MapSqlParameterSource().addValue("encounterId", encounterId).addValue("actorId", actorId));
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

    private DispenseCommand dispenseCommand(Fixture fixture, int quantity, UUID actor) {
        return new DispenseCommand(fixture.prescriptionLine.id(),
                List.of(new DispenseLineInput(fixture.batchId, quantity, null)),
                actor, "idem-" + UUID.randomUUID(), "counselled on adherence", null);
    }

    private UUID createFixtureItem(String code, UUID admin) {
        Item item = createItemService.create(new CreateItemCommand(code, "Amoxicillin 500mg capsule", "ANTIBIOTIC",
                "CAPSULE", "500mg", "CAPSULE", 100, admin));
        activateItemService.activate(item.id());
        return item.id();
    }

    private StockBatch restock(UUID itemId, UUID storeId, UUID supplierId, String batchNumber, LocalDate expiryDate,
                                int quantity, UUID admin) {
        return restockService.restock(new RestockCommand(itemId, storeId, batchNumber, expiryDate, quantity,
                new Money(50000, "TZS"), supplierId, "DN-" + UUID.randomUUID(), null, admin));
    }

    // RestockService can't produce an already-expired ACTIVE batch — its
    // CHECK (expiry_date > received_at::date) forbids it by design. Insert
    // directly with both dates already in the past instead, to simulate a
    // batch nobody has flagged EXPIRED yet.
    private UUID insertExpiredFixtureBatch(UUID itemId, UUID storeId, UUID supplierId, UUID admin) {
        UUID batchId = jdbcTemplate.queryForObject("""
                        INSERT INTO stock_batch (item_id, store_id, batch_number, expiry_date, received_at,
                                                  unit_cost_amount_minor, supplier_id, delivery_note_ref, received_by)
                        VALUES (:itemId, :storeId, 'BATCH-EXPIRED', CURRENT_DATE - INTERVAL '1 day',
                                CURRENT_DATE - INTERVAL '2 years', 50000, :supplierId, 'DN-1', :admin)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("itemId", itemId)
                        .addValue("storeId", storeId)
                        .addValue("supplierId", supplierId)
                        .addValue("admin", admin),
                UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO stock_movement (item_id, batch_id, store_id, qty_delta, movement_type,
                                                     reference_type, reference_id, actor_id)
                        VALUES (:itemId, :batchId, :storeId, 50, 'RECEIPT', 'STOCK_BATCH', :batchId, :admin)
                        """,
                new MapSqlParameterSource()
                        .addValue("itemId", itemId).addValue("batchId", batchId).addValue("storeId", storeId)
                        .addValue("admin", admin));
        jdbcTemplate.update(
                "INSERT INTO stock_balance (item_id, batch_id, store_id, quantity) VALUES (:itemId, :batchId, :storeId, 50)",
                new MapSqlParameterSource()
                        .addValue("itemId", itemId).addValue("batchId", batchId).addValue("storeId", storeId));
        return batchId;
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

    private void insertFixtureAllergy(UUID patientId, String description, String confirmationStatus) {
        // §6.1's CHECK: CONFIRMED/REFUTED require confirmed_by/confirmed_at
        // set together; REFUTED also requires a confirmation_note.
        boolean confirmedOrRefuted = !confirmationStatus.equals("UNCONFIRMED");
        jdbcTemplate.update("""
                        INSERT INTO allergy_record (patient_id, description, source, recorded_by, confirmation_status,
                                                     confirmed_by, confirmed_at, confirmation_note)
                        VALUES (:patientId, :description, 'TRIAGE', (SELECT id FROM app_user WHERE username = 'admin'), :status,
                                CASE WHEN :confirmedOrRefuted THEN (SELECT id FROM app_user WHERE username = 'admin') END,
                                CASE WHEN :confirmedOrRefuted THEN now() END,
                                CASE WHEN :status = 'REFUTED' THEN 'patient tolerates it on direct questioning' END)
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("description", description)
                        .addValue("confirmedOrRefuted", confirmedOrRefuted)
                        .addValue("status", confirmationStatus));
    }

    private int countWhere(String table, String column, UUID value) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = :value",
                new MapSqlParameterSource("value", value), Integer.class);
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
