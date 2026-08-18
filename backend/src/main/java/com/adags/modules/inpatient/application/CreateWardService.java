package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.Ward;
import com.adags.modules.inpatient.domain.WardRepository;
import com.adags.shared.authz.ScopeHierarchyPort;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

// WARD_BED_MANAGE — root CLAUDE.md "Admin has no clinical data access":
// defining ward inventory is administrative setup, not a clinical action,
// so ADMIN holding this permission does not violate that invariant.
@Service
public class CreateWardService {

    private final WardRepository wardRepository;
    private final ScopeHierarchyPort scopeHierarchyPort;

    public CreateWardService(WardRepository wardRepository, ScopeHierarchyPort scopeHierarchyPort) {
        this.wardRepository = wardRepository;
        this.scopeHierarchyPort = scopeHierarchyPort;
    }

    public Ward create(CreateWardCommand command) {
        UUID facilityId = scopeHierarchyPort.parentOf(ScopeRef.of(ScopeType.DEPARTMENT, command.departmentId()))
                .orElseThrow(() -> new NoSuchElementException("Department not found: " + command.departmentId()))
                .id();

        Ward toInsert = new Ward(null, facilityId, command.departmentId(), command.code(), command.name(),
                command.wardClass(), command.genderRestriction(), command.dailyRate(), true, null);
        return wardRepository.insert(toInsert);
    }
}
