package com.adags.modules.insurance.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InsuranceSchemeRepository {

    InsuranceScheme insert(InsuranceScheme scheme);

    Optional<InsuranceScheme> findById(UUID id);

    Optional<InsuranceScheme> findByCode(String code);

    List<InsuranceScheme> findAllActive();
}
