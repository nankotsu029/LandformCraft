# LandformCraft 横断監査・独立レビュー

Date: 2026-07-23

Revision: `f3588f6` (`main`)

Status: COMPLETE WITH FINDINGS

Scope: 現在の実装、Schema、地形データモデル、自然言語・画像入力、compile、generator、validation、preview、Release、Paper placement、test、ADR、roadmap、Task、既存監査

Change policy: production code、Schema、example、roadmap、Task Indexは変更していない。本書のみを追加した。

## 1. 結論

既存の `cross-cutting-audit-2026-07-23.md` の中心命題、すなわち「決定性、fail-closed、Release検証、配置安全性の基盤に対して、最終blockへ実体化される地形表現力が著しく弱い」は正しい。

ただし、既存報告は画像入力経路を実態より完成に近く評価している。現在の問題は `HEIGHT_GUIDE` のconsumer不在だけではない。

1. 公開design経路は、requestにreference imageが1枚でもあると必ず失敗する。providerへ画像を渡す部品はあるが、公開Application Serviceが常に空の画像listを構築するためである。
2. CLIの画像抽出E2Eは、`LAND_WATER_MASK`についてもsource宣言までであり、Intent側のbindingは事前作成済みfixtureに依存する。`HEIGHT_GUIDE`と`ZONE_LABEL_MAP`は抽出・昇格後に公開requestへ宣言する経路すらない。
3. production surfaceは、HARD `LAND_WATER_MASK`とland/water別の2定数からなるmacro foundationに、coastal 4種の局所modifierを重ねる。feature非所有領域に連続的な大域標高場はない。
4. `RIVER`のoffline routeはrouting、reconciliation、validation、preview artifactを作るが、surface tileを書き直さない。Minecraft block streamはriverを宣言しないcoastal Releaseと同じ経路で作られる。
5. `V2-15-10`のper-leaf conformanceは、RIVERについて最終block streamではなく `hydrology/validation.json` を測る。このAcceptanceをそのまま `V2-15-11`以降へコピーすると、「planとmetricは存在するがMinecraftには現れない」Featureを正規routeとして増やせる。

したがって、現時点で「自然言語や画像に近いリアルな複合地形を生成できる」とは評価できない。正確な表現は次のとおりである。

> LandformCraftは、安全に検証・運搬・配置できる強いproduction基盤と、多数の型付き地形plan／generator sliceを持つ。しかし公開production経路でblockへ実体化される地形は、2値macro foundationとcoastal 4種にほぼ限られる。自然言語と画像の入力能力、既存plan群、最終block materializationの間には複数の未接続境界がある。

## 2. 監査方法と評価基準

文書上の `SUPPORTED` や完了Taskをそのまま能力認定には使わず、次の証拠を順に追跡した。

1. CLI／Paperから到達するApplication Service
2. Request／Intent SchemaとJava record
3. provider request、画像準備、Intent publish
4. Blueprint compile、composition profile、production dispatch
5. field生成、tile block resolver
6. validation／previewが読む実データ
7. Release publishとstrict read-back
8. ReleaseからPaper world mutationまで
9. positive／negative／determinism／phase-gate test
10. ADR、roadmap、Task Index、既存監査とコードの一致

能力分類は以下を用いる。

| 分類 | 意味 |
|---|---|
| PUBLIC_PRODUCTION | CLIまたはPaperから到達し、対象意味が最終blockへ現れ、公開Release検証と対応testがある |
| PUBLIC_OFFLINE | CLIからRelease生成まで到達するが、Paper `SUPPORTED`ではない |
| INTERNAL_SLICE | generator／plan／Application Serviceはあるが公開workflowから到達しない |
| CONTRACT_ONLY | Schema、record、codec、fixture、validator等だけがある |
| BROKEN_PUBLIC | 公開入口は見えるが、正常な入力で途中の契約上必ず失敗する |
| PLANNED | docs／roadmap／Taskだけで、現在の実行経路はない |

「汎用placement engineがある」ことと「そのFeatureが意味どおりblock化されPaper対応済みである」ことは分離した。

## 3. 既存報告のファクトチェック

| 既存の主張 | 判定 | 確認結果 |
|---|---|---|
| macro foundationはland/waterの2定数で、producerは空 | 確認 | `MacroFoundationStageV2`は `new MacroFoundationV2(..., List.of())` を返す。background elevationは `MacroFoundationV2.elevationMillionthsAt` がland/water別定数を返す |
| `HEIGHT_GUIDE`に生成側readerがない | 確認 | production main sourceでの参照は抽出・昇格・field契約・verifyに限られ、macro elevation consumerはない |
| 高低差契約自体は存在する | 確認 | requestのY範囲、height encoding、fixed-point、field sidecarは大きな標高差を表現できる。制約はconsumerである |
| RIVERはblockを変えない | 確認 | hydrology pipelineは先にcoastal surface source／tileを生成し、後段でrouting／validation／previewを追加する。返すRelease sourceも `surface.source()` を再利用する |
| per-leaf義務にblock materializationがない | 確認 | `V2-15-10`のportfolioはRIVERについて `hydrology/validation.json` の3 metricを読むが、最終tile／block stream上のriver bedやwaterを検査しない |
| 画像入力側は完成しておりconsumerだけが律速 | 反証 | reference-image public designはBROKEN_PUBLIC。constraint mapもIntent bindingを公開authoringできず、HEIGHT／ZONEはrequest source宣言へ進めない |
| CLI画像E2Eは抽出からproduction exportまで完全に接続 | 部分的 | `LAND_WATER_MASK`の抽出物はrequest sourceになる。ただしtestは事前に `mapReferences`を持つIntent fixtureを渡しており、source→Intent bindingはE2Eで生成していない |
| repository全体にnoise／fractal生成が全くない | 要修正 | coherentなmulti-scale noise、terrain erosion kernelは確認できない。一方、非公開plain sliceにはcell hashによるmicro relief、個別generatorには解析形状・hash variationがある。「一切ない」ではなく「公開productionにcoherent detail kernelがない」が正確 |
| block paletteは9種類 | 要修正 | hard-codeされたresolverの候補はair／bedrockを含め11 block state。実sampleの中央tileも `paletteSize: 11`。9はair／bedrockを除いた数としてのみ成立する |
| coastal contributorは1種以上で動くため、docsの「4種必須」はstale | 反証 | `CoastalGeneratorRuntimeV2.create` は4種すべてを `REQUIRED` とし、不足を明示拒否する。docs側が正しい |
| full testの失敗は未登録sample 2ファイル | 要修正 | 失敗testは1件である点は正しいが、failureが列挙する未参照documentは `shore-2to1-400.terrain-intent-v2.json` 1件のみ。requestはfailure listにない |
| environment／sparse／foundation adapterは公開workflowから未到達 | 確認 | main sourceで各Application Serviceのcallerはなく、対応testからのみ直接生成される。公開 `V2WorkflowServiceV2` が保持するexport serviceはsurfaceとhydrologyだけ |
| `current-limitations`等に進捗staleがある | 確認 | `implementation-roadmap.md`は次Taskを `V2-15-05`、`migration-plan.md`は `V2-12-07` と記載する一方、正本roadmapの次Taskは `V2-15-11` |
| `HEIGHT_GUIDE`接続にはADR 0038 amendmentが必要 | 要修正 | ADR 0038 D2はelevation sourceとして `HEIGHT_GUIDE`またはmedium base levelを既に許可する。優先順位、no-data、budget等を現契約内で具体化できる限り、接続そのものにamendmentは必須ではない |

既存報告にない最重要の追加発見は、reference-image design経路の必然的失敗と、image extraction E2EがIntent bindingを生成していないことである。

## 4. 入力からMinecraftまでの実経路

### 4.1 自然言語request

画像を持たないrequestは、CLI／Paperの `design` から `TerrainDesignApplicationServiceV2`を通り、OpenAI／Anthropic provider adapterまたはfixture／importへ到達できる。構造化IntentのSchema検証、retry／audit、strict publishの境界は実装されている。

ただし、provider promptに渡すrequest要約はrequest ID、bounds、自由文promptだけである。constraint mapのsource ID、role、strength、binding候補はprovider promptに含まれない。provider Schemaはhistoric `FeatureKind`全体を受理する一方、design時点にproduction dispatch reachability lintがない。このためproviderが妥当なIntentを返しても、export時に初めて `feature kind has no production dispatch route` で拒否され得る。

評価: 自然言語→Intentは画像なしならPUBLIC_PRODUCTION相当。ただしIntent→実地形のsupport範囲がdesign契約へ反映されていない。

### 4.2 reference image

`TerrainDesignApplicationServiceV2.buildProviderRequest` は、requestの `referenceImages`数に関係なく `List.of()` をprovider requestへ渡す。`TerrainDesignRequestV2`は宣言image数とprepared handle数の完全一致をconstructorで要求する。

したがってreference imageが1枚以上あるrequestはprovider呼出し前に失敗する。`PreparedReferenceImageV2`をmain sourceで構築するcallerはなく、testだけが直接構築している。provider adapterが画像byteを送信できることは、このpublic orchestration欠落を補わない。

さらに、公開 `V2WorkflowServiceV2.designPathKinds()` は `import`、`fixture`、`openai`、`anthropic`だけを公開し、Application Service内に存在するmanual constraint／reference image draft pathを公開しない。4画像を宣言する `oblique-multi-view.request-v2.json` の実画像4ファイルもrepositoryには存在せず、Schema load test以上のevidenceにならない。

評価: BROKEN_PUBLIC。

### 4.3 決定論的画像抽出とconstraint map

CLIは `LAND_WATER_MASK`、`HEIGHT_GUIDE`、`ZONE_LABEL_MAP`のextract→draft→explicit promoteを持つ。decode budget、digest、dimension、no-data、promotion record、strict codecはよく設計されている。

公開authoringの接続状態はroleごとに異なる。

| Role | Extract | Promote | Request source宣言 | Intent binding | Generator consumer |
|---|---:|---:|---:|---:|---:|
| LAND_WATER_MASK | あり | あり | あり。ただしexactly 1、U8 gray、0/1固定 | 自動生成なし。事前作成Intentまたは非公開manual pathが必要 | macro foundationであり |
| HEIGHT_GUIDE | あり | あり | 公開CLIなし | 公開authoringなし | なし |
| ZONE_LABEL_MAP | あり | あり | 公開CLIなし | 公開authoringなし | なし |

`V2RequestStoreV2.constraintMap`は既存map listを `List.of(source)`で置換し、land/water固定encodingだけを作る。Schemaは複数mapを表現できるが、公開surface authoringは複数mapを作れない。

CLI test `extractPromoteDeclareAndExportRunsTheWholeImageFidelityChain` は抽出mapをrequestへ宣言しproduction exportまで到達する。しかしexportへ渡す `harbor-cove-64-honored.terrain-intent-v2.json` は、最初から `constraint-source:coast-mask`への `mapReferences`を持つ。このtestはsourceからbindingを生成していない。HEIGHT／ZONEのtestはpromoted PNG生成で終わる。

`ImageFidelityPhaseGateV2Test`自身もextractを「SUPPORTED candidate、EXPERIMENTAL／unwired」と明記している。`MultiSourceReconciliationServiceV2`のproduction callerはなく、testとpreview rendererだけが参照する。

評価: LAND_WATER_MASKはPARTIAL、HEIGHT_GUIDE／ZONE_LABEL_MAPはINTERNAL_SLICE。

### 4.4 Intent、Blueprint、composition

historic `TerrainIntentV2.FeatureKind`は60 kindを持ち、Schema／enum／catalog／module projectionのexact coverをCIで検査する。relation、HARD conflict、typed parameters、canonical checksum、module／stage／field ownershipの契約は強い。

`CompositionProfileRegistryV2`にfoundation producer／surface modifier／volume／fluidのstage契約を導入した方向は妥当である。ただし大半はPROVISIONALであり、profile登録はblock materializationを意味しない。

`WorldBlueprintV2`は現在、generic `featurePlans`に加えて17個の型別plan listと複数のsingleton planを直接record fieldとして持つ。strictで安全だが、新family追加時にrecord、constructor validation、codec、Schema、canonicalization、test fixtureの広い同時変更を要求する。型安全を保ちながらも、closed typed plan envelope／registryへ段階的に集約する余地がある。

評価: contract／governanceは強い。拡張時の変更面積は大きい。

### 4.5 production dispatch

`ProductionDispatchRegistryV2.builtIn()` が実際にrouteを作るkindは次の6個だけである。

- `PRODUCTION_CONNECTED`: `SANDY_BEACH`、`HARBOR_BASIN`、`BREAKWATER_HARBOR`、`ROCKY_CAPE`
- `OFFLINE_PRODUCTION`: `RIVER`、`MEANDERING_RIVER`

他kindはpipeline descriptor上のcontract-only fixtureとして存在しても、public feature routeではない。routeなしkindはartifact生成前にfail closedで拒否される。

Feature Support Catalogには `export: SUPPORTED`のkindがこれより多く存在する。これはplan-level／shared capability supportを含むため、公開dispatch reachabilityと同義ではない。利用者とproviderにはこの差が見えにくい。design lintはcatalogだけでなく、選択予定capabilityに対するdispatch registryの到達性を使う必要がある。

### 4.6 macro foundationとcoastal surface

production foundation stageは、HARD `LAND_WATER_MASK`、`foundationBaseLevels.landSurfaceY`、`waterBedY`を読み、producer listを常に空で作る。

feature非所有cellの標高は次の2値である。

```text
mask == LAND  -> landSurfaceY × 1,000,000
mask == WATER -> waterBedY × 1,000,000
```

coastal featureがactiveな局所領域だけは、beach、harbor basin、breakwater、rocky capeのanalytic fieldが標高とmediumを変更する。runtimeは4 contributorすべてを必須とするため、単一beachだけをpublic surface exportすることも現在はできない。

`shore-2to1-400` sampleは `landSurfaceY=54`、`waterBedY=42`、`waterLevel=50`である。実際の `actual-height.png` は北側mainlandと南側seabedがそれぞれ広い単色領域になり、局所変化はcoastal feature周辺に集中する。これはpreviewの見た目だけでなく、上記production codeから決定的に説明できる。

block resolverのmaterial候補は以下の11種類に固定される。

```text
air, bedrock, stone_bricks, gravel, stone, water,
cobblestone, sand, sandstone, grass_block, dirt
```

environment側が作るpalette／material planはこのresolverに接続されない。

評価: coastal 4種の組合せだけPUBLIC_PRODUCTION。大域foundationは2値flat。

### 4.7 foundation generator slice

`PlainGeneratorV2`、HILL系等のfoundation generator／planと `FoundationSurfaceExportAdapterV2`は存在し、testからReleaseを生成する。plainにはcell hash由来の小さなmicro reliefもある。

しかし `Release2FoundationSurfaceExportApplicationServiceV2`のmain source callerはなく、`V2WorkflowServiceV2`、CLI、Paperの公開経路に登録されていない。macro foundationのproducer layerにも接続されない。

評価: INTERNAL_SLICE。現在のMinecraft生成能力には算入しない。

### 4.8 RIVER／hydrology

hydrology pipelineは次の順で動く。

1. coastal pipelineでsurface fieldとtileを生成
2. hydrology plan、routing、reconciliationを生成
3. hydrology field samplerをvalidation／previewへ渡す
4. Release sourceへhydrology artifactを追加
5. base terrainとして、coastal fieldだけを読む `CoastalSurfaceTerrainQueryV2`を返す

river fieldを使ってbedをcarveし、fluidをfillし、surface tileを再生成する処理はない。

`V2-15-10` conformance testが確認するのは、公開Release内 `hydrology/validation.json` のchannel gap、reverse gradient、source-mouth reachabilityである。これらはriver plan／routingの整合性を示すが、最終blockにriverがあることを示さない。

汎用placement serviceはverified hydrology Releaseのsurface tileを適用できる。しかしそのtileにはriver効果がなく、catalogのPaper列もEXPERIMENTALのままである。この状態を「RIVERがPaper配置可能」と数えてはならない。

評価: routeとartifactはPUBLIC_OFFLINE、river terrain materializationはCONTRACT_ONLY相当。

### 4.9 environment-fields

environment pipelineはgeology、climate、snow、material、palette、ecology等のplan／artifactをReleaseへ追加するが、base surfaceはhydrology経路のcoastal sourceを再利用する。

公開pipeline内のenvironment validation samplerは全cellで同じ `healthy` snapshotを返す。実際のmaterial／climate fieldをsampleしていないため、このvalidation合格は地形反映を示さない。個別environment Feature planは明示拒否される。

対応Application Serviceのmain callerはない。

評価: shared capability spineはINTERNAL_SLICE。個別Featureとblock materializationは未接続。

### 4.10 sparse volume

sparse pipelineはSDF／CSG／AABB／validation／volume tile contractを作るが、現在のshared pathはbedrock cell内の小sphereに `ADD_FLUID`を適用するidentity構成で、base block class／material／fluidが変化しない場合はcoastal block resolverをそのまま返す。

これはvolume Release容器とplacement overlayのspineを検査する有用なsmoke sliceだが、cave、overhang、arch、sky island等のpublic materializationではない。Application Serviceも公開workflowから未到達である。

評価: INTERNAL_SLICE。

### 4.11 validation、preview、conformance

強い点:

- desired／actual／residualを分離したfield artifact
- digest／dimension／no-dataを含むconstraint binding
- final coastal fieldに対するEDGE evaluator
- owner coverage fail-closed
- publish済みReleaseだけを入力にするcoastal conformance portfolio
- preview indexとbounded render
- locale／timezone／thread／tile順のdeterminism test

弱い点:

- RIVER conformanceはfinal block streamではなくhydrology validation artifactを測る
- environment validationはconstant healthy sampler
- sparse validationはidentity／empty descriptor slice
- `HEIGHT_GUIDE` desired／actual／residualの契約はあるが、actual生成consumerがない
- prompt／reference imageと生成結果のsemantic fidelity scoreをpublic workflowが持たない

validation frameworkの品質は高いが、何を測っているかをFeatureごとに区別しなければ「合格している未実体化Feature」を作れる。

### 4.12 ReleaseとPaper placement

Release 2は今回の監査で最も強い領域の一つである。

- manifestのexact file setとrequired capability
- directory／ZIP parity
- unknown／missing／extra／duplicate拒否
- traversal、case collision、symlink、bomb、budget対策
- artifact／semantic checksum
- staging後のstrict read-backとatomic publish
- commit point前のcancel

placementも次の実装順序を確認した。

```text
verified Release open
→ dimension admission
→ effect envelope
→ reservation
→ reservation-bound confirmation
→ snapshot-all
→ containment preflight
→ apply
→ settle
→ full verify
→ failure時rollback
```

LARGEはmutation前に拒否される。world access gatewayと非同期serviceも分離されている。

この安全性は本物だが、入力tileの意味が弱い問題を解決しない。「安全に平らな／riverのないtileを置ける」状態と「意図した地形を作れる」状態は別である。

## 5. 現在能力の一覧

| 能力 | 現在状態 | 最終block | Public CLI | Paper評価 |
|---|---|---:|---:|---|
| coastal 4種の複合surface | PUBLIC_PRODUCTION | あり | あり | measured範囲でSUPPORTED |
| coastal 1〜3種だけのsurface | 未対応 | なし | runtimeが拒否 | 未対応 |
| macro land/water foundation | PUBLIC_PRODUCTION | あり | あり | coastal Releaseとして配置可 |
| macro連続標高／HEIGHT_GUIDE | CONTRACT_ONLY | なし | extract/promoteまで | なし |
| PLAIN／HILL foundation generator | INTERNAL_SLICE | test Releaseではあり | なし | なし |
| RIVER／MEANDERING_RIVER | PUBLIC_OFFLINE route | river効果なし | hydrology exportあり | EXPERIMENTAL、意味実体なし |
| LAKE／CANYON／WATERFALL等の既存plan | CONTRACT／INTERNAL | public tileにはなし | routeなし | 未対応 |
| environment material／palette | INTERNAL_SLICE | coastal tileへ未反映 | なし | なし |
| cave／overhang等sparse volume | INTERNAL_SLICE | public shared sliceはidentity | なし | なし |
| promptのみのprovider design | 接続あり | export可能kindに限定 | あり | export後は同じ |
| reference-image provider design | BROKEN_PUBLIC | なし | 入口はあるが必ず失敗 | 同左 |
| image LAND_WATER extraction | PARTIAL | 手動binding済Intentなら反映 | あり | export後は同じ |
| image HEIGHT／ZONE extraction | INTERNAL_SLICE | なし | promoteまで | なし |
| Release verify／placement safety | PUBLIC_PRODUCTION | 入力tileを正確に配置 | あり | 強い |

## 6. 質問への回答

### 6.1 リアルな地形、prompt／画像に近い地形を生成できる設計か

長期設計の方向は妥当だが、現在の接続状態ではできない。

良い設計要素は、Intentとblock listの分離、typed relation、foundation／modifier／volume／fluidのstage分離、global hydrology before tiles、desired／actual／residual、strict Releaseである。これらはリアルな複合地形を安全に構築する土台になる。

不足しているのは次の実行可能な縦経路である。

- 画像byte→verified provider image
- constraint source→Intent binding
- HEIGHT_GUIDE→foundation elevation
- promptのterrain kind→現在到達可能なcompositionへの制約
- producer／carver／fluid field→最終tile
- material／ecology plan→block resolver
- 最終blockからのfeature conformance

「今後実装予定の地形を組み合わせれば自動的にリアルになる」とも言えない。各planを既存shared pipelineへ登録するだけではRIVERと同じ不可視Featureになり得る。composition契約と同時に、block effectとfinal-stream validationを必須化する必要がある。

### 6.2 大高低差と局所的で複雑な標高変化

契約上は可能、production実装上は不可能である。

- vertical span、fixed-point、height field、sidecarは十分
- macro backgroundはland/waterの2定数
- producer listは空
- HEIGHT_GUIDE consumerなし
- coherent multi-scale detail／erosion kernelなし
- 局所高低差はcoastal 4種のanalytic shapeだけ

山地、谷、崖、河床、湖盆等を同一surface fieldへ合成するproduction owner／interactionはまだ存在しない。

### 6.3 安全かつ一貫して地形を増やせるか

契約追加の安全性は高いが、能力追加のAcceptanceに穴がある。

良い点:

- enum／Schema／module／catalog／dispatchのexact-cover検査
- compile-time registry
- unknown／partial route fail-closed
- composition profileとrelation
- canonical checksumとdeterminism
- per-leaf Task、ADR gate、Paper昇格分離

問題:

- route、plan、validator、previewが揃えば、block effectがなくてもleafを完了できる
- Feature Support Catalogの `export: SUPPORTED`とpublic dispatch reachabilityが一致しない
- shared pipelineがidentity／constant samplerでもcapability artifactは成立する
- `WorldBlueprintV2`の型別list増加で変更面積が拡大している
- design時にunsupported kindを止めない

結論として、構造は安全だが、semantic completenessを保証するgateが不足する。

### 6.4 prompt／画像忠実性をどう改善するか

優先順位は「AI modelを高度化する」より「入力とconsumerを完全に接続する」が先である。

1. reference imageのsecure prepare／budget／redaction／provider handoffを公開designへ配線する。
2. roleに依存しないgeneric constraint source authoringを作り、複数mapとIntent bindingを同一操作でstrictに生成する。
3. `HEIGHT_GUIDE`をmacro foundationのelevation sourceとして消費する。ADR 0038 D2の既存許可を使い、medium base levelとの優先順位、no-data、tolerance、resamplingを明示する。
4. provider promptへ使用可能なmap role／source IDと現在のproduction reachabilityを渡し、unsupported kindをdesign前またはdesign直後に明示diagnosticにする。
5. prompt／image proposal、HARD map、manual editを `MultiSourceReconciliationServiceV2`へpublic接続する。HARDをpriorityで上書きせず、SOFT conflictはpreviewとconfirmationへ出す。
6. fidelityをfinal field／blockから測る。land-water、height、river centerline／bed、material zone等をrole別metricにし、unconsumed targetを0点ではなく明示状態にする。

### 6.5 その他の重要課題

最重要は、`V2-15-11`へ進む前にV2-15 leaf Acceptanceを是正することである。

また、docsのcurrent-state driftは利用者だけでなく次Taskを実行するagentを誤誘導する。少なくとも `implementation-roadmap.md`、`migration-plan.md`、current limitations、READMEのreference-image／image-E2E表現をコードの接続状態へ同期すべきである。

## 7. 優先度付き修正Task案

以下は提案のみであり、Task Index、roadmap、model assignmentへは登録していない。既存IDを流用せず、登録時に新IDと人間承認を与えること。

### P0-A: V2-15 leafのsemantic materialization gate

目的: `V2-15-11`以降へRIVERの不完全なAcceptanceをコピーしない。

Scope:

- per-leaf義務へ `BlockEffectDeclaration`相当を追加
- Featureが変えるcanonical field、block class、material、fluidを明示
- intentional no-opはcapability spine smokeに限定し、Featureのproduction route昇格には使用不可
- final canonical block streamから対象Featureの非空効果と形状conformanceを測定
- plan-only metricとblock metricを別欄にする
- Feature Support Catalogのsupportとpublic dispatch reachabilityを別々に表示

Acceptance:

- block effectを除いたRIVER実装がnegative fixtureで必ず失敗
- environment constant sampler、sparse identity sliceはFeature materializationとして合格しない
- coastal既存Releaseは不変

### P0-B: RIVER block materialization

目的: 現在のoffline routeを初めてMinecraft上のriverへする。

Scope:

- routing／reconciliation resultからbounded river bed fieldを生成
- surface modifierまたはcarverとしてbedを下げる
- `ADD_FLUID`責務でwaterを入れ、`CARVE_SOLID`にfluid ownershipを持たせない
- mouth／source、bank、existing coastal foundationとのinteractionを宣言
- tile書出し前にglobal routeをfreeze
- final tile streamからbed depth、water continuity、source-mouth reachability、leak envelopeを検査

Paper mutation／SUPPORTED昇格は同Taskに含めない。まずoffline Releaseを完成させる。

### P0-C: reference-image public design repair

目的: 宣言画像を安全にproviderへ届ける。

Scope:

- request相対pathのsecure resolve
- symlink／traversal／size／decode／dimension／MIME／pixel budget
- EXIF等metadataをprovider payload／logへ漏らさない
- `PreparedReferenceImageV2`をrequest順に構築
- cancel／cleanup
- provider adapterへのrole textとimage byte handoff
- missing、digest mismatch、oversize、unsupported encoding negative
- actual CLI／Paper design E2E。Schema-only fixtureは禁止

### P0-D: generic constraint source＋Intent binding authoring

目的: 抽出物を手作業fixtureなしでproductionへ接続する。

Scope:

- LAND_WATER／HEIGHT／ZONE共通のsource宣言
- 複数mapを置換せず追加・更新
- role／encoding／strength／tolerance／source IDを明示
- Intent bindingをproviderの推測に依存せず生成・確認
- source→binding→compile→consumerのE2E
- 既存 `LAND_WATER_MASK` single-map commandのcompatibilityを維持

### P0-E: HEIGHT_GUIDE macro foundation consumer

目的: 大域高低差と画像標高忠実性を開く。

依存: P0-D。

Scope:

- ADR 0038 D2に従うelevation source
- fixed-point sample、bounds clampではなくout-of-contract拒否
- no-dataとmedium base level fallback規則
- LAND_WATERとの整合
- whole／tile、tile順、thread、locale／timezone決定性
- desired／actual／residual heightを入力guideに束縛
- working-set／decode／artifact budget

新artifact type／capabilityが不要ならADR amendmentは不要。契約解釈がD2を超える場合のみamendmentを求める。

### P1-A: public foundation producerの縦接続

PLAIN／HILLを一度にまとめず、1 kindずつmacro `ProducerLayer`、composition、surface field、block resolver、conformance、public dispatchへ接続する。その後にMOUNTAIN_RANGE／VALLEY等を追加する。producer overlap、replacement、modifier interactionをnegative fixtureで固定する。

### P1-B: design-time support lint

provider呼出し前に、要求されたkind／capabilityのreachable setを提示する。provider出力後にもdispatch dry-runを行い、unsupported kindをartifact publish前に明示する。historic 60-kind Schemaを狭めるのではなく、runtime capability profileを別入力として与える。

### P1-C: material／paletteのblock反映

environment material planをcanonical block resolverへ接続し、hard-coded 11 stateをprofile-driven allowlistへ置換する。任意block stateや外部scriptは許可しない。validationはconstant samplerを廃止し、実fieldとfinal block materialを読む。

### P1-D: coherent detail kernel

foundation shapeを壊さないbounded、deterministic、multi-scale detail modifierを新versionとして設計する。cell hashの独立ノイズではなく空間的に連続したfieldとし、seed namespace、frequency、amplitude、halo、support radius、tile seam、CPU／memory budgetを固定する。erosionは別Task／別stageにし、同時導入しない。

### P1-E: Schema／example inventory testのGradle input修正

`SchemaContractTest`は実行時に `src`、`docs`、`README.md`、`schemas`、`examples`を走査するが、Gradle `test` taskのinputとしてこれらの変更が完全には追跡されない。今回、監査文書編集中の一時的な参照で成功したtest resultを、文書を未参照状態へ戻した後の `./gradlew build` がcacheから再利用した。対象test単独実行は同じ最終状態で失敗した。

Scope:

- Schema／example inventoryが読むdirectoryをtest taskの明示inputへ登録
- untracked fileの追加・削除・docs参照変更でもup-to-date／build cacheを無効化
- cache有効／無効で同じorphan判定
- CIとlocalの同一性

### P2-A: Blueprint拡張面積の縮小

`WorldBlueprintV2`の多数の型別listを、unknownを受理しないclosed typed plan envelope／registryへ段階移行する案をADRで検討する。既存Schema ID、artifact type、checksumをin-place変更しない。

### P2-B: docs current-state同期

対象:

- `docs/design-v2/implementation-roadmap.md`
- `docs/design-v2/migration-plan.md`
- current limitations
- READMEのreference imageとimage extraction E2E
- Feature Support Catalogの「plan-level support」と「public route」の説明
- operationsのsnapshot-all順序

文書を能力より先に更新せず、上記P0の実装前は現在の未接続状態を明記する。

## 8. 推奨実行順

```text
P0-A semantic gate
  ├─ P0-B RIVER materialization
  └─ V2-15-11以降の再開判断

P0-C reference-image repair
  └─ provider image fidelity

P0-D generic map authoring/binding
  └─ P0-E HEIGHT_GUIDE consumer
       └─ P1-A foundation producers
            └─ P1-D coherent detail

P1-B support lint はP0-C/P0-Dと並行可能
P1-C material反映はsurface field契約確定後
```

`V2-15-11`を先行して同じpatternで完了させることは推奨しない。

## 9. テストと実測

### 実行結果

- `./gradlew build -x test`: PASS
- 関連targeted suite: 73 tests、0 failure、0 skip
  - production dispatch
  - macro foundation stage／Phase gate
  - CLI image chain
  - hydrology export
  - Release 2 placement
  - image fidelity Phase gate
- `./gradlew test`: 1,240 tests、1 failure、9 skip
- `./gradlew test --tests '...SchemaContractTest'`（最終文書状態）: 5 tests、1 failure
- 通常の `./gradlew build` は、監査文書編集中に一時的に成功したtest resultをcacheから再利用してPASS表示になった。このrunは最終文書状態の有効な証拠ではない
- `./gradlew build --rerun-tasks`（最終文書状態）: full testを強制実行し、同じ1 failureでFAILED

### 唯一のfailure

```text
SchemaContractTest.everyExampleDocumentIsReferencedBySourceOrDocs
expected: []
actual: [shore-2to1-400.terrain-intent-v2.json]
```

これはproduction code回帰ではなく、作業開始前から未追跡だったsample Intentがsource／docsのどこからも参照されないため、repository hygiene testが拒否したものである。failure messageはrequest fileを列挙していない。

本監査では既存の未追跡sample、PNG、`out/`を変更・削除・登録していない。

### Preview／artifact実測

既存未追跡 `out/shore2to1`をread-onlyで確認した。

- bounds: 400×400、Y=32..72
- foundation: land Y=54、water bed Y=42
- actual height preview: 大域land／waterは各定数面、局所featureだけ変化
- tile grid: 4×4
- 中央のfeature混在tile: `paletteSize=11`
- manifest／blueprint／constraints／preview／validation／schematicがRelease treeに存在

実測結果はproduction codeの2値foundationとhard-coded resolverに一致する。

## 10. 品質評価

| 観点 | 評価 | 理由 |
|---|---|---|
| 決定性 | 強い | canonical checksum、stable ordering、locale／timezone／thread／tile順test |
| fail-closed | 強い | unknown route、HARD conflict、map、Release、placement境界 |
| Release security | 強い | exact set、strict read-back、path／ZIP／budget／checksum |
| placement safety | 強い | reservation、snapshot-all、containment、settle、full verify、rollback |
| terrain expressiveness | 弱い | public block化が2値foundation＋coastal 4種 |
| prompt fidelity | 弱い | reachable kind lintなし、prompt semantic targetのfinal measurementなし |
| image fidelity | 不成立 | reference-image public path broken、map binding／consumer未接続 |
| hydrology realism | 不成立 | route／metricはあるがriver blockなし |
| material realism | 弱い | 11 state固定、environment plan未反映 |
| extension governance | 強い | registry／ADR／per-leaf／phase gate |
| extension semantic completeness | 弱い | block materialization gate欠落 |
| test cache soundness | 要改善 | filesystem inventory testが読む未追跡docs／example変更をGradle inputが完全追跡しない |
| docs currentness | 要改善 |複数のstatus／能力表現がcurrent codeと不一致 |

## 11. 最終判断

LandformCraftは「リアルな地形generator」より先に「壊れた／不正なartifactを安全に拒否し、正しいartifactを決定論的に配置するplatform」を完成させた状態である。この順序自体は悪くない。むしろplacementとReleaseを後付けするより安全である。

しかし現在のroadmap実行patternは、plan-level generatorをshared pipelineへ接続しmetricを追加するだけで「public wiring」を完了できる。RIVERがその反例になった。今ここでsemantic materialization gateを追加しなければ、catalog、dispatch、validationの見かけ上の成熟度と、Minecraftで見える地形の差は拡大する。

次の最大レバーは単独では `HEIGHT_GUIDE`ではない。優先順位は以下である。

1. Feature完了条件へfinal block materializationを導入する。
2. RIVERをその新条件で実体化する。
3. reference imageとconstraint bindingの公開入力経路を修復する。
4. `HEIGHT_GUIDE`をmacro foundationへ接続する。
5. foundation producer、material、coherent detailを1縦sliceずつ追加する。

この順なら、既存の決定性、安全性、Release／placement契約を維持しながら、入力忠実性と地形表現力を同時に引き上げられる。
