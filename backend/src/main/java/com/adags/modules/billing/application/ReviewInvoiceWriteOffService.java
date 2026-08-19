package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.InvoiceWriteOff;
import com.adags.modules.billing.domain.InvoiceWriteOffAlreadyDecidedException;
import com.adags.modules.billing.domain.InvoiceWriteOffRepository;
import com.adags.modules.billing.domain.InvoiceWriteOffSelfApprovalNotAllowedException;
import com.adags.modules.billing.domain.WriteOffStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// The second signature for a write-off that landed PENDING because it
// exceeded the threshold — a below-threshold write-off is never PENDING
// (RequestInvoiceWriteOffService applies it immediately), so nothing here
// needs to re-check the threshold, only that the reviewer isn't the
// requester.
@Service
public class ReviewInvoiceWriteOffService {

    private final InvoiceWriteOffRepository invoiceWriteOffRepository;
    private final InvoiceRepository invoiceRepository;

    public ReviewInvoiceWriteOffService(
            InvoiceWriteOffRepository invoiceWriteOffRepository,
            InvoiceRepository invoiceRepository
    ) {
        this.invoiceWriteOffRepository = invoiceWriteOffRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public InvoiceWriteOff review(ReviewInvoiceWriteOffCommand command) {
        if (command.decision() != WriteOffStatus.APPLIED && command.decision() != WriteOffStatus.REJECTED) {
            throw new IllegalArgumentException("A review decision must be APPLIED or REJECTED, not " + command.decision() + ".");
        }
        InvoiceWriteOff writeOff = invoiceWriteOffRepository.findById(command.writeOffId())
                .orElseThrow(() -> new NoSuchElementException("Write-off not found: " + command.writeOffId()));
        if (writeOff.status() != WriteOffStatus.PENDING) {
            throw new InvoiceWriteOffAlreadyDecidedException(writeOff.id());
        }
        if (writeOff.requestedBy().equals(command.decidedBy())) {
            throw new InvoiceWriteOffSelfApprovalNotAllowedException();
        }

        if (command.decision() == WriteOffStatus.APPLIED) {
            invoiceRepository.recordWriteOff(writeOff.invoiceId(), writeOff.amount());
        }
        return invoiceWriteOffRepository.decide(writeOff.id(), command.decision(), command.decidedBy(), command.decisionNote());
    }
}
