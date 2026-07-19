package com.github.nankotsu029.landformcraft.generator.v2.environment.local;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.HostVolumeBinding;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.HostVolumeKind;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.LocalMaterialClass;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.SparsePlacementKind;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.VolumeSurfaceClass;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeLocalEnvironmentResolverV2Test {
    private static final String MATERIAL =
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static final String GEOM =
            "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";

    @Test
    void classifiesLushSeaCaveSkyAndWaterfallSurfaces() throws Exception {
        VolumeLocalEnvironmentPlanV2 plan = positive();
        VolumeLocalEnvironmentResolverV2 resolver = new VolumeLocalEnvironmentResolverV2(plan);
        assertEquals(VolumeLocalEnvironmentPlanCompilerV2.LIFECYCLE, "SUPPORTED");

        var lushFloor = resolver.resolve(VolumeLocalEnvironmentResolverV2.SurfaceSample.of(
                0, 10, 0, HostVolumeKind.LUSH_CAVE, VolumeSurfaceClass.FLOOR,
                800_000, 0, 400_000, true));
        assertEquals(LocalMaterialClass.LUSH_MOSS_FLOOR.compactCode(), lushFloor.materialClassCode());
        assertEquals(SparsePlacementKind.MOSS, lushFloor.placement().orElseThrow());

        var lushCeiling = resolver.resolve(VolumeLocalEnvironmentResolverV2.SurfaceSample.of(
                0, 20, 0, HostVolumeKind.LUSH_CAVE, VolumeSurfaceClass.CEILING,
                800_000, 500_000, 800_000, true));
        assertEquals(LocalMaterialClass.LUSH_ROOT_CEILING.compactCode(), lushCeiling.materialClassCode());
        assertEquals(SparsePlacementKind.MOSS, lushCeiling.placement().orElseThrow());

        var sea = resolver.resolve(VolumeLocalEnvironmentResolverV2.SurfaceSample.of(
                1, 5, 1, HostVolumeKind.SEA_CAVE, VolumeSurfaceClass.SUBMERGED,
                900_000, 0, 0, false));
        assertEquals(LocalMaterialClass.SEA_CAVE_WET_ROCK.compactCode(), sea.materialClassCode());

        var skyTop = resolver.resolve(VolumeLocalEnvironmentResolverV2.SurfaceSample.of(
                2, 40, 2, HostVolumeKind.SKY_ISLAND_GROUP, VolumeSurfaceClass.EXTERIOR_TOP,
                0, 0, 0, false));
        assertEquals(LocalMaterialClass.SKY_TOP.compactCode(), skyTop.materialClassCode());

        var skyUnder = resolver.resolve(VolumeLocalEnvironmentResolverV2.SurfaceSample.of(
                2, 30, 2, HostVolumeKind.SKY_ISLAND_GROUP, VolumeSurfaceClass.UNDERSIDE,
                0, 0, 500_000, false));
        assertEquals(LocalMaterialClass.SKY_UNDERSIDE.compactCode(), skyUnder.materialClassCode());

        var fall = resolver.resolve(VolumeLocalEnvironmentResolverV2.SurfaceSample.of(
                3, 15, 3, HostVolumeKind.WATERFALL_VOLUME, VolumeSurfaceClass.WALL,
                700_000, 400_000, 0, false));
        assertEquals(LocalMaterialClass.WATERFALL_WET_ROCK.compactCode(), fall.materialClassCode());

        Path example = Path.of("examples/v2/environment/volume-local-environment-plan-v2.json");
        assertEquals(plan.canonicalChecksum(),
                new LandformV2DataCodec().readVolumeLocalEnvironmentPlan(example).canonicalChecksum());
        assertTrue(Files.size(example) > 0);
    }

    @Test
    void rejectsMossOnDryCeilingRootWithoutSupportAndWrongUnderside() {
        VolumeLocalEnvironmentPlanV2 plan = positive();
        VolumeLocalEnvironmentResolverV2 resolver = new VolumeLocalEnvironmentResolverV2(plan);

        VolumeLocalEnvironmentExceptionV2 dryMoss = assertThrows(VolumeLocalEnvironmentExceptionV2.class,
                () -> resolver.resolve(new VolumeLocalEnvironmentResolverV2.SurfaceSample(
                        0, 20, 0, HostVolumeKind.LUSH_CAVE, VolumeSurfaceClass.CEILING,
                        100_000, 0, 800_000, true,
                        null, SparsePlacementKind.MOSS)));
        assertEquals(VolumeLocalEnvironmentFailureCodeV2.MOSS_ON_DRY_CEILING, dryMoss.failureCode());

        VolumeLocalEnvironmentExceptionV2 root = assertThrows(VolumeLocalEnvironmentExceptionV2.class,
                () -> resolver.resolve(new VolumeLocalEnvironmentResolverV2.SurfaceSample(
                        0, 20, 0, HostVolumeKind.LUSH_CAVE, VolumeSurfaceClass.CEILING,
                        800_000, 500_000, 800_000, false,
                        null, SparsePlacementKind.ROOT)));
        assertEquals(VolumeLocalEnvironmentFailureCodeV2.ROOT_WITHOUT_SUPPORT, root.failureCode());

        VolumeLocalEnvironmentExceptionV2 underside = assertThrows(VolumeLocalEnvironmentExceptionV2.class,
                () -> resolver.resolve(new VolumeLocalEnvironmentResolverV2.SurfaceSample(
                        2, 40, 2, HostVolumeKind.SKY_ISLAND_GROUP, VolumeSurfaceClass.EXTERIOR_TOP,
                        0, 0, 0, false,
                        LocalMaterialClass.SKY_UNDERSIDE, null)));
        assertEquals(VolumeLocalEnvironmentFailureCodeV2.WRONG_UNDERSIDE, underside.failureCode());

        VolumeLocalEnvironmentExceptionV2 unknown = assertThrows(VolumeLocalEnvironmentExceptionV2.class,
                () -> resolver.resolve(VolumeLocalEnvironmentResolverV2.SurfaceSample.of(
                        0, 0, 0, HostVolumeKind.CAVE_NETWORK, VolumeSurfaceClass.UNDERSIDE,
                        0, 0, 0, false)));
        assertEquals(VolumeLocalEnvironmentFailureCodeV2.UNKNOWN_PROFILE, unknown.failureCode());

        VolumeLocalEnvironmentExceptionV2 mismatch = assertThrows(VolumeLocalEnvironmentExceptionV2.class,
                () -> resolver.requireHostGeometry(
                        HostVolumeKind.LUSH_CAVE,
                        "lush.host",
                        "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"));
        assertEquals(VolumeLocalEnvironmentFailureCodeV2.HOST_CHECKSUM_MISMATCH, mismatch.failureCode());
    }

    @Test
    void compileAndMetricsAreOrderThreadLocaleStable() throws Exception {
        VolumeLocalEnvironmentPlanV2 expected = positive();
        List<VolumeLocalEnvironmentResolverV2.SurfaceSample> samples = List.of(
                VolumeLocalEnvironmentResolverV2.SurfaceSample.of(
                        0, 10, 0, HostVolumeKind.LUSH_CAVE, VolumeSurfaceClass.FLOOR,
                        800_000, 0, 400_000, true),
                VolumeLocalEnvironmentResolverV2.SurfaceSample.of(
                        1, 5, 1, HostVolumeKind.SEA_CAVE, VolumeSurfaceClass.SUBMERGED,
                        900_000, 0, 0, false),
                VolumeLocalEnvironmentResolverV2.SurfaceSample.of(
                        2, 40, 2, HostVolumeKind.SKY_ISLAND_GROUP, VolumeSurfaceClass.EXTERIOR_TOP,
                        0, 0, 0, false));
        String checksum = new VolumeLocalEnvironmentResolverV2(expected).metricChecksum(samples);

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.KOREA);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            VolumeLocalEnvironmentPlanV2 again = positive();
            assertEquals(expected.canonicalChecksum(), again.canonicalChecksum());
            assertEquals(checksum, new VolumeLocalEnvironmentResolverV2(again).metricChecksum(samples));
            try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
                assertEquals(checksum, one.submit(() ->
                        new VolumeLocalEnvironmentResolverV2(positive()).metricChecksum(samples)).get());
                assertEquals(checksum, four.submit(() ->
                        new VolumeLocalEnvironmentResolverV2(positive()).metricChecksum(samples)).get());
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private static VolumeLocalEnvironmentPlanV2 positive() {
        return VolumeLocalEnvironmentPlanCompilerV2.compile(
                "local.fixture-env",
                List.of(
                        new HostVolumeBinding(HostVolumeKind.LUSH_CAVE, "lush.host", GEOM),
                        new HostVolumeBinding(HostVolumeKind.SEA_CAVE, "sea.host", GEOM),
                        new HostVolumeBinding(HostVolumeKind.SKY_ISLAND_GROUP, "sky.host", GEOM),
                        new HostVolumeBinding(HostVolumeKind.WATERFALL_VOLUME, "fall.host", GEOM),
                        new HostVolumeBinding(HostVolumeKind.CAVE_NETWORK, "cave.host", GEOM)),
                MATERIAL,
                VolumeLocalEnvironmentPlanV2.Kernel.standard());
    }
}
