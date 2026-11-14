package com.b2bfintech.organisation.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.Commission;
import com.b2bfintech.organisation.entity.CommissionLog;
import com.b2bfintech.organisation.repository.CommissionLogRepository;
import com.b2bfintech.organisation.repository.CommissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A new commission is approved immediately. A vendor cannot hold two active commissions on the
 * same operator whose amount ranges overlap. Every change is written to {@code commission_log}.
 */
@Service
public class CommissionService {

    private final CommissionRepository repository;
    private final CommissionLogRepository logRepository;
    private final ClientService clientService;

    public CommissionService(CommissionRepository repository, CommissionLogRepository logRepository,
                             ClientService clientService) {
        this.repository = repository;
        this.logRepository = logRepository;
        this.clientService = clientService;
    }

    @Transactional(readOnly = true)
    public List<Commission> list(String vendorId) {
        List<Commission> rows = vendorId == null
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByVendorIdOrderByCreatedAtDesc(vendorId);
        return rows.stream()
                .sorted(Comparator.comparing(Commission::isActive).reversed()
                        .thenComparing(Commission::getOperatorName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Commission::getMinRange)
                        .thenComparing(Commission::getVendorName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public Commission get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Commission not found"));
    }

    @Transactional(readOnly = true)
    public List<CommissionLog> history(UUID id) {
        get(id);
        return logRepository.findByCommissionIdOrderByCreatedAtDesc(id);
    }

    @Transactional
    public Commission create(NewCommission request, Actor actor) {
        Client vendor = requireSelectableVendor(request.vendorId());
        Commission commission = new Commission();
        commission.setCategoryId(request.categoryId());
        commission.setOperatorId(request.operatorId().trim());
        commission.setOperatorName(request.operatorName().trim());
        commission.setVendorId(vendor.getId());
        commission.setVendorName(vendor.getClientName());
        commission.setMinRange(money(request.minRange(), "Enter a minimum amount"));
        commission.setMaxRange(request.maxRange() == null ? null : money(request.maxRange(), "Enter a maximum amount"));
        commission.setCommission(percent(request.commission()));
        commission.setStatus(Commission.Status.APPROVED);
        commission.setActive(true);
        commission.setCreatedById(actor.userId());
        commission.setUpdatedById(actor.userId());
        assertRange(commission);
        assertNoOverlap(commission);
        Commission saved = repository.save(commission);
        record(saved.getId(), CommissionLog.Action.CREATED,
                "Created as approved. Amount " + describe(saved) + ". Commission " + saved.getCommission().toPlainString() + "%.",
                actor);
        return saved;
    }

    /** Amount and percent can change. An approved commission stays approved. */
    @Transactional
    public Commission update(UUID id, BigDecimal minRange, BigDecimal maxRange, BigDecimal percent, Actor actor) {
        Commission commission = get(id);
        BigDecimal nextMin = money(minRange, "Enter a minimum amount");
        BigDecimal nextMax = maxRange == null ? null : money(maxRange, "Enter a maximum amount");
        BigDecimal nextPercent = percent(percent);
        String changes = describeChanges(commission, nextMin, nextMax, nextPercent);
        if (changes.isEmpty()) {
            return commission;
        }
        commission.setMinRange(nextMin);
        commission.setMaxRange(nextMax);
        commission.setCommission(nextPercent);
        commission.setUpdatedById(actor.userId());
        assertRange(commission);
        assertNoOverlap(commission);
        Commission saved = repository.save(commission);
        record(saved.getId(), CommissionLog.Action.UPDATED, changes, actor);
        return saved;
    }

    @Transactional
    public Commission approve(UUID id, Actor actor) {
        Commission commission = get(id);
        if (commission.getStatus() == Commission.Status.APPROVED) {
            throw new BadRequestException("This commission is already approved");
        }
        commission.setStatus(Commission.Status.APPROVED);
        commission.setUpdatedById(actor.userId());
        Commission saved = repository.save(commission);
        record(saved.getId(), CommissionLog.Action.APPROVED, "Approved.", actor);
        return saved;
    }

    @Transactional
    public Commission setActive(UUID id, boolean active, Actor actor) {
        Commission commission = get(id);
        if (commission.isActive() == active) {
            return commission;
        }
        commission.setActive(active);
        commission.setUpdatedById(actor.userId());
        if (active) {
            assertNoOverlap(commission);
        }
        Commission saved = repository.save(commission);
        record(saved.getId(), active ? CommissionLog.Action.ACTIVATED : CommissionLog.Action.DEACTIVATED,
                active ? "Activated." : "Deactivated.", actor);
        return saved;
    }

    @Transactional
    public void delete(UUID id, Actor actor) {
        Commission commission = get(id);
        record(commission.getId(), CommissionLog.Action.DELETED,
                "Deleted. " + commission.getVendorName() + ", " + commission.getOperatorName()
                        + ", " + describe(commission) + ", " + commission.getCommission().toPlainString() + "%.",
                actor);
        repository.delete(commission);
    }

    /** Approved, active commissions on this operator, for assigning a paid bill. */
    @Transactional(readOnly = true)
    public List<Commission> liveRates(UUID categoryId, String operatorId) {
        if (categoryId == null || operatorId == null) {
            return List.of();
        }
        return repository.findByCategoryIdAndOperatorId(categoryId, operatorId).stream()
                .filter(Commission::isLive)
                .toList();
    }

    private void assertNoOverlap(Commission commission) {
        if (!commission.isActive()) {
            return;
        }
        for (Commission other : repository.findByCategoryIdAndOperatorId(commission.getCategoryId(), commission.getOperatorId())) {
            if (other.getId() != null && other.getId().equals(commission.getId())) {
                continue;
            }
            if (!other.isActive() || !other.getVendorId().equals(commission.getVendorId())) {
                continue;
            }
            if (CommissionRangeService.overlaps(
                    commission.getMinRange(), commission.getMaxRange(), other.getMinRange(), other.getMaxRange())) {
                throw new BadRequestException(commission.getVendorName() + " already has a commission on "
                        + commission.getOperatorName() + " for " + describe(other) + ". Amount ranges cannot overlap.");
            }
        }
    }

    private static void assertRange(Commission commission) {
        if (commission.getMinRange().signum() < 0) {
            throw new BadRequestException("Minimum amount cannot be negative");
        }
        if (commission.getMaxRange() != null && commission.getMaxRange().compareTo(commission.getMinRange()) < 0) {
            throw new BadRequestException("Maximum amount must be at least the minimum");
        }
    }

    private Client requireSelectableVendor(String vendorId) {
        Client vendor = clientService.getById(vendorId);
        if (vendor.getClientType() != Client.ClientType.VENDOR) {
            throw new BadRequestException("A commission can only be for a vendor");
        }
        if (vendor.getStatus() != Client.ClientStatus.ACTIVE && vendor.getStatus() != Client.ClientStatus.PENDING) {
            throw new BadRequestException(vendor.getClientName() + " is not available for a commission");
        }
        return vendor;
    }

    private void record(UUID commissionId, CommissionLog.Action action, String summary, Actor actor) {
        CommissionLog entry = new CommissionLog();
        entry.setCommissionId(commissionId);
        entry.setAction(action);
        entry.setSummary(summary.length() > 500 ? summary.substring(0, 497) + "..." : summary);
        entry.setActorUserId(actor.userId());
        entry.setActorName(actor.name());
        logRepository.save(entry);
    }

    private static String describeChanges(Commission current, BigDecimal min, BigDecimal max, BigDecimal percent) {
        StringBuilder changes = new StringBuilder();
        if (current.getMinRange().compareTo(min) != 0) {
            appendChange(changes, "Minimum amount", moneyText(current.getMinRange()), moneyText(min));
        }
        if (!sameMoney(current.getMaxRange(), max)) {
            appendChange(changes, "Maximum amount", moneyText(current.getMaxRange()), moneyText(max));
        }
        if (current.getCommission().compareTo(percent) != 0) {
            appendChange(changes, "Commission", current.getCommission().toPlainString() + "%", percent.toPlainString() + "%");
        }
        return changes.toString();
    }

    private static void appendChange(StringBuilder changes, String label, String from, String to) {
        if (!changes.isEmpty()) {
            changes.append(' ');
        }
        changes.append(label).append(' ').append(from).append(" → ").append(to).append('.');
    }

    private static boolean sameMoney(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
    }

    private static String moneyText(BigDecimal amount) {
        return amount == null ? "none" : "₹" + amount.toPlainString();
    }

    private static String describe(Commission commission) {
        return commission.getMaxRange() == null
                ? "₹" + commission.getMinRange().toPlainString() + " and above"
                : "₹" + commission.getMinRange().toPlainString() + "–₹" + commission.getMaxRange().toPlainString();
    }

    private static BigDecimal money(BigDecimal value, String missing) {
        if (value == null) {
            throw new BadRequestException(missing);
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new BadRequestException("Amounts can have at most 2 decimal places");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal percent(BigDecimal value) {
        if (value == null) {
            throw new BadRequestException("Enter a commission percent");
        }
        if (value.stripTrailingZeros().scale() > 3) {
            throw new BadRequestException("Commission percent can have at most 3 decimal places");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw new BadRequestException("Commission percent must be greater than 0 and at most 100");
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    public record NewCommission(
            UUID categoryId, String operatorId, String operatorName, String vendorId,
            BigDecimal minRange, BigDecimal maxRange, BigDecimal commission) {
    }

    public record Actor(String userId, String name) {
    }
}
