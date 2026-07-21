# V2-12-06 v1 retirement evidence

Status: **PASS** (2026-07-21)

## Approval and D8 drain gate

The repository owner and final decision maker explicitly approved V2-12-06 and waived only the unpublished-project 30-day waiting condition under ADR 0035 rev.5. The waiver did not change D8, the R1–R8 allowlist, the D2b/D3 preservation boundary, identifier freezes, or test gates.

Before deleting any production v1 implementation, `LegacyRetirementPreflightV2` inventoried every known v1 operational root. Non-empty state was resolved with ADR 0035 D8 option 2: byte-exact copy to a newly-created neutral archive, source re-hash, archive read-back, and checksum-indexed `inventory.json`. Source state was not modified. Both known runtime roots passed with zero unresolved entries. The machine-readable summary is [v2-12-06-drain-evidence.json](v2-12-06-drain-evidence.json).

| Runtime label | Entries | Bytes | Inventory SHA-256 | Result |
|---|---:|---:|---|---|
| Paper / WorldEdit | 214 | 244620 | `cbc68cb5e4fca57107664646b0e729a71830241eb02f14da9b3d2c80af214786` | PASS |
| Paper / FAWE | 22 | 20560 | `f1d7474b465d10c31f8d1bf3b55a125705ba797db86cd16af6e0bacec0db3157` | PASS |

The archives remain recoverable operator data and are intentionally excluded from Git. The repository records only labels, counts, byte totals, and report digests so operator paths are not disclosed.

## D2c and deletion order

The version-neutral recursive deletion routine was extracted to `format.FileTreeOperations` before R2 deletion. The static dependency check and compile checkpoints preserved ADR 0035 D7 order:

```text
D8 archive and read-back
→ D2c extraction
→ R6 → R4 → R5 → R3 → R2 → R1+R8 → R7
```

## R1–R8 result

| ID | Result |
|---|---|
| R1 | Removed generator `3.0.0-phase6` executable implementation and its direct golden harness; retained immutable golden bytes and migration/equivalence tests. |
| R2 | Removed Release 1 and design-v1 publishers; retained read-only `ReleaseVerifier`, `ReleaseVerification`, `DesignArtifacts`, `DesignArtifactVerifier`, and `DesignVerification`. |
| R3 | Removed v1 placement, Undo, recovery, cleanup repositories/services, and the Paper gateway after D8 PASS. |
| R4 | Removed v1 design/generation orchestration and repositories; v2 request/job/export services remain. |
| R5 | Removed v1 provider SPI/adapters; v2 provider implementations remain. |
| R6 | Removed v1 Paper/CLI dispatch, `/lfc r2`, `/lfc v1`, `--v1`, and `landformcraft.r2.*`. The explicit `v2` alias remains. |
| R7 | Removed the ten v1-only schemas and matching fixtures from active inventories; byte-preserving contracts and fixtures are packaged below `legacy/v1/`. |
| R8 | Removed `V1TerrainQueryAdapter` and its test. |

No deletion outside ADR 0035 D2a R1–R8 was required.

## Command replacement inventory

The removal is one-to-one at the user operation level. `v2` below means both the default form and the permanent explicit `v2` alias.

| Retired v1 operation | Maintained v2/default operation |
|---|---|
| request create / bounds / selection / prompt / list / validate / info | `request` authoring, validate, and info |
| design import / fixture / OpenAI / Anthropic | `design` |
| generate | `generate`, or asynchronous `job` plus `candidate` |
| export | `export plan` then `export create`, or direct offline `export` |
| job status / cancel / list | `job status` / `job cancel` / `job list` |
| candidate list / info | `candidate list` / `candidate info` |
| Release 1 apply plan / execute / status | Release 2 `place plan` / `place confirm` / `place execute` / `status` |
| v1 Undo | operation-bound `undo plan` / `undo execute` |
| v1 recovery | `recover diagnose` / `recover plan` / `recover execute` |
| v1 cleanup | `retention plan` / `retention execute` / `retention status` |
| v1 journal verification | v2 `journal-verify` |

Maintained version-neutral or compatibility operations were not deleted: `asset`, `doctor`, `job`, `recovery`, `version`, `help`, `ops`, `selection`, and `migrate`. The migration service owns the read-only v1 intent/design/Release verifiers; no v1 production command dispatch remains.

## Compatibility evidence

- `LegacyV1GoldenArchiveTest` verifies immutable v1 fixture checksums and the v2 migration/equivalence path without running the deleted generator.
- `LegacyMigrationPackagedJarV2Test` migrates packaged v1 intent and Release 1 fixtures with active v1 schemas and v1 orchestration absent.
- `V1RetirementBoundaryTest` fixes the R1–R8 absence and D2b/D3 presence boundary.
- Active schema/example inventories contain v2 contracts only; the packaged legacy resource is not publicly enumerated.
- Release 2 capability identifiers, contract IDs, generator versions, semantic checksums, and catalog support state were not renamed or promoted.

V2-12-07 has not been started.
