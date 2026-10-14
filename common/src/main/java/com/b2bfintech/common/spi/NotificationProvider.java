package com.b2bfintech.common.spi;

/**
 * SPI for all non-OTP notifications (bill status, low balance, reward credited, redemption
 * status, KYC/onboarding status, disputes, SLA breaches - see plan &sect;8.4). Phase 0 ships a
 * dummy adapter that just logs; a real SMS/email/push/webhook provider is swapped in later.
 */
public interface NotificationProvider {
    void send(NotificationEvent event);
}
