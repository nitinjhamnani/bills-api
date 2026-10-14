package com.b2bfintech.app.controller;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.common.tenant.TenantContext;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.platform.repository.OrganisationRepository;
import com.b2bfintech.platform.repository.UserDirectoryRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only, cross-tenant reports for the platform operator. */
@RestController
@RequestMapping("/api/v1/platform/reports")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformReportsController {

    private final OrganisationRepository organisationRepository;
    private final UserDirectoryRepository userDirectoryRepository;
    private final ClientRepository clientRepository;
    private final BillPaymentRepository billPaymentRepository;

    public PlatformReportsController(
            OrganisationRepository organisationRepository, UserDirectoryRepository userDirectoryRepository,
            ClientRepository clientRepository, BillPaymentRepository billPaymentRepository) {
        this.organisationRepository = organisationRepository;
        this.userDirectoryRepository = userDirectoryRepository;
        this.clientRepository = clientRepository;
        this.billPaymentRepository = billPaymentRepository;
    }

    /** One row per organisation: status, headcount, and lifetime GMV. */
    @GetMapping("/organisations")
    public List<ReportDtos.OrganisationPortfolioRowDto> organisations() {
        return organisationRepository.findAll().stream()
                .map(org -> {
                    TenantContext.setCurrentTenant(org.tenantSchema());
                    try {
                        long clientCount = clientRepository.count();
                        long staffCount = userDirectoryRepository.findByTenantSchema(org.tenantSchema()).stream()
                                .filter(entry -> entry.clientId() == null)
                                .count();
                        return new ReportDtos.OrganisationPortfolioRowDto(
                                org.id().toString(), org.organisationCode(), org.organisationName(), org.status(),
                                org.createdAt(), clientCount, staffCount,
                                billPaymentRepository.sumAmountByOrgStatus(BillPayment.OrgStatus.PAID));
                    } finally {
                        TenantContext.clear();
                    }
                })
                .toList();
    }

    /** Bill value actually settled (billStatus PAID) in the window, per organisation. */
    @GetMapping("/gmv")
    public List<ReportDtos.OrganisationGmvRowDto> gmv(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return organisationRepository.findAll().stream()
                .map(org -> {
                    TenantContext.setCurrentTenant(org.tenantSchema());
                    try {
                        long billsPaid = billPaymentRepository.countByBillStatusAndCreatedAtBetween(
                                Bill.BillStatus.PAID, range.from(), range.to());
                        var totalAmount = billPaymentRepository.sumAmountByBillStatusAndCreatedAtBetween(
                                Bill.BillStatus.PAID, range.from(), range.to());
                        return new ReportDtos.OrganisationGmvRowDto(
                                org.id().toString(), org.organisationCode(), org.organisationName(), billsPaid, totalAmount);
                    } finally {
                        TenantContext.clear();
                    }
                })
                .toList();
    }
}
