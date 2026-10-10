# VoxelCore multi-currency economy (opt-in API)

This subsystem introduces exact, durable player-wallet accounting for multiple independent currencies, without changing existing Vault or ShopGUIPlus balances. It is **disabled by default** and does not migrate existing accounts automatically.

## Enable the service

In `plugins/VoxelCore/config.yml`:

```yaml
economy:
  enabled: true
  currencies:
    'voxel:coins':
      name: Coins
      precision: 2
      maximum-balance: '1000000000000'
    'voxel:cosmetics':
      name: Cosmetic Tokens
      precision: 0
      maximum-balance: '1000000000'
```

Restart to initialize. Wallets start with 0. The database is `plugins/VoxelCore/economy/ledger.sqlite`. **Back up this file and its `-wal`/`-shm` files together using a proper SQLite backup or a stopped server; don't delete it when updating plugins.** Do not change a currency's precision or maximum in-place once registered; the ledger refuses such changes to prevent misinterpreted existing balances.

SQLite writes use WAL, FULL synchronous durability, a transaction audit trail and exact signed 64-bit minor-unit balances. Invalid precision and negative balances are rejected. No floating-point amount storage is used.

## Administrator test commands

Once the economy is enabled and the server restarted, operators with
`voxelcore.admin.economy` may run:

```text
/vceconomy currencies
/vceconomy balance FlopsiNZ voxel:coins
/vceconomy credit FlopsiNZ voxel:coins 100.00
/vceconomy debit FlopsiNZ voxel:coins 10.00
/vceconomy transfer FlopsiNZ OtherOnlinePlayer voxel:coins 5.00
```

Player names must be online. For offline accounts, use the full UUID. Each successful
operation creates a random transaction ID and is recorded in the SQLite audit journal.
These commands are intentionally admin-only and **are not player-accessible payment commands**.

## Java integration

Depend on the matching `voxelcore-common` API and obtain the running service:

```java
RegisteredServiceProvider<EconomyService> provider =
    Bukkit.getServicesManager().getRegistration(EconomyService.class);
if (provider == null) {
    // Economy is disabled or VoxelCore is not installed; fail closed.
    return;
}
EconomyService economy = provider.getProvider();
UUID id = UUID.randomUUID(); // Retain and reuse the same ID during retries.
EconomyTransaction tx = EconomyTransaction.builder(id, "voxelshop")
    .transfer(buyerId, sellerId, "voxel:coins", new BigDecimal("42.75"))
    .build();
EconomyReceipt receipt = economy.execute(tx);
if (!receipt.successful()) {
    // Handle INSUFFICIENT_FUNDS or BALANCE_LIMIT.
}
```

Import `org.voxelhorizons.economy.api.*`, `org.bukkit.Bukkit`, `org.bukkit.plugin.RegisteredServiceProvider`, `java.math.BigDecimal`, and `java.util.UUID`.

An exchange between named currencies is **not** automatic. Both currency adjustments can be included in a single transaction, with conversion logic defined explicitly by the calling trusted plugin.

### Replay protection

A transaction is keyed by UUID. The ledger stores a hash of the normalized account/currency adjustments plus the origin. Identical replays return `ALREADY_APPLIED` without charging again. Reusing the same UUID with a different adjustment set throws an error. Rejected transactions (insufficient funds/balance limit) are not marked as applied and can be safely retried with new or existing IDs after the underlying constraint is resolved.

### Service availability

The optional service is registered using Bukkit `ServicesManager` while VoxelCore is enabled, unregistered and closed on shutdown. If the enabled database cannot initialize, **VoxelCore refuses to start**, rather than silently falling back to untracked in-memory balances. Changes to `economy.enabled` or currency definitions require restart; plugin content reloads do not modify the ledger service.

## Critical limitation: wallet atomicity is NOT item atomicity

Wallet entries and their audit records are atomic **within SQLite**. Minecraft inventory mutations and VoxelShop stock live outside that database. This API alone does **not** make a buy/sell operation crash-safe. A production VoxelShop trade needs a durable operation journal, replayable idempotent wallet step, reconciliation of delivered/removed items, stock revision revalidation and recovery after disconnect or server crash.

Do not simply debit a wallet and then give items without recovery logic, or credit a wallet before safely consuming sold items. Do not trust displayed client GUI prices, item names, NBT, stack sizes, or shop slot indexes. Verify authoritative catalogue definitions and player inventories at transaction time.

### Performance and trust

The service currently uses one serialized JDBC connection with durable synchronous commits; it is a **correctness-first ledger**, not yet a nonblocking bulk transaction executor. Never call it at high frequency on the server tick thread without performance validation. A future VoxelShop trade coordinator should run database steps in a bounded worker pool and switch back to the main thread for Bukkit inventory operations, with explicit state transitions. This is not yet included here.

All server-side plugins using Bukkit's service registry are trusted; the API does not authenticate external Java callers. Player-exposed credit/debit or administrative operations must have their own permission and authorization checks. A Vault compatibility adapter and player money commands are intentionally not enabled at this stage. Privileged balance/credit/debit/transfer test commands are available to administrators.

See [VoxelShop PR #1](https://github.com/VoxelHorizons/VoxelShop/pull/1) for the market simulation prototype and [issue #65](https://github.com/VoxelHorizons/VoxelCore/issues/65) for the remaining currency and recovery milestones.
