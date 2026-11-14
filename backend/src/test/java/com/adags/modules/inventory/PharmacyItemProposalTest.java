package com.adags.modules.inventory;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.inventory.application.ProposeNewItemCommand;
import com.adags.modules.inventory.application.ProposePharmacyItemService;
import com.adags.modules.inventory.application.ProposePriceChangeCommand;
import com.adags.modules.inventory.application.ReviewPharmacyItemProposalCommand;
import com.adags.modules.inventory.application.ReviewPharmacyItemProposalService;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.PharmacyItemProposal;
import com.adags.modules.inventory.domain.PharmacyItemProposalAlreadyReviewedException;
import com.adags.modules.inventory.domain.PharmacyItemProposalRepository;
import com.adags.modules.inventory.domain.PharmacyItemProposalSelfReviewNotAllowedException;
import com.adags.modules.inventory.domain.ProposalStatus;
import com.adags.modules.inventory.domain.RegulatorySchedule;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 12-pharmacist.md §9.2: new items and price changes both through one
// approval queue.
class PharmacyItemProposalTest extends IntegrationTestSupport {

    @Autowired
    private ProposePharmacyItemService proposePharmacyItemService;
    @Autowired
    private ReviewPharmacyItemProposalService reviewPharmacyItemProposalService;
    @Autowired
    private BillingFacade billingFacade;
    @Autowired
    private PharmacyItemProposalRepository pharmacyItemProposalRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        // backend/CLAUDE.md "Testing": price_list_entry holds migration-
        // seeded rows other test suites depend on (e.g. CONSULT_GENERAL) —
        // TRUNCATE would wipe them for the whole run. Delete only this
        // test's own PHARMACY-category rows instead.
        jdbcTemplate.update("TRUNCATE pharmacy_item_proposal", new MapSqlParameterSource());
        jdbcTemplate.update("""
                        DELETE FROM price_list_entry
                        WHERE service_item_id IN (SELECT id FROM service_item WHERE category = 'PHARMACY')
                        """,
                new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM service_item WHERE category = 'PHARMACY'", new MapSqlParameterSource());
        jdbcTemplate.update("TRUNCATE item CASCADE", new MapSqlParameterSource());
    }

    @Test
    void approvingANewItemProposalCreatesAnUnclassifiedInactiveItemWithItsFirstPrice() {
        UUID admin = bootstrapAdminId();
        UUID doctor = insertFixtureUser();
        PharmacyItemProposal proposal = proposePharmacyItemService.proposeNewItem(new ProposeNewItemCommand(
                "Ibuprofen 400mg tablet", "NSAID", "TABLET", "TABLET", 1_500, null, admin));

        PharmacyItemProposal approved = reviewPharmacyItemProposalService.review(
                new ReviewPharmacyItemProposalCommand(proposal.id(), ProposalStatus.APPROVED, doctor, "looks fine"));

        assertThat(approved.status()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(approved.createdItemId()).isNotNull();

        Item created = jdbcTemplate.queryForObject("""
                        SELECT regulatory_schedule, active, name FROM item WHERE id = :id
                        """,
                new MapSqlParameterSource("id", approved.createdItemId()),
                (rs, rowNum) -> new Item(null, null, rs.getString("name"), null, null, null, null,
                        RegulatorySchedule.valueOf(rs.getString("regulatory_schedule")), 0, rs.getBoolean("active"),
                        false, null, null));
        assertThat(created.regulatorySchedule()).isEqualTo(RegulatorySchedule.UNCLASSIFIED);
        assertThat(created.active()).isFalse();
        assertThat(created.name()).isEqualTo("Ibuprofen 400mg tablet");

        String itemCode = jdbcTemplate.queryForObject(
                "SELECT code FROM item WHERE id = :id", new MapSqlParameterSource("id", approved.createdItemId()), String.class);
        assertThat(billingFacade.findCurrentPharmacyItemPrice(itemCode)).isPresent();
        assertThat(billingFacade.findCurrentPharmacyItemPrice(itemCode).get().amountMinor()).isEqualTo(1_500);
    }

    @Test
    void approvingAPriceChangeVersionsTheExistingPriceRow() {
        UUID admin = bootstrapAdminId();
        UUID doctor = insertFixtureUser();
        PharmacyItemProposal newItemProposal = proposePharmacyItemService.proposeNewItem(new ProposeNewItemCommand(
                "Paracetamol 500mg tablet", "ANALGESIC", "TABLET", "TABLET", 500, null, admin));
        PharmacyItemProposal approvedNewItem = reviewPharmacyItemProposalService.review(
                new ReviewPharmacyItemProposalCommand(newItemProposal.id(), ProposalStatus.APPROVED, doctor, null));
        UUID itemId = approvedNewItem.createdItemId();

        PharmacyItemProposal priceChange = proposePharmacyItemService.proposePriceChange(
                new ProposePriceChangeCommand(itemId, 750, "supplier cost increased", admin));
        assertThat(priceChange.currentPrice()).isNotNull();
        assertThat(priceChange.currentPrice().amountMinor()).isEqualTo(500);

        reviewPharmacyItemProposalService.review(
                new ReviewPharmacyItemProposalCommand(priceChange.id(), ProposalStatus.APPROVED, doctor, "approved"));

        String itemCode = jdbcTemplate.queryForObject(
                "SELECT code FROM item WHERE id = :id", new MapSqlParameterSource("id", itemId), String.class);
        assertThat(billingFacade.findCurrentPharmacyItemPrice(itemCode).get().amountMinor()).isEqualTo(750);

        Integer closedEntries = jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM price_list_entry ple
                        JOIN service_item si ON si.id = ple.service_item_id
                        WHERE si.code = :code AND ple.valid_to IS NOT NULL
                        """,
                new MapSqlParameterSource("code", itemCode), Integer.class);
        assertThat(closedEntries).as("the old price row is closed, not deleted").isEqualTo(1);
    }

    @Test
    void dosageFormCorrectionAppearsInAdminPendingQueueWithMedicineName() {
        UUID proposer = bootstrapAdminId();
        UUID reviewer = insertFixtureUser();
        PharmacyItemProposal newItem = proposePharmacyItemService.proposeNewItem(new ProposeNewItemCommand(
                "Amoxicillin 500mg", "ANTIBIOTIC", "TABLET", "TABLET", 500, null, proposer));
        UUID itemId = reviewPharmacyItemProposalService.review(
                new ReviewPharmacyItemProposalCommand(newItem.id(), ProposalStatus.APPROVED, reviewer, null))
                .createdItemId();

        PharmacyItemProposal correction = proposePharmacyItemService.proposeFormChange(
                itemId, "CAPSULE", "CAPSULE", "Manufacturer label confirms capsule", proposer);

        assertThat(pharmacyItemProposalRepository.findPending()).extracting(PharmacyItemProposal::id)
                .contains(correction.id());
        PharmacyItemProposal visible = pharmacyItemProposalRepository.findPending().stream()
                .filter(proposal -> proposal.id().equals(correction.id())).findFirst().orElseThrow();
        assertThat(visible.proposalType().name()).isEqualTo("FORM_CHANGE");
        assertThat(visible.proposedName()).isEqualTo("Amoxicillin 500mg");
        assertThat(visible.proposedForm()).isEqualTo("CAPSULE");
    }

    @Test
    void proposingAPriceChangeWithNoJustificationIsRejected() {
        assertThatThrownBy(() -> com.adags.modules.inventory.domain.PharmacyItemProposal.proposePriceChange(
                UUID.randomUUID(), new com.adags.shared.kernel.Money(1000, "TZS"), null, "  ", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectingAProposalCreatesNoItemOrPrice() {
        UUID admin = bootstrapAdminId();
        UUID doctor = insertFixtureUser();
        PharmacyItemProposal proposal = proposePharmacyItemService.proposeNewItem(new ProposeNewItemCommand(
                "Suspicious Item", "UNKNOWN", "TABLET", "TABLET", 999_999, null, admin));

        PharmacyItemProposal rejected = reviewPharmacyItemProposalService.review(
                new ReviewPharmacyItemProposalCommand(proposal.id(), ProposalStatus.REJECTED, doctor, "not needed"));

        assertThat(rejected.status()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(rejected.createdItemId()).isNull();
        Integer itemCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM item WHERE name = 'Suspicious Item'", new MapSqlParameterSource(), Integer.class);
        assertThat(itemCount).isZero();
    }

    @Test
    void theProposerCannotReviewTheirOwnProposal() {
        UUID admin = bootstrapAdminId();
        PharmacyItemProposal proposal = proposePharmacyItemService.proposeNewItem(new ProposeNewItemCommand(
                "Ibuprofen 400mg tablet", "NSAID", "TABLET", "TABLET", 1_500, null, admin));

        assertThatThrownBy(() -> reviewPharmacyItemProposalService.review(
                new ReviewPharmacyItemProposalCommand(proposal.id(), ProposalStatus.APPROVED, admin, null)))
                .isInstanceOf(PharmacyItemProposalSelfReviewNotAllowedException.class);
    }

    @Test
    void reviewingAnAlreadyReviewedProposalIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID doctor = insertFixtureUser();
        PharmacyItemProposal proposal = proposePharmacyItemService.proposeNewItem(new ProposeNewItemCommand(
                "Ibuprofen 400mg tablet", "NSAID", "TABLET", "TABLET", 1_500, null, admin));
        reviewPharmacyItemProposalService.review(
                new ReviewPharmacyItemProposalCommand(proposal.id(), ProposalStatus.REJECTED, doctor, "no"));

        assertThatThrownBy(() -> reviewPharmacyItemProposalService.review(
                new ReviewPharmacyItemProposalCommand(proposal.id(), ProposalStatus.APPROVED, doctor, null)))
                .isInstanceOf(PharmacyItemProposalAlreadyReviewedException.class);
    }

    // 18-admin.md §2/§7: the admin's "Pricing / price proposals" review queue
    // — reviewed proposals (approved or rejected) must drop out of it.
    @Test
    void pendingListsOnlyUnreviewedProposals() {
        UUID admin = bootstrapAdminId();
        UUID doctor = insertFixtureUser();
        PharmacyItemProposal stillPending = proposePharmacyItemService.proposeNewItem(new ProposeNewItemCommand(
                "Amoxicillin 250mg capsule", "ANTIBIOTIC", "CAPSULE", "CAPSULE", 2_000, null, admin));
        PharmacyItemProposal willBeRejected = proposePharmacyItemService.proposeNewItem(new ProposeNewItemCommand(
                "Vitamin C 100mg tablet", "SUPPLEMENT", "TABLET", "TABLET", 300, null, admin));
        reviewPharmacyItemProposalService.review(
                new ReviewPharmacyItemProposalCommand(willBeRejected.id(), ProposalStatus.REJECTED, doctor, "not stocked"));

        var pending = pharmacyItemProposalRepository.findPending();

        assertThat(pending).extracting(PharmacyItemProposal::id).contains(stillPending.id());
        assertThat(pending).extracting(PharmacyItemProposal::id).doesNotContain(willBeRejected.id());
    }

    private UUID insertFixtureUser() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app_user (username, password_hash) VALUES (:username, 'x') RETURNING id",
                new MapSqlParameterSource("username", "test-reviewer-" + UUID.randomUUID()), UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
