package com.b2bfintech.platform.controller;

import com.b2bfintech.platform.dto.OrganisationResponseDto;
import com.b2bfintech.platform.dto.CreateOrganisationRequestDto;
import com.b2bfintech.platform.model.BillerService;
import com.b2bfintech.platform.repository.BillerServiceCatalogRepository;
import com.b2bfintech.platform.repository.OrganisationRepository;
import com.b2bfintech.platform.repository.PlatformAuditReadRepository;
import com.b2bfintech.platform.repository.BbpsCategoryRepository;
import com.b2bfintech.platform.service.BbpsCatalogSyncService;
import com.b2bfintech.platform.service.OrganisationProvisioningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Platform-admin-only: tenant (organisation) onboarding and the shared bill-service catalog. */
@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformController {

    private final OrganisationProvisioningService provisioningService;
    private final OrganisationRepository organisationRepository;
    private final BillerServiceCatalogRepository billerServiceCatalogRepository;
    private final PlatformAuditReadRepository platformAuditReadRepository;
    private final BbpsCategoryRepository bbpsCategoryRepository;
    private final BbpsCatalogSyncService bbpsCatalogSyncService;

    public PlatformController(
            OrganisationProvisioningService provisioningService,
            OrganisationRepository organisationRepository,
            BillerServiceCatalogRepository billerServiceCatalogRepository,
            PlatformAuditReadRepository platformAuditReadRepository,
            BbpsCategoryRepository bbpsCategoryRepository,
            BbpsCatalogSyncService bbpsCatalogSyncService) {
        this.provisioningService = provisioningService;
        this.organisationRepository = organisationRepository;
        this.billerServiceCatalogRepository = billerServiceCatalogRepository;
        this.platformAuditReadRepository = platformAuditReadRepository;
        this.bbpsCategoryRepository = bbpsCategoryRepository;
        this.bbpsCatalogSyncService = bbpsCatalogSyncService;
    }

    @PostMapping("/organisations")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganisationResponseDto createOrganisation(@Valid @RequestBody CreateOrganisationRequestDto request) {
        var organisation = provisioningService.provision(
                request.organisationCode(), request.organisationName(), request.adminPhoneNumber(), request.adminName());
        return OrganisationResponseDto.from(organisation);
    }

    @GetMapping("/organisations")
    public List<OrganisationResponseDto> listOrganisations() {
        return organisationRepository.findAll().stream().map(OrganisationResponseDto::from).toList();
    }

    @GetMapping("/biller-services")
    public List<BillerService> listBillerServices() {
        return billerServiceCatalogRepository.findAllActive();
    }

    @GetMapping("/bbps/categories")
    public List<BbpsCategoryView> listBbpsCategories() {
        return bbpsCategoryRepository.findAllActive().stream()
                .map(category -> new BbpsCategoryView(category.categoryId(), category.name()))
                .toList();
    }

    @PostMapping("/bbps/sync")
    public BbpsCatalogSyncService.SyncSummary syncBbpsCatalog() {
        return bbpsCatalogSyncService.sync();
    }

    public record BbpsCategoryView(String categoryId, String name) {
    }

    @GetMapping("/audit-log")
    public List<PlatformAuditReadRepository.Entry> auditLog() {
        return platformAuditReadRepository.findRecent();
    }
}
