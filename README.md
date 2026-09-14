# VoxelCore

VoxelCore is the version-aware content foundation for Voxel Horizons. The current MVP covers the complete custom-item pipeline: deterministic YAML pack discovery, inheritance compilation, immutable runtime snapshots, stable content identity, version-specific `ItemStack` creation, stable render allocations, deterministic Java resource-pack compilation, and safe atomic reloads.

Higher-level gameplay systems such as placed blocks, furniture, vehicles, crops, GUIs, Bedrock/Geyser presentation, and legacy HavenCore migration are deliberately outside this MVP.

## Current implementation status

The repository has completed the roadmap's foundation, content/item, and minimal Java pack milestones (phases 0, 1, and 2a):

- multi-module Maven build with per-version shaded distributions
- schema-validated content packs and deterministic inheritance
- immutable item registry and atomic reload rollback
- persistent ContentID storage and version-aware item creation
- stable numeric and structured render allocations
- deterministic Java resource-pack validation and compilation
- exact real-server smoke coverage from Minecraft 1.12.2 through Minecraft 26.2
- snapshot JAR publication from `main`

Bedrock items/mappings, action and menu sessions, richer item metadata and behavior, placed blocks, furniture, crops, skills, NPCs, vehicles, and network persistence have not been implemented yet.

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

## Runtime version families

Install exactly one distribution jar matching the server family.

| Minecraft | Module | Runtime path |
|---|---|---|
| 1.12.x–1.13.x | `voxelcore-v1_12` | legacy NBT identity, durability rendering |
| 1.14–1.19.3 | `voxelcore-v1_14` | PDC identity, numeric Custom Model Data |
| 1.19.4–1.20.4 | `voxelcore-v1_19_4` | PDC/CMD plus display-entity-era capability boundary |
| 1.20.5–1.21.3 | `voxelcore-v1_20_5` | data-component-era family with scalar CMD compatibility |
| 1.21.4 | `voxelcore-v1_21_4` | item-model component and structured Custom Model Data |
| 26.2 | `voxelcore-v26_2` | Paper 26.2 item-model component and structured Custom Model Data |

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
    material: minecraft:paper
    bound: false
    lore:
      - Shared values for inherited gemstone definitions

  ruby:
    extends: gem_base
    display_name: Ruby
    bound: true
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
- `render`
- `properties`

Unknown keys and malformed values are rejected rather than ignored.

### Bound and list visibility

`bound` is inherited like the other scalar fields. In the current implementation, `/voxelcore admin item list` shows only definitions whose resolved value is `bound: true`. This allows `bound: false` definitions to act as hidden inheritance bases while remaining available to the compiler and registry.

This flag does **not yet enforce full soulbound inventory, drop, death, container, trade, or cross-server transfer rules**. Those mechanics remain a later item-behavior milestone.

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

/voxelcore admin pack info
/voxelcore admin pack validate [target]
/voxelcore admin pack build [target]
```

When `[target]` is omitted, VoxelCore uses the current server version only when an exact validated pack profile exists. Otherwise specify a target explicitly.

`item list` reports only resolved `bound: true` definitions. Hidden `bound: false` definitions can still be inspected directly with `item info` and used as inheritance parents.

## Safe reloads

Reload builds the candidate revision in isolation:

1. discover and parse packs
2. resolve inheritance
3. compile immutable definitions
4. reconcile stable render allocations
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
- unit tests for parsing, inheritance, immutable snapshots, allocation persistence/tombstones, structured indices, modern rule generation, and reload rollback
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
