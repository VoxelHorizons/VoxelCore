# VoxelCore Color Picker

Put dyeable VoxelCore furniture/cosmetics into the input, click one of the nine primary colours and a shade, then take the preview from the output. **Taking the output is the only action that consumes and recolours the input item.** One input item is consumed when a painted item is taken. Closing the GUI returns any unpainted input to the player; if the inventory is full, it drops at the player's location. No dye ingredient or currency is consumed.

## Setup

1. Install a VoxelCore build containing this feature on **Minecraft 1.21.4+** (required by the existing VoxelCore RGB item API). It creates `plugins/VoxelCore/colorpicker.yml` on first start.
2. Copy `coloring_bench.yml` into your existing `voxel` content pack's `content/` directory alongside your block definitions. It extends the user's supplied 6-texture cube and adds a main-hand right-click command action.
3. For the nine background variants, copy the **nine bundled** PNG files from `examples/colorpicker/assets/voxel/textures/ui/colorpicker/` to `plugins/VoxelCore/content/voxel/assets/voxel/textures/ui/colorpicker/<palette>.png` and merge `ui-colorpicker.yml` into the pack's `content/` directory. Don't load the UI definitions until the images exist: VoxelCore validates references at pack-load time.
4. Rebuild/send the resource pack with `/vc admin pack build`. Run `/vc admin reload` after editing the palettes/config, or restart to pick up changes.

Both Java and Bedrock use **one shared menu title**: `title: '&f:offset_-8::colorpicker_{palette}:'` in `colorpicker.yml`. VoxelCore resolves the configured glyph/offset aliases for every player, and its existing Geyser integration handles Bedrock rendering; do not introduce separate Bedrock titles or bypass alias resolution. Switching palettes creates a replacement 54-slot GUI with the newly selected title on the next server tick (rather than relying on Paper's unsupported live `InventoryView#setTitle`) and preserves the input item. When an authored `colorpicker_<palette>` alias is missing, the implementation falls back to a plain title to avoid exposing raw placeholders. Both clients use the same six-row inventory (54 slots). Existing configurations with `titles.java` are read as a legacy fallback when the new `title` key is absent; `titles.bedrock` and `titles.fallback` are obsolete and ignored.

## Permissions

- `/vc colorpicker` requires `voxelcore.colorpicker.self` (operator by default), checked by the Color Picker command handler. Ordinary players receive a use-the-bench message.
- `/vc colorpicker <player>` requires `voxelcore.colorpicker.others` (operator by default), and can be dispatched by console. Console actions bypass player permissions. Do not grant the targeted permission to normal players.
- Other `/vc` subcommands still require the existing `voxelcore.use` root permission; their admin permissions are unchanged.
- The built-in block action executes `vc colorpicker {player}` **as console** on a main-hand right click, so visitors can use the bench even though neither colorpicker permission is granted to them.
- The command opens a menu for the specified online player; it never automatically applies a colour to an arbitrary held item.

## Configuration

`colorpicker.yml` defines the 54-slot menu (input slot 19, output slot 25, nine primary palette buttons, and shade slots 36–44), material/display names, a single shared title, messages, and 81 editable `#RRGGBB` shades. You can change the palette names, the primary button positions and order, shades and number of active palettes. All occupied positions must be unique and within the GUI.

Values were sampled at the centres of the nine shade swatches from the uploaded 176×138 PNGs (not estimates). The primary grid matches the PNGs exactly: red/orange/yellow, magenta/white/purple, green/cyan/blue. After changing shade RGB values or moving swatches, adjust the authored background textures too if you want their baked-in pixels to continue matching the configurable item icons. First shade in each palette is selected by default. `white` is the greyscale palette.

## Safety / compatibility

The output is a protected preview, not a separate owned item. Click/drag/shift/number-key transfers in the top menu are blocked, bottom inventory interaction remains usable, and closing returns an unspent item. Dyeing delegates to `ItemManager.setDyeColor` so custom model/tint metadata is preserved. This does not add ProtocolLib or PacketEvents. The RGB item API is only available from Minecraft 1.21.4 onwards, and this version gate is communicated to the player. Each server still requires testing with its own Java resource pack and installed Geyser version.

### Custom button models

`buttons.color-material` accepts either a vanilla item material, such as
`FIREWORK_STAR`, or a custom VoxelCore item identifier. To use the transparent
blank UI icon, set `color-material: voxel:ui/blank`. This resource-style
spelling resolves to the actual content item key `voxel:ui-blank`, matching
VoxelCore's ShopGUI+ integration. Custom buttons can be **abstract render-only**
items; they must still be defined in a loaded VoxelCore content pack and have a
renderable material and model. When a custom icon is selected, the configured
`color-name`, `shade-name` and `selected-prefix` still apply, but the
firework-star swatch colour effect is not used (your GUI texture supplies the
coloured artwork). Invalid or unknown custom item IDs are reported when
the picker config loads, rather than silently falling back to PAPER.

### Selected-shade outline and live palette backgrounds

The custom **18×18 selection outline** supplied with this feature is packaged as:

- `examples/colorpicker/assets/voxel/textures/item/ui/colorpicker_selected.png` (original, unmodified);
- `examples/colorpicker/assets/voxel/models/item/ui/colorpicker_selected.json` (flat generated item with 1.125 GUI scale);
- `examples/colorpicker/content/colorpicker_selected.yml` (abstract display-only VoxelCore item, `render.oversized_in_gui: true`).

Copy the assets and the item content definition into your existing `voxel` content pack. The resulting content ID is `voxel:ui-colorpicker_selected`. Enable the overlay in `plugins/VoxelCore/colorpicker.yml` after the content has been installed:

```yaml
buttons:
  color-material: 'voxel:ui/blank'
  selected-shade-material: 'voxel:ui/colorpicker_selected'
  color-name: '&f{name}'
  shade-name: '&f{name} &7- &f{hex}'
  selected-prefix: '&e▶ '
```

`selected-shade-material` is optional (empty by default). When configured, only the **selected** shade uses the transparent-centred 18×18 outline icon; all other shade buttons use the regular `color-material`. Your selected palette's baked-in colour swatches remain visible through the transparent centre. The nine separately registered UI glyphs must be named `colorpicker_red`, `colorpicker_orange`, etc., matching the palette keys. Each palette switch now opens the new title background without using unsupported `setTitle`. Changing **shades** does not reopen the inventory.

**Reload order:** add the custom item/model/texture first, reload the VoxelCore content and rebuild/send the resource pack, then set the selection model in `colorpicker.yml` and reload the picker configuration or restart. Unknown custom model IDs are deliberately reported as errors rather than silently replaced with paper icons.
