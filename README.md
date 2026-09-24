# VoxelCore

VoxelCore is the version-aware content foundation for Voxel Horizons. The current MVP covers the complete custom-item pipeline: deterministic YAML pack discovery, inheritance compilation, immutable runtime snapshots, stable content identity, version-specific `ItemStack` creation, stable render allocations, deterministic Java resource-pack compilation, stable UI glyph/font compilation, and safe atomic reloads.

Higher-level gameplay systems such as placed blocks, furniture, vehicles, crops, GUIs, Bedrock/Geyser presentation, and legacy HavenCore migration are deliberately outside this MVP.

## Current implementation status

The repository has completed the roadmap's foundation, content/item, minimal Java pack, and Java UI font-content milestones (phases 0, 1, 2a, and 2c):

- multi-module Maven build with per-version shaded distributions
- automatic Paper capability detection with a Bukkit-compatible fallback
- schema-validated content packs and deterministic inheritance
- immutable item registry and atomic reload rollback
- persistent ContentID storage and version-aware item creation
- stable numeric and structured render allocations
- deterministic Java resource-pack validation and compilation
- compact cropped UI glyph definitions, stable private-use codepoints, generated bitmap/spacing font providers, white-safe UI/offset placeholders, and admin glyph lookup/copy commands
- exact real-server smoke coverage from Minecraft 1.12.2 through Minecraft 26.2
- snapshot JAR publication from `main`

Bedrock items/mappings, item actions and menu sessions, richer item metadata and behavior, placed blocks, furniture, crops, skills, NPCs, vehicles, and network persistence have not been implemented yet.

## Java UI font content

Phase 2c compiles shared spacing glyphs and namespaced bitmap UI definitions into every compatible generated Java pack. Textures can be tightly cropped: transparent padding is neither required nor used for positioning.

Use the compact ItemsAdder-style authoring shape:

```yaml
ui:
  warps_menu:
    path: ui/npc/warps_menu.png
    scale_ratio: 18
    y_position: 8
    gui: true
```

`path` is relative to `assets/<pack namespace>/textures/`. `scale_ratio` becomes the bitmap provider height and defaults to the PNG height. `y_position` becomes its ascent, defaults to `min(8, scale_ratio)`, may be negative, and cannot exceed `scale_ratio`. `gui` defaults to `false`; when `true`, the placeholder automatically appends the exact negative rendered advance so following title text returns to its original horizontal position. PNG dimensions and the scale ratio are limited to 256. An optional `symbol` may assign one private-use Unicode character; otherwise VoxelCore allocates a stable character and preserves the allocation in `glyph-allocations.yml`.

Menu rows are deliberately not configuration data. The same cropped overlay can cover any subset of slots in a 1–6 row inventory; its vertical placement is controlled only by `scale_ratio` and `y_position`.

VoxelCore generates the final `assets/minecraft/font/default.json`, including positive/negative spacing providers and all bitmap providers. If a content pack supplies its own `assets/minecraft/font/default.json`, VoxelCore preserves its providers in their authored order and appends the generated providers. Custom font files such as `assets/voxel/font/branding.json`, language overrides such as `assets/minecraft/lang/en_nz.json`, and their referenced textures are copied unchanged. Authored bitmap/space characters reserve their Unicode values globally, so automatic allocations skip them and an explicit `symbol` collision fails validation instead of silently replacing branding glyphs. Missing textures, unsafe paths, invalid dimensions, duplicate explicit symbols, and incompatible targets fail validation. Minecraft 1.12.2 is rejected because it predates resource-pack bitmap fonts.

Each definition has a readable alias such as `:warps_menu:` and an unambiguous full alias such as `:voxel/warps_menu:`. Runtime replacement prefixes every UI glyph with Minecraft white (`§f`, conventionally authored as `&f`) so gray inventory-title formatting does not tint the bitmap. Definitions marked `gui: true` then append their calculated negative advance; ordinary emoji, rank, and inline font images leave the cursor after the image. Initial pixel positioning uses `:offset_<pixels>:`, for example:

```text
:offset_-16::warps_menu:§rWarps
```

Offsets from -1024 through 1024 are supported. Non-power-of-two values are composed from multiple generated spacing glyphs, so `:offset_-17:` also resolves exactly.

VoxelCore resolves inline definitions (`gui: false`, including the default) in player chat only for senders with `voxelcore.placeholders.chat`. Definitions marked `gui: true` and `:offset_<pixels>:` controls require the separate `voxelcore.placeholders.chat.gui` permission. The permissions are independent: premium players can receive the inline permission for emojis or ranks, while staff can receive only the GUI permission when appropriate. Both default to operators and can be granted through the server permission manager. Unauthorized placeholders remain unchanged.

VoxelCore-owned UI and integrations should call `VoxelCore.getInstance().getTextPlaceholderService().resolve(text)` before sending titles or other text. A resource pack cannot mutate a title already created by an unrelated plugin; therefore DeluxeMenus cannot receive transparent `:name:` replacement from VoxelCore without a dedicated integration. Until that integration exists, use the literal allocated character returned by:

```text
/voxelcore admin ui copy voxel:warps_menu
/voxelcore admin ui copy :warps_menu:
```

Both forms have tab completion. `ui list` and `ui info <content-id-or-alias>` expose definitions and resolved metadata. `ui copy` sends a clickable clipboard component, supports Shift-click insertion, and prints the literal character and codepoint. On clients without direct clipboard support, clicking puts the character into the chat input so it can be selected and copied.

VoxelCore automatically resolves UI placeholders in player chat, inventory titles, and Bukkit player-list display names, headers, and footers. The player-list synchronizer runs after server ticks so values supplied by ordinary tab-list plugins are processed without a plugin-specific dependency. Integrations writing packet-only or Adventure-only components should call `VoxelCore#getTextPlaceholderService()` before sending their text.

When PlaceholderAPI is installed, VoxelCore registers an optional persistent `voxelcore` expansion. `%voxelcore_font_staff%` resolves exactly like `:staff:`, including GUI spacing behavior, while `%voxelcore_font_voxel/staff%` selects the explicit `voxel:staff` ContentID when aliases are ambiguous. Font names may contain underscores. Unknown or ambiguous names remain unresolved, and VoxelCore continues to work without PlaceholderAPI installed.

When ShopGUI+ is installed, VoxelCore registers an optional custom-item provider during ShopGUI+'s supported post-enable lifecycle. A shop entry can reference any concrete VoxelCore item by ContentID:

```yaml
1:
  type: item
  item:
    voxelcore: "voxel:item"
  buyPrice: 50
  sellPrice: 25
  slot: 0
```

ShopGUI+ creates the same persistent item stack as `/vc admin item give voxel:item`. Buying, selling, and comparison use the VoxelCore ContentID rather than display name, lore, material, or model data. Unknown and abstract IDs are rejected during ShopGUI+ shop loading. VoxelCore continues to work when ShopGUI+ is absent.

The value normally uses the exact key beneath `items:` (for example `voxel:ui-blank`). ShopGUI+ configurations may also use resource-style slash shorthand such as `voxel:ui/blank`; the provider resolves that to the hyphenated VoxelCore ContentID. A present but invalid `voxelcore:` reference fails immediately with its configuration path instead of leaving ShopGUI+ with a null button or shop item.

VoxelCore also resolves `:font_name:` and offset placeholders recursively throughout ShopGUI+'s live main, language, price-modifier, and shop configurations before shops are loaded. This includes strings nested in lore lists and maps, so the same syntax works in item names, lore, menu text, and language messages. The resolution is performed in memory—the authored ShopGUI+ YAML files retain their readable placeholders—and is repeated after ShopGUI+ shop reloads.

Because some ShopGUI+ releases do not expose their individual shop-file configuration through the published API, VoxelCore also resolves the compiled shop titles, per-page titles, fill items, shop-item names/lore, and placeholder items after every shop load. Configuration capabilities are detected at runtime so an API mismatch disables only the unavailable source rather than interrupting ShopGUI+ startup.

Player-specific lore that ShopGUI+ generates while opening a menu (including calculated buy/sell prices and currency suffixes) is resolved on the live top inventory across a short ten-tick render window. Inventory clicks and drags start another bounded render pass so buy/sell amount controls cannot restore unresolved text when they rebuild their preview stack. ShopGUI+'s live economy-provider prefixes and suffixes are also resolved after startup and reload, covering currency glyphs copied out of YAML before transaction messages or lore are assembled. Both legacy string metadata and modern Paper Adventure item components are supported. Only text containing a registered VoxelCore placeholder changes. ShopGUI+ language values are accessed through runtime-compatible configuration discovery and reprocessed after shop reloads, covering command confirmations and transaction messages without intercepting unrelated server chat.

## Next development milestone

The next recommended slice is **phase 3a: Java item actions and interaction dispatch**. It should add typed triggers and immutable compiled actions behind a central dispatcher while keeping menu session lifecycle in phase 3b.

This milestone adds resource-pack content only. It does not yet open inventories, handle clicks, introduce runtime menu sessions, or add alternate-client UI. Java item actions move back one place and follow after the UI content contract is proven.

## Item MVP

```text
content packs + assets
        ↓
deterministic discovery / bounded YAML parse
        ↓
inheritance + validation
        ↓
immutable ItemDefinitionRegistry
        ↓
stable render allocation reconciliation
        ↓
platform preflight validation
        ↓
atomic ContentSnapshot publication
        ↓
ItemManager + version adapter
        ↓
ItemStack with persistent ContentID

same definitions + allocations
        ↓
deterministic Java resource-pack compiler
```

Failed startup validation aborts plugin enable. Failed reload validation leaves the previous content revision and allocation state active.

### Server software capabilities

VoxelCore automatically detects Paper at startup and selects its Paper capability provider. Other supported Bukkit-compatible servers use the fallback provider, so no configuration toggle or separate plugin JAR is required. The server-software capability layer is independent of the Minecraft version adapter and is the extension point for future Paper-specific implementations.

## Runtime version families

Install exactly one distribution jar matching the server family.

| Minecraft | Module | API version | Runtime path |
|---|---|---:|---|
| 1.12.x–1.13.x | `voxelcore-v1_12` | `1.13` | legacy NBT identity, durability rendering |
| 1.14–1.19.3 | `voxelcore-v1_14` | `1.14` | PDC identity, numeric Custom Model Data |
| 1.19.4–1.20.4 | `voxelcore-v1_19_4` | `1.19` | PDC/CMD plus display-entity-era capability boundary |
| 1.20.5–1.21.3 | `voxelcore-v1_20_5` | `1.20.5` | data-component-era family with scalar CMD compatibility |
| 1.21.4 | `voxelcore-v1_21_4` | `1.21.4` | item-model component and structured Custom Model Data |
| 26.2 | `voxelcore-v26_2` | `26.2` | Paper 26.2 item-model component and structured Custom Model Data |

Each shaded distribution embeds the corresponding `api-version` in `plugin.yml`; CI inspects the finished JARs to prevent legacy material compatibility from being enabled accidentally.

The real-server matrix validates **1.12.2, 1.13.2, 1.14.4, 1.19.4, 1.20.5, 1.21.4, and 26.2**. Intermediate versions in the first four family ranges are selected by their providers but are not individually smoke-tested.

`voxelcore-v1_21_4` and `voxelcore-v26_2` intentionally accept only their exact validated versions. VoxelCore does not treat the gap between 1.21.4 and 26.2, or a future version after 26.2, as compatible without a dedicated adapter and pack profile.

## Build

Building the complete reactor requires **JDK 25** because the Minecraft 26.2/Paper module targets Java 25. Older distribution modules still emit bytecode for their own required Java level.

Build every distribution:

```bash
mvn -U clean package
```

Or one family and its dependencies:

```bash
mvn -pl voxelcore-v1_21_4 -am clean package
```

Every successful push to `main` updates the prerelease tag `v1.0-SNAPSHOT` with all six version-family JARs. Pull-request builds also upload each distribution as a workflow artifact.

## Runtime directory

```text
plugins/VoxelCore/
├── config.yml
├── render-allocations.yml
├── content/
│   └── <pack>/
│       ├── pack.yml
│       ├── content/
│       │   └── **/*.yml
│       └── assets/
│           └── <namespace>/
│               ├── models/
│               ├── textures/
│               ├── font/
│               └── sounds/
└── build/
    └── resource-packs/
```

`content/` may be nested however you like. File and folder names do not define a ContentID; the key beneath `items:` does.

## Pack manifest

```yaml
schema: 1
namespace: mypack
dependencies:
  - core
  - shared
```

Supported keys are `schema`, `namespace`, and optional `dependencies`.

A cross-pack inheritance or model reference is accepted only when the referenced namespace is declared as a dependency. A pack may always reference its own namespace and Minecraft resources where appropriate.

## Item authoring

```yaml
items:
  gem_base:
    abstract: true
    material: minecraft:paper
    bound: false
    lore:
      - Shared values for inherited gemstone definitions

  ruby:
    extends: gem_base
    abstract: false
    display_name: Ruby
    bound: false
    render:
      model: mypack:item/ruby
      unbreakable: true
      durability: 0
      attributes:
        hide_attributes: true
      custom_model_data: 1001
    properties:
      category: gemstone
```

Supported item fields:

- `extends`
- `type`
- `material`
- `display_name`
- `lore`
- `bound`
- `abstract`
- `render`
- `properties`
- `events`

Unknown keys and malformed values are rejected rather than ignored.

### Abstract definitions and bound state

`abstract` is an inherited scalar for reusable definitions that must not become game items. A resolved `abstract: true` definition remains available as an inheritance parent and through `item info`, but it is hidden from `item list` and cannot be created by `item give` or `item verify`. Abstract definitions with both `material` and `render.model` receive a stable render allocation and pack model for addon use (for example VoxelFurniture neighbor variants); the internal `ItemManager.createRenderItem` API is not a player give command. Abstract definitions without a render model may omit `material`; concrete definitions may not.

Because `abstract` is inherited, a concrete child of an abstract parent must explicitly set `abstract: false`.

`bound` is independent of abstraction and visibility. It remains inherited item metadata intended for future ownership restrictions; full bound-item inventory, drop, death, container, trade, and cross-server transfer enforcement is not implemented yet.

### Identity

Persistent gameplay identity is a namespaced `ContentID`, for example:

```text
mypack:ruby
```

It is independent of Custom Model Data and model paths. IDs are normalized to lowercase and each namespace/value segment uses `[a-z0-9._-]+`.

### Inheritance

VoxelCore currently supports one parent per item. Resolution is bounded and cycle-aware: missing parents, duplicate IDs, and circular inheritance fail compilation.

Merge behavior:

- omitted scalar → inherit parent
- provided scalar → replace parent
- explicit `false` → replaces inherited `true`
- list → child replaces parent list
- `properties` maps → recursive deep merge
- render metadata → field-by-field merge
- structured `custom_model_data` → semantic-key merge when both sides are structured

Compiled definitions and nested property/render-rule structures are immutable.

## Custom blocks

VoxelCore supports real, full-cube custom blocks intended for ores, building blocks, logs, and similar content. It does not use display entities or furniture-style placement; furniture belongs in an addon such as VoxelFurniture.

The smallest definition needs one texture:

```yaml
blocks:
  ruby_ore:
    display_name: "&cRuby Ore"
    texture: mypack:block/ruby_ore
    break_tools:
      - PICKAXE
    minimum_tool_tier: IRON
    drop: ruby_ore
```

`model: cube_column` supports a log-style side/top/bottom layout, while `model: cube` supports six independent faces:

```yaml
blocks:
  ruby_log:
    model: cube_column
    textures:
      side: mypack:block/ruby_log
      top: mypack:block/ruby_log_top
      bottom: mypack:block/ruby_log_bottom

  directional_ore:
    model: cube
    textures:
      north: mypack:block/ore_north
      south: mypack:block/ore_south
      east: mypack:block/ore_east
      west: mypack:block/ore_west
      up: mypack:block/ore_top
      down: mypack:block/ore_bottom
```

Supported block fields are `extends`, `abstract`, `display_name`, `method`, `model`, `texture`, `textures`, `hardness`, `blast_resistance`, `break_tools`, `minimum_tool_tier`, `stackable`, `explosion_immune`, `drop_when_mined`, `drop`, `silk_touch`, and `events`. Block definitions support the same bounded, cycle-aware inheritance rules as items.

`break_tools` is an optional harvesting whitelist, not a destruction whitelist. Category entries `PICKAXE`, `AXE`, `SHOVEL`, `HOE`, `SWORD`, `SHEARS`, and `HAND` match the corresponding family, while a material such as `DIAMOND_PICKAXE` matches only that exact vanilla item. Namespaced VoxelCore item IDs such as `voxel:ruby_drill` are also supported. `minimum_tool_tier` can be `WOOD`, `GOLD`, `STONE`, `IRON`, `DIAMOND`, or `NETHERITE`; combine `PICKAXE` with `IRON` for vanilla gold/diamond-ore harvesting. A wrong tool or an empty hand still breaks the block but produces no configured drop. Omitting both fields lets any held item harvest the block, while Creative breaking never produces a drop.

Generated block items are stackable by default and use a clean stackable carrier on modern versions. Set `stackable: false` to opt into a non-stackable carrier. Minecraft 1.12–1.13 automatically use a hidden damageable carrier because those clients require durability predicates for custom inventory models. `display_name` and inherited item lore accept legacy `&` formatting codes on every supported server version.

Carrier methods in this first implementation:

| Method | Modern servers | 1.12 legacy servers | Status |
|---|---|---|---|
| `auto` | selects `solid` | selects `mushroom` | supported |
| `solid` | unused note-block states | legacy mushroom states | supported |
| `mushroom` | unused mushroom face states | legacy mushroom states | supported |
| `transparent`, `wire`, `fire` | — | — | recognized but rejected until a safe runtime implementation is available |

Allocations are persisted in `plugins/VoxelCore/block-allocations.yml`. Removed IDs become tombstones instead of silently reusing a state, so existing worlds remain stable. Do not delete this file on a live server. Interaction, physics, and note playback are suppressed for allocated carrier states, and `explosion_immune: true` removes the block from explosion damage lists.

On Minecraft 1.20.5 and newer, `hardness` controls survival mining time through the native player block-break-speed attribute. VoxelCore applies the ratio between the carrier's vanilla hardness and the configured hardness without replacing the player's base value or other active modifiers. Effects and modifiers therefore continue to compose with the custom hardness, while vanilla carrier-specific tool effectiveness remains the underlying behavior. `hardness: 0` is instant-break. Minecraft 1.12–1.20.4 retain carrier-native mining time. `blast_resistance` remains compiled metadata; non-immune blast-strength simulation is not yet implemented.

The resource-pack compiler generates the block model, inventory model, and complete carrier blockstate tables. Authors only supply the referenced texture PNGs. Every concrete block automatically receives a matching placeable inventory item, and its default drop is that item. An explicit `items:` definition with the same content ID overrides the generated item when a special 2D icon or custom item behavior is required.

On Paper versions with `PlayerPickBlockEvent`, Creative middle-click on a placed custom block selects its matching VoxelCore item from the inventory, or creates one in the selected hotbar slot if absent. Older server APIs continue to load without this optional pick-block behavior.

## Actions

Items and blocks can declare reusable event actions. For example, a 2D ore item can place its corresponding block:

```yaml
items:
  ruby_ore:
    material: minecraft:paper
    render:
      model: mypack:item/ruby_ore
    events:
      interact:
        right:
          actions:
            - type: set_block
              block: ruby_ore
              target: relative
              replace: air_only
              consume: 1
```

Item triggers are `interact.right`, `interact.right_shift`, `interact.left`, and `interact.left_shift`. Block triggers are `placed_block.interact` and `placed_block.break`.

Available actions are:

- `set_block`: `block`, optional `target` (`relative`, `clicked`, or `target`), `replace`, and `consume`
- `remove_block`: optional `target`
- `command`: `command` and optional `executor` (`player` or `console`)
- `give_item` / `drop_item`: `item` and optional `amount`
- `message`: `text`
- `cancel`

Command and message strings support `{player}`, `{player_uuid}`, `{world}`, `{x}`, `{y}`, `{z}`, `{item_id}`, and `{block_id}` placeholders. Unknown action keys and missing content references fail validation rather than being ignored.

## Rendering

Rendering state is separate from item identity.

### Common render metadata

```yaml
render:
  model: mypack:item/ruby
  unbreakable: true
  durability: 4
  attributes:
    hide_attributes: true
  custom_model_data: 1001
```

`attributes` currently maps to Bukkit `ItemFlag` names. It is **not** the future combat `AttributeModifier` system.

`durability` is independent of `custom_model_data`. Before 1.14, Custom Model Data has no runtime representation; legacy resource-pack damage predicates use explicit `render.durability`.

### Structured Custom Model Data — 1.21.4

```yaml
render:
  model: mypack:item/ruby
  custom_model_data:
    variant: red
    powered: true
    intensity: 0.75
    tint: '#ff0000'
```

VoxelCore allocates stable typed indices for semantic keys in `render-allocations.yml`; adding/removing a key does not renumber existing keys.

The inferred types are:

- number → float
- boolean → flag
- ordinary string → string
- `#RRGGBB` → color

### Modern render rules — 1.21.4

`render.rule` tells the generated item-model graph how to read structured state:

```yaml
render:
  model: mypack:item/ruby
  custom_model_data:
    variant: red
    powered: true
    intensity: 0.75
    tint: '#ff0000'
  rule:
    select:
      key: variant
      cases:
        red:
          condition:
            key: powered
            true:
              range:
                key: intensity
                entries:
                  0.75:
                    model:
                      id: mypack:item/ruby_powered
                      tint: tint
                fallback: mypack:item/ruby
            false: mypack:item/ruby
      fallback: mypack:item/ruby
```

Rule nodes support `select`, `condition`, `range`, and `model`. Pre-1.21.4 pack targets reject `render.rule` rather than silently discarding it.

### Dyeable custom items — 1.21.4+

An item whose model faces use `tintindex: 0` can expose a mutable per-stack colour:

```yaml
render:
  model: voxel:furniture/curtain/curtain_closed
  custom_model_data:
    color: '#FFFFFF'
  rule:
    model:
      id: voxel:furniture/curtain/curtain_closed
      tint: color
properties:
  dyeable:
    key: color
    color: '#FFFFFF'
```

Players can place a vanilla dye onto the item in an inventory to recolour it. The selected RGB value is stored in
structured Custom Model Data, so the content ID remains unchanged and differently coloured stacks remain variants of
the same item. Addons can use `ItemManager.getDyeColor`, `setDyeColor`, and `createRenderItem(id, source)` to preserve
the mutable colour in their own renderers. Dynamic tinting is disabled safely before 1.21.4.

## Assets

Authored model reference:

```yaml
render:
  model: mypack:item/ruby
```

maps to:

```text
plugins/VoxelCore/content/mypack/assets/mypack/models/item/ruby.json
```

A model texture such as `mypack:item/ruby` maps to:

```text
plugins/VoxelCore/content/mypack/assets/mypack/textures/item/ruby.png
```

Generated `minecraft` override/item-model glue is compiler-owned and is written only to build output.

## Stable render allocations

`plugins/VoxelCore/render-allocations.yml` is the shared authority for runtime item creation and pack compilation. The current manifest schema is `3`.

It persists:

- numeric Custom Model Data allocations
- inactive tombstones so removed IDs do not silently recycle allocations
- model association
- stable structured float/flag/string/color semantic-key indices

Do not casually delete this file on a live content set: doing so discards allocation history.

## Resource-pack targets

The MVP compiler intentionally exposes exact validated profiles instead of pretending one format works for an entire historical range:

| Target | Mode | Pack format |
|---|---|---:|
| `mc-1.12.2` | legacy durability/damage predicate | 3 |
| `mc-1.13.2` | legacy durability/damage predicate | 4 |
| `mc-1.14.4` | numeric Custom Model Data | 4 |
| `mc-1.19.4` | numeric Custom Model Data | 13 |
| `mc-1.20.5` | numeric Custom Model Data | 32 |
| `mc-1.21.4` | item-model / structured CMD | 46 |
| `mc-26.2` | item-model / structured CMD | 88.0 |

Minecraft 26.2 uses modern `min_format`/`max_format` pack metadata. Earlier targets retain the legacy `pack_format` field.

Pack output is deterministic and stored below:

```text
plugins/VoxelCore/build/resource-packs/<target>.zip
```

## Admin commands

```text
/voxelcore admin content info
/voxelcore admin content reload
/voxelcore admin content rl

/voxelcore admin item list
/voxelcore admin item info <content-id>
/voxelcore admin item give <content-id> [amount] [player]
/voxelcore admin item identify
/voxelcore admin item id
/voxelcore admin item verify <content-id>
/voxelcore admin item test <content-id>

/voxelcore admin block list
/voxelcore admin block info <content-id>
/voxelcore admin block give <content-id> [amount] [player]
/voxelcore admin block identify

/voxelcore admin pack info
/voxelcore admin pack validate [target]
/voxelcore admin pack build [target]
```

When `[target]` is omitted, VoxelCore uses the current server version only when an exact validated pack profile exists. Otherwise specify a target explicitly.

`pack build` publishes the completed ZIP atomically, preserving the previous successful artifact when compilation fails. After a successful build it also performs the safe content reload below, so new items and `:name:` UI placeholders become active without a second command. If live reload validation fails, the built ZIP is retained while the previous runtime revision stays active and the command reports both outcomes.

The final pack is size-optimized deterministically: JSON and `pack.mcmeta` whitespace is removed without modifying string values, PNGs are losslessly re-encoded only when the result is smaller, and each ZIP entry uses maximum DEFLATE compression only when it beats storing the optimized bytes directly. Rebuilding identical content therefore produces identical pack bytes without inflating already-compressed images.

`item list` reports only concrete definitions. Abstract definitions can still be inspected directly with `item info` and used as inheritance parents; `bound` does not affect list visibility.

## Safe reloads

Reload builds the candidate revision in isolation:

1. discover and parse packs
2. resolve inheritance
3. compile immutable definitions
4. reconcile stable item and block allocations
5. preflight every definition through the active platform adapter
6. persist the validated allocation state
7. atomically publish the new snapshot

Any failure before publication leaves the existing runtime revision active.

## Configuration migration

`config.yml` contains a schema `version`. When the bundled schema changes, VoxelCore backs up the old file as `config.yml.old`, `config.yml.old.1`, etc., adds newly introduced defaults, updates the schema version, and preserves existing user values instead of replacing the configuration wholesale.

`game.bedrock_support` is currently only a future integration setting; setting it to `true` does not provide Bedrock support in this MVP.

## MVP validation

GitHub Actions performs:

- full Maven build of all distribution jars
- unit tests for parsing, inheritance, immutable snapshots, allocation persistence/tombstones, UI/offset placeholder resolution, structured indices, modern rule generation, and reload rollback
- real-server smoke tests for 1.12.2, 1.13.2, 1.14.4, 1.19.4, 1.20.5, 1.21.4, and 26.2
- item create/identify verification
- exact-target pack validate/build
- failed reload preservation followed by successful reload

## Deliberately outside this MVP

These are next systems, not blockers for the item/content MVP:

- placed custom blocks
- furniture and display-entity placement lifecycle
- vehicles
- crops
- GUI/menu framework and Bedrock presentation
- Geyser/Floodgate mappings
- true Minecraft attribute modifiers
- richer potion/skull/book/map/firework metadata
- generic low-level post-1.20.5 component escape hatches
- HavenCore backward-compatibility/import aliases
- support for additional Minecraft families beyond the explicit validated adapters

See `Migration-Roadmap.md` for the broader rebuild plan and architectural rationale. Its repository review appendices describe the original audited snapshots; the implementation-status section in this README is the canonical description of current VoxelCore functionality.
