package com.adags.modules.clinical.domain;

import java.util.UUID;

// One evaluated reading within a VitalSigns submission. appliedRangeId is
// the VitalReferenceRange row this value was actually checked against —
// snapshotted at write time so a later retune of the range (root CLAUDE.md
// "Data": price-versioning applied to reference data generally) does not
// silently rewrite what a historical reading meant.
public record RecordedVital(VitalType vitalType, double value, boolean abnormal, UUID appliedRangeId) {
}
