# V2-6-14 WorldEdit 7.3.19 smoke runbook

Release 2 end-to-end placement smoke for **Paper 1.21.11 + WorldEdit 7.3.19 only**（FAWE 禁止）。

この runbook は手順の正本である。完了証拠は [v2-6-14-worldedit-smoke-evidence.md](../design-v2/audits/v2-6-14-worldedit-smoke-evidence.md) を正本とする。自動化runnerは `scripts/smoke/v2-6-14-run.sh`（RCON）である。

## 0. Preconditions

```bash
./gradlew clean shadowJar
# Prefer: ./gradlew runServer  (downloads paperclip, uses run/ with WE)
# Or: bash scripts/smoke/v2-6-14-run.sh
# Do NOT: java -jar run*/versions/*/paper-*.jar
#   → NoClassDefFoundError: joptsimple/OptionException
```

Release 2 Paper path（必須・`V2-6-21`で実装済み）:

- R2 plan→reservation-bound confirmation→snapshot-all→containment→apply→settle/full verifyを束ねるPaper application serviceと明示command routingがあること（`/lfc r2`）
- `Landformcraft.java` が`PaperRelease2PlacementServiceV2`を注入し、Undo／Recoveryは同service経由であること
- `PaperPlacementWorldGatewayV2.advanceSettleTick`の逐次scheduler dispatchが実際のmulti-tick progressionとなり、delayed physicsがeffect envelope外へ漏れないこと
- 上記のservice shutdown／restart／failure testが通っていること

Server profile:

- WorldEdit **7.3.19** + LandformCraft only（FAWE 禁止）
- Java 21／Paper 1.21.11

## 1. Fixture prepare（CLI, offline）

推奨 capability prefix（いずれか＋可能なら複数）:

- `surface-2_5d`（coastal）— `scripts/smoke/v2-6-14-run.sh` が `V2614WorldEditSmokeFixtureExporterTest` で4×4 fluid fixtureをexport
- `hydrology-plan,surface-2_5d`（fluid）
- `sparse-volume` 完全 prefix（volume）

```bash
LANDFORMCRAFT_V2614_EXPORT_DIR=build/smoke/v2-6-14/fixture \
  ./gradlew test --tests '*V2614WorldEditSmokeFixtureExporterTest' --rerun-tasks
```

Acceptance 用に次を保存する（Git へは checksum と相対 path のみ。server world／secret は入れない）:

- Release directory path（server外）
- ZIP path
- manifest canonical checksum
- requiredCapabilities

## 2. Server smoke sequence

Test world で（または `scripts/smoke/v2-6-14-run.sh`）:

1. `/lfc version`
2. `/lfc doctor` — WE 7.3.19、writable、atomic move、R2 paths
3. Release verify（CLI or admin path）
4. R2 plan（bounds／anchor／capability）
5. reservation-bound confirmation（actor-bound token；CONSOLE token は log 平文問題あり → 証拠に明記）
6. snapshot-all complete
7. containment preflight（fluid／gravity fixture）
8. apply → settle → full verify → terminal APPLIED
9. Undo prepare → confirm → UNDONE
10. optional: restart mid-flight journal を別 run で1回（RECOVERY_REQUIRED 経路は証拠化）

Fluid fixtureでは effect envelope 内を既知solidでsealしてからplanする（Paper `fill` 上限32768に注意）。

## 3. Assertions to capture

- WorldEdit version string == `7.3.19`
- LandformCraft JAR SHA-256 == build record
- no Paper main-thread AI／artifact I／O／heavy CPU（logs）
- peak heap／snapshot disk ≤ declared budget
- post-Undo exact world verify
- no API keys／absolute secret paths in committed evidence
- v1 placement smoke on same server still passes（regression）

## 4. After success

1. Fill machine-verifiable fields in `docs/design-v2/audits/v2-6-14-worldedit-smoke-evidence.md`
2. Set Task Index `V2-6-14` status to 完了 with evidence link
3. Advance roadmap Next to `V2-6-15`
4. Do **not** mark FAWE or 500/1000 measurement complete
5. Do **not** accept attestation without checksums／journal terminals
