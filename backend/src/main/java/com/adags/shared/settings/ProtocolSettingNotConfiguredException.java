package com.adags.shared.settings;

// A caller asked for a setting it treats as always-seeded (getDouble(key))
// and no system-wide row exists. Thrown rather than silently substituting
// a value, because a fallback that happens to match the seed is never
// exercised — a wiped or unseeded table then looks identical to a healthy
// one. That silence is exactly what let a test-truncation bug (TRUNCATE
// ... department CASCADE wiping protocol_setting via its FK) survive
// undetected across fifteen test files.
public class ProtocolSettingNotConfiguredException extends RuntimeException {

    public ProtocolSettingNotConfiguredException(String settingKey) {
        super("No system-wide protocol_setting row for '" + settingKey + "' — expected a migration to have seeded it.");
    }
}
