# Bedrock — Geyser publishing, projection and operations

> **Development and acceptance status:** [the sole active roadmap](../ROADMAP.md#bedrock-work-package-acceptance-register-b01b20).
>
> This guide is for **deployment, architecture limits, diagnostics and reproducible testing**. It is not another task ledger. Keep the exact VoxelCore build, Paper/Java, Geyser, Floodgate, Bedrock client and pack revision with any reported failure. Do not equate a successful pack build or GitHub test with real-client rendering.

## Architecture and support boundary

Paper/VoxelCore owns item identity, interaction, placement, permissions and custom blocks. VoxelFurniture and VoxelVehicles own their respective world entities, seats, inventory, ownership and simulation. Geyser provides Bedrock resource-pack and Geyser v2 item mappings; VoxelCore's **separately installed** Geyser extension projects *known Paper display entities* to native Bedrock definitions using published visual snapshots. It must never become a second authoritative world/inventory/economy writer.

Bedrock resources are not Java `assets/<namespace>` directories. Geyser does **not** load arbitrary Bedrock behavior packs on Java backends. Java shader portraits, fullscreen glyph layout and negative-space font titles cannot be assumed to work natively on Bedrock. The generated Java-style chest background supports **Bedrock Classic UI**; Pocket UI is a separate compatibility problem.

Supported compiler mappings depend on the underlying Java item representation: modern 1.21.4+ item-model data with predicates, limited 1.14–1.21.3 numeric-CMD `legacy` Geyser v2 mappings when current `render-allocations.yml` is present, and **no damage-predicate Bedrock mapping support for 1.12/1.13**. The Geyser extension uses experimental custom-entity APIs introduced in Geyser 2.11; test the **exact** installed Geyser build. Do not deploy two overlapping display replacement extensions without conflict analysis.

## Configure local publishing

Enable Geyser's custom content in the applicable Geyser configuration (`gameplay.enable-custom-content: true`), then restart Geyser. Merge into VoxelCore `config.yml`:

```yaml
game:
  bedrock_support: true
bedrock:
  auto_publish_local: true
  geyser_data_directory: '' # detect an actual loaded local Geyser-Spigot folder
```

An explicit `bedrock.geyser_data_directory` always takes precedence and must refer to an **existing actual** Geyser data directory; relative paths resolve from the server's working directory. The empty path can auto-detect a local loaded Geyser-Spigot instance. A standalone/proxy Geyser elsewhere is **not** updated just because a similarly named folder exists on the Paper machine. Without a valid destination, builds export only and print the reason. To force export-only with local Geyser installed, set `auto_publish_local: false` and leave the directory empty.

Install a compatible built JAR from the `voxelcore-geyser-extension` module into the Geyser instance's `extensions/` directory for world entity projections. **Running the pack builder never installs this extension.** The VoxelFurniture [snapshot PR #47](https://github.com/VoxelHorizons/VoxelFurniture/pull/47) and VoxelVehicles [snapshot PR #21](https://github.com/VoxelHorizons/VoxelVehicles/pull/21) merged on 25 October 2026. Match their published builds and enable their `bedrock.projection` settings if using the bridge.

## Author, build, publish and roll back

The compiler automatically renders supported Java models into Bedrock icons, including renderable `abstract: true` definitions. For placed custom blocks, it reads **persisted** `block-allocations.yml` from the Java build and translates supported `cube_all`, `cube_column` and six-face `cube` definitions into Geyser `only_override_states` entries with unit-cube native terrain textures. Run the Java build first to create or update block allocations; Bedrock-only compilation fails closed if blocks exist but that manifest is missing. Give individual content a separate explicit flat icon if the original model is unsupported:

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

The source for this override is `assets/voxel/textures/item/token.png`. Native Bedrock files go under `<content-pack>/bedrock/resource_pack/` using native `textures/`, `ui/`, `models/`, `entity/` and `font/` paths; do **not** paste the Java `assets/` tree there. The compiler reserves `manifest.json` and `textures/item_texture.json`, merges compatible non-conflicting JSON entries and rejects incompatible collisions or invalid paths.

Available commands:

```text
/voxelcore admin pack info
/voxelcore admin pack validate [target]
/voxelcore admin pack build [java|bedrock|all] [target]
/voxelcore admin pack stop
/voxelcore admin pack host [status|test]
```

With no scope (or `all`), build both editions. `pack build java` generates/reloads Java without Bedrock compilation, while `pack build bedrock` only converts/publishes Bedrock. After a content change **build Java first**, so render allocations match the Bedrock predicates. Only one build runs at once. `pack stop` cancels at the next safe checkpoint, without corrupting an already published artifact; an active final publish is allowed to finish.

Inspect `plugins/VoxelCore/build/resource-packs/bedrock/<revision>/`:

- `voxelcore.mcpack` — native Bedrock resource pack
- `voxelcore-items.json` — paired Geyser v2 custom item mappings
- `voxelcore-blocks.json` — Geyser v1 custom block state overrides for supported Java carrier-backed blocks
- `unsupported-items.txt` — items lacking a valid mapping/preview, with reasons
- `unsupported-geometry.txt` — icon produced, but native geometry/attachable export unsupported

**Local build cleanup:** After a successful Bedrock build and any configured Geyser/remote
publication, VoxelCore prunes older revision folders in
`build/resource-packs/bedrock/`. The `bedrock.generated_pack_retention` setting
defaults to `2` (current plus one recent revision); set it to `0` to disable
automatic cleanup or increase it for longer rollback history. The current revision
and the previously verified local Geyser publication are always protected, even
if this temporarily exceeds the configured count. Failed or cancelled builds
do not trigger cleanup. Only complete, SHA-256-named VoxelCore export directories
with known generated files are removed. `icon-cache/`, `glyph-tiles.json`,
unrecognised or modified folders, and packs already installed in Geyser are
not affected. Back up revisions you wish to keep indefinitely outside this build
output directory.

Configured publication writes `packs/voxelcore.mcpack`, `custom_mappings/voxelcore-items.json` and `custom_mappings/voxelcore-blocks.json` under Geyser, followed by `voxelcore-publication.json` (revision + three SHA-256 digests). `pack info` shows publication status. **VERIFIED_ON_DISK does not prove Geyser loaded the revision or that a Bedrock player accepted it.** Restart Geyser after changing mapping/entity registration, and reconnect Bedrock clients. For rollback, stop the owning Geyser, restore **both** files from the same previously exported revision, restart, reconnect and check the receipt. Restore all three artifacts together; never restore a mismatched subset.

VoxelCore's Java resource-pack `required` setting does not enforce Geyser's Bedrock pack policy. Configure required-pack behavior on Geyser itself. If Floodgate is installed only on a proxy, correctly configure backend Floodgate and key transfer; otherwise the backend may not recognize a Bedrock session.

### Rendering and text limits

- **Custom block inventory icons:** The Bedrock item compiler also resolves the Java-generated models for concrete `blocks:` definitions (`cube_all`, `cube_column`, and `cube`), rather than only reading authored model JSON. Their implicit placeable items now receive generated 64px transparent 2D previews in `textures/item_texture.json` and matching Geyser item definitions in `voxelcore-items.json`. Existing explicitly authored `items:` definitions for the same ID keep their selected model and carrier; the change does not alter placed-block terrain/state mapping. A missing PNG is still reported as unsupported rather than silently exported as paper. Run Java and Bedrock pack builds, restart Geyser and reconnect to activate the matching item mapping and icons.
- `BedrockIconRenderer` reuses flat sprites and renders supported cuboid models into transparent icons. `BedrockGeometryCompiler` generates geometry/attachables and face atlases for a supported subset of Java element-based models; unsupported parent/assets/shader cases remain in reports rather than silently gaining fake 3D parity.
- **High-resolution Java model faces (#98):** Geometry conversion now derives each face's Bedrock atlas dimensions from the Java PNG pixel dimensions and its UV span. The previous fixed 16×16 tile for every face reduced detailed 128×128 banner faces (44×48 and 44×64 source pixels) to 16×16. Models now use pixel-preserving, non-overlapping face rectangles, matching Bedrock `uv_size` values, deterministic power-of-two atlas packing, and replicated one-pixel gutters when any face exceeds 16px. Native 16px models retain their existing layout. Static, animated and tint/dye geometry use the same atlas logic. Unsupported UV requests exceeding 1024 source pixels per axis, or a generated atlas exceeding 4096px/16 million pixels, are reported as unsupported geometry instead of silently being downscaled. Larger atlases increase GPU texture memory and require client testing.
- **Separate issues:** This texture-atlas change does *not* modify the inline font glyph pipeline, its default-font codepoints or UI glyph alignment; those require independent #98 glyph verification. Java shaders (including portrait effects) are not automatically converted to Bedrock.
- Some Java `render.rule` leaves with structured CMD conditions, selections and ranges are converted into ordered Geyser definitions. Constant Bedrock tint overrides can use `properties.bedrock.tints`. Arbitrary runtime tint, model-rule types, held animation and full shader parity are **not** certified.
- Supported dyeable entity models can generate default and 16 Java dye atlases, selected by extension-projected properties. Vehicle seat offsets/pitch/roll and furniture nonuniform scale use client entity animation properties. Validate signs, UVs, transforms, model swaps and animation on a real client before calling them equivalent to Java.
- Bedrock reserves `U+E000..U+E1FF` controller/emoji glyphs. Managed VoxelCore glyph allocations start at `U+E200`. Run `/voxelcore admin pack glyphs scan` before `glyphs migrate confirm`; inspect and back up unmanaged font/config literal changes. Database-held ranks and tags are **not** automatically rewritten. Supported authored default font icons export to Bedrock native glyph pages. Managed `ui:` glyphs taller than 32 Java pixels (logos, banners) are reduced to `bedrock.oversized_glyph_size` pixels on their longest side with proportions kept, or to `bedrock_height` / `bedrock_scale` when the glyph sets one (see the README); bitmap providers in custom font files (`assets/<namespace>/font/*.json`) with private-use characters are converted the same way, since Bedrock has a single font (a custom-font logo taller than 32 pixels is reduced like a managed one). Providers with a negative `ascent` in a custom font are screen overlays and stay Java-only. Other large pictures, negative offsets and custom portraits need alternate UI.
- **Active frontend decision (owner-confirmed 27 Oct 2026):** Java uses DeluxeMenus; Bedrock uses **VoxelCore ChestGUI** with generated native Bedrock Classic chest-screen backgrounds. The owner **abandoned CrossplatForms**; the old DeluxeMenus `feat/bedrock-forms` branch is not the active UI migration plan. Self/target-player and Citizens NPC profiles render correct target data in live tests, except Java shader-driven 3D portraits cannot be displayed on Bedrock. Previously joined **offline** profiles are currently blocked by the operator's displayname-placeholder existence requirement. Preserve player/NPC args, requirements and click permissions; shift/drag/hotbar exploit safety remains untested.

## Live owner acceptance and known limitations (27 October 2026)

The 85-question owner audit confirms a useful live Bedrock feature subset on Paper 26.3 / Java 25, **not complete Bedrock parity or release certification**. Exact deployed Bedrock/Geyser/Floodgate/addon revisions and reproducible logs still need recording. Use the [central acceptance register](../ROADMAP.md) for issue ownership and priority.

| Behavior | Owner-reported outcome | Tracking |
| --- | --- | --- |
| Native pack distribution after build/Geyser restart and client reconnect | Works in observed flow; pack refusal enforcement **not tested** | R2/B01-B03 |
| Held custom 3D item geometry and inventory/browser icons | Generally works; selected generated textures visibly degrade and some glyphs render incorrectly | [Core #98](https://github.com/VoxelHorizons/VoxelCore/issues/98) |
| Custom placed blocks using Java carrier/blockstate remaps | **Reported defect (31 Oct screenshot)**: Bedrock displays note-block carriers. New mapping generation is pending **live client verification** of both old note-block and new mushroom carriers; never mark it verified from compilation alone | [Core #94](https://github.com/VoxelHorizons/VoxelCore/issues/94) |
| Furniture models, seats, storage and chunk/client reconnect | Generally works, but some furniture spins/rotates on spawning and model change | [Furniture #46](https://github.com/VoxelHorizons/VoxelFurniture/issues/46) |
| Bedrock non-OP Survival opening bamboo cupboard-bench storage | **Confirmed defect**: drops/breaks furniture on right-click; can leave ghost that survives chunk reload and server restart. Cherry drawer bench and comparable Java interaction were unaffected. Stored items tested recoverable; duplication unconfirmed. | [Furniture #48](https://github.com/VoxelHorizons/VoxelFurniture/issues/48) |
| Tractor movement, steering, dismount and chest inventory | Works; attached chest **physical model is invisible** while storage operates | [Vehicles #24](https://github.com/VoxelHorizons/VoxelVehicles/issues/24) |
| VoxelCore ChestGUI profiles / other-player and NPC target arguments | Work except Java-only 3D shader portrait; intermittent page/menu transitions sometimes immediately close | [Core #97](https://github.com/VoxelHorizons/VoxelCore/issues/97) |
| VoxelFurnitureShop showroom variant/tooltip | Variant rotation and informational tooltip work; extra empty box above popup on Bedrock | [FurnitureShop #14](https://github.com/VoxelHorizons/VoxelFurnitureShop/issues/14) |

**Important separation of concerns:** Bedrock textures/chest-screen JSON are not known to cause the intermittent menu close. Geyser inventory lifecycle is a hypothesis only. Bedrock interaction causing cupboard-bench drops must be investigated without assuming Geyser is the cause. Java resource-pack `players.required: true` has a separate observed failure to disconnect declined/failed Java clients ([Core #95](https://github.com/VoxelHorizons/VoxelCore/issues/95)); it does not itself control Bedrock pack policy.

**Acceptance still outstanding:** Bedrock Classic/Pocket UI comparison, GUI extraction/duplication (shift, drag, hotbar), Bedrock pack refusal/retry and proxy Geyser publishing, exact client/build evidence, full multiworld protections and per-feature older-version support. Full Bedrock 3D shader portrait parity is **not an advertised capability**.

## Remote / proxy Geyser

The VoxelCore repository contains signed remote publication and projection components. Their implementation and unit tests do **not** prove a real proxy deployment. Use TLS for remotely reachable endpoints, pin versions and keep credentials outside Git.

On the Paper backend set `bedrock.remote_sync.secret` to a private secret **at least 32 characters** long and enable `resource_pack.host`. A Bedrock build exposes `remote.json`, `voxelcore.mcpack`, `voxelcore-items.json` and `voxelcore-blocks.json` under the hosted `/bedrock/` route using a derived bearer authorization token. In the extension data directory, create `<geyser>/extensions/voxelcore/remote-sync.properties`:

```properties
url=https://pack.example.net/bedrock
secret=<same private secret>
# allow_insecure_http=true  # only on an appropriately secured private network
```

At pre-initialization, the extension verifies the versioned HMAC signature, SHA-256 digests for pack/items/blocks, revision and issue time; rejects replay, rollback and future-dated bundle metadata; then stages the pack, both mappings and receipt with failure recovery. Running Geyser still requires a restart for registration. Treat publication receipt verification as a disk-state fact until the active Geyser/client revision is confirmed.

For remote visual feeds, configure VoxelCore `bedrock.remote_sync.projection_directory` and point VoxelFurniture/VoxelVehicles `bedrock.projection.geyser-data-directory` at the matching export folder. The host serves authenticated `/bedrock/projection/<kind>.json`. Configure one entry per backend in the Geyser extension:

```properties
projection.survival.url=https://survival.example.net:8173/bedrock
projection.lobby.url=https://lobby.example.net:8173/bedrock
```

The extension polls signed feeds, rejects forged/replayed data, and mirrors each backend separately. Stale feeds expire (approximately 60 seconds) to avoid preserving dead server render ownership indefinitely. For mounted directories, use companions' distinct `bedrock.projection.feed-id` identifiers. Test feeds when one backend is offline, after model changes and upon chunk unload/reconnect.

## Client certification procedure

Record exact build SHAs and versions and attach outcomes to the relevant issue/release. The canonical [B01–B20 register](../ROADMAP.md#bedrock-work-package-acceptance-register-b01b20) is the **only** place to mark work complete.

1. Confirm actual Java pack revision, current `render-allocations.yml` and generated Bedrock revision. Preserve `unsupported-items.txt` and `unsupported-geometry.txt` from the **current** content; previous 69-mapped/527-unsupported numbers are stale.
2. Verify published matching pack/mapping/receipt on the **real** Geyser host; restart Geyser, reconnect Bedrock and exercise both required-pack acceptance and refusal.
3. Compare held/inventory/placed render on Java and Bedrock for basic icons, cuboids, faces, inherited models, dyed variants, furniture, vehicles and animated assets. Store paired screenshots.
4. Open the VoxelCore item/block browser, paginate **1→2→3→2**, close and reopen. Test Bedrock warp/profile/settings/NPC/shop menus, passed arguments, conditions, placeholders and no item/slot duplication or ghost windows.
5. Test placed furniture/vehicles: ownership/protection, dye, animations, seats, cargo/chests, transform/model change, disconnect, chunk unload/load, teleport/world change and server restart. Verify stale entity cleanup.
6. Test local and remote deployment, interrupted/mixed revision, stale signed feed, proxy offline, genuine rollback, old client cache and rejoin.
7. Record expected/actual result, artifact SHA/revision, Paper/Geyser/Floodgate/Bedrock versions, screenshots/logs and approver. **Only then mark a capability Verified.**

### Custom block-state safety and legacy worlds

New modern `method: auto` or `method: mushroom` blocks use a limited reservation pool of opposed horizontal mushroom-face combinations (not the usual naturally generated mushroom faces or vanilla-placed all-faces state). VoxelCore does **not** allocate new note-block states automatically: every combination of vanilla note pitch and instrument is reachable. Persisted note-block allocations continue to map and are **never silently migrated**, because changing them would strand existing placed world blocks. The reserved mushroom pool is intentionally small (24 states). The reservation rule covers ordinary vanilla generation and placement; commands and debug sticks can deliberately create any legal block state, so no carrier-based system can provide an unconditional collision guarantee. Back up `block-allocations.yml` before changing world content.

For #94 reproduction, build Java then Bedrock from the supplied `voxel:ruby_ore` and `voxel:deepslate_ruby_ore` YAML, inspect `voxelcore-blocks.json` and `textures/terrain_texture.json` in the matching revisions, restart the actual Geyser instance, reconnect on Bedrock, and compare the same two placed blocks and vanilla mushrooms/note blocks side by side. Verify break/drop/placement permissions, unload/reload, and a server restart. An on-disk receipt alone is not proof that Geyser accepted the mappings.

## Known test exception and external bridge decision

Intermittent Minecraft **1.13.2/1.14.4** smoke failures have shown `VOXELCORE_READY` but disappeared RCON listener access. The [26 October main run](https://github.com/VoxelHorizons/VoxelCore/actions/runs/40280473024) passed 7/8, with 1.14.4 RCON refusing and the container console fallback complaining `Exec needs to be run with user ID 1000`. This signature is conditionally **non-blocking** if the distribution, changed tests and Paper **26.3** smoke pass; **record the failure, never call the run passed**, and investigate any new signature. Do not disable assertions to conceal it.

The community [GeyserDisplayEntity](https://github.com/GeyserExtensionists/GeyserDisplayEntity) is an external **AGPL-3.0** reference, not bundled; review its license and conflicts before attempting to install alongside VoxelCore's own extension. Avoid two independent custom display owners. Relevant upstream docs: [Geyser packs](https://geysermc.org/wiki/geyser/packs/), [custom items](https://geysermc.org/wiki/geyser/custom-items/), and [experimental custom entities](https://geysermc.org/wiki/geyser/custom-entities/).
