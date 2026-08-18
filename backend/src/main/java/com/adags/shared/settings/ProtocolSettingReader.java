package com.adags.shared.settings;

// docs/spec/11-clinical-lead-role.md §6: the system-wide/department-scoped
// key-value store for thresholds that must be tunable without a redeploy.
// Read-only here — writes go through a future admin-facing facade once
// 18-admin.md §12's settings screen exists; this is only what callers like
// patient duplicate detection need today.
//
// Two shapes, deliberately not one: a caller that depends on a key a
// migration seeds (the common case — every current caller) must use the
// throwing overload, so a missing row is a loud failure at the call site
// instead of a fallback value nobody can distinguish from a correctly
// configured one. The defaulting overload exists only for a setting that
// is genuinely allowed to be unconfigured; using the default is logged at
// WARN precisely so that case stays visible instead of blending in.
public interface ProtocolSettingReader {

    /** Throws ProtocolSettingNotConfiguredException if no system-wide row exists for settingKey. */
    double getDouble(String settingKey);

    /**
     * For a setting that is genuinely optional — logs at WARN when
     * settingKey has no row and defaultValue is used, so a missing row
     * stays visible rather than looking identical to a configured one.
     */
    double getDouble(String settingKey, double defaultValue);
}
