# V2-13-06 Apply slice calibration and batching optimization — audit

- Status: **COMPLETE**（2026-07-22、実機evidence人間確認済み）
- Task: `V2-13-06`
- Input analysis: [apply slice analysis](v2-13-06-apply-slice-analysis.md)
- Evidence: [measured evidence](v2-13-06-apply-slice-calibration-evidence.md)
- Runbook: [calibration runbook](../../smoke/v2-13-06-apply-slice-calibration-runbook.md)
- Runtime: Paper 1.21.11 build 132 + FAWE 2.15.2 standalone（WorldEdit plugin absent）

## Decision

Closed candidates `32 / 128 / 256 / 512 / 1024` were measured twice on the same isolated
1024×1024 host. `1024 mutations/slice` is the largest measured candidate and passed every automated
selection gate: APPLY -93.38%, lifecycle -74.23%, maximum observed tick 16.6 ms, maximum observed
window-average MSPT 5.4 ms, peak RSS/used heap no worse than the 32 baseline, maximum GC pause
43.473 ms, no watchdog event, and both passes reached `APPLIED` after full exact verify then
`UNDONE`. The selected value was then re-measured twice at both 1000² and 1024² on one production
default build; all four lifecycles passed.

The production default is therefore changed from 32 to **1024 mutations/slice**. This does not
promote 1024 in `PlacementDimensionLimitV2`, the feature catalog, or any capability column.

## `maximumWorkingBytes` meaning audit

`maximumWorkingBytes` is the admission contract for one canonical apply plan's largest materialized
slice, not a shared heap pool and not an artifact-format field. The conservative accounting remains
640 bytes per mutation:

- up to 512 bytes for the bounded canonical block-state text;
- coordinate/record/reference storage, list backing storage and defensive copy;
- stream validation state and object/alignment headroom.

The estimate is deliberately conservative and is not a Java object-layout ABI. At 1024 mutations,
one plan admits `1024 × 640 = 655,360` bytes. The apply service submits exactly one slice, awaits its
receipt, validates it, and only then continues; it does not pipeline slices. The previous slice
becomes unreachable at the receipt continuation before the next slice is submitted. The bounded
application executor admits at most 18 transactions (`2 workers + 16 queued`), so the worst accepted
aggregate slice estimate is `18 × 655,360 = 11,796,480` bytes (11.25 MiB), within the existing
bounded executor envelope. Snapshot, source decode, Undo and Release artifact budgets remain
separate pre-admission contracts.

`PlacementPlanCompilerV2` and apply admission now use the same `655,360`-byte ceiling. A plan above
the ceiling fails before world mutation. Because this is a per-transaction plan contract rather than
a shared reservation, increasing it does not weaken concurrent admission or borrow from snapshot,
disk, or artifact budgets.

## Safety and determinism

- The lifecycle order remains validate → preview/export → effect-envelope estimate → reservation →
  bound confirmation → snapshot-all → apply → settle → full effect-envelope verify.
- Slice values are a closed set. Production uses 1024; measurement-only overrides require the
  existing isolated measurement profile and accept only the five measured values.
- Canonical tile and mutation order, tile checkpoint progression, plan checksum, terminal journal
  state, Undo and Recovery contracts are unchanged.
- Cancel is observed after an accepted receipt and before the next submission. Unit tests prove that
  every candidate submits at most the already accepted slice after cancellation. The measured
  scheduler queue-depth upper bound remains one.
- No BlockData/BlockState cache, slice pipelining, multiple-slices-per-tick behavior, new artifact
  format, Schema change, catalog promotion, SLO, or LARGE support was added.

## Gate status

All automated Acceptance conditions pass. On 2026-07-22 the operator explicitly approved changing
the production apply slice to 1024 after reviewing the measured evidence, satisfying the human
confirmation required by `docs/design-v2/model-assignment.md`. `V2-13-06` is complete. This approval
does not close the V2-13 parent phase and does not authorize catalog or dimension promotion.

## Verification

- Closed candidate/budget/order/checksum/journal/cancel tests, Release 2 application regression,
  `PlacementPhaseGateV2Test`, and `LegacyV1GoldenArchiveTest`: PASS.
- Explicit regeneration of the affected sealed placement portfolio, repeated byte-for-byte: PASS.
- `./gradlew test`: PASS (`BUILD SUCCESSFUL`, 2m26s).
- `./gradlew build`: PASS.
- Measurement shell syntax, summary parser validation, JSON summary parse and `git diff --check`: PASS.
