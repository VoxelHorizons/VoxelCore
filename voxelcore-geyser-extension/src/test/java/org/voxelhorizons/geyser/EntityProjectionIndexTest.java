package org.voxelhorizons.geyser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public final class EntityProjectionIndexTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    private static String fixture(String displayUuid,String model) {
        return "{\"schema\":1,\"content_revision\":5,\"furniture\":[{"
                +"\"model_item\":\""+model+"\",\"entity_links\":{"
                +"\"display_entity_uuid\":\""+displayUuid+"\",\"parts\":[]}}]}";
    }

    @Test public void reloadsKnownDisplayMappingsWithoutInventingOtherEntities() throws Exception {
        Path root=temp.newFolder().toPath(),dir=root.resolve("voxelcore-projection");
        Files.createDirectories(dir);
        UUID id=UUID.randomUUID();
        UUID next=UUID.randomUUID();
        EntityProjectionIndex index=new EntityProjectionIndex(root,
                Map.of("voxel:oak_bench","voxelcore:entity_i_1234567890abcdef1234567890abcdef"));
        assertNull(index.entityType(id));
        Path file=dir.resolve("furniture.json");
        Files.writeString(file,fixture(id.toString(),"voxel:oak_bench"),StandardCharsets.UTF_8);
        assertEquals("voxelcore:entity_i_1234567890abcdef1234567890abcdef",index.entityType(id));
        assertEquals(1,index.size());
        assertNull(index.entityType(next));
        Files.writeString(file,fixture(next.toString(),"voxel:oak_bench"),StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file,FileTime.fromMillis(System.currentTimeMillis()+5000));
        assertNull(index.entityType(id));
        assertNotNull(index.entityType(next));
        Files.delete(file);
        assertEquals(0,index.size());
    }

    @Test public void doesNotMapUnknownModelVariants() throws Exception {
        Path root=temp.newFolder().toPath(),dir=root.resolve("voxelcore-projection");
        Files.createDirectories(dir);
        UUID id=UUID.randomUUID();
        Files.writeString(dir.resolve("furniture.json"),fixture(id.toString(),"voxel:unknown"));
        EntityProjectionIndex index=new EntityProjectionIndex(root,
                Map.of("voxel:oak_bench","voxelcore:entity_i_1234567890abcdef1234567890abcdef"));
        assertNull(index.entityType(id));
    }

    @Test public void rejectsCorruptStateInsteadOfOverridingArbitraryJavaEntities() throws Exception {
        Path root=temp.newFolder().toPath(),dir=root.resolve("voxelcore-projection");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("furniture.json"),"{\"schema\":3,\"furniture\":[]}");
        EntityProjectionIndex index=new EntityProjectionIndex(root,Map.of());
        try {index.size();fail("Must reject incompatible schema");}
        catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("schema"));}
    }
}
