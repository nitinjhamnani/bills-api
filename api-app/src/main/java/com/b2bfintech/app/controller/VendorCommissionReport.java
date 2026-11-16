package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.VendorSettlementService;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Excel report of vendor commission still to be collected for a period. */
final class VendorCommissionReport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);
    private static final String[] SUMMARY_HEADERS = {
            "Vendor", "Bills", "Corporates", "Bill amount paid", "Commission rate", "Commission due"
    };
    private static final String[] BILL_HEADERS = {
            "Vendor", "Corporate", "Biller", "Consumer number", "Paid on",
            "Bill amount", "Commission %", "Commission amount", "Payment id"
    };

    private VendorCommissionReport() {
    }

    static byte[] workbook(List<VendorSettlementService.CommissionBill> bills, LocalDate periodStart,
                           LocalDate periodEnd, String vendorLabel) throws IOException {
        List<VendorSettlementService.CommissionBill> rows = bills == null ? List.of() : bills.stream()
                .sorted(Comparator
                        .comparing((VendorSettlementService.CommissionBill bill) -> text(bill.vendorName()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(bill -> bill.paidAt() == null ? Instant.EPOCH : bill.paidAt())
                        .thenComparing(bill -> text(bill.corporateName()), String.CASE_INSENSITIVE_ORDER))
                .toList();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            writeSummary(workbook, styles, rows, periodStart, periodEnd, vendorLabel);
            writeBills(workbook, styles, rows);
            workbook.setActiveSheet(0);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static void writeSummary(XSSFWorkbook workbook, Styles styles,
                                     List<VendorSettlementService.CommissionBill> rows,
                                     LocalDate periodStart, LocalDate periodEnd, String vendorLabel) {
        XSSFSheet sheet = workbook.createSheet("Summary");
        sheet.setDisplayGridlines(false);
        sheet.createRow(0).setHeightInPoints(24);
        title(sheet, styles, 0, "Paytrix");
        title(sheet, styles, 1, "Vendor commission report");
        sheet.getRow(1).setHeightInPoints(28);

        labelValue(sheet, styles, 3, "Period", PERIOD.format(periodStart) + " – " + PERIOD.format(periodEnd));
        labelValue(sheet, styles, 4, "Vendor", vendorLabel == null || vendorLabel.isBlank() ? "All vendors" : vendorLabel);
        labelValue(sheet, styles, 5, "Generated", GENERATED.format(java.time.ZonedDateTime.now(ZONE)) + " IST");
        labelValue(sheet, styles, 6, "Status", "Commission still to be collected");

        int headerRow = 8;
        Row header = sheet.createRow(headerRow);
        header.setHeightInPoints(22);
        for (int column = 0; column < SUMMARY_HEADERS.length; column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(SUMMARY_HEADERS[column]);
            cell.setCellStyle(styles.header);
        }

        Map<String, VendorTotal> totals = new LinkedHashMap<>();
        for (VendorSettlementService.CommissionBill bill : rows) {
            String key = text(bill.vendorClientId());
            totals.computeIfAbsent(key, id -> new VendorTotal(text(bill.vendorName()).isBlank() ? "Unknown vendor" : bill.vendorName()))
                    .add(bill);
        }
        List<VendorTotal> vendors = new ArrayList<>(totals.values());
        vendors.sort(Comparator.comparing(total -> total.name, String.CASE_INSENSITIVE_ORDER));

        int rowIndex = headerRow + 1;
        BigDecimal grandAmount = BigDecimal.ZERO;
        BigDecimal grandCommission = BigDecimal.ZERO;
        int grandBills = 0;
        for (VendorTotal vendor : vendors) {
            Row row = sheet.createRow(rowIndex);
            boolean shaded = (rowIndex - headerRow) % 2 == 0;
            textCell(row, 0, vendor.name, shaded ? styles.textShaded : styles.text);
            numberCell(row, 1, vendor.bills, shaded ? styles.countShaded : styles.count);
            numberCell(row, 2, vendor.corporates.size(), shaded ? styles.countShaded : styles.count);
            moneyCell(row, 3, vendor.amount, shaded ? styles.moneyShaded : styles.money);
            rateCell(row, 4, rate(vendor.commission, vendor.amount), shaded ? styles.rateShaded : styles.rate);
            moneyCell(row, 5, vendor.commission, shaded ? styles.moneyShaded : styles.money);
            grandAmount = grandAmount.add(vendor.amount);
            grandCommission = grandCommission.add(vendor.commission);
            grandBills += vendor.bills;
            rowIndex++;
        }
        if (vendors.isEmpty()) {
            Row empty = sheet.createRow(rowIndex++);
            textCell(empty, 0, "No commission is due in this period.", styles.text);
            for (int column = 1; column < SUMMARY_HEADERS.length; column++) {
                textCell(empty, column, "", styles.text);
            }
        }
        Row total = sheet.createRow(rowIndex);
        total.setHeightInPoints(22);
        textCell(total, 0, "Total", styles.totalText);
        numberCell(total, 1, grandBills, styles.totalCount);
        numberCell(total, 2, rows.stream().map(bill -> text(bill.corporateName())).filter(name -> !name.isBlank()).distinct().count(), styles.totalCount);
        moneyCell(total, 3, grandAmount, styles.totalMoney);
        rateCell(total, 4, rate(grandCommission, grandAmount), styles.totalRate);
        moneyCell(total, 5, grandCommission, styles.totalMoney);

        int noteRow = rowIndex + 2;
        note(sheet, styles, noteRow, "Bill amount paid is what Paytrix has already paid the vendor.");
        note(sheet, styles, noteRow + 1, "Commission due is what the vendor still has to pay Paytrix.");
        note(sheet, styles, noteRow + 2, "Commission rate is the commission divided by the bill amount.");
        note(sheet, styles, noteRow + 3, "Paid on is the date Paytrix paid the vendor, in India time. Amounts are in Indian rupees.");

        int[] widths = {36, 12, 14, 22, 18, 20};
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
        sheet.setAutoFilter(new CellRangeAddress(headerRow, Math.max(headerRow, rowIndex - 1), 0, SUMMARY_HEADERS.length - 1));
        sheet.createFreezePane(0, headerRow + 1);
        sheet.setRepeatingRows(CellRangeAddress.valueOf((headerRow + 1) + ":" + (headerRow + 1)));
        preparePrint(sheet, SUMMARY_HEADERS.length);
    }

    private static void writeBills(XSSFWorkbook workbook, Styles styles, List<VendorSettlementService.CommissionBill> rows) {
        XSSFSheet sheet = workbook.createSheet("Bills");
        Row header = sheet.createRow(0);
        header.setHeightInPoints(22);
        for (int column = 0; column < BILL_HEADERS.length; column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(BILL_HEADERS[column]);
            cell.setCellStyle(styles.header);
        }
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        int rowIndex = 1;
        for (VendorSettlementService.CommissionBill bill : rows) {
            Row row = sheet.createRow(rowIndex);
            boolean shaded = rowIndex % 2 == 0;
            textCell(row, 0, text(bill.vendorName()), shaded ? styles.textShaded : styles.text);
            textCell(row, 1, text(bill.corporateName()), shaded ? styles.textShaded : styles.text);
            textCell(row, 2, text(bill.billerName()), shaded ? styles.textShaded : styles.text);
            textCell(row, 3, text(bill.consumerNumber()), shaded ? styles.textShaded : styles.text);
            dateCell(row, 4, bill.paidAt(), shaded ? styles.dateShaded : styles.date);
            moneyCell(row, 5, bill.amount(), shaded ? styles.moneyShaded : styles.money);
            rateCell(row, 6, bill.commissionPercent() == null ? 0 : bill.commissionPercent().doubleValue(), shaded ? styles.rateShaded : styles.rate);
            moneyCell(row, 7, bill.commissionAmount(), shaded ? styles.moneyShaded : styles.money);
            textCell(row, 8, bill.billPaymentId() == null ? "" : bill.billPaymentId().toString(), shaded ? styles.textShaded : styles.text);
            amount = amount.add(money(bill.amount()));
            commission = commission.add(money(bill.commissionAmount()));
            rowIndex++;
        }
        Row total = sheet.createRow(rowIndex);
        total.setHeightInPoints(22);
        textCell(total, 0, "Total", styles.totalText);
        for (int column = 1; column <= 4; column++) {
            textCell(total, column, "", styles.totalText);
        }
        moneyCell(total, 5, amount, styles.totalMoney);
        rateCell(total, 6, rate(commission, amount), styles.totalRate);
        moneyCell(total, 7, commission, styles.totalMoney);
        textCell(total, 8, rows.size() + (rows.size() == 1 ? " bill" : " bills"), styles.totalText);

        int[] widths = {28, 28, 42, 20, 16, 18, 16, 22, 40};
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
        if (!rows.isEmpty()) {
            sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, BILL_HEADERS.length - 1));
        }
        sheet.createFreezePane(0, 1);
        sheet.setRepeatingRows(CellRangeAddress.valueOf("1:1"));
        preparePrint(sheet, BILL_HEADERS.length);
    }

    private static void title(XSSFSheet sheet, Styles styles, int rowIndex, String value) {
        Row row = sheet.getRow(rowIndex) == null ? sheet.createRow(rowIndex) : sheet.getRow(rowIndex);
        Cell cell = row.createCell(0);
        cell.setCellValue(value);
        cell.setCellStyle(rowIndex == 0 ? styles.brand : styles.title);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, SUMMARY_HEADERS.length - 1));
    }

    private static void labelValue(XSSFSheet sheet, Styles styles, int rowIndex, String label, String value) {
        Row row = sheet.createRow(rowIndex);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.label);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(styles.value);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 1, 3));
    }

    private static void note(XSSFSheet sheet, Styles styles, int rowIndex, String value) {
        Row row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(0);
        cell.setCellValue(value);
        cell.setCellStyle(styles.note);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, SUMMARY_HEADERS.length - 1));
    }

    private static void textCell(Row row, int column, String value, XSSFCellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void numberCell(Row row, int column, long value, XSSFCellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void moneyCell(Row row, int column, BigDecimal value, XSSFCellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(money(value).doubleValue());
        cell.setCellStyle(style);
    }

    private static void rateCell(Row row, int column, double value, XSSFCellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void dateCell(Row row, int column, Instant instant, XSSFCellStyle style) {
        Cell cell = row.createCell(column);
        if (instant != null) {
            cell.setCellValue(instant.atZone(ZONE).toLocalDate());
        }
        cell.setCellStyle(style);
    }

    private static void preparePrint(XSSFSheet sheet, int columns) {
        sheet.setFitToPage(true);
        PrintSetup print = sheet.getPrintSetup();
        print.setLandscape(true);
        print.setFitWidth((short) 1);
        print.setFitHeight((short) 0);
        sheet.setHorizontallyCenter(true);
        sheet.setPrintGridlines(false);
        sheet.getFooter().setLeft("Paytrix · Vendor commission");
        sheet.getFooter().setRight("Page &P of &N");
        if (columns > 0) {
            sheet.setAutobreaks(true);
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static double rate(BigDecimal commission, BigDecimal amount) {
        if (amount == null || amount.signum() == 0 || commission == null) {
            return 0;
        }
        return commission.multiply(BigDecimal.valueOf(100)).divide(amount, 2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class VendorTotal {
        private final String name;
        private int bills;
        private final java.util.Set<String> corporates = new java.util.LinkedHashSet<>();
        private BigDecimal amount = BigDecimal.ZERO;
        private BigDecimal commission = BigDecimal.ZERO;

        private VendorTotal(String name) {
            this.name = name;
        }

        private void add(VendorSettlementService.CommissionBill bill) {
            bills++;
            String corporate = text(bill.corporateName());
            if (!corporate.isBlank()) {
                corporates.add(corporate);
            }
            amount = amount.add(money(bill.amount()));
            commission = commission.add(money(bill.commissionAmount()));
        }
    }

    private static final class Styles {
        private final XSSFCellStyle brand;
        private final XSSFCellStyle title;
        private final XSSFCellStyle label;
        private final XSSFCellStyle value;
        private final XSSFCellStyle note;
        private final XSSFCellStyle header;
        private final XSSFCellStyle text;
        private final XSSFCellStyle textShaded;
        private final XSSFCellStyle count;
        private final XSSFCellStyle countShaded;
        private final XSSFCellStyle money;
        private final XSSFCellStyle moneyShaded;
        private final XSSFCellStyle rate;
        private final XSSFCellStyle rateShaded;
        private final XSSFCellStyle date;
        private final XSSFCellStyle dateShaded;
        private final XSSFCellStyle totalText;
        private final XSSFCellStyle totalCount;
        private final XSSFCellStyle totalMoney;
        private final XSSFCellStyle totalRate;

        private Styles(XSSFWorkbook workbook) {
            XSSFColor navy = color(30, 41, 59);
            XSSFColor indigo = color(49, 46, 129);
            XSSFColor headerFill = color(30, 58, 95);
            XSSFColor shade = color(248, 250, 252);
            XSSFColor totalFill = color(238, 242, 255);
            XSSFColor line = color(226, 232, 240);
            XSSFColor muted = color(100, 116, 139);

            brand = fontStyle(workbook, 11, true, indigo, null, line, false);
            title = fontStyle(workbook, 18, true, navy, null, line, false);
            label = fontStyle(workbook, 11, true, muted, null, line, false);
            value = fontStyle(workbook, 11, false, navy, null, line, false);
            note = fontStyle(workbook, 10, false, muted, null, line, false);
            note.getFont().setItalic(true);

            header = fontStyle(workbook, 11, true, color(255, 255, 255), headerFill, headerFill, true);
            header.setAlignment(HorizontalAlignment.LEFT);

            text = fontStyle(workbook, 11, false, navy, null, line, true);
            textShaded = fontStyle(workbook, 11, false, navy, shade, line, true);
            count = copy(workbook, text);
            count.setAlignment(HorizontalAlignment.RIGHT);
            count.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            countShaded = copy(workbook, textShaded);
            countShaded.setAlignment(HorizontalAlignment.RIGHT);
            countShaded.setDataFormat(count.getDataFormat());
            money = copy(workbook, count);
            money.setDataFormat(workbook.createDataFormat().getFormat("₹#,##0.00"));
            moneyShaded = copy(workbook, countShaded);
            moneyShaded.setDataFormat(money.getDataFormat());
            rate = copy(workbook, count);
            rate.setDataFormat(workbook.createDataFormat().getFormat("0.00\"%\""));
            rateShaded = copy(workbook, countShaded);
            rateShaded.setDataFormat(rate.getDataFormat());
            date = copy(workbook, text);
            date.setDataFormat(workbook.createDataFormat().getFormat("dd mmm yyyy"));
            dateShaded = copy(workbook, textShaded);
            dateShaded.setDataFormat(date.getDataFormat());

            totalText = fontStyle(workbook, 11, true, navy, totalFill, indigo, true);
            totalCount = copy(workbook, totalText);
            totalCount.setAlignment(HorizontalAlignment.RIGHT);
            totalCount.setDataFormat(count.getDataFormat());
            totalMoney = copy(workbook, totalCount);
            totalMoney.setDataFormat(money.getDataFormat());
            totalRate = copy(workbook, totalCount);
            totalRate.setDataFormat(rate.getDataFormat());
        }

        private static XSSFCellStyle fontStyle(XSSFWorkbook workbook, int size, boolean bold, XSSFColor fontColor,
                                               XSSFColor fill, XSSFColor border, boolean boxed) {
            XSSFFont font = workbook.createFont();
            font.setFontName("Calibri");
            font.setFontHeightInPoints((short) size);
            font.setBold(bold);
            font.setColor(fontColor);
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            if (fill != null) {
                style.setFillForegroundColor(fill);
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (boxed) {
                style.setBorderTop(BorderStyle.THIN);
                style.setBorderBottom(BorderStyle.THIN);
                style.setBorderLeft(BorderStyle.THIN);
                style.setBorderRight(BorderStyle.THIN);
                style.setTopBorderColor(border);
                style.setBottomBorderColor(border);
                style.setLeftBorderColor(border);
                style.setRightBorderColor(border);
            }
            return style;
        }

        private static XSSFCellStyle copy(XSSFWorkbook workbook, XSSFCellStyle source) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.cloneStyleFrom(source);
            return style;
        }

        private static XSSFColor color(int red, int green, int blue) {
            return new XSSFColor(new byte[] {(byte) red, (byte) green, (byte) blue}, new DefaultIndexedColorMap());
        }
    }
}
