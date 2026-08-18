package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.WardClass;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAdmissionRepository implements AdmissionRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, encounter_id, patient_id, facility_id, admitting_doctor_id, ordered_ward_class,
                   admitted_at, discharge_ordered_at, discharge_ordered_by, actual_departure_at,
                   version, created_at
            FROM admission
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAdmissionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Admission> findById(UUID id) {
        List<Admission> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<Admission> findByEncounterId(UUID encounterId) {
        List<Admission> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE encounter_id = :encounterId",
                new MapSqlParameterSource("encounterId", encounterId), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Admission insert(Admission admission) {
        String sql = """
                INSERT INTO admission (encounter_id, patient_id, facility_id, admitting_doctor_id,
                                        ordered_ward_class, admitted_at)
                VALUES (:encounterId, :patientId, :facilityId, :admittingDoctorId, :orderedWardClass, :admittedAt)
                RETURNING id, version, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("encounterId", admission.encounterId())
                .addValue("patientId", admission.patientId())
                .addValue("facilityId", admission.facilityId())
                .addValue("admittingDoctorId", admission.admittingDoctorId())
                .addValue("orderedWardClass", admission.orderedWardClass().name())
                .addValue("admittedAt", Timestamp.from(admission.admittedAt()));

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Admission(
                (UUID) rs.getObject("id"), admission.encounterId(), admission.patientId(), admission.facilityId(),
                admission.admittingDoctorId(), admission.orderedWardClass(), admission.admittedAt(),
                null, null, null, rs.getInt("version"), rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public List<Admission> findAllWithActiveBedAssignment() {
        String sql = SELECT_COLUMNS + """
                 WHERE EXISTS (
                     SELECT 1 FROM bed_assignment ba
                      WHERE ba.admission_id = admission.id AND ba.released_at IS NULL
                 )
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), this::mapRow);
    }

    private Admission mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp dischargeOrderedAt = rs.getTimestamp("discharge_ordered_at");
        Timestamp actualDepartureAt = rs.getTimestamp("actual_departure_at");
        return new Admission(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("encounter_id"),
                (UUID) rs.getObject("patient_id"),
                (UUID) rs.getObject("facility_id"),
                (UUID) rs.getObject("admitting_doctor_id"),
                WardClass.valueOf(rs.getString("ordered_ward_class")),
                rs.getTimestamp("admitted_at").toInstant(),
                dischargeOrderedAt == null ? null : dischargeOrderedAt.toInstant(),
                (UUID) rs.getObject("discharge_ordered_by"),
                actualDepartureAt == null ? null : actualDepartureAt.toInstant(),
                rs.getInt("version"),
                rs.getTimestamp("created_at").toInstant());
    }
}
