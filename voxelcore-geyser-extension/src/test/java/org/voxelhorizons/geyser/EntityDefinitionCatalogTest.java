package org.voxelhorizons.geyser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public final class EntityDefinitionCatalogTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    private Path fixture(boolean includeTexture) throws Exception {
        Path root=temp.newFolder().toPath();
        Path pack=root.resolve("packs/voxelcore.mcpack");
        Files.createDirectories(pack.getParent());
        String id="i_1234567890abcdef1234567890abcdef";
        Map<String,String> entries=new TreeMap<String,String>();
        entries.put("voxelcore/entity_index.json",
                "{\"schema\":1,\"content_models\":{\"voxel:oak_bench\":\"voxelcore:entity_"+id+"\"}}");
        entries.put("entity/voxelcore/"+id+".entity.json",
                "{\"minecraft:client_entity\":{\"description\":{\"identifier\":\"voxelcore:entity_"+id
                        +"\",\"geometry\":{\"default\":\"geometry.voxelcore."+id
                        +"\"},\"textures\":{\"default\":\"textures/voxelcore/g_"+id+"\"}}}}");
        entries.put("models/entity/voxelcore/"+id+".geo.json",
                "{\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.voxelcore."+id+"\"}}]}");
        if (includeTexture) entries.put("textures/voxelcore/g_"+id+".png","PNG fixture");
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(pack))) {
            for (Map.Entry<String,String> entry:entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return root;
    }

    @Test public void validatesInstalledEntityIndexAgainstReferencedFiles() throws Exception {
        Map<String,String> entity = EntityDefinitionCatalog.load(fixture(true));
        assertEquals(1,entity.size());
        assertEquals("voxelcore:entity_i_1234567890abcdef1234567890abcdef",
                entity.get("voxel:oak_bench"));
        try {entity.put("hijack","foo");fail("immutable registry expected");}
        catch(UnsupportedOperationException expected){}
    }
    @Test public void rejectsUnresolvedVisualTexture() throws Exception {
        try {
            EntityDefinitionCatalog.load(fixture(false));
            fail("Missing face atlas must not register a custom entity");
        } catch(java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("Missing Bedrock entity texture"));
        }
    }
}
