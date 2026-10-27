# Voxel Horizons — Authoritative Development Roadmap

> **Updated 27 October 2026 · initial VoxelCore code-audit baseline `1700f6d1` · 85-question production owner audit incorporated; owner reports remain distinct from certified builds**
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
| **Owner-confirmed** | Owner's direct live-server observation; not by itself a substitute for pinned addon/client build IDs, logs and repeatable acceptance evidence |
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
- [x] **Owner-confirmed Java runtime (27 October 2026):** Java 25 under the Pterodactyl image `ghcr.io/pterodactyl/yolks:java_25`. Java vendor, exact patch version and image digest not supplied.
- [ ] Record the four plugin build versions, confirm expected enable/load order and verify optional integrations degrade safely when absent.
- [ ] **Owner-confirmed Hub protection failures (27 October 2026):** ordinary non-OPs can place and break furniture, and spawn vehicles in the protected Hub. Custom WorldGuard flag UI reports allow and the owner cannot configure effective deny there; test direct `/rg flag` commands, priorities and actual region state before assigning a root cause. The Furniture JAR tested includes still-open [PR #44](https://github.com/VoxelHorizons/VoxelFurniture/pull/44); track [Furniture #49](https://github.com/VoxelHorizons/VoxelFurniture/issues/49). Vehicle protection [PR #19](https://github.com/VoxelHorizons/VoxelVehicles/pull/19) is also open; track [Vehicles #23](https://github.com/VoxelHorizons/VoxelVehicles/issues/23). Separately, non-owners can ride, drive, destroy unoccupied, attach chests to and withdraw from private vehicles; track [Vehicles #22](https://github.com/VoxelHorizons/VoxelVehicles/issues/22). Intended Hub behavior is furniture place/break DENY but seat/animate/interact ALLOW; storage currently shares allowed interaction policy. Test region and ownership gates separately.
- [ ] Place/use/remove custom items, blocks, furniture and vehicles; verify reload, chunk unload/load, world change, restart, orphan cleanup, failed placement rollback and ownership retention.
- [ ] Check vehicle chest accessories/cargo with real inventory metadata through every attach/remove, owner, logout, failed save and restart path. No loss or duplication.
- [ ] Test FurnitureShop capture/variant restore and edit-mode recovery; review [PR #12](https://github.com/VoxelHorizons/VoxelFurnitureShop/pull/12) for the CraftLegacy capture stall.
- [ ] Validate abnormal flows: bad content YAML, disabled integration, delayed pack build, protection changes, explosion/piston, partial disk save, crashing/rejoining players.
- [ ] Profile representative real content (100/1,000 placements, concurrent active vehicles, packed menus), recording MSPT, heap, GC, entity/packet counts and build time rather than guessed optimization percentages.

**Owner-confirmed functioning (27 Oct 2026):** furniture persists through chunk unload/reload and clean restarts including model, rotation, inventory and animation; vehicle positions, models, colors, attached chests and cargo persist through restart/chunk reload and Bedrock reconnect; normal break/drop tests returned items and contents once with no observed duplication. Rider disconnection and rejoin preserved the vehicle, cargo and usability. FurnitureShop variant switching, scheduled rotation, Java/Bedrock popup availability, showroom state/edit protection and normal restart persistence work; showroom currently has **no purchasing implementation**. Multiworld movement and plugin upgrades preserved data.

**P0 Bedrock furniture interaction:** a non-OP Bedrock Survival player right-clicking a bamboo cupboard bench may drop the furniture item or break it unexpectedly; sometimes a ghost remains through chunk reload **and restart**. Cherry drawer bench and Java interactions were unaffected in compared tests. Stored items were recoverable in tested cases; furniture duplication has **not** been confirmed. [Furniture #48](https://github.com/VoxelHorizons/VoxelFurniture/issues/48).

**Done when:** no known dupe/loss, unauthorized interaction, orphan state or unrecoverable data issue on the certified stack; preserve reproducible evidence.

### R2. Bedrock asset, entity, GUI and transport acceptance — **release blocker for advertised Bedrock features**

Owner: VoxelCore + Geyser extension + companions + frontend.

- [ ] Obtain the **current** `unsupported-items.txt` and `unsupported-geometry.txt` from production content; classify failure causes and add automated fixtures. Earlier 69-mapped/527-unsupported logs were from an old revision.
- [ ] Verify held icons/3D attachables, parent/model rules, face UV/culling, static and dynamic tint, animation and transform with paired Java/Bedrock screenshots.
- [ ] Verify Paper-owned display projections: furniture and vehicle models, multipart render, scale/rotation/seat offsets, dye, state swaps, chunk unload, reconnect, world change and despawn. **Known Bedrock owner-reported defect (27 Oct 2026):** furniture unexpectedly rotates/spins when spawned and when switching animated models. This is NOT client-verified as correct. Ensure one Geyser extension owns replacement and does not create gameplay state.
- [ ] Test packs and Geyser extension on local **and remote/proxy** deployment: signed bundle, digest/revision receipt, active loaded revision, remote feeds, network loss, replay rejection, rollback, restart and required pack accept/refuse.
- [ ] Test browser pagination, close/reopen and live warps/profiles/settings/shops/NPCs on the selected **VoxelCore ChestGUI** frontend. Owner-confirmed self/other-player and Citizens NPC profiles show target data on Java and Bedrock, though the Java shader-based portrait is unsupported on Bedrock. Some Bedrock page/menu transitions open then immediately close ([Core #97](https://github.com/VoxelHorizons/VoxelCore/issues/97)); inventory exploit testing is outstanding.
- [ ] Record exact Paper/Java/plugin/Geyser/Floodgate/Bedrock versions, pack revision, logs and paired screenshots. Mark individual B01–B20 criteria **Verified** only when corresponding behavior was seen.

**Owner-confirmed Bedrock functioning:** updated packs are delivered through Geyser and loaded after Geyser restart and client reconnect; item/browser icons and held 3D geometry appear; furniture, seats, storage, vehicle driving/steering/dismount, chest inventory access, vehicle/furniture reappearance and FurnitureShop variant changes were exercised. **Unresolved:** custom block models appear as vanilla carrier blocks ([Core #94](https://github.com/VoxelHorizons/VoxelCore/issues/94)); attached tractor chest model is invisible ([Vehicles #24](https://github.com/VoxelHorizons/VoxelVehicles/issues/24)); furniture rotates/spins at spawn or model swap ([Furniture #46](https://github.com/VoxelHorizons/VoxelFurniture/issues/46)); selected glyphs/3D texture atlases lack fidelity ([Core #98](https://github.com/VoxelHorizons/VoxelCore/issues/98)). Bedrock pack-refusal enforcement and remote/proxy publication were not verified.

**Done when:** all Bedrock capabilities **advertised for launch** pass on pinned clients; explicitly disclose unsupported models/shaders/GUI modes.

### R3. Secure VoxelShop trades — **release blocker if replacing ShopGUIPlus**

Owner: [VoxelShop](https://github.com/VoxelHorizons/VoxelShop) / VaultUnlocked integration.

- [ ] Finish [issue #4](https://github.com/VoxelHorizons/VoxelShop/issues/4): authoritative quotes, actual BUY/SELL/SELL_ALL, metadata-aware inventory identity, anti-reentrancy and multi-step durable journal.
- [ ] Never retry an ambiguous VaultUnlocked payment without a recoverable provider outcome; simulate crash at every payment/item/stock phase, disconnect, double click, shift/drag and malicious client values.
- [ ] Review [PR #7](https://github.com/VoxelHorizons/VoxelShop/pull/7) after integration tests for enriched items, snapshot migrations and shop layouts.
- [ ] Verify prices, permissions, dynamic stock and currency rendering for **both** Java and Bedrock menus.

**Owner-confirmed production boundary:** **ShopGUIPlus** is the live buy/sell system, works with the current default economy, and is not yet replaced by VoxelShop. VoxelShop has developed trading code but is **not production-tested or installed as the active shop**. VoxelFurnitureShop is an informational showroom without currency or purchase transactions. VoxelCore/VaultUnlocked Tokens and their placeholder/administration commands work; EssentialsX standard commands still use legacy/default economy (currency-aware fork is only planned). ShopGUIPlus-DynamicPricing is **not installed or tested**. Vehicle recolor crafting recipes are **not implemented**, despite support for stored/displayed color variants.

**Done when:** money, items and stock reconcile after failures without duplication or unpaid delivery; otherwise retain the existing proven shop for launch.

### R4. Menus, authoring and pack operations

- [x] **Owner-confirmed frontend decision:** Java uses DeluxeMenus; Bedrock uses **VoxelCore ChestGUI** with native Classic chest-screen backgrounds. CrossplatForms was abandoned. The DeluxeMenus forms branch is historical and not required by this active approach. Target-player and Citizens NPC profile arguments work on both editions; offline player profiles remain limited.
- [ ] Retest inventory click-exploit safety and menu-transition reliability ([Core #97](https://github.com/VoxelHorizons/VoxelCore/issues/97)).
- [ ] Finish high-value [ContentManager issues #1–#9](https://github.com/VoxelHorizons/ContentManager/issues): reference graph, safe editor, furniture/vehicle/block metadata, glyphs and dependent assets, validated pack build, transactional publish/rollback. [PR #13](https://github.com/VoxelHorizons/ContentManager/pull/13) is open.
- [ ] Exercise all pack build scopes, cancellation, host, stale allocation migration, failed reload rollback and paired-publish integrity. **Owner-confirmed passing:** Java, Bedrock and all build modes work; `/voxelcore admin pack stop` cancels without damaging previous published packs; `build all` does not significantly disrupt online gameplay; live Java pack updates resend to online clients; malformed running `content reload` retains previous content. **Owner-confirmed failing:** required Java pack mode does not kick clients who decline/fail ([Core #95](https://github.com/VoxelHorizons/VoxelCore/issues/95)); malformed startup content can disable VoxelCore ([Core #96](https://github.com/VoxelHorizons/VoxelCore/issues/96)). These are distinct from live-reload safety.
- [ ] Test config upgrades, dependency absence, client rendering on both editions and asset rollback.

**Additional owner-confirmed limits:** `/profile <player>` requirements currently check `%parseother_unsafe_{{player}}_{player_displayname}%`, rejecting **previously joined offline** players as well as truly unknown names. Replace with an offline-aware player-existence check in the menu/operator integration; do not count this as a VoxelCore engine failure without reproduction. Bedrock FurnitureShop popup has an extra blank tooltip rectangle ([FurnitureShop #14](https://github.com/VoxelHorizons/VoxelFurnitureShop/issues/14)). ContentManager is **not production-ready**; its separate [P0–P8 roadmap](https://github.com/VoxelHorizons/ContentManager/blob/main/docs/ROADMAP.md) tracks offline editing, compiler validation and safe publishing. Java custom shader portraits have no direct Bedrock equivalent.

**Done when:** an operator can edit/build/publish/recover without silent asset corruption or broken menu routing.

### R5. Release certification and documentation

- [ ] Pin production versions (Paper, Java, Geyser, Floodgate, VoxelCore and addons), configuration revisions and content pack hashes.
- [ ] Require passing distribution build, changed-code unit/integration tests and **26.3 smoke**. Record all historical-version smoke outcomes; do not hide or reclassify failures as success.
- [ ] Run a repeatable gameplay acceptance suite covering restart/chunk recovery, ownership, storage, economy, packaging, Java/Bedrock UI, remote/unavailable dependency, and rollback.
- [ ] Attach exact build SHA, commands, expected versus actual outcome, reviewer, logs/screenshots to tracked issues or release evidence.
- [ ] Publish a supported-feature/version matrix; avoid inferring all-version parity from a core startup check.

**Owner-confirmed release checks:** four addons load on Paper `26.3-209-main@03af8b4` with Java 25; non-OP users cannot use VoxelCore administration commands; regular multiworld behavior and plugin upgrades preserve persistent data. Others have reportedly run plugins on 1.12 without issues, **but no full owner-run legacy-version compatibility matrix exists**. Community anecdotes are not universal addon/Bedrock certification.

**Still untested/unverified:** abrupt server crash and fault-injected storage recovery, backup restoration, high-load Spark profiling (MSPT/CPU/heap/GC), GUI item extraction/duplication via click/drag/hotbar/shift, simultaneous furniture inventory viewers, remote/proxy Geyser publish/recovery, and Bedrock pack refusal. These are test gaps, **not automatically confirmed vulnerabilities**.

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
| B06 | Native geometry, attachables and UVs | **Partial — 3D geometry owner-confirmed** | Held 3D item geometry works on Bedrock; some generated 3D textures degrade. Validate atlas/UV/transform visual fidelity ([Core #98](https://github.com/VoxelHorizons/VoxelCore/issues/98)). |
| B07 | Block states, static/dynamic tint and animation | **Partial — custom block model fails live Bedrock test** | VoxelCore custom carrier-backed blocks display as vanilla note-block states rather than Java's custom block models ([Core #94](https://github.com/VoxelHorizons/VoxelCore/issues/94)). |
| B08 | Companion model/variant registration | **Partial** | Runtime addon variants, shared identity and revision |
| B09 | Furniture visual state feed | **Implemented** ([#47 merged](https://github.com/VoxelHorizons/VoxelFurniture/pull/47)) | Real Geyser/client consumption and lifecycle |
| B10 | World furniture/custom-entity projection | **Partial — live visual and interaction bugs** | Bedrock furniture renders, seats and stores items, but spins/rotates on spawn/model swap ([Furniture #46](https://github.com/VoxelHorizons/VoxelFurniture/issues/46)); right-clicking bamboo cupboard benches can drop furniture or leave restart-persistent ghosts ([Furniture #48](https://github.com/VoxelHorizons/VoxelFurniture/issues/48)). |
| B11 | Vehicle projection, seats, movement and accessories | **Partial — driving owner-confirmed** | Bedrock vehicles render and drive/steer/dismount; cargo works and survives reload, but attached chest visual is invisible ([Vehicles #24](https://github.com/VoxelHorizons/VoxelVehicles/issues/24)). |
| B12 | Bedrock-safe browser pagination | **Partial — transitions unreliable** | Items and browser icons work, but some Bedrock menu/page switches open and immediately close ([Core #97](https://github.com/VoxelHorizons/VoxelCore/issues/97)); item-extraction exploit tests remain open. |
| B13 | Managed glyph allocation and controller range | **Partial — live font glyph differences** | Some Bedrock glyphs including a branding icon render incorrectly ([Core #98](https://github.com/VoxelHorizons/VoxelCore/issues/98)); collect exact client/codepoint corpus. |
| B14 | Unmanaged font/literal scan and rewrite | **Implemented** | Run on live configs; audit DB-backed ranks/tags |
| B15 | Bedrock menu frontend (formerly forms) | **Superseded — ChestGUI owner-confirmed** | **CrossplatForms abandoned**; VoxelCore ChestGUI with Bedrock Classic UI replaces forms; Java remains DeluxeMenus. Validate click requirements, menu transitions and inventory safety. |
| B16 | Profiles, avatars, statistics and tooltip fallback | **Partial — profiles owner-confirmed** | Self/target/NPC information works on Java and Bedrock. Java shader portrait is not supported on Bedrock; previously joined offline profiles currently fail menu existence requirements. |
| B17 | Signed remote bundle and projection feeds | **Implemented/tested components** | Remote proxy/active revision/replay/failure test |
| B18 | Versioned backend and client boundary | **Partial** | Per-feature legacy support claims and test builds |
| B19 | End-to-end real Bedrock acceptance | **Partial — owner-tested subset** | Live pack, furniture, vehicle, menu and showroom behavior tested, but defects remain and exact Bedrock/Geyser client-version corpus is missing. |
| B20 | Cross-plugin regression and release signoff | **Open — protection and Bedrock blockers** | Address live protection bypass, persistent furniture ghost, custom-block rendering and documented asset/UI gaps or explicitly narrow launch claims; retain evidence. |

Geyser version/build capabilities must match the extension's experimental APIs. Java fonts/full-screen negative-spacing UI, Java-only shaders and arbitrary model rules are **not** automatically transferable to Bedrock.

## Known problems, release-gate exceptions and decisions

| Item | Current evidence and treatment |
| --- | --- |
| **CI-LEGACY-RCON** | VoxelCore `VOXELCORE_READY` appears on intermittent 1.13.2/1.14.4 RCON failures. At [current main smoke](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40280473024) the matrix was **7/8**: 1.14.4 failed, 26.2/26.3 passed. The attempted console fallback reported `Exec needs to be run with user ID 1000`. This exact infrastructure signature is **conditionally non-blocking** after recording the failed run. Never call 7/8 a pass; preserve assertions and investigate new failure signatures. [Current distribution build](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40280472998) passed. |
| **BEDROCK-LIVE** | Many native/projection features compile but still lack representative client proof. Treat them as implemented or partial, not Verified. |
| **BEDROCK-FURNITURE-ROTATION** | Owner's Bedrock test (27 Oct 2026) shows visible placed furniture with unexpected rotation/spinning upon spawn and animation model swaps. Reported visually in-game, with a still screenshot provided; exact Geyser/extension/plugin revisions and video not yet captured. Track [VoxelFurniture #46](https://github.com/VoxelHorizons/VoxelFurniture/issues/46). |
| **STATE-SAFETY** | Furniture, vehicles, showroom inventory and shop payments have distinct persistent state owners. Certify failure/restart paths, not just happy-path startup. |
| **FRONTEND** | Owner chose VoxelCore ChestGUI on Bedrock and DeluxeMenus on Java; CrossplatForms is abandoned. Older form-frontend work is no longer the active plan. Bedrock transitions can close immediately ([Core #97](https://github.com/VoxelHorizons/VoxelCore/issues/97)). |

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

## Owner-reported issue register — 27 October 2026

The 85-question acceptance discussion is a **manual owner-reported test audit**, not 85 independently automated checks. The owner confirmed behavior on Paper 26.3 / Java 25, but did not supply every addon/Geyser/Bedrock client revision or all logs. Use **Owner-confirmed**, not formally **Verified**, until the evidence criteria above are met. Links below are primary issue owners, not evidence of implemented fixes.

| Priority | Primary tracker | Acceptance failure |
| --- | --- | --- |
| **P0** | [Furniture #49](https://github.com/VoxelHorizons/VoxelFurniture/issues/49) | Non-OP furniture placement/break not denied in Hub; seat/interact must remain permitted. |
| **P0** | [Vehicles #22](https://github.com/VoxelHorizons/VoxelVehicles/issues/22) | Non-owner private vehicle ride/modify/remove and cargo access bypass. |
| **P0** | [Vehicles #23](https://github.com/VoxelHorizons/VoxelVehicles/issues/23) | Hub region vehicle placement and flag enforcement fail. |
| **P0** | [Furniture #48](https://github.com/VoxelHorizons/VoxelFurniture/issues/48) | Bedrock cupboard bench right-click drops item / sometimes persistent ghost, inventory items tested recoverable. |
| **P1** | [Core #94](https://github.com/VoxelHorizons/VoxelCore/issues/94) | Bedrock custom block carrier state renders vanilla. |
| **P1** | [Core #95](https://github.com/VoxelHorizons/VoxelCore/issues/95) | Required Java resource-pack decline/load failure does not disconnect. |
| **P1** | [Core #96](https://github.com/VoxelHorizons/VoxelCore/issues/96) | Invalid startup content disables plugin despite working live-reload rollback. |
| **P1** | [Furniture #46](https://github.com/VoxelHorizons/VoxelFurniture/issues/46) | Bedrock furniture rotation / model-change spinning. |
| **P1** | [Vehicles #24](https://github.com/VoxelHorizons/VoxelVehicles/issues/24) | Bedrock vehicle chest accessory model invisible although storage works. |
| **P2** | [Core #97](https://github.com/VoxelHorizons/VoxelCore/issues/97) | Bedrock ChestGUI intermittently closes on page/menu switches; Geyser suspected only. |
| **P2** | [Core #98](https://github.com/VoxelHorizons/VoxelCore/issues/98) | Selected Bedrock glyphs and generated 3D model textures render poorly. |
| **P2** | [FurnitureShop #14](https://github.com/VoxelHorizons/VoxelFurnitureShop/issues/14) | Extra empty box above Bedrock showroom tooltip. |

**Not a confirmed defect:** untested backup restoration, crash recovery, concurrent furniture inventory viewing, GUI exploit resistance, Bedrock required-pack refusal, old-version full ecosystem parity and large-scale Spark profiling. Do not file these as breakages without reproductions. **Not yet implemented or operational:** showroom purchases, production VoxelShop/ContentManager, multi-currency EssentialsX fork, vehicle dye crafting recipes and Bedrock shader portraits. Track scope under R3/R4 or the owning project's development plan rather than falsely reporting live failures.

## Maintenance

Update this document when significant work merges: record repository, PR/commit and new remaining acceptance evidence. Use GitHub issues for individual defects and external plugin roadmaps for addon-specific detail. **Do not recreate extra `ROADMAP.md`, `PROGRESS.md`, `HANDOFF.md`, `KNOWN-ISSUES.md`, or dated migration progress files in this repository.** Only this roadmap governs active status; retired historical snapshots remain accessible through Git history.
