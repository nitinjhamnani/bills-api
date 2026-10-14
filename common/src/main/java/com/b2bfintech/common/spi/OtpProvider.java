package com.b2bfintech.common.spi;

/**
 * SPI for phone-number OTP delivery/verification. Login no longer uses OTP.
 * Phase 0 ships {@code DummyOtpProvider} (notification-core) which accepts any phone number
 * with a fixed code; a real SMS/OTP gateway can be swapped in behind this same interface.
 */
public interface OtpProvider {

    /** Triggers OTP delivery to the given phone number (E.164 or bare 10-digit Indian number). */
    void sendOtp(String phoneNumber);

    /** @return true if {@code otp} is currently valid for {@code phoneNumber}. */
    boolean verifyOtp(String phoneNumber, String otp);
}
