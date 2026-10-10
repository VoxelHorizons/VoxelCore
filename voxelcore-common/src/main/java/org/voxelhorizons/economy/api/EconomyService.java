package org.voxelhorizons.economy.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Stable multi-currency API, exposed through Bukkit ServicesManager.
 *
 * Calls are synchronous and durable; execute() is atomic and idempotent for
 * wallet balances only. Item transfers/market stock are NOT part of this commit.
 * Plugins must not perform SQL work on hot-path/async Bukkit entity callbacks.
 */
public interface EconomyService {
    Collection<CurrencyDefinition> currencies();
    Optional<CurrencyDefinition> currency(String id);
    BigDecimal balance(UUID account, String currencyId);
    EconomyReceipt execute(EconomyTransaction transaction);
}
