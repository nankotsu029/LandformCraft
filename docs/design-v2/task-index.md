# v2 Task Index

> Status: V2-0〜V2-13の既登録Taskは、V2-8のHOLDと親Phase人間承認待ちを除き完了し、V2-14は`V2-14-01`〜`03`まで完了している。2026-07-22の[全地形カタログ監査](../audits/terrain-catalog-full-audit-2026-07-22.md)を受け、Track E続編`V2-15`／`V2-16`、Track A placement evidence follow-up `V2-17`を登録した。`V2-15-01`〜`V2-15-09`を完了し、ADR 0036に従うcanonical target registry、strict Schema／codec、明示migration、registry-driven production dispatch spine、`hydrology-plan`／`environment-fields`／`sparse-volume` shared production pipeline、およびADR 0037に従うV2-9／10 foundation→`surface-2_5d` export adapterを固定した。2026-07-22の[macro foundation／intent conformance監査](../audits/macro-foundation-conformance-audit-2026-07-22.md)を受け、Track Aへ`V2-18`（登録時12 Task、`V2-18-11`のconformance portfolioが確定した非適合を受けて`V2-18-13`を追加し13 Task、§18）を登録し、V2-15のproduction export／placement昇格系leafへ`V2-18-09`完了＋composition role登録を前提とするstage gateを適用した。`V2-18-01`〜`13`は全13 Task完了している（13/13。Phase gate `V2-18-12`は2026-07-23の人間承認で完了し親Phaseを閉じた。`V2-18-03`はHARD preflight gate `HardPreflightGateV2`をproduction export spineへfail-closed配線し、gate通過用のclean fixture `coastal-honored-400`／`harbor-cove-64-honored`を追加、既存coastal fixtureはnegative fixture化、coastal-fishing-mapのダミーmaskを実体化した。`V2-18-04`は`ValidationTargetV2`消費のtarget-driven validation framework（`validation.v2.target`）と`EdgeClassificationEvaluatorV2`（version 1、edge band=外周`max(1,垂直/32)`）を新設し、coastal生成経路で生成後にEDGE HARD違反をexport拒否、EDGEをpreflight評価対象へ登録、`IntentContributionCoverageV2`からEDGEをunconsumed除外した。`V2-18-05`は`coastal.breakwater.clear-opening`の恒真metric（plan値と自分自身の比較）をcrest region 8連結成分から2 arm間最小距離を計測する実測へ修復し、片側band `[planClear, planClear+6block]`＋下側tolerance 1blockとした。residual系metric（`coastal.land-water.residual-cells`／`coastal.height.residual-max`）のdesired＝actual自己参照は監査item 3として点検・記録し、修正は`V2-18-07`へ委ねた。terrain semantic checksum不変、絶対checksum pinなし）。`V2-18-06`は再利用可能constraint-map binding（`core.v2.binding`の`ConstraintMapFieldBindingV2`／`BoundConstraintFieldV2`）と暫定coastal adapter（`ProvisionalCoastalLandWaterSourceV2`）を新設し、secure resolve→digest→decode→canonical field登録（normalized XZ）を`SecureConstraintMapSourceLoader`＋`NumericPngDecoder`＋`CanonicalConstraintRasterV2`の再利用で恒久共通部品化、coastal生成への`HardLandWaterSourceV2`注入だけを暫定adapterへ分離した。production coastal生成へは未配線でfield／tile／block semantic checksumとRelease checksumは不変（恒久consumerは`V2-18-09`のmacro foundation stage、coastal専用loader化は禁止）。`V2-18-07`はconformance契約（`validation.v2.conformance`）を新設して異種target集合（desired raster／aggregate metric／topology／geometric＋provenance binding）とresidual種別（raster／scalar metric／topology pass-fail／unconsumed target／tolerance violation）を定義し、desired rasterをoptional（未解決provenance `SELF_DERIVED`はfalse 0ではなく`UnconsumedTarget`）とした。あわせて`withLandWaterBinding`（coastal／foundation adapter）の生成field checksumへの自己再束縛を入力mask digest束縛へ置換し、`SurfaceReleaseCapabilityVerifierV2`はintent binding＝宣言input digest／field index canonicalArtifactId＝field checksumを別々に検証するよう分離した。terrain semantic checksum不変、intent／blueprint／manifest容器byteは変化。CoastalValidatorのproduction self-reference解消は実mask解決を伴う`V2-18-09`のScope。`V2-18-08`は [ADR 0038](../adr/0038-macro-foundation-contract.md)（全surface XZ cellへeffective foundation ownerを要求するmacro foundation契約）を著述し、同日の第一レビュー（Changes Requested、4 blocking指摘）を反映した改訂第2版へ更新した。D1 candidate／background（macro base）／effectiveのowner 3概念と全cell被覆契約（merge後ちょうど1、宣言的replacement、ownerless拒否）、D2 foundation入力契約（normative＝`mapReferences`のHARD `LAND_WATER_MASK`＋`ConstraintMapFieldBindingV2`、elevationは`HEIGHT_GUIDE`またはmedium別宣言値、polygon region型は契約要件のみ固定し導入は新Task＋amendment、topology planは境界を新規生成しない、EDGE等からの内部境界推測禁止）、D3 単一role enum不採用→`CompositionProfileV2`（foundationEligible＋stage集合＋parentPolicy、canonical carrier lookup、要否＝要）、D4 60 kind対応表のNORMATIVE 6／PROVISIONAL 54の2段階化（foundation-eligible 17、`ABYSSAL_PLAIN`をmodifierへ再分類、多段stage kindを明示）、D5 foundation resolverとmodifier compositor（0..N interaction合成）の分離を含む昇格差分、D6 resource budget、D7 検証3層分担（kernel不変条件／`V2-18-10` coverage／`V2-18-11` conformance）、D8 `SurfaceBaselineV2` deprecation方針、D9 凍結と可変域を確定した。新capability／format無し、terrain semantic checksum不変。**ADR 0038は改訂第2版に対する2026-07-22の人間承認で`Accepted`となり、`V2-18-08`は完了、`V2-18-09`の開始gateは満たされた。**`V2-18-09`はmacro foundation production spine＋coastal統合を完了した: `MacroFoundationStageV2`／`MacroFoundationV2`（`core.v2.export`）がHARD `LAND_WATER_MASK`（V2-18-06 binding恒久consumer化）＋request新設optional `foundationBaseLevels`（medium別provisional elevation宣言、Schema追加、absent時request checksum不変）からbackground candidateを解決し、coastal 4種を`CoastalTransitionCompositorV2`のmodifierとしてfoundation上へ再接続（mask＝HARD source、feature非所有cellはbackground所有、`SurfaceBaselineV2`充填を置換）。kernel不変条件（OWNERLESS_CELL／UNDECLARED_OVERLAP／medium有効性）は生成時常時fail-closed。`CompositionProfileV2`＋`CompositionProfileRegistryV2`（60 kind、NORMATIVE 6／PROVISIONAL 54、carrier継承、foundation-eligible 17）をADR 0038 D3/D4どおり登録。desired＝入力mask由来へ置換（自己参照解消、residual実測）、foundation owner coverage 100%（activeContributorと分岐）、mask consumedをcoverage報告へ反映。baseline引数はfoundation入力完備requestで無視＋`v2.cli.surface-baseline-deprecated` NON_GATING警告、legacy requestは従来経路維持。honored fixtureはfoundationBaseLevels宣言＋mask再生成（composed∪macro構図）。暫定`ProvisionalCoastalLandWaterSourceV2`は削除。coastal容器checksum変化はADR承認範囲、v1 golden・shared pipeline回帰PASS。`V2-18-10`（2026-07-23人間承認で完了）はsurface foundation owner gateをfail-closedへ昇格した（ADR 0038 D7-2）: `SurfaceFoundationOwnerGateV2`（`surface-foundation-owner-gate-v1`、rule `v2.export.foundation-owner-coverage-incomplete`、override無し）が`V2-18-02`のfoundation owner metricを唯一の計測源として読み、被覆<100%を`SurfaceFoundationOwnerRejectedV2`（IOException）で拒否する。配線先はcoastal surface生成stage（surface／hydrology／environment／sparse-volumeの4 Application Serviceが`generateWithFields`再利用で継承）と`FoundationSurfaceExportAdapterV2`で、後者の従来の`coveredCells`判定は無条件increment由来の恒真だったためowner index実測へ是正した。gateはconstraint field index verify直後・preview／tile書き出し前に走り、公開物を一切生成しない。Scopeはsurface foundation ownerのみ（modifier被覆・volume／material被覆・request domain外は対象外）。coverage分母はrequest domain全cellでno-dataを除外せず、HARD maskのno-dataはbinding（`INVALID_NO_DATA`）が生成前に、ownerless cellはkernel不変条件（`OWNERLESS_CELL`）が生成時に拒否するためgateへ「covered扱い」で到達できない。owner候補は0..N許容だがeffective ownerは各cellちょうど1（ADR 0038 D1、tie／undeclared overlapはmerge kernelが先に拒否）。legacy baseline経路のsurface exportはこれで終了し（D8-2）、fixture移行としてauthoring surfaceへ`v2 request foundation-base-levels`／`v2 request generation`を追加（maskは`V2-18-09`以降seedの合成形状に束縛されるため）、V2-14-01 image fidelity chain fixtureは抽出元画像をhonored mask由来へ移行した。terrain field／tile／block semantic checksum・Release manifest checksum・v1 goldenは不変。`V2-18-11`は intent-conformance E2E portfolio（`integration.v2.conformance`の`IntentConformancePortfolioV2`＋`IntentConformancePortfolioV2Test`）を新設し、**公開済みRelease**（sealed intent／frozen blueprint／ACTUAL land-water sidecar）だけを入力として形状を測定する常設回帰にした。測定は(1) EDGE実測＝published blueprintの`v2.edge-classification` targetをproductionの`EdgeClassificationEvaluatorV2`で再評価、(2) beach↔backshore land連続性＝beachがeffective ownerであるFORESHORE／BACKSHORE cellが全てland・単一land component・mainland所属で、宣言`BACKSHORE_PLAINS` polygonも同一mainlandに載ること（NEARSHOREは全てwater）、(3) breakwater両armのland接続＝arm footprintがbreakwater以外のmainland landへ8近傍接触し、宣言landfall endpoint cellがmainlandにあること。あわせて`coastal-honored-400`／`harbor-cove-64-honored`のEDGE_CLASSIFICATION宣言を`V2-18-03`が暫定SOFT化した状態からHARDへ戻した（north-is-land ≥0.85／south-is-sea ≥0.90、監査fixtureの原意図）。V2-18-04 evaluatorとV2-18-09 macro foundationが揃ったため両fixtureとも実測1.000000で、production exportがEDGE非適合を拒否する状態になった。negativeはpublished rasterのnorth edge band浸水（EDGE不充足を同一経路で検出）、宣言hinterland浸水（連続性measurementの非空虚性）、合成rasterのisolated blob（mainland判定）、および`coastal-honored-400`東armの実測非適合。determinismはlocale／timezone（tr-TR／Pacific/Chatham）とRelease複製後の再測定で固定した。**portfolioは計測専用でproduction経路へgateを追加せず**、terrain field／tile／block semantic checksumは不変（fixtureのEDGE強度変更によりintent／blueprint canonicalChecksumとmanifest容器byteは変化する）。portfolioのPhase gate昇格は`V2-18-12`が行う。本Taskの測定で**`coastal-honored-400`の東breakwater armが宣言landfall（0.61, 0.47）でrocky cape西端（0.62）へ届かず、arm全体が孤立land component（2163 cell）になる**非適合を確定し、fixture geometry是正＋`V2-18-09` mask再生成を要するため新IDの`V2-18-13`へ分離した（隠蔽せずportfolioへ現状を明示pin）。`V2-18-13`は2026-07-23に完了した（`coastal-honored-400`のrocky cape西端をNW `0.62→0.60`／SW `0.72→0.685`へ拡張して東breakwater armのfoundation toeがcape landへ届くようfixture geometryを是正し、land-water maskを`V2-18-09`の合成規則`active ? composed : macro-background`で再生成、孤立2163 cell armをmainland（72852 cell）へ併合。mask sha `8b3f04df…`→`60095de7…`、`RegenerateHonoredCoastalMaskExampleTest`が再生成手順をcurrent geometry byte一致で担保。portfolioは両arm接続へ更新。generator挙動・compositor契約・Release format不変、full `./gradlew test`／`build` PASS）。`V2-18-12`（Phase gate）は2026-07-23に実装・検証を完了した: 新gate test `MacroFoundationPhaseGateV2Test`（`integration.v2.conformance`）がconformance portfolioをPhase gateへ昇格し — 全caseで宣言arm集合＝shore-connected arm集合（登録済み非適合の残存はgate失敗。欠陥は新IDへ分離する）、EDGE HARD充足＋intentのHARD EDGE constraint集合とblueprint target集合の完全一致、beach↔backshore land連続性、全宣言armのlandfall接続、非空虚性（coastal 4 kind＋backshore実在、band／polygon／arm非空）、land-mass会計（mainland外componentは計画sea stack数・footprint上限内）を、gate自身がexportしたpublish済みRelease成果物の再測定で固定（release qualification保証。production fail-closed契約とは区別） — fail-closed export spine契約（`diagnostic-gate-contract-v1`／preflight 3 rule／EDGE evaluator登録／`surface-foundation-owner-gate-v1`）、legacy negative fixture `coastal-fishing-map`のartifact生成前拒否、ADR 0038 D4対応表（60 kind／NORMATIVE 6／PROVISIONAL 54／foundation-eligible 17／`ABYSSAL_PLAIN` modifier／carrier継承）、capability・寸法の無昇格を再検証した。full clean suite PASS、証拠は [V2-18-12 Phase gate audit](audits/v2-18-12-phase-gate.md)。`V2-15-47`／`V2-16-19` Acceptanceへのintent-conformance追記と、V2-15 stage gateの解除判断 — 一律保留を解除し、各production配線leafのper-leaf義務（対象kindのcomposition profile確定（PROVISIONAL→確定、ADR 0038 D4）＋intent-conformance portfolio case追加・全case適合維持）へ転換する — は**2026-07-23の人間承認（不可分のatomic decision）で有効化され、`V2-18-12`は完了、V2-18親Phaseは閉じた**。Track E `V2-15-10`（`RIVER`＋legacy meandering subtype public wiring）は、着手前 Gate 0 の [ADR 0039](../adr/0039-offline-production-route-eligibility.md)（offline production route eligibility）が2026-07-23の人間承認で`Accepted`（候補A採択: `PRODUCTION_CONNECTED`＝Paper込み完全接続＝coastal 4 の意味は不変のまま、offline production route を dispatch `production-dispatch-registry-v1→v2` contract bump で追加する）となったことを受けて2026-07-23に**完了した**（詳細は`V2-15-10`行）。2026-07-23の[横断監査](../audits/cross-cutting-audit-2026-07-23.md)（rev.2、[独立レビュー](../audits/cross-cutting-audit-independent-review-2026-07-23.md)との相互ファクトチェック済み、同日人間承認）を受け、Track A主担当の横断Phase `V2-19`（Input integrity and block materialization、16 Task、§19）を登録した。監査は (1) `V2-15-10`のRIVERがrouting／validation artifactを作るのみでsurface tileを書き直さず**blockを1個も変えない**こと、(2) reference image付き公開designが`buildProviderRequest`の空list構築で必ず失敗すること（BROKEN_PUBLIC）、(3) constraint source→Intent bindingの公開authoring不在、(4) HEIGHT_GUIDEのconsumer不在＋「map正確に1枚」制約によるmacro foundation標高の2定数flat固定、(5) filesystem inventory testのGradle input追跡漏れ、を確定した。V2-15の公開配線leaf（`V2-15-11`以降のproduction wiring系）に適用された新stage gate `V2-19-01`（semantic materialization gate）は、**2026-07-23に実装・検証を完了し、同日の人間承認（V2-15／16 Acceptance遡及変更のatomic decision）で完了・解除された**: §15.1／§16.1のsemantic materialization義務（対象Featureのblock effect class宣言＋final canonical block streamからの非空効果・形状conformance portfolio case、plan-only metricとblock metricの別欄化、意図的no-opのFeature昇格使用不可）が発効し、`FeatureMaterializationV2`のgate契約はblock効果なしのV2-15-10 RIVER実routeをnegative fixtureとして必ず拒否することをpinした。support列と公開dispatch到達性は`public-dispatch-reachability-v1`で別軸表示となった（詳細は§19.2 `V2-19-01`行）。**`V2-19-02`（Gradle test input修正）は2026-07-23に完了した（走査rootを`test` taskのinputへ宣言し、cache有効／無効で同一判定になることを実測固定。判定規則は不変。詳細は§19.2 `V2-19-02`行）。`V2-19-03`（reference-image public design修理）も2026-07-23に完了し、BROKEN_PUBLICは解消した（宣言画像のsecure準備→provider handoff、実CLI／Paper E2E、任意`expectedSha256`。詳細は§19.2 `V2-19-03`行）。`V2-19-04`（generic constraint source＋Intent binding authoring）も2026-07-23に完了し、3 role共通のsource宣言verbとIntent binding生成・確認verbでsource→binding→compile→consumerが公開command surfaceだけで通るようになった（詳細は§19.2 `V2-19-04`行）。`V2-19-05`（RIVER block materialization）も2026-07-23に完了し、監査item 1のplan-only配線は解消した（tile書出し前のglobal route freeze、`CARVE_SOLID`→`ADD_FLUID`の責務分離、宣言interactionのfail-closed化、final tile streamからのbed depth／water continuity／source-mouth reachability／leak envelope実測。`RIVER`／`MEANDERING_RIVER`の表示は`PLAN_ONLY`→`MATERIALIZED`。詳細は§19.2 `V2-19-05`行）。`V2-19-06`（HEIGHT_GUIDE macro foundation consumer）も2026-07-23に完了し、監査item 4は解消した（guide＞medium base levelの優先順位でbackground elevationを決め、no-dataはbase levelへfallback。surface exportのconstraint map要求はrole別＝`LAND_WATER_MASK`ちょうど1＋`HEIGHT_GUIDE`任意1へ緩和、desired／actual／residual heightを入力guideへ束縛しconformance `RasterResidual`を有効化。ADR 0038 D2-2の範囲内でamendment不要。詳細は§19.2 `V2-19-06`行）。現在の次TaskはTrack A `V2-19-07`であり、Track E `V2-15-11`（`LAKE`）は新Acceptanceの下で再開できる。Track Aの残りはV2-19（07〜16）とV2-17（`V2-15-47`／`V2-16-19`完了待ち）である。** この文書はTask ScopeとAcceptance gateの正本、進捗状態の正本は [docs/roadmap.md](../roadmap.md)、モデル割当は [model-assignment.md](model-assignment.md) である。

## 1. 読み方

各Taskは [Task Execution Guide](task-execution-guide.md) を継承し、記載されたTaskだけを1回の作業で閉じる。`主変更` は主対象であり、Acceptanceに必要なtest、Schema、example、ADR、docsの同期を禁止しない。`D/M/S` は決定性、memory、securityの追加条件である。原則としてすべてのTaskでv1 Schema、generator `3.0.0-phase6`、Release format 1、placement、Undoを凍結する。ただし [ADR 0035](../adr/0035-v1-retirement-governance.md) が `Accepted` であり、同ADR D1および対象行のD2a条件を満たした`V2-12-05`／`V2-12-06`に限り、ADRが列挙した範囲の変更を許可する。ADR 0035 D0のとおり、本Indexは実行ScopeとAcceptance gateの正本、ADR 0035は削除・改名を許可するgovernance境界の正本であり、両者が矛盾する場合はより厳しい制約を適用して停止し、同期するまで削除・改名を実行しない。

| Track | Phase | Task数 | 最初 | 親Phaseを閉じるTask |
|---|---|---:|---|---|
| A | V2-2 Coastal 2.5D | 12 | `V2-2-01` | `V2-2-12` |
| A | V2-3 Hydrology | 15 | `V2-3-01` | `V2-3-15` |
| A | V2-4 Environment | 15 | `V2-4-01` | `V2-4-15` |
| A | V2-5 Sparse volume | 18 | `V2-5-01` | `V2-5-18` |
| A | V2-6 Placement | 21 | `V2-6-01` | `V2-6-19` |
| A | V2-11 Capability promotion | 6 | —（6 Task完了） | — |
| A | V2-12 V2 production path／v1 migration | 11 | `V2-12-01` | `V2-12-07`（閉了） |
| B | V2-7 Image fidelity | 7 | `V2-7-01` | `V2-7-07` |
| C | V2-8 Scale-up | 8 | `V2-8-01` | `V2-8-08`（`V2-8-03`〜`08`はS2決定で保留） |
| C | V2-13 MEDIUM 1024 enablement／measurement | 6 | —（6 Task完了。`V2-13-06`は2026-07-22実機evidence人間承認済み） | —（Phase gate Task未設定、親Phase完了宣言は別の人間承認要） |
| B | V2-14 Image fidelity wiring follow-up | 3 | —（01〜03完了） | —（Phase gate Task未設定、完了宣言は人間承認要） |
| D | V2-9 Terrain foundation | 14 | `V2-9-01` | `V2-9-14` |
| E | V2-10 Deferred terrain families | 11 | `V2-10-01` | `V2-10-09` |
| E | V2-15 Canonical catalog／existing-generator wiring | 47 | `V2-15-01` | `V2-15-47` |
| E | V2-16 Deferred／new terrain and composition | 19 | `V2-16-01`（`V2-15-47`待ち） | `V2-16-19` |
| A | V2-17 Paper placement evidence／promotion | 7 | `V2-17-01`（`V2-15-47`／`V2-16-19`待ち） | `V2-17-07` |
| A | V2-18 Macro foundation／intent conformance | 13 | `V2-18-01` | `V2-18-12` |
| A | V2-19 Input integrity／block materialization（横断、B／E leaf含む） | 16 | `V2-19-01` | `V2-19-16` |

合計247 Taskである（従来144 Task＋`V2-14-03`＋V2-15の47 Task＋V2-16の19 Task＋V2-17の7 Task＋V2-18の13 Task＋V2-19の16 Task）。Task追加時は既存IDを振り直さず、該当Phaseへ新しい未使用IDを追加する。Track間は依存を明示したTaskだけが待ち、それ以外は並行実行できる。ただし同一ファイルを複数エージェントが同時編集してはならず、`format.v2.release`等の共有領域を変更するTaskは直列にする。

## 2. V2-2 Coastal 2.5D vertical slice

### V2-2-01 Coastal field／geometry／module foundation

- **状態:** 完了。4 coastal featureのcanonical plan、block millionths geometry、明示coast-side、初期3 descriptor field、built-in module／stage／halo／seed／`SINGLE_OWNER`契約、strict rejectionとv1 golden回帰を確認した。rasterと個別generatorは含まない（field集合は`V2-2-02`で後方互換に拡張）。

- **目的／前提:** coastal featureが共有するcompile契約とfield ownershipを1本化する。前提はV2-1 gate完了。
- **Scope／成果物:** coast splineのblock座標化、coast-side、signed distance、nearshore profile descriptor、built-in coastal module interface、stage／halo／seed／merge宣言を実装し、Azure Coastの4 featureを実行planへcompileできる `EXPERIMENTAL` 基盤を作る。
- **非Scope:** 個別beach／harbor／cape形状、transition、validator、preview、schematic、Release 2。
- **主変更:** `model.v2`、`core.v2` compiler、`generator.v2.coast`、TerrainIntent／WorldBlueprint v2 Schema、coastal example、pipeline／taxonomy docs。
- **必須test／D/M/S:** strict parameter／relation、self-intersection／範囲外／owner conflict、module登録順／locale／timezone／named seed不変、field／point／halo budget事前拒否、未知kind／merge／future version拒否。
- **Gate／docs／次／停止:** 4 feature planとfield DAGがcanonical round-tripしv1 goldenが不変なら完了し、roadmap・pipeline・taxonomy・data modelを更新して `V2-2-02` へ進める。既存contractで一意なowner／mergeを定義できない、または新formatが必要なら停止する。

### V2-2-02 Coast raster and nearshore kernels

- **状態:** 完了。integer-only spline flatten／raster、land-positive signed distance、land-oriented normal、nearshore depth、HARD mask window adapter、core最大256＋halo最大64のbounded window、row-major field checksumを実装し、whole／tile／逆順／thread／locale／timezone一致、1000角bounded peak、strict LFC read/write連携とv1 golden不変を確認した。個別feature shapingは含まない。

- **目的／前提:** coast splineからtile共通のland-water、distance、normal、nearshore基礎fieldを決定論的に作る。前提は`V2-2-01`。
- **Scope／成果物:** fixed-point spline raster、signed distance／normal、hard LAND_WATER_MASK保護、window／halo API、whole／tile共通samplerを実装する。
- **非Scope:** feature固有地形、material、transition、Release。
- **主変更:** `generator.v2.coast`, field reader／writer連携、coastal kernel docs。
- **必須test／D/M/S:** straight／curved／boundary coast golden、pixel-center mapとの整合、whole／tile／seam／tile逆順／thread不変、1000角bounded windowとstage peak、overflow／degenerate spline／過大support拒否。
- **Gate／docs／次／停止:** hard maskを1 cellも上書きせず全field checksumが実行形態で一致すれば完了し、pipeline／current limitationsを更新して `V2-2-03` へ進める。global dense working setまたはplatform floatを正本にする必要があれば停止する。

### V2-2-03 SANDY_BEACH

- **状態:** 完了。endpoint taperによる幅profile、version固定integer tangentのshore slope、foreshore／backshore／nearshore band、semantic sand field、`SandyBeachPlanV2`、bounded window／canonical checksum／単体hard metricを実装した。HARD mask conflict、短すぎるendpoint fixture、幅／vertical／数値／budget corruptionを拒否し、whole／tile／seam／thread／locale／timezoneとv1 golden不変を確認した。featureは`EXPERIMENTAL`のままで、独立validator／previewは`V2-2-08`に残す。

- **目的／前提:** `SANDY_BEACH`だけを生成可能な`EXPERIMENTAL` featureにする。前提は`V2-2-02`。
- **Scope／成果物:** width profile、shore slope、backshore／foreshore、nearshore depth、semantic sand帯のcompiler／generatorを実装する。
- **非Scope:** harbor、breakwater、rocky cape、transition、`SUPPORTED`昇格。
- **主変更:** `generator.v2.coast.beach`、feature parameter Schema、Azure fixture、beach docs。
- **必須test／D/M/S:** width／slope／depth positive、幅不足／hard mask conflict／端部corruption、whole／tile／seam／thread不変、halo／field budget、NaN相当・overflow・未知parameter拒否。
- **Gate／docs／次／停止:** beach単体のhard metricを満たしnegative fixtureが失敗しv1不変なら完了し、taxonomy／validation設計／roadmap証拠を更新して `V2-2-04` へ進める。共通kernelの意味変更が必要なら先に`V2-2-02`を再openする。

### V2-2-04 HARBOR_BASIN

- **状態:** 完了。HARD `ENCLOSES` relationと同一の2 named endpointをouter polygonの1 opening edgeへ解決し、inner／outer／entrance corridor、`EDGE_TO_CENTER_LINEAR` bottom profile、water membership／depth／bottom heightをinteger-onlyで生成する`HarborBasinPlanV2`と4 fieldを実装した。closed entrance／寸法不足／HARD land conflict／field・halo budgetを拒否し、positive／独立negative fixture、whole／tile／seam／thread／locale／timezone、1000角bounded window、v1 golden不変を確認した。featureは`EXPERIMENTAL`のまま、独立validator／previewは`V2-2-08`に残す。

- **目的／前提:** `HARBOR_BASIN`の水域とnavigable depthを単独で生成する。前提は`V2-2-02`。
- **Scope／成果物:** polygon basin、inner／outer classification、entrance corridor、bottom profile、water／depth field ownershipを持つ`EXPERIMENTAL` generatorを実装する。
- **非Scope:** breakwater arm、beach／cape transition、hydrology v2、Release。
- **主変更:** `generator.v2.coast.harbor`、feature parameter／relation Schema、harbor fixture／docs。
- **必須test／D/M/S:** basin depth／opening corridor／land exclusion、closed entrance／dimension不足／hard mask conflict、whole／tile／seam／thread不変、polygon／depth／artifact budget、unknown relation拒否。
- **Gate／docs／次／停止:** basin hard metricsとcorruption detectionが独立fixtureで通れば完了し、taxonomy／validation／roadmapを更新して `V2-2-05` へ進める。lake／river graphを暗黙導入する必要があれば停止する。

### V2-2-05 BREAKWATER_HARBOR

- **状態:** 完了。2本のnamed armをfixed-gridでflattenし、ID順のstable arm order、`FLAT` crest、`LINEAR_SIDE_SLOPE` foundation、明示run/rise、clear edge-to-edge openingを`BreakwaterHarborPlanV2`へfreezeした。HARD `ENCLOSES` relationとbasin entranceを同一endpoint／座標へ結合し、region／arm index／top／bottomの4 field、bounded window、streaming arm／opening／solid-block metricを実装した。交差arm、閉鎖opening、過大support、未知subgeometryを拒否し、whole／tile／seam／thread／module順／locale／timezone、memory／halo／block budget、strict round-trip、v1回帰を確認した。featureは`EXPERIMENTAL`のままで、独立validator／previewは`V2-2-08`に残す。

- **目的／前提:** `BREAKWATER_HARBOR` armとopeningを`HARBOR_BASIN`へ接続する。前提は`V2-2-04`。
- **Scope／成果物:** multi-spline arm、crest／foundation profile、opening、basin `ENCLOSES` relation、stable arm orderを実装する。
- **非Scope:** small structure asset化、Paper配置、fluid simulation、他coast transition。
- **主変更:** `generator.v2.coast.breakwater`、relation／parameter Schema、Azure fixture、coastal docs。
- **必須test／D/M/S:** arm length／crest／opening／basin connection、交差arm／閉鎖opening／unsupported depth、whole／tile／thread／module順不変、foundation halo／block stream budget、未知subgeometry拒否。
- **Gate／docs／次／停止:** breakwaterとbasinのhard relation・metricが通りcorruptionを検出すれば完了し、taxonomy／validation／roadmapを更新して `V2-2-06` へ進める。structure catalogへ逃がす必要がある場合は停止する。

### V2-2-06 ROCKY_CAPE

- **状態:** 完了。`RockyCapePlanV2`へ2.5D-only profile、cardinal seaward side、cliff／relief／露岩、最大4 channel／12 sea-stackの有界descriptorと20 block以下のfixture supportをfreezeした。region／surface height／rock exposure／descriptor indexの4 field、bounded window、streaming relief／rock ratio／turning metricを実装した。HARD mask conflict、薄いland bridge、孤立stack、未知parameter、`LOCAL_VOLUME_REQUIRED`をstable ruleで拒否し、whole／tile／seam／thread／locale／timezone、descriptor／halo／memory、strict round-tripとv1回帰を確認した。full 3D stack、overhang、sea caveはV2-5まで非対応、featureは`EXPERIMENTAL`のままである。

- **目的／前提:** `ROCKY_CAPE`の2.5D relief、cliff、露岩、sea stackを生成する。前提は`V2-2-02`。
- **Scope／成果物:** cape polygon、cliff band、rock exposure、bounded channel／sea-stack descriptorの2.5D subsetを実装する。
- **非Scope:** overhang／sea cave／full 3D sea stack、geology v2、transition、`SUPPORTED`昇格。
- **主変更:** `generator.v2.coast.cape`、parameter Schema、cape fixtures／docs。
- **必須test／D/M/S:** relief／rock ratio／coast complexity、孤立stack／薄いland bridge／mask conflict、whole／tile／seam／thread不変、descriptor count／halo budget、3D fallbackやunknown parameter拒否。
- **Gate／docs／次／停止:** 2.5D metricとcorruption fixtureが通り3D要求を診断できれば完了し、taxonomy／current limitations／roadmapを更新して `V2-2-07` へ進める。局所volumeが必須ならV2-5まで非対応として停止する。

### V2-2-07 Coastal feature transition compositor

- **状態:** 完了。`ADJACENT_TO`／`OVERLAPS`のversion付き`PRIORITY_BLEND` bandをIntentで明示し、feature priority、canonical owner index、pair interaction、HARD cell／ambiguity policy、breakwater-over-basin seamを`CoastalTransitionPlanV2`へfreezeした。専用built-in module／stage、land-water／surface-height／owner-index／blend-weight／conflictの5 field、integer-only compositor、既存4 generator adapter、bounded windowとstable conflict diagnosticを実装した。未契約overlap、equal categorical vote、HARD-HARD conflict、future policy、halo／memory budgetを拒否し、module／layer順、whole／tile／seam／thread／locale／timezone不変を確認した。featureは`EXPERIMENTAL`のままである。

- **目的／前提:** beach、harbor、cape境界のfield ownership衝突を明示合成する。前提は`V2-2-03`〜`V2-2-06`。
- **Scope／成果物:** transition band、priority blend、protected hard cells、connection seam、conflict diagnosticsを持つ専用compositorを実装する。
- **非Scope:** 新feature、generic全地形compositor、validator／preview、Release。
- **主変更:** `generator.v2.composition`／`coast`、Blueprint ownership／merge Schema、Azure interaction fixture、pipeline docs。
- **必須test／D/M/S:** beach↔harbor↔cape順列、zero／overlap band、HARD-HARD conflict、whole／tile／module登録／thread不変、band halo／working memory、last-write-wins禁止とbudget拒否。
- **Gate／docs／次／停止:** 全module順列で同checksum、hard cell不変、曖昧非可換衝突拒否なら完了し、pipeline／taxonomy／roadmapを更新して `V2-2-08` へ進める。stable merge ruleを契約化できなければ停止する。

### V2-2-08 Coastal validators and diagnostic previews

- **状態:** 完了。`CoastalValidatorV2`はgeneratorのtask-local `evaluate()`／private stateに依存せず、freeze済みBlueprintとbounded `CoastalFieldSamplerV2`だけをrow-majorで測定する。beach width、harbor depth／entrance、breakwater opening、cape exposure／descriptor complexity、transition conflict、desired/actual residualをHARD metric／structured issueへ変換した。compilerはcoastal feature用`ValidationTargetV2`と`v2.coast.validation-preview` stage capabilityをfreezeする。`CoastalDiagnosticPreviewRendererV2`は4 feature overlay、desired／actual／residual land-water・height、constraint errorの11固定PNGを1枚ずつrenderし、strict `coastal-preview-index-v2.schema.json`／checksum／directory entry read-backを完了してからatomic publishする。V2-1のsecure PNG source envelopeをread-backへ再利用し、corruption、path／entry、PNG byte budget、checksum、cancel cleanupをtestで確認した。featureは`EXPERIMENTAL`のままである。

- **目的／前提:** coastal featureの成功・失敗を独立に測定し診断可能にする。前提は`V2-2-07`。
- **Scope／成果物:** beach width、harbor depth/opening、cape rock/complexity、relation／transition validator、feature overlays、desired／actual／residual、constraint error previewとindex metadataを実装する。
- **非Scope:** generator修正の抱き合わせ、Release container、Paper UI。
- **主変更:** `validation.v2.coast`、`preview.v2` registry、ValidationTarget／preview index Schema、positive／corruption fixtures、validation docs。
- **必須test／D/M/S:** deliberate width／connection／depth／complexity corruption、metric stable reduction、preview fixed palette／dimension／checksum、1枚ずつbounded render、path／entry／PNG budgetとcancel cleanup。
- **Gate／docs／次／停止:** generatorと独立したcorruption fixtureを全件検出しpreview index strict read-backが通ったため完了。validation docs／taxonomy／roadmapを更新し、次は `V2-2-09` である。validatorがgenerator内部値しか読めない場合は停止する。

### V2-2-09 Offline tile schematic export

- **状態:** 完了。`OfflineTilePlanV2`とstrict `offline-tile-artifact-v2` metadataを追加し、final `TerrainBlockResolver`を二走査してpalette countだけを保持する`OfflineTileSchematicWriterV2`を実装した。canonical streamはrelease-local boundsをheaderにbindingし、X fastest→Z→Yのblock-state UTF-8列をSHA-256化する。Sponge v3 paletteは辞書順、Dataはgeneral VarInt 0..16383で、staging NBTのversion／dimension／offset／palette／entry／semantic checksumをstrict read-backしてからatomic publishする。V2-2 coastal allowlist外state、非canonical state、resolver二走査差、truncated／checksum／path／artifact／palette／decode budget、cancelを拒否する。WorldEdit 7.3.19のversion分離adapterで同じcanonical checksumを再計算し、tile正逆順／thread／locale／timezone、air／water／structureを含むstream、v1 golden／Release 1回帰を確認した。Release 2とPaperへは接続していない。

- **目的／前提:** final `TerrainBlockResolver`からcoastal tileをstreaming Sponge v3へ出す。前提は`V2-2-08`。
- **Scope／成果物:** v2 offline tile plan、stable XYZ block-state stream、general VarInt palette writer、tile semantic checksum、WorldEdit offline read-backを実装する。
- **非Scope:** Release 2 container、Paper apply、volume／fluid特殊処理、v1 writer変更。
- **主変更:** `format.v2.tile`、`worldedit`のversion分離adapter、tile artifact model／Schema、artifact-format docs。
- **必須test／D/M/S:** palette 127／128／16383境界、air／water／structure順、whole／tile block stream一致、tile順／thread不変、streaming memory／artifact／palette budget、truncated／unknown block／checksum改変拒否。
- **Gate／docs／次／停止:** WorldEdit 7.3.19 offline read-backとcanonical block checksum一致、Release 1 writer回帰不変を確認したため完了。artifact／pipeline／roadmapを更新し、次は `V2-2-10` である。全block Listとv1 format変更は導入していない。

### V2-2-10 Release format 2 core

- **状態:** 完了。v1とは別packageに`ReleaseManifestV2`とstrict `release-manifest-v2` Schemaを追加し、canonical exclude-self checksumを持つ`manifest.json`、`artifacts[]`、`requiredCapabilities[]`を固定した。V2-2-10ではpayload capabilityを有効化せず、core-only releaseは空のindexだけを許可する。compile-time catalogは`surface-2_5d`とその候補artifact type/versionを予約として認識するが、manifestに現れたcapability／artifact、unknown type/version/capabilityを全て拒否する。directory／ZIP publisher/verifierはstaging self-verify、atomic publish、strict index、path／symlink／ZIP traversal／case collision／checksum、entry／展開／resident／disk budget、cancel cleanupを検査する。core directory／ZIP deterministic round-trip、corruption、v1 golden／Release 1 verifier回帰を確認した。Paper、Release 1変更、`surface-2_5d`有効化は含まない。

- **目的／前提:** capability非依存のRelease 2 containerとstrict indexを導入する。前提は`V2-2-09`。
- **Scope／成果物:** format 2 manifest、`artifacts[]`、artifact type/version catalog、`requiredCapabilities[]`、directory／ZIP publisher/verifier、staging self-verify／atomic publishを実装する。
- **非Scope:** `surface-2_5d`有効化、Paper placement、Release 1 allowlist変更、hydrology／environment／volume capability。
- **主変更:** `format.v2.release`、manifest／artifact index Schema、新ADR、release examples、artifact／security／migration docs。
- **必須test／D/M/S:** core directory／ZIP round-trip、unknown type/version/capability、missing／extra／duplicate path、ZIP traversal／bomb／checksum改変、canonical order不変、entry／expand／resident／disk budgetとcancel cleanup。
- **Gate／docs／次／停止:** empty-capability coreをstrict self-verifyしRelease 1 verifier／v1 golden回帰が通ったため完了した。ADR／artifact／migration／roadmapを更新し、次は `V2-2-11` である。shared mutable allowlistは導入していない。

### V2-2-11 Release 2 `surface-2_5d` capability

- **状態:** 完了。`surface-2_5d`だけをRelease format 2の非empty capabilityとして有効化した。manifestはrequest／intent／Blueprint、constraint field indexと全`LFC_GRID_V1` sidecar、sealed coastal validation、preview indexと固定11 PNG、少なくとも1個のtile metadata／Sponge v3 schematicを全file個別descriptorで持つ。`ReleaseSurfacePublisherV2`はsourceをstagingへcopyし、directory／ZIP双方をread-backしてatomic publishする。strict verifierはschema／version、index-external file、checksumに加えてrequest→intent→Blueprint、Intent binding→field、Blueprint→validation／preview／tile、tile metadata→canonical block stream、全X/Z tile coverageを検査する。
- **目的／前提:** coastal offline artifactsだけをRelease 2 capabilityとして有効化する。前提は`V2-2-08`〜`V2-2-10`。
- **Scope／成果物:** `surface-2_5d`必須artifact集合、Blueprint／field／validation／preview／tile Schema versions、semantic checksum binding、directory／ZIP strict verifierを実装する。
- **非Scope:** hydrology-plan、environment-fields、sparse-volume、Paper placement、feature `SUPPORTED`宣言。
- **主変更:** `format.v2.release` capability catalog、Release Schema／examples、artifact-format／migration／security docs。
- **必須test／D/M/S:** complete coastal release、各必須file欠損、extra／version／capability／tile checksum改変、directory／ZIP parity、artifact順不変、decode／entry／palette／preview budget、future capability拒否。
- **Gate／docs／次／停止:** core-onlyとsurface releaseを双方strict verifyし、未知capabilityへfallbackせず、missing／extra／future version／tile semantic tampering、directory／ZIP parity、artifact order、cancel cleanup、Release 1回帰が通ったため完了した。ADR 0013、artifact／migration／security／roadmapを更新した。次は`V2-2-12`だけであり、4 featureを`SUPPORTED`へ昇格させない。capability必須集合を一意に固定できなければ停止する条件は解消済みである。

### V2-2-12 Azure Coast integration fixture and Phase gate audit

- **状態:** 完了。400×400 Azure Coast fixtureで4 feature、HARD land-water sidecar、transition、field-only validation、固定11 preview、whole／tile block stream、16個のoffline Sponge v3 tile、代表3 tileのWorldEdit 7.3.19 read-back、`surface-2_5d` directory／ZIP self-verifyを統合した。forward／reverse tile順、1／4 thread、module逆順、locale／timezone、1000角window admission、cancel／disk／tamper拒否を再検証し、full suiteでv1 golden、Release 1、配置／Undo回帰を維持した。4 coastal kindとcoastal foundation／transition／validation-preview module、offline `surface-2_5d` capabilityを`SUPPORTED`とし、V2-2 Phase gateを閉じた。Paper applyとBeta実機項目は有効化していない。監査証拠は [V2-2 Phase gate audit](audits/v2-2-phase-gate.md) を参照する。V2-2完了時点の次Taskは`V2-3-01`だった。

- **目的／前提:** V2-2全体を統合し親Phase gateを閉じる。前提は`V2-2-01`〜`V2-2-11`。
- **Scope／成果物:** Azure Coast end-to-end fixture、4 feature lifecycle監査、whole／tile block stream、coastal validation／preview、offline schematic、Release 2 directory／ZIP self-verify、Phase audit reportを実行する。
- **非Scope:** 新機能追加、Hydrology v2、Paper apply、Beta hardening実機項目。
- **主変更:** integration／compatibility tests、audits、roadmap、implementation roadmap、READMEの実装状態だけ。production修正は小規模なgate defectに限定する。
- **必須test／D/M/S:** Phase全positive／corruption／tampering、tile正逆／thread／module順／locale／timezone、1000角peak admission、cancel／disk／artifact cleanup、v1全golden／Release 1／placement／Undo回帰、clean build。
- **Gate／docs／次／停止:** 全Acceptanceが証拠付きで通ったため4 featureと`surface-2_5d`を`SUPPORTED`、V2-2を完了し、Nextを`V2-3-01`とした。大規模修正、未解決risk、未計測budgetは残していない。

## 3. V2-3 Global hydrology and regional landforms

### V2-3-01 Hydrology IR and fixed priors

- **状態:** 完了。`HydrologyPlanV2` version 1にbasin／node／reach／water body／fallのstable-ID graph、strict endpoint／cycle／range検査、V2-3限定のchecksum付き`UNIFORM_GEOLOGY_PRIOR`／`CONSTANT_RUNOFF_PRIOR`、6 semantic fieldのsingle-owner binding、graph／CPU／resident budgetを固定した。`v2.hydrology.ir`をcompile-time catalogと`compile.hydrology-ir` stageへ追加し、通常compilerはrouting前のempty planをBlueprintへsealする。standalone Schema／example／codec、minimal river／waterfall graph、future version／prior／checksum／field conflict／budget corruption、canonical order／locale／timezone／1000角admissionを検証した。routing、raster、feature、Release capabilityは実装していない。

- **目的／前提:** global hydrologyのversion付きgraph契約とV2-3限定priorを固定する。前提はV2-2 gate完了。
- **Scope／成果物:** basin／node／reach／water body／fall plan、stable ID、`UNIFORM_GEOLOGY_PRIOR`、`CONSTANT_RUNOFF_PRIOR`、field ownership、graph／work budgetをBlueprintへcompileする。
- **非Scope:** routing solve、river raster、個別feature、V2-4 geology/climate。
- **主変更:** `model.v2.hydrology`、`core.v2` compiler、HydrologyPlan Schema、module catalog、hydrology／Blueprint docs。
- **必須test／D/M/S:** strict graph contract、ID／endpoint／cycle／range／future version、canonical order／locale／timezone、node／edge／field／CPU／resident budget、unknown prior／hard conflict拒否。
- **Gate／docs／次／停止:** empty/minimal planのcanonical round-tripとfixed prior version/checksum、strict graph／budget rejectionを確認したため完了。hydrology／Blueprint／pipeline／roadmapを更新し、次は`V2-3-02`である。V2-4 fieldは先取りしていない。

### V2-3-02 Deterministic basin and routing solver

- **状態:** 完了。全域のinteger provisional surfaceと明示`BOUNDARY`／`HARD` outletを入力に、priority `(spill elevation, global cell ID)`でD8 forestを確定する`hydrology-priority-flood-v1`を実装した。outlet候補をglobal Z／X／ID順へ正規化し、stable basin ID、flow direction U8、flow accumulation I32、graph／field／routing checksumをfreezeする。2個の`LFC_GRID_V1` sidecarとstrict indexはstagingでsemantic reachability／checksum／exact file setをbounded read-backしてからatomic publishする。bowl／flat／multiple outlet／boundary、candidate／tile／thread／locale／timezone不変、1000×1000でpeak 40 MiB未満・retained 6 MiB未満、blocked hard outlet／disconnected component／overflow／budget／cancel／tamperingを検証した。river shaping、lake、delta、reconciliation、Release capabilityは含まない。

- **目的／前提:** tile化前にbasin、outlet、flow direction／accumulationを全域solveする。前提は`V2-3-01`。
- **Scope／成果物:** provisional surface、priority routing、stable cell-ID tie-break、basin/routing artifact、global freezeとbounded readerを実装する。
- **非Scope:** feature別channel shaping、lake、delta、reconciliation。
- **主変更:** `generator.v2.hydrology.core`、graph／field artifact、solver tests、hydrology docs。
- **必須test／D/M/S:** bowl／flat／multiple outlet／boundary fixtures、route reachability、candidate／tile／thread順不変、compact graphと1000角peak、overflow／unroutable hard outlet／graph budget／cancel拒否。
- **Gate／docs／次／停止:** 同じsurface/priorでgraph／field checksumが全実行順で一致し、strict read-backと1000角budgetを満たしたため完了。hydrology／pipeline／Schema／example／roadmapを同期し、次は`V2-3-03`である。tile-local routingやunbounded iterationは導入していない。

### V2-3-03 River and meandering river

- **状態:** 完了。`MEANDERING_RIVER`にtyped parametersと`RIVER`／`MEANDERING_RIVER` variantを追加し、source→mouth reach、monotonic bed、width／discharge、bank／floodplain、bounded meander corridor、raster fields、EXPERIMENTAL validator／confluence hooksを実装した。逆勾配／孤立reach／幅conflict／HARD route conflict／budgetを拒否し、whole／tile／seam／thread／candidate順／locale／timezone不変を確認した。lake、canyon、waterfall、delta、full validator bundle、`SUPPORTED`昇格は含まない。

- **目的／前提:** `RIVER`と同じreach contractを使う`MEANDERING_RIVER` variantを生成する。前提は`V2-3-02`。
- **Scope／成果物:** source→mouth reach、bed profile、width／flow、bank／floodplain、bounded meander corridor、rasterと`EXPERIMENTAL` validators hooksを実装する。
- **非Scope:** lake、canyon、waterfall、delta、full validator bundle、`SUPPORTED`昇格。
- **主変更:** `generator.v2.hydrology.river`、feature parameter／relation Schema、river fixtures／docs。
- **必須test／D/M/S:** reachability／monotonic bed／confluence flow／meander bounds、逆勾配／孤立reach／width conflict、whole／tile／seam／thread／candidate順不変、reach／halo／raster budget、hard route conflict拒否。
- **Gate／docs／次／停止:** river variantsのgraphとfield metricが通りnegative fixtureを検出したため完了。taxonomy／hydrology／pipeline／roadmapを更新し、次は`V2-3-04`である。meanderはcorridor shapingに限定し、graph意味は変えていない。

### V2-3-04 Lake, rim, and spillway

- **目的／前提:** independent `LAKE` basinとlevel、rim、inlet/outlet、spillwayを実装する。前提は`V2-3-02`。
- **Scope／成果物:** basin fill、single surface level、rim saddle、spill reach、depth profile、river graph接続を実装する。
- **非Scope:** dam、reservoir、delta、wetland ecology。
- **主変更:** `generator.v2.hydrology.lake`、lake parameter／relation Schema、lake fixtures／docs。
- **必須test／D/M/S:** lake level／rim／spill／inlet-outlet、leaking rim／multiple ambiguous spill／逆流、whole／tile／thread不変、basin queue／depth field budget、unbounded fill／hard conflict拒否。
- **Gate／docs／次／停止:** lake metricとdownstream reachが整合しcorruptionを検出したため完了。hydrology／taxonomy／pipeline／roadmapを更新し、次は`V2-3-05`である。dam／reservoir構造は非Scopeのまま導入していない。

### V2-3-05 Canyon and river skeleton

- **目的／前提:** `CANYON`断面とshared river centerlineを2.5Dで統合する。前提は`V2-3-03`。
- **Scope／成果物:** rim/floor幅、V/U profile、terrace、river bed ownership、`WITHIN` relation compilerを実装する。
- **非Scope:** strata material、volume ledge、waterfall、full validator bundle。
- **主変更:** `generator.v2.landform.canyon`、interaction compiler、parameter／relation Schema、canyon fixtures／docs。
- **必須test／D/M/S:** canyon floor/rim/river containment、crossing／disconnected centerline／thin wall、whole／tile／seam／thread不変、cross-section halo／elevation budget、owner conflict拒否。
- **Gate／docs／次／停止:** canyon＋river hard relationとbed monotonicityが通りcorruptionを検出したため完了。taxonomy／hydrology／pipeline／roadmapを更新し、次は`V2-3-06`である。形状は固定geology priorで成立させ、strata／volume／waterfallは非Scopeのままである。

### V2-3-06 Waterfall graph and 2.5D shaping

- **目的／前提:** `WATERFALL` fall node、lip、drop、base、plunge poolの2.5D部分を実装する。前提は`V2-3-03`、canyon scenarioは`V2-3-05`。
- **Scope／成果物:** upstream/downstream reach split、allowed bed discontinuity、lip/base elevation、plunge pool、`ON_PATH_OF` relationを実装する。
- **非Scope:** falling water column、behind-fall cavity、volume fluid、final material。
- **主変更:** `generator.v2.hydrology.waterfall`、fall node Schema、fixtures／docs。
- **必須test／D/M/S:** drop／lip／base／plunge continuity、off-path fall／uphill base／zero roof clearance diagnostic、whole／tile／thread不変、node／pool halo budget、unsupported volume要求を明示診断。
- **Gate／docs／次／停止:** 2.5D metricが通り、`behindFallClearanceBlocks>0`をV2-5 deferred（zero roof clearance）として拒否できたため完了。hydrology／taxonomy／pipeline／roadmapを更新し、次は`V2-3-07`である。falling water column／behind-fall cavityは導入していない。

### V2-3-07 Delta

- **状態:** 完了。HARD `DRAINS_TO` trunkとHARD SEA `EMPTIES_INTO` boundaryを明示bindingし、apexからstable mouthへ分岐するflow-conserving DAG、低起伏fan、sandbar、shallow receiving sea、7 descriptor fieldをcanonical `DeltaPlanV2`へfreezeした。dead branch／loop／landlocked mouth／flow破損／HARD outlet conflictを独立hookで拒否し、whole／tile／順序／thread／locale／timezone、memory／CPU admission、cancelを確認した。tidal channel／mangrove／sediment geologyは含まない。

- **目的／前提:** `DELTA` distributary DAGとsediment fanを実装する。前提は`V2-3-03`と海岸field。
- **Scope／成果物:** trunk split、branch count、low-relief fan、sandbar／shallow receiving sea、全active mouthのmarine reachabilityを実装する。
- **非Scope:** tidal channel network、mangrove、sediment geology全般。
- **主変更:** `generator.v2.hydrology.delta`、delta parameter／relation Schema、delta fixtures／docs。
- **必須test／D/M/S:** branch count／fan relief／mouth reachability／flow conservation、dead branch／loop／landlocked mouth、whole／tile／thread／candidate順不変、branch／raster／CPU budget、hard outlet conflict拒否。
- **Gate／docs／次／停止:** distributary hard metricsとcorruption fixtureが通ったため完了。hydrology／taxonomy／pipeline／roadmapを更新し、次は`V2-3-08`である。潮汐双方向edgeは導入せず次Taskへ分離した。

### V2-3-08 Tidal channel network

- **状態:** 完了。`BIDIRECTIONAL` edge kind、HARD `EMPTIES_INTO`＋明示HARD SEA、optional HARD `WITHIN` mangrove child-plan hook、marine-connected undirected graph、channel／branch／depth／marine-connectionの4 descriptor fieldをcanonical `TidalChannelPlanV2`へfreezeした。closed channel／ambiguous direction／isolated component／unknown edge／hard no-dataを独立hookで拒否し、whole／tile／順序／thread／locale／timezone、memory／CPU admission、cancelを確認した。mangrove shaping／salinity／hydroperiod／coral passは含まない。
- **目的／前提:** `TIDAL_CHANNEL_NETWORK`のmarine-connected graphを実装する。前提は`V2-3-02`、delta連携は`V2-3-07`。
- **Scope／成果物:** tidal edge kind、branch/channel raster、open-sea connection、bidirectional semantics、wetland child-plan hookを実装する。
- **非Scope:** mangrove shaping、salinity／hydroperiod field、coral pass。
- **主変更:** `generator.v2.hydrology.tidal`、graph／feature Schema、fixtures／docs。
- **必須test／D/M/S:** marine connection／branch connectivity／depth corridor、closed channel／ambiguous direction／isolated component、whole／tile／thread不変、graph／channel budget、unknown edge／hard no-data拒否。
- **Gate／docs／次／停止:** graph semanticsとmarine connectivityがvalidator hookで測定できたため完了。hydrology／taxonomy／pipeline／roadmapを更新し、次は`V2-3-09`である。salinityを暗黙推測していない。

### V2-3-09 Fjord

- **状態:** 完了。`FJORD`のtyped parameters、HARD `EMPTIES_INTO`＋明示HARD SEA、optional HARD `FLANKS` glacial wall plan hook、head-to-sea centerline、U profile／sidewall relief、5 field、EXPERIMENTAL validator hook、landlocked／too-wide／broken wall／hard boundary／budget／cancel拒否、whole／tile／thread／locale／timezone不変を確認した。

- **目的／前提:** `FJORD` centerline、marine channel、U断面、sidewall reliefを2.5Dで実装する。前提は`V2-3-02`とcoast field。
- **Scope／成果物:** head-to-sea centerline、deep channel、U profile、glacial wall plan hook、boundary connectionを実装する。
- **非Scope:** ice／snow、general glacial geology、volume sea cave。
- **主変更:** `generator.v2.landform.fjord`、fjord parameter／constraint Schema、fixtures／docs。
- **必須test／D/M/S:** sea connection／slenderness／U profile／sidewall relief、landlocked／too-wide／broken wall、whole／tile／seam／thread不変、profile／distance budget、hard boundary conflict拒否。
- **Gate／docs／次／停止:** fjord hard metricsとcorruption detectionが通ったため完了。hydrology／taxonomy／roadmapを更新し、次は`V2-3-10`である。snow/materialを成立条件にしていない。

### V2-3-10 Mountain range skeleton

- **状態:** 完了。`ALPINE_MOUNTAIN_RANGE`／`GLACIAL_MOUNTAIN_RANGE`共有のtyped parameters、ridge spline／polygon major-axis、peak／saddle／spur、derived ridge IDs、provisional surface 6 field、EXPERIMENTAL validator hook、self-cross／bounds／hard coast／budget／cancel拒否、whole／tile／thread／locale／timezone不変を確認した。snowline／materialは成立条件にしていない。

- **目的／前提:** `MOUNTAIN_RANGE`／alpine・glacial scenarioが共有するridge骨格を2.5Dで実装する。前提はV2-2 field基盤。
- **Scope／成果物:** ridge spline、peak／saddle／spur、relief profile、drainage-compatible provisional surface、derived ridge IDsを実装する。
- **非Scope:** snowline、cirque ecology、material、full alpine `SUPPORTED`化。
- **主変更:** `generator.v2.landform.mountain`、parameter Schema、mountain fixtures／docs。
- **必須test／D/M/S:** ridge continuity／peak order／relief／drainage handoff、self-cross／bounds／hard coast conflict、whole／tile／thread不変、ridge／halo budget、platform-float branch禁止。
- **Gate／docs／次／停止:** mountain skeletonがhydrology入力としてstableでcorruptionを検出したため完了。taxonomy／pipeline／roadmapを更新し、次は`V2-3-11`である。snowを同時に必要としていない。

### V2-3-11 Volcanic archipelago skeleton

- **目的／前提:** `VOLCANIC_ARCHIPELAGO`の島列と火山骨格を2.5Dで実装する。前提はcoast fieldと`V2-3-10`のrelief kernel。
- **Scope／成果物:** stable multipoint island mass、size/spacing distribution、central dominance、cone/caldera hooks、submarine saddle、radial drainage handoffを実装する。
- **非Scope:** basalt／tuff／ash material、lava tube、full volcanic ecology。
- **主変更:** `generator.v2.landform.volcanic`、feature／relation Schema、scenario fixture／docs。
- **必須test／D/M/S:** component count／dry gap／dominance／marine separation、merged islands／orphan caldera／bounds、whole／tile／thread／point-order不変、island／descriptor budget、unknown child plan拒否。
- **Gate／docs／次／停止:** 2.5D skeleton hard metricsとcorruption fixtureが通ったため完了。taxonomy／pipeline／roadmapを更新し、次は`V2-3-12`である。geology/materialを骨格ownerへ混ぜていない。

### V2-3-12 Bounded hydrology reconciliation

- **状態:** 完了。reach bed、open-lake spill、delta mouth、tidal／fjord marine connection、waterfall lip/baseをversion付きscalar planへfreezeし、`kind → feature → constraint`順で必ず3 pass走査するinteger-only reconcilerを追加した。recoverable targetは右辺だけを上限内で補正し、verify-only connection、locked target、補正上限、3 pass後残差はcanonical failure reasonとresidualへ保存する。plan／artifactのstrict Schema、checksum、example、bounded codec、iteration×working-set×artifact admission、cancel観測を実装した。feature／candidate／thread／locale／timezone順でresult／canonical checksumが一致し、v1契約は変更していない。

- **目的／前提:** regional shaping後の水系を固定回数で再整合する。前提は`V2-3-03`〜`V2-3-11`。
- **Scope／成果物:** reach bed、lake spill、delta mouth、tidal／fjord connection、waterfall lip/baseをversion固定順・回数で検査／補正し、residualとfailure reasonを保存する。
- **非Scope:** 無制限収束、V2-4 environmental fields、generatorの大規模再設計。
- **主変更:** `generator.v2.hydrology.reconcile`、stage graph／budget Schema、residual artifacts、pipeline／hydrology docs。
- **必須test／D/M/S:** recoverable／unrecoverable fixtures、iteration boundary、feature順列、tile／thread／candidate順不変、iteration×working-set admission、non-convergence／hard conflict／cancel安全失敗。
- **Gate／docs／次／停止:** 固定3 passと事前budgetで停止性を保証し、hard targetを満たすかcanonical failureとなり、同入力で同residual／checksumになることを確認したため完了。hydrology／pipeline／Blueprint／Schema／example／roadmapを同期した。次は`V2-3-13`だけであり、validator／preview、Release capability、V2-4 fieldは先取りしていない。

### V2-3-13 Hydrology validators and previews

- **状態:** 完了。`HydrologyValidatorV2`はgenerator-private `evaluate()`に依存せず、freeze済みBlueprintとbounded `HydrologyFieldSamplerV2`、optional reconciliation artifactだけを測定する。reachability、reverse gradient、flow cycle、lake spill、delta mouth、tidal／fjord marine connection、waterfall drop、mountain ridge／peak、volcanic island components、reconcile residualをHARD metric／structured issueへ変換した。compilerはhydrology用`ValidationTargetV2`と`v2.hydrology.validation-preview` stageをfreezeする。`HydrologyDiagnosticPreviewRendererV2`はbasin／flow／reach／bed／surface／water-body／lake／delta／fjord／waterfall／residualの12固定PNGを1枚ずつrenderし、strict `hydrology-preview-index-v2.schema.json`／checksum／directory entry read-back後にatomic publishする。isolated reach／reverse gradient／cycle／leaking lake／dead delta／broken fjord／fall mismatch等のcorruption、tile／locale決定性、path／entry／PNG budget／cancel cleanupをtestで確認した。featureは`EXPERIMENTAL`のままである。

- **目的／前提:** V2-3 graph／field／cross-featureを独立検証し可視化する。前提は`V2-3-12`。
- **Scope／成果物:** reachability、bed／flow、lake spill、delta branch、tidal/fjord marine connection、waterfall／mountain／volcano metrics、graph／basin／river／water-body／residual previewsを実装する。
- **非Scope:** generator修正の抱き合わせ、Release capability、environment／volume validation。
- **主変更:** `validation.v2.hydrology`、`preview.v2` registry、validation／preview Schema、corruption fixtures／docs。
- **必須test／D/M/S:** isolated reach／reverse gradient／cycle／leaking lake／dead delta／broken fjord／fall mismatch、stable metric reduction、bounded scan/render、artifact path／count／checksum／cancel拒否。
- **Gate／docs／次／停止:** 全corruption fixtureを独立検出しpreview index strict read-backを確認したため完了。validation／hydrology／roadmapを同期した。次は`V2-3-14`だけであり、Release capabilityとfeatureの`SUPPORTED`昇格は先取りしていない。

### V2-3-14 Release 2 `hydrology-plan` capability

- **状態:** 完了。`hydrology-plan`を`surface-2_5d`依存のRelease 2 capabilityとして有効化し、plan／routing index＋`hydrology-field-grid-v1`、reconciliation plan／artifact、validation、固定12 previewのexact artifact setとplan／graph semantic bindingをdirectory／ZIP双方でstrict verifyした。surface-only回帰、missing／extra／future capability／version、`hydrology-plan`単独、graph checksum改変、cancel cleanupを確認した。ADR 0014と`examples/v2/release-hydrology/`を追加した。featureの`SUPPORTED`昇格とPaper applyは含まない。

- **目的／前提:** hydrology plan／field／previewをRelease 2へstrict追加する。前提は`V2-3-13`とV2-2 Release core。
- **Scope／成果物:** `hydrology-plan`必須artifact/version、plan semantic checksum、directory／ZIP publisher/verifier、surface capabilityとの依存を実装する。
- **非Scope:** environment-fields、sparse-volume、Paper placement、feature lifecycle最終昇格。
- **主変更:** `format.v2.release` capability catalog、Hydrology artifact Schema／examples、artifact／migration docs。
- **必須test／D/M/S:** surface-only回帰、hydrology complete、missing／extra／version／graph checksum／capability dependency改変、directory／ZIP parity、entry／graph／expand budget、future capability拒否。
- **Gate／docs／次／停止:** 直前Release 2（surface）とhydrology Releaseを双方strict verifyしfallbackなしで完了した。artifact／migration／security／roadmapを更新し、次は`V2-3-15`だけである。format 2 coreの意味は変更していない。

### V2-3-15 Hydrology integration and Phase gate audit

- **状態:** 完了。9 scenarioをcanonical Blueprintへcompileし、順序／1・4 thread／locale／timezone不変を統合確認した。全featureのpositive／corruption、routing／reconciliation／previewのbudget・cancel、Release 2 directory／ZIP tampering、v1 golden／Release 1／V2-2 capability回帰、clean buildを再検証した。offlineのRIVER／MEANDERING_RIVER、LAKE、CANYON、DELTA、TIDAL_CHANNEL_NETWORK、FJORDと`hydrology-plan`を`SUPPORTED`とした。WATERFALL、ALPINE／GLACIAL mountain、VOLCANIC_ARCHIPELAGOは後続依存のため`EXPERIMENTAL`を維持した。証拠は [V2-3 Phase gate audit](audits/v2-3-phase-gate.md) を参照する。

- **目的／前提:** V2-3全Taskを統合しPhase gateを閉じる。前提は`V2-3-01`〜`V2-3-14`。
- **Scope／成果物:** river/lake、canyon-waterfall、delta、tidal、fjord、mountain、volcanic scenario、reconciliation、validation/preview、Release capabilityのPhase auditを実行する。
- **非Scope:** geology/climate/ecology、waterfall volume、Paper apply、実world計測。
- **主変更:** integration／compatibility tests、audit、roadmap、implementation roadmap、README実装状態。大規模feature修正は別Task。
- **必須test／D/M/S:** 全positive／corruption／tampering、tile正逆／thread／candidate／module順／locale／timezone、1000角graph/field peak、cancel／non-convergence、v1／Release 1／V2-2 capability回帰、clean build。
- **Gate／docs／次／停止:** 全gate通過時だけ`hydrology-plan`と、full completionがV2-3のfeatureを`SUPPORTED`にしてV2-3完了、Nextを`V2-4-01`にする。waterfall volume、volcanic material、snow/environmentを要するkindは`EXPERIMENTAL`のまま残す。未解決metric、budget、format regressionは新V2-3 Taskを追加して停止する。

## 4. V2-4 Geology, climate, ecology, and semantic material

### V2-4-01 Geology field core

- **状態:** 完了。checksum付き`GeologyPlanV2`、V2-3 priorの明示replacement、named seed、Stage 3 built-in module、province／opaque formation／hardness／permeabilityの4 field ownership、canonical `LFC_GRID_V1` bundleとbounded cross-field reader、CPU／retained／working／artifact budgetを実装した。empty/minimal round-trip、whole／tile正逆／1・4 thread／locale／timezone、1000角admission、unknown ID／checksum／future version／extra file／cancel cleanupを検証した。Hydrology plan、v1、Release capabilityは変更していない。次は`V2-4-02`である。

- **目的／前提:** geology semantic fieldのtyped contract、ownership、storageを確立する。前提はV2-3 gate完了。
- **Scope／成果物:** geology province、formation ID、hardness／permeability field descriptor、named seed、stage 3 module、sidecar／bounded reader、budgetを実装する。
- **非Scope:** lithology catalog、strata生成、material palette、feature連携。
- **主変更:** `model.v2.environment`、`generator.v2.geology`、Blueprint／field Schema、geology docs。
- **必須test／D/M/S:** strict field/ID/version、ownership conflict、canonical sidecar、whole／tile／thread／locale不変、1000角retained/working admission、unknown geology／checksum／future version拒否。
- **Gate／docs／次／停止:** empty/minimal geology planとfield strict round-tripが通りV2-3 priorを明示置換できれば完了し、geology／pipeline／roadmapを更新して`V2-4-02`へ進める。hydrologyを非version的に変える必要があれば停止する。

### V2-4-02 Semantic lithology catalog

- **状態:** 完了。`LithologyPlanV2`へ`landformcraft.builtin-lithology`のversion/checksum固定catalog、9種のsemantic ID、8-bit compact code、hardness／permeability／erosion response、source `GeologyPlanV2` checksumに厳密結合したprovince assignmentを実装した。catalog／plan strict JSON read-back、既存province fieldのbounded assignment検査、catalog size／code width budget、whole／tile正逆／thread／locale／timezone、unknown ID／future version／任意class／external preset／checksum corruptionを検証した。新field、Minecraft block state、strata、climate、Release capabilityは導入していない。次は`V2-4-03`である。

- **目的／前提:** version付きlithologyと形成特性を実装する。前提は`V2-4-01`。
- **Scope／成果物:** stone/granite/andesite/basalt/tuff/sandstone/limestone-like/sediment等のsemantic IDs、hardness、permeability、erosion response、province assignmentを実装する。
- **非Scope:** Minecraft block state、strata layering、climate、feature material integration。
- **主変更:** `model.v2.environment` catalog、`generator.v2.geology`、lithology Schema／fixtures／docs。
- **必須test／D/M/S:** catalog checksum／unknown ID／range、assignment order／thread不変、compact code width／catalog size budget、arbitrary class／external preset／future catalog拒否。
- **Gate／docs／次／停止:** catalogとprovince fieldがversion/checksum固定でstrict読取できれば完了し、geology／taxonomy／roadmapを更新して`V2-4-03`へ進める。Minecraft paletteをsemantic catalogへ混ぜる必要があれば停止する。

### V2-4-03 Strata, hardness, and permeability

- **状態:** 完了。`StrataPlanV2`へprovinceごとの`BOTTOM_TO_TOP` ordered strata、thickness、cardinal dip／optional foldのbounded subset、surface-exposed derived hardness／permeability、およびV2-3 `UNIFORM_GEOLOGY_PRIOR`／`hydrology-reconcile-fixed-v1`からの明示`HydrologyGeologyInputHandoff`を実装した。`StrataExposureResolverV2`はdense 3Dを割り当てずdescriptorからinteger-onlyで露出層を解決する。zero/thin/inverted strata、unknown lithology、非cardinal azimuth、layer×tile budget、future version／checksum corruptionを拒否し、whole／tile／seam／thread／locale／timezoneとv1／Hydrology plan不変を確認した。featureは`EXPERIMENTAL`のまま、climate／material／Release capabilityは導入していない。次は`V2-4-04`である。

- **目的／前提:** strata stackとexposure条件を生成する。前提は`V2-4-02`。
- **Scope／成果物:** ordered strata、thickness、fold/tiltのbounded subset、hardness／permeability derived field、erosion／hydrology input handoffを実装する。
- **非Scope:** canyon/volcano固有material、full geologic simulation、Minecraft blocks。
- **主変更:** `generator.v2.geology.strata`、strata plan Schema、fixtures／docs。
- **必須test／D/M/S:** layer order／thickness／surface exposure／field range、zero/thin/inverted strata、whole／tile／seam／thread不変、layer×tile budget、overflow／unknown lithology拒否。
- **Gate／docs／次／停止:** strataとderived fieldがdeterministicでhydrology reconciliation version transitionを明示できれば完了し、geology／pipeline／roadmapを更新して`V2-4-04`へ進める。unbounded 3D strata allocationが必要なら停止する。

### V2-4-04 Climate field core and prior transition

- **状態:** 完了。`ClimatePlanV2`、32-cell coarse precipitation／runoff prior、標高減率／緯度相当／exposure／flow accumulationを読むfinal temperature／moisture、2個のbuilt-in module／stage、4 fieldのphase／single ownership、integer-only global X/Z sampler、CPU／retained／window budgetを実装した。V2-3 `HydrologyPlan`／`CONSTANT_RUNOFF_PRIOR`／source generatorからreplacement prior／target generatorへのchecksum付き`EXPLICIT_VERSION_TRANSITION`を別planへfreezeし、V2-3 artifactは変更していない。prior/final separation、lapse/exposure、same prior／same graph、whole／tile正逆／1・4 thread／locale／timezone、1000角budget、implicit／unknown climate、future／version mismatch／checksum corruptionを検証した。fieldは`EXPERIMENTAL`かつdescriptor-onlyで、wetness／salinity／hydroperiod、snow、sidecar、Release capabilityは導入していない。次は`V2-4-05`である。

- **目的／前提:** climate priorと最終temperature/moisture計算のversion境界を実装する。前提は`V2-4-01`、V2-3 hydrology。
- **Scope／成果物:** coarse precipitation/runoff prior、elevation／latitude-like normalized gradient／exposure入力、final temperature/moisture descriptor、V2-3 constant priorからの明示generator/capability version transitionを実装する。
- **非Scope:** wetness／salinity／hydroperiod、snow、ecology。
- **主変更:** `generator.v2.climate`、ClimatePlan Schema、hydrology prior binding、climate docs。
- **必須test／D/M/S:** prior/final separation、lapse/exposure fixtures、same prior same graph、whole／tile／thread不変、coarse/full field budget、implicit default／unknown climate／version mismatch拒否。
- **Gate／docs／次／停止:** prior checksumがHydrologyPlanへfreezeされfinal fieldsと混同されず再現できれば完了し、geology-climate／hydrology／migration／roadmapを更新して`V2-4-05`へ進める。V2-3 artifactを黙って再解釈する必要があれば停止する。

### V2-4-05 Wetness, salinity, and hydroperiod

- **状態:** 完了。`WaterConditionPlanV2`、bounded 64-block water distance／groundwater proxy／tidal influence／salinity／hydroperiod／wetness／wetness residualの7 field、`generate.water-condition` module／stage、Hydrology＋Climate moisture checksum binding、integer-only global X/Z sampler、CPU／retained／window／distance budgetを実装した。river/lake/tide gradients、marine disconnectでsalinity=0、no-data／hard range／implicit ocean／unbounded diffusion拒否、whole／tile正逆／1・4 thread／locale／timezone、1000角budget、future／checksum corruptionを検証した。fieldは`EXPERIMENTAL`かつdescriptor-onlyで、mangrove／coral／ecology／snow／sidecar／Release capabilityは導入していない。次は`V2-4-06`である。

- **目的／前提:** final terrain/hydrologyからregional water-condition fieldsを作る。前提は`V2-4-04`とV2-3 hydrology。
- **Scope／成果物:** drainage/water distance、groundwater proxy、tidal influence、salinity、hydroperiod、wetness fieldとresidualを実装する。
- **非Scope:** mangrove shaping、coral bathymetry、ecology placement、cave-local moisture。
- **主変更:** `generator.v2.environment.water`、field Schema、fixtures／docs。
- **必須test／D/M/S:** river/lake/tide gradients、marine disconnect、no-data/hard range、whole／tile／seam／thread不変、distance/window budget、implicit ocean fallback／unbounded diffusion拒否。
- **Gate／docs／次／停止:** fieldsがhydrology connectivityに従いhard range corruptionを検出できたため完了し、geology-climate／hydrology／roadmapを更新して`V2-4-06`へ進める。full fluid simulationは不要だった。

### V2-4-06 Snow and snowline

- **状態:** 完了。`SnowPlanV2`、`SnowFieldModulesV2`、`SnowFieldSamplerV2`、`SnowValidatorV2`を実装し、temperature/moisture/slope/exposureに基づく`SNOW_POTENTIAL`、`SNOW_COVER`の2 fieldを追加した。warm peak、steep face等でのsnow reductionとbudget検査、deterministic checksum生成をテストで確認した。Minecraft blockへの解決やice volumeは含めず、semantic fieldの段階で停止している。次は`V2-4-07`である。


- **目的／前提:** height、temperature、moisture、slopeに基づくsnow coverを実装する。前提は`V2-4-04`とmountain skeleton。
- **Scope／成果物:** snow potential／cover field、snowline transition、slope/wind exposure rule、alpine scenario validator hookを実装する。
- **非Scope:** ice glacier volume、Minecraft snow block adapter、general ecology。
- **主変更:** `generator.v2.environment.snow`、environment override／constraint Schema、snow fixtures／docs。
- **必須test／D/M/S:** elevation/temp/slope transition、maxY一律でないこと、warm peak／steep face corruption、whole／tile／seam／thread不変、field/preview budget、unknown preset／out-of-bounds transition拒否。
- **Gate／docs／次／停止:** snowline metricがfield入力で説明可能かつdeterministicなら完了し、geology-climate／taxonomy／roadmapを更新して`V2-4-07`へ進める。Minecraft block解決が必要ならsemantic fieldで止める。

### V2-4-07 Semantic material profile

- **状態:** 完了。`model.v2.material.MaterialProfilePlanV2`へ、geology／lithology／strata、water-condition wetness、snow coverをchecksumで直接結合する`GeologyBinding`／`WaterConditionBinding`／`SnowBinding`と、version固定の閉じたcatalog（`landformcraft.builtin-material-profile`、6 semantic class: `HOST_ROCK_EXPOSED`／`HOST_ROCK_WET`／`SEDIMENT_EXPOSED`／`SEDIMENT_WET`／`SNOW_COVERED_ROCK`／`SNOW_COVERED_SEDIMENT`）を実装した。3段の`ResolutionRule`（`BASE_SUBSTRATE_FROM_LITHOLOGY`→`WETNESS_OVERRIDE`→`SNOW_OVERRIDE`）はexplicitな`RuleMergeOperator`と`applicableAspects`（`SURFACE`／`CEILING`／`FLOOR`のsurface/ceiling/floor hook）を持つ固定順で、last-write-winsやarbitrary expressionを受理しない。`generator.v2.material.MaterialProfileResolverV2`は`StrataExposureResolverV2`のhost lithology／erosion responseからrock／sedimentを導出し、wetness／snow coverしきい値をinteger-onlyで適用する。`core.v2.MaterialProfilePlanCompilerV2`はcatalog／rule／kernel／budgetを組み立ててchecksumをfreezeする。wet-rock／sediment／snow profile、surface対ceiling／floorでのsnow上書き差、unknown province／semantic ID拒否、whole／tile／thread／locale／timezone不変、catalog/rule/cache budgetを`MaterialProfilePlanV2Test`／`MaterialProfileResolverV2Test`／`MaterialProfilePlanCompilerV2Test`で検証した。Minecraft palette、volcanic/canyon固有rule、volume-local materialは対象外である。次は`V2-4-08`である。

- **目的／前提:** geology/environment/surface classからMinecraft非依存のmaterial profileを解決する。前提は`V2-4-03`〜`V2-4-06`。
- **Scope／成果物:** substrate/rock/sediment/wetness/snow/cover semantic IDs、ordered resolution rules、profile checksum、surface/ceiling/floor hookを実装する。
- **非Scope:** Minecraft palette、feature固有volcanic/canyon rule、volume-local material。
- **主変更:** `model.v2.material`、`generator.v2.material`、profile Schema／fixtures／docs。
- **必須test／D/M/S:** rule precedence／exclusive conflict／wet-rock/sediment/snow profiles、rule／module／thread順不変、catalog/rule/cache budget、last-write-wins／unknown semantic ID／arbitrary expression拒否。
- **Gate／docs／次／停止:** semantic profileがcanonical checksumを持ちMinecraft型へ依存せずstrict解決できれば完了し、geology-climate／pipeline／roadmapを更新して`V2-4-08`へ進める。block stateをmodelへ入れる必要があれば停止する。

### V2-4-08 Minecraft palette adapter

- **状態:** 完了。`MinecraftPalettePlanV2`／`MinecraftPalettePlanCompilerV2`／`MinecraftPaletteResolverV2`／`EnvironmentBlockStateCatalogV2`を実装し、6 semantic class × SURFACE／CEILING／FLOORの閉じた18 mappingをMinecraft 1.21.11／DataVersion 4671／`minecraft-palette-resolver-v1`へ凍結した。material-profile checksum binding、unknown ID／future resolver／NBT拒否、palette 127／128 VarInt境界、offline Sponge write→inspect→WorldEdit 7.3.19 read-backのcanonical block checksum一致、thread／locale／timezone決定性を検証した（ADR 0018）。Paper apply、biome、v1 palette変更は含まない。次は`V2-4-09`である。

- **目的／前提:** semantic materialをversion固定Minecraft 1.21.11 block stateへ隔離解決する。前提は`V2-4-07`とoffline schematic基盤。
- **Scope／成果物:** semantic profile→palette mapping、resolver version/checksum、fallback禁止、Sponge writer/read-back連携を実装する。
- **非Scope:** Paper apply、biome書換え、feature shaping、v1 palette変更。
- **主変更:** `format.v2.minecraft`／`worldedit` adapter、palette artifact Schema、新ADRまたはartifact docs、fixtures。
- **必須test／D/M/S:** all semantic IDs、palette 127/128境界、block-state canonicalization、whole／tile／thread不変、palette/cache/artifact budget、unknown ID／Minecraft version／checksum／future resolver拒否。
- **Gate／docs／次／停止:** offline Sponge read-backとcanonical block checksumが一致しv1 palette回帰不変なら完了し、artifact／geology-climate／roadmapを更新して`V2-4-09`へ進める。WorldEdit内部APIが必要なら停止する。

### V2-4-09 Mangrove regional shaping

- **状態:** 完了。`MangroveWetlandPlanV2`／`MangrovePlanCompilerV2`／`MangroveGeneratorV2`／`LandformMangroveModuleV2`（`EXPERIMENTAL`）を実装し、wetland polygon、microRelief／waterloggedShare parameters、tidal channel open-water protection、`SEDIMENT_WET` substrate marker、5 descriptor field、Diagnostic Blueprint配線を追加した。Stage 6へ`MANGROVE_TIDAL_LINK`を追加し、reconciliation scan orderを`kind-feature-constraint-v2`へ更新した。relief／open-water／marine connection、filled channel／dry wetland／hard mask、whole／tile／thread／locale／timezone、budgetを検証した。tree/root／ecology／new tidal solverは含まない。次は`V2-4-10`である。

- **目的／前提:** `MANGROVE_WETLAND`の低relief、microtopography、open-water gapをregional stageで実装する。前提はtidal graph、`V2-4-05`。
- **Scope／成果物:** wetland polygon、bounded elevation shaping、channel/open-water protection、mud/silt semantic substrate、Stage 6 reconciliation hookを実装する。
- **非Scope:** mangrove tree/root placement、general ecology、new tidal solver。
- **主変更:** `generator.v2.landform.mangrove`、feature/environment Schema、scenario fixtures／docs。
- **必須test／D/M/S:** relief／hydroperiod／open-water gap／marine connection、filled channel／dry wetland／hard mask conflict、whole／tile／thread不変、microtopography/reconcile budget、non-convergence拒否。
- **Gate／docs／次／停止:** shaping後もtidal hard targetsとregional metricsが通れば完了し、taxonomy／geology-climate／hydrology／roadmapを更新して`V2-4-10`へ進める。ecology placementを同時に必要とするなら分離したまま停止する。

### V2-4-10 Coral reef bathymetry

- **状態:** 完了。`CoralReefPlanV2`／`CoralReefPlanCompilerV2`／`CoralReefGeneratorV2`／`LandformCoralReefModuleV2`（`EXPERIMENTAL`）を実装し、reef ring、crest／outer slope profile、lagoon depth、`REEF_PASS` corridor carve、marine connection、5 descriptor field、Diagnostic Blueprint配線を追加した。Stage 6へ`REEF_LAGOON_PASS`を追加し、reconciliation scan orderを`kind-feature-constraint-v3`へ更新した。crest／lagoon／pass count、sealed lagoon／deep reef／broken pass／hard conflict、whole／tile／thread／locale／timezone、budgetを検証した。coral object／ecology placement、volume reef、Paper fluidは含まない。次は`V2-4-11`である。

- **目的／前提:** `CORAL_REEF` crest、outer slope、lagoon bathymetry、`REEF_PASS`をregional stageで実装する。前提はcoast/hydrology、`V2-4-04`。
- **Scope／成果物:** reef ring、crest/depth profile、lagoon、pass carve、marine connectivity、Stage 6 reconciliation hookを実装する。
- **非Scope:** coral object placement、full ecology、volume reef、Paper fluid。
- **主変更:** `generator.v2.landform.reef`、reef/pass parameter／relation Schema、scenario fixtures／docs。
- **必須test／D/M/S:** crest／outer slope／lagoon depth／pass count、sealed lagoon／deep reef／broken pass、whole／tile／seam／thread不変、bathymetry/reconcile budget、hard marine conflict拒否。
- **Gate／docs／次／停止:** bathymetryとmarine hard targetsが通りcorruptionを検出すれば完了し、taxonomy／geology-climate／hydrology／roadmapを更新して`V2-4-11`へ進める。coral habitatを形状ownerへ混ぜる必要があれば停止する。

### V2-4-11 Ecology placement

- **状態:** 完了。`EcologyPlanV2`／`EcologyPlanCompilerV2`／`EcologyPlacementResolverV2`（`EXPERIMENTAL`）を実装し、habitat U16 field、閉じたassemblage catalog、stable cell/feature seed、density／spacing lattice selector、mangrove canopy／root・coral colony・alpine shrub／meadow descriptors、strict Schema／example／codecを追加した。dry mangrove／deep-cold coral／unsupported root、whole／tile／seam／thread／candidate順不変、descriptor budget、unknown preset／external script拒否を検証した。cave ecology、entity／block entity、Paper placement、dense object grid、WorldBlueprint配線は含まない。次は`V2-4-12`である。

- **目的／前提:** habitat/assemblageとsparse deterministic placement基盤を実装する。前提は`V2-4-04`〜`V2-4-10`。
- **Scope／成果物:** habitat field、assemblage catalog、stable cell/feature seed、density/spacing selector、mangrove/root・coral・alpine vegetationのbounded descriptorsを実装する。
- **非Scope:** cave-local ecology、entity、block entity、Paper placement。
- **主変更:** `model.v2.ecology`、`generator.v2.ecology`、EcologyPlan／catalog Schema、fixtures／docs。
- **必須test／D/M/S:** habitat eligibility／density／spacing／support、dry mangrove／deep-cold coral／unsupported root、whole／tile／seam／thread／candidate順不変、descriptor/spatial-index budget、external script/preset拒否。
- **Gate／docs／次／停止:** placementsがhabitat内だけでstable、budget内、invalid supportを検出すれば完了し、geology-climate／taxonomy／roadmapを更新して`V2-4-12`へ進める。dense object gridが必要なら停止する。

### V2-4-12 Volcanic and canyon material integration

- **状態:** 完了。`FeatureMaterialProfilePlanV2`／`FeatureMaterialProfilePlanCompilerV2`／`FeatureMaterialProfileResolverV2`（`EXPERIMENTAL`）を実装し、volcanic basalt／tuff／ash zones、canyon strata／talus／floor sediment、fixed-order overlay／conflict rules（canyon wins）、strict Schema／example／codecを追加した。shape generator未変更でのvolcanic field checksum回帰、unknown lithology拒否、whole／tile／thread／locale不変、budgetを検証した。lava tube、cave、Minecraft palette変更、WorldBlueprint配線は含まない。

- **目的／前提:** 既存2.5D volcanic/canyonへsemantic geology/material ruleを接続する。前提は`V2-4-03`、`V2-4-07`、V2-3 skeletons。
- **Scope／成果物:** volcanic basalt/tuff/ash zones、canyon strata exposure/talus/sediment、feature-specific profile overlayとcross-feature conflict rulesを実装する。
- **非Scope:** 新形状generator、lava tube、cave、Minecraft palette変更。
- **主変更:** `generator.v2.material.feature`、feature profile Schema、volcanic/canyon fixtures／docs。
- **必須test／D/M/S:** crater/lava/shore profile、canyon wall/floor strata、profile conflict corruption、whole／tile／thread／module順不変、rule/descriptor budget、unknown lithology／silent fallback拒否。
- **Gate／docs／次／停止:** shape checksumを不必要に変えずsemantic metricsとmaterial read-backが通れば完了し、taxonomy／geology-climate／roadmapを更新して`V2-4-13`へ進める。shape修正が大規模なら原因Taskを再openする。

### V2-4-13 Environment validators and previews

- **状態:** 完了。`EnvironmentValidatorV2`はgenerator-private配列に依存せず、公開`EnvironmentFieldSamplerV2`／`EnvironmentCellSnapshotV2`だけを測定する。wrong snowline／mangrove salinity／reef depth／root support／material exposureをHARD metric／structured issueへ変換し、habitat／volcanic／canyon／strataのsummary reductionも固定した。`EnvironmentDiagnosticPreviewRendererV2`はtemperature／moisture／wetness／salinity／hydroperiod／snow-cover／habitat／material-profile／feature-material／constraint-errorの10固定PNGを1枚ずつrenderし、strict `environment-preview-index-v2.schema.json`／checksum／directory entry read-back後にatomic publishする。`environment-validation-artifact-v2.schema.json`とexample、corruption／tile／locale決定性、path／byte budget、cancel cleanupを検証した。Release capabilityとgenerator redesignは含まない。

- **目的／前提:** geology/climate/ecology/materialを独立検証し可視化する。前提は`V2-4-01`〜`V2-4-12`。
- **Scope／成果物:** lithology/strata、temperature/moisture/wetness/salinity/hydroperiod/snow、habitat/ecology/material、mangrove/coral/volcanic/canyon metric validatorとpreview layersを実装する。
- **非Scope:** generator redesign、Release capability、volume-local fields。
- **主変更:** `validation.v2.environment`、`preview.v2` registry、validation/preview Schema、corruption fixtures／docs。
- **必須test／D/M/S:** wrong snowline／salinity／reef depth／root support／material exposure、stable reductions、bounded scan/one-image render、artifact count/path/checksum/budget、cancel cleanup。
- **Gate／docs／次／停止:** 全corruption fixtureをgenerator-independentに検出しpreview index strict read-backなら完了し、validation／geology-climate／roadmapを更新して`V2-4-14`へ進める。generator-private arraysが必要なら停止する。

### V2-4-14 Release 2 `environment-fields` capability

- **状態:** 完了。`environment-fields`を`hydrology-plan`＋`surface-2_5d`依存のRelease 2 capabilityとして有効化し（ADR 0019）、geology／lithology／strata／climate／water／snow／material／palette／ecology／feature-material／validation／固定10 previewのexact artifact setとchecksum bindingをdirectory／ZIP双方でstrict verifyした。prior surface／hydrology回帰、missing／extra／future capability／version、`environment-fields`単独、palette checksum改変、cancel cleanupを確認した。`snow-plan-v2` Schemaと`examples/v2/release-environment/`を追加した。featureの`SUPPORTED`昇格とPaper applyは含まない。次は`V2-4-15`である。

- **目的／前提:** environment plans/fields/palette/ecologyをRelease 2へstrict追加する。前提は`V2-4-13`とV2-3 Release capability。
- **Scope／成果物:** `environment-fields`必須artifact/version、hydrology prior binding、palette checksum、directory／ZIP publisher/verifierを実装する。
- **非Scope:** sparse-volume、Paper placement、feature lifecycle最終昇格。
- **主変更:** `format.v2.release` catalog、environment artifact Schema／examples、artifact／migration docs。
- **必須test／D/M/S:** prior surface/hydrology Release回帰、environment complete、missing／extra／version／palette／field checksum改変、directory／ZIP parity、field/ecology/palette budget、future capability拒否。
- **Gate／docs／次／停止:** 直前capability setsとenvironment releaseを全てstrict verifyしfallbackなしなら完了し、artifact／migration／roadmapを更新して`V2-4-15`へ進める。HydrologyPlanを暗黙変換する必要があれば停止する。

### V2-4-15 Environment integration and Phase gate audit

- **状態:** 完了。5 scenarioのstrict compileとsnow／material／palette／ecology／feature-material planを統合し、scenario／module／stage順、1／4 thread、locale／timezone、1000角stage peak／sparse descriptor budgetを固定した。全V2-4 positive／corruption／tampering／cancel、v1／Release 1／V2-2／V2-3回帰、clean test／buildを通過し、geology／climate／water／snow infrastructure、ALPINE／GLACIAL mountain、MANGROVE_WETLAND、CORAL_REEF、VOLCANIC_ARCHIPELAGO、`environment-fields`をoffline `SUPPORTED`へ昇格した。監査は [V2-4 Phase gate audit](audits/v2-4-phase-gate.md)。次は`V2-5-01`である。

- **目的／前提:** V2-4全Taskを統合しPhase gateを閉じる。前提は`V2-4-01`〜`V2-4-14`。
- **Scope／成果物:** snowy mountains、mangrove wetland、coral reef、volcanic material、canyon strata、environment ReleaseのPhase auditを実行する。
- **非Scope:** cave/lush local environment、Paper apply、実world計測。
- **主変更:** integration／compatibility tests、audit、roadmap、implementation roadmap、README実装状態。大規模修正は別Task。
- **必須test／D/M/S:** 全positive／corruption／tampering、whole／tile／thread／module／locale／timezone、1000角stage peakとsparse descriptor budget、cancel、V1／Release 1／V2-2/3回帰、clean build。
- **Gate／docs／次／停止:** 全gate通過時だけ対象featureと`environment-fields`を`SUPPORTED`、V2-4完了、Nextを`V2-5-01`にする。未解決prior transition、budget、palette regressionは新V2-4 Taskを追加して停止する。

## 5. V2-5 Sparse local volumetric terrain

### V2-5-01 Fixed-point SDF primitives

- **状態:** 完了。`model.v2.volume`／`generator.v2.volume.sdf`へ`volume-sdf-primitive-contract-v1`とinteger-only kernel（`volume-sdf-fixed-v1`／`volume-sdf-q-v1`）を実装し、sphere／ellipsoid／capsule／plane／rounded box／swept splineのsigned distance、conservative AABB、Schema／example、boundary／symmetry／translation／overflow、order／locale／thread決定性、zero radius／future kernel拒否を検証した。CSG／index／cache／feature generatorは含まない。次は`V2-5-02`である。

- **目的／前提:** 局所volumeの解析的signed distance primitiveを決定論的に実装する。前提はV2-4 gate完了。
- **Scope／成果物:** sphere/ellipsoid/capsule/plane/rounded box/swept splineのfixed-point distance、quantization/version、bounds estimateを実装する。
- **非Scope:** CSG、AABB index、voxel cache、feature generator。
- **主変更:** `generator.v2.volume.sdf`、VolumePlan primitive Schema、math fixtures／volumetric docs。
- **必須test／D/M/S:** boundary／symmetry／translation／overflow golden、primitive順／locale不変、allocation-free sampleとoperator budget、NaN相当／zero radius／future kernel拒否。
- **Gate／docs／次／停止:** supported runtimeでgolden distance/sign/checksumが一致しconservative boundsを返せれば完了し、volumetric／pipeline／roadmapを更新して`V2-5-02`へ進める。platform-dependent float branchが正本になるなら停止する。

### V2-5-02 Ordered CSG

- **状態:** 完了。`VolumeCsgPlanV2`／`VolumeCsgEvaluatorV2`へ`volume-csg-contract-v1`と`volume-csg-ordered-v1`を実装し、`ADD_SOLID`／`CARVE_SOLID`／`ADD_FLUID`、intersection mask、SDF plan binding、明示ordinal、stable operator IDをfreezeした。add→carve差、CARVEのfluid非所有、ambiguous order／unknown dependency／self-cycle／budget拒否、thread決定性、Schema／example round-tripを検証した。AABB index以降は含まない。次は`V2-5-03`である。

- **目的／前提:** volume add/carve/fluid operationを明示順序で合成する。前提は`V2-5-01`。
- **Scope／成果物:** `ADD_SOLID`、`CARVE_SOLID`、`ADD_FLUID`、mask/intersectionのordered plan、stable operator ID、conflict validationを実装する。
- **非Scope:** spatial index、feature-specific volume、material paint。
- **主変更:** `model.v2.volume`、`generator.v2.volume.csg`、VolumePlan Schema、fixtures／docs。
- **必須test／D/M/S:** add→carve／carve→add差、CARVEがfluid非所有、operator permutation conflict、thread不変、operator count/depth/CPU budget、cycle／unknown op／ambiguous non-commutative order拒否。
- **Gate／docs／次／停止:** operation semanticsとcanonical orderがstrict round-tripしconflictを黙って解決しなければ完了し、volumetric／pipeline／roadmapを更新して`V2-5-03`へ進める。implicit last-write-winsが必要なら停止する。

### V2-5-03 AABB spatial index

- **状態:** 完了。`VolumeAabbIndexPlanV2`／`generator.v2.volume.index`へ`volume-aabb-index-contract-v1`とkernel `volume-aabb-index-v1`を実装し、CSG plan checksum binding、operatorごとのconservative AABB（intersection maskは交差AABB、空ならprimaryへfallback）、stable bulk build、XZ/Y halo query、candidateのoperator ordinal昇順、Schema／exampleをfreezeした。brute-force oracle一致、input順／thread不変、overflow／future kernel／checksum拒否を`VolumeAabbIndexV2Test`で検証した。voxel cache／feature generatorは含まない。次は`V2-5-04`である。
- **目的／前提:** tileと交差するvolume operationだけをboundedに検索する。前提は`V2-5-02`。
- **Scope／成果物:** conservative AABB、stable bulk build、XZ/Y halo query、overlap candidate canonical order、index artifactを実装する。
- **非Scope:** voxel evaluation、cache、feature generation。
- **主変更:** `generator.v2.volume.index`、VolumePlan index descriptor、fixtures／docs。
- **必須test／D/M/S:** boundary-touch／nested／large overlap／translation、input順／thread不変、entry/node/query-result budget、integer overflow／invalid AABB／path checksum拒否。
- **Gate／docs／次／停止:** brute-force oracleと全query一致し1000角でdescriptor/index budget内なら完了し、volumetric／pipeline／roadmapを更新して`V2-5-04`へ進める。全operation全tile走査が必要なら停止する。

### V2-5-04 3D tile cache

- **状態:** 完了。`VolumeTileCachePlanV2`／`generator.v2.volume.cache`へ`volume-tile-cache-contract-v1`とkernel `volume-tile-cache-v1`を実装し、chunk key、XYZ halo、bounded LRU、solid／fluid column intervals、`retained + concurrency×peak + cache` admission、1000×1000×512 dense allocation detector、cancel-safe fillをfreezeした。hit／evict／edge Y、cache有無の同checksum、thread／locale決定性、oversized chunk／support拒否、Schema／exampleを`VolumeTileCacheV2Test`で検証した。TerrainQuery／feature generatorは含まない。次は`V2-5-05`である。
- **目的／前提:** 交差volumeを3D halo付きtile-local chunkへ有界評価する。前提は`V2-5-03`。
- **Scope／成果物:** chunk key、XYZ halo、bounded LRU/explicit lifecycle、solid/fluid interval cache、cancel-safe allocation/admissionを実装する。
- **非Scope:** TerrainQuery公開、feature generator、global dense voxel field。
- **主変更:** `generator.v2.volume.cache`、resource budget model、instrumentation tests／docs。
- **必須test／D/M/S:** hit/evict／edge Y／overlap／cancel、tile／request／thread順不変、`retained + concurrency×peak + cache` admission、1000×1000×512 dense allocation detector、oversized chunk/support拒否。
- **Gate／docs／次／停止:** instrumentationがdense allocationなしとpeak上限を示しcache有無で同checksumなら完了し、volumetric／pipeline／roadmapを更新して`V2-5-05`へ進める。bounded cacheで上限化できなければ停止する。

### V2-5-05 TerrainQuery volume support

- **状態:** 完了。`TerrainQuery.queryKernelVersion()`と`generator.v2.volume.query.VolumeTerrainQueryV2`／`VolumeTerrainCompositionKernelV2`へ`terrain-query-volume-v1`を実装し、base heightfield＋ordered CSG composition、`solidIntervals`／`fluidIntervals`／`surfaceBelow`／`ceilingAbove`、base-only pass-throughをfreezeした。add／carve／fluid、XYZ seam／whole／tile／thread／locale決定性、interval budget／owner conflict／oob拒否、`V1TerrainAdapterTest`／`V1CompatibilityGoldenTest`回帰を`VolumeTerrainQueryV2Test`で検証した。feature generator／Paperは含まない。次は`V2-5-06`である。
- **目的／前提:** common `TerrainQuery`／`TerrainBlockResolver`へsparse solid/fluid volumeを統合する。前提は`V2-5-04`。
- **Scope／成果物:** `solidIntervals`、`fluidIntervals`、ceiling/surface query、base heightfield＋ordered CSG composition、structure/validator/export共通APIを実装する。
- **非Scope:** feature-specific volumes、Paper placement、v1 adapter意味変更。
- **主変更:** `generator.v2.TerrainQuery` extension/versioned interface、resolver、query tests、architecture／volumetric docs。
- **必須test／D/M/S:** base-only互換、add/carve/fluid intervals、XYZ seam／whole/tile／thread不変、query/cache budget、invalid interval／Y overflow／owner conflict拒否、v1 adapter全block不変。
- **Gate／docs／次／停止:** base-only v2とv1 adapter回帰が一致しvolume queryが全consumerでpureなら完了し、architecture／volumetric／roadmapを更新して`V2-5-06`へ進める。column materializerを正本に戻す必要があれば停止する。

### V2-5-06 Cave network

- **状態:** 完了。`CaveNetworkPlanV2`／`generator.v2.volume.cave`へ`cave-network-contract-v1`とkernel `cave-network-v1`を実装し、stable graph、entrance reachability、SDF／ordered CARVE_SOLID、minimum roof、AABB、Schema／exampleをfreezeした。connectivity／isolated／thin roof／breakthrough、thread／locale決定性を`CaveNetworkGeneratorV2Test`で検証した。lush／lake／sea caveは含まない。次は`V2-5-07`である。
- **目的／前提:** `CAVE_NETWORK` tunnel/chamber graphを局所carveへcompileする。前提は`V2-5-05`。
- **Scope／成果物:** stable tunnel graph、chamber/tunnel SDF、entrance relation、minimum roof、AABB plan、`EXPERIMENTAL` cave generatorを実装する。
- **非Scope:** lush ecology、underground lake、sea cave、material finishing。
- **主変更:** `generator.v2.volume.cave`、feature/relation/VolumePlan Schema、cave fixtures／docs。
- **必須test／D/M/S:** connectivity／entrance reachability／roof thickness、isolated chamber／surface breakthrough／thin roof、XYZ tile/thread/order不変、graph/AABB/cache budget、hard clearance conflict拒否。
- **Gate／docs／次／停止:** cave metricとcorruption detectionが通りdense volumeなしなら完了し、taxonomy／volumetric／roadmapを更新して`V2-5-07`へ進める。全域cellular gridが必要なら停止する。

### V2-5-07 Lush cave

- **状態:** 完了。`LushCavePlanV2`／`generator.v2.volume.cave.lush`へ`lush-cave-contract-v1`とkernel `lush-cave-v1`を実装し、host `CAVE_NETWORK`へのHARD `WITHIN`／`REACHABLE_FROM`、lush chamber carve、wet floor／wall／ceiling、`LUSH_SUBTERRANEAN` ecology hooks、Schema／exampleをfreezeした。orphan／too-dry／unreachable／containment／thin roof／breakthrough、thread／locale決定性を`LushCaveGeneratorV2Test`で検証した。full ecology／lake／lightingは含まない。次は`V2-5-08`である。
- **目的／前提:** `LUSH_CAVE` chamberと局所湿潤conditionをcave network内に実装する。前提は`V2-5-06`とV2-4 environment/material。
- **Scope／成果物:** lush chamber carve、`WITHIN`／`REACHABLE_FROM` relation、local wet floor/wall/ceiling classification、ecology hooksを実装する。
- **非Scope:** full ecology placement（post-volume Task）、underground lake、lighting engine。
- **主変更:** `generator.v2.volume.cave.lush`、feature/relation Schema、scenario fixtures／docs。
- **必須test／D/M/S:** network containment／entrance reachability／wet surface eligibility、orphan/too-dry/thin roof、XYZ seam/thread不変、local field/descriptor budget、surface breakthrough拒否。
- **Gate／docs／次／停止:** lush chamberのshape/environment hooksとnegative fixtureが通れば完了し、taxonomy／geology-climate／volumetric／roadmapを更新して`V2-5-08`へ進める。Stage 10全体を先取りする必要があれば停止する。

### V2-5-08 Underground lake

- **状態:** 完了。`UndergroundLakePlanV2`／`generator.v2.volume.water`へ`underground-lake-contract-v1`とkernel `underground-lake-v1`を実装し、host caveへのHARD `WITHIN`／`REACHABLE_FROM`、basin `CARVE_SOLID`→単一`ADD_FLUID`、rim／air cavity／contained fluid、offline CSG read-back、Schema／exampleをfreezeした。orphan／unreachable／leak／double fluid／carve-as-fluid、thread／locale決定性を`UndergroundLakeGeneratorV2Test`で検証した。sea／Paper／lush materialは含まない。次は`V2-5-09`である。
- **目的／前提:** `UNDERGROUND_LAKE` basin carveと明示fluid bodyを実装する。前提は`V2-5-06`とordered CSG。
- **Scope／成果物:** chamber basin、single water surface、rim/containment、`ADD_FLUID` ownership、cave connectionを実装する。
- **非Scope:** sea connection、Paper fluid physics、lush material。
- **主変更:** `generator.v2.volume.water`、fluid body Schema、fixtures／docs。
- **必須test／D/M/S:** fluid continuity／rim／air cavity／cave access、leak／double fluid owner／carve-as-fluid corruption、XYZ seam/thread不変、fluid interval/cache budget、uncontained fluid拒否。
- **Gate／docs／次／停止:** offline resolverでcontained waterとair cavityがstrict read-backできれば完了し、volumetric／hydrology／roadmapを更新して`V2-5-09`へ進める。physics simulationが必要なら停止する。

### V2-5-09 Sea cave

- **状態:** 完了。`SeaCavePlanV2`／`generator.v2.volume.seacave`へ`sea-cave-contract-v1`とkernel `sea-cave-v1`を実装し、cliff `CARVES_FLANK_OF`、marine `EMPTIES_INTO`、capsule carve＋静的`ADD_FLUID`、sea opening／fluid continuity／roof、Schema／exampleをfreezeした。landlocked／unsupported／leaking inland／land-water conflict、thread／locale決定性を`SeaCaveGeneratorV2Test`で検証した。dynamic tide／Paperは含まない。次は`V2-5-10`である。
- **目的／前提:** `SEA_CAVE` carve、marine opening、bounded fluid connectionを実装する。前提は`V2-5-06`、`V2-5-08`、coast/hydrology fields。
- **Scope／成果物:** cliff-hosted entrance、marine boundary relation、tidal/static water subset、roof/support constraintsを実装する。
- **非Scope:** dynamic tide、wave erosion、Paper placement containment。
- **主変更:** `generator.v2.volume.seacave`、feature/relation Schema、fixtures／docs。
- **必須test／D/M/S:** sea opening／fluid continuity／roof、landlocked／leaking inland／unsupported host、XYZ seam/thread不変、AABB/fluid budget、hard land-water conflict拒否。
- **Gate／docs／次／停止:** marine connectionとcontainment metricsが通りcorruption検出なら完了し、taxonomy／volumetric／roadmapを更新して`V2-5-10`へ進める。dynamic waterを成立条件にする必要があれば停止する。

### V2-5-10 Overhang

- **状態:** 完了。`OverhangPlanV2`／`generator.v2.volume.overhang`へ`overhang-contract-v1`とkernel `overhang-v1`を実装し、host cliff `SUPPORTS_FROM`、seaward `ADD_SOLID` lobe＋underside `CARVE_SOLID` recess、support／roof／projection／clearance／seaward opening、Schema／exampleをfreezeした。floating slab／thin roof／blocked corridor／short projection、thread／locale決定性を`OverhangGeneratorV2Test`で検証した。natural arch／Paper gravityは含まない。次は`V2-5-11`である。
- **目的／前提:** `OVERHANG`をhost cliffへのsolid add＋recess carveで実装する。前提は`V2-5-05`とrock/cliff surface。
- **Scope／成果物:** support relation、roof slab、seaward clearance、recess operation、host AABB bindingを実装する。
- **非Scope:** natural arch、sea cave、Paper gravity/physics。
- **主変更:** `generator.v2.volume.overhang`、feature/relation Schema、scenario fixtures／docs。
- **必須test／D/M/S:** support／roof thickness／clearance／open side、floating slab／thin roof／blocked corridor、XYZ seam/thread/order不変、AABB/cache budget、unsupported host拒否。
- **Gate／docs／次／停止:** overhang hard metricsとcorruption fixtureが通りstable block streamなら完了し、taxonomy／volumetric／roadmapを更新して`V2-5-11`へ進める。Paper containmentを同時に必要とするなら停止する。

### V2-5-11 Natural arch

- **状態:** 完了。`NaturalArchPlanV2`／`generator.v2.volume.arch`へ`natural-arch-contract-v1`とkernel `natural-arch-v1`を実装し、two-pier `ADD_SOLID`＋through `CARVE_SOLID`、pier／crown／span／clearance、Schema／exampleをfreezeした。one-pier／thin crown／closed opening／short span、thread／locale決定性を`NaturalArchGeneratorV2Test`で検証した。bridge asset／sky island／materialは含まない。次は`V2-5-12`である。
- **目的／前提:** `NATURAL_ARCH`のpiers、crown、through carveを実装する。前提は`V2-5-02`、`V2-5-05`。
- **Scope／成果物:** two-support solid plan、ordered opening carve、minimum pier/crown、clearance corridorを実装する。
- **非Scope:** bridge structure asset、sky island、material finishing。
- **主変更:** `generator.v2.volume.arch`、feature parameter Schema、fixtures／docs。
- **必須test／D/M/S:** pier/crown/support/clearance、one-pier／thin crown／closed opening、XYZ seam/thread/order不変、operator/AABB/cache budget、non-commutative conflict拒否。
- **Gate／docs／次／停止:** arch metricsとcorruption detectionが通りresolver read-back一致なら完了し、taxonomy／volumetric／roadmapを更新して`V2-5-12`へ進める。structure assetでしか表せないなら停止する。

### V2-5-12 Sky island group

- **状態:** 完了。`SkyIslandGroupPlanV2`／`generator.v2.volume.skyisland`へ`sky-island-group-contract-v1`とkernel `sky-island-group-v1`を実装し、ordered multipoint `ADD_SOLID`＋underside `CARVE_SOLID`、ground clearance／gap／top-edge-underside、Schema／exampleをfreezeした。merged／touching ground／out-of-Y／dense fill、thread／locale／component-order決定性を`SkyIslandGroupGeneratorV2Test`で検証した。ecology／material／Paperは含まない。次は`V2-5-13`である。
- **目的／前提:** `SKY_ISLAND_GROUP`の独立solid componentsとunderside carveを実装する。前提は`V2-5-05`。
- **Scope／成果物:** stable multipoint/group plan、solid lobes、top/edge/underside classes、ground clearance、inter-island gap、support-free allowed policyを実装する。
- **非Scope:** ecology/material finishing、Paper apply、unbounded floating world。
- **主変更:** `generator.v2.volume.skyisland`、feature/vertical Schema、scenario fixtures／docs。
- **必須test／D/M/S:** component count／ground clearance／gap／bounds、merged/touching ground/out-of-Y、XYZ seam/thread/point-order不変、component/AABB/cache budget、dense air fill拒否。
- **Gate／docs／次／停止:** group hard metricsとcorruption fixtureが通りbounded descriptorsのみなら完了し、taxonomy／volumetric／roadmapを更新して`V2-5-13`へ進める。global sky voxel layerが必要なら停止する。

### V2-5-13 Waterfall volume integration

- **状態:** 完了。`WaterfallVolumePlanV2`と`generator.v2.volume.waterfall`へfalling column／behind-fall／plunge-poolの静的volume overlayを実装し、V2-3 fall geometry checksumへ`BOUND_TO_FALL`で結合した。column shaft `CARVE_SOLID`→`ADD_FLUID`、behind carve、pool fluid continuity、Schema／exampleを追加し、offset column／missing behind／missing pool／graph checksum mismatch拒否とthread／locale決定性を`WaterfallVolumeGeneratorV2Test`で検証した。dynamic fluid／Paper settle／new waterfall graph／`SUPPORTED`は対象外で、V2-5親Phase gateは未完了である。次は`V2-5-14`である。

- **目的／前提:** V2-3 waterfallへfalling water columnとbehind-fall clearanceを接続する。前提は`V2-3-06`、`V2-5-05`、`V2-5-08`。
- **Scope／成果物:** fall column `ADD_FLUID` plan、air/rock clearance、plunge pool continuity、fall node checksum bindingを実装する。
- **非Scope:** dynamic fluid simulation、Paper settle、new waterfall graph。
- **主変更:** `generator.v2.volume.waterfall`、VolumePlan/hydrology binding Schema、fixtures／docs。
- **必須test／D/M/S:** lip→column→pool continuity／behind clearance、offset column／leak／missing pool、XYZ seam/thread不変、fluid/AABB/cache budget、graph checksum mismatch拒否。
- **Gate／docs／次／停止:** graphとvolumeがchecksum-boundでfluid continuity metricを満たせば完了し、hydrology／volumetric／roadmapを更新して`V2-5-14`へ進める。Paper physicsが必要なら停止する。

### V2-5-14 Post-volume environment and material

- **状態:** 完了。`VolumeLocalEnvironmentPlanV2`と`generator.v2.environment.local`へ、volume確定後のsurface class／wetness／drip／shade、lush moss／root／pool、cave／sea-cave wet rock、sky-island top／edge／underside profile、sparse placementsを実装した。Schema／exampleを追加し、moss on dry ceiling／root without support／wrong underside／unknown profile拒否とthread／locale決定性を`VolumeLocalEnvironmentResolverV2Test`で検証した。new volume shapes／lighting／Paper／`SUPPORTED`は対象外で、V2-5親Phase gateは未完了である。次は`V2-5-15`である。

- **目的／前提:** volume確定後のfloor/wall/ceiling/top/edge/undersideへlocal environment/material/ecologyを適用する。前提は`V2-5-06`〜`V2-5-13`とV2-4 material/ecology。
- **Scope／成果物:** local wetness/drip/shade/surface class、lush moss/root/pool、cave/sea-cave wet rock、sky-island top/edge/underside profile、sparse placementsを実装する。
- **非Scope:** new volume shapes、lighting engine、Paper biome/entity。
- **主変更:** `generator.v2.environment.local`、material/ecology rules、profile Schema、fixtures／docs。
- **必須test／D/M/S:** surface classification/support/habitat、moss on dry ceiling／root without support／wrong underside corruption、XYZ seam/thread/module順不変、local query/placement budget、unknown surface/profile拒否。
- **Gate／docs／次／停止:** final resolverでsemantic conditionsとplacementsがstableかつbudget内なら完了し、geology-climate／volumetric／roadmapを更新して`V2-5-15`へ進める。regional fieldを再生成する必要があれば停止する。

### V2-5-15 Volume validators and previews

- **状態:** 完了。`validation.v2.volume`と`preview.v2`へ、descriptor-only `VolumeValidatorV2`（`volume-validator-v1`）とstrict 5-layer `VolumeDiagnosticPreviewRendererV2`（`volume-diagnostic-palette-v1`）を実装した。isolated cave／thin roof／fluid leak／floating overhang／broken arch／merged sky island／fall discontinuity検出、Schema／example、atomic publish／strict read-back／cancel／budgetを`VolumeValidatorV2Test`／`VolumeDiagnosticPreviewRendererV2Test`で検証した。dense validation grid／schematic／Release／`SUPPORTED`は対象外で、V2-5親Phase gateは未完了である。次は`V2-5-16`である。

- **目的／前提:** volume topology、support、fluid、environmentを独立検証し可視化する。前提は`V2-5-06`〜`V2-5-14`。
- **Scope／成果物:** connectivity/roof/support/clearance/component/fluid/material validators、AABB/operator/slice/solid-fluid/surface-class previewsとindex metadataを実装する。
- **非Scope:** generator redesign、schematic、Release capability。
- **主変更:** `validation.v2.volume`、`preview.v2` registry、validation/preview Schema、corruption fixtures／docs。
- **必須test／D/M/S:** isolated cave/thin roof/leak/floating overhang/broken arch/merged sky island/fall discontinuity、stable traversal/reduction、bounded slice render、preview/path/count/checksum/cancel budget。
- **Gate／docs／次／停止:** 全corruption fixtureをresolver/IRから検出しpreview strict read-backなら完了し、validation／volumetric／roadmapを更新して`V2-5-16`へ進める。dense validation gridが必要なら停止する。

### V2-5-16 Offline 3D schematic read-back

- **状態:** 完了。writer／inspector／WorldEdit reader（`OfflineTileSchematicWriterV2`／`SpongeV3TileInspectorV2`／`WorldEditOfflineTileReaderV2`）は既に`minY..maxY`全域をX-fast／Z／Yで走査する汎用3D exporterで意味・format・checksum・budgetを凍結したまま、新規`format.v2.tile.VolumeTileBlockResolverV2`のみを追加した（`terrain-query-volume-v1`合成`TerrainQuery`→canonical block state、air/fluid/independent solid、`EnvironmentBlockStateCatalogV2` allowlist、v1 adapter分離、NONE solid／非water fluid拒否）。cave／floating solid／fluid／air volume tileがexport→strict inspector→WorldEdit 7.3.19 read-backでresolver semantic checksumと全XYZ一致、whole／tile・thread／order・locale／timezone不変、palette 127/128 VarInt境界3D read-back、truncated／corrupt／checksum改変拒否を`OfflineVolumeTileReadBackV2Test`／`WorldEditOfflineVolumeTileReaderV2Test`で検証し、`examples/v2/volume/offline-volume-tile-artifact-v2.json`を追加した。出力Sponge v3は一般仕様features（Version 3／DataVersion 4671／Offset `[0,0,0]`／general VarInt／proprietary tag無し）のみでoffline FAWE readerでも読める。v1 golden／V2-2 export回帰不変。Paper apply、running-server FAWE smoke、`sparse-volume` Release capabilityは対象外で、V2-5親Phase gateは未完了である。次は`V2-5-17`である。

- **目的／前提:** volumeを含むtile block streamをSponge v3へ出しoffline read-backする。前提は`V2-5-14`、V2-2 schematic基盤。
- **Scope／成果物:** air cavity／fluid／independent solidのstable XYZ export、general VarInt、tile boundary palette、WorldEdit 7.3.19とFAWE-compatible offline inspector testを実装する。
- **非Scope:** Paper apply、FAWE server smoke、Release capability。
- **主変更:** `format.v2.tile`、`worldedit` inspector/adapters、3D fixtures／artifact docs。
- **必須test／D/M/S:** cave/overhang/sky/fluid/air, palette boundaries, whole/tile XYZ stream, thread/order不変、stream/palette/buffer budget、truncated/invalid VarInt/checksum改変拒否。
- **Gate／docs／次／停止:** offline read-back block streamがresolver checksumと全XYZ一致しv1/V2-2 export回帰不変なら完了し、artifact／volumetric／roadmapを更新して`V2-5-17`へ進める。server runtimeを必要とする場合はV2-6へ延期する。

### V2-5-17 Release 2 `sparse-volume` capability

- **状態:** 完了。`format.v2.release`へ`sparse-volume` capabilityを`environment-fields`＋`hydrology-plan`＋`surface-2_5d`依存で追加した。`ReleaseArtifactCatalogV2`にcapability list（natural order）、6 artifact type、dispatch、依存規則を登録し、`SparseVolumeReleaseCapabilityVerifierV2`／`ReleaseSparseVolumePublisherV2`／`ReleaseSparseVolumeVerifierV2`／`SparseVolumeReleaseSourceV2`／`ReleaseSparseVolumeArtifactsV2`を実装した。`volume/`配下へSDF primitive／ordered CSG／AABB index／volume validation／3D volume tile（`volume-offline-tile-artifact-v2`／`volume-sponge-schematic-v3`専用type）を収容し、CSG→SDF／AABB→CSG checksum binding、validation `sourcePlanChecksum`＝CSG＋hard-pass、volume tile strict Sponge v3 read-backと`sourceBlueprintChecksum` bindingをstrict verifyする。missing/extra/version/binding/tile checksum改変／future capability／依存downgrade拒否、directory／ZIP parity、cancel、prior capability（surface/hydrology/environment）回帰を`ReleaseSparseVolumePublisherVerifierV2Test`＋共有fixture `EnvironmentReleaseFixtureV2`で検証した。exampleは`examples/v2/release-sparse-volume/README.md`。format core意味は不変で、`SUPPORTED`昇格は`V2-5-18`まで保留。次は`V2-5-18`である。

- **目的／前提:** VolumePlan／index／validation／3D tileをRelease 2へstrict追加する。前提は`V2-5-15`〜`V2-5-16`とV2-4 Release capability。
- **Scope／成果物:** `sparse-volume`必須artifact/version、AABB/operator/palette/block checksum binding、directory／ZIP publisher/verifierを実装する。
- **非Scope:** Paper placement、feature lifecycle最終昇格。
- **主変更:** `format.v2.release` catalog、volume artifact Schema／examples、artifact／migration docs。
- **必須test／D/M/S:** earlier capability regression、complete volume release、missing/extra/version/operator/AABB/tile checksum改変、directory/ZIP parity、operator/index/expand/palette budget、future capability拒否。
- **Gate／docs／次／停止:** 全過去capability setとsparse-volume releaseをstrict verifyしfallbackなしなら完了し、artifact／migration／roadmapを更新して`V2-5-18`へ進める。format core意味変更が必要なら停止する。

### V2-5-18 Volume integration and Phase gate audit

- **状態:** 完了（2026-07-18）。`VolumePhaseGateV2Test`で9 volume compiler lifecycle、`sparse-volume` capability依存順、13 volume plan exampleのstrict read安定性（正逆順／1・4 thread／locale／timezone）、共有sceneのwhole／tile dispatch／window限定query stream一致（3D whole/tile/XYZ seam）、dense allocation拒否と1000角streaming admissionを固定し、full clean suiteでv1 golden／Release 1／V2-2〜V2-4回帰とclean buildを通過した。volume infrastructure・7 volume feature・waterfall volume・local environment・validator/preview・offline export・`sparse-volume`をoffline `SUPPORTED`へ、deferredだった`WATERFALL` kind／moduleも`SUPPORTED`へ昇格した。VOLUME_GUIDE intent kindはdiagnostic-only、child-plan kindは`EXPERIMENTAL`、Paper apply／実機smokeは非Scopeのまま。証拠は [V2-5 Phase gate audit](audits/v2-5-phase-gate.md)。次は`V2-6-01`である。

- **目的／前提:** V2-5全Taskを統合しPhase gateを閉じる。前提は`V2-5-01`〜`V2-5-17`。
- **Scope／成果物:** cave/lush/underground lake/sea cave、overhang、arch、sky island、waterfall volume、post-environment、offline export、Release capabilityのPhase auditを実行する。
- **非Scope:** Paper apply、server smoke、実world計測。
- **主変更:** integration／compatibility tests、audit、roadmap、implementation roadmap、README実装状態。大規模修正は別Task。
- **必須test／D/M/S:** 全positive/corruption/tampering、3D whole/tile/XYZ seam/operator/thread/locale/timezone、dense allocation detectorと1000角admission、cancel、V1/Release1/V2-2〜4回帰、clean build。
- **Gate／docs／次／停止:** 全gate通過時だけvolume featureと`sparse-volume`を`SUPPORTED`、V2-5完了、Nextを`V2-6-01`にする。未解決memory/palette/offline read-back riskは新V2-5 Taskを追加して停止する。

## 6. V2-6 Release 2 placement, hardening, and supported catalog

### V2-6-01 Release 2 placement contract

- **状態:** 完了（2026-07-18）。`model.v2.placement`へ`PlacementPlanV2`／`PlacementJournalV2`とformat 2 journal statesを追加し、Release／target／capability／tile order／envelope参照／reservation-confirmation binding／operation IDをchecksum-boundなimmutable契約として固定した（ADR 0020）。`PlacementPlanCompilerV2`は`PLANNED`＋`PENDING`だけをsealする。Schema／example、strict round-trip、unknown state／version／capability、target mismatch、path／checksum／future拒否、locale／timezone／thread決定性、journal entry budget、v1 journal codec不変を確認した。envelope算出、reservation、snapshot、apply、Undo／Recoveryは含まない。

- **目的／前提:** verified Release 2をworld mutationから分離するplacement plan／journal契約を固定する。前提はV2-5 gate完了。
- **Scope／成果物:** target/bounds/anchor、capability set、tile/order、mutation/effect envelope参照、reservation/confirmation binding、operation ID、format 2 journal statesのstrict contractを実装する。
- **非Scope:** envelope算出、reservation、snapshot、apply、Undo/Recovery。
- **主変更:** `model.v2.placement`、`core.v2` plan compiler、placement plan/journal Schema、新ADR、migration／placement docs。
- **必須test／D/M/S:** strict round-trip／unknown state/version/capability／target mismatch、canonical order／locale/timezone、journal/entry budget、path/checksum/future version拒否、v1 journal codec不変。
- **Gate／docs／次／停止:** Release/target/capabilityへchecksum-boundしたimmutable planがstrict読取できれば完了し、ADR／migration／roadmapを更新して`V2-6-02`へ進める。v1 journal再解釈が必要なら停止する。

### V2-6-02 Mutation and effect envelope

- **状態:** 完了（2026-07-18）。`PlacementEnvelopePlanV2`／`PlacementEnvelopeCompilerV2`でper-tile mutation AABB、version固定physics policy（FLUID／GRAVITY／NEIGHBOR）、union effect envelope、overflow-safe world bounds、disk／volume admission、checksum provenance、plan envelope-ref bindingを実装した（ADR 0021）。solid／air／fluid／gravity／neighbor fixture、independent oracle under-approx検出、tile／order／thread／locale／timezone決定性、world border／budget／unknown physics拒否を確認した。reservation／snapshot／apply／settleは含まない。

- **目的／前提:** final resolverとphysics-sensitive contentからmutation/effect envelopeを保守的に算出する。前提は`V2-6-01`。
- **Scope／成果物:** per-tile mutation AABB、union effect envelope、fluid/gravity/neighbor support radius、overflow-safe world bounds、envelope checksum/provenanceを実装する。
- **非Scope:** reservation、snapshot、apply、settle。
- **主変更:** `core.v2.placement.envelope`、envelope Schema、fixtures／placement docs。
- **必須test／D/M/S:** solid/air/fluid/gravity/boundary fixtures、under-approx corruption、tile/order/thread不変、envelope count/volume/disk estimate admission、world border/Y overflow/unknown physics class拒否。
- **Gate／docs／次／停止:** independent oracleでmutationを包含しeffect上限を安全に証明できれば完了し、placement／security／roadmapを更新して`V2-6-03`へ進める。副作用上限を定義できないcontentはhard rejectできなければ停止する。

### V2-6-03 Region/disk reservation and bound confirmation

- **状態:** 完了（2026-07-18）。`PlacementReservationPlanV2`／`PlacementSafetyStateV2`と`FilePlacementSafetyStoreV2`／`PlacementConfirmationBinderV2`／`PlacementReservationConfirmCompilerV2`でatomic multi-region／disk reservation、overlap拒否、actor-bound one-time expiry confirmation、`CONFIRMATION_ISSUED` journal、失敗時全解放を実装した（ADR 0022）。overlap／race／expiry／replay／disk shortage／restart、actor／checksum mismatch、v1 reservation回帰を確認した。snapshot／apply／settle、v1 reservation意味変更は含まない。

- **目的／前提:** 全region/diskを先に予約しconfirmationをrelease/target/envelope/reservationへ結合する。前提は`V2-6-02`。
- **Scope／成果物:** atomic multi-region reservation、disk estimate/reservation、overlap拒否、actor-bound one-time expiry confirmation、late completion-safe state transitionを実装する。
- **非Scope:** snapshot、apply、settle、v1 reservation意味変更。
- **主変更:** `core.v2.placement`、reservation store、confirmation contract/Schema、tests／admin docs。
- **必須test／D/M/S:** overlap/race/expiry/replay/disk shortage/restart、canonical reservation order、bounded locks/entries/disk, actor/target/checksum mismatch拒否、v1 reservation回帰。
- **Gate／docs／次／停止:** confirm前に全予約が成功しbinding改変/replayを拒否、失敗時全解放なら完了し、placement／admin／roadmapを更新して`V2-6-04`へ進める。partial reservationを安全にrollbackできなければ停止する。

### V2-6-04 Snapshot-all

- **状態:** 完了（2026-07-18）。`core.v2.placement.snapshot`へ`PlacementWorldGatewayV2`（`release-2-placement-world-gateway-v1`、canonical X→Z→Y read stream／apply分離）、`PlacementSnapshotFileCodecV2`（`release-2-placement-snapshot-file-v1`、palette＋VarInt、2回走査stream hash一致によるworld drift／TOCTOU拒否）、`PlacementSnapshotAllCompilerV2`（staging→全file strict read-back→sealed index read-back→atomic publish）を実装し、`PlacementSnapshotPlanV2`（`release-2-placement-snapshot-v1`）でplan／envelope／reservation／confirmationへchecksum bindingした。journalは`SNAPSHOTTING`＝全tile PENDING、`SNAPSHOT_COMPLETE`（apply-ready）＝全tile SNAPSHOTTED＋bytes>0を契約で強制し、disk leaseを上限とするhard byte cap、事前admission、disk shortage、palette budget、cancel／crash cleanup（canonical partialなし）、`cleanupAbandoned`／`loadPublished` restart経路、tamper（byte／truncation／index checksum／extra file）拒否、thread／repeat決定性、apply gateway未呼出を`PlacementSnapshotAllCompilerV2Test`で検証した。block apply、settle、rollback execution、Undo UXは対象外で、V2-6親Phase gateは未完了である。

- **目的／前提:** 最初のapply前に全effect envelopeをsnapshotしてstrict verifyする。前提は`V2-6-03`。
- **Scope／成果物:** canonical envelope order、snapshot artifact/index/checksum、disk budget、all-before-any-apply state invariant、cancel/shutdown cleanupを実装する。
- **非Scope:** block apply、settle、rollback execution、Undo UX。
- **主変更:** `core.v2.placement.snapshot`、`worldedit` gateway contract、snapshot Schema、tests／operations docs。
- **必須test／D/M/S:** multiple envelope/snapshot failure/disk full/cancel/restart、order/thread不変、snapshot/buffer/disk admission、partial/index/path/checksum/TOCTOU拒否、apply gateway未呼出確認。
- **Gate／docs／次／停止:** 全snapshot strict read-back後だけstateがAPPLY_READYになり失敗時canonical partialなしなら完了し、placement／operations／roadmapを更新して`V2-6-05`へ進める。tileごとのsnapshot→applyへ戻す必要があれば停止する。

### V2-6-05 Fluid and gravity containment preflight

- **状態:** 完了（2026-07-18）。`format.v2.minecraft.PlacementBlockPhysicsCatalogV2`（閉じたSOLID／AIR／FLUID／GRAVITY／NEIGHBOR／UNSUPPORTED分類）と`PlacementContainmentPolicyV2`／`PlacementContainmentEvidenceV2`、`core.v2.placement.safety.PlacementContainmentPreflightV2`でapply前containmentを実装した（ADR 0023）。`SNAPSHOT_COMPLETE`後にpost-apply predicted worldをcanonical X→Z→Y順で走査し、fluid closure／boundary seal、gravity support、neighbor radius、physics-class宣言、unsupported／unknown denylistを検査し、`CONTAINED` evidenceだけをplan／envelope／snapshotへchecksum bindingしてsealする。contained／uncontained water／lava／sand／gravel／neighbor、classification order／locale／timezone／thread不変、scan／cache budget、envelope gap、journal state拒否を`PlacementContainmentPreflightV2Test`で検証した。apply、settle simulation、事後rollback代替は対象外で、V2-6親Phase gateは未完了である。

- **目的／前提:** apply前にfluid/gravity/neighbor updateがeffect envelope外へ出ないことを判定する。前提は`V2-6-02`と`V2-6-04`。
- **Scope／成果物:** content classifier、support/closure rules、boundary seals、unsupported-state denylist、containment evidenceとhard rejectionを実装する。
- **非Scope:** apply、settle simulation、事後rollbackによる代替。
- **主変更:** `core.v2.placement.safety`、`format.v2.minecraft` metadata、policy Schema、security／placement docs。
- **必須test／D/M/S:** contained/uncontained water/lava/sand/gravel/neighbor-sensitive states、classification order不変、bounded scan/cache budget、unknown block state/version/envelope gap拒否。
- **Gate／docs／次／停止:** effect上限を証明できないcontentをapply前に100% hard rejectしsnapshot外検出へ依存しなければ完了し、security／placement／roadmapを更新して`V2-6-06`へ進める。安全な上限が定義できなければ対象capabilityを拒否したまま停止する。

### V2-6-06 Release 2 apply transaction orchestration

- **状態:** 完了（2026-07-18）。`core.v2.placement.apply.PlacementApplyTransactionServiceV2`をbounded executor／queue／slice admission付きで実装し、Release、unbound→envelope-bound→confirmed plan checksum chain、reservation ownership、consumed confirmation、published snapshot、containment evidence、canonical source bindingを最初のscheduler submission前にstrict再検証する。feature-neutralな`PlacementCanonicalBlockSourceV2`をcanonical X→Z→Y exact coverageでpreflightし、閉じたphysics catalog／effect-envelope宣言／fingerprintを検査後、tile-index順、明示`SOLID(10) → AIR_CARVE(20) → FLUID(30)`、overlay ordinal昇順でbounded slice適用する（ADR 0024）。`PaperPlacementWorldGatewayV2`／`WorldEditBlockMutationAccessV2`はscheduler main-thread executionとresource close receiptを返し、observer timeout／cancelは受理済みoperationを取消さない。journalはatomic `APPLYING` publish後にcanonical `APPLIED` tile prefixだけをcheckpointし、submission後のfailure／cancel／shutdown／source driftは`RECOVERY_REQUIRED`とする。surface／cave／sky solid／waterfall／underground fluid、cross-tile fluid、tile登録正逆／1・4 thread／locale／timezone、late completion、queue／block／overlay budget、main-thread／close receipt、confirm／envelope／checksum mismatch、journal symlink／prefix、v1回帰をtestした。settle／full verify、rollback、Undo／Recovery、public command接続、production `SUPPORTED`は対象外で、V2-6親Phase gateは未完了である。
- **Schema／example:** persisted shapeは追加しない。既存`placement-journal-v2.schema.json`が`APPLYING`／`APPLIED`／`RECOVERY_REQUIRED`を既にversion 1で固定しており、canonical `APPLIED` prefixはJava record invariantとして強制する。`examples/v2/placement/README.md`へinternal canonical stream例とV2-6-07までterminal成功へ進めない境界を追記した。
- **目的／前提:** validate→reserve→confirm→snapshot-all→applyの順序を破れないapplication serviceを実装する。前提は`V2-6-01`〜`V2-6-05`。
- **Scope／成果物:** operation state machine、canonical tile apply、surface／volumeのsolid→air carve→fluid pass、child-plan／volume-overlayのexplicit ordinal、cross-tile river／volume継続、gateway completion/close tracking、scheduler dispatch contract、failed future propagationを実装する。FeatureKindごとのPaper adapterは作らず、Release 2のcanonical surface／solid／air／fluid block streamだけを入力にする。
- **非Scope:** settle/full verify、rollback完成、Undo/Recovery、production `SUPPORTED` enable。
- **主変更:** `core.v2.placement` application service、Paper/worldedit v2 gateway、journal transitions、tests／architecture docs。
- **必須test／D/M/S:** illegal order/late completion/timeout/cancel/shutdown/tile failure/main-thread assertion、surface-only／cave carve／sky solid／waterfall overlay／underground fluid、tile正逆／thread不変、bounded scheduler slices/queue/admission、confirm/envelope/checksum mismatch拒否、v1 service回帰。
- **Gate／docs／次／停止:** apply開始前invariantが強制され、preview/exportと同じcanonical streamをFeature別分岐なしで適用し、受理済みoperationを観測timeoutだけで取消扱いにしなければ完了する。新しいFeatureごとに個別配置ロジックまたはsnapshot外mutationが必要なら停止する。

### V2-6-07 Bounded settle and full verify

- **状態／Phase／優先度:** 完了（2026-07-18）／V2-6／P0。
- **Schema／example:** `placement-settle-verify-policy-v2.schema.json`／`placement-verify-evidence-v2.schema.json`とexamplesを追加。journalの`SETTLING`／`VERIFYING`／terminal `APPLIED`（tiles `VERIFIED`）は既存version 1で固定。
- **目的／前提:** apply後にbounded multi-tick settleしeffect envelope全体をexact verifyする。前提は`V2-6-06`。
- **Scope／成果物:** settle policy、scheduler-sliced full scan、canonical expected resolver、late update reconciliation、surface foundation／marine underwater column／surface-volume entrance／underground fluid／overlay continuity metric、journal evidenceを実装する。
- **非Scope:** sampled verify、rollback実行、Undo、performance support declaration。
- **主変更:** `core.v2.placement.verify`、Paper gateway、verify policy Schema、tests／operations docs。
- **必須test／D/M/S:** delayed update/timeout/mismatch/server shutdown/tile checkpoint false-positive、solid/air/fluid順、tile seam river/volume連続性、scan order/thread不変、slice/timeout/queue budget、effect外 update/unknown settle policy拒否。
- **Gate／docs／次／停止:** settle後effect envelope全体のexact block-state streamを比較しfailureをcanonical分類できれば完了し、placement／operations／roadmapを更新して`V2-6-08`へ進める。sample verifyへ弱める必要があれば停止する。
- **状態:** 完了（2026-07-18）。`PlacementSettleVerifyPolicyV2`／`PlacementSettleVerifyServiceV2`／`PlacementExpectedBlockResolverV2`／`PlacementVerifyEvidenceV2`（ADR 0025）で`SETTLING → VERIFYING →` terminal `APPLIED`を実装。effect envelope全体のexact X→Z→Y比較、continuity metrics、out-of-envelope／timeout／mismatch／cancel／shutdown／slice・queue budgetの`RECOVERY_REQUIRED`分類、tile checkpoint alone非成功を`PlacementSettleVerifyServiceV2Test`で検証。rollback／Undo／Recovery／public command／`SUPPORTED`は含まない。

### V2-6-08 Rollback

- **状態／Phase／優先度:** 完了（2026-07-19）／V2-6／P0。
- **状態:** 完了。`core.v2.placement.rollback.PlacementRollbackServiceV2`（ADR 0026）が`RECOVERY_REQUIRED` journal（全tile `SNAPSHOTTED`／`APPLIED`）だけを入力に、mutation前のstrict preflight（binding、reservation ownership、`loadPublished`再検証、全snapshot fileのstrict decode＋sealed index checksum照合、union envelope被覆完全性）→逆canonical tile-index順のbounded `PlacementRestoreSliceV2` restore（receiptでscheduler受理・main-thread・resource close証明、`ROLLING_BACK`のcanonical `RESTORED` suffix checkpoint）→bounded rollback settle（envelope外update拒否）→union effect envelope全体のsnapshot baselineとのexact stream verify→terminal `ROLLED_BACK`＋reservation lease解放を実装した。missing／tampered snapshotとcoverage gapはmutationゼロで拒否し、restore失敗／receipt不正／late completion／shutdown／cancel／budget超過は`PlacementRollbackFailureCodeV2`で分類して`RECOVERY_REQUIRED`へ戻す。published snapshotは削除しない。apply・settle・verify各failure pointからの復元、逆順restore orderのthread／repeat不変、bounded slice、v1 rollback回帰を`PlacementRollbackServiceV2Test`で検証した。user Undo、startup Recovery、snapshot外変更対応は含まない。persisted shapeは追加せず、既存`placement-journal-v2.schema.json`のstate／tile enumで表現する。
- **目的／前提:** Release 2 placement失敗時にsnapshot済みenvelopeを逆順復元しfull verifyする。前提は`V2-6-04`、`V2-6-06`、`V2-6-07`。
- **Scope／成果物:** reverse-order restore、close/completion tracking、rollback settle/verify、partial failure classification、reservation/journal finalizationを実装する。
- **非Scope:** user Undo、startup Recovery、snapshot外変更対応。
- **主変更:** `core.v2.placement.rollback`、world gateway、journal states、tests／operations docs。
- **必須test／D/M/S:** surface/solid/air/fluid/child/overlayの各apply・settle・verify failure point、rollback failure/late completion/shutdown、effect envelope全体のreverse restore order不変、bounded slices/disk lifetime、missing/tampered snapshot拒否、v1 rollback回帰。
- **Gate／docs／次／停止:** fault injection全点で復元または明示RECOVERY_REQUIREDとなり成功を偽らなければ完了し、placement／operations／roadmapを更新して`V2-6-09`へ進める。snapshot外副作用をrollback対象とする必要があれば停止する。

### V2-6-09 Undo

- **状態:** 完了。`PlacementUndoPrepareCompilerV2`／`PlacementUndoServiceV2`／`PlacementUndoApplicationServiceV2`／`PaperPlacementUndoServiceV2`、sealed `PlacementUndoPlanV2`（ADR 0027）、world-drift preflight（force禁止）、逆順restore＋baseline full verify、`UNDONE`＋snapshot保持、Paper `isRelease2Path`識別、v1 Undo不変を確認した。startup Recoveryは`V2-6-10`。
- **目的／前提:** 完了済みRelease 2 operationをconfirmation付きで安全にUndoする。前提は`V2-6-08`。
- **Scope／成果物:** operation-bound Undo plan/confirm、current-world preflight、snapshot restore、settle/full verify、journal retention transitionを実装する。
- **非Scope:** v1 Undo変更、startup Recovery、force overwrite。
- **主変更:** `core.v2.placement.undo`、Paper command/service v2 explicit path、journal Schema、tests／user/admin docs。
- **必須test／D/M/S:** actor/replay/expiry/world drift/disk/cancel/late completion、canonical restore order、bounded scan/retention budget、tampered/missing snapshot拒否、v1 Undo回帰。
- **Gate／docs／次／停止:** Release 2 Undoがworld driftを黙って上書きせずfull verifyしv1意味不変なら完了し、user/admin/placement／roadmapを更新して`V2-6-10`へ進める。force semanticsが必要なら別Taskとして停止する。

### V2-6-10 Recovery

- **状態:** 完了（2026-07-19）。`core.v2.recovery`の`PlacementRecoveryServiceV2`が、永続journal／published snapshot／worldの3証拠を読み取り専用で保守的分類（`NO_WORLD_MUTATION`／`SAFE_TO_ROLLBACK`／`SAFE_TO_ACCEPT`／`ALREADY_TERMINAL`／`MANUAL_INTERVENTION_REQUIRED`）し、snapshot strict再検証・durable lease・manifest checksum／capability／overlay ordinal照合・union effect envelope全体のbounded exact world scanなしにworld actionを提示しない。actor-bound一回用confirmation（`RECOVERY_ROLLBACK`／`RECOVERY_ACCEPT`）付きsealed `PlacementRecoveryPlanV2`（Schema／example／codec）、中断journalのVERIFIED→APPLIED／RESTORED→SNAPSHOTTED決定論的late reconciliation→V2-6-08 rollback委譲、full exact scan再一致時のみのaccept（terminal `APPLIED`）、terminal限定dry-run bound cleanup retentionを実装し、`PaperPlacementRecoveryServiceV2.isRelease2Path()`でv1 recoveryと分離した（ADR 0028）。各persisted state／missing・tamper／lease欠損／replay・actor・expiry／drift／budget／cleanupを`PlacementRecoveryServiceV2Test`で検証した。自動acceptとv1 recovery意味変更は行っていない。
- **目的／前提:** restart後のRelease 2 operationを保守的にdiagnose/rollback/acceptできるようにする。前提は`V2-6-08`〜`V2-6-09`。
- **Scope／成果物:** journal/artifact/world evidence classification、manifest capability/tile/child/overlay ordinalとの照合、late operation reconciliation、deterministic resume可否判定、recovery plan、confirmation-bound rollback/accept、cleanup retentionを実装する。
- **非Scope:** 自動accept、v1 recovery意味変更、external backup integration。
- **主変更:** `core.v2.recovery`、journal/recovery Schema、Paper admin path、tests／operations docs。
- **必須test／D/M/S:** each persisted state/restart/missing artifact/tamper/late scheduler/disk full、surface/volume/fluid途中状態、tile/child/overlay順のstable classification、bounded scan/retention budget、ambiguous stateはmanual-required、v1 recovery回帰。
- **Gate／docs／次／停止:** ambiguityを成功へ分類せず全stateに安全なoperator actionを示せれば完了し、operations／admin／roadmapを更新して`V2-6-11`へ進める。一意な証拠がない状態を自動修復する必要があれば停止する。

### V2-6-11 Provider, manual, and image capability integration

- **状態:** 完了（2026-07-19）。`ai.spi.v2`（capability catalog／negotiator、versioned provider SPI）、OpenAI／Anthropic／import／fixture adapters、`TerrainDesignApplicationServiceV2`（manual constraint binding＋reference-image soft draft）、Design Package v2 publisher／Schema／example、ADR 0029を実装した。no-fallback、provider間canonical Intent一致、soft draft HARD昇格拒否、path／secret拒否、v1 default不変をcontract testで確認。CLI／Paper既定`design`はv1のまま。次は`V2-6-12`。
- **目的／前提:** OpenAI／Anthropic/manual/import/image pathsからv2 capabilityを明示選択し同じcanonical compileへ接続する。前提はV2-2〜5 supported contractsとRelease 2。
- **Scope／成果物:** provider capability negotiation、v2 structured intent/import、manual/constraint bundle binding、reference image→soft draft/confirmation boundary、no-fallback dispatchを実装する。
- **非Scope:** new AI model feature、Web UI、画像からhard geometryの暗黙生成、Paper apply internals。
- **主変更:** `ai.spi` versioned interfaces、provider adapters、`core.v2` design service、request/provider docs、tests。
- **必須test／D/M/S:** each provider/manual/image path、unsupported model/capability/unknown version、canonical output equivalence、bounded payload/image/budget、secret redaction/path security、v1 provider default回帰。
- **Gate／docs／次／停止:** provider差がcanonical Intent後のgenerationへ影響せずunsupported capabilityをfallbackしなければ完了し、README／provider／migration／roadmapを更新して`V2-6-12`へ進める。real credentialをfixtureへ必要とする場合はcontract testで止め外部smoke Taskを追加する。

### V2-6-12 Release 2 cross-capability hardening

- **状態:** 完了（2026-07-19）。`ReleaseCapabilityDependencyMatrixV2`をvalid prefix正本とし、Catalog／PlacementPlanが参照。cross-version reader policy、artifact limits catalog、placement eligibility verifier、shared placement input contract（foundation／bathymetry→canonical overlay stream）、ADR 0030を実装。全5 prefixのdirectory／ZIP eligibilityとtamper corpusを`ReleaseCrossCapabilityHardeningV2Test`で確認。Release 1 allowlistは未共有・未緩和。次は`V2-6-13`。
- **目的／前提:** Release 2 directory/ZIPと全capability組合せのstrict security回帰を閉じる。前提はV2-2〜5 capabilitiesと`V2-6-01`。
- **Scope／成果物:** capability dependency matrix、cross-version reader policy、surface／hydrology／environment／sparse-volumeの共通placement input contract、all artifact limits、tamper corpus、placement eligibility verifierを実装する。将来foundation/bathymetryも新しいFeature別placement typeではなく既存canonical streamへ収容できることをcontract testで固定する。
- **非Scope:** world mutation、new artifact type、Release format 3。
- **主変更:** `format.v2.release` verifier、security tests/corpus、artifact-format／security／migration docs。
- **必須test／D/M/S:** every valid capability prefix、unknown/missing/extra/duplicate/version/checksum/path/ZIP bomb、order/charset/locale不変、entry/expand/decode/disk budget、Release 1 strict regression。
- **Gate／docs／次／停止:** 全valid prefixを読み全corruptionをdirectory/ZIP双方で拒否しplacement eligibilityがstrictなら完了し、artifact/security/migration／roadmapを更新して`V2-6-13`へ進める。allowlist共有でRelease 1を緩める必要があれば停止する。

### V2-6-13 Operational metrics, diagnostics, and retention

- **状態:** 完了（2026-07-19）。`core.v2.operations`へclosed-label metrics、redacted diagnostics、audit JSONL correlation、actor-bound Release 2 retention（dry-run／confirm／audit）、Schema／example、ADR 0031、Paper `/lfc ops metrics|diagnose`を実装。自動削除default・telemetry SaaS・Web UIは含まない。次は`V2-6-14`。
- **目的／前提:** Release 2 generation/placement/recoveryの運用証拠と保持policyを完成する。前提は`V2-6-06`〜`V2-6-12`。
- **Scope／成果物:** stage/queue/memory/disk/settle/verify metrics、redacted admin diagnostics、audit correlation、snapshot/release retention dry-run/confirm cleanupを実装する。
- **非Scope:** external telemetry SaaS、Web UI、自動削除default。
- **主変更:** `core.v2.operations`、Paper admin commands、metric/audit Schema、tests／admin/operations docs。
- **必須test／D/M/S:** bounded labels/queues/logs、cancel/failure/restart/cleanup, stable metric units/reduction、retention/disk budget、secret/path/raw payload redaction、actor-bound cleanup確認。
- **Gate／docs／次／停止:** operatorが各failureをcorrelation IDから診断できcleanupがdry-run/confirm/audit付きなら完了し、admin/operations/security／roadmapを更新して`V2-6-14`へ進める。unbounded cardinalityやsecret保存が必要なら停止する。

### V2-6-14 WorldEdit 7.3.19 smoke

- **状態:** 完了（2026-07-19）。WorldEdit 7.3.19単独profileで`/lfc r2 plan→confirm→execute→undo`を`APPLIED`／`UNDONE`まで実機実行。[evidence](audits/v2-6-14-worldedit-smoke-evidence.md)。次は`V2-6-15`。
- **目的／前提:** 今回buildのRelease 2 end-to-end placementをWorldEdit 7.3.19実機でsmokeする。前提は`V2-6-01`〜`V2-6-13`および`V2-6-20`／`V2-6-21`。
- **Scope／成果物:** isolated server/worldでgenerate→Release verify→plan→confirm→snapshot-all→apply→settle→full verify→Undo、logs/versions/checksums/cleanup evidenceを記録する。
- **非Scope:** FAWE、500/1000 performance claim、Beta hardening既存項目の自動完了。
- **主変更:** smoke scripts/fixtures、audit evidence、development/release checklist/roadmap。production変更はsmoke defectの小規模修正だけ。
- **必須test／D/M/S:** main-thread assertions、fluid/volume/coastal fixture、restart-safe journal、measured peak/disk within declared budget、no secret/world artifact commit、v1 smoke regression。
- **Gate／docs／次／停止:** exact build/version付き実機evidenceとUndo full verifyが揃えば完了し`V2-6-15`へ進める。環境がなければ`BLOCKED_EXTERNAL`として手順を残し、mockで完了扱いにしない。

### V2-6-15 FAWE standalone smoke

- **状態:** 完了（2026-07-19）。FAWE 2.15.2単独profile（`runFaweServer`／`run-fawe`）で`/lfc r2 plan→confirm→execute→undo`を`APPLIED`／`UNDONE`まで実機実行。[evidence](audits/v2-6-15-fawe-smoke-evidence.md)。`V2-6-16`／`V2-6-17`は無効化のため次は`V2-6-18`。
- **目的／前提:** version固定FAWE単独profileでRelease 2 placement smokeを実行する。前提は`V2-6-14`。
- **Scope／成果物:** dependency lock/ADRへFAWE buildを記録し、WorldEdit plugin併用なしで同じend-to-end/Undo/recovery smoke evidenceを取得する。
- **非Scope:** 過去FAWE smoke流用、500/1000 claim、FAWE private API採用。
- **主変更:** FAWE smoke profile/scripts、ADR、audit、development/release checklist/roadmap。
- **必須test／D/M/S:** gateway close/async completion/main-thread boundary、same canonical expected checksum、measured queue/memory/disk、no secret/server artifacts commit、failure/recovery smoke。
- **Gate／docs／次／停止:** 今回buildと明記versionの独立実機evidenceが揃えば完了し、当初は`V2-6-16`へ進める想定だった。`V2-6-16`／`V2-6-17`無効化後は`V2-6-18`へ進める。環境不足は`BLOCKED_EXTERNAL`、private API必須やsemantic差は停止してadapter Taskを追加する。

### V2-6-16 500×500 real-world measurement

- **状態:** 無効化（CANCELLED、2026-07-19）。IDは振り直さない。典型開発ホスト（例: 8 GiB RAM）ではeffect envelope全走査のcontainment／settle／full verifyが許容壁時計を超え、Acceptanceの完全E2E（apply→settle→full verify→Undo）を閉じられないことが判明した。500角 Paper placementをcatalog候補／`SUPPORTED`へしない。再実測が必要なら新Task IDを追加する（本IDは復活させない）。再実測は`V2-11-04`として登録済み（2026-07-20）。
- **目的／前提（凍結）:** 500×500 Release 2をsupported placement候補として実測する。前提は`V2-6-14`〜`V2-6-15`。
- **Scope／成果物（凍結）:** representative capability fixtureのgenerate/export/apply/settle/full verify/Undo、peak memory/disk/time/scheduler slices、failure cleanup evidence。
- **非Scope:** 1000×1000、上限の自動引上げ、offline測定による代用。
- **Gate／docs／次／停止:** 本Taskは実行しない。次のTrack A Taskは`V2-6-18`。500角は未測定のまま`UNSUPPORTED`／明示hard limit対象とする。

### V2-6-17 1000×1000 real-world measurement

- **状態:** 無効化（CANCELLED、2026-07-19）。IDは振り直さない。前提`V2-6-16`が無効化され、かつ1000角実測はさらに重いためTrack Aの必須経路から外す。1000角 Paper placementをcatalog候補／`SUPPORTED`へしない。再実測が必要なら新Task IDを追加する。再実測は`V2-11-05`として登録済み（2026-07-20）。
- **目的／前提（凍結）:** 1000×1000 Release 2 placementを別Taskとして実測する。前提は`V2-6-16`。
- **Scope／成果物（凍結）:** 1000角のgenerate/export/apply/settle/full verify/Undo、peak memory/disk/time/scheduler、failure/recovery evidence。
- **非Scope:** 500角結果の外挿、測定なしのsupport宣言、Web UI。
- **Gate／docs／次／停止:** 本Taskは実行しない。次のTrack A Taskは`V2-6-18`。1000角は未測定のまま`UNSUPPORTED`／明示hard limit対象とする。

### V2-6-18 Final supported catalog

- **状態:** 完了（2026-07-19）／V2-6／P0。`FeatureSupportCatalogV2`（contract `feature-support-catalog-v1`）、`BuiltInFeatureSupportCatalogV2`（71 entry）、`FeatureSupportCatalogConsistencyVerifierV2`、Schema／sealed example、false-promotion corpusを追加した。placement hard limitはsmoke実測の**64×64**。Paper能力列と500／1000は`SUPPORTED`にしない。次は`V2-6-19`。
- **目的／前提:** feature/capability/dimension/provider/runtimeのsupported matrixを証拠から固定する。前提は`V2-6-11`〜`V2-6-15`および`V2-6-20`／`V2-6-21`。`V2-6-16`／`V2-6-17`は無効化済みのため未測定寸法（500／1000 Paper apply）を`SUPPORTED`にしないことと、smoke規模以下の明示hard limitをcatalogへ固定することが必須である。
- **Scope／成果物:** `primaryRole`／`allowedUsages[]`と13能力（intent、offline generate、validation、preview、export、standalone、child、overlay、paper apply、post-apply validation、snapshot、rollback、restart recovery）のversion付きcatalog、required capability/runtime/version、placement dimension limit、preset availability、unsupported/deferred diagnostics、README/user docs、taxonomy-code consistency verifierを整合する。legacy `lifecycleStatus`は互換表示に限定する。
- **非Scope:** 新feature追加、gate waiver、Web UI、Beta hardening未完項目の削除、無効化した500／1000実測の再実施。
- **主変更:** built-in descriptor/catalog data、capability matrix docs/Schema、README/user/provider/limitations/roadmap。
- **必須test／D/M/S:** 全entryの必須13能力、role/usage整合、enum/binding/generator/validator/preview/export/evidence link照合、child-onlyのstandalone昇格拒否、plan-level volumeとpublic Intentの分離、Paper evidenceなし昇格拒否、未測定寸法のfalse-promotion拒否、unknown/deprecated selection、catalog order/checksum、catalog size/parse budget、future capability拒否。
- **Gate／docs／次／停止:** evidence欠損能力を`EXPERIMENTAL`または`UNSUPPORTED`へ残し、実測済み上限（WE／FAWE smoke規模）だけ設定し、generator未実装kindとV2-6未完能力を`SUPPORTED`へできない自動検査が通れば完了する。手動主張だけ、単一statusへの再集約、Beta項目削除が必要なら停止する。

### V2-6-19 Release candidate audit and Phase gate

- **状態／Phase／優先度:** 完了（2026-07-19）／V2-6／P0。`PlacementPhaseGateV2Test`（catalog非昇格・hard limit 64×64・sealed example・capability list／5 valid prefix不変・placement example portfolio決定性・R2全lifecycleのrepeat／thread／locale／timezone同一block-state map＋terminal `UNDONE`）とfull clean test／build（909 tests）でV2-6 Phase gateを閉じた。監査発見のlocale依存`toLowerCase()` 6箇所をchecksum不変の`Locale.ROOT`最小修正として同Task内で修正した。能力昇格は行わず`V2-11-01`へ登録し、release candidateはBeta hardening未完のため未承認のまま。証拠は [V2-6 Phase gate audit](audits/v2-6-phase-gate.md)。Track D（V2-9）／Track E（V2-10）のPaper apply capability `SUPPORTED`化の必須gate（達成済み。実昇格は`V2-11-01`）。
- **目的／前提:** V2-6とTerrain Generation v2全体を監査し親Phase gateを閉じる。前提は`V2-6-01`〜`V2-6-15`、`V2-6-18`、および`V2-6-20`／`V2-6-21`。`V2-6-16`／`V2-6-17`は無効化済みであり未完BLOCKED扱いにはしないが、未測定寸法の`SUPPORTED`主張があればgate失敗とする。
- **Scope／成果物:** contract→provider/manual/image→generation→validation/preview→Release→placement/verify/rollback/Undo/Recovery、catalog、security、performance、v1回帰のRC auditとrelease decisionを作る。
- **非Scope:** 新実装、大規模bug fix、未完Beta hardeningの免除、Web UI。
- **主変更:** audit、roadmap、release checklist、README/limitations/operationsの実態同期。production修正は行わず、defectは新Task化する。
- **必須test／D/M/S:** full clean test/build、all capability directory/ZIP tamper、全`paper_apply: SUPPORTED` scenarioのsurface/solid/air/fluid/child/overlay、determinism/runtime profile、measured admission/effect/snapshot/disk、fault injection/recovery、capability matrix false-promotion corpus、v1 Schema/generator/Release1/placement/Undo golden。
- **Gate／docs／次／停止:** 全必須Taskと能力ごとのsupport evidenceが完了し未解決critical/high riskがない時だけV2-6を完了とする。Track D（V2-9）／Track E（V2-10）はこのgateの完了を待たず並行実装できるが、新foundation featureのPaper `SUPPORTED`化にはこのgateと`V2-6-06`〜`V2-6-10`の完了が必要である。Track D／Eの進行はV2-6を延期する理由にしない。Beta hardeningの別未完項目はそのまま残す。失敗・外部環境不足・大規模修正は新Taskを登録し、Release candidateを未承認のまま停止する。

### V2-6-20 Verified Release 2 canonical block source

- **状態／Phase／優先度:** 完了（2026-07-19）／V2-6／P0。`VerifiedReleaseCanonicalBlockSourceV2`、closeable `VerifiedReleaseViewV2`、bounded Sponge decode cursorを実装した。surface／ZIP／sparse-volume final stream、cursor reopen、post-verify drift、extra file、cancel、ZIP staging cleanupと既存tamper corpusを検証し、ADR 0033を追加した。Release format／Schema／v1は不変。次は`V2-6-21`。
- **目的／前提:** strict verify済みRelease format 2 directory／ZIPから、apply／full verify／Undo／Recoveryが再利用できるimmutable `PlacementCanonicalBlockSourceV2`を構築する。前提は`V2-5-16`／`V2-5-17`／`V2-6-01`／`V2-6-12`。
- **Scope／成果物:** manifest exact-file-setとcapability prefixの再検証、bounded Sponge v3 decode、surface＋sparse-volume overlayのcanonical X-fastest/Z/Y stream、source binding／fingerprint、directory／ZIP parity、restart-safe reopen、positive／corruption fixtures。
- **非Scope:** Paper world mutation、command routing、新artifact format、新Schema version、v1 reader／Release 1 allowlist変更。
- **主変更:** `format.v2.release`／`format.v2.tile`のstrict reader adapter、`core.v2.placement.apply` source adapter、tests／fixtures、artifact-format／security／operations docs。
- **必須test／D/M/S:** missing／extra／duplicate／case collision／traversal／symlink、unknown version／capability／palette／block state、checksum／semantic checksum tamper、directory／ZIP同値、tile／overlay順不変、locale／timezone／charset不変、entry／decode／resident／disk budget、cursor close／reopen／interrupt cleanup、v1 golden。
- **Gate／docs／次／停止:** verified Release 2からbyte-identical cursorを繰返し開け、tamper corpusをstrict rejectし、bounded resourceとv1回帰を満たせば完了して`V2-6-21`へ進める。新artifact format、unbounded dense decode、WorldEdit／Paper依存が必要なら停止する。

### V2-6-21 Release 2 Paper application／command lifecycle

- **状態／Phase／優先度:** **再完了（2026-07-20、P0再オープン）**／V2-6／P0。2026-07-19実装に加え、placement durability／Undo reservation／R2 command securityをhardeningした。既存version付きrecordだけを使うstage commit marker、fsync＋atomic publish＋strict read-back、全12 operation-store write point×before／after failpoint、confirm／apply間restart、Undo self-overlap許可／第三者overlap拒否／baseline完全復元、Recovery retryをproduction file store testで固定した。R2 commandはoperator Player／CONSOLE／RCONだけを受理し、command block／非operator、actor mismatch、deny／missing worldをworld mutation前のstable domain errorにする。permission-aware completionはdeny worldとtokenを候補へ出さない。新Release artifact／Schema／example／support昇格と後続Taskは含めない。
- **目的／前提:** Release 2のplan→effect envelope→reservation-bound confirm→snapshot-all→containment→apply→settle→full verify→rollback／Undo／Recoveryをproduction lifecycleとして組み立て、v1と混同しない明示command経路を提供する。前提は`V2-6-01`〜`V2-6-13`および`V2-6-20`。
- **Scope／成果物:** version-dispatched R2 application facade、Paper gateway／store／journal／executor所有とshutdown、explicit R2 plan／confirm／execute／status／Undo／Recovery command、restart inspection、operator diagnostics、fake-gateway integration tests。
- **非Scope:** WorldEdit／FAWE実機smoke、500／1000実測、v1 command意味変更、新Release artifact／Schema、support catalog昇格。
- **主変更:** `core.v2.placement` application orchestration、Paper lifecycle／command adapter、config／permissions、tests、commands／admin／operations／security docs。
- **必須test／D/M/S:** happy path全順序、confirm binding、全snapshot前mutationゼロ、containment failureゼロmutation、settle/full verify／rollback、late cancel commit point、restart ambiguity、Undo／Recovery actor binding、main-thread assertion、bounded queue／memory／disk admission、shutdown／interrupt cleanup、v1 command／golden回帰。P0再オープンgateとして全operation-store write failpoint（before／after publish）、restart、即時Undoのself／third-party overlap、baseline exact restore、Recovery plan retry、operator／sender／world policy／missing world／actor／completion回帰を追加する。
- **Gate／docs／次／停止:** 上記P0再オープンgateと既存Acceptanceを満たしたため再完了。新Schema／exampleは不要（operational stateは既存version付きrecordのstrict保存のみ）。support catalog昇格は行わず、後続`V2-11-01`は本Taskで開始しない。private WorldEdit／FAWE API、新artifact format、またはsnapshot外副作用の容認が必要なら停止する。

## 7. V2-7 Image fidelity（Track B: 画像・スケッチの直接制約化）

前提はV2-1 gate完了のみで、Track Aの進行を待たない。全Taskで「画像からhard geometryの暗黙生成禁止」（draft＋明示昇格）を維持する。

### V2-7-01 Deterministic land-water extraction core

- **状態:** 完了（2026-07-17再設計時に実装）。`format.v2.constraint.extract`へ`image-land-water-extract-v1`（integer-only固定閾値、alpha/UNKNOWN帯、信頼度0..255）、`ExtractedMaskDraftV2`（tagged SHA-256 semantic checksum、water/land/unknown計数）、trusted ceiling付き`ImageMaskExtractionLimitsV2`（4096/16Mピクセル/aspect32/working 64 MiB）、行単位cancelを実装した。golden分類、checksum再現（repeat/thread/locale/timezone）、寸法/aspect/pixel/working/buffer/checksum拒否、cancelを`ImageLandWaterExtractorV2Test`で検証した。CLI/Paper/Requestへは未接続、`EXPERIMENTAL`である。採用判断は [ADR 0017](../adr/0017-deterministic-image-mask-extraction.md)。

- **目的／前提:** 通常画像からland-water draftをAIなしで決定論的に抽出するcore契約を固定する。前提はV2-1 gate完了。
- **Scope／成果物:** 分類規則のversion凍結、draft record、admission limits、semantic checksum、決定性・拒否テスト。
- **非Scope:** ファイル入力封筒、draft artifact保存、preview、昇格、height/zone抽出。
- **Gate:** golden分類と決定性・拒否・cancelが通り、既存回帰が不変であること（達成済み）。

### V2-7-02 Secure extraction input envelope

- **状態:** 完了（2026-07-21）。`SecureImageExtractionEnvelopeV2`／`SanitizedArgbImageV2`／`ImageExtractionInputLimitsV2`を`format.v2.constraint.extract`へ追加し、retired `ReferenceImageProcessor`と同等のpath／symlink／hardlink alias／magic／extension／byte／pixel／aspect／APNG multi-frame／EXIF orientation検査、TOCTOU、trusted ceiling decode／working budget、cancel、raw SHA-256→draft checksum連鎖を実装した。`loadAndExtractLandWater`で実PNG／JPEG→draftのstrict経路を固定し、`SecureImageExtractionEnvelopeV2Test`で拒否・決定性・cancelを検証した。CLI／Paper／Request／draft artifactへは未接続、`EXPERIMENTAL`のまま。
- **目的／前提:** sanitized画像fileから抽出coreへのARGB供給路を固定する。前提は`V2-7-01`。
- **Scope／成果物:** v1 `ReferenceImageProcessor`と同等のpath/symlink/hardlink/magic/byte/pixel/frame/EXIF検査を持つ抽出専用envelope、decode/working budget、cancel、source checksum連鎖を実装する。
- **非Scope:** Request Schema変更、draft artifact保存、AI Provider接続。
- **主変更:** `format.v2.constraint.extract`、fixtures、image-constraint-maps docs。
- **必須test／D/M/S:** path/link/TOCTOU/bomb/multi-frame拒否、byte/pixel/decode/working budget、checksum連鎖、thread/locale不変、cancel cleanup。
- **Gate／docs／次／停止:** 達成。次は`V2-7-03`だが本Taskからは開始しない。v1画像境界の意味変更は不要だった。

### V2-7-03 Draft artifact and confidence preview

- **状態:** 完了（2026-07-21）。`ExtractedMaskDraftArtifactV2`／Codec／Publisherで`classes.u8`＋`confidence.u8`＋strict indexをstaging→read-back→atomic publishし、`ExtractedMaskDraftPreviewIndexV2`／Codec／Rendererでclass／confidence／unknownの固定palette PNG previewを1枚ずつrenderしてstrict index公開した。Schema 2件と`examples/v2/extract/`を追加。round-trip・改変／extra／symlink拒否・cancel cleanup・決定性をtestで固定。昇格／Release／CLI／Paper／Request未接続、`EXPERIMENTAL`のまま。
- **目的／前提:** draftを検証可能なartifact＋診断previewにする。前提は`V2-7-02`。
- **Scope／成果物:** draft artifactのstrict Schema/codec（staging→read-back→atomic publish）、class/confidence/unknownの固定PNG preview、strict index、budget。
- **非Scope:** 昇格、Release capability、Paper UI。
- **主変更:** `format.v2.constraint.extract`、`preview.v2` registry、新Schema、examples、docs。
- **必須test／D/M/S:** round-trip、checksum改変/欠損/extra拒否、preview palette/dimension固定、1枚ずつbounded render、cancel cleanup。
- **Gate／docs／次／停止:** 達成。次は`V2-7-04`だが本Taskからは開始しない。既存preview registryの意味変更は不要だった。

### V2-7-04 Explicit promotion to constraint map

- **状態:** 完了（2026-07-21）。`ExtractedMaskPromotionServiceV2`がconfidence閾値とUNKNOWN処理の明示指定を必須とし、U8 grayscale `land-water.png`をstaging→`SecureConstraintMapSourceLoader`＋`NumericPngDecoder`再検証→`extracted-mask-promotion-v2.json` provenanceとatomic publishする経路を実装した。Schema／`examples/v2/extract/extracted-mask-promotion-v2.json`／`ExtractedMaskPromotionServiceV2Test`でround-trip・閾値境界・改変拒否・cancel・決定性を固定。自動／暗黙昇格・AI補完・CLI／Paper／Request／Release未接続、`EXPERIMENTAL`のまま。
- **目的／前提:** draft→数値constraint PNG（V2-1経路）への明示昇格を実装する。前提は`V2-7-03`。
- **Scope／成果物:** 昇格操作（confidence閾値・UNKNOWN処理の明示指定）、生成した数値PNGのV2-1 strict decoder再検証、source→draft→constraint mapのprovenance連鎖、昇格記録。
- **非Scope:** 自動/暗黙昇格、AIによる補完、Request外の新しいhard constraint種別。
- **主変更:** `core.v2`昇格サービス、provenance Schema、examples、image-constraint-maps docs。
- **必須test／D/M/S:** 昇格round-trip（draft→PNG→field）、checksum連鎖検証、閾値境界、UNKNOWN扱いの明示指定必須、改変拒否、決定性。
- **Gate／docs／次／停止:** 達成。次は`V2-7-05`だが本Taskからは開始しない。暗黙のhard化は不要だった。

### V2-7-05 Height guide extraction

- **状態:** 完了（2026-07-21）。`ImageHeightGuideExtractorV2`（`image-height-guide-extract-v1`）がinteger-only輝度→U8 height draft（0..254 clamp、alpha < 128はno-data）を抽出し、sample空間宣言`luminance-u8-requires-explicit-height-value-meaning-v1`で3種`HeightValueMeaning`の推測を禁止した。`ExtractedHeightGuideDraftArtifactPublisherV2`と`ExtractedHeightGuidePromotionServiceV2`でdraft artifact／明示昇格（意味・scale・offset必須）→V2-1 decoder＋residual一貫を実装。Schema／example／testでgolden・no-data・clamp・3意味・決定性を固定。CLI／Paper／Request未接続、`EXPERIMENTAL`のまま。
- **目的／前提:** 輝度ベースのheight guide draft抽出を追加する。前提は`V2-7-02`（並行可: `V2-7-03`）。
- **Scope／成果物:** integer-only輝度→height draft（version凍結）、信頼度、既存3種height意味との対応宣言、昇格連携。
- **非Scope:** 等高線ベクトル化、陰影推定、AI推定。
- **主変更:** `format.v2.constraint.extract`、Schema、fixtures、docs。
- **必須test／D/M/S:** golden輝度変換、no-data、範囲clamp、決定性、budget、拒否。
- **Gate／docs／次／停止:** 達成。次は`V2-7-06`だが本Taskからは開始しない。等高線ベクトル化は不要だった。

### V2-7-06 Zone and sketch label extraction

- **状態:** 完了（2026-07-21）。`ImageZoneLabelExtractorV2`（`image-zone-label-extract-v1`）が固定sketch paletteへの整数平方ユークリッド距離量子化でzone label draftを抽出し、ambiguous帯・遠色・alpha < 128をUNKNOWNとする（k-means禁止）。`ExtractedZoneLabelDraftArtifactPublisherV2`と`ExtractedZoneLabelPromotionServiceV2`でdraft artifact／明示昇格（confidence閾値＋noData）→V2-1 categorical decoder＋`ZONE_LABEL_MAP` canonical経路を実装。Schema／example／`ImageZoneLabelExtractorV2Test`／`ExtractedZoneLabelPromotionServiceV2Test`でpalette golden・ambiguous・budget・決定性・昇格検証を固定。CLI／Paper／Request未接続、`EXPERIMENTAL`のまま。
- **目的／前提:** スケッチ・zone画像から有界・整数のみの量子化でzone label draftを抽出する。前提は`V2-7-02`。
- **Scope／成果物:** 固定palette距離量子化（反復クラスタリング禁止）、label表提案、ambiguous帯のUNKNOWN化、昇格連携。
- **非Scope:** 自由曲線のベクトル化、意味推定（AIによるlabel命名は提案のみ）。
- **主変更:** `format.v2.constraint.extract`、Schema、fixtures、docs。
- **必須test／D/M/S:** palette境界golden、ambiguous帯、label数budget、決定性、拒否。
- **Gate／docs／次／停止:** 達成。次は`V2-7-07`だが本Taskからは開始しない。自由曲線ベクトル化は不要だった。
- **モデル割当:** [model-assignment.md](model-assignment.md)参照。

### V2-7-07 Multi-source reconciliation, source diff, and Phase gate audit

- **状態:** 完了（2026-07-21）。`image-fidelity-multisource-reconcile-v1`／`image-constraint-priority-v1`でprompt vs 画像・hard vs draftの優先順位を凍結し、HARD/HARDおよび同rank SOFT peer競合をfail closed（last-write-wins禁止）にした。source-to-result差分metricとresult／conflict／source-diff previewをstrict artifactとして公開。`MultiSourceReconciliationServiceV2Test`／`ImageFidelityPhaseGateV2Test`と[V2-7 Phase gate audit](audits/v2-7-phase-gate.md)でportfolioを閉じた。抽出経路は**SUPPORTED候補**として記録し、runtimeは`EXPERIMENTAL`・CLI／Paper／Request未接続・Release capability追加なし。
- **目的／前提:** 複数入力（複数画像・prompt・手動map）の競合と優先順位を明示解決し、Phase gateを閉じる。前提は`V2-7-01`〜`V2-7-06`。
- **Scope／成果物:** source優先順位contract（prompt vs 画像、hard vs draft）、競合診断、source-to-result差分metric/preview、V2-7全portfolioの統合監査。
- **非Scope:** 新抽出種別、Release capability追加（必要なら新Task）、Paper UI。
- **必須test／D/M/S:** 競合fixture（画像同士/画像とprompt）、優先順位の決定性、差分metricのstable reduction、全corruption回帰、clean build。
- **Gate／docs／次／停止:** 達成。V2-7親Phaseを完了とし、抽出経路をSUPPORTED候補として記録した。未解決競合ruleは残っていない。
- **モデル割当:** [model-assignment.md](model-assignment.md)参照。

## 8. V2-8 Scale-up（Track C: LARGE 3000×3000）

設計正本は [scale-and-streaming.md](scale-and-streaming.md)、採用判断は [ADR 0016](../adr/0016-scale-classes-and-execution-planning.md)。V2-8はofflineのLARGE生成をgateし、LARGEのPaper配置はV2-6完了後の追加Taskとする。

**2026-07-21 scale方針決定（Option S2）:** LARGE／3072契約は凍結保持し、3000／3072をSUPPORTEDまたはactive implementation targetと表現しない。`V2-8-03`〜`V2-8-08`は保留（HOLD）とし、active next Taskから除外する。ADR 0016は現時点ではsupersedeしない。実装重点はV2-13の1024（MEDIUM）へ移し、1024完成後にLARGE正式退役（S3）を独立migration Phaseとして再審査する。保留TaskのScope・Acceptance定義は再開時の正本として本節に維持する。

### V2-8-01 Scale class, profile, tile plan, and admission contract

- **状態:** 完了（2026-07-17再設計時に実装）。`model.v2.scale`へ`ScaleClassV2`（SMALL≤512/MEDIUM≤1024/LARGE≤3072、stable id、LARGE=streaming必須）、trusted ceiling付き`ScaleProfileV2`（version凍結defaults: tile128、halo16/32、coarse4/8/16、予算）、canonical row-major `TilePlanV2`（`tileZ*countX+tileX`、halo clamp、`tile-x{X}-z{Z}`）を実装した。`core.v2.scale.ScaleAdmissionV2`（`scale-admission-v1`）が寸法/class/tile/working/retained/artifactを割当前に検査し、stable failure codeで拒否する。3000×3000=576 tileのgolden分解、決定性（repeat/thread/locale/timezone）、全failure code、ceiling clampを`TilePlanV2Test`/`ScaleAdmissionV2Test`で検証した。既存の分散寸法検査・v1・全checksumは不変で、LARGE生成は未実装・`EXPERIMENTAL`である。

- **目的／前提:** 規模別実行の単一契約を固定する。前提はなし。
- **Scope／成果物:** scale class、profile、tile plan、admission、決定性・拒否テスト。
- **非Scope:** 既存検査の置換、LARGE生成、予算実測。
- **Gate:** 契約のstrict検証と既存回帰不変（達成済み）。

### V2-8-02 Dimension policy unification

- **状態:** 完了（2026-07-21）。`model.v2.scale.ScaleDimensionPolicyV2`（`MEDIUM_HORIZONTAL_CEILING = ScaleClassV2.MEDIUM.maximumHorizontalBlocks() = 1024`）を導入し、v2 subsystemへ分散していた63ファイルの`1..1_000`水平寸法検査（`ConstraintMapSamplerV2`、`HydrologyRoutingRequestV2`、`CoastalRasterKernelV2`、coast generator 4種、compositor、feature／foundation／environment Plan record群、preview index／codec／diagnostic fields、`OfflineTilePlanV2`の領域上限、V2-7昇格サービス2種、`EnvironmentValidationInputV2`）をscale契約参照へ置換した。java routeのpredicateはMEDIUM上限1024（1025拒否）となったが、**`GenerationRequestV2.Bounds`（1..1000）とJSON Schemaの`maximum: 1000`は本Taskでは不変**（Schema変更なしのScope遵守。拡張は`V2-13-02`）。preview面積予算（1,000,000 cells）等の資源予算も非Scopeどおり不変で、1024²はSchema／request／予算の外側gateで引き続き到達不能である。境界testは`ScaleDimensionPolicyV2Test`（1024受理／1025拒否、`EnvironmentValidationInputV2`／`OfflineTilePlanV2`の追随）で固定し、`EnvironmentValidatorV2Test`の境界値を1025へ更新した。full `./gradlew test`／`./gradlew build`が通過し、v1 golden・V2-2／V2-3統合fixture・全checksum回帰は不変。1024をSUPPORTEDまたはtestedMaximumとは表現しない。次は`V2-13-01`（本Taskからは開始しない）。

- **目的／前提:** v2 subsystemへ分散した`1..1_000`検査をscale契約経由へ集約する。前提は`V2-8-01`。
- **Scope／成果物:** `ConstraintMapSamplerV2`、`HydrologyRoutingRequestV2`、`CoastalRasterKernelV2`、各coast generator、preview index等の寸法検査をscale契約参照へ置換し、MEDIUM以下の全出力checksum不変を回帰で固定する。v1（`GenerationBounds`）は変更しない。
- **非Scope:** LARGE値の受理（predicateはMEDIUM上限のまま）、予算変更。
- **主変更:** `core.v2`／`generator.v2`各所、tests。Schema変更なし。
- **必須test／D/M/S:** v1 golden、V2-2/V2-3統合fixtureのchecksum不変、寸法境界（1024/1025）拒否、決定性回帰。
- **Gate／docs／次／停止:** checksum完全不変で置換が完了すれば`V2-8-03`へ。1箇所でもchecksumが変わる場合は停止して原因を報告する。

### V2-8-03 Constraint map and field store LARGE budgets

- **状態:** 保留（HOLD、2026-07-21 S2決定。LARGE専用のためactive next Taskから除外。契約・Scope定義は再開時の正本として維持し、再開はLARGE再審査の人間承認を要する）。
- **目的／前提:** 数値PNG decode（現行1枚4Mピクセル）と`LFC_GRID_V1`/windowの上限をLARGEへ拡張する。前提は`V2-8-02`。
- **Scope／成果物:** 3072²を収容するdecode/resident/artifact上限の再設計、disk-backed windowでのpeak実測テスト、trusted ceilingの更新（ADR追記）。
- **非Scope:** generator側のLARGE対応、抽出経路。
- **必須test／D/M/S:** 3072角のdecode/window peak実測、budget拒否境界、既存4M以下回帰、決定性。
- **Gate／docs／次／停止:** 実測がbudget内で回帰不変なら`V2-8-04`へ。ImageIOのdecode保証が得られない形式は拒否のまま残す。

### V2-8-04 Coarse global planning and LARGE hydrology

- **状態:** 保留（HOLD、2026-07-21 S2決定。LARGE専用のためactive next Taskから除外。契約・Scope定義は再開時の正本として維持し、再開はLARGE再審査の人間承認を要する）。
- **目的／前提:** coarse大域計画passとhydrology global solveのLARGE実行を確定する。前提は`V2-8-03`、V2-3 gate完了。
- **Scope／成果物:** coarse cell解像度のmacro layout、priority floodの3072²実測またはcoarse solve＋reach詳細化の採用判断（ADR）、graph/field予算。
- **非Scope:** environment/volumeのLARGE対応、streaming resume。
- **必須test／D/M/S:** 3072角routingのpeak/時間実測、coarse↔fullの整合契約、決定性（tile/thread/locale/timezone）、budget拒否。
- **Gate／docs／次／停止:** LARGE routingが予算内で決定論的なら`V2-8-05`へ。予算内に収まらない場合はcoarse解像度契約を再設計するADRを書いて停止する。

### V2-8-05 Streaming generation, resume, and partial regeneration

- **状態:** 保留（HOLD、2026-07-21 S2決定。LARGE専用のためactive next Taskから除外。契約・Scope定義は再開時の正本として維持し、再開はLARGE再審査の人間承認を要する）。
- **目的／前提:** tile artifact hash＝cache key、resume、halo依存グラフによる部分再生成を実装する。前提は`V2-8-04`。
- **Scope／成果物:** tileごとのartifact hash導出（Blueprint checksum・seed・依存halo）、中断/再開、変更featureの影響tile無効化、journal。
- **非Scope:** Paper配置、Release分割。
- **必須test／D/M/S:** resume前後checksum一致、cache有無一致、部分再生成の影響範囲正確性、cancel/crash安全、budget。
- **Gate／docs／次／停止:** 全再生成と部分再生成の結果が全XZで一致すれば`V2-8-06`へ。一致しないhalo依存が見つかれば該当generator Taskを再openする。

### V2-8-06 Staged preview pyramid

- **状態:** 保留（HOLD、2026-07-21 S2決定。LARGE専用のためactive next Taskから除外。契約・Scope定義は再開時の正本として維持し、再開はLARGE再審査の人間承認を要する）。
- **目的／前提:** LARGEのpreviewを段階生成（coarse overview→tile詳細）にする。前提は`V2-8-04`（並行可: `V2-8-05`）。
- **Scope／成果物:** 解像度段数契約、downsample規則（integer-only）、bounded render、preview index拡張。
- **非Scope:** 新しいvalidation種別、Web UI。
- **必須test／D/M/S:** downsample golden、段階間整合、1枚ずつbounded render、index strict read-back。
- **Gate／docs／次／停止:** 3072角のpreview一式がbudget内なら`V2-8-07`へ。

### V2-8-07 Export and Release segmentation

- **状態:** 保留（HOLD、2026-07-21 S2決定。LARGE専用のためactive next Taskから除外。契約・Scope定義は再開時の正本として維持し、再開はLARGE再審査の人間承認を要する）。
- **目的／前提:** LARGEのRelease 2をsegment分割し結合検証する。前提は`V2-8-05`。
- **Scope／成果物:** segment単位のdirectory/ZIP、跨りmanifest、結合strict verify、disk budget。
- **非Scope:** Release format 3、Paper配置。
- **必須test／D/M/S:** segment欠損/入替/改変拒否、結合順不変、disk予約、既存capability回帰。
- **Gate／docs／次／停止:** 分割・結合のstrict verifyが通れば`V2-8-08`へ。format 2 coreの意味変更が必要なら停止する。

### V2-8-08 LARGE offline integration and Phase gate audit

- **状態:** 保留（HOLD、2026-07-21 S2決定。LARGE専用のためactive next Taskから除外。契約・Scope定義は再開時の正本として維持し、再開はLARGE再審査の人間承認を要する）。
- **目的／前提:** 3000×3000 offline統合fixtureでV2-8全体を監査しPhase gateを閉じる。前提は`V2-8-01`〜`V2-8-07`。
- **Scope／成果物:** 代表featureを含む3000角end-to-end（生成→validation→preview→分割export→self-verify）、peak memory/時間/disk実測、決定性portfolio、Phase audit。
- **非Scope:** LARGEのPaper配置（V2-6完了後の新Task）、新機能。
- **必須test／D/M/S:** 全positive/corruption、resume/部分再生成、tile正逆/thread/locale/timezone、実測admission対比、v1/Release/V2-2〜4回帰、clean build。
- **Gate／docs／次／停止:** 全gate通過時だけLARGE offlineを`SUPPORTED`とする。実測未達は明示上限（例: 2048）を証拠付きで固定して完了してもよい。未測定をsupportedにしない。

## 9. V2-9 Terrain foundation expansion（Track D）

V2-9は`V2-6-01`／`V2-6-02`がfreezeするRelease 2 placement plan／mutation-effect envelope契約だけを前提に開始でき、`V2-6-19`のPhase gate完了を待たない。Track A（V2-6）／B（V2-7）／C（V2-8）／D（V2-9）は明示依存があるTask以外を互いに待たず並行実行できる。新FeatureごとのPaper adapterは作らず、全出力をV2-6 canonical surface／solid／air／fluid placement streamへ適合させる。V2-9 Taskはoffline生成／検証／previewまでを完了条件とし、Paper apply capabilityの`SUPPORTED`宣言はV2-6 apply/verify/rollback/Undo/Recovery（`V2-6-06`〜`V2-6-10`）と`V2-6-19`のPhase gate完了後に別Taskで接続する。

### V2-9共通contract（全Task必須）

- **Design／implementation:** vertical sliceはintent/schema→compile→named derived seed→deterministic generation→validation→preview→export→tests→docsを閉じる。未実装kindへのfallback noise、placeholder、暗黙default、priority-only conflictは禁止する。
- **Determinism:** request seedからfeature stable key/IDでseedを分離し、input/module/candidate/tile order、thread scheduling、locale、timezone、charsetに非依存とする。preview／export／Paper expected streamは同一query結果を使う。
- **Ownership／transition:** 変更可能なcell/voxel、parent/child/overlay ordinal、overlap解決、transition band、solid/fluid競合、surface/volume接続をplanへfreezeする。HARD同士はerror、last-write-winsは禁止する。
- **Memory／security:** `ScaleProfileV2`とadmissionを使い、最大3072角を想定しても全域dense 3Dを保持しない。tile、coarse global plan、sparse AABB/interval、bounded windowを使う。寸法、座標、finite数値、標高/深度、graph node/edge、nesting、allocation、decode/disk/path/manifestを上限検査する。LARGEをsupportedにできるのはV2-8 gate後だけである。
- **Compatibility:** v1 Schema/generator `3.0.0-phase6`/Release 1/placement/Undo、既存FeatureKind ID、Release 2 manifest、seed checksum、v2 fixtureを変更しない。既存specialized featureの出力変更は新schema/generator versionとmigration/aliasを必要とする。
- **Tests／docs:** positive、negative/corruption、strict round-trip、whole/tile/seam、tile正逆、1/4 thread、module/candidate順、locale/timezone/charset、cancel/cleanup、CPU/memory/disk/artifact budget、v1/直前capability回帰を実行し、taxonomy、design、example、roadmapを同期する。

### V2-9-01 Surface foundation field, ownership, and transition contract

- **状態／Phase／優先度:** 完了／V2-9／P0。
- **状態詳細:** `SurfaceFoundationPlanV2`（`surface-foundation-field-contract-v1`）、`SurfaceFoundationPlanCompilerV2`（ScaleAdmission＋named seed）、`SurfaceFoundationMergeCompilerV2`、Schema／example／strict codecを実装。empty/minimal strict round-tripと2 synthetic ownerのwhole/tile同checksum、ownerless／tie／undeclared overlap／band外／seed collision／scale admission拒否を`SurfaceFoundationPlanCompilerV2Test`で検証。kindはSUPPORTEDへしていない。
- **目的／背景:** PLAIN、hill、mountain、valley、river、wetlandが共有する2.5D field、ownership、transition、seed契約を先に固定し、specialized generatorの重複を防ぐ。
- **Scope／成果物:** version付きfoundation plan、surface class/elevation/residual/owner/transition descriptors、stable seed namespace、merge/interaction compiler、ScaleProfile admission、strict Schema/example/codec。共通contractのみを実装しkindをsupportedへしない。
- **非Scope:** 個別形状generator、既存ALPINE/GLACIAL/MEANDERING出力変更、Paper実装、LARGE enable。
- **依存／変更面:** `V2-6-02`（placement mutation/effect envelope契約）、`V2-8-01`（`ScaleProfileV2`／`ScaleAdmissionV2`）。`V2-8-02`寸法統一と`V2-6-19` Phase gateの完了は前提としない。`model.v2.foundation`、`core.v2` compiler、`generator.v2.foundation`、Schema/design/tests。
- **個別要件:** ownerなしcell、同票owner、未宣言overlap、0..32外transition、seed collision、field/support/halo budgetを拒否する。
- **Acceptance／停止:** empty/minimal planがstrictで、2つのsynthetic ownerのwhole/tile mergeが同checksumなら完了する。既存field意味のin-place変更または新artifact formatが必要なら停止する。

### V2-9-02 PLAIN and HILL_RANGE vertical slices

- **状態／Phase／優先度:** 完了／V2-9／P0。
- **状態詳細:** `PLAIN`／`HILL_RANGE`をIntentへ追加し、`PlainPlanV2`／`HillRangePlanV2`、integer-only generator、foundation merge経由のplain↔hill transition、validation artifact／preview index、sealed plan exportを実装。dedicated moduleは`EXPERIMENTAL`だが、既存WorldBlueprint checksum不変のためグローバルcatalogへは未登録（diagnostic bindingのまま）。`BACKSHORE_PLAINS`はID維持のまま`BackshorePlainsAliasV2`でPLAIN候補へ写像。`SUPPORTED`昇格はしていない。
- **目的／背景:** v1 zoneや`BACKSHORE_PLAINS` diagnosticを実装済みと見なさず、基準面と低〜中reliefの共通基盤を作る。
- **Scope／成果物:** `PLAIN`と`HILL_RANGE`のtyped parameter、compiler、integer-only generator、microrelief/ridge/saddle fields、validator、preview、Release 2 export、fixtures。`BACKSHORE_PLAINS`はlegacy alias/migration候補を設計するが既存IDを削除しない。
- **非Scope:** forest/grassland ecology、mountain peak、river routing、Paper専用処理。
- **依存／変更面:** `V2-9-01`。Intent/schema追加はswitch影響を全列挙し、generator完成までcatalogを`EXPERIMENTAL`に保つ。
- **個別要件:** plainはdefault/no-opにせず微地形と地下水handoffを持ち、hillはclosed ridge/saddle budgetとplain transitionを持つ。
- **Acceptance／停止:** 2 kindのvertical sliceとtransition corruptionが通り、既存coast/hydrology/v1 checksum不変なら完了する。aliasで既存serializationを変える必要があればversioned migration Taskへ分離する。

### V2-9-03 MOUNTAIN_RANGE and VALLEY foundation

- **状態／Phase／優先度:** 完了／V2-9／P0。
- **状態詳細:** `MOUNTAIN_RANGE`／`VALLEY`をIntentへ追加し、`MountainRangePlanV2`／`ValleyPlanV2`、ridge／peak／saddle／spur／pass／foothillのderived component catalog（FeatureKind化なし）、V／U cross-section、floor／shoulder／transition、`FoundationMountainValleySliceCompilerV2`、validation／preview／sealed exportを実装。dedicated moduleは`EXPERIMENTAL`だが既存WorldBlueprint checksum不変のためグローバルcatalog未登録（diagnostic binding）。ALPINE／GLACIAL／FJORD specialized module・seed・parametersは未変更。`SUPPORTED`昇格はしていない。
- **目的／背景:** ALPINE/GLACIAL mountain、fjord、river valleyが再利用できるrange/valley coreを、既存specialized outputを変えず導入する。
- **Scope／成果物:** general range/valley plans、ridge/peak/saddle/spur/pass/foothill component catalog、V/U cross-section、floor/shoulder/transition fields、vertical slice、validator/preview/export。
- **非Scope:** specialized generatorの全面リファクタ、glacier ice、karst、escarpment、Paper専用処理。
- **依存／変更面:** `V2-9-01`、V2-3 mountain/fjord contracts。既存moduleはadapter/version transitionで段階再利用する。
- **個別要件:** componentはFeatureKind化せずstable derived IDを持つ。ridge graph/floor owner conflict、valley/fjord/river connection、peak/pass count budgetを検査する。
- **Acceptance／停止:** 2 kindのvertical sliceが通り、ALPINE/GLACIAL/FJORD golden checksumが旧versionで不変なら完了する。既存seedを黙って再解釈する必要があれば停止する。

### V2-9-04 General RIVER contract and vertical slice

- **状態／Phase／優先度:** 完了／V2-9／P0。
- **状態詳細:** public `FeatureKind.RIVER`と`RiverParameters`／`RiverPlanV2`（source→mouth reach graph、bank／bed／discharge／floodplain handoff）を`EXPERIMENTAL`で追加。`FoundationRiverSliceCompilerV2`がvalidation／preview／sealed exportを閉じる。dedicated moduleはグローバルcatalog未登録（diagnostic binding）。`MEANDERING_RIVER`／`HydrologyRiverModuleV2`／`MeanderingRiverPlanV2` serializationとSUPPORTED bindingは不変。`MeanderingRiverSubtypeBridgeV2`はlegacy params→suggested `RiverParameters`の写像のみ。orphan／self-loop graph拒否を検証。`SUPPORTED`昇格はしていない。
- **目的／背景:** public `MEANDERING_RIVER`に限定されない一般riverを、V2-3 global Hydrology IRの共通Featureとして定義する。
- **Scope／成果物:** `RIVER` typed contract、source-to-mouth reach graph、bank/bed/discharge/floodplain handoff、compiler/generator/validator/preview/export、`MEANDERING_RIVER`互換subtype transition。
- **非Scope:** braided/bedrock/estuary、rapids等のgraph role拡張、Paper個別配置。
- **依存／変更面:** `V2-9-01`、V2-3 IR/routing/reconciliation。既存`RiverVariant`/fixtureのserializationを維持する。
- **個別要件:** source-mouth reachability、bed monotonicity、confluence flow、cross-tile continuity、graph node/edge/degree/work budget、cycle/duplicate/orphan拒否を固定する。
- **Acceptance／停止:** straight/general riverとlegacy meanderが同IRでstrict生成でき、legacy seed output不変なら完了する。V2-3 planの非version的再解釈が必要なら停止する。

### V2-9-05 FLOODPLAIN and MARSH hydrologic foundation

- **状態／Phase／優先度:** 完了／V2-9／P0。`FLOODPLAIN`／`MARSH` parameter／plan、river adjacency、groundwater／hydroperiod／wetness、microrelief／open-water、fluid／solid ownership、`FoundationFloodplainMarshSliceCompilerV2`、validator／preview／sealed exportを`EXPERIMENTAL`で実装した。surface mergeはfloodplain+marsh、river graphは別compile。`MANGROVE_WETLAND`回帰を維持し、bog／fen／salt-marsh／ecology FeatureKind化とmangrove再実装は含まない。`SUPPORTED`昇格とWorldBlueprint埋込はしていない。

- **目的／背景:** river fieldだけのfloodplainとv1 wetlandを、独立ownershipを持つv2 hydrologic surfaceへ引き上げる。
- **Scope／成果物:** `FLOODPLAIN`/`MARSH` parameter/plan、river adjacency、groundwater/hydroperiod/wetness、microrelief/open-water transition、generator/validator/preview/export。MANGROVEは既存kindのままprofile/childとして再利用する。
- **非Scope:** mangrove tree/root再実装、bog/fen/salt-marsh kinds、ecology FeatureKind化。
- **依存／変更面:** `V2-9-02`、`V2-9-04`、V2-4 water/environment fields。
- **個別要件:** dry marsh、filled channel、river disconnect、groundwater/hydroperiod conflict、owner overlapをrejectし、fluid/solid ownershipを明示する。
- **Acceptance／停止:** floodplain-river-marsh transitionのvertical sliceとMANGROVE回帰が通れば完了する。ecologyをshape generatorへ吸収する必要があれば停止する。

### V2-9-06 ROCKY_COAST and SEA_CLIFF foundation

- **状態／Phase／優先度:** 完了／V2-9／P0。`RockyCoastPlanV2`／`SeaCliffPlanV2`／`FoundationRockyCoastCliffSliceCompilerV2`がshelf／cliff face／talus／notch・coast transition・sea-cave／overhang host AABB handoffのvertical sliceを閉じた。moduleは`EXPERIMENTAL`・catalog未登録。`ROCKY_CAPE`と既存SeaCave／Overhang checksumは不変。
- **目的／背景:** v1 rocky coast、v2 rocky cape、V2-5 sea cave/overhangをつなぐcoast/volume hostを作る。
- **Scope／成果物:** `ROCKY_COAST`/`SEA_CLIFF` vertical slices、rock shelf/cliff face/talus/notch descriptors、coast transition、`CARVES_FLANK_OF`/`SUPPORTS_FROM` host handoff、validator/preview/export。
- **非Scope:** wave simulation、sea stack generator、existing `ROCKY_CAPE` output rewrite、Paper adapter。
- **依存／変更面:** `V2-9-01`、V2-2 coast、V2-5 sea-cave/overhang contracts。
- **個別要件:** cliff face/support AABB、marine opening、shore side、talus material、surface-volume ownership、halo/window budgetを検査する。
- **Acceptance／停止:** rocky coast↔cape/beach transitionとsea-cave/overhang host fixtureがstrictで既存coast checksum不変なら完了する。dense cliff voxel正本が必要なら停止する。

### V2-9-07 SINGLE_ISLAND, ARCHIPELAGO, and VOLCANIC_CONE foundation

- **状態／Phase／優先度:** 完了／V2-9／P0。`SingleIslandPlanV2`／`ArchipelagoPlanV2`／`VolcanicConePlanV2`と3 slice compiler、validation／preview／export、`VolcanicIslandConeAdapterV2` suggested-paramsを閉じた。moduleはEXPERIMENTAL・catalog未登録（diagnostic binding）。既存`VOLCANIC_ARCHIPELAGO` checksum／hook／moduleは未変更。
- **目的／背景:** volcanicに限定されないisland ownershipと、既存VOLCANIC_ARCHIPELAGO内で再利用できるcone coreを定義する。
- **Scope／成果物:** island mass/shore/drainage/submarine saddle、group spacing/dominance、cone/crater/radial drainageのplansと3 vertical slices、validator/preview/export、legacy volcanic adapter。
- **非Scope:** caldera standalone昇格、lava tube、atoll/coral ecology、既存volcanic checksum変更。
- **依存／変更面:** `V2-9-01`、`V2-9-06`、V2-3/4 volcanic contracts。
- **個別要件:** dry-land gap、component count、shore/basin ownership、saddle depth、cone containment、derived seedを検査する。
- **Acceptance／停止:** non-volcanic single/groupとcone sliceが通り、VOLCANIC_ARCHIPELAGO旧version output不変なら完了する。既存child hook ID変更が必要なら停止する。

### V2-9-08 OCEAN_BASIN, CONTINENTAL_SHELF, and CONTINENTAL_SLOPE core

- **状態／Phase／優先度:** 完了／V2-9／P0。`OceanBasinPlanV2`／`ContinentalShelfPlanV2`／`ContinentalSlopePlanV2`と3 slice＋`FoundationBathymetryTransectCompilerV2`、validation／preview／streaming underwater exportを閉じた。moduleはEXPERIMENTAL・catalog未登録（diagnostic binding）。dense 3D water配列なし。`SUPPORTED`昇格はしていない。
- **目的／背景:** coastから深海まで連続するbathymetry depth fieldとownershipを先に閉じる。
- **Scope／成果物:** basin/shelf/slope plans、water-level/depth/slope/coast-distance fields、basin→slope→shelf→coast transition、3 vertical slices、validator/preview/export。
- **非Scope:** submarine canyon、abyssal/trench/ridge/seamount、dynamic ocean、Paper専用水処理。
- **依存／変更面:** `V2-9-01`、`V2-9-06`、V2-3 marine boundary、水中columnはV2-6 canonical fluid contractへ適合。
- **個別要件:** depth finite/range、monotone transition、shelf/slope width、water column/solid floor conflict、global field freeze後のtile query、resident/artifact budgetを検査する。
- **Acceptance／停止:** coast-to-basin断面とwhole/tile depth/checksum、underwater exportが通れば完了する。全域dense 3D water arrayまたはFeature別Paper applyが必要なら停止する。

### V2-9-09 SUBMARINE_CANYON vertical slice

- **状態／Phase／優先度:** 完了／V2-9／P1。
- **目的／背景:** shelf/slopeを横断する海底谷をsurface `CANYON`と識別し、bathymetry ownershipへ接続する。
- **Scope／成果物:** typed spline/plan、shelf head、slope crossing、basin outlet、bounded carve、validator/preview/export。
- **非Scope:** continental sediment simulation、surface canyon ID変更、deep trench。
- **依存／変更面:** `V2-9-08`、既存CANYON cross-section assets（reuse only；`LandformCanyonModuleV2`／`CanyonPlanV2`未変更）。
- **個別要件:** head/outlet containment、down-gradient continuity、floor depth、shelf/slope owner handoff、water/solid column、cross-tile seamを検査する。
- **Acceptance／停止:** positive/blocked/out-of-host fixturesとbathymetry regressionが通れば完了する。surface CANYONの意味変更が必要なら別kind/versionで停止する。
- **完了要約:** `FeatureKind.SUBMARINE_CANYON`＋`SubmarineCanyonParameters`、`SubmarineCanyonPlanV2`／`FoundationSubmarineCanyonSliceCompilerV2`、EXPERIMENTAL module（catalog未登録・diagnostic binding）、whole／tile＋underwater streaming checksum。`SUPPORTED`／WorldBlueprint埋込なし。

### V2-9-10 CAVE_ENTRANCE surface-volume connector

- **状態／Phase／優先度:** 完了／V2-9／P0。
- **目的／背景:** diagnostic-only enumを、surface ownerとCAVE_NETWORKの明示connectorとして完成する。
- **Scope／成果物:** entrance plan、surface opening/approach、cave reachability、roof/support/clearance、ordered carve、validator/preview/exportのvertical slice。
- **非Scope:** cave generator再実装、underground river、Paper個別配置、arbitrary surface breakthrough。
- **依存／変更面:** `V2-9-03`、V2-5-06/15/17。host surface/volume checksumsへbindingする。
- **個別要件:** exactly one surface host/one cave target、orphan/unreachable/thin roof/flood leak/owner conflict、AABB/support budgetを検査する。
- **Acceptance／停止:** entrance→cave reachabilityとsurface/volume seamless query/exportが通れば完了する。既存cave plan checksumをin-place変更する必要があればversion移行へ分離する。
- **完了要約:** `CaveEntranceParameters`、`CaveEntrancePlanV2`／`FoundationCaveEntranceSliceCompilerV2`、EXPERIMENTAL module（catalog未登録・diagnostic binding）、frozen `CaveNetworkPlanV2` checksum bind、seamless query／carve export。`SUPPORTED`／WorldBlueprint埋込なし。

### V2-9-11 UNDERGROUND_RIVER and flooded-volume connection

- **状態:** 完了。`UndergroundRiverParameters`／`FloodedCaveParameters`、`UndergroundRiverPlanV2`／`FoundationUndergroundRiverSliceCompilerV2`、EXPERIMENTAL module（catalog未登録・diagnostic binding）、frozen cave＋underground-lake checksum bind、carve→単一`ADD_FLUID`、FLOODED_CAVE hook、whole／tile／scene exportを閉じた。`SUPPORTED`／WorldBlueprint埋込なし。次は`V2-9-12`。

- **目的／背景:** cave、underground lake、surface river/entranceをつなぐbounded underground fluid graphを定義する。
- **Scope／成果物:** underground reach graph、source/outlet、carve→fluid ordering、cave/lake/entrance relations、`FLOODED_CAVE` fluid-region hook、validator/preview/export vertical slice。
- **非Scope:** full fluid simulation、karst-specific generator、sump/shaft FeatureKind、Paper個別fluid code。
- **依存／変更面:** `V2-9-04`、`V2-9-10`、V2-5 underground lake/query/CSG。
- **個別要件:** reachability/gradient/flow continuity、single fluid owner、leak/air pocket policy、graph/CSG/AABB/interval budget、fluid orderingを検査する。
- **Acceptance／停止:** cave→underground river→lake/outlet sceneがwhole/tile/exportで一致しV2-6 common fluid contractと互換なら完了する。dynamic simulation必須なら停止する。
- **完了要約:** static `ADD_FLUID`（dynamic simなし）、単一`fluid.underground-lake` owner、frozen host checksum不変、CaveEntrance moduleは引き続きEXPERIMENTAL。

### V2-9-12 Macro land-water topology contract

- **状態:** 完了。`MacroLandWaterTopologyPlanV2`／`MacroLandWaterTopologyPlanCompilerV2`／`MacroLandWaterTopologySliceCompilerV2`、manual mask／zone→topology、adjacency／containment／min-width、validator／preview／canonical sidecar、whole／tile／thread freezeを閉じた。FeatureKind追加なし。次は`V2-9-13`。

- **目的／背景:** bay/cove/headland/peninsula/isthmus/strait/enclosed basin/coastal embaymentをgenerator kindではなくtopology constraintとして表す。
- **Scope／成果物:** region topology primitive、land/water adjacency/connectivity/containment/min-width、mask/zone semantic、compiler、topology validator/preview、canonical sidecar。FeatureKindは追加しない。
- **非Scope:** 8個のshape generator、画像draftの暗黙hard昇格、coast feature shaping。
- **依存／変更面:** V2-1 constraint maps、`V2-9-01`、画像由来入力は`V2-7-04`完了時だけ利用可。
- **個別要件:** disconnected strait、collapsed isthmus、nested basin、ambiguous boundary、node/edge/nesting/raster/decode budgetを検査する。
- **Acceptance／停止:** manual mask/zoneから同じtopologyがtile/order/thread非依存でcompileされ後段coast/bathymetryへfreezeされれば完了する。FeatureKind列挙が必要になる設計なら停止する。
- **完了要約:** zone-split connected components、`MACRO_CONSTRAINT` kinds、strict Schema／example、coast／bathymetry向け`freezeChecksum`。画像draft経路は未接続。

### V2-9-13 River graph roles and composite patterns

- **状態／Phase／優先度:** 完了／V2-9／P1。`RiverPlanV2`へnode role／reach class／modifier／child ownershipを拡張し、`WaterfallChainPlanV2` COMPOSITE_PRESET、`RiverGraphRolesPlanCompilerV2`／`WaterfallChainPlanCompilerV2`／`FoundationRiverGraphRolesSliceCompilerV2`、Schema／example／validator／preview／exportを閉じた。FeatureKind追加なし。既存meandering／delta／waterfall fixtures不変。次は`V2-9-14`。
- **目的／背景:** stream/headwater/tributary/confluence/distributary/rapids等を不要にFeatureKind化せず、一般river graphを拡張する。
- **Scope／成果物:** node/edge/reach-class/modifier/child semantic、sandbar/river-island child ownership、multiple waterfall nodes＋plunge poolsの`WATERFALL_CHAIN` preset compiler/validator/preview/export。
- **非Scope:** estuary/braided river vertical slices、専用waterfall-chain generator、existing graph ID変更。
- **依存／変更面:** `V2-9-04`、`V2-9-05`、V2-3 delta/waterfall/reconciliation。
- **個別要件:** split/merge flow conservation、elevation continuity、stable graph IDs、modifier order、node/edge/branch budget、cycle/orphan/conflict拒否。
- **Acceptance／停止:** graph rolesとwaterfall chainが同じgeneral IRで再現され、既存river/delta/waterfall fixtures不変なら完了する。新FeatureKindが必要なら独立ownershipを証明する別Taskへ分離する。
- **完了要約:** HEADWATER／STREAM／TRIBUTARY／CONFLUENCE／BIFURCATION／DISTRIBUTARY／RAPIDS／SANDBAR／RIVER_ISLAND／PLUNGE_POOLをgeneral `RiverPlanV2`へ載せ、`WATERFALL_CHAIN` presetが複数fall＋plunge poolをfreeze。flow／elevation／child／budget拒否とlegacy fixture不変を`FoundationRiverGraphRolesSliceCompilerV2Test`で確認。

### V2-9-14 Terrain foundation integration and Phase gate

- **状態／Phase／優先度:** 完了（2026-07-18）／V2-9／P0 gate。`FoundationPhaseGateV2Test`が19 foundation kindのdiagnostic binding維持（false-promotionなし）、18 dedicated moduleの`EXPERIMENTAL`、Release capability list不変、22 foundation plan exampleのstrict read安定性（正逆順／1・4 thread／locale／timezone）、plain-hill代表scenarioの再compile＋whole／tile merge一致、1000角ScaleProfile admission＋oversized拒否を固定し、full clean suiteでv1／Release 1／V2-2〜V2-6回帰を通過した。offline plan-levelのgenerate／validation／previewを`SUPPORTED`、intent_compile／standalone／exportを`PARTIAL`（diagnostic binding・sealed JSONのみ、Release capability未定義）、Paper以降を`UNSUPPORTED`（共通stream互換としてV2-6 evidence継承を記録）とした。証拠は [V2-9 Phase gate audit](audits/v2-9-phase-gate.md)。次はTrack E `V2-10-01`。
- **目的／背景:** `V2-9-01`〜`V2-9-13`を統合し、基礎Featureとcontractのoffline support能力だけを確定する。
- **Scope／成果物:** representative surface/coast/island/marine/connection/macro/river scenarios、capability matrix、strict Release、determinism/resource/security/compatibility audit。
- **非Scope:** V2-10 feature、LARGEの無条件support、Paper個別実装、未完能力の昇格。
- **依存／変更面:** 全V2-9 Task。`V2-6-19` Phase gateの完了は前提としない（回帰対象は着手時点でのV2-6実装状態）。audit、roadmap、taxonomy、README/limitations、support catalog。
- **個別要件:** full positive/corruption/tamper、whole/tile/thread/module/candidate/locale/timezone、1000角＋ScaleProfile admission、cancel/cleanup、v1/Release1/V2-2〜V2-6回帰を統合する。
- **Acceptance／停止:** 証拠が揃った能力だけ`SUPPORTED`へし、Paper能力はV2-6 evidenceを継承できる共通stream compatibilityとして記録する。欠損、cycle、budget、compatibility regressionがあれば新V2-9 Taskを追加し親Phaseを未完のまま停止する。

### V2-9 dependency table

| Task | Direct dependencies | Unblocks |
|---|---|---|
| V2-9-01 | V2-6-02, V2-8-01 | 02〜08, 12 |
| V2-9-02 | 01 | 05 |
| V2-9-03 | 01, V2-3 | 10, V2-10 glacial |
| V2-9-04 | 01, V2-3 | 05, 11, 13 |
| V2-9-05 | 02, 04, V2-4 | V2-10 river/wetland |
| V2-9-06 | 01, V2-2, V2-5 sea cave | 07, 08 |
| V2-9-07 | 01, 06, V2-4 volcanic | V2-10 island/reef |
| V2-9-08 | 01, 06, V2-3 | 09, V2-10 marine |
| V2-9-09 | 08, V2-3 canyon | gate |
| V2-9-10 | 03, V2-5 cave | 11, V2-10 karst |
| V2-9-11 | 04, 10, V2-5 lake/query | V2-10 karst/lava tube |
| V2-9-12 | V2-1, 01 | gate |
| V2-9-13 | 04, 05, V2-3 | V2-10 river |
| V2-9-14 | 01〜13 | V2-10 |

## 10. V2-10 Deferred terrain families（Track E、V2-9後）

V2-10はV2-9の対応する先行Task完了後に開始し、V2-6のPhase gate完了は前提としない。V2-9共通contractをそのまま適用する。Paper apply capabilityの`SUPPORTED`宣言はV2-9同様、V2-6 apply/verify/rollback/Undo/Recoveryと`V2-6-19`完了後に別Taskで接続する。

### V2-10-01 Glacial ice foundation

- **状態／Phase／優先度:** 完了（2026-07-18）／V2-10／P2。`VALLEY_GLACIER`／`ICE_CAP`／`ICE_SHEET`の共通`GlacialIcePlanV2`、cold climate＋snow binding、bounded sparse `ADD_SOLID`、meltwater handoff、`IceFjordPlanV2` COMPOSITE_PRESET、validator／preview／sealed exportを`EXPERIMENTAL`で実装した。dedicated moduleはcatalog未登録（diagnostic binding）。`SUPPORTED`昇格とdense ice voxel正本はしていない。次は`V2-10-02`（完了）。
- **目的／Scope:** `VALLEY_GLACIER`、`ICE_CAP`、`ICE_SHEET`のsurface/volume ownership、flow direction、thickness、bed contact、meltwater handoffを3 vertical sliceで実装する。
- **非Scope:** moraine/outwash/permafrost、ice physics、`ICE_FJORD`専用generator。
- **依存／変更面:** `V2-9-03/11/14`、V2-4 climate/snow、V2-5 volume。
- **要件／test:** cold-climate binding、support/contact、bounded sparse ice、flow/terminus、whole/tile/thread/budget、warm/unsupported/leaking corruption。
- **Acceptance／停止:** 3 sliceと`ICE_FJORD` preset compositionが共通contractで表現できれば完了する。dense ice voxel正本が必要なら停止する。

### V2-10-02 Glacial deposition and cold-ground profiles

- **状態／Phase／優先度:** 完了（2026-07-18）／V2-10／P2。`MORAINE_FIELD`／`OUTWASH_PLAIN`のEXPERIMENTAL deposition slice（`MoraineFieldPlanV2`／`OutwashPlainPlanV2`、glacial parent geometry bind、integer raster whole／tile export）と`PermafrostPlainProfileV2`（`PLAIN`＋cold climate、FeatureKindなし）を実装した。dedicated moduleはcatalog未登録（diagnostic binding）。`PERMAFROST_PLAIN` FeatureKind化と`SUPPORTED`昇格はしていない。次は`V2-10-03`。
- **目的／Scope:** `MORAINE_FIELD`、`OUTWASH_PLAIN`のstandalone/child ownershipを評価しvertical sliceを実装、`PERMAFROST_PLAIN`はplain＋environment profileとして実装する。
- **非Scope:** new climate solver、全glacial landform、Paper adapter。
- **依存／変更面:** `V2-10-01`、`V2-9-02/04`、V2-4 material/environment。
- **要件／test:** provenance、sediment owner、ridge/flow relation、profile-kind分離、budget/determinism/corruption、v1/parent回帰。
- **Acceptance／停止:** independent ownershipがない概念はprofile/childのままにし、無理なFeatureKind化をしなければ完了する。

### V2-10-03 Karst hydrology graph

- **状態／Phase／優先度:** 完了（2026-07-18）／V2-10／P2。
- **目的／Scope:** `SINKHOLE`／`KARST_SPRING` FeatureKind、`KarstHydrologyGraphPlanV2` typed graph freeze、`CenotePlanV2` COMPOSITE_PRESET、surface/volume/material handoff、validator/preview/exportを実装する。`KARST_CAVE_SYSTEM`はsealed `CAVE_NETWORK`上のgraph node role（FeatureKind化しない）。`CENOTE`もFeatureKind化しない。
- **非Scope:** doline/polje/limestone pavement/karst peak generators、full groundwater simulation、module catalog登録。
- **依存／変更面:** `V2-9-10/11/14`、V2-5 cave、limestone semantic material。
- **要件／test:** drainage reachability、loss/spring balance、collapse/roof/fluid ownership、graph/CSG/budget、orphan/cycle/leak corruption、host cave checksum不変。
- **Acceptance／停止:** graph中心のvertical sliceが成立しcomponentを誤ってFeatureKind化しなければ完了する。unbounded groundwater solveが必要なら停止する。

### V2-10-04 Additional marine landforms

- **状態／Phase／優先度:** 完了（2026-07-18）／V2-10／P2。
- **目的／Scope:** `ABYSSAL_PLAIN`と`SEAMOUNT`をbathymetry foundation上のvertical sliceとして実装し、`OCEAN_TRENCH`、`MID_OCEAN_RIDGE`、`SUBMARINE_VOLCANO`はownership/scale evidence後の候補としてcatalog評価する。
- **非Scope:** 5種一括generator、plate tectonic simulation、LARGE自動support。
- **依存／変更面:** `V2-9-08/09/14`。
- **要件／test:** basin containment、depth/relief/slope、transition、tile/global planning、budget/corruption、marine regression。
- **Acceptance／停止:** first two slicesを閉じ、advanced threeを実装なしで`SUPPORTED`にしなければ完了する。
- **証拠:** `FoundationAdditionalMarineSliceCompilerV2Test`（positive abyssal/seamount metrics、round-trip、missing/out-of-basin、FeatureKind classification、module EXPERIMENTAL、4-thread/locale checksum、host `ocean-basin-plan-v2.json` checksum不変）、`SchemaContractTest`、full `./gradlew test`／`build`。

### V2-10-05 Advanced river and lake landforms

- **状態／Phase／優先度:** 完了（2026-07-18）／V2-10／P2。contract／分割決定のみ。FeatureKind／generatorは導入していない。
- **目的／Scope:** 7候補をownership/graph依存で分類し、最初の実装対象を最大2種類へ固定、実装用Task IDを追加する。
- **非Scope:** 7 generator一括実装、pond/crater/glacial lake kind化、dam entity/block entity、本Task内のslice実装。
- **依存／変更面:** `V2-9-04/05/13/14`、V2-3 lake/delta。
- **分割決定:** first slices = `SPRING`（`V2-10-10`）＋`OXBOW_LAKE`（`V2-10-11`）。deferred = `RIVER_TERRACE`（child/reach profile）、`ALLUVIAL_FAN`（later sediment-fan）、`ESTUARY`（later mouth composition）、`BRAIDED_RIVER`（later multi-channel DAG）、`DAM_RESERVOIR`（later barrier+basin）。
- **成果物:** `AdvancedRiverLakeSplitContractV2`＋schema＋sealed example、taxonomy／gap-audit／roadmap同期。
- **要件／test:** graph node/edge/basin/barrier ownership分類、risk exclusion、resource/security/compatibility notes、strict contract round-trip、7候補のFeatureKind非導入、river/lake regression fixture load。
- **Acceptance／停止:** 高リスクが混在しない最大2 sliceへTask IDを追加し、本Taskはcontract/分割決定だけで完了する。実装を抱き合わせる必要があれば停止する（本Taskでは停止せず完了）。

### V2-10-06 Escarpment and dry-land foundation

- **状態／Phase／優先度:** 完了（2026-07-18）／V2-10／P2。
- **目的／Scope:** standalone ownershipを持つ`ESCARPMENT`を評価し、`PLATEAU`とのtransition vertical sliceを実装する。dune/badlands/salt-flat群はcommon dry-land modifier/field contractへ分類する。
- **非Scope:** 全dry-land generator、climate profileのFeatureKind化、existing canyon rewrite。
- **依存／変更面:** `V2-9-01/02/03/14`、V2-4 geology/climate/material。
- **成果物:** `EscarpmentPlanV2`／`PlateauPlanV2`、`DryLandModifierContractV2`、`FoundationEscarpmentPlateauSliceCompilerV2`、generators／schemas／examples／tests。
- **要件／test:** long scarp owner、cap/floor/talus、erosion/material handoff、tile seam/budget、degenerate/overlap corruption。
- **Acceptance／停止:** escarpment独立性が成立し、残りを無理にkind化しなければ完了する。独立validatorを定義できなければmodifierへ降格する。
- **完了証拠:** `FoundationEscarpmentPlateauSliceCompilerV2Test`（positive metrics、HARD OVERLAPS transition、short-scarp/missing-transition rejection、`DryLandModifierContractV2.decisionV21006()`、forbidden FeatureKind asserts、locale/thread checksum stability、plain-plan regression checksum）、`SchemaContractTest`、sealed examples `escarpment-plan-v2.json`／`plateau-plan-v2.json`／`dry-land-modifier-contract-v2.json`。

### V2-10-07 LAVA_TUBE volumetric vertical slice

- **状態／Phase／優先度:** 完了／V2-10／P2。
- **目的／Scope:** volcanic provenance、lava-flow/caldera relation、swept tunnel carve、roof/support/entrance、local materialを持つ`LAVA_TUBE`のplan/generator/validator/preview/exportを実装する。
- **非Scope:** lava simulation、standalone caldera/lava-flow昇格、Paper個別配置。
- **依存／変更面:** `V2-9-07/10/11/14`、V2-5 SDF/CSG。
- **要件／test:** host relation、tube continuity、roof/collapse/fluid conflict、AABB/budget、whole/tile/thread、orphan/corruption。
- **Acceptance／停止:** bounded sparse volumeとして共通placement stream互換なら完了する。dynamic lavaまたはdense voxelが必要なら停止する。
- **完了証拠:** `FoundationLavaTubeSliceCompilerV2Test`（positive metrics、round-trip、orphan/missing-provenance rejection、FeatureKind asserts、locale/thread stability、volcanic-cone checksum regression、cave-network/underground-river fixture load）、`SchemaContractTest`、sealed examples `lava-tube-plan-v2.json`／`lava-tube-positive.terrain-intent-v2.json`／`lava-tube-orphan.terrain-intent-v2.json`。

### V2-10-08 Advanced island and reef landforms

- **状態／Phase／優先度:** 完了／V2-10／P2。
- **目的／Scope:** `BARRIER_ISLAND`と`ATOLL`をgeneral island/coast/reef child plansのcompositionとして実装し、advanced reef/island候補をpreset/child/standaloneへ分類する。
- **非Scope:** floating reef専用generator、coral ecology再実装、全island taxonomy実装。
- **依存／変更面:** `V2-9-07/08/12/14`、existing CORAL_REEF/LAGOON/REEF_PASS。
- **要件／test:** shore-parallel ridge、lagoon/pass/marine connectivity、islet ownership、transition/budget、sealed/disconnected/collision corruption。
- **Acceptance／停止:** 2 composition slicesが既存child contractsを再利用しlegacy coral/volcanic fixtures不変なら完了する。
- **完了証拠:** `FoundationAdvancedIslandReefSliceCompilerV2Test`（positive barrier/atoll metrics、round-trip、FeatureKind asserts、missing lagoon/disconnected pass rejection、catalog contract seal、locale/thread stability、`single-island-plan-v2.json` checksum regression、diagnostic coral-reef load、volcanic-cone checksum regression）、`SchemaContractTest`、sealed examples `barrier-island-plan-v2.json`／`atoll-plan-v2.json`／`advanced-island-reef-catalog-contract-v2.json`、fixtures `barrier-island-composition.terrain-intent-v2.json`／`atoll-composition.terrain-intent-v2.json`。

### V2-10-09 Deferred terrain integration and Phase gate

- **状態／Phase／優先度:** 完了（2026-07-18）／V2-10／P2 gate。`DeferredTerrainPhaseGateV2Test`が14 V2-10 FeatureKindのdiagnostic binding維持（false-promotionなし）、12 dedicated moduleの`EXPERIMENTAL`・catalog未登録、17 profile/preset/role/候補名のFeatureKind不在、Release capability list不変、21 V2-10 sealed exampleのstrict read安定性（正逆順／1・4 thread／tr-TR locale／Chatham timezone）、plateau-escarpment代表scenarioの再compile＋whole／tile export一致、protected host checksum（plain／ocean-basin／volcanic-cone／single-island／river／river-graph-roles／split contract／open-spill lake）不変、1000角ScaleProfile admission＋SMALL越え拒否を固定し、full suite（`./gradlew test`／`build`）でv1／Release 1／V2-2〜V2-6／V2-9回帰を通過した。offline plan-levelのgenerate／validationを全completed sliceで`SUPPORTED`、previewはpreview index証拠があるsliceのみ`SUPPORTED`（`MORAINE_FIELD`／`OUTWASH_PLAIN`は`EXPERIMENTAL`のまま）、intent_compile／standalone／exportを`PARTIAL`（diagnostic binding・sealed JSONのみ、Release capability未定義）、Paper以降を`UNSUPPORTED`とし、preset／profile／graph role／deferred候補は昇格していない。証拠は [V2-10 Phase gate audit](audits/v2-10-phase-gate.md)。V2-10全11 Task完了によりTrack Eは終了。
- **目的／Scope:** V2-10 completed slicesだけを統合監査し、deferred候補を実装済みに見せず能力別supportを確定する。
- **非Scope:** 未選択候補の実装、gate waiver、Paper個別処理。
- **依存／変更面:** `V2-10-01`〜`V2-10-08`、`V2-10-10`、`V2-10-11`。
- **要件／test:** full positive/corruption/tamper/determinism/resource、V2-9/V2-6/v1 regression、support false-promotion検査。
- **Acceptance／停止:** evidenceがあるsliceだけを昇格し、profile/preset/component/候補をFeatureKind `SUPPORTED`にしなければ完了する。未解決riskは追加V2-10 Taskで停止する。

### V2-10-10 Surface SPRING graph-node vertical slice

- **状態／Phase／優先度:** 完了／V2-10／P2（`V2-10-05` split）。
- **目的／Scope:** surface `SPRING`のEXPERIMENTAL graph-node／source／outflow ownership vertical sliceを実装する。`HydrologyPlanV2.NodeKind.SPRING`／`RiverPlanV2` SOURCE bind、flow continuity、validator／preview／export。
- **非Scope:** `KARST_SPRING` rewrite、braided／dam／estuary、pond kind化。
- **依存／変更面:** `V2-10-05`、`V2-9-04/13/14`。
- **要件／test:** source ownership、outflow continuity、budget／corruption、karst spring／river sealed checksum不変、diagnostic binding。
- **Acceptance／停止:** surface spring ownershipが`KARST_SPRING`と分離して閉じれば完了する。karst graph改変が必要なら停止する。
- **完了証拠:** `FoundationSpringSliceCompilerV2Test`（positive metrics/export、round-trip、FeatureKind `SPRING` assert、orphan/missing-host/disconnect rejection、locale/thread stability、protected karst/river checksum regression、diagnostic catalog binding、module catalog-unregistered）、`SchemaContractTest`、sealed `spring-plan-v2.json`、fixtures `spring-positive.terrain-intent-v2.json`／`spring-orphan.terrain-intent-v2.json`。

### V2-10-11 OXBOW_LAKE reach-cutoff basin vertical slice

- **状態／Phase／優先度:** 完了／V2-10／P2（`V2-10-05` split）。
- **目的／Scope:** `OXBOW_LAKE`をreach-relation＋`LakePlan`互換basinとしてEXPERIMENTAL vertical slice実装する（cutoff geometry、stagnant level、wetland handoff、`RIVER`／`MEANDERING_RIVER` bind）。
- **非Scope:** `BRAIDED_RIVER`、`DAM_RESERVOIR`、`ESTUARY`、LAKE derivative kind化。
- **依存／変更面:** `V2-10-05`、V2-3-04 lake、`V2-9-04/13/14`。
- **要件／test:** cutoff basin ownership、level／rim、budget／corruption、open-spill lake fixture不変。
- **Acceptance／停止:** braid／barrierと独立したcutoff basin ownershipが閉じれば完了する。barrier ownershipが必要なら`DAM_RESERVOIR` Taskへ分離する。
- **完了証拠:** `FoundationOxbowLakeSliceCompilerV2Test`（positive metrics/export、round-trip、FeatureKind `OXBOW_LAKE` assert、orphan/missing-host/disconnect rejection、locale/thread stability、protected open-spill SHA-256／river-split checksum regression、open-spill `LAKE` `OPEN_SPILL` load、diagnostic catalog binding、module catalog-unregistered）、`SchemaContractTest`、sealed `oxbow-lake-plan-v2.json`、fixtures `oxbow-lake-positive.terrain-intent-v2.json`／`oxbow-lake-orphan.terrain-intent-v2.json`。

### V2-10 dependency table

| Task | Direct dependencies | Notes |
|---|---|---|
| V2-10-01 | V2-9-03/11/14, V2-4/5 | glacial ice |
| V2-10-02 | 01, V2-9-02/04 | deposition/profile |
| V2-10-03 | V2-9-10/11/14 | karst graph |
| V2-10-04 | V2-9-08/09/14 | marine follow-up |
| V2-10-05 | V2-9-04/05/13/14 | contract then split |
| V2-10-06 | V2-9-01/02/03/14 | escarpment/dry land |
| V2-10-07 | V2-9-07/10/11/14 | lava tube |
| V2-10-08 | V2-9-07/08/12/14 | island/reef |
| V2-10-09 | 01〜08, 10, 11 | Phase gate |
| V2-10-10 | 05, V2-9-04/13/14 | surface SPRING slice |
| V2-10-11 | 05, V2-3 lake, V2-9-04/13/14 | OXBOW_LAKE slice |

## 11. V2-11 Capability promotion（Track A後続、`V2-6-19`監査で追加）

`V2-6-19` RC auditは監査専任Taskであり能力を昇格しないため、gate完了後のPaper capability昇格・接続を独立Taskとして登録する。追加理由: 各所で「`V2-6-06`〜`V2-6-10`と`V2-6-19`完了後に別Taskで接続する」と予告されていた作業に具体的なTask IDがなく、gate完了後のTrack Aの次Taskが不定になるため。

2026-07-20の全体再監査（V2-6-19再検証。P0指摘は`V2-6-21`再完了で解消済み）を受け、`V2-11-02`〜`V2-11-06`を追加した。無効化済み`V2-6-16`／`V2-6-17`は復活させず、500／1000再実測は専用高memory実測ホストを前提に`V2-11-04`／`V2-11-05`の新IDで管理する。

### V2-11-01 Paper apply capability promotion and Track D／E connection

- **状態／Phase／優先度:** 完了（2026-07-20）／V2-11／P1。2026-07-20全体再監査で検出した「catalog／testは64×64昇格済み、正本（roadmap／本索引）は未着手」という矛盾を、既存昇格コードを正式成果として確定する方向で解消した（差し戻しは選ばない。`V2-6-14`／`V2-6-15`の実機smokeと [V2-6 Phase gate audit](audits/v2-6-phase-gate.md) が`surface-2_5d`×64×64の範囲でevidenceを満たすため）。昇格範囲は`surface-2_5d` capabilityのSANDY_BEACH／BREAKWATER_HARBOR／HARBOR_BASIN／ROCKY_CAPEの4 entry×5 Paper列のみで、`hydrology-plan`／`environment-fields`／`sparse-volume`は`EXPERIMENTAL`、Release capability未接続のV2-9／V2-10 foundation entryは`UNSUPPORTED`を維持した。dimension limitは64×64のまま、docs（README／commands／limitations／taxonomy／migration-plan／beta checklist／roadmap）とevidence linkを同期した。証拠は [docs/roadmap.md](../roadmap.md) の`V2-11-01`段落。
- **目的／前提:** smoke実測済み範囲（64×64、WorldEdit 7.3.19／FAWE 2.15.2）のRelease 2 placementについて、Feature Support Catalogの`paper_apply`／`post_apply_validation`／`snapshot`／`rollback`／`restart_recovery`列を証拠へbindして昇格し、Track D（V2-9）／Track E（V2-10）foundation featureの共通canonical stream互換を能力表へ反映する。前提は`V2-6-19`（完了）と`V2-6-06`〜`V2-6-10`（完了）。
- **Scope／成果物:** catalog data／sealed exampleの更新（dimension limit 64×64のまま）、evidence link（V2-6-14／15 smoke、V2-6 Phase gate audit）、false-promotion corpusの更新（昇格後も未測定寸法・未接続featureを拒否）、README／user docs／taxonomy／limitations同期。
- **非Scope:** 500／1000寸法の昇格、新featureのPaper adapter、Release format変更、Beta hardening項目の完了化。
- **必須test／D/M/S:** catalog consistency verifier、sealed example round-trip、昇格entryのevidence link照合、未測定寸法／未接続featureの昇格拒否回帰、v1回帰。
- **Gate／docs／次／停止:** 達成。`FeatureSupportCatalogConsistencyVerifierV2`（smoke-evidenced prefix／宣言runtime／smoke evidence link／Release capability path必須）、false-promotion corpus（`FeatureSupportCatalogV2Test`）、sealed example round-trip、`PlacementPhaseGateV2Test`のhard limit 64×64／500・1000拒否、full test／buildが通過した。寸法昇格は行わず次は`V2-11-02`（本Taskからは開始しない）。evidenceのない昇格が必要になれば停止する。

### V2-11-02 R2 placement dimension guard and measurement profile

- **状態／Phase／優先度:** 完了（2026-07-20）／V2-11／P0。2026-07-20全体再監査で追加（監査判定#5: catalog hard limit 64×64が設定で最大10,000まで上書き可能、R2経路へ一部disk設定未反映）。両指摘を解消した。
- **目的／前提:** 通常運用のR2 placementをcatalog hard limit（64×64）で設定によらずfail-fastさせ、上限超の寸法は再実測専用profileだけで明示的に許可する。前提は`V2-6-21`（P0再完了）。`V2-11-01`と並行実行できるが同一ファイルの同時編集は不可。
- **Scope／成果物:** production設定でのdimension上限クランプ（catalog値を超える設定値の起動時拒否またはworld mutation前のstable domain error）、measurement profile（明示config flag＋operator限定＋隔離world前提、`V2-11-04`／`V2-11-05`専用）、R2経路へのdisk関連設定の完全反映、restart時reservation rebuildがeffect envelope基準であることの回帰固定。
- **非Scope:** 500／1000実測そのもの、capability昇格、新artifact format、v1 placement変更、寸法・予算の新しいhard-code追加（scale契約ADR 0016を正本とする）。
- **主変更:** `core.v2.placement`のadmission／config検証、Paper config／permissions、tests、admin／operations／limitations docs。
- **必須test／D/M/S:** 64×64境界許可と65×65以上の通常profile拒否、超過config値の拒否、measurement profile gating（flagなし・非operator・deny worldで拒否）、disk設定反映、effect envelope基準rebuild回帰、v1回帰。既定挙動でsecurity boundaryを弱めない。
- **Gate／docs／次／停止:** 達成。`Release2MeasuredDimensionGateV2.production(...)`がcatalog `SMOKE_MEASURED_MAXIMUM`超の設定値を起動時に拒否し、`Release2PlacementDimensionPolicyV2`が唯一のadmission pointとしてworld mutation・journal・reservationのいずれよりも前にlayoutを拒否する。`Release2MeasurementProfileV2`は明示flag＋隔離world＋CONSOLE／RCON operatorの3条件が揃った時だけ有効で既定無効。`disk.minimum-free-bytes`／`disk.safety-margin-bytes`を`Release2DiskBudgetV2`でR2 reservation floorへ反映し、`FilePlacementSafetyStoreV2.rebuild`をsealed effect envelope基準へ修正した。新規寸法hard-codeは追加していない（catalogの`SMOKE_MEASURED_MAXIMUM`／`CATALOG_BUDGET_MAXIMUM`が正本）。次は`V2-11-03`（本Taskからは開始しない）。
- **既知の未解決（本Task外）:** full suiteで`PlacementPhaseGateV2Test.release2LifecycleAppliesVerifiesAndUndoesDeterministically`が約4回に1回`RejectedExecutionException: I/O concurrency limit reached`で落ちる。原因は`GenerationExecutors.TrackedTask`がfutureを完了させた**後**にI/O permitを解放するため、`io=1`構成では`.join()`直後の次submitが`tryAcquire`に失敗する既存のrace（本Taskで追加したsubmitはない）。共有core executorの変更は本TaskのScope外のため修正せず、新Task化を提案する。

### V2-11-03 Docs／Schema／example consistency sync

- **状態／Phase／優先度:** 完了（2026-07-20）／V2-11／P2。2026-07-20全体再監査で追加。
- **目的／前提:** 監査で検出した文書・Schema整備不足を実装と一致した表現へ同期する。前提なし（`V2-6-19`完了済み）。
- **Scope／成果物:** Quickstartの`sandy-coast-001`生成→`rocky-coast-001`配置の誤記修正、console confirmation token説明の追記、roadmapの古いanchor参照3件の修正、Schema 2件へのtitle・139件へのdescription追加（validation挙動不変）、feature-support catalog Schemaの共通Schema検証への追加、Schema／exampleのinventory-driven検証（disk列挙とtest対象の一致検査）、任意でdocs anchor checker。
- **非Scope:** Schema semantic変更、契約version変更、新機能docs、roadmap進捗状態の変更、64×64以外を`SUPPORTED`と表示する記述の追加。
- **主変更:** `docs/`（Quickstart／user／admin／roadmap anchor）、`schemas/`のtitle／description、`SchemaContractTest`等の共通検証、（任意）`scripts/` checker。
- **必須test／D/M/S:** 全Schemaのtitle／description presence検査、inventory完全性検査、既存example round-trip・checksum不変、v1 golden不変。
- **Gate／docs／次／停止:** 達成。全141 schemaが非空の`title`／`description`を持ち（`SchemaContractTest.everySchemaDeclaresATitleAndDescription`）、schema inventory（disk＝`StructuredDataValidator`のbundle＝packaged resource）とexample inventory（`examples/`配下178 documentがsource／docsから参照される）が固定された。共通検証から唯一漏れていた`feature-support-catalog-v2.schema.json`はbundleへ加え、drift（5 Paper能力列の`const: UNSUPPORTED`。`V2-11-01`昇格後のsealed exampleと125件矛盾）を`supportLevel` enumへ同期して`FeatureSupportCatalogCodecV2.read`から共通strict検証を通した。昇格evidenceの強制は従来どおり`FeatureSupportCatalogConsistencyVerifierV2`が正本である。Quickstart誤記とCONSOLE／RCON confirmation token運用、`roadmap.md#terrain-generation-v2`参照3件を修正し、`DocsLinkConsistencyTest`をdocs anchor checkerとして追加した。契約version、canonical checksum、昇格範囲（64×64／`surface-2_5d`）は不変。次は`V2-11-04`（完了済み。本Taskからは開始しない）。

### V2-11-04 500×500 real-world measurement on dedicated host

- **状態:** 完了（2026-07-20）。無効化済み`V2-6-16`の後継。専用ホストでFAWE 2.15.2／`run-fawe`単独JVM＋RCON＋PID telemetryにより500×500 solid surface Release 2をPass1／Pass2とも`APPLIED`→full effect-envelope verify→`UNDONE`まで完走し、plan→SIGKILL→restart recoveryとv1 help／doctor smokeを記録した。peak RSS ≈ 6.08 GiB（Xmx 6G）。catalog寸法昇格は行っていない（`V2-11-06`）。証拠: [audit](audits/v2-11-04-fawe-500-measurement.md)／[evidence](audits/v2-11-04-fawe-500-measurement-evidence.md)／[runbook](../smoke/v2-11-04-fawe-500-measurement-runbook.md)。runner: `scripts/measure/v2-11-04-fawe-run.sh`。
- **目的／前提:** 500×500 Release 2 placementの全lifecycleを実機で完走・計測し、寸法昇格（`V2-11-06`）の判断証拠を作る。前提は`V2-11-01`（昇格状態確定）と`V2-11-02`（measurement profile）。
- **Scope／成果物:** PaperをGradleから完全分離した単独JVM起動（`run-fawe/`＋paperclip、Gradle daemon経由のconsole中継なし）、RCON操作、bounded log、server PID単独のheap／RSS／GC／JFR計測、**FAWE 2.15.2単独profileのみ**（WorldEdit plugin併用禁止）でgenerate→export→strict R2 verify→plan→confirm→snapshot-all→containment→apply→settle→full verify→Undo（baseline復元）まで、反復実行のchecksum決定性、failure／recovery drill、evidence文書。WE 7.3.19再実測は本Taskの必須Scope外（必要なら別ID）。
- **非Scope:** 1000×1000、catalog昇格（`V2-11-06`）、WorldEdit単独profileの必須完走、coreの推測最適化（budget超過なら停止して別Taskを提案）、offline測定による代用、過去のGradle daemon混在計測の流用。
- **主変更:** measurement scripts／fixtures（`scripts/measure/`、`run-fawe/`）、audit evidence docs、release checklist／roadmap。production変更は実測で見つかったdefectの小規模修正だけ。
- **必須test／D/M/S:** measurement profile経由のみで実行、main-thread assertion、journal restart安全、measured peak／RSS／GC／disk／時間の記録、secret／server artifact非commit、v1 smoke回帰。
- **Gate／docs／次／停止:** FAWE単独profileで`APPLIED`→full verify→`UNDONE`完走とserver PID単独telemetryが揃ったため完了。次は`V2-11-06`（`V2-11-05`完了済み）。500をcatalog昇格候補へは上げない（`V2-11-06`）。

### V2-11-05 1000×1000 real-world measurement

- **状態:** 完了（2026-07-20）。無効化済み`V2-6-17`の後継。FAWE 2.15.2／`run-fawe`単独JVM＋RCON＋PID telemetryで1000×1000 solid surface Release 2をPass1／Pass2とも`APPLIED`→full effect-envelope verify→`UNDONE`まで完走（wall ≈ 6342／6436 s、peak RSS ≈ 6.02 GiB、Xmx 8G／Xms 2G）。≤256-chunk strip forceload、journal `SNAPSHOT_COMPLETE`待ち、`Map.copyOf` MapN欠陥の`unmodifiableMap`修正、disk admission超過なし、plan→SIGKILL recovery、v1 help／doctor。catalog寸法昇格は行っていない（`V2-11-06`）。証拠: [audit](audits/v2-11-05-fawe-1000-measurement.md)／[evidence](audits/v2-11-05-fawe-1000-measurement-evidence.md)／[runbook](../smoke/v2-11-05-fawe-1000-measurement-runbook.md)。runner: `scripts/measure/v2-11-05-fawe-run.sh`。
- **目的／前提:** 1000×1000 Release 2 placementを`V2-11-04`と同一手法（FAWE 2.15.2／`run-fawe`単独）で実測する。前提は`V2-11-04`がFAWE profileで安定して全lifecycleを完走していること。
- **Scope／成果物:** `V2-11-04`と同じ分離Paper JVM／RCON／PID単独telemetry手法での1000角全lifecycle、加えてscheduler slice／queueの長時間挙動とdisk予約上限の実測、evidence文書。
- **非Scope:** 500結果の外挿、LARGE（V2-8）、catalog昇格、測定なしのsupport宣言。
- **主変更:** measurement scripts、audit evidence docs、release checklist／roadmap。MEDIUM envelopeの`Map.copyOf`欠陥修正。
- **必須test／D/M/S:** `V2-11-04`と同じ。加えて長時間実行でのbounded queue／memory admissionの実証。
- **Gate／docs／次／停止:** FAWE単独profile完走とtelemetryが揃ったため完了。次は`V2-11-06`。1000をcatalog昇格候補へは上げない（`V2-11-06`）。

### V2-11-06 Measured dimension catalog promotion

- **状態／Phase／優先度:** 完了（2026-07-20）／V2-11／P1。published `PlacementDimensionLimitV2`を実測範囲の1000×1000へ昇格し、runtime別production ceiling（FAWE 2.15.2は1000、WorldEdit 7.3.19は64）、sealed example、schema、docsを同期した。証拠は [docs/roadmap.md](../roadmap.md) の`V2-11-06`段落、[V2-11-04 audit](audits/v2-11-04-fawe-500-measurement.md)、[V2-11-05 audit](audits/v2-11-05-fawe-1000-measurement.md)。
- **目的／前提:** `V2-11-04`／`V2-11-05`の実測evidenceに限定してcatalog placement dimension limitを更新する。前提は`V2-11-04`と`V2-11-05`（いずれも2026-07-20完了）。
- **Scope／成果物:** catalog data／sealed exampleの更新、evidence link（各実測audit）、false-promotion corpus更新（未実測寸法拒否の維持）、README／user／limitations docs同期。
- **非Scope:** 実測なし寸法の昇格、LARGE、Release format変更、新feature追加。
- **主変更:** built-in catalog data／sealed example、capability matrix docs、README／user／limitations／roadmap。
- **必須test／D/M/S:** catalog consistency verifier、sealed example round-trip、昇格entryのevidence link照合、未実測寸法のfalse-promotion拒否回帰、v1回帰。
- **Gate／docs／次／停止:** 達成。昇格は実測evidenceの範囲（FAWE 2.15.2の1000×1000まで）に限定し、feature集合・contract version・Release formatは不変とした。`FeatureSupportCatalogConsistencyVerifierV2`（published limit＝実測最大、500／1000のadmit、1001以上の拒否、昇格entryのmeasurement evidence link、runtime別ceilingとevidence分割の一致）、false-promotion corpus、sealed example round-trip、`PlacementPhaseGateV2Test`、full test／buildが通過した。1000超とWorldEdit単独runtimeでの64超は未実測のまま拒否する。次は`V2-12-01`（本Taskからは開始しない）。

## 12. V2-12 V2 production path and v1 full migration（Track A後続、2026-07-20全体再監査で追加）

追加理由: 2026-07-20の全体再監査で、通常のCLI／Paper経路が依然v1であり、v2のrequest→design→generate→preview/export→Release 2 placementが未接続、production Release 2 export経路とv1→v2 migration toolが未実装であることを確認した。本PhaseはV2を正式なユーザー経路にし、その後にv1の不要機能削除と名称整理（完全移行）を段階的に行う。解除の範囲・順序・deprecation windowの正本は [ADR 0035](../adr/0035-v1-retirement-governance.md)（2026-07-20に `Accepted`）であり、削除してよいv1資産はADR 0035 D2aのR1〜R8に限る。ADR承認によりD1の第1条件は成立したが、第2条件（対象行のD2a前提Task完了）が未成立のため、`V2-12-02`〜`V2-12-04`は2026-07-20に完了した。実際にv1契約へ触れてよいのは`V2-12-05`（deprecation）と`V2-12-06`（削除・改名）だけであり、両者は独立した人間承認を必須とする。`V2-12-05`以降はユーザー可視の破壊的変更を含むため人間承認を必須とする。2026-07-21、`V2-12-05`のカバレッジ監査がv2未カバーのv1機能を3件（request authoring、job／candidate／export lifecycle、未接続の運用verb）検出して停止し、ADR 0035 rev.4 D11がこれをv2実装で解消する方針を承認したため、`V2-12-08`〜`V2-12-10`を追加してPhaseを10 Taskとした。実行順は`V2-12-08`→`V2-12-09`→`V2-12-10`→`V2-12-05`（再開）→`V2-12-06`→`V2-12-07`である。

### V2-12-01 v1 retirement governance ADR and compatibility policy update

- **状態／Phase／優先度:** **完了**（2026-07-20）／V2-12／P1。[ADR 0035](../adr/0035-v1-retirement-governance.md) はrev.3で `Accepted`（承認者 nankotsu029）となり、正本間の矛盾検査と人間承認の双方を満たした。ADRはD1（解除条件）、D2（削除可能なv1資産R1〜R8とv2カバレッジ／前提Task）、D3（維持対象K1〜K5。custom asset catalogはv2等価物が無いため削除対象から明示除外し、Gateの停止条件を成立させない）、D4（名称変更方針と、明示承認した識別子変更2件）、D5（`/lfc r2`→`/lfc v2 <verb>`→`/lfc <verb>`既定→v1経路削除の4段階とdeprecation windowの4条件）、D6（WorldEdit単独runtime 64×64のカバレッジ劣化を寸法限定で明示承認）、D7（削除順序と例外条件）、D8（v1 job／journal／Undo／recovery／cleanup stateのdrain gate、fail closed）、D9（migration toolの品質条件10項目）を決定した。rev.2でD2をD2a（writer削除）／D2b（legacy compatibility reader維持）／D2c（削除前に解消が必要な構造問題）へ分割し、deprecation window条件の循環解消と削除順序修正（R6→R4→R5）を行った。AGENTS.md §6へ「v1凍結の解除条件」、roadmap「継続する製品境界」へv1退役方針、task-execution-guide §2へADR参照を同期した。コード・Schema・example・catalogの変更はゼロである。
- **目的／前提:** v1凍結（AGENTS.md §6）を段階的に解除するgovernance ADRを作り、削除・改名の範囲と順序を正本化する。前提は`V2-11-01`。
- **Scope／成果物:** ADR（削除対象v1機能の一覧と根拠、維持するv1資産（参照用golden等）の扱い、名称変更方針 — Javaクラス等の`V2` suffix改名は自由、contract ID／Schema `$id`／checksumへ影響する識別子はADRが明示承認した場合のみ変更、`/lfc r2`→正式command名、deprecation window）、AGENTS.md §6／roadmap「継続する製品境界」／task-execution-guideの改訂。コード変更なし。
- **非Scope:** 実コードの削除・改名、catalog変更、Schema変更。
- **主変更:** `docs/adr/`、AGENTS.md、roadmap、task-execution-guide。
- **必須test／D/M/S:** なし（正本間の矛盾検査のみ）。
- **Gate／docs／次／停止:** 達成。[ADR 0035](../adr/0035-v1-retirement-governance.md) rev.3と改訂正本（AGENTS.md §6、roadmap「継続する製品境界」、task-execution-guide §2、本Index §1／`V2-12-03`〜`V2-12-07`）が矛盾なく揃い、2026-07-20に人間承認（nankotsu029）を得た。これによりD1の**第1条件**が成立し、`V2-12-05`以降のv1変更Taskは「D2a前提Taskを満たした範囲で解禁され得る」状態になった。ただし第2条件（対象行のD2a前提Taskと追加条件の完了）は未成立であり、`V2-12-02`〜`V2-12-04`が完了するまで実際のv1削除・改名はできない。`V2-12-05`／`V2-12-06`はそれぞれ独立した人間承認を要する。停止条件に該当し得たv2未カバー機能はcustom asset catalog（K3）のみで、削除対象から明示除外して回避した。次は`V2-12-02`（本Taskからは開始しない）。

### V2-12-02 Production Release 2 export path

- **状態／Phase／優先度:** **完了**（2026-07-20）／V2-12／P1。2026-07-20全体再監査の残課題「P1 — V2通常利用経路」の前半。`core.v2.export`に production export経路 `Release2ExportApplicationServiceV2` を追加した。sealed request＋design stage intentを入力に、coastal generator／compositorからrelease-local descriptor fieldをrow-majorで一度だけsampleし、constraint field sidecar、rebindしたintent、frozen Blueprint、field-only coastal validation、固定11 preview、`TilePlanV2`幾何のSponge v3 tileを生成する。生成物は`ReleaseSurfacePublisherV2`のstaging→strict read-back→atomic publishへ渡し、publish後に`ReleaseSurfaceVerifierV2`（directory／ZIP manifest一致）と`ReleasePlacementEligibilityVerifierV2`を通過した場合だけ`Release2ExportResultV2`をsealする。tile幾何が`TilePlanV2`正本のため、publish済みReleaseは`V2-6-20`の`VerifiedReleaseCanonicalBlockSourceV2`でそのまま開けてplacement planをcompileでき、`requirePlanMatches`が通る。coastal featureが所有しないcellのbaselineは推測せず、`SurfaceBaselineV2`として呼び出し側が明示する。`ExportBudgetV2`がtile数とdense descriptor working setをartifact生成前にadmitする。CLI／Paper command routingとworld mutationは追加していない。
- **目的／前提:** v2のrequest→design→generate→preview/exportをstrict Release 2 publisherへ接続し、production export経路（core application service／API）を成立させる。前提は`V2-11-01`。v1不変のため`V2-12-01`とは独立に実装できる。
- **Scope／成果物:** `TerrainDesignApplicationServiceV2`〜generation〜preview/export〜Release 2 publisherの正式接続、staging→strict read-back→atomic publish、export成果物を`V2-6-20` verified canonical block sourceで開きplacement planを作れるまでのoffline E2E、CLI／Paperから呼べるapplication API。
- **非Scope:** Paper／CLI command routing（`V2-12-03`）、v1変更、新capability／artifact format、world mutation。
- **主変更:** `core.v2`のapplication orchestration、`format.v2.release` publisher接続（共有領域のため直列実行）、tests、architecture／artifact-format docs。
- **必須test／D/M/S:** request fixture→R2 strict verify→placement eligibilityのoffline E2E、同Blueprint同checksumのdeterminism、cancel／cleanup、budget admission、v1 golden不変。
- **Gate／docs／次／停止:** 達成。`Release2ExportApplicationServiceV2Test`が(1) Azure Coast request fixture→export→directory／ZIP strict verify→placement eligibility→`VerifiedReleaseCanonicalBlockSourceV2`＋`PlacementPlanCompilerV2`のplacement plan、(2) design（FIXTURE path）出力をそのままexportへ接続、(3) 同一入力での同一Blueprint／manifest／tile checksum、(4) 生成途中cancelでpublish済みReleaseもstaging残骸もゼロ、(5) tile数budgetとdense working set budgetのartifact生成前拒否、を検証した。full `./gradlew test`／`./gradlew build`が通り、v1 golden・Release 1・既存Release 2 capabilityは不変である。新artifact format、新capability、推測fallbackは導入していない。次は`V2-12-03`（本Taskからは開始しない）。

### V2-12-03 CLI／Paper v2 command routing and E2E

- **状態／Phase／優先度:** **完了**（2026-07-20）／V2-12／P1。2026-07-20全体再監査の残課題「P1 — V2通常利用経路」の後半（監査判定#13: CLI・補完・routing test不足）。実装・自動testに加え、Gateが要求する実機smoke（64×64）を2026-07-20に実行し合格した（[evidence](audits/v2-12-03-v2-command-path-evidence.md)）。CLI／Paper共有の`core.v2.command`（`V2CommandRouterV2`／`V2CommandVerbV2`／`V2CommandRouteV2`／`V2CommandErrorCodeV2`／`V2WorkflowServiceV2`）を追加し、`/lfc v2 <verb>`と`lfc v2 <verb>`を正式経路として実装した。`/lfc r2 <op>`はrouter内でcanonical形へ翻訳される同一semanticのdeprecated aliasで、実行時に警告を出す（`handleRelease2`は1実装のまま）。offline verb（request／design／generate／export／preview）は`V2-6-11`のdesign serviceと`V2-12-02`のexport serviceへ接続し、Paperでは`PaperV2WorkflowServiceV2`がplugin所有workspace内へpathをsandbox化してbounded executorで実行する。world系verb（place／status／undo／recover）はPaper専用で、CLIからは安定code`V2_PAPER_ONLY`で拒否する。permissionはADR 0035 D4の承認どおり`landformcraft.v2.*`へ改名し、旧`landformcraft.r2.*`をdeprecation window中の同権限として受理する。tab completionはpermission・surface・world policyを尊重する。全失敗経路に安定error codeと`v2CorrelationId`が付く。v1 commandは未変更である。64×64 fixture`harbor-cove-64`をexampleへ追加した。
- **目的／前提:** CLIとPaperへ正式なv2 command経路（request／design／generate／preview／export／place／undo／status）を追加し、評価用`/lfc r2`を正式経路へ統合する。前提は`V2-12-02`と`V2-6-21`。
- **Scope／成果物:** CLI subcommand、Paper command＋help／tab completion／permission整合、routing tests、fake gateway E2E（request→export→64×64 placement→Undo）、実機smoke evidence（64×64）。正式名は[ADR 0035](../adr/0035-v1-retirement-governance.md) D5に従い`/lfc v2 <verb>`（`/lfc r2`はdeprecated alias）、CLIは`lfc v2 <verb>`。ADR 0035 D10で維持対象とされたverb（`asset`／`doctor`／`job`／`recovery`／`version`／`help`）は本Taskでは変更せず、v2 backendへの接続は`V2-12-05`が行う。
- **非Scope:** v1 commandの削除・意味変更（`V2-12-06`まで凍結）、D10維持verbのbackend切替（`V2-12-05`）、500／1000、Web UI。
- **主変更:** CLI／Paper command adapter、permissions／config、tests、commands／user／admin docs。
- **必須test／D/M/S:** command routing／permission／completion回帰、operator／actor／world policyの継承（`V2-6-21`のsecurity gate回帰）、v1 command不変回帰、main-thread assertion、correlation ID／stable error code。
- **Gate／docs／次／停止:** 達成。自動test: `V2CommandRouterV2Test`（canonical routing、alias 1対1翻訳、permission node改名、surface別拒否、arity／unknown tokenの安定code、correlation ID、completion）、`LandformCraftCliV2Test`（request／export／generate／preview実行、Paper専用verb拒否、alias警告、v1 dispatch不変）、`V2CommandPathE2EV2Test`（fake gateway E2E: request→export→64×64 place plan/confirm/execute→status→undo plan/execute→`UNDONE`、および65×64の寸法gate拒否）。実機smokeは`scripts/smoke/v2-12-03-run.sh`でPaper 1.21.11＋WorldEdit 7.3.19単独・64×64を実行し、production `lfc v2 export`のReleaseを`/lfc v2 place plan→confirm→execute`で`APPLIED`（settle＋effect envelope全体のexact verify成功）、`/lfc v2 status`と`/lfc r2 status`（deprecation警告付き）、`/lfc v2 undo plan→execute`で`UNDONE`まで通した（[evidence](audits/v2-12-03-v2-command-path-evidence.md)）。full `./gradlew test`／`./gradlew build`が通り、v1 golden・Release 1・既存Release 2 capabilityは不変である。同auditの§4に本実行で判明したrunnerの欠陥3件と修正を記録した（v1 command不変のlive証拠は取得できておらず、自動testで担保している）。catalogの能力・寸法昇格は行っていない。次は`V2-12-04`（本Taskからは開始しない）。

### V2-12-04 v1→v2 migration tool

- **状態／Phase／優先度:** **完了**（2026-07-20）／V2-12／P1。2026-07-20全体再監査（監査判定#12: migration toolなし）の解消。新package `core.v2.migration`（`LegacyMigrationApplicationServiceV2`／`LegacyV1ArtifactReaderV2`／`LegacyV1IntentMapperV2`）、`format.v2.migration`（report codec、bundle verifier）、`model.v2.migration`（report contract）を追加した。operatorが明示した1資産（v1 `terrain-intent.json`／v1 design package／Release format 1 directory・ZIP）をstrict readし、Release 2 design packageと`migration-report-v2.json`からなるmigration bundleを publish する。CLIは`lfc v2 migrate inspect`（dry-run）と`lfc v2 migrate apply <kind> <source> <output-root> <migration-id> <strict|accept-lossy>`。routing契約へ`Surface.CLI`と安定code`V2_CLI_ONLY`を追加し、`migrate`はPaperのroutingにもcompletionにも現れない。**v2 intentへ写るのは`schemaVersion`と`theme`だけである。** `TerrainIntentV2.Feature`はnormalized geometryを必須とし、v1のzoneは`preferredArea`と`areaShare`しか持たないため、topology／seaSides／landRatio／relief／coastline／water／zone／structureをv2 feature・constraintへ変換することは位置・形状の発明にあたる。これらはreportへ1件ずつ理由付きで列挙し、intentには入れない。Release 1のtile schematic／structures／assets／previewも、v2 Blueprintのmodule・stage・field descriptorを欠くため非対応elementとして報告し、`lfc v2 export`での再生成を指示する。実質すべてのv1資産がlossyであるため、`apply`は`strict`（非対応が1件でもあれば拒否）か`accept-lossy`の明示を必須とする。移行intentは`provenance.source = UPGRADED_V1`／`UNCONFIRMED`、design packageは`pathKind = IMPORT`。v1 Schema・generator・Release format 1・v1 placement／Undo・v1 commandは不変で、catalog昇格も行っていない。
- **目的／前提:** 既存v1資産（intent／design package／Release 1）を明示操作でv2 artifactへ変換するmigration toolを実装する。前提は`V2-12-02`。
- **Scope／成果物:** v1 strict read→v2 contract mapping（v1から欠落した位置・形状・関係・地質をhard constraintへ推測補完しない — 欠落はdraft／未指定のまま）、新artifact生成（in-place書換なし）、変換reportとstrict verify、CLI subcommand、user docs。
- **非Scope:** 自動一括migration、v1資産の削除・変更、lossy変換の暗黙容認、未知version受理。
- **主変更:** `core.v2`のmigration service、CLI subcommand、tests／fixtures、user／artifact-format docs。
- **必須test／D/M/S:** 代表v1 fixture変換のstrict verify／round-trip、非対応要素の明示reject／report、determinism（同入力同checksum）、v1資産の非破壊、path／ZIP／未知version拒否。加えて[ADR 0035](../adr/0035-v1-retirement-governance.md) D9のmigration品質条件10項目（非破壊、dry-run、非overwrite、determinism、report、version＋checksum記録（canonical checksum非定義資産はbyte digest）、部分publish禁止、unknown version／未定義fieldをreport のうえ拒否、corrupted fail closed、結果のRelease 2 strict verify）と、D2b Acceptanceの**R4／R7削除後を模した構成でのpackaged-JAR migration test**を満たす。
- **Gate／docs／次／停止:** 達成。v1代表資産3種（`examples/mountain-stream/terrain-intent.json`、`examples/azure-coast`のv1 design package、testが生成する実Release format 1）がv2 design package＋reportへ再現的に変換でき、非対応要素はすべてreportへ理由付きで列挙される。D9の10条件は実装で満たした（非破壊、dry-run、非overwrite、determinism＝job UUIDをsource digestから導出しtimestampをv1 audit由来かepochに固定、report、元version＋checksum記録＝canonical checksum不定義sourceはbyte digest／Release 1は`checksums.sha256` digest、staging→strict read-back→atomic move→published read-backで部分publishなし、unknown version・未定義fieldのstrict拒否、corrupted fail closed、結果の`DesignArtifactVerifierV2`＋bundle checksums検証）。D2b Acceptance 6は`LegacyMigrationPackagedJarV2Test`が、active v1 schemaとv1 orchestration classを隠すClassLoader下でのpackaged-JAR migrationとして満たす。先行整備としてv1 contract schema 8件のimmutable copyを`src/main/resources/legacy/v1/contracts/`へ置き、`StructuredDataValidator`を`/schemas/`→`/legacy/v1/contracts/`のfallback解決へ変更した（active copyの削除は`V2-12-06`）。test: `LegacyMigrationApplicationServiceV2Test`（11件）、`LegacyMigrationPackagedJarV2Test`（2件）、`V2CommandRouterV2Test`／`LandformCraftCliV2Test`の追加回帰、`SchemaContractTest`のsealed example読取。full `./gradlew test`／`./gradlew build`が通り、v1 golden・Release 1・既存Release 2 capabilityは不変である。hard constraint推測は不要だった（推測が必要な要素はすべてreport行にしたため停止条件に該当しない）。**未解決事項**: `docs/design-v2/migration-plan.md` §6「自動変換できるもの」は推測値を`SOFT`＋provenanceとして書き出す方針で、本Taskの保守方針と差異がある。現行`TerrainIntentV2`はgeometryにstrengthを持たず§6は契約変更なしには実装できないため、本Task Index Scope／Gate／AGENTS.md §6に従った。方針決定が必要である（同§へ記録済み）。次は`V2-12-05`（本Taskからは開始しない）。

### V2-12-05 v2 default switchover and v1 deprecation

- **状態／Phase／優先度:** **完了（2026-07-21、人間承認 nankotsu029）**／V2-12／P1。`V2-12-08`〜`V2-12-10`完了でADR 0035 rev.4 D11の再開条件が成立し、[再監査](audits/v2-12-05-v1-to-v2-coverage-audit-rerun.md)（最初からやり直し）で劣化なしをPASS後、既定経路をv2へ切替えた。初回STOP時の[監査](audits/v2-12-05-v1-to-v2-coverage-audit.md)も参照。
- **目的／前提:** CLI／Paperの既定経路をv2へ切替え、v1を明示flag付きdeprecatedへ移す。前提は`V2-12-01`〜`V2-12-04`、**`V2-12-08`〜`V2-12-10`**（ADR 0035 rev.4 D11。2026-07-21追加）、および`V2-11-06`（v1同等の寸法カバレッジ確定。2026-07-20完了。ただし寸法evidenceはFAWE 2.15.2単独であり、WorldEdit単独runtimeの64×64制限はADRで明示承認する）。
- **Scope／成果物:** default routing切替（Paperは`/lfc <verb>`既定＋`/lfc v1 <verb>` opt-in、CLIは位置非依存global option `lfc --v1 <verb>`）、v1のdeprecation表示、v1→v2機能カバレッジ監査（v1で可能だった操作・寸法がv2で同等以上であるevidence。[ADR 0035](../adr/0035-v1-retirement-governance.md) D2a-R5のprovider設定key／failure mapping／retry／quota／audit同等性検査を含む）、**ADR 0035 D10 command inventory**（削除するverbと維持するverbの両方を含む移行先表）、D10維持verbのv2 backend接続、user／admin／README／Quickstart全面更新、CHANGELOG。tagged releaseが存在しない場合はD5条件1の代替として「v1経路にしか存在しない利用可能機能はゼロ」を証拠付きで確認する。
- **非Scope:** v1コード削除（`V2-12-06`）、catalog昇格、新機能。
- **主変更:** CLI／Paperのdefault routing、config、tests、全ユーザー向けdocs。
- **必須test／D/M/S:** 既定操作の全E2Eがv2経路で完走、v1 opt-in経路の回帰維持、deprecation表示、v1 golden不変。
- **Gate／docs／次／停止:** 達成（2026-07-21）。既定操作が全てv2で完走し（Paper `/lfc <verb>`＝`/lfc v2 <verb>`、CLI `lfc <verb>`＝`lfc v2 <verb>`）、v1が明示opt-in（Paper `/lfc v1 <verb>`、CLI 位置非依存global option `lfc --v1 <verb>`）へ移り、[再監査](audits/v2-12-05-v1-to-v2-coverage-audit-rerun.md)で劣化なし（F1〜F3解消、F4はD6承認範囲内）、D10 command inventory（再監査§7に削除verbと維持verbの両方）が揃い、人間承認（nankotsu029）を得た。`git tag`は0件のためD5条件1の代替判定「v1経路にしか存在しない退役対象機能はゼロ」を証拠付きで満たした（`asset`＝K3、`verify`／`design-verify`＝D2b legacy readerは維持対象）。実装は version中立verb→v1 opt-in→v2明示→既定v2 の順でPaper `onCommand`／CLI `run`を再構成（Paper `handleLegacy`、CLI `legacy()`へv1 dispatchを分離）、tab completionとhelpを既定v2へ更新した。v1挙動・r2 alias・v1 permission・v1 golden・Release format・Schema・placement contract・support catalogは不変。破壊対象の既存testはopt-in形へ更新して回帰維持。docs全面更新（README／quickstart／commands／admin-guide／operations）とCHANGELOG。未承認のカバレッジ劣化は無いため停止条件に該当しない。次は`V2-12-06`（v1削除・名称正規化。本Taskからは開始しない。独立した人間承認を要する）。
- **2026-07-21の実行結果（STOPPED）:** D10 command inventory（Paper／CLIの全verb、削除verbと維持verbの両方）とv1→v2カバレッジ監査を作成し、[監査](audits/v2-12-05-v1-to-v2-coverage-audit.md)へ記録した。D2a-R5のprovider同等性検査は設定key・retry・quota・audit記録が同等以上であることを確認した。一方で**D6の承認範囲（WorldEdit単独runtimeの寸法）外の劣化を4件検出**した。F1: v1のrequest authoring（`request create`／`bounds`／WorldEdit selection取込／chat `prompt`／`list`。backendは`PaperWorkflowService`＝R4）にv2等価物が無く、v2の`request`は既存fileのvalidate／infoだけである。F2: v2の`Release2RetentionServiceV2`等は実装済みだがcommand未接続で、v1 `cleanup`／CLI `journal-verify`／CLI `recovery`に相当するv2 verbが無い。F3: v1の非同期job→candidate→token確認付きexport lifecycleと`job status|cancel`にv2等価物が無く、D10が`job`へ求める「v1／v2 dispatch」のdispatch先が存在しない。F4: `ProviderFailureCode` 9値が`DesignFailureCodeV2` 4値へcollapseされ、運用者に見えるerror codeの粒度が低下する。`git tag`は0件のためD5条件1の代替判定「v1経路にしか存在しない利用可能機能はゼロ」が必須だが、F1／F3により**不成立**である。ADR 0035 D3-K3「`V2-12-05`で他の未カバー機能が判明した場合は停止し、本ADRをamendする」とD6の同旨規定に従い、default routing切替・`/lfc v1`／`lfc --v1` opt-in・deprecation表示・user docs全面更新・CHANGELOGは**実施していない**。コード変更はゼロである。再開にはADR 0035のamendment（rev.4）と、案A（不足機能をv2へ実装する新Task `V2-12-08`〜`V2-12-10`を追加）／案B（劣化をD6へ明示承認として追記）／案C（併用）の選択について人間の決定が必要である（監査§7）。`V2-12-06`へは進めない。

### V2-12-06 v1 removal and renaming

- **状態／Phase／優先度:** **完了（2026-07-21、人間承認 nankotsu029）**／V2-12／P1。ADR 0035 rev.5の未公開project限定例外により30日条件だけをrepository ownerが免除した。D8、R1〜R8限定、D2b／D3維持境界、識別子凍結、test gateは免除していない。
- **目的／前提:** `V2-12-01` ADRの一覧に従いv1の不要機能を削除し、名称を正式化する。前提は`V2-12-05`（deprecation windowはADRに従う）。
- **Scope／成果物:** [ADR 0035](../adr/0035-v1-retirement-governance.md) D2aのR1〜R8だけを削除する。すなわち v1 generator `3.0.0-phase6`（R1）、Release format 1／Design package v1の**publisher**（R2。`ReleasePublisher`／`ReleaseArtifacts`／`DesignArtifactPublisher`）、v1 placement／Undo／recovery（R3）、v1 design／generation orchestration（R4）、v1 provider adapter（R5）、v1 command（R6。D10 inventoryで「維持」とされたverbを除く）、v1専用Schema／exampleのactive inventoryからの除外とimmutable legacy contract resourceへの移設（R7。**削除ではなく移設**）、V2-0互換spine（R8）、およびv1 generatorを直接実行するgolden test harness。加えて単一versionとなる箇所のversion dispatch layer簡約、`V2` suffix等の名称正規化（contract ID／Schema `$id`／checksumへ影響しない範囲。影響する改名はADR明示承認分のみ）、全docs／testsの同期。着手前にD8 drain gateとD2c構造問題解消を完了し、削除順序はD7に従う。
- **非Scope:** ADR 0035 D2a一覧外の削除、v2契約のsemantic変更、機能追加、および次の**維持・移設対象**の削除 — D2bのread-only legacy verifier／reader／migration（`ReleaseVerifier`／`ReleaseVerification`／`DesignArtifacts`／`DesignArtifactVerifier`／`DesignVerification`とv1 intent／design package reader）、K1のimmutable golden archiveとmigration／equivalence regression test（fixture dataは恒久維持）、K2のversion中立共通基盤、K3のcustom asset（`asset` command含む）、K4のverb名（`doctor`／`job`／`recovery`／`version`／`help`）、K5の参照用ADR、R7で移設したlegacy contract resource。
- **主変更:** v1系source／schemas／examples／tests削除、rename一括、CLI／Paper、全docs。
- **必須test／D/M/S:** 削除後のfull `./gradlew test`／`build`、v2全回帰（placement／Undo／Recovery／Release 2／catalog／determinism）、改名後のchecksum不変確認（ADR承認済み変更を除く）、dead code／dead docs／dead link検査。
- **Gate／docs／次／停止:** 達成（2026-07-21）。D8 preflightは2つの既知runtime rootをbyte-exact neutral archiveへ非破壊保存し、strict read-back、inventory SHA-256、unresolved 0でPASSした（[machine-readable evidence](audits/v2-12-06-drain-evidence.json)）。D2cの`deleteTree`はversion中立`FileTreeOperations`へ抽出。D7順でR6→R4→R5→R3→R2→R1＋R8→R7を削除／移設し、一覧外の削除は無い。v1 production writer／generator／placement／commandは存在せず、`/lfc v2`／`lfc v2`は恒久aliasとして維持した。D2bのintent／design／Release 1 read／verify／migrate、K1 immutable golden、K2共通基盤、K3 custom asset、K4運用verb、K5 ADRを維持。R7のSchema／fixtureは`src/main/resources/legacy/v1/`へ隔離し、packaged JAR migrationとactive inventory非掲載を回帰化した。改名はcommand／permissionのADR明示承認範囲だけで、Release 2 contract／semantic checksum／catalogは不変。[retirement evidence](audits/v2-12-06-v1-retirement-evidence.md)、targeted test、full `./gradlew test`（985件、6 skip）、`./gradlew build`、CLI `--help`が通過した。次は`V2-12-07`だが、本Taskからは開始しない。

### V2-12-07 Migration integration audit and Phase gate

- **状態／Phase／優先度:** **完了（2026-07-21）**／V2-12／P1。
- **目的／前提:** V2-12全体を統合監査しPhase gateを閉じる。前提は`V2-12-01`〜`V2-12-06`と`V2-12-08`〜`V2-12-10`。
- **Scope／成果物:** **v2 production-only構成**（[ADR 0035](../adr/0035-v1-retirement-governance.md) Consequences定義: v2が唯一のproduction writer／generator／placement／通常command経路であり、v1はmigration専用read-only compatibility境界（D2b）と明示的維持対象（K1 archive、K3 custom asset、K5 ADR、R7 legacy contract resource）だけに隔離された構成。「v1コードが一切ない構成」ではない）でのfull clean test／build、E2E（request→design→generate→export→placement→Undo→Recovery）、command／permission／security監査、docs／roadmap／catalog／Schema inventory整合（active inventoryにR7移設済みlegacy resourceが現れないこと）、legacy migration経路の回帰、release checklist更新、release decision。
- **非Scope:** 新実装、大規模bug fix（defectは新Task化して本gateを未完のままにする）、未完Beta hardeningの免除。
- **主変更:** audit、roadmap、release checklist、README／limitations／operationsの実態同期。
- **必須test／D/M/S:** full clean suite、all capability directory／ZIP tamper、determinism／runtime profile、fault injection／recovery、catalog false-promotion corpus、（v1削除後の）v2 golden。
- **Gate／docs／次／停止:** 達成（2026-07-21）。v2 production-only構成をADR 0035 Consequencesの定義どおり監査し、未解決critical／highなしでv2単独運用の成立を確認したためV2-12 Phase gateを閉じた。executable gateは新規`integration/v2/MigrationPhaseGateV2Test`（6件）で、(1) v2 production-only構成境界（D2a writer不在＋D2b/D3維持資産＋R7 active inventory排除・legacy packaged copy存在＋r2/`--v1`削除＋router root=v2）、(2) 8 production verbのv2 routingとlandformcraft.v2.* permission、(3) production offline経路E2E（request→generate/export→strict preview→placement-eligible）、(4) capability false-promotion不在（4 paper_apply SUPPORTED、`measured()` dimension limit、sealed catalog checksum一致、Release capability list `[environment-fields, hydrology-plan, sparse-volume, surface-2_5d]`不変）、(5) K1 v1 golden→D2b packaged reader migration回帰（deterministic PUBLISHED）、(6) portfolio determinism（正逆順／4 thread／tr-TR／Chatham）を固定した。world placement／Undo／Recovery lifecycle、tamper corpus、fault injection、cross-runtime determinismは既存test（`V2CommandPathE2EV2Test`／`Release2PlacementApplicationServiceV2Test`／`PlacementRecoveryServiceV2Test`／`PlacementPhaseGateV2Test`／capability verifier suites）がfull clean suiteで再実行し担保する。`./gradlew clean test build`は991件・6 skip・0 failure／errorで**BUILD SUCCESSFUL**（985＋新規6）。v1 golden／Release format／catalog／placement contract／capability列は不変で、能力・寸法昇格は行っていない。監査で**pre-existing・LOW・Scope外のtest-hygiene defect 1件**（V2-6-09/10のplacement round-trip testがtracked exampleへ非決定的なconfirmation nonce由来値を書き込む）を検出し`V2-12-11`として登録した。gate criterion（critical/high無し・v2単独運用成立）に該当しないためgateを閉じ、churnした2 exampleはcommitしない。Beta hardening blockerは免除せず、release decisionは「Beta blockerが残るため未リリース」を維持する。証拠は [V2-12 Phase gate audit](audits/v2-12-07-phase-gate.md) を正本とする。V2-12親Phaseは`完了`。次のTrack A未完はfollow-up defect `V2-12-11`（LOW）だけで、本Taskからは開始しない。

### V2-12-11 Placement example test-hygiene fix（`V2-12-07` gate検出、post-gate follow-up）

- **状態／Phase／優先度:** **完了（2026-07-21）**／V2-12／P2（LOW）。
- **目的／前提:** `V2-12-07` gateが検出したpre-existing test-hygiene defectを解消する。前提は`V2-12-07`。gateはこのdefectでブロックしない（critical/high非該当、v2単独運用に無影響、失敗テストなし）。
- **Scope／成果物:** `core/v2/placement/undo/PlacementUndoServiceV2Test.undoPlanCodecRoundTrip`（line 242）と`core/v2/recovery/PlacementRecoveryServiceV2Test`（line 565）が、assertionを伴わずtracked example（`examples/v2/placement/placement-undo-plan-v2.json`／`placement-recovery-plan-v2.json`）へ書き込む副作用を除去する。両testは`@TempDir`へのround-trip書き込みだけを残す（または固定test nonceでsealed exampleを再生成する）。`confirmationHash`は`PlacementConfirmationBinderV2.newPlaintextToken`＝`UUID.randomUUID()`由来の一回用security nonceのsha256であり、production security上random要件が正しいため、production側は変更しない。
- **非Scope:** confirmation token生成のsecurity contract変更、production determinism契約変更、v1資産、Release format／capability／catalog、default routing。
- **主変更:** `core/v2/placement/undo/PlacementUndoServiceV2Test`、`core/v2/recovery/PlacementRecoveryServiceV2Test`（必要なら`examples/v2/placement/`のsealed値）。
- **必須test／D/M/S:** 修正後の`./gradlew test`がtracked fileをdirtyにしないこと（`git status --short`が空）、両testのround-trip assertionが不変で通ること、`PlacementPhaseGateV2Test`／`SchemaContractTest`のexample読取回帰。
- **Gate／docs／次／停止:** 達成（2026-07-21）。両testの`plaintextToken`固定nonce注入seam（`request.plaintextToken()`非null時に採用、null時は従来どおりrandom）を使い、codec round-trip testだけがsealed exampleを決定論的に再生成するようにした。`PlacementUndoServiceV2Test`へ`afterApplied(root, twoTiles, plaintextToken)` overloadと固定token `EXAMPLE_CONFIRMATION_TOKEN`、`PlacementRecoveryServiceV2Test`へ`prepareRequest`/`prepareAccept`のtoken overloadと固定tokenを追加し、他の全scenarioはrandom token経路を維持した。production側の`PlacementConfirmationBinderV2.newPlaintextToken`（`UUID.randomUUID()`）はsecurity要件のため不変。`examples/v2/placement/placement-undo-plan-v2.json`／`placement-recovery-plan-v2.json`を固定nonceで再生成し、連続runでbyte-stable（churnなし）を確認した。`./gradlew clean test build`が通り、`PlacementPhaseGateV2Test`／`SchemaContractTest`のexample読取回帰は不変。security契約への抵触は不要だった。V2-12 follow-upはこれで完了で、Track Aに未着手Taskは残らない。

### V2-12-08 v2 request authoring

- **状態／Phase／優先度:** **完了（2026-07-21）**／V2-12／P1。
- **目的／前提:** `V2-12-05`監査のF1を解消する。v1にしか存在しないrequest authoring（作成・編集・列挙・WorldEdit selection取込・chat prompt取込）を、v2の`generation-request-v2.json`に対して提供する。前提は`V2-12-03`（v2 command経路）と[ADR 0035](../adr/0035-v1-retirement-governance.md) D11（rev.4）。
- **Scope／成果物:** v2 request storeと`v2 request`のauthoring verb群。Paperは`create`／`bounds <id> ...`／`bounds selection <id>`（WorldEdit selectionからbounds取込）／`prompt <id>`（次のchat 1件を取込。失効・`cancel`取消・secret検査はv1と同等以上）／`list`、CLIは`create`／`bounds`／`prompt`／`list`。既存の`validate`／`info`は不変。書込先はPaperでは`data/v2/`配下へsandbox化し、絶対path・`..`・symlinkを拒否する。`GenerationRequestV2`のstrict schemaでvalidateしてからatomic publishする。permission nodeは`landformcraft.v2.request`系を細分（`landformcraft.v2.request.create`／`.edit`）し、`plugin.yml`とtab completionへ反映する。
- **非Scope:** `GenerationRequestV2` contractの変更、AI provider呼び出し、v1 request storeの削除・変更、default routing切替（`V2-12-05`）、image入力経路。
- **主変更:** `core.v2.command`（request authoring backend）、`paper/PaperV2WorkflowServiceV2`、`paper/LandformCraftCommand`、`cli/LandformCraftCli`、`plugin.yml`、`docs/commands.md`。
- **必須test／D/M/S:** create→bounds→prompt→validate→`v2 export`までのE2E、strict schema拒否、path traversal／symlink拒否、secret入力拒否、prompt失効と`cancel`、同入力→同checksum（D）、request store のadmission（M）、sandbox境界（S）。v1 request store不変の回帰。
- **Gate／docs／次／停止:** 達成（2026-07-21）。v1で可能だったrequest authoring操作がすべてv2で実行でき、authoringしたrequestが`v2 export`で`placementEligible: true`まで到達することをE2Eで示した。実装は新しいBukkit非依存の`core.v2.command.V2RequestStoreV2`で、`GenerationRequestV2`契約は不変（各操作は現requestを読み1 fieldだけ差し替え、`LandformV2DataCodec`のstrict schema検証＋atomic publishを毎回通す）。**Scope明確化2点**: (1) Paper側の選択範囲取込はTask記載の`bounds selection <id>`ではなく`v2 request selection <id>`とした。routerは`(verb, operation)`で候補を選んでからarityを見るため、同じ`bounds` operationに9 tokenと5 tokenの2形を置くと衝突するためである。(2) 非Scope「image入力経路」はV2-7の画像抽出pipeline（`format.v2.constraint.extract`）を指し、request levelのconstraint map **source宣言**は含まないと解釈した。`surface-2_5d` exportが「宣言済みconstraint map sourceちょうど1件」をfail closedで要求するため（`CoastalSurfaceExportPipelineV2.writeConstraintFields`）、宣言verbが無いとauthoringしたrequestは`v2 export`へ到達できず本TaskのGateを満たせない。canonical categorical形（U8 grayscale・north-west原点・east/south軸・pixel中心・回転／反転なし・全面crop・`0=water` `1=land`・no-data禁止）に固定し、それ以外は推測せず手書きJSONのままとする。routerは1 operation tokenがsurfaceごとに異なるarityを持つ場合にcaller surface→arityの順で解決するよう拡張し、各surfaceが自分のusageを受け取る。permissionは`landformcraft.v2.request.create`／`.edit`を追加（read系は既存`.request`）。promptのcredential filterはstore側にあるため、v1 Paperのchat取込より厳しい。test: `V2RequestStoreV2Test`（16件）、`V2CommandRouterV2Test`追加6件、`LandformCraftCliV2Test`追加6件。full `./gradlew test`／`./gradlew build`が通り、v1 request store・v1 command・v1 golden・Release format・catalogは不変である。`GenerationRequestV2`契約変更は不要だったため停止条件に該当しない。docsは`docs/commands.md`（v2 request authoring節）、`docs/admin-guide.md`（permission／sandbox／secret）、CHANGELOGを更新した。次は`V2-12-09`（本Taskからは開始しない）。

### V2-12-09 v2 job, candidate, and export lifecycle

- **状態／Phase／優先度:** **完了（2026-07-21）**／V2-12／P1。
- **目的／前提:** `V2-12-05`監査のF3を解消する。v2の`generate`／`export`を非同期job lifecycleへ載せ、`job`verbのv2 backendとcandidate列挙を提供する。前提は`V2-12-02`（production export経路）、`V2-12-08`、[ADR 0035](../adr/0035-v1-retirement-governance.md) D11。
- **Scope／成果物:** v2 job store（job ID、state、cancel token）、`v2 generate`／`v2 export`の非同期実行と既存同期形の維持、`job status <job-id>`／`job cancel <job-id>`のv2 dispatch（ADR 0035 D10の`job`行を履行）、request単位のv2 candidate列挙（`v2 candidate list <request-id>`／`info`）、`v2 export`のreservation-bound confirmation token。cancelはcancel token・Future cancel・interrupt・job stateを連動させ、中途artifactをpublishしない。
- **非Scope:** Release format 2の変更、placement lifecycleの変更（ADR 0034のまま）、v1 job store の削除・変更、default routing切替（`V2-12-05`）。
- **必須test／D/M/S:** 非同期export E2E、mid-pipeline cancelでのstaging cleanupと未publish確認、job state遷移、`job cancel`後の`status`、candidate列挙の決定的順序（D）、job store とexport working setのadmission（M）、job IDからのpath traversal拒否とconfirmation tokenの偽造拒否（S）。既存同期経路とv1 job storeの回帰。
- **Gate／docs／次／停止:** 達成（2026-07-21）。v1の`generate`→job→`candidate`→`export plan`／`create`に相当する運用がv2で完走し、`v2 job status`／`cancel`がv2 job storeへdispatchする。実装は新package `core.v2.job`／`format.v2.job`／`model.v2.job`で、`ExportJobServiceV2`が`Release2ExportApplicationServiceV2`を包む形をとり**既存の単発同期`v2 export`／`v2 generate`は不変**である。cancelはcancel token・Future cancel・interrupt・job stateを連動させ、publisherがcommit point（atomic move）直前までtokenを観測するため取り消したjobはReleaseを公開しない（testで公開物ゼロを確認）。新Schema `generation-job-v2.schema.json`＋example（v1 `generation-job.schema.json`は凍結のまま、拡張も再利用もしない）。**設計判断3点**: (1) candidate列挙はRelease format 2 manifestがrequest identityを持たず、format変更が停止条件であるため、job snapshotの`requestId`を正本にして「そのrequestの公開済みjob」を返す形にした。(2) `export plan`／`create`はPaper専用とした。planは実行中processに存在し、確認したjobも同じprocessで走るため（v1の`export plan`／`create`も`PaperWorkflowService`のin-memory planでPaper専用だった）。`job`／`candidate`はjob storeが永続的なため両surfaceから読めるが、CLIの`job cancel`は別processのworkerに届かずdurable snapshotを`CANCELLED`にするだけで、これはv1 CLIの`job cancel`と同じ性質である。(3) 上位verbの`/lfc job`／`/lfc candidate`をv1／v2 dispatchへ変更することはしていない。既定routingの切替は`V2-12-05`のScopeであり、本Taskはそのdispatch先となるv2 backendを用意する（ADR 0035 D10の`job`行が要求していた欠落はこれで解消した）。routerは1 verbが直接形とoperation形を併せ持つ場合をoperation名かつarity一致で判定するよう拡張した。`ExportBudgetV2.requireFreeDisk`をplan時予約のためpublicにした（挙動不変）。test: `ExportJobServiceV2Test`（11件）、`ExportPlanStoreV2Test`（7件）、`V2CommandRouterV2Test`追加5件、`LandformCraftCliV2Test`追加4件、`SchemaContractTest`のsealed example読取。full `./gradlew test`／`./gradlew build`が通り、v1 job store・v1 command・v1 golden・Release format 2・placement contract・catalogは不変である。Release format 2とplacement contractの変更は不要だったため停止条件に該当しない。docsは`docs/commands.md`（v2 job／candidate／二段階export節）、`docs/admin-guide.md`、CHANGELOGを更新した。次は`V2-12-10`（本Taskからは開始しない）。

### V2-12-10 v2 operational verb wiring

- **状態／Phase／優先度:** **完了（2026-07-21）**／V2-12／P2。
- **目的／前提:** `V2-12-05`監査のF2を解消する。実装済みだがcommandへ未接続のv2運用機能を接続する。前提は`V2-12-03`と[ADR 0035](../adr/0035-v1-retirement-governance.md) D11。
- **Scope／成果物:** `core.v2.operations.Release2RetentionServiceV2`／`RetentionCleanupPortV2`をPaper commandへ接続する retention cleanup verb（plan→confirmation token→execute→status。v1 `cleanup`と同等以上）、v2 placement journalをCLIから検証するverb（v1 `journal-verify`相当）、CLIからv2 recovery状態をread-onlyで参照するverb（v1 `recovery list|status|diagnose`相当。world mutationは伴わないためCLI可）。permission nodeと`plugin.yml`、tab completionを同期する。
- **非Scope:** retention policyそのものの変更、新しいrecovery分類、v1 `cleanup`／`journal-verify`／`recovery`の削除・変更、default routing切替（`V2-12-05`）。
- **主変更:** `core.v2.command`のverb表、`paper/LandformCraftCommand`、`cli/LandformCraftCli`、`plugin.yml`、`docs/operations.md`、`docs/admin-guide.md`。
- **必須test／D/M/S:** retention plan→execute→statusのE2Eとconfirmation token失効、削除対象がretention window外だけであること、journal検証の positive／corruption fixture、recovery read-onlyがworldへ触れないこと、報告順序の決定性（D）、対象列挙のadmission（M）、CLIからworld mutationへ到達できないこと（S）。
- **Gate／docs／次／停止:** 達成（2026-07-21）。v1の`cleanup`はPaper `v2 retention plan|execute|status`、`journal-verify`はCLI `v2 journal-verify`、`recovery`（read-only）はCLI `v2 recovery inspect`へ接続し、F2の3項目すべてがv2 backendで実行できる。retentionは既存`Release2RetentionServiceV2`を接続しただけでpolicy契約は不変（削除対象はrecovery cleanup plannerのretention window判定＝`V2-6-13`と同一）。本番配線として`Release2PlacementApplicationServiceV2.retentionCleanupPort()`をLandformcraftで`Release2RetentionServiceV2`へ渡し、従来throwしていたdeferred portを置換した。**設計判断3点**: (1) `retention`はplacement stateを読みsnapshot stateを削除するためPaper専用。(2) `journal-verify`／`recovery inspect`は明示artifactだけを読みworldへ触れないためCLI専用（`V2_CLI_ONLY`）。v2 recoveryの実行系`recover diagnose|plan|execute`はPaper専用のままで、`recovery inspect`とはverb token（`recover`／`recovery`）で区別する。(3) CLI `recovery list`（全placement列挙）は設けない。offline store走査はv2の明示path・非推測方針に反するため、明示artifactのinspectのみ提供し、列挙はPaper起動時のrestart-state点検が担う。test: `Release2RetentionExpiryV2Test`（token失効で削除ゼロ、削除対象がrecovery scopeのbyte総量のみ。actor束縛・wrong-token拒否は既存`OperationalOperationsV2Test`）、`V2CommandRouterV2Test`追加3件、`LandformCraftCliV2Test`追加5件。full `./gradlew test`／`./gradlew build`が通り、v1 `cleanup`／`journal-verify`／`recovery`・v1 command・v1 golden・Release format・placement contract・catalogは不変である。retention policy契約の変更は不要だったため停止条件に該当しない。docsは`docs/operations.md`（retention接続とread-only CLI）、`docs/commands.md`、`docs/admin-guide.md`、CHANGELOGを更新した。次は`V2-12-05`の再開（本Taskからは開始しない。`V2-12-08`〜`V2-12-10`が揃い、ADR 0035 rev.4 D11の再開条件は成立した）。

## 13. V2-13 MEDIUM 1024 enablement and measurement（Track C、2026-07-21 S2決定で追加）

2026-07-21承認のproposal（B1／B3／B4／B5／B6）を確定Task化したPhase。`V2-13-04`完了後のread-only performance review（2026-07-21）を受けて`V2-13-06`を追加し6 Taskとした。目的は、実測済み1000×1000を基礎として1024×1024のoffline生成と既存world安全配置を成立させることである。全Taskで既存の配置安全順序（validate→preview/export→effect envelope→estimate/reserve→bound confirmation→snapshot-all→apply→settle→full effect-envelope verify→Undo可能性）とverify範囲を弱めない。未測定寸法を`SUPPORTED`／`testedMaximum`と表現しない。`PlacementDimensionLimitV2`のcatalog昇格は`V2-13-04`完了後の**別承認**だけが行う。

### V2-13-01 Placement stage instrumentation and 1000 baseline re-measurement（旧PROPOSAL-B1）

- **状態:** 完了（2026-07-21）。closed vocabulary（`OperationalMetricUnitV2.SECONDS`＋additive `OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_{PLAN,SNAPSHOT,APPLY,SETTLE,VERIFY,UNDO}`）、runtime隔離（`OperationalMetricsCollectorV2.RUNTIME_LABELS`で既存17 sample／checksum不変）、deterministic bottleneck分析（`PlacementStageDurationAnalysisV2`）、committed evidence emitter（`scripts/measure/v2-13-01-stage-durations.py`）＋host runner（`v2-13-01-stage-instrumentation-run.sh`、V2-11-05 lifecycle再利用・safety順序／verify範囲不変・現行`v2 place`／`v2 undo`経路）、Schema＋example＋ADR 0031 additive追記を納品し全test＋build緑。**FAWE 2.15.2単独ホストで1000×1000をcold pass1／warm pass2実測（両passともAPPLIED→full verify→UNDONE）。工程別秒数（秒）: PLAN 4、SNAPSHOT ~200、APPLY ~1571-1572、SETTLE 0、VERIFY 24、UNDO 176、total 1975。最大ボトルネック=`PLACEMENT_STAGE_DURATION_APPLY`（~79.5%、両pass一致）。** 含意: per-block `Map` を持つ SNAPSHOT＋UNDO は ~19% にとどまるため、`V2-13-05`（Map packing）の条件付き実行条件は不成立。証跡: [audit](audits/v2-13-01-stage-instrumentation.md)、[measured evidence](audits/v2-13-01-stage-instrumentation-evidence.md)、[runbook](../smoke/v2-13-01-stage-instrumentation-runbook.md)。
- **目的／前提:** V2-11-05の総wall時間（約106分/pass）を工程別へ分解する。前提はなし。
- **Scope／成果物:** `OperationalMetricLabelV2`へのadditiveなstage duration metrics追加（ADR 0031へadditive追記。承認済み）、measurement runnerの工程別evidence出力（コミットされる形式）、1000既存fixtureと実地形相当fixtureでのbaseline再測定（cold／warm、median／max、stage duration、allocation／GC、heap／RSS、disk read/write、loaded chunk peak、FAWE queue depth、snapshot bytes、mutation count、effect-envelope count）。
- **非Scope:** 最適化実装、safety順序・verify範囲の変更、catalog昇格、自由文字列label。
- **必須test／D/M/S:** metric label round-trip、closed enum拒否回帰、既存metrics snapshot回帰。計測は生成checksumへ不影響。PID単独telemetry、raw path／secret非記録。
- **Gate／停止:** 工程別秒数がevidenceとしてコミットされ、最大ボトルネック工程が特定できること。実測ホストが確保できない場合は`BLOCKED_EXTERNAL`。

### V2-13-02 MEDIUM 1024 route and schema extension（旧PROPOSAL-B3、条件付き承認）

- **状態:** 完了（2026-07-21）。`GenerationRequestV2.Bounds`と水平寸法を表すSchema `width`／`length` の `maximum` をscale契約MEDIUM（1024）へ拡張し、V2-8-02で残っていたroute残差（`MacroLandWaterTopologyPlanV2.MAXIMUM_DIMENSION`、`HydrologyRoutingArtifactV2.Outlet`座標、Paper選択`inclusive`）も`ScaleDimensionPolicyV2`へ揃えた。positive fixture `examples/v2/diagnostic/medium-1024.request-v2.json`（1024×1001）と1025 Schema／Java拒否、既存≤1000 checksum／v1 golden不変を確認。preview／rasterの1,000,000 cell予算と`PlacementDimensionLimitV2`（実測1000）は非Scopeどおり不変。1024をSUPPORTED／testedMaximumとは表現しない。
- **目的／前提:** `GenerationRequestV2.Bounds`と水平寸法Schemaの上限をscale契約のMEDIUM（1024）へ拡張する。前提は`V2-8-02`完了（済）。
- **Scope／成果物:** request／routeの水平寸法上限1024化、水平寸法を表すSchema `maximum: 1000`の1024化、1001..1024のpositive fixture、1025拒否境界、既存≤1000の全checksum不変回帰。1024²到達に必要な資源予算（preview面積1,000,000 cells等）の再設計はpeak実測を伴う場合だけ本Taskへ含める。
- **非Scope（承認条件）:** 1025以上の受理、LARGE／3072の受理、`PlacementDimensionLimitV2`変更、1024の`SUPPORTED`／`testedMaximum`表現、Schema `$id`／capability名／format識別子の変更、水平寸法以外の`maximum: 1000`（値レンジ等）の機械的変更。
- **必須test／D/M/S:** 境界1024／1025、v1 golden、既存fixture checksum不変、決定性回帰。
- **Gate／停止:** checksum不変と境界検査が通ること。水平寸法か値レンジか判別できない`maximum: 1000`が見つかった場合は変更せず停止して報告する。

### V2-13-03 1024×1024 offline generation budget measurement（旧PROPOSAL-B4）

- **状態:** 完了（2026-07-21）。1024専用fixture `examples/v2/diagnostic/medium-1024-square.request-v2.json`、`PreviewDimensionBudgetV2`（既存1_000_000 cell gateの共有化）、`GenerationBounds.MAX_HORIZONTAL_SIZE`／`TopDownCoordinateMapping`のMEDIUM天井1024整合、offline probe（`Medium1024OfflineBudgetProbeV2`＋`scripts/measure/v2-13-03-offline-budget-run.sh`）を納品。[evidence](audits/v2-13-03-offline-budget-measurement-evidence.md): Scale／Export admissionはPASS（64 tile、estimated resident ~60 MiB ＜ MEDIUM retained 96 MiB）だが、Blueprint compileが`HydrologyPlanV2.GraphWorkBudget.globalCellCount ≤ 1_000_000`で fail closed（blockingStage=`HYDROLOGY_GRAPH_WORK_BUDGET`）。preview／macro rasterの1_000_000 cell gateも1024²を拒否（unit確認）。**予算は拡張せず超過を報告して停止**。E2E（validation→preview→export→self-verify）は未完了。1024をSUPPORTED／testedMaximumとは表現しない。
- **同日follow-up修正（2026-07-21、Task外）:** 本Taskが特定した1_000_000 cell budget（`HydrologyPlanV2.GraphWorkBudget.globalCellCount`、`PreviewDimensionBudgetV2.MAXIMUM_CELLS`、`MacroLandWaterTopologyPlanV2.MAXIMUM_RASTER_CELLS`、および同型の`globalCellCount`/scan cell gate 8箇所と対応するJSON Schema `maximum`）を、新設の`ScaleDimensionPolicyV2.MEDIUM_MAXIMUM_CELLS`（=`MEDIUM_HORIZONTAL_CEILING`²=1_048_576）へ拡張した。offline `surface-2_5d` pipeline（field sidecars→Blueprint compile→coastal validation artifact→preview render→全64 tile schematic）が1024²でE2E完了（[updated evidence](audits/v2-13-03-offline-budget-measurement-evidence.md)）。`PlacementDimensionLimitV2`／Paper配置／LARGEは不変。1024をSUPPORTED／testedMaximumとは表現しない。
- **目的／前提:** 1024角end-to-end offline（生成→validation→preview→export→self-verify）のpeak memory／時間／diskを実測する。前提は`V2-13-02`。
- **Scope／成果物:** 1024専用fixture、既存MEDIUM budget内での実測evidence、budget拒否境界の確認。
- **非Scope:** SLO宣言、supported化、Paper配置、LARGE、cell予算の引き上げ（**同日follow-upで別途実施**、上記参照）。
- **Gate／停止:** 実測evidenceのコミット。budget超過が判明した場合は超過箇所を報告して停止する。→ **達成**（超過箇所=`HYDROLOGY_GRAPH_WORK_BUDGET`、二次ゲート=preview／macro 1_000_000 cells）。

### V2-13-04 1024×1024 FAWE placement measurement（旧PROPOSAL-B5）

- **状態:** 完了（2026-07-21）。V2-11-05／V2-13-01と同一実機手法（`run-fawe/` paperclip単独JVM＋RCON＋PID telemetry、FAWE 2.15.2のみ／WorldEdit禁止、measurement profile ceiling 1024×1024、idle Gradle daemon停止でPaper JVMを隔離）で1024×1024を実測。**Pass1（cold）／Pass2（warm）とも`APPLIED`→full effect-envelope verify→`UNDONE`**（wall 2287／2283 s、peak RSS ~5.18／5.23 GiB、swap ~25 MiB固定=非paging、Xmx 8G／Xms 2G）。工程別秒数（pass1）: PLAN 4、SNAPSHOT 209、APPLY **1650**、SETTLE 0、VERIFY 27、UNDO 184、total 2074。**最大ボトルネック=`PLACEMENT_STAGE_DURATION_APPLY`（~79.5%、両pass一致、V2-13-01の1000²形状と同型。+5%はcell数+4.9%に一致し新ボトルネックなし）**。Recovery drill=plan→SIGKILL（confirmation発行前）→restart→doctor clean（`RECOVERY_REQUIRED` orphanなし）。disk admission超過0、v1 help／doctor smoke。納品物: `MeasurementSurfaceFixtureV2.build1024`＋`V21304MeasurementFixtureExporterTest`（offline export→publish→verify PASS）、runner `scripts/measure/v2-13-04-fawe-run.sh`（stage-mark 2-pass＋Recovery drill、`v2 place`／`v2 undo` verb、`DIM=1023`。per-stage emitterは`v2-13-01-stage-durations.py`を再利用）。証跡: [runbook](../smoke/v2-13-04-fawe-1024-measurement-runbook.md)、[audit](audits/v2-13-04-fawe-1024-measurement.md)、[measured evidence](audits/v2-13-04-fawe-1024-measurement-evidence.md)（committed per-stage: [pass1](audits/v2-13-04-evidence/pass1-stage-durations.json)／[pass2](audits/v2-13-04-evidence/pass2-stage-durations.json)）。`PlacementDimensionLimitV2`／catalog／Paper能力列／LARGEは不変、1024を`SUPPORTED`／`testedMaximum`表現しない。catalog昇格は本Task完了後の**別承認Task**。
- **目的／前提:** V2-11-04／05と同一手法（FAWE 2.15.2単独、専用高memoryホスト、単独Paper JVM、RCON、2 pass＋Recovery drill）で1024×1024の実world配置を実測する。前提は`V2-13-01`（工程別計測）、`V2-13-03`。**注意:** `V2-13-03`が当初特定したcell予算gateは同日follow-up修正で`ScaleDimensionPolicyV2.MEDIUM_MAXIMUM_CELLS`へ拡張済みで、offline 1024² generationはE2E成立している。`PlacementDimensionLimitV2`（実測1000）は本Taskの実測範囲であり、着手前の追加cell予算再設計は不要と判断できる。
- **Scope／成果物:** 実地形相当fixtureでの全lifecycle（plan→confirm→snapshot-all→containment→apply→settle→full verify→Undo）実測、工程別duration evidence。
- **非Scope:** 実測なしの`PlacementDimensionLimitV2`定数変更、WorldEdit単独runtimeの昇格、固定10分等の事前SLO宣言。**catalog昇格は本Task完了後の別承認Taskとする。**
- **Gate／停止:** 2 pass＋Recovery drillのevidence。ホスト未確保は`BLOCKED_EXTERNAL`。

### V2-13-05 Per-block map packing optimization（旧PROPOSAL-B6、条件付き）

- **状態:** クローズ（**`NOT_APPLICABLE_BY_MEASUREMENT`**、2026-07-21、不実行）。Reason: APPLY scheduler-slice overhead is the dominant bottleneck; per-block Map packing does not address the measured cause. `V2-13-01`（1000²）と`V2-13-04`（1024²）の2寸法独立実測の両方で最大ボトルネックは`PLACEMENT_STAGE_DURATION_APPLY`（~79.5%、全passで一致）であり、per-block `Map`を持つSNAPSHOT＋UNDOは合計~19%にとどまる。さらにV2-13-04後のread-only performance review（[apply slice analysis](audits/v2-13-06-apply-slice-analysis.md)）がAPPLY律速の根本原因を**slice往復粒度**（`PlacementApplyLimitsV2.defaults()`の32 mutations/slice＋slice毎のscheduler往復・EditSession生成/close）とコード上で特定し、Map表現と無関係であることを確認した。条件付き承認の前提（Map／allocationが主要ボトルネック）の不成立が実測で確定したため、§15の「条件不成立時の不実行クローズ」により本Taskを閉じる。対応は新Task `V2-13-06` が担う。証跡: [V2-13-01 evidence](audits/v2-13-01-stage-instrumentation-evidence.md)、[V2-13-04 evidence](audits/v2-13-04-fawe-1024-measurement-evidence.md)。
- **目的／前提:** `PlacementExpectedBlockResolverV2`／Undo baselineのper-block `HashMap<Coord,…>`をsection-packed表現へ置換する。前提は`V2-13-01`の実測確認。
- **Scope／成果物:** 内部表現のみの変更、同一Releaseでの再測定、checksum／Undo／Recovery回帰。
- **非Scope:** verify弱体化、artifact format変更、safety順序変更。
- **Gate／停止:** 全checksum不変＋改善の実測。ボトルネックが別工程だった場合は実行せずクローズし、代替Taskを提案する。→ **クローズ**（ボトルネックは別工程=APPLY slice往復。代替Task=`V2-13-06`）。

### V2-13-06 Apply slice calibration and batching optimization（2026-07-21 performance review起点で追加）

- **状態:** **完了**（2026-07-22、実機evidence人間承認済み）。閉じた5候補×2 pass較正、1024選択、production既定反映、1000²／1024²各2 pass再測定、budget監査、必須test／example／docs同期までAcceptance PASS。operatorが1024 production変更を明示承認し、`model-assignment.md`の人間確認gateを満たした。親Phase完了・catalog／dimension昇格は含まない。
- **根拠:** `V2-13-04`後のread-only performance review（[apply slice analysis](audits/v2-13-06-apply-slice-analysis.md)）。APPLY（~79.5%）はslice数に厳密に線形（1000²/1024²とも~25.2 ms/slice、誤差0.1%）で、原因は`PlacementApplyLimitsV2.defaults()`の**32 mutations/slice**＋slice毎のPaper scheduler往復（`runTask`）＋EditSession生成/close。同一gateway・同型`restore()`経路のUndo（1,024 blocks/slice）は同量~2.1M blocksを184 s（drift検証read込み）で書いており、slice粒度が説明変数であることのin-repo対照実測が存在する。gateway契約`PlacementApplySliceV2`は4,096 blocks/sliceまで許容済み。plan budget `maximumWorkingBytes`（32 KiB）がadmission連鎖でslice拡大を制限している。
- **目的／前提:** apply書込みslice粒度を較正実測に基づき**安全な最大値**へ引き上げ、APPLY律速を解消する。1,024を無条件採用しない（Undo実績はあるがAPPLYのmain-thread占有時間が同じとは限らず、tick stall／TPS低下／watchdog risk／他plugin影響／cancellation検知遅延を較正で評価する）。前提は`V2-13-01`（工程別計測）、`V2-13-04`（1024² baseline）。
- **Scope／成果物:**
  1. slice size **32／128／256／512／1024**の較正実測（同一ホスト・同一手法）。各設定で**APPLY時間、blocks/s、最大tick時間、MSPT、queue depth、heap／RSS、GC、cancel応答時間、checksum／verify／Undo／Recovery不変**を記録する。
  2. 較正結果に基づく**最大の安全なslice値**の選択。
  3. `maximumWorkingBytes`（plan budget、現32 KiB）の**意味監査を変更の前提**とする: 640 bytes/mutationの内訳、実際に同時保持される最大mutation数、slice終了後に確実に解放されるか、複数placement同時実行時の合計、admissionの役割（メモリ保護か単なるplan契約か）を明確化した上で根拠付きで変更する。共有安全budgetであると判明した場合は機械的増額をせず設計を見直す。
  4. 選択値での**1000²と1024²の再測定**（2 pass）とevidenceコミット。
  5. sealed placement example（`placement-plan-v2.json`等、budget埋込み）の決定論的再生成（`V2-12-11`パターン）。
- **非Scope:** `Bukkit.createBlockData`のBlockData／BlockStateキャッシュ（低リスクの補助改善だが、slice拡大＝主改善と効果を分離測定するため本Taskへ同梱しない。別Task候補として較正evidence取得後に判断）、slice pipelining／複数slice per tick（順序・cancel観測点の意味論に触れるため不採用）、safety順序・snapshot-all・containment・verify範囲・Undo／Recovery契約の変更、catalog／`PlacementDimensionLimitV2`昇格、SLO宣言、LARGE。
- **必須test／D/M/S:** `PlacementApplyLimitsV2`境界test、plan budget admission境界test、全slice設定でcanonical順序・tile checkpoint・journal状態機械の不変、sealed example再生成のbyte-stable確認、既存placement lifecycle回帰（`Release2PlacementApplicationServiceV2Test`／`PlacementPhaseGateV2Test`等）、v1 golden。決定性: slice粒度は結果block checksumへ不影響であること（同一Blueprint／seedで同一checksum）。メモリ: slice working set×同時実行数がbudget内で事前admissionされること。security: 変更なし。
- **Gate／停止:**
  - APPLY wall timeが**最低30%以上短縮**
  - end-to-end placement lifecycleが**最低20%以上短縮**
  - checksum、full verify、Undo、Recoveryが**不変**
  - watchdog、tick-stall、memory budgetの**悪化なし**
  - **改善が閾値未満ならコード変更を採用しない**（較正evidenceのみコミットしTaskをクローズする。閾値30%は保守的であり、分析どおりなら大きく上回るはずのため、満たせない場合は仮説誤りと判断する）
  - `maximumWorkingBytes`が共有安全budgetでslice拡大と両立しないと判明した場合は変更せず停止して報告する
  - 実測ホスト未確保は`BLOCKED_EXTERNAL`

- **実装／実測結果:** 1024²同一FAWEホスト較正でslice 32のAPPLY 1647／1648 s・lifecycle 2072／2072 sに対し、slice 1024はAPPLY 109／109 s（-93.38%）・lifecycle 534／534 s（-74.23%）。max tick 16.6 ms、window-average MSPT 5.4 ms、peak RSS 5,498,265,600 bytes、peak used heap 3,572,608,000 bytes、max GC pause 43.473 ms、watchdogなし。選定build再測定は1000² APPLY 102／103 s・lifecycle 506／507 s、1024² 108／108 s・533／533 sで、4/4 pass `APPLIED`→full exact verify→`UNDONE`、1024 Recovery drillとv1 smoke PASS。production既定は1024 mutations/slice、plan ceilingは655,360 bytes。640 bytes/mutationはbounded block-state text（最大512 bytes）＋record/list/validation/alignment headroomを含む保守estimateで、最大18 accepted transactionのslice合計11,796,480 bytes。1 sliceだけin-flightでreceipt後に解放し、pipeliningなし。sealed plan／journal examplesはexplicit regeneration後もbyte-stable。Schema／artifact format／catalog／dimension／safety順序は不変。証跡: [audit](audits/v2-13-06-apply-slice-calibration.md)、[evidence](audits/v2-13-06-apply-slice-calibration-evidence.md)、[runbook](../smoke/v2-13-06-apply-slice-calibration-runbook.md)。
- **Acceptance close:** 2026-07-22、上記実機evidenceを確認したoperatorが1024へのproduction変更を完全承認。全Gate達成として本Taskを閉じる。後続Task、親Phase完了、1024 catalog昇格には進まない。

## 14. V2-14 Image fidelity wiring follow-up（Track B、2026-07-21決定で追加）

2026-07-21承認のproposal（A1／A2）を確定Task化したPhase。V2-7 Phase gateが予約した「wiring／capability Task」の実体である。A3（複数Design Package比較workflow）とMATERIALIZE_NEW_WORLDは**保留**であり本Phaseに含まない。

### V2-14-01 Extract path CLI／Request wiring（旧PROPOSAL-A1）

- **状態:** 実装済み（2026-07-21）。CLI `v2 extract <land-water|height-guide|zone-label>` と `v2 promote <...>` を追加し、V2-7 secure envelope→draft→明示昇格経路をCLIへ接続した。昇格出力（`land-water.png`／`height-guide.png`／`zone-labels.png`＋digest／dimensions）を`v2 request constraint-map`の source宣言へ渡せるようになった。両verbはoperator workstationのimage pathを読むため`migrate`と同様にCLI専用（Paperからは安定code`V2_CLI_ONLY`）。permission node `landformcraft.v2.extract`。runtimeは`EXPERIMENTAL`のまま（`SUPPORTED`昇格は別承認）。
- **目的／前提:** V2-7抽出経路（secure envelope→draft→preview→明示昇格→reconciliation）をCLI／Requestへ接続し、昇格済みconstraint PNGを`v2 request`のconstraint map source宣言へ渡せるようにする。前提はなし（V2-8／V2-13と独立）。
- **Scope／成果物:** CLI verb（extract／promote／source宣言連携）、permission、docs。Bukkit非依存backend `core.v2.ImageExtractionWorkflowServiceV2`（envelope＋extractor＋publisher＋promotion serviceを配線）。
- **非Scope:** 自動／暗黙昇格、Release capability追加、AI provider接続の変更、Paper UI拡張。**`EXPERIMENTAL`→`SUPPORTED`昇格は別承認とする。**
- **必須test／D/M/S:** 既存envelope／promotion／provenance回帰（不変）、CLI routing（`V2CommandRouterV2Test`にextract／promote CLI専用routing・arity・unknown operationを追加）、`ImageExtractionWorkflowServiceV2Test`（extract→promote→V2-1 loader再decode、locale／timezone／thread決定性）、`LandformCraftCliV2Test`（抽出→昇格→request宣言→`v2 export`到達E2E、height／zone、usage、unknown handling拒否）。secret／raw pathは既存envelope／promotionのredact境界を再利用。
- **Gate／停止:** 抽出→昇格→request宣言→`v2 export`到達のE2E。既存契約の意味変更が必要になった場合は停止する。→ **達成**: `LandformCraftCliV2Test.extractPromoteDeclareAndExportRunsTheWholeImageFidelityChain` が CLI から抽出→昇格→`request constraint-map`宣言→`v2 export`（`placementEligible: true`）まで到達することを確認。既存契約の意味変更なし。

### V2-14-02 Oblique／multi-view reference roles（旧PROPOSAL-A2、条件付き承認）

- **状態:** 実装済み（2026-07-21）。checksum影響監査（[audits/v2-14-02-reference-role-checksum-audit.md](audits/v2-14-02-reference-role-checksum-audit.md)、結論PASS）を実装前に完了し、`GenerationRequestV2.ReferenceImageRole`へ`OBLIQUE_TERRAIN_REFERENCE`／`MULTI_VIEW_REFERENCE`を追加、Schema enum・provider prompt文（AI提案入力専用、座標／HARD geometry生成禁止）を同期した。runtimeは引き続き`EXPERIMENTAL`（`SUPPORTED`昇格は別承認）。
- **目的／前提:** `ReferenceImageRole`へ斜視地形reference／同一地点multi-view用roleを追加し、AI提案入力専用（座標constraint生成禁止）をroleの契約として凍結する。前提は`V2-14-01`と独立。
- **Scope／成果物:** enum＋Schema enum追加、provider role説明文、checksum影響監査（canonical checksum影響識別子に該当しないことの確認を実装前に完了する）。
- **非Scope:** 斜視→top-down自動変換、未確認地下地形の推定、hard constraint化。
- **Gate／停止:** checksum影響監査で互換性が確認できない場合は実装せず停止して報告する。→ **達成**: 監査PASS（enum値名はcanonical checksum影響識別子に非該当、追加はadditiveで既存artifact checksum不変、reference roleは決定論的生成へ入らない）。`SchemaContractTest.schemaEnumsStayInSyncWithJavaEnums`（v2 role enum同期）、`GenerationRequestV2CodecTest`（新role round-trip＋既存role checksum golden不変）、`TerrainIntentPromptV2Test`（全roleのsoft-only guard、斜視／multi-viewの座標・地下推定禁止）で確認。

### V2-14-03 README current-state consistency follow-up

- **状態:** 完了（2026-07-22）。登録時の「8種」誤記をproductionの`GenerationRequestV2.ReferenceImageRole` **6種**へamendしたうえで、`README.md`をCLI `extract`→`promote`→`request constraint-map`→`v2 export` E2Eと6 roleへ同期し、2026-07-18 Gap auditを履歴、2026-07-22監査をcurrent inventoryとしてリンクした。production source／Schema差分は0件。
- **目的／前提:** [全地形カタログ監査](../audits/terrain-catalog-full-audit-2026-07-22.md)が検出したV2-7／V2-14の対外説明矛盾を解消する。前提は`V2-14-01`／`V2-14-02`完了。
- **Scope／成果物:** `README.md`の画像抽出状態をproduction CLIの`extract`→`promote`→`request constraint-map`→`v2 export` E2Eと一致させ、reference image roleをproductionの6種へ同期する。2026-07-18 Gap auditは履歴資料と明示し、2026-07-22監査をcurrent inventoryとしてリンクする。
- **非Scope／凍結:** production code、Schema、enum、checksum、runtime capability、`EXPERIMENTAL`／`SUPPORTED`状態を変更しない。V2-15実装を同じTaskで開始しない。
- **主変更:** `README.md`、必要最小限のdocs link（監査正本の件数amend含む）。production source／Schema差分は0件とする。
- **必須test／D/M/S:** README旧主張とrole件数を`rg`で検査し、`ImageExtractionWorkflowServiceV2Test`、`LandformCraftCliV2Test`、`SchemaContractTest`を再実行する。secret／raw pathを文書例へ追加しない。
- **Gate／docs／次／停止:** READMEとproduction事実が一致しリンク切れがなく、上記testが成功すれば完了する。→ **達成**。次は`V2-15-01`を監査正本と現行HEADで再照合してcloseする。production変更が必要と判明した場合、または正本間の新たな矛盾を検出した場合は本Taskを停止する。

## 15. V2-15 Canonical catalog and existing-generator public wiring（Track E続編、47 Task）

### 15.1 Phase共通契約

- **目的／前提:** [全地形カタログ監査](../audits/terrain-catalog-full-audit-2026-07-22.md)の4区分を、重複しないcanonical registryと公開入力→validation→plan→generator→preview→Release→CLI／Paper commandの縦経路へ落とす。Phase開始前提は`V2-14-03`、Phase実装前提は各行の依存Task。
- **共通Scope／成果物:** registry、typed model／codec、semantic・bounds validation、named seed、generator dispatch、bounded plan／field／sparse volume、diagnostic preview、strict Release directory／ZIP read-back、manifest、CLI／Paper status、docs／example／testを必要なTaskへ分割して接続する。
- **共通非Scope／凍結:** 1 Taskで2個目の独立generatorを抱えない。公開識別子の削除・意味変更は`V2-15-03`のADR人間承認前に行わない。Release format 1、legacy read／verify／migrate、既存golden、Paper capabilityを変更しない。Paper `SUPPORTED`昇格はV2-17専管とする。
- **共通主変更:** `model.v2.catalog`、TerrainIntent v2 Schema／codec、`core.v2` dispatch／validation／application service、`generator.v2`、`preview.v2`、`format.v2.release`、CLI／Paper adapter、examples／docs。各leafは自分のfamilyだけを変更する。
- **共通test／D/M/S:** 公開leafはpositive＋malformed／unknown／bounds negative、Schema strict round-trip、same input／seed／generator version checksum、whole／tile／tile逆順／1・4 thread／locale／timezone、preview index、Release directory／ZIP strict read-back、CLI／Paper smoke、unsupported capability rejection、既存20 Feature回帰を必須とする。全域dense 3D allocationを禁止し、2.5Dはbounded tile、volumeはAABB sparse、1000角は既存ScaleProfile budgetで検証する。通常Taskは対象test＋`./gradlew test`＋`./gradlew build`、full clean scenario/tamper portfolioは`V2-15-47`だけが実行する。
- **公開配線leafのsemantic materialization義務（`V2-19-01`、2026-07-23人間承認による遡及追加）:** production wiring系leaf（dispatch route追加・public export接続・placement昇格系）は、(1) 対象Featureが変えるcanonical fieldとblock effect class（`SOLID_SHAPE`／`FLUID`／`MATERIAL`）を宣言し、(2) **final canonical block stream**（公開Releaseのtile block-state stream）から、当該Featureを持たないbaseline Releaseとのdiffで非空効果・形状conformanceを実測するintent-conformance portfolio caseを追加する（判定は`integration.v2.conformance`の`FeatureMaterializationV2.requireMaterialized`。宣言effect class集合と実測effect class集合の完全一致を要求）。plan-only metric（hydrology validation JSON等の計画・検証artifact）とblock metricはportfolioで別欄として報告し、plan-only metricの充足はblock materializationの証拠にならない。意図的no-op（capability spine smoke、定数healthy sampler、`ADD_FLUID`-into-solid identity slice）はspine検証としては許容されるが、Feature昇格・materialization証拠には使用不可。support列（sealed Feature Support Catalog）と公開dispatch到達性は`public-dispatch-reachability-v1`（[registry](current-feature-state-machine-registry.md)）で別軸表示し、`OFFLINE_PRODUCTION` routeのmaterialization表示をPLAN_ONLYからMATERIALIZEDへ変えるのはportfolio block-effect実測を伴う同一変更だけである。registry／Schema／codec／offline plan／determinism系leafはこの義務の対象外。
- **共通停止:** 新artifact format、公開contract破壊、generator＋Paper mutationの同時実装、複数独立generator、既存checksum変更、または1 Taskで収まらないsubsystemが必要なら停止し新ID／ADRを登録する。

### 15.2 Task一覧

| Task | 依存 | Scope／成果物 | Gate／非Scope |
|---|---|---|---|
| `V2-15-01` | `V2-14-03` | 2026-07-22監査、4区分、57-kind target案、Task graphを現行HEADへ再照合 | **完了（2026-07-22）**。HEAD `23cc424`で60 enum＝60 Schema、catalog 71（public 60＋synthetic 11）、dedicated binding 16、production coastal 4、4区分4／45／8／48、target差分`60 - 14 + 11 = 57`、Task graph 47／19／7を再確認。production／Schema／example／capability変更なし。証拠は監査§2.2／§13 |
| `V2-15-02` | 01 | current-state machine registryとCI projection（enum／Schema／module／catalog／docs差分検出） | **完了（2026-07-22）**。read-only registryが60 FeatureKindのsource set、binding、catalog profile／current stateをstableに投影し、欠落／未知／catalog欠落／module binding不整合をCIでfail-closed検出。文書projectionもSHA-256で同期検査。現行semantic／checksum、Schema、example、capability不変。証拠は[registry](current-feature-state-machine-registry.md)と監査§13.2。target migrationは非Scope |
| `V2-15-03` | 01 | 既存14 Schema valueのalias／subtype／child移行ADR | **完了（2026-07-22、人間承認済み）**。[ADR 0036](../adr/0036-canonical-feature-identifier-disposition.md)をAcceptedとし、14値の処置、V2-15-04のexact typed-field allowlist、explicit `LEGACY_V2`／`CANONICAL_V2` projection、canonical `legacySeedBinding`、1 release cycle、単調lifecycleと運用rollbackを固定した。Schema／enum／writer／generator／capabilityは本Taskで変更していない |
| `V2-15-04` | 02,03 | target registry、Schema projection、migration／deprecation、docs projection | **完了（2026-07-22）**。46-kind `CANONICAL_V2` Schema／model／strict codec、14 disposition registry、explicit `LEGACY_V2` readerとguess禁止dispatcher、lossless migrator／version付きreport／atomic bundleを追加。14 mapping、ambiguous／missing owner、seed tuple checksum参加、idempotence、lifecycle単調性、compatibility pairを検証し、legacy Schema／fixture／checksumとcapabilityは不変。証拠は[target registry](canonical-feature-target-registry.md)と監査§13.4 |
| `V2-15-05` | 02 | registry-driven generator／validator／preview／export dispatch spine | **完了（2026-07-22）**。`production-dispatch-registry-v1`がV2-15-02 current-state projectionとcompile-time handlerを照合し、既存coastal 4件の完全なgenerator／validator／preview／export chainだけをproduction export前に選択する。`BACKSHORE_PLAINS`は既存fixture互換のcontract-only入力で、単独実行・昇格は不可。missing／duplicate／unknown／partial／state昇格、未接続kind、複数未合成pipelineをartifact生成前にfail closedで拒否し、registry／plan checksumのorder／thread／locale／timezone安定性を検証した。Release capabilityは`surface-2_5d`のまま、Schema／example／support level／Paper能力は不変 |
| `V2-15-06` | 05 | `hydrology-plan` production application pipeline | **完了（2026-07-22）**。`HydrologyPlanExportPipelineV2`と`Release2HydrologyExportApplicationServiceV2`がcoastal production featureへempty-graph routing／reconciliation／validation／previewの共有artifactを載せ、`["hydrology-plan","surface-2_5d"]`をstrict directory／ZIP verifyする。capability明示`select`、graph binding、determinism、cancel cleanup、surface-only回帰を検証。個別Feature昇格・Schema／support level／Paper能力は不変 |
| `V2-15-07` | 05,06 | `environment-fields` production application pipeline | **完了（2026-07-22）**。`EnvironmentFieldsExportPipelineV2`と`Release2EnvironmentExportApplicationServiceV2`がshared hydrology chainへgeology／climate／snow／material／palette／ecology／validation／previewを載せ、`["environment-fields","hydrology-plan","surface-2_5d"]`をstrict directory／ZIP verifyする。hydrology dependency、determinism、cancel cleanup、surface／hydrology回帰を検証。個別Feature昇格・Schema／support level／Paper能力は不変 |
| `V2-15-08` | 05,07 | `sparse-volume` production application pipeline | **完了（2026-07-22）**。既存environment chainへbounded identity SDF、ordinal固定ordered CSG、AABB index、hard-pass validation、streaming 3D volume tileを重ね、exact capability prefixのdirectory／ZIP strict read-backをproduction APIへ接続。cancel terminal stateのread→save raceも直列化し、全test／build成功。個別volume Feature、Schema／artifact format、support level、CLI／Paperは不変 |
| `V2-15-09` | 05 | V2-9／10 foundation surface export adapter | **完了（2026-07-22）**。[ADR 0037](../adr/0037-foundation-surface-to-surface-2_5d-mapping.md)で2.5D foundation→既存`surface-2_5d`写像を固定。`FoundationSurfaceExportAdapterV2`／`Release2FoundationSurfaceExportApplicationServiceV2`がplain／hill mergeをcoastal exact setへ投影し、directory／ZIP strict verify＋eligibilityを通す。dispatch registryへ第二`["surface-2_5d"]`は登録せず、Feature昇格・新capabilityなし |
| `V2-15-10` | 03,06 | `RIVER`＋legacy meandering subtype public wiring＋[ADR 0039](../adr/0039-offline-production-route-eligibility.md) 候補A の dispatch `v1→v2` contract bump（最初の適用 leaf） | **完了（2026-07-23）**。Gate 0（ADR 0039 Accepted、候補A採択）を満たし配線した。`ProductionDispatchRegistryV2`を`v2`contractへbumpし`RouteClass.OFFLINE_PRODUCTION`（dedicated module＋export SUPPORTED＋非production-connected＋pipeline executableKinds所属のみ許可）を新設、`RIVER`／`MEANDERING_RIVER`を`hydrology-plan`共有pipelineへ登録した（`PRODUCTION_CONNECTED`意味・Paper `SUPPORTED` exact set はcoastal 4のまま不変）。`RIVER`は`HydrologyRiverModuleV2`へ専用binding、`DiagnosticBlueprintCompilerV2`が`MeanderingRiverSubtypeBridgeV2`逆変換で既存`MeanderingRiverPlanCompilerV2`／`MeanderingRiverGeneratorV2`をそのまま再利用（field math／checksum契約不変）。`HydrologyPlanExportPipelineV2`のreconciliation state構築を空list固定から`baselineState`へ修正（coastal-only経路は不変）。`CompositionProfileRegistryV2`のNORMATIVEへ`RIVER`確信度のみ追加（6→7／PROVISIONAL 54→53）。intent-conformance portfolioへ`harbor-cove-64-honored-river`caseを追加し、公開Releaseの`hydrology/validation.json`から`hydrology.river.*`metricを読み戻して適合を確認、`MacroFoundationPhaseGateV2Test`も追随。CLI／`V2WorkflowServiceV2`へCLI専用`v2 export hydrology-plan`を追加（Paper能力は不変）。`BRAIDED`（V2-16）・他kindは対象外、既存meander checksum・v1 golden・full suiteはPASS |
| `V2-15-11` | 03,06,`V2-19-01` | `LAKE`＋oxbow cutoff subtype public wiring | dam reservoirはV2-16。**stage gateは`V2-19-01`の2026-07-23完了（人間承認済み）で解除された。本leaf以降の公開配線leafは§15.1のsemantic materialization義務（block effect class宣言＋final block streamからの非空効果・形状conformance portfolio case）をAcceptanceへ含む** |
| `V2-15-12` | 06 | `CANYON` public CLI／Release wiring | submarine canyon非Scope |
| `V2-15-13` | 06,08 | `WATERFALL`＋`WATERFALL_VOLUME` overlay wiring | volumeをFeatureKind化しない |
| `V2-15-14` | 06 | `DELTA` public wiring | estuary非Scope |
| `V2-15-15` | 06 | `TIDAL_CHANNEL_NETWORK` public wiring | mangrove／estuary composition非Scope |
| `V2-15-16` | 06 | `FJORD` public wiring | `ICE_FJORD`はV2-16 |
| `V2-15-17` | 03,07,09 | `MOUNTAIN_RANGE`＋alpine／glacial compatibility profiles | 3独立generatorを作らない |
| `V2-15-18` | 03,07,09 | `ARCHIPELAGO`＋volcanic compatibility profile | caldera／lava childを再利用 |
| `V2-15-19` | 03,07,09 | `MARSH`＋mangrove compatibility profile | peat bogはV2-16 |
| `V2-15-20` | 07 | `CORAL_REEF` public wiring | lagoon／passはchild、floating reefはV2-16 |
| `V2-15-21` | 03,09 | `PLAIN`＋backshore alias | backshore独立generator禁止 |
| `V2-15-22` | 09 | `HILL_RANGE` public wiring | mountain profile変更なし |
| `V2-15-23` | 09 | `VALLEY` public wiring | U字谷はprofile |
| `V2-15-24` | 09 | `FLOODPLAIN` public wiring | river relation必須 |
| `V2-15-25` | 09 | `ROCKY_COAST` public wiring | cape checksum不変 |
| `V2-15-26` | 09 | `SEA_CLIFF` public wiring | sea cave host handoffを検証 |
| `V2-15-27` | 09 | `SINGLE_ISLAND` public wiring | barrier preset非Scope |
| `V2-15-28` | 09 | `VOLCANIC_CONE` public wiring | caldera／lava childのみ |
| `V2-15-29` | 09 | basin／shelf／slope continuous marine trio | 同一transect kernelの明示variant。3独立generator禁止 |
| `V2-15-30` | 29 | `SUBMARINE_CANYON` public wiring | surface canyonとowner分離 |
| `V2-15-31` | 29 | `ABYSSAL_PLAIN`＋`SEAMOUNT` basin-contained variants | trench／ridge非Scope |
| `V2-15-32` | 08,09 | `CAVE_ENTRANCE` public wiring | frozen cave checksum bind |
| `V2-15-33` | 08,09 | `UNDERGROUND_RIVER` public wiring | flooded hookはinternal |
| `V2-15-34` | 07,09 | valley glacier／ice cap／ice sheet common-kernel wiring | 3 variant以外非Scope |
| `V2-15-35` | 34 | moraine／outwash wiring＋missing preview index | glacial parent bounds必須 |
| `V2-15-36` | 08,09 | sinkhole／karst spring graph wiring | karst caveはsubtype |
| `V2-15-37` | 09 | escarpment／plateau transition pair wiring | mesa／butteはprofile |
| `V2-15-38` | 08,09 | `LAVA_TUBE` public wiring | `CARVE_SOLID` ownershipのみ |
| `V2-15-39` | 06,09 | `SPRING` public wiring | karst spring specialization不変 |
| `V2-15-40` | 08 | cave network／lush cave common graph wiring | cave graph internal component非公開 |
| `V2-15-41` | 08 | `OVERHANG` public wiring | gravity containmentはPaper gateへ残す |
| `V2-15-42` | 08 | `SKY_ISLAND_GROUP` public wiring | single islandと混同しない |
| `V2-15-43` | 03,08 | `UNDERGROUND_LAKE` public vertical slice | 新public kind 1件、cave／fluid bounds |
| `V2-15-44` | 03,08 | `SEA_CAVE` public vertical slice | rocky coast／cliff host relation |
| `V2-15-45` | 03,08 | `NATURAL_ARCH` public vertical slice | through-passage／support invariant |
| `V2-15-46` | 03,04 | child／overlay／compatibility catalog cleanup | generator削除／Schema破壊はADR承認範囲のみ |
| `V2-15-47` | 10..46 | Phase gate、既存20＋全leaf E2E、full clean baseline、**intent-conformance再検証（`V2-18-12`追記、2026-07-23人間承認）**: 公開接続した各kindがintent-conformance portfolio case（`integration.v2.conformance`）を持ち、portfolio全case適合（登録済み非適合ゼロ＝宣言arm等の宣言集合と実測接続集合の一致、HARD target充足）であること | 親Phaseを閉じる唯一のTask。欠陥は隠さず新IDへ分離 |

## 16. V2-16 Deferred／new terrain and composition（Track E続編、19 Task）

### 16.1 Phase共通契約

- **目的／前提:** `V2-15-47`で閉じたcanonical registry／public dispatchを再利用し、preset、deferred generator、新規地形を重複実装なしで完成させる。
- **共通Scope／成果物:** parent-child contract、composition engine、bounds配分、依存順、named seed derivation、validation／failure behavior、preview legend、manifest composition記録を`V2-16-01`で固定し、各leafは既存Feature／child planを再利用する。
- **共通非Scope／凍結:** preset専用generator、同じ地形の二重実装、未承認public identifier削除、Paper `SUPPORTED`昇格、LARGE supported表現を禁止する。
- **共通test／D/M/S:** V2-15共通gateに加え、childがparent boundsを越えないこと、overlap／不正交差のfail closed、配置順とseed派生の順序不変、preview legend／manifest strict read-back、1000角bounded working setを検証する。§15.1の**semantic materialization義務**（`V2-19-01`、2026-07-23人間承認による遡及追加）はV2-16の公開配線・preset・composition leafにも同様に適用する: 対象Feature／presetのblock effect class宣言と、final canonical block streamからの非空効果・形状conformance portfolio caseを必須とし、意図的no-opをFeature昇格に使用しない。通常Taskは対象test＋`test`／`build`、full clean portfolioは`V2-16-19`だけが実行する。
- **共通停止:** 共通composition contractで表せない、独立generatorが2個以上必要、新artifact format／公開breaking changeが必要なら停止して新Task／ADRを登録する。

### 16.2 Task一覧

| Task | 依存 | Scope／成果物 | Gate／非Scope |
|---|---|---|---|
| `V2-16-01` | `V2-15-47` | parent／child／preset composition engine、derived seed、bounds allocation、manifest／preview contract | dedicated preset generator禁止 |
| `V2-16-02` | 01 | `WATERFALL_CHAIN` public preset | river／fall／pool再利用 |
| `V2-16-03` | 01 | `ICE_FJORD` public preset | fjord／glacier／environment再利用 |
| `V2-16-04` | 01 | `CENOTE` public preset | sinkhole／cave／flooded hook再利用 |
| `V2-16-05` | 01 | `BARRIER_ISLAND` public preset | island／lagoon再利用 |
| `V2-16-06` | 01 | `ATOLL` public preset | reef／lagoon／pass再利用 |
| `V2-16-07` | 01 | `ESTUARY` composition | delta複製禁止。river mouth＋tidal coupling |
| `V2-16-08` | 01 | `FLOATING_REEF` composition | coral＋aerial host。独立FeatureKindなし |
| `V2-16-09` | `V2-15-10` | braided／bedrock river subtype、river terrace child | `RIVER` family内。new kindなし |
| `V2-16-10` | `V2-15-11` | `DAM_RESERVOIR` standalone vertical slice | dam／barrier／spillway ownership |
| `V2-16-11` | `V2-15-10` | `ALLUVIAL_FAN` standalone vertical slice | fan groupsはmulti-placement |
| `V2-16-12` | `V2-15-29` | `OCEAN_TRENCH` vertical slice | global continuity。LARGEはHOLD policyに従う |
| `V2-16-13` | 12 | `MID_OCEAN_RIDGE`＋submarine volcano subtype | ridge 1 kind。volcanoはseamount provenance |
| `V2-16-14` | `V2-15-21` | `DUNE_FIELD` vertical slice | wind／spacing／height／stoss-lee／interdune／context invariant |
| `V2-16-15` | `V2-15-37` | `BADLANDS` vertical slice | ridge／gully／hoodoo／strata／drainage invariant |
| `V2-16-16` | `V2-15-36` | `TOWER_KARST` vertical slice | tower／spacing／steepness／depression／floor／cave hook invariant |
| `V2-16-17` | `V2-15-37` | `SALT_FLAT` vertical slice | flat floor／crust／water／crack／rim／subtype invariant |
| `V2-16-18` | `V2-15-19` | `PEAT_BOG` subtype | hummock／hollow／pool／drainage／saturation／material。new kindなし |
| `V2-16-19` | 02..18 | Phase gate、full clean baseline、canonical count再確認、**intent-conformance再検証（`V2-18-12`追記、2026-07-23人間承認）**: composition／preset含む公開接続kindのportfolio case登録と全case適合（登録済み非適合ゼロ） | 親Phaseを閉じる唯一のTask |

## 17. V2-17 Paper placement evidence and promotion（Track A follow-up、7 Task）

### 17.1 Phase共通契約

- **目的／前提:** `V2-15-47`／`V2-16-19`でoffline経路が閉じたFeatureを、既存Acceptance gateに従って実機計測し、証拠がある範囲だけPaper capabilityへ反映する。
- **共通Scope／成果物:** feature×capability×runtime×dimension evidence、runbook、machine-readable evidence、full verify、Undo、Recovery drill、catalog promotionとfalse-promotion rejection。
- **共通非Scope／凍結:** 実機未測定kind／dimensionの`SUPPORTED`化、推測値、mock-only完了、placement安全順序、既存FAWE 1000×1000／WorldEdit 64×64上限の根拠なき変更を禁止する。
- **共通test／D/M/S:** runtime／plugin／server version、seed、generator version、artifact／block checksum、memory／disk／tick／MSPT／GC、full verify、Undo、Recoveryを記録する。hostがなければ各実測Taskを`BLOCKED_EXTERNAL`とし、offline testだけでcloseしない。full runtime portfolioは`V2-17-07`だけが実行する。
- **共通停止:** snapshot外副作用、unknown physics、unbounded effect envelope、Recovery不成立、証拠Schema不一致、またはhost欠如では昇格せず停止する。

### 17.2 Task一覧

| Task | 依存 | Scope／成果物 | Gate／非Scope |
|---|---|---|---|
| `V2-17-01` | `V2-15-47`,`V2-16-19` | feature×capability×runtime measurement harness、runbook、evidence Schema | capability昇格なし |
| `V2-17-02` | 01 | hydrology-plan実機matrix、full verify／Undo／Recovery | hostなしは`BLOCKED_EXTERNAL` |
| `V2-17-03` | 01 | environment-fields実機matrix | hostなしは`BLOCKED_EXTERNAL` |
| `V2-17-04` | 01 | sparse-volume実機matrix（fluid／gravity／containment含む） | hostなしは`BLOCKED_EXTERNAL` |
| `V2-17-05` | 01 | foundation／new／preset exported stream実機matrix | 未定義capabilityならRelease Taskへ戻し昇格しない |
| `V2-17-06` | 02..05 | evidence範囲だけcatalog promotion | **人間承認必須**。未測定kind／dimension昇格禁止 |
| `V2-17-07` | 06 | Paper Phase gate、Recovery drill、full runtime profile | 親Phaseを閉じる唯一のTask。host不足は`BLOCKED_EXTERNAL` |

## 18. V2-18 Macro foundation and intent conformance（Track A、13 Task）

### 18.1 Phase共通契約

- **目的／前提:** [2026-07-22 macro foundation監査](../audits/macro-foundation-conformance-audit-2026-07-22.md)で確定した欠陥群 — surface production経路に基礎地形ownerが存在せずfeature非所有cell（実測73.0%）が一律`SurfaceBaselineV2`へフォールバックする、map-level HARD入力（`LAND_WATER_MASK`／`EDGE_CLASSIFICATION`／contract-only featureへのrelation／`constraints[]`エントリ）が生成・検証のどちらにも接続されていない、desired field＝生成結果の自己参照でresidualが恒等0、diagnostic ERRORとexport gateが断絶している — を、可視化→fail closed→macro foundation→conformance検証の順で解消する。前提はHEAD `5920afc`の実測evidence。
- **共通Scope:** production export spine（`core.v2.export`）、diagnostic契約、validation target評価、constraint-map binding、macro foundation stage、conformance E2E。
- **共通非Scope／凍結:** Paper `SUPPORTED`昇格（V2-17専管）、LARGE有効化、v1 legacy境界、Release format変更（必要ならADRで停止）、HARD情報の推測補完。各Taskはterrain field／tile／blockの**semantic checksum不変性**と、diagnostic／manifest**容器byte変化の可否**を別項目として宣言し、混同しない。
- **共通test／D/M/S:** 各leafはpositive＋negative、whole／tile／thread／locale／timezone決定性、strict read-back、既存coastal 4件とshared pipeline（hydrology／environment／sparse-volume）の回帰を必須とする。fail-closed化は必ずreport-only段階を先行させ、どの時点でも動作するproduction経路が1つ存在する状態を保つ。
- **共通停止:** 新capability名、Release format変更、公開contract破壊、または1 Taskへ収まらないsubsystemが必要なら停止し新ID／ADRを登録する。

### 18.2 Task一覧

| Task | 依存 | Scope／成果物 | Gate／非Scope |
|---|---|---|---|
| `V2-18-01` | — | Diagnostic severity／production gate契約の正常化。issueを`GATING`／`NON_GATING`へ分類し、production接続済みkindへ無条件発行される`v2.unsupported-capability` ERRORを廃止、exportが消費するseverity／rule IDを契約化、現在無視されているERROR一覧をgolden test化 | field／tile semantic checksum不変。fail-closed化はしない |
| `V2-18-02` | 01 | Intent contribution／coverage診断（report-only）。unconsumed HARD要素一覧、`contractOnlyKinds`のCLI／summary表示、**active contributor被覆**と**surface foundation owner被覆**を別metricとして単一runの`active[]`／owner indexから直接集計 | field／tile／block semantic checksum不変。manifest／diagnostic容器のbyte変化可否を本Taskで明文化 |
| `V2-18-03` | 02 | HARD preflight gate。評価器なきHARD constraint kind、consumerなきHARD relation（contract-only／未接続kind宛を含む）、未解決mapReference（実在・digest・寸法）をartifact生成前にstable rule IDで拒否。SOFTは警告。exampleのダミーdigest（`dddd…`）と欠落mask PNGを実体へ是正 | 正常な既存fixtureは通過。override flagは設けない |
| `V2-18-04` | 02 | Target-driven validation framework＋EDGE evaluator。blueprintの`ValidationTargetV2`を消費する共通経路を新設し、`EDGE_CLASSIFICATION`の測定領域（edge band定義）をevaluator version付き契約として固定、HARD違反でexport拒否 | `CoastalValidatorV2`既存metricは不変。EDGE以外のevaluatorは後続Task |
| `V2-18-05` | 04 | 既存coastal metricの正当性修復。breakwater clear-openingの「plan値と自分自身の比較」恒真metricを実測化し、他の自己参照metricを点検 | golden更新はADR承認範囲のみ |
| `V2-18-06` | 03 | 再利用可能constraint-map binding＋暫定coastal adapter。secure resolve→digest→decode→canonical field登録（normalized XZ registration、寸法不一致・no-data・bit depth／legend・path traversal・digest mismatch・decode memory budget・seed／thread／locale非依存のnegative込み）を恒久共通部品化し、coastal生成への`HardLandWaterSourceV2`注入だけを暫定adapterとして分離 | coastal専用loader化を禁止。恒久consumerは`V2-18-09`のmacro foundation stage |
| `V2-18-07` | 04,06 | ConformanceTargetSet＋desired／actual分離。desired raster（maskがある場合）、aggregate metric target（edge比率等）、topology target（接続性等）、geometric target、provenance bindingの異種集合と、residual種別（raster／scalar metric／topology pass-fail／unconsumed target／tolerance violation）を定義し、`withLandWaterBinding`の生成結果への自己再束縛を入力mask digestへの束縛へ置換 | 全ケースへ完全desired rasterを要求しない |
| `V2-18-08` | 02 | Macro foundation契約ADR（→ [ADR 0038](../adr/0038-macro-foundation-contract.md)、2026-07-22人間承認で`Accepted`・Task完了）。全surface XZ cellにeffective foundation ownerを要求するproduction契約、`MacroLandWaterTopologyPlanV2`（V2-9-12）／`SurfaceFoundationPlanV2`＋merge kernel（ownerless拒否）の昇格差分、`FeaturePrimaryRoleV2`（catalog意味）と分離したcomposition role軸（foundation producer／surface modifier／carver／overlay等）の要否と60 kind対応表、資源budget、EDGE等からの内部境界推測を禁止するresolution policy | `V2-18-04`の結果はADRレビュー入力でありblocking依存ではない。新capability／formatが必要なら停止 |
| `V2-18-09` | 08 | Macro foundation production spine＋coastal統合。明示foundation geometry（LAND／OCEAN polygon被覆）または`LAND_WATER_MASK`からland-water＋provisional elevation rasterを生成し、coastal 4種をfoundation上のmodifierとして再接続、`SurfaceBaselineV2` CLI引数をdeprecation | HARD情報の推測補完禁止（EDGE制約のみからの内部海岸線生成はしない）。coastal単体planのchecksum変更はADR承認範囲 |
| `V2-18-10` | 09 | Surface foundation owner gateのfail-closed昇格。surface domainのfoundation owner被覆100%をexport必須化 | 対象はsurface foundation ownerのみ。modifier／volume／material被覆・request domain外・no-dataは対象外。多重ownerはmerge契約に従い許容 |
| `V2-18-11` | 04 | Intent-conformance E2E portfolio。`coastal-fishing-map`の形状assertion（EDGE実測、beach↔backshore land連続性、breakwater両armのland接続）を常設回帰化し、以降のleaf完了ごとに複合fixture（山＋川＋湖、島＋reef等）を蓄積 | portfolioのPhase gate昇格は`V2-18-12`が行う。実装対象fixtureは`coastal-fishing-map`の後継である`coastal-honored-400`／`harbor-cove-64-honored`（`V2-18-03`以降、`coastal-fishing-map`自身はpreflight拒否されexportへ到達しないnegative fixture）。portfolioは計測専用でproduction gateを追加しない |
| `V2-18-13` ✅ | 11 | **完了（2026-07-23）。** `coastal-honored-400`の東breakwater arm landfall非適合の是正。宣言landfall（0.61, 0.47）がrocky cape西端へ届かずarm全体が孤立land component（2163 cell）になる状態を、rocky cape polygon西端の拡張（NW `0.62→0.60`／SW `0.72→0.685`）によるfixture geometry是正と、`V2-18-09` land-water mask（composed出力∪macro構図、規則`active ? composed : macro-background`）の再生成で解消し、arm（現72852 cell mainlandへ併合）とlandfall cellをmainlandへ接続、portfolioの期待を両arm接続へ更新した。再生成helper `RegenerateHonoredCoastalMaskExampleTest`はcurrent geometryをbyte一致で再現し規則一致を保証。mask sha `8b3f04df…`→`60095de7…`（1154 cell water→land） | `V2-18-11`のportfolioが確定した非適合の是正専用。generator挙動・compositor契約・Release formatは変更していない（fixture＋test＋docsのみ）。残るnon-mainland land componentはrocky capeの宣言済みsea stack 3個のみ。terrain semantic checksumの意味論・v1 golden不変、coastal容器checksum変化はADR 0038 D9承認範囲内 |
| `V2-18-12` | 01..11,13 | Phase gate。全leaf再検証、conformance portfolioのgate化、`V2-15-47`／`V2-16-19` Acceptanceへのintent-conformance追記（人間承認）、V2-15 stage gate解除判断 | **完了（2026-07-23、人間承認済み）。** `MacroFoundationPhaseGateV2Test`がportfolioのgate化（登録済み非適合ゼロ、EDGE HARD充足＋intent→blueprint非脱落、beach↔backshore連続性、全宣言arm landfall、非空虚性、land-mass会計）、fail-closed spine契約、legacy fixture拒否（finding集合pin）、ADR 0038 D4対応表（60／6／54／17）、capability無昇格（exact set）を実行可能に固定し、full clean suite PASS（1217 test、9 skipは全て実機opt-in／手動再生成helperでAcceptance外）。stage gate解除判断は「一律保留→各配線leafのper-leaf義務（対象kind composition profile確定＋portfolio case追加）」。Acceptance追記・解除判断・親Phase閉鎖は2026-07-23の人間承認（atomic decision）で有効化された（[audit](audits/v2-18-12-phase-gate.md)）。親Phaseを閉じた唯一のTask |

## 19. V2-19 Input integrity and block materialization（Track A主担当・横断、16 Task）

### 19.1 Phase共通契約

- **目的／前提:** [2026-07-23横断監査](../audits/cross-cutting-audit-2026-07-23.md)（rev.2、独立レビュー照合済み）で確定した欠陥群 — (1) V2-15 leaf Acceptanceがfinal block streamへの実体化を要求せず「plan-only配線」でleafを完了できる（`V2-15-10`のRIVERはblockを1個も変えない）、(2) reference image付き公開designは`buildProviderRequest`の空list×宣言数一致要求で必ず失敗する（BROKEN_PUBLIC）、(3) constraint source→Intent bindingの公開authoring経路が存在せずHEIGHT_GUIDE／ZONE_LABELはrequest宣言verbすら無い、(4) HEIGHT_GUIDEの生成側consumer不在＋「map正確に1枚」制約でmacro foundationの標高がland/water 2定数flatに固定される、(5) filesystem inventory testのGradle input追跡漏れで失敗がcacheに隠れる — を、gate導入→公開入口修理→consumer接続→表現力拡張の順で解消する。前提はHEAD `f3588f6`の監査evidence（2026-07-23人間承認済み）。
- **共通Scope:** per-leaf Acceptance契約、design／request authoring（CLI／Paper）、constraint binding、macro foundation elevation、surface tile書き出し、conformance portfolio、build設定、docs同期。
- **共通非Scope／凍結:** Paper `SUPPORTED`昇格（V2-17専管）、LARGE有効化、v1 legacy境界、新Release capability／artifact format（必要ならADRで停止）、historic 60-kind Schemaの縮小、HARD情報の推測補完。各Taskはterrain field／tile／blockの**semantic checksum変化の有無**と容器byte変化の可否を別項目として宣言する。
- **Track横断の注記:** 本PhaseはTrack A主担当だが、`V2-19-03`／`04`／`08`はTrack B（V2-7／V2-14後続領域）、`V2-19-05`／`09`はTrack E（V2-15配線領域）のファイルへ触れる。同一ファイルの同時編集禁止規則に従い、V2-15 leafと共有領域（`core.v2.export`、`format.v2.release`等）を変更するTaskは直列にする。
- **共通test／D/M/S:** 各leafはpositive＋negative、whole／tile／thread／locale／timezone決定性、strict read-back、既存coastal 4件とshared pipeline回帰を必須とする。fail-closed化はreport-only段階を先行させ、どの時点でも動作するproduction経路が1つ存在する状態を保つ。
- **共通停止:** 新capability名、Release format変更、公開contract破壊、または1 Taskへ収まらないsubsystemが必要なら停止し新ID／ADRを登録する。

### 19.2 Task一覧

| Task | 依存 | Scope／成果物 | Gate／非Scope |
|---|---|---|---|
| `V2-19-01` | — | Semantic materialization gate。V2-15／V2-16公開配線leafのper-leaf義務へ「対象Featureが変えるcanonical field／block class／material／fluidの宣言」と「**final canonical block stream**からの非空効果・形状conformance測定」を追加し、plan-only metricとblock metricをportfolioで別欄化、Feature Support Catalogのsupport列と公開dispatch到達性を別表示する。意図的no-op（capability spine smoke）はFeature昇格に使用不可と明文化 | **完了（2026-07-23、人間承認済み）**。`FeatureMaterializationV2`（`integration.v2.conformance`）が公開Releaseのfinal tile block-state streamをbaseline Releaseとcell単位でdiffし、非空効果＋宣言effect class（`SOLID_SHAPE`／`FLUID`／`MATERIAL`）と実測effect classの完全一致を`requireMaterialized`で要求する。mandated negative: 実際のV2-15-10 RIVER route（`harbor-cove-64-honored-river` vs `harbor-cove-64-honored`）はblock diffゼロを正直にpinしたうえでgateが必ず拒否し、公開`hydrology/validation.json`の全plan metricがPASSでも代替不可。定数healthy sampler／identity sliceの共通形＝自己diff（changedCells 0）も必ず拒否。positive制御はlandSurfaceY 54→55の変種Release（実block効果を測定・分類・合格）とlocale／timezone／再測定determinism。別表示は`PublicDispatchReachabilityV2`（`public-dispatch-reachability-v1`、表示専用・gate無し）がsupport列／公開dispatch到達性／block materializationの3軸を分離し、[registry](current-feature-state-machine-registry.md)のCI検査済みprojectionへ固定（RIVER／MEANDERING_RIVERは到達可能かつPLAN_ONLYと表示、MATERIALIZED化はportfolio実測を伴う同一変更のみ）。§15.1／§16.1へ遡及義務を追記し、V2-15-11以降のstage gateを解除した。production生成コード・coastal既存Release・sealed catalog checksum・terrain semantic checksum・v1 goldenは不変。full `./gradlew test`／`build` PASS |
| `V2-19-02` | — | Gradle test input修正。filesystem inventory test（`SchemaContractTest`等）が実行時走査するsrc／docs／README／schemas／examplesをtest taskの明示inputへ登録し、untracked含む変更でup-to-date／build cacheを無効化。cache有効／無効で同一orphan判定をCI固定 | **完了（2026-07-23）**。監査実測（文書状態でtest結果が変わる）が根拠。修正前は未追跡orphan exampleを置いても`Task :test UP-TO-DATE`でBUILD SUCCESSFUL、同じ作業treeに`--rerun-tasks`を付けると`everyExampleDocumentIsReferencedBySourceOrDocs`がFAILEDだった。`build.gradle.kts`へ走査rootの単一list `filesystemInventoryRoots`（`AGENTS.md`／`CHANGELOG.md`／`README.md`／`build.gradle.kts`／`docs`／`examples`／`schemas`／`src`）を追加し、`test` taskの`inputs.files(...)`＋`PathSensitivity.RELATIVE`で消費する（`V2-15-02`／`V2-15-04`の個別4ファイル宣言は`schemas`／`docs`へ包含・統合）。走査rootの正本は`buildcontract.FilesystemInventoryRootsV2`で、`SchemaContractTest`／`DocsLinkConsistencyTest`はrootをここから取る（**判定規則は未変更**）。`GradleTestInputContractV2Test`が (1) build script宣言＝`SCANNED_ROOTS`、(2) `test` taskが当該listをnamed input＋RELATIVE normalizationで消費、(3) `src/test/java`の全`Path.of`先頭segmentがrepository root実在なら宣言rootに含まれる（`build`のみ明示許可、`@TempDir`相対のbare nameは対象外）というdrift guard、を固定する。再現checkは`scripts/ci/v2-19-02-inventory-input-check.sh`（orphan example／broken docs linkのprobeを`--build-cache`／`--no-build-cache`両方で実行し8/8一致、probeは`trap`で削除）。full `./gradlew test`（219 class／1256 test／9 skip／0 failure）＋`./gradlew build` PASS、直後の再実行は`Task :test UP-TO-DATE`（example書き戻しtestによる自己無効化なし）。production code・Schema・example・artifact・checksum・v1 goldenは不変。証跡は [V2-19-02 audit](audits/v2-19-02-test-input-tracking.md) |
| `V2-19-03` | — | Reference-image public design修理（BROKEN_PUBLIC解消）。request相対pathのsecure resolve、size／decode／dimension／MIME／pixel budget、metadata（EXIF等）のprovider payload／log非漏洩、`PreparedReferenceImageV2`のrequest順構築、cancel／cleanup、provider handoff、実画像を持つexample整備（`oblique-multi-view`の欠落4画像を含む） | **完了（2026-07-23）**。negative（missing／digest mismatch／oversize／unsupported encoding）と**実CLI／Paper design E2E**必須、Schema-only fixtureでの完了は禁止 — いずれも充足。`ReferenceImagePreparationServiceV2`（`core.v2.design`）が宣言画像をrequest canonical順（id昇順）に準備し、`buildProviderRequest`の`List.of()`を置換して全4 path（import／fixture／openai／anthropic）でproviderへ渡す（画像なしrequestは空listのままbyte不変）。専用decoderは作らず共有`SecureImageExtractionEnvelopeV2`を再利用し、resolve／symlink・hard link alias・traversal拒否、magicと拡張子の一致、single frame、byte／寸法／aspect／pixel／decode予算、TOCTOU再statを通したうえで**sanitized rasterからPNGを再符号化**して提出bytesとする（EXIF／XMP／comment等のsource metadataとfilesystem pathはpayload・logに出ない。`PreparedReferenceImageV2.checksum`＝提出bytesのSHA-256）。提出byteは per-image／合計16MiBで判定し、cancelは画像境界で観測、拒否・cancel時にartifactを生成しない。failure codeは`PATH_SECURITY`／`BUDGET_EXCEEDED`／`INVALID_REQUEST`へ安定対応させ、CLIの`DesignExceptionV2`分類を`LFC-INTERNAL`から`LFC-REQUEST-INVALID`（transport／invalid responseは`LFC-PROVIDER-FAILED`）＋safe message `Design failed safely (<code>).`へ是正した。Schemaは`referenceImage`へ**任意**の`expectedSha256`（既存`$defs/checksum`）を追加し、absentでは書き出さないため既存requestのcanonical byte・checksumは不変。examplesは実画像4枚（`references/*.png`、64×64、整数演算の合成画像。`ReferenceImageExampleFixtureV2`が正本でpixel一致をtestが検査）と`oblique-multi-view.terrain-intent-v2.json`を追加し、`mood`だけに digest を宣言してoptional両分岐を持たせた。E2Eは実CLI `v2 design fixture`（成功／画像欠落のfail closed）とPaper adapter `PaperV2WorkflowServiceV2`（`JavaPlugin`非生成、workspace外symlinkは`PATH_SECURITY`）。**非Scope:** `MANUAL_CONSTRAINT`／`REFERENCE_IMAGE_DRAFT`のCLI公開（`designPathKinds`は4種のまま）、source→Intent binding authoring（`V2-19-04`）、画像からのHARD geometry（ADR 0017の禁止は不変）。terrain field／tile／block semantic checksum・Release format／capability・v1 goldenは不変。証跡は [V2-19-03 audit](audits/v2-19-03-reference-image-design.md) |
| `V2-19-04` | — | Generic constraint source＋Intent binding authoring。LAND_WATER／HEIGHT／ZONE共通のsource宣言verb（既存listの置換でなく追加・更新、role／encoding／strength／tolerance／source ID明示）と、**Intent bindingの生成・確認verb**（binding artifactId＝input digest規則をコード側が担保）。source→binding→compile→consumerのE2E | **完了（2026-07-23）**。既存single-map `request constraint-map` commandの互換維持（land/water 1件を置換する従来の意味のまま）、providerの推測にbinding生成を依存させない — 両方充足。`request constraint-source <id> <slug> <land-water｜height-guide｜zone-label> <promotion-dir> <file>`（CLI／Paper両面、`request.edit`）が3 role共通のsource宣言を**追加・更新**（他の宣言は保持）で行い、role・encoding・寸法・digestは`promote`が封印したpromotion recordから読む（`PromotedConstraintSourceFactoryV2`。height guideのvalue meaning／scale／offset／valid sample範囲とzoneのlabel legendはcommand引数に載らないため推測せずrecord値を使用、roleは操作者が明示しrecordと不一致なら失敗）。`intent bind`／`intent bindings`（**CLI専用**、operator workstationのintent artifactを読み書きするため`migrate`／`extract`／`promote`と同じ扱い）は`IntentConstraintBindingServiceV2`が担い、canonical `artifactId`＝`artifactPrefix(role)+source.expectedSha256()`をコード側で組み立てる（V2-18-07の`SurfaceReleaseCapabilityVerifierV2.verifyIntentBindings`が公開Releaseで照合する規則。authorもproviderもdigestを入力しない）。binding idはsource slug、同一id／sourceIdは置換、role×encoding矛盾（categorical→HEIGHT_GUIDE、water/land以外のlegend→LAND_WATER_MASK）と未宣言slug・requestId不一致は拒否。`bindings`は同規則の再計算に加えmap実体を`SecureConstraintMapSourceLoader`＋IHDR寸法で再解決し（V2-18-03の`dimensionMismatch`は`ConstraintMapPngHeaderV2`へ切り出して共有）、不一致は`LFC-REQUEST-INVALID`でfail closed、binding無しの宣言済みsourceは`unboundSources`として報告する。出力の`generatorConsumer`は実際に読む生成側の有無を表示し（`LAND_WATER_MASK`＝`MACRO_FOUNDATION`、他は`NONE`）、能力を先取りしない。E2Eは実CLIで extract→promote→request authoring→`constraint-source`→`intent bind`→`intent bindings`→`v2 export`（`placementEligible: true`）を通し、map差し替え時のfail closedとPaper側のworkspace外promotion拒否も固定した。**非Scope:** `HEIGHT_GUIDE` consumerと「map正確に1枚」緩和（`V2-19-06`）、`MANUAL_CONSTRAINT`／`REFERENCE_IMAGE_DRAFT`のCLI公開（後者の`artifactId`はcanonical field checksumを使う別規則で、混同しないことを文書化）。**新Schema・新capability・新artifact format無し**、terrain field／tile／block semantic checksum・Release format・v1 goldenは不変。証跡は [V2-19-04 audit](audits/v2-19-04-constraint-source-binding-authoring.md) |
| `V2-19-05` | 01 | RIVER block materialization。routing／reconciliation結果からbounded river bed fieldを生成し、surface tileへbed carve＋water fill（`ADD_FLUID`と`CARVE_SOLID`の責務分離、fluid ownershipはADD_FLUID側）、mouth／source／bank／coastal foundationとのinteraction宣言、tile書出し前のglobal route freeze、**final tile stream**からのbed depth／water continuity／source-mouth reachability／leak envelope検査 | **完了（2026-07-23）**。`V2-19-01`新条件の最初の適用で、Paper `SUPPORTED`昇格なし・hydrology plan artifact契約不変 — いずれも充足。`CoastalSurfaceExportPipelineV2`を`prepare`（tiles以外）と`completeWithTiles`（final canonical block streamの唯一の生成点）へ分割し、`HydrologyPlanExportPipelineV2`が`prepare→routing→reconciliation→river bed freeze→completeWithTiles`の順で走るようにした（**global route freezeはtile書出し前**で、全tileが1つの凍結routeを見る。tile単位の再導出なし。river無しrunはoverlay空でbyte不変）。`RiverBedMaterializationV2`（`river-bed-materialization-v1`、`core.v2.export`）がbounded river bed field（channel／bank AABBのみ確保）をfreezeし、列ごとに`CARVE_SOLID`（`[bedY+1, surfaceY]`をvoid化。solidのみ除去しfluidを作らない）→`ADD_FLUID`（そのvoid内側`[bedY+1, waterSurfaceY]`へwater。fluid所有はfill側のみでsolidを置換しない）の順で適用する。bed blockとその下はcoastal resolver所有のまま（bed再ライニングなし＝実測`MATERIAL`は空）。宣言interactionは`SOURCE_TERMINUS`（実体化bed＝reconcile済みbed、乖離は`v2.river.route-not-frozen`）／`MOUTH_TERMINUS`（v1は内陸終端、海へのjunctionは`v2.river.marine-contact`で拒否）／`BANK_ENVELOPE`（外周はcarve・fill対象外でwater surface以上、`v2.river.leak-envelope`）／`COASTAL_FOUNDATION`（macro foundation背景のみ、modifier所有cellは`v2.river.coastal-owner-conflict`）／`MACRO_MEDIUM`（land-water mediumはHARD mask所有のまま。ADR 0038）で、加えて`v2.river.vertical-bounds`／`v2.river.materialization-budget`（MEDIUM上限1024²cell、AABB境界到達で拒否）を持つ。riverはland-water fieldを書かないためowner gate／EDGE／coastal conformance測定は不変。実測（公開Releaseのfinal tile stream、baseline `harbor-cove-64-honored`比）は`changedCells=575`（`SOLID_SHAPE` 458／`FLUID` 117／`MATERIAL` 0）で、宣言effect class`{SOLID_SHAPE, FLUID}`と完全一致。`RiverBlockConformanceV2`（test scope）がbed depth（全channel列＝bed solid＋宣言water depth 1 block＋上方空）、water continuity（XZ 4連結で単一water body）、source→mouth reachability、leak envelope（`leakCells=0`、外周列はwater surface高で全solid）をfinal tile streamだけから測る。plan-only metric（`hydrology/validation.json`）はportfolioの別欄のままで代替不可。fixtureは`harbor-cove-64-honored-river`のreachを短縮（3点目`(0.17,0.20)`→`(0.16,0.15)`。従来reachはbeach backshore bandへ食い込み`coastal-owner-conflict`で拒否される）し、`harbor-cove-64-honored-meander`（同一geometryを`MEANDERING_RIVER` kindで宣言）を追加した。`public-dispatch-reachability-v1`の`RIVER`／`MEANDERING_RIVER`を`PLAN_ONLY`→`MATERIALIZED`へ移し[registry](current-feature-state-machine-registry.md)のCI検査済みprojectionを更新（`e092326b…`→`0516e59c…`）。**両kindの`paper_apply`は`EXPERIMENTAL`のまま**で、昇格はV2-17の実機証跡のみ。新Schema・新capability・新artifact format無し、coastal既存Release（`harbor-cove-64-honored`／`coastal-honored-400`）・sealed catalog・v1 goldenは不変。full `./gradlew test`／`./gradlew build` PASS。証跡は [V2-19-05 audit](audits/v2-19-05-river-block-materialization.md) |
| `V2-19-06` | 04 | HEIGHT_GUIDE macro foundation consumer。ADR 0038 D2-2の範囲内でHEIGHT_GUIDEをbackground elevation source化（medium base levelはfallback、優先順位・no-data・out-of-contract拒否・resampling・budget明示）、surface exportの「constraint map正確に1枚」制約をrole別（LAND_WATER_MASK必須1＋HEIGHT_GUIDE任意1）へ緩和、desired／actual／residual heightを入力guideへ束縛しconformanceのRasterResidualを有効化 | **完了（2026-07-23）**。D2-2の明示source (a) の実装であり**ADR amendment不要**（範囲内）、決定性・legacy byte不変 — いずれも充足。`MacroFoundationV2.HeightGuideV2`がbackground ownerのelevationを cellごとに guide＞`foundationBaseLevels` の優先順位で決め（逆転・blend無し）、guideのno-data cellだけがmedium別base levelへfallbackする。mediumは常にmask由来でguideは寄与しない。out-of-contractはclampせず拒否（主gateは既存のrequest宣言時検査「height encoding output is outside request bounds」、per-cellの`v2.foundation.height-guide-out-of-contract`はdefence in depth）。resamplingは既存canonical登録（`NEAREST`／`BILINEAR_FIXED`）のままで新規補間規則なし、budgetは既存`ConstraintMapDecodeLimits.defaults()`＋request `constraintMapBudget`の再利用でhard-code追加なし（ADR 0038 D6）。cardinalityは`MacroFoundationStageV2.FoundationInputRolesV2`が role別（`LAND_WATER_MASK` ちょうど1・HARD、`HEIGHT_GUIDE` 0..1、`ZONE_LABEL_MAP`はconsumer不在のため受理せず拒否）へ緩和し、加えて「宣言済みsource集合＝consume済みsource集合」を要求する（公開Releaseの`verifyIntentBindings`が両者一致を検査するため）。guideが在るrunだけ`constraint.coast.height.{desired,actual,residual}`（I32、`actual - desired`、no-dataは`Integer.MIN_VALUE`）を追加し、`coastal.height.residual-max`は構造的0の自己参照から実測へ変わる。`ConformanceTargetSetV2`が`HEIGHT_GUIDE`にもdesired raster target（field id `intent.height-guide`）を作り`RasterResidual`が算出される（`ZONE_LABEL_MAP`は対象外のまま）。modifier所有cellの高さはmodifierのもので（ADR 0038 D5-3）、HARD guideがtolerance超で同じcellを指定した場合はHARD同士の矛盾として`v2.foundation.height-guide-modifier-conflict`でexport拒否、SOFT guideは差をresidualへ記録する。fixture `harbor-cove-64-honored-guided`（mask byte同一、4段terrace＋南西4×4のno-data patch、SOFT binding）をportfolio常設caseへ追加し、公開sidecarから background 3122 cellのguide厳密再現・no-data 16 cellのfallback・modifier所有955 cellのみ差分、2件の`RasterResidual`（land-water 4096/0、height 4080/955）、unguided baselineとのblock diff （changed 13289＝solid 3103／fluid 4940／material 5246）を実測した。実CLI E2Eは extract→promote→`constraint-source`×2→`intent bind`×2（`generatorConsumer: MACRO_FOUNDATION`）→`v2 export`（`placementEligible: true`）。**guide無しrequestのbyteは不変**（V2-19-06直前treeで実測したmanifest checksum `9832cd4d…`をtestがpin）。新Schema・新capability・新artifact format無し、Release format・既存coastal Release・v1 goldenは不変。full `./gradlew test`／`./gradlew build` PASS。証跡は [V2-19-06 audit](audits/v2-19-06-height-guide-macro-foundation-consumer.md) |
| `V2-19-07` | 01,06 | Foundation producer tier実装＋PLAIN vertical slice。`MacroFoundationV2.ProducerLayer`（現状型のみ・構築箇所ゼロ）へ既存`PlainGeneratorV2`を最初のproducerとして接続し、candidate→effective（ADR 0038 D1）→surface field→block resolver→conformance→public dispatchまで一気通貫。foundation adapter（test-only）の到達可能性もここで裁定 | **完了（2026-07-24）**。1 Task 1 kind（`PLAIN`のみ）、producer overlap／replacement／modifier interactionのnegative fixture — いずれも充足。`MacroFoundationStageV2`が宣言featureからproducer layerを**feature id昇順**で構築し（`WIRED_PRODUCER_KINDS`＝`{PLAIN}`。plan compileは既存`PlainPlanCompilerV2`＋seal、raster化は既存`PlainGeneratorV2`で、**新generatorなし**）、`MacroFoundationV2`が footprint内でbackgroundを置換してmedium＝LAND・elevation＝`waterLevel + baseElevation + microRelief`（**datumはrequest water level**という単一の明示規則。ADR 0037 offline adapterの値ごとの推測＋clampとは異なり契約外は拒否）を確定する。kernel不変条件はproducerが所有する全cellで生成時に評価され、`UNDECLARED_OVERLAP`（footprint重複）／`v2.foundation.producer-mask-medium-conflict`（HARD maskが正本、ADR 0038 D2-3）／`v2.foundation.producer-elevation-out-of-contract`（clampなし）／`v2.foundation.height-guide-producer-conflict`（HARD guideとのHARD同士矛盾。SOFTはproducerへ譲りresidual化）でfail closedする。modifier interactionはADR 0038 D5-3どおり「modifier所有cellの高さはmodifierのもの」を維持し（実測: modifier所有列の変化0）、`CoastalSurfaceFieldsV2`のfoundation owner indexをper-cell化してbackgroundとproducerを区別した。公開到達性はADR 0039 Candidate Aのコピー: `LandformPlainModuleV2`（V2-9-02）をbuilt-in catalogへ登録（dedicated 17→18、stage `generate.foundation-plain`、4 field descriptor＋Schema enum `FOUNDATION_PLAIN_*`追加）、`PLAIN`の`intent_compile`／`export`をPARTIAL→SUPPORTED（ADR 0039 Decision A-2の同一leaf昇格）、coastal surface pipelineへ`OFFLINE_PRODUCTION` route追加（registry checksum `2fdb87e6…`→`182f713e…`）、`public-dispatch-reachability-v1`で`PLAIN`＝`OFFLINE_PRODUCTION`＋`MATERIALIZED`（projection `0516e59c…`→`7d46e315…`）。**`PRODUCTION_CONNECTED`の意味・Paper `SUPPORTED` exact setは不変、`PLAIN`のPaper 5列はUNSUPPORTEDのまま、`standalone_usage`はPARTIALのまま**（coastal 4種必須の緩和は`V2-19-09`。PLAIN単独intentが拒否されることをtestで固定）。fixture `harbor-cove-64-honored-plain`（mask byte同一＋内陸PLAIN 1件）をportfolio常設caseへ追加し、baselineとのblock diff changed 1238（`SOLID_SHAPE` 713／`MATERIAL` 525／`FLUID` 0）＝宣言effect class完全一致、footprint 175 cell全てbackground・surface Y 57..59（宣言band）・footprint外はbase level維持を公開Releaseから実測した。**ADR 0037 foundation adapterは公開dispatchへ載せないと裁定**し（第二の`surface-2_5d` pipelineを作らない、ADR 0039凍結4）testで固定。**新ADR／amendment不要**（`PLAIN`はD4 NORMATIVE確定済み）、新capability・新artifact format無し。producer無しrequestのterrain semantic checksumは直前treeの実測値と一致（tile `20318e6c…`）、容器byte（blueprint／manifest checksum）は変化（ADR 0038 D9）。full `./gradlew test`／`./gradlew build` PASS。証跡は [V2-19-07 audit](audits/v2-19-07-foundation-producer-tier.md)。**非Scope:** HILL_RANGE／MOUNTAIN_RANGE／VALLEYの配線（V2-15-22／17／23が本機構の上で行う）、coastal 4種必須の緩和、material／paletteのblock反映（`V2-19-10`） |
| `V2-19-08` | — | Design-time support lint（report-only先行）。provider呼出し前のreachable kind／capability集合提示、provider出力後のdispatch dry-run、design auditへの`NON_GATING`記録とCLI／Paper designサマリ表示 | historic 60-kind Schemaは狭めない。fail-closed化は別途人間承認 |
| `V2-19-09` | 01 | Coastal 4種必須の緩和ADR＋実装。macro foundationが背景ownerを持つ現在、`CoastalGeneratorRuntimeV2.REQUIRED`の全4種要求（V2-2期の残存制約）を見直し、部分集合（beach単体等）の許可条件・checksum影響・conformance caseを定義 | **ADR＋人間承認必須**。既存4種fixtureのchecksum取り扱いはADRで宣言。承認前に実装しない |
| `V2-19-10` | 01,07 | Material／paletteのblock反映。environment material planをcanonical block resolverへ接続し、hard-coded 11 stateをprofile-driven allowlistへ置換、environment validationの定数healthy samplerを実field読みへ是正 | semantic checksum変更はADR承認範囲で計画。任意block state・外部scriptは不許可。ZONE_LABEL→material zone接続は`V2-19-04`のbinding経由 |
| `V2-19-11` | — | Docs current-state同期。`implementation-roadmap.md`（次Task=V2-15-05表記）／`migration-plan.md`（次=V2-12-07表記）／`current-limitations.md`（Status行のV2-18-11表記）／READMEのreference image・image E2E表現をコードの接続状態へ同期し、実装状態matrixへ「公開到達性（CLI／Paper到達可否）」列を追加 | 能力を実装より先に記述しない。`V2-19-03`／`04`完了前は未接続状態を明記 |
| `V2-19-12` | 07 | Coherent detail kernel設計ADR＋実装。空間的に連続なbounded・deterministic・multi-scale detail modifier（seed namespace／frequency／amplitude／halo／support radius／tile seam／CPU・memory budget固定）。cell-hash独立ノイズの流用はしない | **ADR＋人間承認必須**（承認前に実装しない）。erosionは別Task・同時導入禁止 |
| `V2-19-13` | — | Blueprint拡張面積縮小のADR検討。`WorldBlueprintV2`のper-kind typed plan list（17 list＋singleton群）のclosed typed plan envelope／registryへの段階集約案と、4 pipelineのexecutableKinds単一ソース化 | ADR `Proposed`著述までが本Task。実装は承認後の新ID。Schema ID・artifact type・checksumのin-place変更禁止 |
| `V2-19-14` | 06 | mask⇔feature SOFT reconcile pre-pass ADR＋実装。feature geometryをmask shorelineへ決定論的にsnapする補正（補正量tolerance超は従来どおり拒否）で、mask⇔seed束縛によるauthoring負担を軽減 | **ADR＋人間承認必須**（HARD不発明原則との整合を明文化、承認前に実装しない） |
| `V2-19-15` | — | Commit message規約。Task ID必須等の規約をAGENTS.md等の実行規約へ追加 | docs-only。既存commitの書き換えはしない |
| `V2-19-16` | 01..15 | Phase gate。全leaf再検証、materialization gate発効とV2-15再開状態の確認、full clean suite | 親Phaseを閉じる唯一のTask。**人間承認必須** |

## 20. 親Phaseを閉じる規則

`V2-2-12`、`V2-3-15`、`V2-4-15`、`V2-5-18`、`V2-6-19`、`V2-7-07`、`V2-8-08`、`V2-9-14`、`V2-10-09`、`V2-12-07`、`V2-15-47`、`V2-16-19`、`V2-17-07`、`V2-18-12`、`V2-19-16`だけが親Phaseを完了へ変更できる。V2-13／V2-14は統合監査Task未設定であり、全Task完了後も親Phaseの完了宣言には人間承認を要する（V2-13-05は条件不成立により2026-07-21に`NOT_APPLICABLE_BY_MEASUREMENT`で不実行クローズ済み）。先行Taskが完了しても、次のどれかが残れば親Phaseは `進行中` である。

- 同Phaseの未完／再open／追加Task
- feature lifecycleのvalidator／preview／corruption／budget不足
- capability strict verifierまたは直前capability回帰不足
- whole／tile／thread／locale／timezone決定性不足
- 1000角offline budget不足
- v1互換回帰
- V2-6では必要な実機smoke／対応寸法計測、またはcritical/high risk

Phase統合Taskは問題を隠すためにAcceptanceを緩めない。修正が1回のTask規模を超える場合は新IDを追加し、統合Taskを未完のまま終了する。
