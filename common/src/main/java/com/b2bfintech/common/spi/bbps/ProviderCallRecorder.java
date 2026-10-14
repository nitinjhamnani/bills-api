package com.b2bfintech.common.spi.bbps;

/**
 * Durable audit of a bill-fetch call. Catalog lookups stay in the log only; a fetch that will
 * be paid needs to be answerable after logs have rotated. The token and raw parameter values
 * must already be masked by the caller.
 */
public interface ProviderCallRecorder {

    void record(ProviderCallRecord record);

    record ProviderCallRecord(
            String provider,
            String operation,
            String tenant,
            String billId,
            String clientRefId,
            int httpStatus,
            String responseCode,
            long durationMs,
            String maskedRequest,
            String truncatedResponse) {
    }
}
