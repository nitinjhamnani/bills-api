package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.BillActivityLogService;
import com.b2bfintech.app.service.BranchService;
import com.b2bfintech.app.service.ManagedBillService;
import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.common.exception.ApiException;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.ClientScopeGuard;
import com.b2bfintech.app.service.BbpsLookupService;
import com.b2bfintech.app.service.CategoryLabels;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.common.spi.bbps.BillParameterValidator;
import com.b2bfintech.organisation.entity.Branch;
import com.b2bfintech.organisation.entity.ClientCategoryConfig;
import com.b2bfintech.organisation.entity.OrganisationCategoryConfig;
import com.b2bfintech.organisation.repository.BranchRepository;
import com.b2bfintech.organisation.service.CategoryEnablementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Managed Bills: create single or bulk (a {@code bill} row is inserted immediately, then fetched
 * and resolved - see {@link ManagedBillService}), list, and per-bill detail/activity log/automation.
 */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/managed-bills")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','CORP_ADMIN','CORP_OPERATOR','BRANCH_USER')")
public class ManagedBillController {

    private final ManagedBillService managedBillService;
    private final com.b2bfintech.billing.repository.BillRepository billRepository;
    private final com.b2bfintech.billing.repository.BillPaymentRepository billPaymentRepository;
    private final BillActivityLogService activityLogService;
    private final CategoryLabels categoryLabels;
    private final CategoryEnablementService categoryEnablementService;
    private final BbpsLookupService bbpsLookupService;
    private final BranchRepository branchRepository;
    private final BranchService branchService;

    public ManagedBillController(
            ManagedBillService managedBillService, com.b2bfintech.billing.repository.BillRepository billRepository,
            com.b2bfintech.billing.repository.BillPaymentRepository billPaymentRepository,
            BillActivityLogService activityLogService, CategoryLabels categoryLabels,
            CategoryEnablementService categoryEnablementService, BbpsLookupService bbpsLookupService,
            BranchRepository branchRepository, BranchService branchService) {
        this.managedBillService = managedBillService;
        this.billRepository = billRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.activityLogService = activityLogService;
        this.categoryLabels = categoryLabels;
        this.categoryEnablementService = categoryEnablementService;
        this.bbpsLookupService = bbpsLookupService;
        this.branchRepository = branchRepository;
        this.branchService = branchService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillDtos.ManagedBillDto create(@PathVariable String clientId, @Valid @RequestBody CreateRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCorporateAccess(principal, clientId);
        categoryEnablementService.assertEnabled(clientId, request.categoryId());
        List<BbpsProvider.ProviderBillerParameter> declared = bbpsLookupService.parameters(request.categoryId(), request.billerId());
        BillParameterValidator.ValidatedParameters validated = BillParameterValidator.validate(request.parameters(), declared);
        BbpsProvider.ProviderBiller biller = bbpsLookupService.billers(request.categoryId()).stream()
                .filter(candidate -> candidate.billerId().equals(request.billerId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown biller for this category"));
        String primaryParamName = declared.get(0).name();
        Bill bill = managedBillService.createBill(
                clientId, resolveBranch(clientId, principal, request.branchId()),
                request.categoryId(), biller.billerId(), biller.name(),
                primaryParamName, validated.parameters().get(primaryParamName),
                validated.parameters(), request.mobileNumber(), principal.userId());
        return toDto(bill);
    }

    @GetMapping("/bulk/template")
    public ResponseEntity<byte[]> bulkTemplate(@PathVariable String clientId,
                                                @AuthenticationPrincipal AuthenticatedUser principal) throws IOException {
        ClientScopeGuard.assertCorporateAccess(principal, clientId);
        byte[] xlsx = ManagedBillBulkTemplate.workbook(templateOptions(clientId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"managed-bills-upload-template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    /** Categories Paytrix has switched on for this company, with each category's active service providers. */
    private List<ManagedBillBulkTemplate.CategoryProviders> templateOptions(String clientId) {
        Map<String, String> names = categoryLabels.names();
        Set<String> disabledForClient = categoryEnablementService.listClientRows(clientId).stream()
                .filter(row -> !row.isEnabled())
                .map(ClientCategoryConfig::getCategoryId)
                .collect(Collectors.toSet());
        return categoryEnablementService.listOrganisation().stream()
                .filter(OrganisationCategoryConfig::isEnabled)
                .map(OrganisationCategoryConfig::getCategoryId)
                .filter(categoryId -> !disabledForClient.contains(categoryId))
                .map(categoryId -> new ManagedBillBulkTemplate.CategoryProviders(
                        names.getOrDefault(categoryId, categoryId),
                        serviceProviders(categoryId)))
                .sorted(Comparator.comparing(ManagedBillBulkTemplate.CategoryProviders::category, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<String> serviceProviders(String categoryId) {
        return bbpsLookupService.billers(categoryId).stream()
                .map(BbpsProvider.ProviderBiller::name)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<BillDtos.BulkRowResult> createBulk(@PathVariable String clientId, @RequestParam MultipartFile file,
                                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCorporateAccess(principal, clientId);
        throw new BadRequestException(
                "Bulk upload cannot collect the fields each biller asks for. Create bills one at a time.");
    }

    @GetMapping
    public List<BillDtos.ManagedBillDto> list(@PathVariable String clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCorporateAccess(principal, clientId);
        Function<String, String> names = categoryLabels.lookup();
        Function<String, String> branches = branchNames();
        return billRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .filter(bill -> visibleTo(principal, bill))
                .map(b -> BillDtos.ManagedBillDto.from(b, names, null, null, branches)).toList();
    }

    @GetMapping("/{billId}")
    public BillDtos.ManagedBillDto get(@PathVariable String clientId, @PathVariable String billId,
                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCorporateAccess(principal, clientId);
        return toDto(ownedBill(clientId, billId, principal));
    }

    /** Every fetch/payment cycle ever opened for this managed bill, newest first - the "bill
     * trail" shown above the activity log on the details page. */
    @GetMapping("/{billId}/payments")
    public List<BillDtos.BillPaymentDto> payments(@PathVariable String clientId, @PathVariable String billId,
                                                   @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCorporateAccess(principal, clientId);
        Bill bill = ownedBill(clientId, billId, principal);
        Function<String, String> names = categoryLabels.lookup();
        return billPaymentRepository.findByBillIdOrderByCreatedAtDesc(billId).stream()
                .map(p -> BillDtos.BillPaymentDto.from(p, bill, names))
                .toList();
    }

    @GetMapping("/{billId}/activity-log")
    public List<BillDtos.ActivityLogDto> activityLog(@PathVariable String clientId, @PathVariable String billId,
                                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCorporateAccess(principal, clientId);
        ownedBill(clientId, billId, principal);
        return activityLogService.listForBill(billId).stream().map(BillDtos.ActivityLogDto::from).toList();
    }

    /** One specific fetch/payment cycle's detail - what a row in "Bills against this account" opens. */
    @GetMapping("/{billId}/payments/{paymentId}")
    public BillDtos.BillPaymentDto payment(@PathVariable String clientId, @PathVariable String billId, @PathVariable UUID paymentId,
                                            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCorporateAccess(principal, clientId);
        Bill bill = ownedBill(clientId, billId, principal);
        BillPayment payment = getOwnedPayment(bill, paymentId);
        return BillDtos.BillPaymentDto.from(payment, bill, categoryLabels.lookup(), false, false, null, null, null, branchNames());
    }

    /** Just this one cycle's activity - as opposed to {@link #activityLog}, which is the whole bill's history. */
    @GetMapping("/{billId}/payments/{paymentId}/activity-log")
    public List<BillDtos.ActivityLogDto> paymentActivityLog(@PathVariable String clientId, @PathVariable String billId, @PathVariable UUID paymentId,
                                                             @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCorporateAccess(principal, clientId);
        Bill bill = ownedBill(clientId, billId, principal);
        getOwnedPayment(bill, paymentId);
        return activityLogService.listForBillPayment(paymentId).stream().map(BillDtos.ActivityLogDto::from).toList();
    }

    private BillPayment getOwnedPayment(Bill bill, UUID paymentId) {
        BillPayment payment = billPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
        if (!payment.getBillId().equals(bill.getId())) {
            throw new ResourceNotFoundException("Bill payment not found");
        }
        return payment;
    }

    @PostMapping("/{billId}/retry-fetch")
    public BillDtos.ManagedBillDto retryFetch(@PathVariable String clientId, @PathVariable String billId,
                                               @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCorporateAccess(principal, clientId);
        ownedBill(clientId, billId, principal);
        return toDto(managedBillService.retryFetch(clientId, billId));
    }

    @PatchMapping("/{billId}/automation")
    public BillDtos.ManagedBillDto configureAutomation(@PathVariable String clientId, @PathVariable String billId,
                                                         @RequestBody AutomationRequest request,
                                                         @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCorporateAccess(principal, clientId);
        Bill current = ownedBill(clientId, billId, principal);
        boolean autoPayEnabled = request.autoPayEnabled();
        if ("CORP_OPERATOR".equals(principal.role()) || "BRANCH_USER".equals(principal.role())) {
            if (autoPayEnabled != current.isAutoPayEnabled()) {
                throw new AccessDeniedException("Client operators cannot change auto-pay");
            }
            autoPayEnabled = current.isAutoPayEnabled();
        }
        return toDto(managedBillService.configureAutomation(
                clientId, billId, request.autoFetchEnabled(), autoPayEnabled));
    }

    private BillDtos.ManagedBillDto toDto(Bill bill) {
        return BillDtos.ManagedBillDto.from(bill, categoryLabels.lookup(), null, null, branchNames());
    }

    private Bill ownedBill(String clientId, String billId, AuthenticatedUser principal) {
        Bill bill = managedBillService.getOwnedBill(clientId, billId);
        if (!visibleTo(principal, bill)) {
            throw new ResourceNotFoundException("Bill not found");
        }
        return bill;
    }

    private boolean visibleTo(AuthenticatedUser principal, Bill bill) {
        return branchService.branchUserCanSee(principal, bill.getBranchId());
    }

    private String resolveBranch(String clientId, AuthenticatedUser principal, String requested) {
        if ("BRANCH_USER".equals(principal.role())) {
            List<String> allowed = branchService.branchIdsFor(principal);
            if (requested != null && !requested.isBlank()) {
                return ownedBranch(clientId, requested, allowed);
            }
            if (allowed.size() == 1) {
                return ownedBranch(clientId, allowed.get(0), allowed);
            }
            if (allowed.isEmpty()) {
                throw new BadRequestException("You are not assigned to a branch");
            }
            throw new BadRequestException("Choose a branch");
        }
        if (requested == null || requested.isBlank()) {
            throw new BadRequestException("Choose a branch");
        }
        return ownedBranch(clientId, requested, null);
    }

    private String ownedBranch(String clientId, String requested, List<String> allowed) {
        String branchId = requested.trim();
        if (allowed != null && !allowed.contains(branchId)) {
            throw new BadRequestException("You do not have this branch");
        }
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new BadRequestException("Choose a branch"));
        if (!branch.ownedBy(clientId)) {
            throw new BadRequestException("Choose a branch");
        }
        return branch.getId();
    }

    private Function<String, String> branchNames() {
        return branchRepository.findAll().stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName, (left, right) -> left))::get;
    }

    public record CreateRequest(
            String branchId,
            @NotBlank String categoryId,
            @NotBlank String billerId,
            @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a 10-digit mobile number starting with 6, 7, 8, or 9")
            String mobileNumber,
            @NotNull Map<String, String> parameters) {
    }

    public record AutomationRequest(boolean autoFetchEnabled, boolean autoPayEnabled) {
    }
}
