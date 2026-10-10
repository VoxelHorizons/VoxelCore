# VaultUnlocked currencies (experimental)

This is an opt-in implementation of a VaultUnlocked **Vault2 economy provider** for the server.

## Configuration

In `plugins/VoxelCore/config.yml`:

```yaml
economy:
  enabled: true
  default-currency: coins
  currencies:
    cosmetics:
      decimals: 0
    gems:
      decimals: 0
```

Install VaultUnlocked and keep EssentialsX's existing Vault-compatible economy provider installed. Restart the server after enabling the integration. Coins continue to use the existing Essentials balance; VoxelCore stores `cosmetics` and `gems` balances in `plugins/VoxelCore/currency-balances.yml`.

## Usage from other plugins

Consumers must use the VaultUnlocked Vault2 economy API and provide the currency ID, e.g. `cosmetics`, not the original legacy Vault API. Check balances with the provider's `balance(pluginName, playerUuid, world, currency)`; credit and debit via `deposit` and `withdraw` with the same currency ID.

VaultUnlocked's PlaceholderAPI expansion supports `%vaultunlocked_balance_currency_cosmetics%` when a Vault2 economy is registered and the PlaceholderAPI expansion is available.

## Important limitations

- **Opt-in by default.** Do not enable on a production server until validated.
- VoxelCore refuses to override any existing Vault2 economy provider: it is not a universal multi-provider aggregator.
- `coins` uses the existing legacy Vault provider. All other currencies are persisted by VoxelCore. Do not delete `currency-balances.yml`.
- Shared accounts, inter-account transfers and asynchronous economy operations are **not yet supported**. Consumers should use balance/deposit/withdraw.
- Only integer or configured-decimal positive amounts are accepted; insufficient funds fail a withdrawal.
- This is an early integration. Validate service registration, balance persistence and transaction failure behavior on a staging Paper server before deployment.
- Currency storage and the legacy economy may have different durability/transaction semantics. No cross-currency atomic transfers are offered.
