# Bedrock recovery handoff — 24 October 2026 (Australia/Melbourne)

## Latest checkpoint — 24 October 2026, session 4 (supersedes everything below)

Fetch live HEADs first; this file is not a ref. No PR merged, no force push.

| Repo | Branch / PR | Code commits this session (oldest→newest) | CI |
| --- | --- | --- | --- |
| VoxelCore | `feat/bedrock-pack-publishing`, [PR #84](https://github.com/VoxelHorizons/VoxelCore/pull/84) | `d0ec247` rescale+flat planes; `d0b847b` render.rule→Geyser predicates; `9448ec2` constant tints; `a9511d6` B17 signed remote sync; `f811217` Bedrock tooltip; `991c7b0` legacy CMD mappings | build SUCCESS on every commit through `a9511d6`; smoke 8/8 on `d0ec247`, `d0b847b`, `9448ec2`; later runs: re-query. KI-01 = non-blocking |
| DeluxeMenus | `feat/bedrock-forms` (no PR; CI via `workflow_dispatch` on build.yml) | `557bac4` form frontend; `6b8d858` `bedrock_image` | both [SUCCESS](https://github.com/VoxelHorizons/DeluxeMenus/actions/runs/40082176306) |
| VoxelFurniture | PR #47 | `b1b7585` | SUCCESS |
| VoxelVehicles | PR #21 | `09a0abb` | SUCCESS |

**CI policy:** TESTING.md "CI policy" — build + changed-code tests + 26.3 smoke are blocking; KI-01 legacy smoke is recorded, not chased.

**Status (IMPLEMENTED; TESTED where CI green; nothing VERIFIED live):**
- B05 rule predicates; B06 rescale/planes; B07 constant tints; B08 shop audit done (no addon models); B10 live scale + model re-send; B14 glyph migration; B15 DeluxeMenus forms; B16 forms stats/avatars + tooltip text; B17 signed pack/mappings sync; B18 legacy CMD mappings.

**Local test method (no Maven repo access here):** `javac` against `~/.m2` gson/junit/snakeyaml with `-sourcepath voxelcore-common/src/main/java:voxelcore-pack/src/main/java`; 48 pack tests, 17 extension tests, 5 DeluxeMenus text tests pass. Bukkit/Geyser/Paper glue compiles only in CI.

**Findings to reuse**
- Geyser API: no plugin-messaging hook; `SEAT_OFFSET` is rider-side with a Geyser-computed baseline (do B11 seats only with live client); custom-item definitions sharing a Java model are ordered by `priority` desc, then predicate count, then range thresholds; range = `value >= threshold`; `legacy` definitions take `custom_model_data`.
- Cumulus via reflection: resolve methods on public interfaces only.

**Next actions (in order)**
1. Re-query CI for `a9511d6`…`991c7b0` (build + 26.3 blocking only).
2. B17 projection feeds for proxies: multiple backends collide on `furniture.json`/`vehicles.json` — needs per-backend feed names in `EntityProjectionIndex` before a network transport.
3. B10 dye/animation: register Geyser entity properties (`GeyserDefineEntityPropertiesEvent`) and generate render controllers reading `query.property` — needs design + live client.
4. B20: shared schema-v1 projection fixture tests across companions.
5. B19 BLOCKED (real Bedrock client + VoxelHorizons Geyser server).

--- | --- | --- | --- |
| VoxelCore | `feat/bedrock-pack-publishing`, [PR #84](https://github.com/VoxelHorizons/VoxelCore/pull/84) | this commit: exact Java `rescale` + flat planes (B05/B06), CI policy docs | `d0b84cb`: [build SUCCESS](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40081056409); [smoke](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40081056462) 7/8, 26.3 + 26.2 green, 1.14.4 = **KI-01, non-blocking** |
| DeluxeMenus | `feat/bedrock-forms` (no PR yet; CI via `workflow_dispatch`) | `557bac4` Bedrock form frontend, `6b8d858` `bedrock_image` | `557bac4` [build SUCCESS](https://github.com/VoxelHorizons/DeluxeMenus/actions/runs/40082105372); `6b8d858` dispatched, re-check |
| VoxelFurniture | `feat/bedrock-state-projection`, [PR #47](https://github.com/VoxelHorizons/VoxelFurniture/pull/47) | `b1b7585` | SUCCESS |
| VoxelVehicles | `feat/bedrock-state-projection`, [PR #21](https://github.com/VoxelHorizons/VoxelVehicles/pull/21) | `09a0abb` | SUCCESS |

**CI policy:** see TESTING.md "CI policy". KI-01 legacy smoke failures are recorded, not chased.

**Done (cumulative, all IMPLEMENTED; TESTED where CI above is green; nothing VERIFIED live):** B10 live scale reconcile + companion model-change re-send; B14 glyph literal scanner/migrator + command; B08 unmapped-model status; B15 DeluxeMenus Bedrock forms (`com.extendedclip.deluxemenus.bedrock.*`, `menu/MenuClickDispatcher`); B16 partial (stats as text, avatar via `bedrock_image`); B05/B06 `rescale` exact + flat planes.

**Findings to reuse**
- Geyser API has **no plugin-messaging hook** (event/java: spawn, passengers, define commands, parrots, transfer, CoC). B17 remote transport therefore needs an out-of-band channel (signed HTTP pull or mounted folder).
- `SEAT_OFFSET` is rider-side metadata whose baseline Geyser computes; overriding it blind risks sinking riders. Do B11 seat work only with a live client or a Geyser-core-derived baseline.
- Cumulus via reflection: resolve methods on public interfaces (`FloodgateApi`, `GeyserApiBase`, `SimpleForm$Builder`, `SimpleFormResponse`), not impl classes.

**Next actions (in order)**
1. Confirm DeluxeMenus `6b8d858` dispatch build and VoxelCore CI for this commit (build + 26.3 only blocking).
2. Open DeluxeMenus PR `feat/bedrock-forms` → `main` only if the user asks; otherwise keep branch and record.
3. B05: modern 1.21.4+ `assets/*/items/*.json` item-model definitions (`minecraft:model`, `select`, `condition`, `range_dispatch`, `composite`) → pick the default/fallback model for Bedrock and report the rest; synthetic fixtures.
4. B07 tint: bake `tintindex` faces with a configured default tint colour (opt-in) instead of rejecting.
5. B17 design + implementation: HMAC-signed projection/receipt feed served over HTTP from Paper, pulled by the Geyser extension on proxies.
6. B19 BLOCKED: real Bedrock client + VoxelHorizons Geyser server.

---

## Latest checkpoint — 24 October 2026, session 3 (supersedes everything below)

Fetch live HEADs first; this file is not a ref. No PR merged, no force push.

| Repo / PR | Last code commit this session | CI |
| --- | --- | --- |
| VoxelCore [PR #84](https://github.com/VoxelHorizons/VoxelCore/pull/84) | `041708b` live scale reconcile; `950a8bb` B14 glyph migration; `ac68754` B08 unmapped-model status | `041708b` and `950a8bb`: build + smoke 8/8 SUCCESS ([950a8bb build](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40080049988), [smoke](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40080050052)). `ac68754`: [build SUCCESS](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40080128234); [smoke](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40080128175) 7/8, 1.14.4 KI-01 RCON listener death (thread dump in KNOWN-ISSUES); failed job re-run once |
| VoxelFurniture [PR #47](https://github.com/VoxelHorizons/VoxelFurniture/pull/47) | `b1b7585` re-send on model change | [build SUCCESS](https://github.com/VoxelHorizons/VoxelFurniture/actions/runs/40079728359) |
| VoxelVehicles [PR #21](https://github.com/VoxelHorizons/VoxelVehicles/pull/21) | `09a0abb` re-send on model change | [build SUCCESS](https://github.com/VoxelHorizons/VoxelVehicles/actions/runs/40079763805) |

**Done this session**
- **B10** `ProjectionReconciler` + 1 s loop in `VoxelCoreGeyserExtension`: pushes changed uniform SCALE to spawned entities via `GeyserConnection#entities().byUuid()` → `GeyserEntity#override` (Geyser API: `byUuid` added in Custom Entity API #6010, 2026-07, before 2.11.2). Despawn/move/rotation = Geyser's own Java packet translation; chunk reload/reconnect = new `ServerSpawnEntityEvent` reading the current index. No despawn/chunk events exist in the API.
- **B10 companions:** `ProjectionModelChanges` + `BedrockEntityResender` in both plugins: when a display's `model_item` changes, after the feed is on disk, hide/show (1.18+ reflection, plugin-keyed) for Bedrock viewers (`GeyserApi#isBedrockPlayer` via reflection; all viewers if absent). Config `bedrock.projection.resend-on-model-change`.
- **B14** `voxelcore-pack/.../GlyphLiteralMigration.java` + `/voxelcore admin pack glyphs <scan|migrate confirm>` (perm `voxelcore.admin.pack.glyphs`, config `bedrock.glyph_migration_roots`, default `DeluxeMenus`). Report `glyph-literal-migration.txt`, backups+plan in `plugins/VoxelCore/glyph-literal-backup/`.
- **B08** `EntityProjectionIndex#unmappedModels()` shown by Geyser `voxelcore status`.

**Local test evidence:** javac+JUnit only (Maven repos opencollab/spigot blocked by this sandbox proxy): Geyser extension pure tests 11/11, `GlyphLiteralMigrationTest` 5/5, furniture/vehicle `ProjectionModelChangesTest` 2/2 + 1/1. Bukkit/Geyser glue compiled only in CI. All of the above: IMPLEMENTED; TESTED once the listed CI is green; none VERIFIED live.

**Remaining / next actions (in order)**
1. Confirm CI for `950a8bb` and the following commit (glyph command compiles against Spigot 1.12.2 API; extension against Geyser 2.11.2). Fix any failure first.
2. B10: dye/colour + animation (schema-v1 already has `dye_rgb`, `use_animation_active`; needs Bedrock geometry/material that consumes `COLOR`/`VARIANT` or entity properties — design before coding); non-uniform scale stays rejected (Geyser SCALE is uniform).
3. B08: audit VoxelFurnitureShop items; decide on a versioned registry export only if a model source outside VoxelCore content is found.
4. B05/B06/B07 compiler work needs the live `unsupported-items.txt`/`unsupported-geometry.txt` corpus (KI-04).
5. B15/B16 DeluxeMenus Bedrock forms frontend — large; start with a design doc in `docs/bedrock/`.
6. B19 BLOCKED: real Bedrock client + VoxelHorizons Geyser server.

---


## Latest verified repository checkpoint — 24 October 2026

This section **supersedes any older SHA, CI status, or next-task claim below**. Refresh the remote branch HEAD on every new session; this document is not a git ref.

- **VoxelCore** `feat/bedrock-pack-publishing`, [PR #84](https://github.com/VoxelHorizons/VoxelCore/pull/84): code HEAD before this checkpoint `c1f3b5360f9534fe7d2f5b5ea2f095aa2402380b`. Latest [distribution build](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40077217228) **SUCCESS**, [MVP smoke](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40077217237) **SUCCESS (8/8)**. Older RCON failures are intermittent and unresolved, not reason to disable the tests.
- **VoxelFurniture** [PR #47](https://github.com/VoxelHorizons/VoxelFurniture/pull/47): `2332b3ebea2cfcee41bbe97bf59bf5e1c148280f`, [build SUCCESS](https://github.com/VoxelHorizons/VoxelFurniture/actions/runs/40077356575). Includes `plugin.yml` Geyser-Spigot optional startup ordering, so the existing publisher can detect an enabled Geyser instance before starting.
- **VoxelVehicles** [PR #21](https://github.com/VoxelHorizons/VoxelVehicles/pull/21): `6eb79cbf84bebc24d262e3a9c72d92a047299ead`, [build SUCCESS](https://github.com/VoxelHorizons/VoxelVehicles/actions/runs/40077363366). Same optional load-order fix.
- Code added since prior handoff: `200eab4f4410eedaf7b94bbaf7c8056a2feb2329` and test fixture correction `a58450602d6cd7d558baeb3315e6e19982e19f2f`: schema-v1 projection now carries an optional, bounded **uniform scale** from furniture main display, ITEM part or vehicle model. `VoxelCoreGeyserExtension#onServerEntitySpawn` uses Geyser's public `preSpawnConsumer`/`GeyserEntityDataTypes.SCALE`. Non-uniform XYZ values are *not* silently converted. [Build and 8/8 smoke for scale commit](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40077155518) / [smoke](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40077155527) SUCCESS. **TESTED** by CI, **NOT VERIFIED** on live Bedrock.
- `c1f3b536` adds failure-only SIGQUIT JVM thread-dump capture to `.github/scripts/mvp-smoke.sh#rcon`. The runner's next smoke [40077217237](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40077217237) passed 8/8, so no failing thread dump has been captured yet.

**Current exact engineering boundary:** item-display spawn substitution, stale-file expiry and optional *uniform* scale are implemented/tested. B10 still lacks live state change (motion, rotation, dye, animation, non-uniform scale), chunk unload/reload, reconnect rehydration and authoritative interactions. Geyser client acceptance B19 remains BLOCKED pending a real Bedrock client/server. Other major B05–B08 and B14–B17 tasks still unfinished. Do not claim 100%.

**Resume in order (three independent actions):**
1. Recheck Core/furniture/vehicle HEAD and their most recent CI. Capture failing 1.13.2/1.14.4 JVM thread dump using failure-only diagnostic when reproducible. Keep entire smoke matrix enabled.
2. Finish the next B10 feature in `voxelcore-geyser-extension/src/main/java/org/voxelhorizons/geyser/EntityProjectionIndex.java` and `VoxelCoreGeyserExtension.java`: synchronize Paper render-state updates and despawns/chunk/reconnection using supported, verified Geyser API events. Add focused regression tests; do not invent APIs or overwrite Paper authority. Amend companion schema-v1 producers only if necessary, in their existing draft PRs.
3. Independently tackle B08 live addon item/variant registration or B14 authored glyph literal migration scanner, with deterministic fixtures. Obtain `unsupported-items.txt` and actual Bedrock client acceptance data when accessible, but continue independent coding meanwhile.

**Installation note:** Geyser extension JAR is a separate artifact, pack and mappings must match a validated receipt and be loaded at Geyser restart, JSON publisher files are local/mounted-only; two competing display entity bridges must not be activated together. No PRs were merged.

---


**READ THIS FILE FIRST, THEN USE LIVE GITHUB HEAD.** Never reset to the anchor or create duplicate PRs.

## Branch and PRs
- VoxelCore `feat/bedrock-pack-publishing`, [PR #84](https://github.com/VoxelHorizons/VoxelCore/pull/84), existing branch was exactly `64b8a3275c424c3cdac0892d6f5cc53bce780255` at recovery. First post-recovery implementation commit: `363147abd04889d84acb3cf77f02b4410f58ff1c` (stale projection expiration). This handoff is committed *after* that SHA; the exact branch HEAD must be fetched dynamically from GitHub.
- VoxelFurniture `feat/bedrock-state-projection`, [PR #47](https://github.com/VoxelHorizons/VoxelFurniture/pull/47), remote HEAD `cd90266be94255ed887cec966ebc0133916df3bc`.
- VoxelVehicles `feat/bedrock-state-projection`, [PR #21](https://github.com/VoxelHorizons/VoxelVehicles/pull/21), remote HEAD `cd28c5d3f001144846c15e77dee534616d12c623`.
- No PR merged and no force push; no previous work overwritten.

## Current CI
- Anchor `64b8a327`: [Core distribution SUCCESS](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40072434517), [smoke FAILURE](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40072434436) (1.13.2, 1.14.4 RCON refusal; six other versions pass). See TESTING.md for exact log evidence.
- Commit `363147a`: [distribution 40076547872](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40076547872) and [smoke 40076547878](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40076547878) were **in progress** at writing; re-query before marking TESTED.
- Companion current heads: Furniture [build SUCCESS](https://github.com/VoxelHorizons/VoxelFurniture/actions/runs/40072620582); Vehicles [build SUCCESS](https://github.com/VoxelHorizons/VoxelVehicles/actions/runs/40072624355). Both remain draft.

## Completed this recovery session
- Confirmed GitHub HEAD/commit sequence beyond the stale PROGRESS.md; inspected Geyser entity catalog/extension/UUID reader and the atomic furniture/vehicle publishers and schemas.
- Read and diagnosed legacy smoke logs. Spigot/VoxelCore actually initialized and emitted RCON start; the socket is absent by command time, yet JVM is alive and not OOM-killed. Root cause not proven.
- Implemented 60-second fail-closed expiry for old Paper projection JSON, cache expiry without file writes, independent feed retention. Added two explicit regression tests in `voxelcore-geyser-extension/src/test/java/org/voxelhorizons/geyser/EntityProjectionIndexTest.java`. Implementation `363147a`, **CI verification pending**.
- Updated design/status records to reflect that B10 is IN PROGRESS, not NOT STARTED, and companion draft plugins now publish files. Audited GeyserDisplayEntity (AGPL-3.0) and retained single VoxelCore event-based bridge without copying code.

## Active implementation task and exact target
**B10: finish runtime world model/transform synchronization; registration and one-time spawn substitution already exist.**
Read `voxelcore-geyser-extension/src/main/java/org/voxelhorizons/geyser/VoxelCoreGeyserExtension.java`, `EntityProjectionIndex.java` and its tests; read `src/main/java/org/voxelhorizons/{furniture|vehicles}/bedrock/BedrockProjectionPublisher.java` in both plugins and the schema capture classes. Extend the transport/consumer with generation/revision and render-state delta handling, including model variants, orientation, position/scale, metadata, despawn, chunk unload and reconnect. Validate against actual available Geyser API events; do not implement unsupported or fictional methods. Keep Paper authoritative.

## Current errors and blockers
1. Legacy 1.13.2/1.14.4 CI container RCON socket absence; logs/commands and runner details in KNOWN-ISSUES.md and TESTING.md. Need Java thread dump at failure, not just more RCON retries.
2. Core latest commit tests still need passing CI evidence; no local Git clone/Maven due DNS in this runtime.
3. Geyser API experimental: client appearance and registered 3D model semantics cannot be marked VERIFIED without live extension and Bedrock tests.
4. Live `unsupported-items.txt`/`unsupported-geometry.txt` corpus and Bedrock environment absent.
5. B07/B08/B14-B17 incomplete; see ROADMAP.md.

## Next three independent actions
1. Inspect GitHub Actions for `363147a` and this documentation commit; fix a failing test if present; capture JVM/RCON diagnostics for legacy 1.13.2+1.14.4 smoke without removing matrix entries.
2. Implement **one** cross-repo B10 state reconciliation increment using schema-v1 snapshots + tests (furniture/vehicle model changes or despawn/reconnect). Commit/push to existing PR(s), update TESTING/PROGRESS immediately.
3. Independently implement B08 addon item/variant registry export or B14 authored glyph migration scanner with tests; never equate compilation to Bedrock visual parity.

## Resume commands (locally with GitHub access)
```bash
git clone https://github.com/VoxelHorizons/VoxelCore.git
cd VoxelCore
git fetch origin feat/bedrock-pack-publishing
git checkout feat/bedrock-pack-publishing
git pull --ff-only
git log -15 --oneline
git status --short
mvn -pl voxelcore-geyser-extension -am test
mvn -B verify
```
Review the two cross-repository PR heads, recent build/smoke runs and Geyser license constraints; do not reset any branch. For a remote-only GitHub connector session, inspect the branch via the GitHub API before editing and use fast-forward/expected-SHA updates.

## Acceptance warning
This is **NOT 100% complete**: no live Bedrock render/interaction parity, no stable remote update protocol, no connected menu frontend, and outstanding smoke failures. B19 is BLOCKED on real client access, not an excuse to stop other engineering.
