package com.b2bfintech.app.controller;

import com.b2bfintech.platform.model.Biller;
import com.b2bfintech.platform.repository.BillerRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The master list of individual service providers (billers) a client picks from in the
 * "Create bill" flow, optionally narrowed to one category. Platform-wide master data, same as
 * {@code /api/v1/organisation/bill-services/catalog}.
 */
@RestController
public class BillerController {

    private final BillerRepository billerRepository;

    public BillerController(BillerRepository billerRepository) {
        this.billerRepository = billerRepository;
    }

    @GetMapping("/api/v1/billers")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER','CLIENT_ADMIN','CLIENT_OPERATOR')")
    public List<BillerDto> list(@RequestParam(required = false) String category) {
        List<Biller> billers = (category == null || category.isBlank())
                ? billerRepository.findAllActive()
                : billerRepository.findActiveByCategory(category);
        return billers.stream().map(BillerDto::from).toList();
    }

    public record BillerDto(String billerId, String billerName, String category, String state) {
        static BillerDto from(Biller b) {
            return new BillerDto(b.billerId(), b.billerName(), b.category(), b.state());
        }
    }
}
