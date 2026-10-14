package com.b2bfintech.common.spi;

/**
 * Thrown by a {@link BillingProvider#fetchBill} implementation when the biller couldn't return a
 * bill. {@code permanent} distinguishes something retrying will never fix (bad consumer number)
 * from something transient (biller/aggregator temporarily unavailable) - this is exactly the
 * distinction the bill status state machine needs to decide between FAILED (terminal) and
 * PROCESSING (retried on the next scheduled fetch).
 */
public class BillFetchFailedException extends RuntimeException {

    private final boolean permanent;

    public BillFetchFailedException(String message, boolean permanent) {
        super(message);
        this.permanent = permanent;
    }

    public boolean isPermanent() {
        return permanent;
    }
}
