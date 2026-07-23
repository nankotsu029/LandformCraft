# V2-19-06 HEIGHT_GUIDE macro foundation consumer

> Status: **PASS（2026-07-23）**。[横断監査 rev.2](../../audits/cross-cutting-audit-2026-07-23.md) §2 item (4)が確定した「`HEIGHT_GUIDE`の生成側consumer不在＋『constraint mapは正確に1枚』制約で、macro foundationの標高がland/water 2定数flatに固定される」欠陥を解消した。[ADR 0038](../../adr/0038-macro-foundation-contract.md) D2-2が既に許可している明示source (a) の実装であり、**ADR amendmentは不要**（D2-2の範囲内）。新Schema・新capability・新artifact format無し。guide無しrequestの公開byteは不変（実測、§4）。

## 1. 欠陥（修正前）

| 事項 | 修正前 |
|---|---|
| background elevation | `MacroFoundationV2`が`FoundationBaseLevels`の2定数（land／water）だけを返す。宣言された`HEIGHT_GUIDE`は読まれない |
| constraint map cardinality | `CoastalSurfaceExportPipelineV2`が request source **ちょうど1件**（`constraintMaps().size() != 1`）とintent binding **ちょうど1件のLAND_WATER_MASK**を要求。`V2-19-04`で宣言・bindingは作れるようになったが、guideを含むrequestは**exportに到達できない** |
| desired／actual／residual height | `maskDesiredSampler`がsurface heightに`NO_DATA`を返すため、`coastal.height.residual-max`は構造的に常に0。公開Releaseにheight sidecarが存在しない |
| conformance | `ConformanceTargetSetV2.from`が`LAND_WATER_MASK`以外のbindingをskipし、`HEIGHT_GUIDE`は`RasterResidual`も`UnconsumedTarget`も生成しない（存在しない） |
| CLI表示 | `generatorConsumer: NONE`（正直な表示だが、能力が無いという事実そのもの） |

## 2. 実装

### 2.1 background elevation source（優先順位・no-data・out-of-contract）

`MacroFoundationV2.HeightGuideV2`（bound field＋strength＋tolerance＋request vertical contract）を追加し、background ownerのelevationを次の順で決める（ADR 0038 D2-2）。

1. **guideがcellの値を宣言している** → その値（block-millionths）をそのまま使う。
2. **guideがno-dataを宣言している／guide自体が無い** → request宣言の medium別 base level（`foundationBaseLevels`）へfallback。

逆転・blend・平均は行わない。mediumは常にmaskのものであり、guideはmediumへ寄与しない。

**out-of-contract:** guide値がrequestの`[minY, maxY]`外ならclampせず`SurfaceFoundationExceptionV2(CONTRACT_VIOLATION)`＋rule `v2.foundation.height-guide-out-of-contract`。ただし主gateは既存の宣言時検査で、`GenerationRequestV2`が`validSampleRange`×scale＋offsetの全域がbounds内に入ることをrequest構築時に検査する（"height encoding output is outside request bounds"）。consumer側の検査は直接構築されたfoundation（将来のproducer tierを含む）に対する**defence in depth**である。

**resampling:** 新しい補間規則は追加しない。canonical登録（`ConstraintMapSamplerV2`の`NEAREST`／`BILINEAR_FIXED`、integer固定小数）がそのまま担う。

**budget:** 新しいhard-codeを追加しない（ADR 0038 D6）。各mapは従来どおり`ConstraintMapDecodeLimits.defaults()`（source byte／寸法／aspect／pixel／decoded sample／working byte）で受理し、宣言集合はrequest構築時に`constraintMapBudget`（count／pixels／decoded bytes）が既に制限する。foundationが保持するdecoded rasterは最大2枚で、常駐は既存decoded-sample上限の2倍で抑えられる。

### 2.2 role別cardinality（「正確に1枚」の緩和）

`MacroFoundationStageV2.FoundationInputRolesV2`がintentのmap bindingをrole別に分類する。

- `LAND_WATER_MASK`: **ちょうど1件**、HARD必須（従来どおり）。
- `HEIGHT_GUIDE`: **0または1件**、strengthはHARD／SOFTどちらでもよい。
- `ZONE_LABEL_MAP`: consumerが無いため**拒否**（受理して無視しない）。

さらに export は「宣言済みsourceの集合＝consumeしたsourceの集合」を要求する（公開Releaseの`verifyIntentBindings`が両者の一致を検査するため、bindingの無い宣言はartifactとして検証できない）。legacy baseline経路（foundation入力なし）はV2-19-06以前の「宣言1件・LAND_WATER_MASK binding 1件」をそのまま維持する。

### 2.3 desired／actual／residual heightの入力guide束縛

guideが在るrunだけ、既存のland-water 3種と同じ形で height sidecarを追加する（field index契約の`HEIGHT_GUIDE` shapeは`ConstraintFieldIndexV2`に既存）。

| field | 内容 |
|---|---|
| `constraint.coast.height.desired`（`DESIRED_HEIGHT`、I32、scale 1） | 解決済みguide値（no-data cellは`Integer.MIN_VALUE`） |
| `constraint.coast.height.actual`（`ACTUAL_HEIGHT`） | 公開Releaseが書き出した合成surface height |
| `constraint.coast.height.residual`（`RESIDUAL_HEIGHT`） | `actual - desired`（land-water residualと同じ向き）。desiredがno-dataなら`Integer.MIN_VALUE` |

`inputDesiredSampler`がsurface height fieldにguide値を答えるようになったため、`coastal.height.residual-max`は構造的に0の自己参照ではなく**実測**になる（監査item 3の残り半分。metric自体の期待範囲は従来どおり無制限＝report-only）。

### 2.4 conformance `RasterResidual`の有効化

`ConformanceTargetSetV2`が`HEIGHT_GUIDE` bindingにもdesired raster targetを作る（field id `intent.height-guide`、`ZONE_LABEL_MAP`は引き続き対象外）。provenanceは従来どおり宣言digestと一致する場合だけ`INPUT_MASK`である。`ConformanceResidualEvaluatorV2`は変更していない（targetのfield idで測る設計だったため）。

### 2.5 modifierとの責務分離（HARD衝突のfail closed）

surface modifierは自分が主張するcellの高さを所有する（ADR 0038 D5-3）。したがって

- **SOFT guide**: modifier所有cellではmodifierが勝ち、その差はresidual sidecar／conformance residualへ記録される。
- **HARD guide**: modifier所有cellをtolerance超で指定していれば、HARD同士の矛盾（[AGENTS.md §7](../../../AGENTS.md)）として`v2.foundation.height-guide-modifier-conflict`でexport拒否する。

## 3. Example fixture

`harbor-cove-64-honored-guided`（request／intent／`maps/harbor-cove-64-honored-height-guide-u8.png`）を追加した。land-water maskは`harbor-cove-64-honored`とbyte同一で、guideだけが異なる。

- guideは committed maskから導出（`HeightGuideExampleFixtureV2`が正本、testがpixel一致を検査）。land側は北へ54→57の4段terrace、water側は南へ46→43の4段terrace、南西角4×4は**no-data**（fallback経路の実証）。
- encoding: U8 grayscale、`ABSOLUTE_BLOCK_Y`、scale 1 block／sample、offset 0、valid sample範囲40..60、no-data sentinel 255。
- binding: **SOFT**（tolerance 2、weight 0.5）。coastal 4種がcellを所有するため、HARD宣言は§2.5により拒否される。

## 4. 検証記録（2026-07-23）

| test | 内容 |
|---|---|
| `MacroFoundationStageV2Test`（新規4） | guide＞base levelの優先順位（4032 cell）とno-data fallback（16 cell）、mediumがguideに影響されないこと、out-of-contract値の拒否（rule id照合）、role cardinality拒否（2件目のHEIGHT_GUIDE／ZONE_LABEL_MAP）、guided pathのlocale／timezone／thread決定性 |
| `HeightGuideExampleFixtureV2Test`（新規2） | committed PNGが文書化したsampleへ復号すること、no-data patch 16 cell、valid range内、requestの digest／寸法／encodingとintent bindingが同じmapを指すこと |
| `HeightGuideMacroFoundationV2Test`（新規4） | 公開Releaseがmapごとに1 binding＋3 sidecar（計2 binding／6 field）を持ちartifactIdが宣言digestを指すこと、**guide無しfixtureのmanifest checksumがV2-19-06直前の実測値と一致**（`9832cd4d…`、§4末尾）、HARD guide×modifier衝突の拒否と公開物を残さないこと、宣言のみでbindingの無いsourceの拒否 |
| `IntentConformancePortfolioV2Test`（case追加＋新規2） | `harbor-cove-64-honored-guided`を常設caseへ追加（EDGE／beach continuity／arm landfall／locale・timezone決定性／published-only測定の全既存assertionを通過）。公開sidecarからbackground cell 3122がguideを厳密に再現、no-data 16 cellがbase levelへfallback、modifier所有955 cellだけが差分であることを実測。`ConformanceResidualEvaluatorV2`が land-water（compared 4096／mismatch 0）とheight（compared 4080／mismatch 955）の2 `RasterResidual`を返し、`UnconsumedTarget`が出ないこと。guided Releaseとunguided baselineのfinal canonical block streamのdiff（changed 13289＝solid 3103／fluid 4940／material 5246）でguideがblockまで届くことを実測 |
| `LandformCraftCliV2Test`（新規1、実CLI E2E） | extract→promote（land-water／height-guide）→`request constraint-source`×2→`intent bind`×2（`generatorConsumer: MACRO_FOUNDATION`）→`v2 export`（`placementEligible: true`）をCLIだけで通過 |
| suite | `./gradlew test` PASS（§5）、`./gradlew build` PASS |

**byte不変の実測方法:** V2-19-06着手直前のtree（HEAD `3a495c0`のdetached worktree）で`harbor-cove-64-honored`を公開exportし、manifest canonical checksum `9832cd4dca6360b6e3b81b4850693077a5d90841ddf983abfbf572ad62d0e28b` を得た。同じ値を`HeightGuideMacroFoundationV2Test`がpinしており、現行treeのexportが一致する。guide無しrequestのfield／tile／block semantic checksum、manifest容器byteはいずれも不変である。

## 5. Scope外／不変

- **Scope外:** foundation producer tier（`V2-19-07`）、`ZONE_LABEL_MAP` consumer、mask⇔feature SOFT reconcile（`V2-19-14`）、coastal 4種必須の緩和（`V2-19-09`）、`FoundationSurfaceExportAdapterV2`（plain/hill経路）のmap cardinality。後者はmacro foundation stageを走らせないためguideのconsumerを持たず、「宣言済みsource ちょうど1件」の従来要求を維持する（guideを受理して無視することはしない）。
- **不変:** Schema（request／intentは既に複数map・3 roleを表現できていた）、Release format／capability、artifact type／version、v1資産・golden、既存coastal Release（`harbor-cove-64-honored`／`coastal-honored-400`／river 2件）のsemantic checksum、`public-dispatch-reachability-v1` projection。
- `HEIGHT_GUIDE`はbackground elevationの入力であり、feature geometryやland-water境界の推測入力ではない（ADR 0038 D2の禁止事項は不変）。
