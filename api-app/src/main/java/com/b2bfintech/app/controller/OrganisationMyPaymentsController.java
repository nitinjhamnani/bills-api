package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.MakerCheckerPolicy;
import com.b2bfintech.app.service.OrganisationPaymentService;
import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.ApiException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.app.service.CategoryLabels;
import com.b2bfintech.wallet.repository.LedgerEntryRepository;
import jakarta.validation.constraints.NotBlank;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The organisation's My Payments screen: bill payments the client has already paid for
 * (org_status PENDING/IN_PROCESS), with pick-up/mark-paid/mark-failed actions, plus a bulk Excel
 * download/upload for marking many paid at once (implementation plan's Apache POI pattern, reused
 * from the old bulk bill upload template).
 */
@RestController
@RequestMapping("/api/v1/organisation/my-payments")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class OrganisationMyPaymentsController {

    private final OrganisationPaymentService organisationPaymentService;
    private final BillPaymentRepository billPaymentRepository;
    private final BillRepository billRepository;
    private final CategoryLabels categoryLabels;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final MakerCheckerPolicy makerCheckerPolicy;

    public OrganisationMyPaymentsController(
            OrganisationPaymentService organisationPaymentService, BillPaymentRepository billPaymentRepository,
            BillRepository billRepository, CategoryLabels categoryLabels,
            LedgerEntryRepository ledgerEntryRepository, MakerCheckerPolicy makerCheckerPolicy) {
        this.organisationPaymentService = organisationPaymentService;
        this.billPaymentRepository = billPaymentRepository;
        this.billRepository = billRepository;
        this.categoryLabels = categoryLabels;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.makerCheckerPolicy = makerCheckerPolicy;
    }

    @GetMapping
    public List<BillDtos.BillPaymentDto> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        Function<String, String> names = categoryLabels.lookup();
        return billPaymentRepository
                .findByOrgStatusInOrderByCreatedAtDesc(List.of(BillPayment.OrgStatus.PENDING, BillPayment.OrgStatus.IN_PROCESS))
                .stream().map(p -> toDto(p, names, principal)).toList();
    }

    @GetMapping("/{id}")
    public BillDtos.BillPaymentDto get(@PathVariable java.util.UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return toDto(getOrThrow(id), categoryLabels.lookup(), principal);
    }

    @GetMapping("/{id}/transactions")
    public List<BillDtos.LedgerEntryDto> transactions(@PathVariable java.util.UUID id) {
        getOrThrow(id);
        return ledgerEntryRepository.findByReferenceIdOrderByCreatedAtDesc(id.toString()).stream()
                .map(BillDtos.LedgerEntryDto::from).toList();
    }

    @PostMapping("/{id}/pick-up")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER')")
    public BillDtos.BillPaymentDto pickUp(@PathVariable java.util.UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return toDto(organisationPaymentService.pickUp(id, principal.phoneNumber()),
                categoryLabels.lookup(), principal);
    }

    @PostMapping("/{id}/mark-paid")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER')")
    public BillDtos.BillPaymentDto markPaid(@PathVariable java.util.UUID id, @RequestBody MarkPaidRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser principal) {
        return toDto(organisationPaymentService.markPaid(
                        id, principal, request.bbpsTransactionId(), request.externalTransactionId(), request.billType()),
                categoryLabels.lookup(), principal);
    }

    @PostMapping("/{id}/mark-failed")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER')")
    public BillDtos.BillPaymentDto markFailed(@PathVariable java.util.UUID id, @RequestBody MarkFailedRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser principal) {
        return toDto(organisationPaymentService.markFailed(id, principal.phoneNumber(), request.reason()),
                categoryLabels.lookup(), principal);
    }

    /** Downloads the selected bill payments as an editable .xlsx. Status defaults to Paid so a
     * re-upload settles every row; change a row to Failed or Unchanged if needed. */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER')")
    public ResponseEntity<byte[]> exportExcel(@RequestParam List<java.util.UUID> ids,
                                               @AuthenticationPrincipal AuthenticatedUser principal) throws IOException {
        Function<String, String> names = categoryLabels.lookup();
        List<BillDtos.BillPaymentDto> rows = ids.stream()
                .map(this::getOrThrow).map(p -> toDto(p, names, principal)).toList();
        byte[] xlsx = buildExportWorkbook(rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bill-payments.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    private static final String[] EXPORT_HEADERS = {
            "Bill Payment ID", "Client Consumer Number", "Biller", "Amount", "Due Date", "Current Status",
            "Status (change to Paid or Failed)", "BBPS Transaction ID", "External Transaction ID", "Bill Type", "Failure Reason",
    };
    private static final int STATUS_COL = 6;

    private static byte[] buildExportWorkbook(List<BillDtos.BillPaymentDto> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bill Payments");
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(EXPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 24 * 256);
            }

            Sheet listSheet = workbook.createSheet("Lists");
            listSheet.createRow(0).createCell(0).setCellValue("Unchanged");
            listSheet.createRow(1).createCell(0).setCellValue("Paid");
            listSheet.createRow(2).createCell(0).setCellValue("Failed");
            workbook.setSheetHidden(workbook.getSheetIndex(listSheet), true);
            var statusRange = workbook.createName();
            statusRange.setNameName("StatusList");
            statusRange.setRefersToFormula("Lists!$A$1:$A$3");

            DataValidationHelper dvHelper = sheet.getDataValidationHelper();
            DataValidationConstraint statusConstraint = dvHelper.createFormulaListConstraint("StatusList");
            DataValidation statusValidation = dvHelper.createValidation(
                    statusConstraint, new CellRangeAddressList(1, Math.max(rows.size(), 500), STATUS_COL, STATUS_COL));
            statusValidation.setShowErrorBox(true);
            sheet.addValidationData(statusValidation);

            int r = 1;
            for (BillDtos.BillPaymentDto p : rows) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(p.id());
                row.createCell(1).setCellValue(p.customerBillAccountNumber());
                row.createCell(2).setCellValue(p.billerName());
                row.createCell(3).setCellValue(p.amount().doubleValue());
                row.createCell(4).setCellValue(p.dueDate() == null ? "" : p.dueDate());
                row.createCell(5).setCellValue(p.orgStatus());
                row.createCell(6).setCellValue("Paid");
                row.createCell(7).setCellValue(p.bbpsTransactionId() == null ? "" : p.bbpsTransactionId());
                row.createCell(8).setCellValue(p.externalTransactionId() == null ? "" : p.externalTransactionId());
                row.createCell(9).setCellValue(p.billType() == null ? "" : p.billType());
                row.createCell(10).setCellValue(p.billPayFailureReason() == null ? "" : p.billPayFailureReason());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** Settles every row marked Paid (pick-up first if still awaiting pick-up). Failed rows
     * return to the queue. Unchanged/blank rows with no settlement fields are skipped. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER')")
    public List<BillDtos.BulkRowResult> importExcel(@RequestParam MultipartFile file, @AuthenticationPrincipal AuthenticatedUser principal) {
        List<BillDtos.BulkRowResult> results = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                int rowNum = r + 1;
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String idRaw = formatter.formatCellValue(row.getCell(0)).trim();
                if (idRaw.isEmpty()) continue;
                String status = formatter.formatCellValue(row.getCell(STATUS_COL)).trim();
                String bbps = formatter.formatCellValue(row.getCell(7)).trim();
                String external = formatter.formatCellValue(row.getCell(8)).trim();
                String billType = formatter.formatCellValue(row.getCell(9)).trim();
                String reason = formatter.formatCellValue(row.getCell(10)).trim();
                try {
                    java.util.UUID id = java.util.UUID.fromString(idRaw);
                    String normalized = status.replace(' ', '_').toUpperCase();
                    boolean markPaid = "PAID".equals(normalized) || "SUCCESS".equals(normalized)
                            || ((normalized.isEmpty() || "UNCHANGED".equals(normalized))
                            && (!bbps.isEmpty() || !billType.isEmpty()));
                    boolean markFailed = "FAILED".equals(normalized) || "FAIL".equals(normalized);
                    if (markPaid) {
                        organisationPaymentService.settleFromImport(
                                id, principal, true,
                                bbps.isEmpty() ? null : bbps, external.isEmpty() ? null : external, billType.isEmpty() ? null : billType, null);
                        results.add(new BillDtos.BulkRowResult(rowNum, true, idRaw, null));
                    } else if (markFailed) {
                        organisationPaymentService.settleFromImport(
                                id, principal, false, null, null, null, reason.isEmpty() ? null : reason);
                        results.add(new BillDtos.BulkRowResult(rowNum, true, idRaw, null));
                    }
                    // "Unchanged" or blank with no settlement fields - skipped, not an error.
                } catch (RuntimeException ex) {
                    results.add(new BillDtos.BulkRowResult(rowNum, false, idRaw, ex.getMessage()));
                }
            }
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                    "Could not read the uploaded file - make sure it's a valid, unprotected .xlsx (re-saving with some spreadsheet apps, e.g. Numbers, can subtly change the file format). "
                            + describeCause(ex));
        }
        return results;
    }

    private static String describeCause(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "" : "Detail: " + message;
    }

    private BillPayment getOrThrow(java.util.UUID id) {
        return billPaymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
    }

    private BillDtos.BillPaymentDto toDto(BillPayment payment, Function<String, String> names, AuthenticatedUser principal) {
        Bill bill = billRepository.findById(payment.getBillId()).orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        return BillDtos.BillPaymentDto.from(
                payment, bill, names,
                makerCheckerPolicy.awaitingChecker(payment),
                makerCheckerPolicy.canSettleBill(principal, payment));
    }

    public record MarkPaidRequest(String bbpsTransactionId, String externalTransactionId, String billType) {
    }

    public record MarkFailedRequest(@NotBlank String reason) {
    }
}
