package com.github.nankotsu029.landformcraft.generator.v2.geology.strata;

import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pure surface-exposure and derived hardness/permeability sampler over strata profile descriptors.
 * It never allocates a dense W×L×depth layer array.
 */
public final class StrataExposureResolverV2 {
    private final Map<Integer, StrataPlanV2.Profile> profilesByProvinceCode;
    private final Map<Integer, LithologyPlanV2.Entry> lithologyByCode;
    private final StrataPlanV2.HydrologyGeologyInputHandoff hydrologyHandoff;

    public StrataExposureResolverV2(
            GeologyPlanV2 geologyPlan,
            LithologyPlanV2 lithologyPlan,
            StrataPlanV2 strataPlan
    ) {
        Objects.requireNonNull(geologyPlan, "geologyPlan");
        Objects.requireNonNull(lithologyPlan, "lithologyPlan");
        Objects.requireNonNull(strataPlan, "strataPlan");
        strataPlan.requireLithologyPlan(geologyPlan, lithologyPlan);
        Map<Integer, StrataPlanV2.Profile> profiles = new HashMap<>();
        for (StrataPlanV2.Profile profile : strataPlan.profiles()) {
            if (profiles.putIfAbsent(profile.provinceCode(), profile) != null) {
                throw new IllegalArgumentException("duplicate strata province profile");
            }
        }
        Map<Integer, LithologyPlanV2.Entry> lithology = new HashMap<>();
        for (LithologyPlanV2.Entry entry : lithologyPlan.catalog().entries()) {
            lithology.put(entry.lithologyCode(), entry);
        }
        this.profilesByProvinceCode = Map.copyOf(profiles);
        this.lithologyByCode = Map.copyOf(lithology);
        this.hydrologyHandoff = strataPlan.hydrologyHandoff();
    }

    public StrataPlanV2.HydrologyGeologyInputHandoff hydrologyHandoff() {
        return hydrologyHandoff;
    }

    public int exposedLithologyCode(int provinceRaw, int globalX, int globalZ) {
        if (provinceRaw == GeologyPlanV2.NO_DATA_RAW) {
            return GeologyPlanV2.NO_DATA_RAW;
        }
        StrataPlanV2.Profile profile = profilesByProvinceCode.get(provinceRaw);
        if (profile == null) {
            throw new IllegalArgumentException("unknown geology province code for strata exposure: " + provinceRaw);
        }
        int depthFromTop = depthFromTop(profile, globalX, globalZ);
        return layerAtDepthFromTop(profile, depthFromTop).lithologyCode();
    }

    public int derivedHardnessMillionths(int provinceRaw, int globalX, int globalZ) {
        int code = exposedLithologyCode(provinceRaw, globalX, globalZ);
        if (code == GeologyPlanV2.NO_DATA_RAW) {
            return GeologyPlanV2.NO_DATA_RAW;
        }
        return requireLithology(code).hardnessMillionths();
    }

    public int derivedPermeabilityMillionths(int provinceRaw, int globalX, int globalZ) {
        int code = exposedLithologyCode(provinceRaw, globalX, globalZ);
        if (code == GeologyPlanV2.NO_DATA_RAW) {
            return GeologyPlanV2.NO_DATA_RAW;
        }
        return requireLithology(code).permeabilityMillionths();
    }

    public int derivedHardnessRaw(int provinceRaw, int globalX, int globalZ) {
        int millionths = derivedHardnessMillionths(provinceRaw, globalX, globalZ);
        return millionths == GeologyPlanV2.NO_DATA_RAW ? GeologyPlanV2.NO_DATA_RAW : millionths / 1_000;
    }

    public int derivedPermeabilityRaw(int provinceRaw, int globalX, int globalZ) {
        int millionths = derivedPermeabilityMillionths(provinceRaw, globalX, globalZ);
        return millionths == GeologyPlanV2.NO_DATA_RAW ? GeologyPlanV2.NO_DATA_RAW : millionths / 1_000;
    }

    public LithologyPlanV2.ErosionResponse erosionResponse(int provinceRaw, int globalX, int globalZ) {
        int code = exposedLithologyCode(provinceRaw, globalX, globalZ);
        if (code == GeologyPlanV2.NO_DATA_RAW) {
            throw new IllegalArgumentException("erosion response is undefined for geology no-data");
        }
        return requireLithology(code).erosionResponse();
    }

    private static int depthFromTop(StrataPlanV2.Profile profile, int globalX, int globalZ) {
        StrataPlanV2.FoldTilt orientation = profile.orientation();
        long shift = Math.addExact(
                Math.multiplyExact((long) globalX, orientation.tiltDxMillionths()),
                Math.multiplyExact((long) globalZ, orientation.tiltDzMillionths()));
        shift = Math.floorDiv(shift, 1_000_000L);
        if (orientation.foldAmplitudeBlocks() > 0) {
            shift = Math.addExact(shift, foldShiftBlocks(globalX, globalZ, orientation));
        }
        int total = profile.totalThicknessBlocks();
        if (shift <= 0L) {
            return 0;
        }
        if (shift >= total) {
            return total - 1;
        }
        return (int) shift;
    }

    private static int foldShiftBlocks(int globalX, int globalZ, StrataPlanV2.FoldTilt orientation) {
        int wavelength = orientation.foldWavelengthBlocks();
        int phase = Math.floorMod(globalX + globalZ, wavelength);
        // Quarter-wave integer table: 0, +A, 0, -A over the wavelength.
        int quarter = wavelength / 4;
        if (quarter < 1) {
            return 0;
        }
        int segment = phase / quarter;
        return switch (segment) {
            case 0 -> 0;
            case 1 -> orientation.foldAmplitudeBlocks();
            case 2 -> 0;
            default -> -orientation.foldAmplitudeBlocks();
        };
    }

    private static StrataPlanV2.Layer layerAtDepthFromTop(StrataPlanV2.Profile profile, int depthFromTop) {
        int remaining = depthFromTop;
        for (int index = profile.layers().size() - 1; index >= 0; index--) {
            StrataPlanV2.Layer layer = profile.layers().get(index);
            if (remaining < layer.thicknessBlocks()) {
                return layer;
            }
            remaining -= layer.thicknessBlocks();
        }
        return profile.layers().getFirst();
    }

    private LithologyPlanV2.Entry requireLithology(int code) {
        LithologyPlanV2.Entry entry = lithologyByCode.get(code);
        if (entry == null) {
            throw new IllegalArgumentException("unknown lithology compact code for strata exposure: " + code);
        }
        return entry;
    }
}
