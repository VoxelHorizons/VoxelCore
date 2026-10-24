# Upstream Bedrock integrations — evaluated 24 October 2026

## Active decision
The **already implemented** VoxelCore extension uses Geyser API 2.11 experimental `GeyserDefineEntitiesEvent` and `ServerSpawnEntityEvent` to register models derived from the VoxelCore pack and replace only explicitly known Paper display UUIDs. Keep this implementation as the single integration owner during validation; do **not** install a second display replacement extension concurrently until conflicts have been tested.

## GeyserExtensionists
| Project | License / purpose | Adoption status |
| --- | --- | --- |
| [GeyserDisplayEntity](https://github.com/GeyserExtensionists/GeyserDisplayEntity) | **AGPL-3.0**; generic Java item-display support for Nexo, CraftEngine and ItemsAdder, model mapping/converter workflow | EVALUATED as reference; not included/copied/forked. Check 26.3 compatibility, geometry transform fidelity, and installation conflict with VoxelCore native custom entity definitions before selecting adapter. |
| [GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine) | MIT; ModelEngine-specific bridge | NOT ADOPTED: requiring ModelEngine in a system with native VoxelFurniture/VoxelVehicles would add a runtime dependency. |
| [GeyserUtils](https://github.com/GeyserExtensionists/GeyserUtils) | MIT utilities | NOT ADOPTED; no indispensable API extracted yet. |
| [GeyserModelEnginePackGenerator](https://github.com/GeyserExtensionists/GeyserModelEnginePackGenerator) | ModelEngine pack generation | NOT ADOPTED; reconsider as fixture reference only. |
| [GeyserModelEngineBlockbenchPacker](https://github.com/GeyserExtensionists/GeyserModelEngineBlockbenchPacker) | ModelEngine Blockbench asset pipeline | NOT ADOPTED; not a substitute for VoxelCore Java content converter. |
| [GeyserMoreRows](https://github.com/GeyserExtensionists/GeyserMoreRows) | Extended Bedrock inventory support | RESEARCH CANDIDATE for B15; not yet integrated with DeluxeMenus, arguments, or inventory safety. |

## Evidence and conditions to reconsider
- [GeyserDisplayEntity README](https://github.com/GeyserExtensionists/GeyserDisplayEntity/blob/main/README.md) describes generic third-party item-display support and explicitly states AGPL-3.0.
- [Geyser custom-entity API documentation](https://geysermc.org/wiki/geyser/custom-entities/) identifies 2.11 as the start of the experimental custom entity API; entity metadata and geometry remain version-sensitive.
- Before adopting upstream, compare real Bedrock model/transform/rotation/dye/animation/seat parity using a pinned extension version, inspect competing spawn substitutions, confirm licensing, and avoid dual mapping ownership.
- AGPL requirements need review before distributing a modified binary or incorporating AGPL code into another module. This repository has not copied AGPL code.
