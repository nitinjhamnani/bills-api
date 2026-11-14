package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.BbpsLookupService;
import com.b2bfintech.app.service.CommissionBillGuard;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Commission;
import com.b2bfintech.organisation.entity.CommissionAmountRange;
import com.b2bfintech.organisation.entity.CommissionLog;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.service.CategoryEnablementService;
import com.b2bfintech.organisation.service.CommissionRangeService;
import com.b2bfintech.organisation.service.CommissionService;
import com.b2bfintech.platform.model.BbpsCategory;
import com.b2bfintech.platform.repository.BbpsCategoryRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/paytrix/commissions")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class CommissionController {

    private final CommissionRangeService rangeService;
    private final CommissionService commissionService;
    private final BbpsCategoryRepository categoryRepository;
    private final CategoryEnablementService categoryEnablementService;
    private final BbpsLookupService lookupService;
    private final CommissionBillGuard billGuard;
    private final AppUserRepository appUserRepository;

    public CommissionController(
            CommissionRangeService rangeService, CommissionService commissionService,
            BbpsCategoryRepository categoryRepository, CategoryEnablementService categoryEnablementService,
            BbpsLookupService lookupService, CommissionBillGuard billGuard, AppUserRepository appUserRepository) {
        this.rangeService = rangeService;
        this.commissionService = commissionService;
        this.categoryRepository = categoryRepository;
        this.categoryEnablementService = categoryEnablementService;
        this.lookupService = lookupService;
        this.billGuard = billGuard;
        this.appUserRepository = appUserRepository;
    }

    @GetMapping("/ranges")
    public List<RangeDto> ranges() {
        return rangeService.list().stream().map(RangeDto::from).toList();
    }

    @PostMapping("/ranges")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public RangeDto addRange(@Valid @RequestBody RangeRequest request) {
        return RangeDto.from(rangeService.add(request.minAmount(), request.maxAmount()));
    }

    @PatchMapping("/ranges/{id}/active")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public RangeDto setRangeActive(@PathVariable UUID id, @Valid @RequestBody ActiveRequest request) {
        return RangeDto.from(rangeService.setActive(id, request.active()));
    }

    @GetMapping("/providers")
    public List<ProviderDto> providers(@RequestParam String categoryId) {
        requireEnabled(categoryId);
        return lookupService.billers(categoryId).stream()
                .map(biller -> new ProviderDto(biller.billerId(), biller.name()))
                .toList();
    }

    @GetMapping
    public List<CommissionDto> list(@RequestParam(required = false) String vendorId) {
        return toDtos(commissionService.list(vendorId));
    }

    @GetMapping("/{id}")
    public CommissionDto get(@PathVariable UUID id) {
        return toDtos(List.of(commissionService.get(id))).get(0);
    }

    @GetMapping("/{id}/log")
    public List<CommissionLogDto> log(@PathVariable UUID id) {
        return commissionService.history(id).stream().map(CommissionLogDto::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public CommissionDto create(@Valid @RequestBody CreateRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
        BbpsCategory category = requireEnabled(request.categoryId());
        BbpsProvider.ProviderBiller operator = lookupService.billers(category.categoryId()).stream()
                .filter(biller -> biller.billerId().equals(request.operatorId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found for this category"));
        Commission saved = commissionService.create(new CommissionService.NewCommission(
                category.id(), operator.billerId(), operator.name(), request.vendorId(),
                request.minRange(), request.maxRange(), request.commission()), actor(principal));
        return toDtos(List.of(saved)).get(0);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public CommissionDto update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request,
                                @AuthenticationPrincipal AuthenticatedUser principal) {
        Commission current = commissionService.get(id);
        if (current.isLive()) {
            billGuard.assertClear(current);
        }
        Commission saved = commissionService.update(
                id, request.minRange(), request.maxRange(), request.commission(), actor(principal));
        return toDtos(List.of(saved)).get(0);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public CommissionDto approve(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return toDtos(List.of(commissionService.approve(id, actor(principal)))).get(0);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public CommissionDto setActive(@PathVariable UUID id, @Valid @RequestBody ActiveRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser principal) {
        if (!request.active()) {
            billGuard.assertClear(commissionService.get(id));
        }
        return toDtos(List.of(commissionService.setActive(id, request.active(), actor(principal)))).get(0);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        billGuard.assertClear(commissionService.get(id));
        commissionService.delete(id, actor(principal));
    }

    private static CommissionService.Actor actor(AuthenticatedUser principal) {
        String name = principal.displayName();
        if (name == null || name.isBlank()) {
            name = principal.userId();
        }
        return new CommissionService.Actor(principal.userId(), name);
    }

    private BbpsCategory requireEnabled(String categoryId) {
        BbpsCategory category = categoryRepository.findActiveByCategoryId(categoryId)
                .orElseThrow(() -> new BadRequestException("Unknown category: " + categoryId));
        if (!categoryEnablementService.isOrganisationEnabled(category.categoryId())) {
            throw new BadRequestException("Category '" + category.categoryId() + "' is not enabled for Paytrix");
        }
        return category;
    }

    private List<CommissionDto> toDtos(List<Commission> rows) {
        Map<UUID, BbpsCategory> categories = categoryRepository.findAllActive().stream()
                .collect(Collectors.toMap(BbpsCategory::id, Function.identity(), (a, b) -> a));
        Set<String> userIds = new HashSet<>();
        for (Commission row : rows) {
            userIds.add(row.getCreatedById());
            userIds.add(row.getUpdatedById());
        }
        userIds.remove(null);
        Map<String, String> users = appUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getUserId, AppUser::getName, (a, b) -> a));
        return rows.stream().map(row -> {
            BbpsCategory category = categories.get(row.getCategoryId());
            return new CommissionDto(
                    row.getId(), row.getMinRange(), row.getMaxRange(),
                    row.getCategoryId(), category == null ? null : category.categoryId(),
                    category == null ? "Unknown category" : category.name(),
                    row.getOperatorId(), row.getOperatorName(), row.getVendorId(), row.getVendorName(),
                    row.getCommission(), row.getStatus().name(), row.isActive(),
                    row.getCreatedAt(), row.getUpdatedAt(),
                    row.getCreatedById(), users.get(Objects.requireNonNullElse(row.getCreatedById(), "")),
                    row.getUpdatedById(), users.get(Objects.requireNonNullElse(row.getUpdatedById(), "")));
        }).toList();
    }

    public record RangeRequest(BigDecimal minAmount, BigDecimal maxAmount) {
    }

    public record RangeDto(UUID id, BigDecimal minAmount, BigDecimal maxAmount, boolean isActive) {
        static RangeDto from(CommissionAmountRange range) {
            return new RangeDto(range.getId(), range.getMinAmount(), range.getMaxAmount(), range.isActive());
        }
    }

    public record ProviderDto(String billerId, String name) {
    }

    public record CreateRequest(
            @NotBlank String categoryId,
            @NotBlank String operatorId,
            @NotBlank String vendorId,
            @NotNull @DecimalMin("0") BigDecimal minRange,
            BigDecimal maxRange,
            @NotNull @DecimalMin("0.001") @DecimalMax("100") BigDecimal commission) {
    }

    public record UpdateRequest(
            @NotNull @DecimalMin("0") BigDecimal minRange,
            BigDecimal maxRange,
            @NotNull @DecimalMin("0.001") @DecimalMax("100") BigDecimal commission) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    /** {@code categoryCode} is the code the category and operator pickers use. */
    public record CommissionDto(
            UUID id, BigDecimal minRange, BigDecimal maxRange,
            UUID categoryId, String categoryCode, String categoryName,
            String operatorId, String operatorName, String vendorId, String vendorName,
            BigDecimal commission, String status, boolean isActive,
            Instant createdAt, Instant updatedAt,
            String createdById, String createdByName, String updatedById, String updatedByName) {
    }

    public record CommissionLogDto(
            UUID id, String action, String summary, String actorUserId, String actorName, Instant createdAt) {
        static CommissionLogDto from(CommissionLog entry) {
            return new CommissionLogDto(
                    entry.getId(), entry.getAction().name(), entry.getSummary(),
                    entry.getActorUserId(), entry.getActorName(), entry.getCreatedAt());
        }
    }
}
