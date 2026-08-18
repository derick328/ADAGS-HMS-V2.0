package com.adags.modules.inpatient.application;

import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.Bed;
import com.adags.modules.inpatient.domain.BedAssignment;
import com.adags.modules.inpatient.domain.BedAssignmentRepository;
import com.adags.modules.inpatient.domain.BedRepository;
import com.adags.modules.inpatient.domain.Ward;
import com.adags.modules.inpatient.domain.WardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

// 17-ward-nurse.md §8.1/§8.3: assigns a bed within the admission's ordered
// ward class (or above it, with a reason). The FIRST assignment for an
// admission also moves the encounter ADMITTED -> IN_WARD; every later call
// is a transfer — it releases the current bed and does not touch the
// encounter state again, which is already IN_WARD.
@Service
public class AssignBedService {

    private final AdmissionRepository admissionRepository;
    private final BedRepository bedRepository;
    private final WardRepository wardRepository;
    private final BedAssignmentRepository bedAssignmentRepository;
    private final EncounterFacade encounterFacade;
    private final Clock clock;

    public AssignBedService(
            AdmissionRepository admissionRepository,
            BedRepository bedRepository,
            WardRepository wardRepository,
            BedAssignmentRepository bedAssignmentRepository,
            EncounterFacade encounterFacade,
            Clock clock
    ) {
        this.admissionRepository = admissionRepository;
        this.bedRepository = bedRepository;
        this.wardRepository = wardRepository;
        this.bedAssignmentRepository = bedAssignmentRepository;
        this.encounterFacade = encounterFacade;
        this.clock = clock;
    }

    @Transactional
    public BedAssignment assign(AssignBedCommand command) {
        Admission admission = admissionRepository.findById(command.admissionId())
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + command.admissionId()));
        Bed bed = bedRepository.findById(command.bedId())
                .orElseThrow(() -> new NoSuchElementException("Bed not found: " + command.bedId()));
        Ward ward = wardRepository.findById(bed.wardId())
                .orElseThrow(() -> new NoSuchElementException("Ward not found: " + bed.wardId()));

        Optional<BedAssignment> currentAssignment = bedAssignmentRepository.findActiveByAdmissionId(command.admissionId());
        currentAssignment.ifPresent(active -> bedAssignmentRepository.release(active.id(), Instant.now(clock)));

        BedAssignment toInsert = BedAssignment.create(
                command.admissionId(), command.bedId(), ward.wardClass(), admission.orderedWardClass(),
                command.placementReason(), command.assignedBy());
        BedAssignment saved = bedAssignmentRepository.insert(toInsert);

        if (currentAssignment.isEmpty()) {
            encounterFacade.moveToWard(admission.encounterId(), command.assignedBy());
        }

        return saved;
    }
}
