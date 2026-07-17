package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Canonical V2-2 execution plan for one supported, strictly 2.5D rocky cape. */
public record RockyCapePlanV2(
        int planVersion,
        String featureId,
        ProfileKind profileKind,
        TerrainIntentV2.CapeMode capeMode,
        TerrainIntentV2.Edge seawardSide,
        int cliffHeightBlocks,
        int localReliefAboveSeaBlocks,
        int cliffBandWidthBlocks,
        int minimumRockExposureMillionths,
        int maximumRockExposureMillionths,
        int selectedRockExposureMillionths,
        long exposurePatternSeed,
        int minimumSeaStackCount,
        int maximumSeaStackCount,
        List<SeaStackDescriptor> seaStacks,
        int minimumChannelCount,
        int maximumChannelCount,
        List<ChannelDescriptor> channels,
        int minY,
        int maxY,
        int waterLevel,
        String regionFieldId,
        String surfaceHeightFieldId,
        String rockExposureFieldId,
        String descriptorIndexFieldId,
        int supportRadiusXZ,
        int coastlineTurningCount
) {
    public static final int VERSION = 1;
    public static final int FIXED_SCALE = 1_000_000;
    public static final int MAXIMUM_DESCRIPTORS = 16;

    public RockyCapePlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("rocky cape planVersion must be 1");
        featureId = V2Validation.slug(featureId, "featureId");
        Objects.requireNonNull(profileKind, "profileKind");
        Objects.requireNonNull(capeMode, "capeMode");
        Objects.requireNonNull(seawardSide, "seawardSide");
        if (capeMode != TerrainIntentV2.CapeMode.TWO_POINT_FIVE_D_ONLY) {
            throw new IllegalArgumentException("rocky cape plan must remain 2.5D-only");
        }
        if (cliffHeightBlocks < 1 || cliffHeightBlocks > localReliefAboveSeaBlocks
                || localReliefAboveSeaBlocks > 64
                || cliffBandWidthBlocks < 1 || cliffBandWidthBlocks > 64) {
            throw new IllegalArgumentException("rocky cape relief profile is invalid");
        }
        if (minimumRockExposureMillionths < 50_000
                || minimumRockExposureMillionths > selectedRockExposureMillionths
                || selectedRockExposureMillionths > maximumRockExposureMillionths
                || maximumRockExposureMillionths > FIXED_SCALE) {
            throw new IllegalArgumentException("rocky cape exposure range is invalid");
        }
        if (minimumSeaStackCount < 1 || minimumSeaStackCount > maximumSeaStackCount
                || maximumSeaStackCount > 12
                || minimumChannelCount < 1 || minimumChannelCount > maximumChannelCount
                || maximumChannelCount > 4) {
            throw new IllegalArgumentException("rocky cape descriptor count range is invalid");
        }
        seaStacks = V2Validation.sorted(
                seaStacks, "seaStacks", 12, Comparator.comparingInt(SeaStackDescriptor::descriptorIndex));
        channels = V2Validation.sorted(
                channels, "channels", 4, Comparator.comparingInt(ChannelDescriptor::descriptorIndex));
        if (seaStacks.size() < minimumSeaStackCount || seaStacks.size() > maximumSeaStackCount
                || channels.size() < minimumChannelCount || channels.size() > maximumChannelCount
                || seaStacks.size() + channels.size() > MAXIMUM_DESCRIPTORS) {
            throw new IllegalArgumentException("rocky cape descriptor count differs from requested range");
        }
        HashSet<Integer> indexes = new HashSet<>();
        HashSet<String> ids = new HashSet<>();
        for (ChannelDescriptor channel : channels) {
            if (!indexes.add(channel.descriptorIndex()) || !ids.add(channel.descriptorId())) {
                throw new IllegalArgumentException("duplicate rocky cape descriptor identity");
            }
        }
        for (SeaStackDescriptor stack : seaStacks) {
            if (!indexes.add(stack.descriptorIndex()) || !ids.add(stack.descriptorId())) {
                throw new IllegalArgumentException("duplicate rocky cape descriptor identity");
            }
        }
        if (minY >= maxY || waterLevel < minY || waterLevel > maxY
                || (long) waterLevel + localReliefAboveSeaBlocks > maxY
                || channels.stream().anyMatch(channel -> (long) waterLevel - channel.depthBlocks() < minY)) {
            throw new IllegalArgumentException("rocky cape vertical bounds are invalid");
        }
        regionFieldId = V2Validation.qualifiedId(regionFieldId, "regionFieldId");
        surfaceHeightFieldId = V2Validation.qualifiedId(surfaceHeightFieldId, "surfaceHeightFieldId");
        rockExposureFieldId = V2Validation.qualifiedId(rockExposureFieldId, "rockExposureFieldId");
        descriptorIndexFieldId = V2Validation.qualifiedId(descriptorIndexFieldId, "descriptorIndexFieldId");
        int requiredSupport = cliffBandWidthBlocks;
        for (ChannelDescriptor channel : channels) {
            requiredSupport = Math.max(requiredSupport,
                    channel.lengthBlocks() + (channel.widthBlocks() + 1) / 2);
        }
        for (SeaStackDescriptor stack : seaStacks) {
            requiredSupport = Math.max(requiredSupport, stack.offshoreDistanceBlocks() + stack.radiusBlocks());
        }
        if (supportRadiusXZ < requiredSupport || supportRadiusXZ > 64) {
            throw new IllegalArgumentException("rocky cape support radius is invalid");
        }
        if (coastlineTurningCount < 3 || coastlineTurningCount > 4_096) {
            throw new IllegalArgumentException("rocky cape coastline turning count is invalid");
        }
    }

    public enum ProfileKind { LINEAR_CLIFF_TO_INTERIOR }

    public record ChannelDescriptor(
            int descriptorIndex,
            String descriptorId,
            CoastalFeaturePlanV2.BlockPoint mouth,
            CoastalFeaturePlanV2.BlockPoint inlandEnd,
            int widthBlocks,
            int lengthBlocks,
            int depthBlocks
    ) {
        public ChannelDescriptor {
            if (descriptorIndex < 1 || descriptorIndex > 4) {
                throw new IllegalArgumentException("channel descriptorIndex outside 1..4");
            }
            descriptorId = V2Validation.slug(descriptorId, "channel descriptorId");
            Objects.requireNonNull(mouth, "mouth");
            Objects.requireNonNull(inlandEnd, "inlandEnd");
            if (mouth.equals(inlandEnd) || widthBlocks < 2 || widthBlocks > 8
                    || lengthBlocks < 4 || lengthBlocks > 64
                    || depthBlocks < 1 || depthBlocks > 16) {
                throw new IllegalArgumentException("rocky cape channel descriptor is invalid");
            }
        }
    }

    public record SeaStackDescriptor(
            int descriptorIndex,
            String descriptorId,
            CoastalFeaturePlanV2.BlockPoint center,
            int radiusBlocks,
            int heightAboveSeaBlocks,
            int offshoreDistanceBlocks
    ) {
        public SeaStackDescriptor {
            if (descriptorIndex < 1_025 || descriptorIndex > 1_036) {
                throw new IllegalArgumentException("sea stack descriptorIndex outside 1025..1036");
            }
            descriptorId = V2Validation.slug(descriptorId, "sea stack descriptorId");
            Objects.requireNonNull(center, "center");
            if (radiusBlocks < 1 || radiusBlocks > 8
                    || heightAboveSeaBlocks < 1 || heightAboveSeaBlocks > 64
                    || offshoreDistanceBlocks < radiusBlocks + 2 || offshoreDistanceBlocks > 64) {
                throw new IllegalArgumentException("rocky cape sea stack descriptor is invalid");
            }
        }
    }
}
