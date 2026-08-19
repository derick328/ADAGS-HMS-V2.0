package com.adags.modules.reporting.application;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

// 18-admin.md §5: "by category, monthly trend." Only APPROVED expenses
// count — DRAFT/PENDING_APPROVAL are not yet a real spend and VOID was
// reversed, same reasoning as GeneratePayrollBatchService only ever
// disbursing PENDING batches.
//
// expense (V48) carries no facility_id — item 4's brief didn't ask for
// one, so this report is hospital-wide, not facility-scoped. That is
// data that was never captured, not a filter this report can honestly
// offer.
@Service
public class ExpenseReportService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ExpenseReportService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ExpenseCategoryTotal> byCategory(LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("from", from).addValue("to", to);
        return jdbcTemplate.query("""
                SELECT category, COALESCE(SUM(amount_minor), 0) AS total_minor
                FROM expense
                WHERE status = 'APPROVED' AND expense_date BETWEEN :from AND :to
                GROUP BY category
                ORDER BY category
                """, params, categoryRowMapper());
    }

    public List<ExpenseMonthlyTotal> monthlyTrend(LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("from", from).addValue("to", to);
        return jdbcTemplate.query("""
                SELECT EXTRACT(YEAR FROM expense_date)::int AS yr, EXTRACT(MONTH FROM expense_date)::int AS mo,
                       COALESCE(SUM(amount_minor), 0) AS total_minor
                FROM expense
                WHERE status = 'APPROVED' AND expense_date BETWEEN :from AND :to
                GROUP BY yr, mo
                ORDER BY yr, mo
                """, params, monthlyRowMapper());
    }

    private RowMapper<ExpenseCategoryTotal> categoryRowMapper() {
        return (rs, rowNum) -> new ExpenseCategoryTotal(rs.getString("category"), rs.getLong("total_minor"), "TZS");
    }

    private RowMapper<ExpenseMonthlyTotal> monthlyRowMapper() {
        return (rs, rowNum) -> new ExpenseMonthlyTotal(rs.getInt("yr"), rs.getInt("mo"), rs.getLong("total_minor"), "TZS");
    }
}
