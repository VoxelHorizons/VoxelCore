# VoxelCore Color Picker — one background and dynamic RGB swatches

## Overview

The Color Picker has one **176×138 background** `colorpicker_background`, and
only **two reusable item models**: a 16×16 slot and an oversized 18×18 selected
slot. Both models use the **original unmodified single PNG** (`slot.png` and
`selected.png`). Their custom `elements` each reuse the same texture:
`tintindex: 0` colours the centre, white inner outline and darker bottom
padding, while the outer black/grey border faces remain untinted. No extra
texture layers or generated PNGs are necessary.

No nine-per-palette background PNGs or separate textures per hex shade are
needed. Clicking a primary palette changes the nine live-tinted shade items in
the existing 54-slot inventory without closing/reopening or flickering. The
primary buttons are tinted with their palette's representative RGB. Both
the active **primary button** and the active **shade button** use the selected
18×18 model; the remaining buttons use the 16×16 slot model.

The input remains slot **19**, the output remains slot **25**, the primary
buttons occupy **12–14, 21–23, 30–32**, and the shade items **36–44**. The
input is only consumed when the recoloured output item is taken. **Shift-click
the output** to place the completed item directly into the player inventory,
including merging into matching stacks; if there is no space the input remains
untouched and the configured inventory-full message is shown. Empty/blank
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
   Remove the four obsolete split-layer textures
   (`slot_tint.png`, `slot_frame.png`, `selected_tint.png`,
   `selected_frame.png`) if you installed the previous draft version.
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
  model using the original 16×16 `slot.png`
- `items.ui-colorpicker_selected`: abstract
  `voxel:ui/colorpicker/selected` model using the original 18×18
  `selected.png` with 1.125 GUI scale
- Both items use `render.oversized_in_gui: true`, structured
  `custom_model_data.color: '#FFFFFF'`, and the existing VoxelCore
  `properties.dyeable` API. The compiler emits a
  `minecraft:custom_model_data` tint source for `tintindex: 0` faces.
  Each JSON uses five non-overlapping flat elements: one tintable inner
  rectangle and four untinted outer-frame strips, UV-mapped onto the same
  unmodified PNG.

The palette's primary button uses its first configured shade as the RGB tint
unless `palettes.<name>.button-color: '#RRGGBB'` is explicitly supplied.
Each of the nine shade icons uses its configured `shades` hex value.
The selected primary button and selected shade both use the larger selected
icon, and all other buttons use the slot icon.

## Bedrock palette-coloured icons (in progress)

Java still uses **one original texture** for each model, with true per-stack
RGB tinting. Bedrock needs static 2D item icons, so the Bedrock pack compiler
now **derives the finite colour set** directly from the effective
`plugins/VoxelCore/colorpicker.yml`, including any `button-color` overrides.

During `/vc admin pack build`, it renders the two Color Picker swatch models
and any other VoxelCore `properties.dyeable` model for each unique configured
shade. It hashes the **final PNG bytes** so visually identical icons share
one file and one atlas entry across models, caches expensive rendering across
builds, and compresses the resulting pack with the existing PNG optimiser and
ZIP level 9. The underlying Java models stay as two original textures;
generated icons exist **only inside the Bedrock pack**.

A Geyser v2 definition with a `custom_model_data` string `match` predicate
selects the appropriate baked image. When VoxelCore recolours an item, its
modern Paper adapter saves BOTH the exact 24-bit RGB in the existing
`CustomModelDataComponent.colors` list and
`voxelcore:rgb:RRGGBB` in a reserved string index. The index is permanently
allocated through `render-allocations.yml`, not hard-coded, so it cannot
overwrite authored `render.rule.select` strings.

```yaml
# plugins/VoxelCore/config.yml
bedrock:
  palette_icons:
    max_mappings: 8192
    max_unique_png_bytes: 33554432
```

These are maximum **extra** Geyser mapping entries and raw, deduplicated PNG
bytes. If a budget is exceeded, the uncoloured/default item icon remains
available and the skipped colours appear in `unsupported-items.txt`.
Set lower limits if your pack has dozens of complex dyeable furniture models;
the pack builder logs mapped shades, unique PNG count and raw byte usage.

**Important limitations:**

- This works for colours explicitly listed in the colour picker; arbitrary
  RGB values outside those shades fall back to the default Bedrock icon while
  retaining their precise Java colour and item metadata.
- Each new shade or changed model requires rebuilding/publishing the Bedrock
  pack and **restarting Geyser** so its fixed custom item mappings load.
  Reconnect Bedrock clients afterward.
- Items dyed before the selector was introduced retain their exact RGB data
  but may require re-dyeing to acquire the Bedrock string selector.
- This fixes the **inventory / Color Picker 2D icons**, not the separate 3D
  Geyser furniture-entity dye system.
- Visual equivalence still needs in-game verification on Bedrock and Java.
  No ProtocolLib, PacketEvents or other packet plugin is required.

## Built-in compatibility and permissions

If the custom items are not yet installed, leave the generated
`colorpicker.yml` defaults at `FIREWORK_STAR` and an empty
`selected-shade-material`; those vanilla buttons work without custom
content. The Colouring Bench continues running `vc colorpicker {player}`
as console when right-clicked. `/vc colorpicker` remains admin-only,
and `/vc colorpicker <player>` requires the restricted targeted permission.
