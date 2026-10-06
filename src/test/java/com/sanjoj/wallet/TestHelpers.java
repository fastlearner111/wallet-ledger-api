package com.sanjoj.wallet;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;

final class TestHelpers {

    interface Task {
        void run(int index) throws Exception;
    }

    private TestHelpers() {
    }

    // Runs the task `tasks` times on `threads` threads. A start gate releases every thread at the same
    // moment, so the calls really do overlap. Returns every exception that escaped a task.
    static List<Throwable> runConcurrently(int tasks, int threads, Task task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < tasks; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    task.run(index);
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
        }
        startGate.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(120, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            failures.add(new IllegalStateException("Tasks did not finish within 120 seconds"));
        }
        return List.copyOf(failures);
    }

    // The sum of every ledger entry for an account. It must always equal the account's balance.
    static long ledgerSum(JdbcTemplate jdbc, UUID accountId) {
        Long sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(delta_cents), 0) FROM ledger_entries WHERE account_id = ?",
                Long.class, accountId);
        return sum == null ? 0 : sum;
    }

    static long ledgerCount(JdbcTemplate jdbc, UUID accountId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE account_id = ?", Long.class, accountId);
        return count == null ? 0 : count;
    }
}
