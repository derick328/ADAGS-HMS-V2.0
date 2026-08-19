package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.WardStatus;
import com.adags.modules.inpatient.domain.WardStatusNotSettableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

// 17-ward-nurse.md §9: the nurse's own confirming action. Whether she
// agrees with an auto-suggested CRITICAL or not, this call is what
// actually changes ward_status — a breaching vital only ever proposes
// (see RecordWardVitalsService), never sets it directly.
@Service
public class UpdateWardStatusService {

    private final AdmissionRepository admissionRepository;
    private final Clock clock;

    public UpdateWardStatusService(AdmissionRepository admissionRepository, Clock clock) {
        this.admissionRepository = admissionRepository;
        this.clock = clock;
    }

    @Transactional
    public Admission update(UpdateWardStatusCommand command) {
        if (command.wardStatus() != WardStatus.ADMITTED && command.wardStatus() != WardStatus.STABLE
                && command.wardStatus() != WardStatus.CRITICAL) {
            throw new WardStatusNotSettableException(command.wardStatus());
        }
        return admissionRepository.updateWardStatus(
                command.admissionId(), command.wardStatus(), Instant.now(clock), command.actorUserId());
    }
}
