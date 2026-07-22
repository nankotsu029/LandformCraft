# Macro foundation / intent conformance監査（2026-07-22）

対象HEADは`5920afc`である。この監査は、400×400海岸TerrainIntent（`coastal-fishing-map`）で北側本土が消失しfeatureが孤立する事象の根本原因を、静的追跡と実行実測で確定し、V2-18 Phase登録の根拠を与える。実測はローカルworkstationで`./gradlew run`のCLI `export`を用い、Release出力はセッション外の一時領域へ書き、リポジトリへ成果物を残していない。

## 1. 実測evidence

同一request／intentを`SurfaceBaselineV2`のwater／land両指定でexportした。**両runともexportは成功し、strict verify・`placementEligible: true`まで到達した。**

| 計測 | baseline=water | baseline=land | Intentの要求 |
|---|---|---|---|
| 北端band（z=0..59）land率 | **0.000** | 1.000 | HARD `north-is-land` ≥0.85 |
| 南端band（z=340..399）water率 | 1.000 | **0.000** | HARD `south-is-sea` ≥0.90 |
| 全体land率 | 0.205 | 0.935 | theme記載 約0.32 |
| desired vs actual field | byte一致 | byte一致 | — |
| residual field | 全cell 0 | 全cell 0 | — |

- 2 run間で値が一致したcellは43,196／160,000（27.0%）、変化したcellは116,804（**73.0%**）。`CoastalSurfaceFieldsV2.generate`の構築上、land-water fieldでは「baseline変更で値が変わるcell」と「active contributorを持たないcell」は厳密に一致する（inactive cellは常にbaseline分類を採り、active cellはbaselineを参照しない）。ただし正式なcoverage metricは単一runの`active[]`／owner indexから直接集計して確定する（`V2-18-02`）。
- requestが参照するHARD `LAND_WATER_MASK`のPNG（`maps/coastal-fishing-map-land-water-u8.png`、宣言digestはダミー`dddd…`）はリポジトリに存在しないが、両exportは成功した。`core.v2.export`には`ConstraintMapSource.file()`へのI/O呼出サイト自体が存在せず、production export経路でmaskは解決・検証・消費されない。
- `EDGE_CLASSIFICATION`のSchema／recordは測定帯域幅を持たない（`edge`／`classification`／`minimumShare01`のみ）。上記band幅は監査上の便宜であり、最外周1 rowで測っても北端land率は0.000で、結論は帯域定義へ依存しない。測定領域の契約化は`V2-18-04`が行う。
- 元の事象を観測したセッションのCLI引数履歴は未取得である。water baselineで報告症状（北側本土消失・feature孤立）を再現でき、`docs/quickstart.md`／`docs/commands.md`の正典例もwater baselineを使うため、事象はwater baseline fallbackによるものと判断する。

## 2. 静的に確定した欠陥群

1. **基礎地形ownerの不在。** production surface経路はfeature非所有cellを一律`SurfaceBaselineV2`（全mapで単一のland/water分類＋単一Y）で埋める（`CoastalSurfaceFieldsV2.generate`、`SurfaceBaselineV2`）。大域land-water構図の正本が存在しない。設計文書のStage 2（macro layout）に対応するproduction実装はなく、正しい契約（ownerless cell拒否）は`SurfaceFoundationMergeCompilerV2`／`MacroLandWaterTopologyPlanV2`としてofflineに存在するがproduction spineへ未接続である。そのまま昇格できる完成品ではなく、raster生成stage等の統合実装を要する。
2. **map-level HARD入力の不作動。** (a) `LAND_WATER_MASK` mapReferenceは読まれず、`withLandWaterBinding`が生成結果fieldのdigestへ自己再束縛する。(b) `EDGE_CLASSIFICATION`は`ValidationTargetV2`へcompileされるが、どのvalidatorも`ValidationTargetV2`を消費しない。(c) contract-only kind（`BACKSHORE_PLAINS`）へのHARD relationはFeaturePlanのrelationIdsに記録されるだけで消費されない。なおHARD全般が黙殺されるわけではない（`ENCLOSES`はcompileで強制、beach幅はfeature parameters経由で検証される）。
3. **desired＝actualの自己参照。** `CoastalValidationInputV2(blueprint, fields, fields)`と、生成結果から書かれるdesired sidecar／恒等0のresidualにより、意図適合性の検査が構造的に成立しない。
4. **diagnostic契約とexport gateの断絶。** Blueprint compileは全featureへ無条件に`v2.unsupported-capability` ERRORを積むが、exportはblueprint diagnostic issuesを一切gateにしない。GATING／NON_GATINGの区別がなく、将来の一括fail-closed化を不可能にしている。
5. **テストの検証対象の欠落。** 既存テストは決定論・checksum・strict verify・budget拒否を固定するが、地形形状（北端が陸か、featureが接続するか）をどの層でもassertしない。
6. **一般化リスク。** production接続は coastal 4 kindのみ（current-feature-state registry）。V2-15の公開配線を現行アーキテクチャのまま進めると、同じ「一律baseline上の孤立feature」問題を全FeatureKindへ拡散する。

## 3. 未確定事項

- previewレンダラのpixelレベル照合（軸・色・転置）は未実施。preview入力の合成fieldが崩れていることは確定済みで、優先度は低い。
- `SurfaceFoundationMergeCompilerV2`等のfoundation資産をproductionへ昇格する際の正確な差分・予算はADR（`V2-18-08`）で確定する。

## 4. 登録判断

- 新Phase **V2-18 Macro foundation and intent conformance（12 Task）** をTrack Aへ登録する。Scope正本は[Task Index §18](../design-v2/task-index.md)。
- **V2-15 stage gate:** production export／placement昇格系leaf（公開配線・Release統合・eligibility昇格）は、`V2-18-09`（macro foundation spine）完了と対象kindのcomposition role登録を前提とする。registry／Schema／codec／offline plan／determinism系leafは継続可。`V2-15-09`は継続可であり`V2-18-08`／`09`の前提資材となる。
- fail-closed化は必ずreport-only段階を先行させる。owner coverage gateを`V2-18-09`より先にfail-closedへ入れると、現行で動作する唯一のproduction経路（feature非所有cell約73%）を全停止させるため禁止する。
- `V2-15-47`／`V2-16-19`のPhase gate Acceptanceへのintent-conformance portfolio追記は`V2-18-12`で人間承認のうえ行う。
