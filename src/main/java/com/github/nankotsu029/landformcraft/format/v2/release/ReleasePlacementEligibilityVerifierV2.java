package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.release.ReleaseCapabilityDependencyMatrixV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Pre-placement eligibility gate for a Release 2 directory or ZIP (V2-6-12).
 * Does not mutate the world; it only proves the Release is strict-verified and capability-bound
 * for placement planning.
 */
public final class ReleasePlacementEligibilityVerifierV2 {
    public static final String ELIGIBILITY_CONTRACT_VERSION = "release-2-placement-eligibility-v1";

    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    private final ReleaseCoreVerifierV2 coreVerifier;

    public ReleasePlacementEligibilityVerifierV2() {
        this(new ReleaseCoreVerifierV2(ReleaseArtifactLimitsCatalogV2.coreLimits()));
    }

    public ReleasePlacementEligibilityVerifierV2(ReleaseCoreVerifierV2 coreVerifier) {
        this.coreVerifier = Objects.requireNonNull(coreVerifier, "coreVerifier");
    }

    public EligibilityResultV2 verifyEligible(Path releasePath) throws IOException {
        return verifyEligible(releasePath, () -> false);
    }

    public EligibilityResultV2 verifyEligible(Path releasePath, CancellationToken cancellationToken)
            throws IOException {
        Objects.requireNonNull(releasePath, "releasePath");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        ReleaseArtifactLimitsCatalogV2.requireCoreWithinCatalog(ReleaseArtifactLimitsCatalogV2.coreLimits());
        ReleaseCoreVerificationV2 verification = coreVerifier.verify(releasePath, cancellationToken);
        ReleaseManifestV2 manifest = verification.manifest();
        try {
            ReleaseCrossVersionReaderPolicyV2.requireSupportedVersions(
                    manifest.releaseFormatVersion(), manifest.manifestVersion());
            ReleaseCapabilityDependencyMatrixV2.requireValidPrefix(manifest.requiredCapabilities());
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
        if (manifest.hasPendingCanonicalChecksum()) {
            throw new IOException("Release placement eligibility requires a sealed canonical checksum");
        }
        if (!CHECKSUM.matcher(manifest.canonicalChecksum()).matches()) {
            throw new IOException("Release placement eligibility requires a lowercase sha-256 checksum");
        }
        return new EligibilityResultV2(
                ELIGIBILITY_CONTRACT_VERSION,
                verification.verifiedPath(),
                manifest,
                List.copyOf(manifest.requiredCapabilities()),
                manifest.canonicalChecksum(),
                verification.verifiedFiles(),
                verification.verifiedBytes(),
                true
        );
    }

    /**
     * Ensures a placement plan's Release binding matches a previously verified eligibility result.
     */
    public void requirePlanMatches(
            EligibilityResultV2 eligibility,
            PlacementPlanV2 plan
    ) throws IOException {
        Objects.requireNonNull(eligibility, "eligibility");
        Objects.requireNonNull(plan, "plan");
        if (!eligibility.eligible()) {
            throw new IOException("Release is not placement-eligible");
        }
        if (!eligibility.manifestChecksum().equals(plan.releaseBinding().manifestChecksum())) {
            throw new IOException("placement plan Release checksum does not match eligibility evidence");
        }
        if (!eligibility.requiredCapabilities().equals(plan.requiredCapabilities())) {
            throw new IOException("placement plan capability set does not match Release eligibility");
        }
    }

    public record EligibilityResultV2(
            String eligibilityContractVersion,
            Path verifiedPath,
            ReleaseManifestV2 manifest,
            List<String> requiredCapabilities,
            String manifestChecksum,
            int verifiedFiles,
            long verifiedBytes,
            boolean eligible
    ) {
        public EligibilityResultV2 {
            if (!ELIGIBILITY_CONTRACT_VERSION.equals(eligibilityContractVersion)) {
                throw new IllegalArgumentException("unknown placement eligibility contract");
            }
            Objects.requireNonNull(verifiedPath, "verifiedPath");
            Objects.requireNonNull(manifest, "manifest");
            Objects.requireNonNull(requiredCapabilities, "requiredCapabilities");
            requiredCapabilities = List.copyOf(requiredCapabilities);
            if (!CHECKSUM.matcher(Objects.requireNonNull(manifestChecksum, "manifestChecksum")).matches()) {
                throw new IllegalArgumentException("manifestChecksum must be lowercase sha-256");
            }
            if (verifiedFiles < 1 || verifiedBytes < 1) {
                throw new IllegalArgumentException("eligibility requires verified files and bytes");
            }
            if (!eligible) {
                throw new IllegalArgumentException("EligibilityResultV2 only seals eligible=true results");
            }
        }
    }
}
