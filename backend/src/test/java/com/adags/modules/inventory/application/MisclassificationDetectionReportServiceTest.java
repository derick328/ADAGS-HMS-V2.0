package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.MisclassificationCandidate;
import com.adags.modules.inventory.domain.RegulatorySchedule;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 12-pharmacist.md §10.6: the only safety net now that UNCLASSIFIED is
// dispensable (D9) — an UNCLASSIFIED or PRESCRIPTION_ONLY item whose name
// trigram-matches the (pharmacist-curated) narcotic watchlist, plus the
// permanent CONTROLLED -> PRESCRIPTION_ONLY reclassification history
// (build brief item 4).
class MisclassificationDetectionReportServiceTest extends IntegrationTestSupport {

    @Autowired
    private CreateItemService createItemService;

    @Autowired
    private ClassifyItemService classifyItemService;

    @Autowired
    private AddWatchlistEntryService addWatchlistEntryService;

    @Autowired
    private MisclassificationDetectionReportService reportService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "TRUNCATE narcotic_watchlist_entry, item_classification_change, item CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void flagsAnUnclassifiedItemWhoseNameMatchesTheWatchlist() {
        addWatchlistEntryService.add(new AddWatchlistEntryCommand("Morphine", null,
                "TMDA Scheduling of Medicines Regulations 2025, First Schedule", LocalDate.now(), bootstrapAdminId()));
        Item narcoticLookAlike = createFixtureItem("Morphine");
        Item unrelated = createFixtureItem("Paracetamol 500mg tablet");

        List<MisclassificationCandidate> candidates = reportService.generate().candidates();

        assertThat(candidates).extracting(MisclassificationCandidate::itemId).contains(narcoticLookAlike.id());
        assertThat(candidates).extracting(MisclassificationCandidate::itemId).doesNotContain(unrelated.id());
    }

    @Test
    void excludesAControlledItemFromTheCandidateListEvenIfItMatches() {
        addWatchlistEntryService.add(new AddWatchlistEntryCommand("Morphine", null,
                "TMDA Scheduling of Medicines Regulations 2025, First Schedule", LocalDate.now(), bootstrapAdminId()));
        Item item = createFixtureItem("Morphine");
        classifyItemService.classify(new ClassifyItemCommand(item.id(), RegulatorySchedule.CONTROLLED,
                "confirmed on First Schedule", bootstrapAdminId()));

        List<MisclassificationCandidate> candidates = reportService.generate().candidates();

        assertThat(candidates).extracting(MisclassificationCandidate::itemId).doesNotContain(item.id());
    }

    @Test
    void reportsControlledToPrescriptionOnlyReclassificationsPermanently() {
        Item item = createFixtureItem("Ketamine 50mg/ml injection");
        classifyItemService.classify(new ClassifyItemCommand(item.id(), RegulatorySchedule.CONTROLLED,
                "narcotic list", bootstrapAdminId()));
        classifyItemService.classify(new ClassifyItemCommand(item.id(), RegulatorySchedule.PRESCRIPTION_ONLY,
                "TMDA clarified this formulation is exempt under regulation 15(3)", bootstrapAdminId()));

        var releases = reportService.generate().controlledReleases();

        assertThat(releases).hasSize(1);
        assertThat(releases.get(0).itemId()).isEqualTo(item.id());
        assertThat(releases.get(0).reason()).contains("regulation 15(3)");
    }

    private Item createFixtureItem(String name) {
        return createItemService.create(new CreateItemCommand("ITEM-" + UUID.randomUUID(), name, "TEST", "TABLET",
                null, "TABLET", 0, bootstrapAdminId()));
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
