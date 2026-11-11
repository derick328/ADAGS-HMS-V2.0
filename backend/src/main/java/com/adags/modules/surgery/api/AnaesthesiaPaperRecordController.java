package com.adags.modules.surgery.api;

import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.surgery.application.RecordAnaesthesiaCommand;
import com.adags.modules.surgery.application.RecordAnaesthesiaService;
import com.adags.modules.surgery.domain.AnaesthesiaPaperRecord;
import com.adags.modules.surgery.domain.AnaesthesiaPaperRecordRepository;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.EncounterContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/anaesthesia-paper-records")
public class AnaesthesiaPaperRecordController {

    private final RecordAnaesthesiaService recordAnaesthesiaService;
    private final AnaesthesiaPaperRecordRepository anaesthesiaPaperRecordRepository;
    private final SurgeryOrderRepository surgeryOrderRepository;
    private final EncounterFacade encounterFacade;
    private final AuthorizationService authorizationService;

    public AnaesthesiaPaperRecordController(
            RecordAnaesthesiaService recordAnaesthesiaService,
            AnaesthesiaPaperRecordRepository anaesthesiaPaperRecordRepository,
            SurgeryOrderRepository surgeryOrderRepository,
            EncounterFacade encounterFacade,
            AuthorizationService authorizationService
    ) {
        this.recordAnaesthesiaService = recordAnaesthesiaService;
        this.anaesthesiaPaperRecordRepository = anaesthesiaPaperRecordRepository;
        this.surgeryOrderRepository = surgeryOrderRepository;
        this.encounterFacade = encounterFacade;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnaesthesiaPaperRecordResponse record(
            @Valid @RequestBody RecordAnaesthesiaRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, request.surgeryOrderId());
        RecordAnaesthesiaCommand command = new RecordAnaesthesiaCommand(request.surgeryOrderId(),
                request.noAnaesthesia(), request.anaesthetistNameText(), request.anaesthesiaTypeText(),
                request.paperRecordReference(), actor.userId());
        AnaesthesiaPaperRecord record = recordAnaesthesiaService.record(command);
        return AnaesthesiaPaperRecordResponse.from(record);
    }

    @GetMapping
    public List<AnaesthesiaPaperRecordResponse> listBySurgeryOrder(
            @RequestParam UUID surgeryOrderId, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, surgeryOrderId);
        return anaesthesiaPaperRecordRepository.findBySurgeryOrderId(surgeryOrderId).stream()
                .map(AnaesthesiaPaperRecordResponse::from).toList();
    }

    private void requirePermission(Actor actor, UUID surgeryOrderId) {
        SurgeryOrder order = surgeryOrderRepository.findById(surgeryOrderId)
                .orElseThrow(() -> new NoSuchElementException("Surgery order not found: " + surgeryOrderId));
        EncounterContext context = encounterFacade.findContext(order.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + order.encounterId()));
        if (!authorizationService.hasPermission(
                actor, "SURGERY_EPISODE_DOCUMENT", context.scope())) {
            throw new AccessDeniedException("Missing permission: SURGERY_EPISODE_DOCUMENT");
        }
    }
}
