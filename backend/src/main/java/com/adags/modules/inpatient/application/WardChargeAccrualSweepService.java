package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Orchestrates WardChargeAccrualService.accrueForAdmission across every
// currently-occupied admission. Deliberately NOT @Transactional itself —
// each admission's accrual is its own transaction (accrueForAdmission's
// own boundary via the injected bean, not a self-invoked call, so its
// @Transactional actually takes effect); one admission failing (e.g. a
// misconfigured price) must not roll back, or block, every other
// admission's accrual in the same sweep.
@Service
public class WardChargeAccrualSweepService {

    private static final Logger log = LoggerFactory.getLogger(WardChargeAccrualSweepService.class);

    private final AdmissionRepository admissionRepository;
    private final WardChargeAccrualService wardChargeAccrualService;

    public WardChargeAccrualSweepService(AdmissionRepository admissionRepository, WardChargeAccrualService wardChargeAccrualService) {
        this.admissionRepository = admissionRepository;
        this.wardChargeAccrualService = wardChargeAccrualService;
    }

    /** Returns the total number of bed-day rows posted across every admission in this sweep. */
    public int sweep() {
        int totalPosted = 0;
        for (Admission admission : admissionRepository.findAllWithActiveBedAssignment()) {
            try {
                totalPosted += wardChargeAccrualService.accrueForAdmission(admission.id());
            } catch (RuntimeException failure) {
                log.error("Bed-day accrual failed for admission {}", admission.id(), failure);
            }
        }
        return totalPosted;
    }
}
