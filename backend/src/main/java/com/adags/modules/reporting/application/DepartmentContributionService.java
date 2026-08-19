package com.adags.modules.reporting.application;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 18-admin.md / 02-billing-architecture.md §13. Invoices with no
// encounter (a pharmacy walk-in or records fee — V16) cannot be
// attributed to a department and are excluded here by the INNER JOINs
// to encounter, not summed into some "unattributed" bucket that would
// silently overstate a real department's contribution.
@Service
public class DepartmentContributionService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DepartmentContributionService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DepartmentContribution> generate(LocalDate from, LocalDate to, UUID facilityId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", from).addValue("to", to).addValue("facilityId", facilityId);

        Map<UUID, String> departmentNames = new HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM department WHERE facility_id = :facilityId", params,
                (rs, rowNum) -> departmentNames.put((UUID) rs.getObject("id"), rs.getString("name")));

        Map<UUID, Long> cashByDepartment = amountByDepartment(jdbcTemplate.queryForList("""
                SELECT e.department_id AS department_id, SUM(pa.amount_minor - pa.refunded_minor) AS total_minor
                FROM payment_allocation pa
                JOIN payment p ON p.id = pa.payment_id
                JOIN invoice_line il ON il.id = pa.invoice_line_id
                JOIN invoice i ON i.id = il.invoice_id
                JOIN encounter e ON e.id = i.encounter_id
                WHERE p.status = 'SETTLED' AND p.received_at::date BETWEEN :from AND :to AND i.facility_id = :facilityId
                GROUP BY e.department_id
                """, params));

        Map<UUID, Long> insuranceByDepartment = amountByDepartment(jdbcTemplate.queryForList("""
                SELECT e.department_id AS department_id, SUM(i.insurance_portion_total_minor) AS total_minor
                FROM insurance_claim c
                JOIN invoice i ON i.id = c.invoice_id
                JOIN encounter e ON e.id = i.encounter_id
                WHERE c.created_at::date BETWEEN :from AND :to AND i.facility_id = :facilityId
                GROUP BY e.department_id
                """, params));

        Map<UUID, Long> pharmacyCostByDepartment = amountByDepartment(jdbcTemplate.queryForList("""
                SELECT e.department_id AS department_id, SUM(dl.quantity * dl.unit_cost_amount_minor) AS total_minor
                FROM dispense_line dl
                JOIN dispense d ON d.id = dl.dispense_id
                JOIN prescription_line pl ON pl.id = d.prescription_line_id
                JOIN prescription_order po ON po.id = pl.prescription_order_id
                JOIN encounter e ON e.id = po.encounter_id
                WHERE d.dispensed_at::date BETWEEN :from AND :to AND po.facility_id = :facilityId
                GROUP BY e.department_id
                """, params));

        return departmentNames.entrySet().stream()
                .map(entry -> {
                    UUID departmentId = entry.getKey();
                    long cash = cashByDepartment.getOrDefault(departmentId, 0L);
                    long insurance = insuranceByDepartment.getOrDefault(departmentId, 0L);
                    long pharmacyCost = pharmacyCostByDepartment.getOrDefault(departmentId, 0L);
                    return new DepartmentContribution(departmentId, entry.getValue(), cash, insurance, pharmacyCost,
                            cash + insurance - pharmacyCost, "TZS", false, false);
                })
                .filter(line -> line.cashCollectedMinor() != 0 || line.insuranceBilledMinor() != 0 || line.pharmacyCostMinor() != 0)
                .sorted((a, b) -> a.departmentName().compareTo(b.departmentName()))
                .toList();
    }

    private Map<UUID, Long> amountByDepartment(List<Map<String, Object>> rows) {
        Map<UUID, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            UUID departmentId = (UUID) row.get("department_id");
            Number totalMinor = (Number) row.get("total_minor");
            result.put(departmentId, totalMinor == null ? 0L : totalMinor.longValue());
        }
        return result;
    }
}
