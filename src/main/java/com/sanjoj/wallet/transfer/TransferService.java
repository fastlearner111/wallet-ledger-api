package com.sanjoj.wallet.transfer;

import com.sanjoj.wallet.account.Account;
import com.sanjoj.wallet.account.AccountRepository;
import com.sanjoj.wallet.common.WalletExceptions.AccountNotFoundException;
import com.sanjoj.wallet.common.WalletExceptions.IdempotencyKeyReusedException;
import com.sanjoj.wallet.common.WalletExceptions.InsufficientFundsException;
import com.sanjoj.wallet.common.WalletExceptions.InvalidRequestException;
import com.sanjoj.wallet.common.WalletExceptions.SameAccountException;
import com.sanjoj.wallet.common.WalletExceptions.TransferNotFoundException;
import com.sanjoj.wallet.ledger.LedgerEntry;
import com.sanjoj.wallet.ledger.LedgerEntryRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    // replayed is true when the Idempotency-Key was already used and the stored transfer is returned.
    public record TransferResult(Transfer transfer, boolean replayed) {
    }

    private static final int MAX_KEY_LENGTH = 255;

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerEntryRepository ledger;
    private final IdempotencyStore idempotency;

    public TransferService(
            AccountRepository accounts,
            TransferRepository transfers,
            LedgerEntryRepository ledger,
            IdempotencyStore idempotency) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.ledger = ledger;
        this.idempotency = idempotency;
    }

    // Everything below happens in ONE database transaction. If anything throws, everything rolls back,
    // including the idempotency key, so a failed transfer can be retried with the same key.
    // Note: @Transactional only works when this method is called from ANOTHER bean (the controller).
    @Transactional
    public TransferResult transfer(UUID fromId, UUID toId, long amountCents, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new InvalidRequestException(
                    "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1 to " + MAX_KEY_LENGTH + " characters");
        }
        if (fromId.equals(toId)) {
            throw new SameAccountException();
        }

        // Step 1: claim the key. If it already exists, this is a retry.
        String fingerprint = fingerprint(fromId, toId, amountCents);
        if (!idempotency.claim(idempotencyKey, fingerprint)) {
            return replay(idempotencyKey, fingerprint);
        }

        // Step 2: lock both accounts in a fixed order so two opposite transfers can never deadlock.
        // Any consistent order works (this is UUID.compareTo), it just has to be the same for everyone.
        UUID firstId = fromId.compareTo(toId) < 0 ? fromId : toId;
        UUID secondId = firstId.equals(fromId) ? toId : fromId;
        Account first = lock(firstId);
        Account second = lock(secondId);
        Account source = firstId.equals(fromId) ? first : second;
        Account target = firstId.equals(fromId) ? second : first;

        // Step 3: the balance check runs on the locked, current value.
        if (source.getBalanceCents() < amountCents) {
            throw new InsufficientFundsException(source.getId(), source.getBalanceCents(), amountCents);
        }

        // Step 4: move the money. Both changes and both ledger entries commit together or not at all.
        source.debit(amountCents);
        target.credit(amountCents);

        Transfer transfer = transfers.saveAndFlush(new Transfer(fromId, toId, amountCents));
        ledger.save(LedgerEntry.transferDebit(fromId, transfer.getId(), amountCents, source.getBalanceCents()));
        ledger.save(LedgerEntry.transferCredit(toId, transfer.getId(), amountCents, target.getBalanceCents()));

        idempotency.attachTransfer(idempotencyKey, transfer.getId());
        return new TransferResult(transfer, false);
    }

    @Transactional(readOnly = true)
    public Transfer get(UUID id) {
        return transfers.findById(id).orElseThrow(() -> new TransferNotFoundException(id));
    }

    private TransferResult replay(String idempotencyKey, String fingerprint) {
        IdempotencyStore.StoredKey stored = idempotency.find(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency key vanished: " + idempotencyKey));
        if (!stored.requestFingerprint().equals(fingerprint)) {
            throw new IdempotencyKeyReusedException(idempotencyKey);
        }
        if (stored.transferId() == null) {
            throw new IllegalStateException("Idempotency key has no transfer: " + idempotencyKey);
        }
        return new TransferResult(get(stored.transferId()), true);
    }

    private Account lock(UUID id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private static String fingerprint(UUID from, UUID to, long amountCents) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((from + "|" + to + "|" + amountCents).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
