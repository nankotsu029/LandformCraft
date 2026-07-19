package com.github.nankotsu029.landformcraft.model.v2.placement;

import com.github.nankotsu029.landformcraft.model.v2.catalog.PlacementDimensionLimitV2;

import java.util.Locale;
import java.util.Objects;

/**
 * Re-measurement-only escape hatch for Release 2 Paper placement dimensions (V2-11-02).
 *
 * <p>Normal operation is clamped to the Feature Support Catalog hard limit by
 * {@link Release2MeasuredDimensionGateV2}. This profile exists solely so the dedicated
 * measurement Tasks {@code V2-11-04} (500×500) and {@code V2-11-05} (1000×1000) can run
 * above-catalog layouts on an isolated host, and it is disabled by default. Enabling it requires
 * all three of an explicit configuration flag, a named isolated world, and a CONSOLE/RCON
 * operator actor; an in-game Player can never reach an unmeasured dimension. Admitting a
 * dimension here does not promote it: catalog {@code SUPPORTED} promotion stays with
 * {@code V2-11-06} and its measurement evidence.
 */
public record Release2MeasurementProfileV2(
        boolean enabled,
        String isolatedWorldName,
        int maximumWidth,
        int maximumLength
) {
    public static final String CONFIG_ENABLED_KEY =
            "placement.release2.measurement-profile.enabled";
    public static final String CONFIG_WORLD_KEY =
            "placement.release2.measurement-profile.isolated-world";
    public static final String CONFIG_WIDTH_KEY =
            "placement.release2.measurement-profile.max-width";
    public static final String CONFIG_LENGTH_KEY =
            "placement.release2.measurement-profile.max-length";

    public Release2MeasurementProfileV2 {
        Objects.requireNonNull(isolatedWorldName, "isolatedWorldName");
        isolatedWorldName = isolatedWorldName.strip();
        if (!enabled) {
            if (!isolatedWorldName.isEmpty() || maximumWidth != 0 || maximumLength != 0) {
                throw new IllegalArgumentException(
                        "disabled measurement profile must not declare a world or ceiling");
            }
        } else {
            if (isolatedWorldName.isEmpty()) {
                throw new IllegalArgumentException(
                        CONFIG_WORLD_KEY + " must name the isolated measurement world");
            }
            if (isolatedWorldName.length() > 128) {
                throw new IllegalArgumentException(CONFIG_WORLD_KEY + " is too long");
            }
            requireCeiling(maximumWidth, CONFIG_WIDTH_KEY);
            requireCeiling(maximumLength, CONFIG_LENGTH_KEY);
        }
    }

    private static void requireCeiling(int value, String key) {
        if (value < 1 || value > PlacementDimensionLimitV2.CATALOG_BUDGET_MAXIMUM) {
            throw new IllegalArgumentException(
                    key + " must be between 1 and "
                            + PlacementDimensionLimitV2.CATALOG_BUDGET_MAXIMUM);
        }
    }

    /** Default for every normal server: no above-catalog dimension is reachable. */
    public static Release2MeasurementProfileV2 disabled() {
        return new Release2MeasurementProfileV2(false, "", 0, 0);
    }

    public static Release2MeasurementProfileV2 forIsolatedWorld(
            String isolatedWorldName, int maximumWidth, int maximumLength) {
        return new Release2MeasurementProfileV2(
                true, isolatedWorldName, maximumWidth, maximumLength);
    }

    /**
     * A layout is admitted only when the profile is explicitly enabled, the target world is the
     * declared isolated world, the actor is a CONSOLE/RCON operator, and the layout is inside the
     * declared measurement ceiling.
     */
    public boolean admits(int width, int length, String worldName, PlacementActorKindV2 actorKind) {
        if (!enabled || worldName == null || actorKind != PlacementActorKindV2.CONSOLE) {
            return false;
        }
        if (!isolatedWorldName.equals(worldName.strip())) {
            return false;
        }
        return width >= 1 && length >= 1 && width <= maximumWidth && length <= maximumLength;
    }

    /** Stable, secret-free description for operator-facing errors and startup logs. */
    public String describe() {
        if (!enabled) {
            return "measurement-profile=disabled";
        }
        return String.format(
                Locale.ROOT,
                "measurement-profile=enabled world=%s ceiling=%dx%d actor=CONSOLE",
                isolatedWorldName, maximumWidth, maximumLength);
    }
}
