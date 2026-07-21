# V2-13-06 apply slice bottleneck analysis（read-only performance review、2026-07-21）

- Status: **ANALYSIS**（read-only。コード・Schema・Task状態・fixtureの変更なし）
- 入力: [V2-13-01 evidence](v2-13-01-stage-instrumentation-evidence.md)（1000²）、[V2-13-04 evidence](v2-13-04-fawe-1024-measurement-evidence.md)（1024²）、現行placement実装
- 出力: `V2-13-06` の根拠、`V2-13-05` の `NOT_APPLICABLE_BY_MEASUREMENT` クローズの根拠
- 判定: **ACTIONABLE_OPTIMIZATION_FOUND**（2026-07-22 follow-up: `V2-13-06`較正は1024を選択し全gate PASS、実機evidence人間承認済み。[calibration audit](v2-13-06-apply-slice-calibration.md)）

## 1. 実測から確定している事実

| 事実 | 根拠 |
|---|---|
| APPLYが両寸法・両passで最大ボトルネック（~79.5%） | V2-13-01: 1572 s／V2-13-04: 1650 s |
| APPLYは**slice数に厳密に線形** | 1000²: 2,000,000÷32=62,500 slices→1572 s=**25.15 ms/slice**。1024²: 2,097,152÷32=65,536 slices→1650 s=**25.18 ms/slice**（2寸法で誤差0.1%） |
| ブロック内容・FAWE書込み速度は支配項ではない | 実効~1,270 blocks/sはper-slice固定費25 msで全て説明できる |
| 1024²に非線形増加なし | total +5.0%＝cell数+4.9%、stage構成比は1000²と同型 |

## 2. コード上の原因（確定）

根本原因: **apply書込みsliceが32 mutations/sliceで、1 sliceごとにPaper scheduler往復＋EditSession生成/closeを行う。**

1. `PlacementApplyLimitsV2.defaults()` — `maximumMutationsPerSchedulerSlice = 32`（契約上限は4,096）。
2. `PaperMainThreadDispatcher.supply()` — slice毎に `Bukkit.getScheduler().runTask(...)`（次tick実行）。1往復＝worker→scheduler→main→workerで実測~25 ms。
3. `WorldEditBlockMutationAccessV2.apply()` — slice毎に `new EditSession`→32 blocks→close。FAWEのバッチ性能を引き出せない粒度。
4. 連鎖制約: `PlacementPlanCompilerV2` のplan budget `maximumWorkingBytes = 32 KiB` がadmission（slice×640 B ≤ budget）でslice拡大を上限51に制限。gateway契約 `PlacementApplySliceV2` 自体は4,096 blocks/sliceまで許容。

### In-repo対照実測（決定的）

| 経路 | slice | 同量~2.1M blocksの実測（1024²） |
|---|---:|---:|
| APPLY（書込み、`apply()`） | **32** blocks/slice＝65,536往復 | **1650 s** |
| UNDO restore（書込み、同一gateway・同型`restore()`） | **1,024** blocks/slice＝2,048往復（＋drift検証read 2,048往復込み） | **184 s** |
| VERIFY（読取り） | 1,024 blocks/slice＝2,048往復 | 27 s |

Undoは同一 `WorldEditBlockMutationAccessV2`／同一dispatcher／同一EditSessionパターンで同量のブロックを**9分の1の時間**で書いている。slice粒度が唯一の説明変数。

副次的発見: `Bukkit.createBlockData(String)` をmutation毎に毎回パース（キャッシュなし、apply/restore共通）。~2.1M回パース（solid fixtureのユニーク状態は3種）。ただし現在の1650 sの大部分はscheduler往復で説明されるため、キャッシュ単独の寄与は小さい見込み（µsオーダー×2.1M＝数秒〜数十秒）。**slice拡大（主改善）とBlockDataキャッシュ（低リスク補助改善）は分離して測定する。**

## 3. 期待効果の推定と不確実性

- 下限~90 s／上限~200 s（slice=1,024時のAPPLY）: UNDO実測からの内挿（下限）とper-slice 90 ms×2,048の保守値（上限）。
- 仮定: restore sliceとapply sliceのper-slice費用が同オーダー（同型コードのため妥当性は高いが、apply側はStreamValidator SHA-256継続分やや重い）。
- **1,024を無条件採用しない。** Undoでの1,024実績はあるが、APPLYのmain-thread占有時間が同じとは限らず、tick stall／TPS低下／watchdog risk／他plugin影響／cancellation検知遅延の懸念があるため、32／128／256／512／1,024の較正実測で安全な最大値を選択する（`V2-13-06` Scope）。
- `maximumWorkingBytes`（32 KiB）は単なる定数ではない可能性がある。640 bytes/mutationの内訳、同時保持最大mutation数、slice終了後の解放、複数placement同時実行時の合計、admissionの役割（メモリ保護かplan契約か）の監査を変更の前提とする（`V2-13-06` Scope）。

## 4. V2-13-05への含意

SNAPSHOT＋UNDO（per-block `Map`保有工程）は1000²/1024²とも合計~19%で、2寸法の独立実測により「Map／allocationが主要ボトルネック」という条件付き承認の前提は不成立が確定した。さらにAPPLY律速の根本原因はslice往復粒度でありMap表現と無関係。

```text
Status: NOT_APPLICABLE_BY_MEASUREMENT
Reason: APPLY scheduler-slice overhead is the dominant bottleneck;
per-block Map packing does not address the measured cause.
```

## 5. 不採用とした代替案

- slice pipelining／複数slice per tick: 順序・cancel観測点・containmentの意味論に触れるため不採用（slice拡大が同じ効果を無並行変更で達成）。
- no-op（同一state）書込みskip: 読取り追加とcanonical stream契約への複雑性が見合わない。
- SNAPSHOT/VERIFY read slice拡大（1,024→4,096）: 期待効果<60 sで主対象としない。

## 6. Non-claims

- 本分析はコード・Schema・docs（本fileと`V2-13-06`定義を除く）・Task状態・設定・fixtureを変更していない。
- 改善率はin-repo対照実測に基づく推定であり、採否と最終値は`V2-13-06`の較正gate（APPLY≥30%短縮等）が決める。
- safety順序・snapshot-all・containment・full verify・Undo・Recovery・決定性・checksum・artifact formatの変更は提案に含まない。
