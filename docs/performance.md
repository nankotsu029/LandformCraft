# Phase 1性能budget

## Budget

Phase 1のterrain gridとpreview生成では次を上限とします。

| 対象 | 上限 |
|---|---:|
| 500×500 terrain generation | 30秒 |
| 1000×1000 terrain generation | 60秒 |
| 推定peak working memory | 96 MiB |

`generationMillis`は`TerrainGenerator`だけのwall-clock計測です。Schema読込、PNG encoding、filesystem I/Oは含みません。memory値はgrid、作業配列、logical layout、1枚分のPNGを基にした保守的な推定であり、JVM process全体の実測RSSではありません。

## 2026-07-14 beta再計測

環境: Java 21.0.11、macOS、seed `827413`、generator `3.0.0-phase6`。`/usr/bin/time -l ./gradlew run`で現行beta codeを計測しました。両fixtureとも桟橋2基の安全候補を全域探索し、条件を満たさないため地形だけをwarning付きで採用するworst-case寄りの計測です。

| fixture | columns | tiles | generation | export | dir／ZIP verify | 推定peak | max RSS | Release dir／ZIP |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| rocky coast 500×500 | 250,000 | 16 | 1,258 ms（command 4.13 s） | 18.52 s | 1.09／0.91 s | 12,065,536 B | 108,560,384 B | 480 KiB／328,831 B |
| rocky coast 1000×1000 | 1,000,000 | 64 | 4,277 ms（command 7.46 s） | 67.77 s | 1.29／1.12 s | 48,262,144 B | 108,593,152 B | 1.5 MiB／1,083,255 B |

PNG preview時間はCLIの独立metricとしてまだ分離されずgenerate command wall timeへ含まれます。64×64 Paper＋WorldEdit／FAWE smoke（`V2-6-14`／`V2-6-15`）ではplan／apply／full verify／Undoが成功しました。500×500／1000×1000のPaper実測Task（`V2-6-16`／`V2-6-17`）は無効化済みで、未測定寸法のPaper apply性能を保証せずcatalog `SUPPORTED`にもしません。

計測値はhardwareとJVM warm-upで変動します。自動testは500×500を30秒以内かつ96 MiB推定以内で生成できることを検査し、1000×1000は`examples/performance/request-1000.yml`でgenerate／export／directory・ZIP verifyの手動smoke testができます。このfixtureはRelease計測のためschematicとZIPを有効にしています。

```bash
./gradlew run --args="generate examples/performance/request-1000.yml examples/rocky-coast/terrain-intent.json build/phase1-1000"
```
