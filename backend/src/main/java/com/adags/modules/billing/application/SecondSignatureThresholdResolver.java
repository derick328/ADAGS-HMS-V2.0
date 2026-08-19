package com.adags.modules.billing.application;

import com.adags.shared.settings.ProtocolSettingReader;
import org.springframework.stereotype.Component;

// Shared by every "second signature above a configurable threshold"
// mechanism in this module — write-offs, credit notes, refunds all read
// the same protocol_setting (V43/V44's shared brief: "the same
// configurable threshold as write-offs," not a separate one per
// mechanism).
@Component
public class SecondSignatureThresholdResolver {

    static final String SETTING_KEY = "billing.write_off.second_signature_threshold_minor";
    private static final double DEFAULT_MINOR = 10_000_000;

    private final ProtocolSettingReader protocolSettingReader;

    public SecondSignatureThresholdResolver(ProtocolSettingReader protocolSettingReader) {
        this.protocolSettingReader = protocolSettingReader;
    }

    /** True when amountMinor exceeds the configured threshold and therefore requires a second, distinct signer. */
    public boolean requiresSecondSignature(long amountMinor) {
        double thresholdMinor = protocolSettingReader.getDouble(SETTING_KEY, DEFAULT_MINOR);
        return amountMinor > thresholdMinor;
    }
}
