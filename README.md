# VoxelCore

VoxelCore is the shared content foundation for Voxel Horizons. It is being rebuilt as a version-aware Minecraft content library capable of loading custom content from YAML, compiling inheritance once at startup or reload, and creating version-appropriate Minecraft `ItemStack` instances while preserving a stable VoxelCore content identity.

The current MVP focuses on the custom item/content pipeline. Blocks, furniture, vehicles, GUIs, resource-pack generation, legacy HavenCore migration, pack priorities, variants, and other higher-level systems are planned but are not part of the current supported authoring surface.

## Contents

- [Current MVP](#current-mvp)
- [Supported Minecraft versions](#supported-minecraft-versions)
- [Installation](#installation)
- [First startup](#first-startup)
- [Main configuration](#main-configuration)
- [Content directory](#content-directory)
- [Creating a content pack](#creating-a-content-pack)
- [Creating items](#creating-items)
- [Item field reference](#item-field-reference)
- [Content IDs and namespaces](#content-ids-and-namespaces)
- [Inheritance](#inheritance)
- [Rendering and model metadata](#rendering-and-model-metadata)
- [Properties](#properties)
- [Reloading content safely](#reloading-content-safely)
- [Commands](#commands)
- [Permissions](#permissions)
- [Complete example pack](#complete-example-pack)
- [Troubleshooting](#troubleshooting)
- [Development and testing](#development-and-testing)
- [Current limitations](#current-limitations)

---

## Current MVP

The current item/content MVP provides this full runtime path:

```text
YAML content pack
    ↓
Content pack discovery
    ↓
Raw item definitions
    ↓
Inheritance compiler
    ↓
Immutable item registry
    ↓
Runtime content snapshot
    ↓
ItemManager
    ↓
Version-specific platform adapter
    ↓
Minecraft ItemStack
    ↓
Persistent VoxelCore ContentID
```

The same authored item definition is interpreted by a different platform adapter depending on the Minecraft server version.

The MVP has real-server smoke coverage for both architectural extremes:

- Minecraft 1.12.2 / Java 8 / Spigot / legacy NBT identity
- Minecraft 1.21.4 / Java 21 / Paper / modern PDC and item-model APIs

The smoke tests also verify that failed content reloads preserve the previously active content revision.

---

## Supported Minecraft versions

VoxelCore is distributed as version-family-specific jars.

| Minecraft version | Module | Distribution jar | Notes |
|---|---|---|---|
| 1.12.x - 1.13.x | `voxelcore-v1_12` | `VoxelCore-<version>-mc1.12-1.13.jar` | Legacy identity path. See the 1.13 limitation below. |
| 1.14 - 1.19.3 | `voxelcore-v1_14` | `VoxelCore-<version>-mc1.14-1.19.3.jar` | Persistent Data Container and numeric Custom Model Data. |
| 1.19.4 - 1.20.4 | `voxelcore-v1_19_4` | `VoxelCore-<version>-mc1.19.4-1.20.4.jar` | Adds display-entity-era capabilities. |
| 1.20.5 - 1.21.3 | `voxelcore-v1_20_5` | `VoxelCore-<version>-mc1.20.5-1.21.3.jar` | Data-component-era platform family. |
| 1.21.4+ | `voxelcore-v1_21_4` | `VoxelCore-<version>-mc1.21.4-plus.jar` | Structured Custom Model Data and item-model support. |

Do not install multiple VoxelCore distribution jars at the same time. Pick the one matching the Minecraft version of the server.

### Important 1.13 note

The current `v1_12` adapter family also covers 1.13.x, but 1.13 introduced Minecraft's material flattening and many material names changed. A successful 1.12.2 test does not guarantee that every material name authored for 1.12 will resolve correctly on 1.13.

Dedicated 1.13 material translation or a separate compatibility layer is still planned.

---

## Installation

1. Build or obtain the VoxelCore jar matching your Minecraft version.
2. Place that jar in the server's `plugins/` directory.
3. Remove any VoxelCore jar for a different Minecraft version family.
4. Start the server.
5. Confirm the startup log contains a readiness line similar to:

```text
VOXELCORE_READY revision=1 items=0 platform=1.21.4
```

If content packs already exist, the item count will be greater than zero.

### Building a distribution yourself

From the repository root, build the required module with Maven.

Example for 1.21.4+:

```bash
mvn -pl voxelcore-v1_21_4 -am clean package
```

Example for 1.12.x / 1.13.x:

```bash
mvn -pl voxelcore-v1_12 -am clean package
```

`-am` tells Maven to also build the common/plugin dependencies required by the selected distribution.

---

## First startup

VoxelCore stores its runtime data under:

```text
plugins/VoxelCore/
```

On first startup VoxelCore creates:

```text
plugins/VoxelCore/
├── config.yml
└── content/
```

An empty `content/` directory is valid. VoxelCore starts with an empty item registry at content revision `1`.

If authored content exists but is invalid during initial startup, VoxelCore aborts startup instead of silently publishing a partially valid registry.

This is intentional. A broken pack should be fixed rather than ignored.

---

## Main configuration

The generated `config.yml` currently contains:

```yaml
version: 1 # DO NOT CHANGE

game:
  bedrock_support: false
```

### `version`

Do not modify this value manually.

VoxelCore uses it to detect configuration schema changes. If the installed plugin expects a different configuration version, the existing file is moved to:

```text
config.yml.old
```

and a fresh default configuration is generated.

### `game.bedrock_support`

```yaml
game:
  bedrock_support: false
```

This setting is present as a future integration point for Geyser/Floodgate/Bedrock behavior.

**Current status:** the item/content MVP does not yet implement Bedrock-specific runtime behavior from this setting. Enabling it should not currently be treated as complete Bedrock support.

---

## Content directory

Content packs live under:

```text
plugins/VoxelCore/content/
```

Each immediate subdirectory is treated as one content pack.

For example:

```text
plugins/VoxelCore/content/
├── furniture/
│   ├── pack.yml
│   └── definitions/
│       ├── chairs.yml
│       └── tables.yml
│
└── weapons/
    ├── pack.yml
    └── definitions/
        └── weapons.yml
```

VoxelCore discovers pack directories and definition files deterministically.

Every discovered pack directory must contain a valid `pack.yml`.

---

## Creating a content pack

Create a directory beneath `plugins/VoxelCore/content/`.

Example:

```text
plugins/VoxelCore/content/my_pack/
```

Then create:

```text
plugins/VoxelCore/content/my_pack/pack.yml
```

with:

```yaml
schema: 1
namespace: mypack
```

### `pack.yml` reference

Only these keys are currently supported:

| Key | Required | Type | Description |
|---|---|---|---|
| `schema` | Yes | Integer | Content-pack schema. Must currently be `1`. |
| `namespace` | Yes | String | Namespace used for local item IDs in this pack. |

Unknown manifest keys are rejected.

The namespace is normalized to lowercase and must contain only:

```text
a-z
0-9
.
_
-
```

Examples of valid namespaces:

```text
voxelhorizons
my_pack
furniture.v1
server-items
```

Examples of invalid namespaces:

```text
My Pack
my:pack
Furniture/Pack
```

---

## Creating items

Create one or more YAML files under the pack's `definitions/` directory.

Example:

```text
plugins/VoxelCore/content/my_pack/
├── pack.yml
└── definitions/
    └── items.yml
```

A definition file uses an `items:` mapping:

```yaml
items:
  ruby:
    material: minecraft:paper
    display_name: Ruby

  ruby_block_item:
    material: minecraft:paper
    display_name: Ruby Block
```

The resulting content IDs are:

```text
mypack:ruby
mypack:ruby_block_item
```

The filename does not define the item namespace or ID. The item key under `items:` does.

You may split items across multiple YAML files for organization:

```text
definitions/
├── materials.yml
├── furniture.yml
├── weapons.yml
└── food.yml
```

Every definition file currently supports only the top-level `items:` key. Unknown top-level keys are rejected.

---

## Item field reference

An item currently supports the following fields:

```yaml
items:
  example_item:
    extends: parent_item
    type: item
    material: minecraft:paper
    display_name: Example Item
    lore:
      - First lore line
      - Second lore line
    bound: false
    render:
      model: mypack:item/example_item
      legacy_custom_model_data: 1001
    properties:
      custom_section:
        enabled: true
```

### Supported fields

| Field | Type | Inheritable | Description |
|---|---|---:|---|
| `extends` | String | No | Parent ContentID to inherit from. |
| `type` | String | Yes | VoxelCore item category. Defaults to `ITEM`. |
| `material` | String | Yes | Base Minecraft material used to create the ItemStack. Required after inheritance resolves. |
| `display_name` | String | Yes | Display name applied to the generated item. |
| `lore` | List of strings | Yes | Item lore. Child list replaces parent list. |
| `bound` | Boolean | Yes | General bound-state flag stored in the compiled definition. Defaults to `false`. |
| `render` | Mapping | Yes | Version-aware rendering metadata. |
| `properties` | Mapping | Yes | Arbitrary structured properties for higher-level systems. |

Unknown item keys are rejected. This is intentional so misspelled configuration does not silently disappear.

### `type`

The currently recognized item types are:

```text
ITEM
BLOCK
TOOL
WEAPON
ARMOR
FOOD
FURNITURE
VEHICLE
GUI
MISC
```

YAML values are case-insensitive because VoxelCore normalizes the provided value before matching the enum.

For example:

```yaml
type: furniture
```

is valid.

At the current MVP stage, these categories primarily classify the definition. The specialized gameplay systems for blocks, furniture, vehicles, GUIs, and similar types are not implemented yet.

### `material`

Example:

```yaml
material: minecraft:paper
```

`material` is the physical Minecraft material used by the selected platform adapter to create the base stack.

The content compiler intentionally does not hard-code one version's complete `Material` enum because VoxelCore supports multiple Minecraft version families.

A material therefore needs to be valid on the server version on which that content pack is being used.

### `display_name`

Example:

```yaml
display_name: Oak Chair
```

If inherited, a child can replace it simply by declaring a new value.

### `lore`

Example:

```yaml
lore:
  - A custom VoxelCore item
  - Created from YAML
```

Lore must be a YAML list and every entry must be a string.

When inherited, a child-provided lore list replaces the parent's lore list. It is not appended automatically.

### `bound`

Example:

```yaml
bound: true
```

or:

```yaml
bound: false
```

The field is nullable during raw compilation so an omitted child value can inherit the parent value.

This also means an explicit `false` correctly overrides an inherited `true`.

---

## Content IDs and namespaces

VoxelCore uses namespaced IDs as the persistent identity of authored content.

Format:

```text
namespace:value
```

Example:

```text
voxelhorizons:oak_chair
```

This identity is deliberately independent from Minecraft rendering metadata such as Custom Model Data.

### Local IDs

Inside a pack with:

```yaml
namespace: furniture
```

this item:

```yaml
items:
  oak_chair:
    material: minecraft:paper
```

becomes:

```text
furniture:oak_chair
```

### Explicit namespaced IDs

An item key itself must remain inside the pack namespace.

VoxelCore rejects an item declared in one pack using a different namespace.

### ID rules

Both namespace and value are normalized to lowercase and support:

```text
[a-z0-9._-]+
```

Spaces, slashes, uppercase-only identifiers, and other unsupported characters should not be used.

### Command shorthand

The current admin item commands default an unqualified ID to the namespace:

```text
voxelhorizons
```

Therefore this:

```text
/voxelcore admin item info oak_chair
```

is interpreted as:

```text
voxelhorizons:oak_chair
```

For packs using another namespace, specify the complete ContentID:

```text
/voxelcore admin item info mypack:oak_chair
```

Using fully qualified IDs is recommended in documentation, scripts, and administration workflows.

---

## Inheritance

VoxelCore supports single-parent item inheritance through `extends`.

Example:

```yaml
items:
  chair_base:
    material: minecraft:paper
    type: furniture
    bound: true
    lore:
      - A furniture item

  oak_chair:
    extends: chair_base
    display_name: Oak Chair
    render:
      model: furniture:item/oak_chair
```

`oak_chair` inherits the parent material, type, bound value, and lore.

### Relative parent references

Inside a pack whose namespace is `furniture`:

```yaml
extends: chair_base
```

means:

```text
furniture:chair_base
```

### Cross-pack parent references

You may reference a fully namespaced parent:

```yaml
extends: shared:chair_base
```

The referenced definition must exist when the complete content set is compiled.

### Multi-level inheritance

Inheritance may span several levels:

```yaml
items:
  base:
    material: minecraft:paper
    bound: true

  red_item:
    extends: base
    display_name: Red Item

  special_red_item:
    extends: red_item
    display_name: Special Red Item
    bound: false
```

The compiler resolves the dependency graph once during startup/reload.

### Merge rules

Current merge semantics are:

- Scalar value: child replaces parent when provided.
- Omitted value: inherits parent.
- Boolean: explicit `false` overrides inherited `true`.
- Lore/list: child list replaces parent list.
- Render mapping: merged field-by-field.
- Properties mapping: recursively deep merged.
- Properties scalar/list value: child value replaces parent value.

### Missing parent

This is invalid:

```yaml
items:
  broken_item:
    extends: missing_parent
```

Reload/startup fails with a dependency error rather than endlessly retrying.

### Circular inheritance

This is invalid:

```yaml
items:
  a:
    extends: b
    material: minecraft:paper

  b:
    extends: a
```

VoxelCore detects the cycle and rejects the new content revision.

---

## Rendering and model metadata

Rendering configuration is intentionally separate from VoxelCore content identity.

Example:

```yaml
render:
  legacy_custom_model_data: 1001
  model: mypack:item/ruby
```

The same definition can carry metadata useful to different version families.

### `legacy_custom_model_data`

Example:

```yaml
render:
  legacy_custom_model_data: 1001
```

This value is used by platform families that represent item rendering with numeric Custom Model Data.

It is also bridged into the modern structured Custom Model Data component by the 1.21.4+ adapter where appropriate.

The value must be numeric.

### `model`

Example:

```yaml
render:
  model: mypack:item/ruby
```

On Minecraft 1.21.4+, VoxelCore can use the modern namespaced item-model API.

On older versions, the model string itself does not replace the need for the resource-pack mapping appropriate to that Minecraft version.

### Rendering does not define identity

Do not treat this:

```yaml
legacy_custom_model_data: 1001
```

as the permanent item ID.

The persistent gameplay identity is the ContentID:

```text
mypack:ruby
```

That separation allows rendering implementations to change across Minecraft versions without changing gameplay identity.

### Resource-pack generation

VoxelCore does **not yet generate resource packs** in the current MVP.

You are responsible for preparing the matching client resource pack and model mappings for the Minecraft version being tested.

The future pack compiler will build on this render metadata.

---

## Properties

`properties` is a structured extension area for data that is not currently represented by the core item fields.

Example:

```yaml
items:
  oak_chair:
    material: minecraft:paper
    type: furniture
    properties:
      furniture:
        seats:
          main:
            x: 0
            y: 0.5
            z: 0
        storage:
          slots: 0
```

Supported property values are:

- mappings
- lists
- strings
- numbers
- booleans
- null

Property mapping keys must be strings.

### Property inheritance

Mappings are recursively merged.

Parent:

```yaml
properties:
  furniture:
    seats:
      count: 1
    storage:
      slots: 0
```

Child:

```yaml
properties:
  furniture:
    storage:
      slots: 9
```

Compiled result conceptually becomes:

```yaml
properties:
  furniture:
    seats:
      count: 1
    storage:
      slots: 9
```

Lists are replaced rather than appended automatically.

Higher-level gameplay systems consuming these properties are still being built, so adding a property does not automatically create furniture/block/vehicle behavior yet.

---

## Reloading content safely

VoxelCore content reloads are atomic.

The currently active registry is not cleared before parsing new files.

Instead:

```text
active revision 5
      ↓
load candidate revision 6
      ↓
parse + validate + compile completely
      ↓
      valid?
      /   \
    yes    no
     ↓      ↓
publish 6  discard candidate
            ↓
        revision 5 remains active
```

This protects a running server from losing all registered content because of one bad YAML edit.

### Successful reload

Run:

```text
/voxelcore admin content reload
```

or the alias:

```text
/voxelcore admin content rl
```

Success looks similar to:

```text
VoxelCore content reloaded: revision 2, 42 items
```

### Failed reload

If a definition is malformed, has a missing parent, creates an inheritance cycle, duplicates an existing ContentID, or otherwise fails compilation, the command reports failure and the old revision remains active.

Example conceptually:

```text
VoxelCore content reload failed; revision 2 remains active: ...
```

### Startup is stricter than reload

If invalid content exists during plugin startup, there is no previous known-good snapshot to preserve.

VoxelCore therefore disables itself instead of publishing a partial registry.

---

## Commands

Primary command:

```text
/voxelcore
```

Alias:

```text
/vc
```

The current administration surface is intentionally useful for testing and diagnosing authored content.

### Content commands

#### View active revision

```text
/voxelcore admin content info
```

Example output:

```text
VoxelCore content revision 3: 42 items
```

#### Reload content

```text
/voxelcore admin content reload
```

Alias for the final subcommand:

```text
/voxelcore admin content rl
```

---

### Item commands

#### List registered items

```text
/voxelcore admin item list
```

Returns the sorted set of currently compiled ContentIDs.

`items` is also accepted as an alias for the `item` parent command.

#### Inspect a definition

```text
/voxelcore admin item info <content-id>
```

Example:

```text
/voxelcore admin item info furniture:oak_chair
```

The output currently includes:

- ContentID
- item type
- material
- display name
- bound state
- immediate parent

#### Give an item

```text
/voxelcore admin item give <content-id> [amount] [player]
```

Examples:

```text
/voxelcore admin item give furniture:oak_chair
/voxelcore admin item give furniture:oak_chair 16
/voxelcore admin item give furniture:oak_chair 1 Dan
```

Rules:

- amount defaults to `1`
- amount must currently be between `1` and `64`
- if run by a player with no target argument, the sender is the target
- console must provide a valid online player name

#### Identify the held item

Player-only command:

```text
/voxelcore admin item identify
```

Alias:

```text
/voxelcore admin item id
```

If the held stack carries VoxelCore identity metadata, its ContentID is returned.

Example:

```text
VoxelCore item: furniture:oak_chair
```

#### Verify create → identify round trip

```text
/voxelcore admin item verify <content-id>
```

Alias:

```text
/voxelcore admin item test <content-id>
```

This command creates an item in memory through the active version adapter and immediately asks VoxelCore to identify it again.

Success resembles:

```text
VOXELCORE_ITEM_VERIFY_OK id=furniture:oak_chair material=PAPER
```

This command is console-safe and is used by the automated MVP smoke tests.

---

## Permissions

All current permissions default to server operators.

| Permission | Purpose |
|---|---|
| `voxelcore.use` | Access the root `/voxelcore` command. |
| `voxelcore.admin` | Access the admin command tree. |
| `voxelcore.admin.reload` | Legacy/general admin reload command permission. |
| `voxelcore.admin.content` | Access content administration commands. |
| `voxelcore.admin.content.info` | View active content revision/item count. |
| `voxelcore.admin.content.reload` | Reload content packs. |
| `voxelcore.admin.item` | Access item administration commands. |
| `voxelcore.admin.item.list` | List registered items. |
| `voxelcore.admin.item.info` | Inspect compiled item definitions. |
| `voxelcore.admin.item.give` | Give VoxelCore items to players. |
| `voxelcore.admin.item.identify` | Identify a held VoxelCore item. |
| `voxelcore.admin.item.verify` | Run an in-memory item identity round-trip test. |

If a permissions plugin is used, grant only the administrative capabilities required for the relevant staff roles.

---

## Complete example pack

The repository includes an MVP fixture under:

```text
mvp-content/voxeltest/
```

A similar server-side pack can be created as follows.

### Directory layout

```text
plugins/VoxelCore/content/voxeltest/
├── pack.yml
└── definitions/
    └── items.yml
```

### `pack.yml`

```yaml
schema: 1
namespace: voxeltest
```

### `definitions/items.yml`

```yaml
items:
  base:
    material: minecraft:paper
    display_name: Base MVP Item
    bound: true
    lore:
      - Loaded from the VoxelCore MVP fixture

  red_item:
    extends: base
    display_name: Red Test Item
    render:
      legacy_custom_model_data: 1001
      model: voxeltest:item/red_item

  child_item:
    extends: red_item
    display_name: Inherited Test Item
    bound: false
```

This demonstrates:

- pack namespaces
- local ContentIDs
- inheritance
- inherited material
- display-name override
- inherited lore
- explicit `false` overriding inherited `true`
- legacy Custom Model Data
- modern namespaced item model metadata

After placing the pack on a running server:

```text
/voxelcore admin content reload
/voxelcore admin content info
/voxelcore admin item list
/voxelcore admin item verify voxeltest:red_item
/voxelcore admin item give voxeltest:child_item
```

---

## Troubleshooting

### VoxelCore starts with zero items

Run:

```text
/voxelcore admin content info
```

If the count is zero, confirm the directory shape is:

```text
plugins/VoxelCore/content/<pack-name>/pack.yml
plugins/VoxelCore/content/<pack-name>/definitions/*.yml
```

Do not place `pack.yml` directly in `content/`.

### Missing content pack manifest

Every immediate content-pack directory requires:

```text
pack.yml
```

For example:

```text
plugins/VoxelCore/content/furniture/pack.yml
```

### Unsupported content pack schema

The only currently supported manifest schema is:

```yaml
schema: 1
```

### Unsupported key

VoxelCore rejects unknown manifest, root, item, and render keys.

For example this typo is invalid:

```yaml
displayname: Ruby
```

Use:

```yaml
display_name: Ruby
```

This strictness is intended to catch authoring mistakes early.

### Duplicate ContentID

Two definitions across the loaded content set cannot compile to the same ContentID.

If both produce:

```text
mypack:ruby
```

reload fails instead of allowing one file to silently override the other.

Pack priority/explicit override semantics are not implemented yet.

### Missing parent

Check the `extends` value.

Inside the same namespace:

```yaml
extends: base
```

Across namespaces:

```yaml
extends: shared:base
```

The parent must exist in the loaded definition set.

### Circular inheritance

Review the dependency chain reported by VoxelCore and ensure an item does not eventually extend itself.

### Material does not exist

Material availability is Minecraft-version-dependent.

A material authored for a modern server may not exist on an older server, and 1.13 is especially important because of Minecraft's material flattening changes.

Use a material supported by the target server version.

### Custom model is not visible

The current VoxelCore MVP stores/applies supported model metadata, but it does not generate or distribute the client resource pack.

Confirm that:

- the correct resource pack is installed
- the pack format matches the client/server version
- legacy Custom Model Data mappings exist where required
- modern 1.21.4+ item-model definitions exist where required
- the YAML render values match the resource-pack identifiers

### Reload failed but old items still work

That is expected behavior.

VoxelCore compiles candidate content separately and only publishes it after complete validation. A failed candidate revision is discarded and the old snapshot remains live.

### Old Spigot / SnakeYAML conflicts

VoxelCore shades and relocates its YAML parser into a private package in the final distribution jars. This is required because older Spigot versions bundle incompatible SnakeYAML releases.

Use the final version-family distribution jar rather than manually assembling `voxelcore-common` and `voxelcore-plugin` jars into the server.

---

## Development and testing

### Full Maven build

```bash
mvn clean package
```

This builds/tests the multi-module project and packages every configured distribution.

### Build one version family

```bash
mvn -pl voxelcore-v1_21_4 -am clean package
```

Replace the module with one of:

```text
voxelcore-v1_12
voxelcore-v1_14
voxelcore-v1_19_4
voxelcore-v1_20_5
voxelcore-v1_21_4
```

### Automated real-server MVP smoke tests

The repository includes a GitHub Actions workflow that boots real Minecraft servers for:

```text
1.12.2
1.21.4
```

The smoke harness validates:

1. matching VoxelCore distribution builds
2. real Minecraft server boots
3. MVP pack is installed before startup
4. VoxelCore reaches `VOXELCORE_READY`
5. three fixture items compile
6. an item can be created and identified back to the same ContentID
7. intentionally broken YAML causes reload failure
8. the previous revision remains active after that failure
9. the old item remains usable
10. restored YAML publishes the next revision
11. an inherited item verifies correctly after successful reload

This provides end-to-end coverage of the current content/item MVP rather than only checking that the Java project compiles.

---

## Current limitations

The following are intentionally not documented as supported authoring features yet because they are not implemented in the current runtime:

- automatic resource-pack generation
- automatic client resource-pack distribution
- dedicated 1.13 material translation
- pack priority and override declarations
- pack dependency metadata
- abstract item definitions
- automatic finite variant expansion
- multiple inheritance
- append/remove list merge operators
- blocks as placed custom gameplay objects
- furniture runtime behavior
- vehicles
- GUI runtime definitions
- crops
- legacy HavenCore configuration import
- automatic migration of old custom item identities/model allocations
- completed Bedrock/Geyser/Floodgate frontend behavior

These systems should build on the same compiled content registry and version-adapter architecture rather than bypassing it.

---

## Architecture principles

A few rules are important when extending VoxelCore:

1. **ContentID is gameplay identity.** Do not use Custom Model Data as the primary persistent identity.
2. **Rendering metadata is version-specific presentation state.** It may change without changing the content ID.
3. **Compile configuration once.** Gameplay hot paths should read compiled definitions rather than scan YAML files.
4. **Do not mutate shared template stacks.** Create fresh item stacks for runtime use.
5. **Reload atomically.** Never clear the active registry before a replacement revision has fully parsed and compiled.
6. **Keep version-specific Bukkit/NMS behavior behind platform adapters.** Shared content definitions should remain portable across supported server families.
7. **Reject invalid authoring early.** Missing parents, cycles, duplicate IDs, unsupported keys, and malformed types should fail compilation rather than degrade silently.

These principles are what allow the same authored content to work across legacy and modern Minecraft implementations while keeping future systems such as blocks, furniture, vehicles, resource-pack compilation, and Bedrock presentation layered on top of one authoritative content model.
