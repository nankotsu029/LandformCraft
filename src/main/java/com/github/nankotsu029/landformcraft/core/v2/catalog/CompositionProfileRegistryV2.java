package com.github.nankotsu029.landformcraft.core.v2.catalog;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.composition.CompositionProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.composition.CompositionStageV2;
import com.github.nankotsu029.landformcraft.model.v2.composition.ParentPolicyV2;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * ADR 0038 D4/D5-5 per-kind composition profile registration (V2-18-09).
 *
 * <p>The registry assigns every {@code TerrainIntentV2.FeatureKind} a {@link CompositionProfileV2}
 * following the ADR's lookup rule: legacy alias / subtype kinds do not carry their own profile —
 * they inherit the profile of their ADR 0036 canonical carrier
 * ({@link CanonicalFeatureTargetRegistryV2}), so a kind's profile can never diverge from its
 * carrier's. Parent-child / parent-overlay kinds keep their own explicit registration because the
 * ADR table assigns them distinct parent policies.</p>
 *
 * <p>Entries are two-tiered exactly as ADR 0038 D4 fixes them: the six {@code NORMATIVE} kinds
 * (the four production-connected coastal modifiers plus the ADR 0037 adapter-connected
 * {@code PLAIN} / {@code HILL_RANGE} producers) are confirmed by the ADR approval; every other kind
 * is {@code PROVISIONAL} and is confirmed (or amended) by its own V2-15 production wiring Task —
 * the "composition role registration" the V2-15 stage gate requires.</p>
 */
public final class CompositionProfileRegistryV2 {
    public static final String CONTRACT_VERSION = "composition-profile-registry-v1";

    /** ADR 0038 D4 registration tier: NORMATIVE rows are ADR-confirmed, PROVISIONAL rows are not yet. */
    public enum Confidence { NORMATIVE, PROVISIONAL }

    /** One kind's resolved registration. {@code carrierInherited} marks alias/subtype rows. */
    public record Registration(
            TerrainIntentV2.FeatureKind kind,
            CompositionProfileV2 profile,
            Confidence confidence,
            boolean carrierInherited
    ) {
        public Registration {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(confidence, "confidence");
        }
    }

    private static final Set<TerrainIntentV2.FeatureKind> NORMATIVE_KINDS = EnumSet.of(
            TerrainIntentV2.FeatureKind.SANDY_BEACH,
            TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
            TerrainIntentV2.FeatureKind.HARBOR_BASIN,
            TerrainIntentV2.FeatureKind.ROCKY_CAPE,
            TerrainIntentV2.FeatureKind.PLAIN,
            TerrainIntentV2.FeatureKind.HILL_RANGE);

    private final Map<TerrainIntentV2.FeatureKind, Registration> registrations;

    private CompositionProfileRegistryV2(Map<TerrainIntentV2.FeatureKind, Registration> registrations) {
        this.registrations = Map.copyOf(registrations);
    }

    public static CompositionProfileRegistryV2 builtIn() {
        Map<TerrainIntentV2.FeatureKind, CompositionProfileV2> direct = directProfiles();
        Map<TerrainIntentV2.FeatureKind, Registration> registrations =
                new EnumMap<>(TerrainIntentV2.FeatureKind.class);
        CanonicalFeatureTargetRegistryV2 carriers = CanonicalFeatureTargetRegistryV2.project(
                canonicalKindNames());
        for (TerrainIntentV2.FeatureKind kind : TerrainIntentV2.FeatureKind.values()) {
            CanonicalFeatureTargetRegistryV2.Entry entry = carriers.entry(kind);
            boolean inherited = entry.disposition() == CanonicalFeatureTargetRegistryV2.Disposition.PARENT_ALIAS
                    || entry.disposition() == CanonicalFeatureTargetRegistryV2.Disposition.PARENT_SUBTYPE;
            CompositionProfileV2 profile;
            if (inherited) {
                TerrainIntentV2.FeatureKind carrier = carrierKind(entry.targetCarrier());
                profile = direct.get(carrier);
                if (profile == null) {
                    throw new IllegalStateException("composition carrier has no direct profile: " + carrier);
                }
            } else {
                profile = direct.get(kind);
                if (profile == null) {
                    throw new IllegalStateException("feature kind has no composition profile: " + kind);
                }
            }
            Confidence confidence = NORMATIVE_KINDS.contains(kind)
                    ? Confidence.NORMATIVE : Confidence.PROVISIONAL;
            registrations.put(kind, new Registration(kind, profile, confidence, inherited));
        }
        return new CompositionProfileRegistryV2(registrations);
    }

    /** Resolved registration for one kind (carrier lookup already applied). */
    public Registration registration(TerrainIntentV2.FeatureKind kind) {
        Registration registration = registrations.get(Objects.requireNonNull(kind, "kind"));
        if (registration == null) {
            throw new IllegalArgumentException("unregistered composition profile kind: " + kind);
        }
        return registration;
    }

    /** Resolved composition profile for one kind. */
    public CompositionProfileV2 profile(TerrainIntentV2.FeatureKind kind) {
        return registration(kind).profile();
    }

    /** Foundation-eligible kinds (ADR 0038 D4: 17 including carrier-inherited entries). */
    public Set<TerrainIntentV2.FeatureKind> foundationEligibleKinds() {
        Set<TerrainIntentV2.FeatureKind> eligible = new TreeSet<>(Comparator.comparing(Enum::name));
        registrations.values().stream()
                .filter(registration -> registration.profile().foundationEligible())
                .forEach(registration -> eligible.add(registration.kind()));
        return eligible;
    }

    /**
     * ADR 0038 D4 direct table for every kind that is not an alias/subtype of a canonical carrier.
     * Alias/subtype kinds are deliberately absent so they can only resolve through their carrier.
     */
    private static Map<TerrainIntentV2.FeatureKind, CompositionProfileV2> directProfiles() {
        Map<TerrainIntentV2.FeatureKind, CompositionProfileV2> direct =
                new EnumMap<>(TerrainIntentV2.FeatureKind.class);
        // NORMATIVE: production-connected coastal modifiers + ADR 0037 adapter-connected producers.
        direct.put(TerrainIntentV2.FeatureKind.SANDY_BEACH, modifier());
        direct.put(TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR, modifier());
        direct.put(TerrainIntentV2.FeatureKind.HARBOR_BASIN, modifier());
        direct.put(TerrainIntentV2.FeatureKind.ROCKY_CAPE, modifier());
        direct.put(TerrainIntentV2.FeatureKind.PLAIN, producer());
        direct.put(TerrainIntentV2.FeatureKind.HILL_RANGE, producer());
        // PROVISIONAL foundation producers (areal land-water + base elevation without another owner).
        direct.put(TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE, producer());
        direct.put(TerrainIntentV2.FeatureKind.VALLEY, producer());
        direct.put(TerrainIntentV2.FeatureKind.PLATEAU, producer());
        direct.put(TerrainIntentV2.FeatureKind.SINGLE_ISLAND, producer());
        direct.put(TerrainIntentV2.FeatureKind.ARCHIPELAGO, producer());
        direct.put(TerrainIntentV2.FeatureKind.VOLCANIC_CONE, producer());
        direct.put(TerrainIntentV2.FeatureKind.OCEAN_BASIN, producer());
        direct.put(TerrainIntentV2.FeatureKind.CONTINENTAL_SHELF, producer());
        direct.put(TerrainIntentV2.FeatureKind.CONTINENTAL_SLOPE, producer());
        direct.put(TerrainIntentV2.FeatureKind.ICE_CAP, producer());
        direct.put(TerrainIntentV2.FeatureKind.ICE_SHEET, producer());
        // PROVISIONAL surface modifiers over an existing foundation.
        direct.put(TerrainIntentV2.FeatureKind.ABYSSAL_PLAIN, modifier());
        direct.put(TerrainIntentV2.FeatureKind.SEAMOUNT, modifier());
        direct.put(TerrainIntentV2.FeatureKind.ROCKY_COAST, modifier());
        direct.put(TerrainIntentV2.FeatureKind.SEA_CLIFF, modifier());
        direct.put(TerrainIntentV2.FeatureKind.FJORD, modifier());
        direct.put(TerrainIntentV2.FeatureKind.RIVER, modifier());
        direct.put(TerrainIntentV2.FeatureKind.LAKE, modifier());
        direct.put(TerrainIntentV2.FeatureKind.DELTA, modifier());
        direct.put(TerrainIntentV2.FeatureKind.SPRING, modifier());
        direct.put(TerrainIntentV2.FeatureKind.KARST_SPRING, modifier());
        direct.put(TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK, modifier());
        direct.put(TerrainIntentV2.FeatureKind.FLOODPLAIN, modifier());
        direct.put(TerrainIntentV2.FeatureKind.MARSH, modifier());
        direct.put(TerrainIntentV2.FeatureKind.CANYON, modifier());
        direct.put(TerrainIntentV2.FeatureKind.SUBMARINE_CANYON, modifier());
        direct.put(TerrainIntentV2.FeatureKind.ESCARPMENT, modifier());
        direct.put(TerrainIntentV2.FeatureKind.MORAINE_FIELD, modifier());
        direct.put(TerrainIntentV2.FeatureKind.OUTWASH_PLAIN, modifier());
        direct.put(TerrainIntentV2.FeatureKind.VALLEY_GLACIER, modifier());
        direct.put(TerrainIntentV2.FeatureKind.CORAL_REEF, modifier());
        // Multi-stage standalone kinds (taxonomy §3.7 / §3.4).
        direct.put(TerrainIntentV2.FeatureKind.WATERFALL, profile(false, ParentPolicyV2.STANDALONE,
                CompositionStageV2.SURFACE_MODIFICATION, CompositionStageV2.VOLUME_OPERATION,
                CompositionStageV2.FLUID_OPERATION));
        direct.put(TerrainIntentV2.FeatureKind.CAVE_ENTRANCE, profile(false, ParentPolicyV2.STANDALONE,
                CompositionStageV2.SURFACE_MODIFICATION, CompositionStageV2.VOLUME_OPERATION));
        direct.put(TerrainIntentV2.FeatureKind.SINKHOLE, profile(false, ParentPolicyV2.STANDALONE,
                CompositionStageV2.SURFACE_MODIFICATION, CompositionStageV2.VOLUME_OPERATION));
        direct.put(TerrainIntentV2.FeatureKind.UNDERGROUND_RIVER, profile(false, ParentPolicyV2.STANDALONE,
                CompositionStageV2.VOLUME_OPERATION, CompositionStageV2.FLUID_OPERATION));
        // Volume-only standalone kinds.
        direct.put(TerrainIntentV2.FeatureKind.CAVE_NETWORK, volume());
        direct.put(TerrainIntentV2.FeatureKind.LUSH_CAVE, volume());
        direct.put(TerrainIntentV2.FeatureKind.OVERHANG, volume());
        direct.put(TerrainIntentV2.FeatureKind.SKY_ISLAND_GROUP, volume());
        direct.put(TerrainIntentV2.FeatureKind.LAVA_TUBE, volume());
        // Parent-child / parent-overlay kinds carry explicit parent policies (ADR 0038 D4).
        direct.put(TerrainIntentV2.FeatureKind.LAGOON, profile(false, ParentPolicyV2.PARENT_REQUIRED,
                CompositionStageV2.SURFACE_MODIFICATION));
        direct.put(TerrainIntentV2.FeatureKind.REEF_PASS, profile(false, ParentPolicyV2.PARENT_REQUIRED,
                CompositionStageV2.SURFACE_MODIFICATION));
        direct.put(TerrainIntentV2.FeatureKind.VOLCANIC_CALDERA, profile(false, ParentPolicyV2.PARENT_REQUIRED,
                CompositionStageV2.SURFACE_MODIFICATION));
        direct.put(TerrainIntentV2.FeatureKind.GLACIAL_CIRQUE_FIELD, profile(false, ParentPolicyV2.PARENT_REQUIRED,
                CompositionStageV2.SURFACE_MODIFICATION));
        direct.put(TerrainIntentV2.FeatureKind.LAVA_FLOW_FIELD, profile(false, ParentPolicyV2.PARENT_REQUIRED,
                CompositionStageV2.SURFACE_MODIFICATION, CompositionStageV2.VOLUME_OPERATION));
        direct.put(TerrainIntentV2.FeatureKind.FLOODED_CAVE, profile(false, ParentPolicyV2.PARENT_BOUND_OVERLAY,
                CompositionStageV2.FLUID_OPERATION));
        return direct;
    }

    private static CompositionProfileV2 producer() {
        return profile(true, ParentPolicyV2.STANDALONE, CompositionStageV2.FOUNDATION);
    }

    private static CompositionProfileV2 modifier() {
        return profile(false, ParentPolicyV2.STANDALONE, CompositionStageV2.SURFACE_MODIFICATION);
    }

    private static CompositionProfileV2 volume() {
        return profile(false, ParentPolicyV2.STANDALONE, CompositionStageV2.VOLUME_OPERATION);
    }

    private static CompositionProfileV2 profile(
            boolean foundationEligible,
            ParentPolicyV2 parentPolicy,
            CompositionStageV2 first,
            CompositionStageV2... rest
    ) {
        return new CompositionProfileV2(foundationEligible, EnumSet.of(first, rest), parentPolicy);
    }

    /** Carrier prefix of an ADR 0036 target-carrier expression, e.g. {@code PLAIN.context=BACKSHORE}. */
    private static TerrainIntentV2.FeatureKind carrierKind(String targetCarrier) {
        int dot = targetCarrier.indexOf('.');
        String name = dot < 0 ? targetCarrier : targetCarrier.substring(0, dot);
        return TerrainIntentV2.FeatureKind.valueOf(name);
    }

    private static Set<String> canonicalKindNames() {
        Set<String> names = new TreeSet<>();
        com.github.nankotsu029.landformcraft.model.v2.CanonicalTerrainIntentV2.AUTHORING_KINDS
                .forEach(kind -> names.add(kind.name()));
        return names;
    }
}
