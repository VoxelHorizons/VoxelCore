package org.voxelhorizons.economy.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One idempotent set of wallet adjustments. Only server-side trusted plugins
 * may originate credit/debit operations; a player command must not pass arbitrary deltas.
 */
public final class EconomyTransaction {
    private final UUID id;
    private final String origin;
    private final List<Entry> entries;

    private EconomyTransaction(UUID id, String origin, List<Entry> entries) {
        this.id = Objects.requireNonNull(id, "id");
        this.origin = Objects.requireNonNull(origin, "origin");
        if (!origin.matches("[a-zA-Z0-9._:-]{1,80}") || entries.isEmpty() || entries.size() > 128)
            throw new IllegalArgumentException("Invalid transaction origin or entry count");
        this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
    }

    public static Builder builder(UUID id, String origin) { return new Builder(id, origin); }
    public UUID id() { return id; }
    public String origin() { return origin; }
    public List<Entry> entries() { return entries; }

    public static final class Entry {
        private final UUID account;
        private final String currency;
        private final BigDecimal change;

        private Entry(UUID account, String currency, BigDecimal change) {
            this.account = Objects.requireNonNull(account, "account");
            this.currency = Objects.requireNonNull(currency, "currency");
            this.change = Objects.requireNonNull(change, "change");
            if (change.signum() == 0) throw new IllegalArgumentException("Zero adjustment");
        }

        public UUID account() { return account; }
        public String currency() { return currency; }
        public BigDecimal change() { return change; }
    }

    public static final class Builder {
        private final UUID id;
        private final String origin;
        private final List<Entry> entries = new ArrayList<Entry>();
        private Builder(UUID id, String origin) {
            this.id = id;
            this.origin = origin;
        }
        public Builder adjust(UUID account, String currency, BigDecimal change) {
            entries.add(new Entry(account, currency, change));
            return this;
        }
        /** Positive amounts only. */
        public Builder transfer(UUID sender, UUID recipient, String currency, BigDecimal amount) {
            if (Objects.requireNonNull(amount).signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
            return adjust(sender, currency, amount.negate()).adjust(recipient, currency, amount);
        }
        public EconomyTransaction build() { return new EconomyTransaction(id, origin, entries); }
    }
}
