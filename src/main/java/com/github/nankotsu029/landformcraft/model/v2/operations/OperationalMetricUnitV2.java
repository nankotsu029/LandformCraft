package com.github.nankotsu029.landformcraft.model.v2.operations;

/**
 * Stable units for operational metrics. Reductions must keep these units; callers must not invent
 * free-form unit strings.
 */
public enum OperationalMetricUnitV2 {
    COUNT,
    BYTES,
    TICKS
}
