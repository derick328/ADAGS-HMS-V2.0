package com.adags.modules.billing.api;

import com.adags.modules.billing.application.CloseCashierShiftService;
import com.adags.modules.billing.application.OpenCashierShiftService;
import com.adags.modules.billing.domain.CashierShift;
import com.adags.modules.billing.domain.CashierShiftRepository;
import com.adags.modules.billing.domain.PaymentRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.kernel.Money;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 05-receptionist.md §7.1, 02-billing-architecture.md §9.1. Close and
// reconcile are the Cashier Shift screen's own build pass — this is what
// the dashboard's "My shift total" tile and PAYMENT_CREATE's "no shift
// open" prompt need: open, current, and this shift's payments.
@RestController
@RequestMapping("/api/cashier-shifts")
public class CashierShiftController {

    private final OpenCashierShiftService openCashierShiftService;
    private final CloseCashierShiftService closeCashierShiftService;
    private final CashierShiftRepository cashierShiftRepository;
    private final PaymentRepository paymentRepository;
    private final AuthorizationService authorizationService;

    public CashierShiftController(
            OpenCashierShiftService openCashierShiftService,
            CloseCashierShiftService closeCashierShiftService,
            CashierShiftRepository cashierShiftRepository,
            PaymentRepository paymentRepository,
            AuthorizationService authorizationService
    ) {
        this.openCashierShiftService = openCashierShiftService;
        this.closeCashierShiftService = closeCashierShiftService;
        this.cashierShiftRepository = cashierShiftRepository;
        this.paymentRepository = paymentRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CashierShiftResponse open(@Valid @RequestBody OpenCashierShiftRequest request, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "CASHIER_SHIFT_OPEN");
        CashierShift shift = openCashierShiftService.open(request.facilityId(), actor.userId());
        return CashierShiftResponse.from(shift);
    }

    @GetMapping("/current")
    public CashierShiftResponse current(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "CASHIER_SHIFT_OPEN");
        CashierShift shift = cashierShiftRepository.findOpenByOpenedBy(actor.userId())
                .orElseThrow(() -> new NoSuchElementException("No open cashier shift for the current user."));
        return CashierShiftResponse.from(shift);
    }

    // 05-receptionist.md §7.1: "at end of shift they enter counted cash;
    // the system computes expected, records the variance." Reconciliation
    // (a distinct actor, per the DB CHECK) is the accountant's separate
    // action, not built here.
    @PostMapping("/{id}/close")
    public CashierShiftResponse close(
            @PathVariable UUID id, @Valid @RequestBody CloseCashierShiftRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "CASHIER_SHIFT_CLOSE_OWN");
        CashierShift shift = closeCashierShiftService.close(id, actor.userId(), new Money(request.countedCashMinor(), "TZS"));
        return CashierShiftResponse.from(shift);
    }

    @GetMapping("/{id}/payments")
    public List<PaymentResponse> payments(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "CASHIER_SHIFT_OPEN");
        return paymentRepository.findByCashierShiftId(id).stream().map(PaymentResponse::from).toList();
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
