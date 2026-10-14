package com.b2bfintech.organisation.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.FeeConfig;
import com.b2bfintech.organisation.repository.FeeConfigRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Organisation-to-client convenience fee configuration (implementation plan &sect;8.2). */
@Service
public class FeeConfigService {

    private final FeeConfigRepository repository;

    public FeeConfigService(FeeConfigRepository repository) {
        this.repository = repository;
    }

    public FeeConfig upsert(String billServiceCode, FeeConfig.FeeType feeType, java.math.BigDecimal value, boolean enabled) {
        if (value == null || value.signum() < 0) {
            throw new BadRequestException("value must not be negative");
        }
        Optional<FeeConfig> existing = billServiceCode == null
                ? repository.findByBillServiceCodeIsNullAndEnabledTrue()
                : repository.findByBillServiceCodeAndEnabledTrue(billServiceCode);
        FeeConfig config = existing.orElseGet(FeeConfig::new);
        config.setBillServiceCode(billServiceCode);
        config.setFeeType(feeType);
        config.setValue(value);
        config.setEnabled(enabled);
        return repository.save(config);
    }

    public List<FeeConfig> listAll() {
        return repository.findAll();
    }

    /** Service-specific fee takes precedence over a blanket (null billServiceCode) fee. */
    public Optional<FeeConfig> getApplicableFee(String billServiceCode) {
        Optional<FeeConfig> specific = repository.findByBillServiceCodeAndEnabledTrue(billServiceCode);
        return specific.isPresent() ? specific : repository.findByBillServiceCodeIsNullAndEnabledTrue();
    }
}
