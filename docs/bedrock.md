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

Add an explicit Bedrock texture to each desired concrete item (properties inherit through normal content inheritance):

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

The source is `assets/voxel/textures/item/token.png` inside a content pack. It becomes a flat Bedrock item icon. The generated selector uses the item's actual `render.model` and vanilla material. Multiple definitions claiming the same selector are rejected. Complex models can use a separately authored icon, but this does not reproduce their 3D rendering or conditional model rules. Items without an explicit texture are listed in `unsupported-items.txt` rather than falsely reported as converted.

Optional native Bedrock assets belong under `<content-pack>/bedrock/resource_pack/` and are copied to the pack root. `manifest.json` and `textures/item_texture.json` are generated/reserved. Conflicting authored paths fail the build. No behavior pack is produced.

## Build and publish

1. Run `/vc admin pack build` for the running server's target. Bedrock compilation runs off the main thread alongside the Java publishing workflow, independently of whether Java HTTP hosting is enabled.
2. Review output: `build/resource-packs/bedrock/<revision>/` contains `voxelcore.mcpack`, `voxelcore-items.json`, and `unsupported-items.txt`. Prior revisions remain available. Rebuilding identical inputs reuses the revision.
3. With a configured directory, VoxelCore replaces only `packs/voxelcore.mcpack` and `custom_mappings/voxelcore-items.json`. Individual replacements are staged; an I/O failure attempts rollback. This is not a cross-process atomic transaction: do not reload Geyser during publication.
4. Restart Geyser after publishing, then reconnect Bedrock clients. Registry changes are deliberately not hot-reloaded or followed by an automatic disruptive restart. On a same-process installation, restart the server. Online Bedrock clients cannot accept pack changes mid-session.

Pack header/module UUIDs stay stable. The version tuple changes with the content hash; outputs are deterministic and prior revisions support rollback. To roll back, stop Geyser, restore both files from the same revision, restart, and reconnect clients.

Floodgate and local Geyser are optional soft dependencies. Their public APIs identify Bedrock players so Java pack delivery and its required-pack enforcement do not target those clients. With proxy Floodgate, install/configure backend Floodgate, enable proxy `send-floodgate-data`, and share the correct key as described in Floodgate's documentation. Without a local API exposing a connection, VoxelCore cannot identify it. Configure required Bedrock pack acceptance in Geyser itself; VoxelCore's Java `resource_pack.players.required` does not configure Geyser.

A Bedrock failure is reported separately and does not block the Java build. This first phase does not coordinate content activation across the two runtimes: use a maintenance restart for content registry changes and check both outputs before admitting players.

## Remaining roadmap and acceptance tests

- [x] Explicit item textures, v2 item-model mappings, resource manifests and authored assets.
- [x] Revision exports and configured local/shared-directory Geyser publishing.
- [x] Public Floodgate/Geyser detection and Java pack exclusion.
- [ ] Automatic flat-model texture extraction and 3D geometry/attachable conversion.
- [ ] Geyser-side extension: authenticated remote revision synchronization, lifecycle-safe registration, pack-only updates and active revision reporting.
- [ ] Block mappings and experimental custom entities for furniture/vehicles, with hitbox/riding tests.
- [ ] Bedrock menu/form frontend and deliberate alternatives for Java glyph overlays, shader portraits and tooltips.
- [ ] Legacy backend mappings, finite component variants and dye/animation parity.
- [ ] Live test on the exact production Geyser/Floodgate versions: join, required-pack refusal, reconnect after update, rollback, item inventory/held appearance and gameplay identity.

Automated compiler tests cover deterministic output, changed revisions, missing assets preserving previous builds, reserved files, and scoped Geyser publication. A successful unit test is not a Bedrock client compatibility test.

References: [Geyser packs](https://geysermc.org/wiki/geyser/packs/), [v2 item mappings](https://geysermc.org/wiki/geyser/custom-items/), [Floodgate API](https://geysermc.org/wiki/floodgate/api/).
