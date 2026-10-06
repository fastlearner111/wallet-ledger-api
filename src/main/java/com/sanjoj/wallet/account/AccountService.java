package com.sanjoj.wallet.account;

import com.sanjoj.wallet.common.WalletExceptions.AccountNotFoundException;
import com.sanjoj.wallet.ledger.LedgerEntry;
import com.sanjoj.wallet.ledger.LedgerEntryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;

    public AccountService(AccountRepository accounts, LedgerEntryRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Transactional
    public Account create(String ownerName) {
        return accounts.save(new Account(ownerName));
    }

    @Transactional(readOnly = true)
    public Account get(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    // A funding stub for testing. It is deliberately not idempotent (a documented limitation).
    @Transactional
    public LedgerEntry deposit(UUID accountId, long amountCents) {
        Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        account.credit(amountCents);
        return ledger.save(LedgerEntry.deposit(account.getId(), amountCents, account.getBalanceCents()));
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> history(UUID accountId, int page, int size) {
        get(accountId); // 404 if the account does not exist
        return ledger.findByAccountIdOrderBySeqDesc(accountId, PageRequest.of(page, size));
    }
}
