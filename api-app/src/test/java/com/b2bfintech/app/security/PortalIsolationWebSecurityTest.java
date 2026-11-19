package com.b2bfintech.app.security;

import com.b2bfintech.app.controller.ManagedBillController;
import com.b2bfintech.app.controller.OrganisationTransactionsController;
import com.b2bfintech.app.controller.VendorCommissionController;
import com.b2bfintech.app.service.BbpsLookupService;
import com.b2bfintech.app.service.BillActivityLogService;
import com.b2bfintech.app.service.BranchService;
import com.b2bfintech.app.service.CategoryLabels;
import com.b2bfintech.app.service.ClientAccountGuard;
import com.b2bfintech.app.service.ManagedBillService;
import com.b2bfintech.app.service.VendorPaymentService;
import com.b2bfintech.app.service.VendorSettlementService;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.billing.repository.VendorPaymentRepository;
import com.b2bfintech.common.exception.GlobalExceptionHandler;
import com.b2bfintech.common.security.JwtService;
import com.b2bfintech.common.security.Portal;
import com.b2bfintech.common.security.SecurityConfig;
import com.b2bfintech.common.security.SessionValidator;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.BranchRepository;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.organisation.service.CategoryEnablementService;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.wallet.repository.PlatformTransactionRepository;
import com.b2bfintech.wallet.repository.WalletAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {
        PortalIsolationWebSecurityTest.TestConfig.class,
        ManagedBillController.class,
        VendorCommissionController.class,
        OrganisationTransactionsController.class,
        SecurityConfig.class,
        JwtService.class,
        GlobalExceptionHandler.class
})
class PortalIsolationWebSecurityTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private SessionValidator sessionValidator;

    @MockBean
    private ManagedBillService managedBillService;
    @MockBean
    private BillRepository billRepository;
    @MockBean
    private BillPaymentRepository billPaymentRepository;
    @MockBean
    private BillActivityLogService activityLogService;
    @MockBean
    private CategoryLabels categoryLabels;
    @MockBean
    private CategoryEnablementService categoryEnablementService;
    @MockBean
    private BbpsLookupService bbpsLookupService;
    @MockBean
    private BranchRepository branchRepository;
    @MockBean
    private BranchService branchService;
    @MockBean
    private VendorPaymentService vendorPaymentService;
    @MockBean
    private VendorSettlementService vendorSettlementService;
    @MockBean
    private ClientAccountGuard clientAccountGuard;
    @MockBean
    private ClientService clientService;
    @MockBean
    private VendorPaymentRepository vendorPaymentRepository;
    @MockBean
    private PlatformTransactionRepository platformTransactionRepository;
    @MockBean
    private WalletAccountRepository walletAccountRepository;
    @MockBean
    private ClientRepository clientRepository;

    @Test
    void vendorAdminCannotAccessCorporateBillsOrPaytrixEndpoints() throws Exception {
        String vendorToken = issueActiveToken("USV001", Portal.VENDOR, "VENDOR_ADMIN", "VENDOR_ADMIN", "VEND0001");

        Client vendor = new Client();
        vendor.setId("VEND0001");
        vendor.setClientName("Vendor One");
        vendor.setClientType(Client.ClientType.VENDOR);
        vendor.setStatus(Client.ClientStatus.ACTIVE);
        when(clientService.getById("VEND0001")).thenReturn(vendor);
        when(vendorPaymentService.assigned("VEND0001")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/client/assigned-bills")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/clients/CORP0001/managed-bills")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/clients/VEND0001/managed-bills")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/paytrix/transactions")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void corpAdminCannotAccessVendorEndpointsOtherCorporatesOrPaytrix() throws Exception {
        String corp1Token = issueActiveToken("USC001", Portal.CORPORATE, "CORP_ADMIN", "CORP_ADMIN", "CORP0001");

        when(categoryLabels.lookup()).thenReturn(k -> k);
        when(branchRepository.findAll()).thenReturn(List.of());
        when(billRepository.findByClientIdOrderByCreatedAtDesc("CORP0001")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/clients/CORP0001/managed-bills")
                        .header("Authorization", "Bearer " + corp1Token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/clients/CORP0002/managed-bills")
                        .header("Authorization", "Bearer " + corp1Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/client/assigned-bills")
                        .header("Authorization", "Bearer " + corp1Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/paytrix/transactions")
                        .header("Authorization", "Bearer " + corp1Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokedSessionTokenReturns401Immediately() throws Exception {
        UUID sid = UUID.randomUUID();
        String token = jwtService.generateAccessToken(new JwtService.TokenSubject(
                sid, "USC001", "9000000001", "USC001", Portal.CORPORATE,
                "CORP_ADMIN", "CORP_ADMIN", "Corp Admin", "CORP0001", null, null));
        when(sessionValidator.validateAndTouch(sid, "USC001", "CORP_ADMIN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/clients/CORP0001/managed-bills")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String issueActiveToken(String userId, Portal portal, String userType, String role, String clientId) {
        UUID sid = UUID.randomUUID();
        when(sessionValidator.validateAndTouch(sid, userId, userType))
                .thenReturn(Optional.of(new SessionValidator.ActiveSession(sid, userId, portal, userType, role, clientId)));
        return jwtService.generateAccessToken(new JwtService.TokenSubject(
                sid, userId, "9000000000", userId, portal, userType, role, "Test User", clientId, null, null));
    }
}
