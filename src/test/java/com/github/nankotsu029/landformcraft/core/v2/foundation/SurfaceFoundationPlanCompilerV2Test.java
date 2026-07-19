package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceFoundationPlanCompilerV2Test {
    private final SurfaceFoundationPlanCompilerV2 compiler = new SurfaceFoundationPlanCompilerV2();
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void emptyMinimalPlanIsStrictRoundTrip(@TempDir Path temp) throws Exception {
        SurfaceFoundationPlanV2 plan = compiler.compileEmpty(64, 48, 827_413L);
        assertTrue(plan.owners().isEmpty());
        assertTrue(plan.interactions().isEmpty());
        assertEquals(5, plan.fields().size());
        assertEquals("small", plan.scaleClassId());

        Path file = temp.resolve("surface-foundation-plan-v2.json");
        codec.writeSurfaceFoundationPlan(file, plan);
        SurfaceFoundationPlanV2 read = codec.readSurfaceFoundationPlan(file);
        assertEquals(plan, read);
        assertEquals(codec.canonicalSurfaceFoundationPlan(plan), Files.readString(file));
    }

    @Test
    void exampleFixtureLoadsStrictly() throws Exception {
        SurfaceFoundationPlanV2 plan = codec.readSurfaceFoundationPlan(
                Path.of("examples/v2/foundation/surface-foundation-plan-v2.json"));
        assertEquals(SurfaceFoundationPlanV2.VERSION, plan.planVersion());
        assertEquals(SurfaceFoundationPlanV2.FIELD_CONTRACT_VERSION, plan.fieldContractVersion());
        assertTrue(plan.owners().isEmpty());
        assertEquals(5, plan.fields().size());
    }

    @Test
    void twoSyntheticOwnersWholeAndTileMergeMatch() {
        SurfaceFoundationPlanV2 plan = compiler.compile(
                64, 48, 42L,
                List.of(
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                "owner-alpha", 1, 10, 0,
                                SurfaceFoundationPlanV2.SurfaceClassCode.PLAIN),
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                "owner-beta", 2, 20, 1,
                                SurfaceFoundationPlanV2.SurfaceClassCode.HILL)),
                List.of(new SurfaceFoundationPlanCompilerV2.InteractionSpec(
                        "alpha-beta-blend", "owner-alpha", "owner-beta", 4)));

        SurfaceFoundationMergeCompilerV2 merge = new SurfaceFoundationMergeCompilerV2(plan, List.of(
                rectLayer(plan.owners().get(0), 0, 0, 40, 48, 1_000_000),
                rectLayer(plan.owners().get(1), 24, 0, 40, 48, 2_000_000)));

        Map<SurfaceFoundationMergeCompilerV2.CompositionField, String> whole =
                merge.wholeFieldChecksums();
        TilePlanV2 tiles = TilePlanV2.of(64, 48, ScaleProfileV2.defaults(ScaleClassV2.SMALL));
        Map<SurfaceFoundationMergeCompilerV2.CompositionField, String> tiled =
                merge.tiledFieldChecksums(tiles);
        assertEquals(whole, tiled);

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(whole, merge.wholeFieldChecksums());
            assertEquals(tiled, merge.tiledFieldChecksums(tiles));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void reverseTileOrderAndThreadCountDoNotChangeChecksum() throws Exception {
        SurfaceFoundationPlanV2 plan = compiler.compile(
                64, 48, 99L,
                List.of(
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                "owner-alpha", 1, 10, 0,
                                SurfaceFoundationPlanV2.SurfaceClassCode.PLAIN),
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                "owner-beta", 2, 30, 0,
                                SurfaceFoundationPlanV2.SurfaceClassCode.VALLEY)),
                List.of(new SurfaceFoundationPlanCompilerV2.InteractionSpec(
                        "overlap", "owner-alpha", "owner-beta", 2)));
        SurfaceFoundationMergeCompilerV2 merge = new SurfaceFoundationMergeCompilerV2(plan, List.of(
                rectLayer(plan.owners().get(0), 0, 0, 64, 48, 500_000),
                rectLayer(plan.owners().get(1), 16, 0, 48, 48, 750_000)));
        Map<SurfaceFoundationMergeCompilerV2.CompositionField, String> expected =
                merge.wholeFieldChecksums();

        TilePlanV2 tiles = TilePlanV2.of(64, 48, ScaleProfileV2.defaults(ScaleClassV2.SMALL));
        assertEquals(expected, merge.tiledFieldChecksums(tiles));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Future<Map<SurfaceFoundationMergeCompilerV2.CompositionField, String>> future =
                    pool.submit(merge::wholeFieldChecksums);
            assertEquals(expected, future.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectsOwnerlessCellUndeclaredOverlapTieAndOutOfRangeBand() {
        SurfaceFoundationPlanV2 covered = compiler.compile(
                32, 32, 7L,
                List.of(new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                        "only-owner", 1, 0, 0, SurfaceFoundationPlanV2.SurfaceClassCode.PLAIN)),
                List.of());
        SurfaceFoundationMergeCompilerV2 sparse = new SurfaceFoundationMergeCompilerV2(
                covered, List.of(rectLayer(covered.owners().getFirst(), 0, 0, 16, 16, 100)));
        SurfaceFoundationExceptionV2 ownerless = assertThrows(
                SurfaceFoundationExceptionV2.class, () -> sparse.sampleAt(20, 20));
        assertEquals(SurfaceFoundationFailureCodeV2.OWNERLESS_CELL, ownerless.failureCode());

        SurfaceFoundationPlanV2 noInteraction = compiler.compile(
                32, 32, 8L,
                List.of(
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                "a", 1, 10, 0, SurfaceFoundationPlanV2.SurfaceClassCode.PLAIN),
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                "b", 2, 20, 0, SurfaceFoundationPlanV2.SurfaceClassCode.HILL)),
                List.of());
        SurfaceFoundationMergeCompilerV2 overlap = new SurfaceFoundationMergeCompilerV2(
                noInteraction, List.of(
                rectLayer(noInteraction.owners().get(0), 0, 0, 20, 32, 100),
                rectLayer(noInteraction.owners().get(1), 10, 0, 20, 32, 200)));
        SurfaceFoundationExceptionV2 undeclared = assertThrows(
                SurfaceFoundationExceptionV2.class, () -> overlap.sampleAt(12, 4));
        assertEquals(SurfaceFoundationFailureCodeV2.UNDECLARED_OVERLAP, undeclared.failureCode());

        SurfaceFoundationPlanV2 tiedPlan = compiler.compile(
                32, 32, 9L,
                List.of(
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                "a", 1, 10, 0, SurfaceFoundationPlanV2.SurfaceClassCode.PLAIN),
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                "b", 2, 10, 0, SurfaceFoundationPlanV2.SurfaceClassCode.HILL)),
                List.of(new SurfaceFoundationPlanCompilerV2.InteractionSpec(
                        "tie", "a", "b", 1)));
        SurfaceFoundationMergeCompilerV2 tied = new SurfaceFoundationMergeCompilerV2(
                tiedPlan, List.of(
                rectLayer(tiedPlan.owners().get(0), 0, 0, 32, 32, 100),
                rectLayer(tiedPlan.owners().get(1), 0, 0, 32, 32, 200)));
        SurfaceFoundationExceptionV2 tie = assertThrows(
                SurfaceFoundationExceptionV2.class, () -> tied.sampleAt(1, 1));
        assertEquals(SurfaceFoundationFailureCodeV2.OWNER_TIE, tie.failureCode());

        SurfaceFoundationExceptionV2 band = assertThrows(
                SurfaceFoundationExceptionV2.class,
                () -> compiler.compile(
                        32, 32, 1L,
                        List.of(
                                new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                        "a", 1, 1, 0, SurfaceFoundationPlanV2.SurfaceClassCode.PLAIN),
                                new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                        "b", 2, 2, 0, SurfaceFoundationPlanV2.SurfaceClassCode.HILL)),
                        List.of(new SurfaceFoundationPlanCompilerV2.InteractionSpec(
                                "wide", "a", "b", 33))));
        assertEquals(SurfaceFoundationFailureCodeV2.TRANSITION_OUT_OF_RANGE, band.failureCode());
    }

    @Test
    void rejectsFutureVersionAndChecksumTamper(@TempDir Path temp) throws Exception {
        SurfaceFoundationPlanV2 plan = compiler.compileEmpty(32, 32, 1L);
        Path file = temp.resolve("plan.json");
        codec.writeSurfaceFoundationPlan(file, plan);
        String raw = Files.readString(file).replace("\"planVersion\":1", "\"planVersion\":2");
        assertThrows(Exception.class, () -> codec.readSurfaceFoundationPlan(raw, "tampered"));

        String checksumTamper = Files.readString(file).replace(
                plan.canonicalChecksum(),
                "a".repeat(64));
        assertThrows(Exception.class,
                () -> codec.readSurfaceFoundationPlan(checksumTamper, "checksum"));
    }

    @Test
    void rejectsDuplicateOwnerSeedCollisionViaDuplicateOwnerId() {
        assertThrows(SurfaceFoundationExceptionV2.class, () -> compiler.compile(
                16, 16, 1L,
                List.of(
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                "same", 1, 1, 0, SurfaceFoundationPlanV2.SurfaceClassCode.PLAIN),
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                "same", 2, 2, 0, SurfaceFoundationPlanV2.SurfaceClassCode.HILL)),
                List.of()));
    }

    @Test
    void scaleAdmissionIsRequiredForOversizedProfile() {
        ScaleProfileV2 smallOnly = ScaleProfileV2.defaults(ScaleClassV2.SMALL);
        SurfaceFoundationExceptionV2 failure = assertThrows(
                SurfaceFoundationExceptionV2.class,
                () -> compiler.compile(600, 600, 1L, List.of(), List.of(), smallOnly));
        assertEquals(SurfaceFoundationFailureCodeV2.SCALE_ADMISSION_REJECTED, failure.failureCode());
    }

    private static SurfaceFoundationMergeCompilerV2.OwnerLayer rectLayer(
            SurfaceFoundationPlanV2.OwnerDescriptor owner,
            int minX,
            int minZ,
            int width,
            int length,
            int elevation
    ) {
        return new SurfaceFoundationMergeCompilerV2.OwnerLayer(
                owner,
                packed -> {
                    long value = packed;
                    int x = (int) value;
                    int z = (int) (value >>> 32);
                    return x >= minX && z >= minZ && x < minX + width && z < minZ + length;
                },
                (x, z) -> elevation);
    }
}
