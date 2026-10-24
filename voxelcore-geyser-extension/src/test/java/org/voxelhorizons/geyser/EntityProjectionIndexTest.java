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

    @Test public void stalePaperFileDoesNotReplaceNewDisplaysAndExpiresCachedMappings() throws Exception {
        Path root=temp.newFolder().toPath(),dir=root.resolve("voxelcore-projection");
        Files.createDirectories(dir);
        UUID id=UUID.randomUUID();
        Path file=dir.resolve("furniture.json");
        Files.writeString(file,fixture(id.toString(),"voxel:oak_bench"),StandardCharsets.UTF_8);
        String mapped="voxelcore:entity_i_1234567890abcdef1234567890abcdef";
        EntityProjectionIndex index=new EntityProjectionIndex(root,Map.of("voxel:oak_bench",mapped));
        Files.setLastModifiedTime(file,FileTime.fromMillis(System.currentTimeMillis()-120_000L));
        assertNull("An orphaned Paper file is not authoritative",index.entityType(id));
        Files.setLastModifiedTime(file,FileTime.fromMillis(System.currentTimeMillis()));
        assertEquals("A renewed feed becomes usable",mapped,index.entityType(id));
        Files.setLastModifiedTime(file,FileTime.fromMillis(System.currentTimeMillis()-120_000L));
        assertNull("Cached entries must expire when the source goes stale",index.entityType(id));
        assertEquals(0,index.size());
    }

    @Test public void expiringOneFeedPreservesOtherFreshModelMappings() throws Exception {
        Path root=temp.newFolder().toPath(),dir=root.resolve("voxelcore-projection");
        Files.createDirectories(dir);
        UUID furniture=UUID.randomUUID(), vehicle=UUID.randomUUID();
        Path furnitureFile=dir.resolve("furniture.json"),vehicleFile=dir.resolve("vehicles.json");
        Files.writeString(furnitureFile,fixture(furniture.toString(),"voxel:oak_bench"));
        com.google.gson.JsonObject vehicleLink=new com.google.gson.JsonObject();
        vehicleLink.addProperty("visual",vehicle.toString());
        com.google.gson.JsonObject vehicleRow=new com.google.gson.JsonObject();
        vehicleRow.addProperty("model_item","voxel:tractor");
        vehicleRow.add("entity_links",vehicleLink);
        com.google.gson.JsonArray vehicleRows=new com.google.gson.JsonArray();
        vehicleRows.add(vehicleRow);
        com.google.gson.JsonObject vehicleFeed=new com.google.gson.JsonObject();
        vehicleFeed.addProperty("schema",1);
        vehicleFeed.add("vehicles",vehicleRows);
        Files.writeString(vehicleFile,vehicleFeed.toString());
        Files.setLastModifiedTime(furnitureFile,FileTime.fromMillis(System.currentTimeMillis()-120_000L));
        EntityProjectionIndex index=new EntityProjectionIndex(root,Map.of(
                "voxel:oak_bench","voxelcore:entity_bench",
                "voxel:tractor","voxelcore:entity_tractor"));
        assertNull(index.entityType(furniture));
        assertEquals("voxelcore:entity_tractor",index.entityType(vehicle));
        assertEquals(1,index.size());
    }

    @Test public void projectsUniformFurnitureScaleButNotAnisotropicOrInvalidScale() throws Exception {
        Path root=temp.newFolder().toPath(),dir=root.resolve("voxelcore-projection");
        Files.createDirectories(dir);
        UUID id=UUID.randomUUID();
        Path file=dir.resolve("furniture.json");
        com.google.gson.JsonObject uniformFeed=com.google.gson.JsonParser.parseString(
                fixture(id.toString(),"voxel:oak_bench")).getAsJsonObject();
        com.google.gson.JsonArray uniformScale=new com.google.gson.JsonArray();
        uniformScale.add(2.5); uniformScale.add(2.5); uniformScale.add(2.5);
        uniformFeed.getAsJsonArray("furniture").get(0).getAsJsonObject().add("scale",uniformScale);
        String uniform=uniformFeed.toString();
        Files.writeString(file,uniform);
        EntityProjectionIndex index=new EntityProjectionIndex(root,Map.of(
                "voxel:oak_bench","voxelcore:entity_bench"));
        assertEquals(Float.valueOf(2.5f),index.projection(id).scale());
        assertEquals("voxelcore:entity_bench",index.entityType(id));
        Files.writeString(file,uniform.replace("[2.5,2.5,2.5]","[2.5,3,2.5]"));
        Files.setLastModifiedTime(file,FileTime.fromMillis(System.currentTimeMillis()+5000));
        assertNull("XYZ anisotropic scale is not a Geyser uniform scale",index.projection(id).scale());
        Files.writeString(file,uniform.replace("[2.5,2.5,2.5]","[0,0,0]"));
        Files.setLastModifiedTime(file,FileTime.fromMillis(System.currentTimeMillis()+10000));
        assertNull("Non-positive scale is unsafe",index.projection(id).scale());
    }

    @Test public void projectsVehicleUniformScaleWithoutInventingMissingValues() throws Exception {
        Path root=temp.newFolder().toPath(),dir=root.resolve("voxelcore-projection");
        Files.createDirectories(dir);
        UUID vehicle=UUID.randomUUID();
        com.google.gson.JsonObject links=new com.google.gson.JsonObject();
        links.addProperty("visual",vehicle.toString());
        com.google.gson.JsonObject entry=new com.google.gson.JsonObject();
        entry.addProperty("model_item","voxel:tractor");
        entry.addProperty("scale",1.75);
        entry.add("entity_links",links);
        com.google.gson.JsonArray entries=new com.google.gson.JsonArray();
        entries.add(entry);
        com.google.gson.JsonObject feed=new com.google.gson.JsonObject();
        feed.addProperty("schema",1);
        feed.add("vehicles",entries);
        Files.writeString(dir.resolve("vehicles.json"),feed.toString());
        EntityProjectionIndex index=new EntityProjectionIndex(root,Map.of(
                "voxel:tractor","voxelcore:entity_tractor"));
        assertEquals(Float.valueOf(1.75f),index.projection(vehicle).scale());
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
