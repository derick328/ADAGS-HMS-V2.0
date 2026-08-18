package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.NursingAssignment;
import com.adags.modules.inpatient.domain.NursingAssignmentRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

// 17-ward-nurse.md §8.2: the roster row itself, not a mutated field —
// UNIQUE (admission_id, nurse_id, shift, assignment_date) lets the same
// nurse be re-recorded for a shift without erroring, and lets a different
// nurse cover the same shift on a later day.
@Service
public class AssignNurseService {

    private final AdmissionRepository admissionRepository;
    private final NursingAssignmentRepository nursingAssignmentRepository;

    public AssignNurseService(AdmissionRepository admissionRepository, NursingAssignmentRepository nursingAssignmentRepository) {
        this.admissionRepository = admissionRepository;
        this.nursingAssignmentRepository = nursingAssignmentRepository;
    }

    public NursingAssignment assign(AssignNurseCommand command) {
        admissionRepository.findById(command.admissionId())
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + command.admissionId()));

        NursingAssignment toInsert = new NursingAssignment(null, command.admissionId(), command.nurseId(),
                command.shift(), command.assignmentDate(), command.assignedBy(), null);
        return nursingAssignmentRepository.insert(toInsert);
    }
}
