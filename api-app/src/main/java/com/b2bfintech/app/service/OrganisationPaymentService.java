package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillFetch;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.provider.branchx.BranchXMapper;
import com.b2bfintech.billing.repository.BillFetchRepository;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.spi.BillFetchFailedException;
import com.b2bfintech.common.spi.BillingProvider;
import com.b2bfintech.wallet.service.WalletService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Paytrix settling a bill it holds: pick it up, then mark it paid with the references, or mark it
 * failed and send it back to the queue. Amounts at or above the BILL_PAYMENT approval threshold
 * need a distinct checker or an organisation admin to mark paid.
 */
@Service
public class OrganisationPaymentService {

    private final BillPaymentRepository billPaymentRepository;
    private final BillFetchRepository billFetchRepository;
    private final BillRepository billRepository;
    private final BillActivityLogService activityLogService;
    private final MakerCheckerPolicy makerCheckerPolicy;
    private final WalletService walletService;
    private final BillingProvider billingProvider;
    private final ObjectMapper objectMapper;

    public OrganisationPaymentService(
            BillPaymentRepository billPaymentRepository, BillFetchRepository billFetchRepository,
            BillRepository billRepository, BillActivityLogService activityLogService,
            MakerCheckerPolicy makerCheckerPolicy, WalletService walletService,
            BillingProvider billingProvider, ObjectMapper objectMapper) {
        this.billPaymentRepository = billPaymentRepository;
        this.billFetchRepository = billFetchRepository;
        this.billRepository = billRepository;
        this.activityLogService = activityLogService;
        this.makerCheckerPolicy = makerCheckerPolicy;
        this.walletService = walletService;
        this.billingProvider = billingProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BillPayment pickUp(UUID id, String actor) {
        BillPayment payment = getOrThrow(id);
        refuseAssigned(payment);
        if (payment.getOrgStatus() != BillPayment.OrgStatus.PENDING) {
            throw new BadRequestException("Bill payment is not pending pick-up");
        }
        payment.setBillAssignedToStatus(BillPayment.AssignedStatus.IN_PROGRESS);
        payment = billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.ORG_PICKED_UP,
                "Picked up by " + actor, actor);
        return payment;
    }

    /**
     * Excel import path: a row marked Paid/Failed is the settlement action. Pending rows are
     * picked up first so the workbook does not fail just because pick-up was not done in the UI.
     * Already paid rows are left as they are.
     */
    @Transactional
    public BillPayment settleFromImport(
            UUID id, AuthenticatedUser actor, boolean paid,
            String bbpsTransactionId, String externalTransactionId, String billType, String failureReason) {
        BillPayment payment = getOrThrow(id);
        refuseAssigned(payment);
        if (payment.getOrgStatus() == BillPayment.OrgStatus.PAID) {
            return payment;
        }
        if (payment.getOrgStatus() == BillPayment.OrgStatus.PENDING) {
            pickUp(id, actor.phoneNumber());
        }
        if (paid) {
            return markPaid(id, actor, bbpsTransactionId, externalTransactionId, billType);
        }
        return markFailed(id, actor.phoneNumber(), failureReason);
    }

    /** The bill must be picked up first, whether this is one click or an Excel row. */
    @Transactional
    public BillPayment markPaid(UUID id, AuthenticatedUser actor, String bbpsTransactionId, String externalTransactionId, String billType) {
        return finishPaid(id, actor, bbpsTransactionId, externalTransactionId,
                "Marked paid by " + actor.phoneNumber());
    }

    /**
     * Pays a Paytrix-held bill through the biller. A pending bill is picked up first. The provider
     * is called only after this person is allowed to settle it, and the bill stays unpaid when the
     * provider does not accept the payment.
     */
    @Transactional
    public BillPayment payWithBbps(UUID id, AuthenticatedUser actor) {
        BillPayment payment = getOrThrow(id);
        refuseAssigned(payment);
        if (payment.getOrgStatus() == BillPayment.OrgStatus.PAID) {
            return payment;
        }
        makerCheckerPolicy.assertCanDirectPay(actor, payment);
        Bill bill = billRepository.findById(payment.getBillId()).orElse(null);
        String mobile = bill == null ? null : bill.getCustomerMobileNumber();
        if (mobile == null || mobile.isBlank()) {
            throw new BadRequestException("Enter the mobile number for this bill before it can be paid");
        }
        String token = payToken(payment.getBillFetch());
        if (token == null) {
            throw new BadRequestException("Fetch this bill again before paying it through BBPS");
        }
        BigDecimal amount = payment.getBillFetch() != null && payment.getBillFetch().getBillAmount() != null
                ? payment.getBillFetch().getBillAmount()
                : payment.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("The amount must be greater than zero");
        }
        assertWalletCovers(amount);
        if (payment.getOrgStatus() == BillPayment.OrgStatus.PENDING) {
            payment = pickUp(id, actor.phoneNumber());
        }
        makerCheckerPolicy.assertCanSettleBill(actor, payment);

        BillingProvider.SettlementResult result;
        try {
            result = billingProvider.settleFetchedBill(new BillingProvider.FetchedBillSettlement(
                    amount, mobile, bill.getCustomerName(), token));
        } catch (BillFetchFailedException ex) {
            throw new BadRequestException(ex.getMessage());
        }
        if (result == null || !result.success()) {
            String message = result == null ? null : result.message();
            throw new BadRequestException(message == null || message.isBlank()
                    ? "Bill provider did not accept the payment" : message);
        }
        return finishPaid(id, actor, bbpsReference(result.reference(), result.message()), null,
                providerNote(actor.phoneNumber(), result.message(), result.data()));
    }

    private BillPayment finishPaid(
            UUID id, AuthenticatedUser actor, String bbpsTransactionId, String externalTransactionId, String activityNote) {
        BillPayment payment = getOrThrow(id);
        refuseAssigned(payment);
        if (payment.getOrgStatus() != BillPayment.OrgStatus.IN_PROCESS) {
            throw new BadRequestException("Bill payment must be picked up before it can be marked paid");
        }
        makerCheckerPolicy.assertCanSettleBill(actor, payment);
        payment.setBillPaidOn(Instant.now());
        payment.setBbpsExternalReference(clip(blankToNull(bbpsTransactionId), 120));
        payment.setTransactionReferenceNumber(blankToNull(externalTransactionId));
        payment = billPaymentRepository.save(payment);

        BillFetch fetch = payment.getBillFetch();
        fetch.setBillStatus(BillFetch.BillStatus.PAID);
        fetch.setBillPaymentStatus(BillFetch.PaymentStatus.PAID);
        billFetchRepository.save(fetch);

        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.ORG_PAID,
                clip(activityNote, 255), actor.phoneNumber());
        Bill bill = billRepository.findById(payment.getBillId()).orElse(null);
        walletService.recordBillerPayment(
                payment.getCorpId(), payment.getBillAmount(), actor.userId(), billerNote(bill, payment));
        MoneyActionLog.record("mark_paid", actor.userId(), payment.getId().toString());
        return payment;
    }

    /** The balance shown before Direct Pay. A failed lookup stays non-readable and is not thrown. */
    public BillingProvider.WalletBalance branchxWallet() {
        try {
            BillingProvider.WalletBalance wallet = billingProvider.walletBalance();
            return wallet == null
                    ? new BillingProvider.WalletBalance(false, null, null, null, "The wallet balance could not be read")
                    : wallet;
        } catch (BillFetchFailedException ex) {
            return new BillingProvider.WalletBalance(false, null, null, null, "The wallet balance could not be read");
        }
    }

    private void assertWalletCovers(BigDecimal amount) {
        BillingProvider.WalletBalance wallet = branchxWallet();
        if (!wallet.readable() || wallet.balance() == null) {
            throw new BadRequestException(wallet.message() == null || wallet.message().isBlank()
                    ? "The wallet balance could not be read" : wallet.message());
        }
        if (wallet.balance().compareTo(amount) < 0) {
            throw new BadRequestException("The BranchX wallet has " + rupee(wallet.balance())
                    + ". This payment needs " + rupee(amount) + ".");
        }
    }

    private static String rupee(BigDecimal amount) {
        return "₹" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String payToken(BillFetch fetch) {
        if (fetch == null || fetch.getFetchSnapshotJson() == null || fetch.getFetchSnapshotJson().isBlank()) {
            return null;
        }
        try {
            return BranchXMapper.payToken(objectMapper.readTree(fetch.getFetchSnapshotJson()));
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static String bbpsReference(String reference, String message) {
        String ref = blankToNull(reference);
        String msg = blankToNull(message);
        String value;
        if (ref != null && msg != null && !ref.equals(msg)) {
            value = ref + " · " + msg;
        } else {
            value = ref != null ? ref : msg;
        }
        return clip(value, 120);
    }

    private static String providerNote(String actor, String message, String rawData) {
        StringBuilder note = new StringBuilder("Paid through BBPS by ").append(actor == null ? "" : actor);
        if (message != null && !message.isBlank()) {
            note.append(". ").append(message.trim());
        }
        if (rawData != null && !rawData.isBlank() && !"null".equals(rawData)) {
            note.append(". ").append(rawData.trim());
        }
        return clip(note.toString(), 255);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String billerNote(Bill bill, BillPayment payment) {
        String category = bill == null || bill.getCategoryName() == null || bill.getCategoryName().isBlank()
                ? "bill" : bill.getCategoryName().trim();
        String consumer = payment.getConsumerId();
        String note = consumer == null || consumer.isBlank()
                ? "Paid the " + category + " bill to the biller"
                : "Paid the " + category + " bill " + consumer.trim() + " to the biller";
        return note.length() <= 300 ? note : note.substring(0, 300);
    }

    /** Sends it back to the queue so Paytrix can pick it up and try again. The reason is logged. */
    @Transactional
    public BillPayment markFailed(UUID id, String actor, String reason) {
        BillPayment payment = getOrThrow(id);
        refuseAssigned(payment);
        if (payment.getOrgStatus() != BillPayment.OrgStatus.IN_PROCESS) {
            throw new BadRequestException("Bill payment must be picked up before it can be marked failed");
        }
        payment.setBillAssignedToStatus(BillPayment.AssignedStatus.PENDING);
        payment = billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.ORG_MARK_FAILED, reason, actor);
        return payment;
    }

    private static void refuseAssigned(BillPayment payment) {
        if (payment.isWithVendor()) {
            throw new BadRequestException("This bill is with a vendor. Verify it from Settlements.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BillPayment getOrThrow(UUID id) {
        return billPaymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
    }
}
