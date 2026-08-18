package com.adags.modules.referencedata;

import com.adags.modules.referencedata.application.ProposeIcd10SynonymService;
import com.adags.modules.referencedata.application.ReviewIcd10SynonymService;
import com.adags.modules.referencedata.application.SynonymAlreadyReviewedException;
import com.adags.modules.referencedata.domain.Icd10CodeRepository;
import com.adags.modules.referencedata.domain.Icd10Synonym;
import com.adags.modules.referencedata.domain.SelfReviewNotAllowedException;
import com.adags.modules.referencedata.domain.SynonymStatus;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 10-clinical-reference-data.md §3: "propose freely, approve deliberately."
class Icd10SynonymModerationTest extends IntegrationTestSupport {

    @Autowired
    private ProposeIcd10SynonymService proposeService;

    @Autowired
    private ReviewIcd10SynonymService reviewService;

    @Autowired
    private Icd10CodeRepository codeRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE icd10_synonym, icd10_code CASCADE", new MapSqlParameterSource());
    }

    @Test
    void proposeThenApproveMakesTheSynonymApproved() {
        UUID proposer = insertFixtureUser();
        UUID approver = insertFixtureUser();
        insertFixtureCode("B50.9");

        Icd10Synonym proposed = proposeService.propose("B50.9", "homa ya malaria", "sw", proposer);
        assertThat(proposed.status()).isEqualTo(SynonymStatus.PROPOSED);

        Icd10Synonym approved = reviewService.review(proposed.id(), SynonymStatus.APPROVED, approver, "Confirmed with clinical lead");

        assertThat(approved.status()).isEqualTo(SynonymStatus.APPROVED);
        assertThat(approved.reviewedBy()).isEqualTo(approver);
        assertThat(approved.reviewNote()).isEqualTo("Confirmed with clinical lead");
    }

    @Test
    void proposeThenRejectMakesTheSynonymRejected() {
        UUID proposer = insertFixtureUser();
        UUID approver = insertFixtureUser();
        insertFixtureCode("B50.9");

        Icd10Synonym proposed = proposeService.propose("B50.9", "wrong term", "sw", proposer);
        Icd10Synonym rejected = reviewService.review(proposed.id(), SynonymStatus.REJECTED, approver, "Not a recognised term");

        assertThat(rejected.status()).isEqualTo(SynonymStatus.REJECTED);
    }

    // §3: "REFERENCE_SYNONYM_APPROVE — clinical lead only" — and never the
    // same person who proposed it. Application-layer pre-check.
    @Test
    void aProposerCannotReviewTheirOwnSynonym() {
        UUID proposer = insertFixtureUser();
        insertFixtureCode("B50.9");
        Icd10Synonym proposed = proposeService.propose("B50.9", "homa ya malaria", "sw", proposer);

        assertThatThrownBy(() -> reviewService.review(proposed.id(), SynonymStatus.APPROVED, proposer, null))
                .isInstanceOf(SelfReviewNotAllowedException.class);
    }

    // root CLAUDE.md "Authorisation": the same rule as a real database
    // constraint, independent of the service's pre-check — proves the
    // database itself rejects it even if a future caller bypasses the
    // service (same pattern as elevation_request.reviewed_by != requested_by).
    @Test
    void theDatabaseRejectsAProposerReviewingTheirOwnSynonymEvenBypassingTheService() {
        UUID user = insertFixtureUser();
        insertFixtureCode("B50.9");
        UUID synonymId = jdbcTemplate.queryForObject("""
                        INSERT INTO icd10_synonym (code, term, language, proposed_by)
                        VALUES ('B50.9', 'homa ya malaria', 'sw', :user)
                        RETURNING id
                        """,
                new MapSqlParameterSource("user", user), UUID.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE icd10_synonym SET status = 'APPROVED', reviewed_by = :user, reviewed_at = now()
                        WHERE id = :id
                        """,
                new MapSqlParameterSource().addValue("user", user).addValue("id", synonymId)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void reviewingAnAlreadyReviewedSynonymIsRejected() {
        UUID proposer = insertFixtureUser();
        UUID approver = insertFixtureUser();
        UUID secondApprover = insertFixtureUser();
        insertFixtureCode("B50.9");
        Icd10Synonym proposed = proposeService.propose("B50.9", "homa ya malaria", "sw", proposer);
        reviewService.review(proposed.id(), SynonymStatus.APPROVED, approver, null);

        assertThatThrownBy(() -> reviewService.review(proposed.id(), SynonymStatus.REJECTED, secondApprover, null))
                .isInstanceOf(SynonymAlreadyReviewedException.class);
    }

    @Test
    void proposingForAnUnknownCodeIsRejected() {
        UUID proposer = insertFixtureUser();

        assertThatThrownBy(() -> proposeService.propose("Z99.9", "unknown", "sw", proposer))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void anUnrecognisedLanguageIsRejected() {
        UUID proposer = insertFixtureUser();
        insertFixtureCode("B50.9");

        assertThatThrownBy(() -> proposeService.propose("B50.9", "term", "fr", proposer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void insertFixtureCode(String code) {
        jdbcTemplate.update("""
                        INSERT INTO icd10_code (code, title, icd10_release)
                        VALUES (:code, 'P. falciparum malaria, unspecified', '2019')
                        """,
                new MapSqlParameterSource("code", code));
    }

    private UUID insertFixtureUser() {
        String username = "fixture_" + UUID.randomUUID();
        return jdbcTemplate.queryForObject(
                "INSERT INTO app_user (username, password_hash) VALUES (:username, 'x') RETURNING id",
                new MapSqlParameterSource("username", username), UUID.class);
    }
}
