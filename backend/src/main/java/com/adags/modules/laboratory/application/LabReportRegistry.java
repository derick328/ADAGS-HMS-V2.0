package com.adags.modules.laboratory.application;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LabReportRegistry {
    private final NamedParameterJdbcTemplate jdbc;

    public LabReportRegistry(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void register(String key, UUID orderId, UUID uploadedBy) {
        jdbc.update("INSERT INTO lab_report_upload (object_key, lab_order_id, uploaded_by) VALUES (:key, :orderId, :userId)",
                new MapSqlParameterSource().addValue("key", key).addValue("orderId", orderId).addValue("userId", uploadedBy));
    }

    public boolean belongsToOrder(String key, UUID orderId, UUID userId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM lab_report_upload WHERE object_key = :key AND lab_order_id = :orderId AND uploaded_by = :userId",
                new MapSqlParameterSource().addValue("key", key).addValue("orderId", orderId).addValue("userId", userId), Integer.class);
        return count != null && count == 1;
    }
}
