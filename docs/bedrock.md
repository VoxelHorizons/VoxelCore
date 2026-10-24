# Bedrock publishing — first implementation

VoxelCore keeps gameplay authoritative on Paper. Geyser supplies Bedrock resource packs and maps custom items; behavior-pack add-ons are not supported by Geyser. This increment provides explicit flat item textures, generated Geyser v2 item-model mappings, authored Bedrock resource assets, and publishing to a configured Geyser data directory. It is not full Java pack conversion.

## Enable

Use a modern backend (1.21.4+, including 26.3) and a Geyser release with v2 custom item mappings (API 2.9.3 / build 1062 or newer). Older Java adapters use a different item representation and are not supported by this Bedrock compiler yet.

On the **Geyser** side, ensure `gameplay.enable-custom-content: true` in the Geyser configuration, then restart Geyser after changing it. Otherwise its custom item/block/skull mapping systems are disabled. Confirm `voxelcore.mcpack` is in Geyser's `packs/` directory, `voxelcore-items.json` is in `custom_mappings/`, and inspect the actual `unsupported-items.txt` revision report. If Geyser runs on a proxy/another machine, these files must be on that Geyser instance; exporting to a Paper-only directory is not enough.

```yaml
game:
  bedrock_support: true
bedrock:
  auto_publish_local: true
  geyser_data_directory: '' # Auto-detect a loaded local Geyser-Spigot plugin
```

Merge into existing config sections, then reload VoxelCore configuration. With `auto_publish_local: true` (the default), an empty `geyser_data_directory` now uses the **actual data folder of a loaded local Bukkit Geyser-Spigot plugin**, not a guessed directory name. A configured non-empty `geyser_data_directory` always takes precedence, including when local auto-publication is disabled. A relative path resolves from the server working directory; configured targets must exist. For Geyser on a proxy or another machine, configure a mounted path to the real Geyser data directory, or deploy the exported pair together yourself; writing into a backend-only folder cannot update remote Geyser. When no local Geyser is installed and the override is blank, VoxelCore exports only and explains why in the build output. To force export-only even when local Geyser is installed, set `bedrock.auto_publish_local: false` with an empty path.

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
2. Review output: `build/resource-packs/bedrock/<revision>/` contains `voxelcore.mcpack`, `voxelcore-items.json`, `unsupported-items.txt` and `unsupported-geometry.txt`. The last file tracks cases where an icon exists but native 3D attachable conversion cannot safely represent the model. Prior revisions remain available.
3. With a configured or auto-detected local Geyser directory, VoxelCore publishes `packs/voxelcore.mcpack`, `custom_mappings/voxelcore-items.json`, and a trailing `voxelcore-publication.json` integrity receipt. `/vc admin pack info` inspects revision and SHA-256 equality; `VERIFIED_ON_DISK` proves only the two files match the receipt, **not** that running Geyser loaded them. A filesystem lock and rollback attempts protect cooperative deployments from mixed writes; crash/power-loss atomicity across Geyser is not guaranteed. Restart Geyser and reconnect to activate. **No Geyser extension jar exists yet.**
4. Restart Geyser after publishing, then reconnect Bedrock clients. Registry changes are deliberately not hot-reloaded or followed by an automatic disruptive restart. On a same-process installation, restart the server. Online Bedrock clients cannot accept pack changes mid-session.

Pack header/module UUIDs stay stable. The version tuple changes with the content hash; outputs are deterministic and prior revisions support rollback. To roll back, stop Geyser, restore both files from the same revision, restart, and reconnect clients.

Floodgate and local Geyser are optional soft dependencies. Their public APIs identify Bedrock players so Java pack delivery and its required-pack enforcement do not target those clients. With proxy Floodgate, install/configure backend Floodgate, enable proxy `send-floodgate-data`, and share the correct key as described in Floodgate's documentation. Without a local API exposing a connection, VoxelCore cannot identify it. Configure required Bedrock pack acceptance in Geyser itself; VoxelCore's Java `resource_pack.players.required` does not configure Geyser.

A Bedrock failure is reported separately and does not block the Java build. This first phase does not coordinate content activation across the two runtimes: use a maintenance restart for content registry changes and check both outputs before admitting players.

## Diagnosing the Java/Bedrock UI and furniture mismatch

The current Bedrock output is a **resource pack plus Geyser v2 item mappings**, not an equivalent Java client renderer. Compare both clients using the same loaded content revision and a fresh Bedrock reconnect after Geyser restart.

| Java feature | Why simply copying Java assets does not work | Proposed implementation |
| --- | --- | --- |
| VoxelCore/DeluxeMenus glyph inventory backgrounds | Java bitmap font providers and negative-space glyphs in inventory titles are not a Bedrock UI screen definition | VoxelCore's administrative item/block browser now uses readable `ui.browser.bedrock_titles` (with optional 1-based `bedrock_page_titles`), bypassing the Java glyph resolver while retaining its inventory actions. DeluxeMenus and the profile UI still require their own Bedrock frontend/form integration, or a verified native Bedrock JSON-UI overlay. |
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

### October 24: Bedrock inventory pagination, glyph migration and furniture icons

**Inventory navigation:** The VoxelCore item/block browser now schedules next/previous page and close actions outside of Bukkit's `InventoryClickEvent`. Bedrock gets a two-tick delay to avoid the old Geyser container-close acknowledgement closing the newly opened page. The queued action only replaces the prior browser, or the player's standard crafting screen if Geyser already closed it; it never replaces another plugin's GUI. Verify pages 1 → 2 → 3 → 2 on Bedrock and Java with the same inventory contents and no flash-close.

**Managed VoxelCore glyph allocations:** Bedrock reserves `U+E000..U+E1FF` for controller symbols/emoji, including PlayStation R1 (`U+E025`). New automatic VoxelCore UI allocations start at `U+E200`. On successful Java pack builds, old **active and inactive** managed `glyph-allocations.yml` entries in Bedrock's range are remapped deterministically; the original allocation manifest is preserved once at `glyph-allocations.yml.before-bedrock-migration`, and `glyph-bedrock-migration.txt` reports old and new codepoints. Explicit legacy `symbol` values in managed UI definitions are also remapped at compilation. Use `:glyph_alias:` rather than literal Unicode to follow new allocations.

**Native Bedrock inline fonts:** When the live VoxelCore Java build has persisted glyph allocations, the Bedrock compiler exports small **managed, non-GUI** icon bitmaps (up to 32×32 source pixels and scale ratio up to 32) into native `font/glyph_E2.png`, `glyph_E3.png`, etc. Each page is a 16×16 character grid with 16px native tiles, nearest-neighbour scaling and no use of vanilla `glyph_E0/E1` controller sheets. Existing authored Bedrock font pages can be filled only in vacant cells; occupied managed codepoints cause a clear collision error. Bigger pictures, negative spacing and oversized GUI backgrounds are excluded; this native glyph-sheet technique does not display in every newer Bedrock Ore UI context. The pack reports the number of mapped inline glyphs independently of the mapped items. <https://wiki.bedrock.dev/text/custom-emojis.html>

**Unmanaged authored font JSON:** User-authored `assets/**/font/*.json` files are separate from managed VoxelCore UI definitions. Their Bedrock-reserved codepoints are **audited and listed** in the report, not automatically rewritten. The same literal characters may be hardcoded into DeluxeMenus YAML, tags, ranks or item lore; blindly rewriting the font without those references would break Java screens. Rewrite these definitions and references together, or use Bedrock-only readable text/native forms. A raw Java font glyph cannot be transferred directly into Bedrock's hard-coded PlayStation icon slots. This increment therefore does not make the profile GUI visually equivalent to Java.

**3D-to-2D furniture icons:** The icon renderer now merges nested inherited `display.gui` transforms, centers/fits elongated furniture geometry in 64×64 PNGs, and recognizes common vanilla cube-model parents even when their vanilla JSON isn't in the content pack. Its cache version changes to force re-rendering of old previews. For an item with an unusual Java authoring camera, set `properties.bedrock.gui_rotation: [30, 225, 0]` (Euler degrees, X/Y/Z) to override its Bedrock **preview renderer** without requiring a separate `properties.bedrock.texture`. Change those values only if the automatically inherited GUI view differs from the intended Java item presentation; flat generated-sprite icons do not have camera transforms. Automatic icon conversion is still a subset of Java rendering: tint-index, dynamic conditional models, missing model/texture paths, special Java shaders and unsupported parent types may remain unsupported. Pack build logs now summarize the `unsupported-items.txt` reasons, making the original 527 failures actionable rather than assuming child texture inheritance is itself broken.

**Acceptance checks:** Run a full build; confirm both managed glyphs and original manifest are preserved, inspect `glyph-bedrock-migration.txt` for remaining authored collisions, verify rotated/centred parent and child furniture models render with different textures, and inspect the reported unsupported-reason groups before attempting further conversion. Restart Geyser and reconnect Bedrock after publishing updated atlas/mappings.

### 26.3 regression: excessive texture path lengths and browser command crash

- Generated icon names now use a deterministic SHA-256-derived 128-bit identifier (`voxelcore:i_<32-hex>`) instead of hex-encoding the *entire* Java content ID. The mapping `bedrock_identifier`, item atlas `icon`, texture entry and file path are all generated from the same short ID. Hash collisions are rejected. Generated texture paths remain below Geyser's 80-character warning threshold, even for very long furniture variant names. **This changes the Bedrock resource pack revision and mapping identifiers**, so always publish the two files as a pair before restarting Geyser/reconnecting.
- Native authored assets are not silently renamed because other native Bedrock JSON/UI resources may reference them. A path of 80 or more UTF-8 bytes fails the build with an actionable filename instead of uploading a known non-portable pack.
- `/vc admin item ui` and `/vc admin block ui` previously failed on **both Java and Bedrock** with `NoClassDefFoundError: ContentBrowser$1`. The anonymous Java comparator responsible for that class is now a Java 8 method-reference comparator, removing the runtime dependency on the missing class. CI also checks for the actual browser and nested holder/type classes inside every shaded distribution JAR.
- Acceptance: no `textures/voxelcore/... meets or exceeds 80 characters` warnings on Geyser 2.12; the two inventory browser commands open, page and close on **both clients**. Inspect the **real compiled JAR** and restart Paper, rather than hot-replacing it.

### Local Geyser publication smoke test

1. Confirm Geyser-Spigot is loaded on the *same* Paper server, or set `bedrock.geyser_data_directory` explicitly to a mounted proxy/standalone Geyser data directory.
2. Run `/vc admin pack info`. It should identify either `detected local plugin` or `configured path` and list the expected `packs/voxelcore.mcpack` and `custom_mappings/voxelcore-items.json` destinations.
3. Run `/vc admin pack build`. Look for `Published to detected local plugin:` (or `configured path:`) **and the actual absolute paths and file sizes**. If it says `Export only`, it is not a successful Geyser deployment; follow the reason.
4. Check both files exist in the **same Geyser data directory** that Geyser uses. A pre-existing empty `plugins/Geyser-Spigot` directory is not by itself evidence that Geyser runs locally.
5. Restart Geyser, reconnect Bedrock, and inspect `unsupported-items.txt` for the list of models that have no valid Bedrock mapping. The pack cannot render unconverted models merely because it was deployed.
6. The `extensions/` directory will still be unchanged because the initial publisher installs only mappings and resource packs; a custom-display/entity extension is a separately tracked implementation.

## Remaining roadmap and acceptance tests

- [x] Explicit item textures, v2 item-model mappings, resource manifests and authored assets.
- [x] Revision exports and configured local/shared-directory Geyser publishing.
- [x] Public Floodgate/Geyser detection and Java pack exclusion.
- [x] Automatic single-layer generated textures and headless cube-model inventory previews.
- [x] Include abstract GUI item models in Geyser mappings; merge compatible native Bedrock JSON assets and reject Java-format source trees.
- [x] Use Bedrock-specific readable titles for VoxelCore's administrative item/block browser without Java glyph rendering.
- [ ] Full Bedrock geometry/attachable conversion.
- [ ] Geyser-side extension: authenticated remote revision synchronization, lifecycle-safe registration, pack-only updates and active revision reporting.
- [ ] Block mappings and experimental custom entities for furniture/vehicles, with hitbox/riding tests.
- [ ] Bedrock menu/form frontend and deliberate alternatives for Java glyph overlays, shader portraits and tooltips.
- [ ] Legacy backend mappings, finite component variants and dye/animation parity.
- [ ] Live test on the exact production Geyser/Floodgate versions: join, required-pack refusal, reconnect after update, rollback, item inventory/held appearance and gameplay identity.

Automated compiler tests cover deterministic output, changed revisions, missing assets preserving previous builds, reserved files, and scoped Geyser publication. A successful unit test is not a Bedrock client compatibility test.

References: [Geyser packs](https://geysermc.org/wiki/geyser/packs/), [v2 item mappings](https://geysermc.org/wiki/geyser/custom-items/), [Floodgate API](https://geysermc.org/wiki/floodgate/api/).

### Automatic preview renderer

Without an override, the compiler resolves authored Java model parents and texture references. Single-layer generated items reuse their PNG. Cube models render to transparent 64×64 PNGs with face UVs, UV rotation, element rotation, GUI transforms and depth testing. It is a preview renderer, not the Minecraft rendering engine; lighting/shader parity and translucent-face compositing are not implemented. Tint-dependent faces, missing vanilla assets and special models are reported for overrides. Generated flat items reuse their texture without applying GUI transforms.

Icons are cached across builds in `bedrock/icon-cache`; the key includes the renderer version, model ID and all authored Java assets. Any asset change conservatively invalidates the cache. Explicit texture overrides still take precedence.

### Native cuboid/attachable exporter (implementation slice, not verified gameplay parity)

`BedrockGeometryCompiler` creates Bedrock `minecraft:geometry` JSON, a matching custom-item `minecraft:attachable` JSON, and UV atlas textures for Java element-based models whose inheritance, axes, cuboid rotation and face textures can be safely resolved. It uses the same generated Bedrock identifier as the item v2 mappings. 2D inventory icon mapping is preserved. Unsupported 3D features are reported separately in `unsupported-geometry.txt` and never silently presented as supported.

This is not equivalent to completed held-item animations or placed furniture: real-client orientation, hold transforms, texture UV correctness on all faces and item attachable compatibility must be validated with the actual Geyser/Bedrock stack. Conditional `render.rule` items (select/condition/range on structured custom_model_data) become one Bedrock item per rule leaf; the Geyser v2 mapping gives each leaf the path's `match`/`condition`(`expected`)/`range_dispatch` predicates and a descending `priority`, reproducing Java's evaluation order (Geyser sorts same-model definitions by priority; range matches `value >= threshold`). This needs `render-allocations.yml` from a Java pack build; without it such items stay in `unsupported-items.txt`. The default (last) leaf supplies the placed-entity model. Java `rescale: true` is converted exactly (perpendicular-axis stretch by 1/cos(angle) about the rotation origin, which commutes with the rotation) and single-axis flat planes (crosses, decals) are kept as zero-thickness Bedrock cubes. Tinted faces/layers are baked with an author-chosen constant colour from `properties.bedrock.tints` (e.g. `{0: '#48b518'}` for foliage) using Java's per-channel multiply; without it they remain reported. Runtime tint sources (dye, potion) and arbitrary shader materials remain unsupported. Java display entities continue to require a Geyser extension. Source: [Microsoft Bedrock attachables](https://learn.microsoft.com/en-us/minecraft/creator/documents/addcustomitems?view=minecraft-bedrock-stable), [Bedrock geometry schema](https://github.com/bedrock-studio/bedrock-json-schemas/blob/master/geometry.1.12.0.json).

### Companion plugin state projections (draft PRs, do not merge automatically)

- [VoxelFurniture #47](https://github.com/VoxelHorizons/VoxelFurniture/pull/47): `BedrockFurnitureSnapshot` captures a versioned, immutable Paper-origin visual representation: definition/model identity, world position, display parts and their Paper entity associations, transforms, dye/animation, view distance and hitbox metadata. `FurnitureManager#bedrockSnapshot()` must be called on the server thread. No ownership/inventory/interaction duplication.
- [VoxelVehicles #21](https://github.com/VoxelHorizons/VoxelVehicles/pull/21): analogous `BedrockVehicleSnapshot` for model, world position, orientation/suspension, colours, rider association, seat/hitbox offsets and chest visual entity associations. Again, Paper retains physics, cargo and authoritative interactions.

Both companion PRs have passing repository build checks. Neither includes a Geyser extension or cross-process transport. They provide stable read-only APIs on which those later components can depend.


### Remote / proxy Geyser publication (B17)

For Geyser on a proxy or another host, set `bedrock.remote_sync.secret` (32+ characters) in
VoxelCore's `config.yml` and enable `resource_pack.host`. Each Bedrock build then exports
`remote.json`, `voxelcore.mcpack` and `voxelcore-items.json` to
`plugins/VoxelCore/build/resource-packs/bedrock-remote/`, served at `/bedrock/<file>` only to
requests carrying `Authorization: Bearer HMAC-SHA256(secret, "voxelcore-bedrock-pull|1")`.

On the Geyser side create `<geyser>/extensions/voxelcore/remote-sync.properties` (the
extension's data folder):

```properties
url=https://pack.example.net/bedrock
secret=<same secret>
# allow_insecure_http=true   # only on a private network; loopback http is always allowed
```

At Geyser pre-initialisation (before packs and custom mappings are read) the extension
downloads the bundle, verifies the HMAC signature over revision + both SHA-256 digests +
issue time, checks the digests, refuses a bundle older than the last one it installed
(rollback/replay) or issued in the future, then installs the pack, mappings and the standard
`voxelcore-publication.json` receipt atomically with rollback. Any failure keeps the previous
verified publication. Activation still requires a Geyser restart, as for local publishing.

**Live projection feeds over the network.** On each Paper backend set
`bedrock.remote_sync.projection_directory` (e.g. `VoxelCore/projection-export`) and point
VoxelFurniture/VoxelVehicles `bedrock.projection.geyser-data-directory` at that folder
(`plugins/VoxelCore/projection-export`). VoxelCore serves `/bedrock/projection/<kind>.json`
signed per response (HMAC over kind, issue time = file mtime, body SHA-256). In the Geyser
extension's `remote-sync.properties` add one line per backend:

```properties
projection.survival.url=https://survival.example.net:8173/bedrock
projection.lobby.url=https://lobby.example.net:8173/bedrock
```

The extension polls every second, ignores unsigned, forged or replayed responses, and mirrors
each backend to `voxelcore-projection/<kind>-<backend>.json` with the backend's issue time, so
an unreachable backend's displays expire after 60 s. Shared mounted folders can instead use
the companions' `bedrock.projection.feed-id`.

### Older backends (B18)

Bedrock mappings follow the backend's Java item identity: 1.21.4+ maps by `item_model` (and
`render.rule` predicates); 1.14–1.21.3 maps with Geyser v2 `{"type": "legacy",
"custom_model_data": N}` definitions using the stable number from `render-allocations.yml`
(build the Java pack first). 1.12/1.13 damage-based identities have no Geyser predicate and
are rejected with a clear message.
