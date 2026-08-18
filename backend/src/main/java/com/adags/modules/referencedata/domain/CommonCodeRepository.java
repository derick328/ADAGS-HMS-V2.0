package com.adags.modules.referencedata.domain;

import java.util.UUID;

public interface CommonCodeRepository {

    /** departmentId null adds a hospital-wide entry. */
    CommonCode insert(CommonCode commonCode);
}
