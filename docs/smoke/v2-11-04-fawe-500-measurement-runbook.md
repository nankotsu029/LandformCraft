# V2-11-04 FAWE 500×500 measurement runbook

Release 2 **500×500** placement measurement for **Paper 1.21.11 + FastAsyncWorldEdit-Paper 2.15.2 only**（WorldEdit plugin 禁止）。

Evidence: [v2-11-04-fawe-500-measurement-evidence.md](../design-v2/audits/v2-11-04-fawe-500-measurement-evidence.md)。
Audit: [v2-11-04-fawe-500-measurement.md](../design-v2/audits/v2-11-04-fawe-500-measurement.md)。

## 0. Preconditions

- Host ~14 GiB RAM（Xmx 6G 推奨）、Java 21
- `run-fawe/paper-1.21.11-132.jar` と `run-fawe/plugins/FastAsyncWorldEdit-Paper-2.15.2.jar`
- WorldEdit jar が `run-fawe/plugins/` に無いこと
- Measurement profile は runner が config へ書き込む（手動で catalog 寸法を上げない）

```bash
./gradlew shadowJar
# FAWE-only profile
ls run-fawe/plugins/FastAsyncWorldEdit*.jar
test ! -e run-fawe/plugins/worldedit*.jar
```

## 1. Boot（standalone — not Gradle runServer）

```bash
LANDFORMCRAFT_V21104_XMX=6G bash scripts/measure/v2-11-04-fawe-run.sh
```

Runner が行うこと:

1. 500×500 solid surface Release fixture export
2. `java -jar paper-1.21.11-132.jar` を `run-fawe/` で起動（RCON 25576）
3. measurement-profile enable（world ceiling 500×500）
4. stone-seal（`forceload` **block** range + strip `fill`）
5. Pass1／Pass2: plan→confirm→snapshot→apply→settle→full verify→Undo
6. Pass 間は Paper 再起動後に placement store を掃除
7. plan→SIGKILL→restart recovery drill
8. evidence を `build/measure/v2-11-04/evidence/fawe/` へ保存

## 2. forceload 注意

Paper 1.21 では:

```text
forceload add <blockX> <blockZ> [<toBlockX> <toBlockZ>]
```

`forceload add 5 5` は chunk `[0,0]` を mark する（block 5,5）。500×500 envelope は `forceload add 0 0 499 499`。

## 3. Non-claims

- Catalog `SUPPORTED` 寸法は 64×64 のまま（昇格は `V2-11-06`）
- 1000×1000 は `V2-11-05`
- WorldEdit 単独 profile は本 Task の必須 Scope 外
