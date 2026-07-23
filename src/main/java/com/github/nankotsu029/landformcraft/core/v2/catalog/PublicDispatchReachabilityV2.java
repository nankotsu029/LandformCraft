package com.github.nankotsu029.landformcraft.core.v2.catalog;

import com.github.nankotsu029.landformcraft.core.v2.export.ProductionDispatchRegistryV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * V2-19-01 read-only projection separating three axes the 2026-07-23 cross-cutting audit found
 * conflated in operator-facing displays:
 *
 * <ol>
 *   <li><b>Feature Support Catalog support columns</b> ({@code export}, {@code offline_generate},
 *       …) — capability maturity per entry, owned by the sealed catalog;</li>
 *   <li><b>public dispatch reachability</b> — whether an intent declaring the kind can select a
 *       production pipeline at all, owned by {@link ProductionDispatchRegistryV2};</li>
 *   <li><b>block materialization</b> — whether the routed kind changes the final canonical block
 *       stream, owned by the intent-conformance portfolio's block-effect measurement.</li>
 * </ol>
 *
 * <p>None of the three implies another: {@code export == SUPPORTED} does not make a kind publicly
 * dispatchable, a dispatch route does not by itself materialize blocks (as the V2-15-10 RIVER route
 * did not until {@code V2-19-05} carved and filled its bed), and an intentional no-op route
 * (capability spine smoke) is never admissible as Feature-promotion evidence. This class is
 * display/report-only: it adds no gate, changes no dispatch behavior and no checksum of the registry
 * or the sealed catalog.</p>
 */
public final class PublicDispatchReachabilityV2 {
    public static final String CONTRACT_VERSION = "public-dispatch-reachability-v1";

    /** Whether a public intent declaring the kind can select a production pipeline. */
    public enum ReachabilityV2 { PRODUCTION_CONNECTED, OFFLINE_PRODUCTION, CONTRACT_ONLY, NOT_PUBLICLY_DISPATCHABLE }

    /** Whether the reachable route changes the final canonical block stream. */
    public enum BlockMaterializationV2 { MATERIALIZED, PLAN_ONLY, NOT_APPLICABLE }

    /**
     * Offline-production kinds whose block materialization has been measured as non-empty from the
     * final canonical block stream by the intent-conformance portfolio. Adding a kind here requires
     * that portfolio block-effect evidence in the same change (V2-19-01 gate).
     *
     * <p>{@code V2-19-05} populated it first: the bed carve and water fill of a declared reach are
     * measured per kind from published Releases ({@code harbor-cove-64-honored-river} for
     * {@code RIVER}, {@code harbor-cove-64-honored-meander} for {@code MEANDERING_RIVER}), so
     * neither entry is inferred from the other even though both compile through one plan shape.
     * {@code V2-19-07} added {@code PLAIN}, whose macro foundation producer raises the land it owns
     * ({@code harbor-cove-64-honored-plain} against the same baseline).</p>
     */
    private static final Set<TerrainIntentV2.FeatureKind> MATERIALIZED_OFFLINE_KINDS = EnumSet.of(
            TerrainIntentV2.FeatureKind.RIVER,
            TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
            TerrainIntentV2.FeatureKind.PLAIN);

    public record EntryV2(
            TerrainIntentV2.FeatureKind kind,
            ReachabilityV2 reachability,
            String pipelineId,
            BlockMaterializationV2 blockMaterialization
    ) {
        public EntryV2 {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(reachability, "reachability");
            Objects.requireNonNull(pipelineId, "pipelineId");
            Objects.requireNonNull(blockMaterialization, "blockMaterialization");
            if (reachability == ReachabilityV2.NOT_PUBLICLY_DISPATCHABLE != pipelineId.isEmpty()) {
                throw new IllegalArgumentException(
                        "pipelineId must be empty exactly for unreachable kinds: " + kind);
            }
        }

        String canonicalLine() {
            return String.join("|",
                    kind.name(), reachability.name(), pipelineId, blockMaterialization.name());
        }
    }

    private final Map<TerrainIntentV2.FeatureKind, EntryV2> entries;
    private final String canonicalChecksum;

    private PublicDispatchReachabilityV2(Map<TerrainIntentV2.FeatureKind, EntryV2> entries) {
        this.entries = Map.copyOf(entries);
        this.canonicalChecksum = checksum(canonicalLines());
    }

    public static PublicDispatchReachabilityV2 builtIn() {
        return of(ProductionDispatchRegistryV2.builtIn());
    }

    public static PublicDispatchReachabilityV2 of(ProductionDispatchRegistryV2 registry) {
        Objects.requireNonNull(registry, "registry");
        Map<TerrainIntentV2.FeatureKind, EntryV2> entries =
                new EnumMap<>(TerrainIntentV2.FeatureKind.class);
        for (ProductionDispatchRegistryV2.Route route : registry.routes()) {
            ReachabilityV2 reachability = switch (route.routeClass()) {
                case PRODUCTION_CONNECTED -> ReachabilityV2.PRODUCTION_CONNECTED;
                case OFFLINE_PRODUCTION -> ReachabilityV2.OFFLINE_PRODUCTION;
            };
            entries.put(route.featureKind(), new EntryV2(
                    route.featureKind(), reachability, route.pipelineId(),
                    materializationOf(route)));
        }
        for (Map.Entry<TerrainIntentV2.FeatureKind, String> contractOnly
                : registry.contractOnlyKinds().entrySet()) {
            if (entries.containsKey(contractOnly.getKey())) {
                throw new IllegalArgumentException(
                        "kind is both routed and contract-only: " + contractOnly.getKey());
            }
            entries.put(contractOnly.getKey(), new EntryV2(
                    contractOnly.getKey(), ReachabilityV2.CONTRACT_ONLY, contractOnly.getValue(),
                    BlockMaterializationV2.NOT_APPLICABLE));
        }
        for (TerrainIntentV2.FeatureKind kind : TerrainIntentV2.FeatureKind.values()) {
            entries.putIfAbsent(kind, new EntryV2(
                    kind, ReachabilityV2.NOT_PUBLICLY_DISPATCHABLE, "",
                    BlockMaterializationV2.NOT_APPLICABLE));
        }
        return new PublicDispatchReachabilityV2(entries);
    }

    private static BlockMaterializationV2 materializationOf(ProductionDispatchRegistryV2.Route route) {
        // PRODUCTION_CONNECTED means the coastal surface pipeline owns the final surface tile
        // stream itself; an OFFLINE_PRODUCTION route stays PLAN_ONLY until portfolio block-effect
        // evidence admits it into MATERIALIZED_OFFLINE_KINDS.
        return switch (route.routeClass()) {
            case PRODUCTION_CONNECTED -> BlockMaterializationV2.MATERIALIZED;
            case OFFLINE_PRODUCTION -> MATERIALIZED_OFFLINE_KINDS.contains(route.featureKind())
                    ? BlockMaterializationV2.MATERIALIZED
                    : BlockMaterializationV2.PLAN_ONLY;
        };
    }

    /** All 60 compatibility kinds, sorted by kind name. */
    public List<EntryV2> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(entry -> entry.kind().name()))
                .toList();
    }

    public EntryV2 entry(TerrainIntentV2.FeatureKind kind) {
        return Objects.requireNonNull(entries.get(Objects.requireNonNull(kind, "kind")),
                () -> "no reachability entry for " + kind);
    }

    public Set<TerrainIntentV2.FeatureKind> kindsWith(ReachabilityV2 reachability) {
        Objects.requireNonNull(reachability, "reachability");
        Set<TerrainIntentV2.FeatureKind> result = EnumSet.noneOf(TerrainIntentV2.FeatureKind.class);
        for (EntryV2 entry : entries.values()) {
            if (entry.reachability() == reachability) {
                result.add(entry.kind());
            }
        }
        return result;
    }

    /** Checked markdown block for {@code current-feature-state-machine-registry.md} (CI-pinned). */
    public String documentationProjection() {
        return String.join("\n",
                "- Contract: `" + CONTRACT_VERSION + "`",
                "- Axes: Feature Support Catalog support columns, public dispatch reachability, and"
                        + " block materialization are independent; none implies another, and an"
                        + " intentional no-op route is never Feature-promotion evidence",
                "- Reachability: production-connected="
                        + kindsWith(ReachabilityV2.PRODUCTION_CONNECTED).size()
                        + ", offline-production=" + kindsWith(ReachabilityV2.OFFLINE_PRODUCTION).size()
                        + ", contract-only=" + kindsWith(ReachabilityV2.CONTRACT_ONLY).size()
                        + ", not-publicly-dispatchable="
                        + kindsWith(ReachabilityV2.NOT_PUBLICLY_DISPATCHABLE).size(),
                "- Production-connected (materialized surface owners): "
                        + names(kindsWith(ReachabilityV2.PRODUCTION_CONNECTED)),
                "- Offline-production, block-effect measured by the portfolio: "
                        + names(offlineKindsWith(BlockMaterializationV2.MATERIALIZED)),
                "- Offline-production, plan-only until portfolio block-effect evidence: "
                        + names(offlineKindsWith(BlockMaterializationV2.PLAN_ONLY)),
                "- Contract-only diagnostic inputs: " + names(kindsWith(ReachabilityV2.CONTRACT_ONLY)),
                "- Canonical projection SHA-256: `" + canonicalChecksum + "`");
    }

    /** Offline-production kinds displayed with one block-materialization state. */
    public Set<TerrainIntentV2.FeatureKind> offlineKindsWith(BlockMaterializationV2 materialization) {
        Objects.requireNonNull(materialization, "materialization");
        Set<TerrainIntentV2.FeatureKind> result = EnumSet.noneOf(TerrainIntentV2.FeatureKind.class);
        for (EntryV2 entry : entries.values()) {
            if (entry.reachability() == ReachabilityV2.OFFLINE_PRODUCTION
                    && entry.blockMaterialization() == materialization) {
                result.add(entry.kind());
            }
        }
        return result;
    }

    private static String names(Set<TerrainIntentV2.FeatureKind> kinds) {
        return kinds.stream()
                .map(kind -> "`" + kind.name() + "`")
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("(none)");
    }

    public List<String> canonicalLines() {
        List<String> lines = new ArrayList<>();
        lines.add(CONTRACT_VERSION);
        entries().stream().map(EntryV2::canonicalLine).forEach(lines::add);
        return lines;
    }

    public String canonicalChecksum() {
        return canonicalChecksum;
    }

    private static String checksum(List<String> lines) {
        String canonical = String.join("\n", lines) + "\n";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format(Locale.ROOT, "%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
