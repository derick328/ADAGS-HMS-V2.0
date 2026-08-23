package com.adags.modules.surgery.application;

import com.adags.modules.surgery.domain.ProcedureItem;
import com.adags.modules.surgery.domain.ProcedureItemAlreadyAttestedException;
import com.adags.modules.surgery.domain.ProcedureItemNotReconciledException;
import com.adags.modules.surgery.domain.ProcedureItemRepository;
import com.adags.modules.surgery.domain.ProcedureItemSelfReconciliationNotAllowedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// Phase 8 item 3: the surgeon's half of reconciliation — attesting to
// counts the circulating nurse already entered. root CLAUDE.md task text:
// "CHECK (reconciled_by != attested_by) would be ideal but both may be
// nullable during the procedure; enforce at the service layer with the
// same pattern as other second-person checks" — this is that check.
@Service
public class AttestProcedureItemService {

    private final ProcedureItemRepository procedureItemRepository;

    public AttestProcedureItemService(ProcedureItemRepository procedureItemRepository) {
        this.procedureItemRepository = procedureItemRepository;
    }

    @Transactional
    public ProcedureItem attest(AttestProcedureItemCommand command) {
        ProcedureItem item = procedureItemRepository.findById(command.procedureItemId())
                .orElseThrow(() -> new NoSuchElementException("Procedure item not found: " + command.procedureItemId()));
        if (item.reconciledBy() == null) {
            throw new ProcedureItemNotReconciledException(item.id());
        }
        if (item.attestedBy() != null) {
            throw new ProcedureItemAlreadyAttestedException(item.id());
        }
        if (item.reconciledBy().equals(command.attestedBy())) {
            throw new ProcedureItemSelfReconciliationNotAllowedException(item.id());
        }

        boolean updated = procedureItemRepository.attest(item.id(), command.attestedBy());
        if (!updated) {
            throw new ProcedureItemAlreadyAttestedException(item.id());
        }

        return procedureItemRepository.findById(item.id()).orElseThrow();
    }
}
