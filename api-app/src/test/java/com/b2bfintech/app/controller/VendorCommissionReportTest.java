package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.VendorSettlementService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VendorCommissionReportTest {

    @Test
    void totalsBillAmountAndCommission() throws Exception {
        List<VendorSettlementService.CommissionBill> bills = List.of(
                bill("Harbor Utilities", "Payvitt", "BESCOM", "4505756", "15.00", "1", "0.15"),
                bill("Harbor Utilities", "Northwind", "CSPDCL", "1000608438", "3300.00", "1", "33.00"));
        byte[] xlsx = VendorCommissionReport.workbook(
                bills, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 16), "Harbor Utilities");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet summary = workbook.getSheet("Summary");
            Row vendor = summary.getRow(9);
            assertEquals("Harbor Utilities", vendor.getCell(0).getStringCellValue());
            assertEquals(2, vendor.getCell(1).getNumericCellValue());
            assertEquals(2, vendor.getCell(2).getNumericCellValue());
            assertEquals(3315, vendor.getCell(3).getNumericCellValue(), 0.001);
            assertEquals(1, vendor.getCell(4).getNumericCellValue(), 0.001);
            assertEquals(33.15, vendor.getCell(5).getNumericCellValue(), 0.001);

            Row total = summary.getRow(10);
            assertEquals("Total", total.getCell(0).getStringCellValue());
            assertEquals(3315, total.getCell(3).getNumericCellValue(), 0.001);
            assertEquals(33.15, total.getCell(5).getNumericCellValue(), 0.001);

            Sheet detail = workbook.getSheet("Bills");
            assertEquals("Northwind", detail.getRow(1).getCell(1).getStringCellValue());
            assertEquals(3300, detail.getRow(1).getCell(5).getNumericCellValue(), 0.001);
            assertEquals("Payvitt", detail.getRow(2).getCell(1).getStringCellValue());
            assertEquals(15, detail.getRow(2).getCell(5).getNumericCellValue(), 0.001);
            assertEquals(3315, detail.getRow(3).getCell(5).getNumericCellValue(), 0.001);
            assertEquals(33.15, detail.getRow(3).getCell(7).getNumericCellValue(), 0.001);
        }
    }

    private static VendorSettlementService.CommissionBill bill(
            String vendor, String corporate, String biller, String consumer,
            String amount, String percent, String commission) {
        return new VendorSettlementService.CommissionBill(
                UUID.randomUUID(), "VDTOU243", vendor, corporate, consumer, biller,
                new BigDecimal(amount), new BigDecimal(percent), new BigDecimal(commission),
                Instant.parse("2026-11-16T08:59:00Z"));
    }
}
