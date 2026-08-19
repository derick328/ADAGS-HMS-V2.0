package com.adags.modules.finance.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffRepository {

    Staff insert(Staff staff);

    Optional<Staff> findById(UUID id);

    /** Every active staff member — what payroll generation iterates over. */
    List<Staff> findAllActive();
}
