package com.adags.modules.billing.api;

import com.adags.modules.billing.application.CreateDraftInvoiceCommand;
import com.adags.modules.billing.application.CreateDraftInvoiceService;
import com.adags.modules.billing.application.IssueInvoiceService;
import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.InvoiceState;
import com.adags.modules.billing.domain.PaymentAllocationRepository;
import com.adags.modules.billing.domain.ServiceItemRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 05-receptionist.md §7.2/§8, 02-billing-architecture.md §10/§11: create
// (DRAFT, multi-line, catalogue-backed), the separate explicit Issue step,
// and read/list — cancellation-request already lives on
// InvoiceCancellationController (/api/billing/invoices/{id}/cancellation-requests),
// reused as-is rather than duplicated here.
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final CreateDraftInvoiceService createDraftInvoiceService;
    private final IssueInvoiceService issueInvoiceService;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final AuthorizationService authorizationService;

    public InvoiceController(
            CreateDraftInvoiceService createDraftInvoiceService,
            IssueInvoiceService issueInvoiceService,
            InvoiceRepository invoiceRepository,
            InvoiceLineRepository invoiceLineRepository,
            ServiceItemRepository serviceItemRepository,
            PaymentAllocationRepository paymentAllocationRepository,
            AuthorizationService authorizationService
    ) {
        this.createDraftInvoiceService = createDraftInvoiceService;
        this.issueInvoiceService = issueInvoiceService;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public List<InvoiceResponse> search(
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID encounterId,
            @RequestParam(required = false) List<InvoiceState> states,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "false") boolean outstandingOnly,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "INVOICE_READ");
        return invoiceRepository.search(patientId, encounterId, states, createdFrom, createdTo, outstandingOnly)
                .stream().map(InvoiceResponse::from).toList();
    }

    @GetMapping("/{id}")
    public InvoiceResponse getById(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "INVOICE_READ");
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Invoice not found: " + id));
        return InvoiceResponse.from(invoice);
    }

    @GetMapping("/{id}/lines")
    public List<InvoiceLineResponse> lines(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "INVOICE_READ");
        return invoiceLineRepository.findByInvoiceId(id).stream()
                .map(line -> {
                    String name = serviceItemRepository.findById(line.serviceItemId()).map(item -> item.name()).orElse("—");
                    long settled = paymentAllocationRepository.findSettledByInvoiceLineId(line.id()).stream()
                            .mapToLong(a -> a.amount().amountMinor())
                            .sum();
                    return InvoiceLineResponse.from(line, name, settled);
                })
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@Valid @RequestBody CreateInvoiceRequest request, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "INVOICE_CREATE");
        List<CreateDraftInvoiceCommand.Line> lines = request.lines().stream()
                .map(line -> new CreateDraftInvoiceCommand.Line(line.serviceItemCode(), line.quantity()))
                .toList();
        Invoice draft = createDraftInvoiceService.create(new CreateDraftInvoiceCommand(
                request.patientId(), request.encounterId(), request.facilityId(), request.dueDate(),
                request.notes(), lines, actor.userId()));
        return InvoiceResponse.from(draft);
    }

    @PostMapping("/{id}/issue")
    public InvoiceResponse issue(
            @PathVariable UUID id, @Valid @RequestBody IssueInvoiceRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "INVOICE_CREATE");
        Invoice draft = invoiceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Invoice not found: " + id));
        Invoice issued = issueInvoiceService.issue(id, draft.facilityId(), request.expectedVersion());
        return InvoiceResponse.from(issued);
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
