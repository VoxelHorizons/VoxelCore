# VoxelCore Color Picker — one background and dynamic RGB swatches

## Overview

The Color Picker has one **176×138 background** `colorpicker_background`, and
only **two reusable item models**: a 16×16 slot and an oversized 18×18 selected
slot. The uploaded source images are split into a pure-white tintable centre
(layer0) and a neutral untinted border/highlights (layer1). Minecraft applies
VoxelCore's per-item structured custom model data RGB only to layer0. The
black, grey and white frame therefore never changes colour.

No nine-per-palette background PNGs or separate textures per hex shade are
needed. Clicking a primary palette changes the nine live-tinted shade items in
the existing 54-slot inventory without closing/reopening or flickering. The
primary buttons use the same slot model and are each tinted with that
palette's representative RGB. The active shade uses the selected model.

The input remains slot **19**, the output remains slot **25**, the primary
buttons occupy **12–14, 21–23, 30–32**, and the shade items **36–44**. The
input is only consumed when the recoloured output item is taken. Empty/blank
button names and lore remain configurable.

## Installation

1. Install the latest PR build of VoxelCore for Minecraft **1.21.4+**.
2. Copy `examples/colorpicker/content/colorpicker.yml` to your existing
   `plugins/VoxelCore/content/voxel/content/` folder (or merge the `ui:` and
   `items:` sections into your existing content file).
3. Copy every asset from `examples/colorpicker/assets/voxel/` into your
   existing `plugins/VoxelCore/content/voxel/assets/voxel/`.
4. Remove the old `ui-colorpicker.yml` nine-background example and old
   `colorpicker_selected.yml` example if you previously installed them:
   keeping two item definitions with the same ID will prevent content loading.
   The *old PNGs* can be removed from your content pack to reduce pack size.
5. Set the **single background title** and **two button item IDs** in
   `plugins/VoxelCore/colorpicker.yml` as below. Leave your palette hex
   arrays and 0-based button slot assignments unchanged.
6. Rebuild/send the VoxelCore resource pack and reload the server/plugin.
   The content definitions must be loaded before button items are validated.

```yaml
title: '&f:offset_-8::colorpicker_background:'
buttons:
  color-material: 'voxel:ui/colorpicker/slot'
  selected-shade-material: 'voxel:ui/colorpicker/selected'
  color-name: '&f{name}'
  color-lore: []
  shade-name: '&f{name}'
  shade-lore: []
  selected-prefix: ''
```

Names and lore may be blank; a missing lore key preserves legacy hex lore,
but explicit `[]` removes it. Use `{hex}`, `{name}`, `{palette}`, and
`&` formatting in any configured button text.

## Item ID resolution and missing pack definitions

The authored **model resource paths** and **content item IDs** are not the same:

| Model resource | Authored content ID | Direct config ID |
| --- | --- | --- |
| `voxel:ui/colorpicker/slot` | `ui-colorpicker_slot` | `voxel:ui-colorpicker_slot` |
| `voxel:ui/colorpicker/selected` | `ui-colorpicker_selected` | `voxel:ui-colorpicker_selected` |

The Color Picker now accepts both the exact content IDs **and** the slash-style
resource identifiers above. The older all-hyphen naming convention is also
accepted if such items exist in your pack. If VoxelCore logs "Unknown item",
check `plugins/VoxelCore/content/voxel/content/colorpicker.yml` (the content
pack item definitions), **not** the separate top-level
`plugins/VoxelCore/colorpicker.yml` (the GUI configuration). They serve
different purposes.

Missing optional custom icon definitions now cause a startup warning and use
vanilla firework stars for that session instead of disabling VoxelCore. When
you reload content, icons are re-resolved after the new item registry is
published.

## RGB model details

The bundled `content/colorpicker.yml` is based on the user's supplied
configuration. It declares:

- `ui.colorpicker_background`: single bitmap title glyph using
  `ui/menu/colorpicker/background.png`, `scale_ratio: 138`,
  `y_position: 13`, `gui: true`
- `items.ui-colorpicker_slot`: abstract `voxel:ui/colorpicker/slot`
  generated-item model; 16×16 original dimensions
- `items.ui-colorpicker_selected`: abstract
  `voxel:ui/colorpicker/selected` generated-item model;
  18×18 dimensions with 1.125 GUI scale
- Both items use `render.oversized_in_gui: true`, structured
  `custom_model_data.color: '#FFFFFF'`, and the existing VoxelCore
  `properties.dyeable` API. The compiler emits a
  `minecraft:custom_model_data` tint source for layer0.

The palette's primary button uses its first configured shade as the RGB tint
unless `palettes.<name>.button-color: '#RRGGBB'` is explicitly supplied.
Each of the nine shade icons uses its configured `shades` hex value. Only
the chosen shade uses the selected icon; all others use the slot icon.

The title is identical for Java and Bedrock; VoxelCore/Geyser handles UI
translation downstream. **Current compatibility note:** the Java renderer
displays exact arbitrary RGB icon tints. VoxelCore's existing Bedrock pack
compiler bakes default icon tints and does not yet support per-instance RGB
item icon shading, so identical per-shade swatch colours on Bedrock require
separate Geyser/Bedrock translation support. This does not introduce
ProtocolLib, PacketEvents, or any third-party packet plugin.

## Built-in compatibility and permissions

If the custom items are not yet installed, leave the generated
`colorpicker.yml` defaults at `FIREWORK_STAR` and an empty
`selected-shade-material`; those vanilla buttons work without custom
content. The Colouring Bench continues running `vc colorpicker {player}`
as console when right-clicked. `/vc colorpicker` remains admin-only,
and `/vc colorpicker <player>` requires the restricted targeted permission.
