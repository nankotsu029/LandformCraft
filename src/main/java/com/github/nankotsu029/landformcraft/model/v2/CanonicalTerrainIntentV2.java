package com.github.nankotsu029.landformcraft.model.v2;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Active canonical TerrainIntent v2 authoring projection (ADR 0036).
 *
 * <p>The historic {@link TerrainIntentV2} remains the immutable LEGACY_V2 compatibility model.
 * This record keeps the same common value types while representing the fourteen legacy public
 * identifiers as typed parent specializations or nested children. It deliberately contains no
 * generator, Release, provider, or Paper capability.</p>
 */
public record CanonicalTerrainIntentV2(
        int intentVersion,
        FeatureProjection featureProjection,
        String intentId,
        String theme,
        TerrainIntentV2.CoordinateSystem coordinateSystem,
        List<Feature> features,
        List<TerrainIntentV2.Relation> relations,
        List<TerrainIntentV2.Constraint> constraints,
        TerrainIntentV2.EnvironmentDescriptor environment,
        List<TerrainIntentV2.ConstraintMapBinding> mapReferences,
        List<TerrainIntentV2.StructureRequest> structures,
        TerrainIntentV2.Provenance provenance
) {
    public static final int VERSION = 2;
    public static final int MAXIMUM_TOTAL_FEATURES = 256;
    public static final int MAXIMUM_CHILDREN_PER_FEATURE = 64;
    private static final Comparator<String> UTF8_ORDER = (left, right) ->
            compareBytes(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));

    public static final Set<TerrainIntentV2.FeatureKind> LEGACY_SOURCE_KINDS = Set.copyOf(EnumSet.of(
            TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
            TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE,
            TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE,
            TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO,
            TerrainIntentV2.FeatureKind.MANGROVE_WETLAND,
            TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS,
            TerrainIntentV2.FeatureKind.BEDROCK_RIVER,
            TerrainIntentV2.FeatureKind.OXBOW_LAKE,
            TerrainIntentV2.FeatureKind.LAGOON,
            TerrainIntentV2.FeatureKind.REEF_PASS,
            TerrainIntentV2.FeatureKind.VOLCANIC_CALDERA,
            TerrainIntentV2.FeatureKind.LAVA_FLOW_FIELD,
            TerrainIntentV2.FeatureKind.GLACIAL_CIRQUE_FIELD,
            TerrainIntentV2.FeatureKind.FLOODED_CAVE));

    public static final Set<TerrainIntentV2.FeatureKind> AUTHORING_KINDS;

    static {
        EnumSet<TerrainIntentV2.FeatureKind> kinds = EnumSet.allOf(TerrainIntentV2.FeatureKind.class);
        kinds.removeAll(LEGACY_SOURCE_KINDS);
        AUTHORING_KINDS = Set.copyOf(kinds);
    }

    public CanonicalTerrainIntentV2 {
        if (intentVersion != VERSION) {
            throw new IllegalArgumentException("intentVersion must be exactly 2");
        }
        if (featureProjection != FeatureProjection.CANONICAL_V2) {
            throw new IllegalArgumentException("canonical intent requires CANONICAL_V2 projection");
        }
        intentId = requireSlug(intentId, "intentId");
        theme = requireText(theme, "theme", 1_000);
        Objects.requireNonNull(coordinateSystem, "coordinateSystem");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(provenance, "provenance");
        features = sorted(features, "features", MAXIMUM_TOTAL_FEATURES, Feature::id);
        relations = sorted(relations, "relations", 512, TerrainIntentV2.Relation::id);
        constraints = sorted(constraints, "constraints", 512, TerrainIntentV2.Constraint::id);
        mapReferences = sorted(mapReferences, "mapReferences", 32, TerrainIntentV2.ConstraintMapBinding::id);
        structures = sorted(structures, "structures", 64, TerrainIntentV2.StructureRequest::id);

        List<TerrainIntentV2.Feature> flattened = new ArrayList<>();
        for (Feature feature : features) {
            flattened.add(feature.legacyValidationFeature());
            for (Child child : feature.children()) {
                flattened.add(child.legacyValidationFeature());
            }
        }
        if (flattened.size() > MAXIMUM_TOTAL_FEATURES) {
            throw new IllegalArgumentException("canonical parent and child count exceeds 256");
        }
        // Reuse the frozen legacy semantic validation with children flattened only for validation.
        // Serialization never exposes this projection and therefore cannot reinterpret a document.
        new TerrainIntentV2(intentVersion, intentId, theme, coordinateSystem, flattened, relations,
                constraints, environment, mapReferences, structures, provenance);
    }

    public enum FeatureProjection { LEGACY_V2, CANONICAL_V2 }

    public enum Morphology { DEFAULT, MEANDERING }
    public enum ChannelSubtype { DEFAULT, BEDROCK }
    public enum MountainProfile { DEFAULT, ALPINE, GLACIAL }
    public enum ArchipelagoOrigin { DEFAULT, VOLCANIC }
    public enum WetlandType { DEFAULT, MANGROVE }
    public enum PlainContext { DEFAULT, BACKSHORE }
    public enum LakeOrigin { DEFAULT, RIVER_CUTOFF }

    public enum ChildKind {
        LAGOON(TerrainIntentV2.FeatureKind.LAGOON),
        REEF_PASS(TerrainIntentV2.FeatureKind.REEF_PASS),
        VOLCANIC_CALDERA(TerrainIntentV2.FeatureKind.VOLCANIC_CALDERA),
        LAVA_FLOW_FIELD(TerrainIntentV2.FeatureKind.LAVA_FLOW_FIELD),
        GLACIAL_CIRQUE_FIELD(TerrainIntentV2.FeatureKind.GLACIAL_CIRQUE_FIELD),
        FLOODED_CAVE(TerrainIntentV2.FeatureKind.FLOODED_CAVE);

        private final TerrainIntentV2.FeatureKind legacyKind;

        ChildKind(TerrainIntentV2.FeatureKind legacyKind) {
            this.legacyKind = legacyKind;
        }

        public TerrainIntentV2.FeatureKind legacyKind() {
            return legacyKind;
        }

        public static ChildKind fromLegacy(TerrainIntentV2.FeatureKind kind) {
            for (ChildKind value : values()) {
                if (value.legacyKind == kind) {
                    return value;
                }
            }
            throw new IllegalArgumentException("not a canonical child source kind: " + kind);
        }
    }

    public sealed interface Parameters permits UnchangedParameters, RiverParameters,
            MountainRangeParameters, ArchipelagoParameters, MarshParameters, PlainParameters,
            LakeParameters {
        TerrainIntentV2.FeatureKind validationKind();
        TerrainIntentV2.FeatureParameters validationParameters();
        TerrainIntentV2.FeatureKind migratedSourceKind();
    }

    public record UnchangedParameters(
            TerrainIntentV2.FeatureKind validationKind,
            TerrainIntentV2.FeatureParameters validationParameters
    ) implements Parameters {
        public UnchangedParameters {
            Objects.requireNonNull(validationKind, "validationKind");
            Objects.requireNonNull(validationParameters, "validationParameters");
            if (!AUTHORING_KINDS.contains(validationKind)) {
                throw new IllegalArgumentException("legacy source kind is not canonical authoring: " + validationKind);
            }
        }

        @Override public TerrainIntentV2.FeatureKind migratedSourceKind() { return null; }
    }

    public record RiverParameters(
            Morphology morphology,
            ChannelSubtype channelSubtype,
            TerrainIntentV2.RiverParameters defaultParameters,
            TerrainIntentV2.MeanderingRiverParameters meanderingParameters
    ) implements Parameters {
        public RiverParameters {
            Objects.requireNonNull(morphology, "morphology");
            Objects.requireNonNull(channelSubtype, "channelSubtype");
            boolean normal = morphology == Morphology.DEFAULT && channelSubtype == ChannelSubtype.DEFAULT;
            boolean meandering = morphology == Morphology.MEANDERING && channelSubtype == ChannelSubtype.DEFAULT;
            boolean bedrock = morphology == Morphology.DEFAULT && channelSubtype == ChannelSubtype.BEDROCK;
            if (!(normal || meandering || bedrock)) {
                throw new IllegalArgumentException("RIVER specialization combination is not approved");
            }
            if (normal != (defaultParameters != null)
                    || meandering != (meanderingParameters != null)) {
                throw new IllegalArgumentException("RIVER discriminator and typed payload do not match");
            }
        }

        @Override public TerrainIntentV2.FeatureKind validationKind() {
            if (morphology == Morphology.MEANDERING) return TerrainIntentV2.FeatureKind.MEANDERING_RIVER;
            if (channelSubtype == ChannelSubtype.BEDROCK) return TerrainIntentV2.FeatureKind.BEDROCK_RIVER;
            return TerrainIntentV2.FeatureKind.RIVER;
        }

        @Override public TerrainIntentV2.FeatureParameters validationParameters() {
            if (morphology == Morphology.MEANDERING) return meanderingParameters;
            if (channelSubtype == ChannelSubtype.BEDROCK) return new TerrainIntentV2.NoParameters();
            return defaultParameters;
        }

        @Override public TerrainIntentV2.FeatureKind migratedSourceKind() {
            return validationKind() == TerrainIntentV2.FeatureKind.RIVER ? null : validationKind();
        }
    }

    public record MountainRangeParameters(
            MountainProfile profile,
            TerrainIntentV2.MountainRangeParameters defaultParameters,
            TerrainIntentV2.MountainParameters profileParameters
    ) implements Parameters {
        public MountainRangeParameters {
            Objects.requireNonNull(profile, "profile");
            if ((profile == MountainProfile.DEFAULT) != (defaultParameters != null)
                    || (profile != MountainProfile.DEFAULT) != (profileParameters != null)) {
                throw new IllegalArgumentException("MOUNTAIN_RANGE profile and typed payload do not match");
            }
        }

        @Override public TerrainIntentV2.FeatureKind validationKind() {
            return switch (profile) {
                case DEFAULT -> TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE;
                case ALPINE -> TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE;
                case GLACIAL -> TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE;
            };
        }

        @Override public TerrainIntentV2.FeatureParameters validationParameters() {
            return profile == MountainProfile.DEFAULT ? defaultParameters : profileParameters;
        }

        @Override public TerrainIntentV2.FeatureKind migratedSourceKind() {
            return profile == MountainProfile.DEFAULT ? null : validationKind();
        }
    }

    public record ArchipelagoParameters(
            ArchipelagoOrigin origin,
            TerrainIntentV2.ArchipelagoParameters defaultParameters,
            TerrainIntentV2.VolcanicArchipelagoParameters volcanicParameters
    ) implements Parameters {
        public ArchipelagoParameters {
            Objects.requireNonNull(origin, "origin");
            if ((origin == ArchipelagoOrigin.DEFAULT) != (defaultParameters != null)
                    || (origin == ArchipelagoOrigin.VOLCANIC) != (volcanicParameters != null)) {
                throw new IllegalArgumentException("ARCHIPELAGO origin and typed payload do not match");
            }
        }

        @Override public TerrainIntentV2.FeatureKind validationKind() {
            return origin == ArchipelagoOrigin.DEFAULT
                    ? TerrainIntentV2.FeatureKind.ARCHIPELAGO
                    : TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO;
        }

        @Override public TerrainIntentV2.FeatureParameters validationParameters() {
            return origin == ArchipelagoOrigin.DEFAULT ? defaultParameters : volcanicParameters;
        }

        @Override public TerrainIntentV2.FeatureKind migratedSourceKind() {
            return origin == ArchipelagoOrigin.DEFAULT ? null : validationKind();
        }
    }

    public record MarshParameters(
            WetlandType wetlandType,
            TerrainIntentV2.MarshParameters defaultParameters,
            TerrainIntentV2.MangroveWetlandParameters mangroveParameters
    ) implements Parameters {
        public MarshParameters {
            Objects.requireNonNull(wetlandType, "wetlandType");
            if ((wetlandType == WetlandType.DEFAULT) != (defaultParameters != null)
                    || (wetlandType == WetlandType.MANGROVE) != (mangroveParameters != null)) {
                throw new IllegalArgumentException("MARSH wetlandType and typed payload do not match");
            }
        }

        @Override public TerrainIntentV2.FeatureKind validationKind() {
            return wetlandType == WetlandType.DEFAULT
                    ? TerrainIntentV2.FeatureKind.MARSH : TerrainIntentV2.FeatureKind.MANGROVE_WETLAND;
        }

        @Override public TerrainIntentV2.FeatureParameters validationParameters() {
            return wetlandType == WetlandType.DEFAULT ? defaultParameters : mangroveParameters;
        }

        @Override public TerrainIntentV2.FeatureKind migratedSourceKind() {
            return wetlandType == WetlandType.DEFAULT ? null : validationKind();
        }
    }

    public record PlainParameters(
            PlainContext context,
            TerrainIntentV2.PlainParameters defaultParameters,
            TerrainIntentV2.BackshorePlainsParameters backshoreParameters
    ) implements Parameters {
        public PlainParameters {
            Objects.requireNonNull(context, "context");
            if ((context == PlainContext.DEFAULT) != (defaultParameters != null)
                    || (context == PlainContext.BACKSHORE) != (backshoreParameters != null)) {
                throw new IllegalArgumentException("PLAIN context and typed payload do not match");
            }
        }

        @Override public TerrainIntentV2.FeatureKind validationKind() {
            return context == PlainContext.DEFAULT
                    ? TerrainIntentV2.FeatureKind.PLAIN : TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS;
        }

        @Override public TerrainIntentV2.FeatureParameters validationParameters() {
            return context == PlainContext.DEFAULT ? defaultParameters : backshoreParameters;
        }

        @Override public TerrainIntentV2.FeatureKind migratedSourceKind() {
            return context == PlainContext.DEFAULT ? null : validationKind();
        }
    }

    public record LakeParameters(
            LakeOrigin origin,
            TerrainIntentV2.LakeParameters defaultParameters,
            TerrainIntentV2.OxbowLakeParameters riverCutoffParameters
    ) implements Parameters {
        public LakeParameters {
            Objects.requireNonNull(origin, "origin");
            if ((origin == LakeOrigin.DEFAULT) != (defaultParameters != null)
                    || (origin == LakeOrigin.RIVER_CUTOFF) != (riverCutoffParameters != null)) {
                throw new IllegalArgumentException("LAKE origin and typed payload do not match");
            }
        }

        @Override public TerrainIntentV2.FeatureKind validationKind() {
            return origin == LakeOrigin.DEFAULT
                    ? TerrainIntentV2.FeatureKind.LAKE : TerrainIntentV2.FeatureKind.OXBOW_LAKE;
        }

        @Override public TerrainIntentV2.FeatureParameters validationParameters() {
            return origin == LakeOrigin.DEFAULT ? defaultParameters : riverCutoffParameters;
        }

        @Override public TerrainIntentV2.FeatureKind migratedSourceKind() {
            return origin == LakeOrigin.DEFAULT ? null : validationKind();
        }
    }

    public record LegacySeedBinding(
            TerrainIntentV2.FeatureKind sourceKind,
            String derivationVersion,
            String seedNamespace,
            String moduleId,
            String moduleVersion,
            String generatorVersion
    ) {
        private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._:/-]{0,127}");

        public LegacySeedBinding {
            Objects.requireNonNull(sourceKind, "sourceKind");
            if (!LEGACY_SOURCE_KINDS.contains(sourceKind)) {
                throw new IllegalArgumentException("legacy seed sourceKind is outside the approved 14-token set");
            }
            if (!"sha256-tagged-v1".equals(derivationVersion)) {
                throw new IllegalArgumentException("unsupported legacy seed derivationVersion");
            }
            seedNamespace = qualified(seedNamespace, "seedNamespace");
            moduleId = qualified(moduleId, "moduleId");
            moduleVersion = requireText(moduleVersion, "moduleVersion", 128);
            generatorVersion = requireText(generatorVersion, "generatorVersion", 128);
        }

        private static String qualified(String value, String field) {
            value = requireText(value, field, 128);
            if (!QUALIFIED.matcher(value).matches()) {
                throw new IllegalArgumentException(field + " must be a qualified identifier");
            }
            return value;
        }
    }

    public record Child(
            String id,
            ChildKind childKind,
            TerrainIntentV2.Geometry geometry,
            TerrainIntentV2.FeatureParameters parameters,
            int priority,
            TerrainIntentV2.Provenance provenance,
            LegacySeedBinding legacySeedBinding
    ) {
        public Child {
            id = requireSlug(id, "child id");
            Objects.requireNonNull(childKind, "childKind");
            Objects.requireNonNull(geometry, "geometry");
            Objects.requireNonNull(parameters, "parameters");
            Objects.requireNonNull(provenance, "provenance");
            if (legacySeedBinding != null && legacySeedBinding.sourceKind() != childKind.legacyKind()) {
                throw new IllegalArgumentException("child legacySeedBinding sourceKind mismatch");
            }
            new TerrainIntentV2.Feature(id, childKind.legacyKind(), geometry, parameters, priority, provenance);
        }

        TerrainIntentV2.Feature legacyValidationFeature() {
            return new TerrainIntentV2.Feature(id, childKind.legacyKind(), geometry, parameters, priority, provenance);
        }
    }

    public record Feature(
            String id,
            TerrainIntentV2.FeatureKind kind,
            TerrainIntentV2.Geometry geometry,
            Parameters parameters,
            int priority,
            TerrainIntentV2.Provenance provenance,
            List<Child> children,
            LegacySeedBinding legacySeedBinding
    ) {
        public Feature {
            id = requireSlug(id, "feature id");
            Objects.requireNonNull(kind, "kind");
            if (!AUTHORING_KINDS.contains(kind)) {
                throw new IllegalArgumentException("legacy token is not a canonical authoring kind: " + kind);
            }
            Objects.requireNonNull(geometry, "geometry");
            Objects.requireNonNull(parameters, "parameters");
            Objects.requireNonNull(provenance, "provenance");
            children = sorted(children, "children", MAXIMUM_CHILDREN_PER_FEATURE, Child::id);
            if (parameters.validationKind() == kind) {
                if (legacySeedBinding != null) {
                    throw new IllegalArgumentException("non-migrated canonical feature must not carry legacySeedBinding");
                }
            } else {
                TerrainIntentV2.FeatureKind sourceKind = parameters.migratedSourceKind();
                if (legacySeedBinding != null && legacySeedBinding.sourceKind() != sourceKind) {
                    throw new IllegalArgumentException("feature legacySeedBinding sourceKind mismatch");
                }
            }
            validateParameterOwner(kind, parameters);
            validateChildOwners(kind, parameters, children);
            new TerrainIntentV2.Feature(id, parameters.validationKind(), geometry,
                    parameters.validationParameters(), priority, provenance);
        }

        TerrainIntentV2.Feature legacyValidationFeature() {
            return new TerrainIntentV2.Feature(id, parameters.validationKind(), geometry,
                    parameters.validationParameters(), priority, provenance);
        }

        private static void validateParameterOwner(TerrainIntentV2.FeatureKind kind, Parameters parameters) {
            boolean valid = switch (kind) {
                case RIVER -> parameters instanceof RiverParameters;
                case MOUNTAIN_RANGE -> parameters instanceof MountainRangeParameters;
                case ARCHIPELAGO -> parameters instanceof ArchipelagoParameters;
                case MARSH -> parameters instanceof MarshParameters;
                case PLAIN -> parameters instanceof PlainParameters;
                case LAKE -> parameters instanceof LakeParameters;
                default -> parameters instanceof UnchangedParameters unchanged
                        && unchanged.validationKind() == kind;
            };
            if (!valid) {
                throw new IllegalArgumentException("canonical parameter owner mismatch for " + kind);
            }
        }

        private static void validateChildOwners(
                TerrainIntentV2.FeatureKind kind,
                Parameters parameters,
                List<Child> children
        ) {
            for (Child child : children) {
                boolean valid = switch (child.childKind()) {
                    case LAGOON, REEF_PASS -> kind == TerrainIntentV2.FeatureKind.CORAL_REEF;
                    case VOLCANIC_CALDERA, LAVA_FLOW_FIELD ->
                            kind == TerrainIntentV2.FeatureKind.VOLCANIC_CONE
                                    || kind == TerrainIntentV2.FeatureKind.ARCHIPELAGO
                                    && parameters instanceof ArchipelagoParameters archipelago
                                    && archipelago.origin() == ArchipelagoOrigin.VOLCANIC;
                    case GLACIAL_CIRQUE_FIELD ->
                            kind == TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE
                                    && parameters instanceof MountainRangeParameters mountain
                                    && mountain.profile() == MountainProfile.GLACIAL;
                    case FLOODED_CAVE -> kind == TerrainIntentV2.FeatureKind.CAVE_NETWORK
                            || kind == TerrainIntentV2.FeatureKind.UNDERGROUND_RIVER;
                };
                if (!valid) {
                    throw new IllegalArgumentException("unsupported canonical child owner: "
                            + child.childKind() + " -> " + kind);
                }
            }
        }
    }

    private static <T> List<T> sorted(
            List<T> values,
            String field,
            int maximum,
            java.util.function.Function<T, String> id
    ) {
        Objects.requireNonNull(values, field);
        if (values.size() > maximum) {
            throw new IllegalArgumentException(field + " exceeds maximum " + maximum);
        }
        List<T> result = new ArrayList<>(values.size());
        Set<String> ids = new HashSet<>();
        for (T value : values) {
            Objects.requireNonNull(value, field + " entry");
            String itemId = id.apply(value);
            if (!ids.add(itemId)) {
                throw new IllegalArgumentException("duplicate " + field + " id: " + itemId);
            }
            result.add(value);
        }
        result.sort(Comparator.comparing(id, UTF8_ORDER));
        return List.copyOf(result);
    }

    private static String requireSlug(String value, String field) {
        value = requireText(value, field, 64);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException(field + " is not a canonical slug");
        }
        return value;
    }

    private static String requireText(String value, String field, int maximum) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + maximum);
        }
        return value;
    }

    private static int compareBytes(byte[] left, byte[] right) {
        int length = Math.min(left.length, right.length);
        for (int index = 0; index < length; index++) {
            int difference = Byte.toUnsignedInt(left[index]) - Byte.toUnsignedInt(right[index]);
            if (difference != 0) return difference;
        }
        return left.length - right.length;
    }
}
