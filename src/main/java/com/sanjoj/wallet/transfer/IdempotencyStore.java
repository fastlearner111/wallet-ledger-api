package com.sanjoj.wallet.transfer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Talks to idempotency_keys with plain SQL. It runs inside the caller's transaction, because
// JdbcTemplate and JPA share the same connection while a Spring transaction is open.
@Component
public class IdempotencyStore {

    public record StoredKey(String requestFingerprint, UUID transferId) {
    }

    private final JdbcTemplate jdbc;

    public IdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Tries to claim the key. Returns true if this call inserted it, false if it already existed.
    // If another transaction is inserting the same key right now, PostgreSQL makes this statement wait
    // until that transaction commits or rolls back. That wait is what makes concurrent retries safe.
    public boolean claim(String key, String requestFingerprint) {
        int inserted = jdbc.update(
                "INSERT INTO idempotency_keys (idempotency_key, request_fingerprint) VALUES (?, ?) "
                        + "ON CONFLICT (idempotency_key) DO NOTHING",
                key, requestFingerprint);
        return inserted == 1;
    }

    public Optional<StoredKey> find(String key) {
        return jdbc.query(
                        "SELECT request_fingerprint, transfer_id FROM idempotency_keys WHERE idempotency_key = ?",
                        (rs, rowNum) -> new StoredKey(
                                rs.getString("request_fingerprint"),
                                rs.getObject("transfer_id", UUID.class)),
                        key)
                .stream()
                .findFirst();
    }

    public void attachTransfer(String key, UUID transferId) {
        jdbc.update("UPDATE idempotency_keys SET transfer_id = ? WHERE idempotency_key = ?", transferId, key);
    }
}
