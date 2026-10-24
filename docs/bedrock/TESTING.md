# Bedrock Compatibility — Test and Evidence Register

**Branch:** `feat/bedrock-pack-publishing`. See [ROADMAP.md](ROADMAP.md). Record the exact artifact SHA, Paper/Geyser/Floodgate/Bedrock versions, test commands and links when run.

## Verified by repository audit (not new live testing)
- Existing CI: `.github/workflows/build.yml` builds Java 1.12→26.3 distributions and checks shaded browser classes; `.github/workflows/mvp-smoke.yml` launches server smoke jobs by version.
- Existing tests: `BedrockPackCompilerTest`, `BedrockIconRendererTest`, `UiGlyphCompilerTest` (unit/integration fixtures); these cannot render a real Bedrock window.
- The prior conversation recorded passing GitHub distribution build for commit `8c1f4634297fb10f241f8e8af690a0bf3f2ccfd8` and its tested predecessors. Do not transfer that result automatically to later commits.
- User-provided paired Java/Bedrock screenshots on 2026-10-24 show missing profile visuals, built-in PlayStation controller symbols and missing furniture models. Previous log: 69 mapped, 527 unsupported before later updates. These are **failure evidence**, not acceptance.

## Automated matrix
| Suite | Invocation / fixture | Scope | Status |
| --- | --- | --- | --- |
| Pack compiler | `mvn -pl voxelcore-pack -am test` | Item maps, pack archive, icon paths, glyph pages, deterministic builds | TESTED on earlier SHA; rerun for active SHA |
| Geyser deployment | `GeyserPackPublisherTest` | Revision digest inspection, fake failure rollback, corrupt source, legacy untracked | IMPLEMENTED, latest CI status pending |
| Final shaded artifact | `mvn -U clean package` and CI `Verify browser classes` | All target adapters and packaged classes | TESTED on earlier SHA; new HEAD pending |
| Server matrix | `.github/workflows/mvp-smoke.yml` | Boot, commands and Java versions | IN PROGRESS: individual runs must be linked |
| Geometry/attachables | `BedrockGeometryCompilerTest` | Native cuboids, UV atlas, parent/child textures, attachable references, strict tint/rescale rejection | IN PROGRESS, latest CI pending; **held animation and real-client orientation unverified** |
| Cross-plugin addon | Registry fixture plus metadata/live API integration | Furniture/vehicle variants | NOT STARTED |
| Geyser extension | API test, entity lifecycle and chunk resync | Bedrock display projection | NOT STARTED |
| Menu/Unicode | JVM tests and session trace | No PUA controller collision, page 1→2→3→2 | IMPLEMENTED, live verification outstanding |

## Live acceptance procedure — BLOCKED without running server/client access
1. Record versions, current Java and Bedrock pack revision, Geyser/Floodgate plugin metadata and active configuration.
2. Run `/vc admin pack build`; confirm matching files in **actual** Geyser `packs/` and `custom_mappings/` and review unsupported report.
3. Restart Geyser/Paper as required, reconnect Bedrock, accept pack and compare paired Java/Bedrock profile screenshots. Test refusal policy separately.
4. Verify custom item browser page transitions 1→2→3→2 and close, inventory mutation safety, 3D held items and cube/bench variants.
5. Place/rotate/dye furniture and vehicles; test seating, WorldGuard/Lands ownership and inventories, unloading chunks, reconnect and restart.
6. Stage an earlier revision and roll back pack+mappings with maintenance restart; confirm no mixed revision and no stale client cache.
7. Capture logs/screenshots under an evidence folder (or issue attachment) and mark each criterion VERIFIED only after matching versioned evidence.

## Failure register
- FB01: Bedrock controller PUA collision in profile titles/tooltips (managed migration only; raw authored aliases remain).
- FB02: Only some furniture preview icons rendered in earlier client tests, remainder unsupported; need current `unsupported-items.txt`.
- FB03: Bedrock browser next page flashes/closes; delayed page change added but live retest missing.
- FB04: Placed Java display entity furniture invisible on Bedrock; Geyser bridge missing.
- FB05: Remote Geyser deployment and full revision consistency not implemented; only local/mounted pair copy.

## Current development evidence
- `b7f01f88` — publication receipts, SHA-256 pair verification, preflight archive validation, cooperative cross-process lock, rollback attempts.
- `7e4299f2` — publisher regression tests incl. injected second-file failure, initial state rollback, corrupt input and tampering.
- `fbbb6de8` / `4f0ef87c` — generated multilayer item texture compositing and inheritance regression tests.
- `87dd692d` / `1037adc3` — first native cuboid+attachable exporter and compiler integration.
- `2b380d3d` — cuboid tests; first CI build failed because oversized Java coordinates produced transparent atlas tiles. Regression was **not deleted**.
- `e5013dca` — corrected face raster UV source-bound sampling. Full CI outcome must be recorded from the final HEAD, not assumed.

## Cross-plugin implementation and GitHub CI (2026-10-24)
- [VoxelFurniture PR #47](https://github.com/VoxelHorizons/VoxelFurniture/pull/47), commit `7147c68b88bcac50bab6b956f63bfb126ffb95f0`, GitHub build jobs `120803682285` and `120803579986`: **SUCCESS**. Tests assert deterministic/immutable visual projection and error on unknown furniture definitions. This is not rendering verified on Bedrock.
- [VoxelVehicles PR #21](https://github.com/VoxelHorizons/VoxelVehicles/pull/21), commit `98748d7a4e95c0e37418fd5afb038287d5978511`, GitHub build job `120804509111`: **SUCCESS**. Tests assert immutable visual/rider/geometry-state metadata and no cargo replication. This is not a working Geyser extension.
- VoxelCore commit `98c4a32bd76d598d93bf13ed57efd213ebf6bffe`: Maven/distribution build **SUCCESS**, all smoke jobs except Minecraft 1.14.4 succeeded. The 1.14.4 smoke log shows `VOXELCORE_READY`, `Done`, and RCON starting on `0.0.0.0:25575` but `rcon-cli` refused the socket. Subsequent forced `127.0.0.1` smoke still refused RCON. Container socket/state diagnostics were added in commit `7e6fee680f44dfd11b12ef3c064f3ccf22bb2ed3`. This CI failure remains unresolved; don't claim the full matrix passed.

## Recovery audit and current CI (24 October 2026)

- **Core recovery HEAD `64b8a3275c424c3cdac0892d6f5cc53bce780255`:** [distribution](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40072434517) **SUCCESS**; [smoke](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40072434436) **FAILURE**, with 1.13.2 and 1.14.4 failing and 1.12.2, 1.19.4, 1.20.5, 1.21.4, 26.2, 26.3 passing. Failing containers start Spigot and VoxelCore successfully (`VOXELCORE_READY`), and initially report RCON listening at `0.0.0.0:25575`, but later reject localhost RCON commands and show no matching socket. The JVM process was alive and not OOM-killed. This is **unresolved**, not proven an application regression or environment flake.
- **VoxelFurniture `cd90266be94255ed887cec966ebc0133916df3bc`:** [build success](https://github.com/VoxelHorizons/VoxelFurniture/actions/runs/40072620582) for schema-v1 snapshot and atomic JSON publisher tests. No Bedrock client evidence.
- **VoxelVehicles `cd28c5d3f001144846c15e77dee534616d12c623`:** [build success](https://github.com/VoxelHorizons/VoxelVehicles/actions/runs/40072624355) for vehicle snapshot and atomic publisher tests. No Bedrock client evidence.
- **Core `363147abd04889d84acb3cf77f02b4410f58ff1c`:** first recovery fix expiring stale Paper display projection files, with regression tests `stalePaperFileDoesNotReplaceNewDisplaysAndExpiresCachedMappings` and `expiringOneFeedPreservesOtherFreshModelMappings`. [distribution run](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40076547872), [smoke run](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40076547878) **IN PROGRESS at document creation**. Promote this change to TESTED only after observing a successful build; do not imply a live render test.

Next reproducible CI diagnosis: capture RCON port 25575 with `ss -lntp`/`/proc/net/tcp*`, a JVM thread dump (`jstack` or SIGQUIT diagnostics) while the RCON retry loop fails, Java process CPU state, and evidence of whether RCON's listener thread exited. Never disable those matrix versions just to achieve green.

## Verified current post-recovery automated evidence

| Repository and SHA | Check | Result | Scope |
| --- | --- | --- | --- |
| Core `a58450602d6cd7d558baeb3315e6e19982e19f2f` | [Distribution 40077155518](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40077155518) | **SUCCESS** | Compiled extension and new scale regression tests |
| Core `a58450602d6cd7d558baeb3315e6e19982e19f2f` | [Smoke 40077155527](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40077155527) | **SUCCESS, 8/8** | All supported Java/Paper smoke matrix entries; no live Bedrock client |
| Core `c1f3b5360f9534fe7d2f5b5ea2f095aa2402380b` | [Distribution 40077217228](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40077217228) | **SUCCESS** | Includes failure-only JVM RCON diagnostics |
| Core `c1f3b5360f9534fe7d2f5b5ea2f095aa2402380b` | [Smoke 40077217237](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40077217237) | **SUCCESS, 8/8** | Intermittent legacy RCON failure not observed in this run |
| Furniture `2332b3ebea2cfcee41bbe97bf59bf5e1c148280f` | [Build 40077356575](https://github.com/VoxelHorizons/VoxelFurniture/actions/runs/40077356575) | **SUCCESS** | Optional Geyser enable-order fix / snapshot build |
| Vehicles `6eb79cbf84bebc24d262e3a9c72d92a047299ead` | [Build 40077363366](https://github.com/VoxelHorizons/VoxelVehicles/actions/runs/40077363366) | **SUCCESS** | Optional Geyser enable-order fix / snapshot build |

The earlier RCON failure at Core `64b8a327` was observed again intermittently at `a85f9bef` and passed at `a5845060`/`c1f3b536`. The exact root cause is unresolved; failure-only HotSpot SIGQUIT diagnostics have been added but not triggered on the latest successful run. Tests passed is not visual verification.
