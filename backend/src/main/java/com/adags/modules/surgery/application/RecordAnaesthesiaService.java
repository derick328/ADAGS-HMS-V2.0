package com.adags.modules.surgery.application;

import com.adags.modules.surgery.domain.AnaesthesiaPaperRecord;
import com.adags.modules.surgery.domain.AnaesthesiaPaperRecordRepository;
import com.adags.modules.surgery.domain.ChecklistNotSatisfiedException;
import com.adags.modules.surgery.domain.ChecklistPhase;
import com.adags.modules.surgery.domain.SurgicalChecklistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Phase 8 item 7: "Creates no order, no charge, no stock movement" — no
// BillingFacade or InventoryFacade dependency here at all, deliberately.
// 04-*.md §7's own gate: "SIGN_IN incomplete -> anaesthesia cannot be
// recorded as started" — checked here, the one place anaesthesia
// recording actually happens.
@Service
public class RecordAnaesthesiaService {

    private final AnaesthesiaPaperRecordRepository anaesthesiaPaperRecordRepository;
    private final SurgicalChecklistRepository surgicalChecklistRepository;

    public RecordAnaesthesiaService(
            AnaesthesiaPaperRecordRepository anaesthesiaPaperRecordRepository,
            SurgicalChecklistRepository surgicalChecklistRepository
    ) {
        this.anaesthesiaPaperRecordRepository = anaesthesiaPaperRecordRepository;
        this.surgicalChecklistRepository = surgicalChecklistRepository;
    }

    @Transactional
    public AnaesthesiaPaperRecord record(RecordAnaesthesiaCommand command) {
        if (!surgicalChecklistRepository.isPhaseSatisfied(command.surgeryOrderId(), ChecklistPhase.SIGN_IN)) {
            throw new ChecklistNotSatisfiedException(command.surgeryOrderId(), ChecklistPhase.SIGN_IN);
        }

        AnaesthesiaPaperRecord toInsert = command.noAnaesthesia()
                ? AnaesthesiaPaperRecord.declareNoAnaesthesia(command.surgeryOrderId(), command.recordedBy())
                : AnaesthesiaPaperRecord.record(command.surgeryOrderId(), command.anaesthetistNameText(),
                        command.anaesthesiaTypeText(), command.paperRecordReference(), command.recordedBy());
        return anaesthesiaPaperRecordRepository.insert(toInsert);
    }
}
