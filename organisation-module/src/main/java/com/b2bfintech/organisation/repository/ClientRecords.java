package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.CorpDetails;
import com.b2bfintech.organisation.entity.VendorDetails;

final class ClientRecords {

    private ClientRecords() {
    }

    static Client fromCorp(CorpDetails row) {
        Client client = new Client();
        client.setId(row.getCorpId());
        client.setClientName(row.getName());
        client.setContactPhone(row.getPhone());
        client.setEmail(row.getEmail());
        client.setAddress(row.getAddress());
        client.setState(row.getState());
        client.setLocation(row.getLocation());
        client.setClientType(Client.ClientType.CORPORATE);
        client.setCorporateKind(corporateKind(row.getCorpType()));
        client.setActive(row.isActive());
        client.setOnboardingStatus(row.getOnboardingStatus() == null
                ? "NOT_STARTED" : row.getOnboardingStatus().name());
        client.setAgreementStatus(row.getAgreementStatus() == null
                ? "NOT_STARTED" : row.getAgreementStatus().name());
        client.setAgreementSigned(row.isAgreementSigned());
        client.setOnboardedBy(row.getOnboardedBy() == null ? null : row.getOnboardedBy().name());
        client.setOnboardedById(row.getOnboardedById());
        return client;
    }

    static Client fromVendor(VendorDetails row) {
        Client client = new Client();
        client.setId(row.getVendorId());
        client.setClientName(row.getName());
        client.setContactPhone(row.getPhone());
        client.setEmail(row.getEmail());
        client.setAddress(row.getAddress());
        client.setState(row.getState());
        client.setLocation(row.getLocation());
        client.setClientType(Client.ClientType.VENDOR);
        client.setActive(row.isActive());
        client.setOnboardingStatus(row.getAgreementStatus() == CorpDetails.AgreementStatus.SIGNED
                ? "COMPLETE" : "NOT_STARTED");
        client.setAgreementStatus(row.getAgreementStatus() == null
                ? "NOT_STARTED" : row.getAgreementStatus().name());
        client.setAgreementSigned(row.isAgreementSigned());
        client.setOnboardedBy(row.getOnboardedBy() == null ? null : row.getOnboardedBy().name());
        client.setOnboardedById(row.getOnboardedById());
        return client;
    }

    static void copyToCorp(Client client, CorpDetails row) {
        row.setCorpId(client.getId());
        row.setName(client.getClientName());
        row.setPhone(client.getContactPhone());
        row.setEmail(client.getEmail());
        row.setAddress(client.getAddress());
        row.setState(client.getState());
        row.setLocation(client.getLocation());
        row.setCorpType(client.getCorporateKind() == null
                ? CorpDetails.CorpType.COMPANY
                : CorpDetails.CorpType.valueOf(client.getCorporateKind().name()));
        row.setActive(client.isActive());
        row.setOnboardingStatus(enumOr(client.getOnboardingStatus(), CorpDetails.OnboardingStatus.NOT_STARTED));
        row.setAgreementStatus(enumOr(client.getAgreementStatus(), CorpDetails.AgreementStatus.NOT_STARTED));
        row.setAgreementSigned(client.isAgreementSigned());
        row.setOnboardedBy(client.getOnboardedBy() == null ? null : CorpDetails.OnboardedBy.valueOf(client.getOnboardedBy()));
        row.setOnboardedById(client.getOnboardedById());
    }

    private static Client.CorporateKind corporateKind(CorpDetails.CorpType value) {
        if (value == null) {
            return null;
        }
        try {
            return Client.CorporateKind.valueOf(value.name());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static <E extends Enum<E>> E enumOr(String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Enum.valueOf(fallback.getDeclaringClass(), value);
    }

    static void copyToVendor(Client client, VendorDetails row) {
        row.setVendorId(client.getId());
        row.setName(client.getClientName());
        row.setPhone(client.getContactPhone());
        row.setEmail(client.getEmail());
        row.setAddress(client.getAddress());
        row.setState(client.getState());
        row.setLocation(client.getLocation());
        row.setActive(client.isActive());
        row.setAgreementStatus(enumOr(client.getAgreementStatus(), CorpDetails.AgreementStatus.NOT_STARTED));
        row.setAgreementSigned(client.isAgreementSigned());
        row.setOnboardedBy(client.getOnboardedBy() == null ? null : CorpDetails.OnboardedBy.valueOf(client.getOnboardedBy()));
        row.setOnboardedById(client.getOnboardedById());
    }
}
