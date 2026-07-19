package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.BathymetryChecksumSupportV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.BathymetrySampleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.ContinentalShelfGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.ContinentalSlopeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.OceanBasinGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ContinentalShelfPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ContinentalSlopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationContinentalShelfValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationContinentalSlopeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationOceanBasinValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OceanBasinPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Coast-to-basin bathymetry transect: shelf → slope → basin with relation chain and composed depth.
 * Does not require SurfaceFoundation full-map merge coverage.
 */
public final class FoundationBathymetryTransectCompilerV2 {
    private static final String COMPOSED_VERSION = "foundation-bathymetry-transect-fixed-v1";

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final OceanBasinPlanCompilerV2 basinCompiler = new OceanBasinPlanCompilerV2();
    private final ContinentalShelfPlanCompilerV2 shelfCompiler = new ContinentalShelfPlanCompilerV2();
    private final ContinentalSlopePlanCompilerV2 slopeCompiler = new ContinentalSlopePlanCompilerV2();

    public FoundationBathymetryTransectV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");

        TerrainIntentV2.Feature shelfFeature = require(intent, TerrainIntentV2.FeatureKind.CONTINENTAL_SHELF);
        TerrainIntentV2.Feature slopeFeature = require(intent, TerrainIntentV2.FeatureKind.CONTINENTAL_SLOPE);
        TerrainIntentV2.Feature basinFeature = require(intent, TerrainIntentV2.FeatureKind.OCEAN_BASIN);

        requireTransitionChain(intent, shelfFeature.id(), slopeFeature.id(), basinFeature.id());

        ContinentalShelfPlanV2 shelfPlan = codec.sealContinentalShelfPlan(shelfCompiler.compile(
                shelfFeature, intent, bounds, codec.geometryChecksum(shelfFeature.geometry())));
        ContinentalSlopePlanV2 slopePlan = codec.sealContinentalSlopePlan(slopeCompiler.compile(
                slopeFeature, intent, bounds, codec.geometryChecksum(slopeFeature.geometry())));
        OceanBasinPlanV2 basinPlan = codec.sealOceanBasinPlan(basinCompiler.compile(
                basinFeature, intent, bounds, codec.geometryChecksum(basinFeature.geometry())));

        if (shelfPlan.selectedShelfDepthBlocksBelowSea() > slopePlan.selectedLowerDepthBlocksBelowSea()) {
            throw new FoundationSliceException("v2.bathymetry-shelf-deeper-than-slope",
                    "continental shelf depth exceeds continental slope lower depth");
        }

        ContinentalShelfGeneratorV2 shelfGenerator = new ContinentalShelfGeneratorV2(shelfPlan);
        ContinentalSlopeGeneratorV2 slopeGenerator = new ContinentalSlopeGeneratorV2(slopePlan);
        OceanBasinGeneratorV2 basinGenerator = new OceanBasinGeneratorV2(basinPlan);

        ContinentalShelfGeneratorV2.ContinentalShelfMetrics shelfMetrics = shelfGenerator.evaluate();
        ContinentalSlopeGeneratorV2.ContinentalSlopeMetrics slopeMetrics = slopeGenerator.evaluate();
        OceanBasinGeneratorV2.OceanBasinMetrics basinMetrics = basinGenerator.evaluate();
        if (!slopeMetrics.monotoneOk()) {
            throw new FoundationSliceException("v2.continental-slope-non-monotone",
                    "continental slope depth is not monotone seaward");
        }
        if (!shelfMetrics.fluidSolidConflictFree()
                || !slopeMetrics.fluidSolidConflictFree()
                || !basinMetrics.fluidSolidConflictFree()) {
            throw new FoundationSliceException("v2.bathymetry-fluid-solid-conflict",
                    "fluid column conflicts with solid floor");
        }
        if (!shelfMetrics.budgetOk() || !slopeMetrics.budgetOk() || !basinMetrics.budgetOk()) {
            throw new FoundationSliceException("v2.bathymetry-budget", "bathymetry raster budget exceeded");
        }

        BathymetryChecksumSupportV2.CellSource composed = (x, z) -> compose(
                shelfGenerator.sampleAt(x, z),
                slopeGenerator.sampleAt(x, z),
                basinGenerator.sampleAt(x, z),
                bounds.waterLevel());

        validateCoastToBasinMonotone(bounds, composed);

        Map<BathymetrySampleV2.BathymetryField, String> whole =
                BathymetryChecksumSupportV2.fieldChecksumsFrom(
                        COMPOSED_VERSION, bounds.width(), bounds.length(), composed);
        Map<BathymetrySampleV2.BathymetryField, String> tiled =
                BathymetryChecksumSupportV2.tiledFieldChecksums(
                        COMPOSED_VERSION, bounds.width(), bounds.length(), 32, composed);
        if (!whole.equals(tiled)) {
            throw new FoundationSliceException("v2.bathymetry-whole-tile-mismatch",
                    "composed bathymetry whole/tile depth checksums disagree");
        }

        String underwater = BathymetryChecksumSupportV2.underwaterColumnExportChecksum(
                COMPOSED_VERSION, bounds.width(), bounds.length(),
                bounds.waterLevel(), bounds.minY(), composed);

        FoundationContinentalShelfValidationArtifactV2 shelfValidation =
                codec.sealFoundationContinentalShelfValidationArtifact(
                        new FoundationContinentalShelfValidationArtifactV2(
                                FoundationContinentalShelfValidationArtifactV2.VERSION,
                                FoundationContinentalShelfValidationArtifactV2.CONTRACT_VERSION,
                                shelfPlan.featureId(),
                                new FoundationContinentalShelfValidationArtifactV2.Metrics(
                                        shelfMetrics.depthFinite(),
                                        shelfMetrics.widthOk(),
                                        shelfMetrics.fluidSolidConflictFree(),
                                        shelfMetrics.budgetOk()),
                                List.of(),
                                "0".repeat(64)));
        FoundationContinentalSlopeValidationArtifactV2 slopeValidation =
                codec.sealFoundationContinentalSlopeValidationArtifact(
                        new FoundationContinentalSlopeValidationArtifactV2(
                                FoundationContinentalSlopeValidationArtifactV2.VERSION,
                                FoundationContinentalSlopeValidationArtifactV2.CONTRACT_VERSION,
                                slopePlan.featureId(),
                                new FoundationContinentalSlopeValidationArtifactV2.Metrics(
                                        slopeMetrics.depthFinite(),
                                        slopeMetrics.monotoneOk(),
                                        slopeMetrics.widthOk(),
                                        slopeMetrics.fluidSolidConflictFree(),
                                        slopeMetrics.budgetOk()),
                                List.of(),
                                "0".repeat(64)));
        FoundationOceanBasinValidationArtifactV2 basinValidation =
                codec.sealFoundationOceanBasinValidationArtifact(
                        new FoundationOceanBasinValidationArtifactV2(
                                FoundationOceanBasinValidationArtifactV2.VERSION,
                                FoundationOceanBasinValidationArtifactV2.CONTRACT_VERSION,
                                basinPlan.featureId(),
                                new FoundationOceanBasinValidationArtifactV2.Metrics(
                                        basinMetrics.depthFinite(),
                                        basinMetrics.widthOk(),
                                        basinMetrics.fluidSolidConflictFree(),
                                        basinMetrics.budgetOk()),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "composed-depth", "foundation.bathymetry.composed-depth",
                whole.get(BathymetrySampleV2.BathymetryField.DEPTH)));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "underwater-export", "foundation.bathymetry.underwater-export", underwater));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        return new FoundationBathymetryTransectV2(
                shelfPlan, slopePlan, basinPlan,
                shelfValidation, slopeValidation, basinValidation,
                preview, whole, underwater, globalSeed);
    }

    static BathymetrySampleV2 compose(
            BathymetrySampleV2 shelf,
            BathymetrySampleV2 slope,
            BathymetrySampleV2 basin,
            int waterLevel
    ) {
        // Deeper owner wins on overlaps (basin > slope > shelf) for continuous coast→basin depth.
        if (basin.owned()) {
            return basin;
        }
        if (slope.owned()) {
            return slope;
        }
        if (shelf.owned()) {
            return shelf;
        }
        return BathymetrySampleV2.outside(waterLevel);
    }

    private static void validateCoastToBasinMonotone(
            WorldBlueprintV2.Bounds bounds,
            BathymetryChecksumSupportV2.CellSource composed
    ) {
        int midZ = bounds.length() / 2;
        int previous = -1;
        boolean sawOwned = false;
        for (int x = 0; x < bounds.width(); x++) {
            BathymetrySampleV2 sample = composed.sampleAt(x, midZ);
            if (!sample.owned()) {
                continue;
            }
            sawOwned = true;
            if (previous >= 0 && sample.depthBlocksBelowSea() + 1 < previous) {
                throw new FoundationSliceException("v2.bathymetry-non-monotone",
                        "coast-to-basin depth decreases seaward along transect");
            }
            previous = sample.depthBlocksBelowSea();
        }
        if (!sawOwned) {
            throw new FoundationSliceException("v2.bathymetry-empty-transect",
                    "coast-to-basin transect has no owned bathymetry cells");
        }
    }

    private static void requireTransitionChain(
            TerrainIntentV2 intent,
            String shelfId,
            String slopeId,
            String basinId
    ) {
        boolean shelfSlope = hasTransition(intent, shelfId, slopeId);
        boolean slopeBasin = hasTransition(intent, slopeId, basinId);
        if (!shelfSlope || !slopeBasin) {
            throw new FoundationSliceException("v2.bathymetry-missing-relation",
                    "transect requires ADJACENT_TO/OVERLAPS/FLANKS chain shelf↔slope↔basin");
        }
    }

    private static boolean hasTransition(TerrainIntentV2 intent, String a, String b) {
        String fromA = "feature:" + a;
        String fromB = "feature:" + b;
        for (TerrainIntentV2.Relation relation : intent.relations()) {
            if (relation.kind() != TerrainIntentV2.RelationKind.ADJACENT_TO
                    && relation.kind() != TerrainIntentV2.RelationKind.OVERLAPS
                    && relation.kind() != TerrainIntentV2.RelationKind.FLANKS) {
                continue;
            }
            boolean ab = relation.from().equals(fromA) && relation.to().equals(fromB);
            boolean ba = relation.from().equals(fromB) && relation.to().equals(fromA);
            if (ab || ba) {
                return true;
            }
        }
        return false;
    }

    private static TerrainIntentV2.Feature require(
            TerrainIntentV2 intent,
            TerrainIntentV2.FeatureKind kind
    ) {
        Optional<TerrainIntentV2.Feature> feature = intent.features().stream()
                .filter(item -> item.kind() == kind)
                .findFirst();
        if (feature.isEmpty()) {
            throw new FoundationSliceException("v2.foundation-slice-empty",
                    "bathymetry transect requires " + kind);
        }
        return feature.get();
    }

    public record FoundationBathymetryTransectV2(
            ContinentalShelfPlanV2 shelf,
            ContinentalSlopePlanV2 slope,
            OceanBasinPlanV2 basin,
            FoundationContinentalShelfValidationArtifactV2 shelfValidation,
            FoundationContinentalSlopeValidationArtifactV2 slopeValidation,
            FoundationOceanBasinValidationArtifactV2 basinValidation,
            FoundationPreviewIndexV2 preview,
            Map<BathymetrySampleV2.BathymetryField, String> composedFieldChecksums,
            String underwaterColumnExportChecksum,
            long globalSeed
    ) {
    }
}
