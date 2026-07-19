package com.github.nankotsu029.landformcraft.paper;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LandformCraftSuggestionsTest {
    private static final List<String> IDS = List.of(
            "11111111-2222-4333-8444-555555555555",
            "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
    );

    @Test
    void completesPermissionAwareRootAndSubcommands() {
        assertEquals(
                List.of("apply", "help", "selection", "undo"),
                complete(new String[]{""}, true, true, true)
        );
        assertEquals(
                List.of("help", "selection"),
                complete(new String[]{""}, true, false, false)
        );
        assertEquals(
                List.of("execute", "plan", "status", "undo"),
                complete(new String[]{"apply", ""}, true, true, true)
        );
        assertEquals(List.of("selection"), complete(new String[]{"sel"}, true, true, true));
    }

    @Test
    void completesReleaseWorldCoordinatesAndPlacementIdsButNotTokens() {
        assertEquals(
                List.of("coast/release-001"),
                complete(new String[]{"apply", "plan", "co"}, true, true, true)
        );
        assertEquals(
                List.of("world", "world_nether"),
                complete(new String[]{"apply", "plan", "coast/release-001", "wo"}, true, true, true)
        );
        assertEquals(
                List.of("12"),
                complete(new String[]{"apply", "plan", "coast/release-001", "world", ""},
                        true, true, true)
        );
        assertEquals(
                List.of(IDS.get(0)),
                complete(new String[]{"apply", "status", "111"}, true, true, true)
        );
        assertEquals(
                List.of(IDS.get(1)),
                complete(new String[]{"undo", "execute", "aaa"}, true, true, true)
        );
        assertEquals(
                List.of(),
                complete(new String[]{"apply", "execute", IDS.get(0), ""}, true, true, true)
        );
    }

    @Test
    void release2CompletionIsPermissionAwareWorldFilteredAndNeverSuggestsTokens() {
        assertEquals(
                List.of("confirm", "execute", "plan", "recover-diagnose", "recover-execute",
                        "recover-plan", "status", "undo-execute", "undo-plan"),
                completeRelease2(new String[]{"r2", ""},
                        true, true, true, true, true, true)
        );
        assertEquals(
                List.of("plan"),
                completeRelease2(new String[]{"r2", ""},
                        true, false, false, false, false, false)
        );
        assertEquals(
                List.of("world"),
                completeRelease2(new String[]{"r2", "plan", "coast/release-001", "wo"},
                        true, false, false, false, false, false)
        );
        assertEquals(
                List.of(IDS.getFirst()),
                completeRelease2(new String[]{"r2", "execute", "111"},
                        false, false, true, false, false, false)
        );
        assertEquals(
                List.of("rollback"),
                completeRelease2(new String[]{"r2", "recover-plan", "ro"},
                        false, false, false, false, false, true)
        );
        assertEquals(
                List.of(),
                completeRelease2(new String[]{"r2", "confirm", IDS.getFirst(), ""},
                        false, true, false, false, false, false)
        );
        assertEquals(
                List.of(),
                completeRelease2(new String[]{"r2", "undo-execute", IDS.getFirst(), ""},
                        false, false, false, false, true, false)
        );
        assertEquals(
                List.of(),
                completeRelease2(new String[]{"r2", "recover-execute", "rollback",
                                IDS.getFirst(), ""},
                        false, false, false, false, false, true)
        );
    }

    private static List<String> complete(
            String[] args,
            boolean canSelect,
            boolean canApply,
            boolean canUndo
    ) {
        return LandformCraftSuggestions.complete(
                args,
                canSelect,
                canApply,
                canUndo,
                IDS,
                List.of("coast/release-001", "island/release-002"),
                List.of("world", "world_nether"),
                List.of("12", "64", "-8")
        );
    }

    private static List<String> completeRelease2(
            String[] args,
            boolean canPlan,
            boolean canConfirm,
            boolean canExecute,
            boolean canStatus,
            boolean canUndo,
            boolean canRecovery
    ) {
        return LandformCraftSuggestions.completeRelease2(
                args,
                canPlan,
                canConfirm,
                canExecute,
                canStatus,
                canUndo,
                canRecovery,
                IDS,
                List.of("coast/release-001", "island/release-002"),
                List.of("world"),
                List.of("12", "64", "-8")
        );
    }
}
