package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.VendorSettlementService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Per-client commission the vendor owes for reimbursements in a period. */
final class CommissionReportSheet {

    private static final String[] HEADERS = {
            "Client", "Payment id", "Consumer number", "Biller", "Bill amount", "Commission %", "Commission amount"
    };

    private CommissionReportSheet() {
    }

    static byte[] workbook(VendorSettlementService.CommissionPreview preview) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Commission");
            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle boldStyle = workbook.createCellStyle();
            boldStyle.setFont(bold);
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(boldStyle);
                sheet.setColumnWidth(i, 24 * 256);
            }
            int rowIndex = 1;
            for (VendorSettlementService.ClientGroup group : preview.clients()) {
                for (VendorSettlementService.BillLine line : group.bills()) {
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(group.clientName());
                    row.createCell(1).setCellValue(line.billPaymentId().toString());
                    row.createCell(2).setCellValue(line.consumerNumber() == null ? "" : line.consumerNumber());
                    row.createCell(3).setCellValue(line.billerName() == null ? "" : line.billerName());
                    row.createCell(4).setCellValue(line.amount() == null ? "" : line.amount().toPlainString());
                    row.createCell(5).setCellValue(line.commissionPercent() == null ? "" : line.commissionPercent().toPlainString());
                    row.createCell(6).setCellValue(line.commissionAmount().toPlainString());
                }
                Row total = sheet.createRow(rowIndex++);
                total.createCell(0).setCellValue(group.clientName() + " total");
                total.createCell(4).setCellValue(group.totalAmount().toPlainString());
                total.createCell(6).setCellValue(group.totalCommission().toPlainString());
                total.getCell(0).setCellStyle(boldStyle);
            }
            Row grand = sheet.createRow(rowIndex + 1);
            grand.createCell(0).setCellValue("Grand total");
            grand.createCell(4).setCellValue(preview.totalAmount().toPlainString());
            grand.createCell(6).setCellValue(preview.totalCommission().toPlainString());
            grand.getCell(0).setCellStyle(boldStyle);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
