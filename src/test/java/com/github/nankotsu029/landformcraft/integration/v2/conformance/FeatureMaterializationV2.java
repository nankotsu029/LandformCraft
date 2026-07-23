package com.github.nankotsu029.landformcraft.integration.v2.conformance;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseCoreVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.VerifiedReleaseViewV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.SpongeV3TileInspectorV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.release.ReleaseCapabilityDependencyMatrixV2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * V2-19-01 semantic materialization gate: the block-metric column of the intent-conformance
 * portfolio, kept deliberately separate from the plan-only column (hydrology validation metrics
 * and other JSON evidence artifacts).
 *
 * <p>A public wiring leaf claiming that a Feature is materialized must (1) declare which canonical
 * fields and block effect classes the Feature changes, and (2) prove a non-empty effect measured
 * from the <em>final canonical block stream</em> of a published Release against a published
 * baseline Release without the Feature. Plan artifacts, validation reports, constant "healthy"
 * samplers and identity volume slices (an {@code ADD_FLUID} aimed at solid cells cannot change any
 * block) all produce an empty block effect and therefore can never pass
 * {@link #requireMaterialized}; an intentional no-op route stays admissible as a capability spine
 * smoke but never as Feature-promotion evidence.</p>
 */
final class FeatureMaterializationV2 {
    private static final CancellationToken NEVER = () -> false;
    private static final String SURFACE_METADATA_TYPE = "offline-tile-artifact-v2";
    private static final String VOLUME_METADATA_TYPE = "volume-offline-tile-artifact-v2";
    private static final String SURFACE_PREFIX = "tiles/";
    private static final String VOLUME_PREFIX = "volume/tiles/";
    private static final Set<String> FLUID_STATES = Set.of("minecraft:water", "minecraft:lava");
    private static final String AIR_STATE = "minecraft:air";

    private FeatureMaterializationV2() {
    }

    /** How a Feature is allowed to change the final canonical block stream. */
    enum EffectClassV2 { SOLID_SHAPE, FLUID, MATERIAL }

    /**
     * The per-leaf declaration the V2-19-01 Acceptance obligation requires: which canonical fields
     * and which block effect classes the Feature changes. An empty declaration is itself a claim
     * of no effect and is rejected by {@link #requireMaterialized}.
     */
    record MaterializationClaimV2(
            String featureId,
            TerrainIntentV2.FeatureKind kind,
            Set<String> declaredChangedFields,
            Set<EffectClassV2> declaredEffects
    ) {
        MaterializationClaimV2 {
            Objects.requireNonNull(featureId, "featureId");
            Objects.requireNonNull(kind, "kind");
            declaredChangedFields = Set.copyOf(declaredChangedFields);
            declaredEffects = Set.copyOf(declaredEffects);
        }
    }

    /**
     * Cell-by-cell difference between two published final canonical block streams. Classes:
     * {@code SOLID_SHAPE} — solid mass appears or disappears (air ↔ solid);
     * {@code FLUID} — a fluid cell appears, disappears or changes;
     * {@code MATERIAL} — a solid cell keeps existing but changes block state.
     */
    record BlockEffectV2(
            long comparedCells,
            long changedCells,
            long solidShapeChanges,
            long fluidChanges,
            long materialChanges
    ) {
        boolean empty() {
            return changedCells == 0;
        }

        Set<EffectClassV2> observedClasses() {
            Set<EffectClassV2> observed = java.util.EnumSet.noneOf(EffectClassV2.class);
            if (solidShapeChanges > 0) {
                observed.add(EffectClassV2.SOLID_SHAPE);
            }
            if (fluidChanges > 0) {
                observed.add(EffectClassV2.FLUID);
            }
            if (materialChanges > 0) {
                observed.add(EffectClassV2.MATERIAL);
            }
            return observed;
        }
    }

    /**
     * Measures the block effect of {@code featureRelease} against {@code baselineRelease}. Both
     * directories are strictly verified first; both must publish final tiles over the identical
     * release-local geometry. Pure: the same pair of directories always yields the same record.
     */
    static BlockEffectV2 measureBlockEffect(Path baselineRelease, Path featureRelease)
            throws IOException {
        ReleaseCoreVerifierV2 verifier = new ReleaseCoreVerifierV2();
        try (VerifiedReleaseViewV2 baseline = verifier.openVerified(baselineRelease, NEVER);
             VerifiedReleaseViewV2 feature = verifier.openVerified(featureRelease, NEVER)) {
            Map<String, TileRef> baselineTiles = finalTiles(baseline);
            Map<String, TileRef> featureTiles = finalTiles(feature);
            if (!baselineTiles.keySet().equals(featureTiles.keySet())) {
                throw new IOException("block effect measurement requires identical tile geometry: "
                        + baselineTiles.keySet() + " vs " + featureTiles.keySet());
            }
            long compared = 0;
            long changed = 0;
            long solidShape = 0;
            long fluid = 0;
            long material = 0;
            for (Map.Entry<String, TileRef> entry : baselineTiles.entrySet()) {
                TileRef base = entry.getValue();
                TileRef feat = featureTiles.get(entry.getKey());
                SpongeV3TileInspectorV2.BlockStateCursor baseCursor = decode(baseline, base);
                SpongeV3TileInspectorV2.BlockStateCursor featCursor = decode(feature, feat);
                for (int i = 0; i < base.artifact().blockCount(); i++) {
                    String before = baseCursor.next();
                    String after = featCursor.next();
                    if (before == null || after == null) {
                        throw new IOException("published tile stream ended before its declared block count");
                    }
                    compared++;
                    if (before.equals(after)) {
                        continue;
                    }
                    changed++;
                    boolean beforeFluid = FLUID_STATES.contains(before);
                    boolean afterFluid = FLUID_STATES.contains(after);
                    boolean beforeAir = AIR_STATE.equals(before);
                    boolean afterAir = AIR_STATE.equals(after);
                    if (beforeFluid || afterFluid) {
                        fluid++;
                    } else if (beforeAir != afterAir) {
                        solidShape++;
                    } else {
                        material++;
                    }
                }
                if (baseCursor.next() != null || featCursor.next() != null) {
                    throw new IOException("published tile stream is longer than its declared block count");
                }
                baseCursor.close();
                featCursor.close();
            }
            return new BlockEffectV2(compared, changed, solidShape, fluid, material);
        }
    }

    /**
     * The V2-19-01 gate decision. Only a {@link BlockEffectV2} is admissible evidence — there is
     * deliberately no overload accepting a validation report or any other plan artifact — and the
     * observed effect classes must equal the declared ones exactly: an empty effect, an undeclared
     * observed class, and a declared-but-unobserved class each fail.
     */
    static void requireMaterialized(MaterializationClaimV2 claim, BlockEffectV2 effect) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(effect, "effect");
        if (claim.declaredEffects().isEmpty() || claim.declaredChangedFields().isEmpty()) {
            throw new IllegalStateException("Feature materialization claim for " + claim.featureId()
                    + " declares no changed canonical field or block effect class; a no-op cannot be"
                    + " promoted as a Feature (V2-19-01)");
        }
        if (effect.empty()) {
            throw new IllegalStateException("Feature " + claim.featureId() + " (" + claim.kind()
                    + ") produced no effect on the final canonical block stream; plan-only metrics,"
                    + " constant healthy samplers and identity slices are not materialization"
                    + " evidence (V2-19-01)");
        }
        if (!effect.observedClasses().equals(claim.declaredEffects())) {
            throw new IllegalStateException("Feature " + claim.featureId() + " declared block effect"
                    + " classes " + claim.declaredEffects() + " but the final canonical block stream"
                    + " shows " + effect.observedClasses() + " (V2-19-01 shape conformance)");
        }
    }

    /**
     * The whole final canonical block stream of one published Release, addressed by release-local
     * cell. V2-19-05 reads it to measure a Feature's <em>shape</em> (bed depth, water continuity,
     * containment) rather than only the size of its diff against a baseline.
     */
    record FinalBlockStreamV2(int width, int length, int minY, int maxY, String[] states) {
        String at(int x, int y, int z) {
            if (x < 0 || z < 0 || x >= width || z >= length || y < minY || y > maxY) {
                throw new IndexOutOfBoundsException("coordinate outside the published block stream");
            }
            return states[((y - minY) * length + z) * width + x];
        }

        boolean isFluid(int x, int y, int z) {
            return FLUID_STATES.contains(at(x, y, z));
        }

        boolean isAir(int x, int y, int z) {
            return AIR_STATE.equals(at(x, y, z));
        }

        boolean isSolid(int x, int y, int z) {
            String state = at(x, y, z);
            return !AIR_STATE.equals(state) && !FLUID_STATES.contains(state);
        }
    }

    /**
     * Decodes every published tile of {@code release} into one release-local block grid. The tiles
     * are strictly verified first and decoded through the same inspector {@link #measureBlockEffect}
     * uses, so the grid is exactly what an operator would place.
     */
    static FinalBlockStreamV2 readFinalBlockStream(Path release) throws IOException {
        ReleaseCoreVerifierV2 verifier = new ReleaseCoreVerifierV2();
        try (VerifiedReleaseViewV2 view = verifier.openVerified(release, NEVER)) {
            Map<String, TileRef> tiles = finalTiles(view);
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (TileRef tile : tiles.values()) {
                minX = Math.min(minX, tile.artifact().originX());
                minZ = Math.min(minZ, tile.artifact().originZ());
                maxX = Math.max(maxX, tile.artifact().originX() + tile.artifact().width() - 1);
                maxZ = Math.max(maxZ, tile.artifact().originZ() + tile.artifact().length() - 1);
                minY = Math.min(minY, tile.artifact().minY());
                maxY = Math.max(maxY, tile.artifact().maxY());
            }
            if (minX != 0 || minZ != 0) {
                throw new IOException("published tiles do not start at the release-local origin");
            }
            int width = maxX + 1;
            int length = maxZ + 1;
            String[] states = new String[Math.multiplyExact(
                    Math.multiplyExact(width, length), maxY - minY + 1)];
            for (TileRef tile : tiles.values()) {
                OfflineTileArtifactV2 artifact = tile.artifact();
                if (artifact.minY() != minY || artifact.maxY() != maxY) {
                    throw new IOException("published tiles disagree on the vertical span");
                }
                SpongeV3TileInspectorV2.BlockStateCursor cursor = decode(view, tile);
                // Sponge v3 order, as OfflineTileSchematicWriterV2 writes it: y, then z, then x.
                for (int y = artifact.minY(); y <= artifact.maxY(); y++) {
                    for (int z = artifact.originZ(); z < artifact.originZ() + artifact.length(); z++) {
                        for (int x = artifact.originX(); x < artifact.originX() + artifact.width(); x++) {
                            String state = cursor.next();
                            if (state == null) {
                                throw new IOException("published tile stream ended before its block count");
                            }
                            states[((y - minY) * length + z) * width + x] = state;
                        }
                    }
                }
                if (cursor.next() != null) {
                    throw new IOException("published tile stream is longer than its declared block count");
                }
                cursor.close();
            }
            for (String state : states) {
                if (state == null) {
                    throw new IOException("published tiles do not cover the release-local domain");
                }
            }
            return new FinalBlockStreamV2(width, length, minY, maxY, states);
        }
    }

    private record TileRef(OfflineTileArtifactV2 artifact, ReleaseArtifactDescriptorV2 schematic) {
    }

    private static Map<String, TileRef> finalTiles(VerifiedReleaseViewV2 view) throws IOException {
        ReleaseManifestV2 manifest = view.verification().manifest();
        boolean volume = manifest.requiredCapabilities()
                .contains(ReleaseCapabilityDependencyMatrixV2.SPARSE_VOLUME);
        String metadataType = volume ? VOLUME_METADATA_TYPE : SURFACE_METADATA_TYPE;
        String prefix = volume ? VOLUME_PREFIX : SURFACE_PREFIX;
        Map<String, ReleaseArtifactDescriptorV2> byPath = new LinkedHashMap<>();
        for (ReleaseArtifactDescriptorV2 descriptor : manifest.artifacts()) {
            byPath.put(descriptor.path(), descriptor);
        }
        OfflineTileArtifactCodecV2 codec = new OfflineTileArtifactCodecV2();
        Map<String, TileRef> tiles = new TreeMap<>();
        for (ReleaseArtifactDescriptorV2 descriptor : manifest.artifacts()) {
            if (!descriptor.artifactType().equals(metadataType)) {
                continue;
            }
            OfflineTileArtifactV2 tile = codec.read(view.root().resolve(descriptor.path()));
            ReleaseArtifactDescriptorV2 schematic = byPath.get(prefix + tile.schematicPath());
            if (schematic == null) {
                throw new IOException("published tile metadata references a missing schematic: "
                        + tile.schematicPath());
            }
            String key = tile.originX() + "," + tile.originZ() + "," + tile.width() + ","
                    + tile.length() + "," + tile.minY() + "," + tile.maxY();
            if (tiles.put(key, new TileRef(tile, schematic)) != null) {
                throw new IOException("published Release carries duplicate tile bounds: " + key);
            }
        }
        if (tiles.isEmpty()) {
            throw new IOException("published Release carries no final tiles to measure");
        }
        return tiles;
    }

    private static SpongeV3TileInspectorV2.BlockStateCursor decode(VerifiedReleaseViewV2 view, TileRef tile)
            throws IOException {
        Path path = view.root().resolve(tile.schematic().path()).normalize();
        if (!path.startsWith(view.root())) {
            throw new IOException("published tile path escapes the verified release root");
        }
        long size = Files.size(path);
        if (size != tile.schematic().byteLength()
                || size > OfflineTileArtifactV2.MAXIMUM_ARTIFACT_BYTES) {
            throw new IOException("published tile byte length changed after verification");
        }
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = input.readNBytes(Math.toIntExact(OfflineTileArtifactV2.MAXIMUM_ARTIFACT_BYTES + 1L));
        }
        SpongeV3TileInspectorV2.DecodedTile decoded =
                new SpongeV3TileInspectorV2().decode(bytes, tile.artifact().tilePlan());
        if (!decoded.inspection().semanticChecksum().equals(tile.artifact().semanticChecksum())) {
            throw new IOException("published tile decode differs from its verified metadata");
        }
        return decoded.openCursor();
    }
}
