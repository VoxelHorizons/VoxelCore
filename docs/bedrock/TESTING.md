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
| Geyser deployment | `GeyserPackPublisherTest` (planned/added in implementation) | Integrity, destination validation, mixed state and rollback | IN PROGRESS |
| Final shaded artifact | `mvn -U clean package` and CI `Verify browser classes` | All target adapters and packaged classes | TESTED on earlier SHA; new HEAD pending |
| Server matrix | `.github/workflows/mvp-smoke.yml` | Boot, commands and Java versions | IN PROGRESS: individual runs must be linked |
| Geometry/attachables | golden fixture, Java→Bedrock model geometry and UV / GLB? | Actual native Bedrock geometry and held transforms | NOT STARTED |
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
