package org.voxelhorizons.economy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.voxelhorizons.economy.api.CurrencyDefinition;
import org.voxelhorizons.economy.api.EconomyReceipt;
import org.voxelhorizons.economy.api.EconomyTransaction;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.*;

/** Persistence, rollback and replay tests use real temporary SQLite databases. */
public class SqliteEconomyServiceTest {
    private Path directory;
    private Path database;
    private SqliteEconomyService economy;
    private final UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID bob = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private SqliteEconomyService open() throws Exception {
        return new SqliteEconomyService(database, Arrays.asList(
                new CurrencyDefinition("voxel:coins", "Coins", 2, new BigDecimal("1000000.00")),
                new CurrencyDefinition("voxel:cosmetics", "Cosmetic Tokens", 0, new BigDecimal("1000"))));
    }

    @Before public void setUp() throws Exception {
        directory = Files.createTempDirectory("voxelcore-economy-test-");
        database = directory.resolve("ledger.sqlite");
        economy = open();
    }

    @After public void tearDown() throws Exception {
        if (economy != null) economy.close();
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (Exception ignored) { }
            });
        }
    }

    private EconomyTransaction add(UUID id, UUID account, String currency, String amount) {
        return EconomyTransaction.builder(id, "test").adjust(account, currency, new BigDecimal(amount)).build();
    }

    @Test public void persistsBalancesAndReplaysAcrossRestart() throws Exception {
        UUID id = UUID.randomUUID();
        EconomyTransaction transaction = add(id, alice, "voxel:coins", "15.25");
        assertEquals(EconomyReceipt.Status.APPLIED, economy.execute(transaction).status());
        assertEquals(EconomyReceipt.Status.ALREADY_APPLIED, economy.execute(transaction).status());
        economy.close();
        economy = open();
        assertEquals(new BigDecimal("15.25"), economy.balance(alice, "voxel:coins"));
        assertEquals(EconomyReceipt.Status.ALREADY_APPLIED, economy.execute(transaction).status());
        assertEquals(new BigDecimal("15.25"), economy.balance(alice, "voxel:coins"));
    }

    @Test public void rejectsIdempotencyKeyReuseWithDifferentAmount() {
        UUID id = UUID.randomUUID();
        economy.execute(add(id, alice, "voxel:coins", "5.00"));
        assertThrows(IllegalArgumentException.class,
                () -> economy.execute(add(id, alice, "voxel:coins", "10.00")));
        assertEquals(new BigDecimal("5.00"), economy.balance(alice, "voxel:coins"));
    }

    @Test public void transfersAtomicallyAndRejectsInsufficientFunds() {
        economy.execute(add(UUID.randomUUID(), alice, "voxel:coins", "10.00"));
        EconomyTransaction tooLarge = EconomyTransaction.builder(UUID.randomUUID(), "shop")
                .transfer(alice, bob, "voxel:coins", new BigDecimal("12.00")).build();
        assertEquals(EconomyReceipt.Status.INSUFFICIENT_FUNDS, economy.execute(tooLarge).status());
        assertEquals(new BigDecimal("10.00"), economy.balance(alice, "voxel:coins"));
        assertEquals(new BigDecimal("0.00"), economy.balance(bob, "voxel:coins"));
        // Failed transactions do not consume their idempotency IDs.
        EconomyTransaction valid = EconomyTransaction.builder(tooLarge.id(), "shop")
                .transfer(alice, bob, "voxel:coins", new BigDecimal("6.00")).build();
        assertEquals(EconomyReceipt.Status.APPLIED, economy.execute(valid).status());
        assertEquals(new BigDecimal("4.00"), economy.balance(alice, "voxel:coins"));
        assertEquals(new BigDecimal("6.00"), economy.balance(bob, "voxel:coins"));
    }

    @Test public void multipleCurrenciesAndEntriesCommitTogether() {
        economy.execute(add(UUID.randomUUID(), alice, "voxel:coins", "20.00"));
        economy.execute(add(UUID.randomUUID(), alice, "voxel:cosmetics", "4"));
        EconomyTransaction multi = EconomyTransaction.builder(UUID.randomUUID(), "shop")
                .adjust(alice, "voxel:coins", new BigDecimal("-5.00"))
                .adjust(bob, "voxel:coins", new BigDecimal("5.00"))
                .adjust(alice, "voxel:cosmetics", new BigDecimal("-1"))
                .adjust(bob, "voxel:cosmetics", new BigDecimal("1")).build();
        assertEquals(EconomyReceipt.Status.APPLIED, economy.execute(multi).status());
        assertEquals(new BigDecimal("15.00"), economy.balance(alice, "voxel:coins"));
        assertEquals(new BigDecimal("3"), economy.balance(alice, "voxel:cosmetics"));
        assertEquals(new BigDecimal("1"), economy.balance(bob, "voxel:cosmetics"));
    }

    @Test public void precisionAndMaximumAreEnforced() {
        assertThrows(ArithmeticException.class, () ->
                economy.execute(add(UUID.randomUUID(), alice, "voxel:cosmetics", "0.5")));
        assertEquals(EconomyReceipt.Status.BALANCE_LIMIT,
                economy.execute(add(UUID.randomUUID(), alice, "voxel:cosmetics", "1001")).status());
        assertEquals(new BigDecimal("0"), economy.balance(alice, "voxel:cosmetics"));
        assertThrows(IllegalArgumentException.class, () ->
                economy.execute(add(UUID.randomUUID(), alice, "unknown:currency", "1")));
    }

    @Test public void protectsStoredBalancesFromAccidentalCurrencyRedefinition() throws Exception {
        economy.execute(add(UUID.randomUUID(), alice, "voxel:coins", "10.50"));
        economy.close();
        economy = null;
        assertThrows(java.sql.SQLException.class, () -> new SqliteEconomyService(database, Arrays.asList(
                new CurrencyDefinition("voxel:coins", "Coins", 0, new BigDecimal("1000000")),
                new CurrencyDefinition("voxel:cosmetics", "Cosmetic Tokens", 0, new BigDecimal("1000")))));
        economy = open();
        assertEquals(new BigDecimal("10.50"), economy.balance(alice, "voxel:coins"));
    }

    @Test public void concurrentDepositsRemainSerializedAndExact() throws Exception {
        final int threads = 4;
        final int creditsPerThread = 25;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            java.util.List<java.util.concurrent.Future<?>> pending = new java.util.ArrayList<>();
            for (int t = 0; t < threads; t++) {
                pending.add(pool.submit(() -> {
                    for (int i = 0; i < creditsPerThread; i++) {
                        economy.execute(add(UUID.randomUUID(), alice, "voxel:coins", "0.01"));
                    }
                }));
            }
            for (java.util.concurrent.Future<?> task : pending) task.get();
            assertEquals(new BigDecimal("1.00"), economy.balance(alice, "voxel:coins"));
        } finally {
            pool.shutdownNow();
        }
    }
}
