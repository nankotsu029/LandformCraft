package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.MultiSourceReconciliationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.ImageFidelityReconcileRoleV2;
import com.github.nankotsu029.landformcraft.model.v2.ImageFidelitySourceKindV2;
import com.github.nankotsu029.landformcraft.model.v2.MultiSourceConflictCodeV2;
import com.github.nankotsu029.landformcraft.model.v2.MultiSourceReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.MultiSourceReconciliationStatusV2;
import com.github.nankotsu029.landformcraft.preview.v2.MultiSourceReconciliationPreviewIndexCodecV2;
import com.github.nankotsu029.landformcraft.preview.v2.MultiSourceReconciliationPreviewRendererV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiSourceReconciliationServiceV2Test {
    private final MultiSourceReconciliationServiceV2 service = new MultiSourceReconciliationServiceV2();
    private final MultiSourceReconciliationArtifactCodecV2 codec =
            new MultiSourceReconciliationArtifactCodecV2();

    @Test
    void imageDraftBeatsPromptSoftAndRecordsSuppression() {
        // cell0: image WATER, prompt LAND → image wins
        // cell1: both WATER → agree
        // cell2: only prompt LAND → prompt wins (image absent)
        // cell3: empty
        byte[] image = samples(0, 0, 255, 255);
        byte[] prompt = samples(1, 0, 1, 255);
        var options = options(
                layer("image.a", ImageFidelitySourceKindV2.IMAGE_DRAFT, 255, image),
                layer("prompt.a", ImageFidelitySourceKindV2.PROMPT_SOFT, 255, prompt));
        var result = service.reconcile(2, 2, options, () -> false);
        assertEquals(MultiSourceReconciliationStatusV2.RESOLVED, result.status());
        assertEquals(0, Byte.toUnsignedInt(result.result()[0]));
        assertEquals(0, Byte.toUnsignedInt(result.result()[1]));
        assertEquals(1, Byte.toUnsignedInt(result.result()[2]));
        assertEquals(255, Byte.toUnsignedInt(result.result()[3]));
        assertEquals(MultiSourceConflictCodeV2.NONE.code(), Byte.toUnsignedInt(result.conflict()[0]));
        var promptDiff = result.sourceDiffs().stream()
                .filter(d -> d.sourceId().equals("prompt.a")).findFirst().orElseThrow();
        assertTrue(promptDiff.suppressedCells() >= 1);
        assertEquals(promptDiff.presentCells(),
                promptDiff.agreedCells() + promptDiff.suppressedCells() + promptDiff.conflictCells());
    }

    @Test
    void hardVersusHardConflictFailsClosed() {
        byte[] left = samples(0, 0);
        byte[] right = samples(1, 0);
        var options = options(
                layer("manual.hard", ImageFidelitySourceKindV2.MANUAL_HARD, 255, left),
                layer("map.hard", ImageFidelitySourceKindV2.HARD_CANONICAL_MAP, 255, right));
        var result = service.reconcile(2, 1, options, () -> false);
        assertEquals(MultiSourceReconciliationStatusV2.UNRESOLVED_HARD_CONFLICT, result.status());
        assertEquals(1, result.hardConflictCells());
        assertEquals(255, Byte.toUnsignedInt(result.result()[0]));
        assertEquals(MultiSourceConflictCodeV2.HARD_CONFLICT.code(), Byte.toUnsignedInt(result.conflict()[0]));
        assertEquals(0, Byte.toUnsignedInt(result.result()[1]));
    }

    @Test
    void softPeerConflictAtSameRankFailsClosed() {
        // Two IMAGE_DRAFT layers disagree — same rank, no last-write-wins.
        byte[] a = samples(0);
        byte[] b = samples(1);
        var options = options(
                layer("image.a", ImageFidelitySourceKindV2.IMAGE_DRAFT, 255, a),
                layer("image.b", ImageFidelitySourceKindV2.IMAGE_DRAFT, 255, b));
        var result = service.reconcile(1, 1, options, () -> false);
        assertEquals(MultiSourceReconciliationStatusV2.UNRESOLVED_SOFT_PEER_CONFLICT, result.status());
        assertEquals(1, result.softPeerConflictCells());
        assertEquals(MultiSourceConflictCodeV2.SOFT_PEER_CONFLICT.code(),
                Byte.toUnsignedInt(result.conflict()[0]));
    }

    @Test
    void publishesArtifactAndPreview(@TempDir Path root) throws Exception {
        byte[] image = samples(0, 1);
        byte[] prompt = samples(1, 1);
        var options = options(
                layer("image.a", ImageFidelitySourceKindV2.IMAGE_DRAFT, 255, image),
                layer("prompt.a", ImageFidelitySourceKindV2.PROMPT_SOFT, 255, prompt));
        Path artifactDir = root.resolve("artifact");
        MultiSourceReconciliationArtifactV2 artifact = service.reconcileAndPublish(
                artifactDir, 2, 1, options, () -> false);
        assertEquals(MultiSourceReconciliationStatusV2.RESOLVED, artifact.status());
        assertEquals(artifact, codec.readAndVerify(
                artifactDir.resolve(MultiSourceReconciliationArtifactCodecV2.INDEX_FILE_NAME),
                artifactDir,
                () -> false));

        var reconcile = service.reconcile(2, 1, options, () -> false);
        Path previewDir = root.resolve("preview");
        var preview = new MultiSourceReconciliationPreviewRendererV2().render(
                previewDir, 2, 1, 255, reconcile, () -> false);
        assertEquals(3, preview.layers().size());
        new MultiSourceReconciliationPreviewIndexCodecV2().readAndVerify(
                previewDir.resolve(MultiSourceReconciliationPreviewIndexCodecV2.INDEX_FILE_NAME),
                previewDir,
                () -> false);
    }

    @Test
    void determinismAcrossLocaleTimezoneAndThreads() throws Exception {
        byte[] image = samples(0, 1, 0, 255);
        byte[] prompt = samples(1, 1, 255, 255);
        var options = options(
                layer("image.a", ImageFidelitySourceKindV2.IMAGE_DRAFT, 255, image),
                layer("prompt.a", ImageFidelitySourceKindV2.PROMPT_SOFT, 255, prompt));
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            String expected = service.reconcile(2, 2, options, () -> false).semanticChecksum();
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(expected, service.reconcile(2, 2, options, () -> false).semanticChecksum());
            try (var pool = Executors.newFixedThreadPool(4)) {
                assertEquals(expected, pool.submit(() -> service.reconcile(2, 2, options, () -> false)
                        .semanticChecksum()).get());
            }
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void cancelCleansUpBeforeAtomicCommit(@TempDir Path root) {
        AtomicInteger checks = new AtomicInteger();
        Path target = root.resolve("cancelled");
        byte[] image = samples(0, 1);
        byte[] prompt = samples(1, 1);
        var options = options(
                layer("image.a", ImageFidelitySourceKindV2.IMAGE_DRAFT, 255, image),
                layer("prompt.a", ImageFidelitySourceKindV2.PROMPT_SOFT, 255, prompt));
        assertThrows(CancellationException.class, () -> service.reconcileAndPublish(
                target, 2, 1, options, () -> checks.incrementAndGet() > 2));
        assertFalse(Files.exists(target));
    }

    @Test
    void rejectsTampering(@TempDir Path root) throws Exception {
        byte[] image = samples(0, 1);
        byte[] prompt = samples(0, 1);
        var options = options(
                layer("image.a", ImageFidelitySourceKindV2.IMAGE_DRAFT, 255, image),
                layer("prompt.a", ImageFidelitySourceKindV2.PROMPT_SOFT, 255, prompt));
        Path target = root.resolve("artifact");
        service.reconcileAndPublish(target, 2, 1, options, () -> false);
        Files.writeString(target.resolve("extra.bin"), "nope");
        assertThrows(Exception.class, () -> codec.readAndVerify(
                target.resolve(MultiSourceReconciliationArtifactCodecV2.INDEX_FILE_NAME),
                target,
                () -> false));
    }

    private static MultiSourceReconciliationOptionsV2 options(MultiSourceProposalLayerV2... layers) {
        return new MultiSourceReconciliationOptionsV2(
                ImageFidelityReconcileRoleV2.LAND_WATER_MASK,
                MultiSourceReconciliationOptionsV2.DEFAULT_NODATA,
                List.of(layers));
    }

    private static MultiSourceProposalLayerV2 layer(
            String id,
            ImageFidelitySourceKindV2 kind,
            int noData,
            byte[] samples
    ) {
        return new MultiSourceProposalLayerV2(id, kind, kind.defaultStrength(), noData, samples);
    }

    private static byte[] samples(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
