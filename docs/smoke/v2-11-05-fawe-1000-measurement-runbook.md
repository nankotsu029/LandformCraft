# V2-11-05 FAWE 1000×1000 measurement runbook

Release 2 **1000×1000** placement measurement for **Paper 1.21.11 + FastAsyncWorldEdit-Paper 2.15.2 only**（WorldEdit plugin 禁止）。

Evidence: [v2-11-05-fawe-1000-measurement-evidence.md](../design-v2/audits/v2-11-05-fawe-1000-measurement-evidence.md)。
Audit: [v2-11-05-fawe-1000-measurement.md](../design-v2/audits/v2-11-05-fawe-1000-measurement.md)。

## 0. Preconditions

- Host with enough free RAM for Xmx 8G + forceloaded ~3969 chunks（専用高memoryホスト推奨）
- Java 21
- `run-fawe/paper-1.21.11-132.jar` と `run-fawe/plugins/FastAsyncWorldEdit-Paper-2.15.2.jar`
- WorldEdit jar が `run-fawe/plugins/` に無いこと
- Measurement profile は runner が config へ書き込む（手動で catalog 寸法を上げない）

```bash
./gradlew shadowJar
ls run-fawe/plugins/FastAsyncWorldEdit*.jar
test ! -e run-fawe/plugins/worldedit*.jar
```

## 1. Boot（standalone — not Gradle runServer）

```bash
LANDFORMCRAFT_V21105_XMX=8G LANDFORMCRAFT_V21105_XMS=2G \
  bash scripts/measure/v2-11-05-fawe-run.sh
```

Runner が行うこと:

1. 1000×1000 solid surface Release fixture export
2. `java -jar paper-1.21.11-132.jar` を `run-fawe/` で起動（RCON 25576）
3. measurement-profile enable（world ceiling 1000×1000）
4. stone-seal（strip `forceload` + `fill`）
5. envelope keep-load in ≤256-chunk strips（Paper cap）
6. Pass1／Pass2: plan→confirm→snapshot→apply→settle→full verify→Undo
7. Confirm 完了は journal `SNAPSHOT_COMPLETE` を待つ（`SNAPSHOTTING` 誤マッチ禁止）
8. Pass 間は Paper 再起動後に placement store を掃除
9. plan→SIGKILL→restart recovery drill
10. evidence を `build/measure/v2-11-05/evidence/fawe/` へ保存

## 2. forceload 注意

Paper 1.21 では単一 `forceload add` の上限が **256 chunks**。1000×1000 全域 forceload は失敗する:

```text
Too many chunks in the specified area (maximum 256, but specified 3969)
```

Runner は幅 64 block（4 chunks）× Z 全域の strip で keep-load する。

## 3. Non-claims

- Catalog `SUPPORTED` 寸法は 64×64 のまま（昇格は `V2-11-06`）
- WorldEdit 単独 profile は本 Task の必須 Scope 外
