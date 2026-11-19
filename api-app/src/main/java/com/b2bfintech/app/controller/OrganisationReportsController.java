package com.b2bfintech.app.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Reports for Paytrix staff across the companies they serve. */
@RestController
@RequestMapping("/api/v1/paytrix/reports")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class OrganisationReportsController {

    private final ReportQueryService reports;

    public OrganisationReportsController(ReportQueryService reports) {
        this.reports = reports;
    }

    /** Every corporate: bills raised and bills paid to the biller in the period. */
    @GetMapping("/corporate-business")
    public List<ReportDtos.CorporateBusinessRowDto> corporateBusiness(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.corporateBusiness(range.from(), range.to());
    }

    /** Every vendor: bills assigned and bills that vendor paid in the period. */
    @GetMapping("/vendor-business")
    public List<ReportDtos.VendorBusinessRowDto> vendorBusiness(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.vendorBusiness(range.from(), range.to());
    }

    /** Every sales person, the companies assigned to them, and the bills those companies paid. */
    @GetMapping("/sales-business")
    public List<ReportDtos.SalesBusinessRowDto> salesBusiness(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.salesBusiness(range.from(), range.to());
    }

    /** Paytrix transactions grouped by the What column, with money in and money out. */
    @GetMapping("/transactions")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public List<ReportDtos.TransactionCategoryRowDto> transactions(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.transactionsByWhat(range.from(), range.to());
    }

    /** Bills a corporate raised that a vendor paid and Paytrix verified, by the date the vendor paid. */
    @GetMapping("/verified-vendor-payments")
    public List<ReportDtos.VerifiedVendorPaymentRowDto> verifiedVendorPayments(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.verifiedVendorPayments(range.from(), range.to());
    }
}
