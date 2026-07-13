package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.structure.BuiltInStructureAssetCatalog;
import com.github.nankotsu029.landformcraft.worldedit.SpongeSchematicWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomAssetServiceTest {
    @Test
    void validatesImportsListsAndRemovesRestrictedSpongeV3Asset(@TempDir Path directory) throws Exception {
        Path imports = directory.resolve("imports");
        Files.createDirectories(imports);
        var builtIn = new BuiltInStructureAssetCatalog().requireByType(
                com.github.nankotsu029.landformcraft.model.StructureType.PATH);
        new SpongeSchematicWriter().writeAsset(imports.resolve("custom-path.schem"), builtIn, () -> false);
        Files.writeString(imports.resolve("custom-path.json"), metadata("custom-path-beta"));
        CustomAssetService service = new CustomAssetService(
                imports, directory.resolve("assets"), directory.resolve("exports"), Clock.systemUTC());

        var validated = service.validate("custom-path.schem", "custom-path.json");
        var imported = service.importAsset("custom-path.schem", "custom-path.json");

        assertEquals("custom-path-beta", validated.metadata().assetId());
        assertEquals(validated.semanticChecksum(), imported.semanticChecksum());
        assertEquals(1, service.list().size());
        assertEquals(imported, service.info("custom-path-beta"));
        service.remove("custom-path-beta");
        assertEquals(0, service.list().size());
    }

    @Test
    void rejectsBuiltInIdAbsolutePathAndSymbolicLink(@TempDir Path directory) throws Exception {
        Path imports = directory.resolve("imports");
        Files.createDirectories(imports);
        var builtIn = new BuiltInStructureAssetCatalog().requireByType(
                com.github.nankotsu029.landformcraft.model.StructureType.PATH);
        Path schematic = imports.resolve("asset.schem");
        new SpongeSchematicWriter().writeAsset(schematic, builtIn, () -> false);
        Files.writeString(imports.resolve("metadata.json"), metadata("path-v1"));
        CustomAssetService service = new CustomAssetService(
                imports, directory.resolve("assets"), directory.resolve("exports"), Clock.systemUTC());

        assertThrows(LandformException.class,
                () -> service.validate("asset.schem", "metadata.json"));
        assertThrows(LandformException.class,
                () -> service.validate(schematic.toAbsolutePath().toString(), "metadata.json"));
        Path link = imports.resolve("link.schem");
        Files.createSymbolicLink(link, schematic);
        assertThrows(LandformException.class,
                () -> service.validate("link.schem", "metadata.json"));
    }

    private static String metadata(String id) {
        return """
                {
                  "schemaVersion": 1,
                  "assetId": "%s",
                  "type": "PATH",
                  "minecraftVersion": "1.21.11",
                  "anchorX": 0,
                  "anchorY": 0,
                  "anchorZ": 0,
                  "allowedRotations": ["NONE", "CLOCKWISE_90"],
                  "placementKind": "DRY_FOLLOWING",
                  "maximumSlope": 4,
                  "waterAllowed": false
                }
                """.formatted(id);
    }
}
