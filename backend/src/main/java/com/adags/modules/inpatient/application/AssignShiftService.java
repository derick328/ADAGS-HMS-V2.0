package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.ShiftAssignment;
import com.adags.modules.inpatient.domain.ShiftAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 19-work-shifts-roster.md §3: "Rostering is delegated — admin defines the
// shift patterns, department heads or the clinical lead assign people to
// them." The UNIQUE (user_id, work_shift_id, shift_date) constraint (V39)
// is the real guard against double-rostering one person onto the same
// shift twice, not an application pre-check.
@Service
public class AssignShiftService {

    private final ShiftAssignmentRepository shiftAssignmentRepository;

    public AssignShiftService(ShiftAssignmentRepository shiftAssignmentRepository) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
    }

    @Transactional
    public ShiftAssignment assign(AssignShiftCommand command) {
        ShiftAssignment assignment = ShiftAssignment.assign(command.userId(), command.workShiftId(),
                command.shiftDate(), command.wardId(), command.departmentId(), command.roleContext(), command.assignedBy());
        return shiftAssignmentRepository.insert(assignment);
    }
}
