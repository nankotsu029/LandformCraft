package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.core.v2.catalog.CurrentFeatureStateRegistryV2;
import com.github.nankotsu029.landformcraft.format.v2.catalog.FeatureSupportCatalogCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.DiagnosticIssueV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * V2-18-01 diagnostic severity / production gate contract.
 *
 * <p>The {@link DiagnosticBlueprintCompilerV2} records machine-readable {@link DiagnosticIssueV2}
 * entries on the sealed blueprint, but the export spine consumes none of them (V2-18 macro
 * foundation audit, item 4). Without an explicit {@code GATING} / {@code NON_GATING} classification
 * there is no way to describe which diagnostic rules a future export gate would reject on, nor to
 * lock the set of ERROR issues that are emitted today but silently ignored.</p>
 *
 * <p>This contract makes that classification explicit and canonical. In the current state every
 * blueprint diagnostic rule is {@code NON_GATING}: export still ignores all of them, so the contract
 * only describes reality — it does <em>not</em> fail-close anything. Later Tasks (V2-18-03 HARD
 * preflight gate) flip individual rules to {@code GATING} together with the export enforcement that
 * consumes them, so classification and runtime behaviour always move as one unit.</p>
 *
 * <p>The contract also owns the {@code production-connected} FeatureKind set, projected from the same
 * current-feature-state authority the production dispatch spine uses
 * ({@link CurrentFeatureStateRegistryV2}). The compiler consults it so the blanket
 * {@code v2.unsupported-capability} ERROR is no longer stamped onto kinds that already have a real
 * production export route.</p>
 *
 * <p>This type is a pure contract. It never gates, rejects, or mutates a blueprint; it only
 * classifies. Blueprint field and tile semantic checksums are therefore unaffected.</p>
 */
public final class DiagnosticGateContractV2 {
    public static final String CONTRACT_VERSION = "diagnostic-gate-contract-v1";

    /**
     * How the export spine would treat a diagnostic issue once it consumes this contract.
     * {@code GATING} issues (at ERROR severity) would reject before artifact publication;
     * {@code NON_GATING} issues are advisory and never block export.
     */
    public enum GateClass { GATING, NON_GATING }

    /**
     * Canonical classification of every {@link DiagnosticIssueV2} rule the diagnostic blueprint
     * compiler emits. Every rule is {@code NON_GATING} in the current state, matching the fact that
     * export gates on none of them. Adding a compiler-emitted rule without registering it here is a
     * contract gap that {@code DiagnosticBlueprintCompilerV2Test} detects.
     */
    private static final Map<String, GateClass> BUILT_IN_RULE_CLASSES;
    static {
        Map<String, GateClass> classes = new TreeMap<>();
        classes.put("v2.unsupported-capability", GateClass.NON_GATING);
        classes.put("v2.missing-validator-capability", GateClass.NON_GATING);
        classes.put("v2.missing-preview-capability", GateClass.NON_GATING);
        classes.put("v2.unsupported-constraint-map", GateClass.NON_GATING);
        classes.put("v2.unsupported-structure-capability", GateClass.NON_GATING);
        // V2-18-09 (ADR 0038 D8-1): the surface-baseline argument is ignored on a request with an
        // explicit macro foundation input. CLI-surface warning, advisory only; never blocks export.
        classes.put("v2.cli.surface-baseline-deprecated", GateClass.NON_GATING);
        BUILT_IN_RULE_CLASSES = Map.copyOf(classes);
    }

    private final Map<String, GateClass> ruleClasses;
    private final Set<TerrainIntentV2.FeatureKind> productionConnectedKinds;
    private final String contractChecksum;

    /** Builds the built-in contract from the compile-time catalog and sealed support catalog. */
    public static DiagnosticGateContractV2 builtIn() {
        return new DiagnosticGateContractV2(BUILT_IN_RULE_CLASSES, projectProductionConnectedKinds());
    }

    /**
     * Package-private constructor used by {@link #builtIn()} and by tests that need a variant
     * classification (for example to prove that a GATING rule at ERROR severity would be selected).
     */
    DiagnosticGateContractV2(
            Map<String, GateClass> ruleClasses,
            Set<TerrainIntentV2.FeatureKind> productionConnectedKinds
    ) {
        Objects.requireNonNull(ruleClasses, "ruleClasses");
        Objects.requireNonNull(productionConnectedKinds, "productionConnectedKinds");
        Map<String, GateClass> copied = new TreeMap<>();
        for (Map.Entry<String, GateClass> entry : ruleClasses.entrySet()) {
            String ruleId = entry.getKey();
            if (ruleId == null || !ruleId.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*")) {
                throw new IllegalArgumentException("diagnostic gate rule id must be a qualified id: " + ruleId);
            }
            copied.put(ruleId, Objects.requireNonNull(entry.getValue(), "gate class"));
        }
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("diagnostic gate contract requires at least one rule");
        }
        this.ruleClasses = Map.copyOf(copied);
        this.productionConnectedKinds = productionConnectedKinds.isEmpty()
                ? EnumSet.noneOf(TerrainIntentV2.FeatureKind.class)
                : EnumSet.copyOf(productionConnectedKinds);
        this.contractChecksum = checksum(canonicalLines(this.ruleClasses, this.productionConnectedKinds));
    }

    /** True when {@code kind} has a real production export route and should not carry the blanket unsupported-capability ERROR. */
    public boolean isProductionConnected(TerrainIntentV2.FeatureKind kind) {
        Objects.requireNonNull(kind, "kind");
        return productionConnectedKinds.contains(kind);
    }

    /** Immutable production-connected FeatureKind set, deterministically ordered by enum name. */
    public Set<TerrainIntentV2.FeatureKind> productionConnectedKinds() {
        Set<TerrainIntentV2.FeatureKind> ordered = new TreeSet<>(Comparator.comparing(Enum::name));
        ordered.addAll(productionConnectedKinds);
        return ordered;
    }

    /** True when the rule id is part of this contract's classification table. */
    public boolean isRegistered(String ruleId) {
        return ruleClasses.containsKey(Objects.requireNonNull(ruleId, "ruleId"));
    }

    /**
     * Classifies a rule id. Throws when the rule is not registered: every blueprint diagnostic rule
     * must be declared here so drift is caught rather than silently defaulting.
     */
    public GateClass classify(String ruleId) {
        GateClass gateClass = ruleClasses.get(Objects.requireNonNull(ruleId, "ruleId"));
        if (gateClass == null) {
            throw new IllegalArgumentException("unregistered diagnostic gate rule id: " + ruleId);
        }
        return gateClass;
    }

    /** Classifies an issue by its rule id. */
    public GateClass classify(DiagnosticIssueV2 issue) {
        return classify(Objects.requireNonNull(issue, "issue").ruleId());
    }

    /**
     * Whether an issue would gate export: only ERROR-severity issues whose rule is {@code GATING}
     * gate. WARNING and INFO issues never gate regardless of their rule class.
     */
    public boolean gates(DiagnosticIssueV2 issue) {
        Objects.requireNonNull(issue, "issue");
        return issue.severity() == DiagnosticIssueV2.Severity.ERROR
                && classify(issue) == GateClass.GATING;
    }

    /** The subset of {@code issues} that would gate export, in input order. Empty in the current state. */
    public List<DiagnosticIssueV2> gatingIssues(List<DiagnosticIssueV2> issues) {
        return issues.stream().filter(this::gates).toList();
    }

    /**
     * ERROR-severity issues that are emitted but do <em>not</em> gate export — the currently-ignored
     * ERROR list. Locking this set makes the diagnostic/export disconnect explicit and testable.
     */
    public List<DiagnosticIssueV2> ignoredErrorIssues(List<DiagnosticIssueV2> issues) {
        return issues.stream()
                .filter(issue -> issue.severity() == DiagnosticIssueV2.Severity.ERROR && !gates(issue))
                .toList();
    }

    /** Immutable rule id → gate class table, deterministically ordered by rule id. */
    public Map<String, GateClass> ruleClasses() {
        return new LinkedHashMap<>(new TreeMap<>(ruleClasses));
    }

    public String contractChecksum() {
        return contractChecksum;
    }

    private static Set<TerrainIntentV2.FeatureKind> projectProductionConnectedKinds() {
        BuiltInLandformModuleCatalogV2 modules = new BuiltInLandformModuleCatalogV2();
        Set<String> compatibilityKinds = new TreeSet<>();
        for (TerrainIntentV2.FeatureKind kind : TerrainIntentV2.FeatureKind.values()) {
            compatibilityKinds.add(kind.name());
        }
        CurrentFeatureStateRegistryV2 source = CurrentFeatureStateRegistryV2.project(
                compatibilityKinds,
                new FeatureSupportCatalogCodecV2().builtInSealed(),
                modules.featureBindings(),
                modules.modules());
        source.requireConsistent();
        Set<TerrainIntentV2.FeatureKind> connected = EnumSet.noneOf(TerrainIntentV2.FeatureKind.class);
        for (CurrentFeatureStateRegistryV2.Entry entry : source.entries()) {
            if (entry.currentState() == CurrentFeatureStateRegistryV2.CurrentState.PRODUCTION_CONNECTED) {
                connected.add(entry.featureKind());
            }
        }
        return connected;
    }

    private static List<String> canonicalLines(
            Map<String, GateClass> ruleClasses,
            Set<TerrainIntentV2.FeatureKind> productionConnectedKinds
    ) {
        List<String> lines = new ArrayList<>();
        lines.add(CONTRACT_VERSION);
        ruleClasses.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> lines.add("rule|" + entry.getKey() + "|" + entry.getValue().name()));
        productionConnectedKinds.stream()
                .map(Enum::name)
                .sorted()
                .forEach(name -> lines.add("production-connected|" + name));
        return lines;
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
