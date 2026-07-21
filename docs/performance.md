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

PNG preview時間はCLIの独立metricとしてまだ分離されずgenerate command wall timeへ含まれます。64×64 Paper＋WorldEdit／FAWE smoke（`V2-6-14`／`V2-6-15`）ではplan／apply／full verify／Undoが成功しました。`V2-11-04`（2026-07-20）はFAWE 2.15.2／`run-fawe`単独JVMで500×500 solid surfaceをPass1／Pass2とも`APPLIED`→full verify→`UNDONE`まで実測し、wall ≈ 621／627 s、peak RSS ≈ 6.08 GiB（Xmx 6G）を記録した（[evidence](design-v2/audits/v2-11-04-fawe-500-measurement-evidence.md)）。`V2-11-05`（2026-07-20）の1000×1000旧baselineはwall ≈ 6342／6436 s、peak RSS ≈ 6.02 GiBだった（[evidence](design-v2/audits/v2-11-05-fawe-1000-measurement-evidence.md)）。`V2-13-06`（2026-07-22）はapply slice 32／128／256／512／1024を同一ホスト較正して1024をproduction既定に選び、1000×1000再測定をwall 716／716 s（stage total 506／507 s、APPLY 102／103 s、peak RSS 5,624,307,712 bytes）、1024×1024をwall 746／746 s（stage total 533／533 s、APPLY 108／108 s、peak RSS 5,527,519,232 bytes）で完走した。4 passすべて`APPLIED`→full verify→`UNDONE`、Recovery不変、watchdogなし（[evidence](design-v2/audits/v2-13-06-apply-slice-calibration-evidence.md)）。`V2-11-06`が昇格したpublished dimension limitは引き続き1000×1000（FAWE 2.15.2 evidence）で、V2-13-06はcatalogを変更しません。WorldEdit単独runtimeは64×64のままで、1000超は通常配置で拒否されます。

計測値はhardwareとJVM warm-upで変動します。上表のrocky coast CLI値はv1退役前の歴史的baselineで、fixtureは`src/main/resources/legacy/v1/fixtures/performance/`へ移設済みです。現行のv2寸法根拠は`V2-11-04`／`V2-11-05`のFAWE実機evidenceとcatalogです。新しい手動計測はv2 request／intentを明示し、`generate`／`export`／`preview`を使って実行します。v1 generatorは実行できません。
