package com.github.nankotsu029.landformcraft.model.v2.release;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseCapabilityDependencyMatrixV2Test {
    @Test
    void freezesExactlyFiveValidPrefixesInStableOrder() {
        assertEquals(5, ReleaseCapabilityDependencyMatrixV2.validPrefixes().size());
        assertEquals(List.of(
                ReleaseCapabilityDependencyMatrixV2.CORE_ONLY,
                ReleaseCapabilityDependencyMatrixV2.SURFACE_ONLY,
                ReleaseCapabilityDependencyMatrixV2.HYDROLOGY_WITH_SURFACE,
                ReleaseCapabilityDependencyMatrixV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE,
                ReleaseCapabilityDependencyMatrixV2.SPARSE_VOLUME_WITH_ENVIRONMENT
        ), ReleaseCapabilityDependencyMatrixV2.validPrefixesInStableOrder());
    }

    @Test
    void rejectsUnknownCapabilityAndIncompleteDependencyChains() {
        assertThrows(IllegalArgumentException.class,
                () -> ReleaseCapabilityDependencyMatrixV2.requireValidPrefix(List.of("future-capability")));
        assertThrows(IllegalArgumentException.class,
                () -> ReleaseCapabilityDependencyMatrixV2.requireValidPrefix(
                        List.of(ReleaseCapabilityDependencyMatrixV2.HYDROLOGY_PLAN)));
        assertThrows(IllegalArgumentException.class,
                () -> ReleaseCapabilityDependencyMatrixV2.requireValidPrefix(List.of(
                        ReleaseCapabilityDependencyMatrixV2.ENVIRONMENT_FIELDS,
                        ReleaseCapabilityDependencyMatrixV2.SURFACE_TWO_POINT_FIVE_D)));
        assertThrows(IllegalArgumentException.class,
                () -> ReleaseCapabilityDependencyMatrixV2.requireValidPrefix(List.of(
                        ReleaseCapabilityDependencyMatrixV2.SPARSE_VOLUME,
                        ReleaseCapabilityDependencyMatrixV2.SURFACE_TWO_POINT_FIVE_D)));
        assertTrue(ReleaseCapabilityDependencyMatrixV2.dependencyFailureMessage(
                List.of(ReleaseCapabilityDependencyMatrixV2.HYDROLOGY_PLAN)).contains("surface-2_5d"));
    }

    @Test
    void normalizeAndValidityAreLocaleAndTimezoneInvariant() {
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            List<String> shuffled = List.of(
                    ReleaseCapabilityDependencyMatrixV2.SURFACE_TWO_POINT_FIVE_D,
                    ReleaseCapabilityDependencyMatrixV2.HYDROLOGY_PLAN);
            assertEquals(ReleaseCapabilityDependencyMatrixV2.HYDROLOGY_WITH_SURFACE,
                    ReleaseCapabilityDependencyMatrixV2.normalize(shuffled));
            assertTrue(ReleaseCapabilityDependencyMatrixV2.isValidPrefix(shuffled));
            assertFalse(ReleaseCapabilityDependencyMatrixV2.isValidPrefix(
                    List.of(ReleaseCapabilityDependencyMatrixV2.SPARSE_VOLUME)));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }
}
