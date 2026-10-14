package com.b2bfintech.app.controller;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.common.spi.BillingProvider;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.wallet.entity.LedgerEntry;
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

    static Function<java.util.UUID, String> clientNameLookup(ClientRepository clientRepository) {
        Map<java.util.UUID, String> names = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getId, Client::getClientName, (a, b) -> a));
        return id -> names.getOrDefault(id, "Unknown client");
    }

    static Function<java.util.UUID, String> clientCodeLookup(ClientRepository clientRepository) {
        Map<java.util.UUID, String> codes = clientRepository.findAll().stream()
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

    record BulkRowResult(int row, boolean success, String billId, String error) {
    }

    record ActivityLogDto(String eventType, String description, String actor, Instant createdAt) {
        static ActivityLogDto from(BillPaymentLog l) {
            return new ActivityLogDto(l.getEventType().name(), l.getDescription(), l.getActor(), l.getCreatedAt());
        }
    }

    record LedgerEntryDto(String walletType, String entryType, BigDecimal amount, BigDecimal balanceAfter,
                           String referenceType, String description, Instant createdAt) {
        static LedgerEntryDto from(LedgerEntry e) {
            return new LedgerEntryDto(
                    e.getWalletAccount().getWalletType().name(), e.getEntryType().name(), e.getAmount(),
                    e.getBalanceAfter(), e.getReferenceType().name(), e.getDescription(), e.getCreatedAt());
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
            Instant createdAt, Instant updatedAt) {

        static ManagedBillDto from(Bill b, Function<String, String> serviceNameLookup) {
            return from(b, serviceNameLookup, null, null);
        }

        static ManagedBillDto from(Bill b, Function<String, String> serviceNameLookup,
                                    Function<java.util.UUID, String> clientNameLookup,
                                    Function<java.util.UUID, String> clientCodeLookup) {
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
                    b.getCreatedAt(), b.getUpdatedAt());
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
            Instant createdAt, Instant updatedAt,
            boolean awaitingChecker, boolean canSettle) {

        static BillPaymentDto from(BillPayment p, Bill bill, Function<String, String> serviceNameLookup) {
            return from(p, bill, serviceNameLookup, false, false);
        }

        static BillPaymentDto from(BillPayment p, Bill bill, Function<String, String> serviceNameLookup,
                                   boolean awaitingChecker, boolean canSettle) {
            return new BillPaymentDto(
                    p.getId().toString(), p.getBillId().toString(), bill.getClientId().toString(), bill.getBillServiceCode(),
                    serviceNameLookup.apply(bill.getBillServiceCode()),
                    bill.getBillerName() != null ? bill.getBillerName() : serviceNameLookup.apply(bill.getBillServiceCode()),
                    bill.getBillerId(), bill.getCustomerBillAccountNumber(), bill.getCustomerName(), bill.getCustomerMobileNumber(),
                    p.getAmount(), parseAmountBreakdown(p.getAmountBreakdown()),
                    p.getBillNumber(), p.getBillDate(), p.getDueDate(), p.getBillPeriod(),
                    p.getOrgStatus().name(), p.getPaymentStatus().name(), p.getBillStatus().name(),
                    p.getClientPaidAt(), p.getClientPaidBy(), p.getOrgPickedUpAt(), p.getOrgPickedUpBy(),
                    p.getOrgPaidAt(), p.getOrgPaidBy(), p.getBbpsTransactionId(), p.getExternalTransactionId(),
                    p.getBillType(), p.getBillPayFailureReason(), p.getCreatedAt(), p.getUpdatedAt(),
                    awaitingChecker, canSettle);
        }
    }
}
