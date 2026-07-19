package com.github.nankotsu029.landformcraft.model.v2.catalog;

import com.github.nankotsu029.landformcraft.core.v2.catalog.FeatureSupportCatalogConsistencyVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.catalog.FeatureSupportCatalogCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureSupportCatalogV2Test {
    private final FeatureSupportCatalogCodecV2 codec = new FeatureSupportCatalogCodecV2();
    private final FeatureSupportCatalogConsistencyVerifierV2 verifier =
            new FeatureSupportCatalogConsistencyVerifierV2();

    @Test
    void builtInCatalogCoversEveryFeatureKindAndThirteenCapabilities() {
        FeatureSupportCatalogV2 catalog = verifier.requireConsistentBuiltIn();
        assertEquals(1, catalog.catalogVersion());
        assertEquals(64, catalog.placementDimensionLimit().maximumWidth());
        assertEquals(64, catalog.placementDimensionLimit().maximumLength());
        assertEquals(TerrainIntentV2.FeatureKind.values().length,
                catalog.entries().stream().filter(FeatureSupportEntryV2::hasFeatureKind).count());
        for (FeatureSupportEntryV2 entry : catalog.entries()) {
            assertEquals(FeatureSupportCapabilityV2.values().length, entry.support().asMap().size());
            FeatureSupportLevelV2 paperApply =
                    entry.support().level(FeatureSupportCapabilityV2.PAPER_APPLY);
            if (BuiltInFeatureSupportCatalogV2.PAPER_SMOKE_EVIDENCED_CAPABILITIES
                    .contains(entry.requiredReleaseCapability())) {
                assertEquals(FeatureSupportLevelV2.SUPPORTED, paperApply, entry.entryId());
            } else if (!entry.requiredReleaseCapability().isEmpty()) {
                assertEquals(FeatureSupportLevelV2.EXPERIMENTAL, paperApply, entry.entryId());
            } else {
                assertEquals(FeatureSupportLevelV2.UNSUPPORTED, paperApply, entry.entryId());
            }
        }
        assertTrue(catalog.availablePresets().contains("WATERFALL_CHAIN"));
        assertTrue(catalog.deferredDiagnostics().contains("FLOATING_REEF"));
    }

    @Test
    void refusesChildOnlyStandaloneAndPaperFalsePromotion() {
        FeatureSupportCatalogV2 catalog = codec.builtInSealed();
        FeatureSupportEntryV2 lagoon = catalog.require("LAGOON");
        assertThrows(IllegalArgumentException.class, () -> lagoon.withSupport(
                lagoon.support().with(
                        FeatureSupportCapabilityV2.STANDALONE_USAGE,
                        FeatureSupportLevelV2.SUPPORTED)));
        // V2-11-01: paper columns without a Release capability path or declared runtime stay
        // structurally rejected; the smoke-evidenced SANDY_BEACH entry is legitimately SUPPORTED.
        FeatureSupportEntryV2 plain = catalog.require("PLAIN");
        assertThrows(IllegalArgumentException.class, () -> plain.withSupport(
                plain.support().with(
                        FeatureSupportCapabilityV2.PAPER_APPLY,
                        FeatureSupportLevelV2.SUPPORTED)));
        FeatureSupportEntryV2 river = catalog.require("MEANDERING_RIVER");
        assertThrows(IllegalArgumentException.class, () -> river.withSupport(
                river.support().with(
                        FeatureSupportCapabilityV2.PAPER_APPLY,
                        FeatureSupportLevelV2.SUPPORTED)));
        FeatureSupportEntryV2 beach = catalog.require("SANDY_BEACH");
        assertEquals(FeatureSupportLevelV2.SUPPORTED,
                beach.support().level(FeatureSupportCapabilityV2.PAPER_APPLY));
        // Verifier policy: a hydrology entry promoted with runtime and smoke text still fails
        // the smoke-evidenced-prefix rule.
        FeatureSupportEntryV2 falsePromoted = new FeatureSupportEntryV2(
                river.entryId(), river.profileId(), river.primaryRole(), river.allowedUsages(),
                river.support().with(
                        FeatureSupportCapabilityV2.PAPER_APPLY, FeatureSupportLevelV2.SUPPORTED),
                river.featureKindName(), river.lifecycleStatus(),
                river.requiredReleaseCapability(),
                BuiltInFeatureSupportCatalogV2.REQUIRED_RUNTIME,
                "fabricated smoke evidence", river.notes());
        List<FeatureSupportEntryV2> tamperedEntries = new ArrayList<>();
        for (FeatureSupportEntryV2 entry : catalog.entries()) {
            tamperedEntries.add(entry.entryId().equals(river.entryId()) ? falsePromoted : entry);
        }
        FeatureSupportCatalogV2 tamperedCatalog = codec.seal(new FeatureSupportCatalogV2(
                FeatureSupportCatalogV2.VERSION,
                FeatureSupportCatalogV2.CONTRACT_VERSION,
                catalog.placementDimensionLimit(),
                tamperedEntries,
                catalog.availablePresets(),
                catalog.unsupportedDiagnostics(),
                catalog.deferredDiagnostics(),
                "0".repeat(64)));
        List<String> failures = new FeatureSupportCatalogConsistencyVerifierV2().verify(
                tamperedCatalog, new BuiltInLandformModuleCatalogV2().modules());
        assertTrue(failures.stream().anyMatch(failure ->
                failure.contains("smoke-evidenced capability prefix")), failures.toString());
        assertThrows(IllegalArgumentException.class, () -> new FeatureSupportCatalogV2(
                FeatureSupportCatalogV2.VERSION,
                FeatureSupportCatalogV2.CONTRACT_VERSION,
                new PlacementDimensionLimitV2(500, 500),
                catalog.entries(),
                catalog.availablePresets(),
                catalog.unsupportedDiagnostics(),
                catalog.deferredDiagnostics(),
                catalog.canonicalChecksum()));
        assertTrue(catalog.rejectsUnmeasuredPaperPromotion(500, 500));
        assertTrue(catalog.rejectsUnmeasuredPaperPromotion(1000, 1000));
        assertFalse(catalog.rejectsUnmeasuredPaperPromotion(64, 64));
    }

    @Test
    void rejectsUnknownCapabilityAndFutureCatalogVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> FeatureSupportCapabilityV2.requireKnown("future_capability"));
        FeatureSupportCatalogV2 sealed = codec.builtInSealed();
        assertThrows(IllegalArgumentException.class, () -> new FeatureSupportCatalogV2(
                2,
                FeatureSupportCatalogV2.CONTRACT_VERSION,
                sealed.placementDimensionLimit(),
                sealed.entries(),
                sealed.availablePresets(),
                sealed.unsupportedDiagnostics(),
                sealed.deferredDiagnostics(),
                sealed.canonicalChecksum()));
    }

    @Test
    void sealedExampleRoundTripAndChecksumAreStable(@TempDir Path temp) throws Exception {
        FeatureSupportCatalogV2 sealed = codec.builtInSealed();
        Path path = temp.resolve("feature-support-catalog-v2.json");
        codec.write(path, sealed);
        FeatureSupportCatalogV2 read = codec.read(path);
        assertEquals(sealed.canonicalChecksum(), read.canonicalChecksum());
        assertEquals(sealed.entries().size(), read.entries().size());

        Path example = Path.of("examples/v2/catalog/feature-support-catalog-v2.json");
        assertTrue(Files.isRegularFile(example), "sealed example must exist");
        FeatureSupportCatalogV2 fromExample = codec.read(example);
        assertEquals(sealed.canonicalChecksum(), fromExample.canonicalChecksum());
    }

    @Test
    void catalogOrderChecksumIndependentOfLocaleTimezoneAndThreads() throws Exception {
        FeatureSupportCatalogV2 baseline = codec.builtInSealed();
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            try (var executor = Executors.newFixedThreadPool(4)) {
                List<Callable<String>> tasks = new ArrayList<>();
                for (int index = 0; index < 4; index++) {
                    tasks.add(() -> codec.builtInSealed().canonicalChecksum());
                }
                List<Future<String>> futures = executor.invokeAll(tasks);
                for (Future<String> future : futures) {
                    assertEquals(baseline.canonicalChecksum(), future.get());
                }
            }
            List<FeatureSupportEntryV2> reversed = new ArrayList<>(baseline.entries());
            reversed.sort(Comparator.comparing(FeatureSupportEntryV2::entryId).reversed());
            FeatureSupportCatalogV2 reordered = codec.seal(new FeatureSupportCatalogV2(
                    FeatureSupportCatalogV2.VERSION,
                    FeatureSupportCatalogV2.CONTRACT_VERSION,
                    baseline.placementDimensionLimit(),
                    reversed,
                    baseline.availablePresets(),
                    baseline.unsupportedDiagnostics(),
                    baseline.deferredDiagnostics(),
                    "0".repeat(64)));
            assertEquals(baseline.canonicalChecksum(), reordered.canonicalChecksum());
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void consistencyVerifierMatchesModuleCatalog() {
        List<String> failures = verifier.verify(
                codec.builtInSealed(),
                new BuiltInLandformModuleCatalogV2().modules());
        assertEquals(List.of(), failures);
        for (TerrainIntentV2.FeatureKind kind : BuiltInFeatureSupportCatalogV2.childPlanOnlyKinds()) {
            assertEquals(FeaturePrimaryRoleV2.CHILD_PLAN_ONLY,
                    codec.builtInSealed().require(kind.name()).primaryRole());
        }
        assertEquals(ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                codec.builtInSealed().require("SANDY_BEACH").lifecycleStatus());
    }

    @Test
    void unknownKindSelectionRejected() {
        FeatureSupportCatalogV2 catalog = codec.builtInSealed();
        assertThrows(IllegalArgumentException.class, () -> catalog.require("NOT_A_REAL_KIND"));
    }

    @Test
    void tamperedChecksumAndExtraFieldRejected(@TempDir Path temp) throws Exception {
        FeatureSupportCatalogV2 sealed = codec.builtInSealed();
        Path path = temp.resolve("tampered.json");
        codec.write(path, sealed);
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Files.writeString(path, text.replace(sealed.canonicalChecksum(), "a".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> codec.read(path));

        Path extra = temp.resolve("extra.json");
        Files.writeString(extra, "{\"extra\":true," + text.substring(1));
        assertThrows(IllegalArgumentException.class, () -> codec.read(extra));
    }
}
