# Bedrock publishing — first implementation

VoxelCore keeps gameplay authoritative on Paper. Geyser supplies Bedrock resource packs and maps custom items; behavior-pack add-ons are not supported by Geyser. This increment provides explicit flat item textures, generated Geyser v2 item-model mappings, authored Bedrock resource assets, and publishing to a configured Geyser data directory. It is not full Java pack conversion.

## Enable

Use a modern backend (1.21.4+, including 26.3) and a Geyser release with v2 custom item mappings (API 2.9.3 / build 1062 or newer). Older Java adapters use a different item representation and are not supported by this Bedrock compiler yet.

```yaml
game:
  bedrock_support: true
bedrock:
  geyser_data_directory: '/home/container/plugins/Geyser-Spigot'
```

Merge into existing config sections, then reload VoxelCore configuration. Empty `geyser_data_directory` builds exports only. A relative path resolves from the server working directory. The target directory must already exist. For Geyser on a proxy or another machine, configure a mounted path to the actual Geyser data directory, or deploy the exported pair together yourself; writing into a backend-only folder cannot update remote Geyser.

Icons are now generated automatically for supported Java models, including **renderable `abstract: true` GUI definitions**. Abstract item flags restrict creation/give behavior, not Geyser item-model mapping. Add an optional explicit Bedrock texture override to a concrete item (properties inherit through normal content inheritance):

```yaml
items:
  token:
    material: minecraft:paper
    render:
      model: voxel:item/token
    properties:
      bedrock:
        texture: voxel:item/token
```

The source is `assets/voxel/textures/item/token.png` inside a content pack. It becomes a flat Bedrock item icon. The generated selector uses the item's actual `render.model` and vanilla material. Multiple definitions claiming the same selector are rejected. Complex models can use a separately authored icon, but this does not reproduce their 3D rendering or conditional model rules. Items whose models cannot be rendered are listed with reasons in `unsupported-items.txt`.

Optional native Bedrock assets belong under `<content-pack>/bedrock/resource_pack/` and are copied to the pack root. `manifest.json` and `textures/item_texture.json` are generated/reserved. Disjoint object keys in duplicate Bedrock JSON files are merged, while conflicting values and non-JSON byte collisions fail. **Do not copy Java Edition `assets/<namespace>/...` trees under this directory**: these are explicitly rejected, including `assets/minecraft/font/default.json`. Bedrock assets must use native Bedrock paths and schemas such as `textures/`, `ui/`, `font/`, `models/` and `entity/`. No behavior pack is produced.

## Build and publish

1. Run `/vc admin pack build` for the running server's target. Bedrock compilation runs off the main thread alongside the Java publishing workflow, independently of whether Java HTTP hosting is enabled.
2. Review output: `build/resource-packs/bedrock/<revision>/` contains `voxelcore.mcpack`, `voxelcore-items.json`, and `unsupported-items.txt`. Prior revisions remain available. Rebuilding identical inputs reuses the revision.
3. With a configured directory, VoxelCore replaces only `packs/voxelcore.mcpack` and `custom_mappings/voxelcore-items.json`. Individual replacements are staged; an I/O failure attempts rollback. This is not a cross-process atomic transaction: do not reload Geyser during publication.
4. Restart Geyser after publishing, then reconnect Bedrock clients. Registry changes are deliberately not hot-reloaded or followed by an automatic disruptive restart. On a same-process installation, restart the server. Online Bedrock clients cannot accept pack changes mid-session.

Pack header/module UUIDs stay stable. The version tuple changes with the content hash; outputs are deterministic and prior revisions support rollback. To roll back, stop Geyser, restore both files from the same revision, restart, and reconnect clients.

Floodgate and local Geyser are optional soft dependencies. Their public APIs identify Bedrock players so Java pack delivery and its required-pack enforcement do not target those clients. With proxy Floodgate, install/configure backend Floodgate, enable proxy `send-floodgate-data`, and share the correct key as described in Floodgate's documentation. Without a local API exposing a connection, VoxelCore cannot identify it. Configure required Bedrock pack acceptance in Geyser itself; VoxelCore's Java `resource_pack.players.required` does not configure Geyser.

A Bedrock failure is reported separately and does not block the Java build. This first phase does not coordinate content activation across the two runtimes: use a maintenance restart for content registry changes and check both outputs before admitting players.

## Diagnosing the Java/Bedrock UI and furniture mismatch

The current Bedrock output is a **resource pack plus Geyser v2 item mappings**, not an equivalent Java client renderer. Compare both clients using the same loaded content revision and a fresh Bedrock reconnect after Geyser restart.

| Java feature | Why simply copying Java assets does not work | Proposed implementation |
| --- | --- | --- |
| VoxelCore/DeluxeMenus glyph inventory backgrounds | Java bitmap font providers and negative-space glyphs in inventory titles are not a Bedrock UI screen definition | Route Bedrock users to session-driven Bedrock forms for navigation or implement a native Bedrock JSON-UI overlay with explicit compatibility testing. Do not leak private-use glyphs into visible Bedrock titles. Keep Java inventory actions authoritative. |
| Custom profile player shader/3D portrait | Java item/model shaders and player portrait render transforms are Java-only | Use Bedrock-compatible static/avatar preview or native Bedrock UI entity preview when support is verified; never assume shader parity. |
| VoxelCore item browser icons | Geyser mappings need the **exact Java base material + `minecraft:item_model`** at runtime. Existing compiler only reads VoxelCore content definitions and currently generates flat icon previews | Validate each inventory stack's material and item-model component against generated JSON. Map abstract display-only icons too. Add geometry/attachable exports for authored Java 3D item models, not just icon PNGs. |
| VoxelFurniture catalog items | They may be VoxelCore-defined items or VoxelFurniture-created variants; the static VoxelCore compiler does **not** scan every runtime inventory | Define a registry/export protocol for addon items/variants and reconcile material/model/predicates and generated names across plugins. Audit VoxelFurniture inventories alongside VoxelCore. |
| VoxelFurniture placed displays | `DisplayFurnitureRenderer` emits Java `ITEM_DISPLAY`, `TEXT_DISPLAY`, and `INTERACTION` entities, which are not Bedrock native entities | Add a Geyser-side custom-entity/display projection extension using Geyser API 2.11.0+ (experimental). Convert geometry, textures, animation and transformations. Preserve authoritative Paper interaction/ownership/hitbox state. |
| State changes/animations | Native Bedrock clients do not interpret Java display metadata transforms identically | Synchronize models/state, multipart overlays, offsets, rotations, scale, dye, text, transitions and chunk lifecycle; include reconnect/resync tests. |

**Important distinction:** Geyser does not install standard Bedrock behavior packs/add-ons for a Java backend. Resource-pack content (including native entity geometry, attachables, animations and Bedrock UI JSON) can be delivered, while behavior must remain server-authoritative and be projected through the Geyser API or a Geyser extension. The experimental custom-entity API is **not** currently configurable through Geyser JSON mappings alone. Reference: [Geyser custom entities](https://geysermc.org/wiki/geyser/custom-entities/) and [Geyser packs](https://geysermc.org/wiki/geyser/packs/).

**Reference converters/bridges (not bundled):** [Rainbow](https://geysermc.org/wiki/other/rainbow/) already converts many Java 3D item models to Bedrock attachables/animations and generates inventory icons; use its output as a reference fixture when implementing a native VoxelCore compiler, rather than assuming an orthographic 2D icon can render a 3D item. [GeyserDisplayEntity](https://github.com/GeyserExtensionists/GeyserDisplayEntity) is an external, community-supported display-entity extension worth evaluating as a temporary compatibility bridge, not a tested VoxelCore integration. Check its license and compatibility before distribution.

### Cross-plugin work sequence

1. **Build diagnostics and item mapping correctness.** Log concrete VoxelCore model IDs, underlying Bukkit materials and generated Bedrock mappings, and categorize unmapped items. Verify static VoxelCore and abstract GUI items; add addon-export support for VoxelFurniture/VoxelVehicles items.
2. **Native item visuals.** Implement Java cube-model -> Bedrock geometry, texture atlas/material outputs, attachables and transform/animation output; include golden-file converter tests. Unsupported shaders/dynamic rules must remain explicit.
3. **World furniture renderer.** Publish a stable VoxelFurniture placement/render-state feed and a Geyser extension that registers Bedrock entities and projects Java display state into Bedrock equivalents without duplicating world gameplay.
4. **Bedrock GUI.** Prefer an explicit Bedrock frontend routed by Floodgate/Geyser session detection: readable forms and inventory navigation first, native JSON UI artwork where reliable. Keep Java inventory GUI behavior unchanged for Java players.
5. **End-to-end parity harness.** Test profile, inventory catalog, placed chair/table/animated shelf and reconnect on the actual VoxelHorizons Geyser 26.3 stack. Confirm actions, ownership, inventory persistence, animation, scale, unload/reload and no ghost entities.

**Acceptance is visual and functional, not just “pack sent”:** the same source content must show correct items and furniture when held, browsed and placed on Bedrock, with no missing textures, text glyphs, or inaccessible interaction targets. Store paired Java/Bedrock screenshots and actual Bedrock/Geyser version with every acceptance record.

## Remaining roadmap and acceptance tests

- [x] Explicit item textures, v2 item-model mappings, resource manifests and authored assets.
- [x] Revision exports and configured local/shared-directory Geyser publishing.
- [x] Public Floodgate/Geyser detection and Java pack exclusion.
- [x] Automatic single-layer generated textures and headless cube-model inventory previews.
- [x] Include abstract GUI item models in Geyser mappings; merge compatible native Bedrock JSON assets and reject Java-format source trees.
- [ ] Full Bedrock geometry/attachable conversion.
- [ ] Geyser-side extension: authenticated remote revision synchronization, lifecycle-safe registration, pack-only updates and active revision reporting.
- [ ] Block mappings and experimental custom entities for furniture/vehicles, with hitbox/riding tests.
- [ ] Bedrock menu/form frontend and deliberate alternatives for Java glyph overlays, shader portraits and tooltips.
- [ ] Legacy backend mappings, finite component variants and dye/animation parity.
- [ ] Live test on the exact production Geyser/Floodgate versions: join, required-pack refusal, reconnect after update, rollback, item inventory/held appearance and gameplay identity.

Automated compiler tests cover deterministic output, changed revisions, missing assets preserving previous builds, reserved files, and scoped Geyser publication. A successful unit test is not a Bedrock client compatibility test.

References: [Geyser packs](https://geysermc.org/wiki/geyser/packs/), [v2 item mappings](https://geysermc.org/wiki/geyser/custom-items/), [Floodgate API](https://geysermc.org/wiki/floodgate/api/).

### Automatic preview renderer

Without an override, the compiler resolves authored Java model parents and texture references. Single-layer generated items reuse their PNG. Cube models render to transparent 64×64 PNGs with face UVs, UV rotation, element rotation, GUI transforms and depth testing. It is a preview renderer, not the Minecraft rendering engine; lighting/shader parity and translucent-face compositing are not implemented. Conditional models, tint-dependent faces, rescaled elements, missing vanilla assets and special models are reported for overrides. Generated flat items reuse their texture without applying GUI transforms.

Icons are cached across builds in `bedrock/icon-cache`; the key includes the renderer version, model ID and all authored Java assets. Any asset change conservatively invalidates the cache. Explicit texture overrides still take precedence.
