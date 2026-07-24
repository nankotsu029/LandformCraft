package com.github.nankotsu029.landformcraft.model.v2.design;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * V2-19-08: the reachable kind and capability set presented <em>before</em> a provider is called.
 *
 * <p>The 2026-07-23 cross-cutting audit (§2.2, §5.1) found that neither the provider nor the
 * operator can see which FeatureKinds a design may actually use: the Feature Support Catalog's
 * {@code export} column includes plan-level and shared-capability support, so it is not the same
 * question as "can an intent declaring this kind select a production pipeline". This record answers
 * the second question only, and is therefore projected from the production dispatch registry — never
 * from the support catalog.</p>
 *
 * <p>It is advisory data. It narrows nothing: the historic 60-kind Schema still accepts every kind,
 * and a design declaring an unreachable kind is still published (the lint is report-only).</p>
 */
public record DesignSupportSurfaceV2(
        String contractVersion,
        String dispatchRegistryVersion,
        String dispatchRegistryChecksum,
        String reachabilityChecksum,
        List<String> productionConnectedKinds,
        List<String> offlineProductionKinds,
        List<String> contractOnlyKinds,
        List<String> requiredCompanionKinds,
        List<String> designCapabilities
) {
    /** Version of the lint contract, and of the advisory text derived from it. */
    public static final String CONTRACT_VERSION = "design-support-lint-v1";

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern CONTRACT_ID = Pattern.compile("[a-z0-9][a-z0-9.-]{0,63}");
    private static final int MAXIMUM_KINDS = 64;

    public DesignSupportSurfaceV2 {
        contractVersion = requireContractId(contractVersion, "contractVersion");
        dispatchRegistryVersion = requireContractId(dispatchRegistryVersion, "dispatchRegistryVersion");
        dispatchRegistryChecksum = requireChecksum(dispatchRegistryChecksum, "dispatchRegistryChecksum");
        reachabilityChecksum = requireChecksum(reachabilityChecksum, "reachabilityChecksum");
        productionConnectedKinds = requireIdentifiers(productionConnectedKinds, "productionConnectedKinds");
        offlineProductionKinds = requireIdentifiers(offlineProductionKinds, "offlineProductionKinds");
        contractOnlyKinds = requireIdentifiers(contractOnlyKinds, "contractOnlyKinds");
        requiredCompanionKinds = requireIdentifiers(requiredCompanionKinds, "requiredCompanionKinds");
        designCapabilities = requireIdentifiers(designCapabilities, "designCapabilities");
        if (productionConnectedKinds.isEmpty() && offlineProductionKinds.isEmpty()) {
            throw new IllegalArgumentException("a design support surface requires at least one reachable kind");
        }
    }

    /** Kinds an intent may declare and still select a production pipeline, sorted by name. */
    public List<String> reachableKinds() {
        return java.util.stream.Stream.concat(
                        productionConnectedKinds.stream(), offlineProductionKinds.stream())
                .sorted()
                .toList();
    }

    private static List<String> requireIdentifiers(List<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName);
        List<String> copied = List.copyOf(values);
        if (copied.size() > MAXIMUM_KINDS) {
            throw new IllegalArgumentException(fieldName + " must hold at most " + MAXIMUM_KINDS + " entries");
        }
        String previous = null;
        for (String value : copied) {
            Objects.requireNonNull(value, fieldName);
            if (!IDENTIFIER.matcher(value).matches()) {
                throw new IllegalArgumentException(fieldName + " must match " + IDENTIFIER + ": " + value);
            }
            if (previous != null && previous.compareTo(value) >= 0) {
                throw new IllegalArgumentException(fieldName + " must be sorted and unique");
            }
            previous = value;
        }
        return copied;
    }

    private static String requireContractId(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (!CONTRACT_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must match " + CONTRACT_ID);
        }
        return value;
    }

    private static String requireChecksum(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (!SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must be a lowercase SHA-256");
        }
        return value;
    }
}
