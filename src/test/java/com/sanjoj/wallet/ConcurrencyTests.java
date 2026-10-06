package com.sanjoj.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sanjoj.wallet.account.Account;
import com.sanjoj.wallet.account.AccountService;
import com.sanjoj.wallet.common.WalletExceptions.InsufficientFundsException;
import com.sanjoj.wallet.transfer.TransferService;
import com.sanjoj.wallet.transfer.TransferService.TransferResult;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// These tests are the point of the project. Each one must FAIL if the protection it covers is removed
// Remove the protection and the matching test must fail.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConcurrencyTests {

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

    @Test
    void concurrentRandomTransfers_neverCreateOrDestroyMoney() throws Exception {
        List<UUID> ids = List.of(fundedAccount(50_000), fundedAccount(50_000), fundedAccount(50_000));

        List<Throwable> failures = TestHelpers.runConcurrently(200, 8, i -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int from = random.nextInt(3);
            int to = (from + 1 + random.nextInt(2)) % 3;
            long amount = 1 + random.nextLong(20_000);
            try {
                transferService.transfer(ids.get(from), ids.get(to), amount, UUID.randomUUID().toString());
            } catch (InsufficientFundsException expected) {
                // A legitimate outcome when the source ran low. Money must still be conserved.
            }
        });

        assertTrue(failures.isEmpty(), () -> "unexpected failures: " + failures);
        long total = 0;
        for (UUID id : ids) {
            long balance = balanceOf(id);
            assertTrue(balance >= 0, "balance went negative");
            assertEquals(TestHelpers.ledgerSum(jdbc, id), balance, "balance must equal the sum of its ledger entries");
            total += balance;
        }
        assertEquals(150_000L, total, "total money must not change");
    }

    @Test
    void opposingTransfers_doNotDeadlock() throws Exception {
        UUID a = fundedAccount(1_000_000);
        UUID b = fundedAccount(1_000_000);

        List<Throwable> failures = TestHelpers.runConcurrently(200, 8, i -> {
            if (i % 2 == 0) {
                transferService.transfer(a, b, 1, UUID.randomUUID().toString());
            } else {
                transferService.transfer(b, a, 1, UUID.randomUUID().toString());
            }
        });

        assertTrue(failures.isEmpty(), () -> "unexpected failures (deadlock?): " + failures);
        // 100 transfers each way, 1 cent each, so both balances end exactly where they started.
        assertEquals(1_000_000L, balanceOf(a));
        assertEquals(1_000_000L, balanceOf(b));
    }

    @Test
    void sameKeySentConcurrently_movesMoneyExactlyOnce() throws Exception {
        UUID a = fundedAccount(10_000);
        UUID b = fundedAccount(0);
        String key = UUID.randomUUID().toString();
        List<TransferResult> results = new CopyOnWriteArrayList<>();

        List<Throwable> failures = TestHelpers.runConcurrently(
                10, 8, i -> results.add(transferService.transfer(a, b, 100, key)));

        assertTrue(failures.isEmpty(), () -> "unexpected failures: " + failures);
        assertEquals(10, results.size(), "every request must get an answer");
        assertEquals(1, results.stream().filter(r -> !r.replayed()).count(), "exactly one request does the work");
        assertEquals(1, results.stream().map(r -> r.transfer().getId()).distinct().count(),
                "every request must see the same transfer");
        assertEquals(9_900L, balanceOf(a));
        assertEquals(100L, balanceOf(b));
    }
}
