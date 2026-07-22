package com.github.nankotsu029.landformcraft.core.v2.migration;

import com.github.nankotsu029.landformcraft.core.v2.catalog.CanonicalFeatureTargetRegistryV2;
import com.github.nankotsu029.landformcraft.format.FileTreeOperations;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalTerrainIntentCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.TerrainIntentVersionDispatcher;
import com.github.nankotsu029.landformcraft.format.v2.migration.CanonicalFeatureMigrationReportCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.CanonicalTerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.CanonicalFeatureMigrationReportV2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict, explicit LEGACY_V2 → CANONICAL_V2 migration from ADR 0036. */
public final class CanonicalFeatureMigrationServiceV2 {
    public static final String TARGET_FILE = "terrain-intent-v2-canonical.json";
    public static final String REPORT_FILE = "migration-report.json";

    private static final Set<TerrainIntentV2.FeatureKind> COAST_KINDS = EnumSet.of(
            TerrainIntentV2.FeatureKind.SANDY_BEACH,
            TerrainIntentV2.FeatureKind.ROCKY_CAPE,
            TerrainIntentV2.FeatureKind.ROCKY_COAST,
            TerrainIntentV2.FeatureKind.SEA_CLIFF,
            TerrainIntentV2.FeatureKind.CORAL_REEF);

    private final LandformV2DataCodec legacyCodec = new LandformV2DataCodec();
    private final CanonicalTerrainIntentCodecV2 canonicalCodec = new CanonicalTerrainIntentCodecV2();
    private final TerrainIntentVersionDispatcher dispatcher = new TerrainIntentVersionDispatcher();
    private final CanonicalFeatureMigrationReportCodecV2 reportCodec =
            new CanonicalFeatureMigrationReportCodecV2();
    private final CanonicalFeatureTargetRegistryV2 registry = CanonicalFeatureTargetRegistryV2.project(
            CanonicalTerrainIntentV2.AUTHORING_KINDS.stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));

    public Result migrate(
            Path source,
            CanonicalTerrainIntentV2.FeatureProjection sourceProjection,
            String sourceArtifactIdentity,
            Map<String, CanonicalTerrainIntentV2.LegacySeedBinding> legacySeedBindings
    ) throws IOException {
        Objects.requireNonNull(sourceProjection, "sourceProjection");
        Path normalizedSource = source.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalizedSource)
                || !Files.isRegularFile(normalizedSource, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("canonical feature migration source must be a regular non-symlink file");
        }
        if (sourceProjection == CanonicalTerrainIntentV2.FeatureProjection.LEGACY_V2) {
            TerrainIntentV2 legacy = dispatcher.readLegacy(normalizedSource, sourceProjection).value();
            return migrateLegacy(legacy, sourceArtifactIdentity, legacySeedBindings);
        }
        if (!legacySeedBindings.isEmpty()) {
            throw new IOException("ALREADY_CANONICAL migration must not accept out-of-band seed bindings");
        }
        return alreadyCanonical(canonicalCodec.read(normalizedSource), sourceArtifactIdentity);
    }

    public Result migrateLegacy(
            TerrainIntentV2 source,
            String sourceArtifactIdentity,
            Map<String, CanonicalTerrainIntentV2.LegacySeedBinding> legacySeedBindings
    ) throws IOException {
        Objects.requireNonNull(source, "source");
        sourceArtifactIdentity = identity(sourceArtifactIdentity);
        Map<String, CanonicalTerrainIntentV2.LegacySeedBinding> seeds = Map.copyOf(legacySeedBindings);
        Map<String, TerrainIntentV2.Feature> byId = new HashMap<>();
        Set<String> requiredSeedIds = new HashSet<>();
        for (TerrainIntentV2.Feature feature : source.features()) {
            byId.put(feature.id(), feature);
            if (CanonicalTerrainIntentV2.LEGACY_SOURCE_KINDS.contains(feature.kind())) {
                requiredSeedIds.add(feature.id());
            }
        }
        if (!seeds.keySet().equals(requiredSeedIds)) {
            Set<String> missing = new java.util.TreeSet<>(requiredSeedIds);
            missing.removeAll(seeds.keySet());
            Set<String> extra = new java.util.TreeSet<>(seeds.keySet());
            extra.removeAll(requiredSeedIds);
            throw new IOException("legacy seed binding set differs; missing=" + missing + ", extra=" + extra);
        }
        for (String id : requiredSeedIds) {
            if (seeds.get(id).sourceKind() != byId.get(id).kind()) {
                throw new IOException("legacy seed binding sourceKind mismatch for " + id);
            }
        }

        Map<String, String> childOwners = resolveChildOwners(source, byId);
        Map<String, List<CanonicalTerrainIntentV2.Child>> childrenByOwner = new HashMap<>();
        List<CanonicalFeatureMigrationReportV2.FeatureMigration> reportEntries = new ArrayList<>();
        for (TerrainIntentV2.Feature feature : source.features()) {
            if (!isChild(feature.kind())) continue;
            String ownerId = childOwners.get(feature.id());
            CanonicalTerrainIntentV2.LegacySeedBinding seed = seeds.get(feature.id());
            CanonicalTerrainIntentV2.Child child = new CanonicalTerrainIntentV2.Child(
                    feature.id(), CanonicalTerrainIntentV2.ChildKind.fromLegacy(feature.kind()),
                    feature.geometry(), feature.parameters(), feature.priority(), feature.provenance(), seed);
            childrenByOwner.computeIfAbsent(ownerId, ignored -> new ArrayList<>()).add(child);
            reportEntries.add(reportEntry(feature, ownerId, seed));
        }

        List<CanonicalTerrainIntentV2.Feature> targetFeatures = new ArrayList<>();
        for (TerrainIntentV2.Feature feature : source.features()) {
            if (isChild(feature.kind())) continue;
            validateSpecializationPreconditions(feature, source, byId);
            CanonicalTerrainIntentV2.Parameters parameters = parameters(feature);
            TerrainIntentV2.FeatureKind targetKind = targetKind(feature.kind());
            CanonicalTerrainIntentV2.LegacySeedBinding seed = seeds.get(feature.id());
            targetFeatures.add(new CanonicalTerrainIntentV2.Feature(
                    feature.id(), targetKind, feature.geometry(), parameters, feature.priority(),
                    feature.provenance(), childrenByOwner.getOrDefault(feature.id(), List.of()), seed));
            if (CanonicalTerrainIntentV2.LEGACY_SOURCE_KINDS.contains(feature.kind())) {
                reportEntries.add(reportEntry(feature, feature.id(), seed));
            }
        }
        for (String ownerId : childrenByOwner.keySet()) {
            if (targetFeatures.stream().noneMatch(feature -> feature.id().equals(ownerId))) {
                throw new IOException("canonical child owner did not survive migration: " + ownerId);
            }
        }

        CanonicalTerrainIntentV2 target = new CanonicalTerrainIntentV2(
                source.intentVersion(), CanonicalTerrainIntentV2.FeatureProjection.CANONICAL_V2,
                source.intentId(), source.theme(), source.coordinateSystem(), targetFeatures,
                source.relations(), source.constraints(), source.environment(), source.mapReferences(),
                source.structures(), source.provenance());
        String sourceChecksum = legacyCodec.terrainIntentChecksum(source);
        String targetChecksum = canonicalCodec.checksum(target);
        CanonicalFeatureMigrationReportV2 report = new CanonicalFeatureMigrationReportV2(
                CanonicalFeatureMigrationReportV2.CONTRACT_VERSION, sourceArtifactIdentity,
                CanonicalTerrainIntentV2.FeatureProjection.LEGACY_V2,
                CanonicalTerrainIntentV2.FeatureProjection.CANONICAL_V2,
                sourceChecksum, targetChecksum, CanonicalFeatureMigrationReportV2.Status.MIGRATED,
                reportEntries, List.of("LEGACY_V2_READ_EXPLICIT", "LOSSLESS_MAPPING_VERIFIED",
                        "LEGACY_SEED_SEMANTICS_EMBEDDED"));
        verifyReportBindings(target, report);
        return new Result(target, report);
    }

    public Result alreadyCanonical(CanonicalTerrainIntentV2 source, String sourceArtifactIdentity) {
        String checksum = canonicalCodec.checksum(source);
        List<CanonicalFeatureMigrationReportV2.FeatureMigration> entries = new ArrayList<>();
        for (CanonicalTerrainIntentV2.Feature feature : source.features()) {
            if (feature.legacySeedBinding() != null) {
                entries.add(reportEntry(feature.id(), feature.legacySeedBinding().sourceKind(),
                        feature.id(), feature.legacySeedBinding()));
            }
            for (CanonicalTerrainIntentV2.Child child : feature.children()) {
                if (child.legacySeedBinding() != null) {
                    entries.add(reportEntry(child.id(), child.childKind().legacyKind(),
                            feature.id(), child.legacySeedBinding()));
                }
            }
        }
        CanonicalFeatureMigrationReportV2 report = new CanonicalFeatureMigrationReportV2(
                CanonicalFeatureMigrationReportV2.CONTRACT_VERSION, identity(sourceArtifactIdentity),
                CanonicalTerrainIntentV2.FeatureProjection.CANONICAL_V2,
                CanonicalTerrainIntentV2.FeatureProjection.CANONICAL_V2,
                checksum, checksum, CanonicalFeatureMigrationReportV2.Status.ALREADY_CANONICAL,
                entries, List.of("ALREADY_CANONICAL", "NO_SECOND_MIGRATION_APPLIED"));
        verifyReportBindings(source, report);
        return new Result(source, report);
    }

    public PublishedResult publish(Result result, Path targetDirectory) throws IOException {
        Objects.requireNonNull(result, "result");
        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "migration target requires a parent");
        Files.createDirectories(parent);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("canonical migration target already exists and is never overwritten: " + target);
        }
        Path staging = Files.createTempDirectory(parent, ".canonical-feature-migration-");
        boolean published = false;
        try {
            canonicalCodec.write(staging.resolve(TARGET_FILE), result.target());
            Files.writeString(staging.resolve(REPORT_FILE), reportCodec.canonical(result.report()),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Result verified = verifyBundle(staging);
            if (!result.equals(verified)) {
                throw new IOException("canonical migration strict read-back differs");
            }
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("canonical migration atomic publish is not supported", exception);
            }
            published = true;
            return new PublishedResult(target, verified);
        } finally {
            if (!published) FileTreeOperations.deleteTree(staging);
        }
    }

    public Result verifyBundle(Path directory) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IOException("canonical migration bundle must be a regular directory");
        }
        Set<String> actual = new java.util.TreeSet<>();
        try (var files = Files.list(root)) {
            files.forEach(path -> actual.add(path.getFileName().toString()));
        }
        Set<String> expected = Set.of(TARGET_FILE, REPORT_FILE);
        if (!actual.equals(expected)) {
            throw new IOException("canonical migration bundle files differ; expected=" + expected + ", actual=" + actual);
        }
        for (String file : expected) {
            Path entry = root.resolve(file);
            if (Files.isSymbolicLink(entry) || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("canonical migration bundle entry must be a regular non-symlink file: " + file);
            }
        }
        CanonicalTerrainIntentV2 target = canonicalCodec.read(root.resolve(TARGET_FILE));
        CanonicalFeatureMigrationReportV2 report = reportCodec.read(root.resolve(REPORT_FILE));
        if (!canonicalCodec.checksum(target).equals(report.targetCanonicalChecksum())) {
            throw new IOException("canonical migration target checksum does not match report");
        }
        verifyReportBindings(target, report);
        return new Result(target, report);
    }

    private Map<String, String> resolveChildOwners(
            TerrainIntentV2 source,
            Map<String, TerrainIntentV2.Feature> byId
    ) throws IOException {
        Map<String, String> owners = new HashMap<>();
        for (TerrainIntentV2.Feature feature : source.features()) {
            switch (feature.kind()) {
                case LAGOON -> owners.put(feature.id(), uniqueOwner(source, byId, feature.id(),
                        Set.of(TerrainIntentV2.RelationKind.ENCLOSED_BY),
                        Set.of(TerrainIntentV2.FeatureKind.CORAL_REEF)));
                case REEF_PASS -> {
                    String owner = uniqueOwner(source, byId, feature.id(),
                            Set.of(TerrainIntentV2.RelationKind.CARVES_THROUGH),
                            Set.of(TerrainIntentV2.FeatureKind.CORAL_REEF));
                    requireHardRelationFrom(source, feature.id(), TerrainIntentV2.RelationKind.CONNECTS_TO);
                    owners.put(feature.id(), owner);
                }
                case VOLCANIC_CALDERA -> owners.put(feature.id(), uniqueOwner(source, byId, feature.id(),
                        Set.of(TerrainIntentV2.RelationKind.WITHIN),
                        Set.of(TerrainIntentV2.FeatureKind.VOLCANIC_CONE,
                                TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO)));
                case GLACIAL_CIRQUE_FIELD -> owners.put(feature.id(), uniqueOwner(source, byId, feature.id(),
                        Set.of(TerrainIntentV2.RelationKind.CARVES_FLANK_OF),
                        Set.of(TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE)));
                case FLOODED_CAVE -> owners.put(feature.id(), uniqueOwner(source, byId, feature.id(),
                        Set.of(TerrainIntentV2.RelationKind.WITHIN, TerrainIntentV2.RelationKind.OVERLAPS),
                        Set.of(TerrainIntentV2.FeatureKind.CAVE_NETWORK,
                                TerrainIntentV2.FeatureKind.UNDERGROUND_RIVER)));
                default -> { }
            }
        }
        for (TerrainIntentV2.Feature feature : source.features()) {
            if (feature.kind() != TerrainIntentV2.FeatureKind.LAVA_FLOW_FIELD) continue;
            Set<String> candidates = hardTargets(source, feature.id(),
                    Set.of(TerrainIntentV2.RelationKind.ORIGINATES_AT,
                            TerrainIntentV2.RelationKind.ON_PATH_OF,
                            TerrainIntentV2.RelationKind.WITHIN));
            Set<String> resolved = new HashSet<>();
            for (String candidate : candidates) {
                TerrainIntentV2.Feature target = byId.get(candidate);
                if (target == null) continue;
                if (target.kind() == TerrainIntentV2.FeatureKind.VOLCANIC_CONE
                        || target.kind() == TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO) {
                    resolved.add(candidate);
                } else if (target.kind() == TerrainIntentV2.FeatureKind.VOLCANIC_CALDERA
                        && owners.containsKey(candidate)) {
                    resolved.add(owners.get(candidate));
                }
            }
            if (resolved.size() != 1) {
                throw new IOException("LAVA_FLOW_FIELD requires exactly one explicit supported owner: "
                        + feature.id() + " -> " + new java.util.TreeSet<>(resolved));
            }
            owners.put(feature.id(), resolved.iterator().next());
        }
        return owners;
    }

    private void validateSpecializationPreconditions(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 source,
            Map<String, TerrainIntentV2.Feature> byId
    ) throws IOException {
        if (feature.kind() == TerrainIntentV2.FeatureKind.OXBOW_LAKE) {
            uniqueOwner(source, byId, feature.id(), Set.of(TerrainIntentV2.RelationKind.ORIGINATES_AT),
                    Set.of(TerrainIntentV2.FeatureKind.RIVER, TerrainIntentV2.FeatureKind.MEANDERING_RIVER));
        } else if (feature.kind() == TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS) {
            Set<String> coasts = hardConnectedTargets(source, feature.id(),
                    Set.of(TerrainIntentV2.RelationKind.ADJACENT_TO, TerrainIntentV2.RelationKind.OVERLAPS,
                            TerrainIntentV2.RelationKind.WITHIN, TerrainIntentV2.RelationKind.SUPPORTED_BY));
            coasts.removeIf(id -> !COAST_KINDS.contains(byId.get(id).kind()));
            if (coasts.size() != 1) {
                throw new IOException("BACKSHORE_PLAINS requires exactly one explicit coast relation");
            }
        } else if (feature.kind() == TerrainIntentV2.FeatureKind.MANGROVE_WETLAND
                && source.environment().ecologyPreset() == null) {
            throw new IOException("MANGROVE_WETLAND migration requires an explicit ecology profile");
        }
    }

    private CanonicalTerrainIntentV2.Parameters parameters(TerrainIntentV2.Feature feature) {
        return switch (feature.kind()) {
            case RIVER -> new CanonicalTerrainIntentV2.RiverParameters(
                    CanonicalTerrainIntentV2.Morphology.DEFAULT,
                    CanonicalTerrainIntentV2.ChannelSubtype.DEFAULT,
                    (TerrainIntentV2.RiverParameters) feature.parameters(), null);
            case MEANDERING_RIVER -> new CanonicalTerrainIntentV2.RiverParameters(
                    CanonicalTerrainIntentV2.Morphology.MEANDERING,
                    CanonicalTerrainIntentV2.ChannelSubtype.DEFAULT, null,
                    (TerrainIntentV2.MeanderingRiverParameters) feature.parameters());
            case BEDROCK_RIVER -> new CanonicalTerrainIntentV2.RiverParameters(
                    CanonicalTerrainIntentV2.Morphology.DEFAULT,
                    CanonicalTerrainIntentV2.ChannelSubtype.BEDROCK, null, null);
            case MOUNTAIN_RANGE -> new CanonicalTerrainIntentV2.MountainRangeParameters(
                    CanonicalTerrainIntentV2.MountainProfile.DEFAULT,
                    (TerrainIntentV2.MountainRangeParameters) feature.parameters(), null);
            case ALPINE_MOUNTAIN_RANGE -> new CanonicalTerrainIntentV2.MountainRangeParameters(
                    CanonicalTerrainIntentV2.MountainProfile.ALPINE, null,
                    (TerrainIntentV2.MountainParameters) feature.parameters());
            case GLACIAL_MOUNTAIN_RANGE -> new CanonicalTerrainIntentV2.MountainRangeParameters(
                    CanonicalTerrainIntentV2.MountainProfile.GLACIAL, null,
                    (TerrainIntentV2.MountainParameters) feature.parameters());
            case ARCHIPELAGO -> new CanonicalTerrainIntentV2.ArchipelagoParameters(
                    CanonicalTerrainIntentV2.ArchipelagoOrigin.DEFAULT,
                    (TerrainIntentV2.ArchipelagoParameters) feature.parameters(), null);
            case VOLCANIC_ARCHIPELAGO -> new CanonicalTerrainIntentV2.ArchipelagoParameters(
                    CanonicalTerrainIntentV2.ArchipelagoOrigin.VOLCANIC, null,
                    (TerrainIntentV2.VolcanicArchipelagoParameters) feature.parameters());
            case MARSH -> new CanonicalTerrainIntentV2.MarshParameters(
                    CanonicalTerrainIntentV2.WetlandType.DEFAULT,
                    (TerrainIntentV2.MarshParameters) feature.parameters(), null);
            case MANGROVE_WETLAND -> new CanonicalTerrainIntentV2.MarshParameters(
                    CanonicalTerrainIntentV2.WetlandType.MANGROVE, null,
                    (TerrainIntentV2.MangroveWetlandParameters) feature.parameters());
            case PLAIN -> new CanonicalTerrainIntentV2.PlainParameters(
                    CanonicalTerrainIntentV2.PlainContext.DEFAULT,
                    (TerrainIntentV2.PlainParameters) feature.parameters(), null);
            case BACKSHORE_PLAINS -> new CanonicalTerrainIntentV2.PlainParameters(
                    CanonicalTerrainIntentV2.PlainContext.BACKSHORE, null,
                    (TerrainIntentV2.BackshorePlainsParameters) feature.parameters());
            case LAKE -> new CanonicalTerrainIntentV2.LakeParameters(
                    CanonicalTerrainIntentV2.LakeOrigin.DEFAULT,
                    (TerrainIntentV2.LakeParameters) feature.parameters(), null);
            case OXBOW_LAKE -> new CanonicalTerrainIntentV2.LakeParameters(
                    CanonicalTerrainIntentV2.LakeOrigin.RIVER_CUTOFF, null,
                    (TerrainIntentV2.OxbowLakeParameters) feature.parameters());
            default -> new CanonicalTerrainIntentV2.UnchangedParameters(feature.kind(), feature.parameters());
        };
    }

    private static TerrainIntentV2.FeatureKind targetKind(TerrainIntentV2.FeatureKind source) {
        return switch (source) {
            case MEANDERING_RIVER, BEDROCK_RIVER -> TerrainIntentV2.FeatureKind.RIVER;
            case ALPINE_MOUNTAIN_RANGE, GLACIAL_MOUNTAIN_RANGE -> TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE;
            case VOLCANIC_ARCHIPELAGO -> TerrainIntentV2.FeatureKind.ARCHIPELAGO;
            case MANGROVE_WETLAND -> TerrainIntentV2.FeatureKind.MARSH;
            case BACKSHORE_PLAINS -> TerrainIntentV2.FeatureKind.PLAIN;
            case OXBOW_LAKE -> TerrainIntentV2.FeatureKind.LAKE;
            default -> source;
        };
    }

    private CanonicalFeatureMigrationReportV2.FeatureMigration reportEntry(
            TerrainIntentV2.Feature source,
            String ownerId,
            CanonicalTerrainIntentV2.LegacySeedBinding seed
    ) {
        return reportEntry(source.id(), source.kind(), ownerId, seed);
    }

    private CanonicalFeatureMigrationReportV2.FeatureMigration reportEntry(
            String sourceId,
            TerrainIntentV2.FeatureKind sourceKind,
            String ownerId,
            CanonicalTerrainIntentV2.LegacySeedBinding seed
    ) {
        return new CanonicalFeatureMigrationReportV2.FeatureMigration(
                sourceId, sourceKind, ownerId, registry.entry(sourceKind).targetCarrier(), seed,
                "MIGRATED_EXPLICITLY");
    }

    private static boolean isChild(TerrainIntentV2.FeatureKind kind) {
        return switch (kind) {
            case LAGOON, REEF_PASS, VOLCANIC_CALDERA, LAVA_FLOW_FIELD,
                    GLACIAL_CIRQUE_FIELD, FLOODED_CAVE -> true;
            default -> false;
        };
    }

    private static String uniqueOwner(
            TerrainIntentV2 source,
            Map<String, TerrainIntentV2.Feature> byId,
            String childId,
            Set<TerrainIntentV2.RelationKind> relationKinds,
            Set<TerrainIntentV2.FeatureKind> ownerKinds
    ) throws IOException {
        Set<String> owners = hardTargets(source, childId, relationKinds);
        owners.removeIf(id -> !byId.containsKey(id) || !ownerKinds.contains(byId.get(id).kind()));
        if (owners.size() != 1) {
            throw new IOException("feature requires exactly one explicit supported owner: "
                    + childId + " -> " + new java.util.TreeSet<>(owners));
        }
        return owners.iterator().next();
    }

    private static Set<String> hardTargets(
            TerrainIntentV2 source,
            String fromId,
            Set<TerrainIntentV2.RelationKind> kinds
    ) {
        String endpoint = "feature:" + fromId;
        Set<String> result = new HashSet<>();
        for (TerrainIntentV2.Relation relation : source.relations()) {
            if (relation.strength() == TerrainIntentV2.Strength.HARD
                    && relation.from().equals(endpoint) && kinds.contains(relation.kind())
                    && relation.to().startsWith("feature:")) {
                result.add(relation.to().substring("feature:".length()));
            }
        }
        return result;
    }

    private static Set<String> hardConnectedTargets(
            TerrainIntentV2 source,
            String featureId,
            Set<TerrainIntentV2.RelationKind> kinds
    ) {
        String endpoint = "feature:" + featureId;
        Set<String> result = new HashSet<>();
        for (TerrainIntentV2.Relation relation : source.relations()) {
            if (relation.strength() != TerrainIntentV2.Strength.HARD || !kinds.contains(relation.kind())) continue;
            if (relation.from().equals(endpoint) && relation.to().startsWith("feature:")) {
                result.add(relation.to().substring("feature:".length()));
            } else if (relation.to().equals(endpoint) && relation.from().startsWith("feature:")) {
                result.add(relation.from().substring("feature:".length()));
            }
        }
        return result;
    }

    private static void requireHardRelationFrom(
            TerrainIntentV2 source,
            String featureId,
            TerrainIntentV2.RelationKind kind
    ) throws IOException {
        if (hardTargets(source, featureId, Set.of(kind)).isEmpty()) {
            throw new IOException(featureId + " requires an explicit HARD " + kind + " relation");
        }
    }

    private static void verifyReportBindings(
            CanonicalTerrainIntentV2 target,
            CanonicalFeatureMigrationReportV2 report
    ) {
        Map<String, CanonicalTerrainIntentV2.LegacySeedBinding> targetBindings = new HashMap<>();
        Map<String, String> targetOwners = new HashMap<>();
        for (CanonicalTerrainIntentV2.Feature feature : target.features()) {
            if (feature.legacySeedBinding() != null) {
                targetBindings.put(feature.id(), feature.legacySeedBinding());
                targetOwners.put(feature.id(), feature.id());
            }
            for (CanonicalTerrainIntentV2.Child child : feature.children()) {
                if (child.legacySeedBinding() != null) {
                    targetBindings.put(child.id(), child.legacySeedBinding());
                    targetOwners.put(child.id(), feature.id());
                }
            }
        }
        Set<String> reportIds = new HashSet<>();
        for (CanonicalFeatureMigrationReportV2.FeatureMigration feature : report.features()) {
            reportIds.add(feature.sourceFeatureId());
            if (!feature.legacySeedBinding().equals(targetBindings.get(feature.sourceFeatureId()))) {
                throw new IllegalArgumentException("migration report seed binding differs from target: "
                        + feature.sourceFeatureId());
            }
            if (!feature.targetOwnerId().equals(targetOwners.get(feature.sourceFeatureId()))) {
                throw new IllegalArgumentException("migration report owner differs from target: "
                        + feature.sourceFeatureId());
            }
        }
        if (!reportIds.equals(targetBindings.keySet())) {
            throw new IllegalArgumentException("migration report feature set differs from target seed bindings");
        }
    }

    private static String identity(String value) {
        Objects.requireNonNull(value, "sourceArtifactIdentity");
        if (value.isBlank() || value.length() > 256 || value.contains("/") || value.contains("\\")) {
            throw new IllegalArgumentException("sourceArtifactIdentity must be a redacted portable identity");
        }
        return value;
    }

    public record Result(
            CanonicalTerrainIntentV2 target,
            CanonicalFeatureMigrationReportV2 report
    ) {
        public Result {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(report, "report");
        }
    }

    public record PublishedResult(Path root, Result verified) {
        public PublishedResult {
            root = root.toAbsolutePath().normalize();
            Objects.requireNonNull(verified, "verified");
        }
    }
}
