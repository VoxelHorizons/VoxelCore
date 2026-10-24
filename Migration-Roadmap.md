Warning: truncated output (original token count: 31322)
Total output lines: 662

# VoxelCore migration and optimization roadmap

> **Implementation checkpoint (13 October 2026):** VoxelCore's content/pack, Java actions and custom-block MVP are merged; VoxelFurniture has its placement, display, seating, storage and animation MVP; VoxelVehicles has modern land driving, persistence, storage and chest accessories. The September phase-3a recommendation is superseded. **Next: cross-plugin release hardening, persistent-state/restart validation, vehicle adapter/physics gaps and audited legacy imports**, followed by frontend expansion. See the evidence-linked checkpoint below. A merged PR is *not* proof of in-game acceptance for every server/client combination.

Prepared for Voxel Horizons · 12 September 2026 · Living implementation plan

## October 2026 cross-plugin implementation checkpoint

**Scope and interpretation.** This is a new check against the current GitHub `main` branches and merged PR histories of [VoxelCore](https://github.com/VoxelHorizons/VoxelCore), [VoxelFurniture](https://github.com/VoxelHorizons/VoxelFurniture), [VoxelVehicles](https://github.com/VoxelHorizons/VoxelVehicles) and the closely coupled [VoxelFurnitureShop](https://github.com/VoxelHorizons/VoxelFurnitureShop), as of **13 October 2026**. The original 12 September static audit, architectural rationale and pinned source inventory in Sections 1–12 and the appendices are retained as *historical baseline*, not descriptions of the current implementations. Feature evidence below means source/merged PR evidence; CI passes are reported separately from human/client or production load testing. Do not infer Bedrock or full legacy vehicle support from VoxelCore's seven-version smoke matrix.

| Area | Verified repository progress | Still missing or not sufficiently verified |
| --- | --- | --- |
| VoxelCore platform/content | Multi-version Maven distributions, immutable compiled item content/inheritance, atomic reload, PDC identity, stable render allocations, Paper capability selection and startup smoke tests. [Foundation PRs](https://github.com/VoxelHorizons/VoxelCore/pulls?q=is%3Apr+is%3Amerged) | Legacy HavenCore ID/data importer, comprehensive addon integration testing and explicit per-feature server/version claims |
| VoxelCore packs/UI | Java glyphs/font and spacing, authored assets, generated blocks/models, resource-pack size optimization, custom-item/GUI browsing and configurable per-page titles, inventory/chest text, tooltip overlays, ShopGUIPlus hooks, render tint and modern rules; recent entity/chest atlas fixes ([#68](https://github.com/VoxelHorizons/VoxelCore/pull/68), [#70](https://github.com/VoxelHorizons/VoxelCore/pull/70), [#71](https://github.com/VoxelHorizons/VoxelCore/pull/71)) | General cross-plugin menu-session/action framework, complete authored-asset dependency auditing, Bedrock pack/conversion and Geyser mappings, measured atlas/client validation |
| VoxelCore actions/blocks | Reusable item and placed-block actions, carrier-backed blocks, mine/tool/drop rules, hardness support on supported versions, creative pick-block ([#31](https://github.com/VoxelHorizons/VoxelCore/pull/31), [#43](https://github.com/VoxelHorizons/VoxelCore/pull/43), [#44](https://github.com/VoxelHorizons/VoxelCore/pull/44)) | Full HavenCore item behavior/binding/upgrade parity, full physics/protection matrix and non-immune blast-resistance simulation |
| VoxelCore currency | Independently configured VaultUnlocked Tokens path and currency-aware commands merged in [#67](https://github.com/VoxelHorizons/VoxelCore/pull/67) | Do **not** claim the custom SQLite multi-currency ledger: [#66](https://github.com/VoxelHorizons/VoxelCore/pull/66) was closed **without merge**. Test compatibility/semantics with the currently selected economy provider and shop integrations |
| VoxelFurniture | Version-aware display/armor-stand visual modes, placement/rotation/collision, seats, inventory, neighbour-aware chair/table models, dye, multipart render/text billboards, bob/spin, stateful and neighbour-triggered animations, child interaction commands, pick-block, reload/orphan repair and definition-cache optimization ([#27](https://github.com/VoxelHorizons/VoxelFurniture/pull/27), [#34](https://github.com/VoxelHorizons/VoxelFurniture/pull/34), [#37](https://github.com/VoxelHorizons/VoxelFurniture/pull/37), [#39](https://github.com/VoxelHorizons/VoxelFurniture/pull/39), [#42](https://github.com/VoxelHorizons/VoxelFurniture/pull/42), [#43](https://github.com/VoxelHorizons/VoxelFurniture/pull/43)) | Bulk/chunk lifecycle and crash-recovery benchmarks, ownership/permissions across all interaction paths, migration of real HavenFurniture data and client/version-specific visual acceptance |
| VoxelVehicles | 26.2-focused land vehicle implementation with input/simulation, terrain/airborne motion, stable driver seat/render offsets, particles, persistent dyed item state, owner-aware pickup, UI inventory/cargo, installed chest accessories and model swaps, restored preview/metadata, optimized teleports and chunk indexes ([#8](https://github.com/VoxelHorizons/VoxelVehicles/pull/8), [#11](https://github.com/VoxelHorizons/VoxelVehicles/pull/11), [#12](https://github.com/VoxelHorizons/VoxelVehicles/pull/12), [#17](https://github.com/VoxelHorizons/VoxelVehicles/pull/17)) | **Cannot claim drivable 1.12–1.19.3**: legacy input/render adapters remain open in [issue #2](https://github.com/VoxelHorizons/VoxelVehicles/issues/2). HavenVehicles import, multi-seat/trailers ([#3](https://github.com/VoxelHorizons/VoxelVehicles/issues/3)), precise collision and workload benchmark ([#4](https://github.com/VoxelHorizons/VoxelVehicles/issues/4)). [#18](https://github.com/VoxelHorizons/VoxelVehicles/pull/18) was closed, superseded by merged #17 |
| VoxelFurnitureShop (adjacent addon) | Dynamic regions/variants, shared door snapshots, rotation/requirements, animated persistent fixtures, protections/tooltips, full supported block-entity capture (decorated pots and skull profiles) and restart-safe editing recovery ([#10](https://github.com/VoxelHorizons/VoxelFurnitureShop/pull/10), [#11](https://github.com/VoxelHorizons/VoxelFurnitureShop/pull/11)) | Replay/backups and multi-version restore verification, unsupported native tile NBT, failure atomicity, larger showroom load benchmarks and original HavenFurnitureShop template imports |

**Verification boundary.** VoxelCore's CI smoke matrix has covered 1.12.2, 1.13.2, 1.14.4, 1.19.4, 1.20.5, 1.21.4 and 26.2 (see [smoke workflow](https://github.com/VoxelHorizons/VoxelCore/actions/workflows)). The latest atlas-fix PR's distribution builds and all seven MVP smokes passed. That proves those exercised **VoxelCore** startup/basic flows, not the complete three-plugin runtime on seven versions. VoxelFurniture's legacy renderer is implemented, whereas VoxelVehicles' README explicitly says older versions lack a drive input bridge. The VoxelCore README previously retained MVP-only introductory/status wording that incorrectly characterized existing blocks and actions as future work. **This documentation update also aligns its overview, next milestone and outstanding-work list**; keep them synchronized in future releases.

**Repository coordination.** Before the documentation PR for this checkpoint, the three central repositories had no open PRs. VoxelVehicles issues [#2](https://github.com/VoxelHorizons/VoxelVehicles/issues/2), [#3](https://github.com/VoxelHorizons/VoxelVehicles/issues/3) and [#4](https://github.com/VoxelHorizons/VoxelVehicles/issues/4) remain open and provide concrete acceptance criteria. Preserve those as tracking issues rather than treating the implementation roadmap as a substitute for tests.

## 1. Recommendation and scope

Build VoxelCore around a compiled content registry, a small gameplay API, and explicit platform adapters. Preserve the configurable content and inheritance ideas. Replace construction-time registration, mutable shared ItemStacks, whole-world configuration scans, and direct rendering dependencies before migrating gameplay.

Maven improves repeatable builds and dependency management; it does not itself improve runtime performance. The largest likely gains here come from indexed lookups, bounded scheduling, reduced synchronous persistence, and fewer unnecessary entity updates. These are static-analysis conclusions, not measured benchmark results.

This review covers the default-branch trees of all eleven requested repositories, including source, configuration, build files and addon relationships. The appendix pins the exact commits and inventories the Java files. Detailed inspection concentrated on loading, shared state, gameplay listeners, scheduling, persistence, GUI handling, version-specific code and migration boundaries. This is a repository-wide architectural/static review, not a claim that every path has been executed or that every defect has been found. No repositories were modified, built, deployed, or tested on a live server. Binary dependencies, historical branches, deployed data and external asset packs were not audited.

Important source-versus-description differences:

- Current HavenFurniture spawns armor stands with helmet models, seats and configured blocks. The item-frame path is visible in HavenFarms. Preserve the proposed item-frame compatibility option only if there is content that needs it.
- Current HavenVehicles is already Maven-based and uses ItemsAdder CustomStack, ProtocolLib and packet wrappers. It is not simply an untouched HavenCore addon.
- HavenNetwork's current main branch does not contain the described item exchange. Its entrypoint registers proxy server commands. Tooltip messaging and a resource-pack reload HTTP handler exist in source, but the entrypoint does not register/start them. Treat exchange behavior as a requirement to reconstruct, not an implementation already reviewed.
- Several declared feature names are placeholders rather than complete mechanics: examples include some upgrades, skill effects, shop spawning and bomb-specific game behavior.
- Models, textures and substantial deployed content configurations are not present in these source trees. A real pack conversion needs representative original asset bundles later.

## 2. What should be retained

| Existing idea | Why it is useful | New form |
| --- | --- | --- |
| Persistent item identification | Gameplay can identify content independently of appearance | Namespaced content ID and state schema in PDC, with legacy aliases |
| Configurable inheritance and variants | Avoids repeating similar item definitions | Validated dependency graph compiled once per content revision |
| Map-based registries | Appropriate for fast identity lookup | Immutable maps, typed indexes and collision checks |
| Addon separation | Features can evolve and be optional | Addons consume a stable API; core does not import their implementations |
| Cancellable custom events | Good integration points | Explicit actions/events with documented ownership and cancellation semantics |
| Furniture components | Seats, storage, light and animation are reusable concepts | Separate definition, placement state, rendering and interaction |
| Crop stages and configurable skills | Good data-driven gameplay | Validated rules, indexed lookups and bounded jobs |
| Bedrock-aware text | Recognizes presentation differences | Per-session capabilities and separate UI/rendering adapters |
| CI startup automation | Useful foundation for compatibility checks | Explicit successful-initialization and behavior assertions |

Keep simple maps and immutable objects initially. A large entity-component framework, a distributed service per mechanic, or database storage for every static definition would add complexity before there is evidence it is needed.

## 3. Findings that should shape the rebuild

The source references for these findings are in the evidence appendix. “Confirmed” means visible in source, not reproduced on a running server.

| Priority | Finding | Consequence and migration treatment |
| --- | --- | --- |
| Critical | ItemManager retries loadLater items until none remain. Each retry constructs a new CustomItem, resetting its retry fields. | A missing parent or cycle can keep startup/reload looping. Resolve a graph once; report the exact cycle or missing reference. |
| High | CustomItem.getStack returns the shared stack; getItem(id, quantity) changes that stack's amount. Skull and crop paths also modify shared templates. | Player-specific metadata and quantity can contaminate future uses. Return fresh stacks and keep definition objects immutable. |
| High | hasItem scans every custom item and reads stack metadata despite a keyed registry. Furniture and crop location resolution scan persisted records. | Replace with canonical ID lookup and world/chunk/block-position indexes. |
| High | Inheritance copies the parent's ItemStack but then resets fields such as lore and bound from the child config/defaults. | This is partial stack inheritance, not a consistent deep configuration merge. Specify merge behavior explicitly and characterize old outcomes before conversion. |
| High | Constructors register themselves and subclass instances; loading clears live registries before successful validation. | Partially initialized objects and failed reloads can leak into runtime. Compile in isolation; publish a complete snapshot only after success. |
| High | GUI/block/item registries have inconsistent coverage; general getItem/isItem omit blocks. | Use one identity registry with typed queries rather than three competing authorities. |
| High | CustomGUIItem uses this.getStack for ordinary visible entries instead of the referenced entry item. Slot zero means append. Close cleanup removes activeGUIs but leaves customSlotOpeners. | Fix wrong icons, slot semantics and retained inventory references. Model menus as sessions with deterministic cleanup. |
| High | Block placement saves a complete YAML file immediately; skill XP changes save player data; regeneration saves block data every run. Farm XP executes SQL on the caller's path. | Move persistence out of frequent gameplay paths using immutable snapshots, bounded queues, batch writes and explicit durability policy. |
| High | FurnitureManager.isBlock parses/scans all furniture and occupied locations. Its storage initialization is inside the config loop. | Index placement blocks once; initialize world stores once, independently of definition file count. |
| High | Furniture variant persistence writes light location to the location field. Seats are spawned before all placement blocks are validated; failure removes only the primary stand. | Correct the schema and use preflight/commit/rollback placement to avoid wrong anchors and orphan seats. |
| High | Vehicle input handling reads entities, world blocks and PDC before the later scheduled movement task. Reflection looks for an obfuscated method named b. | Scheduling only the final movement does not establish thread safety. Packet callbacks should capture input; a server-owned simulation consumes it. Isolate tested internals behind adapters. |
| High | Vehicle removal saves world storage before deleting its record; animation reset paths read furniture keys for vehicles. | Stale records and failed animation restoration are source-level risks. Use one placement state schema and ordered persistence. |
| High | CropGrowthTask calls location.getChunk before its loaded check and returns from the entire task on an unloaded/missing crop case. Neighbor coordinates are duplicated and crop checks inspect the farmland position. | Use a chunk-loaded test before resolving blocks, skip individual records, and test a table-driven neighborhood algorithm. Do not reproduce the apparent old growth mistakes. |
| High | RegenMines materializes every region block into a list and stores every original Material; reset deadlines are scanned every second. | Start with sparse modified-block records and a due-time queue. Keep full BlockData where restoration requires it. A full reset baseline is a separate, deliberate feature. |
| High | Playtime SQL uses placeholders for column identifiers and does not match the created TIME schema; UUID storage mixes 16-byte and string assumptions. | Replace with a normalized player/server playtime table and one UUID encoding. A JDBC value parameter cannot stand for a column name. |
| Medium | HavenChat declares void HavenChat(), so its initialization is not a constructor; chat listener registration is commented out. | Treat chat as unfinished optional work. Do not list it as functioning solely because the classes exist. |
| Medium | Tool/upgrade enums exceed implemented effects; skill REPLACE is empty and BREAK is not handled in the reviewed break switch. | Record each mechanic as implemented, partial or new; migrate proven behavior intentionally. |
| Medium | NPC relationship state uses YAML while SQLite classes contain incompatible leftover token/kill fields. FurnitureTemplate.spawn is empty. PassTheBomb largely inherits Tag behavior. | Keep the gameplay concepts; reconstruct incomplete parts with explicit acceptance criteria. |
| Medium | VoxelCore config version changes replace the config and overwrite a single .old backup. | Use schema migrations that preserve custom settings and produce a migration report. |
| High | VoxelCore CI ignores startup exit failure and only checks an Enabling line. CIMode dispatches test commands before registration. | Add a readiness marker after initialization plus meaningful behavior checks and explicit failure detection. Preserve automated shutdown. |

Additional cleanup belongs inside the relevant migration: missing null validation, catch-and-ignore NullPointerException, regular-expression split("|") instead of literal delimiter splitting, switch fall-through in book metadata, direct CraftBukkit imports in inventory serialization, repeated helper copies and strong Player/Entity references without complete lifecycle cleanup. Database connection-error logging includes the password in its constructed message; remove that from the replacement implementation.

## 4. Proposed architecture

Use Maven modules for independently versioned dependencies and API boundaries; use packages where separation does not need a separate artifact.

| Boundary | Responsibilities | Must not own |
| --- | --- | --- |
| voxelcore-api | Content IDs, service contracts, actions/events, state DTOs | Concrete addon classes or obfuscated server classes |
| voxelcore-content | YAML schema, inheritance, validation, immutable registry, aliases | Live Bukkit inventories/entities |
| voxelcore-pack | Asset graph, target compilers, allocation manifest, output validation | Live world mutation |
| voxelcore-runtime | Item factories, placement indexes, sessions, task budgets, persistence orchestration | Geyser internals throughout gameplay code |
| platform adapters | Item serialization/components, rendering, scheduling and input for supported server bands | Content-specific game rules |
| optional integrations | Geyser extension, Floodgate, forms, Citizens, permissions, economy, plots, WorldGuard | Mandatory startup dependencies for unrelated features |
| gameplay addons | Furniture, vehicles, crops, skills, NPC interactions, mines, games | Direct access to other addons' mutable registries |
| network module | Shared profile/inventory ownership, transfer state, proxy coordination | Independent unsynchronized inventory copies |

Dependency direction is addons → API/services → adapters. Shared actions such as open_menu, teleport, purchase and grant_upgrade belong behind service contracts; the core's listener should not switch over every addon-specific command.

A content definition, an item instance and a world placement are different records:

- **Definition:** ID, base material policy, render key, default components, behavior configuration and revision. Shared and immutable.
- **Item instance:** ID plus quantity, durability, upgrades and any unique state. Stored in inventory and serialized independently of resource-pack numbers.
- **Placement:** stable placement UUID, definition ID/revision, world UUID, anchor/transform, owner, storage reference, animation state and collision description.
- **Render handles:** temporary entity/packet IDs produced by the selected adapter. Replaceable without changing the placement's identity.

## 5. Configuration inheritance and item optimization

Recommended pipeline:

1. Discover content files in deterministic order with explicit pack priorities.
2. Parse bounded YAML into plain intermediate definitions. Reject duplicate keys/IDs and unsafe paths.
3. Normalize namespaced IDs once using locale-independent rules.
4. Resolve inheritance with DFS cycle detection or topological ordering. Include filenames, config paths and dependency chains in errors.
5. Merge definitions, expand finite variants, then validate all cross-references: item materials, models, upgrades, menus, drops, seats and crop stages.
6. Compile typed definitions and reverse indexes. Keep authoring YAML out of hot gameplay paths.
7. Prepare compatible runtime templates and asset outputs for a content revision.
8. Publish the validated revision atomically within the runtime. A failed build/reload retains the prior revision.

Graph ordering is O(V + E), plus the real cost of merging/expanding definition data. It is not O(V + E) for arbitrarily large inherited payloads. An initial rebuild of the whole registry may be entirely adequate. Incremental invalidation can follow measured need: a changed parent invalidates its descendants and dependent assets.

Define merge rules before implementing them:

| Value | Proposed rule |
| --- | --- |
| Scalar | Child replaces parent |
| Mapping | Deep merge |
| List | Replace by default; explicit append/remove operations when supported |
| Omitted value | Inherit |
| Explicit removal | Dedicated removal operation with schema validation |
| Multiple parents | Initially avoid; if added, define ordering and ambiguity handling |
| File priority override | Explicit declared override, not whichever file loads last |
| Variant | Named definition with stable derived ID; no unbounded Cartesian expansion |

Illustrative future schema, not code currently supported:

```yaml
schema: 1
namespace: voxelhorizons
items:
  chair_base:
    abstract: true
    material: minecraft:paper
    behavior: furniture
    furniture:
      seats:
        main: {x: 0, y: 0.5, z: 0}
  oak_chair:
    extends: voxelhorizons:chair_base
    display_name: Oak Chair
    render:
      model: voxelhorizons:furniture/oak_chair
    furniture:
      storage: {slots: 0}
  birch_chair:
    extends: voxelhorizons:oak_chair
    display_name: Birch Chair
    render:
      model: voxelhorizons:furniture/birch_chair
```

Keep minecraft:paper as the material and extends as the parent reference. Overloading material to mean either a material or a custom parent makes validation and migration harder.

At runtime, resolve PDC ID → definition directly. Cache reusable static stack templates by definition revision and server serialization profile, then clone before applying instance state. Do not create one unique UUID for every ordinary stackable item. Use UUIDs only where individual identity matters. Avoid caches indexed by arbitrary player input; personalized skulls and rendered text need bounded lifetimes.

Read legacy havenitems/havenitems-upgrades and addon keys through a migration adapter. Preserve old IDs and custom-model allocations in a manifest. Unknown content should become a recoverable missing-content record with original data retained, rather than silently turning into a vanilla item or disappearing.

## 6. Version compatibility policy

“Multiple versions” has three independent dimensions: backend server/API, connected Java client/protocol, and resource-pack format. Bedrock adds its own protocol, pack schema and Geyser build. Supporting login through a protocol translator does not guarantee correct custom visuals or gameplay.

Proposed capability bands—not a promise of complete support for every release in each band:

| Band | Item representation | World rendering | Position in plan |
| --- | --- | --- | --- |
| 1.16.5–1.19.3 | Numeric custom model data; legacy item representation | Armor stands/item frames and separate collision | Optional legacy target, reflecting current CI and old content |
| 1.19.4–1.20.4 | Numeric custom model data | Display entities available | Important display/legacy-item boundary |
| 1.20.5–1.21.3 | Component-based item storage; scalar custom model data | Display entities | Separate serialization adapter; do not combine with pre-component storage |
| 1.21.4–1.21.11 | New item model definitions and structured custom model data | Display entities | Modern item-model target family |
| 26.x | Revalidate exact current item, pack, API and protocol schemas | Modern display adapter, version-tested | Current-release target; not automatically covered by a 1.21 adapter |
| Bedrock via pinned Geyser | Registered mapped Bedrock items and Bedrock packs | Explicit supported proxy/custom entity representation | Separate frontend on the same authoritative gameplay |

Verified historical boundaries: displays arrived in 1.19.4; item data components in 1.20.5; the full new item model definition system and structured custom_model_data in 1.21.4. The latter contains floats, flags, strings and colors. The compiler should allocate their indices centrally rather than allowing addons to compete for the same list position. Sources: [Minecraft 1.19.4](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-19-4), [1.20.5](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-5), [1.21.4](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-4).

For modern packs, prefer a namespaced item model for identity and structured model data for visual state. For legacy packs, generate numeric allocations from the same render manifest. Neither representation is the authoritative gameplay identity.

I recommend first proving one chosen modern Paper release plus Bedrock and one representative legacy target. Keep legacy backend support as a separate tested distribution if its JVM/API floor requires it. Current Paper documentation lists Java 25 for 26.1+, so VoxelCore's fixed Java 16 target and Java 21 “latest” CI runtime are not a sufficient current-version policy. Use Maven toolchains/release targeting, exact server builds and explicit plugin API metadata. Modern classes must not be loaded on legacy runtimes. [Paper requirements](https://docs.papermc.io/paper/getting-started/)

For mixed Java clients on a modern backend, compute a per-session capability profile and test the actual protocol translation path. A fallback item model alone cannot make an unsupported entity or newer gameplay mechanic exist on an old client. Any display replacement must suppress the original visual for that viewer and map interactions back to the canonical placement. Choose one packet integration boundary after a small compatibility experiment; do not stack ProtocolLib and another packet framework without a concrete need.

## 7. Resource-pack creator

Treat this as a deterministic compiler from shared authoring data to several outputs, not a ZIP utility and not a universal Java-to-Bedrock converter.

Implemented Java MVP layout:

- `plugins/VoxelCore/content/<pack>/pack.yml`: schema, namespace and dependencies.
- `content/**/*.yml`: recursively discovered item definitions; paths are organizational and IDs come from item keys.
- `assets/<namespace>/models` and `assets/<namespace>/textures`: authored Java item assets copied into target packs.
- UI font definitions use compact cropped-image metadata while final font composition remains compiler-owned.
- `build/resource-packs/<target>.zip`: deterministic generated output, separate from authored assets.

**Historical September MVP note:** Pack priorities, blocks, furniture, crops, vehicles, menus, Java fonts/sounds, and Bedrock-specific asset trees were then planned. **October update:** The compiler now supports custom block assets, authored font/language assets, generated modern item definitions, UI glyph atlases and model texture compatibility; VoxelFurniture/VoxelVehicles consume VoxelCore's model/content definitions rather than being separate pack compilers. Bedrock conversion/mappings, general recipe/crop compilation and an independent generic menu schema remain future work. See the checkpoint for implemented vs tested distinctions.

Compile configuration inheritance and resource-pack model inheritance as separate graphs. They are related but have different merge semantics. Resolve textures, model parents, animation frames, sounds, fonts, particle references and target-specific references transitively.

Outputs for each release:

| Output | Purpose |
| --- | --- |
| Java packs per supported format/profile | Legacy overrides or modern item definitions, blockstates, textures, sounds/fonts |
| Bedrock pack | Manifest, textures, geometry, attachables, client entities and supported animation definitions |
| Geyser mappings/extension registry data | Ties Java representation to Bedrock custom definitions |
| Stable allocation manifest | Content/render IDs, legacy numbers, occupied block states and schema revision |
| Compatibility report | Exact supported, approximated or unsupported features per target |
| Release manifest | Hashes, compiler/adapter versions, content revision and dependencies |

Optimization rules: cache conversion by content hash and target; reuse identical assets; retain referenced vanilla fallbacks; prune only proven unreachable assets; minify JSON; use lossless texture optimization by default; make resolution reduction opt-in; avoid texture atlasing until UV/animation/tint semantics are supported. Stable sorted ZIP entries and normalized metadata allow reproducible hashes. Never renumber existing items because a file was renamed or deleted; retain tombstones where needed.

Generate block-state mappings from an explicit state allocation table. Reserving note-block or similar states affects vanilla behavior, physics and other plugins. Validate capacity and collisions. “Unlimited configured content” cannot mean unlimited reserved block states, runtime memory, client registrations or entity counts.

Use a staged release: compile → validate → publish immutable pack URLs/files → load matching Geyser registrations at their supported lifecycle → activate compatible runtime content. A resource pack and a backend registry cannot be globally atomically switched by a local map swap. Track the revision accepted by each session and keep prior outputs available during rollout. Adding registered Bedrock item/entity types generally needs a planned Geyser restart and client reconnect; ordinary gameplay values can have a narrower reload path. Treat pack refusal/download failure explicitly for content-dependent play.

Bedrock needs both the pack and mappings. Geyser does not automatically convert Java packs. Its current item v2 format supports modern item_model mappings and legacy definitions; dynamic Bedrock properties may need finite registered variants. [Geyser custom items](https://geysermc.org/wiki/geyser/custom-items/)

Rainbow is a useful conversion reference and prototype tool, but it is currently a Fabric client mod in early development, not a drop-in server-side compiler library. Evaluate its output on representative assets before deciding whether any conversion logic can be reused. Arbitrary Java models, fonts, animated models and dynamic properties should have explicit Bedrock overrides or unsupported reports. [Rainbow](https://geysermc.org/wiki/other/rainbow/)

Delivery must occur where Geyser runs. If it runs on the proxy, a backend writing its own local packs folder is insufficient. Publish matching artifacts to the proxy/standalone deployment. Geyser supports Bedrock packs in its packs directory. [Pack delivery](https://geysermc.org/wiki/geyser/packs/)

## 8. Furniture, vehicles, mobs and moving structures

Use one authoritative placement and multiple render adapters. Separate visual transforms, interaction hitboxes, physical collision, riding seats, storage and real lighting. Display entities are excellent visuals, but have no native physical hitbox and do not supply pathfinding or vehicle simulation. Display brightness is not emitted world light. [Paper display entities](https://docs.papermc.io/paper/dev/display-entities/)

**October implementation note:** VoxelFurniture now owns furniture placement state with version-aware renderers, seats, storage, animation and persistent neighbour variants. VoxelVehicles independently owns modern land-vehicle state and simulation, including attached chest visuals/storage and optimized occupied-vehicle updates. These are functioning code paths, *not* new systems to write from scratch. The next work is validation of collisions, lifecycle and owner/cargo invariants; the explicit version-adapter, import and multi-seat gaps remain separate. See the checkpoint above and vehicle issues #2–#4.

Start with real server entities and supported APIs. Virtual per-viewer entities are an optimization/compatibility tool when measured or required, not an automatic first step. They introduce spawn/destroy lifecycle, chunk tracking, entity-ID mapping, metadata ordering and interaction validation.

For a castle gate, define closed, opening, open and closing states, a pivot and animation curve, ownership/permission rules, obstruction checks, collision update points, and a persisted stable state. Only animate while moving. Modern Java can interpolate visual transforms; legacy clients can receive stepped transforms or a static approximation. Bedrock needs a tested equivalent client-entity animation or fallback.

A walkable rotating drawbridge is harder than a decorative gate: a visual surface is not a collision surface. Prototype supported collision and player movement before promising arbitrary moving walkable geometry. Decide whether the first version supports only stable open/closed collision, stepped collision, or an actual moving platform system.

Current Geyser Entity API 2.11.0, introduced for 26.2, is experimental. It can register Bedrock custom entity definitions and substitute representations on spawn, but requires extension code and a Bedrock pack; JSON entity mappings are not currently supported. Pin its version and contain it in one adapter. This is a candidate path for furniture/vehicles, not proof of full transform, riding or hitbox parity. [Geyser entity API](https://geysermc.org/wiki/geyser/custom-entities/)

For vehicles, packet input becomes a small immutable input record. Validate the driving session, clamp values and expire stale input. A single bounded simulation scheduler updates active vehicles at a fixed tick rate; server code checks world collision and updates authoritative state. Do not base movement speed on how many packets a client sends. Sleep unoccupied vehicles and update rendering only when changed. Test driver/passenger offsets, entry/exit, mobile controls, reverse, braking, slopes, teleport and disconnect separately. Land vehicles precede trailers and flight.

A custom-looking mob also needs a real server-side AI/combat owner or a separate simulation; replacing its visual entity does not create those mechanics. The existing NPC addon is Citizens interaction/dialogue functionality, not evidence of a general custom mob engine.

## 9. Bedrock UI and interaction

The premise that Bedrock can only handle three inventory rows is too restrictive. Geyser's InventoryTranslator explicitly maps Java 9×1 through 9×3 to a single-chest translator and 9×4 through 9×6 to a double-chest translator. Arbitrary visual layouts and resource-pack font tricks still do not translate one-to-one. [Geyser source](https://github.com/GeyserMC/Geyser/blob/master/core/src/main/java/org/geysermc/geyser/translator/inventory/InventoryTranslator.java)

Define menu purpose and actions independently of slots:

| Interaction | Java presentation | Bedrock presentation |
| --- | --- | --- |
| NPC dialogue, server selection, settings | Inventory menu or supported native UI | Simple/modal/custom form |
| Furniture catalogue | Paged icon inventory | Paged form or translated inventory |
| Physical storage | Authoritative inventory | Translated supported container, with real item movement |
| Purchase/upgrade confirmation | Validated menu action | Validated form action |
| Skill tree | Graph-like icon layout | Categories, skill details and upgrade form |
| Custom glyph tooltip | Java font rendering | Plain text/form/actionbar fallback |

Both frontends call the same action service. Validate permissions, ownership, prices, balances and current state when executing the action; do not trust a stale form response. Bind responses to session/action IDs and reject duplicate purchases. Storage is not merely a list of buttons: preserve item transfer, cursor and overflow semantics.

CrossplatForms is a good optional frontend candidate. Its README supports forms, Java inventory menus and actions, but its listed Spigot range is old and includes proxy-specific caveats. Verify an exact build against the selected deployment before committing to it as a required dependency. A small direct Floodgate/Cumulus frontend is an alternative if the integration is unsuitable. The domain action API should accommodate either. [CrossplatForms](https://github.com/kejonaMC/CrossplatForms)

## 10. Persistence and network inventory

Use YAML for authoring. For runtime state, start with SQLite for a single server or a transactional relational database for the network. Redis can provide caching, presence and notifications; it is not a complete inventory consistency protocol.

Recommended scope is **shared inventory groups**. Survival servers can share one group, while creative, lobby and minigame servers remain isolated. Define separately whether armor, offhand, cursor, ender chest, XP, effects and health are shared. Clear cursor/container state safely before transfer; never silently discard it.

Use a single-writer session protocol:

1. A player acquires ownership of their inventory group with a monotonically increasing fencing token.
2. Server A freezes inventory-changing actions for transfer and captures a validated snapshot with a revision and transaction ID.
3. Persist the snapshot and transfer intent durably. Only then hand off through the proxy.
4. Server B acquires the next ownership token and loads the committed revision before enabling gameplay.
5. Stale A writes are rejected by token/revision checks at the durable store, not just by an expiring cache lock.
6. Retries and duplicate notifications reuse the transaction ID. Disconnects/timeouts leave a recoverable state.

If ownership is lost, the old server must stop gameplay/inventory mutation, not merely stop saving. A database token does not protect world drops, chest changes or economy side effects by itself. Test death, drops, purchases, containers and crash recovery during transfers, and define which effects need a durable operation record. A last-saved inventory snapshot alone cannot guarantee zero duplication or zero loss for arbitrary simultaneous world changes.

For initial delivery, consider an escrow/mailbox exchange before full shared live inventories. It is easier to bound and recover, and still meets cross-server item delivery. Full shared inventories can follow the same ID/serialization design.

Never make raw BukkitObjectOutputStream Base64 the long-term cross-version interchange format. Store versioned content IDs and supported instance state, plus a server-version-tagged lossless payload for vanilla/third-party data where necessary. Define a compatibility …11322 tokens truncated…CustomGUIItem.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/objects/item/CustomGUIItem.java) — CustomGUIItem, getCommand, loadItems, openInventory, mapNamespaced, hasHolderSlot, getCommandItem
- [src/com/randamonium/items/objects/item/CustomItem.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/objects/item/CustomItem.java) — CustomItem, loadExtraMeta, getStack, getItem, getGUI, getBlock, updateStack
- [src/com/randamonium/items/objects/item/CustomPrefixItem.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/objects/item/CustomPrefixItem.java) — CustomPrefixItem, addPermission, removePermission
- [src/com/randamonium/items/objects/item/CustomToolItem.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/objects/item/CustomToolItem.java) — CustomToolItem, getAllowedItems, getAllowedMaterials
- [src/com/randamonium/items/objects/item/CustomUpgradeItem.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/objects/item/CustomUpgradeItem.java) — CustomUpgradeItem, getAllowedItems, getAllowedMaterials
- [src/com/randamonium/items/objects/player/MinecraftServerPlayer.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/objects/player/MinecraftServerPlayer.java) — MinecraftServerPlayer, getPlayer, isBedrockPlayer, getFriends, addFriend, removeFriend, hasFriend, getIgnores, addIgnore, removeIgnore, hasIgnore, setChatChannel, getChatChannel
- [src/com/randamonium/items/objects/player/PlayerManager.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/objects/player/PlayerManager.java) — PlayerManager, reloadPlayers, getPlayer, hasPlayer, addPlayer
- [src/com/randamonium/items/objects/player/PlayerTooltip.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/objects/player/PlayerTooltip.java) — PlayerTooltip
- [src/com/randamonium/items/objects/types/ItemType.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/objects/types/ItemType.java) — fromString
- [src/com/randamonium/items/objects/types/ToolType.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/objects/types/ToolType.java) — Type/contract/field declarations; inspect linked source.
- [src/com/randamonium/items/objects/types/UpgradeType.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/objects/types/UpgradeType.java) — Type/contract/field declarations; inspect linked source.
- [src/com/randamonium/items/objects/world/Cuboid.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/objects/world/Cuboid.java) — Cuboid, blockList, chunksList, getCenter, getDistance, getDistanceSquared, getHeight, getPoint1, getPoint2, getRandomLocation, getTotalBlockSize, getXWidth, getZWidth, isInside
- [src/com/randamonium/items/objects/world/Region.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/objects/world/Region.java) — Region, getID, getName, getCuboid
- [src/com/randamonium/items/providers/ShopItemProvider.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/providers/ShopItemProvider.java) — ShopItemProvider, isValidItem, loadItem, compare
- [src/com/randamonium/items/timers/PlaytimeUpdater.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/timers/PlaytimeUpdater.java) — PlaytimeUpdater, run
- [src/com/randamonium/items/utils/formatting.java](https://github.com/VoxelHorizons/HavenCore/blob/b7373e3a4626bffb3f18f5abdffb217894866bb1/src/com/randamonium/items/utils/formatting.java) — formatting, formatChat, formatPlaceholders

### VoxelCore

- [src/main/java/org/voxelhorizons/PluginCore.java](https://github.com/VoxelHorizons/VoxelCore/blob/4c17c226c10583803063a95429063404f65f9e4d/src/main/java/org/voxelhorizons/PluginCore.java) — getInstance, PluginCore, onEnable, onDisable, onReload, replaceConfig, checkConfigVersion, getDefaultConfigVersion
- [src/main/java/org/voxelhorizons/command/CommandFactory.java](https://github.com/VoxelHorizons/VoxelCore/blob/4c17c226c10583803063a95429063404f65f9e4d/src/main/java/org/voxelhorizons/command/CommandFactory.java) — CommandFactory, register, onCommand, subExecute, canExecute, onTabComplete, tabCompleteSub, getAllCommandPaths, collectPaths
- [src/main/java/org/voxelhorizons/command/CommandRegistry.java](https://github.com/VoxelHorizons/VoxelCore/blob/4c17c226c10583803063a95429063404f65f9e4d/src/main/java/org/voxelhorizons/command/CommandRegistry.java) — CommandRegistry, register, getFactories
- [src/main/java/org/voxelhorizons/command/RootCommand.java](https://github.com/VoxelHorizons/VoxelCore/blob/4c17c226c10583803063a95429063404f65f9e4d/src/main/java/org/voxelhorizons/command/RootCommand.java) — Type/contract/field declarations; inspect linked source.
- [src/main/java/org/voxelhorizons/command/SubCommand.java](https://github.com/VoxelHorizons/VoxelCore/blob/4c17c226c10583803063a95429063404f65f9e4d/src/main/java/org/voxelhorizons/command/SubCommand.java) — Type/contract/field declarations; inspect linked source.
- [src/main/java/org/voxelhorizons/command/commands/AdminCommand.java](https://github.com/VoxelHorizons/VoxelCore/blob/4c17c226c10583803063a95429063404f65f9e4d/src/main/java/org/voxelhorizons/command/commands/AdminCommand.java) — AdminCommand, register, getName, getAliases, getPermission, playerOnly, execute
- [src/main/java/org/voxelhorizons/command/commands/BaseCommand.java](https://github.com/VoxelHorizons/VoxelCore/blob/4c17c226c10583803063a95429063404f65f9e4d/src/main/java/org/voxelhorizons/command/commands/BaseCommand.java) — getName, getAliases, getPermission, playerOnly, execute
- [src/main/java/org/voxelhorizons/command/commands/ReloadCommand.java](https://github.com/VoxelHorizons/VoxelCore/blob/4c17c226c10583803063a95429063404f65f9e4d/src/main/java/org/voxelhorizons/command/commands/ReloadCommand.java) — getName, getAliases, getPermission, playerOnly, execute
- [src/main/java/org/voxelhorizons/debug/CIMode.java](https://github.com/VoxelHorizons/VoxelCore/blob/4c17c226c10583803063a95429063404f65f9e4d/src/main/java/org/voxelhorizons/debug/CIMode.java) — CIMode, start

### HavenFurniture

- [src/com/randamonium/furniture/HavenFurniture.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/HavenFurniture.java) — HavenFurniture, onEnable, accept, getCore, getStore, getInstance, onDisable
- [src/com/randamonium/furniture/commands/FurnitureCommand.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/commands/FurnitureCommand.java) — FurnitureCommand, execute
- [src/com/randamonium/furniture/listeners/ChunkListener.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/listeners/ChunkListener.java) — onWorldLoadEvent
- [src/com/randamonium/furniture/listeners/InventoryListener.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/listeners/InventoryListener.java) — onPlayerCloseInventoryEvent
- [src/com/randamonium/furniture/listeners/PlayerListener.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/listeners/PlayerListener.java) — onPlayerPlacesBlockEvent, onPlayerInteractEvent, onPlayerLeftClicksEntity, onPlayerLeftClickBlock, onPlayerRightClickBlock, interactFurniture, openStorageInventory, onPlayerClosesInventory, onPlayerLeaves, resetFurnitureAnimation, onPlayerClicksInventory
- [src/com/randamonium/furniture/objects/Furniture.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/objects/Furniture.java) — Furniture, hasVariants, getAnimationModel, getAnimationValue, getAnimationType, hasAnimation, isSmall, getId, getSide, getParticle, getVariations, isPublic, hasStorage, getStorageSize, getLight
- [src/com/randamonium/furniture/objects/FurnitureBlock.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/objects/FurnitureBlock.java) — FurnitureBlock
- [src/com/randamonium/furniture/objects/FurnitureLight.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/objects/FurnitureLight.java) — FurnitureLight, create, remove, sendChunkUpdate, getxOffset, getyOffset, getzOffset, getStrength
- [src/com/randamonium/furniture/objects/FurnitureSeat.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/objects/FurnitureSeat.java) — FurnitureSeat
- [src/com/randamonium/furniture/objects/FurnitureVariant.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/objects/FurnitureVariant.java) — FurnitureVariant
- [src/com/randamonium/furniture/objects/helpers/ChunkHelper.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/objects/helpers/ChunkHelper.java) — getChunkID, formatRegionID
- [src/com/randamonium/furniture/objects/helpers/EntityHelper.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/objects/helpers/EntityHelper.java) — normalizeYaw, consumeItem
- [src/com/randamonium/furniture/objects/helpers/InventoryHelper.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/objects/helpers/InventoryHelper.java) — savePlayerInventory, getSavedPlayerInventory, inventoryToBase64, inventoryFromBase64
- [src/com/randamonium/furniture/objects/helpers/LocationHelper.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/objects/helpers/LocationHelper.java) — toString, fromString, round, isSimilar
- [src/com/randamonium/furniture/objects/managers/FurnitureManager.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/objects/managers/FurnitureManager.java) — FurnitureManager, saveWorldStorage, addWorldStore, recordFurnitureData, recordFurniture, listFurniture, SpawnFurniture, getFurniture, isBlock, isOwner, removeFurniture, getEntityConfig, resetAnimation
- [src/com/randamonium/furniture/objects/types/AnimationType.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/objects/types/AnimationType.java) — Type/contract/field declarations; inspect linked source.
- [src/com/randamonium/furniture/objects/types/RotateAnimationCheck.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/objects/types/RotateAnimationCheck.java) — RotateAnimationCheck, run
- [src/com/randamonium/furniture/objects/types/StorageAnimationCheck.java](https://github.com/VoxelHorizons/HavenFurniture/blob/ded1703b946403a130deb8c862a6970da50a6d77/src/com/randamonium/furniture/objects/types/StorageAnimationCheck.java) — StorageAnimationCheck, run

### HavenNPCs

- [src/com/randamonium/npc/HavenNPC.java](https://github.com/VoxelHorizons/HavenNPCs/blob/09ff53f2df25d2a2530654bf6ddfc315869bdf93/src/com/randamonium/npc/HavenNPC.java) — HavenNPC, getInstance, onEnable, run, onDisable, onPluginMessageReceived, getYAMLConfig
- [src/com/randamonium/npc/events/PlayerInteractNPCEvent.java](https://github.com/VoxelHorizons/HavenNPCs/blob/09ff53f2df25d2a2530654bf6ddfc315869bdf93/src/com/randamonium/npc/events/PlayerInteractNPCEvent.java) — PlayerInteractNPCEvent, isCancelled, setCancelled, getHandlers, getHandlerList, getPlayer, setPlayer, getNpc, setLobby
- [src/com/randamonium/npc/listeners/EntityInteractListener.java](https://github.com/VoxelHorizons/HavenNPCs/blob/09ff53f2df25d2a2530654bf6ddfc315869bdf93/src/com/randamonium/npc/listeners/EntityInteractListener.java) — EntityInteractListener, onPlayerRightClickEntityEvent, onPlayerClickInventoryEvent, onPlayerClosesInventoryEvent
- [src/com/randamonium/npc/listeners/gui/ChatMenu.java](https://github.com/VoxelHorizons/HavenNPCs/blob/09ff53f2df25d2a2530654bf6ddfc315869bdf93/src/com/randamonium/npc/listeners/gui/ChatMenu.java) — ChatMenu, openInventory, onPlayerClickInventoryEvent, onPlayerClosesInventoryEvent
- [src/com/randamonium/npc/listeners/gui/MainMenu.java](https://github.com/VoxelHorizons/HavenNPCs/blob/09ff53f2df25d2a2530654bf6ddfc315869bdf93/src/com/randamonium/npc/listeners/gui/MainMenu.java) — MainMenu, onPlayerClickInventoryEvent, onPlayerClosesInventoryEvent
- [src/com/randamonium/npc/manager/ChatManager.java](https://github.com/VoxelHorizons/HavenNPCs/blob/09ff53f2df25d2a2530654bf6ddfc315869bdf93/src/com/randamonium/npc/manager/ChatManager.java) — ChatManager, reloadChatOptions, getQuestion, messagePlayer, processChat, sendChat
- [src/com/randamonium/npc/manager/NPCManager.java](https://github.com/VoxelHorizons/HavenNPCs/blob/09ff53f2df25d2a2530654bf6ddfc315869bdf93/src/com/randamonium/npc/manager/NPCManager.java) — NPCManager, interactNPC, getPlayerStanding, reloadNPCs, saveConfigs
- [src/com/randamonium/npc/sql/sqlite/Database.java](https://github.com/VoxelHorizons/HavenNPCs/blob/09ff53f2df25d2a2530654bf6ddfc315869bdf93/src/com/randamonium/npc/sql/sqlite/Database.java) — Database, initialize, getTokens, getTotal, setTokens, close
- [src/com/randamonium/npc/sql/sqlite/Error.java](https://github.com/VoxelHorizons/HavenNPCs/blob/09ff53f2df25d2a2530654bf6ddfc315869bdf93/src/com/randamonium/npc/sql/sqlite/Error.java) — execute, close
- [src/com/randamonium/npc/sql/sqlite/Errors.java](https://github.com/VoxelHorizons/HavenNPCs/blob/09ff53f2df25d2a2530654bf6ddfc315869bdf93/src/com/randamonium/npc/sql/sqlite/Errors.java) — sqlConnectionExecute, sqlConnectionClose, noSQLConnection, noTableFound
- [src/com/randamonium/npc/sql/sqlite/SQLite.java](https://github.com/VoxelHorizons/HavenNPCs/blob/09ff53f2df25d2a2530654bf6ddfc315869bdf93/src/com/randamonium/npc/sql/sqlite/SQLite.java) — SQLite, getSQLConnection, load
- [src/com/randamonium/npc/utils/Utils.java](https://github.com/VoxelHorizons/HavenNPCs/blob/09ff53f2df25d2a2530654bf6ddfc315869bdf93/src/com/randamonium/npc/utils/Utils.java) — getUnicodeSymbol

### HavenFarms

- [src/com/randamonium/havenlife/HavenFarms.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/HavenFarms.java) — HavenFarms, onEnable, addPlotXP, removePlotXP, getRequiredXP, getCurrentLevel, onDisable, getInstance, getActiveInstance, getPlotSelector, getYAMLConfig, getUnicodeSymbol
- [src/com/randamonium/havenlife/commands/ReloadCommand.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/commands/ReloadCommand.java) — Type/contract/field declarations; inspect linked source.
- [src/com/randamonium/havenlife/flags/ChosenSchematicFlag.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/flags/ChosenSchematicFlag.java) — ChosenSchematicFlag, flagOf
- [src/com/randamonium/havenlife/flags/CustomNameFlag.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/flags/CustomNameFlag.java) — CustomNameFlag, parse, merge, toString, getExample, flagOf
- [src/com/randamonium/havenlife/flags/PlotFarmingLevelFlag.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/flags/PlotFarmingLevelFlag.java) — PlotFarmingLevelFlag, flagOf
- [src/com/randamonium/havenlife/flags/PlotMobsLevelFlag.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/flags/PlotMobsLevelFlag.java) — PlotMobsLevelFlag, flagOf
- [src/com/randamonium/havenlife/flags/PlotXPLevelFlag.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/flags/PlotXPLevelFlag.java) — PlotXPLevelFlag, flagOf
- [src/com/randamonium/havenlife/gui/AvailablePlots.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/gui/AvailablePlots.java) — AvailablePlots, onPlayerClosesInventoryEvent, onPlayerClickInventoryEvent, openInventory, getAvailablePlots, reloadPlots, getDayOfMonthSuffix
- [src/com/randamonium/havenlife/gui/PlotSelector.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/gui/PlotSelector.java) — PlotSelector, openInventory, openOwnedInventory, onPlayerClaimsPlotEvent, onPlayerCloseInventoryEvent, onPlayerClickInventoryEvent, reloadSchematics
- [src/com/randamonium/havenlife/listener/MobListener.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/listener/MobListener.java) — MobListener, onMobEntitySpawnEvent, isMonster
- [src/com/randamonium/havenlife/listener/P2Listener.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/listener/P2Listener.java) — P2Listener, onPlotPlayerTeleports
- [src/com/randamonium/havenlife/listener/PlayerListener.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/listener/PlayerListener.java) — PlayerListener, onPlayerBreakItemFrame, onPlayerPlacesBlock, onPlayerBreaksBlock, onPlayerInteractEvent, onPlayerRightClickBlock, randomWithRange, onFarmTeleport, onPlayerOpensGUI
- [src/com/randamonium/havenlife/managers/CropManager.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/managers/CropManager.java) — CropManager, saveWorldStorage, addWorldStore, getEntityConfig, removeEntityConfig, getCrop, SpawnCrop, recordCrop, isBlock, isEntity
- [src/com/randamonium/havenlife/mysql/Database.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/mysql/Database.java) — Database, getConnection, startConnection, run, closeConnection, prepareStatement, execute
- [src/com/randamonium/havenlife/objects/Crop.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/objects/Crop.java) — Crop, getId, getParticle, getMaxGrowth, getMinGrowth, getModel, getCropItem, getDrop, getSeeds
- [src/com/randamonium/havenlife/objects/PlotComparator.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/objects/PlotComparator.java) — compare
- [src/com/randamonium/havenlife/tasks/CropGrowthTask.java](https://github.com/VoxelHorizons/HavenFarms/blob/04a2188c1762ffc0330683a1e50b2cc7c11c1e49/src/com/randamonium/havenlife/tasks/CropGrowthTask.java) — CropGrowthTask, run, randomWithRange

### HavenVehicles

- [src/main/java/com/randamonium/havenrealms/HavenVehicles.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/HavenVehicles.java) — HavenVehicles, onEnable, accept, getYAMLConfig, onDisable, getInstance
- [src/main/java/com/randamonium/havenrealms/havenvehicles/HavenVehicles.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/havenvehicles/HavenVehicles.java) — onEnable, onDisable
- [src/main/java/com/randamonium/havenrealms/helpers/ChunkHelper.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/helpers/ChunkHelper.java) — getChunkID, formatRegionID
- [src/main/java/com/randamonium/havenrealms/helpers/EntityHelper.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/helpers/EntityHelper.java) — normalizeYaw, faceLocation, toEulerAngle, consumeItem
- [src/main/java/com/randamonium/havenrealms/helpers/InventoryHelper.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/helpers/InventoryHelper.java) — savePlayerInventory, getSavedPlayerInventory, toBase64, fromBase64
- [src/main/java/com/randamonium/havenrealms/helpers/LocationHelper.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/helpers/LocationHelper.java) — toString, fromString, round, isSimilar, backwards_flat, forwards_flat, forwards, backwards, getLookAtYaw
- [src/main/java/com/randamonium/havenrealms/helpers/StringHelper.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/helpers/StringHelper.java) — getWidth, capitalize, length
- [src/main/java/com/randamonium/havenrealms/helpers/VectorHelper.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/helpers/VectorHelper.java) — rotateAroundAxisX, rotateAroundAxisY, rotateAroundAxisZ, rotateVector, angleToXAxis
- [src/main/java/com/randamonium/havenrealms/listeners/PlayerListener.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/listeners/PlayerListener.java) — PlayerListener, onItemsAdderLoadEvent, onPlayerInteractEvent, onPlayerRightClicksEntity, onPlayerRightClickBlock, interactVehicle, openStorageInventory, onPlayerClosesInventory, onPlayerLeaves, resetVehicleAnimation, onPlayerClicksInventory
- [src/main/java/com/randamonium/havenrealms/listeners/VehicleListener.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/listeners/VehicleListener.java) — VehicleListener, onPacketReceiving
- [src/main/java/com/randamonium/havenrealms/managers/VehicleManager.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/managers/VehicleManager.java) — VehicleManager, reloadVehicles, saveWorldStorage, addWorldStore, recordVehicleData, recordVehicle, isVehicleEntity, isPrimaryEntity, isSeatEntity, listVehicles, SpawnVehicle, SpawnTrailer, getVehicle, getVehicleClass, isOwner, getOwner, removeVehicle, getEntityConfig, resetAnimation
- [src/main/java/com/randamonium/havenrealms/objects/Vehicle.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/objects/Vehicle.java) — Vehicle, hasVariants, getAnimationModel, getAnimationType, hasAnimation, isSmall, getId, getParticle, getVariations, isPublic, hasStorage, getStorageSize, steerVehicle, run, updateSpeedChange
- [src/main/java/com/randamonium/havenrealms/objects/VehicleVariant.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/objects/VehicleVariant.java) — VehicleVariant
- [src/main/java/com/randamonium/havenrealms/objects/parts/VehicleParticle.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/objects/parts/VehicleParticle.java) — VehicleParticle
- [src/main/java/com/randamonium/havenrealms/objects/parts/VehicleSeat.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/objects/parts/VehicleSeat.java) — VehicleSeat
- [src/main/java/com/randamonium/havenrealms/objects/parts/VehicleTrailer.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/objects/parts/VehicleTrailer.java) — VehicleTrailer, getOffsets, getxOffset, getyOffset, getzOffset
- [src/main/java/com/randamonium/havenrealms/objects/types/AnimationType.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/objects/types/AnimationType.java) — Type/contract/field declarations; inspect linked source.
- [src/main/java/com/randamonium/havenrealms/objects/types/VehicleType.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/objects/types/VehicleType.java) — Type/contract/field declarations; inspect linked source.
- [src/main/java/com/randamonium/havenrealms/tasks/StorageAnimationCheck.java](https://github.com/VoxelHorizons/HavenVehicles/blob/86db6b39b938119a5fc3e6f6a53398821ef0578d/src/main/java/com/randamonium/havenrealms/tasks/StorageAnimationCheck.java) — StorageAnimationCheck, run

### HavenSkills

- [src/com/randamonium/skills/HavenSkills.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/HavenSkills.java) — HavenSkills, onEnable, getInstance, onDisable, reloadPlayerData, savePlayerData, saveBlockData, saveAllConfigurations, getYAMLConfig
- [src/com/randamonium/skills/commands/CommandExecutor.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/commands/CommandExecutor.java) — CommandExecutor, onCommand
- [src/com/randamonium/skills/commands/SkillsCommand.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/commands/SkillsCommand.java) — SkillsCommand, execute
- [src/com/randamonium/skills/gui/SkillsGUI.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/gui/SkillsGUI.java) — SkillsGUI, openPointInventory, openInventory, onPlayerClickInventoryEvent, clickSkill, clickSkillpoint
- [src/com/randamonium/skills/listeners/PlayerListener.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/listeners/PlayerListener.java) — PlayerListener, onPlayerJoinEvent, onPlayerBreaksBlockEvent, onPlayerTeleportsEvent, onPlayerMovesEvent
- [src/com/randamonium/skills/listeners/ShopListener.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/listeners/ShopListener.java) — ShopListener, playerFinishShop, priceActivityUpdate
- [src/com/randamonium/skills/managers/BlockManager.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/managers/BlockManager.java) — BlockManager, run, instanceBlock, setTime, setReplaces, addBlockModifier, setDrops, resetBlocks
- [src/com/randamonium/skills/managers/SkillManager.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/managers/SkillManager.java) — SkillManager, saveData, reloadData, loadPlayer, getPlayerSkillpointPoints, addPlayerSkillpointsPoints, removePlayerSkillpointsPoints, addPlayerSkillpointLevel, getPlayerSkillpointLevel, getRequiredXP, getNextLevelXP, getCurrentLevelXP, getCurrentLevel, visitRegion, addLevelXP, levelupPlayer, getSkill, skillActivityUpdate
- [src/com/randamonium/skills/objects/Condition.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/objects/Condition.java) — Condition, loadModifyProperties
- [src/com/randamonium/skills/objects/Offset.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/objects/Offset.java) — Offset
- [src/com/randamonium/skills/objects/Skill.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/objects/Skill.java) — Skill, reloadSkillPoints, hasEmoji
- [src/com/randamonium/skills/objects/SkillMaterial.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/objects/SkillMaterial.java) — Type/contract/field declarations; inspect linked source.
- [src/com/randamonium/skills/objects/SkillPoint.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/objects/SkillPoint.java) — SkillPoint, loadBreakProperties, loadReplaceProperties, loadDropProperties, loadSwapProperties, loadXPProperties, loadCondition
- [src/com/randamonium/skills/tasks/ActionbarLoop.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/tasks/ActionbarLoop.java) — ActionbarLoop, run, parseTime, getUnicodeSymbol
- [src/com/randamonium/skills/types/BreakType.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/types/BreakType.java) — Type/contract/field declarations; inspect linked source.
- [src/com/randamonium/skills/types/ConditionType.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/types/ConditionType.java) — Type/contract/field declarations; inspect linked source.
- [src/com/randamonium/skills/types/ModifierType.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/types/ModifierType.java) — Type/contract/field declarations; inspect linked source.
- [src/com/randamonium/skills/types/SkillPointType.java](https://github.com/VoxelHorizons/HavenSkills/blob/4d2e1435695b1dab7a116c0dcb6f1b7b2b65036d/src/com/randamonium/skills/types/SkillPointType.java) — Type/contract/field declarations; inspect linked source.

### HavenFurnitureShop

- [src/main/java/com/randamonium/furniture/furntiureshop/FurnitureShop.java](https://github.com/VoxelHorizons/HavenFurnitureShop/blob/ef9713483d767a81214dc680cb055942af018e3c/src/main/java/com/randamonium/furniture/furntiureshop/FurnitureShop.java) — onEnable, getInstance, onDisable
- [src/main/java/com/randamonium/furniture/furntiureshop/command/FurnitureShopCommand.java](https://github.com/VoxelHorizons/HavenFurnitureShop/blob/ef9713483d767a81214dc680cb055942af018e3c/src/main/java/com/randamonium/furniture/furntiureshop/command/FurnitureShopCommand.java) — FurnitureShopCommand, execute
- [src/main/java/com/randamonium/furniture/furntiureshop/managers/ShopManager.java](https://github.com/VoxelHorizons/HavenFurnitureShop/blob/ef9713483d767a81214dc680cb055942af018e3c/src/main/java/com/randamonium/furniture/furntiureshop/managers/ShopManager.java) — ShopManager, templateExists, addShop, resetShop
- [src/main/java/com/randamonium/furniture/furntiureshop/templates/FurnitureTemplate.java](https://github.com/VoxelHorizons/HavenFurnitureShop/blob/ef9713483d767a81214dc680cb055942af018e3c/src/main/java/com/randamonium/furniture/furntiureshop/templates/FurnitureTemplate.java) — FurnitureTemplate, addFurniture, setFurniture, save, spawn

### HavenGames

- [src/com/randamonium/havenrealms/games/HavenGames.java](https://github.com/VoxelHorizons/HavenGames/blob/709c635ccc70b4ada631e8284eb46bdc4d377fea/src/com/randamonium/havenrealms/games/HavenGames.java) — HavenGames, getInstance, onEnable, getYAMLConfig
- [src/com/randamonium/havenrealms/games/bars/GameBars.java](https://github.com/VoxelHorizons/HavenGames/blob/709c635ccc70b4ada631e8284eb46bdc4d377fea/src/com/randamonium/havenrealms/games/bars/GameBars.java) — GameBars, run, addLobbyPlayer, removeLobbyPlayer
- [src/com/randamonium/havenrealms/games/events/PlayerFinishesGameEvent.java](https://github.com/VoxelHorizons/HavenGames/blob/709c635ccc70b4ada631e8284eb46bdc4d377fea/src/com/randamonium/havenrealms/games/events/PlayerFinishesGameEvent.java) — PlayerFinishesGameEvent, getHandlers, getHandlerList, getPlayer, getGame, setGame, setPlayer
- [src/com/randamonium/havenrealms/games/events/PlayerJoinsGameEvent.java](https://github.com/VoxelHorizons/HavenGames/blob/709c635ccc70b4ada631e8284eb46bdc4d377fea/src/com/randamonium/havenrealms/games/events/PlayerJoinsGameEvent.java) — PlayerJoinsGameEvent, isCancelled, setCancelled, getHandlers, getHandlerList, getPlayer, getGame, setGame, setPlayer
- [src/com/randamonium/havenrealms/games/events/PlayerJoinsLobbyEvent.java](https://github.com/VoxelHorizons/HavenGames/blob/709c635ccc70b4ada631e8284eb46bdc4d377fea/src/com/randamonium/havenrealms/games/events/PlayerJoinsLobbyEvent.java) — PlayerJoinsLobbyEvent, isCancelled, setCancelled, getHandlers, getHandlerList, getPlayer, setPlayer, getLobby, setLobby
- [src/com/randamonium/havenrealms/games/events/PlayerLeavesLobbyEvent.java](https://github.com/VoxelHorizons/HavenGames/blob/709c635ccc70b4ada631e8284eb46bdc4d377fea/src/com/randamonium/havenrealms/games/events/PlayerLeavesLobbyEvent.java) — PlayerLeavesLobbyEvent, isCancelled, setCancelled, getHandlers, getHandlerList, getPlayer, setPlayer, getLobby, setLobby
- [src/com/randamonium/havenrealms/games/events/PlayerStartsGameEvent.java](https://github.com/VoxelHorizons/HavenGames/blob/709c635ccc70b4ada631e8284eb46bdc4d377fea/src/com/randamonium/havenrealms/games/events/PlayerStartsGameEvent.java) — PlayerStartsGameEvent, isCancelled, setCancelled, getHandlers, getHandlerList, getPlayer, getGame, setGame, setPlayer
- [src/com/randamonium/havenrealms/games/games/Game.java](https://github.com/VoxelHorizons/HavenGames/blob/709c635ccc70b4ada631e8284eb46bdc4d377fea/src/com/randamonium/havenrealms/games/games/Game.java) — Game, getName, setName, getGameTime, getMaximumPlayers, getMinimumPlayers, getBoard, isStarted, setStarted, isEnded, setEnded, start, stop, gameTickCheck, gameCheckPlayerCount, gameTick, getGameMessage, getTimeMessage
- [src/com/randamonium/havenrealms/games/games/PassTheBombGame.java](https://github.com/VoxelHorizons/HavenGames/blob/709c635ccc70b4ada631e8284eb46bdc4d377fea/src/com/randamonium/havenrealms/games/games/PassTheBombGame.java) — PassTheBombGame
- [src/com/randamonium/havenrealms/games/games/TagGame.java](https://github.com/VoxelHorizons/HavenGames/blob/709c635ccc70b4ada631e8284eb46bdc4d377fea/src/com/randamonium/havenrealms/games/games/TagGame.java) — TagGame, chooseIt, getIt, getLastIt, setIt, gameTickCheck, start, getGameMessage
- [src/com/randamonium/havenrealms/games/listeners/PlayerListener.java](https://github.com/VoxelHorizons/HavenGames/blob/709c635ccc70b4ada631e8284eb46bdc4d377fea/src/com/randamonium/havenrealms/games/listeners/PlayerListener.java) — PlayerListener, onPlayerLeaves, onPlayerClicksPlayer
- [src/com/randamonium/havenrealms/games/lobby/GameLobby.java](https://github.com/VoxelHorizons/HavenGames/blob/709c635ccc70b4ada631e8284eb46bdc4d377fea/src/com/randamonium/havenrealms/games/lobby/GameLobby.java) — GameLobby, getGame, getPlayers, hasPlayer, joinPlayer, removePlayer, hasRequiredPlayers, tickCountdown, tickGameOver, getStopCountdown, gameReady, getGameCountdown, switchDisplay, getId, startGame, stopGame, isStopped, setStopped
- [src/com/randamonium/havenrealms/games/managers/GameManager.java](https://github.com/VoxelHorizons/HavenGames/blob/709c635ccc70b4ada631e8284eb46bdc4d377fea/src/com/randamonium/havenrealms/games/managers/GameManager.java) — GameManager, newTagGame, newPassTheBombGame, insideLobby, getPlayerLobby, findLobby, addLobby, removeLobby, getLobbies, cleanPlayer

### RegenMines

- [src/main/java/dev/pillage/regenmines/MineBlock.java](https://github.com/VoxelHorizons/RegenMines/blob/ac5d9a9ae70b5622c998ef45fc0fd25070dc7d81/src/main/java/dev/pillage/regenmines/MineBlock.java) — Type/contract/field declarations; inspect linked source.
- [src/main/java/dev/pillage/regenmines/MineManager.java](https://github.com/VoxelHorizons/RegenMines/blob/ac5d9a9ae70b5622c998ef45fc0fd25070dc7d81/src/main/java/dev/pillage/regenmines/MineManager.java) — init, reloadRegions, scanInitialStates, getMineBlocks, reset, getRegions, getReplacementMaterial, recordBlockBreak, checkAllResetTimes
- [src/main/java/dev/pillage/regenmines/RegenMines.java](https://github.com/VoxelHorizons/RegenMines/blob/ac5d9a9ae70b5622c998ef45fc0fd25070dc7d81/src/main/java/dev/pillage/regenmines/RegenMines.java) — onPluginStart, onReloadablesStart, onPluginPreReload, onPluginStop
- [src/main/java/dev/pillage/regenmines/commands/MainCommandGroup.java](https://github.com/VoxelHorizons/RegenMines/blob/ac5d9a9ae70b5622c998ef45fc0fd25070dc7d81/src/main/java/dev/pillage/regenmines/commands/MainCommandGroup.java) — registerSubcommands
- [src/main/java/dev/pillage/regenmines/commands/UpdateCmd.java](https://github.com/VoxelHorizons/RegenMines/blob/ac5d9a9ae70b5622c998ef45fc0fd25070dc7d81/src/main/java/dev/pillage/regenmines/commands/UpdateCmd.java) — UpdateCmd, onCommand
- [src/main/java/dev/pillage/regenmines/listeners/BlockBreakListener.java](https://github.com/VoxelHorizons/RegenMines/blob/ac5d9a9ae70b5622c998ef45fc0fd25070dc7d81/src/main/java/dev/pillage/regenmines/listeners/BlockBreakListener.java) — onBlockBreak

### HavenNetwork

- [src/com/randamonium/havenrealms/HavenNetwork.java](https://github.com/VoxelHorizons/HavenNetwork/blob/2c6524f6604174967cf8724da0022fe79d419d7b/src/com/randamonium/havenrealms/HavenNetwork.java) — onEnable
- [src/com/randamonium/havenrealms/Tooltip.java](https://github.com/VoxelHorizons/HavenNetwork/blob/2c6524f6604174967cf8724da0022fe79d419d7b/src/com/randamonium/havenrealms/Tooltip.java) — Tooltip
- [src/com/randamonium/havenrealms/commands/DynamicServerCommand.java](https://github.com/VoxelHorizons/HavenNetwork/blob/2c6524f6604174967cf8724da0022fe79d419d7b/src/com/randamonium/havenrealms/commands/DynamicServerCommand.java) — DynamicServerCommand, execute
- [src/com/randamonium/havenrealms/messaging/TooltipChannel.java](https://github.com/VoxelHorizons/HavenNetwork/blob/2c6524f6604174967cf8724da0022fe79d419d7b/src/com/randamonium/havenrealms/messaging/TooltipChannel.java) — on
- [src/com/randamonium/havenrealms/web/RestProvider.java](https://github.com/VoxelHorizons/HavenNetwork/blob/2c6524f6604174967cf8724da0022fe79d419d7b/src/com/randamonium/havenrealms/web/RestProvider.java) — RestProvider, main, MyHandler, handle


## Bedrock implementation checkpoint — 2026-10-24

The first Bedrock increment now builds explicit item texture mappings and resource packs, exports immutable revisions, publishes VoxelCore-owned files to a configured Geyser data directory, and excludes identified Floodgate/Geyser players from Java pack requests. See [setup, implementation limits and acceptance checklist](docs/bedrock.md). This supersedes the earlier “no Bedrock pack/conversion” status only for these implemented foundations. Automatic model conversion, entity/block mappings, Bedrock menus and remote Geyser extension synchronization remain outstanding. Geyser restart and Bedrock reconnect are required after publication; full cross-platform parity is not claimed.

Bedrock preview follow-up: automatic single-layer texture reuse and headless cube model icons now resolve parents, UVs and GUI transforms, with a persistent asset-hash cache and explicit unsupported-model diagnostics. Native 3D geometry conversion and shader parity remain outstanding.
