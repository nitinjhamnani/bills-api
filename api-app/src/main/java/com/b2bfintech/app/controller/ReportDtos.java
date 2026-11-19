package com.b2bfintech.app.controller;

import com.b2bfintech.common.exception.BadRequestException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** DTOs and small helpers shared by the client reports and the monthly statement. */
public final class ReportDtos {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final int MAX_RANGE_DAYS = 90;
    public static final int MAX_ROWS = 5000;

    private ReportDtos() {
    }

    /** Parses {@code from}/{@code to} as inclusive IST calendar dates. Defaults to the trailing
     * {@code defaultDays} ending today when either is omitted. Rejects windows longer than 90 days. */
    public static DateRange parseRange(String from, String to, int defaultDays) {
        try {
            LocalDate endDate = to != null && !to.isBlank() ? LocalDate.parse(to) : LocalDate.now(IST);
            LocalDate startDate = from != null && !from.isBlank()
                    ? LocalDate.parse(from)
                    : endDate.minusDays(defaultDays);
            if (startDate.isAfter(endDate)) {
                throw new BadRequestException("'from' must be before 'to'");
            }
            long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            if (days > MAX_RANGE_DAYS) {
                throw new BadRequestException("Date range cannot exceed " + MAX_RANGE_DAYS + " days");
            }
            Instant start = startDate.atStartOfDay(IST).toInstant();
            Instant end = endDate.plusDays(1).atStartOfDay(IST).toInstant();
            return new DateRange(start, end);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("from/to must be dates in yyyy-MM-dd format");
        }
    }

    public record DateRange(Instant from, Instant to) {
    }

    public record WalletStatementDto(
            BigDecimal openingBalance, BigDecimal closingBalance,
            BigDecimal totalCredits, BigDecimal totalDebits,
            List<BillDtos.LedgerEntryDto> entries) {
    }

    public record FundingRowDto(
            String id, String clientId, String clientName, String clientCode, BigDecimal amount,
            String paymentMode, String status, String paymentReference, String requestedBy, String reviewedBy,
            Instant createdAt, Instant updatedAt) {
    }

    /** One wallet-paid bill under a branch, with that branch's bill count and amount. */
    public record BranchBillsRowDto(
            String branchId, String branchName,
            String billId, BigDecimal billAmount,
            long branchBills, BigDecimal branchTotal) {
    }

    public record SpendByServiceRowDto(
            String billServiceCode, String billServiceName, long count, BigDecimal amount) {
    }

    /** Bills a corporate raised and bills that were paid to the biller in one period. */
    public record CorporateBusinessRowDto(
            String corpId, String corpName,
            long billsRaised, BigDecimal raisedAmount,
            long billsPaid, BigDecimal paidAmount) {
    }

    /** One company under a sales person, plus that person's total for the period. */
    public record SalesBusinessRowDto(
            String salesUserId, String salesPersonName,
            String corpId, String corpName,
            long billsPaid, BigDecimal paidAmount,
            long salesBillsPaid, BigDecimal salesPaidAmount) {
    }

    /** Bills assigned to a vendor and bills that vendor paid in one period. */
    public record VendorBusinessRowDto(
            String vendorId, String vendorName,
            long billsAssigned, BigDecimal assignedAmount,
            long billsPaid, BigDecimal paidAmount) {
    }

    /** One What from the Paytrix transactions list, with money in and money out. */
    public record TransactionCategoryRowDto(String what, BigDecimal moneyIn, BigDecimal moneyOut) {
    }

    /** A bill a vendor paid and Paytrix verified. */
    public record VerifiedVendorPaymentRowDto(
            String corpId, String corpName, String paidById, String paidByName,
            String billId, BigDecimal billAmount, Instant paidOn) {
    }
}
