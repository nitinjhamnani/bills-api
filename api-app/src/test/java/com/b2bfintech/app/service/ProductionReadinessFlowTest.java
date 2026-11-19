package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillFetch;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.repository.BillFetchRepository;
import com.b2bfintech.billing.repository.BillPaymentLogRepository;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.ClientScopeGuard;
import com.b2bfintech.organisation.entity.ApprovalRule;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.FeeConfig;
import com.b2bfintech.organisation.service.ApprovalRuleService;
import com.b2bfintech.organisation.service.CategoryEnablementService;
import com.b2bfintech.organisation.service.FeeConfigService;
import com.b2bfintech.platform.entity.BbpsCategoryEntity;
import com.b2bfintech.platform.repository.BbpsCategoryRepository;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionReadinessFlowTest {

    @Test
    void payDueDebitsBillAndConvenienceFeeUnderDistinctLedgerCategoriesAndRoutesCommission() {
        BillPaymentRepository billPaymentRepository = mock(BillPaymentRepository.class);
        BillFetchRepository billFetchRepository = mock(BillFetchRepository.class);
        BillRepository billRepository = mock(BillRepository.class);
        CategoryEnablementService categoryEnablementService = mock(CategoryEnablementService.class);
        FeeConfigService feeConfigService = mock(FeeConfigService.class);
        WalletService walletService = mock(WalletService.class);
        BillActivityLogService activityLogService = mock(BillActivityLogService.class);
        ClientAccountGuard clientAccountGuard = mock(ClientAccountGuard.class);
        CommissionAssignmentService commissionAssignmentService = mock(CommissionAssignmentService.class);
        BbpsCategoryRepository categoryRepository = mock(BbpsCategoryRepository.class);

        ClientPaymentService service = new ClientPaymentService(
                billPaymentRepository, billFetchRepository, billRepository,
                categoryEnablementService, feeConfigService, walletService,
                activityLogService, clientAccountGuard, commissionAssignmentService, categoryRepository);

        Client corporate = mock(Client.class);
        when(corporate.getClientName()).thenReturn("Meridian Enterprises");
        when(clientAccountGuard.requireActiveCorporate("COSSI266")).thenReturn(corporate);

        UUID fetchId = UUID.randomUUID();
        UUID categoryPk = UUID.randomUUID();
        Bill bill = new Bill();
        bill.setBillId("GQHGSA7PFA");
        bill.setSourceType(Bill.SourceType.CORP);
        bill.setSourceId("COSSI266");
        bill.setCategoryId(categoryPk);
        bill.setCategoryName("Electricity");
        bill.setOperatorId("BESCOM000KAR01");
        bill.setOperatorName("Bangalore Electricity Supply Co. Ltd (BESCOM)");
        bill.setPrimaryParamValue("4505757");

        BillFetch fetch = new BillFetch();
        fetch.setBillFetchId(fetchId);
        fetch.setBillId("GQHGSA7PFA");
        fetch.setFetchStatus(BillFetch.FetchStatus.SUCCESS);
        fetch.setBillStatus(BillFetch.BillStatus.DUE);
        fetch.setBillPaymentStatus(BillFetch.PaymentStatus.PENDING);
        fetch.setBillAmount(new BigDecimal("1425.50"));

        BbpsCategoryEntity categoryEntity = mock(BbpsCategoryEntity.class);
        when(categoryEntity.getCategoryId()).thenReturn("13");

        FeeConfig fee = new FeeConfig();
        fee.setBillServiceCode("13");
        fee.setFeeType(FeeConfig.FeeType.FLAT);
        fee.setValue(new BigDecimal("10.0000"));
        fee.setEnabled(true);

        when(billFetchRepository.findById(fetchId)).thenReturn(Optional.of(fetch));
        when(billRepository.findById("GQHGSA7PFA")).thenReturn(Optional.of(bill));
        when(billPaymentRepository.findByBillFetchBillFetchId(fetchId)).thenReturn(Optional.empty());
        when(categoryRepository.findById(categoryPk)).thenReturn(Optional.of(categoryEntity));
        when(feeConfigService.getApplicableFee("13")).thenReturn(Optional.of(fee));
        when(billPaymentRepository.save(any(BillPayment.class))).thenAnswer(inv -> {
            BillPayment saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        BillPayment payment = service.payDue("COSSI266", fetchId, "9000000001");

        assertEquals(BillFetch.PaymentStatus.PAID, fetch.getBillPaymentStatus());
        assertEquals(new BigDecimal("1425.50"), payment.getBillAmount());
        verify(categoryEnablementService).assertEnabled("COSSI266", "13");
        verify(walletService).debit("COSSI266", new BigDecimal("1425.50"), "ELECTRICITY", "9000000001");
        verify(commissionAssignmentService).assignIfMatched(payment, bill, "9000000001");
    }

    @Test
    void makerCheckerBlocksSelfSettlementAcrossUserIdPhoneAndUserCode() {
        ApprovalRuleService approvalRuleService = mock(ApprovalRuleService.class);
        BillPaymentLogRepository logRepository = mock(BillPaymentLogRepository.class);
        MakerCheckerPolicy policy = new MakerCheckerPolicy(approvalRuleService, logRepository);

        UUID paymentId = UUID.randomUUID();
        BillPayment payment = new BillPayment();
        payment.setId(paymentId);
        payment.setBillAmount(new BigDecimal("25000.00"));
        payment.assignToPaytrix(java.time.Instant.now());
        payment.setBillAssignedToStatus(BillPayment.AssignedStatus.IN_PROGRESS);

        when(approvalRuleService.requiresApproval(ApprovalRule.ActionType.BILL_PAYMENT, new BigDecimal("25000.00")))
                .thenReturn(true);

        BillPaymentLog pickedByUserId = new BillPaymentLog();
        pickedByUserId.setEventType(BillPaymentLog.EventType.ORG_PICKED_UP);
        pickedByUserId.setActor("USR-UUID-1");
        when(logRepository.findByBillPaymentIdOrderByCreatedAtDesc(paymentId)).thenReturn(List.of(pickedByUserId));

        AuthenticatedUser sameChecker = new AuthenticatedUser(
                "USR-UUID-1", "9000000099", "public", "CHECKER", "Ops Checker", null, "ORG009", null);
        AuthenticatedUser distinctChecker = new AuthenticatedUser(
                "USR-UUID-2", "9000000088", "public", "CHECKER", "Second Checker", null, "ORG010", null);

        assertFalse(policy.canSettleBill(sameChecker, payment));
        assertThrows(AccessDeniedException.class, () -> policy.assertCanSettleBill(sameChecker, payment));
        assertTrue(policy.canSettleBill(distinctChecker, payment));
    }

    @Test
    void clientScopeGuardThrowsForbiddenInsteadOfUnauthorized() {
        AuthenticatedUser branchUser = new AuthenticatedUser(
                "USR-BR-1", "9000000021", "public", "BRANCH_USER", "BLR Branch User", "COSSI266", "BRU01", "5B1649OK");

        ClientScopeGuard.assertCanAccess(branchUser, "COSSI266");
        assertThrows(AccessDeniedException.class, () -> ClientScopeGuard.assertCanAccess(branchUser, "OTHER999"));
        assertThrows(AccessDeniedException.class, () -> ClientScopeGuard.assertCanAccess(branchUser, ""));
    }
}
