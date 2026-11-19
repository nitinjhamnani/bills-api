package com.b2bfintech.app.controller;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.ClientOnboarding;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.organisation.service.OnboardingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final ClientService clientService;

    public OnboardingController(OnboardingService onboardingService, ClientService clientService) {
        this.onboardingService = onboardingService;
        this.clientService = clientService;
    }

    @GetMapping("/api/v1/paytrix/onboarding")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
    public java.util.List<OnboardingRow> list() {
        return clientService.listAll().stream()
                .map(client -> OnboardingRow.from(client, onboardingService.get(client.getId())))
                .toList();
    }

    @PostMapping("/api/v1/paytrix/onboarding/{clientId}/kyc-complete")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public OnboardingRow markKyc(@PathVariable String clientId) {
        onboardingService.markKycComplete(clientId);
        return row(clientId);
    }

    @PostMapping("/api/v1/paytrix/onboarding/{clientId}/contract-signed")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public OnboardingRow markContract(@PathVariable String clientId) {
        onboardingService.markContractSigned(clientId);
        return row(clientId);
    }

    @PostMapping("/api/v1/paytrix/onboarding/{clientId}/activate")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public OnboardingRow activate(@PathVariable String clientId) {
        onboardingService.activate(clientId);
        return row(clientId);
    }

    @GetMapping("/api/v1/client/account")
    @PreAuthorize("hasAnyRole('CORP_ADMIN','CORP_OPERATOR','BRANCH_USER','VENDOR_ADMIN','VENDOR_OPERATOR')")
    public OnboardingRow mine(@AuthenticationPrincipal AuthenticatedUser principal) {
        return row(requiredClient(principal));
    }

    @PatchMapping("/api/v1/client/account")
    @PreAuthorize("hasAnyRole('CORP_ADMIN','VENDOR_ADMIN')")
    public OnboardingRow updateAccount(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateAccountRequest request) {
        String clientId = requiredClient(principal);
        clientService.updateAccountDetails(
                clientId,
                request.clientName(),
                request.corporateKind(),
                request.signingAuthority(),
                request.contactEmail(),
                request.contactPhone(),
                request.registrationNumber(),
                request.registeredAddress(),
                request.state(),
                request.location());
        return row(clientId);
    }

    @PostMapping("/api/v1/client/onboarding/kyc-complete")
    @PreAuthorize("hasAnyRole('CORP_ADMIN','VENDOR_ADMIN')")
    public OnboardingRow completeKyc(@AuthenticationPrincipal AuthenticatedUser principal) {
        String clientId = requiredClient(principal);
        onboardingService.markKycComplete(clientId);
        return row(clientId);
    }

    @PostMapping("/api/v1/client/onboarding/contract-signed")
    @PreAuthorize("hasAnyRole('CORP_ADMIN','VENDOR_ADMIN')")
    public OnboardingRow signContract(@AuthenticationPrincipal AuthenticatedUser principal) {
        String clientId = requiredClient(principal);
        onboardingService.markContractSigned(clientId);
        return row(clientId);
    }

    @PutMapping("/api/v1/client/onboarding")
    @PreAuthorize("hasAnyRole('CORP_ADMIN','VENDOR_ADMIN')")
    public OnboardingRow submit(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody DetailsRequest request) {
        String clientId = requiredClient(principal);
        onboardingService.submitDetails(
                clientId, request.legalName(), request.registrationNumber(), request.registeredAddress(), request.signingAuthority());
        return row(clientId);
    }

    private OnboardingRow row(String clientId) {
        return OnboardingRow.from(clientService.getById(clientId), onboardingService.get(clientId));
    }

    private static String requiredClient(AuthenticatedUser principal) {
        if (principal.clientId() == null || principal.clientId().isBlank()) {
            throw new BadRequestException("This login is not a client account");
        }
        return principal.clientId();
    }

    public record DetailsRequest(
            @Size(max = 200) String legalName,
            @Size(max = 100) String registrationNumber,
            @Size(max = 500) String registeredAddress,
            @Size(max = 200) String signingAuthority) {
    }

    public record UpdateAccountRequest(
            @NotBlank @Size(max = 200) String clientName,
            Client.CorporateKind corporateKind,
            @Size(max = 200) String signingAuthority,
            @Email @Size(max = 150) String contactEmail,
            @Pattern(regexp = "^$|\\d{10}", message = "Phone number must be 10 digits") String contactPhone,
            @Size(max = 100) String registrationNumber,
            @Size(max = 500) String registeredAddress,
            @Size(max = 100) String state,
            @Size(max = 150) String location) {
    }

    public record OnboardingRow(
            String clientId, String clientName, String clientCode, String clientType, String corporateKind, String status,
            String legalName, String registrationNumber, String registeredAddress, String signingAuthority,
            String contactEmail, String contactPhone, String state, String location,
            boolean detailsSubmitted, String kycStatus, String contractStatus) {

        static OnboardingRow from(Client client, ClientOnboarding onboarding) {
            return new OnboardingRow(
                    client.getId().toString(), client.getClientName(), client.getClientCode(),
                    client.getClientType().name(),
                    client.getCorporateKind() == null ? null : client.getCorporateKind().name(),
                    client.getStatus().name(),
                    onboarding.getLegalName(), onboarding.getRegistrationNumber(), onboarding.getRegisteredAddress(),
                    onboarding.getSigningAuthority(),
                    client.getEmail(), client.getContactPhone(), client.getState(), client.getLocation(),
                    onboarding.isDetailsSubmitted(),
                    onboarding.getKycStatus().name(), onboarding.getContractStatus().name());
        }
    }
}
