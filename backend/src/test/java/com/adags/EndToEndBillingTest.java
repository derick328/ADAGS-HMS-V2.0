package com.adags;

import com.adags.modules.billing.application.OpenCashierShiftService;
import com.adags.modules.billing.application.PostPaymentCommand;
import com.adags.modules.billing.application.PostPaymentService;
import com.adags.modules.billing.domain.BillingGate;
import com.adags.modules.billing.domain.BillingGateRejectedException;
import com.adags.modules.billing.domain.PaymentMethod;
import com.adags.modules.encounters.application.MoveToAwaitingConsultService;
import com.adags.modules.encounters.application.OpenEncounterCommand;
import com.adags.modules.encounters.application.OpenEncounterService;
import com.adags.modules.encounters.application.SubmitTriageAssessmentCommand;
import com.adags.modules.encounters.application.SubmitTriageAssessmentService;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.modules.encounters.domain.ImmediateReason;
import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.PregnancyStatus;
import com.adags.modules.encounters.domain.TriagePriority;
import com.adags.modules.laboratory.application.CollectSpecimenCommand;
import com.adags.modules.laboratory.application.CollectSpecimenService;
import com.adags.modules.laboratory.application.ParameterEntry;
import com.adags.modules.laboratory.application.PlaceLabOrderCommand;
import com.adags.modules.laboratory.application.PlaceLabOrderService;
import com.adags.modules.laboratory.application.RecordLabResultCommand;
import com.adags.modules.laboratory.application.RecordLabResultResult;
import com.adags.modules.laboratory.application.RecordLabResultService;
import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.modules.laboratory.domain.LabReferenceRange;
import com.adags.modules.laboratory.domain.LabReferenceRangeRepository;
import com.adags.modules.laboratory.domain.LabResultParameterValue;
import com.adags.modules.laboratory.domain.LabTest;
import com.adags.modules.laboratory.domain.LabTestParameter;
import com.adags.modules.laboratory.domain.LabTestParameterRepository;
import com.adags.modules.laboratory.domain.LabTestRepository;
import com.adags.modules.laboratory.domain.MissingQualityNoteException;
import com.adags.modules.laboratory.domain.ResultFlag;
import com.adags.modules.laboratory.domain.Sex;
import com.adags.modules.laboratory.domain.Specimen;
import com.adags.modules.laboratory.domain.SpecimenQuality;
import com.adags.modules.laboratory.domain.SpecimenStatus;
import com.adags.modules.laboratory.domain.Urgency;
import com.adags.modules.laboratory.domain.VerificationLevel;
import com.adags.modules.patients.application.RegisterPatientCommand;
import com.adags.modules.patients.application.RegisterPatientService;
import com.adags.modules.patients.domain.NextOfKin;
import com.adags.modules.patients.domain.Patient;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// backend/CLAUDE.md "Testing": "End to end: one test covering register ->
// triage -> pay -> consult -> order -> result -> dispense -> settle. This
// is the path that must never break." Phase 4 items 2/3 reach "order" (a
// real LabOrder, a real specimen-collection gate) — result/dispense/settle
// still need modules that don't exist yet — this is that path's seed,
// extended each phase it grows.
//
// Proves Phases 1-4 actually connect: real patient registration, real
// encounter check-in, real triage submission posting a real charge
// through real pricing, a real cashier shift and payment settling it
// through the real gate, and the real consultation-entry transition this
// unlocks. Nothing here is mocked.
class EndToEndBillingTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private RegisterPatientService registerPatientService;
    @Autowired
    private OpenEncounterService openEncounterService;
    @Autowired
    private SubmitTriageAssessmentService submitTriageAssessmentService;
    @Autowired
    private MoveToAwaitingConsultService moveToAwaitingConsultService;
    @Autowired
    private OpenCashierShiftService openCashierShiftService;
    @Autowired
    private PostPaymentService postPaymentService;
    @Autowired
    private BillingGate billingGate;
    @Autowired
    private PlaceLabOrderService placeLabOrderService;
    @Autowired
    private CollectSpecimenService collectSpecimenService;
    @Autowired
    private LabTestRepository labTestRepository;
    @Autowired
    private LabTestParameterRepository labTestParameterRepository;
    @Autowired
    private LabReferenceRangeRepository labReferenceRangeRepository;
    @Autowired
    private RecordLabResultService recordLabResultService;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        // lab_order and specimen aren't listed explicitly — both carry an
        // FK to encounter (lab_order directly, specimen transitively via
        // lab_order), so CASCADE off `encounter` already reaches them; every
        // row either table holds in this suite was created against an
        // encounter this same TRUNCATE removes. lab_test/lab_test_parameter
        // are catalogue rows with no such FK and are deliberately left
        // alone — same "harmless accumulation" reasoning
        // PriceListEntryRepositoryTest documents for service_item: no
        // migration seeds them, so nothing else depends on a clean slate.
        jdbcTemplate.update("""
                        TRUNCATE payment_allocation, payment, cashier_shift, service_authorization, invoice_line,
                                 invoice, payment_bypass, encounter_event, triage_assessment, vital_reading,
                                 vital_signs, allergy_record, outbox_event, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM role_assignment WHERE role_id = (SELECT id FROM role WHERE code = 'DOCTOR')",
                new MapSqlParameterSource());
    }

    @Test
    void registerCheckInTriageChargePaymentGateThenAwaitingConsult() {
        UUID admin = bootstrapAdminId();
        UUID doctorId = insertFixtureDoctor();

        Patient patient = registerPatientService.register(patientCommand("Amina", "Hassan", admin));
        Encounter checkedIn = openEncounterService.checkIn(
                new OpenEncounterCommand(patient.id(), insertFixtureDepartment(), null, admin));
        assertThat(checkedIn.state()).isEqualTo(EncounterState.AWAITING_TRIAGE);

        submitTriageAssessmentService.submit(triageCommand(checkedIn, doctorId, TriagePriority.NON_URGENT, null));

        String afterTriageState = jdbcTemplate.queryForObject("SELECT state FROM encounter WHERE id = :id",
                new MapSqlParameterSource("id", checkedIn.id()), String.class);
        assertThat(afterTriageState).isEqualTo(EncounterState.AWAITING_PAYMENT.name());

        UUID invoiceLineId = consultationLineIdFor(checkedIn.id());
        assertThat(billingGate.isSatisfied(invoiceLineId)).as("unpaid — not yet satisfied").isFalse();

        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        postPaymentService.recordPayment(new PostPaymentCommand(
                patient.id(), PaymentMethod.CASH, null, "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(invoiceLineId, 1_000_000)), admin));

        assertThat(billingGate.isSatisfied(invoiceLineId)).as("paid in full — now satisfied").isTrue();

        Encounter afterGate = moveToAwaitingConsultService.moveToAwaitingConsult(checkedIn.id(), admin);

        assertThat(afterGate.state()).isEqualTo(EncounterState.AWAITING_CONSULT);
    }

    // 02-billing-architecture.md §5: the whole design in one sentence —
    // "the charge sits unpaid on the encounter." The money is tracked,
    // the patient is not delayed.
    @Test
    void immediateTriageSkipsAwaitingPaymentEntirelyWithTheChargeStillUnpaid() {
        UUID admin = bootstrapAdminId();
        UUID doctorId = insertFixtureDoctor();

        Patient patient = registerPatientService.register(patientCommand("Joseph", "Mwita", admin));
        Encounter checkedIn = openEncounterService.checkIn(
                new OpenEncounterCommand(patient.id(), insertFixtureDepartment(), null, admin));

        submitTriageAssessmentService.submit(triageCommand(
                checkedIn, doctorId, TriagePriority.IMMEDIATE, ImmediateReason.ACTIVE_UNCONTROLLED_BLEEDING));

        String state = jdbcTemplate.queryForObject("SELECT state FROM encounter WHERE id = :id",
                new MapSqlParameterSource("id", checkedIn.id()), String.class);
        assertThat(state).as("AWAITING_PAYMENT skipped entirely").isEqualTo(EncounterState.AWAITING_CONSULT.name());

        Integer bypassCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_bypass WHERE encounter_id = :id AND status = 'ACTIVE'",
                new MapSqlParameterSource("id", checkedIn.id()), Integer.class);
        assertThat(bypassCount).isEqualTo(1);

        UUID invoiceLineId = consultationLineIdFor(checkedIn.id());
        Object[] amounts = jdbcTemplate.queryForObject("""
                        SELECT i.gross_total_minor, i.outstanding_total_minor
                        FROM invoice i JOIN invoice_line il ON il.invoice_id = i.id
                        WHERE il.id = :lineId
                        """,
                new MapSqlParameterSource("lineId", invoiceLineId),
                (rs, rowNum) -> new Object[]{rs.getLong("gross_total_minor"), rs.getLong("outstanding_total_minor")});
        assertThat(amounts[0]).as("charge posted, never skipped").isEqualTo(1_000_000L);
        assertThat(amounts[1]).as("charge sits unpaid on the encounter").isEqualTo(1_000_000L);

        Integer paymentCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment WHERE patient_id = :patientId",
                new MapSqlParameterSource("patientId", patient.id()), Integer.class);
        assertThat(paymentCount).isEqualTo(0);

        assertThat(billingGate.isSatisfied(invoiceLineId)).as("bypass, not payment, opens it").isTrue();
    }

    // Phase 4 build brief item 3 — THE GATE AT SPECIMEN COLLECTION. Extends
    // the described path one more leg: register -> triage -> pay -> consult
    // -> order (lab) -> [gate]. Deliberately does NOT pay the consultation
    // charge first: L5/root CLAUDE.md "Billing" says gating is per invoice
    // line, never per invoice, so an unrelated unpaid consultation line
    // must have zero effect on whether the lab charge's own line gates.
    @Test
    void labOrderGatesOnSpecimenCollectionIndependentlyOfTheUnpaidConsultationLine() {
        UUID admin = bootstrapAdminId();
        UUID doctorId = insertFixtureDoctor();
        UUID labTechId = insertFixtureDoctor(); // any authenticated actor — CollectSpecimenService itself has no role check, only its controller does

        Patient patient = registerPatientService.register(patientCommand("Grace", "Mushi", admin));
        Encounter checkedIn = openEncounterService.checkIn(
                new OpenEncounterCommand(patient.id(), insertFixtureDepartment(), null, admin));
        submitTriageAssessmentService.submit(triageCommand(checkedIn, doctorId, TriagePriority.NON_URGENT, null));

        UUID labTestId = insertFixtureLabTest("LAB-CBC-" + UUID.randomUUID(), 5_000, admin);
        LabOrder order = placeLabOrderService.placeOrder(new PlaceLabOrderCommand(
                checkedIn.id(), labTestCode(labTestId), "suspected anaemia", Urgency.ROUTINE, doctorId));

        assertThat(billingGate.isSatisfied(order.invoiceLineId())).as("lab charge unpaid").isFalse();
        assertThatThrownBy(() -> collectSpecimenService.collect(
                new CollectSpecimenCommand(order.id(), SpecimenQuality.ADEQUATE, null, labTechId)))
                .isInstanceOfSatisfying(BillingGateRejectedException.class, ex -> {
                    assertThat(ex.invoiceLineId()).isEqualTo(order.invoiceLineId());
                    assertThat(ex.outstandingAmount().amountMinor()).isEqualTo(5_000);
                });
        assertThat(countWhere("specimen", "lab_order_id", order.id())).as("no accession burned on a rejected gate").isZero();

        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        postPaymentService.recordPayment(new PostPaymentCommand(
                patient.id(), PaymentMethod.CASH, null, "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(order.invoiceLineId(), 5_000)), admin));
        assertThat(billingGate.isSatisfied(order.invoiceLineId())).isTrue();

        assertThat(billingGate.isSatisfied(consultationLineIdFor(checkedIn.id())))
                .as("the consultation line the lab charge never touched").isFalse();

        Specimen collected = collectSpecimenService.collect(
                new CollectSpecimenCommand(order.id(), SpecimenQuality.ADEQUATE, null, labTechId));
        assertThat(collected.status()).isEqualTo(SpecimenStatus.IN_PROGRESS);
        assertThat(collected.accessionNo()).isNotBlank();
    }

    // §4.3: "Recollection is not re-charged. The collection failed, not
    // the patient." — proven by a rejected specimen followed by a second,
    // successful one against the same order, with no second charge posted.
    @Test
    void aRejectedSpecimenAllowsRecollectionWithoutASecondCharge() {
        UUID admin = bootstrapAdminId();
        UUID doctorId = insertFixtureDoctor();

        Patient patient = registerPatientService.register(patientCommand("Edwin", "Mushi", admin));
        Encounter checkedIn = openEncounterService.checkIn(
                new OpenEncounterCommand(patient.id(), insertFixtureDepartment(), null, admin));
        submitTriageAssessmentService.submit(triageCommand(checkedIn, doctorId, TriagePriority.NON_URGENT, null));

        UUID labTestId = insertFixtureLabTest("LAB-CHEM-" + UUID.randomUUID(), 8_000, admin);
        LabOrder order = placeLabOrderService.placeOrder(new PlaceLabOrderCommand(
                checkedIn.id(), labTestCode(labTestId), null, Urgency.ROUTINE, doctorId));

        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        postPaymentService.recordPayment(new PostPaymentCommand(
                patient.id(), PaymentMethod.CASH, null, "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(order.invoiceLineId(), 8_000)), admin));

        Specimen rejected = collectSpecimenService.collect(new CollectSpecimenCommand(
                order.id(), SpecimenQuality.HAEMOLYSED, "visibly haemolysed on receipt", doctorId));
        assertThat(rejected.status()).isEqualTo(SpecimenStatus.REJECTED);
        assertThat(rejected.rejectionReason()).isEqualTo("visibly haemolysed on receipt");

        Specimen recollected = collectSpecimenService.collect(new CollectSpecimenCommand(
                order.id(), SpecimenQuality.ADEQUATE, null, doctorId));
        assertThat(recollected.status()).isEqualTo(SpecimenStatus.IN_PROGRESS);
        assertThat(recollected.accessionNo()).isNotEqualTo(rejected.accessionNo());

        assertThat(countWhere("specimen", "lab_order_id", order.id())).isEqualTo(2);
        Integer chargeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invoice_line WHERE source_event_type = 'LabTestOrdered' AND source_event_id = :labOrderId",
                new MapSqlParameterSource("labOrderId", order.id()), Integer.class);
        assertThat(chargeCount).as("recollection never posts a second charge").isEqualTo(1);
    }

    // §4.2: "quality_note Required when quality != ADEQUATE" — rejected
    // before the gate is even checked, since there's nothing valid to act on.
    @Test
    void collectingASpecimenWithAnAbnormalQualityAndNoNoteIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID doctorId = insertFixtureDoctor();

        Patient patient = registerPatientService.register(patientCommand("Happiness", "Kimaro", admin));
        Encounter checkedIn = openEncounterService.checkIn(
                new OpenEncounterCommand(patient.id(), insertFixtureDepartment(), null, admin));
        submitTriageAssessmentService.submit(triageCommand(checkedIn, doctorId, TriagePriority.NON_URGENT, null));

        UUID labTestId = insertFixtureLabTest("LAB-URINE-" + UUID.randomUUID(), 3_000, admin);
        LabOrder order = placeLabOrderService.placeOrder(new PlaceLabOrderCommand(
                checkedIn.id(), labTestCode(labTestId), null, Urgency.ROUTINE, doctorId));

        assertThatThrownBy(() -> collectSpecimenService.collect(
                new CollectSpecimenCommand(order.id(), SpecimenQuality.CLOTTED, null, doctorId)))
                .isInstanceOf(MissingQualityNoteException.class);
    }

    // Phase 4 build brief item 5 — the one explicitly required to "prove
    // with a test": "Critical results release REGARDLESS of payment
    // status. There must be no code path where an unpaid balance withholds
    // a life-threatening value." IMMEDIATE triage's bypass satisfies the
    // specimen-collection gate without a single tzs of real payment — the
    // lab charge sits outstanding throughout, same as
    // immediateTriageSkipsAwaitingPaymentEntirelyWithTheChargeStillUnpaid
    // proves for the consultation charge. RecordLabResultService never
    // calls BillingFacade at all (see its class comment), so this is a
    // structural guarantee, not an if-branch this test happens to hit.
    @Test
    void criticalLabResultReleasesAndAlertsWhileTheUnderlyingChargeIsStillUnpaid() {
        UUID admin = bootstrapAdminId();
        UUID doctorId = insertFixtureDoctor();

        Patient patient = registerPatientService.register(patientCommand("Neema", "Paul", admin));
        Encounter checkedIn = openEncounterService.checkIn(
                new OpenEncounterCommand(patient.id(), insertFixtureDepartment(), null, admin));
        submitTriageAssessmentService.submit(triageCommand(
                checkedIn, doctorId, TriagePriority.IMMEDIATE, ImmediateReason.ACTIVE_UNCONTROLLED_BLEEDING));

        LabTestWithParameter fixture = insertFixtureLabTestWithParameter("LAB-CHEM7-" + UUID.randomUUID(), 6_000, admin);
        LabOrder order = placeLabOrderService.placeOrder(new PlaceLabOrderCommand(
                checkedIn.id(), labTestCode(fixture.labTestId()), "hyperkalaemia workup", Urgency.STAT, doctorId));

        // Gate satisfied by the IMMEDIATE bypass alone — never paid.
        assertThat(billingGate.isSatisfied(order.invoiceLineId())).isTrue();
        Specimen specimen = collectSpecimenService.collect(
                new CollectSpecimenCommand(order.id(), SpecimenQuality.ADEQUATE, null, doctorId));

        RecordLabResultResult recorded = recordLabResultService.record(new RecordLabResultCommand(
                specimen.id(), doctorId, "ISE", null, "Critically elevated potassium", null, null, null,
                "s3://lab-machine-output/test-object-key",
                List.of(new ParameterEntry(fixture.parameterId(), 7.2, null, null)), List.of()));

        assertThat(recorded.result().releasedAt()).as("released immediately, nothing withheld").isNotNull();
        LabResultParameterValue value = recorded.parameterValues().get(0);
        assertThat(value.flag()).isEqualTo(ResultFlag.CRITICAL);
        assertThat(recorded.criticalNotifications()).hasSize(1);
        assertThat(recorded.criticalNotifications().get(0).notifiedUserId()).isEqualTo(doctorId);

        // The lab charge itself: still zero paid, throughout.
        Object[] amounts = jdbcTemplate.queryForObject("""
                        SELECT i.gross_total_minor, i.outstanding_total_minor
                        FROM invoice i JOIN invoice_line il ON il.invoice_id = i.id
                        WHERE il.id = :lineId
                        """,
                new MapSqlParameterSource("lineId", order.invoiceLineId()),
                (rs, rowNum) -> new Object[]{rs.getLong("gross_total_minor"), rs.getLong("outstanding_total_minor")});
        assertThat(amounts[0]).isEqualTo(6_000L);
        assertThat(amounts[1]).as("still fully outstanding when the critical result released").isEqualTo(6_000L);
    }

    private record LabTestWithParameter(UUID labTestId, UUID parameterId) {
    }

    // Same catalogue-fixture shape as insertFixtureLabTest, plus one
    // parameter with a critical tier (potassium: normal 3.5-5.0, critical
    // outside 2.5-6.5) so a test can exercise §7's alerting path.
    private LabTestWithParameter insertFixtureLabTestWithParameter(String code, long priceMinor, UUID createdBy) {
        UUID labTestId = insertFixtureLabTest(code, priceMinor, createdBy);
        LabTestParameter parameter = labTestParameterRepository.insert(
                LabTestParameter.create(labTestId, "K", "Potassium", "mmol/L", 1, "ISE", "2823-3"));
        labReferenceRangeRepository.insert(LabReferenceRange.create(
                parameter.id(), 216, null, Sex.ANY, 0.0, 15.0, 3.5, 5.0, 2.5, 6.5, createdBy));
        return new LabTestWithParameter(labTestId, parameter.id());
    }

    private int countWhere(String table, String column, UUID value) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = :value",
                new MapSqlParameterSource("value", value), Integer.class);
    }

    private String labTestCode(UUID labTestId) {
        LabTest labTest = labTestRepository.findById(labTestId).orElseThrow();
        return labTest.code();
    }

    private UUID insertFixtureLabTest(String code, long priceMinor, UUID createdBy) {
        jdbcTemplate.update("INSERT INTO service_item (code, name, category) VALUES (:code, :code, 'LAB')",
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

        LabTest labTest = labTestRepository.insert(LabTest.create(
                code, code, "BLOOD", "EDTA_TUBE", 24, VerificationLevel.NONE, true));
        return labTest.id();
    }

    private UUID consultationLineIdFor(UUID encounterId) {
        return jdbcTemplate.queryForObject("""
                        SELECT il.id FROM invoice_line il JOIN invoice i ON i.id = il.invoice_id
                        WHERE i.encounter_id = :encounterId AND il.source_event_type = 'TriageCompleted'
                        """,
                new MapSqlParameterSource("encounterId", encounterId), UUID.class);
    }

    private RegisterPatientCommand patientCommand(String givenName, String familyName, UUID actor) {
        return new RegisterPatientCommand(
                givenName, null, familyName, LocalDate.of(1990, 4, 12), null, null,
                "FEMALE", "SINGLE", null, "+255700" + (100000 + Math.abs((givenName + familyName).hashCode() % 900000)),
                null, null,
                new NextOfKin(null, null, "Next Kin", "SIBLING", "+255711000000", false),
                List.of(), null, BOOTSTRAP_FACILITY_ID, actor, null);
    }

    private SubmitTriageAssessmentCommand triageCommand(
            Encounter encounter, UUID providerId, TriagePriority priority, ImmediateReason immediateReason
    ) {
        return new SubmitTriageAssessmentCommand(
                encounter.id(), encounter.version(), providerId,
                "Fever and headache", "Since yesterday", "NKDA", "None", ModeOfAmbulation.WALKING,
                false, null, null, false, null, false, PregnancyStatus.NO, null,
                priority, immediateReason, immediateReason == ImmediateReason.OTHER ? "n/a" : null,
                null, null,
                36.8, 120, 80, 75, 16, 98, 70, 175, "ALERT", null,
                providerId, Instant.now());
    }

    private UUID insertFixtureDoctor() {
        UUID userId = jdbcTemplate.queryForObject("""
                        INSERT INTO app_user (username, password_hash)
                        VALUES (:username, 'x')
                        RETURNING id
                        """,
                new MapSqlParameterSource("username", "test-doctor-" + UUID.randomUUID()), UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO role_assignment (user_id, role_id, scope_type, scope_id)
                        SELECT :userId, id, 'GLOBAL', NULL FROM role WHERE code = 'DOCTOR'
                        """,
                new MapSqlParameterSource("userId", userId));
        return userId;
    }

    private UUID insertFixtureDepartment() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, code, name)
                        VALUES (:facilityId, :code, 'Test Department')
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
