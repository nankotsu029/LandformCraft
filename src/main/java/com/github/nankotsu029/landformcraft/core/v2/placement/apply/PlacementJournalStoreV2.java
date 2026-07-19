package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;

import java.io.IOException;

/** Durable, atomic journal boundary. The apply service invokes it only on its owned worker pool. */
@FunctionalInterface
public interface PlacementJournalStoreV2 {
    void save(PlacementJournalV2 journal) throws IOException;
}
