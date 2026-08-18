package com.adags.modules.billing;

import com.adags.modules.billing.application.CloseCashierShiftService;
import com.adags.modules.billing.application.OpenCashierShiftService;
import com.adags.modules.billing.application.ReconcileCashierShiftService;
import com.adags.modules.billing.domain.CashierShift;
import com.adags.modules.billing.domain.CashierShiftAlreadyOpenException;
import com.adags.modules.billing.domain.CashierShiftNotClosedException;
import com.adags.modules.billing.domain.CashierShiftStatus;
import com.adags.modules.billing.domain.SelfReconciliationNotAllowedException;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 02-billing-architecture.md §9.1: the cashier declares, the accountant
// reconciles — never the same person.
class CashierShiftTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private OpenCashierShiftService openCashierShiftService;

    @Autowired
    private CloseCashierShiftService closeCashierShiftService;

    @Autowired
    private ReconcileCashierShiftService reconcileCashierShiftService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE payment, cashier_shift, patient CASCADE", new MapSqlParameterSource());
    }

    @Test
    void opensAShiftForACashier() {
        UUID admin = bootstrapAdminId();

        CashierShift shift = openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);

        assertThat(shift.status()).isEqualTo(CashierShiftStatus.OPEN);
        assertThat(shift.openedBy()).isEqualTo(admin);
    }

    @Test
    void aCashierCannotOpenASecondShiftWhileOneIsOpen() {
        UUID admin = bootstrapAdminId();
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);

        assertThatThrownBy(() -> openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin))
                .isInstanceOf(CashierShiftAlreadyOpenException.class);
    }

    // The database constraint behind the friendly exception — proven
    // independently, same as every other partial-unique-index test here.
    @Test
    void theDatabaseRejectsASecondOpenShiftEvenBypassingTheAppCheck() {
        UUID admin = bootstrapAdminId();
        jdbcTemplate.update("INSERT INTO cashier_shift (facility_id, opened_by) VALUES (:facilityId, :openedBy)",
                new MapSqlParameterSource().addValue("facilityId", BOOTSTRAP_FACILITY_ID).addValue("openedBy", admin));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO cashier_shift (facility_id, opened_by) VALUES (:facilityId, :openedBy)",
                new MapSqlParameterSource().addValue("facilityId", BOOTSTRAP_FACILITY_ID).addValue("openedBy", admin)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void closingWithNoPaymentsRecordsZeroExpectedAndVariance() {
        UUID admin = bootstrapAdminId();
        CashierShift shift = openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);

        CashierShift closed = closeCashierShiftService.close(shift.id(), admin, new Money(5_000, "TZS"));

        assertThat(closed.status()).isEqualTo(CashierShiftStatus.CLOSED);
        assertThat(closed.expectedCash()).isEqualTo(new Money(0, "TZS"));
        assertThat(closed.countedCash()).isEqualTo(new Money(5_000, "TZS"));
        assertThat(closed.varianceCash()).isEqualTo(new Money(5_000, "TZS"));
    }

    @Test
    void aClosedShiftCannotReconcileToTheCashierWhoClosedIt() {
        UUID admin = bootstrapAdminId();
        CashierShift shift = openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        closeCashierShiftService.close(shift.id(), admin, new Money(0, "TZS"));

        assertThatThrownBy(() -> reconcileCashierShiftService.reconcile(shift.id(), admin))
                .isInstanceOf(SelfReconciliationNotAllowedException.class);
    }

    @Test
    void aDifferentUserMayReconcileAClosedShift() {
        UUID admin = bootstrapAdminId();
        UUID accountant = insertFixtureUser();
        CashierShift shift = openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        closeCashierShiftService.close(shift.id(), admin, new Money(0, "TZS"));

        CashierShift reconciled = reconcileCashierShiftService.reconcile(shift.id(), accountant);

        assertThat(reconciled.status()).isEqualTo(CashierShiftStatus.RECONCILED);
        assertThat(reconciled.reconciledBy()).isEqualTo(accountant);
    }

    // "Shift close takes a lock; late payments are rejected with a
    // prompt to open a new shift" — closing an already-closed shift.
    @Test
    void closingAnAlreadyClosedShiftIsRejected() {
        UUID admin = bootstrapAdminId();
        CashierShift shift = openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        closeCashierShiftService.close(shift.id(), admin, new Money(0, "TZS"));

        assertThatThrownBy(() -> closeCashierShiftService.close(shift.id(), admin, new Money(0, "TZS")))
                .isInstanceOf(com.adags.modules.billing.domain.CashierShiftNotOpenException.class);
    }

    @Test
    void reconcilingAnOpenShiftIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID accountant = insertFixtureUser();
        CashierShift shift = openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);

        assertThatThrownBy(() -> reconcileCashierShiftService.reconcile(shift.id(), accountant))
                .isInstanceOf(CashierShiftNotClosedException.class);
    }

    private UUID insertFixtureUser() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO app_user (username, password_hash)
                        VALUES (:username, 'x')
                        RETURNING id
                        """,
                new MapSqlParameterSource("username", "test-accountant-" + UUID.randomUUID()), UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
