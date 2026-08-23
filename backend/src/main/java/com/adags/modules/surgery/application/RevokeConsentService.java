package com.adags.modules.surgery.application;

import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Task text (item 1): "Check again at procedure start — consent may be
// revoked." This is the only path that flips consent_obtained back to
// FALSE after scheduling — StartSurgeryOrderService is what reads it live.
@Service
public class RevokeConsentService {

    private final SurgeryOrderRepository surgeryOrderRepository;

    public RevokeConsentService(SurgeryOrderRepository surgeryOrderRepository) {
        this.surgeryOrderRepository = surgeryOrderRepository;
    }

    @Transactional
    public void revoke(RevokeConsentCommand command) {
        surgeryOrderRepository.revokeConsent(command.surgeryOrderId());
    }
}
