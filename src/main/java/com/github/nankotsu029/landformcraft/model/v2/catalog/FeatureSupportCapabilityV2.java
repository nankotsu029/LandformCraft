package com.github.nankotsu029.landformcraft.model.v2.catalog;

/**
 * The thirteen Feature support capabilities required by Task Execution Guide §7
 * and Terrain Feature Taxonomy §1.1.
 */
public enum FeatureSupportCapabilityV2 {
    INTENT_COMPILE("intent_compile"),
    OFFLINE_GENERATE("offline_generate"),
    VALIDATION("validation"),
    PREVIEW("preview"),
    EXPORT("export"),
    STANDALONE_USAGE("standalone_usage"),
    CHILD_PLAN_USAGE("child_plan_usage"),
    VOLUME_OVERLAY_USAGE("volume_overlay_usage"),
    PAPER_APPLY("paper_apply"),
    POST_APPLY_VALIDATION("post_apply_validation"),
    SNAPSHOT("snapshot"),
    ROLLBACK("rollback"),
    RESTART_RECOVERY("restart_recovery");

    private final String wireName;

    FeatureSupportCapabilityV2(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static FeatureSupportCapabilityV2 requireKnown(String wireName) {
        for (FeatureSupportCapabilityV2 capability : values()) {
            if (capability.wireName.equals(wireName)) {
                return capability;
            }
        }
        throw new IllegalArgumentException("unknown feature support capability: " + wireName);
    }
}
