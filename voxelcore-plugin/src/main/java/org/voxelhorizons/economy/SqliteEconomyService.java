package org.voxelhorizons.economy;

import org.voxelhorizons.economy.api.CurrencyDefinition;
import org.voxelhorizons.economy.api.EconomyReceipt;
import org.voxelhorizons.economy.api.EconomyService;
import org.voxelhorizons.economy.api.EconomyTransaction;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Single-connection, serialized, SQLite-backed wallet ledger.
 * All wallet changes and audit entries commit together, or none commit.
 *
 * Callers must coordinate external inventory/market operations independently.
 * Do not invoke blocking disk I/O from high-frequency Bukkit event handlers.
 */
public final class SqliteEconomyService implements EconomyService, AutoCloseable {
    private final Map<String, CurrencyDefinition> currencies;
    private final Connection database;
    private boolean closed;

    public SqliteEconomyService(Path file, Collection<CurrencyDefinition> definitions) throws Exception {
        Objects.requireNonNull(file, "file");
        Map<String, CurrencyDefinition> configured = new LinkedHashMap<String, CurrencyDefinition>();
        for (CurrencyDefinition currency : Objects.requireNonNull(definitions, "definitions")) {
            if (configured.put(currency.id(), currency) != null)
                throw new IllegalArgumentException("Duplicate currency: " + currency.id());
        }
        if (configured.isEmpty()) throw new IllegalArgumentException("No currencies configured");
        this.currencies = Collections.unmodifiableMap(configured);
        Path absolute = file.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        // SQLite JDBC is bundled with the compiled VoxelCore distribution.
        Class.forName("org.sqlite.JDBC");
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
        try {
            this.database = connection;
            pragma("PRAGMA busy_timeout=5000");
            pragma("PRAGMA journal_mode=WAL");
            pragma("PRAGMA synchronous=FULL");
            pragma("PRAGMA foreign_keys=ON");
            createSchema();
        } catch (Exception exception) {
            connection.close();
            throw exception;
        }
    }

    private void pragma(String sql) throws SQLException {
        try (Statement statement = database.createStatement()) { statement.execute(sql); }
    }

    private void createSchema() throws SQLException {
        int version;
        try (Statement statement = database.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            version = result.next() ? result.getInt(1) : 0;
        }
        if (version > 1 || version < 0)
            throw new SQLException("Unsupported future economy database schema version: " + version);
        try (Statement statement = database.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS economy_balances ("
                    + "account_uuid TEXT NOT NULL, currency_id TEXT NOT NULL,"
                    + "amount_minor INTEGER NOT NULL CHECK(amount_minor >= 0),"
                    + "PRIMARY KEY(account_uuid, currency_id))");
            statement.execute("CREATE TABLE IF NOT EXISTS economy_transactions ("
                    + "id TEXT PRIMARY KEY, origin TEXT NOT NULL,"
                    + "fingerprint TEXT NOT NULL, created_at_ms INTEGER NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS economy_entries ("
                    + "transaction_id TEXT NOT NULL, entry_index INTEGER NOT NULL,"
                    + "account_uuid TEXT NOT NULL, currency_id TEXT NOT NULL,"
                    + "delta_minor INTEGER NOT NULL, balance_after_minor INTEGER NOT NULL,"
                    + "PRIMARY KEY(transaction_id, entry_index),"
                    + "FOREIGN KEY(transaction_id) REFERENCES economy_transactions(id))");
            if (version == 0) statement.execute("PRAGMA user_version=1");
        }
    }

    @Override public synchronized Collection<CurrencyDefinition> currencies() {
        return Collections.unmodifiableList(new ArrayList<CurrencyDefinition>(currencies.values()));
    }

    @Override public synchronized Optional<CurrencyDefinition> currency(String id) {
        return Optional.ofNullable(currencies.get(id == null ? "" : id.toLowerCase(Locale.ROOT)));
    }

    @Override public synchronized BigDecimal balance(UUID account, String currencyId) {
        requireOpen();
        CurrencyDefinition definition = requireCurrency(currencyId);
        try {
            return definition.fromMinor(readBalance(account, definition.id()));
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot read economy balance", exception);
        }
    }

    /**
     * Atomic for wallets and journal only. Repeating the same ID and identical
     * normalized request is safe, while ID reuse for another payload fails closed.
     */
    @Override public synchronized EconomyReceipt execute(EconomyTransaction transaction) {
        requireOpen();
        Objects.requireNonNull(transaction, "transaction");
        TreeMap<AccountKey, Long> changes = normalize(transaction);
        String fingerprint = fingerprint(transaction.origin(), changes);
        try {
            database.setAutoCommit(false);
            try {
                String previous = existingFingerprint(transaction.id());
                if (previous != null) {
                    if (!previous.equals(fingerprint))
                        throw new IllegalArgumentException("Transaction ID reused with different contents: " + transaction.id());
                    database.rollback();
                    return new EconomyReceipt(transaction.id(), EconomyReceipt.Status.ALREADY_APPLIED);
                }

                Map<AccountKey, Long> after = new LinkedHashMap<AccountKey, Long>();
                for (Map.Entry<AccountKey, Long> change : changes.entrySet()) {
                    AccountKey key = change.getKey();
                    long balance = readBalance(key.account, key.currency);
                    long next;
                    try {
                        next = Math.addExact(balance, change.getValue());
                    } catch (ArithmeticException overflow) {
                        database.rollback();
                        return new EconomyReceipt(transaction.id(), EconomyReceipt.Status.BALANCE_LIMIT);
                    }
                    if (next < 0L) {
                        database.rollback();
                        return new EconomyReceipt(transaction.id(), EconomyReceipt.Status.INSUFFICIENT_FUNDS);
                    }
                    if (next > requireCurrency(key.currency).maximumMinor()) {
                        database.rollback();
                        return new EconomyReceipt(transaction.id(), EconomyReceipt.Status.BALANCE_LIMIT);
                    }
                    after.put(key, next);
                }

                try (PreparedStatement insert = database.prepareStatement(
                        "INSERT INTO economy_transactions(id,origin,fingerprint,created_at_ms) VALUES (?,?,?,?)")) {
                    insert.setString(1, transaction.id().toString());
                    insert.setString(2, transaction.origin());
                    insert.setString(3, fingerprint);
                    insert.setLong(4, System.currentTimeMillis());
                    insert.executeUpdate();
                }

                int index = 0;
                for (Map.Entry<AccountKey, Long> result : after.entrySet()) {
                    AccountKey key = result.getKey();
                    long next = result.getValue();
                    try (PreparedStatement upsert = database.prepareStatement(
                            "INSERT INTO economy_balances(account_uuid,currency_id,amount_minor) VALUES(?,?,?)"
                            + " ON CONFLICT(account_uuid,currency_id) DO UPDATE SET amount_minor=excluded.amount_minor")) {
                        upsert.setString(1, key.account.toString());
                        upsert.setString(2, key.currency);
                        upsert.setLong(3, next);
                        upsert.executeUpdate();
                    }
                    try (PreparedStatement entry = database.prepareStatement(
                            "INSERT INTO economy_entries(transaction_id,entry_index,account_uuid,currency_id,"
                            + "delta_minor,balance_after_minor) VALUES (?,?,?,?,?,?)")) {
                        entry.setString(1, transaction.id().toString());
                        entry.setInt(2, index++);
                        entry.setString(3, key.account.toString());
                        entry.setString(4, key.currency);
                        entry.setLong(5, changes.get(key));
                        entry.setLong(6, next);
                        entry.executeUpdate();
                    }
                }

                database.commit();
                return new EconomyReceipt(transaction.id(), EconomyReceipt.Status.APPLIED);
            } catch (SQLException exception) {
                database.rollback();
                throw exception;
            } catch (RuntimeException exception) {
                database.rollback();
                throw exception;
            } finally {
                database.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Economy transaction failed; no successful commit acknowledged", exception);
        }
    }

    private TreeMap<AccountKey, Long> normalize(EconomyTransaction transaction) {
        TreeMap<AccountKey, Long> result = new TreeMap<AccountKey, Long>();
        for (EconomyTransaction.Entry entry : transaction.entries()) {
            CurrencyDefinition currency = requireCurrency(entry.currency());
            long amount = currency.toMinor(entry.change());
            if (amount == 0L) throw new IllegalArgumentException("Sub-unit amount or zero adjustment");
            AccountKey key = new AccountKey(entry.account(), currency.id());
            result.put(key, Math.addExact(result.containsKey(key) ? result.get(key) : 0L, amount));
        }
        // Remove canceling adjustments; prevent no-op transactions from disguising writes.
        result.values().removeIf(value -> value.longValue() == 0L);
        if (result.isEmpty()) throw new IllegalArgumentException("Empty net transaction");
        return result;
    }

    private static String fingerprint(String origin, TreeMap<AccountKey, Long> changes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(origin.getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<AccountKey, Long> entry : changes.entrySet()) {
                digest.update((byte) 0);
                digest.update(entry.getKey().account.toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(entry.getKey().currency.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Long.toString(entry.getValue()).getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) hex.append(String.format("%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private String existingFingerprint(UUID id) throws SQLException {
        try (PreparedStatement query = database.prepareStatement(
                "SELECT fingerprint FROM economy_transactions WHERE id=?")) {
            query.setString(1, id.toString());
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private long readBalance(UUID account, String currency) throws SQLException {
        Objects.requireNonNull(account, "account");
        try (PreparedStatement query = database.prepareStatement(
                "SELECT amount_minor FROM economy_balances WHERE account_uuid=? AND currency_id=?")) {
            query.setString(1, account.toString());
            query.setString(2, currency);
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private CurrencyDefinition requireCurrency(String id) {
        CurrencyDefinition result = currencies.get(Objects.requireNonNull(id, "currency").toLowerCase(Locale.ROOT));
        if (result == null) throw new IllegalArgumentException("Unknown currency: " + id);
        return result;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Economy service is closed");
    }

    @Override public synchronized void close() throws SQLException {
        if (!closed) {
            closed = true;
            database.close();
        }
    }

    private static final class AccountKey implements Comparable<AccountKey> {
        final UUID account;
        final String currency;
        AccountKey(UUID account, String currency) { this.account = account; this.currency = currency; }
        @Override public int compareTo(AccountKey other) {
            int currencyComparison = currency.compareTo(other.currency);
            return currencyComparison == 0 ? account.compareTo(other.account) : currencyComparison;
        }
    }
}
