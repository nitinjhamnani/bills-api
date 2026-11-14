package com.b2bfintech.app.controller;
import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.app.service.CategoryLabels;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.entity.WalletTransactionRequest;
import com.b2bfintech.wallet.repository.LedgerEntryRepository;
import com.b2bfintech.wallet.repository.WalletAccountRepository;
import com.b2bfintech.wallet.repository.WalletTransactionRequestRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only report queries shared by the client, organisation, and monthly-statement job. */
@Service
public class ReportQueryService {

    private static final Set<BillPayment.OrgStatus> SETTLEMENT_STATUSES = EnumSet.of(
            BillPayment.OrgStatus.PENDING, BillPayment.OrgStatus.IN_PROCESS, BillPayment.OrgStatus.PAID);
    private static final Set<BillPayment.OrgStatus> STUCK_STATUSES = EnumSet.of(
            BillPayment.OrgStatus.PENDING, BillPayment.OrgStatus.IN_PROCESS);

    private final BillPaymentRepository billPaymentRepository;
    private final BillRepository billRepository;
    private final CategoryLabels categoryLabels;
    private final ClientRepository clientRepository;
    private final WalletAccountRepository walletAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletTransactionRequestRepository walletTopupRequestRepository;
    private final com.b2bfintech.billing.repository.BillFetchRepository billFetchRepository;

    public ReportQueryService(
            com.b2bfintech.billing.repository.BillFetchRepository billFetchRepository,
            BillPaymentRepository billPaymentRepository,
            BillRepository billRepository,
            CategoryLabels categoryLabels,
            ClientRepository clientRepository,
            WalletAccountRepository walletAccountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            WalletTransactionRequestRepository walletTopupRequestRepository) {
        this.billFetchRepository = billFetchRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.billRepository = billRepository;
        this.categoryLabels = categoryLabels;
        this.clientRepository = clientRepository;
        this.walletAccountRepository = walletAccountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.walletTopupRequestRepository = walletTopupRequestRepository;
    }

    public ReportDtos.WalletStatementDto walletStatement(String clientId, Instant from, Instant to) {
        Client client = clientRepository.findById(clientId).orElse(null);
        if (client != null && client.getClientType() == Client.ClientType.VENDOR) {
            throw new com.b2bfintech.common.exception.BadRequestException("A vendor does not have a wallet");
        }
        WalletAccount account = walletAccountRepository
                .findByClientIdAndWalletType(clientId, WalletAccount.WalletType.WALLET)
                .orElse(null);
        if (account == null) {
            return new ReportDtos.WalletStatementDto(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
        BigDecimal opening = ledgerEntryRepository.balanceBefore(account.getId(), from);
        List<LedgerEntry> entries = ledgerEntryRepository
                .findByWalletAccountIdAndCreatedAtBetweenOrderByCreatedAtAsc(account.getId(), from, to);
        BigDecimal totalCredits = entries.stream()
                .filter(e -> e.getEntryType() == LedgerEntry.EntryType.CREDIT)
                .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDebits = entries.stream()
                .filter(e -> e.getEntryType() == LedgerEntry.EntryType.DEBIT)
                .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal closing = opening.add(totalCredits).subtract(totalDebits);
        List<BillDtos.LedgerEntryDto> dtos = cap(BillDtos.LedgerEntryDto.withBalances(entries, opening));
        return new ReportDtos.WalletStatementDto(opening, closing, totalCredits, totalDebits, dtos);
    }

    public List<BillDtos.BillPaymentDto> billPaymentsInRange(String clientId, Instant from, Instant to) {
        return toPaymentDtos(cap(
                billPaymentRepository.findByClientIdAndCreatedAtBetweenOrderByCreatedAtDesc(clientId, from, to)));
    }

    public List<BillDtos.BillPaymentDto> outstandingBills(String clientId) {
        Function<String, String> names = categoryLabels.lookup();
        return cap(billFetchRepository.findDues(clientId)).stream()
                .map(fetch -> billRepository.findById(fetch.getBillId())
                        .map(bill -> BillDtos.BillPaymentDto.fromDue(fetch, bill, names, null))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<BillDtos.BillPaymentDto> paymentHistory(String clientId, Instant from, Instant to) {
        return toPaymentDtos(cap(
                billPaymentRepository.findByClientIdAndClientPaidAtBetweenOrderByClientPaidAtDesc(clientId, from, to)));
    }

    public List<ReportDtos.SpendByServiceRowDto> spendByService(String clientId, Instant from, Instant to) {
        List<BillPayment> paid = billPaymentRepository
                .findByClientIdAndClientPaidAtBetweenOrderByClientPaidAtDesc(clientId, from, to);
        Map<String, Bill> bills = loadBills(paid);
        Function<String, String> names = categoryLabels.lookup();
        Map<String, SpendAcc> byService = new LinkedHashMap<>();
        for (BillPayment payment : paid) {
            Bill bill = bills.get(payment.getBillId());
            if (bill == null) continue;
            String code = bill.getBillServiceCode();
            SpendAcc acc = byService.computeIfAbsent(code, k -> new SpendAcc(names.apply(code)));
            acc.count++;
            acc.amount = acc.amount.add(payment.getAmount());
        }
        return byService.entrySet().stream()
                .map(e -> new ReportDtos.SpendByServiceRowDto(e.getKey(), e.getValue().name, e.getValue().count, e.getValue().amount))
                .toList();
    }

    public List<ReportDtos.FundingRowDto> clientTopUps(String clientId, Instant from, Instant to) {
        Map<String, Client> clients = clientsById();
        return cap(walletTopupRequestRepository.findByCorpIdAndCreatedDateBetweenOrderByCreatedDateDesc(clientId, from, to))
                .stream()
                .map(r -> toFundingRow(r, clients.get(r.getCorpId())))
                .toList();
    }

    public List<ReportDtos.ClientPortfolioRowDto> clientPortfolio() {
        Map<String, WalletAccount> wallets = walletAccountRepository.findByWalletType(WalletAccount.WalletType.WALLET)
                .stream().collect(Collectors.toMap(WalletAccount::getClientId, w -> w, (a, b) -> a));
        Map<String, Object[]> aggregates = new HashMap<>();
        for (Object[] row : billPaymentRepository.aggregatePortfolioByClient()) {
            aggregates.put((String) row[0], row);
        }
        return clientRepository.findAll().stream()
                .map(c -> {
                    Object[] agg = aggregates.get(c.getId());
                    long bills = agg == null ? 0L : ((Number) agg[1]).longValue();
                    BigDecimal settled = agg == null ? BigDecimal.ZERO : decimal(agg[2]);
                    BigDecimal outstanding = agg == null ? BigDecimal.ZERO : decimal(agg[3]);
                    BigDecimal pending = agg == null ? BigDecimal.ZERO : decimal(agg[4]);
                    WalletAccount wallet = c.getClientType() == Client.ClientType.VENDOR ? null : wallets.get(c.getId());
                    return new ReportDtos.ClientPortfolioRowDto(
                            c.getId().toString(), c.getClientName(), c.getClientCode(),
                            c.getClientType().name(), c.getStatus().name(), c.getCreatedAt(),
                            c.getClientType() == Client.ClientType.VENDOR ? null : (wallet != null ? wallet.getBalance() : BigDecimal.ZERO),
                            bills, settled, outstanding, pending);
                })
                .toList();
    }

    public List<BillDtos.BillPaymentDto> settlementPipeline(
            Instant from, Instant to, String clientId, String dateField, String orgStatus) {
        List<BillPayment> payments = switch (normalizeDateField(dateField)) {
            case "clientPaidAt" -> clientId != null
                    ? billPaymentRepository.findByClientIdAndClientPaidAtBetweenOrderByClientPaidAtDesc(clientId, from, to)
                    : billPaymentRepository.findByClientPaidAtBetweenOrderByClientPaidAtDesc(from, to);
            case "orgPaidAt" -> clientId != null
                    ? billPaymentRepository.findByClientIdAndOrgPaidAtBetweenOrderByOrgPaidAtDesc(clientId, from, to)
                    : billPaymentRepository.findByOrgPaidAtBetweenOrderByOrgPaidAtDesc(from, to);
            default -> clientId != null
                    ? billPaymentRepository.findByClientIdAndCreatedAtBetweenOrderByCreatedAtDesc(clientId, from, to)
                    : billPaymentRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
        };
        return toPaymentDtos(cap(filterOrgStatus(payments, orgStatus)));
    }

    public List<ReportDtos.AgingRowDto> agingQueue() {
        Map<String, Client> clients = clientsById();
        List<BillPayment> stuck = billPaymentRepository.findByOrgStatusInOrderByCreatedAtDesc(STUCK_STATUSES);
        Map<String, Bill> bills = loadBills(stuck);
        Instant now = Instant.now();
        List<ReportDtos.AgingRowDto> rows = new ArrayList<>();
        for (BillPayment payment : stuck) {
            Bill bill = bills.get(payment.getBillId());
            Instant stuckSince = payment.getClientPaidAt() != null ? payment.getClientPaidAt()
                    : payment.getUpdatedAt() != null ? payment.getUpdatedAt() : payment.getCreatedAt();
            if (stuckSince == null) stuckSince = now;
            long days = Math.max(0, Duration.between(stuckSince, now).toDays());
            Client client = clients.get(payment.getClientId());
            rows.add(new ReportDtos.AgingRowDto(
                    payment.getId().toString(),
                    payment.getBillId().toString(),
                    payment.getClientId().toString(),
                    client != null ? client.getClientName() : "Unknown client",
                    client != null ? client.getClientCode() : null,
                    bill != null && bill.getBillerName() != null ? bill.getBillerName() : "Unknown biller",
                    bill != null ? bill.getCustomerBillAccountNumber() : "",
                    payment.getAmount(),
                    payment.getOrgStatus().name(),
                    stuckSince,
                    days,
                    payment.getDueDate()));
        }
        rows.sort((a, b) -> Long.compare(b.daysStuck(), a.daysStuck()));
        return cap(rows);
    }

    public List<ReportDtos.ClientGmvRowDto> gmvByClient(Instant from, Instant to) {
        Map<String, Client> clients = clientsById();
        Map<String, long[]> paid = new HashMap<>();
        Map<String, BigDecimal> paidAmt = new HashMap<>();
        for (Object[] row : billPaymentRepository.sumClientPaidByClient(from, to)) {
            String id = (String) row[0];
            paid.put(id, new long[]{((Number) row[1]).longValue()});
            paidAmt.put(id, decimal(row[2]));
        }
        Map<String, long[]> settled = new HashMap<>();
        Map<String, BigDecimal> settledAmt = new HashMap<>();
        for (Object[] row : billPaymentRepository.sumOrgSettledByClient(from, to)) {
            String id = (String) row[0];
            settled.put(id, new long[]{((Number) row[1]).longValue()});
            settledAmt.put(id, decimal(row[2]));
        }
        return clients.values().stream()
                .map(c -> new ReportDtos.ClientGmvRowDto(
                        c.getId().toString(), c.getClientName(), c.getClientCode(),
                        paid.getOrDefault(c.getId(), new long[]{0})[0],
                        paidAmt.getOrDefault(c.getId(), BigDecimal.ZERO),
                        settled.getOrDefault(c.getId(), new long[]{0})[0],
                        settledAmt.getOrDefault(c.getId(), BigDecimal.ZERO)))
                .filter(r -> r.clientPaidCount() > 0 || r.orgSettledCount() > 0)
                .toList();
    }

    /** Fee debits are stored under the bill's category, so they cannot be told apart from bill debits. */
    public List<ReportDtos.RevenueRowDto> revenue(Instant from, Instant to, String clientId) {
        return List.of();
    }

    public List<ReportDtos.RevenueByClientRowDto> revenueByClient(Instant from, Instant to, String clientId) {
        Map<String, ReportDtos.RevenueByClientRowDto> grouped = new LinkedHashMap<>();
        for (ReportDtos.RevenueRowDto row : revenue(from, to, clientId)) {
            ReportDtos.RevenueByClientRowDto existing = grouped.get(row.clientId());
            if (existing == null) {
                grouped.put(row.clientId(), new ReportDtos.RevenueByClientRowDto(
                        row.clientId(), row.clientName(), row.clientCode(), 1, row.amount()));
            } else {
                grouped.put(row.clientId(), new ReportDtos.RevenueByClientRowDto(
                        existing.clientId(), existing.clientName(), existing.clientCode(),
                        existing.feeCount() + 1, existing.totalAmount().add(row.amount())));
            }
        }
        return List.copyOf(grouped.values());
    }

    public List<ReportDtos.FundingRowDto> funding(Instant from, Instant to, String clientId, String status) {
        Map<String, Client> clients = clientsById();
        return cap(walletTopupRequestRepository.findByCreatedDateBetweenOrderByCreatedDateDesc(from, to)
                .stream()
                .filter(r -> clientId == null || clientId.equals(r.getCorpId()))
                .filter(r -> status == null || status.isBlank() || r.getStatus().name().equalsIgnoreCase(status))
                .map(r -> toFundingRow(r, clients.get(r.getCorpId())))
                .toList());
    }

    /** Paytrix manual credits are stored as RECHARGE, like approved top-ups, so they cannot be listed apart. */
    public List<ReportDtos.AdjustmentRowDto> fundingAdjustments(Instant from, Instant to, String clientId) {
        return List.of();
    }

    private List<BillDtos.BillPaymentDto> toPaymentDtos(List<BillPayment> payments) {
        Function<String, String> names = categoryLabels.lookup();
        Map<String, Bill> bills = loadBills(payments);
        List<BillDtos.BillPaymentDto> dtos = new ArrayList<>();
        for (BillPayment payment : payments) {
            Bill bill = bills.get(payment.getBillId());
            if (bill == null) continue;
            dtos.add(BillDtos.BillPaymentDto.from(payment, bill, names));
        }
        return dtos;
    }

    private Map<String, Bill> loadBills(List<BillPayment> payments) {
        Set<String> ids = payments.stream().map(BillPayment::getBillId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return billRepository.findAllById(ids).stream().collect(Collectors.toMap(Bill::getId, b -> b));
    }

    private Map<String, Client> clientsById() {
        return clientRepository.findAll().stream().collect(Collectors.toMap(Client::getId, c -> c));
    }

    private static List<BillPayment> filterOrgStatus(List<BillPayment> payments, String orgStatus) {
        if (orgStatus == null || orgStatus.isBlank() || "SETTLEMENT".equalsIgnoreCase(orgStatus)) {
            return payments.stream().filter(p -> SETTLEMENT_STATUSES.contains(p.getOrgStatus())).toList();
        }
        if ("ALL".equalsIgnoreCase(orgStatus)) return payments;
        return payments.stream().filter(p -> p.getOrgStatus().name().equalsIgnoreCase(orgStatus)).toList();
    }

    private static String normalizeDateField(String dateField) {
        if (dateField == null) return "createdAt";
        return switch (dateField) {
            case "clientPaidAt", "orgPaidAt", "createdAt" -> dateField;
            default -> "createdAt";
        };
    }

    private static ReportDtos.FundingRowDto toFundingRow(WalletTransactionRequest r, Client c) {
        return new ReportDtos.FundingRowDto(
                r.getRequestId().toString(), r.getCorpId(),
                c != null ? c.getClientName() : "Unknown client",
                c != null ? c.getClientCode() : null,
                r.getAmount(), r.getMode().name(), r.getStatus().name(),
                r.getUtr(), r.getCreatedBy(), r.getApprovedBy(),
                r.getCreatedDate(), r.getLastUpdated());
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    private static <T> List<T> cap(List<T> rows) {
        return rows.size() <= ReportDtos.MAX_ROWS ? rows : rows.subList(0, ReportDtos.MAX_ROWS);
    }

    private static final class SpendAcc {
        final String name;
        long count;
        BigDecimal amount = BigDecimal.ZERO;

        SpendAcc(String name) {
            this.name = name;
        }
    }
}
