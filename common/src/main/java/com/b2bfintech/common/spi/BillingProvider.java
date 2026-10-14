package com.b2bfintech.common.spi;

import com.b2bfintech.common.spi.bbps.BbpsProvider;

import java.math.BigDecimal;
import java.util.List;

/**
 * SPI for the actual bill-fetch / bill-pay rail (a BBPS aggregator in production). {@code
 * billing-core} wires the dummy implementation; swapping in a real BBPS aggregator later means
 * writing a new {@link BillingProvider} implementation, not touching any caller of this interface.
 */
public interface BillingProvider {

    /**
     * Fetches the current bill. The command carries the biller, the category, the account number
     * and every extra field that biller declared — different billers ask for different fields.
     */
    FetchedBill fetchBill(BbpsProvider.BillFetchCommand command);

    PaymentResult pay(String idempotencyKey, String billServiceCode, String customerBillAccountNumber, BigDecimal amount);

    /** Mirrors the shape of a real BBPS Bill Fetch response (customer/bill identification + amount + dates). */
    record FetchedBill(
            String customerName,
            String consumerNumber,
            BigDecimal amount,
            String billDate,
            String dueDate,
            String billNumber,
            String billPeriod,
            List<AmountComponent> breakdown) {
    }

    /** One line of a bill's amount break-up (e.g. "Current Charges", "Arrears", "Late Payment Fee", "Tax (GST)"). */
    record AmountComponent(String label, BigDecimal amount) {
    }

    record PaymentResult(boolean success, String providerReferenceId, String failureReason) {
    }
}
