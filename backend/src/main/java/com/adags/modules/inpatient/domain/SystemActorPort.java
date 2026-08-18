package com.adags.modules.inpatient.domain;

import java.util.UUID;

// A real, always-present app_user id to attribute an automated posting to
// (WardChargeAccrualService's nightly job) — same convention as V15's
// price_list_entry seed (the earliest-created user), not a hardcoded
// username, since ADAGS_BOOTSTRAP_ADMIN_USERNAME is configurable.
public interface SystemActorPort {

    UUID systemActorId();
}
