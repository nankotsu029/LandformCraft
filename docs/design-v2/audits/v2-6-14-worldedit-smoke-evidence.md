# V2-6-14 WorldEdit 7.3.19 Release 2 placement smoke — evidence

- Status: **COMPLETE**（2026-07-19）
- See: [audit](v2-6-14-worldedit-smoke.md)、[runbook](../../smoke/v2-6-14-worldedit-7.3.19-runbook.md)、local runner `scripts/smoke/v2-6-14-run.sh`
- Local machine evidence directory (not committed): `build/smoke/v2-6-14/evidence/`

## Machine-verifiable fields

```text
Build JAR SHA-256: 7872c3cd4abd3b5da46619a1277797d884e1306b892fe305de4a75717202002b
Paper version: Paper version 1.21.11-132-ver/1.21.11@c5eb079
WorldEdit version (must be 7.3.19): WorldEdit v7.3.19+7378-cb4171a
LandformCraft version: 0.9.0-beta.1
Fixture Release ID / capability prefix: v2-6-14-we-smoke / surface-2_5d
Placement ID / operation ID: 95879e48-ddca-48e0-8b09-6e76bda0c09d / 13cf0130-6cf0-41d8-a7cd-3ef458da5873
Plan checksum: 0d01d965c9d8b198b3cc317a64468b3094a590ce2b40af54b031ca7e653d91c7
Envelope checksum: e36605c7a5231aea015f8c737b977b5bacf36c9baf707e035ea9dd1e633b5117
Snapshot plan checksum: f9f8ccab440a72f1e08b62882954359326d49723bf49283fe5ac42fd41c1da4e
Apply journal terminal state: APPLIED (before undo)
Verify evidence checksum: 8822bd100669b3b2bb3efcef08578fa5b5ad20204bf9aafed638b4dea4d6546c (expectedStream=observedStream=3b1658b4ea9b17cab102582b7d364c2670a080d71703de639b7aaa2d4e6a5701)
Undo journal terminal state: UNDONE
Peak heap (bytes): not sampled via MXBean; runServer JVM -Xms2G -Xmx2G
Disk used by snapshots (bytes): 12288
Declared budget vs measured: disk snapshots=12288 under disk.maximum-snapshot-bytes default 8589934592
Main-thread violation? (must be no): no
Secrets in logs/artifacts? (must be no): CONSOLE confirmation tokens appear in server log (known limitation; no API keys)
v1 regression placement ID / result: v1 export release-23c09e2f2f5231e3 present; /lfc help + /lfc doctor on same WE 7.3.19 profile
Operator / date: agent / 2026-07-19
Manifest JSON SHA-256: 69bdd0dafd3ce172c5cfde908c091bf5db815dce9698d2ae629bb40519aad525
Anchor: world 16 -60 16
```

## Sequence observed

1. `./gradlew runServer` — Paper 1.21.11-132 + WorldEdit 7.3.19 only（FAWE jar absent）
2. `/lfc version` + `/lfc doctor`
3. forceload + envelope-bounded `fill` stone pocket
4. `/lfc r2 plan` → `CONFIRMATION_ISSUED`
5. `/lfc r2 confirm` → `SNAPSHOT_COMPLETE` + containment pass
6. `/lfc r2 execute` → settle/full verify → `APPLIED`
7. `/lfc r2 undo-plan` → `/lfc r2 undo-execute` → `UNDONE`

## Smoke defect fixed in-tree

WorldEdit／Bukkit live read-back emitted `minecraft:water[level=0]` while Release tiles store catalog form `minecraft:water`. `WorldEditBlockMutationAccessV2.toReleaseCanonical` collapses default property sets onto the environment allowlist identifier so exact verify matches without accepting non-default flowing water.
