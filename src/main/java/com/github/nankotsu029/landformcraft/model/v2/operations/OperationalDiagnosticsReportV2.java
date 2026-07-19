package com.github.nankotsu029.landformcraft.model.v2.operations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Redacted admin diagnostics report keyed by correlation ID (V2-6-13). Findings are bounded and
 * must not embed absolute paths, secrets, or raw payloads.
 */
public record OperationalDiagnosticsReportV2(
        int schemaVersion,
        String contractVersion,
        UUID correlationId,
        String operation,
        String stage,
        String suggestedAction,
        UUID placementId,
        String journalState,
        boolean openaiKeyPresent,
        boolean anthropicKeyPresent,
        List<String> findings,
        String capturedAt
) {
    public static final int SCHEMA_VERSION = 1;
    public static final String CONTRACT_VERSION = "operational-diagnostics-report-v1";
    public static final int MAXIMUM_FINDINGS = 64;
    public static final int MAXIMUM_FINDING_CHARS = 160;

    private static final Pattern INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public OperationalDiagnosticsReportV2 {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("operational diagnostics schemaVersion must be 1");
        }
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown operational diagnostics contract");
        }
        Objects.requireNonNull(correlationId, "correlationId");
        operation = requireSlug(operation, "operation");
        stage = requireSlug(stage, "stage");
        suggestedAction = requireNonBlank(suggestedAction, "suggestedAction", 256);
        journalState = journalState == null || journalState.isBlank()
                ? ""
                : requireSlug(journalState, "journalState");
        findings = normalizeFindings(findings);
        capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        if (!INSTANT.matcher(capturedAt).matches()) {
            throw new IllegalArgumentException("capturedAt must be a UTC ISO-8601 instant");
        }
    }

    private static List<String> normalizeFindings(List<String> findings) {
        Objects.requireNonNull(findings, "findings");
        if (findings.size() > MAXIMUM_FINDINGS) {
            throw new IllegalArgumentException("diagnostics findings exceed bounded cardinality");
        }
        List<String> copy = new ArrayList<>(findings.size());
        for (String finding : findings) {
            copy.add(requireFinding(finding));
        }
        return List.copyOf(copy);
    }

    private static String requireFinding(String finding) {
        finding = requireNonBlank(finding, "finding", MAXIMUM_FINDING_CHARS);
        String lower = finding.toLowerCase(Locale.ROOT);
        if (finding.contains("/") || finding.contains("\\") || finding.contains("://")
                || lower.contains("authorization") || lower.contains("api_key")
                || lower.contains("api-key") || lower.contains("bearer ")) {
            throw new IllegalArgumentException("diagnostics finding must not contain paths or secrets");
        }
        return finding;
    }

    private static String requireSlug(String value, String field) {
        value = Objects.requireNonNull(value, field).trim().toLowerCase(Locale.ROOT);
        if (!SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a slug");
        }
        return value;
    }

    private static String requireNonBlank(String value, String field, int max) {
        value = Objects.requireNonNull(value, field).trim();
        if (value.isEmpty() || value.length() > max) {
            throw new IllegalArgumentException(field + " must be 1.." + max + " chars");
        }
        return value;
    }
}
