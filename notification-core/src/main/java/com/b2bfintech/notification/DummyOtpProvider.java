package com.b2bfintech.notification;

import com.b2bfintech.common.spi.OtpProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production-safe no-op {@link OtpProvider} until a real SMS OTP gateway is integrated.
 * Never issues or accepts any fixed/dummy OTP code.
 */
@Component
public class DummyOtpProvider implements OtpProvider {

    private static final Logger log = LoggerFactory.getLogger(DummyOtpProvider.class);

    @Override
    public void sendOtp(String phoneNumber) {
        log.warn("OTP delivery requested for {}, but no live SMS OTP gateway is configured", phoneNumber);
        throw new UnsupportedOperationException("SMS OTP delivery is not enabled");
    }

    @Override
    public boolean verifyOtp(String phoneNumber, String otp) {
        return false;
    }
}
