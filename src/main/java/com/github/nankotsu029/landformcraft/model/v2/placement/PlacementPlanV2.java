package com.github.nankotsu029.landformcraft.model.v2.placement;

import com.github.nankotsu029.landformcraft.model.v2.release.ReleaseCapabilityDependencyMatrixV2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable Release 2 placement plan contract (V2-6-01). Binds a verified Release 2 manifest,
 * world target/bounds/anchor, capability set, canonical tile order, envelope checksum references,
 * and reservation/confirmation binding slots. Does not calculate envelopes, reserve regions,
 * snapshot, apply, or reinterpret v1 journals.
 */
public record PlacementPlanV2(
        int planVersion,
        String placementContractVersion,
        UUID placementId,
        UUID operationId,
        String requestId,
        PlacementActorV2 actor,
        PlacementTargetV2 target,
        ReleaseBindingV2 releaseBinding,
        List<String> requiredCapabilities,
        TileOrderV2 tileOrder,
        EnvelopeReferencesV2 envelopeReferences,
        ReservationConfirmationBindingV2 reservationConfirmationBinding,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PLACEMENT_CONTRACT_VERSION = "release-2-placement-contract-v1";
    public static final String UNBOUND_CHECKSUM = "0".repeat(64);
    public static final String EPOCH_INSTANT = "1970-01-01T00:00:00Z";
    public static final long MAX_CANONICAL_BYTES = 256L * 1024L;
    public static final int MAXIMUM_TILES = 1_024;
    public static final int MAXIMUM_JOURNAL_ENTRIES = 1_024;

    /** Known Release 2 capability ids owned by {@link ReleaseCapabilityDependencyMatrixV2}. */
    public static final String CAPABILITY_SURFACE_TWO_POINT_FIVE_D =
            ReleaseCapabilityDependencyMatrixV2.SURFACE_TWO_POINT_FIVE_D;
    public static final String CAPABILITY_HYDROLOGY_PLAN =
            ReleaseCapabilityDependencyMatrixV2.HYDROLOGY_PLAN;
    public static final String CAPABILITY_ENVIRONMENT_FIELDS =
            ReleaseCapabilityDependencyMatrixV2.ENVIRONMENT_FIELDS;
    public static final String CAPABILITY_SPARSE_VOLUME =
            ReleaseCapabilityDependencyMatrixV2.SPARSE_VOLUME;
    public static final List<String> CAPABILITIES_HYDROLOGY_WITH_SURFACE =
            ReleaseCapabilityDependencyMatrixV2.HYDROLOGY_WITH_SURFACE;
    public static final List<String> CAPABILITIES_ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE =
            ReleaseCapabilityDependencyMatrixV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE;
    public static final List<String> CAPABILITIES_SPARSE_VOLUME_WITH_ENVIRONMENT =
            ReleaseCapabilityDependencyMatrixV2.SPARSE_VOLUME_WITH_ENVIRONMENT;

    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SAFE_RELATIVE = Pattern.compile(
            "^(?!/)(?![A-Za-z]:)(?!.*\\\\)(?!.*(?:^|/)\\.\\.?(/|$))(?!.*//).+$");
    private static final Pattern TILE_ID = Pattern.compile("tile-x[0-9]+-z[0-9]+");
    private static final Pattern ISO_INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z");
    private static final Set<String> KNOWN_CAPABILITIES =
            ReleaseCapabilityDependencyMatrixV2.knownCapabilities();
    private static final Set<List<String>> ALLOWED_CAPABILITY_SETS =
            ReleaseCapabilityDependencyMatrixV2.validPrefixes();

    public PlacementPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("placement planVersion must be 1");
        }
        placementContractVersion = nonBlank(placementContractVersion, "placementContractVersion", 64);
        if (!PLACEMENT_CONTRACT_VERSION.equals(placementContractVersion)) {
            throw new IllegalArgumentException("unknown placement contract version");
        }
        Objects.requireNonNull(placementId, "placementId");
        Objects.requireNonNull(operationId, "operationId");
        requestId = slug(requestId, "requestId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(releaseBinding, "releaseBinding");
        requiredCapabilities = normalizeCapabilities(requiredCapabilities);
        Objects.requireNonNull(tileOrder, "tileOrder");
        Objects.requireNonNull(envelopeReferences, "envelopeReferences");
        Objects.requireNonNull(reservationConfirmationBinding, "reservationConfirmationBinding");
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateBudget(budget, tileOrder);
        target.requireContainsAnchor();
        if (tileOrder.tiles().size() > budget.maximumTiles()) {
            throw new IllegalArgumentException("placement tile count exceeds budget");
        }
    }

    public PlacementPlanV2 withCanonicalChecksum(String checksum) {
        return new PlacementPlanV2(
                planVersion,
                placementContractVersion,
                placementId,
                operationId,
                requestId,
                actor,
                target,
                releaseBinding,
                requiredCapabilities,
                tileOrder,
                envelopeReferences,
                reservationConfirmationBinding,
                budget,
                checksum);
    }

    public PlacementPlanV2 withEnvelopeReferences(EnvelopeReferencesV2 references) {
        return new PlacementPlanV2(
                planVersion,
                placementContractVersion,
                placementId,
                operationId,
                requestId,
                actor,
                target,
                releaseBinding,
                requiredCapabilities,
                tileOrder,
                Objects.requireNonNull(references, "references"),
                reservationConfirmationBinding,
                budget,
                UNBOUND_CHECKSUM);
    }

    public PlacementPlanV2 withReservationConfirmationBinding(ReservationConfirmationBindingV2 binding) {
        return new PlacementPlanV2(
                planVersion,
                placementContractVersion,
                placementId,
                operationId,
                requestId,
                actor,
                target,
                releaseBinding,
                requiredCapabilities,
                tileOrder,
                envelopeReferences,
                Objects.requireNonNull(binding, "binding"),
                budget,
                UNBOUND_CHECKSUM);
    }

    /** Fails closed unless target world and inclusive bounds match this sealed plan. */
    public void requireTarget(PlacementTargetV2 expected) {
        Objects.requireNonNull(expected, "expected");
        if (!target.equals(expected)) {
            throw new IllegalArgumentException("placement target mismatch");
        }
    }

    /** Fails closed unless Release format 2 binding matches this sealed plan. */
    public void requireReleaseBinding(ReleaseBindingV2 expected) {
        Objects.requireNonNull(expected, "expected");
        if (!releaseBinding.equals(expected)) {
            throw new IllegalArgumentException("placement release binding mismatch");
        }
    }

    public record PlacementActorV2(PlacementActorKindV2 kind, String id) {
        public PlacementActorV2 {
            Objects.requireNonNull(kind, "kind");
            id = nonBlank(id, "id", 128);
            switch (kind) {
                case PLAYER -> id = UUID.fromString(id).toString();
                case CONSOLE -> {
                    if (!"CONSOLE".equals(id)) {
                        throw new IllegalArgumentException("console actor id must be CONSOLE");
                    }
                }
                case SYSTEM -> {
                    if (!id.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) {
                        throw new IllegalArgumentException("system actor id must be an uppercase slug");
                    }
                }
            }
        }

        public static PlacementActorV2 console() {
            return new PlacementActorV2(PlacementActorKindV2.CONSOLE, "CONSOLE");
        }

        public static PlacementActorV2 player(UUID playerId) {
            return new PlacementActorV2(PlacementActorKindV2.PLAYER, playerId.toString());
        }

        public static PlacementActorV2 system(String id) {
            return new PlacementActorV2(
                    PlacementActorKindV2.SYSTEM, id.toUpperCase(Locale.ROOT));
        }

        /** Portable actor key used in confirmation binding hashes. */
        public String canonical() {
            return kind.name() + ":" + id;
        }
    }

    public record PlacementTargetV2(
            UUID worldId,
            String worldName,
            AnchorKind anchorKind,
            int anchorX,
            int anchorY,
            int anchorZ,
            int minimumX,
            int minimumY,
            int minimumZ,
            int maximumX,
            int maximumY,
            int maximumZ
    ) {
        public PlacementTargetV2 {
            Objects.requireNonNull(worldId, "worldId");
            worldName = nonBlank(worldName, "worldName", 128);
            Objects.requireNonNull(anchorKind, "anchorKind");
            if (anchorKind != AnchorKind.MINIMUM_CORNER) {
                throw new IllegalArgumentException("unsupported placement anchor kind");
            }
            if (maximumX < minimumX || maximumY < minimumY || maximumZ < minimumZ) {
                throw new IllegalArgumentException("placement bounds inverted");
            }
            if (anchorX != minimumX || anchorY != minimumY || anchorZ != minimumZ) {
                throw new IllegalArgumentException("placement anchor must equal minimum corner");
            }
        }

        void requireContainsAnchor() {
            if (anchorX < minimumX || anchorX > maximumX
                    || anchorY < minimumY || anchorY > maximumY
                    || anchorZ < minimumZ || anchorZ > maximumZ) {
                throw new IllegalArgumentException("placement anchor outside bounds");
            }
        }
    }

    public enum AnchorKind {
        MINIMUM_CORNER
    }

    public record ReleaseBindingV2(
            int bindingVersion,
            int releaseFormatVersion,
            String releaseDirectory,
            String manifestChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "release-2-placement-release-binding-v1";

        public ReleaseBindingV2 {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown placement release binding");
            }
            if (releaseFormatVersion != 2) {
                throw new IllegalArgumentException("placement requires Release format 2");
            }
            releaseDirectory = safeRelative(releaseDirectory, "releaseDirectory");
            manifestChecksum = checksum(manifestChecksum, "manifestChecksum");
            if (UNBOUND_CHECKSUM.equals(manifestChecksum)) {
                throw new IllegalArgumentException("placement release manifestChecksum must be bound");
            }
        }
    }

    public record TileOrderV2(
            String orderingContractVersion,
            List<TileRefV2> tiles
    ) {
        public static final String CONTRACT_VERSION = "release-2-placement-tile-order-v1";

        public TileOrderV2 {
            orderingContractVersion = nonBlank(orderingContractVersion, "orderingContractVersion", 64);
            if (!CONTRACT_VERSION.equals(orderingContractVersion)) {
                throw new IllegalArgumentException("unknown placement tile-order contract");
            }
            Objects.requireNonNull(tiles, "tiles");
            if (tiles.isEmpty() || tiles.size() > MAXIMUM_TILES) {
                throw new IllegalArgumentException("placement tile order size out of range");
            }
            tiles = List.copyOf(tiles);
            Set<String> ids = new HashSet<>();
            Set<Integer> indexes = new HashSet<>();
            for (int i = 0; i < tiles.size(); i++) {
                TileRefV2 tile = Objects.requireNonNull(tiles.get(i), "tiles[" + i + "]");
                if (tile.tileIndex() != i) {
                    throw new IllegalArgumentException("placement tiles must be canonical index order");
                }
                if (!ids.add(tile.tileId()) || !indexes.add(tile.tileIndex())) {
                    throw new IllegalArgumentException("duplicate placement tile identity");
                }
            }
        }
    }

    public record TileRefV2(
            String tileId,
            int tileIndex,
            int coreMinX,
            int coreMinZ,
            int coreWidth,
            int coreLength
    ) {
        public TileRefV2 {
            tileId = nonBlank(tileId, "tileId", 64);
            if (!TILE_ID.matcher(tileId).matches()) {
                throw new IllegalArgumentException("placement tileId must match tile-xN-zN");
            }
            if (tileIndex < 0 || tileIndex >= MAXIMUM_TILES) {
                throw new IllegalArgumentException("placement tileIndex out of range");
            }
            if (coreWidth < 1 || coreLength < 1) {
                throw new IllegalArgumentException("placement tile core dimensions must be positive");
            }
        }
    }

    /**
     * Checksum references for mutation/effect envelopes. V2-6-01 stores the binding slots only;
     * V2-6-02 populates checksums and sets {@code bound=true}.
     */
    public record EnvelopeReferencesV2(
            String bindingContractVersion,
            boolean bound,
            String mutationEnvelopePlanChecksum,
            String effectEnvelopePlanChecksum
    ) {
        public static final String CONTRACT_VERSION = "release-2-placement-envelope-ref-v1";

        public EnvelopeReferencesV2 {
            bindingContractVersion = nonBlank(bindingContractVersion, "bindingContractVersion", 64);
            if (!CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown placement envelope-ref contract");
            }
            mutationEnvelopePlanChecksum = checksum(mutationEnvelopePlanChecksum, "mutationEnvelopePlanChecksum");
            effectEnvelopePlanChecksum = checksum(effectEnvelopePlanChecksum, "effectEnvelopePlanChecksum");
            if (bound) {
                if (UNBOUND_CHECKSUM.equals(mutationEnvelopePlanChecksum)
                        || UNBOUND_CHECKSUM.equals(effectEnvelopePlanChecksum)) {
                    throw new IllegalArgumentException("bound envelope refs require non-zero checksums");
                }
            } else if (!UNBOUND_CHECKSUM.equals(mutationEnvelopePlanChecksum)
                    || !UNBOUND_CHECKSUM.equals(effectEnvelopePlanChecksum)) {
                throw new IllegalArgumentException("unbound envelope refs must use zero checksums");
            }
        }

        public static EnvelopeReferencesV2 unbound() {
            return new EnvelopeReferencesV2(CONTRACT_VERSION, false, UNBOUND_CHECKSUM, UNBOUND_CHECKSUM);
        }

        public static EnvelopeReferencesV2 bound(String mutationChecksum, String effectChecksum) {
            return new EnvelopeReferencesV2(CONTRACT_VERSION, true, mutationChecksum, effectChecksum);
        }
    }

    /**
     * Reservation and confirmation binding slots. V2-6-01 emits unbound/NONE; V2-6-03 populates.
     */
    public record ReservationConfirmationBindingV2(
            String bindingContractVersion,
            boolean reservationBound,
            String reservationChecksum,
            boolean confirmationIssued,
            PlacementConfirmationActionV2 confirmationAction,
            PlacementActorV2 confirmationActor,
            String confirmationHash,
            String confirmationCreatedAt,
            String confirmationExpiresAt
    ) {
        public static final String CONTRACT_VERSION = "release-2-placement-reservation-confirm-binding-v1";

        public ReservationConfirmationBindingV2 {
            bindingContractVersion = nonBlank(bindingContractVersion, "bindingContractVersion", 64);
            if (!CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown placement reservation-confirm binding");
            }
            reservationChecksum = checksum(reservationChecksum, "reservationChecksum");
            Objects.requireNonNull(confirmationAction, "confirmationAction");
            Objects.requireNonNull(confirmationActor, "confirmationActor");
            confirmationHash = Objects.requireNonNull(confirmationHash, "confirmationHash");
            confirmationCreatedAt = instant(confirmationCreatedAt, "confirmationCreatedAt");
            confirmationExpiresAt = instant(confirmationExpiresAt, "confirmationExpiresAt");
            if (reservationBound) {
                if (UNBOUND_CHECKSUM.equals(reservationChecksum)) {
                    throw new IllegalArgumentException("bound reservation requires non-zero checksum");
                }
            } else if (!UNBOUND_CHECKSUM.equals(reservationChecksum)) {
                throw new IllegalArgumentException("unbound reservation must use zero checksum");
            }
            if (confirmationIssued) {
                if (confirmationAction == PlacementConfirmationActionV2.NONE
                        || confirmationHash.isEmpty()
                        || !CHECKSUM.matcher(confirmationHash).matches()
                        || EPOCH_INSTANT.equals(confirmationCreatedAt)
                        || EPOCH_INSTANT.equals(confirmationExpiresAt)
                        || !java.time.Instant.parse(confirmationExpiresAt)
                                .isAfter(java.time.Instant.parse(confirmationCreatedAt))) {
                    throw new IllegalArgumentException("issued confirmation binding is invalid");
                }
            } else {
                if (confirmationAction != PlacementConfirmationActionV2.NONE
                        || !confirmationHash.isEmpty()
                        || !EPOCH_INSTANT.equals(confirmationCreatedAt)
                        || !EPOCH_INSTANT.equals(confirmationExpiresAt)) {
                    throw new IllegalArgumentException("unissued confirmation must be NONE at epoch");
                }
            }
        }

        public static ReservationConfirmationBindingV2 unbound(PlacementActorV2 actor) {
            return new ReservationConfirmationBindingV2(
                    CONTRACT_VERSION,
                    false,
                    UNBOUND_CHECKSUM,
                    false,
                    PlacementConfirmationActionV2.NONE,
                    actor,
                    "",
                    EPOCH_INSTANT,
                    EPOCH_INSTANT);
        }

        public static ReservationConfirmationBindingV2 reservationBound(
                String reservationChecksum,
                PlacementActorV2 actor
        ) {
            return new ReservationConfirmationBindingV2(
                    CONTRACT_VERSION,
                    true,
                    reservationChecksum,
                    false,
                    PlacementConfirmationActionV2.NONE,
                    actor,
                    "",
                    EPOCH_INSTANT,
                    EPOCH_INSTANT);
        }

        public static ReservationConfirmationBindingV2 confirmationIssued(
                String reservationChecksum,
                PlacementConfirmationActionV2 action,
                PlacementActorV2 actor,
                String confirmationHash,
                String createdAt,
                String expiresAt
        ) {
            return new ReservationConfirmationBindingV2(
                    CONTRACT_VERSION,
                    true,
                    reservationChecksum,
                    true,
                    action,
                    actor,
                    confirmationHash,
                    createdAt,
                    expiresAt);
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            int maximumTiles,
            int maximumJournalEntries,
            long estimatedCanonicalBytes,
            long maximumCanonicalBytes,
            long maximumWorkingBytes
    ) {
        public static final String VERSION = "release-2-placement-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown placement budget version");
            }
            if (maximumTiles < 1 || maximumTiles > MAXIMUM_TILES
                    || maximumJournalEntries < 1 || maximumJournalEntries > MAXIMUM_JOURNAL_ENTRIES
                    || estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || maximumWorkingBytes < 1
                    || estimatedCanonicalBytes > maximumCanonicalBytes) {
                throw new IllegalArgumentException("invalid placement budget");
            }
        }
    }

    private static void validateBudget(ResourceBudget budget, TileOrderV2 tileOrder) {
        if (tileOrder.tiles().size() > budget.maximumTiles()) {
            throw new IllegalArgumentException("placement tiles exceed budget.maximumTiles");
        }
    }

    private static List<String> normalizeCapabilities(List<String> capabilities) {
        Objects.requireNonNull(capabilities, "requiredCapabilities");
        if (capabilities.size() > KNOWN_CAPABILITIES.size()) {
            throw new IllegalArgumentException("placement capability set too large");
        }
        List<String> copy = new ArrayList<>(capabilities.size());
        Set<String> seen = new HashSet<>();
        for (String capability : capabilities) {
            String value = nonBlank(capability, "requiredCapabilities", 64);
            if (!KNOWN_CAPABILITIES.contains(value)) {
                throw new IllegalArgumentException("unknown placement capability: " + value);
            }
            if (!seen.add(value)) {
                throw new IllegalArgumentException("duplicate placement capability: " + value);
            }
            copy.add(value);
        }
        copy.sort(String::compareTo);
        List<String> normalized = List.copyOf(copy);
        if (!ALLOWED_CAPABILITY_SETS.contains(normalized)) {
            throw new IllegalArgumentException("unsupported placement capability combination");
        }
        return normalized;
    }

    private static String slug(String value, String field) {
        value = nonBlank(value, field, 64);
        if (!SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a slug");
        }
        return value;
    }

    private static String safeRelative(String value, String field) {
        value = nonBlank(value, field, 512);
        if (!SAFE_RELATIVE.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a portable relative path");
        }
        return value;
    }

    private static String checksum(String value, String field) {
        value = nonBlank(value, field, 64);
        if (!CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase sha-256 hex digest");
        }
        return value;
    }

    private static String instant(String value, String field) {
        value = nonBlank(value, field, 40);
        if (!ISO_INSTANT.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be an ISO-8601 UTC instant");
        }
        java.time.Instant.parse(value);
        return value;
    }

    private static String nonBlank(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " must be non-blank and <= " + max);
        }
        return value;
    }
}
