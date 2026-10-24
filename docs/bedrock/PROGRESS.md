# Bedrock Compatibility — Durable Progress / Resume Ledger

This is the authoritative operational handoff for **VoxelCore Bedrock development**, not a claim that the integration is finished.

## Session identity
- Date: 2026-10-24, Australia/Melbourne.
- Repository: `VoxelHorizons/VoxelCore`
- Working branch: `feat/bedrock-pack-publishing`
- Open PR: [#84](https://github.com/VoxelHorizons/VoxelCore/pull/84); **do not merge without owner approval**.
- Recorded head before final handoff update: `7e6fee680f44dfd11b12ef3c064f3ccf22bb2ed3` (new documentation commits follow; refresh `git log` / PR head).
- Authoritative spec: [../bedrock.md](../bedrock.md)
- Milestone IDs/statuses: [ROADMAP.md](ROADMAP.md)
- Design decisions: [ARCHITECTURE.md](ARCHITECTURE.md)
- Tests/results: [TESTING.md](TESTING.md)

## Latest implementation commits (all on the development branch)
| Commit | Change | Status | Evidence |
| --- | --- | --- | --- |
| `bf1221f4`, `3dd9fd2a`, `64588575` | Persistent roadmap, architecture, testing record | IMPLEMENTED | Files exist on branch |
| `b7f01f88` | Lock, preflight source/manifest validation, revision+SHA publication receipt and recoverable pair deployment | TESTED | `GeyserPackPublisherTest`; `e5013dca` build pass |
| `7e4299f2` | Injected failure, tampering, mixed revision, corrupt input and rollback regression tests | TESTED | `e5013dca` build pass |
| `e4351dff` | `/vc admin pack info` surfaces deployed consistency state | TESTED | `e5013dca` build pass; actual Geyser state still unverified |
| `fbbb6de8`, `4f0ef87c` | Multi-layer generated sprite support including inherited textures | TESTED | `BedrockIconRendererTest`, CI passing `e5013dca` |
| `87dd692d`, `1037adc3` | Initial native cuboid Bedrock geometry + attachable writer, separate failures report | TESTED | `BedrockGeometryCompilerTest`; live geometry unverified |
| `2b380d3d` | Native geometry tests | TESTED | Initial test uncovered transparent UV atlas; fixed next |
| `e5013dca` | Fix face texture rasterization for oversize/off-cube Java coordinates | TESTED | GitHub build and Minecraft 26.3 smoke both **success** on `e5013dca` |
| `c971f6f4` | Sync main Bedrock spec and new durable records to implemented state | IMPLEMENTED | Final CI in progress at recording |

## Automated evidence actually observed
- GitHub build check on commit `e5013dca5b5a2bcb737e00dd2eee543a1f99cd5b`: **success**, job `120802463093`.
- Minecraft 26.3 MVP smoke on same commit: **success**, job `120802463250`.
- Previous build `ac7dde0b` failed due to explicit `BedrockGeometryCompilerTest` assertion on a transparent atlas; repair `e5013dca` fixed without deleting the test.
- Latest documentation-only head `c971f6f4...`: build and several Minecraft smoke jobs still running at last observation. Verify latest CI before marking branch head TESTED.
- None of the tests exercise a real Bedrock client or running Geyser extension. **No real-client feature is VERIFIED.**

## Current task status (Do not infer a percentage)
- B01–B02: TESTED, live unverified.
- B03: TESTED for deterministic local/mounted files, rollback and digest receipt; running Geyser activation still unverified; remote authenticated path still B17.
- B04: IMPLEMENTED, real unsupported corpus needed.
- B05: IN PROGRESS, layered sprites and several inheritance patterns improved, predicates/tint/model-data rules outstanding.
- B06: IN PROGRESS, *actual native cuboid geometry and attachable files exist*, but UV, animations, transform orientation and all Java model variants need real-client validation.
- B12: IMPLEMENTED but Bedrock browser flashing/closing not independently retested.
- B13: TESTED managed glyph allocator, unmanaged authored glyph migration remains B14.
- B07–B08, B10, B14–B17, B20: NOT STARTED or partial per ROADMAP.
- B09: TESTED schema-v1 immutable snapshot in VoxelFurniture draft PR #47; no Geyser consumer.
- B11: IN PROGRESS (snapshot TESTED) in VoxelVehicles draft PR #21; no cross-process synchronization.
- B18: IN PROGRESS (CI matrix present, version strategy incomplete).
- B19: BLOCKED on physical/remote Bedrock+Geyser/Floodgate client/server access. The user previously provided screenshots/logs; current environment cannot open that live server.

## Next engineering actions (ordered; do not wait for additional chat to select next)
1. Query GitHub Actions checks on current HEAD for failures, examine job logs and fix the exact failing test. Do not suppress tests. Then update evidence in TESTING.md.
2. Add golden files/test fixtures exercising native 3D cube textures, handedness, face UV rotation, inherited models and Bedrock attachable schema. Verify hold transforms against a compatible Geyser Bedrock client before promoting B06 from TESTED to VERIFIED.
3. Obtain live `unsupported-items.txt` and `unsupported-geometry.txt` from the VoxelHorizons 26.3 build (not currently in repository); categorize the old 527 unsupported items by failure and add each missing model feature.
4. Implement B08 versioned addon export from VoxelFurniture/VoxelVehicles item registries to VoxelCore Bedrock build; add tests for runtime variants.
5. Implement B09/B10 Geyser display-entity bridge using public API, with chunk/resync/seat state tests; don't move gameplay ownership away from Paper.
6. Implement B14/15 Java-authored font cross-plugin migration and Bedrock UI rendering using separate frontend, preserving passed player/NPC arguments.
7. Implement B17 authenticated proxy extension/version protocol, then run B19 live matrix; verify pack acceptance/refusal and rollback.

## Known external blockers
- **Real-client visual parity:** No privileged route to the user's live VoxelHorizons Paper/Geyser host and no real Bedrock client automation. Required evidence: exact server+Geyser+Floodgate+Bedrock versions, generated pack revision, screenshot pairs, interaction logs.
- **Old model corpus:** `unsupported-items.txt` and original content packs are not in this repository; need exported fixture or on-server diagnostic files for specific missing meshes/skins.
- **Geyser custom entities:** Experimental API; verify actual extension build/registration/version on user's running Geyser instance.
- **3rd-party license:** GeyserDisplayEntity is AGPL-3.0; reference only until licensing reviewed.

## Handoff contract for next session
Read this file first, then `ROADMAP.md`, `ARCHITECTURE.md`, `TESTING.md`, `../bedrock.md`; inspect `git log` on `feat/bedrock-pack-publishing` and PR #84, validate current GitHub Actions, implement the first unblocked testable next action, and update this ledger at the next commit. Avoid reopening completed decisions and never claim 100% until all roadmap acceptance criteria are VERIFIED.

## Subsequent cross-plugin work completed after original handoff record
- `VoxelHorizons/VoxelFurniture` draft [PR #47](https://github.com/VoxelHorizons/VoxelFurniture/pull/47) on `feat/bedrock-state-projection`: commit `7147c68b`, **GitHub build SUCCESS**, capture-only versioned render snapshot.
- `VoxelHorizons/VoxelVehicles` draft [PR #21](https://github.com/VoxelHorizons/VoxelVehicles/pull/21) on `feat/bedrock-state-projection`: commit `98748d7a`, **GitHub build SUCCESS**, capture-only vehicle render/physics visual snapshot.
- Core CI: `98c4a32b` distribution build success and 7 of 8 Minecraft smoke tests success. Minecraft 1.14.4 smoke is a genuine unresolved RCON connectivity issue despite fully initialized Spigot/VoxelCore server in its recorded log. IPv4 RCON explicit attempt `9c718fde` did not fix. Commit `7e6fee68` adds socket, process and container health capture for reproducing; review next run logs to find true cause. **Do not bypass the failing matrix job**.
- This session still has **no Bedrock client**, runtime Geyser extension, item-held visual parity or deployed furniture/vehicle entity projection. None of B06/B09/B10/B11/B15/B16/B17/B19 are VERIFIED.
