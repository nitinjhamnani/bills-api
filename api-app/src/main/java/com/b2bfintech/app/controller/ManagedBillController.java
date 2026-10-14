package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.BillActivityLogService;
import com.b2bfintech.app.service.ManagedBillService;
import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.common.exception.ApiException;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.ClientScopeGuard;
import com.b2bfintech.app.service.BbpsLookupService;
import com.b2bfintech.app.service.CategoryLabels;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.common.spi.bbps.BillParameterValidator;
import com.b2bfintech.organisation.entity.ClientCategoryConfig;
import com.b2bfintech.organisation.service.CategoryEnablementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Managed Bills: create single or bulk (a {@code bill} row is inserted immediately, then fetched
 * and resolved - see {@link ManagedBillService}), list, and per-bill detail/activity log/automation.
 */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/managed-bills")
@PreAuthorize("hasAnyRole('CLIENT_ADMIN','CLIENT_OPERATOR')")
public class ManagedBillController {

    private final ManagedBillService managedBillService;
    private final com.b2bfintech.billing.repository.BillRepository billRepository;
    private final com.b2bfintech.billing.repository.BillPaymentRepository billPaymentRepository;
    private final BillActivityLogService activityLogService;
    private final CategoryLabels categoryLabels;
    private final CategoryEnablementService categoryEnablementService;
    private final BbpsLookupService bbpsLookupService;

    public ManagedBillController(
            ManagedBillService managedBillService, com.b2bfintech.billing.repository.BillRepository billRepository,
            com.b2bfintech.billing.repository.BillPaymentRepository billPaymentRepository,
            BillActivityLogService activityLogService, CategoryLabels categoryLabels,
            CategoryEnablementService categoryEnablementService, BbpsLookupService bbpsLookupService) {
        this.managedBillService = managedBillService;
        this.billRepository = billRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.activityLogService = activityLogService;
        this.categoryLabels = categoryLabels;
        this.categoryEnablementService = categoryEnablementService;
        this.bbpsLookupService = bbpsLookupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillDtos.ManagedBillDto create(@PathVariable UUID clientId, @Valid @RequestBody CreateRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        categoryEnablementService.assertEnabled(clientId, request.categoryId());
        List<BbpsProvider.ProviderBillerParameter> declared = bbpsLookupService.parameters(request.categoryId(), request.billerId());
        BillParameterValidator.ValidatedParameters validated = BillParameterValidator.validate(request.parameters(), declared);
        BbpsProvider.ProviderBiller biller = bbpsLookupService.billers(request.categoryId(), request.locationId()).stream()
                .filter(candidate -> candidate.billerId().equals(request.billerId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown biller for this category and location"));
        Bill bill = managedBillService.createBill(
                clientId, request.categoryId(), request.locationId(), biller.billerId(), biller.name(),
                validated.accountNumber(), validated.parameters(), request.customerMobileNumber(), principal.phoneNumber());
        return toDto(bill);
    }

    @GetMapping("/bulk/template")
    public ResponseEntity<byte[]> bulkTemplate(@PathVariable UUID clientId,
                                                @AuthenticationPrincipal AuthenticatedUser principal) throws IOException {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        Map<String, String> names = categoryLabels.names();
        List<String> categoryNames = categoryEnablementService.listClientRows(clientId).stream()
                .filter(ClientCategoryConfig::isEnabled)
                .map(c -> names.getOrDefault(c.getCategoryId(), c.getCategoryId()))
                .sorted()
                .toList();
        List<String> billerNames = List.of();
        byte[] xlsx = buildBulkTemplateWorkbook(categoryNames, billerNames);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"managed-bills-upload-template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    private static byte[] buildBulkTemplateWorkbook(List<String> categories, List<String> billers) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bills");
            String[] headers = {"Category", "Service Provider", "Consumer ID / Number", "Customer Mobile Number"};
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 26 * 256);
            }

            Sheet listSheet = workbook.createSheet("Lists");
            int listRows = Math.max(Math.max(categories.size(), billers.size()), 1);
            for (int i = 0; i < listRows; i++) {
                Row row = listSheet.createRow(i);
                if (i < categories.size()) row.createCell(0).setCellValue(categories.get(i));
                if (i < billers.size()) row.createCell(1).setCellValue(billers.get(i));
            }
            workbook.setSheetHidden(workbook.getSheetIndex(listSheet), true);

            var categoryRange = workbook.createName();
            categoryRange.setNameName("CategoryList");
            categoryRange.setRefersToFormula("Lists!$A$1:$A$" + Math.max(categories.size(), 1));

            var billerRange = workbook.createName();
            billerRange.setNameName("BillerList");
            billerRange.setRefersToFormula("Lists!$B$1:$B$" + Math.max(billers.size(), 1));

            DataValidationHelper dvHelper = sheet.getDataValidationHelper();
            int lastDataRow = 500;

            DataValidationConstraint categoryConstraint = dvHelper.createFormulaListConstraint("CategoryList");
            DataValidation categoryValidation = dvHelper.createValidation(categoryConstraint, new CellRangeAddressList(1, lastDataRow, 0, 0));
            categoryValidation.setShowErrorBox(true);
            sheet.addValidationData(categoryValidation);

            DataValidationConstraint billerConstraint = dvHelper.createFormulaListConstraint("BillerList");
            DataValidation billerValidation = dvHelper.createValidation(billerConstraint, new CellRangeAddressList(1, lastDataRow, 1, 1));
            billerValidation.setShowErrorBox(true);
            sheet.addValidationData(billerValidation);

            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue(categories.isEmpty() ? "" : categories.get(0));
            example.createCell(1).setCellValue(billers.isEmpty() ? "" : billers.get(0));
            example.createCell(2).setCellValue("100234567");
            example.createCell(3).setCellValue("9876543210");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<BillDtos.BulkRowResult> createBulk(@PathVariable UUID clientId, @RequestParam MultipartFile file,
                                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        throw new BadRequestException(
                "Bulk upload cannot collect the fields each biller asks for. Create bills one at a time.");
    }

    @GetMapping
    public List<BillDtos.ManagedBillDto> list(@PathVariable UUID clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        Function<String, String> names = categoryLabels.lookup();
        return billRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(b -> BillDtos.ManagedBillDto.from(b, names)).toList();
    }

    @GetMapping("/{billId}")
    public BillDtos.ManagedBillDto get(@PathVariable UUID clientId, @PathVariable UUID billId,
                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return toDto(managedBillService.getOwnedBill(clientId, billId));
    }

    /** Every fetch/payment cycle ever opened for this managed bill, newest first - the "bill
     * trail" shown above the activity log on the details page. */
    @GetMapping("/{billId}/payments")
    public List<BillDtos.BillPaymentDto> payments(@PathVariable UUID clientId, @PathVariable UUID billId,
                                                   @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        Bill bill = managedBillService.getOwnedBill(clientId, billId);
        Function<String, String> names = categoryLabels.lookup();
        return billPaymentRepository.findByBillIdOrderByCreatedAtDesc(billId).stream()
                .map(p -> BillDtos.BillPaymentDto.from(p, bill, names))
                .toList();
    }

    @GetMapping("/{billId}/activity-log")
    public List<BillDtos.ActivityLogDto> activityLog(@PathVariable UUID clientId, @PathVariable UUID billId,
                                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        managedBillService.getOwnedBill(clientId, billId); // 404s if not this client's
        return activityLogService.listForBill(billId).stream().map(BillDtos.ActivityLogDto::from).toList();
    }

    /** One specific fetch/payment cycle's detail - what a row in "Bills against this account" opens. */
    @GetMapping("/{billId}/payments/{paymentId}")
    public BillDtos.BillPaymentDto payment(@PathVariable UUID clientId, @PathVariable UUID billId, @PathVariable UUID paymentId,
                                            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        Bill bill = managedBillService.getOwnedBill(clientId, billId);
        BillPayment payment = getOwnedPayment(bill, paymentId);
        return BillDtos.BillPaymentDto.from(payment, bill, categoryLabels.lookup());
    }

    /** Just this one cycle's activity - as opposed to {@link #activityLog}, which is the whole bill's history. */
    @GetMapping("/{billId}/payments/{paymentId}/activity-log")
    public List<BillDtos.ActivityLogDto> paymentActivityLog(@PathVariable UUID clientId, @PathVariable UUID billId, @PathVariable UUID paymentId,
                                                             @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        Bill bill = managedBillService.getOwnedBill(clientId, billId);
        getOwnedPayment(bill, paymentId); // 404s if not this bill's
        return activityLogService.listForBillPayment(paymentId).stream().map(BillDtos.ActivityLogDto::from).toList();
    }

    private BillPayment getOwnedPayment(Bill bill, UUID paymentId) {
        BillPayment payment = billPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
        if (!payment.getBillId().equals(bill.getId())) {
            throw new ResourceNotFoundException("Bill payment not found");
        }
        return payment;
    }

    @PostMapping("/{billId}/retry-fetch")
    public BillDtos.ManagedBillDto retryFetch(@PathVariable UUID clientId, @PathVariable UUID billId,
                                               @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return toDto(managedBillService.retryFetch(clientId, billId));
    }

    @PatchMapping("/{billId}/automation")
    public BillDtos.ManagedBillDto configureAutomation(@PathVariable UUID clientId, @PathVariable UUID billId,
                                                         @RequestBody AutomationRequest request,
                                                         @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        Bill current = managedBillService.getOwnedBill(clientId, billId);
        boolean autoPayEnabled = request.autoPayEnabled();
        if ("CLIENT_OPERATOR".equals(principal.role())) {
            if (autoPayEnabled != current.isAutoPayEnabled()) {
                throw new AccessDeniedException("Client operators cannot change auto-pay");
            }
            autoPayEnabled = current.isAutoPayEnabled();
        }
        return toDto(managedBillService.configureAutomation(
                clientId, billId, request.autoFetchEnabled(), autoPayEnabled));
    }

    private BillDtos.ManagedBillDto toDto(Bill bill) {
        return BillDtos.ManagedBillDto.from(bill, categoryLabels.lookup());
    }

    public record CreateRequest(
            @NotBlank String categoryId,
            @NotBlank String locationId,
            @NotBlank String billerId,
            @NotNull Map<String, String> parameters,
            @NotBlank @Pattern(regexp = "\\d{10}") String customerMobileNumber) {
    }

    public record AutomationRequest(boolean autoFetchEnabled, boolean autoPayEnabled) {
    }
}
