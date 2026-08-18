package com.adags.modules.clinical.infrastructure;

import com.adags.modules.clinical.domain.Avpu;
import com.adags.modules.clinical.domain.RecordedVital;
import com.adags.modules.clinical.domain.VitalSigns;
import com.adags.modules.clinical.domain.VitalSignsContext;
import com.adags.modules.clinical.domain.VitalSignsRepository;
import com.adags.modules.clinical.domain.VitalType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcVitalSignsRepository implements VitalSignsRepository {

    private static final String SELECT_VITAL_SIGNS_COLUMNS = """
            SELECT id, encounter_id, context, context_ref_id, weight_kg, height_cm, bmi,
                   avpu, blood_glucose_mmol, recorded_by, recorded_at, occurred_at
            FROM vital_signs
            """;

    private static final String SELECT_READING_COLUMNS = """
            SELECT vital_signs_id, vital_type, value, applied_range_id, abnormal
            FROM vital_reading
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcVitalSignsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public VitalSigns insert(VitalSigns vitalSigns) {
        String sql = """
                INSERT INTO vital_signs (encounter_id, context, context_ref_id, weight_kg, height_cm,
                                          avpu, blood_glucose_mmol, recorded_by, occurred_at)
                VALUES (:encounterId, :context, :contextRefId, :weightKg, :heightCm,
                        :avpu, :bloodGlucoseMmol, :recordedBy, :occurredAt)
                RETURNING id, bmi, recorded_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("encounterId", vitalSigns.encounterId())
                .addValue("context", vitalSigns.context().name())
                .addValue("contextRefId", vitalSigns.contextRefId())
                .addValue("weightKg", vitalSigns.weightKg())
                .addValue("heightCm", vitalSigns.heightCm())
                .addValue("avpu", vitalSigns.avpu() == null ? null : vitalSigns.avpu().name(), Types.VARCHAR)
                .addValue("bloodGlucoseMmol", vitalSigns.bloodGlucoseMmol(), Types.NUMERIC)
                .addValue("recordedBy", vitalSigns.recordedBy())
                .addValue("occurredAt", java.sql.Timestamp.from(vitalSigns.occurredAt()));

        Map<String, Object> row = jdbcTemplate.queryForMap(sql, params);
        UUID id = (UUID) row.get("id");
        Double bmi = ((Number) row.get("bmi")).doubleValue();

        insertReadings(id, vitalSigns.readings());

        return new VitalSigns(
                id,
                vitalSigns.encounterId(),
                vitalSigns.context(),
                vitalSigns.contextRefId(),
                vitalSigns.readings(),
                vitalSigns.weightKg(),
                vitalSigns.heightCm(),
                bmi,
                vitalSigns.avpu(),
                vitalSigns.bloodGlucoseMmol(),
                vitalSigns.recordedBy(),
                ((java.sql.Timestamp) row.get("recorded_at")).toInstant(),
                vitalSigns.occurredAt());
    }

    private void insertReadings(UUID vitalSignsId, List<RecordedVital> readings) {
        String sql = """
                INSERT INTO vital_reading (vital_signs_id, vital_type, value, applied_range_id, abnormal)
                VALUES (:vitalSignsId, :vitalType, :value, :appliedRangeId, :abnormal)
                """;
        for (RecordedVital reading : readings) {
            jdbcTemplate.update(sql, new MapSqlParameterSource()
                    .addValue("vitalSignsId", vitalSignsId)
                    .addValue("vitalType", reading.vitalType().name())
                    .addValue("value", reading.value())
                    .addValue("appliedRangeId", reading.appliedRangeId())
                    .addValue("abnormal", reading.abnormal()));
        }
    }

    @Override
    public Optional<VitalSigns> findById(UUID id) {
        List<VitalSigns> results = jdbcTemplate.query(
                SELECT_VITAL_SIGNS_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(withReadings(results.get(0)));
    }

    @Override
    public List<VitalSigns> findByEncounterId(UUID encounterId) {
        List<VitalSigns> results = jdbcTemplate.query(
                SELECT_VITAL_SIGNS_COLUMNS + " WHERE encounter_id = :encounterId ORDER BY occurred_at DESC",
                new MapSqlParameterSource("encounterId", encounterId),
                this::mapRow);
        return results.stream().map(this::withReadings).toList();
    }

    private VitalSigns withReadings(VitalSigns withoutReadings) {
        List<RecordedVital> readings = jdbcTemplate.query(
                SELECT_READING_COLUMNS + " WHERE vital_signs_id = :vitalSignsId",
                new MapSqlParameterSource("vitalSignsId", withoutReadings.id()),
                this::mapReadingRow);
        return new VitalSigns(
                withoutReadings.id(), withoutReadings.encounterId(), withoutReadings.context(),
                withoutReadings.contextRefId(), readings, withoutReadings.weightKg(), withoutReadings.heightCm(),
                withoutReadings.bmi(), withoutReadings.avpu(), withoutReadings.bloodGlucoseMmol(),
                withoutReadings.recordedBy(), withoutReadings.recordedAt(), withoutReadings.occurredAt());
    }

    private RecordedVital mapReadingRow(ResultSet rs, int rowNum) throws SQLException {
        return new RecordedVital(
                VitalType.valueOf(rs.getString("vital_type")),
                rs.getDouble("value"),
                rs.getBoolean("abnormal"),
                (UUID) rs.getObject("applied_range_id"));
    }

    // Readings are populated by withReadings — a separate query keyed on the
    // id this row produces, since vital_reading has no columns to select here.
    private VitalSigns mapRow(ResultSet rs, int rowNum) throws SQLException {
        String avpu = rs.getString("avpu");
        Double bloodGlucose = getNullableDouble(rs, "blood_glucose_mmol");
        return new VitalSigns(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("encounter_id"),
                VitalSignsContext.valueOf(rs.getString("context")),
                (UUID) rs.getObject("context_ref_id"),
                List.of(),
                rs.getDouble("weight_kg"),
                rs.getDouble("height_cm"),
                rs.getDouble("bmi"),
                avpu == null ? null : Avpu.valueOf(avpu),
                bloodGlucose,
                (UUID) rs.getObject("recorded_by"),
                rs.getTimestamp("recorded_at").toInstant(),
                rs.getTimestamp("occurred_at").toInstant());
    }

    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
