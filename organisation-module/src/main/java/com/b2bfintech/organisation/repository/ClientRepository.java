package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.CorpDetails;
import com.b2bfintech.organisation.entity.VendorDetails;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Loads a corporate or a vendor as the client the rest of the application already uses. */
@Repository
public class ClientRepository {

    private final CorpDetailsRepository corps;
    private final VendorDetailsRepository vendors;

    public ClientRepository(CorpDetailsRepository corps, VendorDetailsRepository vendors) {
        this.corps = corps;
        this.vendors = vendors;
    }

    public Optional<Client> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return corps.findById(id).map(ClientRecords::fromCorp)
                .or(() -> vendors.findById(id).map(ClientRecords::fromVendor));
    }

    public List<Client> findAllById(java.util.Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().map(this::findById).flatMap(Optional::stream).toList();
    }

    public List<Client> findAll() {
        List<Client> all = new ArrayList<>();
        corps.findAll().forEach(row -> all.add(ClientRecords.fromCorp(row)));
        vendors.findAll().forEach(row -> all.add(ClientRecords.fromVendor(row)));
        all.sort(Comparator.comparing(client -> client.getClientName() == null ? "" : client.getClientName(),
                String.CASE_INSENSITIVE_ORDER));
        return all;
    }

    public Client save(Client client) {
        if (client.getClientType() == Client.ClientType.VENDOR) {
            VendorDetails row = vendors.findById(client.getId()).orElseGet(VendorDetails::new);
            ClientRecords.copyToVendor(client, row);
            vendors.save(row);
            return ClientRecords.fromVendor(row);
        }
        CorpDetails row = corps.findById(client.getId()).orElseGet(CorpDetails::new);
        ClientRecords.copyToCorp(client, row);
        corps.save(row);
        return ClientRecords.fromCorp(row);
    }

    public boolean existsByClientCode(String code) {
        return code != null && (corps.existsById(code) || vendors.existsById(code));
    }

    public boolean existsBySalesUserId(String salesUserId) {
        return salesUserId != null && corps.existsByOnboardedByAndOnboardedById(
                com.b2bfintech.organisation.entity.CorpDetails.OnboardedBy.ORG_SALES, salesUserId);
    }

    public List<Client> findBySalesUserIdOrderByClientNameAsc(String salesUserId) {
        return corps.findByOnboardedByAndOnboardedByIdOrderByNameAsc(
                com.b2bfintech.organisation.entity.CorpDetails.OnboardedBy.ORG_SALES, salesUserId).stream()
                .map(ClientRecords::fromCorp)
                .toList();
    }
}
