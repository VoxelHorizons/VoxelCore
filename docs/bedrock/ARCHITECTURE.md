# Bedrock Compatibility — Architecture and Invariants

**Branch:** `feat/bedrock-pack-publishing` — see [ROADMAP.md](ROADMAP.md) and [../bedrock.md](../bedrock.md).

## Current verified structure
- Content packs → `ContentLoader` and Java pack compiler → runtime ItemStack identity/`minecraft:item_model` + Java pack.
- `BedrockPackCompiler` currently scans VoxelCore items and Java authored models/textures, outputs `voxelcore.mcpack`, `voxelcore-items.json`, `unsupported-items.txt`. 2D projected model renderer is not 3D Bedrock geometry.
- `GeyserPackPublisher` writes two paths under a selected local or mounted Geyser data directory: `packs/voxelcore.mcpack` and `custom_mappings/voxelcore-items.json`.
- `BedrockPlayers` uses detected Geyser/Floodgate client identity. `ContentBrowser` currently uses a readable title and delayed inventory page switch.
- VoxelFurniture uses Java display/interaction entities; VoxelVehicles has its own Java-side renderer. There is **no implemented Bedrock entity projection**.
- DeluxeMenus continues to author Java inventory/font overlay menus. A Bedrock native frontend is **not implemented**.

## Decisions
**A01 Paper authority:** Geyser never creates authoritative furniture ownership, inventories, hitboxes, seats or currency; client visuals/inputs are projections of existing Paper state.

**A02 One source of content identity:** Do not generate new item IDs from iteration order. Modern selectors require the actual Java base material and `item_model`; custom Bedrock identifiers must be globally unique. Match Geyser mapping format v2. Preserve the Java model/asset semantics when deriving Bedrock geometry.

**A03 Native Bedrock asset rules:** `manifest.json`, `textures/item_texture.json` and generated content cannot collide. Never copy Java `assets/<namespace>/...` directly as native Bedrock assets. Validate file length under 80 bytes for platform portability, and don't rename authored files without rewriting references.

**A04 Two-file publication is not atomically visible to running Geyser:** Publish pack+mappings under a stopped Geyser process or documented maintenance/restart boundary. Stage both and verify revision/contents before changes; retain previous revisions; restore on failure and report rollback errors. Don't claim this provides live, cross-process atomic transactions. For remote/proxy deployments a versioned authenticated bridge is required.

**A05 Glyphs:** Bedrock reserves controller/emoji PUA U+E000–U+E1FF. Managed glyphs start at U+E200; preserve original allocation files and migrate through aliases. Authored Java fonts and DeluxeMenus literal characters must be converted together, not silently remapped. Native Bedrock glyph pages have fixed-size slots and cannot emulate full-screen font titles.

**A06 Client UI contract:** Java screens remain unchanged. Bedrock frontend is a separate view of shared menu actions, placeholders, permissions and argument resolution. Do not assume glyph titles, portrait shader or negative spacing transfer automatically.

**A07 Extension compatibility:** Use public Geyser API/event capabilities for versioned API extension. DisplayEntity reference is AGPL-3.0; do not copy its source without a license/redistribution review. Traditional Bedrock behavior packs are not automatically loaded by Geyser.

## Planned cross-plugin protocol (design only, NOT implemented)
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
