# 0016: scale class契約と3000×3000へ向けた実行計画境界を導入する

- Status: Accepted
- Date: 2026-07-17

## Context

現行の水平上限は`GenerationBounds.MAX_HORIZONTAL_SIZE = 1_000`に加え、v2の各generator・sampler・preview index等に`1..1_000`検査として15箇所以上分散している。予算テストも1000角のみを対象としてきた。再設計目標は推奨最大約3000×3000であり、これは面積で約9倍、全域fullレゾリューションのint field 1枚で約37 MiB（3072²×4 byte）に達するため、「上限値の書き換え」では成立しない。実行方式そのもの（低解像度大域計画、halo付きtile詳細化、disk-backed中間データ、streaming、部分再生成）を規模に応じて切り替える契約が必要である。

## Decision

1. **scale classを`model.v2.scale.ScaleClassV2`として導入する。**
   - `SMALL`（≤512）: 全域working setを許容できる対話規模
   - `MEDIUM`（≤1024）: 現行の対応上限。tile実行必須
   - `LARGE`（≤3072、推奨3000）: offline streaming実行必須。全域full解像度working setの常駐を禁止
2. **`ScaleProfileV2`をclassごとの実行policyとする。** tile size、halo、coarse cell（低解像度大域計画の解像度）、tile数上限、retained/working/artifact予算を持ち、信頼できる実装上限（trusted ceiling）でclampする。既定値はversion凍結し、変更はpolicy変更として扱う。
3. **`TilePlanV2`を決定論的なtile分割の正本とする。** canonical indexは`tileZ * tileCountX + tileX`（X fastest）で、iteration順・thread数・cacheに依存しない。tile IDは`tile-x{X}-z{Z}`で、ordinalへ依存しない。
4. **`core.v2.scale.ScaleAdmissionV2`（`scale-admission-v1`）を配置前検査とする。** 寸法→class→tile予算→per-tile working→retained（coarse計画）→artifactの順に、割当前へ検査し、失敗はstableなfailure codeで拒否する。推定定数は凍結された保守的planning値であり、各subsystemの実測budget gateを代替しない。
5. **段階的移行とする。** 既存の分散した`1..1_000`検査はこのADR時点では変更しない（checksum凍結の維持）。V2-8-02以降のTaskで、v2 subsystemごとにscale契約経由の寸法policyへ集約し、MEDIUM以下での出力checksum不変を回帰で保証しながら移行する。v1（`GenerationBounds`）は凍結のまま変更しない。
6. **LARGEの実行モデル**は [scale-and-streaming.md](../design-v2/scale-and-streaming.md) を設計正本とする: coarse大域計画（既定16 block/cell）→ global graph solve（hydrology等はtile化前にfreeze、必要ならcoarse解像度で解きreach単位で詳細化）→ halo付きtile詳細化（streaming、部分再生成、tile artifact hash）→ 分割export。

## Consequences

- 3000×3000はまだ生成できない。生成可能と表現してはならない。本ADRが導入したのは契約・分割・admissionのみであり、LARGEのsupported化にはV2-8の各budget gateと統合監査（V2-8-08）が必要である。
- LARGE既定profile: tile 128／halo 32／coarse 16 → 3072²で576 tile、coarse 192×192。admissionのretained推定は6層×4 byteで約1.5 MiB（＋tile状態）であり、大域full解像度常駐を計画から排除する。
- constraint map入力の現行上限（1枚4Mピクセル）は3072²（約9.4M）を収容できない。上限拡大はV2-8-03の明示Taskとし、decode/residentの実測を伴う。

## Alternatives

- **上限定数を3072へ一括変更**: 予算・solver計算量（hydrology priority floodは全域走査）が未検証のままsupported表明になるため棄却。
- **無段階（profileなし）で毎回自由指定**: 予算と実行方式の組合せが発散し、実測gateを定義できないため棄却。
- **LARGEをchunked world直接生成（Minecraft world generator化）**: 製品境界（offline Release→検証→配置）を破り、rollback契約が成立しないため棄却。
