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
    void alreadyPaidCodeIsNothingDue() throws Exception {
        BillingProvider.FetchedBill bill = BranchXMapper.bill(json.readTree("""
                {"status":"FAILED","statusCode":"500",
                 "message":"Bill Already paid, additional payment not allowed [BillAvenue: BFR004 - Payment received for the billing period - no bill due]",
                 "data":{"providerErrorCode":"BFR004",
                         "providerErrorMessage":"Payment received for the billing period - no bill due",
                         "providerName":"BillAvenue"}}
                """));
        assertEquals(BigDecimal.ZERO, bill.amount());
        assertNull(bill.providerFetchToken());
    }

    @Test
    void alreadyPaidWordingWithoutACodeIsNothingDue() throws Exception {
        BillingProvider.FetchedBill bill = BranchXMapper.bill(json.readTree("""
                {"status":"FAILED","statusCode":"500",
                 "message":"Bill Already paid, additional payment not allowed"}
                """));
        assertEquals(BigDecimal.ZERO, bill.amount());
        assertNull(bill.providerFetchToken());
    }

    @Test
    void otherFailedCodesStayFailures() throws Exception {
        assertRetryable("""
                {"status":"FAILED","statusCode":"500",
                 "message":"Biller could not locate this account / no bill found [BillAvenue: BFR008 - Unable to get bill details from the biller]",
                 "data":{"providerErrorCode":"BFR008","providerName":"BillAvenue"}}
                """);
        assertRetryable("""
                {"status":"FAILED","statusCode":"500",
                 "message":"Something Went Wrong, Please contact Tech Support [BillAvenue: BFR059 - Bill fetch attempts exceeded (maximum 3 attempts per day) for the consumer. Please try again tomorrow.]",
                 "data":{"providerErrorCode":"BFR059","providerName":"BillAvenue"}}
                """);
        assertRetryable("""
                {"status":"FAILED","statusCode":"500",
                 "message":"Something Went Wrong, Please contact Tech Support [BillAvenue: BVR013 - Service is not available]",
                 "data":{"providerErrorCode":"BVR013","providerName":"BillAvenue"}}
                """);
        assertRetryable("""
                {"status":"FAILED","statusCode":"500",
                 "message":"Something Went Wrong, Please contact Tech Support [BillAvenue: E204 - Request Id is already been used.]",
                 "data":{"providerErrorCode":"E204","providerName":"BillAvenue"}}
                """);
    }

    @Test
    void invalidAccountIsPermanent() throws Exception {
        BillFetchFailedException ex = failed("""
                {"status":"FAILED","statusCode":"500",
                 "message":"Incorrect / invalid Customer account number [BillAvenue: BFR001 - Incorrect / invalid Customer account]",
                 "data":{"providerErrorCode":"BFR001","providerName":"BillAvenue"}}
                """);
        assertTrue(ex.isPermanent());
        assertTrue(ex.getMessage().contains("BFR001"));
    }

    private void assertRetryable(String body) throws Exception {
        BillFetchFailedException ex = failed(body);
        assertFalse(ex.isPermanent());
        assertTrue(ex.getMessage().contains("BillAvenue"));
    }

    private BillFetchFailedException failed(String body) throws Exception {
        return assertThrows(BillFetchFailedException.class, () -> BranchXMapper.bill(json.readTree(body)));
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
    void paymentRequestKeepsTheFetchTokenAndOmitsPan() {
        String token = "opaque+token/with=padding==";
        var payload = BranchXMapper.paymentRequest(json, new BillingProvider.FetchedBillSettlement(
                new BigDecimal("2694.00"), "9999999999", " Nitin ", token));
        assertTrue(payload.get("amount").isNumber());
        assertEquals(new BigDecimal("2694.00"), payload.get("amount").decimalValue());
        assertEquals("9999999999", payload.get("userMobileNumber").asText());
        assertEquals("Nitin", payload.get("userName").asText());
        assertEquals(token, payload.get("billFetchBase64").asText());
        assertFalse(payload.has("panNumber"));
    }

    @Test
    void paymentRequestOmitsABlankName() {
        var payload = BranchXMapper.paymentRequest(json, new BillingProvider.FetchedBillSettlement(
                new BigDecimal("10"), "9999999999", "  ", "token"));
        assertFalse(payload.has("userName"));
        assertEquals("token", payload.get("billFetchBase64").asText());
    }

    @Test
    void payTokenIsReadUnchangedFromTheFetchBody() throws Exception {
        String token = "  keep-spaces  ";
        assertEquals(token, BranchXMapper.payToken(json.readTree("""
                {"status":"SUCCESS","data":{"billFetchBase64":"  keep-spaces  "}}
                """)));
        assertEquals("top-level-token", BranchXMapper.payToken(json.readTree("""
                {"billFetchBase64":"top-level-token"}
                """)));
    }

    @Test
    void paymentSuccessReadsAReferenceFromData() throws Exception {
        BillingProvider.SettlementResult result = BranchXMapper.paymentResult(200, json.readTree("""
                {"status":"SUCCESS","statusCode":"200","message":"Bill paid successfully","data":{
                  "receipt":{"txnId":"TXN123"},"other":"ignored"
                }}
                """));
        assertTrue(result.success());
        assertEquals("Bill paid successfully", result.message());
        assertEquals("TXN123", result.reference());
        assertTrue(result.data().contains("TXN123"));
    }

    @Test
    void paymentFailedBodyIsNotPaid() throws Exception {
        BillingProvider.SettlementResult result = BranchXMapper.paymentResult(200, json.readTree("""
                {"status":"FAILED","statusCode":"500","message":"Biller rejected the amount","data":null}
                """));
        assertFalse(result.success());
        assertEquals("Biller rejected the amount", result.message());
        assertNull(result.reference());
    }

    @Test
    void walletBalanceAcceptsSuccessWithANumericBalance() throws Exception {
        BillingProvider.WalletBalance wallet = BranchXMapper.walletBalance(json.readTree("""
                {"status":"SUCCESS","statusCode":"61","message":"Wallet Balance with caping","balance":46901.93}
                """));
        assertTrue(wallet.readable());
        assertEquals(new BigDecimal("46901.93"), wallet.balance().setScale(2, java.math.RoundingMode.HALF_UP));
        assertEquals("Wallet Balance with caping (61)", wallet.message());
    }

    @Test
    void walletBalanceKeepsAFailedProviderMessage() throws Exception {
        BillingProvider.WalletBalance wallet = BranchXMapper.walletBalance(json.readTree("""
                {"status":"FAILED","statusCode":"55","message":"Invalid ip 223.181.110.152"}
                """));
        assertFalse(wallet.readable());
        assertNull(wallet.balance());
        assertEquals("Invalid ip 223.181.110.152 (55)", wallet.message());
    }

    @Test
    void walletBalanceBlocksPendingAndAMissingAmount() throws Exception {
        BillingProvider.WalletBalance pending = BranchXMapper.walletBalance(json.readTree("""
                {"status":"PENDING","statusCode":"88","message":"Timeout error occurred"}
                """));
        assertFalse(pending.readable());
        assertEquals("Timeout error occurred (88)", pending.message());

        BillingProvider.WalletBalance missing = BranchXMapper.walletBalance(json.readTree("""
                {"status":"SUCCESS","statusCode":"61","message":"Wallet Balance with caping"}
                """));
        assertFalse(missing.readable());
        assertNull(missing.balance());

        BillingProvider.WalletBalance empty = BranchXMapper.walletBalance(null);
        assertFalse(empty.readable());
        assertEquals("The wallet balance could not be read", empty.message());
    }

    @Test
    void billerSideHttpFailureIsNotPaid() throws Exception {
        BillingProvider.SettlementResult result = BranchXMapper.paymentResult(500, json.readTree("""
                {"status":"SUCCESS","statusCode":"500","message":"Biller is unavailable","data":{"txnId":"SHOULD-NOT-COUNT"}}
                """));
        assertFalse(result.success());
        assertEquals("Biller is unavailable", result.message());
        assertNull(result.reference());
    }

    @Test
    void requestIdDropsSeparatorsAndStaysWithinThirtyFive() {
        assertEquals("BF9ad966e21731000000000", BranchXMapper.requestId("BF-9ad966e2-1731000000000"));
        assertEquals(35, BranchXMapper.requestId("A".repeat(40)).length());
    }
}
