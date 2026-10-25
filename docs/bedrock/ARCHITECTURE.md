# Bedrock Compatibility — Architecture and Invariants

**Branch:** `feat/bedrock-pack-publishing` — see [ROADMAP.md](ROADMAP.md) and [../bedrock.md](../bedrock.md).

## Current verified structure
- Content packs → `ContentLoader` and Java pack compiler → runtime ItemStack identity/`minecraft:item_model` + Java pack.
- `BedrockPackCompiler` scans VoxelCore item/material selectors and Java-authored models/textures. It outputs the Bedrock pack/mappings plus `unsupported-items.txt` and `unsupported-geometry.txt`. `BedrockIconRenderer` renders 2D icons (including layered sprites); `BedrockGeometryCompiler` adds native cuboid geometry, face atlases and custom-item attachables for a strictly supported model subset, not a world entity bridge.
- `GeyserPackPublisher` writes `packs/voxelcore.mcpack` and `custom_mappings/voxelcore-items.json` under a selected local/mounted directory, plus a trailing `voxelcore-publication.json` receipt holding their SHA-256 hashes and export revision. A filesystem lock serialises cooperating publishers; running Geyser remains separately authoritative.
- `BedrockPlayers` uses detected Geyser/Floodgate client identity. `ContentBrowser` currently uses a readable title and delayed inventory page switch.
- VoxelFurniture and VoxelVehicles retain Paper-side renderers and now publish immutable schema-v1 JSON state through atomic local/mounted-Geyser feeds (draft PRs #47/#21). The Geyser extension validates the native model catalog and replaces only known Paper `item_display` UUIDs on spawn; this **partial** projection does not yet synchronize transforms, resync on reconnect/chunk load, replace text/interaction entities, or implement rider behavior.
- DeluxeMenus continues to author Java inventory/font overlay menus. A Bedrock native frontend is **not implemented**.

## Decisions
**A01 Paper authority:** Geyser never creates authoritative furniture ownership, inventories, hitboxes, seats or currency; client visuals/inputs are projections of existing Paper state.

**A02 One source of content identity:** Do not generate new item IDs from iteration order. Modern selectors require the actual Java base material and `item_model`; custom Bedrock identifiers must be globally unique. Match Geyser mapping format v2. Preserve the Java model/asset semantics when deriving Bedrock geometry.

**A03 Native Bedrock asset rules:** `manifest.json`, `textures/item_texture.json` and generated content cannot collide. Never copy Java `assets/<namespace>/...` directly as native Bedrock assets. Validate file length under 80 bytes for platform portability, and don't rename authored files without rewriting references.

**A04 Revision receipt and non-atomic Geyser activation:** Before changing the target, verify archive manifest and v2 mapping JSON, stage both with a filesystem lock, then write `voxelcore-publication.json` last containing SHA-256 digests and revision. `inspect` reports `VERIFIED_ON_DISK`, `MISMATCH`, `UNTRACKED`, etc. This is *not* live Geyser registry confirmation or a cross-process transaction. Use a maintenance/restart boundary; authenticated remote extension remains required.

**A05 Glyphs:** Bedrock reserves controller/emoji PUA U+E000–U+E1FF. Managed glyphs start at U+E200; preserve original allocation files and migrate through aliases. Authored Java fonts and DeluxeMenus literal characters must be converted together, not silently remapped. Native Bedrock glyph pages have fixed-size slots and cannot emulate full-screen font titles.

**A06 Client UI contract:** Java screens remain unchanged. Bedrock frontend is a separate view of shared menu actions, placeholders, permissions and argument resolution. Do not assume glyph titles, portrait shader or negative spacing transfer automatically.

**A07 Extension compatibility:** Use public Geyser API/event capabilities for versioned API extension. DisplayEntity reference is AGPL-3.0; do not copy its source without a license/redistribution review. Traditional Bedrock behavior packs are not automatically loaded by Geyser.

## Cross-plugin protocol (schema-v1 file feed and spawn lookup IMPLEMENTED; other operations planned)
- Content manifest: `revision` (content digest), `schema` integer, mapping and pack digests, server Minecraft version, Geyser compatibility floor, Java item model selector index.
- Bedrock addon registry: material, Java item_model, Bedrock identifier, geometry/texture keys, active state/dye/variant properties; shared IDs across furniture/vehicle plugins.
- World visual feed: stable Paper entity UUID, world UUID and position, model identifier, transform/scale, variant and animation parameters, lifecycle generation; operation `SPAWN|UPDATE|DESPAWN|RESYNC`. Geyser extension must rehydrate after chunk or client reconnect.
- IPC auth and replay/rollback semantics require a separate threat model, ideally authenticated local transport with versioned handshake, not a public unauthenticated HTTP upload.

## Known gaps / risks
1. Flat 2D icon previews do not provide handheld/placed 3D models.
2. Bedrock clients may remap Java PUA into controller glyphs; Java font JSON is not accepted as native Bedrock font.
3. Current static compiler doesn't ingest live VoxelFurniture and VoxelVehicles model variant registries.
4. Complex shader, per-element tint/rotation and Java item component cases need explicit compatibility tests and supported boundaries.
5. Existing live-user screenshot and logs demonstrate incomplete UI, missing furniture, and page close issues; a passing Maven test is not visual parity.

## Native cuboid exporter (new, limited; not live verified)
- `BedrockGeometryCompiler` reads the same inherited model/texture definitions as `BedrockIconRenderer`. A valid cube element creates a Bedrock `minecraft:geometry` definition and a `minecraft:attachable` whose identifier matches the Geyser item mapping; an image atlas is built for visible faces.
- Native geometry paths: `models/entity/voxelcore/<short-id>.geo.json`, `attachables/<short-id>.json`, `textures/voxelcore/g_<short-id>.png`. The user-facing 2D inventory texture remains separately mapped.
- Model limitations are never silently represented as successful 3D: unsupported complex shader, face tint, cuboid rescale, invalid axis/rotation and missing assets appear in `unsupported-geometry.txt`.
- Geometry exports are for *held* custom items only; placing server-side Java display entities still needs B09/B10. Real-client transform, attachable controller, animations and geometry orientation remain UNVERIFIED; neither source-code compilation nor an image test proves those work.

## Recovery audit: precise entity lifecycle boundaries (24 October 2026)

- Paper sources are `FurnitureManager#bedrockSnapshot()` and `VehicleManager#bedrockSnapshot()`. Their publishers serialize `{schema:1,content_revision:<number>,furniture|vehicles:[...]}` to `voxelcore-projection/<kind>.json` with atomic replacement, 5-tick capture by default, and at-most-10-second unchanged-data heartbeat. Deployment is local/mounted directory only; no authentication/transport over a network.
- `EntityProjectionIndex` maps `entity_links.display_entity_uuid` and explicitly declared furniture ITEM parts, or vehicle `entity_links.visual`, to only entity IDs present in the *active* catalog. Unmapped entities keep normal Geyser handling. `MAX_SNAPSHOT_AGE_MILLIS=60000` prevents indefinite mapping from crashed Paper publishers.
- `VoxelCoreGeyserExtension#onDefineEntities` registers the native catalog only when the disk publication pair verifies. `#onServerEntitySpawn` is a one-time identity substitution. It is **not** a live position/scale/rotation/color/animation feed, cross-world watcher, chunk/reconnect replay, or client active-revision handshake. Never claim B10 VERIFIED on these foundations alone.
- The extensions use Geyser API 2.11 experimental custom-entity events. Check runtime Geyser 2.12 API and actual client attachment/geometry compatibility before enabling for players. GeyserDisplayEntity is intentionally **not** bundled to avoid two competing bridges and AGPL obligations; see UPSTREAM-INTEGRATIONS.md.

## Implemented spawn-time uniform scale (post-recovery, TESTED not VERIFIED)

At Core `200eab4f` with test repair `a5845060`, `EntityProjectionIndex.Projection` now holds model identity and an optional Float scale. The JSON consumer accepts a uniformly equal XYZ furniture vector, furniture-item-part scalar, or vehicle scalar, only when finite, positive and <=64. A non-uniform value leaves Geyser's scale unchanged: no guessed projection is used. `VoxelCoreGeyserExtension#onServerEntitySpawn` applies `GeyserEntityDataTypes.SCALE` using `event.preSpawnConsumer` **only for mapped Paper item-display UUIDs**. Native entities do not yet update their scale when the feed changes; this is spawn-time only. Tests and all eight smoke jobs passed on `a5845060`; all existing checks passed again on `c1f3b536`.

Both companion plugin descriptors now add optional `Geyser-Spigot` dependency for enable-order guarantees (Furniture `2332b3e`, Vehicles `6eb79cb`, both CI builds passing). This does not cover unusual Paper/plugin load managers, proxy-only Geyser or runtime hot-enabling a Geyser instance. No live Geyser/Bedrock client acceptance was performed.
