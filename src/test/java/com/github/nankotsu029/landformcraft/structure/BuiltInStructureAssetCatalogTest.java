package com.github.nankotsu029.landformcraft.structure;

import com.github.nankotsu029.landformcraft.model.StructureType;
import com.github.nankotsu029.landformcraft.worldedit.MinecraftBlockPalette;
import com.github.nankotsu029.landformcraft.worldedit.SpongeSchematicInspector;
import com.github.nankotsu029.landformcraft.worldedit.SpongeSchematicWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInStructureAssetCatalogTest {
    @Test
    void coversEverySupportedTypeWithUniqueVersionedChecksums() {
        var catalog = new BuiltInStructureAssetCatalog();
        assertEquals(EnumSet.allOf(StructureType.class), catalog.assets().stream()
                .map(StructureAsset::type).collect(java.util.stream.Collectors.toSet()));
        assertEquals(catalog.assets().size(), catalog.assets().stream()
                .map(StructureAsset::assetId).collect(java.util.stream.Collectors.toSet()).size());
        assertEquals(catalog.assets().size(), catalog.assets().stream()
                .map(StructureAsset::semanticChecksum).collect(java.util.stream.Collectors.toSet()).size());
        catalog.assets().forEach(asset -> {
            assertEquals(BuiltInStructureAssetCatalog.MINECRAFT_VERSION, asset.minecraftVersion());
            assertEquals(asset, catalog.requireById(asset.assetId()));
            assertEquals(asset, catalog.requireByType(asset.type()));
            asset.blocks().forEach(block -> assertTrue(MinecraftBlockPalette.states()
                    .containsKey(block.blockState())));
        });
    }

    @Test
    void standaloneSchematicsCanBeReadBack(@TempDir Path directory) throws Exception {
        var writer = new SpongeSchematicWriter();
        var inspector = new SpongeSchematicInspector();
        var checksums = new HashSet<String>();
        for (StructureAsset asset : new BuiltInStructureAssetCatalog().assets()) {
            Path file = directory.resolve(asset.assetId() + ".schem");
            writer.writeAsset(file, asset, () -> false);
            var info = inspector.inspect(file);
            assertEquals(asset.width(), info.width());
            assertEquals(asset.height(), info.height());
            assertEquals(asset.length(), info.length());
            assertEquals((long) asset.width() * asset.height() * asset.length(), info.blockEntryCount());
            assertTrue(checksums.add(com.github.nankotsu029.landformcraft.format.Sha256.file(file)));
        }
    }
}
