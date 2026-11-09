package com.b2bfintech.billing.provider.branchx;

import com.b2bfintech.common.spi.BillFetchFailedException;
import com.b2bfintech.common.spi.BillingProvider;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.common.spi.bbps.BillParameterValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchXMapperTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void operatorsKeepTheBillerCodeAndSkipOtherCategories() throws Exception {
        var body = json.readTree("""
                {"status":"SUCCESS","statusCode":"200","data":[
                  {"name":"Tata Power - Delhi","category":"Electricity","externalReferenceNumber":"TATAPWR00DEL01","activeStatus":true},
                  {"name":"Airtel DTH","category":"DTH","externalReferenceNumber":"AIRT00000NAT87","activeStatus":true},
                  {"name":"Closed","category":"Electricity","externalReferenceNumber":"CLOSED","activeStatus":false}
                ]}
                """);
        List<BbpsProvider.ProviderBiller> billers = BranchXMapper.billers(body, "Electricity");
        assertEquals(1, billers.size());
        assertEquals("TATAPWR00DEL01", billers.get(0).billerId());
        assertEquals("Electricity", billers.get(0).categoryId());
    }

    @Test
    void parametersUseTheDeclaredNameAndRegex() throws Exception {
        var body = json.readTree("""
                {"status":"SUCCESS","data":{"billDetailsParams":[
                  {"name":"CA Number","paramName":"CA Number","dataType":"NUMERIC","minLength":"11","maxLength":"12","regex":"^[0-9]{11,12}$"}
                ]}}
                """);
        List<BbpsProvider.ProviderBillerParameter> parameters = BranchXMapper.parameters(body);
        assertEquals("CA Number", parameters.get(0).name());
        assertEquals("^[0-9]{11,12}$", parameters.get(0).validationRegex());
        BillParameterValidator.ValidatedParameters valid = BillParameterValidator.validate(
                Map.of("CA Number", "60024291092"), parameters);
        assertEquals("60024291092", valid.accountNumber());
    }

    @Test
    void fetchReadsAmountDueDateAndKeepsThePayToken() throws Exception {
        BillingProvider.FetchedBill bill = BranchXMapper.bill(json.readTree("""
                {"status":"SUCCESS","statusCode":"200","data":{
                  "billFetchBase64":"opaque-token",
                  "amountInfo":{"amount":"2694.00"},
                  "billerResponse":{
                    "customerName":"Nitin Jhamnani",
                    "billDueDate":"20261116",
                    "billDate":"null",
                    "billNumber":"BN1"
                  }
                }}
                """));
        assertEquals(new BigDecimal("2694.00"), bill.amount());
        assertEquals("2026-11-16", bill.dueDate());
        assertEquals("Nitin Jhamnani", bill.customerName());
        assertEquals("BN1", bill.billNumber());
        assertNull(bill.billDate());
        assertEquals("opaque-token", bill.providerFetchToken());
    }

    @Test
    void fetchReadsTheBillStoredInsideTheToken() throws Exception {
        String billJson = """
                {"LastPayedAmount":34700,"DueDate":"2026-11-17","BillDate":"2026-11-02T00:00:00",
                 "Billperiod":"Monthly","ConsumerReferenceName":"NA"}
                """;
        String token = Base64.getEncoder().encodeToString(billJson.getBytes(StandardCharsets.UTF_8));
        BillingProvider.FetchedBill bill = BranchXMapper.bill(json.readTree("""
                {"status":"SUCCESS","statusCode":"200","data":{"amountInfo":null,"billFetchBase64":"%s"}}
                """.formatted(token)));
        assertEquals(new BigDecimal("34700"), bill.amount());
        assertEquals("2026-11-17", bill.dueDate());
        assertEquals("2026-11-02", bill.billDate());
        assertEquals("Monthly", bill.billPeriod());
        assertNull(bill.customerName());
        assertEquals(token, bill.providerFetchToken());
    }

    @Test
    void inputUsesParamValue() {
        ArrayNode inputs = json.createArrayNode();
        BranchXMapper.addInput(inputs, "CA Number", "60024291092");
        assertEquals("CA Number", inputs.get(0).get("paramName").asText());
        assertEquals("60024291092", inputs.get(0).get("paramValue").asText());
        assertTrue(inputs.get(0).get("value") == null || inputs.get(0).get("value").isNull());
    }

    @Test
    void rejectedIpStaysRetryable() throws Exception {
        BillFetchFailedException ex = assertThrows(BillFetchFailedException.class, () -> BranchXMapper.bill(json.readTree("""
                {"statusCode":"500","title":"IP Addreess - Error","message":"Invalid IP 203.0.113.8","status":false}
                """)));
        assertFalse(ex.isPermanent());
        assertTrue(ex.getMessage().contains("203.0.113.8"));
    }

    @Test
    void requestIdDropsSeparatorsAndStaysWithinThirtyFive() {
        assertEquals("BF9ad966e21731000000000", BranchXMapper.requestId("BF-9ad966e2-1731000000000"));
        assertEquals(35, BranchXMapper.requestId("A".repeat(40)).length());
    }
}
