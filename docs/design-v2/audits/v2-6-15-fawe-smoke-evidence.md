# V2-6-15 FAWE 2.15.2 standalone Release 2 placement smoke — evidence

- Status: **COMPLETE**（2026-07-19）
- See: [audit](v2-6-15-fawe-smoke.md)、[runbook](../../smoke/v2-6-15-fawe-2.15.2-runbook.md)、local runner `scripts/smoke/v2-6-15-run.sh`
- Version lock: [ADR 0032](../../adr/0032-fawe-2.15.2-standalone-smoke-profile.md)
- Local machine evidence directory (not committed): `build/smoke/v2-6-15/evidence/`

## Machine-verifiable fields

```text
Build JAR SHA-256: 7872c3cd4abd3b5da46619a1277797d884e1306b892fe305de4a75717202002b
FAWE plugin JAR SHA-256: fb25a65616b51cc54d999f0455ef9d7b75ba932db79e2cf2dcd874b7eef85fd1
Paper version: Paper version 1.21.11-132-ver/1.21.11@c5eb079
FAWE version (must be 2.15.2, include exact build): FastAsyncWorldEdit v2.15.2+e9ed0d1
WorldEdit plugin present? (must be no): no (Initialized 2 plugins: FastAsyncWorldEdit, LandformCraft)
LandformCraft version: 0.9.0-beta.1
Fixture Release ID / capability prefix: v2-6-14-fawe-smoke / surface-2_5d
Placement ID / operation ID: 24874d9d-8cd5-4638-9d17-86c1f87f60de / 424be876-36c7-42ab-986b-0feb87e94af1
Plan checksum: 21a868c2bbb752123ab10c2a1ee9452873e2378177a839a3a0492d198d47c204
Envelope checksum: f3c40b4251af558a15f624a4a468454ad219048aede3f6345ef3589fceb59fb0
Snapshot plan checksum: c5675a51e1d791fc0e93a7759d440040ddacf0af893319b4e5a70f171f58d988
Apply journal terminal state: APPLIED (before undo)
Verify evidence checksum: 39cb1b033af279c5e453307d53838bcf54db330f11a7fd11edcc53e2b76272b7 (expectedStream=observedStream=3b1658b4ea9b17cab102582b7d364c2670a080d71703de639b7aaa2d4e6a5701)
Undo journal terminal state: UNDONE
Recovery journal terminal state / result: not exercised (Undo full verify closed the operation; Recovery path remains available via /lfc r2)
Canonical expected checksum: 3b1658b4ea9b17cab102582b7d364c2670a080d71703de639b7aaa2d4e6a5701 (matches V2-6-14 WE fixture stream)
Peak queue depth: not sampled; smoke used single R2 operation
Peak heap (bytes): not sampled via MXBean; runFaweServer JVM -Xms2G -Xmx2G
Disk used by snapshots (bytes): 12288
Declared budget vs measured: disk snapshots=12288 under disk.maximum-snapshot-bytes default 8589934592
Gateway close / async completion result: FAWE disable on clean stop; no main-thread join / gateway hang in smoke log
Main-thread violation? (must be no): no
Secrets in logs/artifacts? (must be no): CONSOLE confirmation tokens appear in server log (known limitation; no API keys)
Operator / date: agent / 2026-07-19
Manifest JSON SHA-256: 69bdd0dafd3ce172c5cfde908c091bf5db815dce9698d2ae629bb40519aad525
Anchor: world 16 -60 16
Boot path: ./gradlew runFaweServer (runDirectory=run-fawe, Paper build 132)
```

## Sequence observed

1. `./gradlew runFaweServer` — Paper 1.21.11-132 + FastAsyncWorldEdit 2.15.2+e9ed0d1 only（WorldEdit jar absent）
2. `/lfc version` + `/lfc doctor`
3. forceload + envelope-bounded `fill` stone pocket
4. `/lfc r2 plan` → `CONFIRMATION_ISSUED`
5. `/lfc r2 confirm` → `SNAPSHOT_COMPLETE` + containment pass
6. `/lfc r2 execute` → settle/full verify → `APPLIED`
7. `/lfc r2 undo-plan` → `/lfc r2 undo-execute` → `UNDONE`

## Non-claims

- 500／1000 measurement not claimed
- production support catalog not elevated
- past 2026-07-14 FAWE init-only log not used as acceptance
- Beta FAWE checklist “crash Recovery E2E” remains separate unless attached to this evidence
