# V2-19-05 RIVER block materialization

> Status: **PASS（2026-07-23）**。[横断監査 rev.2](../../audits/cross-cutting-audit-2026-07-23.md) が確定した「`V2-15-10`のRIVER routeは公開Releaseのblockを1個も変えない（plan-only配線）」欠陥を解消し、`V2-19-01` semantic materialization gateの**最初の適用**として、offline production routeがfinal canonical block streamを実際に変えることをportfolioで実測した。**新Schema・新capability・新artifact format・新Release capabilityは追加していない**。Paper能力列・寸法上限・sealed catalog・v1資産は不変。

## 1. 欠陥（修正前）

`harbor-cove-64-honored-river`（RIVER宣言）と`harbor-cove-64-honored`（同じcoastal契約、river無し）の
公開Releaseは、final tile block-state streamが**完全一致**していた。

| 列 | 修正前 |
|---|---|
| plan-only metric（`hydrology/validation.json`） | `hydrology.river.channel-gaps=0`／`reverse-gradient-cells=0`／`source-mouth-reachable=1`（全PASS） |
| block metric（final canonical block stream） | `changedCells=0`（`FeatureMaterializationV2.measureBlockEffect`） |
| 表示（`public-dispatch-reachability-v1`） | `RIVER`／`MEANDERING_RIVER` = `OFFLINE_PRODUCTION` かつ `PLAN_ONLY` |

`V2-19-01`はこの状態をnegative fixtureとして正直にpinし、gateが必ず拒否することを固定していた。

## 2. 修正

### 2.1 global route freeze（tile書出し前）

`CoastalSurfaceExportPipelineV2`を`prepare`（tiles以外の全部）と`completeWithTiles`（final canonical
block streamの唯一の生成点）へ分割した。`HydrologyPlanExportPipelineV2`は
`prepare → routing → reconciliation → river bed freeze → completeWithTiles` の順で走るため、
**全tileが1つの凍結routeを見る**（tile単位の再導出もseam毎の再計算も存在しない）。
riverを宣言しないrunはoverlayが空で、block streamはbyte不変である。

### 2.2 `RiverBedMaterializationV2`（`river-bed-materialization-v1`）

bounded river bed field（channel／bank AABBのみ確保。全domainを確保しない）をfreezeし、列ごとに
`volumetric-terrain.md`のordered CSG doctrineと同じ責務分離で適用する。

1. `CARVE_SOLID` — `[bedY+1, surfaceY]`をvoid化。solid massのみを除去し、fluidを作らない。
2. `ADD_FLUID` — そのvoidの内側`[bedY+1, waterSurfaceY]`へwater。fluid所有はこちら側のみで、solidを置換しない。

bed block自身とその下はcoastal surface resolverの所有のまま（bed材質の再ライニングをしない）。

宣言interactionとfail-closed rule id:

| Interaction | 内容 | rule id |
|---|---|---|
| `SOURCE_TERMINUS` | 実体化bedはreconcile済みbed。plan値との乖離は拒否 | `v2.river.route-not-frozen` |
| `MOUTH_TERMINUS` | v1のmouthは内陸終端。海へのjunctionはdelta／estuary leafの責務 | `v2.river.marine-contact` |
| `BANK_ENVELOPE` | channel外周はcarve／fill対象外で、water surface以上に立つ | `v2.river.leak-envelope` |
| `COASTAL_FOUNDATION` | channelはmacro foundation背景のみ。coastal modifier所有cellとの重なりは拒否 | `v2.river.coastal-owner-conflict` |
| `MACRO_MEDIUM` | land-water mediumはHARD mask所有のまま（ADR 0038） | `v2.river.marine-contact` |

加えて`v2.river.vertical-bounds`（bedはbedrock床より上・carve topはmaxY以下）と
`v2.river.materialization-budget`（bounded footprintはMEDIUM上限1024²cell、AABB境界へchannelが
達したら拒否）を持つ。**riverはland-water fieldを書かない**ため、macro foundation owner gate、
EDGE評価、beach↔backshore連続性、breakwater arm測定はいずれも影響を受けない。

### 2.3 fixture

- `harbor-cove-64-honored-river`のreachを短縮した（3点目 `(0.17, 0.20)` → `(0.16, 0.15)`）。
  従来のreachはbeachのbackshore band（owner index 4、z≧14）へ食い込み、
  `v2.river.coastal-owner-conflict`で拒否される。coastal modifier上を無音で掘らないという契約を
  fixture側で満たす形にした。
- `harbor-cove-64-honored-meander`（新規、request＋intent）を追加した。同じreach geometryを
  `MEANDERING_RIVER` kindで宣言する。V2-15-10はRIVERをMEANDERING_RIVERのcompile pathへbridgeするため
  両kindは1つのplan shapeを共有するが、表示（`public-dispatch-reachability-v1`）はkindごとの実測を
  要求するので、**片方から他方を推論せず**2 caseを持つ。

## 3. 実測（2026-07-23、公開Releaseから）

### 3.1 block effect（`FeatureMaterializationV2`）

baseline `harbor-cove-64-honored` に対して、river／meander両caseとも同一:

| 値 | 実測 |
|---|---|
| comparedCells | 167,936（64×64×41） |
| changedCells | 575 |
| solidShapeChanges | 458 |
| fluidChanges | 117 |
| materialChanges | 0 |
| observed effect classes | `{SOLID_SHAPE, FLUID}` ＝ 宣言effect class |

宣言canonical field: `hydrology.river.channel-mask`／`hydrology.bed.elevation`／`hydrology.water.surface`。
`requireMaterialized`は宣言と実測の**完全一致**を要求するため、`MATERIAL`を宣言に足しても落ちる。

### 3.2 shape conformance（`RiverBlockConformanceV2`、final tile streamのみ）

| 測定 | 結果 |
|---|---|
| bed depth | 全channel列がbed solid＋宣言water depth（1 block）＋上方は空（`channelCells == channelCellsAtDeclaredBedDepth == channelCellsOpenAbove`） |
| water continuity | XZ 4連結の water component＝1（段差bedのため3D面連結は2.5D reachの正しい問いではない） |
| source→mouth reachability | 宣言source cellから宣言mouth cellへ到達 |
| leak envelope | `leakCells = 0`（channel waterがchannel外のairへ接しない）、外周列は全てwater surface高でsolid |

### 3.3 表示軸

`public-dispatch-reachability-v1`の`RIVER`／`MEANDERING_RIVER`を`PLAN_ONLY`→`MATERIALIZED`へ移し、
[registry](../current-feature-state-machine-registry.md) の CI検査済みprojectionを更新した
（canonical projection SHA-256 `e092326b…` → `0516e59c…`）。support列とPaper到達性は別軸のままで、
両kindの`paper_apply`は`EXPERIMENTAL`を維持する（昇格はV2-17の実機証跡のみ）。

## 4. test

| test | 内容 |
|---|---|
| `RiverBedMaterializationV2Test` | 実routing solver＋実reconcilerでfreezeし、carve>fill／bed非再ライニング／bounded footprint／river無しはfreeze無し、5 negative（coastal owner conflict、marine contact、bed above surface、envelope below water、reconciled bed乖離、routing domain不一致）、宣言interaction契約、locale／timezone非依存 |
| `IntentConformancePortfolioV2Test` | 両river caseのplan-only列（hydrology metrics）とblock列（materialization＋shape conformance）を別々に測定。両kindのblock effect一致と実数pin。plan列がPASSでも空block列はgateが必ず拒否 |
| `FeatureMaterializationGateV2Test` | gate自身（identity stream拒否・宣言不一致拒否）は不変 |
| `MacroFoundationPhaseGateV2Test` | portfolio 4 case（coastal 2＋river 2）でV2-18 Phase gate条件を再検証 |
| `PublicDispatchReachabilityV2Test` | MATERIALIZED表示、3軸独立、docs projection同期 |
| full `./gradlew test` ／ `./gradlew build` | PASS |

## 5. 非Scope

- Paper `SUPPORTED`昇格（V2-17専管）、新Release capability、LARGE。
- 海へのriver mouth junction（delta／estuary leaf）、coastal modifierとのcomposition（`v2.river.*`で拒否したまま）。
- bed／bank材質のprofile駆動化（`V2-19-10`）、HEIGHT_GUIDE標高（`V2-19-06`）、foundation producer tier（`V2-19-07`）。
- hydrology plan／routing／reconciliation artifactの契約変更（既存契約のまま消費するだけ）。
