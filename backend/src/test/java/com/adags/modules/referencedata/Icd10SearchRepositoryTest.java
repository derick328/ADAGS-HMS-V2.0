package com.adags.modules.referencedata;

import com.adags.modules.referencedata.domain.Icd10SearchRepository;
import com.adags.modules.referencedata.domain.Icd10SearchResult;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 10-clinical-reference-data.md §5.2's ranking formula, end to end against
// real Postgres — trigram similarity and the CASE-based weights aren't
// something H2 or a mock could stand in for (backend/CLAUDE.md "Testing").
class Icd10SearchRepositoryTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private Icd10SearchRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE reference_search_log, icd10_synonym, common_code, icd10_code CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void exactCodeMatchRanksFirst() {
        insertCode("B50.9", "P. falciparum malaria, unspecified", true, true);
        insertCode("B50.0", "P. falciparum malaria with cerebral complications", true, true);

        List<Icd10SearchResult> results = repository.search("B50.9", null, null, 20);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).code()).isEqualTo("B50.9");
        assertThat(results.get(0).score()).isGreaterThanOrEqualTo(1000.0);
    }

    @Test
    void categoryHeadersAndRetiredCodesAreExcluded() {
        insertCode("B50", "Plasmodium falciparum malaria", false, true);   // category header — not billable
        insertCode("A99", "Zika virus disease, retired", true, false);     // retired
        insertCode("B54", "Unspecified malaria", true, true);

        List<Icd10SearchResult> results = repository.search("malaria", null, null, 20);

        assertThat(results).extracting(Icd10SearchResult::code).containsExactly("B54");
    }

    @Test
    void titleWordsAndCommonMisspellingsFindSelectableDiagnoses() {
        insertCode("A53.9", "Syphilis, unspecified", true, true);
        insertCode("A02.9", "Salmonella infection, unspecified", true, true);

        assertThat(repository.search("syphilis", null, null, 20))
                .extracting(Icd10SearchResult::code).contains("A53.9");
        assertThat(repository.search("salmonera", null, null, 20))
                .extracting(Icd10SearchResult::code).contains("A02.9");
    }

    @Test
    void aDepartmentCommonListEntryOutranksAPlainTitleMatch() {
        UUID departmentId = insertFixtureDepartment();
        insertCode("B50.9", "P. falciparum malaria, unspecified", true, true);
        insertCode("B54", "Unspecified malaria", true, true);
        insertCommonCode("B54", departmentId, 0);

        List<Icd10SearchResult> results = repository.search("malaria", departmentId, null, 20);

        assertThat(results.get(0).code()).isEqualTo("B54");
        assertThat(results.get(0).score()).isGreaterThanOrEqualTo(500.0);
    }

    @Test
    void aDepartmentCommonListEntryOutranksAHospitalWideOneForTheSameQuery() {
        UUID departmentId = insertFixtureDepartment();
        insertCode("B50.9", "P. falciparum malaria, unspecified", true, true);
        insertCode("B54", "Unspecified malaria", true, true);
        insertCommonCode("B50.9", null, 0);   // hospital-wide: +300
        insertCommonCode("B54", departmentId, 0); // this department: +500

        List<Icd10SearchResult> results = repository.search("malaria", departmentId, null, 20);

        assertThat(results.get(0).code()).isEqualTo("B54");
        assertThat(results.get(1).code()).isEqualTo("B50.9");
    }

    // A hospital-wide boost still applies for a caller with no department context.
    @Test
    void aHospitalWideCommonListEntryAppliesWithNoDepartmentGiven() {
        insertCode("B50.9", "P. falciparum malaria, unspecified", true, true);
        insertCode("B54", "Unspecified malaria", true, true);
        insertCommonCode("B54", null, 0);

        List<Icd10SearchResult> results = repository.search("malaria", null, null, 20);

        assertThat(results.get(0).code()).isEqualTo("B54");
        assertThat(results.get(0).score()).isGreaterThanOrEqualTo(300.0);
    }

    // §2.3: only APPROVED synonyms enter search.
    @Test
    void anApprovedSynonymMatchesButAProposedOneDoesNot() {
        insertCode("B50.9", "P. falciparum malaria, unspecified", true, true);
        insertCode("B54", "Unspecified malaria", true, true);
        insertSynonym("B50.9", "homa ya malaria", "APPROVED");
        insertSynonym("B54", "presha kali", "PROPOSED");

        List<Icd10SearchResult> approvedMatch = repository.search("homa ya malaria", null, null, 20);
        assertThat(approvedMatch).extracting(Icd10SearchResult::code).contains("B50.9");

        List<Icd10SearchResult> proposedMatch = repository.search("presha kali", null, null, 20);
        assertThat(proposedMatch).extracting(Icd10SearchResult::code).doesNotContain("B54");
    }

    // Isolates the "this user's recent selections" term (+25) from trigram
    // noise: neither code's title resembles the query at all, so without
    // the recency boost both score ~0 and tie-break alphabetically.
    @Test
    void thisUsersRecentSelectionOutranksAnIdenticallyMatchedCodeForThatUserOnly() {
        UUID actorUserId = insertFixtureUser();
        insertCode("Z91.1", "Foo", true, true);
        insertCode("Z91.2", "Bar", true, true);
        insertSearchLog(actorUserId, "Z91", "Z91.2");

        List<Icd10SearchResult> resultsForThatActor = repository.search("Z91", null, actorUserId, 20);
        assertThat(resultsForThatActor.get(0).code()).isEqualTo("Z91.2");

        List<Icd10SearchResult> resultsForAnotherActor = repository.search("Z91", null, null, 20);
        assertThat(resultsForAnotherActor.get(0).code()).isEqualTo("Z91.1"); // alphabetical tie-break, no boost
    }

    private void insertCode(String code, String title, boolean isBillable, boolean isActive) {
        jdbcTemplate.update("""
                        INSERT INTO icd10_code (code, title, icd10_release, is_billable, is_active)
                        VALUES (:code, :title, '2019', :isBillable, :isActive)
                        """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("title", title)
                        .addValue("isBillable", isBillable)
                        .addValue("isActive", isActive));
    }

    private void insertCommonCode(String code, UUID departmentId, int sortRank) {
        jdbcTemplate.update("""
                        INSERT INTO common_code (code, department_id, sort_rank, added_by)
                        VALUES (:code, :departmentId, :sortRank, (SELECT id FROM app_user WHERE username = 'admin'))
                        """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("departmentId", departmentId, java.sql.Types.OTHER)
                        .addValue("sortRank", sortRank));
    }

    private void insertSynonym(String code, String term, String status) {
        jdbcTemplate.update("""
                        INSERT INTO icd10_synonym (code, term, language, status, proposed_by, reviewed_by, reviewed_at)
                        VALUES (:code, :term, 'sw', :status, :proposer,
                                CASE WHEN :status = 'PROPOSED' THEN NULL ELSE :approver END,
                                CASE WHEN :status = 'PROPOSED' THEN NULL ELSE now() END)
                        """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("term", term)
                        .addValue("status", status)
                        .addValue("proposer", insertFixtureUser())
                        .addValue("approver", insertFixtureUser()));
    }

    private void insertSearchLog(UUID actorUserId, String queryText, String selectedId) {
        jdbcTemplate.update("""
                        INSERT INTO reference_search_log (actor_user_id, catalogue_type, query_text, result_count, selected_id)
                        VALUES (:actorUserId, 'ICD10', :queryText, 1, :selectedId)
                        """,
                new MapSqlParameterSource()
                        .addValue("actorUserId", actorUserId)
                        .addValue("queryText", queryText)
                        .addValue("selectedId", selectedId));
    }

    private UUID insertFixtureDepartment() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, category_id, code, name) VALUES (:facilityId, (SELECT id FROM department_category WHERE code = 'GENERAL_CLINICAL'), :code, :code)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }

    private UUID insertFixtureUser() {
        String username = "fixture_" + UUID.randomUUID();
        return jdbcTemplate.queryForObject(
                "INSERT INTO app_user (username, password_hash) VALUES (:username, 'x') RETURNING id",
                new MapSqlParameterSource("username", username), UUID.class);
    }
}
