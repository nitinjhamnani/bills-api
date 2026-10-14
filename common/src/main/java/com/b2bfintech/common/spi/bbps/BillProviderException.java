package com.b2bfintech.common.spi.bbps;

/**
 * A catalog call (categories, locations, billers, parameters) failed. Bill fetch uses
 * {@link com.b2bfintech.common.spi.BillFetchFailedException} instead, because that one drives the
 * bill status state machine.
 */
public class BillProviderException extends RuntimeException {

    private final boolean permanent;
    private final String responseCode;

    public BillProviderException(String message, boolean permanent, String responseCode) {
        super(message);
        this.permanent = permanent;
        this.responseCode = responseCode;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public String responseCode() {
        return responseCode;
    }
}
