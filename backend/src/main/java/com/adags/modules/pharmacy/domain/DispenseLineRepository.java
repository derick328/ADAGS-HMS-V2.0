package com.adags.modules.pharmacy.domain;

import java.util.List;
import java.util.UUID;

public interface DispenseLineRepository {

    DispenseLine insert(DispenseLine line);

    List<DispenseLine> findByDispenseId(UUID dispenseId);
}
