# Experimental Bedrock oversized GUI item overlays

Java 1.21.6+ supports `render.oversized_in_gui: true`. Bedrock and Geyser v2 item
mappings do **not** have a matching item-model flag. On Bedrock, VoxelCore
normally bakes the Java model into one regular inventory icon, which fits
inside a single 18x18 Classic UI cell.

This experiment adds an **opt-in-by-existing-flag** Classic chest UI overlay.
For each item with `render.oversized_in_gui: true` and a GUI scale greater
than one slot, the Bedrock pack compiler adds an extra
`inventory_item_renderer` sized from the Java model's inherited
`display.gui.scale`. It is attached to the existing chest grid cell, and
the original slot button is unchanged. It reuses Geyser's current item stack
rendering rather than shipping a separate PNG for each variant/shade.

Example Java model:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {"layer0": "example:item/toggle_on"},
  "display": {"gui": {"scale": [2.25, 1.125, 1]}}
}
```

Example VoxelCore item:

```yaml
items:
  toggle_on:
    material: minecraft:paper
    display_name: 'Toggle On'
    render:
      model: example:item/toggle_on
      oversized_in_gui: true
```

By default, this requests an overlay of 36x18 GUI pixels centred on the
slot. If the original Java item uses a multi-slot model with no usable
`display.gui.scale`, set dimensions explicitly:

```yaml
    properties:
      bedrock:
        oversized_gui:
          width: 36        # 1..128
          height: 18       # 1..128
          offset_x: 0      # -64..64
          offset_y: 0      # -64..64
          match_text: 'Toggle On'
```

Set `properties.bedrock.oversized_gui: false` to disable the overlay for
one item, without changing its Java behaviour.

## Experimental limitations

- **Not yet visually verified on a live Bedrock client.** The pack compiler
  and tests check JSON construction and mapping preservation, not real
  client rendering.
- The Bedrock JSON UI selects the extra renderer using the slot's
  `#hover_text` collection binding. This is **not a stable Bedrock item ID**.
  Two differently drawn items with the same tooltip text cannot be
  distinguished. Prefer unique item names, or `match_text` containing a
  unique, stable substring of the rendered tooltip.
- The text must be non-empty, at most 80 characters and cannot contain
  an apostrophe. Java colour codes are stripped before matching.
- The original normal-sized icon is still rendered beneath the overlay.
  If the icon has large transparent central areas, the underlying icon
  may be visible. This experiment does not alter or hide any other slots.
- Only **Classic UI chest screens** (small/large) are targeted. Pocket UI,
  other inventory layouts, and client-specific texture packs are outside
  scope. Authored `bedrock/resource_pack/ui/chest_screen.json` takes
  precedence over the generated overlay.
- No extra overlay is generated if both dimensions are at most 16px.
  This is a visual treatment only; a single item stack still owns exactly
  one clickable slot. The covered adjacent slot remains separately
  interactive, just as for a Java oversized model.
- Maximum 64 distinct text selectors, with images capped at 128x128px.
  Invalid configurations are reported in the Bedrock unsupported-items
  report; ordinary item mappings continue to build.

## Live testing before merging

1. Build Java + Bedrock packs and install them in Geyser.
2. Open a small and large chest menu containing a 36x18 toggle model
   in the left slot, with an empty right slot; verify the art spans both.
3. Change the item in-place from ON to OFF and confirm the overlay
   updates without closing the menu. Check an item with the same name
   but different lore and one with an apostrophe.
4. Verify clicking, dragging, hovering and moving items still targets
   the real slot, including the neighbour covered by the art.
5. Test several Bedrock GUI scales and resolutions on Windows,
   mobile Classic UI if possible, and Pocket UI (which should be
   unaffected).
6. Verify regular custom items, tinted palette icons, the colour
   picker, and existing glyph-based chest backgrounds remain intact.

If Bedrock does not expose `#hover_text` to this collection control, or
if the UI clips the larger renderer to the slot, the overlay will not
work. In that case this should **not** be represented as full parity:
keep the PR in draft and investigate a Geyser-side explicit-item marker
or an alternate UI projection before enabling it widely.
