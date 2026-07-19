package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.core.v2.scale.ScaleAdmissionDecisionV2;
import com.github.nankotsu029.landformcraft.core.v2.scale.ScaleAdmissionExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.scale.ScaleAdmissionV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.NamedSeedDeriverV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Compiles the V2-9-01 surface foundation plan: ScaleProfile admission, named seed namespace,
 * field/ownership/transition descriptors, and resource budgets. Does not generate terrain shapes.
 */
public final class SurfaceFoundationPlanCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public SurfaceFoundationPlanV2 compileEmpty(int width, int length, long globalSeed) {
        return compile(width, length, globalSeed, List.of(), List.of(),
                ScaleProfileV2.defaults(ScaleClassV2.forDimensions(width, length)));
    }

    public SurfaceFoundationPlanV2 compile(
            int width,
            int length,
            long globalSeed,
            List<OwnerSpec> owners,
            List<InteractionSpec> interactions
    ) {
        return compile(width, length, globalSeed, owners, interactions,
                ScaleProfileV2.defaults(ScaleClassV2.forDimensions(width, length)));
    }

    public SurfaceFoundationPlanV2 compile(
            int width,
            int length,
            long globalSeed,
            List<OwnerSpec> owners,
            List<InteractionSpec> interactions,
            ScaleProfileV2 profile
    ) {
        Objects.requireNonNull(owners, "owners");
        Objects.requireNonNull(interactions, "interactions");
        Objects.requireNonNull(profile, "profile");
        if (width < 1 || length < 1
                || width > ScaleClassV2.MAXIMUM_HORIZONTAL_BLOCKS
                || length > ScaleClassV2.MAXIMUM_HORIZONTAL_BLOCKS) {
            throw failure(SurfaceFoundationFailureCodeV2.DIMENSIONS_INVALID,
                    "surface foundation dimensions outside trusted bounds");
        }

        ScaleAdmissionDecisionV2 admission;
        try {
            admission = ScaleAdmissionV2.admit(width, length, profile);
        } catch (ScaleAdmissionExceptionV2 exception) {
            throw new SurfaceFoundationExceptionV2(
                    SurfaceFoundationFailureCodeV2.SCALE_ADMISSION_REJECTED,
                    exception.getMessage());
        }

        int tileSize = profile.tileSizeBlocks();
        int supportRadius = interactions.stream().mapToInt(InteractionSpec::bandBlocks).max().orElse(0);
        if (supportRadius > SurfaceFoundationPlanV2.MAX_SUPPORT_RADIUS_XZ) {
            throw failure(SurfaceFoundationFailureCodeV2.TRANSITION_OUT_OF_RANGE,
                    "interaction bandBlocks outside 0..32");
        }
        int haloBlocks = Math.max(profile.haloBlocks(), supportRadius);
        if (haloBlocks > tileSize / 2) {
            throw failure(SurfaceFoundationFailureCodeV2.BUDGET_EXCEEDED,
                    "required foundation halo exceeds half tile size");
        }

        List<OwnerSpec> sortedOwners = owners.stream()
                .sorted(Comparator.comparing(OwnerSpec::ownerId))
                .toList();
        List<SurfaceFoundationPlanV2.OwnerDescriptor> ownerDescriptors = new ArrayList<>(sortedOwners.size());
        Set<Long> seeds = new HashSet<>();
        for (OwnerSpec owner : sortedOwners) {
            long derived = NamedSeedDeriverV2.derive(
                    globalSeed,
                    SurfaceFoundationModuleV2.MODULE_ID,
                    SurfaceFoundationModuleV2.MODULE_VERSION,
                    owner.ownerId(),
                    SurfaceFoundationModuleV2.SEED_NAMESPACE,
                    SurfaceFoundationModuleV2.GENERATOR_VERSION);
            if (!seeds.add(derived)) {
                throw failure(SurfaceFoundationFailureCodeV2.SEED_COLLISION,
                        "derived seed collision for owner " + owner.ownerId());
            }
            ownerDescriptors.add(new SurfaceFoundationPlanV2.OwnerDescriptor(
                    owner.ownerId(),
                    owner.ownerIndex(),
                    owner.priority(),
                    owner.parentOrdinal(),
                    owner.surfaceClass(),
                    derived));
        }

        List<SurfaceFoundationPlanV2.Interaction> interactionDescriptors = interactions.stream()
                .map(spec -> {
                    String first = spec.firstOwnerId().compareTo(spec.secondOwnerId()) < 0
                            ? spec.firstOwnerId() : spec.secondOwnerId();
                    String second = spec.firstOwnerId().compareTo(spec.secondOwnerId()) < 0
                            ? spec.secondOwnerId() : spec.firstOwnerId();
                    return new SurfaceFoundationPlanV2.Interaction(
                            spec.relationId(), first, second, spec.bandBlocks());
                })
                .sorted(Comparator.comparing(SurfaceFoundationPlanV2.Interaction::firstOwnerId)
                        .thenComparing(SurfaceFoundationPlanV2.Interaction::secondOwnerId)
                        .thenComparing(SurfaceFoundationPlanV2.Interaction::relationId))
                .toList();

        long namedSeed = NamedSeedDeriverV2.derive(
                globalSeed,
                SurfaceFoundationModuleV2.MODULE_ID,
                SurfaceFoundationModuleV2.MODULE_VERSION,
                "surface-foundation",
                SurfaceFoundationModuleV2.SEED_NAMESPACE,
                SurfaceFoundationModuleV2.GENERATOR_VERSION);

        long cells = Math.multiplyExact((long) width, length);
        int maximumWindowSize = Math.min(tileSize, Math.max(width, length));
        int windowWidth = Math.min(width, maximumWindowSize);
        int windowLength = Math.min(length, maximumWindowSize);
        long windowCells = Math.multiplyExact((long) windowWidth, windowLength);
        long readerWorkingBytes = Math.addExact(
                Math.multiplyExact(windowCells, SurfaceFoundationPlanV2.MAX_FIELDS * (long) Integer.BYTES),
                Math.multiplyExact((long) windowWidth,
                        SurfaceFoundationPlanV2.MAX_FIELDS * (long) Short.BYTES));
        long writerWorkingBytes = Math.addExact(
                SurfaceFoundationPlanV2.STRICT_READ_BACK_BUFFER_BYTES,
                SurfaceFoundationPlanV2.MAX_HEADER_BYTES_PER_FIELD);
        long maximumWorkingBytes = Math.max(readerWorkingBytes, writerWorkingBytes);
        if (maximumWorkingBytes > admission.estimatedWorkingBytesPerTile()
                && maximumWorkingBytes > profile.maximumWorkingBytes()) {
            throw failure(SurfaceFoundationFailureCodeV2.BUDGET_EXCEEDED,
                    "foundation working budget exceeds scale admission");
        }
        long requiredSingleArtifactBytes = Math.addExact(
                Math.multiplyExact(cells, Short.BYTES),
                SurfaceFoundationPlanV2.MAX_HEADER_BYTES_PER_FIELD);
        long estimatedArtifactBytes = Math.multiplyExact(
                requiredSingleArtifactBytes, SurfaceFoundationPlanV2.MAX_FIELDS);
        long estimatedCpu = Math.multiplyExact(cells, SurfaceFoundationPlanV2.MAX_FIELDS);

        SurfaceFoundationPlanV2 draft;
        try {
            draft = new SurfaceFoundationPlanV2(
                    SurfaceFoundationPlanV2.VERSION,
                    SurfaceFoundationPlanV2.FIELD_CONTRACT_VERSION,
                    SurfaceFoundationModuleV2.MODULE_ID,
                    SurfaceFoundationModuleV2.MODULE_VERSION,
                    SurfaceFoundationModuleV2.STAGE_ID,
                    namedSeed,
                    SurfaceFoundationModuleV2.SEED_NAMESPACE,
                    width,
                    length,
                    admission.scaleClass().id(),
                    tileSize,
                    haloBlocks,
                    ModuleDescriptorV2.MergeOperator.PRIORITY_BLEND,
                    SurfaceFoundationPlanV2.AmbiguityPolicy.REJECT,
                    ownerDescriptors,
                    interactionDescriptors,
                    SurfaceFoundationModuleV2.requiredFieldBindings(),
                    supportRadius,
                    new SurfaceFoundationPlanV2.ResourceBudget(
                            SurfaceFoundationPlanV2.ResourceBudget.VERSION,
                            SurfaceFoundationPlanV2.MAX_OWNERS,
                            SurfaceFoundationPlanV2.MAX_INTERACTIONS,
                            SurfaceFoundationPlanV2.MAX_FIELDS,
                            cells,
                            estimatedCpu,
                            Math.max(SurfaceFoundationPlanV2.ESTIMATED_RETAINED_BYTES,
                                    admission.estimatedRetainedBytes()),
                            maximumWindowSize,
                            Math.max(maximumWorkingBytes, admission.estimatedWorkingBytesPerTile()),
                            Math.max(estimatedArtifactBytes, admission.estimatedArtifactBytes()),
                            Math.max(requiredSingleArtifactBytes,
                                    Math.min(estimatedArtifactBytes, profile.maximumArtifactBytes())),
                            SurfaceFoundationPlanV2.MAX_CANONICAL_BYTES,
                            supportRadius,
                            haloBlocks),
                    "0".repeat(64));
        } catch (IllegalArgumentException exception) {
            throw new SurfaceFoundationExceptionV2(
                    SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION, exception.getMessage());
        }
        return codec.sealSurfaceFoundationPlan(draft);
    }

    private static SurfaceFoundationExceptionV2 failure(
            SurfaceFoundationFailureCodeV2 code,
            String message
    ) {
        return new SurfaceFoundationExceptionV2(code, message);
    }

    /** Compile-time owner input before seed derivation. */
    public record OwnerSpec(
            String ownerId,
            int ownerIndex,
            int priority,
            int parentOrdinal,
            SurfaceFoundationPlanV2.SurfaceClassCode surfaceClass
    ) {
        public OwnerSpec {
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(surfaceClass, "surfaceClass");
        }
    }

    /** Compile-time interaction input; owner order is canonicalized by the compiler. */
    public record InteractionSpec(
            String relationId,
            String firstOwnerId,
            String secondOwnerId,
            int bandBlocks
    ) {
        public InteractionSpec {
            Objects.requireNonNull(relationId, "relationId");
            Objects.requireNonNull(firstOwnerId, "firstOwnerId");
            Objects.requireNonNull(secondOwnerId, "secondOwnerId");
        }
    }
}
