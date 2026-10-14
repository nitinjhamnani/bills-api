package com.b2bfintech.billing.provider.kbill;

/**
 * One failed KBill call, already classified as permanent or transient. Callers translate this
 * into {@link com.b2bfintech.common.spi.BillFetchFailedException} or
 * {@link com.b2bfintech.common.spi.bbps.BillProviderException}.
 */
public class KBillCallException extends RuntimeException {

    private final String responseCode;
    private final boolean permanent;
    private final int httpStatus;

    public KBillCallException(String message, String responseCode, boolean permanent, int httpStatus) {
        super(message);
        this.responseCode = responseCode;
        this.permanent = permanent;
        this.httpStatus = httpStatus;
    }

    public String responseCode() {
        return responseCode;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
