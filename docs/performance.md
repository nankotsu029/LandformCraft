# Phase 1性能budget

## Budget

Phase 1のterrain gridとpreview生成では次を上限とします。

| 対象 | 上限 |
|---|---:|
| 500×500 terrain generation | 30秒 |
| 1000×1000 terrain generation | 60秒 |
| 推定peak working memory | 96 MiB |

`generationMillis`は`TerrainGenerator`だけのwall-clock計測です。Schema読込、PNG encoding、filesystem I/Oは含みません。memory値はgrid、作業配列、logical layout、1枚分のPNGを基にした保守的な推定であり、JVM process全体の実測RSSではありません。

## 2026-07-13計測

環境: Java 21、macOS、seed `827413`、generator `1.1.0-phase1`。

| fixture | columns | tiles | generation | 推定peak | validation |
|---|---:|---:|---:|---:|---|
| rocky coast 500×500 | 250,000 | 16 | 約0.3秒 | 12,065,536 bytes | valid |
| rocky coast 1000×1000 | 1,000,000 | 64 | 約0.9秒 | 48,262,144 bytes | valid |

計測値はhardwareとJVM warm-upで変動します。自動testは500×500を30秒以内かつ96 MiB推定以内で生成できることを検査し、1000×1000は`examples/performance/request-1000.yml`で手動smoke testできます。

```bash
./gradlew run --args="generate examples/performance/request-1000.yml examples/rocky-coast/terrain-intent.json build/phase1-1000"
```
