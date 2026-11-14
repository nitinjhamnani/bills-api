package com.b2bfintech.app.service;

import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.service.WalletOwnerResolver;
import org.springframework.stereotype.Component;

@Component
public class ClientWalletOwnerResolver implements WalletOwnerResolver {

    private final ClientService clientService;

    public ClientWalletOwnerResolver(ClientService clientService) {
        this.clientService = clientService;
    }

    @Override
    public WalletAccount.UserType typeOf(String walletUserId) {
        return clientService.getById(walletUserId).getClientType() == Client.ClientType.VENDOR
                ? WalletAccount.UserType.VENDOR
                : WalletAccount.UserType.CORP;
    }
}
