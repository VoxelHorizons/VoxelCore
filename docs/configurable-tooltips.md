# Configurable UI-font popup tooltips

Popup backgrounds are authored as normal UI glyph content. VoxelCore **does not bundle or inject tooltip PNGs**.

## Author your textures and UI definitions

Copy the example from `mvp-content/voxeltest/content/ui-tooltip.yml` and the three matching PNGs in `mvp-content/voxeltest/assets/voxeltest/textures/ui/tooltip/` into your own content pack, changing the namespace as needed. These are ordinary `ui:` definitions loaded by the existing VoxelCore font/glyph pipeline. You can replace the textures with your own artwork.

For a content pack whose `pack.yml` says `namespace: voxel`:

```yaml
ui:
  tooltip_left:
    path: ui/tooltip/left.png
    scale_ratio: 19
    y_position: 7
  tooltip_center:
    path: ui/tooltip/center.png
    scale_ratio: 19
    y_position: 7
  tooltip_right:
    path: ui/tooltip/right.png
    scale_ratio: 19
    y_position: 7
```

In VoxelCore's `config.yml`:

```yaml
ui:
  tooltip:
    left: 'voxel:tooltip_left'
    center: 'voxel:tooltip_center'
    right: 'voxel:tooltip_right'
    tile_overlap: 1
    horizontal_padding: 4
    x_offset: 55
```

Use the **full** `namespace:id` for each configured glyph. A missing ID produces a clear error when the tooltip is tested, rather than inserting unseen hardcoded assets. Rebuild and publish your resource pack after changing glyph images, then reload VoxelCore to update the glyph registry.

## How spacing is calculated

Bitmap glyphs advance by their scaled pixel width **plus one Minecraft spacing pixel**. A 2x38 texture authored with `scale_ratio: 19` paints a 1px-wide section, but advances 2px. VoxelCore now inserts `:offset_-1:` between each tile (`tile_overlap: 1`), so adjacent tiles touch without the black stripes previously visible.

The effective full background width is computed from each configured glyph's actual advance. The widest of the three text lines determines how many center tiles are needed, plus `horizontal_padding` on each side. Text is moved back with a negative offset over the background. `x_offset` shifts the finished popup horizontally.

## Testing

```
/voxelcore admin ui tooltip 5 This is a test | Second Test | Third
```

`tile_overlap` should normally be 1. Increase it only if your artwork visibly overlaps and you need a different joining style. Custom UI textures can have different widths, as long as their scaled dimensions match how you intend them to join.

The three text-row ASCII providers remain generated because they are **font character mappings, not image assets**. The background textures are entirely content-defined.
