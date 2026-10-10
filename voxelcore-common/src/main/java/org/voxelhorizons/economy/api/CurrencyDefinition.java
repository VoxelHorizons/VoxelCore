package org.voxelhorizons.economy.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** An immutable named currency with exact integer-minor-unit accounting. */
public final class CurrencyDefinition {
    private final String id;
    private final String name;
    private final int precision;
    private final long maximumMinor;

    public CurrencyDefinition(String id, String name, int precision, BigDecimal maximumBalance) {
        this.id = Objects.requireNonNull(id, "id").trim().toLowerCase(java.util.Locale.ROOT);
        this.name = Objects.requireNonNull(name, "name");
        if (!this.id.matches("[a-z0-9._-]+:[a-z0-9._-]+") || name.trim().isEmpty()
                || precision < 0 || precision > 6) {
            throw new IllegalArgumentException("Invalid currency definition");
        }
        this.precision = precision;
        this.maximumMinor = requireMinor(Objects.requireNonNull(maximumBalance, "maximumBalance"), true);
        if (maximumMinor <= 0) throw new IllegalArgumentException("Maximum balance must be positive");
    }

    private long requireMinor(BigDecimal amount, boolean allowZero) {
        Objects.requireNonNull(amount, "amount");
        long result = amount.setScale(precision, RoundingMode.UNNECESSARY)
                .movePointRight(precision).longValueExact();
        if (!allowZero && result == 0L) throw new IllegalArgumentException("Zero amount");
        return result;
    }

    /** Exact conversion; rejects fractional sub-units and values outside signed 64-bit range. */
    public long toMinor(BigDecimal amount) { return requireMinor(amount, true); }
    public BigDecimal fromMinor(long minor) { return BigDecimal.valueOf(minor, precision); }
    public String id() { return id; }
    public String name() { return name; }
    public int precision() { return precision; }
    public long maximumMinor() { return maximumMinor; }
}
