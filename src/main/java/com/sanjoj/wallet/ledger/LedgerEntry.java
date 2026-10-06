package com.sanjoj.wallet.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

// Append-only: entries are inserted and never updated or deleted.
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    public enum EntryType {
        DEPOSIT,
        TRANSFER_DEBIT,
        TRANSFER_CREDIT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Filled in by the database (identity column). Used only to order history.
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "transfer_id", updatable = false)
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 20)
    private EntryType entryType;

    @Column(name = "delta_cents", nullable = false, updatable = false)
    private long deltaCents;

    @Column(name = "balance_after_cents", nullable = false, updatable = false)
    private long balanceAfterCents;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // Required by JPA.
    }

    private LedgerEntry(UUID accountId, UUID transferId, EntryType entryType, long deltaCents, long balanceAfterCents) {
        this.accountId = accountId;
        this.transferId = transferId;
        this.entryType = entryType;
        this.deltaCents = deltaCents;
        this.balanceAfterCents = balanceAfterCents;
        this.createdAt = Instant.now();
    }

    public static LedgerEntry deposit(UUID accountId, long amountCents, long balanceAfterCents) {
        return new LedgerEntry(accountId, null, EntryType.DEPOSIT, amountCents, balanceAfterCents);
    }

    public static LedgerEntry transferDebit(UUID accountId, UUID transferId, long amountCents, long balanceAfterCents) {
        return new LedgerEntry(accountId, transferId, EntryType.TRANSFER_DEBIT, -amountCents, balanceAfterCents);
    }

    public static LedgerEntry transferCredit(UUID accountId, UUID transferId, long amountCents, long balanceAfterCents) {
        return new LedgerEntry(accountId, transferId, EntryType.TRANSFER_CREDIT, amountCents, balanceAfterCents);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public long getDeltaCents() {
        return deltaCents;
    }

    public long getBalanceAfterCents() {
        return balanceAfterCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
