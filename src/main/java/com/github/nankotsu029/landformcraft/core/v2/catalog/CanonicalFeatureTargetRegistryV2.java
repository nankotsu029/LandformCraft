package com.github.nankotsu029.landformcraft.core.v2.catalog;

import com.github.nankotsu029.landformcraft.model.v2.CanonicalTerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** ADR 0036 target authoring and compatibility disposition registry. */
public record CanonicalFeatureTargetRegistryV2(
        List<Entry> entries,
        Set<String> canonicalSchemaKinds,
        List<String> differences
) {
    public static final String CONTRACT_VERSION = "canonical-feature-target-registry-v1";

    public CanonicalFeatureTargetRegistryV2 {
        entries = List.copyOf(entries.stream()
                .sorted(Comparator.comparing(entry -> entry.sourceKind().name()))
                .toList());
        canonicalSchemaKinds = Set.copyOf(new TreeSet<>(canonicalSchemaKinds));
        differences = List.copyOf(differences.stream().sorted().toList());
    }

    public static CanonicalFeatureTargetRegistryV2 project(Set<String> canonicalSchemaKinds) {
        Objects.requireNonNull(canonicalSchemaKinds, "canonicalSchemaKinds");
        List<String> differences = new ArrayList<>();
        Set<String> expected = new TreeSet<>();
        CanonicalTerrainIntentV2.AUTHORING_KINDS.forEach(kind -> expected.add(kind.name()));
        Set<String> actual = new TreeSet<>(canonicalSchemaKinds);
        addDifferences(expected, actual, differences);

        Map<TerrainIntentV2.FeatureKind, Entry> dispositions = approvedDispositions();
        List<Entry> entries = new ArrayList<>();
        for (TerrainIntentV2.FeatureKind kind : TerrainIntentV2.FeatureKind.values()) {
            Entry entry = dispositions.get(kind);
            if (entry == null) {
                entry = new Entry(kind, Disposition.DIRECT, kind.name(),
                        LifecycleState.CURRENT_PUBLIC, OperationalMode.ENABLED);
            }
            entries.add(entry);
        }
        if (entries.size() != TerrainIntentV2.FeatureKind.values().length) {
            differences.add("registry source count mismatch");
        }
        return new CanonicalFeatureTargetRegistryV2(entries, actual, differences);
    }

    public void requireConsistent() {
        if (!differences.isEmpty()) {
            throw new IllegalStateException("canonical target registry differs:\n - "
                    + String.join("\n - ", differences));
        }
    }

    public Entry entry(TerrainIntentV2.FeatureKind sourceKind) {
        return entries.stream().filter(entry -> entry.sourceKind() == sourceKind)
                .findFirst().orElseThrow();
    }

    public CanonicalFeatureTargetRegistryV2 transition(
            TerrainIntentV2.FeatureKind sourceKind,
            LifecycleState target
    ) {
        Entry current = entry(sourceKind);
        if (current.disposition() == Disposition.DIRECT) {
            throw new IllegalArgumentException("direct canonical kind has no legacy lifecycle transition");
        }
        if (!current.lifecycleState().canTransitionTo(target)) {
            throw new IllegalArgumentException("lifecycle transition is not monotonic: "
                    + current.lifecycleState() + " -> " + target);
        }
        List<Entry> changed = entries.stream().map(entry -> entry.sourceKind() == sourceKind
                ? new Entry(entry.sourceKind(), entry.disposition(), entry.targetCarrier(), target,
                        entry.operationalMode()) : entry).toList();
        return new CanonicalFeatureTargetRegistryV2(changed, canonicalSchemaKinds, differences);
    }

    /** Operational rollback changes implementation availability, never lifecycle state. */
    public CanonicalFeatureTargetRegistryV2 withOperationalMode(
            TerrainIntentV2.FeatureKind sourceKind,
            OperationalMode mode
    ) {
        Objects.requireNonNull(mode, "mode");
        if (entry(sourceKind).disposition() == Disposition.DIRECT) {
            throw new IllegalArgumentException("direct canonical kind has no legacy operational mode");
        }
        List<Entry> changed = entries.stream().map(entry -> entry.sourceKind() == sourceKind
                ? new Entry(entry.sourceKind(), entry.disposition(), entry.targetCarrier(),
                        entry.lifecycleState(), mode) : entry).toList();
        return new CanonicalFeatureTargetRegistryV2(changed, canonicalSchemaKinds, differences);
    }

    public String documentationProjection() {
        requireConsistent();
        long direct = entries.stream().filter(entry -> entry.disposition() == Disposition.DIRECT).count();
        long subtype = entries.stream().filter(entry -> entry.disposition() == Disposition.PARENT_SUBTYPE).count();
        long alias = entries.stream().filter(entry -> entry.disposition() == Disposition.PARENT_ALIAS).count();
        long child = entries.stream().filter(entry -> entry.disposition() == Disposition.PARENT_CHILD).count();
        long overlay = entries.stream().filter(entry -> entry.disposition() == Disposition.PARENT_OVERLAY).count();
        String mappings = entries.stream().filter(entry -> entry.disposition() != Disposition.DIRECT)
                .map(entry -> "`" + entry.sourceKind().name() + "`→`" + entry.targetCarrier() + "`")
                .reduce((left, right) -> left + ", " + right).orElse("—");
        return String.join("\n",
                "- Contract: `" + CONTRACT_VERSION + "`",
                "- Projection: `CANONICAL_V2`; intentVersion=2",
                "- Compatibility sources: " + entries.size() + "; canonical authoring kinds: " + direct,
                "- Dispositions: direct=" + direct + ", parent-subtype=" + subtype
                        + ", parent-alias=" + alias + ", parent-child=" + child
                        + ", parent-overlay=" + overlay,
                "- Lifecycle: 14×`CURRENT_PUBLIC` (tagged release window not started)",
                "- Approved mappings: " + mappings,
                "- Canonical projection SHA-256: `" + projectionChecksum() + "`");
    }

    public String projectionChecksum() {
        String canonical = entries.stream().map(entry -> String.join("|",
                entry.sourceKind().name(), entry.disposition().name(), entry.targetCarrier(),
                entry.lifecycleState().name(), entry.operationalMode().name()))
                .reduce("", (left, right) -> left + right + "\n")
                + "schema=" + String.join(",", new TreeSet<>(canonicalSchemaKinds)) + "\n";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static Map<TerrainIntentV2.FeatureKind, Entry> approvedDispositions() {
        Map<TerrainIntentV2.FeatureKind, Entry> result = new EnumMap<>(TerrainIntentV2.FeatureKind.class);
        add(result, TerrainIntentV2.FeatureKind.MEANDERING_RIVER, Disposition.PARENT_SUBTYPE,
                "RIVER.morphology=MEANDERING");
        add(result, TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE, Disposition.PARENT_SUBTYPE,
                "MOUNTAIN_RANGE.profile=ALPINE");
        add(result, TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE, Disposition.PARENT_SUBTYPE,
                "MOUNTAIN_RANGE.profile=GLACIAL");
        add(result, TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO, Disposition.PARENT_SUBTYPE,
                "ARCHIPELAGO.origin=VOLCANIC");
        add(result, TerrainIntentV2.FeatureKind.MANGROVE_WETLAND, Disposition.PARENT_SUBTYPE,
                "MARSH.wetlandType=MANGROVE");
        add(result, TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS, Disposition.PARENT_ALIAS,
                "PLAIN.context=BACKSHORE");
        add(result, TerrainIntentV2.FeatureKind.BEDROCK_RIVER, Disposition.PARENT_SUBTYPE,
                "RIVER.channelSubtype=BEDROCK");
        add(result, TerrainIntentV2.FeatureKind.OXBOW_LAKE, Disposition.PARENT_SUBTYPE,
                "LAKE.origin=RIVER_CUTOFF");
        add(result, TerrainIntentV2.FeatureKind.LAGOON, Disposition.PARENT_CHILD, "CORAL_REEF.children.LAGOON");
        add(result, TerrainIntentV2.FeatureKind.REEF_PASS, Disposition.PARENT_CHILD, "CORAL_REEF.children.REEF_PASS");
        add(result, TerrainIntentV2.FeatureKind.VOLCANIC_CALDERA, Disposition.PARENT_CHILD,
                "VOLCANIC_OWNER.children.VOLCANIC_CALDERA");
        add(result, TerrainIntentV2.FeatureKind.LAVA_FLOW_FIELD, Disposition.PARENT_CHILD,
                "VOLCANIC_OWNER.children.LAVA_FLOW_FIELD");
        add(result, TerrainIntentV2.FeatureKind.GLACIAL_CIRQUE_FIELD, Disposition.PARENT_CHILD,
                "MOUNTAIN_RANGE.profile=GLACIAL.children.GLACIAL_CIRQUE_FIELD");
        add(result, TerrainIntentV2.FeatureKind.FLOODED_CAVE, Disposition.PARENT_OVERLAY,
                "CAVE_OWNER.children.FLOODED_CAVE");
        return result;
    }

    private static void add(
            Map<TerrainIntentV2.FeatureKind, Entry> result,
            TerrainIntentV2.FeatureKind source,
            Disposition disposition,
            String target
    ) {
        result.put(source, new Entry(source, disposition, target,
                LifecycleState.CURRENT_PUBLIC, OperationalMode.ENABLED));
    }

    private static void addDifferences(Set<String> expected, Set<String> actual, List<String> differences) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        missing.forEach(value -> differences.add("canonical Schema missing kind: " + value));
        Set<String> unknown = new TreeSet<>(actual);
        unknown.removeAll(expected);
        unknown.forEach(value -> differences.add("canonical Schema declares unapproved kind: " + value));
    }

    public enum Disposition { DIRECT, PARENT_SUBTYPE, PARENT_ALIAS, PARENT_CHILD, PARENT_OVERLAY }
    public enum OperationalMode { ENABLED, STOPPED }

    public enum LifecycleState {
        CURRENT_PUBLIC,
        DEPRECATED_AUTHORING,
        LEGACY_READABLE_ONLY;

        boolean canTransitionTo(LifecycleState target) {
            return target.ordinal() == ordinal() + 1;
        }
    }

    public record Entry(
            TerrainIntentV2.FeatureKind sourceKind,
            Disposition disposition,
            String targetCarrier,
            LifecycleState lifecycleState,
            OperationalMode operationalMode
    ) {
        public Entry {
            Objects.requireNonNull(sourceKind, "sourceKind");
            Objects.requireNonNull(disposition, "disposition");
            targetCarrier = Objects.requireNonNull(targetCarrier, "targetCarrier");
            Objects.requireNonNull(lifecycleState, "lifecycleState");
            Objects.requireNonNull(operationalMode, "operationalMode");
        }
    }
}
