package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CreditNote;
import com.adags.modules.billing.domain.CreditNoteAlreadyDecidedException;
import com.adags.modules.billing.domain.CreditNoteRepository;
import com.adags.modules.billing.domain.CreditNoteSelfApprovalNotAllowedException;
import com.adags.modules.billing.domain.CreditNoteStatus;
import com.adags.modules.billing.domain.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// The second signature for a credit note that landed PENDING because it
// exceeded the threshold — a below-threshold credit note is never
// PENDING (IssueCreditNoteService applies it immediately at issue).
@Service
public class ReviewCreditNoteService {

    private final CreditNoteRepository creditNoteRepository;
    private final InvoiceRepository invoiceRepository;

    public ReviewCreditNoteService(CreditNoteRepository creditNoteRepository, InvoiceRepository invoiceRepository) {
        this.creditNoteRepository = creditNoteRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public CreditNote review(ReviewCreditNoteCommand command) {
        if (command.decision() != CreditNoteStatus.APPLIED && command.decision() != CreditNoteStatus.REJECTED) {
            throw new IllegalArgumentException("A review decision must be APPLIED or REJECTED, not " + command.decision() + ".");
        }
        CreditNote creditNote = creditNoteRepository.findById(command.creditNoteId())
                .orElseThrow(() -> new NoSuchElementException("Credit note not found: " + command.creditNoteId()));
        if (creditNote.status() != CreditNoteStatus.PENDING) {
            throw new CreditNoteAlreadyDecidedException(creditNote.id());
        }
        if (creditNote.issuedBy().equals(command.decidedBy())) {
            throw new CreditNoteSelfApprovalNotAllowedException();
        }

        if (command.decision() == CreditNoteStatus.APPLIED) {
            invoiceRepository.recordCreditNote(creditNote.invoiceId(), creditNote.amount());
        }
        return creditNoteRepository.decide(creditNote.id(), command.decision(), command.decidedBy(), command.decisionNote());
    }
}
