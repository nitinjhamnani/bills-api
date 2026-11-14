package com.b2bfintech.wallet.repository;

import com.b2bfintech.wallet.entity.WalletAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, UUID> {

    Optional<WalletAccount> findByWalletUserId(String walletUserId);

    /** Row-level lock so concurrent debits/credits against the same wallet can't race past the balance check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletAccount w where w.walletUserId = :walletUserId")
    Optional<WalletAccount> findForUpdate(@Param("walletUserId") String walletUserId);

    default Optional<WalletAccount> findByClientIdAndWalletType(String clientId, WalletAccount.WalletType walletType) {
        return findByWalletUserId(clientId);
    }

    default List<WalletAccount> findByWalletType(WalletAccount.WalletType walletType) {
        return findAll();
    }

    default Optional<WalletAccount> findForUpdate(String clientId, WalletAccount.WalletType walletType) {
        return findForUpdate(clientId);
    }
}
