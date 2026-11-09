package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.ClientOnboarding;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.organisation.service.OnboardingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only book for one sales person: business the corporates they brought in have already paid. */
@Service
public class SalesPortfolioService {

    private final ClientService clientService;
    private final OnboardingService onboardingService;
    private final BillPaymentRepository billPaymentRepository;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final BillRepository billRepository;

    public SalesPortfolioService(
            ClientService clientService,
            OnboardingService onboardingService,
            BillPaymentRepository billPaymentRepository,
            BillRepository billRepository) {
        this.clientService = clientService;
        this.onboardingService = onboardingService;
        this.billPaymentRepository = billPaymentRepository;
        this.billRepository = billRepository;
    }

    @Transactional(readOnly = true)
    public SalesDashboard dashboard(UUID salesUserId) {
        Instant monthStart = LocalDate.now(IST).withDayOfMonth(1).atStartOfDay(IST).toInstant();
        Instant nextMonth = LocalDate.now(IST).withDayOfMonth(1).plusMonths(1).atStartOfDay(IST).toInstant();
        List<CorporateSummary> corporates = ownedCorporates(salesUserId).stream()
                .map(client -> summarize(client, monthStart, nextMonth))
                .toList();
        BigDecimal businessAmount = corporates.stream()
                .map(CorporateSummary::businessAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long businessCount = corporates.stream().mapToLong(CorporateSummary::businessCount).sum();
        BigDecimal monthAmount = corporates.stream()
                .map(CorporateSummary::monthBusinessAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long monthCount = corporates.stream().mapToLong(CorporateSummary::monthBusinessCount).sum();
        long inProgress = corporates.stream()
                .mapToLong(row -> row.withVendorCount() + row.waitingForCheckCount() + row.waitingToPayVendorCount())
                .sum();
        return new SalesDashboard(
                money(businessAmount), businessCount, money(monthAmount), monthCount,
                corporates.size(), inProgress, corporates);
    }

    /**
     * Bills the assigned corporates have paid, using the date they paid.
     * A missing client list means every corporate assigned to this person. Any other id is ignored.
     */
    @Transactional(readOnly = true)
    public SalesPaidReport paidInRange(UUID salesUserId, Instant from, Instant to, List<UUID> clientIds) {
        List<Client> corporates = ownedCorporates(salesUserId);
        if (clientIds != null && !clientIds.isEmpty()) {
            Set<UUID> requested = new HashSet<>(clientIds);
            corporates = corporates.stream().filter(client -> requested.contains(client.getId())).toList();
        }
        List<BillPayment> matched = new ArrayList<>();
        Map<UUID, Client> owners = corporates.stream().collect(Collectors.toMap(Client::getId, Function.identity()));
        for (Client client : corporates) {
            for (BillPayment payment : billPaymentRepository.findByClientIdOrderByCreatedAtDesc(client.getId())) {
                if (paidIn(payment, from, to)) {
                    matched.add(payment);
                }
            }
        }
        matched.sort(Comparator.comparing(BillPayment::getClientPaidAt, Comparator.nullsLast(Comparator.reverseOrder())));
        Map<UUID, Bill> bills = billsById(matched);
        Map<UUID, Client> vendors = vendorsById(matched);
        List<SalesPaidBill> rows = matched.stream()
                .map(payment -> toPaidBill(
                        payment,
                        bills.get(payment.getBillId()),
                        owners.get(payment.getClientId()),
                        vendors.get(payment.getAssignedVendorClientId())))
                .toList();
        BigDecimal total = rows.stream().map(SalesPaidBill::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SalesPaidReport(money(total), rows.size(), rows);
    }

    @Transactional(readOnly = true)
    public CorporateSummary corporate(UUID salesUserId, UUID clientId) {
        Instant monthStart = LocalDate.now(IST).withDayOfMonth(1).atStartOfDay(IST).toInstant();
        Instant nextMonth = LocalDate.now(IST).withDayOfMonth(1).plusMonths(1).atStartOfDay(IST).toInstant();
        return summarize(requireOwned(salesUserId, clientId), monthStart, nextMonth);
    }

    @Transactional(readOnly = true)
    public List<SalesBill> bills(UUID salesUserId, UUID clientId) {
        Client client = requireOwned(salesUserId, clientId);
        List<BillPayment> payments = billPaymentRepository.findByClientIdOrderByCreatedAtDesc(client.getId());
        Map<UUID, Bill> bills = billsById(payments);
        return payments.stream().map(payment -> toBill(payment, bills.get(payment.getBillId()))).toList();
    }

    private Map<UUID, Bill> billsById(List<BillPayment> payments) {
        return billRepository.findAllById(payments.stream().map(BillPayment::getBillId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Bill::getId, Function.identity()));
    }

    private Map<UUID, Client> vendorsById(List<BillPayment> payments) {
        Set<UUID> ids = payments.stream()
                .map(BillPayment::getAssignedVendorClientId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        return clientService.findByIds(ids).stream().collect(Collectors.toMap(Client::getId, Function.identity()));
    }

    private List<Client> ownedCorporates(UUID salesUserId) {
        return clientService.listForSalesPerson(salesUserId).stream()
                .filter(client -> client.getClientType() == Client.ClientType.CORPORATE)
                .toList();
    }

    private Client requireOwned(UUID salesUserId, UUID clientId) {
        Client client = clientService.getById(clientId);
        if (client.getClientType() != Client.ClientType.CORPORATE || !salesUserId.equals(client.getSalesUserId())) {
            throw new ResourceNotFoundException("Client not found");
        }
        return client;
    }

    private CorporateSummary summarize(Client client, Instant monthStart, Instant nextMonth) {
        Tally tally = new Tally(monthStart, nextMonth);
        for (BillPayment payment : billPaymentRepository.findByClientIdOrderByCreatedAtDesc(client.getId())) {
            tally.add(payment);
        }
        return new CorporateSummary(
                client.getId().toString(),
                client.getClientName(),
                client.getClientCode(),
                client.getStatus().name(),
                onboardingStep(client),
                money(tally.businessAmount),
                tally.businessCount,
                money(tally.monthAmount),
                tally.monthCount,
                tally.unpaid,
                tally.withVendor,
                tally.waitingForCheck,
                tally.waitingToPay,
                tally.reimbursed);
    }

    private static SalesBill toBill(BillPayment payment, Bill bill) {
        String billerName = bill == null || bill.getBillerName() == null || bill.getBillerName().isBlank()
                ? "Biller"
                : bill.getBillerName();
        String consumerNumber = bill == null ? "" : bill.getCustomerBillAccountNumber();
        return new SalesBill(
                payment.getId().toString(),
                billerName,
                consumerNumber,
                money(payment.getAmount()),
                payment.getDueDate(),
                stageOf(payment));
    }

    /** Pending clients show the step they are on. Everyone else has finished onboarding. */
    private String onboardingStep(Client client) {
        if (client.getStatus() != Client.ClientStatus.PENDING) {
            return "Active";
        }
        ClientOnboarding onboarding = onboardingService.get(client.getId());
        if (onboarding.getDetailsSubmittedAt() == null) {
            return client.getCorporateKind() == Client.CorporateKind.INDIVIDUAL ? "Details pending" : "Company pending";
        }
        if (onboarding.getKycStatus() != ClientOnboarding.KycStatus.COMPLETE) {
            return "KYC pending";
        }
        return "Contract pending";
    }

    static String stageOf(BillPayment payment) {
        if (payment.getOrgStatus() == BillPayment.OrgStatus.NA) {
            return "Unpaid";
        }
        BillPayment.VendorStage stage = payment.getVendorStage();
        if (stage == BillPayment.VendorStage.ASSIGNED) {
            return "Assigned";
        }
        if (stage == BillPayment.VendorStage.IN_PROGRESS) {
            return "In progress";
        }
        if (stage == BillPayment.VendorStage.VENDOR_PAID) {
            return "Waiting for check";
        }
        if (stage == BillPayment.VendorStage.VERIFIED) {
            return "Ready to pay vendor";
        }
        if (stage == BillPayment.VendorStage.REIMBURSED || payment.getOrgStatus() == BillPayment.OrgStatus.PAID) {
            return "Reimbursed";
        }
        return "Waiting for check";
    }

    private static boolean paidIn(BillPayment payment, Instant from, Instant to) {
        Instant paidAt = payment.getClientPaidAt();
        return payment.getOrgStatus() != BillPayment.OrgStatus.NA
                && paidAt != null
                && !paidAt.isBefore(from)
                && paidAt.isBefore(to);
    }

    private static SalesPaidBill toPaidBill(BillPayment payment, Bill bill, Client client, Client vendor) {
        String billerName = bill == null || bill.getBillerName() == null || bill.getBillerName().isBlank()
                ? "Biller"
                : bill.getBillerName();
        String consumerNumber = bill == null ? "" : bill.getCustomerBillAccountNumber();
        return new SalesPaidBill(
                payment.getId().toString(),
                client == null ? "" : client.getClientName(),
                client == null ? null : client.getClientCode(),
                vendor == null ? null : vendor.getId().toString(),
                vendor == null ? null : vendor.getClientName(),
                vendor == null ? null : vendor.getClientCode(),
                billerName,
                consumerNumber,
                payment.getBillNumber(),
                money(payment.getAmount()),
                payment.getDueDate(),
                payment.getClientPaidAt(),
                stageOf(payment));
    }

    private static BigDecimal money(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }

    private static final class Tally {
        private final Instant monthStart;
        private final Instant nextMonth;
        private long unpaid;
        private long withVendor;
        private long waitingForCheck;
        private long waitingToPay;
        private long reimbursed;
        private long businessCount;
        private BigDecimal businessAmount = BigDecimal.ZERO;
        private long monthCount;
        private BigDecimal monthAmount = BigDecimal.ZERO;

        private Tally(Instant monthStart, Instant nextMonth) {
            this.monthStart = monthStart;
            this.nextMonth = nextMonth;
        }

        private void add(BillPayment payment) {
            switch (stageOf(payment)) {
                case "Unpaid" -> unpaid++;
                case "Assigned", "In progress" -> withVendor++;
                case "Waiting for check" -> waitingForCheck++;
                case "Ready to pay vendor" -> waitingToPay++;
                default -> reimbursed++;
            }
            if (payment.getOrgStatus() == BillPayment.OrgStatus.NA) {
                return;
            }
            BigDecimal amount = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
            businessCount++;
            businessAmount = businessAmount.add(amount);
            if (paidIn(payment, monthStart, nextMonth)) {
                monthCount++;
                monthAmount = monthAmount.add(amount);
            }
        }
    }

    public record SalesDashboard(
            BigDecimal businessAmount,
            long businessCount,
            BigDecimal monthBusinessAmount,
            long monthBusinessCount,
            int corporateCount,
            long inProgressCount,
            List<CorporateSummary> corporates) {
    }

    public record CorporateSummary(
            String clientId,
            String clientName,
            String clientCode,
            String accountStatus,
            String onboardingStep,
            BigDecimal businessAmount,
            long businessCount,
            BigDecimal monthBusinessAmount,
            long monthBusinessCount,
            long unpaidCount,
            long withVendorCount,
            long waitingForCheckCount,
            long waitingToPayVendorCount,
            long reimbursedCount) {
    }

    public record SalesPaidReport(BigDecimal totalAmount, int billCount, List<SalesPaidBill> bills) {
    }

    public record SalesPaidBill(
            String id,
            String clientName,
            String clientCode,
            String vendorId,
            String vendorName,
            String vendorCode,
            String billerName,
            String consumerNumber,
            String billNumber,
            BigDecimal amount,
            String dueDate,
            Instant paidAt,
            String stage) {
    }

    public record SalesBill(
            String id,
            String billerName,
            String consumerNumber,
            BigDecimal amount,
            String dueDate,
            String stage) {
    }
}
