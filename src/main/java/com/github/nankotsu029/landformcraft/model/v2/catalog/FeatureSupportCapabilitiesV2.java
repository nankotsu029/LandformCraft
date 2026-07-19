package com.github.nankotsu029.landformcraft.model.v2.catalog;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Exact thirteen-capability support vector for one catalog entry. */
public record FeatureSupportCapabilitiesV2(
        FeatureSupportLevelV2 intentCompile,
        FeatureSupportLevelV2 offlineGenerate,
        FeatureSupportLevelV2 validation,
        FeatureSupportLevelV2 preview,
        FeatureSupportLevelV2 export,
        FeatureSupportLevelV2 standaloneUsage,
        FeatureSupportLevelV2 childPlanUsage,
        FeatureSupportLevelV2 volumeOverlayUsage,
        FeatureSupportLevelV2 paperApply,
        FeatureSupportLevelV2 postApplyValidation,
        FeatureSupportLevelV2 snapshot,
        FeatureSupportLevelV2 rollback,
        FeatureSupportLevelV2 restartRecovery
) {
    public FeatureSupportCapabilitiesV2 {
        Objects.requireNonNull(intentCompile, "intent_compile");
        Objects.requireNonNull(offlineGenerate, "offline_generate");
        Objects.requireNonNull(validation, "validation");
        Objects.requireNonNull(preview, "preview");
        Objects.requireNonNull(export, "export");
        Objects.requireNonNull(standaloneUsage, "standalone_usage");
        Objects.requireNonNull(childPlanUsage, "child_plan_usage");
        Objects.requireNonNull(volumeOverlayUsage, "volume_overlay_usage");
        Objects.requireNonNull(paperApply, "paper_apply");
        Objects.requireNonNull(postApplyValidation, "post_apply_validation");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(rollback, "rollback");
        Objects.requireNonNull(restartRecovery, "restart_recovery");
    }

    public FeatureSupportLevelV2 level(FeatureSupportCapabilityV2 capability) {
        Objects.requireNonNull(capability, "capability");
        return switch (capability) {
            case INTENT_COMPILE -> intentCompile;
            case OFFLINE_GENERATE -> offlineGenerate;
            case VALIDATION -> validation;
            case PREVIEW -> preview;
            case EXPORT -> export;
            case STANDALONE_USAGE -> standaloneUsage;
            case CHILD_PLAN_USAGE -> childPlanUsage;
            case VOLUME_OVERLAY_USAGE -> volumeOverlayUsage;
            case PAPER_APPLY -> paperApply;
            case POST_APPLY_VALIDATION -> postApplyValidation;
            case SNAPSHOT -> snapshot;
            case ROLLBACK -> rollback;
            case RESTART_RECOVERY -> restartRecovery;
        };
    }

    public Map<FeatureSupportCapabilityV2, FeatureSupportLevelV2> asMap() {
        EnumMap<FeatureSupportCapabilityV2, FeatureSupportLevelV2> map =
                new EnumMap<>(FeatureSupportCapabilityV2.class);
        for (FeatureSupportCapabilityV2 capability : FeatureSupportCapabilityV2.values()) {
            map.put(capability, level(capability));
        }
        return Map.copyOf(map);
    }

    public FeatureSupportCapabilitiesV2 with(
            FeatureSupportCapabilityV2 capability,
            FeatureSupportLevelV2 level
    ) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(level, "level");
        return switch (capability) {
            case INTENT_COMPILE -> new FeatureSupportCapabilitiesV2(
                    level, offlineGenerate, validation, preview, export, standaloneUsage,
                    childPlanUsage, volumeOverlayUsage, paperApply, postApplyValidation,
                    snapshot, rollback, restartRecovery);
            case OFFLINE_GENERATE -> new FeatureSupportCapabilitiesV2(
                    intentCompile, level, validation, preview, export, standaloneUsage,
                    childPlanUsage, volumeOverlayUsage, paperApply, postApplyValidation,
                    snapshot, rollback, restartRecovery);
            case VALIDATION -> new FeatureSupportCapabilitiesV2(
                    intentCompile, offlineGenerate, level, preview, export, standaloneUsage,
                    childPlanUsage, volumeOverlayUsage, paperApply, postApplyValidation,
                    snapshot, rollback, restartRecovery);
            case PREVIEW -> new FeatureSupportCapabilitiesV2(
                    intentCompile, offlineGenerate, validation, level, export, standaloneUsage,
                    childPlanUsage, volumeOverlayUsage, paperApply, postApplyValidation,
                    snapshot, rollback, restartRecovery);
            case EXPORT -> new FeatureSupportCapabilitiesV2(
                    intentCompile, offlineGenerate, validation, preview, level, standaloneUsage,
                    childPlanUsage, volumeOverlayUsage, paperApply, postApplyValidation,
                    snapshot, rollback, restartRecovery);
            case STANDALONE_USAGE -> new FeatureSupportCapabilitiesV2(
                    intentCompile, offlineGenerate, validation, preview, export, level,
                    childPlanUsage, volumeOverlayUsage, paperApply, postApplyValidation,
                    snapshot, rollback, restartRecovery);
            case CHILD_PLAN_USAGE -> new FeatureSupportCapabilitiesV2(
                    intentCompile, offlineGenerate, validation, preview, export, standaloneUsage,
                    level, volumeOverlayUsage, paperApply, postApplyValidation,
                    snapshot, rollback, restartRecovery);
            case VOLUME_OVERLAY_USAGE -> new FeatureSupportCapabilitiesV2(
                    intentCompile, offlineGenerate, validation, preview, export, standaloneUsage,
                    childPlanUsage, level, paperApply, postApplyValidation,
                    snapshot, rollback, restartRecovery);
            case PAPER_APPLY -> new FeatureSupportCapabilitiesV2(
                    intentCompile, offlineGenerate, validation, preview, export, standaloneUsage,
                    childPlanUsage, volumeOverlayUsage, level, postApplyValidation,
                    snapshot, rollback, restartRecovery);
            case POST_APPLY_VALIDATION -> new FeatureSupportCapabilitiesV2(
                    intentCompile, offlineGenerate, validation, preview, export, standaloneUsage,
                    childPlanUsage, volumeOverlayUsage, paperApply, level,
                    snapshot, rollback, restartRecovery);
            case SNAPSHOT -> new FeatureSupportCapabilitiesV2(
                    intentCompile, offlineGenerate, validation, preview, export, standaloneUsage,
                    childPlanUsage, volumeOverlayUsage, paperApply, postApplyValidation,
                    level, rollback, restartRecovery);
            case ROLLBACK -> new FeatureSupportCapabilitiesV2(
                    intentCompile, offlineGenerate, validation, preview, export, standaloneUsage,
                    childPlanUsage, volumeOverlayUsage, paperApply, postApplyValidation,
                    snapshot, level, restartRecovery);
            case RESTART_RECOVERY -> new FeatureSupportCapabilitiesV2(
                    intentCompile, offlineGenerate, validation, preview, export, standaloneUsage,
                    childPlanUsage, volumeOverlayUsage, paperApply, postApplyValidation,
                    snapshot, rollback, level);
        };
    }
}
