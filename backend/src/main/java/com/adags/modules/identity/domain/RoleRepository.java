package com.adags.modules.identity.domain;

import java.util.List;
import java.util.Optional;

public interface RoleRepository {

    List<RoleSummary> findAll();

    Optional<RoleSummary> findByCode(String code);
}
