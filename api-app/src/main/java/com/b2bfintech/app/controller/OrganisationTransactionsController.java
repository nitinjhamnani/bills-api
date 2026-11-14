package com.b2bfintech.app.controller;

import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.wallet.entity.PlatformTransaction;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.repository.PlatformTransactionRepository;
import com.b2bfintech.wallet.repository.WalletAccountRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Paytrix's own ledger: every movement between Paytrix and a corporate or a vendor. */
@RestController
@RequestMapping("/api/v1/paytrix/transactions")
@PreAuthorize("hasRole('ORGANISATION_ADMIN')")
public class OrganisationTransactionsController {

    private final PlatformTransactionRepository platformTransactionRepository;
    private final WalletAccountRepository walletAccountRepository;
    private final ClientRepository clientRepository;

    public OrganisationTransactionsController(
            PlatformTransactionRepository platformTransactionRepository,
            WalletAccountRepository walletAccountRepository,
            ClientRepository clientRepository) {
        this.platformTransactionRepository = platformTransactionRepository;
        this.walletAccountRepository = walletAccountRepository;
        this.clientRepository = clientRepository;
    }

    @GetMapping
    public List<OrganisationTransaction> list() {
        List<PlatformTransaction> rows = platformTransactionRepository.findAllByOrderByCreatedAtDesc();
        Map<UUID, WalletAccount> wallets = walletAccountRepository.findAllById(
                        rows.stream().map(PlatformTransaction::getWalletId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(WalletAccount::getId, Function.identity()));
        Map<String, Client> clients = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getId, Function.identity(), (left, right) -> left));
        return rows.stream().map(row -> toDto(row, wallets.get(row.getWalletId()), clients)).toList();
    }

    private static OrganisationTransaction toDto(
            PlatformTransaction row, WalletAccount wallet, Map<String, Client> clients) {
        String counterpartyId = wallet == null ? null : wallet.getWalletUserId();
        Client client = counterpartyId == null ? null : clients.get(counterpartyId);
        String counterpartyType = wallet == null ? null : wallet.getWalletUserType().name();
        return new OrganisationTransaction(
                row.getId(),
                row.getType().name(),
                row.getAmount(),
                row.getCategory(),
                describe(row.getCategory(), row.getType()),
                counterpartyId,
                client == null ? counterpartyId : client.getClientName(),
                counterpartyType,
                row.getCreatedAt());
    }

    private static String describe(String category, PlatformTransaction.Type type) {
        if ("RECHARGE".equals(category)) {
            return "Wallet recharge";
        }
        if ("BILL_PAID".equals(category)) {
            return "Paid the vendor";
        }
        if ("COMMISSION".equals(category)) {
            return "Commission received";
        }
        String label = category == null || category.isBlank()
                ? "Bill"
                : category.charAt(0) + category.substring(1).toLowerCase().replace('_', ' ');
        return type == PlatformTransaction.Type.CREDIT ? label + " bill payment" : label + " refund";
    }

    public record OrganisationTransaction(
            UUID id, String entryType, BigDecimal amount, String category, String description,
            String counterpartyId, String counterpartyName, String counterpartyType, Instant createdAt) {
    }
}
