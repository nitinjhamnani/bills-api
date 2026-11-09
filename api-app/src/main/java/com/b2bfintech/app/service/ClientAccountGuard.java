package com.b2bfintech.app.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.service.ClientService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Corporates create and pay bills, and vendors mark assigned bills paid, only after Paytrix has activated the account. */
@Service
public class ClientAccountGuard {

    private final ClientService clientService;

    public ClientAccountGuard(ClientService clientService) {
        this.clientService = clientService;
    }

    public Client requireActiveCorporate(UUID clientId) {
        Client client = clientService.getById(clientId);
        if (client.getClientType() != Client.ClientType.CORPORATE) {
            throw new BadRequestException("Only a corporate client can create or pay bills");
        }
        requireActive(client);
        return client;
    }

    public Client requireActive(UUID clientId) {
        Client client = clientService.getById(clientId);
        requireActive(client);
        return client;
    }

    /** Vendors settle bills outside the app. They do not hold a Paytrix wallet. */
    public void requireWalletClient(UUID clientId) {
        Client client = clientService.getById(clientId);
        if (client.getClientType() == Client.ClientType.VENDOR) {
            throw new BadRequestException("A vendor does not have a wallet");
        }
    }

    public Client requireActiveVendor(UUID clientId) {
        Client client = clientService.getById(clientId);
        if (client.getClientType() != Client.ClientType.VENDOR) {
            throw new BadRequestException("Only a vendor can do this");
        }
        requireActive(client);
        return client;
    }

    private static void requireActive(Client client) {
        if (client.getStatus() != Client.ClientStatus.ACTIVE) {
            throw new BadRequestException("This account is not active yet. Finish onboarding and wait for Paytrix to activate it.");
        }
    }
}
