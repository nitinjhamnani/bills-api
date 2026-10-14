package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.exception.UnauthorizedException;
import com.b2bfintech.organisation.service.CategoryEnablementService;
import com.b2bfintech.organisation.service.FeeConfigService;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The client's "My Payments" pay action (single or bulk): debits the wallet for a pending
 * {@link BillPayment} cycle and hands it off to the organisation ({@code org_status NA -> PENDING}).
 * The bill itself only ever tracks {@code payment_status} here - {@code bill_status} stays DUE
 * until the organisation actually pays the biller (see {@link OrganisationPaymentService}).
 */
@Service
public class ClientPaymentService {

    private final BillPaymentRepository billPaymentRepository;
    private final BillRepository billRepository;
    private final CategoryEnablementService categoryEnablementService;
    private final FeeConfigService feeConfigService;
    private final WalletService walletService;
    private final BillActivityLogService activityLogService;

    public ClientPaymentService(
            BillPaymentRepository billPaymentRepository, BillRepository billRepository,
            CategoryEnablementService categoryEnablementService, FeeConfigService feeConfigService,
            WalletService walletService, BillActivityLogService activityLogService) {
        this.billPaymentRepository = billPaymentRepository;
        this.billRepository = billRepository;
        this.categoryEnablementService = categoryEnablementService;
        this.feeConfigService = feeConfigService;
        this.walletService = walletService;
        this.activityLogService = activityLogService;
    }

    @Transactional
    public BillPayment payOne(UUID clientId, UUID billPaymentId, String actor) {
        BillPayment payment = billPaymentRepository.findById(billPaymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
        if (!payment.getClientId().equals(clientId)) {
            throw new UnauthorizedException("This bill payment does not belong to your client");
        }
        if (payment.getOrgStatus() != BillPayment.OrgStatus.NA) {
            throw new BadRequestException("This bill has already been paid");
        }
        Bill bill = billRepository.findById(payment.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        categoryEnablementService.assertEnabled(clientId,
                bill.getProviderCategoryId() != null ? bill.getProviderCategoryId() : bill.getBillServiceCode());

        walletService.debit(
                clientId, WalletAccount.WalletType.WALLET, payment.getAmount(),
                LedgerEntry.ReferenceType.BILL_PAYMENT, payment.getId().toString(), payment.getIdempotencyKey(),
                "Bill payment - " + bill.getBillServiceCode() + " / " + bill.getCustomerBillAccountNumber());

        feeConfigService.getApplicableFee(bill.getBillServiceCode()).ifPresent(fee -> {
            BigDecimal feeAmount = fee.computeFee(payment.getAmount());
            if (feeAmount.signum() > 0) {
                walletService.debit(clientId, WalletAccount.WalletType.WALLET, feeAmount,
                        LedgerEntry.ReferenceType.FEE, payment.getId().toString(), payment.getIdempotencyKey() + "-fee",
                        "Convenience fee for bill payment " + payment.getId());
            }
        });

        payment.setOrgStatus(BillPayment.OrgStatus.PENDING);
        payment.setPaymentStatus(Bill.PaymentStatus.PAID);
        payment.setClientPaidAt(Instant.now());
        payment.setClientPaidBy(actor);
        billPaymentRepository.save(payment);

        bill.setPaymentStatus(Bill.PaymentStatus.PAID);
        bill.setLastPaidDueDate(payment.getDueDate());
        billRepository.save(bill);

        activityLogService.log(bill.getId(), payment.getId(), BillPaymentLog.EventType.CLIENT_PAID,
                "Paid by " + actor + ", wallet debited Rs." + payment.getAmount(), actor);
        return payment;
    }

    @Transactional
    public List<BillPayment> pay(UUID clientId, List<UUID> billPaymentIds, String actor) {
        List<BillPayment> results = new ArrayList<>();
        for (UUID id : billPaymentIds) {
            results.add(payOne(clientId, id, actor));
        }
        return results;
    }
}
