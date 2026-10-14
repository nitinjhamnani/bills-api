package com.b2bfintech.notification;

import com.b2bfintech.common.spi.OtpProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Local/dev placeholder for the real SMS/OTP gateway: accepts ANY phone number and issues/accepts
 * a single fixed code (default {@code 123456}, overridable via {@code app.otp.dummy-code}).
 * No SMS is actually sent - {@link #sendOtp} just logs. Swap this bean out for a real
 * {@link OtpProvider} implementation in Phase 5 without changing any caller.
 */
@Component
public class DummyOtpProvider implements OtpProvider {

    private static final Logger log = LoggerFactory.getLogger(DummyOtpProvider.class);

    private final String fixedOtp;

    public DummyOtpProvider(@Value("${app.otp.dummy-code:123456}") String fixedOtp) {
        this.fixedOtp = fixedOtp;
    }

    @Override
    public void sendOtp(String phoneNumber) {
        log.info("[DUMMY OTP] Would send OTP '{}' to {} (dummy provider - no real SMS sent)", fixedOtp, phoneNumber);
    }

    @Override
    public boolean verifyOtp(String phoneNumber, String otp) {
        return fixedOtp.equals(otp);
    }
}
