# Implementation Roadmap v2

> Status: v2の詳細設計とgate。完了履歴と現在の状態は[進捗の正本](../roadmap.md)に従う。2026-07-22の[全地形カタログ監査](../audits/terrain-catalog-full-audit-2026-07-22.md)により、既存5 Track制のまま`V2-14-03`、Track E続編`V2-15`／`V2-16`、Track A follow-up `V2-17`を登録し、同日の[macro foundation監査](../audits/macro-foundation-conformance-audit-2026-07-22.md)でTrack AへV2-18（13 Task）を、2026-07-23の[横断監査](../audits/cross-cutting-audit-2026-07-23.md)でTrack AへV2-19（16 Task）を追加登録した。`V2-15-01`〜`13`、V2-18全13 Task、V2-19全16 Taskが完了している（Phase gate `V2-19-16`は2026-07-24の人間承認で完了し親Phaseを閉じた。[audit](audits/v2-19-16-phase-gate.md)）。`V2-15-11`はLAKE基本を配線し、oxbow cutoff subtypeは新ID`V2-15-48`へ分離した（総Task数47→48）。`V2-15-12`はCANYON基本を配線し、着手中に発見した`HardPreflightGateV2`のOFFLINE_PRODUCTION relation誤検出gapも是正した。`V2-15-13`はWATERFALLのsurface側（plunge basin）を配線し、`WATERFALL_VOLUME` overlayとその前提であるenvironment field stackの実測化を新ID`V2-15-49`／`V2-15-50`へ分離した（総Task数48→50）。Track AはV2-17（`V2-15-47`／`V2-16-19`完了待ち）を残すのみで、Track Eの次Taskは`V2-15-14`である。

## 1. Roadmapの読み方

このroadmapはV2-0〜V2-19の依存関係と親Phase gateを示す。日付や完了を約束せず、各Phaseは前提gateを満たすまで能力をsupportedとしない。実行単位は [Task Index](task-index.md)、運用規則は [Task Execution Guide](task-execution-guide.md)、モデル割当は [Model Assignment](model-assignment.md) を正本とする。現行Beta hardeningのrelease checklist未完了を消さない。`V2-6-16`／`V2-6-17`は無効化済み。現行地形inventoryは2026-07-22監査、2026-07-18 Gap監査は履歴資料として扱う。

```text
Track A（コア地形）:
V2-0 Compatibility spine
  → V2-1 Constraint maps / compiled fields
  → V2-2 Coastal 2.5D vertical slice
  → V2-3 Hydrology / regional landforms
  → V2-4 Geology / climate / ecology
  → V2-5 Sparse local volume
  → V2-6 Release 2 placement / hardening / capability catalog
  → V2-11 Capability promotion → V2-12 V2 production path
  → V2-18 Macro foundation / intent conformance
  → V2-19 Input integrity / block materialization
  → V2-17 Paper placement evidence / promotion（V2-15 / V2-16 gate後）

Track B（画像忠実性、前提はV2-1のみ）:
V2-7 Image fidelity: 抽出core → secure envelope → draft artifact/preview
  → 明示昇格 → height/zone抽出 → 多入力競合解決 / Phase gate
  → V2-14 wiring follow-up → V2-14-03 current-state docs sync

Track C（スケール、Task単位で前提宣言）:
V2-8 Scale-up: scale契約 → 寸法policy統一 → LARGE予算 → coarse計画/hydrology
  → streaming/resume/部分再生成 → preview pyramid → export分割 → LARGE gate

Track D／E（地形拡張）:
V2-9 Terrain foundation → V2-10 Deferred terrain families
  → V2-15 canonical registry / existing-generator public wiring
  → V2-16 deferred / new terrain / composition
```

Track間は依存を明示したTaskだけが待ち、それ以外は並行実行できる。同一ファイルの同時編集は禁止し、共有領域（`format.v2.release`等）を変更するTaskは直列にする。

## 2. 共通Definition of Done

各Phaseは次を満たす。

- production code、Schema、docs、example、roadmapの実態が一致する。
- compile、対象test、`./gradlew build` が成功する。
- pure Java logicにunit／negative／determinism testがある。
- whole/tile、seam、seed、cancel、budgetを対象範囲で検証する。
- secret、raw Provider payload、生成temporary artifactがGit差分へ混入しない。
- model/generatorのpackage境界とPaper thread規則を破らない。
- unsupported featureをREADMEで実装済みと表現しない。
- 新Release／配置変更はstrict verifier、rollback、脅威分析を伴う。

## 3. V2-0: Compatibility spine and diagnostic compiler

### 目的

最初に実装すべき最小Phaseである。地形結果を変えず、v1を壊さず、v2の契約とmodule/field境界をend-to-endで通す。

### Scope

- v1/v2のversion dispatch骨格
- TerrainIntent v2の最小contract: ID、POINT/MULTI_POINT/SPLINE/MULTI_SPLINE/POLYGON/VOLUME_GUIDE、stable point/path/endpoint、relation、hard/soft constraint
- WorldBlueprint v2のidentity、space、module、field descriptor、named seed、budget
- compile-time built-in module catalog
- ValidationTarget、MetricResult、diagnostic issueの最小型
- `TerrainQuery`／`TerrainBlockResolver` interfaceとv1 column adapter
- canonical JSON、checksum、unknown version拒否
- 1個のdiagnostic-only coastal fixture

### 明示的な非Scope

- 新しい海岸形状、hydrology、3D、素材、Release 2、Paper配置
- dynamic module/plugin、preset import
- v1 artifactの書換え

### Acceptance gate

- v1全golden checksumとblock streamが変更前と一致する。
- diagnostic coastal fixtureがparse→normalize→Blueprint diagnostic compile→round-tripできる。
- 残り10 scenario例はcontract parse/round-tripでき、未実装feature kindをfallbackせずcapability diagnosticとして返す。
- duplicate ID、hard conflict、未知module、field owner cycle、budget超過を拒否する。
- named seedがmodule登録順、thread、locale、timezoneで不変である。
- v1 adapter経由のresolver出力が現行column materializerと全block一致する。
- v2 outputはdiagnostic temporary artifactに限り、Release 1へ混入しない。

このPhaseが小さい理由は、新地形を作る前にversion、determinism、field ownership、3D query adapterという後戻りしにくい境界だけを検証するためである。

### 実装状態

V2-0 gateは完了した。exact v1/v2 dispatch、TerrainIntent v2／WorldBlueprint v2、strict Schema、millionths canonical JSON、compile-time catalog、named seed、diagnostic compilerに加え、pure `TerrainQuery`／`TerrainBlockResolver`とv1 adapterを実装した。adapterはschema 1／generator `3.0.0-phase6`だけを受け、現行materializerとの全XYZ block一致、whole／tile／tile順／thread数／locale／timezone不変を固定する。Release 1、placement、Undoへv2型は接続していない。

## 4. V2-1: Direct constraint maps and field store

### Scope

- reference imageとconstraint mapのRequest契約分離
- `LAND_WATER_MASK`、`HEIGHT_GUIDE`、`ZONE_LABEL_MAP` の3 role
- categorical U8／U16とheight U8／U16のnumeric decoder、coordinate mapping、fixed-point canonicalization
- compact field sidecar formatとbounded window reader
- desired/actual/residual field
- version固定の8枚map diagnostic PNG bundle
- AIを介さないmanual generation path

### Acceptance gate

- 8/16-bit、label、no-data、rotation/flip/cropのgolden fixture。
- hard land-waterはblock量子化後もtolerance内、soft guideはresidualを報告する。
- malformed image、unknown label、過大decode、path/link/TOCTOUを拒否する。
- 1000角のstage peak memoryが事前budget内で、cancel時にcanonical artifactを公開しない。
- whole/tile samplingが一致する。
- 全artifactとsealed indexのstrict read-back後、最後のcancel checkとbundle atomic moveの間に処理を挟まない。atomic move後はcommit済みとし、late cancelで削除しない。

### Deliverable scenario

mask＋height guideから作る単純なcoast／island preview。まだspecialized coast generatorをsupportedとしない。

### 実装状態

V2-1 gateは完了した。`GenerationRequestV2`、3 roleのstrict binding、grayscale non-interlaced PNGのcategorical／height U8／U16 numeric decoder、clockwise quarter-turn→flip X/Z→crop→pixel-center mapping、no-data、label table、3種類を明示するheight意味、integer fixed-point samplingを実装した。canonical fieldはADR 0011の非圧縮`LFC_GRID_V1`へstream書込みし、artifact／semantic checksumとsource provenanceを持つsealed indexからbounded windowで読む。manual pathはAIを呼ばずdesired／actual／residual fieldと固定8 diagnostic PNGを一つのstaging directoryからatomic publishする。hard land-waterはactualへ完全copyし、soft heightはwhole-block量子化差をresidualに残す。

security／corruption testはmalformed PNG、format／sample mismatch、unknown label、invalid no-data、dimension／aspect mismatch、absolute／traversal、symlink、request内hardlink alias、TOCTOU、byte／pixel／decode／resident／artifact budget、future version、source／artifact／index checksum改変、hard conflict、cancel cleanupを拒否する。trusted ceilingはmap 32、source 1枚8 MiB／合計32 MiB、1枚4,000,000 pixel／合計16,000,000 pixel、decoded 1枚8 MiB／合計32 MiB、decoder working 32 MiB、artifact 64 MiB、resident 96 MiBで、Requestから拡張できない。1000×1000 manual fixture、whole／tile、seam、tile／thread順、locale／timezoneもAcceptance内で検証した。bundle atomic moveをcommit pointとし、move前のcancel／failureだけをcleanupする。V2-2の専用coast generator、Release 2、Paper配置は未実装である。

## 5. Phase Task構造

詳細なScope、非Scope、変更面、test、D/M/S条件、Acceptance、停止条件は [Task Index](task-index.md) を正本とする。実行規則と短い共通プロンプトは [Task Execution Guide](task-execution-guide.md) を参照する。Taskを1個完了しても親Phaseは完了しない。

| Phase | Task数 | 主な直列chain | Phase gate task |
|---|---:|---|---|
| V2-2 | 12 | coastal基盤→4 feature→transition→validator/preview→offline schematic→Release 2 core/capability | `V2-2-12` |
| V2-3 | 15 | Hydrology IR/solve→river/lake/canyon/waterfall/delta/tidal/fjord/mountain/volcano→reconcile→diagnostic/capability | `V2-3-15` |
| V2-4 | 15 | geology→lithology/strata→climate/water/snow→material/palette→mangrove/coral/ecology→diagnostic/capability | `V2-4-15` |
| V2-5 | 18 | SDF→CSG→AABB/cache/query→volume feature単位→post-environment→diagnostic/export/capability | `V2-5-18` |
| V2-6 | 21 | placement contract→envelope/reservation/snapshot/containment→apply/verify/rollback/Undo/Recovery→hardening→strict source/lifecycle→smoke/catalog→RC audit | `V2-6-19` |
| V2-7 | 7 | 抽出core→secure envelope→draft/preview→明示昇格→height/zone抽出→競合解決/gate | `V2-7-07` |
| V2-8 | 8 | scale契約→寸法policy統一→LARGE予算→coarse計画→streaming/resume→preview pyramid→export分割→gate | `V2-8-08` |
| V2-9 | 14 | foundation contract→surface/coast/island/marine→surface-volume→macro/river graph→gate | `V2-9-14` |
| V2-10 | 11 | glacial→karst→advanced marine/river contract→dry land/volume/island→SPRING/OXBOW slices→gate | `V2-10-09` |
| V2-11 | 6 | Paper capability→dimension guard→docs/Schema→500/1000実測→dimension promotion | — |
| V2-12 | 11 | governance→production path→migration→v1 drain→phase gate→follow-up | `V2-12-07` |
| V2-13 | 6 | instrumentation→1024 route→offline/FAWE実測→条件付き最適化→slice較正 | —（人間承認） |
| V2-14 | 3 | extract wiring→reference role→current-state docs sync | —（人間承認） |
| V2-15 | 47 | inventory→canonical registry／dispatch spine→family別public wiring→gate | `V2-15-47` |
| V2-16 | 19 | composition engine→preset／deferred／new terrain→gate | `V2-16-19` |
| V2-17 | 7 | measurement harness→runtime別実機matrix→evidence-bound promotion→gate | `V2-17-07` |
| V2-18 | 13 | 診断gate正常化→coverage report-only→HARD preflight→target-driven validator→map binding→ConformanceTargetSet→foundation ADR→spine→owner gate→E2E portfolio→fixture是正→Phase gate | `V2-18-12` |
| V2-19 | 16 | materialization gate→Gradle input→画像design修理→binding authoring→RIVER実体化→HEIGHT_GUIDE consumer→producer tier→lint→coastal緩和→material palette→detail kernel ADR→Blueprint ADR→SOFT reconcile ADR→commit規約→Phase gate | `V2-19-16` |

V2-7の設計正本は [image-constraint-maps.md](image-constraint-maps.md) と [ADR 0017](../adr/0017-deterministic-image-mask-extraction.md)、V2-8の設計正本は [scale-and-streaming.md](scale-and-streaming.md) と [ADR 0016](../adr/0016-scale-classes-and-execution-planning.md) である。

## 6. V2-2: Coastal 2.5D vertical slice

`V2-2-01`から`V2-2-12`まで完了した。`V2-2-01`〜`V2-2-07`はcoastal field、4 feature、transition compositor、`V2-2-08`は独立validator／strict preview、`V2-2-09`はstreaming offline tile schematicとWorldEdit read-back、`V2-2-10`はempty-capability Release format 2 core、`V2-2-11`はrequest／intent／Blueprint、field、validation、preview、tileのexact `surface-2_5d` artifact setを完成した。`V2-2-12`はAzure Coast統合fixtureと全portfolioを再検証し、親Phase gateを閉じた。

Phase gateはAzure Coast統合fixtureでbeach width、harbor depth/opening、cape rock/complexity、whole/tile block stream、Sponge palette 127/128、WorldEdit offline read-back、Release 2 strict directory/ZIP、Release 1回帰を再検証した`V2-2-12`が閉じた。4 coastal kindとoffline `surface-2_5d`は`SUPPORTED`である。Paper applyは有効化していない。

## 7. V2-3: Global hydrology and regional landforms

`V2-3-01`〜`V2-3-15`は完了した。Hydrology IR／fixed priorとglobal basin/routing solverに加え、RIVER／MEANDERING_RIVER、独立LAKE、CANYON＋shared river skeleton、WATERFALL 2.5D lip／base／plunge、DELTA distributary DAG／fan／sandbar／shallow sea、TIDAL_CHANNEL_NETWORK bidirectional marine graph、FJORD glacial U／sidewall／marine outlet、ALPINE／GLACIAL mountain ridge skeleton、VOLCANIC_ARCHIPELAGO island／caldera／radial skeleton、固定3 passのbounded reconciliation、独立validator／preview、Release 2 `hydrology-plan`（`surface-2_5d`依存）を持つ。統合監査により、offlineでfull completionしたriver／lake／canyon／delta／tidal／fjordと`hydrology-plan`を`SUPPORTED`とした。

Phase gateはsource-mouth reachability、bed monotonicity、confluence flow、lake spill、delta branch、fjord marine/U profile、waterfall graph、graph/field budget、tile/thread/candidate決定性、`hydrology-plan` strict capabilityを統合した`V2-3-15`が閉じた。その時点でdeferしたmountain environmentとvolcanic materialはV2-4 gateで、waterfall volumeはV2-5 gateで完成した。V2-4 priorへの切替はgenerator/capability version変更として扱う。

## 8. V2-4: Geology, climate, ecology, semantic material

`V2-4-01`から`V2-4-15`までを完了した。`V2-4-01`〜`V2-4-03`はtyped geology plan、fixed lithology catalog、ordered strata profile／derived scalarを、`V2-4-04`はcoarse climate prior、final temperature／moisture、Hydrology runoff-prior version transitionを、`V2-4-05`はregional wetness／salinity／hydroperiodを、`V2-4-06`はsnow potential／cover fieldを、`V2-4-07`はgeology／strata／water-condition／snowを閉じたcatalogとfixed-order ruleで結合するMinecraft非依存のsemantic material profileを、`V2-4-08`はsemantic class→Minecraft 1.21.11 block stateの閉じたpalette adapter（ADR 0018）を、`V2-4-09`は`MANGROVE_WETLAND` regional shapingとStage 6 `MANGROVE_TIDAL_LINK`を、`V2-4-10`は`CORAL_REEF` regional bathymetryとStage 6 `REEF_LAGOON_PASS`を、`V2-4-11`はsparse ecology placement（habitat／assemblage／density-spacing）を、`V2-4-12`はvolcanic／canyon feature material overlayを、`V2-4-13`は独立environment validator／10-layer diagnostic previewを、`V2-4-14`はRelease 2 `environment-fields` capabilityを実装した。`V2-4-15`は5 scenario、決定性／resource／cancel、tampering、v1／Release 1／V2-2／V2-3回帰を統合監査し、対象featureと`environment-fields`をoffline `SUPPORTED`へ昇格した。

`MANGROVE_WETLAND`と`CORAL_REEF`のregional shapingはStage 5、固定回数reconciliationはStage 6、environment/ecology/materialはStage 8/10の責務を保つ。Phase gateはsnow/mangrove/coral/volcanic/canyon metric、palette read-back、descriptor/memory budget、`environment-fields` strict capabilityを統合した`V2-4-15`が閉じた。V2-5はSDF／CSG／AABB index／tile cache／TerrainQuery volume／局所volume feature／waterfall volume／post-volume local environment／volume validators／5-layer preview／offline 3D read-back／`sparse-volume` capabilityまで完了し、`V2-5-18`でPhase gateを閉じた。

## 9. V2-5: Sparse local volumetric terrain

`V2-5-01`から`V2-5-18`まで完了した。SDF、ordered CSG、AABB index、3D tile cache、TerrainQuery volume対応を別Taskで固定してから、cave、lush cave、underground lake、sea cave、overhang、natural arch、sky island、waterfall volumeを追加した。

Phase gateはconnectivity、roof/support/clearance、fluid continuity、post-volume environment、3D whole/tile/XYZ seam、dense allocation禁止、general VarInt/palette、offline read-back、`sparse-volume` strict capabilityを統合した`V2-5-18`だけが閉じる。`V2-5-18`は2026-07-18に完了し、volume infrastructure・7 volume feature・waterfall volume・local environment・validator/preview・offline export・`sparse-volume`と、deferredだった`WATERFALL` kind／moduleをoffline `SUPPORTED`へ昇格した（[Volume監査](audits/v2-5-phase-gate.md)）。VOLUME_GUIDE intent kindはdiagnostic-onlyのままである。Paper applyはこのgate完了後のV2-6で開始する。

## 10. V2-6: Placement, hardening, and supported catalog

`V2-6-01`は完了した。Release 2 placement plan／journal契約（ADR 0020）をv1 journalと分離して固定し、target／capability／tile order／envelope参照／reservation-confirmation binding／operation IDとformat 2 journal statesをchecksum-boundなimmutable契約にした。`V2-6-02`はmutation／effect envelope（ADR 0021）を追加し、per-tile mutation AABBとphysics-policy based union effect envelope、overflow-safe bounds、disk／volume admission、plan bindingを固定した。`V2-6-03`はregion／disk reservationとactor-bound one-time confirmation（ADR 0022）を、`V2-6-04`はsnapshot-all（world gateway read契約、canonical envelope順のsnapshot file／sealed index、staging→strict read-back→atomic publish、`SNAPSHOT_COMPLETE`＝apply-ready、失敗時canonical partialなし）を、`V2-6-05`はfluid／gravity／neighbor containment preflight（ADR 0023、閉じたphysics catalog、`CONTAINED` evidenceのみseal）を固定した。`V2-6-06`はfeature-neutral canonical streamのbounded apply transaction（ADR 0024）、strict prerequisite chain、明示solid→air carve→fluid／overlay順、Paper scheduler／WorldEdit close receipt、late completion reconciliation、atomic `APPLYING`／canonical tile prefix checkpointを実装した。

`V2-6-08`から`V2-6-19`までは完了した。rollback、Undo、Recoveryはそれぞれ別Taskで実装済みである。`V2-6-11`〜`V2-6-15`は完了（WE／FAWE smoke evidenceあり）、`V2-6-19`はRC auditとしてV2-6 Phase gateを閉じた。次のTrack A Taskは`V2-11-01`（capability promotion）。

WorldEdit/FAWE smokeと実world計測は指定環境がなければ`BLOCKED_EXTERNAL`であり、mockやoffline testで完了扱いにしない。500角／1000角は各計測Taskが成功した寸法だけをsupported catalogへ載せる。Phase gateは全必須Task、v1回帰、security/performance evidence、supported catalogを監査する`V2-6-19`だけが閉じる。Beta hardeningの未完項目は別trackとして残す。

### V2-3〜V2-5 capability共通gate

`hydrology-plan`、`environment-fields`、`sparse-volume` はそれぞれ専用Taskでのみ有効化する。直前PhaseのRelease 2を引き続きstrict verifyし、capability artifactの欠損／追加／version改ざん、未知capabilityをdirectory/ZIP双方で拒否する。予約名だけをmanifestへ出さない。

## 10a. V2-9: Terrain foundation expansion

V2-9は`V2-6-01`／`V2-6-02`のplacement契約と`V2-8-01`の`ScaleProfileV2`／admissionを先頭contractの前提とし、`V2-6-19` Phase gateと`V2-8-02`寸法統一の完了は待たない。`PLAIN`／`HILL_RANGE`、`MOUNTAIN_RANGE`／`VALLEY`、一般`RIVER`、`FLOODPLAIN`／`MARSH`、`ROCKY_COAST`／`SEA_CLIFF`、island/cone、ocean basin/shelf/slope/submarine canyon、`CAVE_ENTRANCE`／`UNDERGROUND_RIVER`、macro land-water topology、river graph roleを共通contractとvertical sliceへ分割する。

各sliceはoffline compile/generate/validation/preview/exportを閉じるが、PaperはV2-6のcanonical surface/solid/air/fluid streamを利用しFeature別adapterを作らない。`LAGOON`／`REEF_PASS`／caldera／lava flowのchild限定状態、volume plan-levelとpublic Intentの差を能力別catalogで維持する。Phase gateは`V2-9-14`だけが閉じる。

## 10b. V2-10: Deferred terrain families

V2-10はV2-9 gate後に、glacial ice/deposition、karst drainage graph、additional marine、advanced river/lake、escarpment/dry land、lava tube、advanced island/reefを段階化する。profile、component、presetをFeatureKindへ昇格させず、独立ownershipとvalidatorを証明できるvertical sliceだけを実装対象にする。Phase gateは`V2-10-09`だけが閉じる（deps: 01〜08＋10＋11）。`V2-10-01`〜`V2-10-04`でglacial／karst／additional-marineのEXPERIMENTAL slicesを閉じ、`V2-10-05`でadvanced river／lakeのcontract分割をfreezeし（first slices `SPRING`→`V2-10-10`、`OXBOW_LAKE`→`V2-10-11`、他5種deferred）、`V2-10-06`で`ESCARPMENT`／`PLATEAU` sliceとdry-land modifier contractを閉じ、`V2-10-07`で`LAVA_TUBE` swept-tunnel sliceを閉じ、`V2-10-08`で`BARRIER_ISLAND`／`ATOLL` COMPOSITE_PRESETとadvanced island/reef catalog contractを閉じ、`V2-10-10`でsurface `SPRING` graph-node sliceを閉じ、`V2-10-11`で`OXBOW_LAKE` reach-cutoff basin sliceを閉じ、Task数を11へ拡張した。`V2-10-09`のPhase gate（`DeferredTerrainPhaseGateV2Test`＋full suite、[V2-10 Phase gate audit](audits/v2-10-phase-gate.md)）で全11 Taskを完了し、completed sliceのoffline plan-level generate／validationを`SUPPORTED`（previewはpreview index証拠のあるsliceのみ`SUPPORTED`、`MORAINE_FIELD`／`OUTWASH_PLAIN`は`EXPERIMENTAL`のまま）、intent／standalone／exportを`PARTIAL`、Paper以降を`UNSUPPORTED`とした。preset／profile／graph role／deferred候補は昇格していない。

## 11. Scenario coverage matrix

| Required scenario | 最初に表現 | Regional／semantic completion | Full completion |
|---|---|---|---|
| beach＋breakwater＋rockycape | V2-0 Intent例 | V2-2 | V2-2 |
| fjord | V2-0 Intent例 | V2-3 | V2-3 |
| delta | V2-0 Intent例 | V2-3 | V2-3 |
| volcanic archipelago | V2-0 Intent例 | V2-3骨格、V2-4素材 | V2-4 |
| canyon＋waterfall | V2-0 Intent例 | V2-3 graph／2.5D骨格 | V2-5 water column／滝裏volume |
| mangrove wetland | V2-0 Intent例 | V2-3 tidal graph、V2-4地形／環境 | V2-4 |
| snowy mountains | V2-0 Intent例 | V2-4 | V2-4 |
| coral reef | V2-0 Intent例 | V2-4 bathymetry／環境 | V2-4 |
| cave／lush cave | V2-0 Intent例 | V2-5 | V2-5 |
| overhang | V2-0 Intent例 | V2-5 | V2-5 |
| sky islands | V2-0 Intent例 | V2-5 | V2-5 |
| 通常画像から地形 | V2-1 draft/soft map | V2-2以降のsupported kindに限定 | 対象kindのcompletion Phase |

「Intentで表現可能」と「generatorがsupported」は別の状態として表示する。

## 12. Test portfolio

### Contract

- v1/v2 Schema、canonical JSON、unknown version、migration warning
- geometry、relation、constraint、module descriptor、field artifact

### Pure unit / property / metamorphic

- fixed-point、geometry raster、distance、SDF、graph、merge operator
- rounding、overflow、stable reduction、deterministic math kernelのgolden fixture
- seed、tile order、thread、locale、timezone、default charset independence
- inputを平行移動／反転したときの期待変換
- map解像度だけを上げたときのtolerance内一致

### Feature fixture

- 11 required scenarioのpositive fixture
- 各validatorを壊すnegative／corruption fixture
- feature interactionとtransition fixture

### Security / format

- image/map path、link、TOCTOU、bomb、unknown label
- field／Release／ZIP path、checksum、extra/missing、allocation上限
- Sponge palette/VarInt、WorldEdit read-back

### Runtime / placement

- bounded executor saturation、cancel、shutdown、resume
- `retained + parallelism × per-task peak + cache/decode/queue overhead` に基づくadmissionと同時stage上限
- snapshot/apply/verify failure、disk不足、overlap、Undo、Recovery
- Paper Scheduler main-thread assertion
- WorldEdit／FAWE smoke、500/1000実測

## 13. Parallel workstreamとmerge gate

同じTaskのScope内で安全に分担できるworkstream:

- Contract/Schema/compiler
- field storage/image decoder
- module generator/validator fixture
- Release/preview format
- Paper placement gateway

ただし、1回の作業はTask indexの1 Taskだけを完了し、後続Taskを並行実装しない。次のmerge gateは直列にする。

1. contractとcanonicalization確定前にgenerator APIを固定しない。
2. field ownershipとseed規則確定前に複数moduleを追加しない。
3. Release 2 self-verify前にPaper applyを実装しない。
4. offline 3D exportとcorruption validation前にvolume placementを有効化しない。
5. snapshot-allと実機計測前にRelease 2をproduction-readyとしない。

## 14. V2-0／V2-1の実装結果

V2-0全体より小さい最初のchange setとして、次を実装した。

```text
TerrainIntentV2の最小record/Schema
+ FeatureId / POINT・MULTI_POINT・SPLINE・MULTI_SPLINE・POLYGON・VOLUME_GUIDE / stable point・path・endpoint
+ HARD・SOFT metric constraint
+ ModuleDescriptor / FieldKey
+ BlueprintCompilerV2のdiagnostic plan
+ v1 checksum不変test
```

V2-0ではAzure Coastの5 featureをmodule／field diagnosticまでcompileし、残り10 scenarioもfallbackせずround-tripする。v1出力はquery adapterを含むgolden testで固定した。V2-1ではprecision mapをAI referenceから分離し、numeric decodeからfield sidecar、constraint reconciliation、diagnostic previewまでを縦に完成した。`V2-2-01`〜`V2-5-18`で各offline Phase gateを閉じ、V2-6のplacement safety／RecoveryとV2-12のproduction pathも完了した。V2-9／V2-10は各Phase gateまで完了した（[V2-9 audit](audits/v2-9-phase-gate.md)、[V2-10 audit](audits/v2-10-phase-gate.md)）。`V2-14-03`でREADME current-stateを同期し、`V2-15-01`で全地形inventoryを現行HEADへ再照合、`V2-15-02`で[60 FeatureKindのcurrent-state projection](current-feature-state-machine-registry.md)をCI固定した。`V2-15-03`では[ADR 0036](../adr/0036-canonical-feature-identifier-disposition.md)が人間承認され、`V2-15-04`で[46-kind canonical target projection](canonical-feature-target-registry.md)と明示migrationを実装した。`V2-15-05`〜`10`はcompile-time dispatch registry、shared capability pipeline（`hydrology-plan`／`environment-fields`／`sparse-volume`）、foundation→`surface-2_5d` adapter（[ADR 0037](../adr/0037-foundation-surface-to-surface-2_5d-mapping.md)）、RIVER／MEANDERING_RIVERの最初のoffline production route（[ADR 0039](../adr/0039-offline-production-route-eligibility.md)）を追加した。`V2-15-11`は同パターンでLAKE基本（CLOSED basin）を配線・実体化し、oxbow cutoff subtype（`OXBOW_LAKE`）は関係graph束縛の別生成経路のため新ID`V2-15-48`へ分離した。`V2-15-12`は同パターンでCANYON基本（既存MEANDERING_RIVER reachへHARD WITHIN束縛、fluid非所有）を配線・実体化した。`V2-15-13`は同パターンでWATERFALLのsurface側（既存MEANDERING_RIVER reachへHARD `ON_PATH_OF`束縛、落ち口下流のplunge basinのみ）を配線・実体化し、`WATERFALL_VOLUME` overlayは`sparse-volume` prefixが含む`environment-fields` pipelineのriver拒否（`V2-19-10`の明示判断）が前提となるため`V2-15-49`／`V2-15-50`へ分離した。2026-07-22の[macro foundation監査](../audits/macro-foundation-conformance-audit-2026-07-22.md)で登録したV2-18（全13 Task）は[ADR 0038](../adr/0038-macro-foundation-contract.md)のmacro foundation契約とfoundation owner gateを実装して完了し、2026-07-23の[横断監査](../audits/cross-cutting-audit-2026-07-23.md)で登録したV2-19は`V2-19-15`まで完了した（semantic materialization gate、RIVER block materialization、`HEIGHT_GUIDE` consumer、`PLAIN` foundation producer、[ADR 0040](../adr/0040-coastal-contributor-set-cardinality.md)によるcoastal 4種必須の緩和、material／palette block反映、[ADR 0041](../adr/0041-coherent-detail-kernel.md)のcoherent detail kernel、[ADR 0042](../adr/0042-blueprint-typed-plan-envelope.md)の`Proposed`著述、[ADR 0043](../adr/0043-mask-feature-reconcile-pre-pass.md)のmask⇔feature reconcile pre-pass、`AGENTS.md`へのcommit message規約を含む）。Phase gate `V2-19-16`は2026-07-24の人間承認で完了しV2-19親Phaseを閉じた（[audit](audits/v2-19-16-phase-gate.md)）。現在の次TaskはTrack E `V2-15-14`であり、主依存順は`V2-18`→`V2-19`（Track A）／`V2-15`→`V2-16`→`V2-17`（Track E以降）である。
