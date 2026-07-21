# V2-12 Migration integration audit and Phase gate

> Status: PASS — `V2-12-07` completed (2026-07-21). This gate audits the **v2 production-only configuration** defined by [ADR 0035](../../adr/0035-v1-retirement-governance.md) Consequences: v2 is the only production writer/generator/placement/command path, and v1 is isolated to the D2b migration-only read boundary plus the D3 maintained assets. This is deliberately **not** a "zero v1 code" configuration. No capability or dimension is promoted; the Release 2 capability set, `PlacementDimensionLimitV2`, and all sealed contracts are regression-unchanged.

## Decision

`V2-12-01`〜`V2-12-06` and `V2-12-08`〜`V2-12-10` satisfy the V2-12 parent gate. V2 is now the sole production path and v1 is isolated to its ADR 0035 D2b/D3 boundary, verified end to end with no unresolved critical/high issue. The V2-12 parent Phase is **closed**.

One pre-existing, LOW-severity, out-of-scope test-hygiene defect was found and registered as follow-up `V2-12-11` (see [Findings](#findings)). It fails no test, is unrelated to the v2-production-only configuration, and by the gate criterion (no unresolved critical/high, v2-single-operation established) does not block closure.

The gate promotes no capability. The Release 2 capability list stays `[environment-fields, hydrology-plan, sparse-volume, surface-2_5d]`, the four `surface-2_5d` Paper columns stay the only `paper_apply: SUPPORTED` entries, and the published `PlacementDimensionLimitV2` stays `measured()` (1000×1000 on FAWE 2.15.2 evidence; 64×64 on WorldEdit 7.3.19 single runtime). Beta hardening blockers are **not** waived (see [Release decision](#release-decision)).

## v2 production-only configuration (audited invariant)

`MigrationPhaseGateV2Test.v2ProductionOnlyConfigurationIsEstablishedAndV1IsIsolated` pins the ADR 0035 target configuration as one executable claim:

- **D2a — production v1 writers removed.** Absent: `generator/{TerrainGenerator, LogicalLayoutGenerator, StructurePlanner}`, `generator/v2/V1TerrainQueryAdapter`, `format/{ReleasePublisher, ReleaseArtifacts, DesignArtifactPublisher}`, `core/{PlacementApplicationService, SnapshotCleanupService, GenerationApplicationService, TerrainDesignApplicationService}`, `ai/spi/TerrainDesignProvider`.
- **D2b/D3 — legacy read boundary and maintained assets remain.** Present: `format/{ReleaseVerifier, ReleaseVerification, DesignArtifactVerifier, DesignArtifacts}` (migration-only readers/verifiers), `core/CustomAssetService` (K3), the K1 immutable v1 golden archive, and the packaged Release 1 migration fixture.
- **R7 — active Schema inventory carries no v1 contract.** All ten retired v1 schemas (`terrain-intent`, `world-blueprint`, `generation-request`, `generation-job`, `design-audit`, `export-manifest`, `placement-journal`, `placement-safety-state`, `snapshot-cleanup-plan`, `structure-placements`) are absent from `schemas/` and present as immutable packaged legacy contract resources under `src/main/resources/legacy/v1/contracts/`. This is the gate's "active inventory does not show R7-moved legacy resources" requirement.
- **R6/D4 — command surface is v2-only.** `plugin.yml` has no `landformcraft.r2.` alias, `cli/LandformCraftCli.java` has no `--v1` switch, `V2CommandVerbV2.ROOT` is `v2`, and routing an `r2` root is rejected.

The static per-type retirement boundary is additionally pinned by `compatibility/V1RetirementBoundaryTest`; the K1 archive equivalence path by `compatibility/LegacyV1GoldenArchiveTest`; the packaged-JAR migration under a hidden active-schema classloader by `core/v2/migration/LegacyMigrationPackagedJarV2Test`; and the packaged inventory / active-inventory exclusion by `paper/PluginArtifactTest`.

## Command / permission / security audit

`MigrationPhaseGateV2Test.everyProductionVerbRoutesThroughTheV2PathWithACanonicalPermission` routes the eight operator-facing production verbs (`request`, `design`, `generate`, `export`, `preview`, `place`, `status`, `undo`) through the shared `V2CommandRouterV2` and asserts each resolves to its `V2CommandVerbV2` with a `landformcraft.v2.*` permission node. No `r2`/`v1` alias reaches them. The CLI default-surface routing (bare verb ≡ `v2 <verb>`, removed `--v1`/`verify` fail closed) is covered by `cli/LandformCraftCliV2Test`, re-run under the full suite.

## Representative production E2E

`MigrationPhaseGateV2Test.productionV2ExportPathProducesAPlacementEligibleReleaseEndToEnd` runs the offline production path both surfaces call (`V2WorkflowServiceV2`, V2-12-02): request inspect → `generate`/`export` (strict Release directory) → strict preview verify → placement eligibility (`surface-2_5d`, `eligible == true`, matching manifest checksum).

The full request→export→64×64 placement→Undo world lifecycle through the router (`core/v2/command/V2CommandPathE2EV2Test`) and the placement/settle/verify/rollback/Undo/**Recovery** lifecycle (`format/v2/release/Release2PlacementApplicationServiceV2Test`, `core/v2/recovery/PlacementRecoveryServiceV2Test`, `integration/v2/PlacementPhaseGateV2Test`) are re-run under this gate's full clean suite, completing the request→design→generate→export→placement→Undo→Recovery chain.

## No capability false-promotion

`MigrationPhaseGateV2Test.releaseCapabilitySetAndCatalogHaveNoFalsePromotionFromV2Twelve` confirms V2-12 (a command/migration-only phase) promoted nothing:

- exactly four `paper_apply: SUPPORTED` entries (the `surface-2_5d` features);
- `catalog.placementDimensionLimit() == PlacementDimensionLimitV2.measured()`, admitting 1000×1000 and rejecting 1001×1000;
- the sealed catalog example (`examples/v2/catalog/feature-support-catalog-v2.json`) verifies its checksum and matches the built-in sealed catalog;
- the Release 2 capability list is `[environment-fields, hydrology-plan, sparse-volume, surface-2_5d]` and `ReleaseCapabilityDependencyMatrixV2` keeps its five valid prefixes.

The false-promotion corpus (`FeatureSupportCatalogV2Test`, `FeatureSupportCatalogConsistencyVerifierV2`, `PlacementPhaseGateV2Test`) is re-run under the full suite.

## Legacy migration regression

`MigrationPhaseGateV2Test.legacyV1MigrationRegressionIsDeterministicThroughPackagedReaders` migrates the K1 v1 golden (`legacy/v1/golden/rocky-coast/terrain-intent.json`) through the D2b packaged legacy readers twice, to two output roots: both return `PUBLISHED` with a bundle, and the two `migration-report-v2.json` files are byte-identical (the report is path-independent and deterministic — job id from source digest, timestamps fixed). The migration-tool quality corpus (`LegacyMigrationApplicationServiceV2Test`, `LegacyMigrationPackagedJarV2Test`, `LegacyRetirementPreflightV2Test`) is re-run under the full suite.

## Determinism portfolio

`MigrationPhaseGateV2Test.gatePortfolioIsStableAcrossOrderThreadsLocaleAndTimezone` reads a representative v2 sealed portfolio (generation-request-v2, terrain-intent-v2, export-job-v2, migration-report-v2, feature-support-catalog) and re-verifies identical canonical forms across reversed read order, four worker threads, Turkish locale, and Chatham timezone.

Note: v2 production determinism ("same canonical Blueprint, seed, generator version → same checksum") is unaffected by the [finding](#findings) below — placement confirmation tokens are security nonces, not canonical-checksum inputs.

## Integrated portfolio

- Executable gate: `integration/v2/MigrationPhaseGateV2Test` (6 methods).
- Full clean suite: `./gradlew clean test build` — **991 tests, 6 skipped, 0 failures, 0 errors across 175 test classes; BUILD SUCCESSFUL** (985 pre-gate + 6 new gate methods).
- v1 regression: `V1CompatibilityGoldenTest` is retired as an executable v1-generator harness (ADR 0035 K1); the frozen v1 output is preserved as the immutable golden archive and exercised through the migration equivalence path (`LegacyV1GoldenArchiveTest`). Release 1 read/verify and design-package read/verify remain via the D2b legacy verifiers.
- Capability regression: the four Release 2 capability verifiers and the placement lifecycle portfolio are unchanged in the full suite.

## Release, security, and compatibility gate

V2-12 changed no `format.v2.release` semantics, no Release 2 capability, no Schema `$id`/capability name/artifact type/contract id/generator version/error-code/metric-label (D4 frozen identifiers), and no placement contract. The command surface (`landformcraft.v2.*`) and Java identifiers are the only renames, both ADR 0035 D4-approved. No input path/ZIP/image trust boundary changed. The `DesignFailureCodeV2` 4-value surface (vs v1 `ProviderFailureCode` 9-value) is the ADR 0035 D6-approved failure-code granularity reduction, with the original code preserved in the exception cause chain.

Known accepted debt (ADR 0035 Consequences): the K3 custom asset catalog remains a v1-derived implementation with no v2 equivalent; migrating it requires a new, ADR-amended task.

## Findings

**`V2-12-11` (follow-up, LOW, pre-existing, out-of-scope) — placement round-trip tests overwrite tracked example files with non-deterministic content.**

- `core/v2/placement/undo/PlacementUndoServiceV2Test.undoPlanCodecRoundTrip` (line 242) unconditionally writes `examples/v2/placement/placement-undo-plan-v2.json`, and `core/v2/recovery/PlacementRecoveryServiceV2Test` (line 565) writes `examples/v2/placement/placement-recovery-plan-v2.json`, as a side effect with no assertion.
- The written `confirmationHash` is `sha256(binding)` over a `plaintextToken = UUID.randomUUID()` (`PlacementConfirmationBinderV2.newPlaintextToken`) — a one-time security nonce that is correctly random. Persisting it into a tracked example makes the example non-reproducible, so every `./gradlew test` run leaves those two files modified in the working tree.
- Origin: V2-6-09/V2-6-10, unrelated to V2-12. No test fails (portfolio reads compare same-run reads of the on-disk file), and the v2-production-only configuration is unaffected.
- Fix (deferred to `V2-12-11`): have both round-trip tests write only to their `@TempDir` and stop writing the tracked `examples/` paths (or seal these examples with a fixed test nonce). Do not commit the churned files.

This finding is LOW severity and meets neither gate-blocking condition (not critical/high; does not prevent v2-single-operation), so it is tracked as a follow-up rather than blocking the gate.

**Resolution (2026-07-21):** `V2-12-11` fixed both tests to seal the examples deterministically via the existing `request.plaintextToken()` seam (round-trip tests inject a fixed placeholder nonce; every other scenario keeps the realistic random token). The two examples were regenerated with the fixed nonce and verified byte-stable across consecutive runs; production `PlacementConfirmationBinderV2` is unchanged. `./gradlew clean test build` passes with no tracked-file churn.

## Release decision

**Not released — Beta blockers remain.** V2-12 formalizes v2 as the sole production path and closes the migration Phase gate, but it does not close the outstanding Beta hardening items in [beta-release-checklist.md](../../beta-release-checklist.md) (real-machine 500/1000 Paper apply/Undo, crash-injection Recovery classes, filesystem concurrency E2E, backup/restore rehearsal, final artifact SHA-256 publication). Per the V2-12-07 non-scope, incomplete Beta hardening is not waived by this gate.

## Verification commands

```text
./gradlew test --tests '*MigrationPhaseGateV2Test'
./gradlew clean test build
git diff --check
git status --short
```

## Remaining boundaries

- `V2-12-11` test-hygiene fix is resolved (2026-07-21; see [Findings](#findings)).
- Beta hardening blockers remain open; the product is not release-approved.
- K3 custom asset catalog stays a v1-derived implementation (accepted debt, ADR 0035 D3-K3).
- v1 artifacts remain readable/verifiable/migratable through the D2b legacy boundary only; no v1 production writer exists.
