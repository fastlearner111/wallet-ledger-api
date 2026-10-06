package com.sanjoj.wallet.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    // Hibernate generates the UUID when the entity is first saved.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_name", nullable = false, length = 100)
    private String ownerName;

    // Money is always whole cents. No floats anywhere.
    @Column(name = "balance_cents", nullable = false)
    private long balanceCents;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
        // Required by JPA.
    }

    public Account(String ownerName) {
        this.ownerName = ownerName;
        this.balanceCents = 0;
        this.createdAt = Instant.now();
    }

    // Callers must hold the row lock (AccountRepository.findByIdForUpdate) before changing a balance.
    // The "Exact" methods throw instead of silently overflowing.
    public void credit(long amountCents) {
        this.balanceCents = Math.addExact(this.balanceCents, amountCents);
    }

    public void debit(long amountCents) {
        this.balanceCents = Math.subtractExact(this.balanceCents, amountCents);
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public long getBalanceCents() {
        return balanceCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
