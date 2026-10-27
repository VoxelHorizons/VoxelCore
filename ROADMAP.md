# Voxel Horizons — Authoritative Development Roadmap

> **Updated 27 October 2026 · initial VoxelCore code-audit baseline `1700f6d1` · live results separately attributed**
>
> **This is the only active VoxelCore development roadmap.** The [README](README.md) describes current APIs, [Bedrock operations](docs/bedrock.md) explains setup and validation, and the [tooltip](docs/configurable-tooltips.md) and [currency](docs/vaultunlocked-currencies.md) guides document configuration. None is a separate feature backlog. Previous planning/handoff files have been retired from the working tree and are available through Git history.

## Evidence and status rules

| Status | Interpretation |
| --- | --- |
| **Implemented** | Available on the relevant repository's default branch, **not** necessarily proved in-game |
| **Automated tested** | Build or focused tests passed for a specified revision; not proof of live Java/Bedrock parity |
| **Partial** | Meaningful implementation exists, but acceptance criteria remain |
| **Branch only** | Work is in an unmerged branch/PR, not a released default-branch feature |
| **Open** | Required implementation or evidence remains |
| **Verified** | Reproduced on pinned real server/client builds, with logs/screenshots and observable acceptance |
| **Deferred/cancelled** | Explicitly outside the current release commitment |

A merged PR alone is not a release certificate. Do not infer percentage complete from task counts. Recheck current HEAD and tests when updating any status.

## Implemented foundation and feature boundaries

| Area | Confirmed development baseline | Unfinished boundary |
| --- | --- | --- |
| **VoxelCore runtime** | Maven modules/distributions, immutable item definitions/inheritance, stable PDC content identities and render allocations, validated reload/rollback, multi-version adapters; [26.3 support #75](https://github.com/VoxelHorizons/VoxelCore/pull/75) | Cross-plugin live acceptance, old-world conversion and exact feature/version support |
| **Java items and blocks** | Item factory, core actions, custom block placement/mining/tool/drop rules, some native hardness, Creative pick-block | Rich legacy item/upgrade behavior, full bound-item policy, complete physics/protection matrix |
| **Java resource packs/UI** | Authored assets, atlas/JSON compatibility repair, UI glyphs and negative spacing, browser/chest/ShopGUIPlus integrations, tooltip assets, HTTP hosting, Java/Bedrock scoped build/cancel [#92](https://github.com/VoxelHorizons/VoxelCore/pull/92) | Real client visual corpus, unsupported assets, reload and performance acceptance |
| **Bedrock** | Initial native pack and v2 mappings [#84](https://github.com/VoxelHorizons/VoxelCore/pull/84); native icons, cuboid attachables, font and chest UI work, Geyser extension/projection components, remote sync code, later [PRs #85–#92](https://github.com/VoxelHorizons/VoxelCore/pulls?q=is%3Apr+is%3Amerged) | **Full real-client parity unverified**; see B01–B20 below |
| **Currency** | Independent VaultUnlocked Tokens and currency-aware commands [#67](https://github.com/VoxelHorizons/VoxelCore/pull/67) | Shop/payment integration and crash safety; **not** a replacement VoxelCore multi-currency ledger |
| **Furniture** | [VoxelFurniture](https://github.com/VoxelHorizons/VoxelFurniture) placement, rendering, variants, seats, storage and animations; Bedrock snapshot [#47](https://github.com/VoxelHorizons/VoxelFurniture/pull/47) merged | WorldGuard/Lands protection, restore/load benchmarks, Bedrock entity acceptance |
| **Vehicles** | [VoxelVehicles](https://github.com/VoxelHorizons/VoxelVehicles) modern land driving, persistent cargo/chests, dye; Bedrock snapshot [#21](https://github.com/VoxelHorizons/VoxelVehicles/pull/21) merged | Legacy input/render, trailers/seats, exact collision and Bedrock riding |
| **Showroom and commerce** | [VoxelFurnitureShop](https://github.com/VoxelHorizons/VoxelFurnitureShop) fixtures/variants; [VoxelShop](https://github.com/VoxelHorizons/VoxelShop) replacement in development | Restore/rollback and **durable actual BUY/SELL** transaction correctness |
| **Content editor** | [ContentManager](https://github.com/VoxelHorizons/ContentManager) native editor, generators and compiler bridge in progress | Complete graph/editors, staged publish, backup/recovery and release certification |

### Version boundary

Core smoke targets: **1.12.2, 1.13.2, 1.14.4, 1.19.4, 1.20.5, 1.21.4, 26.2, 26.3**. A VoxelCore smoke success does not prove VoxelFurniture, VoxelVehicles or Geyser compatibility on those versions. Current primary integration target: **Paper 26.3 / Java 25**. Folia is unsupported. See [Bedrock operations](docs/bedrock.md) for supported mapping floors; 1.12/1.13 legacy damage models are **not** supported by the Bedrock compiler.

## Release milestones, in priority order

### R1. Cross-plugin durability, gameplay and protection — **release blocker**

Owner: VoxelCore + Furniture + Vehicles + FurnitureShop.

- [x] **Owner-verified startup (27 October 2026):** VoxelCore, VoxelFurniture, VoxelVehicles and VoxelFurnitureShop are installed together and all load without startup errors after server restart. **Evidence:** server owner's direct in-game/server observation; exact JAR revisions and startup logs not supplied. This verifies startup only, not integration behavior.
- [x] **Owner-confirmed Paper build (27 October 2026):** `Paper 26.3-209-main@03af8b4` (built 2026-10-20T21:49:44Z; API `26.3.build.209-stable`), from the running server's version output.
- [ ] Record the Java runtime and four plugin build versions, confirm expected enable/load order and verify optional integrations degrade safely when absent.
- [ ] Verify WorldGuard/Lands controls for furniture/vehicle place, break, interact, storage, dye and driving; audit custom hitboxes and all bypass paths. Review open [Furniture PR #44](https://github.com/VoxelHorizons/VoxelFurniture/pull/44) and [Vehicles PR #19](https://github.com/VoxelHorizons/VoxelVehicles/pull/19).
- [ ] Place/use/remove custom items, blocks, furniture and vehicles; verify reload, chunk unload/load, world change, restart, orphan cleanup, failed placement rollback and ownership retention.
- [ ] Check vehicle chest accessories/cargo with real inventory metadata through every attach/remove, owner, logout, failed save and restart path. No loss or duplication.
- [ ] Test FurnitureShop capture/variant restore and edit-mode recovery; review [PR #12](https://github.com/VoxelHorizons/VoxelFurnitureShop/pull/12) for the CraftLegacy capture stall.
- [ ] Validate abnormal flows: bad content YAML, disabled integration, delayed pack build, protection changes, explosion/piston, partial disk save, crashing/rejoining players.
- [ ] Profile representative real content (100/1,000 placements, concurrent active vehicles, packed menus), recording MSPT, heap, GC, entity/packet counts and build time rather than guessed optimization percentages.

**Done when:** no known dupe/loss, unauthorized interaction, orphan state or unrecoverable data issue on the certified stack; preserve reproducible evidence.

### R2. Bedrock asset, entity, GUI and transport acceptance — **release blocker for advertised Bedrock features**

Owner: VoxelCore + Geyser extension + companions + frontend.

- [ ] Obtain the **current** `unsupported-items.txt` and `unsupported-geometry.txt` from production content; classify failure causes and add automated fixtures. Earlier 69-mapped/527-unsupported logs were from an old revision.
- [ ] Verify held icons/3D attachables, parent/model rules, face UV/culling, static and dynamic tint, animation and transform with paired Java/Bedrock screenshots.
- [ ] Verify Paper-owned display projections: furniture and vehicle models, multipart render, scale/rotation/seat offsets, dye, state swaps, chunk unload, reconnect, world change and despawn. Ensure one Geyser extension owns replacement and does not create gameplay state.
- [ ] Test packs and Geyser extension on local **and remote/proxy** deployment: signed bundle, digest/revision receipt, active loaded revision, remote feeds, network loss, replay rejection, rollback, restart and required pack accept/refuse.
- [ ] Test admin browser 1→2→3→2, close/reopen; validate the selected Bedrock frontend for real warps/profiles/settings/shops/NPCs, including player/NPC arguments, permissions, readable statistics and inventory safety.
- [ ] Record exact Paper/Java/plugin/Geyser/Floodgate/Bedrock versions, pack revision, logs and paired screenshots. Mark individual B01–B20 criteria **Verified** only when corresponding behavior was seen.

**Done when:** all Bedrock capabilities **advertised for launch** pass on pinned clients; explicitly disclose unsupported models/shaders/GUI modes.

### R3. Secure VoxelShop trades — **release blocker if replacing ShopGUIPlus**

Owner: [VoxelShop](https://github.com/VoxelHorizons/VoxelShop) / VaultUnlocked integration.

- [ ] Finish [issue #4](https://github.com/VoxelHorizons/VoxelShop/issues/4): authoritative quotes, actual BUY/SELL/SELL_ALL, metadata-aware inventory identity, anti-reentrancy and multi-step durable journal.
- [ ] Never retry an ambiguous VaultUnlocked payment without a recoverable provider outcome; simulate crash at every payment/item/stock phase, disconnect, double click, shift/drag and malicious client values.
- [ ] Review [PR #7](https://github.com/VoxelHorizons/VoxelShop/pull/7) after integration tests for enriched items, snapshot migrations and shop layouts.
- [ ] Verify prices, permissions, dynamic stock and currency rendering for **both** Java and Bedrock menus.

**Done when:** money, items and stock reconcile after failures without duplication or unpaid delivery; otherwise retain the existing proven shop for launch.

### R4. Menus, authoring and pack operations

- [ ] Define one route per live menu: Java DeluxeMenus plus Bedrock form/CrossplatForms. Preserve `<player>` and `<npc>` arguments, click requirements, placeholders, navigation and permission checks. The DeluxeMenus `feat/bedrock-forms` implementation is **branch only**, not considered released.
- [ ] Finish high-value [ContentManager issues #1–#9](https://github.com/VoxelHorizons/ContentManager/issues): reference graph, safe editor, furniture/vehicle/block metadata, glyphs and dependent assets, validated pack build, transactional publish/rollback. [PR #13](https://github.com/VoxelHorizons/ContentManager/pull/13) is open.
- [ ] Exercise `pack build java`, `pack build bedrock`, `pack build all`, `pack stop`, pack host, stale allocation migration, unsuccessful reload preservation and paired-publish integrity.
- [ ] Test config upgrades, dependency absence, client rendering on both editions and asset rollback.

**Done when:** an operator can edit/build/publish/recover without silent asset corruption or broken menu routing.

### R5. Release certification and documentation

- [ ] Pin production versions (Paper, Java, Geyser, Floodgate, VoxelCore and addons), configuration revisions and content pack hashes.
- [ ] Require passing distribution build, changed-code unit/integration tests and **26.3 smoke**. Record all historical-version smoke outcomes; do not hide or reclassify failures as success.
- [ ] Run a repeatable gameplay acceptance suite covering restart/chunk recovery, ownership, storage, economy, packaging, Java/Bedrock UI, remote/unavailable dependency, and rollback.
- [ ] Attach exact build SHA, commands, expected versus actual outcome, reviewer, logs/screenshots to tracked issues or release evidence.
- [ ] Publish a supported-feature/version matrix; avoid inferring all-version parity from a core startup check.

**Done when:** all release promises have traceable tests and no critical unresolved reliability, economy or protection defects.

## Bedrock work-package acceptance register (B01–B20)

IDs are intentionally preserved from the earlier Bedrock ledger to keep existing references usable. **This table replaces every former Bedrock roadmap/progress/handoff list.** Setup and acceptance procedure are in [docs/bedrock.md](docs/bedrock.md).

| ID | Required capability | Status on this baseline | Remaining acceptance |
| --- | --- | --- | --- |
| B01 | Deterministic pack and Geyser v2 item mappings | **Automated tested** | Real active mapping and representative content |
| B02 | Correct local/mounted Geyser destination | **Automated tested** | Actual target path and local deployment |
| B03 | Paired files, receipt, rollback and digest checks | **Implemented/tested components** | Geyser activation, interrupted publish, live rollback |
| B04 | Unmapped/unsupported reports | **Implemented** | Fresh production corpus and regression fixtures |
| B05 | Parent/model/predicate/variant Java conversion | **Partial** | Dynamic/unsupported model types and visual fidelity |
| B06 | Native geometry, attachables and UVs | **Partial** | Real held transforms, animations, UV/cull test |
| B07 | Block states, static/dynamic tint and animation | **Partial** | Native block variants, live tints and state parity |
| B08 | Companion model/variant registration | **Partial** | Runtime addon variants, shared identity and revision |
| B09 | Furniture visual state feed | **Implemented** ([#47 merged](https://github.com/VoxelHorizons/VoxelFurniture/pull/47)) | Real Geyser/client consumption and lifecycle |
| B10 | World furniture/custom-entity projection | **Partial** | Multipart, model swaps, nonuniform transforms, chunk/reconnect, dye |
| B11 | Vehicle projection, seats, movement and accessories | **Partial** ([#21 merged](https://github.com/VoxelHorizons/VoxelVehicles/pull/21)) | Real steering/seat/offset, cargo visuals and re-entry |
| B12 | Bedrock-safe browser pagination | **Implemented** | In-game page progression and no item loss |
| B13 | Managed glyph allocation and controller range | **Automated tested** | Native rendered icon and language/client parity |
| B14 | Unmanaged font/literal scan and rewrite | **Implemented** | Run on live configs; audit DB-backed ranks/tags |
| B15 | DeluxeMenus Bedrock forms | **Branch only** (`feat/bedrock-forms`) | Merge and verify args, conditions, safe clicks |
| B16 | Profiles, avatars, statistics and tooltip fallback | **Partial** | Full readable profile/forms, no shader assumption |
| B17 | Signed remote bundle and projection feeds | **Implemented/tested components** | Remote proxy/active revision/replay/failure test |
| B18 | Versioned backend and client boundary | **Partial** | Per-feature legacy support claims and test builds |
| B19 | End-to-end real Bedrock acceptance | **Open** | Pinned client/server, screenshot/log test corpus |
| B20 | Cross-plugin regression and release signoff | **Open** | B01–B19 evidence, support statement, no critical issues |

Geyser version/build capabilities must match the extension's experimental APIs. Java fonts/full-screen negative-spacing UI, Java-only shaders and arbitrary model rules are **not** automatically transferable to Bedrock.

## Known problems, release-gate exceptions and decisions

| Item | Current evidence and treatment |
| --- | --- |
| **CI-LEGACY-RCON** | VoxelCore `VOXELCORE_READY` appears on intermittent 1.13.2/1.14.4 RCON failures. At [current main smoke](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40280473024) the matrix was **7/8**: 1.14.4 failed, 26.2/26.3 passed. The attempted console fallback reported `Exec needs to be run with user ID 1000`. This exact infrastructure signature is **conditionally non-blocking** after recording the failed run. Never call 7/8 a pass; preserve assertions and investigate new failure signatures. [Current distribution build](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40280472998) passed. |
| **BEDROCK-LIVE** | Many native/projection features compile but still lack representative client proof. Treat them as implemented or partial, not Verified. |
| **STATE-SAFETY** | Furniture, vehicles, showroom inventory and shop payments have distinct persistent state owners. Certify failure/restart paths, not just happy-path startup. |
| **FRONTEND** | Bedrock DeluxeMenus form work is still on an unmerged branch; external CrossplatForms migration remains a separate repository concern. |

### Deferred feature families (not VoxelCore 1.0 blockers unless separately approved)

- **Historical HavenCore conversion:** actual legacy ID aliases/data import only if required by existing server content; no unconditional rewrite of old Haven runtime.
- **Advanced items:** rich potions/books/skulls/fireworks, binding restrictions, upgrades, recipes, cosmetics and tool behaviors beyond currently implemented actions.
- **Crops/plots:** HavenFarms-style seeds/growth/harvest, plot schematics/level XP and region controls. Evaluate existing plugins before duplicating functionality.
- **Skills and regeneration:** separate from already selected mcMMO/progression plugins unless differentiated gameplay is specified.
- **NPC relationships/quests:** beyond Citizens/DeluxeMenus basic interaction.
- **Games/lobbies:** custom minigame mechanics, Tag/bomb and inventory isolation.
- **Advanced vehicles/structures:** trailer chains, multi-seat flight, gates/drawbridges, old-version vehicle input; tracked separately in [Vehicles #2](https://github.com/VoxelHorizons/VoxelVehicles/issues/2), [#3](https://github.com/VoxelHorizons/VoxelVehicles/issues/3), [#4](https://github.com/VoxelHorizons/VoxelVehicles/issues/4).
- **Network exchange and shared inventories:** escrow/mailbox before any single-writer fenced multi-server inventory design; only if multi-server inventory transfer becomes a concrete requirement.
- **Folia:** unsupported; would require deliberate region-scheduling design.

**Cancelled:** VoxelCore-hosted SQLite multi-currency ledger/wallet ([PR #66 closed unmerged](https://github.com/VoxelHorizons/VoxelCore/pull/66), [issue #65 closed](https://github.com/VoxelHorizons/VoxelCore/issues/65)). Use VaultUnlocked and a compatible economy provider; implement safe actual transactions in VoxelShop instead.

## Maintenance

Update this document when significant work merges: record repository, PR/commit and new remaining acceptance evidence. Use GitHub issues for individual defects and external plugin roadmaps for addon-specific detail. **Do not recreate extra `ROADMAP.md`, `PROGRESS.md`, `HANDOFF.md`, `KNOWN-ISSUES.md`, or dated migration progress files in this repository.** Only this roadmap governs active status; retired historical snapshots remain accessible through Git history.
