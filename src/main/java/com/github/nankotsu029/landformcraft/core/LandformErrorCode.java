package com.github.nankotsu029.landformcraft.core;

/** Stable public error identifiers. Enum names are not exposed; the explicit code string is. */
public enum LandformErrorCode {
    REQUEST_INVALID("LFC-REQUEST-INVALID"),
    IMAGE_UNSAFE("LFC-IMAGE-UNSAFE"),
    PROVIDER_TIMEOUT("LFC-PROVIDER-TIMEOUT"),
    PROVIDER_FAILED("LFC-PROVIDER-FAILED"),
    RELEASE_TAMPERED("LFC-RELEASE-TAMPERED"),
    PLACEMENT_OVERLAP("LFC-PLACEMENT-OVERLAP"),
    SNAPSHOT_NO_SPACE("LFC-SNAPSHOT-NO-SPACE"),
    CONFIRM_INVALID("LFC-CONFIRM-INVALID"),
    CONFIRM_ACTOR_MISMATCH("LFC-CONFIRM-ACTOR-MISMATCH"),
    RECOVERY_REQUIRED("LFC-RECOVERY-REQUIRED"),
    ASSET_UNSAFE("LFC-ASSET-UNSAFE"),
    PATH_UNSAFE("LFC-PATH-UNSAFE"),
    NOT_FOUND("LFC-NOT-FOUND"),
    CONFIG_INVALID("LFC-CONFIG-INVALID"),
    INTERNAL("LFC-INTERNAL");

    private final String code;

    LandformErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
