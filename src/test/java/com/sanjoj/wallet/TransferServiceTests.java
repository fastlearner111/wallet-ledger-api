package com.sanjoj.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sanjoj.wallet.account.Account;
import com.sanjoj.wallet.account.AccountService;
import com.sanjoj.wallet.common.WalletExceptions.AccountNotFoundException;
import com.sanjoj.wallet.common.WalletExceptions.IdempotencyKeyReusedException;
import com.sanjoj.wallet.common.WalletExceptions.InsufficientFundsException;
import com.sanjoj.wallet.common.WalletExceptions.InvalidRequestException;
import com.sanjoj.wallet.common.WalletExceptions.SameAccountException;
import com.sanjoj.wallet.transfer.TransferService;
import com.sanjoj.wallet.transfer.TransferService.TransferResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferServiceTests {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID fundedAccount(long cents) {
        Account account = accountService.create("test");
        if (cents > 0) {
            accountService.deposit(account.getId(), cents);
        }
        return account.getId();
    }

    private long balanceOf(UUID id) {
        return accountService.get(id).getBalanceCents();
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    @Test
    void transfer_movesMoney_andWritesOneLedgerEntryPerAccount() {
        UUID a = fundedAccount(1_000);
        UUID b = fundedAccount(0);

        TransferResult result = transferService.transfer(a, b, 300, newKey());

        assertFalse(result.replayed());
        assertEquals(700L, balanceOf(a));
        assertEquals(300L, balanceOf(b));
        assertEquals(700L, TestHelpers.ledgerSum(jdbc, a));
        assertEquals(300L, TestHelpers.ledgerSum(jdbc, b));
        assertEquals(2L, TestHelpers.ledgerCount(jdbc, a), "a deposit entry plus a debit entry");
        assertEquals(1L, TestHelpers.ledgerCount(jdbc, b), "a single credit entry");
    }

    @Test
    void sameKeyTwice_movesMoneyOnce_andReturnsTheSameTransfer() {
        UUID a = fundedAccount(1_000);
        UUID b = fundedAccount(0);
        String key = newKey();

        TransferResult first = transferService.transfer(a, b, 300, key);
        TransferResult second = transferService.transfer(a, b, 300, key);

        assertFalse(first.replayed());
        assertTrue(second.replayed());
        assertEquals(first.transfer().getId(), second.transfer().getId());
        assertEquals(700L, balanceOf(a), "money must move only once");
        assertEquals(300L, balanceOf(b));
    }

    @Test
    void sameKeyWithDifferentAmount_isRejected() {
        UUID a = fundedAccount(1_000);
        UUID b = fundedAccount(0);
        String key = newKey();
        transferService.transfer(a, b, 100, key);

        assertThrows(IdempotencyKeyReusedException.class, () -> transferService.transfer(a, b, 200, key));

        assertEquals(900L, balanceOf(a), "only the first request may move money");
    }

    @Test
    void insufficientFunds_changesNothing_andDoesNotBurnTheKey() {
        UUID a = fundedAccount(100);
        UUID b = fundedAccount(0);
        String key = newKey();

        assertThrows(InsufficientFundsException.class, () -> transferService.transfer(a, b, 500, key));

        assertEquals(100L, balanceOf(a));
        assertEquals(0L, balanceOf(b));
        assertEquals(1L, TestHelpers.ledgerCount(jdbc, a), "only the original deposit entry");
        assertEquals(0L, TestHelpers.ledgerCount(jdbc, b));

        // The failed attempt rolled back, including its idempotency key, so the same key works after funding.
        accountService.deposit(a, 900);
        TransferResult retry = transferService.transfer(a, b, 500, key);
        assertFalse(retry.replayed());
        assertEquals(500L, balanceOf(a));
        assertEquals(500L, balanceOf(b));
    }

    @Test
    void transferToTheSameAccount_isRejected() {
        UUID a = fundedAccount(100);

        assertThrows(SameAccountException.class, () -> transferService.transfer(a, a, 10, newKey()));
    }

    @Test
    void unknownAccount_isRejected_andRollsBackTheKey() {
        UUID a = fundedAccount(100);
        String key = newKey();

        assertThrows(AccountNotFoundException.class,
                () -> transferService.transfer(a, UUID.randomUUID(), 10, key));

        Long storedKeys = jdbc.queryForObject(
                "SELECT COUNT(*) FROM idempotency_keys WHERE idempotency_key = ?", Long.class, key);
        assertEquals(0L, storedKeys, "the claimed key must be rolled back with the failed transfer");
        assertEquals(100L, balanceOf(a));
    }

    @Test
    void blankIdempotencyKey_isRejected() {
        UUID a = fundedAccount(100);
        UUID b = fundedAccount(0);

        assertThrows(InvalidRequestException.class, () -> transferService.transfer(a, b, 10, "  "));
    }
}
