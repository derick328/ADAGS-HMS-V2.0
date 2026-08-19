package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.RefundStatus;

import java.util.UUID;

public record ReviewRefundCommand(UUID refundId, RefundStatus decision, UUID decidedBy, String decisionNote) {
}
