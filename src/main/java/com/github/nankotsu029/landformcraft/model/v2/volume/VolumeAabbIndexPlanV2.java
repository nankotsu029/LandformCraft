package com.github.nankotsu029.landformcraft.model.v2.volume;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Versioned V2-5-03 VolumePlan AABB index descriptor. Entries bind ordered CSG operators to
 * conservative AABBs for tile+halo overlap queries. Voxel evaluation and feature generators are
 * out of scope.
 */
public record VolumeAabbIndexPlanV2(
        int planVersion,
        String indexContractVersion,
        CsgPlanBinding csgPlanBinding,
        Kernel kernel,
        List<Entry> entries,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String INDEX_CONTRACT_VERSION = "volume-aabb-index-contract-v1";
    public static final long MAX_CANONICAL_BYTES = 64L * 1024L;
    public static final int MAXIMUM_ENTRIES = VolumeCsgPlanV2.MAXIMUM_OPERATORS;
    public static final int MAXIMUM_QUERY_RESULTS = MAXIMUM_ENTRIES;
    public static final int MAXIMUM_SUPPORT_BLOCKS = 64;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public VolumeAabbIndexPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("volume-aabb-index planVersion must be 1");
        }
        indexContractVersion = nonBlank(indexContractVersion, "indexContractVersion", 64);
        if (!INDEX_CONTRACT_VERSION.equals(indexContractVersion)) {
            throw new IllegalArgumentException("unknown volume-aabb-index contract version");
        }
        Objects.requireNonNull(csgPlanBinding, "csgPlanBinding");
        Objects.requireNonNull(kernel, "kernel");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (entries.isEmpty() || entries.size() > MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException("volume-aabb-index entry count out of range");
        }
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateEntries(entries, kernel);
        validateBudget(budget, entries);
    }

    public VolumeAabbIndexPlanV2 withCanonicalChecksum(String checksum) {
        return new VolumeAabbIndexPlanV2(
                planVersion, indexContractVersion, csgPlanBinding, kernel, entries, budget, checksum);
    }

    public void requireCsgPlan(VolumeCsgPlanV2 csgPlan) {
        Objects.requireNonNull(csgPlan, "csgPlan");
        if (!csgPlanBinding.sourceVolumeCsgPlanChecksum().equals(csgPlan.canonicalChecksum())) {
            throw new IllegalArgumentException("volume-aabb-index CSG plan binding mismatch");
        }
        if (entries.size() != csgPlan.operators().size()) {
            throw new IllegalArgumentException("volume-aabb-index entry count must match CSG operators");
        }
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            VolumeCsgPlanV2.Operator operator = csgPlan.operators().get(index);
            if (entry.ordinal() != operator.ordinal()
                    || !entry.operatorId().equals(operator.operatorId())) {
                throw new IllegalArgumentException("volume-aabb-index entry/operator mismatch");
            }
        }
    }

    public record CsgPlanBinding(
            int bindingVersion,
            String sourceVolumeCsgPlanChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "volume-aabb-index-csg-binding-v1";

        public CsgPlanBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown volume-aabb-index CSG binding");
            }
            sourceVolumeCsgPlanChecksum = checksum(
                    sourceVolumeCsgPlanChecksum, "sourceVolumeCsgPlanChecksum");
        }
    }

    public record Kernel(
            String kernelVersion,
            int maximumEntries,
            int maximumQueryResults,
            int maximumSupportBlocksXZ,
            int maximumSupportBlocksY
    ) {
        public static final String VERSION = "volume-aabb-index-v1";

        public Kernel {
            kernelVersion = nonBlank(kernelVersion, "kernelVersion", 64);
            if (!VERSION.equals(kernelVersion)) {
                throw new IllegalArgumentException("unknown volume-aabb-index kernel version");
            }
            if (maximumEntries < 1 || maximumEntries > MAXIMUM_ENTRIES
                    || maximumQueryResults < 1 || maximumQueryResults > MAXIMUM_QUERY_RESULTS
                    || maximumSupportBlocksXZ < 0 || maximumSupportBlocksXZ > MAXIMUM_SUPPORT_BLOCKS
                    || maximumSupportBlocksY < 0 || maximumSupportBlocksY > MAXIMUM_SUPPORT_BLOCKS) {
                throw new IllegalArgumentException("volume-aabb-index kernel budget out of range");
            }
        }

        public static Kernel standard() {
            return new Kernel(VERSION, MAXIMUM_ENTRIES, MAXIMUM_QUERY_RESULTS,
                    MAXIMUM_SUPPORT_BLOCKS, MAXIMUM_SUPPORT_BLOCKS);
        }
    }

    public record Entry(
            String entryId,
            String operatorId,
            int ordinal,
            VolumeSdfAabbV2 aabb,
            int supportRadiusXZBlocks,
            int supportRadiusYBlocks
    ) {
        public Entry {
            entryId = qualified(entryId, "entryId");
            operatorId = qualified(operatorId, "operatorId");
            Objects.requireNonNull(aabb, "aabb");
            if (ordinal < 0) {
                throw new IllegalArgumentException("entry ordinal must be non-negative");
            }
            if (supportRadiusXZBlocks < 0 || supportRadiusXZBlocks > MAXIMUM_SUPPORT_BLOCKS
                    || supportRadiusYBlocks < 0 || supportRadiusYBlocks > MAXIMUM_SUPPORT_BLOCKS) {
                throw new IllegalArgumentException("support radius out of range");
            }
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            int maximumEntries,
            int maximumQueryResults,
            long estimatedCanonicalBytes,
            long maximumCanonicalBytes,
            long maximumWorkingBytes,
            long maximumIndexNodes
    ) {
        public static final String VERSION = "volume-aabb-index-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown volume-aabb-index budget version");
            }
            if (maximumEntries < 1 || maximumEntries > MAXIMUM_ENTRIES
                    || maximumQueryResults < 1 || maximumQueryResults > MAXIMUM_QUERY_RESULTS
                    || estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || maximumWorkingBytes < 1
                    || maximumIndexNodes < 1 || maximumIndexNodes > MAXIMUM_ENTRIES) {
                throw new IllegalArgumentException("volume-aabb-index budget out of range");
            }
        }
    }

    private static void validateEntries(List<Entry> entries, Kernel kernel) {
        if (entries.size() > kernel.maximumEntries()) {
            throw new IllegalArgumentException("volume-aabb-index entry count exceeds kernel");
        }
        Set<String> entryIds = new HashSet<>();
        Set<String> operatorIds = new HashSet<>();
        List<Entry> sorted = entries.stream()
                .sorted(Comparator.comparingInt(Entry::ordinal))
                .toList();
        for (int index = 0; index < sorted.size(); index++) {
            Entry entry = sorted.get(index);
            if (entry.ordinal() != index) {
                throw new IllegalArgumentException(
                        "volume-aabb-index entries must use contiguous ordinals 0..n-1");
            }
            if (!entryIds.add(entry.entryId())) {
                throw new IllegalArgumentException("duplicate volume-aabb-index entry id");
            }
            if (!operatorIds.add(entry.operatorId())) {
                throw new IllegalArgumentException("duplicate volume-aabb-index operator id");
            }
            if (entry.supportRadiusXZBlocks() > kernel.maximumSupportBlocksXZ()
                    || entry.supportRadiusYBlocks() > kernel.maximumSupportBlocksY()) {
                throw new IllegalArgumentException("entry support exceeds kernel ceiling");
            }
        }
        if (!sorted.equals(entries)) {
            throw new IllegalArgumentException(
                    "volume-aabb-index entries must be listed in ordinal order");
        }
    }

    private static void validateBudget(ResourceBudget budget, List<Entry> entries) {
        if (entries.size() > budget.maximumEntries()
                || entries.size() > budget.maximumIndexNodes()) {
            throw new IllegalArgumentException("volume-aabb-index entry budget exceeded");
        }
        if (budget.estimatedCanonicalBytes() > budget.maximumCanonicalBytes()) {
            throw new IllegalArgumentException("volume-aabb-index canonical budget exceeded");
        }
    }

    static String qualified(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!QUALIFIED.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a qualified id");
        }
        return value;
    }

    static String checksum(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a sha-256 hex digest");
        }
        return value;
    }

    static String nonBlank(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " length out of range");
        }
        return value;
    }
}
