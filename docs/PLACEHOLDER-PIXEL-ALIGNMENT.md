# Pixel-aligned placeholders for single FancyHolograms text displays

VoxelCore exposes these optional PlaceholderAPI placeholders when PlaceholderAPI is installed:

- `%voxelcore_topdisplay_<rank>%` — reads JetsAntiAFKPro's `top_<rank>_player_name`, returns the current Bukkit display name if the player is online, the saved username if the ranked player is offline, and gray `Unknown` if the rank is vacant. Requires JetsAntiAFKPro for the leaderboard source.
- `%voxelcore_pad_<width>_<text>%` — resolves bracket-wrapped nested placeholders, measures the resulting text's **Minecraft:default font pixel advance**, and appends VoxelCore's pre-existing invisible spacing glyphs up to the specified target width. Supports target widths 0–1024; does not truncate overlong strings.

The spacing glyphs are provided by VoxelCore's generated Java resource pack. Install its current pack alongside the new plugin build; without those glyphs, the padding cannot render.

## Example (all rows in ONE FancyHolograms hologram)

Keep the hologram's title and footer lines unchanged, set text alignment to `left`, and replace the ten ranked rows with:

```yaml
    - '&d%voxelcore_pad_28_1.%&f%voxelcore_pad_156_{voxelcore_topdisplay_1}% &e- &f%logic_playtime-time_1%'
    - '&d%voxelcore_pad_28_2.%&f%voxelcore_pad_156_{voxelcore_topdisplay_2}% &e- &f%logic_playtime-time_2%'
    - '&d%voxelcore_pad_28_3.%&f%voxelcore_pad_156_{voxelcore_topdisplay_3}% &e- &f%logic_playtime-time_3%'
    - '&d%voxelcore_pad_28_4.%&f%voxelcore_pad_156_{voxelcore_topdisplay_4}% &e- &f%logic_playtime-time_4%'
    - '&d%voxelcore_pad_28_5.%&f%voxelcore_pad_156_{voxelcore_topdisplay_5}% &e- &f%logic_playtime-time_5%'
    - '&d%voxelcore_pad_28_6.%&f%voxelcore_pad_156_{voxelcore_topdisplay_6}% &e- &f%logic_playtime-time_6%'
    - '&d%voxelcore_pad_28_7.%&f%voxelcore_pad_156_{voxelcore_topdisplay_7}% &e- &f%logic_playtime-time_7%'
    - '&d%voxelcore_pad_28_8.%&f%voxelcore_pad_156_{voxelcore_topdisplay_8}% &e- &f%logic_playtime-time_8%'
    - '&d%voxelcore_pad_28_9.%&f%voxelcore_pad_156_{voxelcore_topdisplay_9}% &e- &f%logic_playtime-time_9%'
    - '&d%voxelcore_pad_28_10.%&f%voxelcore_pad_156_{voxelcore_topdisplay_10}% &e- &f%logic_playtime-time_10%'
```

This reuses the `%logic_playtime-time_<rank>%` placeholders already configured to return `0D 0h 0m` for vacant ranks. It does not require the Animations expansion, manual spaces after ranks, or a second hologram.

- The **rank** field is 28 Minecraft font pixels.
- The **display name** field is 156 pixels.
- Both the `-` separator and time begin at the same fixed column.
- Adjust 156 if the server permits long display names; if a name itself exceeds 156px, it is preserved but the separator follows the full name rather than clipping it.

### Supported formatting and limitations

The font-width calculator uses vanilla Mojangles advances for ASCII, skips legacy `&` and `§` colour/style codes, understands common MiniMessage tags, and handles bold advances. For unsupported Unicode or custom-font icons, measurement uses an estimated advance; resource-pack-defined custom glyph widths need a font-metrics integration for *fully exact* positioning.

Bukkit display names are available only for connected players. When a ranked player is offline, VoxelCore deliberately retains the username rather than displaying an incorrect viewer's display name. An Essentials offline nickname source could be integrated separately.

This feature is additive to the existing `%voxelcore_font_<name>%` expansion.
