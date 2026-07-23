# V2-19-07 Foundation producer tier ＋ PLAIN vertical slice

> Status: **PASS（2026-07-24）**。[ADR 0038](../../adr/0038-macro-foundation-contract.md) D1が契約として定義しながら構築箇所ゼロだった`MacroFoundationV2.ProducerLayer`へ、既存`PlainGeneratorV2`（V2-9-02）を最初のproducerとして接続し、candidate→effective owner→surface field→block resolver→published tile→conformance→public dispatchを一気通貫させた。**新ADRもamendmentも不要**（`PLAIN`はADR 0038 D4でNORMATIVE確定済み、公開経路はADR 0039 Candidate A採択パターンのコピー）。**新Schema `$id`・新capability・新artifact format無し**。producer無しrequestのterrain semantic checksumは実測で不変、容器byte（blueprint／manifest checksum）は変化する（§5）。

## 1. 修正前の状態

| 事項 | 修正前 |
|---|---|
| producer tier | `MacroFoundationV2.ProducerLayer`は型だけ存在し、`MacroFoundationStageV2`は常に`List.of()`を渡す。全cellがbackground owner。ADR 0038 D1-1/D1-3（candidate、replacement）は契約のみ |
| foundation owner index | `CoastalSurfaceFieldsV2`が定数1個（background）を返す。cellごとのeffective ownerという概念が実体を持たない |
| `PLAIN`の公開到達性 | dispatch routeなし。`ProductionDispatchRegistryV2.select`が`no production dispatch route: PLAIN`で拒否するため、PLAINを宣言したintentはexportに到達できない（`public-dispatch-reachability-v1`表示は`NOT_PUBLICLY_DISPATCHABLE`） |
| `PLAIN`のmodule binding | diagnostic module（`v2.feature.diagnostic`）。dedicated `LandformPlainModuleV2`はV2-9-02から存在するがcatalog未登録 |
| 標高の実体 | V2-19-06の`HEIGHT_GUIDE`を除き、background elevationはmedium別2定数のみ。地形を「生成する」feature kindは公開経路に1つも無い |
| ADR 0037 adapter | `Release2FoundationSurfaceExportApplicationServiceV2`＋`FoundationSurfaceExportAdapterV2`はtestからのみ呼ばれる。公開到達性は未裁定 |

## 2. 実装

### 2.1 producer tier（ADR 0038 D1）

`MacroFoundationV2.ProducerLayer`へ`ownerId`／`kind`を追加し（fail-closed messageが操作者の書いた宣言を名指しできる）、`MacroFoundationStageV2`が宣言featureからlayerを構築する。

- **wired producer kind:** `MacroFoundationStageV2.WIRED_PRODUCER_KINDS`＝`{PLAIN}`。`HILL_RANGE`／`MOUNTAIN_RANGE`／`VALLEY`は本Taskが確立した機構の上でV2-15対応leaf（`V2-15-22`／`17`／`23`）が追加する。
- **owner index:** background（1）の上に、**feature id昇順**で2,3,…を割り当てる。intent内のfeature並び順に依存しない。
- **replacement:** footprint内はproducerがeffective owner（medium＋base elevation）となり、footprint外はbackgroundのまま。暗黙の優先度比較もlast-write-winsも無い。
- **plan compile:** `PlainPlanCompilerV2`（`core.v2.foundation`）＋`LandformV2DataCodec.sealPlainPlan`で封印し、raster化は既存`PlainGeneratorV2`。**新しいgeneratorは作っていない**。`FoundationSliceException`はrule idを保ったまま`SurfaceFoundationExceptionV2(CONTRACT_VIOLATION)`へ再符号化してexport spineへ渡す。

**elevation datum（明示規則）:** `PLAIN`の`baseElevationAboveDatumBlocks`はrequestの**water levelを datum** として解釈し、surface Y＝`waterLevel + baseElevation + microRelief`とする。ADR 0037 offline adapter（`FoundationSurfaceFieldsV2.absoluteSurfaceY`）は「bounds内なら絶対Y、外ならwater level相対」と値ごとに推測し、収まらない値をclampするが、production tierは推測もclampもせず**契約外を拒否**する。

### 2.2 fail-closed kernel不変条件（ADR 0038 D7-1）

producerがownerとなる全cellで、次を生成時に評価する（`effectiveOwnerIndexAt`が唯一の評価点で、`CoastalSurfaceFieldsV2`が全cell分を先に解決するため、field値を1つも書く前に停止する）。

| rule id | 条件 |
|---|---|
| `UNDECLARED_OVERLAP`（failure code） | 宣言interaction無しにproducerのfootprintが重なる |
| `v2.foundation.producer-mask-medium-conflict` | producerのmediumがHARD land-water maskの宣言と矛盾（maskが正本、ADR 0038 D2-3） |
| `v2.foundation.producer-elevation-out-of-contract` | producerの標高がrequestの`[minY, maxY]`外（clampしない） |
| `v2.foundation.height-guide-producer-conflict` | HARD `HEIGHT_GUIDE`とproducerが同一cellへtolerance超で異なる高さを宣言（AGENTS.md §7のHARD同士矛盾） |

SOFT guideはeffective owner（producer）へ譲り、差はresidualへ記録する。**modifier interaction**はADR 0038 D5-3どおり「modifierが所有するcellの高さはmodifierのもの」で、拒否ではなく宣言された合成である（実測: 本fixtureのmodifier所有列はbaseline Releaseとblock単位で同一、§3）。

### 2.3 surface field → block resolver

`CoastalSurfaceFieldsV2`のfoundation owner indexを**cellごとの配列**へ変え（legacy baseline経路は従来どおり0）、`foundation.owner-index` field namespaceがbackgroundとproducerを区別するようにした。feature非所有cellのland-water／surface heightは従来どおり`MacroFoundationV2.mediumAt`／`elevationMillionthsAt`から取るため、producerの標高がそのまま既存のcanonical block resolver→offline tile→published Releaseへ流れる（新しいblock mapping規則は追加していない）。

### 2.4 public dispatch（ADR 0039 Candidate A のコピー）

`RIVER`（`V2-15-10`）と同じパターンを、pipelineだけ替えて適用した。

1. `LandformPlainModuleV2`（V2-9-02、EXPERIMENTAL）をbuilt-in catalogへ登録し、`PLAIN`のmodule bindingをdiagnostic→dedicatedへ移す（dedicated 17→18、diagnostic 43→42）。stageは`generate.foundation-plain`（依存＝`compile.inputs`）。
2. moduleが宣言する4 field（`foundation.plain.mask`／`base-elevation`／`micro-relief`／`groundwater-handoff`）へblueprint field descriptor＋ownershipとSchema enum値（`FOUNDATION_PLAIN_*`）を追加。
3. Feature Support Catalogの`PLAIN`を`intent_compile`／`export` PARTIAL→**SUPPORTED**（ADR 0039 Decision A-2が同一leafでの昇格を明示許可）。`standalone_usage`はPARTIALのまま、`requiredReleaseCapability`は空のまま、**Paper 5列はUNSUPPORTEDのまま**（V2-17専管）。sealed example再生成。
4. `ProductionDispatchRegistryV2`へ`PLAIN`の`OFFLINE_PRODUCTION` routeを追加（pipelineは`v2.production.surface-2_5d.coastal`。coastal pipelineの`executableKinds`にPLAINを追加し、`PRODUCTION_CONNECTED` routeはcoastal 4のexact coverを維持）。registry checksum `2fdb87e6…`→`182f713e…`。
5. `public-dispatch-reachability-v1`で`PLAIN`＝`OFFLINE_PRODUCTION`＋`MATERIALIZED`（projection SHA `0516e59c…`→`7d46e315…`）。

`PRODUCTION_CONNECTED`の意味（Paper込み完全接続＝coastal 4）とPaper `SUPPORTED` exact setは不変である。

### 2.5 foundation adapter（test-only）の到達可能性 — 裁定

**裁定: ADR 0037 adapterは公開dispatchへ載せない。** `FoundationSurfaceExportAdapterV2`／`Release2FoundationSurfaceExportApplicationServiceV2`はV2-9／V2-10 merge出力をoffline `surface-2_5d` Releaseへ射影する**非公開・test-only経路**として維持し、第二の`["surface-2_5d"]` production pipelineにしない（ADR 0039凍結4）。foundation kindの公開経路は本Taskが確立した**coastal surface pipeline内のproducer tier**である。`MacroFoundationProducerV2Test.theOfflineFoundationAdapterStaysOutsidePublicDispatch`がこの裁定を実行可能に固定する（adapter idはdispatch routeに現れない／`PLAIN`のrouteはcoastal pipelineの`OFFLINE_PRODUCTION`）。両者のelevation datum解釈の違いは§2.1に明記した。

## 3. 実測（公開Releaseから）

fixture `harbor-cove-64-honored-plain`＝`harbor-cove-64-honored`＋`PLAIN` 1件（`inland-plain`、内陸polygon x 0.20–0.60／z 0.03–0.14、base elevation range 5–7＝midpoint 6、micro relief 1–3、groundwater 2–4）。land-water maskはbyte同一。

| 測定 | 値 |
|---|---|
| block効果（baseline `harbor-cove-64-honored`比、final canonical block stream） | changed **1238** ＝ `SOLID_SHAPE` **713**／`MATERIAL` **525**／`FLUID` **0** |
| 宣言effect class | `{SOLID_SHAPE, MATERIAL}` — 実測と完全一致（`FeatureMaterializationV2.requireMaterialized`） |
| producer footprint（published streamから） | 175 cell、全て macro foundation background（modifier重複0） |
| surface Y分布 | 57: 54 cell／58: 54 cell／59: 67 cell（＝water level 50＋base 6＋micro 1..3、宣言bandを全域が満たす） |
| bounded replacement | footprint外のbackground land列は宣言`landSurfaceY`＝54、background water列は`waterBedY`＝46（全数一致） |
| tier分離 | modifier所有列の変化 **0**（producerはmodifierの高さを上書きしない） |
| plan-only metricとの分離 | portfolioのblock欄は`FeatureMaterializationV2`のみが埋める。plan artifactは代替にならない（V2-19-01） |
| 実CLI E2E | `v2 export <plain request> <plain intent> …`が`requiredCapabilities: [surface-2_5d]`／`placementEligible: true`でdirectory＋ZIPを公開（`LandformCraftCliV2Test`） |

portfolioの既存shape assertion（EDGE HARD、beach↔backshore連続性、両arm landfall、locale／timezone非依存、published Releaseのみを入力）はPLAIN caseでも全て成立し、mask由来の値はbaselineと同一である。

## 4. negative fixture

| 事象 | 経路 | 結果 |
|---|---|---|
| producer footprint同士の重なり | 実export（2つ目のPLAINを追加） | `UNDECLARED_OVERLAP`でexport拒否、artifact未生成 |
| producerが宣言water上へland | 実export（polygonを南側外洋へ） | `v2.foundation.producer-mask-medium-conflict`で拒否、artifact未生成 |
| producer標高がbounds外 | unit（`MacroFoundationStageV2Test`） | `v2.foundation.producer-elevation-out-of-contract`（clampしない） |
| HARD guideとproducerの高さ矛盾 | unit | `v2.foundation.height-guide-producer-conflict`（SOFTはproducerへ譲りresidual化） |
| PLAIN単独intent | 実export | coastal 4種必須により拒否（`standalone_usage`がPARTIALである理由。緩和は`V2-19-09`） |
| 未配線のfoundation-eligible kind（`PLATEAU`） | dispatch | `no production dispatch route: PLATEAU`（silent dropしない） |

drift guard: `WIRED_PRODUCER_KINDS`は「公開routeを持つfoundation-eligible kind」と双方向一致すること、profileが`FOUNDATION` stageを持つことをtestで固定する（片方だけ増やすとplan-only配線になるため）。

## 5. 凍結・変化の宣言

| 項目 | 本Task |
|---|---|
| terrain semantic checksum（producer無しrequest） | **不変**。`harbor-cove-64-honored`のtile semantic checksum `20318e6c…`はV2-19-07直前treeの実測値と一致（`MacroFoundationProducerV2Test`がpin） |
| 容器byte | **変化**。plain moduleとその4 field descriptorが全blueprintへ入るため、blueprint canonical checksumとmanifest checksumが変わる（ADR 0038 D9の可変域）。`harbor-cove-64-honored`のmanifest `9832cd4d…`→`4456ae84…`として再pin |
| Release format／capability | 不変（`surface-2_5d`のまま、新capabilityなし） |
| Paper | 不変（`paper_apply` SUPPORTED exact set＝coastal 4、PLAINはUNSUPPORTED） |
| v1 golden／Release 1 | 不変 |
| Schema | `world-blueprint-v2`のfield semantic enumへ`FOUNDATION_PLAIN_*` 4値を**追加**（`$id`・既存値は不変）。`terrain-intent-v2`は不変 |
| sealed catalog | `PLAIN` entryのみ変更（`intent_compile`／`export` SUPPORTED、evidence／notes）。example再生成 |

## 6. 検証

- `./gradlew test` ／ `./gradlew build --rerun-tasks`: PASS（228 test class／1347 test／11 skip／0 failure。skipは全て手動再生成helperと実機opt-in fixture）
- 新規／更新した主なtest: `MacroFoundationStageV2Test`（producer tier unit＋negative）、`MacroFoundationProducerV2Test`（export negative、semantic checksum不変、adapter裁定、drift guard）、`IntentConformancePortfolioV2(+Test)`＋`PlainBlockConformanceV2`（block実測）、`ProductionDispatchRegistryV2Test`／`PublicDispatchReachabilityV2Test`／`CurrentFeatureStateRegistryV2Test`（公開到達性）
