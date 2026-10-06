package com.adags.modules.patients.infrastructure;

import com.adags.modules.patients.domain.DuplicateCandidate;
import com.adags.modules.patients.domain.DuplicateCheckQuery;
import com.adags.modules.patients.domain.InsurancePolicy;
import com.adags.modules.patients.domain.MatchReason;
import com.adags.modules.patients.domain.NextOfKin;
import com.adags.modules.patients.domain.Patient;
import com.adags.modules.patients.domain.PatientAddress;
import com.adags.modules.patients.domain.PatientIdentifier;
import com.adags.modules.patients.domain.PatientListResult;
import com.adags.modules.patients.domain.PatientRepository;
import com.adags.modules.patients.domain.PatientSearchResult;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcPatientRepository implements PatientRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, hospital_no, given_name, middle_name, family_name, date_of_birth,
                   dob_is_estimated, gender, marital_status, national_id, phone, email,
                   occupation, identity_provisional, active, merged_into_id
            FROM patient
            """;

    // Bounded, not a raw WHILE loop: the trg_guard_no_merge_chain trigger
    // guarantees at most one hop in practice, but this reader does not trust
    // that blindly — a stray raw UPDATE bypassing the guard should fail
    // loudly here rather than loop forever.
    private static final int MAX_MERGE_HOPS = 5;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPatientRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Patient> findById(UUID id) {
        Optional<Patient> current = findRawById(id);
        for (int hop = 0; current.isPresent() && current.get().isMerged(); hop++) {
            if (hop >= MAX_MERGE_HOPS) {
                throw new IllegalStateException(
                        "Patient merge chain exceeds " + MAX_MERGE_HOPS + " hops starting from " + id
                                + " — trg_guard_no_merge_chain should have prevented this.");
            }
            current = findRawById(current.get().mergedIntoId());
        }
        return current;
    }

    private Optional<Patient> findRawById(UUID id) {
        List<Patient> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<PatientSearchResult> search(String query, int limit) {
        // Token-based AND match. The query is split on whitespace and every
        // token must appear somewhere in a searchable field. Matching each
        // name column on the whole string instead would make a full-name
        // query ("Derick Gabriel Mhidze") match nothing — no single column
        // holds the entire string — while a single-word query still worked,
        // the exact failure a receptionist hits when typing a patient's full
        // name. Tokenising also makes word order irrelevant ("Mhidze Derick"
        // finds the same person) and lets a name token and a hospital-number
        // token be combined. The full concatenated name is included per token
        // so an identifier typed alongside a name still matches.
        String[] tokens = query == null ? new String[0] : query.trim().split("\\s+");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);

        StringBuilder where = new StringBuilder("merged_into_id IS NULL");
        int i = 0;
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            String p = "tok" + i++;
            params.addValue(p, "%" + token + "%");
            where.append("""
                       AND (
                           hospital_no ILIKE :%1$s
                        OR given_name ILIKE :%1$s
                        OR middle_name ILIKE :%1$s
                        OR family_name ILIKE :%1$s
                        OR phone ILIKE :%1$s
                        OR national_id ILIKE :%1$s
                        OR concat_ws(' ', given_name, middle_name, family_name) ILIKE :%1$s
                      )""".formatted(p));
        }

        String sql = """
                SELECT id, hospital_no, given_name, middle_name, family_name, date_of_birth,
                       dob_is_estimated, phone
                FROM patient
                WHERE %s
                ORDER BY family_name, given_name
                LIMIT :limit
                """.formatted(where);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            String middleName = rs.getString("middle_name");
            String fullName = middleName == null || middleName.isBlank()
                    ? rs.getString("given_name") + " " + rs.getString("family_name")
                    : rs.getString("given_name") + " " + middleName + " " + rs.getString("family_name");
            return new PatientSearchResult(
                    (UUID) rs.getObject("id"),
                    rs.getString("hospital_no"),
                    fullName,
                    rs.getObject("date_of_birth", LocalDate.class),
                    rs.getBoolean("dob_is_estimated"),
                    rs.getString("phone"));
        });
    }

    @Override
    public List<PatientListResult> listRecent(Instant from, Instant to, String payerFilter, int limit) {
        // LATERAL join finds one "most-recent active policy today" per
        // patient — an admin scanning the list wants to see the payer they
        // will bill against, not every historical policy stitched into
        // duplicate rows.
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.hospital_no, p.given_name, p.middle_name, p.family_name,
                       p.date_of_birth, p.dob_is_estimated, p.phone, p.created_at,
                       s.code  AS scheme_code,
                       s.name  AS scheme_name
                FROM patient p
                LEFT JOIN LATERAL (
                    SELECT ip.scheme_id
                    FROM insurance_policy ip
                    WHERE ip.patient_id = p.id
                      AND (ip.valid_from IS NULL OR ip.valid_from <= CURRENT_DATE)
                      AND (ip.valid_to   IS NULL OR ip.valid_to   >= CURRENT_DATE)
                    ORDER BY ip.valid_from DESC NULLS LAST
                    LIMIT 1
                ) active_policy ON TRUE
                LEFT JOIN insurance_scheme s ON s.id = active_policy.scheme_id
                WHERE p.merged_into_id IS NULL
                """);

        MapSqlParameterSource params = new MapSqlParameterSource();
        if (from != null) {
            sql.append(" AND p.created_at >= :from");
            params.addValue("from", Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND p.created_at < :to");
            params.addValue("to", Timestamp.from(to));
        }
        if (payerFilter != null && !payerFilter.isBlank()) {
            String upper = payerFilter.toUpperCase();
            if (upper.equals("CASH")) {
                sql.append(" AND active_policy.scheme_id IS NULL");
            } else if (upper.equals("NHIF")) {
                sql.append(" AND s.code ILIKE '%NHIF%'");
            } else {
                sql.append(" AND active_policy.scheme_id IS NOT NULL AND (s.code IS NULL OR s.code NOT ILIKE '%NHIF%')");
            }
        }
        sql.append(" ORDER BY p.created_at DESC LIMIT :limit");
        params.addValue("limit", limit);

        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> {
            String middleName = rs.getString("middle_name");
            String fullName = middleName == null || middleName.isBlank()
                    ? rs.getString("given_name") + " " + rs.getString("family_name")
                    : rs.getString("given_name") + " " + middleName + " " + rs.getString("family_name");
            Timestamp createdAt = rs.getTimestamp("created_at");
            return new PatientListResult(
                    (UUID) rs.getObject("id"),
                    rs.getString("hospital_no"),
                    fullName,
                    rs.getObject("date_of_birth", LocalDate.class),
                    rs.getBoolean("dob_is_estimated"),
                    rs.getString("phone"),
                    createdAt == null ? null : createdAt.toInstant(),
                    rs.getString("scheme_code"),
                    rs.getString("scheme_name"));
        });
    }

    @Override
    public long countCreatedBetween(Instant from, Instant to) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM patient WHERE created_at >= :from AND created_at < :to",
                new MapSqlParameterSource()
                        .addValue("from", Timestamp.from(from))
                        .addValue("to", Timestamp.from(to)),
                Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<InsurancePolicy> findInsurancePoliciesByPatientId(UUID patientId) {
        String sql = """
                SELECT id, patient_id, scheme_id, policy_number, member_number, is_principal,
                       principal_name, valid_from, valid_to, verification_status, verified_at
                FROM insurance_policy
                WHERE patient_id = :patientId
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("patientId", patientId), (rs, rowNum) -> new InsurancePolicy(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("patient_id"),
                (UUID) rs.getObject("scheme_id"),
                rs.getString("policy_number"),
                rs.getString("member_number"),
                rs.getBoolean("is_principal"),
                rs.getString("principal_name"),
                rs.getObject("valid_from", LocalDate.class),
                rs.getObject("valid_to", LocalDate.class),
                rs.getString("verification_status"),
                rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toInstant()));
    }

    @Override
    public List<DuplicateCandidate> findDuplicateCandidates(DuplicateCheckQuery query) {
        String fullName = query.givenName() + " " + query.familyName();
        String sql = """
                SELECT id, hospital_no, given_name, family_name,
                       similarity(given_name || ' ' || family_name, :fullName) AS name_similarity,
                       (national_id IS NOT NULL AND national_id = :nationalId) AS national_id_exact,
                       (phone = :phone) AS phone_exact,
                       (date_of_birth = :dateOfBirth) AS dob_match
                FROM patient
                WHERE merged_into_id IS NULL
                  AND (
                       (national_id IS NOT NULL AND national_id = :nationalId)
                    OR similarity(given_name || ' ' || family_name, :fullName) >= :lowThreshold
                    OR phone = :phone
                  )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fullName", fullName)
                .addValue("nationalId", query.nationalId(), Types.VARCHAR)
                .addValue("phone", query.phone())
                .addValue("dateOfBirth", query.dateOfBirth())
                .addValue("lowThreshold", query.lowSimilarityThreshold());

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapCandidate(rs, query));
    }

    private DuplicateCandidate mapCandidate(ResultSet rs, DuplicateCheckQuery query) throws SQLException {
        double similarity = rs.getDouble("name_similarity");
        boolean nationalIdExact = rs.getBoolean("national_id_exact");
        boolean phoneExact = rs.getBoolean("phone_exact");
        boolean dobMatch = rs.getBoolean("dob_match");

        Set<MatchReason> reasons = new LinkedHashSet<>();
        if (nationalIdExact) {
            reasons.add(MatchReason.NATIONAL_ID_EXACT);
        }
        if (similarity >= query.highSimilarityThreshold() && dobMatch) {
            reasons.add(MatchReason.NAME_SIMILARITY_WITH_DOB_MATCH);
        } else if (similarity >= query.lowSimilarityThreshold()) {
            reasons.add(MatchReason.NAME_SIMILARITY);
        }
        if (phoneExact) {
            reasons.add(MatchReason.PHONE_EXACT);
        }

        return new DuplicateCandidate(
                (UUID) rs.getObject("id"),
                rs.getString("hospital_no"),
                rs.getString("given_name") + " " + rs.getString("family_name"),
                similarity,
                reasons);
    }

    @Override
    public Patient insert(Patient patient, UUID createdByUserId) {
        String sql = """
                INSERT INTO patient (hospital_no, given_name, middle_name, family_name, date_of_birth,
                                      dob_is_estimated, gender, marital_status, national_id, phone, email,
                                      occupation, identity_provisional, created_by)
                VALUES (:hospitalNo, :givenName, :middleName, :familyName, :dateOfBirth,
                        :dobIsEstimated, :gender, :maritalStatus, :nationalId, :phone, :email,
                        :occupation, :identityProvisional, :createdBy)
                RETURNING id
                """;

        UUID id = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("hospitalNo", patient.hospitalNo())
                .addValue("givenName", patient.givenName())
                .addValue("middleName", patient.middleName())
                .addValue("familyName", patient.familyName())
                .addValue("dateOfBirth", patient.dateOfBirth())
                .addValue("dobIsEstimated", patient.dobIsEstimated())
                .addValue("gender", patient.gender())
                .addValue("maritalStatus", patient.maritalStatus())
                .addValue("nationalId", patient.nationalId(), Types.VARCHAR)
                .addValue("phone", patient.phone())
                .addValue("email", patient.email())
                .addValue("occupation", patient.occupation())
                .addValue("identityProvisional", patient.identityProvisional())
                .addValue("createdBy", createdByUserId), UUID.class);

        return new Patient(id, patient.hospitalNo(), patient.givenName(), patient.middleName(),
                patient.familyName(), patient.dateOfBirth(), patient.dobIsEstimated(), patient.gender(),
                patient.maritalStatus(), patient.nationalId(), patient.phone(), patient.email(),
                patient.occupation(), patient.identityProvisional(), true, null);
    }

    @Override
    public UUID insertNextOfKin(NextOfKin nextOfKin) {
        String sql = """
                INSERT INTO next_of_kin (patient_id, full_name, relationship, relationship_other,
                                          phone, is_guardian)
                VALUES (:patientId, :fullName, :relationship, :relationshipOther,
                        :phone, :isGuardian)
                RETURNING id
                """;
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("patientId", nextOfKin.patientId())
                .addValue("fullName", nextOfKin.fullName())
                .addValue("relationship", nextOfKin.relationship())
                .addValue("relationshipOther", nextOfKin.relationshipOther())
                .addValue("phone", nextOfKin.phone())
                .addValue("isGuardian", nextOfKin.isGuardian()), UUID.class);
    }

    @Override
    public UUID insertAddress(PatientAddress address) {
        String sql = """
                INSERT INTO patient_address (patient_id, address_type, region, district, ward,
                                              street_or_village, landmark)
                VALUES (:patientId, :addressType, :region, :district, :ward, :streetOrVillage, :landmark)
                RETURNING id
                """;
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("patientId", address.patientId())
                .addValue("addressType", address.addressType())
                .addValue("region", address.region())
                .addValue("district", address.district())
                .addValue("ward", address.ward())
                .addValue("streetOrVillage", address.streetOrVillage())
                .addValue("landmark", address.landmark()), UUID.class);
    }

    @Override
    public UUID insertInsurancePolicy(InsurancePolicy policy) {
        String sql = """
                INSERT INTO insurance_policy (patient_id, scheme_id, policy_number, member_number,
                                               is_principal, principal_name, valid_from, valid_to,
                                               verification_status, verified_at)
                VALUES (:patientId, :schemeId, :policyNumber, :memberNumber, :isPrincipal, :principalName,
                        :validFrom, :validTo, :verificationStatus, :verifiedAt)
                RETURNING id
                """;
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("patientId", policy.patientId())
                .addValue("schemeId", policy.schemeId())
                .addValue("policyNumber", policy.policyNumber())
                .addValue("memberNumber", policy.memberNumber())
                .addValue("isPrincipal", policy.isPrincipal())
                .addValue("principalName", policy.principalName())
                .addValue("validFrom", policy.validFrom())
                .addValue("validTo", policy.validTo())
                .addValue("verificationStatus", policy.verificationStatus())
                .addValue("verifiedAt", policy.verifiedAt() == null ? null : Timestamp.from(policy.verifiedAt())),
                UUID.class);
    }

    @Override
    public UUID insertIdentifier(PatientIdentifier identifier) {
        String sql = """
                INSERT INTO patient_identifier (patient_id, identifier_type, value, issuing_authority)
                VALUES (:patientId, :identifierType, :value, :issuingAuthority)
                RETURNING id
                """;
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("patientId", identifier.patientId())
                .addValue("identifierType", identifier.identifierType())
                .addValue("value", identifier.value())
                .addValue("issuingAuthority", identifier.issuingAuthority()), UUID.class);
    }

    private Patient mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Patient(
                (UUID) rs.getObject("id"),
                rs.getString("hospital_no"),
                rs.getString("given_name"),
                rs.getString("middle_name"),
                rs.getString("family_name"),
                rs.getObject("date_of_birth", LocalDate.class),
                rs.getBoolean("dob_is_estimated"),
                rs.getString("gender"),
                rs.getString("marital_status"),
                rs.getString("national_id"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("occupation"),
                rs.getBoolean("identity_provisional"),
                rs.getBoolean("active"),
                (UUID) rs.getObject("merged_into_id")
        );
    }
}
