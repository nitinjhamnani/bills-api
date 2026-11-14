package com.b2bfintech.organisation.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.organisation.entity.CommissionAmountRange;
import com.b2bfintech.organisation.repository.CommissionAmountRangeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class CommissionRangeService {

    private final CommissionAmountRangeRepository rangeRepository;

    public CommissionRangeService(CommissionAmountRangeRepository rangeRepository) {
        this.rangeRepository = rangeRepository;
    }

    public List<CommissionAmountRange> list() {
        return rangeRepository.findAllByOrderByMinAmountAsc();
    }

    @Transactional
    public CommissionAmountRange add(BigDecimal minAmount, BigDecimal maxAmount) {
        BigDecimal min = money(minAmount, "Enter a minimum amount");
        BigDecimal max = maxAmount == null ? null : money(maxAmount, "Enter a maximum amount");
        if (min.signum() < 0) {
            throw new BadRequestException("Minimum amount cannot be negative");
        }
        if (max != null && max.compareTo(min) < 0) {
            throw new BadRequestException("Maximum amount must be at least the minimum");
        }
        assertNoOverlap(min, max, null);
        CommissionAmountRange range = new CommissionAmountRange();
        range.setMinAmount(min);
        range.setMaxAmount(max);
        range.setActive(true);
        return rangeRepository.save(range);
    }

    /** An inactive range is hidden from the commission form. Reactivating it must not overlap an active range. */
    @Transactional
    public CommissionAmountRange setActive(UUID id, boolean active) {
        CommissionAmountRange range = rangeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Amount range not found"));
        if (range.isActive() == active) {
            return range;
        }
        if (active) {
            assertNoOverlap(range.getMinAmount(), range.getMaxAmount(), range.getId());
        }
        range.setActive(active);
        return rangeRepository.save(range);
    }

    private void assertNoOverlap(BigDecimal min, BigDecimal max, UUID ignoreId) {
        for (CommissionAmountRange existing : rangeRepository.findAll()) {
            if (!existing.isActive() || existing.getId().equals(ignoreId)) {
                continue;
            }
            if (overlaps(min, max, existing.getMinAmount(), existing.getMaxAmount())) {
                throw new BadRequestException(
                        "This range overlaps " + describe(existing)
                                + ". Minimum and maximum are inclusive, so two active ranges cannot share an amount.");
            }
        }
    }

    public static boolean overlaps(BigDecimal aMin, BigDecimal aMax, BigDecimal bMin, BigDecimal bMax) {
        boolean aEndsBeforeB = aMax != null && aMax.compareTo(bMin) < 0;
        boolean bEndsBeforeA = bMax != null && bMax.compareTo(aMin) < 0;
        return !aEndsBeforeB && !bEndsBeforeA;
    }

    public static String describe(CommissionAmountRange range) {
        if (range.getMaxAmount() == null) {
            return range.getMinAmount().toPlainString() + " and above";
        }
        return range.getMinAmount().toPlainString() + "–" + range.getMaxAmount().toPlainString();
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
}
