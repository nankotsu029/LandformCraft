package com.github.nankotsu029.landformcraft.format.v2;

import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import com.github.nankotsu029.landformcraft.model.v2.CanonicalTerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

/** Exact dispatch result without changing the v1 domain record. */
public sealed interface VersionedTerrainIntent permits VersionedTerrainIntent.V1, VersionedTerrainIntent.V2,
        VersionedTerrainIntent.CanonicalV2 {
    int version();

    record V1(TerrainIntent value) implements VersionedTerrainIntent {
        @Override public int version() { return 1; }
    }

    record V2(TerrainIntentV2 value) implements VersionedTerrainIntent {
        @Override public int version() { return 2; }
    }

    record CanonicalV2(CanonicalTerrainIntentV2 value) implements VersionedTerrainIntent {
        @Override public int version() { return 2; }
    }
}
