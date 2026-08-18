package com.adags.modules.referencedata.domain;

import java.util.UUID;

public interface ReferenceSearchLogRepository {

    ReferenceSearchLog insert(ReferenceSearchLog log);

    /** Filled in after the picker's selection — the row starts with selectedId null. */
    void recordSelection(UUID logId, String selectedId);
}
