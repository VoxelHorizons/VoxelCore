# Bedrock recovery handoff — 24 October 2026 (Australia/Melbourne)

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
