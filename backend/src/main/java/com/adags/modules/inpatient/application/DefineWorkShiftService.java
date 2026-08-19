package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.WorkShift;
import com.adags.modules.inpatient.domain.WorkShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 19-work-shifts-roster.md §2: admin defines shift patterns once; they recur.
@Service
public class DefineWorkShiftService {

    private final WorkShiftRepository workShiftRepository;

    public DefineWorkShiftService(WorkShiftRepository workShiftRepository) {
        this.workShiftRepository = workShiftRepository;
    }

    @Transactional
    public WorkShift define(DefineWorkShiftCommand command) {
        WorkShift workShift = WorkShift.define(command.code(), command.name(), command.departmentId(),
                command.startTime(), command.endTime(), command.crossesMidnight(), command.daysOfWeek(),
                command.createdBy());
        return workShiftRepository.insert(workShift);
    }
}
