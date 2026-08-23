package com.adags.modules.billing;

import com.adags.modules.billing.application.CreateDraftInvoiceCommand;
import com.adags.modules.billing.application.CreateDraftInvoiceService;
import com.adags.modules.billing.application.IssueInvoiceService;
import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceState;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.ServiceItem;
import com.adags.modules.billing.domain.ServiceItemCategory;
import com.adags.modules.billing.domain.ServiceItemRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 05-receptionist.md §7.2/§7.3, 02-billing-architecture.md §10: the
// receptionist's hand-built, multi-line, catalogue-backed invoice —
// DRAFT (freely editable, no number) then a separate explicit Issue step
// (gapless number, locks the lines). "Nobody types a price": the request
// carries a service item code and quantity, the price always comes from
// PriceResolutionService.
class DraftInvoiceLifecycleTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private CreateDraftInvoiceService createDraftInvoiceService;
    @Autowired
    private IssueInvoiceService issueInvoiceService;
    @Autowired
    private ServiceItemRepository serviceItemRepository;
    @Autowired
    private InvoiceLineRepository invoiceLineRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE invoice_line, invoice CASCADE", new MapSqlParameterSource());
        jdbcTemplate.update("""
                        DELETE FROM price_list_entry
                        WHERE service_item_id IN (SELECT id FROM service_item WHERE code LIKE 'TEST-RECORDS-FEE%')
                        """, new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM service_item WHERE code LIKE 'TEST-RECORDS-FEE%'", new MapSqlParameterSource());
        jdbcTemplate.update("TRUNCATE patient CASCADE", new MapSqlParameterSource());
    }

    @Test
    void createsADraftWithResolvedPricesThenIssueAssignsANumberAndLocksTotals() {
        UUID patientId = insertFixturePatient();
        String itemCode = insertFixtureServiceItemWithCashPrice(2_000);

        Invoice draft = createDraftInvoiceService.create(new CreateDraftInvoiceCommand(
                patientId, null, BOOTSTRAP_FACILITY_ID, LocalDate.now(), "records copy",
                List.of(new CreateDraftInvoiceCommand.Line(itemCode, 3)), bootstrapAdminId()));

        assertThat(draft.state()).isEqualTo(InvoiceState.DRAFT);
        assertThat(draft.invoiceNumber()).isNull();
        // Draft totals stay zero until issue recomputes from the lines
        // (InvoiceRepository.issue's own contract) — the line itself
        // already carries the resolved price.
        List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceId(draft.id());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).grossAmount().amountMinor()).isEqualTo(6_000); // 2,000 x 3
        assertThat(lines.get(0).patientPortion().amountMinor()).isEqualTo(6_000);
        assertThat(lines.get(0).authorizationId()).isNull(); // never gated — see class comment on the service

        Invoice issued = issueInvoiceService.issue(draft.id(), BOOTSTRAP_FACILITY_ID, draft.version());

        assertThat(issued.state()).isEqualTo(InvoiceState.ISSUED);
        assertThat(issued.invoiceNumber()).isNotBlank();
        assertThat(issued.grossTotal().amountMinor()).isEqualTo(6_000);
        assertThat(issued.outstandingTotal().amountMinor()).isEqualTo(6_000);
    }

    @Test
    void requiresAtLeastOneLine() {
        UUID patientId = insertFixturePatient();

        assertThatThrownBy(() -> createDraftInvoiceService.create(new CreateDraftInvoiceCommand(
                patientId, null, BOOTSTRAP_FACILITY_ID, LocalDate.now(), null, List.of(), bootstrapAdminId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ⚠12: "items with no price for the applicable payer are blocked, not
    // defaulted to zero."
    @Test
    void aServiceItemWithNoPriceForThePayerBlocksCreation() {
        UUID patientId = insertFixturePatient();
        String itemCode = "TEST-RECORDS-FEE-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update(
                "INSERT INTO service_item (code, name, category, is_active) VALUES (:code, 'Records fee', 'CONSULTATION', true)",
                new MapSqlParameterSource("code", itemCode));
        // No price_list_entry inserted for it.

        assertThatThrownBy(() -> createDraftInvoiceService.create(new CreateDraftInvoiceCommand(
                patientId, null, BOOTSTRAP_FACILITY_ID, LocalDate.now(), null,
                List.of(new CreateDraftInvoiceCommand.Line(itemCode, 1)), bootstrapAdminId())))
                .isInstanceOf(com.adags.modules.billing.domain.PriceNotConfiguredException.class);
    }

    private String insertFixtureServiceItemWithCashPrice(long amountMinor) {
        String code = "TEST-RECORDS-FEE-" + UUID.randomUUID().toString().substring(0, 8);
        ServiceItem item = serviceItemRepository.insert(new ServiceItem(null, code, "Records fee", ServiceItemCategory.CONSULTATION, true));

        UUID priceListId = jdbcTemplate.queryForObject(
                "SELECT id FROM price_list WHERE payer_category = 'CASH'", new MapSqlParameterSource(), UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, currency_code, created_by)
                        VALUES (:priceListId, :serviceItemId, :amountMinor, 'TZS', (SELECT id FROM app_user WHERE username = 'admin'))
                        """,
                new MapSqlParameterSource()
                        .addValue("priceListId", priceListId)
                        .addValue("serviceItemId", item.id())
                        .addValue("amountMinor", amountMinor));
        return code;
    }

    private UUID insertFixturePatient() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
