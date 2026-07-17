package com.github.nankotsu029.landformcraft.generator.v2;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class NamedSeedDeriverV2Test {
    @Test
    void seedIsStableAcrossOrderThreadsLocaleTimezoneAndDefaultCharsetProperty() throws Exception {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        String originalEncoding = System.getProperty("file.encoding");
        try {
            long expected = seed("feature-a");
            assertEquals(-1_214_016_593_840_718_504L, expected);
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            System.setProperty("file.encoding", "UTF-16");

            Map<String, Long> forward = new HashMap<>();
            for (String feature : List.of("feature-a", "feature-b", "feature-c")) forward.put(feature, seed(feature));
            Map<String, Long> reverse = new HashMap<>();
            for (String feature : List.of("feature-c", "feature-b", "feature-a")) reverse.put(feature, seed(feature));
            assertEquals(forward, reverse);
            assertEquals(expected, seed("feature-a"));

            try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
                assertEquals(expected, one.submit(() -> seed("feature-a")).get());
                assertEquals(expected, four.submit(() -> seed("feature-a")).get());
            }
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
            if (originalEncoding == null) System.clearProperty("file.encoding");
            else System.setProperty("file.encoding", originalEncoding);
        }
    }

    @Test
    void everyNamedInputParticipatesWithoutUsingObjectHashCodeOrOrdinal() {
        long baseline = seed("feature-a");
        assertNotEquals(baseline, NamedSeedDeriverV2.derive(
                827414L, "v2.feature.diagnostic", "0.1.0", "feature-a", "terrain.v2.feature", "v2-diagnostic"));
        assertNotEquals(baseline, NamedSeedDeriverV2.derive(
                827413L, "v2.feature.other", "0.1.0", "feature-a", "terrain.v2.feature", "v2-diagnostic"));
        assertNotEquals(baseline, NamedSeedDeriverV2.derive(
                827413L, "v2.feature.diagnostic", "0.2.0", "feature-a", "terrain.v2.feature", "v2-diagnostic"));
        assertNotEquals(baseline, seed("feature-b"));
        assertNotEquals(baseline, NamedSeedDeriverV2.derive(
                827413L, "v2.feature.diagnostic", "0.1.0", "feature-a", "terrain.v2.other", "v2-diagnostic"));
        assertNotEquals(baseline, NamedSeedDeriverV2.derive(
                827413L, "v2.feature.diagnostic", "0.1.0", "feature-a", "terrain.v2.feature", "v2-diagnostic-2"));
    }

    private static long seed(String featureId) {
        return NamedSeedDeriverV2.derive(
                827413L, "v2.feature.diagnostic", "0.1.0", featureId, "terrain.v2.feature", "v2-diagnostic");
    }
}
