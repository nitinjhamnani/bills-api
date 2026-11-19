package com.b2bfintech.app.controller;

import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedBillBulkTemplateTest {

    @Test
    void categoryAndProviderListsFollowTheSelectedCategory() throws Exception {
        byte[] xlsx = ManagedBillBulkTemplate.workbook(List.of(
                new ManagedBillBulkTemplate.CategoryProviders("Electricity", List.of("BESCOM", "CSPDCL")),
                new ManagedBillBulkTemplate.CategoryProviders("Gas", List.of("Indane"))));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet lists = workbook.getSheet("Lists");
            assertEquals("Electricity", lists.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Providers1", lists.getRow(0).getCell(1).getStringCellValue());
            assertEquals("BESCOM", lists.getRow(0).getCell(3).getStringCellValue());
            assertEquals("CSPDCL", lists.getRow(1).getCell(3).getStringCellValue());
            assertEquals("Gas", lists.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Indane", lists.getRow(0).getCell(4).getStringCellValue());
            assertTrue(workbook.isSheetHidden(workbook.getSheetIndex(lists)));

            Name providers = workbook.getName("Providers1");
            assertEquals("Lists!$D$1:$D$2", providers.getRefersToFormula());

            Sheet bills = workbook.getSheet("Bills");
            List<? extends DataValidation> validations = bills.getDataValidations();
            assertEquals(2, validations.size());
            assertEquals("CategoryList", validations.get(0).getValidationConstraint().getFormula1());
            assertEquals(
                    "INDIRECT(VLOOKUP(A2,CategoryMap,2,FALSE))",
                    validations.get(1).getValidationConstraint().getFormula1());
        }
    }
}
