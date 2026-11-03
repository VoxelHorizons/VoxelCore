# VoxelCore

**VoxelCore** is the content and resource-pack foundation for Voxel Horizons. It provides version-aware custom items and blocks, asset and model compilation, UI fonts and glyphs, and optional Bedrock content projection through Geyser.

[**Documentation Wiki**](https://github.com/VoxelHorizons/VoxelCore/wiki) · [**Installation**](https://github.com/VoxelHorizons/VoxelCore/wiki/Installation) · [**Compatibility**](https://github.com/VoxelHorizons/VoxelCore/wiki/Compatibility) · [**v1.0.0 Release**](https://github.com/VoxelHorizons/VoxelCore/releases/tag/v1.0.0) · [**Report an Issue**](https://github.com/VoxelHorizons/VoxelCore/issues)

## Features

- **Custom items and blocks:** Namespaced content IDs, YAML definitions, inheritance, persistent allocation tracking, custom models and block carriers.
- **Resource packs:** Deterministic Java pack builds for validated Minecraft targets, version-specific item/block rendering, and optional self-hosted distribution.
- **UI and fonts:** Bitmap glyphs, menu backgrounds, spacing placeholders, configurable titles and popup tooltips.
- **Bedrock support:** Bedrock pack generation, Geyser custom-item/block mappings, and an optional separate extension for supported entity projections. Individual features require client testing; full Java/Bedrock parity is not guaranteed.
- **Operations and integrations:** Administration commands, safe content reloads, optional PlaceholderAPI/ShopGUI+ hooks, and independent token currency support.

## Installation

1. Download **one** VoxelCore server JAR matching your Minecraft server version from the [v1.0.0 release](https://github.com/VoxelHorizons/VoxelCore/releases/tag/v1.0.0).
2. Place it in your server's `plugins/` folder, then start the server.
3. Configure content packs and build the resource pack following the [installation guide](https://github.com/VoxelHorizons/VoxelCore/wiki/Installation).
4. For compatible Bedrock installations, follow [Bedrock and Geyser](https://github.com/VoxelHorizons/VoxelCore/wiki/Bedrock-and-Geyser). The Geyser extension is a **separate optional JAR**, not a second VoxelCore server plugin.

The release contains seven version-specific server JARs, one Geyser extension JAR and `SHA256SUMS`. See the [compatibility matrix](https://github.com/VoxelHorizons/VoxelCore/wiki/Compatibility) before installing; intermediate versions within a named family have not all been individually smoke-tested.

## Documentation

The [Wiki](https://github.com/VoxelHorizons/VoxelCore/wiki) is the maintained user and operator reference:

| Topic | Guide |
| --- | --- |
| Set up and update | [Installation](https://github.com/VoxelHorizons/VoxelCore/wiki/Installation) · [Backups and rollback](https://github.com/VoxelHorizons/VoxelCore/wiki/Upgrading-and-Backups) |
| Content authoring | [Content packs](https://github.com/VoxelHorizons/VoxelCore/wiki/Content-Packs) · [Custom items](https://github.com/VoxelHorizons/VoxelCore/wiki/Custom-Items) · [Custom blocks](https://github.com/VoxelHorizons/VoxelCore/wiki/Custom-Blocks) · [Actions](https://github.com/VoxelHorizons/VoxelCore/wiki/Actions) |
| Rendering and UI | [Resource packs](https://github.com/VoxelHorizons/VoxelCore/wiki/Resource-Packs) · [Fonts and glyphs](https://github.com/VoxelHorizons/VoxelCore/wiki/UI-Fonts-and-Glyphs) · [Tooltips](https://github.com/VoxelHorizons/VoxelCore/wiki/Popup-Tooltips) |
| Bedrock | [Bedrock and Geyser](https://github.com/VoxelHorizons/VoxelCore/wiki/Bedrock-and-Geyser) |
| Administration | [Commands](https://github.com/VoxelHorizons/VoxelCore/wiki/Commands-and-Administration) · [Currencies](https://github.com/VoxelHorizons/VoxelCore/wiki/Currencies) · [Integrations](https://github.com/VoxelHorizons/VoxelCore/wiki/Integrations) |
| Help | [Troubleshooting](https://github.com/VoxelHorizons/VoxelCore/wiki/Troubleshooting) |

## Related Voxel Horizons plugins

- [VoxelFurniture](https://github.com/VoxelHorizons/VoxelFurniture) — interactive, persistent furniture; available publicly.
- **VoxelVehicles** — separately distributed private/commercial vehicle add-on. Its private GitHub releases are not public download links.

These plugins have their own configuration and compatibility requirements. VoxelCore does not provide their world entity engines.

## Contributing and support

Please report reproducible defects via [GitHub Issues](https://github.com/VoxelHorizons/VoxelCore/issues), including your server version, installed JAR filename, logs and reproduction steps. Follow [SECURITY.md](SECURITY.md) for private security disclosures. Maintainers can consult the [build and release guide](https://github.com/VoxelHorizons/VoxelCore/wiki/Building-and-Releasing).

## License

VoxelCore is **free to use and source-available**, not OSI-approved open-source software. You may run and privately customise it on your own servers, including monetised servers. Unauthorised redistribution, mirrors, reuploads, repackaging and resale are prohibited, subject to applicable GitHub/authorised-platform rights. See the [Voxel Horizons Community Use License 1.0](LICENSE.md).
