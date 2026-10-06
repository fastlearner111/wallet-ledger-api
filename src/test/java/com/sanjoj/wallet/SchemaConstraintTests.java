package com.sanjoj.wallet;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// Talks to the database directly, bypassing the application code, to prove the schema defends itself.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchemaConstraintTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void database_rejects_a_negative_balance() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO accounts (id, owner_name, balance_cents) VALUES (?, ?, ?)",
                UUID.randomUUID(), "Mallory", -1L));
    }
}
