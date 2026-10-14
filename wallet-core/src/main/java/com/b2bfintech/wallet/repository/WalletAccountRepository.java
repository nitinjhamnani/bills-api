package com.b2bfintech.wallet.repository;

import com.b2bfintech.wallet.entity.WalletAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, UUID> {

    Optional<WalletAccount> findByClientIdAndWalletType(UUID clientId, WalletAccount.WalletType walletType);

    List<WalletAccount> findByWalletType(WalletAccount.WalletType walletType);

    /** Row-level lock so concurrent debits/credits against the same wallet can't race past the balance check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletAccount w where w.clientId = :clientId and w.walletType = :walletType")
    Optional<WalletAccount> findForUpdate(UUID clientId, WalletAccount.WalletType walletType);
}
