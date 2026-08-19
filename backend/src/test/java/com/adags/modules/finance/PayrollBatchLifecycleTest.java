package com.adags.modules.finance;

import com.adags.modules.finance.application.DefineDeductionRuleCommand;
import com.adags.modules.finance.application.DefineDeductionRuleService;
import com.adags.modules.finance.application.DisbursePayrollBatchCommand;
import com.adags.modules.finance.application.DisbursePayrollBatchService;
import com.adags.modules.finance.application.GeneratePayrollBatchService;
import com.adags.modules.finance.application.PayrollBatchPreview;
import com.adags.modules.finance.application.PreviewPayrollBatchService;
import com.adags.modules.finance.application.ReversePayrollBatchCommand;
import com.adags.modules.finance.application.ReversePayrollBatchService;
import com.adags.modules.finance.domain.BatchSelfDisbursementNotAllowedException;
import com.adags.modules.finance.domain.BatchStatus;
import com.adags.modules.finance.domain.CalculationMethod;
import com.adags.modules.finance.domain.DeductionRule;
import com.adags.modules.finance.domain.DuplicateBatchException;
import com.adags.modules.finance.domain.EmploymentType;
import com.adags.modules.finance.domain.PaymentMethod;
import com.adags.modules.finance.domain.SalaryDisbursementBatch;
import com.adags.modules.finance.domain.SalaryDisbursementBatchRepository;
import com.adags.modules.finance.domain.SalaryRecordRepository;
import com.adags.modules.finance.domain.Staff;
import com.adags.modules.finance.domain.StaffRepository;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 18-admin.md §5.1/§9. preview -> generate -> disburse -> (optionally)
// reverse, all gated on PAYROLL_MANAGE. root CLAUDE.md "Authorisation":
// CHECK (generated_by != disbursed_by). root CLAUDE.md "Concurrency":
// UNIQUE(batch_type, month, year) — "running twice for one month must
// not pay twice."
class PayrollBatchLifecycleTest extends IntegrationTestSupport {

    // Anchored safely in the future: PayrollComputation resolves
    // deduction rules against the 1st of the payroll period, so a rule
    // defined "today" (effective_from = LocalDate.now()) would not yet
    // apply to a period whose first day has already passed.
    private static final LocalDate PERIOD = LocalDate.now().plusMonths(2).withDayOfMonth(1);
    private static final int MONTH = PERIOD.getMonthValue();
    private static final int YEAR = PERIOD.getYear();

    @Autowired
    private DefineDeductionRuleService defineDeductionRuleService;
    @Autowired
    private PreviewPayrollBatchService previewPayrollBatchService;
    @Autowired
    private GeneratePayrollBatchService generatePayrollBatchService;
    @Autowired
    private DisbursePayrollBatchService disbursePayrollBatchService;
    @Autowired
    private ReversePayrollBatchService reversePayrollBatchService;
    @Autowired
    private StaffRepository staffRepository;
    @Autowired
    private SalaryDisbursementBatchRepository batchRepository;
    @Autowired
    private SalaryRecordRepository salaryRecordRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE salary_record, salary_disbursement_batch, staff, deduction_rule",
                new MapSqlParameterSource());
    }

    @Test
    void previewMatchesExactlyWhatGenerateCommits() {
        UUID admin = bootstrapAdminId();
        insertFixtureStaff(1_000_000);
        defineDeductionRuleService.define(new DefineDeductionRuleCommand(EmploymentType.PERMANENT, "PAYE",
                CalculationMethod.PERCENTAGE, new BigDecimal("10"), null, false, admin));

        PayrollBatchPreview preview = previewPayrollBatchService.preview(MONTH, YEAR);
        assertThat(preview.headcount()).isEqualTo(1);
        assertThat(preview.grossTotal().amountMinor()).isEqualTo(1_000_000);
        assertThat(preview.netTotal().amountMinor()).isEqualTo(900_000);

        SalaryDisbursementBatch batch = generatePayrollBatchService.generate(MONTH, YEAR, admin);

        assertThat(batch.headcount()).isEqualTo(preview.headcount());
        assertThat(batch.grossTotal().amountMinor()).isEqualTo(preview.grossTotal().amountMinor());
        assertThat(batch.netTotal().amountMinor()).isEqualTo(preview.netTotal().amountMinor());
        assertThat(batch.status()).isEqualTo(BatchStatus.PENDING);
    }

    // §9.1: employer-paid rules (SDL, WCF) must never reduce net pay —
    // only the employee-deducted PAYE line should apply.
    @Test
    void employerPaidDeductionsNeverReduceNetPay() {
        UUID admin = bootstrapAdminId();
        insertFixtureStaff(1_000_000);
        defineDeductionRuleService.define(new DefineDeductionRuleCommand(EmploymentType.PERMANENT, "PAYE",
                CalculationMethod.PERCENTAGE, new BigDecimal("10"), null, false, admin));
        defineDeductionRuleService.define(new DefineDeductionRuleCommand(EmploymentType.PERMANENT, "SDL",
                CalculationMethod.PERCENTAGE, new BigDecimal("4"), null, true, admin));

        PayrollBatchPreview preview = previewPayrollBatchService.preview(MONTH, YEAR);

        // Only PAYE's 10% applies; SDL's 4% must not appear.
        assertThat(preview.netTotal().amountMinor()).isEqualTo(900_000);
    }

    @Test
    void generatingTwiceForTheSamePeriodIsRejected() {
        UUID admin = bootstrapAdminId();
        insertFixtureStaff(1_000_000);
        generatePayrollBatchService.generate(MONTH, YEAR, admin);

        assertThatThrownBy(() -> generatePayrollBatchService.generate(MONTH, YEAR, admin))
                .isInstanceOf(DuplicateBatchException.class);
    }

    @Test
    void aGeneratedBatchCanBeDisbursedByADistinctUser() {
        UUID admin = bootstrapAdminId();
        UUID disburser = insertFixtureUser();
        insertFixtureStaff(1_000_000);
        SalaryDisbursementBatch batch = generatePayrollBatchService.generate(MONTH, YEAR, admin);

        SalaryDisbursementBatch disbursed = disbursePayrollBatchService.disburse(
                new DisbursePayrollBatchCommand(batch.id(), disburser, PaymentMethod.BANK_TRANSFER));

        assertThat(disbursed.status()).isEqualTo(BatchStatus.DISBURSED);
        assertThat(disbursed.disbursedBy()).isEqualTo(disburser);
        List<?> records = salaryRecordRepository.findByBatchId(batch.id());
        assertThat(records).hasSize(1);
    }

    @Test
    void theGeneratorCannotAlsoDisburseTheSameBatch() {
        UUID admin = bootstrapAdminId();
        insertFixtureStaff(1_000_000);
        SalaryDisbursementBatch batch = generatePayrollBatchService.generate(MONTH, YEAR, admin);

        assertThatThrownBy(() -> disbursePayrollBatchService.disburse(
                new DisbursePayrollBatchCommand(batch.id(), admin, PaymentMethod.BANK_TRANSFER)))
                .isInstanceOf(BatchSelfDisbursementNotAllowedException.class);

        SalaryDisbursementBatch reloaded = batchRepository.findById(batch.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(BatchStatus.PENDING);
    }

    // root CLAUDE.md "Authorisation": the database CHECK is the real
    // guard, proven by going around the service entirely.
    @Test
    void theDatabaseRejectsSelfDisbursementEvenBypassingTheServiceLayer() {
        UUID admin = bootstrapAdminId();
        insertFixtureStaff(1_000_000);
        SalaryDisbursementBatch batch = generatePayrollBatchService.generate(MONTH, YEAR, admin);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE salary_disbursement_batch
                        SET status = 'DISBURSED', disbursed_by = :admin, disbursed_at = now()
                        WHERE id = :id
                        """,
                new MapSqlParameterSource().addValue("id", batch.id()).addValue("admin", admin)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void aDisbursedBatchCanBeReversedAsAWhole() {
        UUID admin = bootstrapAdminId();
        UUID disburser = insertFixtureUser();
        insertFixtureStaff(1_000_000);
        SalaryDisbursementBatch batch = generatePayrollBatchService.generate(MONTH, YEAR, admin);
        disbursePayrollBatchService.disburse(new DisbursePayrollBatchCommand(batch.id(), disburser, PaymentMethod.BANK_TRANSFER));

        SalaryDisbursementBatch reversed = reversePayrollBatchService.reverse(
                new ReversePayrollBatchCommand(batch.id(), admin, "duplicate run discovered after disbursement"));

        assertThat(reversed.status()).isEqualTo(BatchStatus.REVERSED);
        assertThat(reversed.reversalReason()).isEqualTo("duplicate run discovered after disbursement");
    }

    @Test
    void reversingWithoutAReasonIsRejected() {
        UUID admin = bootstrapAdminId();
        insertFixtureStaff(1_000_000);
        SalaryDisbursementBatch batch = generatePayrollBatchService.generate(MONTH, YEAR, admin);

        assertThatThrownBy(() -> reversePayrollBatchService.reverse(
                new ReversePayrollBatchCommand(batch.id(), admin, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 18-admin.md §9: "define -> closes the current row, opens a new one"
    // — same versioning as PriceListEntry/SchemeCoverageRule.
    @Test
    void redefiningARuleClosesThePriorVersionRatherThanEditingItInPlace() {
        UUID admin = bootstrapAdminId();
        DeductionRule first = defineDeductionRuleService.define(new DefineDeductionRuleCommand(
                EmploymentType.PERMANENT, "PAYE", CalculationMethod.PERCENTAGE, new BigDecimal("10"), null, false, admin));
        DeductionRule second = defineDeductionRuleService.define(new DefineDeductionRuleCommand(
                EmploymentType.PERMANENT, "PAYE", CalculationMethod.PERCENTAGE, new BigDecimal("12"), null, false, admin));

        Integer openRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM deduction_rule WHERE employment_type = 'PERMANENT' AND deduction_code = 'PAYE' AND effective_to IS NULL",
                new MapSqlParameterSource(), Integer.class);
        assertThat(openRows).isEqualTo(1);
        assertThat(second.id()).isNotEqualTo(first.id());
    }

    private void insertFixtureStaff(long basicSalaryMinor) {
        staffRepository.insert(new Staff(null, null, "Test Staff " + UUID.randomUUID(), EmploymentType.PERMANENT,
                new com.adags.shared.kernel.Money(basicSalaryMinor, "TZS"), LocalDate.of(2020, 1, 1), true, null));
    }

    private UUID insertFixtureUser() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app_user (username, password_hash) VALUES (:username, 'x') RETURNING id",
                new MapSqlParameterSource("username", "test-disburser-" + UUID.randomUUID()), UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
