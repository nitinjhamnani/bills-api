package com.b2bfintech.app.controller;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillFetch;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.common.spi.BillingProvider;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** DTOs and small helpers shared by every bill-related controller (Managed Bills, My Payments -
 * both client and organisation side), mirroring how the old BillController centralized these. */
final class BillDtos {

    private static final ObjectMapper JSON = new ObjectMapper();

    private BillDtos() {
    }

    static Function<String, String> clientNameLookup(ClientRepository clientRepository) {
        Map<String, String> names = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getId, Client::getClientName, (a, b) -> a));
        return id -> names.getOrDefault(id, "Unknown client");
    }

    static Function<String, String> clientCodeLookup(ClientRepository clientRepository) {
        Map<String, String> codes = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getId, c -> c.getClientCode() != null ? c.getClientCode() : "", (a, b) -> a));
        return id -> codes.getOrDefault(id, "");
    }

    static List<AmountComponentDto> toAmountComponentDtos(List<BillingProvider.AmountComponent> breakdown) {
        if (breakdown == null) return List.of();
        return breakdown.stream().map(c -> new AmountComponentDto(c.label(), c.amount())).toList();
    }

    static List<AmountComponentDto> parseAmountBreakdown(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<AmountComponentDto>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    record AmountComponentDto(String label, BigDecimal amount) {
    }

    /** One row of the corporate's My Payments: the bill_payment columns plus the bill it paid. */
    record CorpPaymentDto(
            String id, String billFetchId, String billId, String billerName, String categoryName,
            String consumerNumber, String dueDate, String billCycle, String branchId, String branchName,
            BigDecimal billAmount, Instant corpPaymentOn, String corpId, String corpName, String corpPaidUserId,
            String corpPaymentStatus, String billAssignedTo, String billAssignedToId, String billAssignedToName,
            String billAssignedToStatus, Instant billAssignedOn, Instant billPaidOn,
            String transactionReferenceNumber, String bbpsExternalReference, String paymentProof,
            String verifiedBy, String verifiedById, Instant verifiedOn, boolean isVerified) {

        static CorpPaymentDto from(BillPayment p, Bill bill, Function<String, String> branchNameLookup) {
            return new CorpPaymentDto(
                    p.getId().toString(), p.getBillFetch().getBillFetchId().toString(), bill.getId(),
                    bill.getOperatorName(), bill.getCategoryName(), bill.getPrimaryParamValue(),
                    p.getDueDate(), p.getBillPeriod(), bill.getBranchId(),
                    branchNameLookup == null ? null : branchNameLookup.apply(bill.getBranchId()),
                    p.getBillAmount(), p.getCorpPaymentOn(), p.getCorpId(), p.getCorpName(), p.getCorpPaidUserId(),
                    p.getCorpPaymentStatus().name(), p.getBillAssignedTo().name(), p.getBillAssignedToId(),
                    p.getBillAssignedToName(), p.getBillAssignedToStatus().name(), p.getBillAssignedOn(),
                    p.getBillPaidOn(), p.getTransactionReferenceNumber(), p.getBbpsExternalReference(),
                    p.getPaymentProof(), p.getVerifiedBy(), p.getVerifiedById(), p.getVerifiedOn(), p.isVerified());
        }
    }

    record BulkRowResult(int row, boolean success, String billId, String error) {
    }

    record ActivityLogDto(String eventType, String description, String actor, Instant createdAt) {
        static ActivityLogDto from(BillPaymentLog l) {
            return new ActivityLogDto(l.getEventType().name(), l.getDescription(), l.getActor(), l.getCreatedAt());
        }
    }

    /** {@code balanceAfter} and {@code description} are worked out when read. Neither is stored. */
    record LedgerEntryDto(String walletType, String entryType, BigDecimal amount, BigDecimal balanceAfter,
                           String category, String description, String createdBy, Instant createdAt) {
        static LedgerEntryDto from(LedgerEntry e, BigDecimal balanceAfter) {
            return new LedgerEntryDto(
                    WalletAccount.WalletType.WALLET.name(), e.getEntryType().name(), e.getAmount(),
                    balanceAfter, e.getCategory(), e.describe(), e.getCreatedBy(), e.getCreatedAt());
        }

        /** {@code oldestFirst} with the balance before the first one. Returned newest first. */
        static List<LedgerEntryDto> withBalances(List<LedgerEntry> oldestFirst, BigDecimal opening) {
            List<LedgerEntryDto> rows = new java.util.ArrayList<>(oldestFirst.size());
            BigDecimal running = opening;
            for (LedgerEntry entry : oldestFirst) {
                running = running.add(entry.signedAmount());
                rows.add(from(entry, running));
            }
            java.util.Collections.reverse(rows);
            return rows;
        }
    }

    /** A Managed Bill - one row per unique bill account, always the latest fetch cycle's data.
     * {@code clientName} is only populated on the organisation-wide view, which lists every
     * client's bills together and needs to show whose bill each row is. */
    record ManagedBillDto(
            String id, String clientId, String clientName, String clientCode,
            String billServiceCode, String billServiceName, String billerName, String billerId,
            String customerBillAccountNumber, String customerName, String customerMobileNumber,
            BigDecimal amount, List<AmountComponentDto> amountBreakdown,
            String billNumber, String billDate, String dueDate, String billPeriod,
            String paymentStatus, String billStatus,
            Instant lastFetchAt, boolean lastFetchFailed, String lastFetchFailureReason,
            boolean autoFetchEnabled, boolean autoPayEnabled,
            Instant createdAt, Instant updatedAt,
            String branchId, String branchName) {

        static ManagedBillDto from(Bill b, Function<String, String> serviceNameLookup) {
            return from(b, serviceNameLookup, null, null, null);
        }

        static ManagedBillDto from(Bill b, Function<String, String> serviceNameLookup,
                                    Function<String, String> clientNameLookup,
                                    Function<String, String> clientCodeLookup) {
            return from(b, serviceNameLookup, clientNameLookup, clientCodeLookup, null);
        }

        static ManagedBillDto from(Bill b, Function<String, String> serviceNameLookup,
                                    Function<String, String> clientNameLookup,
                                    Function<String, String> clientCodeLookup,
                                    Function<String, String> branchNameLookup) {
            return new ManagedBillDto(
                    b.getId().toString(), b.getClientId().toString(),
                    clientNameLookup != null ? clientNameLookup.apply(b.getClientId()) : null,
                    clientCodeLookup != null ? clientCodeLookup.apply(b.getClientId()) : null,
                    b.getBillServiceCode(), serviceNameLookup.apply(b.getBillServiceCode()),
                    b.getBillerName() != null ? b.getBillerName() : serviceNameLookup.apply(b.getBillServiceCode()),
                    b.getBillerId(), b.getCustomerBillAccountNumber(), b.getCustomerName(), b.getCustomerMobileNumber(),
                    b.getAmount(), parseAmountBreakdown(b.getAmountBreakdown()),
                    b.getBillNumber(), b.getBillDate(), b.getDueDate(), b.getBillPeriod(),
                    b.getPaymentStatus().name(), b.getBillStatus().name(),
                    b.getLastFetchAt(), b.isLastFetchFailed(), b.getLastFetchFailureReason(),
                    b.isAutoFetchEnabled(), b.isAutoPayEnabled(),
                    b.getCreatedAt(), b.getUpdatedAt(),
                    b.getBranchId(),
                    branchNameLookup == null || b.getBranchId() == null ? null : branchNameLookup.apply(b.getBranchId()));
        }
    }

    /** One fetch/payment cycle - what My Payments (client and organisation side) lists. */
    record BillPaymentDto(
            String id, String billId, String clientId, String billServiceCode, String billServiceName, String billerName, String billerId,
            String customerBillAccountNumber, String customerName, String customerMobileNumber,
            BigDecimal amount, List<AmountComponentDto> amountBreakdown,
            String billNumber, String billDate, String dueDate, String billPeriod,
            String orgStatus, String paymentStatus, String billStatus,
            Instant clientPaidAt, String clientPaidBy,
            Instant orgPickedUpAt, String orgPickedUpBy,
            Instant orgPaidAt, String orgPaidBy,
            String bbpsTransactionId, String externalTransactionId, String billType, String billPayFailureReason,
            String assignedVendorClientId, String assignedVendorName,
            BigDecimal commissionPercent, BigDecimal commissionAmount,
            Instant vendorMarkedPaidAt, String vendorPaymentReference,
            String vendorStage, Instant bbpsVerifiedAt, String bbpsCheckNote, Instant reimbursedAt,
            Instant createdAt, Instant updatedAt,
            boolean awaitingChecker, boolean canSettle,
            String branchId, String branchName, boolean hasPaymentProof, String corporateName) {

        static BillPaymentDto from(BillPayment p, Bill bill, Function<String, String> serviceNameLookup) {
            return from(p, bill, serviceNameLookup, false, false, null);
        }

        static BillPaymentDto from(BillPayment p, Bill bill, Function<String, String> serviceNameLookup,
                                   boolean awaitingChecker, boolean canSettle) {
            return from(p, bill, serviceNameLookup, awaitingChecker, canSettle, null);
        }

        static BillPaymentDto from(BillPayment p, Bill bill, Function<String, String> serviceNameLookup,
                                   boolean awaitingChecker, boolean canSettle,
                                   Function<String, String> vendorNameLookup) {
            return from(p, bill, serviceNameLookup, awaitingChecker, canSettle, vendorNameLookup, null, null);
        }

        /** The same row with the date Paytrix paid the vendor for this bill. */
        BillPaymentDto withReimbursedAt(Instant paidToVendorAt) {
            return new BillPaymentDto(
                    id, billId, clientId, billServiceCode, billServiceName, billerName, billerId,
                    customerBillAccountNumber, customerName, customerMobileNumber, amount, amountBreakdown,
                    billNumber, billDate, dueDate, billPeriod, orgStatus, paymentStatus, billStatus,
                    clientPaidAt, clientPaidBy, orgPickedUpAt, orgPickedUpBy, orgPaidAt, orgPaidBy,
                    bbpsTransactionId, externalTransactionId, billType, billPayFailureReason,
                    assignedVendorClientId, assignedVendorName, commissionPercent, commissionAmount,
                    vendorMarkedPaidAt, vendorPaymentReference, vendorStage, bbpsVerifiedAt, bbpsCheckNote,
                    paidToVendorAt, createdAt, updatedAt, awaitingChecker, canSettle, branchId, branchName,
                    hasPaymentProof, corporateName);
        }

        /** A due fetch the corporate has not paid yet. The id is the bill fetch id. */
        static BillPaymentDto fromDue(BillFetch f, Bill bill, Function<String, String> serviceNameLookup,
                                      Function<String, String> branchNameLookup) {
            return new BillPaymentDto(
                    f.getBillFetchId().toString(), bill.getId(), bill.getClientId(), bill.getBillServiceCode(),
                    serviceNameLookup.apply(bill.getBillServiceCode()),
                    bill.getBillerName() != null ? bill.getBillerName() : serviceNameLookup.apply(bill.getBillServiceCode()),
                    bill.getBillerId(), bill.getCustomerBillAccountNumber(), f.getBillConsumerName(), null,
                    f.getBillAmount(), List.of(),
                    null,
                    f.getBillGeneratedDate() == null ? null : f.getBillGeneratedDate().toString(),
                    f.getBillDueDate() == null ? null : f.getBillDueDate().toString(),
                    f.getBillCycle(),
                    "NA", "PENDING", f.getBillStatus().name(),
                    null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null,
                    null, null,
                    null, null, null, null,
                    f.getFetchedOn(), f.getFetchedOn(),
                    false, false,
                    bill.getBranchId(),
                    branchNameLookup == null || bill.getBranchId() == null ? null : branchNameLookup.apply(bill.getBranchId()),
                    false, null);
        }

        static BillPaymentDto from(BillPayment p, Bill bill, Function<String, String> serviceNameLookup,
                                   boolean awaitingChecker, boolean canSettle,
                                   Function<String, String> vendorNameLookup,
                                   BigDecimal commissionPercent, BigDecimal commissionAmount) {
            return from(p, bill, serviceNameLookup, awaitingChecker, canSettle, vendorNameLookup,
                    commissionPercent, commissionAmount, null);
        }

        static BillPaymentDto from(BillPayment p, Bill bill, Function<String, String> serviceNameLookup,
                                   boolean awaitingChecker, boolean canSettle,
                                   Function<String, String> vendorNameLookup,
                                   BigDecimal commissionPercent, BigDecimal commissionAmount,
                                   Function<String, String> branchNameLookup) {
            String vendorName = null;
            if (p.isWithVendor()) {
                vendorName = vendorNameLookup != null ? vendorNameLookup.apply(p.getBillAssignedToId()) : p.getBillAssignedToName();
            }
            return new BillPaymentDto(
                    p.getId().toString(), p.getBillId(), p.getCorpId(), bill.getBillServiceCode(),
                    serviceNameLookup.apply(bill.getBillServiceCode()),
                    bill.getBillerName() != null ? bill.getBillerName() : serviceNameLookup.apply(bill.getBillServiceCode()),
                    bill.getBillerId(), bill.getCustomerBillAccountNumber(),
                    p.getBillFetch().getBillConsumerName(), null,
                    p.getBillAmount(), List.of(),
                    null, p.getBillDate(), p.getDueDate(), p.getBillPeriod(),
                    p.getOrgStatus().name(), p.getPaymentStatus().name(), p.getBillStatus().name(),
                    p.getCorpPaymentOn(), p.getCorpPaidUserId(), null, null,
                    p.getOrgPaidAt(), null, p.getBbpsTransactionId(), p.getExternalTransactionId(),
                    null, null,
                    p.getAssignedVendorClientId(),
                    vendorName, commissionPercent, commissionAmount,
                    p.getVendorMarkedPaidAt(), p.getVendorPaymentReference(),
                    p.getVendorStage() == null ? null : p.getVendorStage().name(),
                    p.getVerifiedOn(), null, null,
                    p.getCreatedAt(), p.getUpdatedAt(),
                    awaitingChecker, canSettle,
                    bill.getBranchId(),
                    branchNameLookup == null || bill.getBranchId() == null ? null : branchNameLookup.apply(bill.getBranchId()),
                    p.getPaymentProof() != null && !p.getPaymentProof().isBlank(),
                    p.getCorpName());
        }
    }
}
