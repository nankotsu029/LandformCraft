package com.github.nankotsu029.landformcraft.format.v2.release;

/**
 * Cross-version reader policy for Release format 2 (V2-6-12). Future format or manifest
 * versions are never forward-read; callers must refuse rather than upgrade in place.
 */
public final class ReleaseCrossVersionReaderPolicyV2 {
    public static final String POLICY_VERSION = "release-2-cross-version-reader-policy-v1";
    public static final int SUPPORTED_RELEASE_FORMAT_VERSION = 2;
    public static final int SUPPORTED_MANIFEST_VERSION = 1;

    private ReleaseCrossVersionReaderPolicyV2() {
    }

    public static void requireSupportedVersions(int releaseFormatVersion, int manifestVersion) {
        if (releaseFormatVersion != SUPPORTED_RELEASE_FORMAT_VERSION
                || manifestVersion != SUPPORTED_MANIFEST_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported Release format/manifest version: "
                            + releaseFormatVersion + "/" + manifestVersion
                            + " (policy " + POLICY_VERSION + " supports only "
                            + SUPPORTED_RELEASE_FORMAT_VERSION + "/"
                            + SUPPORTED_MANIFEST_VERSION + ")");
        }
    }

    public static boolean isSupported(int releaseFormatVersion, int manifestVersion) {
        return releaseFormatVersion == SUPPORTED_RELEASE_FORMAT_VERSION
                && manifestVersion == SUPPORTED_MANIFEST_VERSION;
    }
}
