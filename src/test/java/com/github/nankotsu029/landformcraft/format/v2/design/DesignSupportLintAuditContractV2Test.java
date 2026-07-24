package com.github.nankotsu029.landformcraft.format.v2.design;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignSupportLintV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignSupportSurfaceV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-19-08: the design audit carries the support lint as an optional, strictly validated block. The
 * lint is advisory by contract — a {@code GATING} finding is rejected by both the record and the
 * schema, because making the design path fail-closed needs separate human approval (Task Index
 * §19.2).
 */
class DesignSupportLintAuditContractV2Test {
    private static final Path WITHOUT_LINT = Path.of("examples/v2/design/design-audit-v2.json");
    private static final Path WITH_LINT =
            Path.of("examples/v2/design/design-audit-with-support-lint-v2.json");

    private final DesignPackageCodecV2 codec = new DesignPackageCodecV2();

    @Test
    void anAuditWithoutTheLintStillReadsAndReportsNoLint() throws IOException {
        assertTrue(codec.readAudit(WITHOUT_LINT).supportLintOrEmpty().isEmpty());
    }

    @Test
    void theLintSurvivesACanonicalWriteAndStrictReadBack(@TempDir Path root) throws IOException {
        var audit = codec.readAudit(WITH_LINT);
        DesignSupportLintV2 lint = audit.supportLintOrEmpty().orElseThrow();
        assertEquals(DesignSupportLintV2.DispatchDryRunV2.NOT_SELECTABLE, lint.dispatchDryRun());
        assertEquals(List.of("PLAIN"), lint.declaredKinds());
        assertEquals(DesignSupportSurfaceV2.CONTRACT_VERSION, lint.surface().contractVersion());

        Path written = root.resolve("audit-v2.json");
        codec.writeAudit(written, audit);
        assertEquals(audit, codec.readAudit(written));
    }

    @Test
    void aGatingFindingIsRejectedByTheSchema(@TempDir Path root) throws IOException {
        Path tampered = root.resolve("audit-v2.json");
        Files.writeString(
                tampered,
                Files.readString(WITH_LINT, StandardCharsets.UTF_8)
                        .replace("\"gateClass\": \"NON_GATING\"", "\"gateClass\": \"GATING\""),
                StandardCharsets.UTF_8);

        assertThrows(RuntimeException.class, () -> codec.readAudit(tampered));
    }

    @Test
    void anUnknownLintFieldIsRejectedRatherThanIgnored(@TempDir Path root) throws IOException {
        Path tampered = root.resolve("audit-v2.json");
        Files.writeString(
                tampered,
                Files.readString(WITH_LINT, StandardCharsets.UTF_8)
                        .replace("\"dispatchDryRun\": \"NOT_SELECTABLE\"",
                                "\"dispatchDryRun\": \"NOT_SELECTABLE\", \"overrideExport\": true"),
                StandardCharsets.UTF_8);

        assertThrows(RuntimeException.class, () -> codec.readAudit(tampered));
    }

    @Test
    void aGatingFindingIsRejectedByTheRecordItself() {
        DesignSupportSurfaceV2 surface = new DesignSupportSurfaceV2(
                DesignSupportSurfaceV2.CONTRACT_VERSION,
                "production-dispatch-registry-v2",
                "0".repeat(64),
                "1".repeat(64),
                List.of("SANDY_BEACH"),
                List.of(),
                List.of(),
                List.of("SANDY_BEACH"),
                List.of("TERRAIN_INTENT_V2_STRUCTURED"));
        DesignSupportLintV2 gating = new DesignSupportLintV2(
                surface,
                DesignSupportLintV2.DispatchDryRunV2.NOT_SELECTABLE,
                List.of(),
                List.of("SANDY_BEACH"),
                List.of(new DesignSupportLintV2.FindingV2(
                        DesignSupportLintV2.RULE_DISPATCH_UNSELECTABLE,
                        DesignSupportLintV2.GateClassV2.GATING,
                        List.of(),
                        "would gate")));

        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> new com.github.nankotsu029.landformcraft.model.v2.design.DesignAuditV2(
                        1,
                        java.util.UUID.fromString("550e8400-e29b-41d4-a716-4466554400a8"),
                        "azure-coast",
                        com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2.FIXTURE,
                        "fixture-v2",
                        "terrain-intent-v2.json",
                        "terrain-intent-v2-structured-guards",
                        "fixture-0123456789abcdef01234567",
                        com.github.nankotsu029.landformcraft.model.ProviderUsage.ZERO,
                        1,
                        "a".repeat(64),
                        "b".repeat(64),
                        java.util.Set.of(com.github.nankotsu029.landformcraft.model.v2.design
                                .DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED),
                        "provider-capability-catalog-v1",
                        java.time.Instant.parse("2026-07-24T00:00:00Z"),
                        java.time.Instant.parse("2026-07-24T00:00:01Z"),
                        gating));
        assertTrue(rejected.getMessage().contains("advisory findings only"), rejected.getMessage());
    }
}
