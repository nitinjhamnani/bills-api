package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillFetch;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.repository.BillFetchRepository;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.service.CategoryEnablementService;
import com.b2bfintech.organisation.service.FeeConfigService;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Paying a due from My Dues debits the wallet, marks that fetch paid, and records one payment.
 * The payment then goes to the matching vendor, or stays with Paytrix.
 */
@Service
public class ClientPaymentService {

    private final BillPaymentRepository billPaymentRepository;
    private final BillFetchRepository billFetchRepository;
    private final BillRepository billRepository;
    private final CategoryEnablementService categoryEnablementService;
    private final FeeConfigService feeConfigService;
    private final WalletService walletService;
    private final BillActivityLogService activityLogService;
    private final ClientAccountGuard clientAccountGuard;
    private final CommissionAssignmentService commissionAssignmentService;

    public ClientPaymentService(
            BillPaymentRepository billPaymentRepository, BillFetchRepository billFetchRepository, BillRepository billRepository,
            CategoryEnablementService categoryEnablementService, FeeConfigService feeConfigService,
            WalletService walletService, BillActivityLogService activityLogService,
            ClientAccountGuard clientAccountGuard, CommissionAssignmentService commissionAssignmentService) {
        this.billPaymentRepository = billPaymentRepository;
        this.billFetchRepository = billFetchRepository;
        this.billRepository = billRepository;
        this.categoryEnablementService = categoryEnablementService;
        this.feeConfigService = feeConfigService;
        this.walletService = walletService;
        this.activityLogService = activityLogService;
        this.clientAccountGuard = clientAccountGuard;
        this.commissionAssignmentService = commissionAssignmentService;
    }

    @Transactional
    public BillPayment payDue(String clientId, UUID billFetchId, String userId) {
        Client corporate = clientAccountGuard.requireActiveCorporate(clientId);
        BillFetch fetch = billFetchRepository.findById(billFetchId)
                .orElseThrow(() -> new ResourceNotFoundException("Due not found"));
        Bill bill = billRepository.findById(fetch.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Due not found"));
        if (bill.getSourceType() != Bill.SourceType.CORP || !clientId.equals(bill.getSourceId())) {
            throw new ResourceNotFoundException("Due not found");
        }
        if (fetch.getBillPaymentStatus() == BillFetch.PaymentStatus.PAID
                || billPaymentRepository.findByBillFetchBillFetchId(billFetchId).isPresent()) {
            throw new BadRequestException("This bill has already been paid");
        }
        if (fetch.getFetchStatus() != BillFetch.FetchStatus.SUCCESS
                || fetch.getBillAmount() == null || fetch.getBillAmount().signum() <= 0) {
            throw new BadRequestException("This bill has nothing due");
        }
        categoryEnablementService.assertEnabled(clientId, bill.getCategoryName());

        Instant now = Instant.now();
        BillPayment payment = new BillPayment();
        payment.setBillFetch(fetch);
        payment.setBillAmount(fetch.getBillAmount());
        payment.setCorpPaymentOn(now);
        payment.setCorpId(clientId);
        payment.setCorpName(corporate.getClientName());
        payment.setCorpPaidUserId(userId);
        payment.setCorpPaymentStatus(BillPayment.CorpPaymentStatus.PAID);
        payment.assignToPaytrix(now);
        payment = billPaymentRepository.save(payment);

        String category = LedgerEntry.categoryOf(bill.getCategoryName());
        walletService.debit(clientId, payment.getBillAmount(), category, userId);
        BillPayment paid = payment;
        feeConfigService.getApplicableFee(bill.getCategoryName()).ifPresent(fee -> {
            BigDecimal feeAmount = fee.computeFee(paid.getBillAmount());
            if (feeAmount.signum() > 0) {
                walletService.debit(clientId, feeAmount, category, userId);
            }
        });

        fetch.setBillPaymentStatus(BillFetch.PaymentStatus.PAID);
        billFetchRepository.save(fetch);

        activityLogService.log(bill.getId(), payment.getId(), BillPaymentLog.EventType.CLIENT_PAID,
                "Paid, wallet debited Rs." + payment.getBillAmount(), userId);
        commissionAssignmentService.assignIfMatched(payment, bill, userId);
        MoneyActionLog.record("pay", userId, payment.getId().toString());
        return payment;
    }

    @Transactional
    public List<BillPayment> payDues(String clientId, List<UUID> billFetchIds, String userId) {
        if (billFetchIds == null || billFetchIds.isEmpty()) {
            throw new BadRequestException("Choose at least one bill");
        }
        List<BillPayment> results = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(billFetchIds)) {
            results.add(payDue(clientId, id, userId));
        }
        return results;
    }
}
