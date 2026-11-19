package com.b2bfintech.app.controller;
import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.entity.VendorPayment;
import com.b2bfintech.billing.repository.BillPaymentLogRepository;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.billing.repository.VendorPaymentRepository;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Branch;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.repository.BranchRepository;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.app.service.CategoryLabels;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.entity.WalletTransactionRequest;
import com.b2bfintech.wallet.entity.PlatformTransaction;
import com.b2bfintech.wallet.repository.LedgerEntryRepository;
import com.b2bfintech.wallet.repository.PlatformTransactionRepository;
import com.b2bfintech.wallet.repository.WalletAccountRepository;
import com.b2bfintech.wallet.repository.WalletTransactionRequestRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only report queries shared by the client reports and the monthly-statement job. */
@Service
public class ReportQueryService {

    private final BillPaymentRepository billPaymentRepository;
    private final BillPaymentLogRepository billPaymentLogRepository;
    private final VendorPaymentRepository vendorPaymentRepository;
    private final BillRepository billRepository;
    private final CategoryLabels categoryLabels;
    private final ClientRepository clientRepository;
    private final BranchRepository branchRepository;
    private final AppUserRepository appUserRepository;
    private final WalletAccountRepository walletAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletTransactionRequestRepository walletTopupRequestRepository;
    private final PlatformTransactionRepository platformTransactionRepository;
    private final com.b2bfintech.billing.repository.BillFetchRepository billFetchRepository;

    public ReportQueryService(
            com.b2bfintech.billing.repository.BillFetchRepository billFetchRepository,
            BillPaymentRepository billPaymentRepository,
            BillPaymentLogRepository billPaymentLogRepository,
            VendorPaymentRepository vendorPaymentRepository,
            BillRepository billRepository,
            CategoryLabels categoryLabels,
            ClientRepository clientRepository,
            BranchRepository branchRepository,
            AppUserRepository appUserRepository,
            WalletAccountRepository walletAccountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            WalletTransactionRequestRepository walletTopupRequestRepository,
            PlatformTransactionRepository platformTransactionRepository) {
        this.billFetchRepository = billFetchRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.billPaymentLogRepository = billPaymentLogRepository;
        this.vendorPaymentRepository = vendorPaymentRepository;
        this.billRepository = billRepository;
        this.categoryLabels = categoryLabels;
        this.clientRepository = clientRepository;
        this.branchRepository = branchRepository;
        this.appUserRepository = appUserRepository;
        this.walletAccountRepository = walletAccountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.walletTopupRequestRepository = walletTopupRequestRepository;
        this.platformTransactionRepository = platformTransactionRepository;
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

    /**
     * Every branch of the corporate, and each bill that branch paid from the wallet in the window.
     * A branch with no payments still appears once. The branch total is repeated on each of its bills.
     */
    public List<ReportDtos.BranchBillsRowDto> branchBills(String clientId, Instant from, Instant to) {
        Map<String, String> branchNames = new LinkedHashMap<>();
        for (Branch branch : branchRepository.findBySourceIdOrderByNameAsc(clientId)) {
            branchNames.put(branch.getId(), branch.getName());
        }
        List<BillPayment> paid = billPaymentRepository
                .findByClientIdAndClientPaidAtBetweenOrderByClientPaidAtDesc(clientId, from, to);
        Map<String, Bill> bills = loadBills(paid);
        Map<String, List<BillPayment>> byBranch = new LinkedHashMap<>();
        for (String branchId : branchNames.keySet()) {
            byBranch.put(branchId, new ArrayList<>());
        }
        for (BillPayment payment : paid) {
            Bill bill = bills.get(payment.getBillId());
            if (bill == null || bill.getBranchId() == null || bill.getBranchId().isBlank()) continue;
            byBranch.computeIfAbsent(bill.getBranchId(), id -> new ArrayList<>()).add(payment);
        }
        List<ReportDtos.BranchBillsRowDto> rows = new ArrayList<>();
        for (Map.Entry<String, List<BillPayment>> entry : byBranch.entrySet()) {
            String branchId = entry.getKey();
            String branchName = branchNames.getOrDefault(branchId, branchId);
            List<BillPayment> items = entry.getValue();
            BigDecimal total = items.stream().map(ReportQueryService::money).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (items.isEmpty()) {
                rows.add(new ReportDtos.BranchBillsRowDto(branchId, branchName, null, null, 0, BigDecimal.ZERO));
                continue;
            }
            items.sort(Comparator.comparing(BillPayment::getCorpPaymentOn, Comparator.nullsLast(Comparator.reverseOrder())));
            for (BillPayment payment : items) {
                rows.add(new ReportDtos.BranchBillsRowDto(
                        branchId, branchName, payment.getBillId(), money(payment), items.size(), total));
            }
        }
        return rows;
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

    /** Every corporate, with bills raised and bills paid to the biller in the window. */
    public List<ReportDtos.CorporateBusinessRowDto> corporateBusiness(Instant from, Instant to) {
        Map<String, BusinessAcc> rows = new LinkedHashMap<>();
        for (Client client : clientRepository.findAll()) {
            if (client.getClientType() != Client.ClientType.CORPORATE) continue;
            rows.put(client.getId(), new BusinessAcc(client.getClientName()));
        }
        for (BillPayment payment : billPaymentRepository.findRaisedBetween(from, to)) {
            BusinessAcc row = rows.computeIfAbsent(payment.getCorpId(), id -> new BusinessAcc(payment.getCorpName()));
            row.count += 1;
            row.amount = row.amount.add(money(payment));
        }
        for (BillPayment payment : billPaymentRepository.findSettledBetween(
                BillPayment.AssignedTo.ORG, BillPayment.AssignedTo.VENDOR, from, to)) {
            BusinessAcc row = rows.computeIfAbsent(payment.getCorpId(), id -> new BusinessAcc(payment.getCorpName()));
            row.paidCount += 1;
            row.paidAmount = row.paidAmount.add(money(payment));
        }
        return rows.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().name, String.CASE_INSENSITIVE_ORDER))
                .map(e -> new ReportDtos.CorporateBusinessRowDto(
                        e.getKey(), e.getValue().name,
                        e.getValue().count, e.getValue().amount,
                        e.getValue().paidCount, e.getValue().paidAmount))
                .toList();
    }

    /** Every vendor, with bills assigned and bills that vendor paid in the window. */
    public List<ReportDtos.VendorBusinessRowDto> vendorBusiness(Instant from, Instant to) {
        Map<String, BusinessAcc> rows = new LinkedHashMap<>();
        for (Client client : clientRepository.findAll()) {
            if (client.getClientType() != Client.ClientType.VENDOR) continue;
            rows.put(client.getId(), new BusinessAcc(client.getClientName()));
        }
        for (BillPayment payment : billPaymentRepository.findAssignedToVendorsBetween(
                BillPayment.AssignedTo.VENDOR, from, to)) {
            BusinessAcc row = rows.computeIfAbsent(
                    payment.getBillAssignedToId(), id -> new BusinessAcc(payment.getBillAssignedToName()));
            row.count += 1;
            row.amount = row.amount.add(money(payment));
        }
        for (BillPayment payment : billPaymentRepository.findVendorPaidBetween(
                BillPayment.AssignedTo.VENDOR, from, to)) {
            BusinessAcc row = rows.computeIfAbsent(
                    payment.getBillAssignedToId(), id -> new BusinessAcc(payment.getBillAssignedToName()));
            row.paidCount += 1;
            row.paidAmount = row.paidAmount.add(money(payment));
        }
        return rows.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().name, String.CASE_INSENSITIVE_ORDER))
                .map(e -> new ReportDtos.VendorBusinessRowDto(
                        e.getKey(), e.getValue().name,
                        e.getValue().count, e.getValue().amount,
                        e.getValue().paidCount, e.getValue().paidAmount))
                .toList();
    }

    /** Every sales person, their assigned companies, and the bills those companies paid in the window. */
    public List<ReportDtos.SalesBusinessRowDto> salesBusiness(Instant from, Instant to) {
        Map<String, BusinessAcc> paidByCorp = new LinkedHashMap<>();
        for (BillPayment payment : billPaymentRepository.findSettledBetween(
                BillPayment.AssignedTo.ORG, BillPayment.AssignedTo.VENDOR, from, to)) {
            BusinessAcc tally = paidByCorp.computeIfAbsent(payment.getCorpId(), id -> new BusinessAcc(payment.getCorpName()));
            tally.paidCount += 1;
            tally.paidAmount = tally.paidAmount.add(money(payment));
        }
        Map<String, List<Client>> corpsBySales = new LinkedHashMap<>();
        for (Client client : clientRepository.findAll()) {
            if (client.getClientType() != Client.ClientType.CORPORATE) continue;
            String salesUserId = client.getSalesUserId();
            if (salesUserId == null || salesUserId.isBlank()) continue;
            corpsBySales.computeIfAbsent(salesUserId, id -> new ArrayList<>()).add(client);
        }
        List<AppUser> salesPeople = appUserRepository.findByUserType(AppUser.UserType.ORG_SALES).stream()
                .sorted(Comparator.comparing(AppUser::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<ReportDtos.SalesBusinessRowDto> rows = new ArrayList<>();
        for (AppUser sales : salesPeople) {
            List<Client> corps = new ArrayList<>(corpsBySales.getOrDefault(sales.getUserId(), List.of()));
            corps.sort(Comparator.comparing(Client::getClientName, String.CASE_INSENSITIVE_ORDER));
            long salesBills = 0;
            BigDecimal salesAmount = BigDecimal.ZERO;
            List<ReportDtos.SalesBusinessRowDto> personRows = new ArrayList<>();
            for (Client corp : corps) {
                BusinessAcc paid = paidByCorp.get(corp.getId());
                long count = paid == null ? 0 : paid.paidCount;
                BigDecimal amount = paid == null ? BigDecimal.ZERO : paid.paidAmount;
                salesBills += count;
                salesAmount = salesAmount.add(amount);
                personRows.add(new ReportDtos.SalesBusinessRowDto(
                        sales.getUserId(), sales.getName(),
                        corp.getId(), corp.getClientName(),
                        count, amount, 0, BigDecimal.ZERO));
            }
            if (personRows.isEmpty()) {
                rows.add(new ReportDtos.SalesBusinessRowDto(
                        sales.getUserId(), sales.getName(), null, null, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO));
                continue;
            }
            for (ReportDtos.SalesBusinessRowDto row : personRows) {
                rows.add(new ReportDtos.SalesBusinessRowDto(
                        row.salesUserId(), row.salesPersonName(),
                        row.corpId(), row.corpName(),
                        row.billsPaid(), row.paidAmount(),
                        salesBills, salesAmount));
            }
        }
        return rows;
    }

    /** Bills a corporate raised that a vendor paid and Paytrix verified, in the vendor-paid window. */
    public List<ReportDtos.VerifiedVendorPaymentRowDto> verifiedVendorPayments(Instant from, Instant to) {
        return cap(billPaymentRepository.findVerifiedVendorPaidByPaidOn(
                BillPayment.AssignedTo.VENDOR, from, to)).stream()
                .map(p -> new ReportDtos.VerifiedVendorPaymentRowDto(
                        p.getCorpId(),
                        p.getCorpName(),
                        p.getBillAssignedToId(),
                        p.getBillAssignedToName(),
                        p.getBillId(),
                        p.getBillAmount(),
                        p.getBillPaidOn()))
                .toList();
    }

    public List<ReportDtos.FundingRowDto> clientTopUps(String clientId, Instant from, Instant to) {
        Map<String, Client> clients = clientsById();
        return cap(walletTopupRequestRepository.findByCorpIdAndCreatedDateBetweenOrderByCreatedDateDesc(clientId, from, to))
                .stream()
                .map(r -> toFundingRow(r, clients.get(r.getCorpId())))
                .toList();
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

    /**
     * Commission marked received in the window, dated by when it was marked received, plus the
     * commission each vendor still owes.
     */
    public List<ReportDtos.VendorCommissionRowDto> vendorCommission(Instant from, Instant to) {
        Map<String, CommissionAcc> rows = new LinkedHashMap<>();
        for (Client client : clientRepository.findAll()) {
            if (client.getClientType() != Client.ClientType.VENDOR) continue;
            rows.put(client.getId(), new CommissionAcc(client.getClientName()));
        }
        List<VendorPayment> payments = vendorPaymentRepository.findByPaymentStatus(VendorPayment.PaymentStatus.PAID);
        Map<UUID, BillPayment> bills = billPaymentRepository.findAllById(
                payments.stream().map(VendorPayment::getBillPaymentId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(BillPayment::getId, Function.identity()));
        Set<UUID> receivedInPeriod = billPaymentLogRepository
                .findByEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        BillPaymentLog.EventType.COMMISSION_CLOSED, from, to)
                .stream()
                .map(BillPaymentLog::getBillPaymentId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        for (VendorPayment payment : payments) {
            if (payment.getCommissionPercent() == null) continue;
            BillPayment bill = bills.get(payment.getBillPaymentId());
            if (bill == null || !bill.isWithVendor() || bill.getBillAssignedToId() == null) continue;
            CommissionAcc row = rows.computeIfAbsent(
                    bill.getBillAssignedToId(), id -> new CommissionAcc(bill.getBillAssignedToName()));
            if (row.name == null || row.name.isBlank()) {
                row.name = bill.getBillAssignedToName();
            }
            if (payment.isCommissionReceived()) {
                if (receivedInPeriod.contains(payment.getBillPaymentId())) {
                    row.earned = row.earned.add(payment.commissionAmount());
                }
            } else {
                row.pending = row.pending.add(payment.commissionAmount());
            }
        }
        return rows.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().name, String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new ReportDtos.VendorCommissionRowDto(
                        entry.getKey(), entry.getValue().name, entry.getValue().earned, entry.getValue().pending))
                .toList();
    }

    /** Bills this vendor paid in the window, grouped by category. */
    public List<ReportDtos.VendorCategoryPaidRowDto> vendorBillsByCategory(String vendorId, Instant from, Instant to) {
        List<BillPayment> paid = paidByVendor(vendorId, from, to);
        Map<String, Bill> bills = loadBills(paid);
        Map<String, SpendAcc> byCategory = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (BillPayment payment : paid) {
            String category = categoryName(bills.get(payment.getBillId()));
            SpendAcc acc = byCategory.computeIfAbsent(category, SpendAcc::new);
            acc.count++;
            acc.amount = acc.amount.add(money(payment));
        }
        return byCategory.values().stream()
                .map(acc -> new ReportDtos.VendorCategoryPaidRowDto(acc.name, acc.count, acc.amount))
                .toList();
    }

    /** Bills this vendor paid in the window that Paytrix has not paid back. */
    public List<ReportDtos.VendorAwaitingRowDto> vendorAwaitingPlatform(String vendorId, Instant from, Instant to) {
        List<BillPayment> paid = paidByVendor(vendorId, from, to);
        Map<String, Bill> bills = loadBills(paid);
        Map<UUID, VendorPayment> settlements = settlementsFor(paid);
        List<ReportDtos.VendorAwaitingRowDto> rows = new ArrayList<>();
        for (BillPayment payment : paid) {
            VendorPayment settlement = settlements.get(payment.getId());
            if (settlement != null && settlement.getPaymentStatus() == VendorPayment.PaymentStatus.PAID) continue;
            rows.add(new ReportDtos.VendorAwaitingRowDto(
                    payment.getBillId(), categoryName(bills.get(payment.getBillId())),
                    payment.getCorpName(), money(payment), payment.getBillPaidOn()));
        }
        return rows;
    }

    /** Commission this vendor paid to Paytrix in the window, dated by when it was marked received. */
    public List<ReportDtos.VendorCommissionPaidRowDto> vendorCommissionPaid(String vendorId, Instant from, Instant to) {
        Map<UUID, Instant> closedAt = new java.util.HashMap<>();
        for (BillPaymentLog log : billPaymentLogRepository.findByEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                BillPaymentLog.EventType.COMMISSION_CLOSED, from, to)) {
            if (log.getBillPaymentId() == null) continue;
            closedAt.merge(log.getBillPaymentId(), log.getCreatedAt(), (left, right) -> left.isBefore(right) ? left : right);
        }
        if (closedAt.isEmpty()) return List.of();
        Map<UUID, VendorPayment> settlements = vendorPaymentRepository.findByBillPaymentIdIn(closedAt.keySet()).stream()
                .collect(Collectors.toMap(VendorPayment::getBillPaymentId, Function.identity(), (left, right) -> left));
        List<BillPayment> payments = billPaymentRepository.findAllById(closedAt.keySet()).stream()
                .filter(payment -> vendorId.equals(payment.getBillAssignedToId()) && payment.isWithVendor())
                .toList();
        Map<String, Bill> bills = loadBills(payments);
        List<ReportDtos.VendorCommissionPaidRowDto> rows = new ArrayList<>();
        for (BillPayment payment : payments) {
            VendorPayment settlement = settlements.get(payment.getId());
            if (settlement == null || !settlement.isCommissionReceived() || settlement.getCommissionPercent() == null) continue;
            rows.add(new ReportDtos.VendorCommissionPaidRowDto(
                    payment.getBillId(), categoryName(bills.get(payment.getBillId())), money(payment),
                    settlement.getCommissionPercent(), settlement.commissionAmount(), closedAt.get(payment.getId())));
        }
        rows.sort(Comparator.comparing(ReportDtos.VendorCommissionPaidRowDto::paidOn, Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    private List<BillPayment> paidByVendor(String vendorId, Instant from, Instant to) {
        return billPaymentRepository.findPaidByVendorBetween(BillPayment.AssignedTo.VENDOR, vendorId, from, to);
    }

    private Map<UUID, VendorPayment> settlementsFor(List<BillPayment> payments) {
        if (payments.isEmpty()) return Map.of();
        return vendorPaymentRepository.findByBillPaymentIdIn(payments.stream().map(BillPayment::getId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(VendorPayment::getBillPaymentId, Function.identity(), (left, right) -> left));
    }

    private static String categoryName(Bill bill) {
        if (bill == null || bill.getCategoryName() == null || bill.getCategoryName().isBlank()) return "Other";
        return bill.getCategoryName();
    }

    /** Groups Paytrix transactions by the What shown on the transactions list. */
    public List<ReportDtos.TransactionCategoryRowDto> transactionsByWhat(Instant from, Instant to) {
        Map<String, MoneyAcc> groups = new java.util.TreeMap<>();
        for (PlatformTransaction row : platformTransactionRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to)) {
            MoneyAcc acc = groups.computeIfAbsent(
                    PlatformTransactionLabels.what(row.getCategory(), row.getType()), MoneyAcc::new);
            BigDecimal amount = row.getAmount() == null ? BigDecimal.ZERO : row.getAmount();
            if (row.getType() == PlatformTransaction.Type.CREDIT) {
                acc.moneyIn = acc.moneyIn.add(amount);
            } else {
                acc.moneyOut = acc.moneyOut.add(amount);
            }
        }
        return groups.values().stream()
                .map(acc -> new ReportDtos.TransactionCategoryRowDto(acc.what, acc.moneyIn, acc.moneyOut))
                .toList();
    }

    private Map<String, Client> clientsById() {
        return clientRepository.findAll().stream().collect(Collectors.toMap(Client::getId, c -> c));
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

    private static BigDecimal money(BillPayment payment) {
        return payment.getBillAmount() == null ? BigDecimal.ZERO : payment.getBillAmount();
    }

    private static <T> List<T> cap(List<T> rows) {
        return rows.size() <= ReportDtos.MAX_ROWS ? rows : rows.subList(0, ReportDtos.MAX_ROWS);
    }

    private static final class BusinessAcc {
        final String name;
        long count;
        BigDecimal amount = BigDecimal.ZERO;
        long paidCount;
        BigDecimal paidAmount = BigDecimal.ZERO;

        BusinessAcc(String name) {
            this.name = name == null || name.isBlank() ? "Unknown" : name;
        }
    }

    private static final class CommissionAcc {
        String name;
        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;

        CommissionAcc(String name) {
            this.name = name == null || name.isBlank() ? "Unknown" : name;
        }
    }

    private static final class MoneyAcc {
        final String what;
        BigDecimal moneyIn = BigDecimal.ZERO;
        BigDecimal moneyOut = BigDecimal.ZERO;

        MoneyAcc(String what) {
            this.what = what;
        }
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
