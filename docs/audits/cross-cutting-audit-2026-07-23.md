# LandformCraft横断監査（2026-07-23）

> Status: `AUDIT_REPORT` rev.2 / **REGISTERED** — 本文書はrev.2として2026-07-23に**人間承認され**、§9の提案は横断Phase `V2-19`（16 Task）として[Task Index §19](../design-v2/task-index.md)へ正式登録した（roadmap／model-assignment同期済み、合計Task 231→247）。§9末尾にT-ID→`V2-19-xx`の対応表を示す。production code・Schemaへの変更は行っていない。
>
> rev.2（同日）: 独立レビュー（`cross-cutting-audit-independent-review-2026-07-23.md`）の全争点を再ファクトチェックし、rev.1の誤り3件を訂正、新規確定事実を統合、Task提案を再構成した。検証結果は§7に示す。

## 0. 監査基線と方法

- HEAD: `f3588f6`（2026-07-23）。未コミットの作業ツリー差分は `examples/v2/diagnostic/shore-2to1-400.*`（新diagnostic example）、`out/`（同exampleのexport成果物）、監査文書2件のみで、production sourceに差分はない。
- 評価の正本は**現在のコードと接続状態**とし、文書上の予定・完了宣言・Feature Support Catalogの`SUPPORTED`表記はコードで再照合した。主要な照合対象:
  - dispatch／export: `ProductionDispatchRegistryV2`、`CoastalSurfaceExportPipelineV2`、`CoastalGeneratorRuntimeV2`、`HydrologyPlanExportPipelineV2`、`EnvironmentFieldsExportPipelineV2`、`SparseVolumeExportPipelineV2`、`MacroFoundationStageV2`／`MacroFoundationV2`、`FoundationSurfaceExportAdapterV2`
  - 入力経路: `LandformCraftCli`、`V2WorkflowServiceV2`、`V2RequestStoreV2`、`TerrainDesignApplicationServiceV2`、`TerrainDesignRequestV2`、`ai/`（Anthropic／OpenAI provider）、`ImageExtractionWorkflowServiceV2`、promotion 3系統
  - validation／conformance: `CoastalValidatorV2`、`TargetDrivenValidatorV2`、`IntentConformancePortfolioV2`、`MacroFoundationPhaseGateV2Test`
  - 配置: `ReleasePlacementInputContractV2`、placement lifecycle、Paper `LandformCraftCommand`
  - governance: ADR 0036〜0039、task-index §15／§18、roadmap、2026-07-22の2監査
- 実測: 未コミットexampleの公開済みRelease（`out/shore2to1`）のpreview／fields、`./gradlew test`（結果は§8）。

## 1. 結論（TL;DR）

**基盤（決定性・fail-closed・Release検証・配置安全性）は非常に強固だが、公開経路で最終的にMinecraft blockへ実体化される地形表現力はその基盤に大きく見劣りし、入力側（自然言語・画像）にも未接続・故障箇所がある。** 具体的には:

1. **production経路でblockになる地形は「2値flatなfoundation＋coastal 4種のmodifier」だけである。** foundationの標高は `landSurfaceY`／`waterBedY` の2定数で、feature非所有領域は完全な平面になる（§2.3、実測§2.4）。さらにruntimeは**coastal 4種全部の同時宣言を必須**とするため、beach単体のような部分集合すらexportできない（§2.3）。
2. **RIVERのoffline production routeはブロックを1個も変えない。** routing／reconciliation／validation／preview artifactは作られるが、surface tileはriverを知らないcoastal resolverが書く（§2.5）。per-leaf義務にblock materializationが無いため、この乖離はV2-15-11以降で再生産され得る（§4.3）。
3. **reference image設計経路は公開入口があるのに必ず失敗する（BROKEN_PUBLIC）。** `TerrainDesignApplicationServiceV2.buildProviderRequest` は宣言画像数に関係なく空listをproviderへ渡し、`TerrainDesignRequestV2` が宣言数との一致を要求するため、画像付きrequestはprovider呼出し前に例外になる（§2.6。rev.1はここを「接続済み」と誤評価していた）。
4. **画像抽出はLAND_WATERでもIntent bindingまで公開authoringできず、HEIGHT_GUIDE／ZONE_LABELは行き場がない。** E2E testはmapReferencesを持つ**事前作成済みintent fixture**でexportしており、source→bindingの生成は経路に存在しない（§2.7）。HEIGHT_GUIDEの生成側consumerも不在で、しかも「constraint mapは正確に1枚」の制約が同時宣言を構造的に塞ぐ（§3.2）。
5. **大高低差・局所的で複雑な標高は現状どの経路でも生成できない。** producer layersは型のみで常に空、公開productionにcoherentな（空間的に連続した）multi-scale detail／erosionは存在しない。非公開のPLAIN sliceにcell-hashのmicro reliefがあるのみ（§3.1）。
6. **拡張governance（registry・per-leaf義務・ADR）は優秀だが、semantic completenessを保証するgateが欠けている。** route・plan・metricが揃えばblock効果ゼロでもleafを完了できる（§4）。

## 2. 入力→Minecraft地形の経路トレース（実装状態の区分）

区分: `PUBLIC_PRODUCTION`（公開到達・block実体化・検証あり）／`PUBLIC_OFFLINE`（CLIからRelease生成まで、Paper非SUPPORTED）／`INTERNAL_SLICE`（実装ありだが公開workflowから未到達）／`CONTRACT_ONLY`（契約・fixtureのみ）／`BROKEN_PUBLIC`（公開入口はあるが正常入力で必ず失敗）。

### 2.1 経路の全体像

| 経路要素 | 状態 | 根拠 |
|---|---|---|
| NL prompt → intent（画像なし。openai／anthropic／fixture／import） | 接続済み | `LandformCraftCli:141-146`、`TerrainDesignApplicationServiceV2` |
| NL prompt → intent（reference image付き） | **BROKEN_PUBLIC** | §2.6 |
| 画像 → LAND_WATER_MASK（extract→promote→request source宣言） | **PARTIAL**（Intent bindingは公開authoring不可） | §2.7 |
| 画像 → HEIGHT_GUIDE／ZONE_LABEL（extract→promote） | **INTERNAL_SLICE**（request宣言verbすら無し、consumerゼロ） | §2.7、§3.2 |
| surface-2_5d export（coastal 4同時宣言） | **PUBLIC_PRODUCTION**（Paper `SUPPORTED`、FAWE実測1000×1000） | `ProductionDispatchRegistryV2.builtIn()` |
| surface export（coastal 1〜3種のみ） | **不可**（runtimeが4種全部を要求） | `CoastalGeneratorRuntimeV2:34-97` |
| hydrology-plan export（RIVER／MEANDERING_RIVER） | **PUBLIC_OFFLINE**（CLI `v2 export hydrology-plan`。**block出力にriver効果なし**） | §2.5 |
| environment-fields／sparse-volume／foundation adapter（PLAIN/HILL） | **INTERNAL_SLICE**（Application Serviceのmain callerゼロ、testのみ） | 参照検索（§2.8） |
| V2-9／V2-10 plan-level generator（mountain／valley／marine／glacial等） | **INTERNAL_SLICE／CONTRACT_ONLY** | 2026-07-22カタログ監査§4.2と一致 |
| multi-source reconciliation | **INTERNAL_SLICE**（CLI callerなし） | `MultiSourceReconciliationServiceV2` 参照検索 |
| Release strict verify／Paper placement lifecycle | **PUBLIC_PRODUCTION**（本監査で最も強い領域） | §2.9 |

### 2.2 dispatch正本（ADR 0039後の現在形）

`production-dispatch-registry-v2` のrouteは6個だけ: `PRODUCTION_CONNECTED`＝coastal 4、`OFFLINE_PRODUCTION`＝`RIVER`／`MEANDERING_RIVER`（hydrology-plan pipeline）。他kindはartifact生成前にfail closedで拒否される。Feature Support Catalogの`export: SUPPORTED`はplan-level／shared capability supportを含むため**公開dispatch到達性と同義ではない**。この差はproviderにも利用者にも見えない（design lintはcatalogではなくdispatch registryを参照する必要がある。提案T5）。V2-15-10のper-leaf義務（RIVER NORMATIVE化＋portfolio case）は履行済みで、governanceと実装は一致している。

### 2.3 foundationの実体 — 標高は2定数flat、coastalは4種固定

`MacroFoundationStageV2.resolve` はHARD `LAND_WATER_MASK`＋`foundationBaseLevels` からbackground candidateを作るが、producer layersは**常に空**（`List.of()`）で、`MacroFoundationV2` の標高は

```java
return mediumAt(globalX, globalZ) == 1 ? landElevationMillionths : waterElevationMillionths;
```

の2値しか返さない。maskが与えるのは平面の切り抜き形状だけである。`ProducerLayer` record（medium／elevation関数）は定義済みだが構築箇所が1つもない。ADR 0038 D5のproducer tierは**契約のみ実装・実体未実装**である。

さらに `CoastalGeneratorRuntimeV2.create` は `REQUIRED = {SANDY_BEACH, HARBOR_BASIN, BREAKWATER_HARBOR, ROCKY_CAPE}` を検査し、不足を「surface-2_5d export requires all four V2-2 coastal contributors」で拒否する。**「beachだけの海岸」はpublic surface exportできない。** （rev.1はtransition plan compilerの「≥1」を根拠にdocs側をstaleと誤判定した。docs（current-limitations.md）の「4種必須」が正しい。マクロfoundationが背景ownerを持つ現在、この全4種要求はV2-2期の残存制約であり、緩和候補である — 提案T-C1。）

### 2.4 実測（shore-2to1-400）

本監査は未コミットexample [examples/v2/diagnostic/shore-2to1-400.request-v2.json](../../examples/v2/diagnostic/shore-2to1-400.request-v2.json)／[examples/v2/diagnostic/shore-2to1-400.terrain-intent-v2.json](../../examples/v2/diagnostic/shore-2to1-400.terrain-intent-v2.json)（400×400、land Y=54／water bed Y=42／waterLevel=50、mask＋foundationBaseLevels宣言）を「2値foundationの実測evidence」として本文書から正式参照する（example参照整合testの登録根拠は本節）。

`out/shore2to1/previews/actual-height.png` は、北側mainland全域が単一グレー（Y=54平面）、南側seabedが単一ダーク（Y=42平面）で、標高変化はbeach断面・harbor掘込み・breakwater天端・capeの局所形状に限られる。promptは「grassland mainland」を求めるが、生成物は草1層の完全平面である。これはpreviewの印象ではなく§2.3のコードから決定的に導かれる。

blockのmaterial候補は resolver（`CoastalSurfaceFieldsV2.resolver`）にhard-codeされた**11 block state**（bedrock／air／water／stone／stone_bricks／cobblestone／gravel／sand／sandstone／grass_block／dirt）に固定される。実測tileの `paletteSize: 11` と一致する。environment側のpalette／material planはこのresolverに接続されない。

### 2.5 RIVERは「planのRelease」であり、ブロックを1個も変えない

`HydrologyPlanExportPipelineV2.generateHydrology` は (1) coastal surface生成（**tilesはここで確定**）→ (2) routing／reconciliation／validation／previewの**JSON artifact**生成の順で、river fieldはvalidation／preview／metricにのみ流れる。Release sourceも `surface.source()` を再利用し、base terrain queryはcoastal fieldだけを読む。placementも `HYDROLOGY_WITH_SURFACE` prefixでsurface 3 overlay（solid/air/fluid）を流用するだけである（`ReleasePlacementInputContractV2:43-47`）。**hydrology Releaseを配置しても地表に川は現れない。**

V2-15-10のconformance caseが測るのは公開Release内 `hydrology/validation.json` の3 metric（channel gap／reverse gradient／reachability）であり、**最終block streamのriverを測っていない**。ADR 0039の契約違反ではないが、「RIVERが生成できる」というユーザー期待とは別物であり、V2-15-11以降が同じAcceptance解釈をコピーすると不可視Featureが量産される（§4.3、提案T-G1）。

### 2.6 reference image設計経路はBROKEN_PUBLIC

`TerrainDesignApplicationServiceV2.buildProviderRequest`（line 355-368）は `TerrainDesignRequestV2` へ**常に `List.of()`** を渡す。一方 `TerrainDesignRequestV2` のコンストラクタは `generationRequest.referenceImages().size() != images.size()` を例外にする（line 40-44）。従って**reference imageを1枚でも宣言したrequestのopenai／anthropic designは、provider呼出し前に必ず失敗する**。`PreparedReferenceImageV2` をmain sourceで構築するcallerは存在せず、testのみが直接構築する。provider adapter側に画像byte送信・role text（`TerrainIntentPromptV2.imageRoleText`）の実装があることは、この公開orchestration欠落を補わない。

付随して: `V2WorkflowServiceV2.designPathKinds()` は `import`／`fixture`／`openai`／`anthropic` の4種だけを公開し、enumに存在する `MANUAL_CONSTRAINT`／`REFERENCE_IMAGE_DRAFT` pathは公開されない。また4画像を宣言する `oblique-multi-view.request-v2.json` の実画像4ファイル（`references/*.png`）はrepositoryに存在せず、Schema load以上のevidenceにならない。

### 2.7 画像抽出: source宣言まではCLI、Intent bindingは手書きfixture依存

CLIのextract→draft→explicit promote（LAND_WATER／HEIGHT／ZONE 3系統、decode budget・digest・no-data・strict codec）は良く設計されている。しかし公開authoringの接続はroleごとに非対称である。

| Role | Extract | Promote | Request source宣言 | Intent binding公開authoring | 生成側consumer |
|---|---|---|---|---|---|
| LAND_WATER_MASK | あり | あり | あり（**正確に1枚**・U8 gray 0/1固定） | **なし** | あり（macro foundation） |
| HEIGHT_GUIDE | あり | あり | **なし** | なし | **なし** |
| ZONE_LABEL_MAP | あり | あり | **なし** | なし | **なし** |

`V2RequestStoreV2.constraintMap` は既存map listを `List.of(source)` で**置換**し、land/water固定encodingしか作れない（Schemaは複数map・3 roleを表現できるのに、公開authoringが作れない）。E2E test `extractPromoteDeclareAndExportRunsTheWholeImageFidelityChain` はsource宣言までをCLIで行い、exportには**最初から`mapReferences`を持つsealed intent fixture**を渡す。つまり**source→Intent bindingを生成する公開経路が存在せず**、画像からexportまでを自力で通すには intent JSONの手書き（binding artifactId＝mask digestの知識を含む）が必要である。NL providerはprompt要約にconstraint map情報を受け取らないため、providerがこのbindingを正しく発明することも期待できない。

### 2.8 test-onlyのApplication Service群

`Release2EnvironmentExportApplicationServiceV2`／`Release2SparseVolumeExportApplicationServiceV2`／`Release2FoundationSurfaceExportApplicationServiceV2` はmain sourceにcallerが無く、対応testのみが直接生成する。公開 `V2WorkflowServiceV2` が保持するのはsurfaceとhydrologyの2 export serviceだけである。さらに中身にも注意が要る:

- environment pipelineのvalidation samplerは**全cellで同一の`healthy` snapshotを返す定数**であり、実fieldをsampleしない。validation合格は地形反映を意味しない。
- sparse-volume shared pathは**bedrock cellへの`ADD_FLUID` identity構成**（SOLIDはfluidで置換不可＝無効果が保証された操作）で、Release容器とplacement overlay spineのsmokeとしては有用だが、cave／overhang等のmaterializationではない。

### 2.9 ReleaseとPaper placement（最も強い領域）

Release 2のexact file set／directory-ZIP parity／traversal・bomb・budget対策／strict read-back／atomic publish、placementのreservation→confirmation→snapshot-all→containment→apply→settle→full verify→rollback→Undo→Recoveryの実装順序は、コード・testともに堅牢であることを再確認した。**ただしこの安全性は入力tileの意味の弱さを解決しない。「安全に、平坦でriverの無いtileを置ける」ことと「意図した地形を作れる」ことは別である。**

## 3. 大きな高低差・複雑な局所標高（質問2への回答）

### 3.1 契約は十分、生成能力が欠けている

- 垂直契約: `GenerationRequestV2.Bounds` は垂直範囲≤512 block、fixed-point millionths、`HeightEncoding`（3種のmeaning、range検証）まで実装済み。**表現契約側に不足はない。**
- 生成能力: production＝§2.3の2値flat＋coastal局所形状。offline＝`HillRangeGeneratorV2`（ridge spline距離＋線形falloff）、`MountainRangeGeneratorV2` 等の解析形状、`PlainGeneratorV2` のcell-hash micro relief（±数block、**空間的に非連続な独立hash**）。**公開productionにcoherentなmulti-scale detail／erosion kernelは存在しない**（rev.1の「noiseが一切ない」はこの表現へ訂正する）。
- 2.5D制約: production surfaceはcolumnあたり単一surfaceのheightmapで、オーバーハング・海食崖の抉れはsparse-volume（INTERNAL_SLICE）が必要。
- 山地・谷・崖・河床・湖盆を**同一surface fieldへ合成するproduction owner／interaction契約はまだ存在しない**（ADR 0038 D5の実体化待ち）。

### 3.2 最大の単一ブロッカー: HEIGHT_GUIDE

`HEIGHT_GUIDE` はrequest契約・抽出・昇格・field契約・release verifyまで揃っているが、(a) request宣言CLIが無い、(b) Intent binding authoringが無い、(c) 生成側consumerが無い、(d) `CoastalSurfaceExportPipelineV2` が「constraint map正確に1枚」「mapReferencesはLAND_WATER_MASK 1件のみ」を強制するためmaskと同時宣言できない、の4点で完全に遮断されている。

なお**ADR 0038 D2-2は「provisional elevationは`HEIGHT_GUIDE`解決値またはmedium別base level」を既に許可している**。優先順位・no-data・resampling・budgetをD2の範囲内で具体化する限り、接続に**ADR amendmentは不要**である（rev.1の「amendment小が必要」を訂正。契約解釈がD2を超える場合のみamendment）。

## 4. 地形タイプを安全・一貫して増やせるか（質問3への回答）

### 4.1 強み（維持すべき構造）

- enum／Schema／module／catalog／dispatchのexact-cover CI（`CurrentFeatureStateRegistryV2`）と、route⇔module⇔pipeline⇔capabilityの相互検証・fail-closed。
- `CompositionProfileRegistryV2`（NORMATIVE 7／PROVISIONAL 53）とper-leaf義務。V2-15-10は義務を実際に履行した。
- 公開済みRelease成果物の再測定によるconformance portfolio／Phase gate（V2-18-11/12/13）。非適合を隠さず新IDへ分離した運用実績。
- ADR 0036の識別子統治、ADR 0039のroute class分離。

### 4.2 弱み（増設コストとリスク）

1. `WorldBlueprintV2` のper-kind typed plan list（17個＋singleton群）と `requireSharedHydrologyOnly` のような**負の列挙**。kind追加のたびにrecord・codec・Schema・canonicalization・fixtureの広い同時変更が必要で、拒否リスト更新漏れは「意図せぬ受理」になる。
2. 4 pipelineが同じcoastal executableKindsを重複列挙。
3. `DiagnosticBlueprintCompilerV2` が事実上のproduction compilerである命名負債。
4. mask⇔seed束縛（V2-18-09のmask再生成規則が生成出力を入力へ焼き込む）。feature geometryとmaskの整合責任が全面的にユーザー側にある。

### 4.3 最重要の構造リスク: 「plan-only配線」でleafを完了できる

V2-15のleaf Acceptance（公開入力→validation→plan→generator→preview→Release→CLI）は**「対象featureが最終block streamへ反映されること」を要求していない**。V2-15-10はこの定義を満たしつつblockを1 byteも変えていない。加えて§2.8のとおり、shared pipelineは定数sampler・identity sliceでもcapability artifactとして成立する。**per-leaf義務へ『block materialization必須（意図的no-opはspine smokeに限定し、Feature昇格には使用不可）＋final block streamからの形状conformance』を追加しない限り、catalog・dispatch・validationの見かけの成熟度とMinecraftで見える地形の差は拡大する**（提案T-G1。V2-15-11着手前に導入すべきである）。

## 5. NL・画像への忠実性の現状と改善（質問1・4への回答）

### 5.1 現状

- **NL（画像なし）**: 構造検証・retry・audit・strict publishは実装済み。ただしprovider promptへ渡るのはrequest ID・bounds・自由文だけで、constraint map情報・現在export可能なkind集合は渡らない。design段階のdispatch到達性lintも無いため、「山と谷のある島」は正常にdesignされ、exportで初めて拒否される。
- **画像**: soft経路（reference image）はBROKEN_PUBLIC（§2.6）。決定論的経路はLAND_WATERのsource宣言まで（§2.7）。HEIGHT／ZONEは昇格後に行き場がない。**「画像の海岸線どおりの平面」までが現在の忠実性の上限**であり、それすらIntent側bindingの手書きを要する。
- **計測**: desired／actual／residual分離、EDGE実測、conformance portfolioにより忠実性を**測る**基盤は完成。ただし測定対象はland-waterのみで、標高・材質・river形状のfidelity metricは未定義。unconsumed targetの明示状態化（V2-18-07）は良い設計で、拡張の受け皿になる。

### 5.2 改善の設計方針

1. **入力とconsumerの完全接続が、AIモデル高度化より先**である。壊れている入口（reference image）と、消費者のいない入力（HEIGHT／ZONE）を先に塞ぐ。
2. HEIGHT_GUIDE→foundation elevation（ADR 0038 D2の範囲内、amendment不要）。
3. role非依存のgeneric constraint source＋Intent binding authoring（複数map、置換でなく追加・更新、binding自動生成）。
4. design-time lint: provider呼出し前にreachable kind／capability集合を提示し、provider出力後にもdispatch dry-runで未到達kindをpublish前に明示。
5. fidelityは**final field／block**から測る（land-water、height、river centerline／bed、material zone）。plan-only metricとblock metricを別欄にする。
6. mask⇔featureのSOFT reconcile pre-pass（決定論的snapまたはtolerance超で拒否）はその後の段。

## 6. その他の重要指摘（質問5）

1. **Gradle test inputの追跡漏れ（新規確定・要修正）**: `SchemaContractTest` は実行時に `src`／`docs`／`README.md`／`schemas`／`examples` を走査するが、`build.gradle.kts` の `test` taskが明示宣言するinputは4ファイルのみ。docs／examplesの変更（untracked含む）がup-to-date／build cache判定に反映されず、**古い成功結果の再利用で失敗が隠れる**。本監査でも文書状態によって同testの結果が変わることを実測した（§8）。
2. **docs staleness**: `implementation-roadmap.md` は「現在の次Taskは`V2-15-05`」（2箇所）、`migration-plan.md` は「Track Aの次は`V2-12-07`」、`current-limitations.md` Status行は「Track Aの次は`V2-18-11`」— いずれも正本roadmap（次=`V2-15-11`）と不一致。READMEのreference image／image E2E表現も§2.6／2.7の実態と乖離。なお`current-limitations.md`の「4 coastal contributor必須」は**正しい**（rev.1の指摘を撤回）。
3. **hard-coded 11 block state**（§2.4）とpalette／material planの未接続。
4. **test-only Application Service群**（§2.8）は「production pipeline追加済み」という文書表現から到達可能と誤読しやすい。実装状態matrixへ「公開到達性」列を設けるべき。
5. 未コミット成果物: example 2ファイルは本監査§2.4がevidenceとして正式参照（登録）した。`out/` は生成物であり `.gitignore` へ追加した（§8）。
6. commit hygiene: 「test」「tset」等の情報量ゼロのcommit messageはbisect・監査の実務コストになる。

## 7. 独立レビューのファクトチェック結果（rev.2）

独立レビュー（2026-07-23、同一revision対象）の主要主張を全てコードで検証した。

| レビューの主張 | 判定 | 根拠 |
|---|---|---|
| reference-image designは必ず失敗（BROKEN_PUBLIC） | **確認** | `buildProviderRequest`の`List.of()`（line 365）×`TerrainDesignRequestV2`の件数一致要求（line 40） |
| 画像E2EはIntent bindingを生成していない | **確認** | E2E testはsealed intent fixtureでexport（`LandformCraftCliV2Test:484`） |
| HEIGHT／ZONEはrequest宣言CLIすら無い | **確認** | `V2RequestStoreV2.constraintMap`はland/water固定・`List.of(source)`置換 |
| coastal 4種は全部必須（docsが正しい） | **確認**（rev.1の誤りを訂正） | `CoastalGeneratorRuntimeV2:34-97` |
| block paletteは11 state（air／bedrock込み） | **確認**（rev.1の「9種」を訂正） | resolver全読＋実測tile `paletteSize: 11` |
| 「noiseが一切ない」は不正確、plainにcell-hash micro reliefあり | **確認**（§3.1へ反映） | `PlainGeneratorV2:99-106` |
| environment validationは定数healthy sampler | **確認** | `EnvironmentFieldsExportPipelineV2:277-280` |
| sparse sharedはbedrockへのADD_FLUID identity | **確認** | `SparseVolumeExportPipelineV2:40-56` |
| HEIGHT_GUIDE接続にADR amendment不要（D2-2が既に許可） | **確認**（rev.1の「amendment小」を訂正） | ADR 0038 D2-2 |
| `implementation-roadmap.md`／`migration-plan.md`のstale | **確認** | 各Status行 |
| Gradle test inputが走査対象を追跡しない | **確認** | `build.gradle.kts:122-134` vs `SchemaContractTest:299` |
| designPathKindsが`MANUAL_CONSTRAINT`／`REFERENCE_IMAGE_DRAFT`を非公開 | **確認** | `V2WorkflowServiceV2:204-206`、`DesignPathKindV2` |
| oblique-multi-viewの実画像4ファイル不在 | **確認** | `examples/v2/diagnostic/references/` 不存在 |
| test failureの列挙はintent 1件のみ（rev.1の「2件」は要修正） | **両方正**（時点依存） | 参照testは≥2 segmentのpath suffix一致で判定（`SchemaContractTest:309-321`）。rev.1実行時は2件orphan。その後rev.1文書がrequestの完全pathを含んだため1件へ変化。この時点依存自体が上記Gradle input問題の実証である |

レビューの結論・優先順位（materialization gate先行）は妥当と判断し、本監査のTask提案を§8のとおり再構成した。レビュー中で検証不能だったのは同氏の実行ログ数値（targeted 73 tests等）のみで、判断に影響しない。

## 8. 検証記録

- `./gradlew test`（rev.1時点、監査文書追加前）: 失敗1件 — `SchemaContractTest.everyExampleDocumentIsReferencedBySourceOrDocs`、orphan=example 2ファイル（request＋intent）。
- `./gradlew test --tests '*SchemaContractTest' --rerun-tasks`（rev.2、両監査文書あり）: 同testのorphanは`shore-2to1-400.terrain-intent-v2.json` 1件へ変化（§7末行の時点依存の実証）。
- 対応（本監査で実施）: §2.4で両exampleファイルを完全pathで正式参照（evidenceとしての登録）し、`.gitignore` へ生成物 `/out/` を追加した。再実行で `SchemaContractTest` 全PASSを確認（下記）。
- production source／Schema／example／Task Index／roadmapへの変更: **0件**（本文書と`.gitignore` 1行のみ）。

## 9. 提案Task（rev.2で再構成。2026-07-23人間承認により`V2-19`として登録済み — 末尾の対応表参照）

優先原則: (1) 誤った完了を防ぐgateが先、(2) 壊れている公開入口の修理、(3) 消費者のいない入力の接続、(4) 表現力の拡張。いずれも既存governance（ADR、fail-closed、report-only先行、Paper昇格はV2-17専管）に整合する。

### P0 — gateと修理（V2-15-11着手前に行う）

- **T-G1: V2-15 leafのsemantic materialization gate**（docs＋test契約、人間承認要）
  - per-leaf義務へ「対象Featureが変えるcanonical field／block class／material／fluidの宣言」と「**final canonical block stream**からの非空効果・形状conformance測定」を追加。意図的no-op（spine smoke）はFeature昇格に使用不可と明記。plan-only metricとblock metricをportfolioで別欄化。Feature Support Catalogのsupport列とpublic dispatch到達性を別表示。
  - Acceptance例: block効果なしのRIVER相当実装がnegative fixtureで必ず失敗する。§2.8の定数sampler／identity sliceはFeature materializationとして合格しない。coastal既存Releaseは不変。
- **T-B1: reference-image public design修理**（BROKEN_PUBLICの解消）
  - request相対pathのsecure resolve、size／decode／dimension／MIME／pixel budget、metadata非漏洩、`PreparedReferenceImageV2`のrequest順構築、cancel／cleanup、provider handoff、negative群（missing／digest mismatch／oversize）、**実CLI／Paper design E2E**（Schema-only fixture禁止）。実画像を持つexample整備を含む。
- **T-B2: generic constraint source＋Intent binding authoring**
  - LAND_WATER／HEIGHT／ZONE共通のsource宣言verb（置換でなく追加・更新、role／encoding／strength／tolerance明示）、**Intent bindingの生成・確認verb**（binding artifactId=input digestの規則をコード側が担保）、source→binding→compile→consumerのE2E。既存single-map commandの互換維持。
- **T-Q1: Gradle test input修正**（小・即効。CI健全性）
  - `SchemaContractTest`等のfilesystem inventory testが走査するdirectoryをtest taskのinputへ登録し、docs／examples／untracked変更でup-to-date／cacheを無効化。cache有効／無効で同一判定をCIで固定（§6-1／§7末行の実測が根拠）。

### P0.5 — 表現力の最小回復

- **T-R1: RIVER block materialization**（T-G1の新条件での最初の適用）
  - routing／reconciliation結果からbounded river bed fieldを生成し、surface tileへbed carve＋water fill（`CARVE_SOLID`にfluid ownershipを持たせず`ADD_FLUID`分離）。mouth／source／bank／coastal foundationとのinteraction宣言、tile書出し前のglobal route freeze、final tile streamからbed depth／water continuity／reachability／leak envelopeを検査。Paper `SUPPORTED`昇格は含めない。
- **T-H1: HEIGHT_GUIDE macro foundation consumer**（依存: T-B2）
  - ADR 0038 D2-2の範囲内でHEIGHT_GUIDEをbackground elevation sourceに（medium base levelはfallback、優先順位・no-data・out-of-contract拒否・resampling・budget明示）。「map正確に1枚」制約のrole別緩和を含む。desired／actual／residual heightを入力guideへ束縛し、conformanceのRasterResidualを有効化。whole／tile・thread・locale／timezone決定性。**ADR amendment不要**（D2超過時のみ）。

### P1 — 縦の拡張

- **T-P1: foundation producerの縦接続**: PLAIN 1 kindから `MacroFoundationV2.ProducerLayer`→composition→surface field→block resolver→conformance→public dispatchまで一気通貫し、次にHILL、その後MOUNTAIN_RANGE／VALLEY。producer overlap／replacement／modifier interactionをnegative fixtureで固定。foundation adapterのtest-only状態も解消。
- **T-L1: design-time support lint**: provider呼出し前のreachable集合提示＋provider出力後のdispatch dry-run（report-only先行、design auditへ`NON_GATING`記録）。historic Schemaは狭めず、runtime capability profileを別入力に。
- **T-C1: coastal 4種必須の緩和**（ADR要）: macro foundationが背景ownerを持つ現在、`CoastalGeneratorRuntimeV2.REQUIRED` の全4種要求はV2-2期の残存制約。部分集合（beachのみ等）の許可条件・checksum影響・conformance caseを定義。
- **T-M1: material／paletteのblock反映**: environment material planをcanonical block resolverへ接続し、hard-coded 11 stateをprofile-driven allowlistへ。定数validation samplerを廃止し実fieldを読む。
- **T-D1: docs current-state同期**: §6-2の4文書＋README＋能力matrixへの「公開到達性」列追加。実装より先に能力を記述しない。

### P2 — その後

- **T-D2: coherent detail kernel**（ADR要）: 空間的に連続なbounded・deterministic・multi-scale detail modifier（seed namespace／frequency／amplitude／halo／tile seam／budget固定）。erosionは別Task・同時導入しない。
- **T-A1: Blueprint拡張面積の縮小**（ADR検討）: per-kind typed plan listのclosed typed plan envelope／registryへの段階集約。Schema ID・artifact type・checksumのin-place変更はしない。
- **T-A2: mask⇔feature SOFT reconcile pre-pass**（ADR要）。
- **T-Q2: commit message規約**（Task ID必須等）。

### 推奨実行順

```text
T-G1 semantic gate ─┬─ T-R1 RIVER materialization ── V2-15-11再開判断
T-Q1 Gradle input ──┘
T-B1 reference-image repair（並行可）
T-B2 generic authoring/binding ── T-H1 HEIGHT_GUIDE ── T-P1 producers ── T-D2 detail
T-L1 lint はT-B1/B2と並行可。T-C1はT-P1と同時期。T-M1はsurface field契約確定後
```

**`V2-15-11`（LAKE）を現行Acceptanceのまま先行させることは推奨しない**（RIVERと同じplan-only配線を1件増やすだけになる）。

### 提案しないこと

- Paper `SUPPORTED` 昇格の前倒し（V2-17専管・実機証拠主義は正しい）。
- 新Release capability／artifact format（DR-2どおり既存4 capabilityで足りる）。
- LARGE解禁（V2-8 HOLD維持）。
- historic 60-kind Schemaの縮小（reachabilityはlint／dispatchで表現する）。

### 登録結果（2026-07-23人間承認）

本節の提案は横断Phase `V2-19`（Track A主担当、16 Task）として[Task Index §19](../design-v2/task-index.md)へ登録した。対応は次のとおり。

| 本監査ID | 登録ID | 備考 |
|---|---|---|
| T-G1 | `V2-19-01` | V2-15公開配線leafのstage gate。人間承認必須 |
| T-Q1 | `V2-19-02` | — |
| T-B1 | `V2-19-03` | — |
| T-B2 | `V2-19-04` | — |
| T-R1 | `V2-19-05` | 依存: `V2-19-01` |
| T-H1 | `V2-19-06` | 依存: `V2-19-04` |
| T-P1 | `V2-19-07` | producer機構＋PLAINのみ。HILL／MOUNTAIN／VALLEYはV2-15対応leafが新Acceptanceで配線 |
| T-L1 | `V2-19-08` | — |
| T-C1 | `V2-19-09` | ADR＋人間承認必須 |
| T-M1 | `V2-19-10` | — |
| T-D1 | `V2-19-11` | — |
| T-D2 | `V2-19-12` | ADR＋人間承認必須 |
| T-A1 | `V2-19-13` | ADR `Proposed`著述まで |
| T-A2 | `V2-19-14` | ADR＋人間承認必須 |
| T-Q2 | `V2-19-15` | — |
| —（Phase gate） | `V2-19-16` | 親Phaseを閉じる唯一のTask。人間承認必須 |

あわせて `V2-15-11` の依存へ `V2-19-01` を追加し（task-index §15.2）、roadmapのTrack A／E行・Phase表・登録段落、model-assignment §9（intro／§9.2 stage gate／§9.3新設）を同期した。
