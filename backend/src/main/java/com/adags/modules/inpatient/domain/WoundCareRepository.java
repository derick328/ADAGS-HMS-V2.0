package com.adags.modules.inpatient.domain;

import java.util.List;
import java.util.UUID;

public interface WoundCareRepository {

    WoundCare insert(WoundCare woundCare);

    List<WoundCare> findByAdmissionId(UUID admissionId);
}
