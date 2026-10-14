package com.b2bfintech.app.controller;

import com.b2bfintech.common.exception.BadRequestException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** DTOs and small helpers shared by the Reports controllers (client, organisation, platform). */
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

    public record ClientPortfolioRowDto(
            String clientId, String clientName, String clientCode, String clientType, String status,
            Instant createdAt, BigDecimal walletBalance, long totalBillPayments, BigDecimal totalPaidAmount,
            BigDecimal outstandingAmount, BigDecimal pendingSettlementAmount) {
    }

    public record RevenueRowDto(
            String clientId, String clientName, String clientCode, BigDecimal amount, String description, Instant createdAt) {
    }

    public record RevenueByClientRowDto(
            String clientId, String clientName, String clientCode, long feeCount, BigDecimal totalAmount) {
    }

    public record FundingRowDto(
            String id, String clientId, String clientName, String clientCode, BigDecimal amount,
            String paymentMode, String status, String paymentReference, String requestedBy, String reviewedBy,
            Instant createdAt, Instant updatedAt) {
    }

    public record AdjustmentRowDto(
            String clientId, String clientName, String clientCode, BigDecimal amount, String description, Instant createdAt) {
    }

    public record OrganisationPortfolioRowDto(
            String organisationId, String organisationCode, String organisationName, String status,
            Instant createdAt, long clientCount, long staffCount, BigDecimal totalGmv) {
    }

    public record OrganisationGmvRowDto(
            String organisationId, String organisationCode, String organisationName,
            long billsPaid, BigDecimal totalAmount) {
    }

    public record AgingRowDto(
            String paymentId, String billId, String clientId, String clientName, String clientCode,
            String billerName, String customerBillAccountNumber, BigDecimal amount, String orgStatus,
            Instant stuckSince, long daysStuck, String dueDate) {
    }

    public record ClientGmvRowDto(
            String clientId, String clientName, String clientCode,
            long clientPaidCount, BigDecimal clientPaidAmount,
            long orgSettledCount, BigDecimal orgSettledAmount) {
    }

    public record SpendByServiceRowDto(
            String billServiceCode, String billServiceName, long count, BigDecimal amount) {
    }
}
