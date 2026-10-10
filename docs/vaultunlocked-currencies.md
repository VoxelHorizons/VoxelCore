# VaultUnlocked currencies

VoxelCore offers an **independent** Vault2 economy provider. It does not wrap Essentials Coins or migrate any Essentials balances. Enable the provider in `plugins/VoxelCore/config.yml`:

```yaml
economy:
  enabled: true
  currencies:
    tokens:
      decimals: 0
```

All balances are stored in `plugins/VoxelCore/currency-balances.yml`. Currency balances are UUID-keyed and use decimal strings internally. Tokens only accept whole-number transactions.

## Commands

Legacy Essentials commands continue to work without currency suffixes. To select VoxelCore Tokens explicitly:

- `/money balance tokens`, `/bal tokens`, `/balance tokens`
- `/money pay <player> <amount> tokens`, `/pay <player> <amount> tokens`
- `/money top tokens`, `/baltop tokens`

These currency-qualified commands are intercepted only for configured VoxelCore currency IDs. The legacy commands remain handled by Essentials.

## PlaceholderAPI

When the VaultUnlocked PAPI expansion sees an active modern Vault2 provider:

```
%vaultunlocked_balance_currency_tokens%
```

It reads the Vault2 balance. If the expansion returns Essentials Coins, confirm the VoxelCore service registered on startup; a missing modern provider makes VaultUnlocked fall back to its legacy provider. Check `/vault-info`: Legacy should be EssentialsX Economy and Modern should be VoxelCoreCurrencies. Check logs for "Registered VaultUnlocked currencies: [tokens]" or warnings about an existing Vault2 provider.

## Rollout cautions

- Opt-in, disabled by default in packaged config.
- If EssentialsX Unlocked registers Vault2, VoxelCore registers at Highest priority so VaultUnlocked selects Tokens for its modern provider. The EssentialsX Unlocked registration remains available but is not selected by the standard single-provider lookup.
- Tokens are independent from Essentials; no exchange or migration.
- On-disk saving occurs on balance mutations. Back up the balance file.
- Test on a staging Paper server first. This implementation does not provide atomic two-account transfers, shared accounts or async APIs.
