package com.b2bfintech.organisation.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.CommissionAmountRange;
import com.b2bfintech.organisation.repository.CommissionAmountRangeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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
        for (CommissionAmountRange existing : rangeRepository.findAll()) {
            if (overlaps(min, max, existing.getMinAmount(), existing.getMaxAmount())) {
                throw new BadRequestException(
                        "This range overlaps " + describe(existing)
                                + ". Minimum and maximum are inclusive, so two ranges cannot share an amount.");
            }
        }
        CommissionAmountRange range = new CommissionAmountRange();
        range.setMinAmount(min);
        range.setMaxAmount(max);
        return rangeRepository.save(range);
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
