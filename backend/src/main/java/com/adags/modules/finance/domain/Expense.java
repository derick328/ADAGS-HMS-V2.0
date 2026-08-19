package com.adags.modules.finance.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// 18-admin.md §8.1/§8.2. status progresses DRAFT -> PENDING_APPROVAL ->
// APPROVED, with VOID reachable from any of those (always with a reason
// — root CLAUDE.md "Data": nothing financial is ever hard-deleted, no
// exception here for DRAFT).
//
// recurring=TRUE marks a row as a template: its own title/category/
// amount/paymentMethod/paidTo is what GenerateRecurringExpensesService
// copies into each period's occurrence. A generated occurrence carries
// recurring=FALSE with templateId pointing back here and period naming
// which occurrence it is ('2026-08' for a monthly template) — the
// (templateId, period) pair is the idempotency guard against generating
// the same month's bill twice.
public record Expense(
        UUID id,
        String title,
        String category,
        Money amount,
        LocalDate expenseDate,
        PaymentMethod paymentMethod,
        String paidTo,
        String receiptNumber,
        String notes,
        String receiptFileKey,
        boolean recurring,
        ExpenseFrequency frequency,
        UUID templateId,
        String period,
        ExpenseStatus status,
        UUID createdBy,
        Instant createdAt,
        UUID approvedBy,
        Instant approvedAt,
        String approvalNote,
        UUID voidedBy,
        Instant voidedAt,
        String voidReason
) {

    public static Expense record(String title, String category, Money amount, LocalDate expenseDate,
                                  PaymentMethod paymentMethod, String paidTo, String receiptNumber, String notes,
                                  String receiptFileKey, boolean recurring, ExpenseFrequency frequency,
                                  UUID templateId, String period, UUID createdBy) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required.");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category is required.");
        }
        if (paidTo == null || paidTo.isBlank()) {
            throw new IllegalArgumentException("paidTo is required.");
        }
        if (amount.amountMinor() <= 0) {
            throw new IllegalArgumentException("amount must be > 0.");
        }
        if (recurring && frequency == null) {
            throw new IllegalArgumentException("frequency is required when recurring is true.");
        }
        if (templateId != null && (period == null || period.isBlank())) {
            throw new IllegalArgumentException("period is required for a template-generated expense.");
        }
        return new Expense(null, title, category, amount, expenseDate, paymentMethod, paidTo, receiptNumber, notes,
                receiptFileKey, recurring, frequency, templateId, period, ExpenseStatus.DRAFT, createdBy, null,
                null, null, null, null, null, null);
    }
}
