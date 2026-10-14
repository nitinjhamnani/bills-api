package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.BillDispute;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillDisputeRepository;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Customer-disputed bill payments, raised by a client and resolved by the organisation
 * (implementation plan &sect;8). A dispute resolved as "valid" refunds the bill payment's amount
 * back to the client's wallet.
 */
@Service
public class DisputeService {

    private final BillDisputeRepository disputeRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final WalletService walletService;

    public DisputeService(BillDisputeRepository disputeRepository, BillPaymentRepository billPaymentRepository, WalletService walletService) {
        this.disputeRepository = disputeRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.walletService = walletService;
    }

    @Transactional
    public BillDispute raise(UUID billPaymentId, String reason, String raisedBy) {
        if (!billPaymentRepository.existsById(billPaymentId)) {
            throw new ResourceNotFoundException("Bill payment not found");
        }
        BillDispute dispute = new BillDispute();
        dispute.setBillPaymentId(billPaymentId);
        dispute.setReason(reason);
        dispute.setRaisedBy(raisedBy);
        return disputeRepository.save(dispute);
    }

    @Transactional
    public BillDispute resolve(UUID disputeId, boolean valid, String notes, String resolvedBy) {
        BillDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found"));
        if (dispute.getStatus() == BillDispute.Status.RESOLVED_VALID || dispute.getStatus() == BillDispute.Status.RESOLVED_INVALID) {
            throw new BadRequestException("This dispute has already been resolved");
        }
        dispute.setResolutionNotes(notes);
        dispute.setResolvedBy(resolvedBy);
        dispute.setStatus(valid ? BillDispute.Status.RESOLVED_VALID : BillDispute.Status.RESOLVED_INVALID);
        dispute = disputeRepository.save(dispute);

        if (valid) {
            BillPayment payment = billPaymentRepository.findById(dispute.getBillPaymentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
            walletService.credit(
                    payment.getClientId(), WalletAccount.WalletType.WALLET, payment.getAmount(),
                    LedgerEntry.ReferenceType.REFUND, payment.getId().toString(), "refund-dispute-" + dispute.getId(),
                    "Refund for upheld dispute on bill payment " + payment.getId());
        }
        return dispute;
    }

    public List<BillDispute> listForBillPayment(UUID billPaymentId) {
        return disputeRepository.findByBillPaymentId(billPaymentId);
    }

    public List<BillDispute> listAll() {
        return disputeRepository.findAllByOrderByCreatedAtDesc();
    }
}
