package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.core.v2.export.ProductionDispatchRegistryV2;
import com.github.nankotsu029.landformcraft.model.v2.DiagnosticIssueV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticGateContractV2Test {

    @Test
    void builtInLocksTheClassificationTableAndProductionKinds() {
        DiagnosticGateContractV2 contract = DiagnosticGateContractV2.builtIn();

        // Golden: every blueprint diagnostic rule is currently NON_GATING (export gates on none).
        Map<String, DiagnosticGateContractV2.GateClass> expected = new LinkedHashMap<>();
        expected.put("v2.missing-preview-capability", DiagnosticGateContractV2.GateClass.NON_GATING);
        expected.put("v2.missing-validator-capability", DiagnosticGateContractV2.GateClass.NON_GATING);
        expected.put("v2.unsupported-capability", DiagnosticGateContractV2.GateClass.NON_GATING);
        expected.put("v2.unsupported-constraint-map", DiagnosticGateContractV2.GateClass.NON_GATING);
        expected.put("v2.unsupported-structure-capability", DiagnosticGateContractV2.GateClass.NON_GATING);
        assertEquals(expected, contract.ruleClasses());
        assertTrue(contract.ruleClasses().values().stream()
                .allMatch(gateClass -> gateClass == DiagnosticGateContractV2.GateClass.NON_GATING));

        // Golden: exactly the four surface-2.5D coastal kinds are production-connected today.
        Set<TerrainIntentV2.FeatureKind> expectedKinds = new TreeSet<>(java.util.Comparator.comparing(Enum::name));
        expectedKinds.add(TerrainIntentV2.FeatureKind.SANDY_BEACH);
        expectedKinds.add(TerrainIntentV2.FeatureKind.HARBOR_BASIN);
        expectedKinds.add(TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR);
        expectedKinds.add(TerrainIntentV2.FeatureKind.ROCKY_CAPE);
        assertEquals(expectedKinds, contract.productionConnectedKinds());
    }

    @Test
    void productionKindsMatchTheProductionDispatchSpine() {
        // Anti-drift: the contract's production-connected set is exactly the dispatch spine's routed
        // kinds, so both derive from the same current-feature-state authority.
        Set<TerrainIntentV2.FeatureKind> dispatchKinds = ProductionDispatchRegistryV2.builtIn().routes().stream()
                .map(ProductionDispatchRegistryV2.Route::featureKind)
                .collect(Collectors.toCollection(() -> new TreeSet<>(java.util.Comparator.comparing(Enum::name))));
        assertEquals(dispatchKinds, DiagnosticGateContractV2.builtIn().productionConnectedKinds());
    }

    @Test
    void builtInContractIsDeterministic() {
        DiagnosticGateContractV2 first = DiagnosticGateContractV2.builtIn();
        DiagnosticGateContractV2 second = DiagnosticGateContractV2.builtIn();
        assertEquals(first.contractChecksum(), second.contractChecksum());
        assertTrue(first.contractChecksum().matches("[0-9a-f]{64}"));
    }

    @Test
    void checksumIsIndependentOfRuleInsertionOrder() {
        Set<TerrainIntentV2.FeatureKind> kinds = Set.of(
                TerrainIntentV2.FeatureKind.SANDY_BEACH, TerrainIntentV2.FeatureKind.ROCKY_CAPE);
        Map<String, DiagnosticGateContractV2.GateClass> forward = new LinkedHashMap<>();
        forward.put("v2.alpha-rule", DiagnosticGateContractV2.GateClass.GATING);
        forward.put("v2.beta-rule", DiagnosticGateContractV2.GateClass.NON_GATING);
        Map<String, DiagnosticGateContractV2.GateClass> reverse = new LinkedHashMap<>();
        reverse.put("v2.beta-rule", DiagnosticGateContractV2.GateClass.NON_GATING);
        reverse.put("v2.alpha-rule", DiagnosticGateContractV2.GateClass.GATING);
        assertEquals(
                new DiagnosticGateContractV2(forward, kinds).contractChecksum(),
                new DiagnosticGateContractV2(reverse, kinds).contractChecksum());
    }

    @Test
    void classifyRejectsUnregisteredRuleIds() {
        DiagnosticGateContractV2 contract = DiagnosticGateContractV2.builtIn();
        assertThrows(IllegalArgumentException.class, () -> contract.classify("v2.not-a-rule"));
        assertFalse(contract.isRegistered("v2.not-a-rule"));
        assertThrows(IllegalArgumentException.class,
                () -> contract.gatingIssues(List.of(issue("i1", "v2.not-a-rule",
                        DiagnosticIssueV2.Severity.ERROR))));
    }

    @Test
    void gatesSelectsOnlyErrorSeverityGatingRules() {
        DiagnosticGateContractV2 contract = new DiagnosticGateContractV2(
                Map.of(
                        "v2.custom-gating", DiagnosticGateContractV2.GateClass.GATING,
                        "v2.custom-advisory", DiagnosticGateContractV2.GateClass.NON_GATING),
                Set.of());

        DiagnosticIssueV2 gatingError = issue("i1", "v2.custom-gating", DiagnosticIssueV2.Severity.ERROR);
        DiagnosticIssueV2 gatingWarning = issue("i2", "v2.custom-gating", DiagnosticIssueV2.Severity.WARNING);
        DiagnosticIssueV2 advisoryError = issue("i3", "v2.custom-advisory", DiagnosticIssueV2.Severity.ERROR);

        assertTrue(contract.gates(gatingError));
        assertFalse(contract.gates(gatingWarning));
        assertFalse(contract.gates(advisoryError));

        List<DiagnosticIssueV2> input = List.of(gatingError, gatingWarning, advisoryError);
        assertEquals(List.of(gatingError), contract.gatingIssues(input));
        // The ignored-ERROR list holds ERROR issues that do not gate; the WARNING is excluded.
        assertEquals(List.of(advisoryError), contract.ignoredErrorIssues(input));
    }

    @Test
    void gatingIssuesPreservesInputOrder() {
        DiagnosticGateContractV2 contract = new DiagnosticGateContractV2(
                Map.of("v2.custom-gating", DiagnosticGateContractV2.GateClass.GATING), Set.of());
        DiagnosticIssueV2 first = issue("i1", "v2.custom-gating", DiagnosticIssueV2.Severity.ERROR);
        DiagnosticIssueV2 second = issue("i2", "v2.custom-gating", DiagnosticIssueV2.Severity.ERROR);
        assertEquals(List.of(first, second), contract.gatingIssues(List.of(first, second)));
        assertEquals(List.of(second, first), contract.gatingIssues(List.of(second, first)));
    }

    private static DiagnosticIssueV2 issue(String issueId, String ruleId, DiagnosticIssueV2.Severity severity) {
        return new DiagnosticIssueV2(
                issueId, ruleId, 1, severity, TerrainIntentV2.Strength.HARD,
                List.of(new DiagnosticIssueV2.Reference(
                        DiagnosticIssueV2.ReferenceType.FEATURE, "test-feature")),
                List.of(), ruleId, List.of("diagnostic.test"));
    }
}
