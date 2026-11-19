package com.b2bfintech.app.controller;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Bulk-create spreadsheet. Category and service provider are dropdowns. The provider list
 * changes with the category selected on that row.
 */
final class ManagedBillBulkTemplate {

    private static final int LAST_DATA_ROW = 500;
    private static final String[] HEADERS = {"Category", "Service Provider", "Consumer ID / Number"};

    private ManagedBillBulkTemplate() {
    }

    static byte[] workbook(List<CategoryProviders> categories) throws IOException {
        List<CategoryProviders> rows = categories == null ? List.of() : categories;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bills");
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }
            sheet.setColumnWidth(0, 28 * 256);
            sheet.setColumnWidth(1, 46 * 256);
            sheet.setColumnWidth(2, 28 * 256);

            Sheet lists = workbook.createSheet("Lists");
            int categoryRows = Math.max(rows.size(), 1);
            for (int i = 0; i < categoryRows; i++) {
                Row row = lists.createRow(i);
                if (i < rows.size()) {
                    row.createCell(0).setCellValue(rows.get(i).category());
                    row.createCell(1).setCellValue("Providers" + (i + 1));
                }
            }
            for (int i = 0; i < rows.size(); i++) {
                List<String> providers = rows.get(i).providers();
                int height = Math.max(providers.size(), 1);
                int column = 3 + i;
                for (int r = 0; r < height; r++) {
                    Row row = lists.getRow(r);
                    if (row == null) {
                        row = lists.createRow(r);
                    }
                    if (r < providers.size()) {
                        row.createCell(column).setCellValue(providers.get(r));
                    }
                }
                Name range = workbook.createName();
                range.setNameName("Providers" + (i + 1));
                range.setRefersToFormula("Lists!$" + columnName(column) + "$1:$" + columnName(column) + "$" + height);
            }

            Name categoryRange = workbook.createName();
            categoryRange.setNameName("CategoryList");
            categoryRange.setRefersToFormula("Lists!$A$1:$A$" + categoryRows);
            Name categoryMap = workbook.createName();
            categoryMap.setNameName("CategoryMap");
            categoryMap.setRefersToFormula("Lists!$A$1:$B$" + categoryRows);
            workbook.setSheetHidden(workbook.getSheetIndex(lists), true);

            DataValidationHelper helper = sheet.getDataValidationHelper();
            if (!rows.isEmpty()) {
                DataValidation categoriesValidation = helper.createValidation(
                        helper.createFormulaListConstraint("CategoryList"),
                        new CellRangeAddressList(1, LAST_DATA_ROW, 0, 0));
                categoriesValidation.setShowErrorBox(true);
                categoriesValidation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                categoriesValidation.createErrorBox("Choose a category", "Select a category from the list.");
                categoriesValidation.setSuppressDropDownArrow(true);
                sheet.addValidationData(categoriesValidation);

                DataValidationConstraint providers = helper.createFormulaListConstraint(
                        "INDIRECT(VLOOKUP(A2,CategoryMap,2,FALSE))");
                DataValidation providersValidation = helper.createValidation(
                        providers, new CellRangeAddressList(1, LAST_DATA_ROW, 1, 1));
                providersValidation.setShowErrorBox(true);
                providersValidation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                providersValidation.createErrorBox(
                        "Choose a service provider", "Select a service provider for the category on this row.");
                providersValidation.setSuppressDropDownArrow(true);
                sheet.addValidationData(providersValidation);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static String columnName(int index) {
        StringBuilder name = new StringBuilder();
        int current = index;
        do {
            name.insert(0, (char) ('A' + (current % 26)));
            current = current / 26 - 1;
        } while (current >= 0);
        return name.toString();
    }

    record CategoryProviders(String category, List<String> providers) {
    }
}
