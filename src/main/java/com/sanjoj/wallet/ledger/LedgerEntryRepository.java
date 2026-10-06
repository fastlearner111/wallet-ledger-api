package com.sanjoj.wallet.ledger;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    // Newest first, using the database sequence so the order is strict.
    List<LedgerEntry> findByAccountIdOrderBySeqDesc(UUID accountId, Pageable pageable);
}
