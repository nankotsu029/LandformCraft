package com.github.nankotsu029.landformcraft.format.v2.field;

import com.fasterxml.jackson.core.JsonParseException;
import com.github.nankotsu029.landformcraft.model.v2.ConstraintFieldIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintFieldIndexCodecV2Test {
    @Test
    void manualBundleShapeIsSealedAndStrictlyVerified(@TempDir Path root) throws Exception {
        Fixture fixture = fixture(root, false, false);
        ConstraintFieldIndexCodecV2 codec = new ConstraintFieldIndexCodecV2();

        assertTrue(fixture.index().hasPendingCanonicalChecksum());
        String canonical = codec.canonical(fixture.index());
        ConstraintFieldIndexV2 decoded = codec.read(canonical, "constraint-index");
        assertFalse(decoded.hasPendingCanonicalChecksum());
        assertEquals(codec.checksum(fixture.index()), decoded.canonicalChecksum());
        assertEquals(canonical, codec.canonical(decoded));

        Path indexPath = root.resolve("fields/index.json");
        codec.write(indexPath, fixture.index());
        assertEquals(canonical, Files.readString(indexPath));
        assertEquals(decoded, codec.readAndVerify(
                indexPath, root, REQUEST_CHECKSUM, INTENT_CHECKSUM, () -> false));
        assertEquals(7, decoded.fields().size());
        assertEquals(3, decoded.bindings().size());
    }

    @Test
    void rejectsFutureUnknownDuplicateAndAnyIndexTampering(@TempDir Path root) throws Exception {
        Fixture fixture = fixture(root, false, false);
        ConstraintFieldIndexCodecV2 codec = new ConstraintFieldIndexCodecV2();
        String canonical = codec.canonical(fixture.index());

        assertThrows(StructuredDataValidationException.class, () -> codec.read(
                canonical.replace("\"indexVersion\":1", "\"indexVersion\":2"), "future-index"));
        assertThrows(StructuredDataValidationException.class, () -> codec.read(
                canonical.replaceFirst("\\{", "{\"unknown\":true,"), "unknown-field"));
        assertThrows(JsonParseException.class, () -> codec.read(
                canonical.replaceFirst("\\{", "{\"indexVersion\":1,"), "duplicate-version"));

        assertThrows(IOException.class, () -> codec.read(
                canonical.replace("manual-index-test", "manual-index-tampered"), "tampered-content"));
        assertThrows(IOException.class, () -> codec.read(
                canonical.replace(
                        "\"canonicalChecksum\":\"" + codec.checksum(fixture.index()) + "\"",
                        "\"canonicalChecksum\":\"" + "f".repeat(64) + "\""),
                "tampered-seal"));
    }

    @Test
    void expectedSourceChecksumsAndCancellationAreEnforced(@TempDir Path root) throws Exception {
        Fixture fixture = fixture(root, false, false);
        ConstraintFieldIndexCodecV2 codec = new ConstraintFieldIndexCodecV2();
        Path indexPath = root.resolve("fields/index.json");
        codec.write(indexPath, fixture.index());
        ConstraintFieldIndexV2 sealed = codec.read(indexPath);

        assertThrows(IOException.class, () -> codec.readAndVerify(
                indexPath, root, "0".repeat(64), INTENT_CHECKSUM, () -> false));
        assertThrows(IOException.class, () -> codec.verifyArtifacts(
                root, sealed, REQUEST_CHECKSUM, "0".repeat(64), () -> false));
        assertThrows(CancellationException.class, () -> codec.readAndVerify(
                indexPath, root, REQUEST_CHECKSUM, INTENT_CHECKSUM, () -> true));
        assertThrows(CancellationException.class, () -> codec.verifyArtifacts(
                root, sealed, () -> true));
    }

    @Test
    void rejectsInvalidRoleShapeOwnershipProvenanceAndLabels(@TempDir Path root) throws Exception {
        Fixture fixture = fixture(root, false, false);
        ConstraintFieldIndexV2.AppliedBinding land = binding(
                fixture.index(), TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK);
        ConstraintFieldIndexV2.AppliedBinding height = binding(
                fixture.index(), TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE);
        ConstraintFieldIndexV2.AppliedBinding zone = binding(
                fixture.index(), TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP);

        assertThrows(IllegalArgumentException.class, () -> replaceBinding(
                fixture.index(), land, new ConstraintFieldIndexV2.AppliedBinding(
                        land.bindingId(), land.sourceId(), land.role(), land.strength(), land.sampling(),
                        land.toleranceBlocks(), land.weightMillionths(),
                        "constraint:height-guide:sha256-" + semantic(fixture.index(), land.canonicalFieldId()),
                        land.canonicalFieldId(), land.fieldIds(), land.labels())));

        assertThrows(IllegalArgumentException.class, () -> replaceBinding(
                fixture.index(), height, new ConstraintFieldIndexV2.AppliedBinding(
                        height.bindingId(), height.sourceId(), height.role(), height.strength(), height.sampling(),
                        height.toleranceBlocks(), height.weightMillionths(), height.canonicalArtifactId(),
                        height.canonicalFieldId(), height.fieldIds().subList(0, 2), height.labels())));

        assertThrows(IllegalArgumentException.class, () -> new ConstraintFieldIndexV2(
                1, fixture.index().requestId(), REQUEST_CHECKSUM, INTENT_CHECKSUM,
                fixture.index().bindings(), append(fixture.index().fields(), cloneField(
                fixture.index().fields().getFirst(), "orphan.field", "fields/orphan.lfgrid", null, null))));

        ConstraintFieldIndexV2.AppliedBinding shared = new ConstraintFieldIndexV2.AppliedBinding(
                "land-shared", "constraint-source:land-shared", land.role(), land.strength(), land.sampling(),
                land.toleranceBlocks(), land.weightMillionths(), land.canonicalArtifactId(),
                land.canonicalFieldId(), land.fieldIds(), land.labels());
        assertThrows(IllegalArgumentException.class, () -> new ConstraintFieldIndexV2(
                1, fixture.index().requestId(), REQUEST_CHECKSUM, INTENT_CHECKSUM,
                append(fixture.index().bindings(), shared), fixture.index().fields()));

        FieldArtifactDescriptorV2 firstLand = fixture.index().fields().stream()
                .filter(field -> field.definition().semantic()
                        == FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER)
                .findFirst().orElseThrow();
        FieldArtifactDescriptorV2 badProvenance = cloneField(
                firstLand, firstLand.definition().fieldId(), firstLand.relativePath(),
                null, new FieldArtifactDescriptorV2.Provenance(
                        FieldArtifactDescriptorV2.SourceKind.CONSTRAINT_MAP,
                        "constraint-source:wrong", "9".repeat(64), "numeric-png", "1", "pixel-center-v1"));
        assertThrows(IllegalArgumentException.class,
                () -> replaceField(fixture.index(), firstLand, badProvenance));

        FieldArtifactDescriptorV2 badDimensions = cloneField(
                firstLand, firstLand.definition().fieldId(), firstLand.relativePath(),
                definition(firstLand.definition().fieldId(), firstLand.definition().semantic(),
                        FieldArtifactDescriptorV2.FieldValueType.U8,
                        FieldArtifactDescriptorV2.Sampling.NEAREST, 7, 8, 1_000_000L, false), null);
        assertThrows(IllegalArgumentException.class,
                () -> replaceField(fixture.index(), firstLand, badDimensions));

        assertThrows(IllegalArgumentException.class, () -> new ConstraintFieldIndexV2.AppliedBinding(
                land.bindingId(), land.sourceId(), land.role(), land.strength(), land.sampling(),
                land.toleranceBlocks(), land.weightMillionths(), land.canonicalArtifactId(),
                land.canonicalFieldId(), land.fieldIds(), List.of(
                new ConstraintFieldIndexV2.LabelEntry(0, 0, "water"),
                new ConstraintFieldIndexV2.LabelEntry(1, 2, "land"))));
        assertThrows(IllegalArgumentException.class, () -> new ConstraintFieldIndexV2.AppliedBinding(
                zone.bindingId(), zone.sourceId(), zone.role(), zone.strength(), zone.sampling(),
                zone.toleranceBlocks(), zone.weightMillionths(), zone.canonicalArtifactId(),
                zone.canonicalFieldId(), zone.fieldIds(), List.of(
                new ConstraintFieldIndexV2.LabelEntry(10, 1, "inland"),
                new ConstraintFieldIndexV2.LabelEntry(20, 3, "shore"))));
    }

    @Test
    void verifiesCategoricalDictionaryAndHardNoDataByScanningSidecars(@TempDir Path root)
            throws Exception {
        ConstraintFieldIndexCodecV2 codec = new ConstraintFieldIndexCodecV2();
        Fixture unknown = fixture(root.resolve("unknown"), true, false);
        assertThrows(IOException.class, () -> codec.verifyArtifacts(root.resolve("unknown"), unknown.index()));

        Fixture noData = fixture(root.resolve("no-data"), false, true);
        assertThrows(IOException.class, () -> codec.verifyArtifacts(root.resolve("no-data"), noData.index()));
    }

    @Test
    void rejectsMissingOrModifiedSidecar(@TempDir Path root) throws Exception {
        Fixture fixture = fixture(root, false, false);
        ConstraintFieldIndexCodecV2 codec = new ConstraintFieldIndexCodecV2();
        FieldArtifactDescriptorV2 field = fixture.index().fields().getFirst();
        Files.write(fieldPath(root, field), new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> codec.verifyArtifacts(root, fixture.index()));

        Fixture missing = fixture(root.resolve("missing"), false, false);
        Files.delete(fieldPath(root.resolve("missing"), missing.index().fields().getFirst()));
        assertThrows(IOException.class,
                () -> codec.verifyArtifacts(root.resolve("missing"), missing.index()));
    }

    private static final String REQUEST_CHECKSUM = "b".repeat(64);
    private static final String INTENT_CHECKSUM = "c".repeat(64);

    private static Fixture fixture(Path root, boolean unknownLandValue, boolean hardZoneNoData)
            throws Exception {
        Files.createDirectories(root);
        LfcGridWriterV1 writer = new LfcGridWriterV1();
        List<FieldArtifactDescriptorV2> fields = new ArrayList<>();

        var landProvenance = provenance("land");
        fields.add(write(writer, root, "fields/000-desired-land-water.lfgrid",
                definition("constraint.000.desired", FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER,
                        FieldArtifactDescriptorV2.FieldValueType.U8,
                        FieldArtifactDescriptorV2.Sampling.NEAREST, 8, 8, 1_000_000L, false),
                landProvenance, (x, z) -> unknownLandValue && x == 7 && z == 7 ? 2 : (x + z) % 2));
        fields.add(write(writer, root, "fields/000-actual-land-water.lfgrid",
                definition("constraint.000.actual", FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER,
                        FieldArtifactDescriptorV2.FieldValueType.U8,
                        FieldArtifactDescriptorV2.Sampling.NEAREST, 8, 8, 1_000_000L, false),
                landProvenance, (x, z) -> (x + z) % 2));
        fields.add(write(writer, root, "fields/000-residual-land-water.lfgrid",
                definition("constraint.000.residual", FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_LAND_WATER,
                        FieldArtifactDescriptorV2.FieldValueType.I32,
                        FieldArtifactDescriptorV2.Sampling.NEAREST, 8, 8, 1L, false),
                landProvenance, (x, z) -> 0));

        var heightProvenance = provenance("height");
        fields.add(write(writer, root, "fields/001-desired-height.lfgrid",
                definition("constraint.001.desired", FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT,
                        FieldArtifactDescriptorV2.FieldValueType.I32,
                        FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, 8, 8, 1L, false),
                heightProvenance, (x, z) -> 40_250_000 + x * 100_000 + z * 10_000));
        fields.add(write(writer, root, "fields/001-actual-height.lfgrid",
                definition("constraint.001.actual", FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_HEIGHT,
                        FieldArtifactDescriptorV2.FieldValueType.I32,
                        FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, 8, 8, 1L, false),
                heightProvenance, (x, z) -> 40_000_000));
        fields.add(write(writer, root, "fields/001-residual-height.lfgrid",
                definition("constraint.001.residual", FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_HEIGHT,
                        FieldArtifactDescriptorV2.FieldValueType.I32,
                        FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, 8, 8, 1L, false),
                heightProvenance, (x, z) -> 250_000 + x * 100_000 + z * 10_000));

        var zoneProvenance = provenance("zones");
        fields.add(write(writer, root, "fields/002-desired-zone-label-map.lfgrid",
                definition("constraint.002.desired", FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP,
                        FieldArtifactDescriptorV2.FieldValueType.U16,
                        FieldArtifactDescriptorV2.Sampling.NEAREST, 8, 8, 1_000_000L, hardZoneNoData),
                zoneProvenance, (x, z) -> hardZoneNoData && x == 7 && z == 7 ? 65_535 : (x < 4 ? 1 : 2)));

        FieldArtifactDescriptorV2 landDesired = field(
                fields, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER);
        FieldArtifactDescriptorV2 heightDesired = field(
                fields, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT);
        FieldArtifactDescriptorV2 zoneDesired = field(
                fields, FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP);
        List<ConstraintFieldIndexV2.AppliedBinding> bindings = List.of(
                new ConstraintFieldIndexV2.AppliedBinding(
                        "land-binding", "constraint-source:land",
                        TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK, TerrainIntentV2.Strength.HARD,
                        TerrainIntentV2.Sampling.NEAREST, 0, 0,
                        "constraint:land-water:sha256-" + landDesired.semanticChecksum(),
                        landDesired.definition().fieldId(), ids(fields, "constraint.000"),
                        List.of(new ConstraintFieldIndexV2.LabelEntry(0, 0, "water"),
                                new ConstraintFieldIndexV2.LabelEntry(1, 1, "land"))),
                new ConstraintFieldIndexV2.AppliedBinding(
                        "height-binding", "constraint-source:height",
                        TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE, TerrainIntentV2.Strength.SOFT,
                        TerrainIntentV2.Sampling.BILINEAR_FIXED, 2, 800_000,
                        "constraint:height-guide:sha256-" + heightDesired.semanticChecksum(),
                        heightDesired.definition().fieldId(), ids(fields, "constraint.001"), List.of()),
                new ConstraintFieldIndexV2.AppliedBinding(
                        "zone-binding", "constraint-source:zones",
                        TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP, TerrainIntentV2.Strength.HARD,
                        TerrainIntentV2.Sampling.NEAREST, 0, 0,
                        "constraint:zone-label-map:sha256-" + zoneDesired.semanticChecksum(),
                        zoneDesired.definition().fieldId(), ids(fields, "constraint.002"),
                        List.of(new ConstraintFieldIndexV2.LabelEntry(10, 1, "inland"),
                                new ConstraintFieldIndexV2.LabelEntry(20, 2, "shore"))));
        return new Fixture(new ConstraintFieldIndexV2(
                ConstraintFieldIndexV2.VERSION, "manual-index-test",
                REQUEST_CHECKSUM, INTENT_CHECKSUM, bindings, fields));
    }

    private static FieldArtifactDescriptorV2 write(
            LfcGridWriterV1 writer,
            Path root,
            String path,
            FieldArtifactDescriptorV2.Definition definition,
            FieldArtifactDescriptorV2.Provenance provenance,
            FieldValueSource values
    ) throws IOException {
        return writer.write(root, path, definition, provenance, values, () -> false);
    }

    private static FieldArtifactDescriptorV2.Definition definition(
            String id,
            FieldArtifactDescriptorV2.FieldSemantic semantic,
            FieldArtifactDescriptorV2.FieldValueType type,
            FieldArtifactDescriptorV2.Sampling sampling,
            int width,
            int length,
            long scale,
            boolean noData
    ) {
        int sentinel = noData ? switch (type) {
            case U8 -> 255;
            case U16 -> 65_535;
            case I32 -> Integer.MIN_VALUE;
        } : 0;
        return new FieldArtifactDescriptorV2.Definition(
                id, semantic, type, width, length,
                FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                sampling, scale, 0, noData, sentinel);
    }

    private static FieldArtifactDescriptorV2.Provenance provenance(String id) {
        String checksumDigit = switch (id) {
            case "land" -> "a";
            case "height" -> "b";
            case "zones" -> "c";
            default -> throw new IllegalArgumentException("unknown fixture provenance");
        };
        return new FieldArtifactDescriptorV2.Provenance(
                FieldArtifactDescriptorV2.SourceKind.CONSTRAINT_MAP,
                "constraint-source:" + id, checksumDigit.repeat(64),
                "numeric-png", "1", "pixel-center-v1");
    }

    private static FieldArtifactDescriptorV2 field(
            List<FieldArtifactDescriptorV2> fields,
            FieldArtifactDescriptorV2.FieldSemantic semantic
    ) {
        return fields.stream().filter(value -> value.definition().semantic() == semantic)
                .findFirst().orElseThrow();
    }

    private static List<String> ids(List<FieldArtifactDescriptorV2> fields, String prefix) {
        return fields.stream().map(field -> field.definition().fieldId())
                .filter(id -> id.startsWith(prefix)).toList();
    }

    private static ConstraintFieldIndexV2.AppliedBinding binding(
            ConstraintFieldIndexV2 index,
            TerrainIntentV2.ConstraintMapRole role
    ) {
        return index.bindings().stream().filter(value -> value.role() == role).findFirst().orElseThrow();
    }

    private static String semantic(ConstraintFieldIndexV2 index, String fieldId) {
        return index.fields().stream().filter(field -> field.definition().fieldId().equals(fieldId))
                .findFirst().orElseThrow().semanticChecksum();
    }

    private static ConstraintFieldIndexV2 replaceBinding(
            ConstraintFieldIndexV2 index,
            ConstraintFieldIndexV2.AppliedBinding oldValue,
            ConstraintFieldIndexV2.AppliedBinding newValue
    ) {
        return new ConstraintFieldIndexV2(
                index.indexVersion(), index.requestId(), index.sourceRequestChecksum(),
                index.sourceIntentChecksum(), index.bindings().stream()
                .map(value -> value.equals(oldValue) ? newValue : value).toList(), index.fields());
    }

    private static ConstraintFieldIndexV2 replaceField(
            ConstraintFieldIndexV2 index,
            FieldArtifactDescriptorV2 oldValue,
            FieldArtifactDescriptorV2 newValue
    ) {
        return new ConstraintFieldIndexV2(
                index.indexVersion(), index.requestId(), index.sourceRequestChecksum(),
                index.sourceIntentChecksum(), index.bindings(), index.fields().stream()
                .map(value -> value.equals(oldValue) ? newValue : value).toList());
    }

    private static FieldArtifactDescriptorV2 cloneField(
            FieldArtifactDescriptorV2 source,
            String fieldId,
            String path,
            FieldArtifactDescriptorV2.Definition replacementDefinition,
            FieldArtifactDescriptorV2.Provenance replacementProvenance
    ) {
        FieldArtifactDescriptorV2.Definition definition = replacementDefinition == null
                ? new FieldArtifactDescriptorV2.Definition(
                fieldId, source.definition().semantic(), source.definition().valueType(),
                source.definition().width(), source.definition().length(),
                source.definition().coordinateSpace(), source.definition().sampling(),
                source.definition().scaleMillionths(), source.definition().offsetMillionths(),
                source.definition().hasNoData(), source.definition().noDataRaw())
                : replacementDefinition;
        return new FieldArtifactDescriptorV2(
                path, definition, source.encodingVersion(), source.artifactChecksum(),
                source.semanticChecksum(), replacementProvenance == null
                ? source.provenance() : replacementProvenance);
    }

    private static <T> List<T> append(List<T> source, T value) {
        List<T> result = new ArrayList<>(source);
        result.add(value);
        return List.copyOf(result);
    }

    private static Path fieldPath(Path root, FieldArtifactDescriptorV2 field) {
        return root.resolve(field.relativePath());
    }

    private record Fixture(ConstraintFieldIndexV2 index) {
    }
}
