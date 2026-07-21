# V2-13-06 apply slice calibration runbook

This runbook calibrates the closed apply-slice set and then re-measures the selected value at 1000²
and 1024². It is real-machine dependent and uses Paper 1.21.11 build 132 + FAWE 2.15.2 standalone.

- Audit: [v2-13-06-apply-slice-calibration.md](../design-v2/audits/v2-13-06-apply-slice-calibration.md)
- Evidence: [v2-13-06-apply-slice-calibration-evidence.md](../design-v2/audits/v2-13-06-apply-slice-calibration-evidence.md)
- Candidate runner: `scripts/measure/v2-13-06-fawe-calibration-run.sh`
- Selected runner: `scripts/measure/v2-13-06-selected-fawe-run.sh`
- Aggregators: `v2-13-06-calibration-summary.py`, `v2-13-06-selected-summary.py`

## Preconditions

Use the same isolated host requirements as V2-13-04: Java 21, at least 8 GiB heap capacity,
`run-fawe/paper-1.21.11-132.jar`, FastAsyncWorldEdit-Paper 2.15.2, no WorldEdit plugin, and no other
server instance on ports 25566/25576. Never enable the slice override outside the isolated
measurement profile.

## Candidate calibration

```bash
bash scripts/measure/v2-13-06-fawe-calibration-run.sh
```

The runner executes 32, 128, 256, 512 and 1024 in ascending order. Each candidate uses the full
1024² two-pass lifecycle and Recovery drill, captures per-stage timing, Paper MSPT/TPS, PID-only
heap/RSS, GC logs, APPLIED/UNDONE journals and the final server log, then emits:

```text
build/measure/v2-13-06/calibration-summary.json
```

Do not select a value by throughput alone. The largest candidate is eligible only if APPLY improves
at least 30%, lifecycle at least 20%, maximum tick/MSPT and memory do not degrade, GC stays within the
configured pause goal, watchdog remains silent, and both passes retain exact verify/Undo/Recovery.

## Selected-value re-measurement

After setting the production default to the measured selection, run:

```bash
bash scripts/measure/v2-13-06-selected-fawe-run.sh
python3 scripts/measure/v2-13-06-selected-summary.py \
  --root build/measure/v2-13-06/selected \
  --calibration build/measure/v2-13-06/calibration-summary.json \
  --out build/measure/v2-13-06/selected-summary.json
```

The selected runner requires one plugin JAR checksum across both dimensions, two passes per
dimension, and repeats the 1024² Recovery drill. A failed optional health sampler must be recorded as
unavailable; never copy or invent samples. The candidate calibration remains valid safety evidence
only when it used the same host/profile and selected slice.

## Example regeneration and verification

```bash
sha256sum examples/v2/placement/placement-plan-v2.json \
  examples/v2/placement/placement-journal-v2.json
LANDFORMCRAFT_V21306_REGENERATE_PLACEMENT_EXAMPLES=true \
  ./gradlew test --tests \
  'com.github.nankotsu029.landformcraft.core.v2.placement.PlacementPlanCompilerV2Test.bundledExamplesMatchCompilerContract' \
  --rerun-tasks
sha256sum examples/v2/placement/placement-plan-v2.json \
  examples/v2/placement/placement-journal-v2.json
```

The two hash sets must match. Before human evidence review, also run targeted placement tests, the
full test suite, build, `git diff --check`, and verify that no `build/`, `run-fawe/`, secrets, or
`.task-context/` files are tracked.

## Non-claims

This runbook does not authorize catalog/dimension promotion, new capability claims, SLOs, LARGE,
slice pipelining, caches, or safety-order changes. Human confirmation of the real-host evidence is
required before `V2-13-06` is marked complete.
