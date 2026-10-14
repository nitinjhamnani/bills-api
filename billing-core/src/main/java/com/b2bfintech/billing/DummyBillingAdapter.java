package com.b2bfintech.billing;

import com.b2bfintech.common.spi.BillFetchFailedException;
import com.b2bfintech.common.spi.BillingProvider;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Local/dev placeholder for a real BBPS aggregator. Returns a random-but-plausible bill for any
 * biller/consumer number, and always succeeds on pay. The dual-status bill lifecycle worker calls
 * this via {@link BillingProvider} so swapping in a real aggregator later needs no change to any
 * caller - only a new {@link BillingProvider} implementation.
 *
 * <p><b>Dev/test magic suffixes</b> on the consumer number, since this dummy otherwise never
 * fails or returns a zero-due bill - both are real outcomes the bill status state machine has to
 * handle, so there needs to be a deterministic way to trigger them:
 * <ul>
 *   <li>ends with {@code 9999} - throws a transient {@link BillFetchFailedException}</li>
 *   <li>ends with {@code 0000} - throws a permanent {@link BillFetchFailedException}</li>
 *   <li>ends with {@code 0001} - returns a zero-amount ("already paid") bill</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.billing.provider", havingValue = "dummy", matchIfMissing = true)
public class DummyBillingAdapter implements BillingProvider {

    private static final Logger log = LoggerFactory.getLogger(DummyBillingAdapter.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("MMM-yyyy");
    private static final String[] SAMPLE_NAMES = {
            "Ravi Kumar", "Priya Sharma", "Amit Patel", "Sneha Iyer", "Vikram Singh",
            "Ananya Reddy", "Rahul Gupta", "Divya Nair", "Karan Mehta", "Pooja Joshi",
    };

    @Override
    public FetchedBill fetchBill(BbpsProvider.BillFetchCommand command) {
        String billerId = command.billerId();
        String billServiceCode = command.categoryId();
        String customerBillAccountNumber = command.accountNumber();
        log.info("[DUMMY BILLING] fetchBill biller={} service={} account={}", billerId, billServiceCode, customerBillAccountNumber);
        if (customerBillAccountNumber != null && customerBillAccountNumber.endsWith("9999")) {
            throw new BillFetchFailedException("Biller service temporarily unavailable, please retry", false);
        }
        if (customerBillAccountNumber != null && customerBillAccountNumber.endsWith("0000")) {
            throw new BillFetchFailedException("Invalid consumer number", true);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();

        LocalDate today = LocalDate.now();
        if (customerBillAccountNumber != null && customerBillAccountNumber.endsWith("0001")) {
            LocalDate paidBillDate = today.minusDays(random.nextInt(5, 20));
            LocalDate paidDueDate = today.plusDays(random.nextInt(5, 15));
            return new FetchedBill(
                    SAMPLE_NAMES[random.nextInt(SAMPLE_NAMES.length)], customerBillAccountNumber, BigDecimal.ZERO,
                    paidBillDate.format(DATE_FORMAT), paidDueDate.format(DATE_FORMAT),
                    "BILL" + random.nextLong(100_000_000L, 999_999_999L), today.format(PERIOD_FORMAT), List.of());
        }
        LocalDate billDate = today.minusDays(random.nextInt(5, 20));
        LocalDate dueDate = today.plusDays(random.nextInt(5, 15));
        BigDecimal amount = BigDecimal.valueOf(random.nextDouble(100, 5000)).setScale(2, RoundingMode.HALF_UP);
        String customerName = SAMPLE_NAMES[random.nextInt(SAMPLE_NAMES.length)];
        String billNumber = "BILL" + random.nextLong(100_000_000L, 999_999_999L);

        return new FetchedBill(
                customerName,
                customerBillAccountNumber,
                amount,
                billDate.format(DATE_FORMAT),
                dueDate.format(DATE_FORMAT),
                billNumber,
                today.format(PERIOD_FORMAT),
                buildBreakdown(amount, random));
    }

    /**
     * Splits the total into plausible line items that always sum exactly to it: an optional
     * arrears line and an optional late-fee line (both "plugged" first, each present about a
     * third/quarter of the time), a fixed-rate tax line, and Current Charges absorbing whatever
     * remains - so rounding never leaves the breakdown a cent off the total.
     */
    private List<AmountComponent> buildBreakdown(BigDecimal total, ThreadLocalRandom random) {
        BigDecimal arrears = random.nextInt(4) == 0
                ? BigDecimal.valueOf(random.nextDouble(50, 300)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal lateFee = random.nextInt(3) == 0
                ? BigDecimal.valueOf(random.nextDouble(10, 50)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal tax = total.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentCharges = total.subtract(arrears).subtract(lateFee).subtract(tax);

        if (currentCharges.signum() <= 0) {
            // Bill too small for extra line items to make sense - just charges + tax.
            arrears = BigDecimal.ZERO;
            lateFee = BigDecimal.ZERO;
            tax = total.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
            currentCharges = total.subtract(tax);
        }

        List<AmountComponent> breakdown = new ArrayList<>();
        breakdown.add(new AmountComponent("Current Charges", currentCharges));
        if (arrears.signum() > 0) breakdown.add(new AmountComponent("Arrears", arrears));
        if (lateFee.signum() > 0) breakdown.add(new AmountComponent("Late Payment Fee", lateFee));
        breakdown.add(new AmountComponent("Tax (GST)", tax));
        return breakdown;
    }

    @Override
    public PaymentResult pay(String idempotencyKey, String billServiceCode, String customerBillAccountNumber, BigDecimal amount) {
        log.info("[DUMMY BILLING] pay idempotencyKey={} service={} account={} amount={}",
                idempotencyKey, billServiceCode, customerBillAccountNumber, amount);
        return new PaymentResult(true, "DUMMY-BILL-" + idempotencyKey, null);
    }
}
