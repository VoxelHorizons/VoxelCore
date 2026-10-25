package org.voxelhorizons.geyser;

import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.entity.BedrockEntityDefinition;
import org.geysermc.geyser.entity.EntityTypeBase;
import org.geysermc.geyser.entity.GeyserEntityType;
import org.geysermc.geyser.entity.VanillaEntityType;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;

/**
 * Geyser has no Java entity type for minecraft:item_display: its add-entity translator
 * returns before ServerSpawnEntityEvent fires, so placed furniture and vehicles were never
 * offered for substitution (live test: 0 spawn events). This registers a minimal type whose
 * default Bedrock entity is an invisible placeholder; VoxelCore displays are then swapped
 * for their native entity by the normal spawn substitution. Display metadata (transforms,
 * item) is ignored here because the projection feed supplies it.
 *
 * Uses Geyser internals (same approach as GeyserDisplayEntity); callers must isolate any
 * LinkageError so a Geyser update only disables this feature.
 */
final class ItemDisplayRegistration {
    static final String PLACEHOLDER = "voxelcore:display_placeholder";

    private ItemDisplayRegistration() {}

    /** @return null when registered, otherwise why not (another extension owns item_display). */
    static String register(CustomEntityDefinition placeholder) {
        GeyserEntityType type = GeyserEntityType.ofVanilla(EntityType.ITEM_DISPLAY);
        if (Registries.JAVA_ENTITY_TYPES.get(type) != null)
            return "another extension (e.g. GeyserDisplayEntity) already handles item_display";
        EntityTypeBase<Entity> entityBase = EntityTypeBase.baseBuilder(Entity.class)
                .addTranslator(MetadataTypes.BYTE, Entity::setFlags)
                .addTranslator(MetadataTypes.INT, Entity::setAir)
                .addTranslator(MetadataTypes.OPTIONAL_COMPONENT, Entity::setCustomName)
                .addTranslator(MetadataTypes.BOOLEAN, Entity::setCustomNameVisible)
                .addTranslator(MetadataTypes.BOOLEAN, Entity::setSilent)
                .addTranslator(MetadataTypes.BOOLEAN, Entity::setGravity)
                .addTranslator(MetadataTypes.POSE, (entity, metadata) -> entity.setPose(metadata.getValue()))
                .addTranslator(MetadataTypes.INT, Entity::setFreezing)
                .build();
        VanillaEntityType.Builder<Entity> builder = VanillaEntityType.inherited(Entity::new, entityBase)
                .type(EntityType.ITEM_DISPLAY)
                .height(0.001f).width(0.001f)
                .bedrockDefinition((BedrockEntityDefinition) placeholder);
        // Display base: interpolation x3, translation, scale, left/right rotation, billboard,
        // brightness, view range, shadow radius/strength, width, height, glow colour; then the
        // item stack and item display transform. All ignored (the projection feed is authoritative).
        for (int i = 0; i < 17; i++) builder.addTranslator(null);
        Registries.JAVA_ENTITY_TYPES.register(type, builder.build());
        return null;
    }
}
