# V2-11-04 FAWE 500×500 measurement — evidence

- Status: **COMPLETE**（2026-07-20）
- See: [audit](v2-11-04-fawe-500-measurement.md)、[runbook](../../smoke/v2-11-04-fawe-500-measurement-runbook.md)
- Runner: `scripts/measure/v2-11-04-fawe-run.sh`
- Local machine evidence directory (not committed): `build/measure/v2-11-04/evidence/fawe/`

## Machine-verifiable fields

```text
Profile: FAWE 2.15.2 standalone (WorldEdit plugin absent)
Boot: java -jar paper-1.21.11-132.jar in run-fawe/ (no Gradle runServer)
Xmx: 6G
Build JAR SHA-256: 89fea947a1a310c8b629fd69ef3bcbf4f2f613254f606f5bd3ae97a241b0b544
Manifest canonical SHA-256: 239bf8940de21a484279d34485a970babb1778ab263f7b50a86f55633505a629
Release: v2-11-04-measure-500 / surface-2_5d / 500×500 solid y=0..1 (16 tiles, tile size 128)
Measurement profile: enabled, isolated-world=world, ceiling=500×500, actor=CONSOLE/RCON
Pass1 placement ID: 2cefa7c0-0bd7-4657-9830-d74528927bd1
Pass1 terminal: APPLIED → full effect-envelope verify → UNDONE (wall_seconds=621)
Pass2 placement ID: 3df5030f-718d-4996-a11d-8a84b60ae505
Pass2 terminal: APPLIED → full effect-envelope verify → UNDONE (wall_seconds=627)
Peak RSS (bytes): 6524817408 (~6.08 GiB; PID-only telemetry)
Disk snapshots (bytes): 573440
Failure/recovery: plan → CONFIRMATION_ISSUED → SIGKILL → restart + doctor recorded
v1 regression: lfc help + lfc doctor issued on same FAWE profile
Catalog promotion: NOT performed (V2-11-06)
Operator / date: agent / 2026-07-20
```

## Sequence observed

1. Standalone Paper JVM (`run-fawe/` paperclip）+ FAWE 2.15.2 only
2. Measurement-profile startup warning present; WorldEdit plugin not enabled
3. Stone-seal of mutation envelope via Paper `forceload` **block** columns + `fill` strips（stable Undo baseline）
4. Pass1: plan → confirm → snapshot-all → apply → settle → full verify → `APPLIED` → Undo → `UNDONE`
5. Clean Paper restart; wipe placement store; Pass2 same lifecycle → `APPLIED` → `UNDONE`
6. Recovery drill: plan then SIGKILL before confirm; restart + doctor
7. v1 help/doctor smoke on same profile

## Non-claims

- Catalog dimension limit remains 64×64（昇格は`V2-11-06`）
- 1000×1000 not measured（`V2-11-05`）
- WorldEdit 7.3.19 profile not required for this Task
- LARGE / V2-8 streaming not claimed
