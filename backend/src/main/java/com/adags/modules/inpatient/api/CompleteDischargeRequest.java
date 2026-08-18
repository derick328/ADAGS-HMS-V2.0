package com.adags.modules.inpatient.api;

import java.time.Instant;

/** actualDepartureAt is optional — null means "now." */
public record CompleteDischargeRequest(Instant actualDepartureAt) {
}
