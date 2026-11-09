package com.b2bfintech.app.controller;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.security.ClientScopeGuard;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.service.WalletService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/** Paytrix and client reporting. */
@RestController
public class AnalyticsController {

    private final ClientRepository clientRepository;
    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final WalletService walletService;

    public AnalyticsController(
            ClientRepository clientRepository, BillRepository billRepository,
            BillPaymentRepository billPaymentRepository, WalletService walletService) {
        this.clientRepository = clientRepository;
        this.billRepository = billRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.walletService = walletService;
    }

    /** Client count, the My Payments funnel, and total GMV. */
    @GetMapping("/api/v1/paytrix/analytics")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
    public OrganisationAnalyticsDto organisationAnalytics() {
        return new OrganisationAnalyticsDto(
                clientRepository.count(),
                billPaymentRepository.countByOrgStatus(BillPayment.OrgStatus.PENDING),
                billPaymentRepository.countByOrgStatus(BillPayment.OrgStatus.IN_PROCESS),
                billPaymentRepository.countByOrgStatus(BillPayment.OrgStatus.PAID),
                billRepository.countByBillStatus(Bill.BillStatus.FAILED),
                billPaymentRepository.sumAmountByOrgStatus(BillPayment.OrgStatus.PAID));
    }

    /**
     * One client's own Managed Bills breakdown (from the {@code bill} table, so each unique bill
     * counts once regardless of how many fetch cycles it's had) plus its wallet snapshot:
     * <ul>
     *   <li>total - every managed bill</li>
     *   <li>pending - {@code paymentStatus = PENDING} (due, awaiting the client's payment)</li>
     *   <li>paid - {@code paymentStatus = PAID} (the client has paid, or it was already settled
     *       at zero due - either way, no more of the client's money is owed)</li>
     *   <li>completed - {@code billStatus = PAID} (the organisation has also paid the biller,
     *       so the bill's full lifecycle is closed)</li>
     * </ul>
     */
    @GetMapping("/api/v1/clients/{clientId}/analytics")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER','CLIENT_ADMIN','CLIENT_OPERATOR')")
    public ClientAnalyticsDto clientAnalytics(@PathVariable UUID clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new com.b2bfintech.common.exception.ResourceNotFoundException("Client not found"));
        BigDecimal wallet = client.getClientType() == Client.ClientType.VENDOR
                ? BigDecimal.ZERO
                : walletService.getBalance(clientId, WalletAccount.WalletType.WALLET);
        return new ClientAnalyticsDto(
                billRepository.countByClientId(clientId),
                billRepository.sumAmountByClientId(clientId),
                billRepository.countByClientIdAndPaymentStatus(clientId, Bill.PaymentStatus.PENDING),
                billRepository.sumAmountByClientIdAndPaymentStatus(clientId, Bill.PaymentStatus.PENDING),
                billRepository.countByClientIdAndPaymentStatus(clientId, Bill.PaymentStatus.PAID),
                billRepository.sumAmountByClientIdAndPaymentStatus(clientId, Bill.PaymentStatus.PAID),
                billRepository.countByClientIdAndBillStatus(clientId, Bill.BillStatus.PAID),
                billRepository.sumAmountByClientIdAndBillStatus(clientId, Bill.BillStatus.PAID),
                wallet);
    }

    public record OrganisationAnalyticsDto(long clientCount, long billsPendingPickup, long billsInProcess,
                                       long billsPaid, long billsFailed, BigDecimal totalGmv) {
    }

    public record ClientAnalyticsDto(
            long totalBills, BigDecimal totalAmount,
            long pendingBills, BigDecimal pendingAmount,
            long paidBills, BigDecimal paidAmount,
            long completedBills, BigDecimal completedAmount,
            BigDecimal walletBalance) {
    }
}
