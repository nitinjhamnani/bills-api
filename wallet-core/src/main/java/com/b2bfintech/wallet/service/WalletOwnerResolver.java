package com.b2bfintech.wallet.service;

import com.b2bfintech.wallet.entity.WalletAccount;

/** Says whether a wallet owner id is a corporate or a vendor, so a new wallet records its type. */
public interface WalletOwnerResolver {
    WalletAccount.UserType typeOf(String walletUserId);
}
