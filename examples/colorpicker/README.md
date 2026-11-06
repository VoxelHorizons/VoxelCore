# VoxelCore Color Picker

Put dyeable VoxelCore furniture/cosmetics into the input, click one of the nine primary colours and a shade, then take the preview from the output. **Taking the output is the only action that consumes and recolours the input item.** One input item is consumed when a painted item is taken. Closing the GUI returns any unpainted input to the player; if the inventory is full, it drops at the player's location. No dye ingredient or currency is consumed.

## Setup

1. Install a VoxelCore build containing this feature on **Minecraft 1.21.4+** (required by the existing VoxelCore RGB item API). It creates `plugins/VoxelCore/colorpicker.yml` on first start.
2. Copy `coloring_bench.yml` into your existing `voxel` content pack's `content/` directory alongside your block definitions. It extends the user's supplied 6-texture cube and adds a main-hand right-click command action.
3. For the nine background variants, copy the **nine bundled** PNG files from `examples/colorpicker/assets/voxel/textures/ui/colorpicker/` to `plugins/VoxelCore/content/voxel/assets/voxel/textures/ui/colorpicker/<palette>.png` and merge `ui-colorpicker.yml` into the pack's `content/` directory. Don't load the UI definitions until the images exist: VoxelCore validates references at pack-load time.
4. Rebuild/send the resource pack with `/vc admin pack build`. Run `/vc admin reload` after editing the palettes/config, or restart to pick up changes.

The Java menu title defaults to `&f:offset_-8::colorpicker_{palette}:`. It swaps the corresponding PNG when a different primary palette is selected (if the server version supports InventoryView#setTitle). When no matching glyph is installed, it uses the configurable plain title. Bedrock has its own readable title because Java inventory-title bitmap art is not supported by Bedrock. **Bedrock opens the same five-row inventory**, rather than the one-row purchase UI used by VoxelFurnitureShop.

## Permissions

- `/vc colorpicker` requires `voxelcore.use` **and** `voxelcore.colorpicker.self` (both operator by default).
- `/vc colorpicker <player>` requires `voxelcore.use` **and** `voxelcore.colorpicker.others` (operator by default). Console actions bypass player permissions. Do not grant the targeted permission to normal players.
- The built-in block action executes `vc colorpicker {player}` **as console** on a main-hand right click, so visitors can use the bench even though neither colorpicker permission is granted to them.
- The command opens a menu for the specified online player; it never automatically applies a colour to an arbitrary held item.

## Configuration

`colorpicker.yml` defines the 54-slot menu (input slot 19, output slot 25, nine primary palette buttons, and shade slots 36–44), material/display names, title variants, messages, and 81 editable `#RRGGBB` shades. You can change the palette names, the primary button positions and order, shades and number of active palettes. All occupied positions must be unique and within the GUI.

Values were sampled at the centres of the nine shade swatches from the uploaded 176×138 PNGs (not estimates). The primary grid matches the PNGs exactly: red/orange/yellow, magenta/white/purple, green/cyan/blue. After changing shade RGB values or moving swatches, adjust the authored background textures too if you want their baked-in pixels to continue matching the configurable item icons. First shade in each palette is selected by default. `white` is the greyscale palette.

## Safety / compatibility

The output is a protected preview, not a separate owned item. Click/drag/shift/number-key transfers in the top menu are blocked, bottom inventory interaction remains usable, and closing returns an unspent item. Dyeing delegates to `ItemManager.setDyeColor` so custom model/tint metadata is preserved. This does not add ProtocolLib or PacketEvents. The RGB item API is only available from Minecraft 1.21.4 onwards, and this version gate is communicated to the player. Each server still requires testing with its own Java resource pack and installed Geyser version.
