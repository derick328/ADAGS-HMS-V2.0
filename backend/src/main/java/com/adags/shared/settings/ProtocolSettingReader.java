package com.adags.shared.settings;

// docs/spec/11-clinical-lead-role.md §6: the system-wide/department-scoped
// key-value store for thresholds that must be tunable without a redeploy.
// Read-only here — writes go through a future admin-facing facade once
// 18-admin.md §12's settings screen exists; this is only what callers like
// patient duplicate detection need today.
public interface ProtocolSettingReader {

    double getDouble(String settingKey, double defaultValue);
}
