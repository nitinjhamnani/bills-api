package com.b2bfintech.billing.provider.kbill;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.common.spi.bbps.BillParameterValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KBillContractTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void categoryPayloadIsNestedAndIdsStayText() throws Exception {
        JsonNode data = KBillEnvelopeReader.payload(load("category.json"), 200);
        List<BbpsProvider.ProviderCategory> categories = KBillMapper.categories(data);
        assertEquals(2, categories.size());
        assertEquals("8", categories.get(0).categoryId());
        assertEquals("Electricity", categories.get(0).name());
        assertTrue(categories.get(0).active());
        assertFalse(categories.get(1).active());
    }

    @Test
    void locationIdKeepsLeadingZero() throws Exception {
        JsonNode data = KBillEnvelopeReader.payload(load("location.json"), 200);
        List<BbpsProvider.ProviderLocation> locations = KBillMapper.locations(data);
        assertEquals("08", locations.get(0).locationId());
        assertEquals("10", locations.get(1).locationId());
    }

    @Test
    void operatorListFiltersByLocationAndTreatsZeroAsNationwide() throws Exception {
        JsonNode data = KBillEnvelopeReader.payload(load("operator-list.json"), 200);
        List<BbpsProvider.ProviderBiller> billers = KBillMapper.billers(data, "2");
        assertNull(billers.get(0).locationId());
        assertEquals("23", billers.get(1).locationId());
        List<BbpsProvider.ProviderBiller> rajasthan = KBillMapper.filterByLocation(billers, "08");
        assertEquals(2, rajasthan.size());
        assertTrue(rajasthan.stream().anyMatch(biller -> "51".equals(biller.billerId())));
        assertTrue(rajasthan.stream().anyMatch(biller -> "900".equals(biller.billerId())));
        assertTrue(rajasthan.stream().noneMatch(biller -> "158".equals(biller.billerId())));
    }

    @Test
    void blankBillerNameFallsBackToTheId() throws Exception {
        JsonNode data = KBillEnvelopeReader.payload(
                json.readTree("{\"status\":true,\"response_code\":\"01\",\"data\":[{\"operator_id\":367,\"name\":\"\",\"location_id\":8,\"operator_category\":8}]}"),
                200);
        List<BbpsProvider.ProviderBiller> billers = KBillMapper.billers(data, "8");
        assertEquals("Biller 367", billers.get(0).name());
    }

    @Test
    void categoryListIsUsedWhenTheLocationHasNoBillers() {
        var gujarat = new BbpsProvider.ProviderBiller("700", "Biller 700", "8", "24", true);
        var rajasthan = new BbpsProvider.ProviderBiller("367", "Biller 367", "8", "8", true);
        assertEquals(List.of(rajasthan), KBillBbpsProvider.chooseBillers(List.of(gujarat, rajasthan), "08"));
        assertEquals(List.of(gujarat, rajasthan), KBillBbpsProvider.chooseBillers(List.of(gujarat, rajasthan), "07"));
    }

    @Test
    void insuranceBillerDeclaresFourParameters() throws Exception {
        List<BbpsProvider.ProviderBillerParameter> parameters = parameters("parameters-insurance.json");
        assertEquals(4, parameters.size());
        assertEquals("utility_acc_no", parameters.get(0).name());
        assertEquals("birth_date", parameters.get(1).name());
        BillParameterValidator.ValidatedParameters valid = BillParameterValidator.validate(Map.of(
                "utility_acc_no", "56781234",
                "birth_date", "12/10/1987",
                "confirmation_mobile_no", "9098897654",
                "email", "kevin@abc.com"), parameters);
        assertEquals("56781234", valid.accountNumber());
        BadRequestException rejected = assertThrows(BadRequestException.class, () -> BillParameterValidator.validate(Map.of(
                "utility_acc_no", "12",
                "birth_date", "12/10/1987",
                "confirmation_mobile_no", "9098897654",
                "email", "kevin@abc.com"), parameters));
        assertEquals("Please Enter a Valid Policy Number (eg. 56781234)", rejected.getMessage());
    }

    @Test
    void educationAccountFieldIsUtilityAccNo2() throws Exception {
        List<BbpsProvider.ProviderBillerParameter> parameters = parameters("parameters-education.json");
        BillParameterValidator.ValidatedParameters valid = BillParameterValidator.validate(
                Map.of("utility_acc_no2", "123456"), parameters);
        assertEquals("123456", valid.accountNumber());
    }

    @Test
    void creditCardBillerWithNoParametersCannotBeFetched() throws Exception {
        List<BbpsProvider.ProviderBillerParameter> parameters = parameters("parameters-credit-card.json");
        assertThrows(BadRequestException.class, () -> BillParameterValidator.validate(Map.of(), parameters));
    }

    @Test
    void validationWithResponseCodeIsPermanent() throws Exception {
        KBillCallException ex = assertThrows(KBillCallException.class,
                () -> KBillEnvelopeReader.payload(load("validation-with-code.json"), 422));
        assertTrue(ex.isPermanent());
        assertEquals("05", ex.responseCode());
        assertTrue(ex.getMessage().contains("category_id"));
    }

    @Test
    void validationWithoutResponseCodeUsesHttpStatus() throws Exception {
        KBillCallException ex = assertThrows(KBillCallException.class,
                () -> KBillEnvelopeReader.payload(load("validation-without-code.json"), 422));
        assertTrue(ex.isPermanent());
        assertTrue(ex.getMessage().contains("operator_id"));
    }

    @Test
    void responseCode04IsTransientEvenOnHttp400() throws Exception {
        KBillCallException ex = assertThrows(KBillCallException.class,
                () -> KBillEnvelopeReader.payload(load("code-04.json"), 400));
        assertFalse(ex.isPermanent());
        assertEquals("04", ex.responseCode());
    }

    @Test
    void cloudflare1010IsTransient() throws Exception {
        KBillCallException ex = assertThrows(KBillCallException.class,
                () -> KBillEnvelopeReader.payload(load("cloudflare-1010.json"), 403));
        assertFalse(ex.isPermanent());
        assertTrue(ex.getMessage().contains("1010"));
    }

    @Test
    void queryLocationIdDropsLeadingZeros() {
        assertEquals("7", KBillBbpsProvider.queryLocationId("07"));
        assertEquals("29", KBillBbpsProvider.queryLocationId("29"));
        assertEquals("0", KBillBbpsProvider.queryLocationId("00"));
    }

    @Test
    void requestUriKeepsTheBasePathAndQuery() {
        var uri = KBillBbpsProvider.absolute(
                "https://development.kbillpay.com/api/kbill/v3/customer/bbps",
                UriComponentsBuilder.fromPath("/operator-list").queryParam("category_id", "8").queryParam("operator_location_id", "08"));
        assertEquals("https://development.kbillpay.com/api/kbill/v3/customer/bbps/operator-list?category_id=8&operator_location_id=08",
                uri.toString());
    }

    @Test
    void payloadsMaskTokenAndCustomerFields() {
        String token = "super-secret-token";
        String query = "utility_acc_no=56781234&mobile_no=9098897654&email=kevin@abc.com&birth_date=12/10/1987&sender_name=Ravi";
        String masked = KBillPayloadMasker.maskQuery(query, token);
        assertTrue(masked.contains("utility_acc_no=***1234"));
        assertTrue(masked.contains("mobile_no=***7654"));
        assertTrue(masked.contains("email=k***@abc.com"));
        assertTrue(masked.contains("birth_date=***"));
        assertTrue(masked.contains("sender_name=***"));
        assertFalse(masked.contains("56781234"));
        assertEquals("https://kbill.example?token=***", KBillPayloadMasker.scrubToken("https://kbill.example?token=" + token, token));
        assertFalse(KBillPayloadMasker.scrubToken("Bearer " + token, token).contains(token));
    }

    private List<BbpsProvider.ProviderBillerParameter> parameters(String fixture) throws Exception {
        return KBillMapper.parameters(KBillEnvelopeReader.payload(load(fixture), 200));
    }

    private JsonNode load(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/kbill/" + name)) {
            return json.readTree(in);
        }
    }
}
