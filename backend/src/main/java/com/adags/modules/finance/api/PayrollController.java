package com.adags.modules.finance.api;

import com.adags.modules.finance.application.DisbursePayrollBatchCommand;
import com.adags.modules.finance.application.DisbursePayrollBatchService;
import com.adags.modules.finance.application.GeneratePayrollBatchService;
import com.adags.modules.finance.application.PreviewPayrollBatchService;
import com.adags.modules.finance.application.ReversePayrollBatchCommand;
import com.adags.modules.finance.application.ReversePayrollBatchService;
import com.adags.modules.finance.domain.SalaryDisbursementBatchRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

// 18-admin.md §5.1: preview -> generate -> disburse, all PAYROLL_MANAGE.
@RestController
@RequestMapping("/api/finance/payroll")
public class PayrollController {

    private final PreviewPayrollBatchService previewPayrollBatchService;
    private final GeneratePayrollBatchService generatePayrollBatchService;
    private final DisbursePayrollBatchService disbursePayrollBatchService;
    private final ReversePayrollBatchService reversePayrollBatchService;
    private final SalaryDisbursementBatchRepository batchRepository;
    private final AuthorizationService authorizationService;

    public PayrollController(
            PreviewPayrollBatchService previewPayrollBatchService,
            GeneratePayrollBatchService generatePayrollBatchService,
            DisbursePayrollBatchService disbursePayrollBatchService,
            ReversePayrollBatchService reversePayrollBatchService,
            SalaryDisbursementBatchRepository batchRepository,
            AuthorizationService authorizationService
    ) {
        this.previewPayrollBatchService = previewPayrollBatchService;
        this.generatePayrollBatchService = generatePayrollBatchService;
        this.disbursePayrollBatchService = disbursePayrollBatchService;
        this.reversePayrollBatchService = reversePayrollBatchService;
        this.batchRepository = batchRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/preview")
    public PayrollBatchPreviewResponse preview(
            @RequestParam int month, @RequestParam int year, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "PAYROLL_MANAGE");
        return PayrollBatchPreviewResponse.from(previewPayrollBatchService.preview(month, year));
    }

    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public SalaryDisbursementBatchResponse generate(
            @Valid @RequestBody GeneratePayrollBatchRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "PAYROLL_MANAGE");
        var batch = generatePayrollBatchService.generate(request.month(), request.year(), actor.userId());
        return SalaryDisbursementBatchResponse.from(batch);
    }

    @GetMapping("/batches/{id}")
    public SalaryDisbursementBatchResponse findById(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "PAYROLL_MANAGE");
        return batchRepository.findById(id)
                .map(SalaryDisbursementBatchResponse::from)
                .orElseThrow(() -> new NoSuchElementException("Payroll batch not found: " + id));
    }

    @PostMapping("/batches/{id}/disburse")
    public SalaryDisbursementBatchResponse disburse(
            @PathVariable UUID id, @Valid @RequestBody DisburseBatchRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "PAYROLL_MANAGE");
        var batch = disbursePayrollBatchService.disburse(
                new DisbursePayrollBatchCommand(id, actor.userId(), request.paymentMethod()));
        return SalaryDisbursementBatchResponse.from(batch);
    }

    @PostMapping("/batches/{id}/reverse")
    public SalaryDisbursementBatchResponse reverse(
            @PathVariable UUID id, @Valid @RequestBody ReverseBatchRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "PAYROLL_MANAGE");
        var batch = reversePayrollBatchService.reverse(
                new ReversePayrollBatchCommand(id, actor.userId(), request.reason()));
        return SalaryDisbursementBatchResponse.from(batch);
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
