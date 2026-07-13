package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.model.PlacementJournal;

public record PreparedPlacement(PlacementJournal journal, String confirmationToken) {
}
