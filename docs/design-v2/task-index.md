# v2 Task Index

> Status: V2-0／V2-1／V2-2／V2-3は完了し、各Phase gateを閉じた。`V2-4-01`〜`V2-4-05`を完了し、次の未完了Taskは`V2-4-06`である。V2-5〜V2-6は前提待ちである。この文書はTask ScopeとAcceptance gateの正本であり、進捗状態の正本は [docs/roadmap.md](../roadmap.md) である。

## 1. 読み方

各Taskは [Task Execution Guide](task-execution-guide.md) を継承し、記載されたTaskだけを1回の作業で閉じる。`主変更` は主対象であり、Acceptanceに必要なtest、Schema、example、ADR、docsの同期を禁止しない。`D/M/S` は決定性、memory、securityの追加条件である。すべてのTaskでv1 Schema、generator `3.0.0-phase6`、Release format 1、placement、Undoを凍結する。

| Phase | Task数 | 最初 | 親Phaseを閉じるTask |
|---|---:|---|---|
| V2-2 Coastal 2.5D | 12 | `V2-2-01` | `V2-2-12` |
| V2-3 Hydrology | 15 | `V2-3-01` | `V2-3-15` |
| V2-4 Environment | 15 | `V2-4-01` | `V2-4-15` |
| V2-5 Sparse volume | 18 | `V2-5-01` | `V2-5-18` |
| V2-6 Placement | 19 | `V2-6-01` | `V2-6-19` |

合計79 Taskである。Task追加時は既存IDを振り直さず、該当Phaseへ新しい未使用IDを追加する。

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

- **目的／前提:** height、temperature、moisture、slopeに基づくsnow coverを実装する。前提は`V2-4-04`とmountain skeleton。
- **Scope／成果物:** snow potential／cover field、snowline transition、slope/wind exposure rule、alpine scenario validator hookを実装する。
- **非Scope:** ice glacier volume、Minecraft snow block adapter、general ecology。
- **主変更:** `generator.v2.environment.snow`、environment override／constraint Schema、snow fixtures／docs。
- **必須test／D/M/S:** elevation/temp/slope transition、maxY一律でないこと、warm peak／steep face corruption、whole／tile／seam／thread不変、field/preview budget、unknown preset／out-of-bounds transition拒否。
- **Gate／docs／次／停止:** snowline metricがfield入力で説明可能かつdeterministicなら完了し、geology-climate／taxonomy／roadmapを更新して`V2-4-07`へ進める。Minecraft block解決が必要ならsemantic fieldで止める。

### V2-4-07 Semantic material profile

- **目的／前提:** geology/environment/surface classからMinecraft非依存のmaterial profileを解決する。前提は`V2-4-03`〜`V2-4-06`。
- **Scope／成果物:** substrate/rock/sediment/wetness/snow/cover semantic IDs、ordered resolution rules、profile checksum、surface/ceiling/floor hookを実装する。
- **非Scope:** Minecraft palette、feature固有volcanic/canyon rule、volume-local material。
- **主変更:** `model.v2.material`、`generator.v2.material`、profile Schema／fixtures／docs。
- **必須test／D/M/S:** rule precedence／exclusive conflict／wet-rock/sediment/snow profiles、rule／module／thread順不変、catalog/rule/cache budget、last-write-wins／unknown semantic ID／arbitrary expression拒否。
- **Gate／docs／次／停止:** semantic profileがcanonical checksumを持ちMinecraft型へ依存せずstrict解決できれば完了し、geology-climate／pipeline／roadmapを更新して`V2-4-08`へ進める。block stateをmodelへ入れる必要があれば停止する。

### V2-4-08 Minecraft palette adapter

- **目的／前提:** semantic materialをversion固定Minecraft 1.21.11 block stateへ隔離解決する。前提は`V2-4-07`とoffline schematic基盤。
- **Scope／成果物:** semantic profile→palette mapping、resolver version/checksum、fallback禁止、Sponge writer/read-back連携を実装する。
- **非Scope:** Paper apply、biome書換え、feature shaping、v1 palette変更。
- **主変更:** `format.v2.minecraft`／`worldedit` adapter、palette artifact Schema、新ADRまたはartifact docs、fixtures。
- **必須test／D/M/S:** all semantic IDs、palette 127/128境界、block-state canonicalization、whole／tile／thread不変、palette/cache/artifact budget、unknown ID／Minecraft version／checksum／future resolver拒否。
- **Gate／docs／次／停止:** offline Sponge read-backとcanonical block checksumが一致しv1 palette回帰不変なら完了し、artifact／geology-climate／roadmapを更新して`V2-4-09`へ進める。WorldEdit内部APIが必要なら停止する。

### V2-4-09 Mangrove regional shaping

- **目的／前提:** `MANGROVE_WETLAND`の低relief、microtopography、open-water gapをregional stageで実装する。前提はtidal graph、`V2-4-05`。
- **Scope／成果物:** wetland polygon、bounded elevation shaping、channel/open-water protection、mud/silt semantic substrate、Stage 6 reconciliation hookを実装する。
- **非Scope:** mangrove tree/root placement、general ecology、new tidal solver。
- **主変更:** `generator.v2.landform.mangrove`、feature/environment Schema、scenario fixtures／docs。
- **必須test／D/M/S:** relief／hydroperiod／open-water gap／marine connection、filled channel／dry wetland／hard mask conflict、whole／tile／thread不変、microtopography/reconcile budget、non-convergence拒否。
- **Gate／docs／次／停止:** shaping後もtidal hard targetsとregional metricsが通れば完了し、taxonomy／geology-climate／hydrology／roadmapを更新して`V2-4-10`へ進める。ecology placementを同時に必要とするなら分離したまま停止する。

### V2-4-10 Coral reef bathymetry

- **目的／前提:** `CORAL_REEF` crest、outer slope、lagoon bathymetry、`REEF_PASS`をregional stageで実装する。前提はcoast/hydrology、`V2-4-04`。
- **Scope／成果物:** reef ring、crest/depth profile、lagoon、pass carve、marine connectivity、Stage 6 reconciliation hookを実装する。
- **非Scope:** coral object placement、full ecology、volume reef、Paper fluid。
- **主変更:** `generator.v2.landform.reef`、reef/pass parameter／relation Schema、scenario fixtures／docs。
- **必須test／D/M/S:** crest／outer slope／lagoon depth／pass count、sealed lagoon／deep reef／broken pass、whole／tile／seam／thread不変、bathymetry/reconcile budget、hard marine conflict拒否。
- **Gate／docs／次／停止:** bathymetryとmarine hard targetsが通りcorruptionを検出すれば完了し、taxonomy／geology-climate／hydrology／roadmapを更新して`V2-4-11`へ進める。coral habitatを形状ownerへ混ぜる必要があれば停止する。

### V2-4-11 Ecology placement

- **目的／前提:** habitat/assemblageとsparse deterministic placement基盤を実装する。前提は`V2-4-04`〜`V2-4-10`。
- **Scope／成果物:** habitat field、assemblage catalog、stable cell/feature seed、density/spacing selector、mangrove/root・coral・alpine vegetationのbounded descriptorsを実装する。
- **非Scope:** cave-local ecology、entity、block entity、Paper placement。
- **主変更:** `model.v2.ecology`、`generator.v2.ecology`、EcologyPlan／catalog Schema、fixtures／docs。
- **必須test／D/M/S:** habitat eligibility／density／spacing／support、dry mangrove／deep-cold coral／unsupported root、whole／tile／seam／thread／candidate順不変、descriptor/spatial-index budget、external script/preset拒否。
- **Gate／docs／次／停止:** placementsがhabitat内だけでstable、budget内、invalid supportを検出すれば完了し、geology-climate／taxonomy／roadmapを更新して`V2-4-12`へ進める。dense object gridが必要なら停止する。

### V2-4-12 Volcanic and canyon material integration

- **目的／前提:** 既存2.5D volcanic/canyonへsemantic geology/material ruleを接続する。前提は`V2-4-03`、`V2-4-07`、V2-3 skeletons。
- **Scope／成果物:** volcanic basalt/tuff/ash zones、canyon strata exposure/talus/sediment、feature-specific profile overlayとcross-feature conflict rulesを実装する。
- **非Scope:** 新形状generator、lava tube、cave、Minecraft palette変更。
- **主変更:** `generator.v2.material.feature`、feature profile Schema、volcanic/canyon fixtures／docs。
- **必須test／D/M/S:** crater/lava/shore profile、canyon wall/floor strata、profile conflict corruption、whole／tile／thread／module順不変、rule/descriptor budget、unknown lithology／silent fallback拒否。
- **Gate／docs／次／停止:** shape checksumを不必要に変えずsemantic metricsとmaterial read-backが通れば完了し、taxonomy／geology-climate／roadmapを更新して`V2-4-13`へ進める。shape修正が大規模なら原因Taskを再openする。

### V2-4-13 Environment validators and previews

- **目的／前提:** geology/climate/ecology/materialを独立検証し可視化する。前提は`V2-4-01`〜`V2-4-12`。
- **Scope／成果物:** lithology/strata、temperature/moisture/wetness/salinity/hydroperiod/snow、habitat/ecology/material、mangrove/coral/volcanic/canyon metric validatorとpreview layersを実装する。
- **非Scope:** generator redesign、Release capability、volume-local fields。
- **主変更:** `validation.v2.environment`、`preview.v2` registry、validation/preview Schema、corruption fixtures／docs。
- **必須test／D/M/S:** wrong snowline／salinity／reef depth／root support／material exposure、stable reductions、bounded scan/one-image render、artifact count/path/checksum/budget、cancel cleanup。
- **Gate／docs／次／停止:** 全corruption fixtureをgenerator-independentに検出しpreview index strict read-backなら完了し、validation／geology-climate／roadmapを更新して`V2-4-14`へ進める。generator-private arraysが必要なら停止する。

### V2-4-14 Release 2 `environment-fields` capability

- **目的／前提:** environment plans/fields/palette/ecologyをRelease 2へstrict追加する。前提は`V2-4-13`とV2-3 Release capability。
- **Scope／成果物:** `environment-fields`必須artifact/version、hydrology prior binding、palette checksum、directory／ZIP publisher/verifierを実装する。
- **非Scope:** sparse-volume、Paper placement、feature lifecycle最終昇格。
- **主変更:** `format.v2.release` catalog、environment artifact Schema／examples、artifact／migration docs。
- **必須test／D/M/S:** prior surface/hydrology Release回帰、environment complete、missing／extra／version／palette／field checksum改変、directory／ZIP parity、field/ecology/palette budget、future capability拒否。
- **Gate／docs／次／停止:** 直前capability setsとenvironment releaseを全てstrict verifyしfallbackなしなら完了し、artifact／migration／roadmapを更新して`V2-4-15`へ進める。HydrologyPlanを暗黙変換する必要があれば停止する。

### V2-4-15 Environment integration and Phase gate audit

- **目的／前提:** V2-4全Taskを統合しPhase gateを閉じる。前提は`V2-4-01`〜`V2-4-14`。
- **Scope／成果物:** snowy mountains、mangrove wetland、coral reef、volcanic material、canyon strata、environment ReleaseのPhase auditを実行する。
- **非Scope:** cave/lush local environment、Paper apply、実world計測。
- **主変更:** integration／compatibility tests、audit、roadmap、implementation roadmap、README実装状態。大規模修正は別Task。
- **必須test／D/M/S:** 全positive／corruption／tampering、whole／tile／thread／module／locale／timezone、1000角stage peakとsparse descriptor budget、cancel、V1／Release 1／V2-2/3回帰、clean build。
- **Gate／docs／次／停止:** 全gate通過時だけ対象featureと`environment-fields`を`SUPPORTED`、V2-4完了、Nextを`V2-5-01`にする。未解決prior transition、budget、palette regressionは新V2-4 Taskを追加して停止する。

## 5. V2-5 Sparse local volumetric terrain

### V2-5-01 Fixed-point SDF primitives

- **目的／前提:** 局所volumeの解析的signed distance primitiveを決定論的に実装する。前提はV2-4 gate完了。
- **Scope／成果物:** sphere/ellipsoid/capsule/plane/rounded box/swept splineのfixed-point distance、quantization/version、bounds estimateを実装する。
- **非Scope:** CSG、AABB index、voxel cache、feature generator。
- **主変更:** `generator.v2.volume.sdf`、VolumePlan primitive Schema、math fixtures／volumetric docs。
- **必須test／D/M/S:** boundary／symmetry／translation／overflow golden、primitive順／locale不変、allocation-free sampleとoperator budget、NaN相当／zero radius／future kernel拒否。
- **Gate／docs／次／停止:** supported runtimeでgolden distance/sign/checksumが一致しconservative boundsを返せれば完了し、volumetric／pipeline／roadmapを更新して`V2-5-02`へ進める。platform-dependent float branchが正本になるなら停止する。

### V2-5-02 Ordered CSG

- **目的／前提:** volume add/carve/fluid operationを明示順序で合成する。前提は`V2-5-01`。
- **Scope／成果物:** `ADD_SOLID`、`CARVE_SOLID`、`ADD_FLUID`、mask/intersectionのordered plan、stable operator ID、conflict validationを実装する。
- **非Scope:** spatial index、feature-specific volume、material paint。
- **主変更:** `model.v2.volume`、`generator.v2.volume.csg`、VolumePlan Schema、fixtures／docs。
- **必須test／D/M/S:** add→carve／carve→add差、CARVEがfluid非所有、operator permutation conflict、thread不変、operator count/depth/CPU budget、cycle／unknown op／ambiguous non-commutative order拒否。
- **Gate／docs／次／停止:** operation semanticsとcanonical orderがstrict round-tripしconflictを黙って解決しなければ完了し、volumetric／pipeline／roadmapを更新して`V2-5-03`へ進める。implicit last-write-winsが必要なら停止する。

### V2-5-03 AABB spatial index

- **目的／前提:** tileと交差するvolume operationだけをboundedに検索する。前提は`V2-5-02`。
- **Scope／成果物:** conservative AABB、stable bulk build、XZ/Y halo query、overlap candidate canonical order、index artifactを実装する。
- **非Scope:** voxel evaluation、cache、feature generation。
- **主変更:** `generator.v2.volume.index`、VolumePlan index descriptor、fixtures／docs。
- **必須test／D/M/S:** boundary-touch／nested／large overlap／translation、input順／thread不変、entry/node/query-result budget、integer overflow／invalid AABB／path checksum拒否。
- **Gate／docs／次／停止:** brute-force oracleと全query一致し1000角でdescriptor/index budget内なら完了し、volumetric／pipeline／roadmapを更新して`V2-5-04`へ進める。全operation全tile走査が必要なら停止する。

### V2-5-04 3D tile cache

- **目的／前提:** 交差volumeを3D halo付きtile-local chunkへ有界評価する。前提は`V2-5-03`。
- **Scope／成果物:** chunk key、XYZ halo、bounded LRU/explicit lifecycle、solid/fluid interval cache、cancel-safe allocation/admissionを実装する。
- **非Scope:** TerrainQuery公開、feature generator、global dense voxel field。
- **主変更:** `generator.v2.volume.cache`、resource budget model、instrumentation tests／docs。
- **必須test／D/M/S:** hit/evict／edge Y／overlap／cancel、tile／request／thread順不変、`retained + concurrency×peak + cache` admission、1000×1000×512 dense allocation detector、oversized chunk/support拒否。
- **Gate／docs／次／停止:** instrumentationがdense allocationなしとpeak上限を示しcache有無で同checksumなら完了し、volumetric／pipeline／roadmapを更新して`V2-5-05`へ進める。bounded cacheで上限化できなければ停止する。

### V2-5-05 TerrainQuery volume support

- **目的／前提:** common `TerrainQuery`／`TerrainBlockResolver`へsparse solid/fluid volumeを統合する。前提は`V2-5-04`。
- **Scope／成果物:** `solidIntervals`、`fluidIntervals`、ceiling/surface query、base heightfield＋ordered CSG composition、structure/validator/export共通APIを実装する。
- **非Scope:** feature-specific volumes、Paper placement、v1 adapter意味変更。
- **主変更:** `generator.v2.TerrainQuery` extension/versioned interface、resolver、query tests、architecture／volumetric docs。
- **必須test／D/M/S:** base-only互換、add/carve/fluid intervals、XYZ seam／whole/tile／thread不変、query/cache budget、invalid interval／Y overflow／owner conflict拒否、v1 adapter全block不変。
- **Gate／docs／次／停止:** base-only v2とv1 adapter回帰が一致しvolume queryが全consumerでpureなら完了し、architecture／volumetric／roadmapを更新して`V2-5-06`へ進める。column materializerを正本に戻す必要があれば停止する。

### V2-5-06 Cave network

- **目的／前提:** `CAVE_NETWORK` tunnel/chamber graphを局所carveへcompileする。前提は`V2-5-05`。
- **Scope／成果物:** stable tunnel graph、chamber/tunnel SDF、entrance relation、minimum roof、AABB plan、`EXPERIMENTAL` cave generatorを実装する。
- **非Scope:** lush ecology、underground lake、sea cave、material finishing。
- **主変更:** `generator.v2.volume.cave`、feature/relation/VolumePlan Schema、cave fixtures／docs。
- **必須test／D/M/S:** connectivity／entrance reachability／roof thickness、isolated chamber／surface breakthrough／thin roof、XYZ tile/thread/order不変、graph/AABB/cache budget、hard clearance conflict拒否。
- **Gate／docs／次／停止:** cave metricとcorruption detectionが通りdense volumeなしなら完了し、taxonomy／volumetric／roadmapを更新して`V2-5-07`へ進める。全域cellular gridが必要なら停止する。

### V2-5-07 Lush cave

- **目的／前提:** `LUSH_CAVE` chamberと局所湿潤conditionをcave network内に実装する。前提は`V2-5-06`とV2-4 environment/material。
- **Scope／成果物:** lush chamber carve、`WITHIN`／`REACHABLE_FROM` relation、local wet floor/wall/ceiling classification、ecology hooksを実装する。
- **非Scope:** full ecology placement（post-volume Task）、underground lake、lighting engine。
- **主変更:** `generator.v2.volume.cave.lush`、feature/relation Schema、scenario fixtures／docs。
- **必須test／D/M/S:** network containment／entrance reachability／wet surface eligibility、orphan/too-dry/thin roof、XYZ seam/thread不変、local field/descriptor budget、surface breakthrough拒否。
- **Gate／docs／次／停止:** lush chamberのshape/environment hooksとnegative fixtureが通れば完了し、taxonomy／geology-climate／volumetric／roadmapを更新して`V2-5-08`へ進める。Stage 10全体を先取りする必要があれば停止する。

### V2-5-08 Underground lake

- **目的／前提:** `UNDERGROUND_LAKE` basin carveと明示fluid bodyを実装する。前提は`V2-5-06`とordered CSG。
- **Scope／成果物:** chamber basin、single water surface、rim/containment、`ADD_FLUID` ownership、cave connectionを実装する。
- **非Scope:** sea connection、Paper fluid physics、lush material。
- **主変更:** `generator.v2.volume.water`、fluid body Schema、fixtures／docs。
- **必須test／D/M/S:** fluid continuity／rim／air cavity／cave access、leak／double fluid owner／carve-as-fluid corruption、XYZ seam/thread不変、fluid interval/cache budget、uncontained fluid拒否。
- **Gate／docs／次／停止:** offline resolverでcontained waterとair cavityがstrict read-backできれば完了し、volumetric／hydrology／roadmapを更新して`V2-5-09`へ進める。physics simulationが必要なら停止する。

### V2-5-09 Sea cave

- **目的／前提:** `SEA_CAVE` carve、marine opening、bounded fluid connectionを実装する。前提は`V2-5-06`、`V2-5-08`、coast/hydrology fields。
- **Scope／成果物:** cliff-hosted entrance、marine boundary relation、tidal/static water subset、roof/support constraintsを実装する。
- **非Scope:** dynamic tide、wave erosion、Paper placement containment。
- **主変更:** `generator.v2.volume.seacave`、feature/relation Schema、fixtures／docs。
- **必須test／D/M/S:** sea opening／fluid continuity／roof、landlocked／leaking inland／unsupported host、XYZ seam/thread不変、AABB/fluid budget、hard land-water conflict拒否。
- **Gate／docs／次／停止:** marine connectionとcontainment metricsが通りcorruption検出なら完了し、taxonomy／volumetric／roadmapを更新して`V2-5-10`へ進める。dynamic waterを成立条件にする必要があれば停止する。

### V2-5-10 Overhang

- **目的／前提:** `OVERHANG`をhost cliffへのsolid add＋recess carveで実装する。前提は`V2-5-05`とrock/cliff surface。
- **Scope／成果物:** support relation、roof slab、seaward clearance、recess operation、host AABB bindingを実装する。
- **非Scope:** natural arch、sea cave、Paper gravity/physics。
- **主変更:** `generator.v2.volume.overhang`、feature/relation Schema、scenario fixtures／docs。
- **必須test／D/M/S:** support／roof thickness／clearance／open side、floating slab／thin roof／blocked corridor、XYZ seam/thread/order不変、AABB/cache budget、unsupported host拒否。
- **Gate／docs／次／停止:** overhang hard metricsとcorruption fixtureが通りstable block streamなら完了し、taxonomy／volumetric／roadmapを更新して`V2-5-11`へ進める。Paper containmentを同時に必要とするなら停止する。

### V2-5-11 Natural arch

- **目的／前提:** `NATURAL_ARCH`のpiers、crown、through carveを実装する。前提は`V2-5-02`、`V2-5-05`。
- **Scope／成果物:** two-support solid plan、ordered opening carve、minimum pier/crown、clearance corridorを実装する。
- **非Scope:** bridge structure asset、sky island、material finishing。
- **主変更:** `generator.v2.volume.arch`、feature parameter Schema、fixtures／docs。
- **必須test／D/M/S:** pier/crown/support/clearance、one-pier／thin crown／closed opening、XYZ seam/thread/order不変、operator/AABB/cache budget、non-commutative conflict拒否。
- **Gate／docs／次／停止:** arch metricsとcorruption detectionが通りresolver read-back一致なら完了し、taxonomy／volumetric／roadmapを更新して`V2-5-12`へ進める。structure assetでしか表せないなら停止する。

### V2-5-12 Sky island group

- **目的／前提:** `SKY_ISLAND_GROUP`の独立solid componentsとunderside carveを実装する。前提は`V2-5-05`。
- **Scope／成果物:** stable multipoint/group plan、solid lobes、top/edge/underside classes、ground clearance、inter-island gap、support-free allowed policyを実装する。
- **非Scope:** ecology/material finishing、Paper apply、unbounded floating world。
- **主変更:** `generator.v2.volume.skyisland`、feature/vertical Schema、scenario fixtures／docs。
- **必須test／D/M/S:** component count／ground clearance／gap／bounds、merged/touching ground/out-of-Y、XYZ seam/thread/point-order不変、component/AABB/cache budget、dense air fill拒否。
- **Gate／docs／次／停止:** group hard metricsとcorruption fixtureが通りbounded descriptorsのみなら完了し、taxonomy／volumetric／roadmapを更新して`V2-5-13`へ進める。global sky voxel layerが必要なら停止する。

### V2-5-13 Waterfall volume integration

- **目的／前提:** V2-3 waterfallへfalling water columnとbehind-fall clearanceを接続する。前提は`V2-3-06`、`V2-5-05`、`V2-5-08`。
- **Scope／成果物:** fall column `ADD_FLUID` plan、air/rock clearance、plunge pool continuity、fall node checksum bindingを実装する。
- **非Scope:** dynamic fluid simulation、Paper settle、new waterfall graph。
- **主変更:** `generator.v2.volume.waterfall`、VolumePlan/hydrology binding Schema、fixtures／docs。
- **必須test／D/M/S:** lip→column→pool continuity／behind clearance、offset column／leak／missing pool、XYZ seam/thread不変、fluid/AABB/cache budget、graph checksum mismatch拒否。
- **Gate／docs／次／停止:** graphとvolumeがchecksum-boundでfluid continuity metricを満たせば完了し、hydrology／volumetric／roadmapを更新して`V2-5-14`へ進める。Paper physicsが必要なら停止する。

### V2-5-14 Post-volume environment and material

- **目的／前提:** volume確定後のfloor/wall/ceiling/top/edge/undersideへlocal environment/material/ecologyを適用する。前提は`V2-5-06`〜`V2-5-13`とV2-4 material/ecology。
- **Scope／成果物:** local wetness/drip/shade/surface class、lush moss/root/pool、cave/sea-cave wet rock、sky-island top/edge/underside profile、sparse placementsを実装する。
- **非Scope:** new volume shapes、lighting engine、Paper biome/entity。
- **主変更:** `generator.v2.environment.local`、material/ecology rules、profile Schema、fixtures／docs。
- **必須test／D/M/S:** surface classification/support/habitat、moss on dry ceiling／root without support／wrong underside corruption、XYZ seam/thread/module順不変、local query/placement budget、unknown surface/profile拒否。
- **Gate／docs／次／停止:** final resolverでsemantic conditionsとplacementsがstableかつbudget内なら完了し、geology-climate／volumetric／roadmapを更新して`V2-5-15`へ進める。regional fieldを再生成する必要があれば停止する。

### V2-5-15 Volume validators and previews

- **目的／前提:** volume topology、support、fluid、environmentを独立検証し可視化する。前提は`V2-5-06`〜`V2-5-14`。
- **Scope／成果物:** connectivity/roof/support/clearance/component/fluid/material validators、AABB/operator/slice/solid-fluid/surface-class previewsとindex metadataを実装する。
- **非Scope:** generator redesign、schematic、Release capability。
- **主変更:** `validation.v2.volume`、`preview.v2` registry、validation/preview Schema、corruption fixtures／docs。
- **必須test／D/M/S:** isolated cave/thin roof/leak/floating overhang/broken arch/merged sky island/fall discontinuity、stable traversal/reduction、bounded slice render、preview/path/count/checksum/cancel budget。
- **Gate／docs／次／停止:** 全corruption fixtureをresolver/IRから検出しpreview strict read-backなら完了し、validation／volumetric／roadmapを更新して`V2-5-16`へ進める。dense validation gridが必要なら停止する。

### V2-5-16 Offline 3D schematic read-back

- **目的／前提:** volumeを含むtile block streamをSponge v3へ出しoffline read-backする。前提は`V2-5-14`、V2-2 schematic基盤。
- **Scope／成果物:** air cavity／fluid／independent solidのstable XYZ export、general VarInt、tile boundary palette、WorldEdit 7.3.19とFAWE-compatible offline inspector testを実装する。
- **非Scope:** Paper apply、FAWE server smoke、Release capability。
- **主変更:** `format.v2.tile`、`worldedit` inspector/adapters、3D fixtures／artifact docs。
- **必須test／D/M/S:** cave/overhang/sky/fluid/air, palette boundaries, whole/tile XYZ stream, thread/order不変、stream/palette/buffer budget、truncated/invalid VarInt/checksum改変拒否。
- **Gate／docs／次／停止:** offline read-back block streamがresolver checksumと全XYZ一致しv1/V2-2 export回帰不変なら完了し、artifact／volumetric／roadmapを更新して`V2-5-17`へ進める。server runtimeを必要とする場合はV2-6へ延期する。

### V2-5-17 Release 2 `sparse-volume` capability

- **目的／前提:** VolumePlan／index／validation／3D tileをRelease 2へstrict追加する。前提は`V2-5-15`〜`V2-5-16`とV2-4 Release capability。
- **Scope／成果物:** `sparse-volume`必須artifact/version、AABB/operator/palette/block checksum binding、directory／ZIP publisher/verifierを実装する。
- **非Scope:** Paper placement、feature lifecycle最終昇格。
- **主変更:** `format.v2.release` catalog、volume artifact Schema／examples、artifact／migration docs。
- **必須test／D/M/S:** earlier capability regression、complete volume release、missing/extra/version/operator/AABB/tile checksum改変、directory/ZIP parity、operator/index/expand/palette budget、future capability拒否。
- **Gate／docs／次／停止:** 全過去capability setとsparse-volume releaseをstrict verifyしfallbackなしなら完了し、artifact／migration／roadmapを更新して`V2-5-18`へ進める。format core意味変更が必要なら停止する。

### V2-5-18 Volume integration and Phase gate audit

- **目的／前提:** V2-5全Taskを統合しPhase gateを閉じる。前提は`V2-5-01`〜`V2-5-17`。
- **Scope／成果物:** cave/lush/underground lake/sea cave、overhang、arch、sky island、waterfall volume、post-environment、offline export、Release capabilityのPhase auditを実行する。
- **非Scope:** Paper apply、server smoke、実world計測。
- **主変更:** integration／compatibility tests、audit、roadmap、implementation roadmap、README実装状態。大規模修正は別Task。
- **必須test／D/M/S:** 全positive/corruption/tampering、3D whole/tile/XYZ seam/operator/thread/locale/timezone、dense allocation detectorと1000角admission、cancel、V1/Release1/V2-2〜4回帰、clean build。
- **Gate／docs／次／停止:** 全gate通過時だけvolume featureと`sparse-volume`を`SUPPORTED`、V2-5完了、Nextを`V2-6-01`にする。未解決memory/palette/offline read-back riskは新V2-5 Taskを追加して停止する。

## 6. V2-6 Release 2 placement, hardening, and supported catalog

### V2-6-01 Release 2 placement contract

- **目的／前提:** verified Release 2をworld mutationから分離するplacement plan／journal契約を固定する。前提はV2-5 gate完了。
- **Scope／成果物:** target/bounds/anchor、capability set、tile/order、mutation/effect envelope参照、reservation/confirmation binding、operation ID、format 2 journal statesのstrict contractを実装する。
- **非Scope:** envelope算出、reservation、snapshot、apply、Undo/Recovery。
- **主変更:** `model.v2.placement`、`core.v2` plan compiler、placement plan/journal Schema、新ADR、migration／placement docs。
- **必須test／D/M/S:** strict round-trip／unknown state/version/capability／target mismatch、canonical order／locale/timezone、journal/entry budget、path/checksum/future version拒否、v1 journal codec不変。
- **Gate／docs／次／停止:** Release/target/capabilityへchecksum-boundしたimmutable planがstrict読取できれば完了し、ADR／migration／roadmapを更新して`V2-6-02`へ進める。v1 journal再解釈が必要なら停止する。

### V2-6-02 Mutation and effect envelope

- **目的／前提:** final resolverとphysics-sensitive contentからmutation/effect envelopeを保守的に算出する。前提は`V2-6-01`。
- **Scope／成果物:** per-tile mutation AABB、union effect envelope、fluid/gravity/neighbor support radius、overflow-safe world bounds、envelope checksum/provenanceを実装する。
- **非Scope:** reservation、snapshot、apply、settle。
- **主変更:** `core.v2.placement.envelope`、envelope Schema、fixtures／placement docs。
- **必須test／D/M/S:** solid/air/fluid/gravity/boundary fixtures、under-approx corruption、tile/order/thread不変、envelope count/volume/disk estimate admission、world border/Y overflow/unknown physics class拒否。
- **Gate／docs／次／停止:** independent oracleでmutationを包含しeffect上限を安全に証明できれば完了し、placement／security／roadmapを更新して`V2-6-03`へ進める。副作用上限を定義できないcontentはhard rejectできなければ停止する。

### V2-6-03 Region/disk reservation and bound confirmation

- **目的／前提:** 全region/diskを先に予約しconfirmationをrelease/target/envelope/reservationへ結合する。前提は`V2-6-02`。
- **Scope／成果物:** atomic multi-region reservation、disk estimate/reservation、overlap拒否、actor-bound one-time expiry confirmation、late completion-safe state transitionを実装する。
- **非Scope:** snapshot、apply、settle、v1 reservation意味変更。
- **主変更:** `core.v2.placement`、reservation store、confirmation contract/Schema、tests／admin docs。
- **必須test／D/M/S:** overlap/race/expiry/replay/disk shortage/restart、canonical reservation order、bounded locks/entries/disk, actor/target/checksum mismatch拒否、v1 reservation回帰。
- **Gate／docs／次／停止:** confirm前に全予約が成功しbinding改変/replayを拒否、失敗時全解放なら完了し、placement／admin／roadmapを更新して`V2-6-04`へ進める。partial reservationを安全にrollbackできなければ停止する。

### V2-6-04 Snapshot-all

- **目的／前提:** 最初のapply前に全effect envelopeをsnapshotしてstrict verifyする。前提は`V2-6-03`。
- **Scope／成果物:** canonical envelope order、snapshot artifact/index/checksum、disk budget、all-before-any-apply state invariant、cancel/shutdown cleanupを実装する。
- **非Scope:** block apply、settle、rollback execution、Undo UX。
- **主変更:** `core.v2.placement.snapshot`、`worldedit` gateway contract、snapshot Schema、tests／operations docs。
- **必須test／D/M/S:** multiple envelope/snapshot failure/disk full/cancel/restart、order/thread不変、snapshot/buffer/disk admission、partial/index/path/checksum/TOCTOU拒否、apply gateway未呼出確認。
- **Gate／docs／次／停止:** 全snapshot strict read-back後だけstateがAPPLY_READYになり失敗時canonical partialなしなら完了し、placement／operations／roadmapを更新して`V2-6-05`へ進める。tileごとのsnapshot→applyへ戻す必要があれば停止する。

### V2-6-05 Fluid and gravity containment preflight

- **目的／前提:** apply前にfluid/gravity/neighbor updateがeffect envelope外へ出ないことを判定する。前提は`V2-6-02`と`V2-6-04`。
- **Scope／成果物:** content classifier、support/closure rules、boundary seals、unsupported-state denylist、containment evidenceとhard rejectionを実装する。
- **非Scope:** apply、settle simulation、事後rollbackによる代替。
- **主変更:** `core.v2.placement.safety`、`format.v2.minecraft` metadata、policy Schema、security／placement docs。
- **必須test／D/M/S:** contained/uncontained water/lava/sand/gravel/neighbor-sensitive states、classification order不変、bounded scan/cache budget、unknown block state/version/envelope gap拒否。
- **Gate／docs／次／停止:** effect上限を証明できないcontentをapply前に100% hard rejectしsnapshot外検出へ依存しなければ完了し、security／placement／roadmapを更新して`V2-6-06`へ進める。安全な上限が定義できなければ対象capabilityを拒否したまま停止する。

### V2-6-06 Release 2 apply transaction orchestration

- **目的／前提:** validate→reserve→confirm→snapshot-all→applyの順序を破れないapplication serviceを実装する。前提は`V2-6-01`〜`V2-6-05`。
- **Scope／成果物:** operation state machine、canonical tile apply、solids/air→fluid-sensitive pass、gateway completion/close tracking、scheduler dispatch contract、failed future propagationを実装する。
- **非Scope:** settle/full verify、rollback完成、Undo/Recovery、production `SUPPORTED` enable。
- **主変更:** `core.v2.placement` application service、Paper/worldedit v2 gateway、journal transitions、tests／architecture docs。
- **必須test／D/M/S:** illegal order/late completion/timeout/cancel/shutdown/tile failure/main-thread assertion、tile/order不変、bounded scheduler slices/queue/admission、confirm/envelope/checksum mismatch拒否、v1 service回帰。
- **Gate／docs／次／停止:** apply開始前invariantが強制され受理済みoperationを観測timeoutだけで取消扱いにしなければ完了し、architecture／placement／roadmapを更新して`V2-6-07`へ進める。rollback設計不能なmutationを実行する必要があれば停止する。

### V2-6-07 Bounded settle and full verify

- **目的／前提:** apply後にbounded multi-tick settleしeffect envelope全体をexact verifyする。前提は`V2-6-06`。
- **Scope／成果物:** settle policy、scheduler-sliced full scan、canonical expected resolver、late update reconciliation、metric/journal evidenceを実装する。
- **非Scope:** sampled verify、rollback実行、Undo、performance support declaration。
- **主変更:** `core.v2.placement.verify`、Paper gateway、verify policy Schema、tests／operations docs。
- **必須test／D/M/S:** delayed update/timeout/mismatch/server shutdown/tile checkpoint false-positive、scan order/thread不変、slice/timeout/queue budget、effect外 update/unknown settle policy拒否。
- **Gate／docs／次／停止:** settle後effect envelope全体のexact block-state streamを比較しfailureをcanonical分類できれば完了し、placement／operations／roadmapを更新して`V2-6-08`へ進める。sample verifyへ弱める必要があれば停止する。

### V2-6-08 Rollback

- **目的／前提:** Release 2 placement失敗時にsnapshot済みenvelopeを逆順復元しfull verifyする。前提は`V2-6-04`、`V2-6-06`、`V2-6-07`。
- **Scope／成果物:** reverse-order restore、close/completion tracking、rollback settle/verify、partial failure classification、reservation/journal finalizationを実装する。
- **非Scope:** user Undo、startup Recovery、snapshot外変更対応。
- **主変更:** `core.v2.placement.rollback`、world gateway、journal states、tests／operations docs。
- **必須test／D/M/S:** failure at every apply/settle/verify point、rollback failure/late completion/shutdown、restore order不変、bounded slices/disk lifetime、missing/tampered snapshot拒否、v1 rollback回帰。
- **Gate／docs／次／停止:** fault injection全点で復元または明示RECOVERY_REQUIREDとなり成功を偽らなければ完了し、placement／operations／roadmapを更新して`V2-6-09`へ進める。snapshot外副作用をrollback対象とする必要があれば停止する。

### V2-6-09 Undo

- **目的／前提:** 完了済みRelease 2 operationをconfirmation付きで安全にUndoする。前提は`V2-6-08`。
- **Scope／成果物:** operation-bound Undo plan/confirm、current-world preflight、snapshot restore、settle/full verify、journal retention transitionを実装する。
- **非Scope:** v1 Undo変更、startup Recovery、force overwrite。
- **主変更:** `core.v2.placement.undo`、Paper command/service v2 explicit path、journal Schema、tests／user/admin docs。
- **必須test／D/M/S:** actor/replay/expiry/world drift/disk/cancel/late completion、canonical restore order、bounded scan/retention budget、tampered/missing snapshot拒否、v1 Undo回帰。
- **Gate／docs／次／停止:** Release 2 Undoがworld driftを黙って上書きせずfull verifyしv1意味不変なら完了し、user/admin/placement／roadmapを更新して`V2-6-10`へ進める。force semanticsが必要なら別Taskとして停止する。

### V2-6-10 Recovery

- **目的／前提:** restart後のRelease 2 operationを保守的にdiagnose/rollback/acceptできるようにする。前提は`V2-6-08`〜`V2-6-09`。
- **Scope／成果物:** journal/artifact/world evidence classification、late operation reconciliation、recovery plan、confirmation-bound rollback/accept、cleanup retentionを実装する。
- **非Scope:** 自動accept、v1 recovery意味変更、external backup integration。
- **主変更:** `core.v2.recovery`、journal/recovery Schema、Paper admin path、tests／operations docs。
- **必須test／D/M/S:** each persisted state/restart/missing artifact/tamper/late scheduler/disk full、stable classification、bounded scan/retention budget、ambiguous stateはmanual-required、v1 recovery回帰。
- **Gate／docs／次／停止:** ambiguityを成功へ分類せず全stateに安全なoperator actionを示せれば完了し、operations／admin／roadmapを更新して`V2-6-11`へ進める。一意な証拠がない状態を自動修復する必要があれば停止する。

### V2-6-11 Provider, manual, and image capability integration

- **目的／前提:** OpenAI／Anthropic/manual/import/image pathsからv2 capabilityを明示選択し同じcanonical compileへ接続する。前提はV2-2〜5 supported contractsとRelease 2。
- **Scope／成果物:** provider capability negotiation、v2 structured intent/import、manual/constraint bundle binding、reference image→soft draft/confirmation boundary、no-fallback dispatchを実装する。
- **非Scope:** new AI model feature、Web UI、画像からhard geometryの暗黙生成、Paper apply internals。
- **主変更:** `ai.spi` versioned interfaces、provider adapters、`core.v2` design service、request/provider docs、tests。
- **必須test／D/M/S:** each provider/manual/image path、unsupported model/capability/unknown version、canonical output equivalence、bounded payload/image/budget、secret redaction/path security、v1 provider default回帰。
- **Gate／docs／次／停止:** provider差がcanonical Intent後のgenerationへ影響せずunsupported capabilityをfallbackしなければ完了し、README／provider／migration／roadmapを更新して`V2-6-12`へ進める。real credentialをfixtureへ必要とする場合はcontract testで止め外部smoke Taskを追加する。

### V2-6-12 Release 2 cross-capability hardening

- **目的／前提:** Release 2 directory/ZIPと全capability組合せのstrict security回帰を閉じる。前提はV2-2〜5 capabilitiesと`V2-6-01`。
- **Scope／成果物:** capability dependency matrix、cross-version reader policy、all artifact limits、tamper corpus、placement eligibility verifierを実装する。
- **非Scope:** world mutation、new artifact type、Release format 3。
- **主変更:** `format.v2.release` verifier、security tests/corpus、artifact-format／security／migration docs。
- **必須test／D/M/S:** every valid capability prefix、unknown/missing/extra/duplicate/version/checksum/path/ZIP bomb、order/charset/locale不変、entry/expand/decode/disk budget、Release 1 strict regression。
- **Gate／docs／次／停止:** 全valid prefixを読み全corruptionをdirectory/ZIP双方で拒否しplacement eligibilityがstrictなら完了し、artifact/security/migration／roadmapを更新して`V2-6-13`へ進める。allowlist共有でRelease 1を緩める必要があれば停止する。

### V2-6-13 Operational metrics, diagnostics, and retention

- **目的／前提:** Release 2 generation/placement/recoveryの運用証拠と保持policyを完成する。前提は`V2-6-06`〜`V2-6-12`。
- **Scope／成果物:** stage/queue/memory/disk/settle/verify metrics、redacted admin diagnostics、audit correlation、snapshot/release retention dry-run/confirm cleanupを実装する。
- **非Scope:** external telemetry SaaS、Web UI、自動削除default。
- **主変更:** `core.v2.operations`、Paper admin commands、metric/audit Schema、tests／admin/operations docs。
- **必須test／D/M/S:** bounded labels/queues/logs、cancel/failure/restart/cleanup, stable metric units/reduction、retention/disk budget、secret/path/raw payload redaction、actor-bound cleanup確認。
- **Gate／docs／次／停止:** operatorが各failureをcorrelation IDから診断できcleanupがdry-run/confirm/audit付きなら完了し、admin/operations/security／roadmapを更新して`V2-6-14`へ進める。unbounded cardinalityやsecret保存が必要なら停止する。

### V2-6-14 WorldEdit 7.3.19 smoke

- **目的／前提:** 今回buildのRelease 2 end-to-end placementをWorldEdit 7.3.19実機でsmokeする。前提は`V2-6-01`〜`V2-6-13`。
- **Scope／成果物:** isolated server/worldでgenerate→Release verify→plan→confirm→snapshot-all→apply→settle→full verify→Undo、logs/versions/checksums/cleanup evidenceを記録する。
- **非Scope:** FAWE、500/1000 performance claim、Beta hardening既存項目の自動完了。
- **主変更:** smoke scripts/fixtures、audit evidence、development/release checklist/roadmap。production変更はsmoke defectの小規模修正だけ。
- **必須test／D/M/S:** main-thread assertions、fluid/volume/coastal fixture、restart-safe journal、measured peak/disk within declared budget、no secret/world artifact commit、v1 smoke regression。
- **Gate／docs／次／停止:** exact build/version付き実機evidenceとUndo full verifyが揃えば完了し`V2-6-15`へ進める。環境がなければ`BLOCKED_EXTERNAL`として手順を残し、mockで完了扱いにしない。

### V2-6-15 FAWE standalone smoke

- **目的／前提:** version固定FAWE単独profileでRelease 2 placement smokeを実行する。前提は`V2-6-14`。
- **Scope／成果物:** dependency lock/ADRへFAWE buildを記録し、WorldEdit plugin併用なしで同じend-to-end/Undo/recovery smoke evidenceを取得する。
- **非Scope:** 過去FAWE smoke流用、500/1000 claim、FAWE private API採用。
- **主変更:** FAWE smoke profile/scripts、ADR、audit、development/release checklist/roadmap。
- **必須test／D/M/S:** gateway close/async completion/main-thread boundary、same canonical expected checksum、measured queue/memory/disk、no secret/server artifacts commit、failure/recovery smoke。
- **Gate／docs／次／停止:** 今回buildと明記versionの独立実機evidenceが揃えば完了し`V2-6-16`へ進める。環境不足は`BLOCKED_EXTERNAL`、private API必須やsemantic差は停止してadapter Taskを追加する。

### V2-6-16 500×500 real-world measurement

- **目的／前提:** 500×500 Release 2をsupported placement候補として実測する。前提は`V2-6-14`〜`V2-6-15`。
- **Scope／成果物:** representative capability fixtureのgenerate/export/apply/settle/full verify/Undo、peak memory/disk/time/scheduler slices、failure cleanup evidenceを取得する。
- **非Scope:** 1000×1000、上限の自動引上げ、offline測定による代用。
- **主変更:** measurement protocol/results、config support gate、performance/operations/release checklist/roadmap。
- **必須test／D/M/S:** repeated seed checksum、tile order equivalence、declared admission vs measured peak、disk reservation/retention、no artifact/secret commit、post-Undo exact world verify。
- **Gate／docs／次／停止:** 対応WorldEdit/FAWE環境で成功し安全marginを根拠化できた時だけ500角placementをcatalog候補にし`V2-6-17`へ進める。環境なし/上限超過は`BLOCKED_EXTERNAL`または未対応として停止する。

### V2-6-17 1000×1000 real-world measurement

- **目的／前提:** 1000×1000 Release 2 placementを別Taskとして実測する。前提は`V2-6-16`。
- **Scope／成果物:** 1000角のgenerate/export/apply/settle/full verify/Undo、peak memory/disk/time/scheduler behavior、failure/recovery evidenceを取得する。
- **非Scope:** 500角結果の外挿、測定なしのsupport宣言、Web UI。
- **主変更:** measurement results、config dimension gate、performance/operations/release checklist/roadmap。
- **必須test／D/M/S:** repeat checksum/tile order/thread、admission vs measured peak、disk/snapshot/envelope/full scan、server responsiveness evidence、post-Undo exact verify。
- **Gate／docs／次／停止:** 実測が全gateと安全marginを満たした場合だけ1000角placementをcatalog候補にする。実測を完遂したが上限を満たさない場合は、1000角をunsupportedとして500以下のhard limitと証拠を固定すればTaskを完了できる。環境不足は`BLOCKED_EXTERNAL`、原因修正を同Taskへ抱える場合は停止する。いずれも確定後だけ`V2-6-18`へ進める。

### V2-6-18 Final supported catalog

- **目的／前提:** feature/capability/dimension/provider/runtimeのsupported matrixを証拠から固定する。前提は`V2-6-11`〜`V2-6-17`。1000角失敗は明示上限を保てばTaskを続行できるが、未測定をsupportedにしない。
- **Scope／成果物:** lifecycle catalog、required capability/runtime/version、placement dimension limit、preset availability、unsupported/deferred diagnostics、README/user docsを整合する。
- **非Scope:** 新feature追加、gate waiver、Web UI、Beta hardening未完項目の削除。
- **主変更:** built-in descriptor/catalog data、capability matrix docs/Schema、README/user/provider/limitations/roadmap。
- **必須test／D/M/S:** every `SUPPORTED` entryのcontract/generator/validator/preview/fixture/budget/release/smoke evidence link、unknown/deprecated selection拒否、catalog order/checksum、catalog size/parse budget、future capability拒否。
- **Gate／docs／次／停止:** evidence欠損entryを`EXPERIMENTAL`へ残し実測済み上限だけ設定できれば完了し`V2-6-19`へ進める。手動主張しかないentryやBeta項目削除が必要なら停止する。

### V2-6-19 Release candidate audit and Phase gate

- **目的／前提:** V2-6とTerrain Generation v2全体を監査し親Phase gateを閉じる。前提は`V2-6-01`〜`V2-6-18`。外部Taskの`BLOCKED_EXTERNAL`は未完扱い。
- **Scope／成果物:** contract→provider/manual/image→generation→validation/preview→Release→placement/verify/rollback/Undo/Recovery、catalog、security、performance、v1回帰のRC auditとrelease decisionを作る。
- **非Scope:** 新実装、大規模bug fix、未完Beta hardeningの免除、Web UI。
- **主変更:** audit、roadmap、release checklist、README/limitations/operationsの実態同期。production修正は行わず、defectは新Task化する。
- **必須test／D/M/S:** full clean test/build、all capability directory/ZIP tamper、all supported scenario/determinism/runtime profile、measured admission/effect/snapshot/disk、fault injection/recovery、v1 Schema/generator/Release1/placement/Undo golden。
- **Gate／docs／次／停止:** 全必須Taskとsupport evidenceが完了し未解決critical/high riskがない時だけV2-6を完了とする。Beta hardeningの別未完項目はそのまま残す。失敗・外部環境不足・大規模修正は新Taskを登録し、Release candidateを未承認のまま停止する。

## 7. 親Phaseを閉じる規則

`V2-2-12`、`V2-3-15`、`V2-4-15`、`V2-5-18`、`V2-6-19`だけが親Phaseを完了へ変更できる。先行Taskが完了しても、次のどれかが残れば親Phaseは `進行中` である。

- 同Phaseの未完／再open／追加Task
- feature lifecycleのvalidator／preview／corruption／budget不足
- capability strict verifierまたは直前capability回帰不足
- whole／tile／thread／locale／timezone決定性不足
- 1000角offline budget不足
- v1互換回帰
- V2-6では必要な実機smoke／対応寸法計測、またはcritical/high risk

Phase統合Taskは問題を隠すためにAcceptanceを緩めない。修正が1回のTask規模を超える場合は新IDを追加し、統合Taskを未完のまま終了する。
