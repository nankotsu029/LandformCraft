# アーキテクチャ

## 1. 目的と品質属性

LandformCraftは、文章と画像から最大約1000×1000のMinecraft地形と少量の人工物を作り、別環境へ安全に持ち運べる汎用システムです。

優先順位は次のとおりです。

1. 本番ワールドを破壊しない
2. 同じ設計・seed・generator versionから再現できる
3. AI Provider、Paper、WorldEditから生成コアを分離する
4. 1000×1000をメモリ上限内で処理する
5. 生成前に人間が検証・比較・承認できる
6. サーバー再起動や外部API失敗から復旧できる

## 2. 責務の分離

```text
入力層              自然言語、画像、bounds、seed
  ↓
AI解釈層            OpenAI / Anthropic / Imported JSON / Fixture
  ↓
設計層              GenerationRequest → TerrainIntent → WorldBlueprint
  ↓
生成・検証層        layout、heightfield、水系、素材、structure、validator
  ↓
出力層              preview、TerrainPlan、tile、manifest、checksum、ZIP
  ↓
Minecraft連携層     Paper、WorldEdit API、FAWE互換、snapshot、apply、undo
```

AIの責務は「何を作るか」を構造化することです。Javaの責務は、値を検証し、seedに基づいてブロックへ決定論的に変換することです。

## 3. packageと依存方向

```text
ai.openai ────────┐
ai.anthropic ─────┴→ ai.spi → model
generator ─────────────────→ model
structure ─────────────────→ model
validation ────────────────→ ai.spi, model
preview ────────→ generator, model
format ────────────────────→ model
worldedit ─────────────────→ format
core ───────────→ model, ai.spi, generator, validation, preview, format
cli ────────────→ core, ai.openai, ai.anthropic, format
paper ──────────→ core, ai.openai, ai.anthropic, format, worldedit
```

すべてルートの`src`内に置きますが、依存は下位の純粋packageへ向けます。`model`にPaper型が入ったり、`generator`がOpenAIを呼んだりする逆流は禁止します。

初期構成をGradle subprojectへ分けていたのは、AI、生成、Paper、WorldEditの依存方向をbuild時に強制するためでした。しかしPhase 0では実装量よりdirectoryとbuild fileの数が多く、変更時の追跡負担が上回ります。そのため現在は単一project＋package分離を採用します。次のいずれかが実際に必要になった時だけ再分割します。

- CLI／Paperを独立したbinaryとして別々にreleaseする
- AI SDKやWorldEdit依存をcompile classpathから物理的に隔離する
- package規約だけでは循環依存を防げなくなる
- module単位のtest／build cacheが開発時間を明確に改善する

### `model`

Java標準ライブラリだけで表現するrecord、enum、不変条件です。serializationの注釈やMinecraft型も原則として持ち込みません。

### `core`

Application Service、job state machine、artifact repository、checkpoint、非同期処理を統括します。I/O用Virtual Threads、CPU生成用bounded pool、生成／Release pipelineに加え、永続`PlacementJournal`を更新する`PlacementApplicationService`を実装しています。

### AI packages

`ai.spi`が `TerrainDesignProvider`、manual import／fixture、共通policyを定義します。Providerは構造化された `TerrainIntent` を返し、block listやコードを返しません。`ai.http`はOpenAI／Anthropic共通のHTTP、retry、timeout、quotaだけを持ちます。Provider固有のendpoint、header、request／response mappingは`ai.openai`／`ai.anthropic`へ残し、APIキーやHTTP型を`core`／`generator`へ漏らしません。

### 生成・検証・preview

`generator`はMinecraftサーバーなしで動きます。64×64／128×128の論理layoutを補間し、global X/Z noise、zone、局所侵食からheight、水域、material、featureを生成します。Phase 6の`StructurePlanner`は同じseedからanchor／rotationを探索し、水、崖、傾斜、bounds、他structureとの間隔を検査します。`structure`はMinecraft versionとsemantic SHA-256を固定した小規模block template catalogです。tile単独生成は16 block marginを計算して中央だけを返します。`validation`はSchema、画像、height、水深、tile coverage、孤立水域、river pit、structure衝突、performance budgetを検査します。`preview`は地形データから8種類のPNGを1枚ずつ生成・解放します。

### 画像入力境界

```text
request.ymlのrole付き相対path
  → I/O executorでroot内regular file／symlink／byte／magic検査
  → bounded CPU executorでheader／single-frame／pixel検査とdecode
  → EXIF向き補正、canonical ARGB化、metadata除去、PNG再符号化
  → TOP_DOWN_SKETCHだけ座標正規化と強い矛盾検査
  → PreparedReferenceImage（memory上のsanitized bytes）
  → OpenAI／Anthropic Provider
```

Providerへ渡すのはrole説明と正規化済み画像bytesだけで、filesystem pathやraw metadataはrequest payloadへserializeしません。画像なしrequestも空の`PreparedImageInputs`を作って同じProvider／Design Package pipelineへ合流します。変換と観測は`image-evidence.json`へ保存しますが、画像binaryはDesign Packageへ保存しません。

### Format／WorldEdit

`format`はportable artifactの論理形式、全artifact checksum、asset semantic checksum、ZIP、atomic publish、strict read-back verifyを担当します。`worldedit`は列規則へ小さなstructure override indexを重ね、Sponge Schematic v3をtileごとにstream出力します。利用assetはstandalone `.schem`としても出力します。WorldEdit 7.3.19の公開reader／paste APIによる互換testを持ちます。Paper側の`PaperWorldEditPlacementGateway`はschematic I/OをVirtual Threadへ、Bukkit／WorldEdit world操作をSchedulerへ分離します。

### Paper／CLI

どちらも薄いadapterです。Paperはcommand、permission、Scheduler、request／design／generation／export、selection、apply、undo、recoveryを担当します。CLIは同じcoreをサーバーなしで呼び、world mutationは行いません。

## 4. 生成パイプライン

```text
GenerationRequest
  → request schema / semantic validation
  → TerrainDesignProvider または JSON import
  → TerrainIntent schema / semantic validation / normalization
  → BlueprintCompiler
  → WorldBlueprint
  → low-resolution layout
  → heightfield
  → water systems
  → terrain features
  → material layers
  → structure asset選択／anchor／rotation／衝突検査
  → tiles
  → result validation
  → previews
  → schematic export
  → release packaging
```

各段階は入力checksum、出力checksum、version、job stageをcheckpointへ保存します。途中状態を黙って再利用せず、互換性とchecksumが一致した安全なcheckpointだけを再開します。

## 5. 低解像度から高解像度へ

大域的な海・陸・山・川・zone配置は64×64または128×128の論理mapで決めます。その後、補間、global noise、侵食などで要求解像度へ拡大します。

AIに1000×1000を直接描かせないことで、応答量、曖昧さ、局所ノイズを抑えます。AI出力を変えてもgeneratorの品質改善を独立して行えます。

## 6. 1000×1000とtile

基本の内部表現は列ごとの2次元データです。

```text
heightMap[x,z]
waterLevelMap[x,z]
surfaceMaterialMap[x,z]
biomeMap[x,z]
featureMask[x,z]
caveDescriptor
```

地下は全block listでなく、表面、表土厚、岩盤、水、空気、cave maskの列規則として表現します。

既定tileは128×128です。1000×1000は8×8の64 tileになります。各tileを生成、検証、出力したら大きな一時bufferを解放します。

連続性の規則:

- noiseは `sample(globalX, globalZ)` で評価する
- River、erosion、slopeなどはmarginを含む領域で計算する
- marginを除く中心tileだけを成果物へ採用する
- tile IDとoriginはmanifestで一意に定義する

## 7. 非同期とスレッド境界

| 処理 | 実行先 |
|---|---|
| HTTP、画像file読込、artifact I/O | admission上限付きVirtual Thread per task executor |
| 画像decode／解析、heightmap、erosion、PNG、検証 | 上限付きqueue／platform thread pool |
| pipeline合成、timeout、失敗伝播 | `CompletableFuture` |
| Bukkit/Paper world read/write | Paper Schedulerのメインスレッド |
| WorldEdit/FAWE edit | APIの非同期契約に従い、完了・closeを追跡 |

Paperメインスレッド上でfutureを `join()`／`get()` しません。非同期結果をworldへ反映するときだけ `PaperMainThreadDispatcher` へ渡し、その前にvalidationとconfirmを完了します。world操作の実行開始をcommit pointとし、execute／undoの返却Futureは観測専用でcallerからcancelできません。plugin disableは新規mutationを拒否してdispatcher／executorを停止し、途中停止で残った`APPLYING`／`ROLLING_BACK`／`UNDOING` journalは次回起動時に`RECOVERY_REQUIRED`へ移します。

CPU生成taskは `CancellationToken` を受け取り、tile行や反復回数など有界な間隔で確認します。Future cancelはdelegateをinterruptしtokenもcancel状態になります。停止時は全poolを先にcancelし、poolごとでなく全体共有の5秒deadlineでterminationを待ちます。

## 8. 実行モード

### Mode A: 手動Intent import

Web版ChatGPT／Claudeなどで人間がIntentを作り、JSONを検証してimportします。APIキー不要で、最初の実用モードです。

### Mode B: Paper直接API

Paper commandはProviderをI/O executorから非同期に呼び、job IDを即時返します。API keyはconfig値でなく環境変数から取得し、model、quota、timeout、retry、plugin停止時cancelを共通policyで制御します。

## 9. Job stateと復旧

標準状態は `GenerationStage` enumを正本にします。

```text
QUEUED → VALIDATING_REQUEST → CALLING_AI → VALIDATING_INTENT
       → COMPILING_BLUEPRINT → GENERATING_LAYOUT → GENERATING_TERRAIN
       → GENERATING_FEATURES → GENERATING_STRUCTURES
       → VALIDATING_RESULT → RENDERING_PREVIEWS
       → EXPORTING_TILES → PACKAGING → READY
```

任意の段階から `FAILED`／`CANCELLED` へ遷移できます。再起動後に自動継続できない状態は `RECOVERY_REQUIRED` とし、人間またはrecovery policyが最後の安全なcheckpointから再開します。

Phase 4／5の設計jobは`QUEUED → VALIDATING_REQUEST → CALLING_AI → VALIDATING_INTENT → READY`だけを通り、地形generatorを呼びません。request／画像file／HTTP／import／artifact I/Oはadmission上限付きVirtual Thread、画像decode／解析はbounded CPU executorで実行し、返却Futureのcancelを実タスクinterruptへ伝播します。画像、timeout、retry枯渇、Schema違反はworld変更前に`FAILED`、明示cancelは`CANCELLED`として`generation-job.schema.json`準拠のatomic JSONへ保存します。

Providerが返したIntentはStructured Outputだけを信用せず、完全な`terrain-intent.schema.json`とJava record不変条件で再検証します。OpenAI／Claudeへ送るSchemaは両APIの共通対応subsetへ制約を落とし、hard limitは必ずローカルの完全Schemaで強制します。

## 10. 安全な配置

```text
Release checksum検証
  → target world / bounds / height / permission検証
  → overlap領域予約 / disk見積・予約
  → dry-run placement plan
  → 明示confirm
  → tile snapshot
  → tile apply
  → tile verify
  → 次tile
  → 全成功でAPPLIED
```

各checkpointはworld変更の前後にatomic JSONとして保存します。confirmationはoperation、Release、world、origin、bounds、actor、期限、nonceへ結合します。apply失敗時はsnapshot済みtileを逆順に、Undo時は全tileを逆順に復元します。snapshotが欠損・改変されていれば復元せず`RECOVERY_REQUIRED`にします。1000×1000全体を1回の巨大transactionとして扱いません。

## 11. WorldEditとFAWE

コードはWorldEdit 7.3.19の公開APIへcompileOnlyで依存します。FAWEが導入されている環境ではWorldEdit互換APIとして利用します。WorldEditとFAWEを同時に導入しません。過去のPhase 6 buildではFAWE 2.15.2 smoke記録がありますが、0.9.0-beta.1のcurrent smoke結果はbeta auditとrelease checklistを正本にします。

FAWE固有機能が必要になっても、generatorやcoreへFAWE型を漏らさず専用adapterへ閉じ込めます。

## 12. 設計判断

採用理由と影響は [adr/README.md](adr/README.md) から追跡します。外部仕様の正本はこの文書、data model、artifact format、schemaへ反映し、ADRだけに仕様を閉じ込めません。

## 13. Terrain Generation v2との関係

この文書の1〜11節は `0.9.0-beta.1`／TerrainIntent v1／generator `3.0.0-phase6`／Release format 1の現行アーキテクチャです。[ADR 0010](adr/0010-adopt-versioned-terrain-v2-roadmap.md) は、これをin-placeで置換せず、version分離したv2を段階導入する方針を採用しました。[roadmap](roadmap.md#terrain-generation-v2) 上、V2-0〜V2-3のPhase gateと`V2-4-01`〜`V2-4-04`は完了し、次は`V2-4-05`です。V2-4-01〜03ではtyped geology plan、closed lithology catalog、strata profile／derived scalarとhydrology geology-input version transitionを追加しました。V2-4-04ではcoarse climate priorとfinal temperature／moistureを別module／stageへ分離し、V2-3 `HydrologyPlan`／fixed runoff prior checksumを保持した明示generator version transitionとして`ClimatePlanV2`へfreezeしました。これらは`EXPERIMENTAL`で、V2-3 artifact、v1、Release能力は変更していません。V2-4親Phase gateとV2-5〜V2-6は未完了です。

v2の目標境界は次のとおりです。

```text
自然言語・reference image・constraint map
  → TerrainIntent v2（feature / geometry / relation / hard-soft constraint）
  → WorldBlueprint v2（stage graph / field ownership / named seed / budget）
  → macro layout / regional module / hydrology / environment
  → 2.5D surface + sparse local volume + structure
  → feature validation / diagnostic preview
  → Release format 2 offline verify
  → effect envelope付きPaper placement
```

現行のpackage境界、bounded executor、global coordinate tile、strict artifact検証、atomic publish、confirm／snapshot／rollbackは流用します。v2 model／codec／generator／publisher／verifierはv1とversion dispatchで並設し、v1 checksum、Release 1 allowlist、配置journalの意味を変えません。詳細は [design-v2](design-v2/implementation-roadmap.md) と [migration plan](design-v2/migration-plan.md) を参照してください。

V2-0／V2-1／V2-2／V2-3の現行実装は次の分離経路です。

```text
terrain-intent JSON
  → TerrainIntentVersionDispatcher（schemaVersion:1 / intentVersion:2のexact dispatch）
  → LandformV2DataCodec（strict Schema / fixed-point normalization / canonical JSON）
  → TerrainIntentV2
  → DiagnosticBlueprintCompilerV2
  → compile-time BuiltInLandformModuleCatalogV2 / named seed / budget preflight
  → descriptor-only WorldBlueprintV2 + machine-readable diagnostic issues

generation-request-v2 JSON + local numeric PNG
  → SecureConstraintMapSourceLoader（path/link/TOCTOU/byte/checksum）
  → NumericPngDecoder（grayscale U8/U16 raw sample）
  → ConstraintMapSamplerV2（rotation/flip/crop/pixel-center/fixed-point）
  → desired/actual/residual LFC_GRID_V1 sidecar + strict index
  → fixed 8 diagnostic PNG（atomic bundle）

CoastalFeaturePlanV2 COASTLINE + optional HARD land-water LFC window
  → CoastalRasterKernelV2（integer spline／distance／normal／nearshore）
  → global X/Z direct sampler または core 256＋halo 64 bounded window
  → row-major stable field checksum／streaming actual-land-water source

SandyBeachPlanV2 + CoastalRasterKernelV2
  → SandyBeachGeneratorV2（endpoint width／fixed slope／foreshore／backshore／nearshore）
  → 4 descriptor-only semantic field／streaming hard metric
```

`WorldBlueprintV2`はdiagnostic用`DESCRIPTOR_ONLY`に加え、artifact metadataと一致する`SIDECAR` fieldを表せます。値はBlueprint JSONへ埋め込まず、row-major sidecarをbounded windowで読みます。V2-0では`TerrainQuery`／`TerrainBlockResolver`とv1 adapterが現行materializerの全block出力を再現します。V2-1 manual pathはAIを呼びません。V2-2-03〜07はcoastal semantic fieldと明示transitionを生成し、V2-2-08は独立validator／preview、V2-2-09はfinal resolverからV2専用Sponge v3 tileへ二走査streamingし`worldedit.v2`でoffline read-backします。V2-2-10はempty-capability Release 2 coreを、V2-2-11はこれらをcomplete setとして収容するoffline `surface-2_5d` capabilityを`format.v2.release`へ並設しました。V2-3-14は`hydrology-plan`を`surface-2_5d`依存で追加し、V2-3-15でstrict capabilityと完成featureのoffline lifecycleを監査しました。Paper world変更は未実装で、v1のcodec、generator、Release publisher／verifier、配置／Undoへv2型は接続していません。

V2-3-01は`HydrologyPlanV2`をBlueprintへ追加し、empty graph、固定prior checksum、6 descriptor field、node／edge／CPU／resident budgetをcompileします。V2-3-02は別のpure generator境界でinteger provisional surfaceと明示outletから全域priority routingを行い、stable basin summary、D8 direction U8、accumulation I32をstrict `LFC_GRID_V1` bundleへatomic publishします。V2-3-03〜11はriver、lake、canyon、waterfall、delta、tidal、fjord、mountain、volcanicをそれぞれversion付きplanとglobal-X/Z bounded field generatorへ分離します。V2-3-12はこれらのscalar targetを`HydrologyReconciliationPlanV2`へfreezeし、field非所有の`reconcile.hydrology` stageで固定3 passの補正またはcanonical residual failureを返します。V2-3-13〜15でfield-only validation／preview、strict Release capability、Phase portfolioを閉じました。mangrove shaping、salinity、V2-4 field、waterfall volume、Paper経路には接続していません。
