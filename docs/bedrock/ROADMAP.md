# Bedrock Compatibility — Acceptance Roadmap

**Branch:** `feat/bedrock-pack-publishing`
**Primary specification:** [../bedrock.md](../bedrock.md)
**Rule:** a checked implementation is never equivalent to real-client verification. Never mark VERIFIED without evidence. Status vocabulary: NOT STARTED, IN PROGRESS, BLOCKED, IMPLEMENTED, TESTED, VERIFIED. New work must add tests and an evidence record.

## Entry criteria
- Preserve Paper as the source of truth, including inventory/ownership and entities.
- Preserve Java pack and DeluxeMenus behavior.
- Align artifacts, models and mappings to the exact same revision.
- Supported Geyser/Geyser API and Bedrock protocol versions must be recorded during client acceptance.
- A native Bedrock resource pack is **not** a Java resource pack and is **not** a behavior-pack add-on.

## Work packages (IDs are permanent)
| ID | Required outcome / independently verifiable acceptance | Repository | Status | Evidence / blocker |
| --- | --- | --- | --- | --- |
| B01 | Deterministic Bedrock manifest/resource pack and Geyser v2 custom-item mapping; stable identifier and file-path integrity | VoxelCore | TESTED | `BedrockPackCompilerTest` and GitHub build prior to audit; **live rendering unverified** |
| B02 | Safe destination discovery; local and manually mounted Geyser exports; no surprise writes to unrelated server | VoxelCore | TESTED | Publisher tests; proxy/remote path not live-tested |
| B03 | Pairwise pack+mappings deployment with manifest revision verification, rollback failure tests, detection of mixed versions | VoxelCore | IMPLEMENTED | `GeyserPackPublisher` receipt + integrity inspection, preflight ZIP validation and injected rollback tests; CI verification pending on latest SHA |
| B04 | Report mapped/unmapped item identities, error classes, actionable invalid asset failures | VoxelCore | IMPLEMENTED | Generated `unsupported-items.txt`, compiler reason summary; needs live corpus |
| B05 | Full modern Java model resolution: inheritance, variant/submodel/item components, vanilla parents and predicates | VoxelCore | IN PROGRESS | Preview renderer adds inherited multilayer sprites; conditional/tint/rescale/item predicates still unsupported |
| B06 | Real native Bedrock geometry and attachables, not just flat 2D icons, plus held-item transforms and texture registration | VoxelCore | IN PROGRESS | `BedrockGeometryCompiler` exports supported Java cuboids, atlases and attachables; needs CI test result, held-item animations/transform verification and more model types |
| B07 | Native block mappings/state permutations, tint, dyes and animations where supported | VoxelCore | NOT STARTED | Geyser feature version and state matrix to verify |
| B08 | Addon item registration contract for VoxelFurniture, VoxelFurnitureShop and VoxelVehicles | Cross-repo | NOT STARTED | Static VoxelCore compilation does not capture plugin runtime items |
| B09 | Versioned VoxelFurniture visual/state export, interaction remains Paper-authoritative | VoxelFurniture | NOT STARTED | Current Java ITEM_DISPLAY renderer only; issue #46 |
| B10 | Geyser extension projects VoxelFurniture displays to Bedrock native custom entities, with despawn/reconnect/chunk state | Cross-repo | NOT STARTED | Experimental Geyser API; extension tests and live client needed |
| B11 | VoxelVehicles render/movement/attachment projection and server-authoritative riding | VoxelVehicles | NOT STARTED | Java renderer only; no Bedrock module |
| B12 | VoxelCore browser pages don't flash-close; no duplication or lost items on Bedrock | VoxelCore | IMPLEMENTED | Delayed pagination committed; live Bedrock reproduction/verification outstanding |
| B13 | Managed Java glyph migration with immutable backup, safe Bedrock U+E2+ pages, no controller glyph overlap | VoxelCore | TESTED | `UiGlyphCompilerTest`, native glyph test; authored literal usage not migrated |
| B14 | Migration scanner/rewrite for unmanaged literal codepoints across font JSON/DeluxeMenus/rank text, preserving Java and Bedrock paths | Cross-repo | NOT STARTED | Explicitly unresolved in bedrock.md |
| B15 | DeluxeMenus Bedrock forms/native UI frontend, passed player/NPC args and navigation, equivalent permissions | DeluxeMenus | NOT STARTED | Java bitmap overlay cannot be directly ported |
| B16 | Bedrock profile GUI, player portrait/avatar, statistics, tooltips, skins and button parity | Cross-repo | NOT STARTED | User screenshot confirms mismatch |
| B17 | Authenticated remote/proxy Geyser extension, revision integrity/rejection, active-version reporting and controlled reload | Cross-repo | NOT STARTED | File copy is local/mounted only |
| B18 | Java/Paper 26.3 and supported previous backends; deterministic CI packaging plus compatibility floor | VoxelCore | IN PROGRESS | Build matrix exists; older Bedrock mapping strategy not defined |
| B19 | Live Bedrock acceptance (join, pack accept/refusal, browser, held items, furniture, vehicles, reconnect, restart, rollback), paired Java parity evidence | Cross-repo | BLOCKED | Actual VoxelHorizons server and physical Bedrock client are not accessible from this execution environment |
| B20 | All required automated CI, cross-plugin regression suite, documented support boundaries with no critical defects | Cross-repo | NOT STARTED | Dependent on B03–B19 |

## Critical path
B03→B04→B05→B06→B08→B09→B10→B11; B13→B14→B15→B16; B17→B19; B18+B19→B20. Independent work may run concurrently.

## Acceptance and evidence conventions
1. IMPLEMENTED means code exists, not that tests ran.
2. TESTED means specified automated tests passed on a named Git commit. Record CI job/run URL or local command result in TESTING.md.
3. VERIFIED requires the applicable production-like Paper 26.3 / Geyser / Floodgate and Bedrock client versions plus screenshots/logs in TESTING.md.
4. Mark BLOCKED only when the missing external dependency is named with a reproducible next action; work on other IDs.
5. Never infer % complete from arbitrary task counts. See PROGRESS.md for ledger and next action.

## References
- [Geyser custom items](https://geysermc.org/wiki/geyser/custom-items/) — v2 item mappings, predicates, Java item_model material selectors and restart requirement.
- [Geyser API](https://geysermc.org/wiki/geyser/api/) — Geyser extension and plugin APIs.
- [GeyserDisplayEntity](https://github.com/GeyserExtensionists/GeyserDisplayEntity) — experimental reference only, AGPL-3.0 licensing must be reviewed.
