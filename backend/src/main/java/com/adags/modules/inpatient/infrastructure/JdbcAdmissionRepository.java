package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.DischargeOrdererRole;
import com.adags.modules.inpatient.domain.StaleAdmissionException;
import com.adags.modules.inpatient.domain.WardClass;
import com.adags.modules.inpatient.domain.WardStatus;
import com.adags.shared.kernel.WardSummary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAdmissionRepository implements AdmissionRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, encounter_id, patient_id, facility_id, admitting_doctor_id, ordered_ward_class,
                   admitted_at, discharge_ordered_at, discharge_ordered_by, discharge_orderer_role,
                   actual_departure_at, ward_status, ward_status_updated_at, ward_status_updated_by,
                   ward_status_suggested_critical_at, version, created_at
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
                null, null, null, null, WardStatus.ADMITTED, null, null, null,
                rs.getInt("version"), rs.getTimestamp("created_at").toInstant()));
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

    @Override
    public List<Admission> findAwaitingPlacement(UUID facilityId) {
        String sql = SELECT_COLUMNS + """
                 WHERE facility_id = :facilityId
                   AND actual_departure_at IS NULL
                   AND NOT EXISTS (
                     SELECT 1 FROM bed_assignment ba
                      WHERE ba.admission_id = admission.id AND ba.released_at IS NULL
                 )
                 ORDER BY admitted_at
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("facilityId", facilityId), this::mapRow);
    }

    @Override
    public List<Admission> findActiveByAdmittingDoctor(UUID admittingDoctorId) {
        String sql = SELECT_COLUMNS + """
                 WHERE admitting_doctor_id = :admittingDoctorId
                   AND actual_departure_at IS NULL
                 ORDER BY admitted_at
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("admittingDoctorId", admittingDoctorId), this::mapRow);
    }

    @Override
    public List<Admission> findByCurrentWardId(UUID wardId) {
        String sql = SELECT_COLUMNS + """
                 WHERE EXISTS (
                     SELECT 1 FROM bed_assignment ba JOIN bed b ON b.id = ba.bed_id
                      WHERE ba.admission_id = admission.id AND ba.released_at IS NULL AND b.ward_id = :wardId
                 )
                 ORDER BY admitted_at
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("wardId", wardId), this::mapRow);
    }

    @Override
    public Optional<WardSummary> findCurrentWardByEncounterId(UUID encounterId) {
        String sql = """
                SELECT w.id AS ward_id, w.name AS ward_name
                FROM admission a
                JOIN bed_assignment ba ON ba.admission_id = a.id AND ba.released_at IS NULL
                JOIN bed b ON b.id = ba.bed_id
                JOIN ward w ON w.id = b.ward_id
                WHERE a.encounter_id = :encounterId AND a.actual_departure_at IS NULL
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("encounterId", encounterId),
                        (rs, rowNum) -> new WardSummary((UUID) rs.getObject("ward_id"), rs.getString("ward_name")))
                .stream().findFirst();
    }

    @Override
    public Admission orderDischarge(UUID admissionId, Instant dischargeOrderedAt, UUID dischargeOrderedBy,
                                     DischargeOrdererRole dischargeOrdererRole, int expectedVersion) {
        String sql = """
                UPDATE admission
                SET discharge_ordered_at = :dischargeOrderedAt, discharge_ordered_by = :dischargeOrderedBy,
                    discharge_orderer_role = :dischargeOrdererRole, version = version + 1
                WHERE id = :id AND version = :expectedVersion
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("dischargeOrderedAt", Timestamp.from(dischargeOrderedAt))
                .addValue("dischargeOrderedBy", dischargeOrderedBy)
                .addValue("dischargeOrdererRole", dischargeOrdererRole.name())
                .addValue("id", admissionId)
                .addValue("expectedVersion", expectedVersion));
        if (updated == 0) {
            throw new StaleAdmissionException(admissionId, expectedVersion);
        }
        return findById(admissionId).orElseThrow();
    }

    @Override
    public Admission reverseDischargeOrder(UUID admissionId, int expectedVersion) {
        String sql = """
                UPDATE admission
                SET discharge_ordered_at = NULL, discharge_ordered_by = NULL, discharge_orderer_role = NULL,
                    version = version + 1
                WHERE id = :id AND version = :expectedVersion
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", admissionId)
                .addValue("expectedVersion", expectedVersion));
        if (updated == 0) {
            throw new StaleAdmissionException(admissionId, expectedVersion);
        }
        return findById(admissionId).orElseThrow();
    }

    @Override
    public Admission recordActualDeparture(UUID admissionId, Instant actualDepartureAt, int expectedVersion) {
        String sql = """
                UPDATE admission SET actual_departure_at = :actualDepartureAt, version = version + 1
                WHERE id = :id AND version = :expectedVersion
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("actualDepartureAt", Timestamp.from(actualDepartureAt))
                .addValue("id", admissionId)
                .addValue("expectedVersion", expectedVersion));
        if (updated == 0) {
            throw new StaleAdmissionException(admissionId, expectedVersion);
        }
        return findById(admissionId).orElseThrow();
    }

    @Override
    public Admission updateWardStatus(UUID admissionId, WardStatus wardStatus, Instant updatedAt, UUID updatedBy) {
        String sql = """
                UPDATE admission
                SET ward_status = :wardStatus, ward_status_updated_at = :updatedAt,
                    ward_status_updated_by = :updatedBy, ward_status_suggested_critical_at = NULL
                WHERE id = :id
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("wardStatus", wardStatus.name())
                .addValue("updatedAt", Timestamp.from(updatedAt))
                .addValue("updatedBy", updatedBy)
                .addValue("id", admissionId));
        return findById(admissionId).orElseThrow();
    }

    @Override
    public Admission suggestCriticalStatus(UUID admissionId, Instant suggestedAt) {
        String sql = "UPDATE admission SET ward_status_suggested_critical_at = :suggestedAt WHERE id = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("suggestedAt", Timestamp.from(suggestedAt))
                .addValue("id", admissionId));
        return findById(admissionId).orElseThrow();
    }

    private Admission mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp dischargeOrderedAt = rs.getTimestamp("discharge_ordered_at");
        Timestamp actualDepartureAt = rs.getTimestamp("actual_departure_at");
        Timestamp wardStatusUpdatedAt = rs.getTimestamp("ward_status_updated_at");
        Timestamp wardStatusSuggestedCriticalAt = rs.getTimestamp("ward_status_suggested_critical_at");
        String dischargeOrdererRole = rs.getString("discharge_orderer_role");
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
                dischargeOrdererRole == null ? null : DischargeOrdererRole.valueOf(dischargeOrdererRole),
                actualDepartureAt == null ? null : actualDepartureAt.toInstant(),
                WardStatus.valueOf(rs.getString("ward_status")),
                wardStatusUpdatedAt == null ? null : wardStatusUpdatedAt.toInstant(),
                (UUID) rs.getObject("ward_status_updated_by"),
                wardStatusSuggestedCriticalAt == null ? null : wardStatusSuggestedCriticalAt.toInstant(),
                rs.getInt("version"),
                rs.getTimestamp("created_at").toInstant());
    }
}
