package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Sealed V2-10-06 contract for dry-land modifier landforms.
 * Records modifier classification only — no FeatureKind and no generator is introduced.
 */
public record DryLandModifierContractV2(
        int planVersion,
        String contractVersion,
        String decisionId,
        List<Modifier> modifiers,
        List<String> compatibilityNotes,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "dry-land-modifier-contract-v1";
    public static final String DECISION_ID = "v2-10-06-dry-land-modifiers";
    public static final List<String> REQUIRED_MODIFIER_KINDS = List.of(
            "DUNE", "COASTAL_DUNE_FIELD", "INLAND_DUNE_FIELD", "BADLANDS",
            "SALT_FLAT", "DESERT_FLAT", "DRY_CANYON");

    public DryLandModifierContractV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("dry land modifier planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown dry land modifier contract version");
        }
        decisionId = FoundationValidationV2.slug(decisionId, "decisionId");
        modifiers = FoundationValidationV2.sorted(
                modifiers, "modifiers", 16, Comparator.comparing(Modifier::kind));
        compatibilityNotes = FoundationValidationV2.immutable(compatibilityNotes, "compatibilityNotes", 32);
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
        validateModifiers(modifiers);
    }

    public DryLandModifierContractV2 withCanonicalChecksum(String checksum) {
        return new DryLandModifierContractV2(
                planVersion, contractVersion, decisionId, modifiers, compatibilityNotes, checksum);
    }

    /** Frozen V2-10-06 Acceptance decision for dry-land modifier deferral. */
    public static DryLandModifierContractV2 decisionV21006() {
        return new DryLandModifierContractV2(
                VERSION,
                CONTRACT_VERSION,
                DECISION_ID,
                List.of(
                        new Modifier("DUNE", Disposition.MODIFIER_OR_FIELD, HostHint.PLAIN,
                                "Aeolian ripple/dune modifier; profile-only until ownership proves standalone"),
                        new Modifier("COASTAL_DUNE_FIELD", Disposition.MODIFIER_OR_FIELD, HostHint.PLAIN,
                                "Coastal dune belt modifier; distinct from inland dune field host"),
                        new Modifier("INLAND_DUNE_FIELD", Disposition.MODIFIER_OR_FIELD, HostHint.PLAIN,
                                "Interior erg/sand-sea modifier; no FeatureKind until slice Task"),
                        new Modifier("BADLANDS", Disposition.MODIFIER_OR_FIELD, HostHint.PLATEAU,
                                "Eroded cap-margin badland modifier; MESA/BUTTE remain plateau profiles"),
                        new Modifier("SALT_FLAT", Disposition.MODIFIER_OR_FIELD, HostHint.PLAIN,
                                "Evaporite flat modifier; basin floor ownership deferred"),
                        new Modifier("DESERT_FLAT", Disposition.MODIFIER_OR_FIELD, HostHint.PLAIN,
                                "Desert pavement/playa modifier; not a standalone FeatureKind"),
                        new Modifier("DRY_CANYON", Disposition.MODIFIER_OR_FIELD, HostHint.HOST,
                                "Arid canyon modifier distinct from sealed surface CANYON FeatureKind")),
                List.of(
                        "v1 Schema, generator 3.0.0-phase6, Release format 1, and v1 placement/Undo remain frozen",
                        "V2-10-06 introduces ESCARPMENT and PLATEAU FeatureKinds only; dry-land modifiers stay contract-only",
                        "MESA and BUTTE remain PlateauProfile values, not FeatureKinds",
                        "Surface CANYON FeatureKind and sealed canyon fixtures remain unchanged",
                        "Dry-land modifiers must not be registered as FeatureKinds or catalog modules in V2-10-06"),
                "0".repeat(64));
    }

    private static void validateModifiers(List<Modifier> modifiers) {
        if (modifiers.size() != REQUIRED_MODIFIER_KINDS.size()) {
            throw new IllegalArgumentException("modifiers must list all seven dry-land kinds");
        }
        Set<String> seen = new HashSet<>();
        for (Modifier modifier : modifiers) {
            if (!seen.add(modifier.kind())) {
                throw new IllegalArgumentException("duplicate modifier kind: " + modifier.kind());
            }
            if (modifier.disposition() != Disposition.MODIFIER_OR_FIELD) {
                throw new IllegalArgumentException("dry land modifiers must use MODIFIER_OR_FIELD disposition");
            }
        }
        if (!seen.containsAll(REQUIRED_MODIFIER_KINDS)) {
            throw new IllegalArgumentException("modifiers missing one or more required kinds");
        }
        for (String kind : modifiers.stream().map(Modifier::kind).toList()) {
            if (isForbiddenFeatureKindName(kind)) {
                throw new IllegalArgumentException("modifier kind must not be a FeatureKind: " + kind);
            }
        }
    }

    public static boolean isForbiddenFeatureKindName(String kind) {
        return Arrays.stream(com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2.FeatureKind.values())
                .anyMatch(featureKind -> featureKind.name().equals(kind));
    }

    public enum Disposition {
        MODIFIER_OR_FIELD
    }

    public enum HostHint {
        PLAIN, PLATEAU, HOST
    }

    public record Modifier(
            String kind,
            Disposition disposition,
            HostHint hostHint,
            String rationale
    ) {
        public Modifier {
            kind = FoundationValidationV2.nonBlank(kind, "kind", 64);
            Objects.requireNonNull(disposition, "disposition");
            Objects.requireNonNull(hostHint, "hostHint");
            rationale = FoundationValidationV2.nonBlank(rationale, "rationale", 512);
        }
    }
}
