# Scale and Streaming（LARGE実行モデル）

> Status: 設計正本。契約（`model.v2.scale`／`core.v2.scale`、`scale-admission-v1`）は実装済み・`EXPERIMENTAL`。LARGE生成そのものは未実装で、[Task Index](task-index.md) のV2-8が実装経路である。採用判断は [ADR 0016](../adr/0016-scale-classes-and-execution-planning.md)。**2026-07-21のS2決定により`V2-8-03`〜`V2-8-08`は保留（LARGE契約は凍結保持、3000／3072のsupported表現禁止）であり、実装重点はV2-13の1024（MEDIUM）である。`V2-8-02`完了により、分散していたv2の水平寸法検査は`ScaleDimensionPolicyV2`（MEDIUM=1024）へ統一済み。`V2-13-02`で`GenerationRequestV2.Bounds`と水平寸法Schema `width`／`length` 上限も1024へ拡張済み。`V2-13-03`実測で1024² offline E2Eは hydrology／preview／macro の1_000_000 cell予算で拒否されたが、同日follow-up修正で`ScaleDimensionPolicyV2.MEDIUM_MAXIMUM_CELLS`（=1024²=1_048_576）へ拡張し、offline E2Eは成立した（[evidence](audits/v2-13-03-offline-budget-measurement-evidence.md)）。`PlacementDimensionLimitV2`は別gateで実測1000×1000のまま不変。**

## 1. scale class

| class | 上限（blocks/辺） | 実行方式 | 状態 |
|---|---|---|---|
| `SMALL` | 512 | whole/tile任意。即時preview・局所編集重視 | 契約のみ（既存経路は寸法上限内で動作） |
| `MEDIUM` | 1024 | tile必須。request／Schema／`GenerationBounds`水平上限とjava routeの対象（`V2-13-02`／`03`）。1024² offline E2Eは`V2-13-03`の同日follow-up修正でcell subsystem予算を`ScaleDimensionPolicyV2.MEDIUM_MAXIMUM_CELLS`へ拡張し成立済み。Paper配置の実測上限は別途1000（未変更） | request／route天井。配置SUPPORTED寸法は`PlacementDimensionLimitV2` |
| `LARGE` | 3072（推奨3000） | streaming必須。全域full解像度working set常駐禁止 | 契約のみ。V2-8 gateまで生成不可 |

既定profile（version凍結。変更はpolicy変更）:

| class | tile | halo | coarse cell | tile上限 | retained | working | artifact |
|---|---:|---:|---:|---:|---:|---:|---:|
| SMALL | 128 | 16 | 4 | 16 | 64 MiB | 32 MiB | 256 MiB |
| MEDIUM | 128 | 16 | 8 | 64 | 96 MiB | 64 MiB | 1 GiB |
| LARGE | 128 | 32 | 16 | 576 | 256 MiB | 128 MiB | 4 GiB |

## 2. LARGEパイプライン（V2-8で実装する実行グラフ）

```text
入力（Intent v2 / constraint map / 抽出draft昇格分）
→ ScaleAdmissionV2（寸法・tile・working・retained・artifact事前検査）
→ coarse大域計画（coarse cell解像度のmacro layout / land-water / zone）
→ global graph solve（hydrology basin/routingをtile化前にfreeze。
   full解像度で予算超過する場合はcoarse solve＋reach単位詳細化）
→ halo付きtile詳細化（canonical row-major、TilePlanV2）
   - tileごとにfield windowを生成し、LFC_GRID_V1 sidecarへstream書込み
   - tile artifact hash＝cache key。入力（Blueprint checksum・seed・依存tile halo）から導出
→ 部分再生成（変更featureの影響tileをhalo依存グラフで無効化し、該当tileだけ再生成）
→ validation / preview（段階生成: coarse overviewを先に、tile詳細を後で）
→ export分割（tile群をsegment単位のRelease 2へ分割。manifestで結合検証）
```

## 3. 不変条件（LARGEでも変更しない）

- whole/tile、tile順、thread数、locale、timezoneでfield/metric/block checksumが一致する。
- 全域dense voxel・全域full解像度field配列を正本または常駐配列にしない。中間データはdisk-backed sidecar＋bounded windowで読む。
- global graph（hydrology等）はtile化前にsolveしfreezeする。tile-local graphを作らない。
- admissionで拒否できない予算超過を実行途中のOOMで発見しない。全stageが事前見積を宣言する。
- resume・cancel・partial regenerationは、staging→strict read-back→atomic publishの既存規約に従う。

## 4. 既知の未解決点（V2-8の各Taskで確定する）

- hydrology priority floodの3072²実測（V2-8-04）。coarse solve時の詳細化誤差契約を含む。
- constraint map decode上限（現行1枚4Mピクセル）のLARGE対応（V2-8-03）。
- preview pyramid（段階preview）の解像度段数（V2-8-06）。
- Release分割の単位とmanifest結合検証（V2-8-07）。
- LARGEのPaper配置はV2-6完了後の追加Task（V2-8はofflineのみをgateする）。
