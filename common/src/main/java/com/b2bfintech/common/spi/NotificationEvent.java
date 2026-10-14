package com.b2bfintech.common.spi;

import java.util.Map;

/**
 * A single notification to deliver (SMS/email/push/webhook - channel is the provider's choice).
 * {@code type} is a stable event code (e.g. {@code BILL_SETTLED}, {@code LOW_WALLET_BALANCE},
 * {@code REWARD_CREDITED}) so real adapters can template/route without changing callers.
 */
public record NotificationEvent(
        String type,
        String recipientPhoneNumber,
        String recipientUserId,
        String tenantSchema,
        Map<String, String> data
) {
}
