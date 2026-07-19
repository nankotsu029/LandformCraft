package com.github.nankotsu029.landformcraft.generator.v2.environment.local;

import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.HostVolumeBinding;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.HostVolumeKind;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.LightExposureClass;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.LocalMaterialClass;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.SparsePlacementKind;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.SparsePlacementRule;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.SurfaceProfileRule;
import com.github.nankotsu029.landformcraft.model.v2.environment.local.VolumeLocalEnvironmentPlanV2.VolumeSurfaceClass;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SUPPORTED (offline) post-volume local environment resolver. Classifies host surfaces, resolves
 * wetness／drip／shade gated material profiles, and emits sparse moss／root／pool placements.
 */
public final class VolumeLocalEnvironmentResolverV2 {
    public static final String GENERATOR_VERSION = "volume-local-environment-resolver-v1";
    public static final int NO_MATERIAL = 0;

    private final VolumeLocalEnvironmentPlanV2 plan;

    public VolumeLocalEnvironmentResolverV2(VolumeLocalEnvironmentPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        if (!VolumeLocalEnvironmentPlanV2.Kernel.VERSION.equals(plan.kernel().kernelVersion())) {
            throw new VolumeLocalEnvironmentExceptionV2(
                    VolumeLocalEnvironmentFailureCodeV2.UNKNOWN_KERNEL,
                    "unsupported volume-local-environment kernel");
        }
    }

    public VolumeLocalEnvironmentPlanV2 plan() {
        return plan;
    }

    public void requireHostGeometry(HostVolumeKind hostKind, String featureId, String geometryChecksum) {
        Objects.requireNonNull(hostKind, "hostKind");
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        HostVolumeBinding binding = plan.hostBindings().stream()
                .filter(entry -> entry.hostKind() == hostKind && entry.featureId().equals(featureId))
                .findFirst()
                .orElseThrow(() -> new VolumeLocalEnvironmentExceptionV2(
                        VolumeLocalEnvironmentFailureCodeV2.BINDING_MISMATCH,
                        "missing host binding for " + featureId));
        if (!binding.sourceGeometryChecksum().equals(geometryChecksum)) {
            throw new VolumeLocalEnvironmentExceptionV2(
                    VolumeLocalEnvironmentFailureCodeV2.HOST_CHECKSUM_MISMATCH,
                    "host geometry checksum mismatch for " + featureId);
        }
    }

    public SurfaceDecision resolve(SurfaceSample sample) {
        SurfaceSample validated = requireSample(sample);
        rejectCorruptClaims(validated);
        SurfaceProfileRule profile = findProfile(validated.hostKind(), validated.surfaceClass())
                .orElseThrow(() -> new VolumeLocalEnvironmentExceptionV2(
                        VolumeLocalEnvironmentFailureCodeV2.UNKNOWN_PROFILE,
                        "no surface profile for " + validated.hostKind() + "/" + validated.surfaceClass()));
        if (!thresholdsPass(profile, validated)) {
            if (validated.hostKind() == HostVolumeKind.SKY_ISLAND_GROUP
                    && validated.surfaceClass() == VolumeSurfaceClass.UNDERSIDE
                    && validated.claimedMaterial() == LocalMaterialClass.SKY_TOP) {
                throw new VolumeLocalEnvironmentExceptionV2(
                        VolumeLocalEnvironmentFailureCodeV2.WRONG_UNDERSIDE,
                        "sky underside claimed top profile");
            }
            return new SurfaceDecision(
                    validated.surfaceClass(),
                    LightExposureClass.OPEN,
                    NO_MATERIAL,
                    Optional.empty(),
                    false);
        }
        Optional<SparsePlacementKind> placement = resolvePlacement(validated);
        return new SurfaceDecision(
                validated.surfaceClass(),
                classifyLight(validated),
                profile.materialClass().compactCode(),
                placement,
                true);
    }

    public Metrics validateWindow(List<SurfaceSample> samples) {
        Objects.requireNonNull(samples, "samples");
        if (samples.size() > plan.budget().maximumDescriptorSamples()) {
            throw new VolumeLocalEnvironmentExceptionV2(
                    VolumeLocalEnvironmentFailureCodeV2.BUDGET_EXCEEDED,
                    "descriptor sample budget exceeded");
        }
        long classified = 0L;
        long habitatReady = 0L;
        long placements = 0L;
        List<SurfaceSample> ordered = new ArrayList<>(samples);
        ordered.sort(Comparator
                .comparingInt(SurfaceSample::x)
                .thenComparingInt(SurfaceSample::y)
                .thenComparingInt(SurfaceSample::z)
                .thenComparing(s -> s.hostKind().name())
                .thenComparing(s -> s.surfaceClass().name()));
        for (SurfaceSample sample : ordered) {
            SurfaceDecision decision = resolve(sample);
            if (decision.matched()) {
                classified++;
                if (decision.materialClassCode() != NO_MATERIAL) {
                    habitatReady++;
                }
                if (decision.placement().isPresent()) {
                    placements++;
                    if (placements > plan.budget().maximumSparsePlacementsPerWindow()) {
                        throw new VolumeLocalEnvironmentExceptionV2(
                                VolumeLocalEnvironmentFailureCodeV2.BUDGET_EXCEEDED,
                                "sparse placement budget exceeded");
                    }
                }
            }
        }
        return new Metrics(
                GENERATOR_VERSION,
                classified,
                habitatReady,
                placements,
                samples.size(),
                plan.canonicalChecksum());
    }

    public String metricChecksum(List<SurfaceSample> samples) {
        Metrics metrics = validateWindow(samples);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(GENERATOR_VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update(plan.canonicalChecksum().getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.classifiedSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.habitatReadySamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.placementCount()).getBytes(StandardCharsets.UTF_8));
            List<SurfaceSample> ordered = new ArrayList<>(samples);
            ordered.sort(Comparator
                    .comparingInt(SurfaceSample::x)
                    .thenComparingInt(SurfaceSample::y)
                    .thenComparingInt(SurfaceSample::z));
            for (SurfaceSample sample : ordered) {
                SurfaceDecision decision = resolve(sample);
                digest.update(Integer.toString(decision.materialClassCode()).getBytes(StandardCharsets.UTF_8));
                digest.update(decision.placement().map(Enum::name).orElse("-")
                        .getBytes(StandardCharsets.UTF_8));
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private void rejectCorruptClaims(SurfaceSample sample) {
        if (sample.claimedMaterial() == LocalMaterialClass.SKY_UNDERSIDE
                && sample.surfaceClass() == VolumeSurfaceClass.EXTERIOR_TOP
                && sample.hostKind() == HostVolumeKind.SKY_ISLAND_GROUP) {
            throw new VolumeLocalEnvironmentExceptionV2(
                    VolumeLocalEnvironmentFailureCodeV2.WRONG_UNDERSIDE,
                    "underside material claimed on sky top");
        }
        if (sample.claimedPlacement() == SparsePlacementKind.MOSS
                && sample.surfaceClass() == VolumeSurfaceClass.CEILING
                && sample.wetnessMillionths() < plan.kernel().minimumWetnessMillionths()) {
            throw new VolumeLocalEnvironmentExceptionV2(
                    VolumeLocalEnvironmentFailureCodeV2.MOSS_ON_DRY_CEILING,
                    "moss claimed on dry ceiling");
        }
        if (sample.claimedPlacement() == SparsePlacementKind.ROOT && !sample.supportPresent()) {
            throw new VolumeLocalEnvironmentExceptionV2(
                    VolumeLocalEnvironmentFailureCodeV2.ROOT_WITHOUT_SUPPORT,
                    "root claimed without support");
        }
        try {
            VolumeSurfaceClass.valueOf(sample.surfaceClass().name());
        } catch (Exception exception) {
            throw new VolumeLocalEnvironmentExceptionV2(
                    VolumeLocalEnvironmentFailureCodeV2.UNKNOWN_SURFACE,
                    "unknown surface class");
        }
    }

    private Optional<SurfaceProfileRule> findProfile(HostVolumeKind host, VolumeSurfaceClass surface) {
        return plan.surfaceProfiles().stream()
                .filter(rule -> rule.hostKind() == host && rule.surfaceClass() == surface)
                .findFirst();
    }

    private boolean thresholdsPass(SurfaceProfileRule profile, SurfaceSample sample) {
        if (profile.requiresWetness()
                && sample.wetnessMillionths() < profile.minimumWetnessMillionths()) {
            return false;
        }
        if (sample.dripMillionths() < profile.minimumDripMillionths()) {
            return false;
        }
        if (sample.shadeMillionths() < profile.minimumShadeMillionths()) {
            return false;
        }
        if (profile.requiresSupport() && !sample.supportPresent()) {
            return false;
        }
        LightExposureClass light = classifyLight(sample);
        return light.ordinal() <= profile.maximumLightExposure().ordinal();
    }

    private Optional<SparsePlacementKind> resolvePlacement(SurfaceSample sample) {
        for (SparsePlacementRule rule : plan.sparsePlacements()) {
            if (rule.hostKind() != sample.hostKind()) {
                continue;
            }
            if (!rule.allowedSurfaceClasses().contains(sample.surfaceClass())) {
                continue;
            }
            if (rule.requiresWetness()
                    && sample.wetnessMillionths() < rule.minimumWetnessMillionths()) {
                continue;
            }
            if (rule.requiresSupport() && !sample.supportPresent()) {
                continue;
            }
            return Optional.of(rule.placementKind());
        }
        return Optional.empty();
    }

    private static LightExposureClass classifyLight(SurfaceSample sample) {
        if (sample.shadeMillionths() >= 700_000) {
            return LightExposureClass.DEEP;
        }
        if (sample.shadeMillionths() >= 300_000) {
            return LightExposureClass.SHADED;
        }
        return LightExposureClass.OPEN;
    }

    private static SurfaceSample requireSample(SurfaceSample sample) {
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(sample.hostKind(), "hostKind");
        Objects.requireNonNull(sample.surfaceClass(), "surfaceClass");
        if (sample.wetnessMillionths() < 0 || sample.wetnessMillionths() > 1_000_000
                || sample.dripMillionths() < 0 || sample.dripMillionths() > 1_000_000
                || sample.shadeMillionths() < 0 || sample.shadeMillionths() > 1_000_000) {
            throw new VolumeLocalEnvironmentExceptionV2(
                    VolumeLocalEnvironmentFailureCodeV2.UNKNOWN_SURFACE,
                    "surface sample fields out of range");
        }
        return sample;
    }

    private static String hex(byte[] digest) {
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    public record SurfaceSample(
            int x,
            int y,
            int z,
            HostVolumeKind hostKind,
            VolumeSurfaceClass surfaceClass,
            int wetnessMillionths,
            int dripMillionths,
            int shadeMillionths,
            boolean supportPresent,
            LocalMaterialClass claimedMaterial,
            SparsePlacementKind claimedPlacement
    ) {
        public static SurfaceSample of(
                int x,
                int y,
                int z,
                HostVolumeKind hostKind,
                VolumeSurfaceClass surfaceClass,
                int wetnessMillionths,
                int dripMillionths,
                int shadeMillionths,
                boolean supportPresent
        ) {
            return new SurfaceSample(
                    x, y, z, hostKind, surfaceClass, wetnessMillionths, dripMillionths,
                    shadeMillionths, supportPresent, null, null);
        }
    }

    public record SurfaceDecision(
            VolumeSurfaceClass surfaceClass,
            LightExposureClass lightExposure,
            int materialClassCode,
            Optional<SparsePlacementKind> placement,
            boolean matched
    ) {
    }

    public record Metrics(
            String generatorVersion,
            long classifiedSamples,
            long habitatReadySamples,
            long placementCount,
            long sampleCount,
            String planChecksum
    ) {
    }
}
