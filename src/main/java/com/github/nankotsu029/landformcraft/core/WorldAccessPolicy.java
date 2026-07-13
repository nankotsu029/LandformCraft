package com.github.nankotsu029.landformcraft.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable allow/deny policy captured at plugin startup for placement safety. */
public record WorldAccessPolicy(Set<String> allowedWorlds, Set<String> deniedWorlds) {
    public WorldAccessPolicy(List<String> allowedWorlds, List<String> deniedWorlds) {
        this(Set.copyOf(allowedWorlds), Set.copyOf(deniedWorlds));
    }

    public WorldAccessPolicy {
        allowedWorlds = Set.copyOf(allowedWorlds);
        deniedWorlds = Set.copyOf(deniedWorlds);
        if (allowedWorlds.stream().anyMatch(WorldAccessPolicy::invalid)
                || deniedWorlds.stream().anyMatch(WorldAccessPolicy::invalid)) {
            throw new IllegalArgumentException("world policy names must be non-blank and at most 128 characters");
        }
        HashSet<String> overlap = new HashSet<>(allowedWorlds);
        overlap.retainAll(deniedWorlds);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("world allow and deny lists overlap");
        }
    }

    public void requireAllowed(String worldName) {
        if (deniedWorlds.contains(worldName)
                || !allowedWorlds.isEmpty() && !allowedWorlds.contains(worldName)) {
            throw new LandformException(LandformErrorCode.CONFIG_INVALID,
                    "Placement is not allowed in this world.", "placement-plan", worldName,
                    "world-policy", "Use an allowed test world or update config and restart Paper.");
        }
    }

    private static boolean invalid(String value) {
        return value == null || value.isBlank() || value.length() > 128;
    }
}
