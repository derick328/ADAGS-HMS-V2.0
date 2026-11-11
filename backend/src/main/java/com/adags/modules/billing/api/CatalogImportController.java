package com.adags.modules.billing.api;

import com.adags.modules.billing.application.ApplyCatalogImportService;
import com.adags.modules.billing.application.CatalogImportPreview;
import com.adags.modules.billing.application.CatalogItemLookupPort;
import com.adags.modules.billing.application.CatalogItemQuery;
import com.adags.modules.billing.application.CatalogPriceManagementService;
import com.adags.modules.billing.application.DeactivateAllCatalogItemsService;
import com.adags.modules.billing.application.PreviewCatalogImportService;
import com.adags.modules.billing.domain.CatalogBillingCategory;
import com.adags.modules.billing.domain.CatalogImportBatch;
import com.adags.modules.billing.domain.CatalogImportBatchRepository;
import com.adags.modules.billing.domain.CatalogItemType;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// Phase 5/9 catalogue import — admin REST surface. Two-stage: preview
// reports the diff; apply writes it in one transaction. History
// endpoints let an auditor see who uploaded and applied what.
//
// Every endpoint gates on CATALOG_IMPORT (V67 seeded to ADMIN only).
@RestController
@RequestMapping("/api/admin/catalog")
public class CatalogImportController {

    private final PreviewCatalogImportService previewService;
    private final ApplyCatalogImportService applyService;
    private final CatalogImportBatchRepository batchRepository;
    private final CatalogItemLookupPort catalogItemLookup;
    private final DeactivateAllCatalogItemsService deactivateAllService;
    private final AuthorizationService authorizationService;
    private final CatalogPriceManagementService priceManagement;
    private final CatalogWorkbookExporter workbookExporter;

    public CatalogImportController(
            PreviewCatalogImportService previewService,
            ApplyCatalogImportService applyService,
            CatalogImportBatchRepository batchRepository,
            CatalogItemLookupPort catalogItemLookup,
            DeactivateAllCatalogItemsService deactivateAllService,
            AuthorizationService authorizationService,
            CatalogPriceManagementService priceManagement,
            CatalogWorkbookExporter workbookExporter
    ) {
        this.previewService = previewService;
        this.applyService = applyService;
        this.batchRepository = batchRepository;
        this.catalogItemLookup = catalogItemLookup;
        this.deactivateAllService = deactivateAllService;
        this.authorizationService = authorizationService;
        this.priceManagement = priceManagement;
        this.workbookExporter = workbookExporter;
    }

    @PostMapping(path = "/import/preview", consumes = { "multipart/form-data" })
    public CatalogImportPreviewResponse preview(
            @RequestParam("file") MultipartFile file, @AuthenticationPrincipal Actor actor) throws IOException {
        requirePermission(actor);
        if (file.isEmpty()) throw new IllegalArgumentException("The uploaded file is empty.");
        CatalogImportPreview preview = previewService.preview(
                file.getBytes(), file.getOriginalFilename(), actor.userId());
        return CatalogImportPreviewResponse.from(preview);
    }

    @PostMapping("/import/{batchId}/apply")
    public CatalogImportBatchResponse apply(@PathVariable UUID batchId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor);
        CatalogImportBatch applied = applyService.apply(batchId, actor.userId());
        return CatalogImportBatchResponse.from(applied);
    }

    @PostMapping("/import/{batchId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID batchId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor);
        batchRepository.markCancelled(batchId);
    }

    @GetMapping("/import/batches")
    public List<CatalogImportBatchResponse> list(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor);
        return batchRepository.listRecent(50).stream().map(CatalogImportBatchResponse::from).toList();
    }

    @GetMapping("/import/batches/{batchId}")
    public CatalogImportBatchResponse get(@PathVariable UUID batchId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor);
        return CatalogImportBatchResponse.from(batchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Batch not found: " + batchId)));
    }

    // Admin browse of everything in the catalogue — the primary
    // "View catalogue items" surface. Search hits name/code via ILIKE
    // (backed by the V67 trigram index on lower(name)); type /
    // billingCategory narrow to one bucket. activeOnly defaults to true.
    @GetMapping("/items")
    public CatalogItemPageResponse listItems(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CatalogItemType type,
            @RequestParam(required = false) CatalogBillingCategory billingCategory,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor);
        CatalogItemQuery query = new CatalogItemQuery(q, type, billingCategory, activeOnly, page, pageSize);
        int total = catalogItemLookup.countItems(query);
        List<CatalogItemResponse> items = catalogItemLookup.listItems(query).stream()
                .map(CatalogItemResponse::from).toList();
        return new CatalogItemPageResponse(query.page(), query.pageSize(), total, items);
    }

    @PutMapping("/items/{itemId}/price")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update an active catalogue item's CASH price", description = "Requires CATALOG_IMPORT. expectedPriceMinor prevents a stale administrator edit; amounts are whole TZS minor units. Existing invoice lines keep their original amounts.")
    @ApiResponse(responseCode = "200", description = "Versioned price change")
    @ApiResponse(responseCode = "400", description = "Invalid amount", content = @Content)
    @ApiResponse(responseCode = "403", description = "Insufficient permission", content = @Content)
    @ApiResponse(responseCode = "404", description = "Item not found", content = @Content)
    @ApiResponse(responseCode = "409", description = "Inactive, unpriced, or concurrently changed item", content = @Content)
    public CatalogPriceManagementService.PriceChange updatePrice(
            @PathVariable UUID itemId, @jakarta.validation.Valid @RequestBody UpdateCatalogPriceRequest request,
            @AuthenticationPrincipal Actor actor) {
        requirePermission(actor);
        priceManagement.updateCashPrice(itemId, request.expectedPriceMinor(), request.newPriceMinor(), actor.userId());
        return priceManagement.history(itemId).get(0);
    }

    @GetMapping("/items/{itemId}/price-history")
    public List<CatalogPriceManagementService.PriceChange> priceHistory(
            @PathVariable UUID itemId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor);
        return priceManagement.history(itemId);
    }

    @GetMapping(value = "/items/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Download the complete current price catalogue", description = "Requires CATALOG_IMPORT; includes active and inactive items across all catalogue pages.")
    @ApiResponse(responseCode = "200", description = "Excel workbook", content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "403", description = "Insufficient permission", content = @Content)
    public ResponseEntity<StreamingResponseBody> exportItems(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor);
        String filename = "ADAGS_Price_Catalogue_" + java.time.LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workbookExporter::export);
    }

    // Bulk soft-deactivate. Typed confirmation enforced in the service so
    // a direct facade call from another module could not bypass it.
    @PostMapping("/items/deactivate-all")
    public DeactivateAllCatalogItemsResponse deactivateAll(
            @RequestBody DeactivateAllCatalogItemsRequest request, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor);
        int deactivated = deactivateAllService.deactivateAll(request.confirmationPhrase(), actor.userId());
        return new DeactivateAllCatalogItemsResponse(deactivated);
    }

    private void requirePermission(Actor actor) {
        if (!authorizationService.hasPermission(actor, "CATALOG_IMPORT")) {
            throw new AccessDeniedException("Missing permission: CATALOG_IMPORT");
        }
    }
}
