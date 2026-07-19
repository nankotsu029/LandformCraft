# V2-6 Placement Release candidate audit and Phase gate

> Status: PASS — `V2-6-19` completed (2026-07-19). The V2-6 parent gate is closed. This gate does **not** promote any Paper capability: `paper_apply`, `post_apply_validation`, `snapshot`, `rollback`, and `restart_recovery` stay `UNSUPPORTED`／`NOT_APPLICABLE` in the Feature Support Catalog, the placement hard limit stays at the smoke-measured 64×64, and the 0.9.0-beta.1 release candidate remains **unapproved for public beta** while the separate Beta hardening blockers stay open.

## Decision

`V2-6-01` through `V2-6-15`, `V2-6-18`, `V2-6-20`, and `V2-6-21` satisfy the V2-6 parent gate; `V2-6-16`／`V2-6-17` (500×500／1000×1000 real-world measurement) are CANCELLED and are treated as disabled, not as blockers. The Release 2 placement path — placement plan／journal contract, mutation／effect envelope, region／disk reservation with bound confirmation, snapshot-all, fluid／gravity／neighbor containment preflight, canonical apply transaction, bounded settle／full exact verify, reverse rollback, operation-bound Undo, conservative startup Recovery, provider／manual／image v2 design path, cross-capability hardening, operational metrics／diagnostics／retention, verified canonical block source, and the explicit `/lfc r2` Paper application lifecycle — is complete as an **evaluation path** with WorldEdit 7.3.19 (`V2-6-14`) and FAWE 2.15.2 standalone (`V2-6-15`) real-machine smoke evidence at 64×64 scale.

Release decision for the candidate build:

- **V2-6 Phase gate: PASS.** All prerequisite tasks carry executable evidence, the full clean suite and build pass, no unresolved critical or high risk remains inside V2-6 scope.
- **Capability promotion: deferred to a follow-up task (`V2-11-01`).** Promoting `paper_apply` and post-apply columns for the smoke-measured dimension, and connecting Track D（V2-9）／Track E（V2-10）foundation features to Paper `SUPPORTED`, are explicitly outside this audit ("production修正は行わず"), and require their own contract／catalog／docs change with this gate as prerequisite.
- **Public beta: NOT approved.** The Beta hardening checklist keeps its open items (CONSOLE confirmation-token log persistence, provider durable response cache, cross-process CLI job cancel IPC, custom-asset→TerrainIntent integration). These belong to the separate Beta track and are unchanged by this gate.
- **v1 remains the default user path.** Explicit version dispatch stays: v1 commands and Release 1 are unchanged, Release 2 is reachable only through the dedicated `/lfc r2` root and the capability-explicit v2 design path.

## Integrated portfolio

- Executable gate: `PlacementPhaseGateV2Test` (integration), on top of the per-task portfolios listed in the evidence matrix.
- Catalog: the sealed built-in Feature Support Catalog passes the consistency verifier; every entry keeps the five Paper columns un-promoted; the sealed example checksum matches the built-in data; unmeasured 65／500／1000 dimensions are rejected by `rejectsUnmeasuredPaperPromotion`.
- Release: the Release 2 capability list stays `[environment-fields, hydrology-plan, sparse-volume, surface-2_5d]` and the `V2-6-12` dependency matrix keeps exactly five valid prefixes.
- Example portfolio: the twelve sealed placement examples plus the sealed catalog example read identically across forward／reverse read order, one／four threads, Turkish locale, and Chatham timezone.
- Lifecycle determinism: the full plan→confirm→snapshot-all→containment→apply→settle→full verify→Undo lifecycle over the shared release fixture produces the identical applied world block-state map across repeat runs with different executor sizes, Turkish locale, and Chatham timezone; zero mutation occurs before snapshot-all; both runs end terminal `UNDONE` with the world restored to the snapshot baseline.

## Task evidence matrix

| Task | Evidence | Result |
|---|---|---|
| V2-6-01 placement plan／journal contract | `PlacementPlanCompilerV2Test`, `PlacementPlanV2Test` | PASS |
| V2-6-02 mutation／effect envelope | `PlacementEnvelopeCompilerV2Test` | PASS |
| V2-6-03 reservation／bound confirmation | `PlacementReservationConfirmCompilerV2Test` | PASS |
| V2-6-04 snapshot-all | `PlacementSnapshotAllCompilerV2Test` | PASS |
| V2-6-05 containment preflight | `PlacementContainmentPreflightV2Test` | PASS |
| V2-6-06 canonical apply transaction | apply transaction／prerequisite verifier tests, `PaperPlacementWorldGatewayV2Test` | PASS |
| V2-6-07 bounded settle／full verify | `PlacementSettleVerifyServiceV2` portfolio | PASS |
| V2-6-08 rollback | `PlacementRollbackServiceV2Test` | PASS |
| V2-6-09 Undo | `PlacementUndoServiceV2Test` | PASS |
| V2-6-10 startup Recovery | `PlacementRecoveryServiceV2Test` | PASS |
| V2-6-11 provider／manual／image integration | ai.spi.v2 contract tests (ADR 0029) | PASS |
| V2-6-12 cross-capability hardening | `ReleaseCrossCapabilityHardeningV2Test` (ADR 0030) | PASS |
| V2-6-13 operational metrics／retention | `core.v2.operations` portfolio (ADR 0031) | PASS |
| V2-6-14 WorldEdit 7.3.19 smoke | [evidence](v2-6-14-worldedit-smoke-evidence.md), `APPLIED`→`UNDONE` at 64×64 | PASS |
| V2-6-15 FAWE 2.15.2 standalone smoke | [evidence](v2-6-15-fawe-smoke-evidence.md), same canonical expected checksum as V2-6-14 | PASS |
| V2-6-16 500×500 measurement | CANCELLED — dimension stays unmeasured and un-catalogued | DISABLED |
| V2-6-17 1000×1000 measurement | CANCELLED — dimension stays unmeasured and un-catalogued | DISABLED |
| V2-6-18 Feature Support Catalog | `FeatureSupportCatalogV2Test`, sealed example, false-promotion corpus | PASS |
| V2-6-19 RC audit | `PlacementPhaseGateV2Test`, full clean suite, this audit | PASS |
| V2-6-20 verified canonical block source | `VerifiedReleaseCanonicalBlockSourceV2` portfolio (ADR 0033) | PASS |
| V2-6-21 Paper application lifecycle | `Release2PlacementApplicationServiceV2Test` (ADR 0034) | PASS |

## RC-audit findings

The audit found one defect class and fixed it as a minimal, checksum-invariant hardening (the same small-defect allowance `V2-6-14` used for smoke defects):

- **Locale-dependent case conversion (fixed).** Six call sites used default-locale `toLowerCase()`: the four preview index codecs (`CoastalPreviewIndexCodecV2`, `HydrologyPreviewIndexCodecV2`, `EnvironmentPreviewIndexCodecV2`, `VolumePreviewIndexCodecV2` — enum-derived `constraint-source:` IDs became invalid under `tr-TR`), the settle-verify continuity metric detail string (`PlacementSettleVerifyServiceV2`), and the provider secret-header check (`AbstractHttpTerrainDesignProviderV2` — case-insensitive header matching broke under `tr-TR`). All six now pass `Locale.ROOT`. Output is byte-identical under every locale whose lowercase mapping of ASCII matches ROOT (all previously passing runs), so no sealed checksum changes; the new gate test exercises the full lifecycle under `tr-TR`／Chatham to lock the fix.

No other unresolved critical or high risk was found inside V2-6 scope. Open items of the Beta hardening track (CONSOLE token logging, provider durable cache, cross-process cancel IPC, custom-asset intent integration) are pre-existing, tracked in [docs/limitations.md](../../limitations.md) and the [Beta release checklist](../../beta-release-checklist.md), and are not V2-6 defects.

## Determinism and resource gate

Placement executes in canonical tile-index order with X-fastest→Z→Y coverage and explicit `SOLID → AIR_CARVE → FLUID` phase order; restore runs in reverse canonical order. The gate test pins repeat／executor-size／locale／timezone invariance of the applied block-state map and of the sealed example portfolio. Snapshot-all writes are hard-capped by the disk lease; the application service enforces a bounded working budget (64 MiB in the gate fixture); admission happens before allocation (`ScaleAdmissionV2`, envelope disk／volume admission, snapshot byte cap, verify slice／queue budgets). No dense voxel world array is introduced; the smoke evidence records measured snapshot bytes within the declared budget.

## Release, security, and compatibility gate

All five valid capability prefixes verify strictly for directory and ZIP; missing／extra／unknown／duplicate／version／checksum／path／ZIP-bomb corruption stays rejected (`ReleaseCrossCapabilityHardeningV2Test` plus per-capability tamper corpora). The confirmation tokens stay actor-bound, one-time, and expiring; secrets and raw payloads stay redacted in metrics／diagnostics／audit; the verified canonical block source re-checksums every cursor open.

The complete clean suite retains TerrainIntent／WorldBlueprint v1 strict Schema behavior, generator `3.0.0-phase6` goldens, Release format 1 verification, v1 placement／rollback／Undo／Recovery, and the V2-2〜V2-5, V2-9, V2-10 portfolios. Frozen example checksums are unchanged in place.

## Verification commands

```text
./gradlew test --tests '*PlacementPhaseGateV2Test'
./gradlew clean test build
git diff --check
```

## Remaining boundaries and follow-up

- Paper capability promotion (64×64 `paper_apply` and post-apply columns, Track D／E foundation connection) is registered as `V2-11-01` and starts only from this gate plus `V2-6-06`〜`V2-6-10` evidence.
- 500×500／1000×1000 Paper apply stays unmeasured and `UNSUPPORTED`; re-measurement needs a new Task ID (never reviving `V2-6-16`／`17`).
- Track B（V2-7 image fidelity）continues at `V2-7-02`; Track C（V2-8 LARGE）continues at `V2-8-02`. LARGE generation stays unavailable.
- The Beta hardening checklist keeps its open items; the release candidate stays unapproved for public beta until that track closes them.
- Full v1→v2 migration is **not** performed: v1 stays the default path with explicit version dispatch. The blocking conditions are recorded in [migration-plan.md](../migration-plan.md).
