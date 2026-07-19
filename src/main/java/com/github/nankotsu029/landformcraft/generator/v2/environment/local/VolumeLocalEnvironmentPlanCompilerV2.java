package com.github.nankotsu029.landformcraft.generator.v2.environment.local;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.HostVolumeBinding;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.HostVolumeKind;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.LightExposureClass;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.LocalMaterialClass;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.SparsePlacementKind;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.SparsePlacementRule;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.SurfaceProfileRule;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.VolumeSurfaceClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compiles the frozen V2-5-14 post-volume local environment plan bound to host volume geometry
 * checksums and the regional material profile.
 */
public final class VolumeLocalEnvironmentPlanCompilerV2 {
    public static final String MODULE_ID = "generate.volume-local-environment";
    public static final String LIFECYCLE = "SUPPORTED";

    private static final String ZERO = "0".repeat(64);

    private VolumeLocalEnvironmentPlanCompilerV2() {
    }

    public static VolumeLocalEnvironmentPlanV2 compile(
            String featureId,
            List<HostVolumeBinding> hostBindings,
            String sourceMaterialProfilePlanChecksum,
            VolumeLocalEnvironmentPlanV2.Kernel kernel
    ) {
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(hostBindings, "hostBindings");
        Objects.requireNonNull(sourceMaterialProfilePlanChecksum, "sourceMaterialProfilePlanChecksum");
        Objects.requireNonNull(kernel, "kernel");
        if (!VolumeLocalEnvironmentPlanV2.Kernel.VERSION.equals(kernel.kernelVersion())) {
            throw new VolumeLocalEnvironmentExceptionV2(
                    VolumeLocalEnvironmentFailureCodeV2.UNKNOWN_KERNEL,
                    "unsupported volume-local-environment kernel");
        }
        if (hostBindings.isEmpty()
                || hostBindings.size() > VolumeLocalEnvironmentPlanV2.MAXIMUM_HOST_BINDINGS) {
            throw new VolumeLocalEnvironmentExceptionV2(
                    VolumeLocalEnvironmentFailureCodeV2.BUDGET_EXCEEDED,
                    "host binding count outside budget");
        }

        List<SurfaceProfileRule> profiles = standardProfiles(hostBindings, kernel);
        List<SparsePlacementRule> sparse = standardSparse(hostBindings, kernel);
        VolumeLocalEnvironmentPlanV2 draft = new VolumeLocalEnvironmentPlanV2(
                VolumeLocalEnvironmentPlanV2.VERSION,
                VolumeLocalEnvironmentPlanV2.LOCAL_ENVIRONMENT_CONTRACT_VERSION,
                featureId,
                kernel,
                hostBindings,
                VolumeLocalEnvironmentPlanV2.Catalog.standard(),
                profiles,
                sparse,
                new VolumeLocalEnvironmentPlanV2.MaterialProfileBinding(
                        VolumeLocalEnvironmentPlanV2.MaterialProfileBinding.VERSION,
                        sourceMaterialProfilePlanChecksum,
                        VolumeLocalEnvironmentPlanV2.MaterialProfileBinding.CONTRACT),
                new VolumeLocalEnvironmentPlanV2.ResourceBudget(
                        VolumeLocalEnvironmentPlanV2.ResourceBudget.VERSION,
                        8192L,
                        VolumeLocalEnvironmentPlanV2.MAX_CANONICAL_BYTES,
                        256L * 1024L,
                        kernel.maximumDescriptorSamples(),
                        kernel.maximumSparsePlacementsPerWindow(),
                        hostBindings.size(),
                        profiles.size(),
                        sparse.size()),
                ZERO);
        return new LandformV2DataCodec().sealVolumeLocalEnvironmentPlan(draft);
    }

    static List<SurfaceProfileRule> standardProfiles(
            List<HostVolumeBinding> hosts,
            VolumeLocalEnvironmentPlanV2.Kernel kernel
    ) {
        List<SurfaceProfileRule> rules = new ArrayList<>();
        int order = 0;
        for (HostVolumeBinding host : hosts.stream()
                .sorted((a, b) -> a.featureId().compareTo(b.featureId())).toList()) {
            switch (host.hostKind()) {
                case LUSH_CAVE -> {
                    rules.add(profile(order++, "lush.floor", host.hostKind(), VolumeSurfaceClass.FLOOR,
                            LocalMaterialClass.LUSH_MOSS_FLOOR, true, false,
                            kernel.minimumWetnessMillionths(), 0, 0, LightExposureClass.SHADED));
                    rules.add(profile(order++, "lush.wall", host.hostKind(), VolumeSurfaceClass.WALL,
                            LocalMaterialClass.CAVE_WET_ROCK, true, false,
                            kernel.minimumWetnessMillionths(), kernel.minimumDripMillionths(),
                            kernel.minimumShadeMillionths(), LightExposureClass.DEEP));
                    rules.add(profile(order++, "lush.ceiling", host.hostKind(), VolumeSurfaceClass.CEILING,
                            LocalMaterialClass.LUSH_ROOT_CEILING, true, true,
                            kernel.minimumWetnessMillionths(), kernel.minimumDripMillionths(),
                            kernel.minimumShadeMillionths(), LightExposureClass.DEEP));
                    rules.add(profile(order++, "lush.pool", host.hostKind(), VolumeSurfaceClass.SUBMERGED,
                            LocalMaterialClass.LUSH_POOL_MARGIN, true, false,
                            kernel.minimumWetnessMillionths(), 0, 0, LightExposureClass.SHADED));
                }
                case SEA_CAVE -> rules.add(profile(order++, "sea.wet", host.hostKind(),
                        VolumeSurfaceClass.SUBMERGED, LocalMaterialClass.SEA_CAVE_WET_ROCK,
                        true, false, kernel.minimumWetnessMillionths(), 0, 0, LightExposureClass.OPEN));
                case CAVE_NETWORK -> {
                    rules.add(profile(order++, "cave.wet", host.hostKind(), VolumeSurfaceClass.FLOOR,
                            LocalMaterialClass.CAVE_WET_ROCK, true, false,
                            kernel.minimumWetnessMillionths(), 0, kernel.minimumShadeMillionths(),
                            LightExposureClass.DEEP));
                    rules.add(profile(order++, "cave.dry", host.hostKind(), VolumeSurfaceClass.WALL,
                            LocalMaterialClass.CAVE_DRY_ROCK, false, false,
                            0, 0, 0, LightExposureClass.OPEN));
                }
                case SKY_ISLAND_GROUP -> {
                    rules.add(profile(order++, "sky.top", host.hostKind(), VolumeSurfaceClass.EXTERIOR_TOP,
                            LocalMaterialClass.SKY_TOP, false, false, 0, 0, 0, LightExposureClass.OPEN));
                    rules.add(profile(order++, "sky.edge", host.hostKind(), VolumeSurfaceClass.EDGE,
                            LocalMaterialClass.SKY_EDGE, false, false, 0, 0, 0, LightExposureClass.OPEN));
                    rules.add(profile(order++, "sky.underside", host.hostKind(), VolumeSurfaceClass.UNDERSIDE,
                            LocalMaterialClass.SKY_UNDERSIDE, false, false, 0, 0,
                            kernel.minimumShadeMillionths(), LightExposureClass.SHADED));
                }
                case WATERFALL_VOLUME -> rules.add(profile(order++, "fall.wet", host.hostKind(),
                        VolumeSurfaceClass.WALL, LocalMaterialClass.WATERFALL_WET_ROCK,
                        true, false, kernel.minimumWetnessMillionths(),
                        kernel.minimumDripMillionths(), 0, LightExposureClass.OPEN));
            }
        }
        return List.copyOf(rules);
    }

    static List<SparsePlacementRule> standardSparse(
            List<HostVolumeBinding> hosts,
            VolumeLocalEnvironmentPlanV2.Kernel kernel
    ) {
        List<SparsePlacementRule> rules = new ArrayList<>();
        int order = 0;
        boolean hasLush = hosts.stream().anyMatch(h -> h.hostKind() == HostVolumeKind.LUSH_CAVE);
        if (hasLush) {
            rules.add(new SparsePlacementRule(
                    order++,
                    "place.moss.floor",
                    SparsePlacementKind.MOSS,
                    HostVolumeKind.LUSH_CAVE,
                    List.of(VolumeSurfaceClass.FLOOR, VolumeSurfaceClass.WALL),
                    true,
                    false,
                    kernel.minimumWetnessMillionths()));
            rules.add(new SparsePlacementRule(
                    order++,
                    "place.moss.ceiling",
                    SparsePlacementKind.MOSS,
                    HostVolumeKind.LUSH_CAVE,
                    List.of(VolumeSurfaceClass.CEILING),
                    true,
                    false,
                    kernel.minimumWetnessMillionths()));
            rules.add(new SparsePlacementRule(
                    order++,
                    "place.root.ceiling",
                    SparsePlacementKind.ROOT,
                    HostVolumeKind.LUSH_CAVE,
                    List.of(VolumeSurfaceClass.CEILING),
                    true,
                    true,
                    kernel.minimumWetnessMillionths()));
            rules.add(new SparsePlacementRule(
                    order,
                    "place.pool.edge",
                    SparsePlacementKind.POOL_EDGE,
                    HostVolumeKind.LUSH_CAVE,
                    List.of(VolumeSurfaceClass.SUBMERGED, VolumeSurfaceClass.FLOOR),
                    true,
                    false,
                    kernel.minimumWetnessMillionths()));
        }
        return List.copyOf(rules);
    }

    private static SurfaceProfileRule profile(
            int order,
            String id,
            HostVolumeKind host,
            VolumeSurfaceClass surface,
            LocalMaterialClass material,
            boolean wet,
            boolean support,
            int minWet,
            int minDrip,
            int minShade,
            LightExposureClass light
    ) {
        return new SurfaceProfileRule(
                order, id, host, surface, material, wet, support, minWet, minDrip, minShade, light);
    }
}
