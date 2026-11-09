package com.b2bfintech.app.controller;

import com.b2bfintech.common.exception.ApiException;
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
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Spreadsheet the vendor uses to mark assigned bills in progress or paid. */
final class VendorBillSheet {

    static final String[] HEADERS = {
            "Payment id", "Consumer number", "Biller", "Amount", "Current stage", "New status", "Reference"
    };

    private VendorBillSheet() {
    }

    static byte[] workbook(List<BillDtos.BillPaymentDto> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Assigned bills");
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 24 * 256);
            }
            for (int i = 0; i < rows.size(); i++) {
                BillDtos.BillPaymentDto bill = rows.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(bill.id());
                row.createCell(1).setCellValue(bill.customerBillAccountNumber() == null ? "" : bill.customerBillAccountNumber());
                row.createCell(2).setCellValue(bill.billerName() == null ? "" : bill.billerName());
                row.createCell(3).setCellValue(bill.amount() == null ? "" : bill.amount().toPlainString());
                row.createCell(4).setCellValue(stageLabel(bill.vendorStage()));
                row.createCell(5).setCellValue("Unchanged");
                row.createCell(6).setCellValue("");
            }
            Sheet lists = workbook.createSheet("Lists");
            lists.createRow(0).createCell(0).setCellValue("Unchanged");
            lists.createRow(1).createCell(0).setCellValue("In progress");
            lists.createRow(2).createCell(0).setCellValue("Paid");
            workbook.setSheetHidden(workbook.getSheetIndex(lists), true);
            var statusRange = workbook.createName();
            statusRange.setNameName("VendorStatusList");
            statusRange.setRefersToFormula("Lists!$A$1:$A$3");
            DataValidationHelper helper = sheet.getDataValidationHelper();
            DataValidationConstraint constraint = helper.createFormulaListConstraint("VendorStatusList");
            DataValidation validation = helper.createValidation(
                    constraint, new CellRangeAddressList(1, Math.max(rows.size(), 1), 5, 5));
            validation.setShowErrorBox(true);
            sheet.addValidationData(validation);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    static List<RawRow> read(InputStream input) {
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<RawRow> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String paymentId = formatter.formatCellValue(row.getCell(0)).trim();
                if (paymentId.isEmpty()) continue;
                rows.add(new RawRow(
                        r + 1,
                        paymentId,
                        formatter.formatCellValue(row.getCell(5)).trim(),
                        formatter.formatCellValue(row.getCell(6)).trim()));
            }
            return rows;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                    "Could not read the uploaded file. Use the downloaded spreadsheet.");
        }
    }

    private static String stageLabel(String stage) {
        if ("IN_PROGRESS".equals(stage)) return "In progress";
        if ("ASSIGNED".equals(stage) || stage == null) return "Assigned";
        return stage;
    }

    record RawRow(int row, String paymentId, String status, String reference) {
    }
}
