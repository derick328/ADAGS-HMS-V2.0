package com.adags.modules.finance.infrastructure;

import com.adags.modules.finance.domain.Expense;
import com.adags.modules.finance.domain.ExpenseFrequency;
import com.adags.modules.finance.domain.ExpenseRepository;
import com.adags.modules.finance.domain.ExpenseStatus;
import com.adags.modules.finance.domain.InvalidExpenseTransitionException;
import com.adags.modules.finance.domain.PaymentMethod;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcExpenseRepository implements ExpenseRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, title, category, amount_minor, currency_code, expense_date, payment_method, paid_to,
                   receipt_number, notes, receipt_file_key, recurring, frequency, template_id, period, status,
                   created_by, created_at, approved_by, approved_at, approval_note, voided_by, voided_at, void_reason
            FROM expense
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcExpenseRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Expense insert(Expense expense) {
        String sql = """
                INSERT INTO expense (title, category, amount_minor, currency_code, expense_date, payment_method,
                                      paid_to, receipt_number, notes, receipt_file_key, recurring, frequency,
                                      template_id, period, created_by)
                VALUES (:title, :category, :amountMinor, :currencyCode, :expenseDate, :paymentMethod, :paidTo,
                        :receiptNumber, :notes, :receiptFileKey, :recurring, :frequency, :templateId, :period, :createdBy)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("title", expense.title())
                .addValue("category", expense.category())
                .addValue("amountMinor", expense.amount().amountMinor())
                .addValue("currencyCode", expense.amount().currencyCode())
                .addValue("expenseDate", expense.expenseDate())
                .addValue("paymentMethod", expense.paymentMethod() == null ? null : expense.paymentMethod().name())
                .addValue("paidTo", expense.paidTo())
                .addValue("receiptNumber", expense.receiptNumber())
                .addValue("notes", expense.notes())
                .addValue("receiptFileKey", expense.receiptFileKey())
                .addValue("recurring", expense.recurring())
                .addValue("frequency", expense.frequency() == null ? null : expense.frequency().name())
                .addValue("templateId", expense.templateId())
                .addValue("period", expense.period())
                .addValue("createdBy", expense.createdBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Expense(
                (UUID) rs.getObject("id"), expense.title(), expense.category(), expense.amount(), expense.expenseDate(),
                expense.paymentMethod(), expense.paidTo(), expense.receiptNumber(), expense.notes(),
                expense.receiptFileKey(), expense.recurring(), expense.frequency(), expense.templateId(), expense.period(),
                ExpenseStatus.DRAFT, expense.createdBy(), rs.getTimestamp("created_at").toInstant(),
                null, null, null, null, null, null));
    }

    @Override
    public Optional<Expense> findById(UUID id) {
        List<Expense> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<Expense> findByStatus(ExpenseStatus status) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE status = :status ORDER BY created_at ASC",
                new MapSqlParameterSource("status", status.name()), this::mapRow);
    }

    @Override
    public List<Expense> findActiveRecurringTemplates() {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE recurring = TRUE AND status != 'VOID' ORDER BY created_at ASC",
                new MapSqlParameterSource(), this::mapRow);
    }

    @Override
    public boolean existsByTemplateIdAndPeriod(UUID templateId, String period) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM expense WHERE template_id = :templateId AND period = :period",
                new MapSqlParameterSource().addValue("templateId", templateId).addValue("period", period), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public Expense submitForApproval(UUID expenseId) {
        return transition(expenseId, ExpenseStatus.DRAFT,
                "UPDATE expense SET status = 'PENDING_APPROVAL' WHERE id = :id AND status = 'DRAFT'", "submit for approval");
    }

    @Override
    public Expense withdraw(UUID expenseId) {
        return transition(expenseId, ExpenseStatus.PENDING_APPROVAL,
                "UPDATE expense SET status = 'DRAFT' WHERE id = :id AND status = 'PENDING_APPROVAL'", "withdraw");
    }

    @Override
    public Expense approve(UUID expenseId, UUID approvedBy, String approvalNote) {
        String sql = """
                UPDATE expense
                SET status = 'APPROVED', approved_by = :approvedBy, approved_at = now(), approval_note = :approvalNote
                WHERE id = :id AND status = 'PENDING_APPROVAL'
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", expenseId).addValue("approvedBy", approvedBy).addValue("approvalNote", approvalNote));
        if (updated == 0) {
            Expense current = findById(expenseId).orElseThrow();
            throw new InvalidExpenseTransitionException(expenseId, current.status(), "approve");
        }
        return findById(expenseId).orElseThrow();
    }

    @Override
    public Expense voidExpense(UUID expenseId, UUID voidedBy, String voidReason) {
        String sql = """
                UPDATE expense
                SET status = 'VOID', voided_by = :voidedBy, voided_at = now(), void_reason = :voidReason
                WHERE id = :id AND status != 'VOID'
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", expenseId).addValue("voidedBy", voidedBy).addValue("voidReason", voidReason));
        if (updated == 0) {
            throw new InvalidExpenseTransitionException(expenseId, ExpenseStatus.VOID, "void");
        }
        return findById(expenseId).orElseThrow();
    }

    private Expense transition(UUID expenseId, ExpenseStatus expectedCurrent, String sql, String action) {
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource("id", expenseId));
        if (updated == 0) {
            Expense current = findById(expenseId)
                    .orElseThrow(() -> new NoSuchElementException("Expense not found: " + expenseId));
            throw new InvalidExpenseTransitionException(expenseId, current.status(), action);
        }
        return findById(expenseId).orElseThrow();
    }

    private Expense mapRow(ResultSet rs, int rowNum) throws SQLException {
        String currencyCode = rs.getString("currency_code");
        String frequency = rs.getString("frequency");
        Object templateId = rs.getObject("template_id");
        Object approvedBy = rs.getObject("approved_by");
        Timestamp approvedAt = rs.getTimestamp("approved_at");
        Object voidedBy = rs.getObject("voided_by");
        Timestamp voidedAt = rs.getTimestamp("voided_at");
        return new Expense(
                (UUID) rs.getObject("id"),
                rs.getString("title"),
                rs.getString("category"),
                new Money(rs.getLong("amount_minor"), currencyCode),
                rs.getDate("expense_date").toLocalDate(),
                PaymentMethod.valueOf(rs.getString("payment_method")),
                rs.getString("paid_to"),
                rs.getString("receipt_number"),
                rs.getString("notes"),
                rs.getString("receipt_file_key"),
                rs.getBoolean("recurring"),
                frequency == null ? null : ExpenseFrequency.valueOf(frequency),
                templateId == null ? null : (UUID) templateId,
                rs.getString("period"),
                ExpenseStatus.valueOf(rs.getString("status")),
                (UUID) rs.getObject("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                approvedBy == null ? null : (UUID) approvedBy,
                approvedAt == null ? null : approvedAt.toInstant(),
                rs.getString("approval_note"),
                voidedBy == null ? null : (UUID) voidedBy,
                voidedAt == null ? null : voidedAt.toInstant(),
                rs.getString("void_reason"));
    }
}
