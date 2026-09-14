package org.voxelhorizons.command.commands;

import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.item.ItemType;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public final class ItemCommandTest {

    @Test
    public void listableItemIdsExcludesAbstractDefinitionsButIncludesUnboundItems() {
        ItemDefinition abstractBase = definition("voxelpack:economy_base", false, true);
        ItemDefinition token = definition("voxelpack:token", false, false);
        ItemDefinition coin = definition("voxelpack:coin", true, false);

        Map<ContentID, ItemDefinition> definitions = new LinkedHashMap<ContentID, ItemDefinition>();
        definitions.put(token.id(), token);
        definitions.put(abstractBase.id(), abstractBase);
        definitions.put(coin.id(), coin);

        List<ContentID> actual = ItemCommand.listableItemIds(new ItemDefinitionRegistry(definitions));

        assertEquals(Arrays.asList(coin.id(), token.id()), actual);
    }

    private static ItemDefinition definition(String id, boolean bound, boolean abstractDefinition) {
        ContentID contentId = ContentID.parse(id, "voxelhorizons");
        return new ItemDefinition(
                contentId,
                ItemType.ITEM,
                "minecraft:diamond_hoe",
                contentId.value(),
                Collections.<String>emptyList(),
                bound,
                abstractDefinition,
                Optional.<ContentID>empty(),
                null,
                Collections.<String, Object>emptyMap());
    }
}
