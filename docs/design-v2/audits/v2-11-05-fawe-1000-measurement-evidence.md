# V2-11-05 FAWE 1000×1000 measurement — evidence

- Status: **COMPLETE**（2026-07-20）
- See: [audit](v2-11-05-fawe-1000-measurement.md)、[runbook](../../smoke/v2-11-05-fawe-1000-measurement-runbook.md)
- Runner: `scripts/measure/v2-11-05-fawe-run.sh`
- Local machine evidence directory (not committed): `build/measure/v2-11-05/evidence/fawe/`

## Machine-verifiable fields

```text
Profile: FAWE 2.15.2 standalone (WorldEdit plugin absent)
Boot: java -jar paper-1.21.11-132.jar in run-fawe/ (no Gradle runServer)
Xmx: 8G / Xms: 2G
Build JAR SHA-256: 4f83ffd72e7fa9ed3b4a61f54fee0be42648a1f49411ce6f3ae77710c2fc0597
Manifest canonical SHA-256: d88db212800a56879c2f6baa35bf3b535c0d326c02bf06980de4b3637fb5a861
Release: v2-11-05-measure-1000 / surface-2_5d / 1000×1000 solid y=0..1 (64 tiles, tile size 128)
Measurement profile: enabled, isolated-world=world, ceiling=1000×1000, actor=CONSOLE/RCON
Pass1 placement ID: 025e3190-59bb-47c9-a9ea-212a7adc6d5d
Pass1 terminal: APPLIED → full effect-envelope verify → UNDONE (wall_seconds=6342)
Pass2 placement ID: 65a84a3a-fb16-4f4b-9f70-1295f2153b2a
Pass2 terminal: APPLIED → full effect-envelope verify → UNDONE (wall_seconds=6436)
Peak RSS (bytes): 6460502016 (~6.02 GiB; PID-only telemetry, max of pass1/pass2)
Disk snapshots (bytes): 2034380
Failure/recovery: plan → CONFIRMATION_ISSUED → SIGKILL → restart + doctor recorded
v1 regression: lfc help + lfc doctor issued on same FAWE profile
Admission: disk_budget_exceeded_hits=0
Catalog promotion: NOT performed (V2-11-06)
Operator / date: agent / 2026-07-20
```

## Sequence observed

1. Standalone Paper JVM（`run-fawe/` paperclip）+ FAWE 2.15.2 only
2. Measurement-profile startup warning（ceiling 1000×1000）；WorldEdit plugin not enabled
3. Stone-seal via strip `forceload`＋`fill`；then keep-load envelope in ≤256-chunk strips
4. Pass1: plan → confirm → snapshot-all → containment → apply → settle → full verify → `APPLIED` → Undo → `UNDONE`
5. Clean Paper restart；wipe placement store；Pass2 same lifecycle → `APPLIED` → `UNDONE`
6. Recovery drill: plan then SIGKILL before confirm；restart + doctor
7. v1 help/doctor smoke on same profile

## Production defect fixed in-Task

- `PlacementExpectedBlockResolverV2`／`PlacementUndoServiceV2`／`PlacementRollbackServiceV2`: replace `Map.copyOf`（`ImmutableCollections.MapN`）with `Collections.unmodifiableMap` for exclusively-owned mutation／baseline maps. MEDIUM 1000×1000（〜2M entries）was stalling confirm／Undo setup for >12 minutes on MapN construction.

## Non-claims

- Catalog dimension limit remains 64×64（昇格は`V2-11-06`）
- WorldEdit 7.3.19 profile not required for this Task
- LARGE / V2-8 streaming not claimed
