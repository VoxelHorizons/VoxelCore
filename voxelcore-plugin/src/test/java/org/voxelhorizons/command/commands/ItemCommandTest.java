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
    public void listableItemIdsExcludesUnboundDefinitionsAndSortsRemainingIds() {
        ItemDefinition unboundBase = definition("voxelpack:economy_base", false);
        ItemDefinition token = definition("voxelpack:token", true);
        ItemDefinition coin = definition("voxelpack:coin", true);

        Map<ContentID, ItemDefinition> definitions = new LinkedHashMap<ContentID, ItemDefinition>();
        definitions.put(token.id(), token);
        definitions.put(unboundBase.id(), unboundBase);
        definitions.put(coin.id(), coin);

        List<ContentID> actual = ItemCommand.listableItemIds(new ItemDefinitionRegistry(definitions));

        assertEquals(Arrays.asList(coin.id(), token.id()), actual);
    }

    private static ItemDefinition definition(String id, boolean bound) {
        ContentID contentId = ContentID.parse(id, "voxelhorizons");
        return new ItemDefinition(
                contentId,
                ItemType.ITEM,
                "minecraft:diamond_hoe",
                contentId.value(),
                Collections.<String>emptyList(),
                bound,
                Optional.<ContentID>empty(),
                null,
                Collections.<String, Object>emptyMap());
    }
}
