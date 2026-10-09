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

In VoxelCore's `config.yml`, define as many named variants as you need:


```yaml
ui:
  tooltips:
    default:
      left: 'voxel:tooltip_left'
      center: 'voxel:tooltip_center'
      right: 'voxel:tooltip_right'
      tile_overlap: 1
      horizontal_padding: 4
      letter_spacing: -1
      word_spacing: -1
      x_offset: 30
    warning:
      left: 'voxel:warning_left'
      center: 'voxel:warning_center'
      right: 'voxel:warning_right'
      # Values not listed here inherit from default.
      horizontal_padding: 6
      letter_spacing: -1
      word_spacing: -1
      x_offset: 30
    quest:
      left: 'voxel:quest_left'
      center: 'voxel:quest_center'
      right: 'voxel:quest_right'

```

The names `default`, `warning`, and `quest` are examples. Add any lowercase, alphanumeric, underscore or hyphen IDs. Other than `default`, variants can omit a value to inherit it from the default configuration. Existing `ui.tooltip` values from the single-tooltip implementation are migrated into `ui.tooltips.default` on the v6 configuration upgrade without overwriting existing variant values.


Use the **full** `namespace:id` for each configured glyph. A missing ID produces a clear error when the tooltip is tested, rather than inserting unseen hardcoded assets. Rebuild and publish your resource pack after changing glyph images, then reload VoxelCore to update the glyph registry.

## How spacing is calculated

Bitmap glyphs advance by their scaled pixel width **plus one Minecraft spacing pixel**. A 2x38 texture authored with `scale_ratio: 19` paints a 1px-wide section, but advances 2px. VoxelCore now inserts `:offset_-1:` between each tile (`tile_overlap: 1`), so adjacent tiles touch without the black stripes previously visible.

Horizontal text spacing can be adjusted independently for each tooltip variant. `letter_spacing: -1` removes one font pixel between adjacent printable ASCII glyphs within a word. `word_spacing: -1` removes one pixel from each ordinary space, while keeping words distinct. Legacy colour codes are ignored for spacing; custom Unicode icons retain their existing width. Both settings are included in the tooltip width calculation, so backgrounds fit the compacted text. Omit either value in a variant to inherit it from `default`.

The original horizontal position mode is unchanged: `x_offset: 30` shifts the centred popup horizontally. The experimental `anchor` feature was removed because a fixed offset already gives the desired position. Old `anchor` entries can be deleted; they are ignored.

Line ascent values remain the original `4`, `-1`, and `-6`. The spacing changes in this patch are **horizontal** and do not affect vertical row placement. To use the updated tooltip text rendering, install the new JAR. Existing UI glyph fonts and background textures remain unchanged.

The effective full background width is computed from each configured glyph's actual advance. The widest of the three text lines determines how many center tiles are needed, plus `horizontal_padding` on each side. Text is moved back with a negative offset over the background. `x_offset` shifts the finished popup horizontally.

## Testing variants

Use `/vc admin ui tooltip <variant> <seconds> <line1> | <line2> | <line3>`:


```text
/vc admin ui tooltip default 5 This is a test | Second Test | Third
/vc admin ui tooltip warning 5 &cDanger ahead! | Please be careful | &eKeep out
/vc admin ui tooltip quest 5 New Quest | Discover the portal | &aFollow the path

```

The command tab-completes configured tooltip variant names. Running it without enough arguments lists the available names. The previous command syntax `/vc admin ui tooltip 5 This is a test | Second Test | Third` remains supported, as do existing Java calls to `show(player, line1, line2, line3, ticks)`, both rendering the `default` variant.

New Java call sites can choose a style via `show(player, "warning", line1, line2, line3, ticks)`. A missing variant or glyph is reported as a descriptive error instead of showing a broken popup.

`tile_overlap` should normally be 1. Increase it only if your artwork visibly overlaps and you need a different joining style. Custom UI textures can have different widths, as long as their scaled dimensions match how you intend them to join.

The three text-row ASCII providers remain generated because they are **font character mappings, not image assets**. The background textures are entirely content-defined.
