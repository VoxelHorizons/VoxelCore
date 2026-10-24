# Bedrock dependency and deployment contract

## Repositories (all development PRs are unmerged)
- VoxelCore: `feat/bedrock-pack-publishing` — [PR #84](https://github.com/VoxelHorizons/VoxelCore/pull/84), recovery starting at `64b8a327`, latest pre-checkpoint code SHA `c1f3b536`.
- VoxelFurniture: `feat/bedrock-state-projection` — [PR #47](https://github.com/VoxelHorizons/VoxelFurniture/pull/47), `2332b3e`, CI success (Geyser-Spigot softdepend).
- VoxelVehicles: `feat/bedrock-state-projection` — [PR #21](https://github.com/VoxelHorizons/VoxelVehicles/pull/21), `6eb79cb`, CI success (Geyser-Spigot softdepend).

## Startup / deployment sequence (NOT proof of working Bedrock rendering)
1. Install a supported Paper/VoxelCore build and the compatible draft-build furniture/vehicle plugins on the same Paper backend; ensure each plugin configuration deliberately enables its `bedrock.projection` publisher (default enabled when an eligible local Geyser exists).
2. Build the VoxelCore Bedrock pack and mapping pair for the exact current content revision. Publish into the **actual** Geyser data directory, not Paper's unrelated plugin folder. Review `voxelcore-publication.json` digest receipt and missing-model reports.
3. Install the separately produced VoxelCore Geyser extension JAR under Geyser's extensions directory. It is **not** created/published automatically by VoxelCore's pack publisher. Use an API-2.11-or-later compatible Geyser and enable custom content; recheck runtime API compatibility.
4. For local Geyser-Spigot, publishers auto-detect its data folder if enabled. For mounted remote proxy deployments, set each companion's `bedrock.projection.geyser-data-directory` explicitly; all must point at the same Geyser data folder. No authenticated remote push exists. Reboot/reconnect so the running Geyser registry loads the published revision.
5. Verify presence and timestamp freshness of `voxelcore-projection/furniture.json` and `vehicles.json`; validate the extension startup log and `/status` Geyser extension command. Schema-v1 feed only maps model identity on Java display spawn. Motion, animation, seating, reconnect and chunk rehydration are still incomplete; do not deploy with full-parity expectations.

## API and safety contract
- Paper is authoritative for world entities, ownership, economy, inventories and rider state.
- JSON schema version `1`, fields `content_revision`, and model IDs must match the generated native catalog. The Geyser reader rejects unknown model IDs.
- Publishers write atomically on a 5-tick capture by default and renew unchanged documents within ~10 seconds; Geyser ignores feeds older than 60 seconds (reader safeguard at `363147a`).
- Do not combine two uncoordinated display-replacement extensions; check Geyser API custom entity lifecycle and version before promoting B10.
- Real Bedrock client visual and interaction acceptance remains BLOCKED without deployed Geyser and a recorded client.

## Current checked CI and precise feature ceiling (2026-10-24)

Core at `c1f3b536`: [distribution](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40077217228) SUCCESS, [all eight server smoke jobs](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40077217237) SUCCESS. Furniture `2332b3e`: [build](https://github.com/VoxelHorizons/VoxelFurniture/actions/runs/40077356575) SUCCESS; Vehicles `6eb79cb`: [build](https://github.com/VoxelHorizons/VoxelVehicles/actions/runs/40077363366) SUCCESS.

Supported visual scope is **registered custom Bedrock native entity identity plus optional bounded uniform spawn scale**, gated by known Paper UUIDs and a fresh atomic snapshot. This is **not** a demonstrated full Bedrock rendering pipeline or a runtime world-entity state update/resync transport. Real Bedrock client acceptance is outstanding.
