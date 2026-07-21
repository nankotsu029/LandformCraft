# データモデルとスキーマ

## 原則

- Java内のデータ定義は不変なrecordを基本とする
- `landformcraft.model` packageはJava標準ライブラリ以外へ依存しない
- request／intent／blueprint／plan／manifestを別契約として扱う
- schema versionとgenerator versionを混同しない
- 外部入力はschema validation後、semantic validationを通してからdomain recordへ変換する

## 主要モデル

### `GenerationRequest`

人間が指定する入力です。

```text
schemaVersion
requestId
bounds(width, length, minY, maxY, waterLevel)
prompt
images(file, role)[]
generation(candidates, baseSeed)
output(tileSize, createSchematics, createZip)
```

主な不変条件:

- width／lengthは1〜1000
- `minY < maxY`、water levelはその範囲内
- request IDは小文字英数字から始まる最大64文字のslug
- candidatesは1〜16
- tile sizeは32〜256の2のべき乗
- image roleを必須とする
- 縦spanはinclusiveで最大512
- reference image pathはportableな相対pathだけを許可し、absolute、`..`、backslashを拒否する

YAML外部名は読みやすさのためkebab-caseを使用します。Java mapping層が`schema-version`を`schemaVersion`へ変換します。

### `ReferenceImageRole`

| 値 | 意味 |
|---|---|
| `MOOD_REFERENCE` | 色、荒々しさ、植生、雰囲気 |
| `TOP_DOWN_SKETCH` | 上面から見た海・陸・山・川の配置 |
| `HEIGHT_REFERENCE` | 高低差と山の形 |
| `ZONE_REFERENCE` | 地域区分 |
| `MATERIAL_REFERENCE` | 岩、砂、植物などの素材感 |
| `STRUCTURE_REFERENCE` | 少量の建築物の外観 |

roleなし画像はSchemaでrequest自体を拒否し、既定roleを推測しません。

### `TerrainIntent`

AIまたはmanual importが作る「何を作るか」の契約です。block ID、絶対block座標、Javaコードは含めません。

```text
schemaVersion
theme
topology
seaSides
landRatio
relief(minimum, average, maximum)
coastline(irregularity, bayCount, capeCount)
water(riverCount, lakeCount, maximumSeaDepth, shallowShelfWidth)
zones[]
structures[]
```

0.0〜1.0の正規化値を使い、AI Provider固有のconfidenceやtoken usageはIntent本体へ混ぜず、provider response metadataへ保存します。

AI出力は非信頼入力なので、湾／岬は各64、川16、湖64、海深512、遠浅幅1000、structure総数256のhard limitをrecordとschemaの両方で課します。zone IDは一意、structureのzone参照は実在必須、zone areaShare合計は1.0以下です。HTTP Provider応答で各areaShareが個別には正常なのに合計だけが超過した場合は、相対比を保つProvider限定正規化後に完全検証します。manual importは正規化せず超過を拒否します。topologyと海・川・湖の最低限の整合もrecordで検査します。

### `WorldBlueprint`

検証・正規化済みIntentをgenerator用にコンパイルした設計です。少なくともrequest ID、bounds、seed、tile size、logical resolution、generator versionを固定します。

Blueprint以降でAI応答を参照せずに生成できることが要件です。

### V2-0 diagnostic contract

V2はv1 recordへnullable fieldを追加せず、`model.v2`の別契約として並設します。`TerrainIntentVersionDispatcher`はv1の`schemaVersion: 1`とv2の`intentVersion: 2`をexact dispatchし、version欠落、両方の指定、future versionを拒否します。

`TerrainIntentV2`の最小契約は、feature、POINT／MULTI_POINT／SPLINE／MULTI_SPLINE／POLYGON／VOLUME_GUIDE geometry、stable point／path／endpoint、relation、HARD／SOFT constraint、environment descriptor、canonical constraint artifact binding、structure request、provenanceです。正規化X/Zと小数contractはmillionthsの整数へ量子化します。自由形式parameter map、raw path、URL、base64 raster、Minecraft block state、任意codeは受理しません。最初のcoastal 5 kindだけが型付きparameterを持ち、`SANDY_BEACH`はspline進行方向に対する`landSide=LEFT|RIGHT`、`foreshoreShare01`、`endpointTaperBlocks`を必須とします。残りのscenario kindは空parameterと明示的なunsupported capability診断に限定します。

`WorldBlueprintV2`はidentity、source request／intent checksum、compiler／generator version、bounds／coordinate space、tile policy、named seed規則、module／stage／field descriptor、field ownership／merge、feature plan、HydrologyPlan、regional feature plan、`hydrologyReconciliationPlan`、validation target、resource budget、diagnostic issue、canonical checksumをfreezeします。V2-0 compilerの出力は`DESCRIPTOR_ONLY`です。V2-1は`SIDECAR` descriptorを、V2-3-01はdescriptor-only Hydrology IRを追加しました。V2-3-02のrouting payloadはBlueprint JSONへ埋め込まず、HydrologyPlan checksumへbindingした別artifactとしてfreezeします。V2-3-03〜11はriver／lake／canyon／waterfall／delta／tidal／fjord／mountain／volcanicの`EXPERIMENTAL` execution planを、V2-3-12は固定3 passのscalar target／budgetを持つ`HydrologyReconciliationPlanV2`をBlueprintへsealします。TerrainPlan v1、schematic、Release、Paper配置は作りません。

### V2-2-01〜03 coastal foundation／raster／beach contract

`CoastalFeaturePlanV2`は`planVersion=1`で、4 coastal kindのfeature ID、kind、block millionths geometry、geometry role、coast-side field／値、signed-distance field／符号／最大距離、nearshore profile、support radiusをfreezeします。normalized X/Zのblock化は`normalizedMillionths × (dimension - 1)`であり、浮動小数を正本にしません。

`v2.coast.foundation`はcompile-time built-in moduleで、`generate.coastal-raster` stage、XZ halo 64、actual land-water、coast-side、signed-distance、normal X/Z、nearshore profileに、beach local width、surface height、band、semantic sandを加えた10 fieldを宣言します。fieldは`DESCRIPTOR_ONLY`、ownerはこのmoduleだけ、mergeは`SINGLE_OWNER`です。`ResourceBudget`は従来のfeature／field／point／byte上限に`maximumHaloXZ`／`maximumHaloY`を加え、module宣言より小さいbudgetをplan作成前に拒否します。

`SandyBeachPlanV2`は`planVersion=1`で、`ENDPOINT_TAPER`幅、foreshore share、shore slopeのmin／max／選択値とinteger rise、nearshore distance／depth、vertical bounds、4 field ID、support radiusをfreezeします。nearshore distanceはsupportの距離飽和と帯終端を区別するため1..63とし、supportはその外側1 blockまで含めます。`SandyBeachGeneratorV2`はplatform floatや全域dense fieldを使わず、global X/Zまたはbounded windowから同じraw field値を返します。Minecraft blockへのmaterial解決はまだ行いません。

`CoastalRasterKernelV2`はCOASTLINE planをinteger Q12へ固定量子化し、signed distance／normal／linear nearshoreをblock millionthsでsampleします。core 256、halo 64、retained 8 MiB上限のwindowだけを保持し、1000角dense fieldを作りません。HARD land-waterはstrict `LFC_GRID_V1` windowから0／1だけを受理し、actual fieldへexact copyします。V2-2-12統合監査後は、このkernelを含む4 coastal featureのoffline経路を`SUPPORTED`としています。

### V2-1 constraint map contract

`GenerationRequestV2`はProviderへ渡す`referenceImages`と、AIを介さず数値decodeする`constraintMaps`を別collection／別recordとして定義します。constraint map sourceは`constraint-source:<slug>`、安全な相対path、期待source checksum／寸法、categoricalまたはheight decoder、pixel-center座標、quarter-turn／flip／crop、U8／U16 encoding、label allowlist、no-dataを所有します。role、HARD／SOFT、weight、tolerance、samplingは重複させず、freeze済み`TerrainIntentV2.ConstraintMapBinding`だけが所有します。

heightの意味は`ABSOLUTE_BLOCK_Y`、`BLOCKS_ABOVE_REQUEST_MIN_Y`、`BLOCKS_RELATIVE_TO_WATER_LEVEL`のenumで必須化し、scale／offsetはmillionths整数です。categorical mapの未知sampleを近いlabelへfallbackせず、LAND／ZONE bindingは`NEAREST`だけを許可します。

`WorldBlueprintV2.FieldDescriptor`はV2-0互換の`DESCRIPTOR_ONLY`に加え、`SIDECAR`時だけ`FieldArtifactDescriptorV2`を必須とします。sidecar descriptorはfield ID、semantic、value type、寸法、coordinate space、sampling、scale／offset、no-data、`LFC_GRID_V1`、artifact／semantic checksum、source provenanceを持ち、Blueprint側descriptorとの不一致をconstructorで拒否します。値配列はBlueprint JSONへ埋め込みません。

`HydrologyPlanV2` version 1はbasin／node／reach／water body／fallのstable-ID graph、checksum付き`UNIFORM_GEOLOGY_PRIOR`／`CONSTANT_RUNOFF_PRIOR`、6 field binding、graph／CPU／resident budgetを保持します。通常compilerはrouting前のempty graphを保存します。minimal graphではendpoint、node reach index、basin source／outlet、water body／fall binding、cycle、source→outlet reachabilityをstrictに検査し、未知version／prior／checksumへfallbackしません。

`GeologyPlanV2` version 1はV2-3 fixed prior checksumを`PriorReplacement`で明示参照し、named seed、emptyまたはuniform province、opaque formation ID、hardness／permeability、4個のU16 single-owner field、CPU／retained／window working／artifact budgetをfreezeします。field payloadは4個の`LFC_GRID_V1`へ保存し、全no-dataまたはprovince descriptorと完全一致するcellだけを専用bounded readerが受理します。

`LithologyPlanV2` version 1はsource `GeologyPlanV2` checksum、`landformcraft.builtin-lithology` catalog contract、catalog／plan checksum、province assignment、resource budgetをfreezeします。catalogは9個のclosed semantic lithology entryと8-bit compact code、hardness、permeability、erosion responseだけを持つ。assignmentはprovince／formation IDとcode、scalarをcatalog entryへ完全一致で結合し、readerは既存province fieldをbounded windowで検査する。strata、Minecraft block state、feature responseは保持しない。

`StrataPlanV2` version 1はsource geology／lithology checksum、provinceごとの`BOTTOM_TO_TOP` ordered layer、thickness、cardinal dip／optional foldのbounded subset、resource budget、およびV2-3 `UNIFORM_GEOLOGY_PRIOR`／`hydrology-reconcile-fixed-v1`を明示参照する`HydrologyGeologyInputHandoff`をfreezeします。surface-exposed derived hardness／permeabilityはprofile descriptorからinteger-onlyで再計算し、dense 3D strata配列や追加sidecarを正本にしません。Minecraft block stateとHydrology planのin-place変更は行いません。

`HydrologyRoutingArtifactV2` version 1はsolver `hydrology-priority-flood-v1`、D8 encoding `hydrology-d8-terminal-v1`、source HydrologyPlan／provisional surface／fixed-prior checksum、global Z／X／ID順の明示outlet、`basin-000001`形式のsummary、2個のfield descriptor、resource usage、graph／routing／canonical checksumを保持します。directionはU8（terminal 0、D8 1..8、no-data 255）、accumulationはI32（no-data 0）で、`LFC_GRID_V1` sidecarに保存します。strict readerは全routable cellのdownstream accumulationが増加し、宣言terminalへ一度だけ到達し、outlet accumulationとbasin areaが一致することをbounded row scanで検証します。

`HydrologyReconciliationPlanV2` version 1はsource HydrologyPlan checksum、`hydrology-reconcile-fixed-v1`、`kind-feature-constraint-v1`、固定3 pass、reach／lake／delta／tidal／fjord／waterfallのscalar variable／constraint、work／working-set／artifact budgetを保持します。`HydrologyReconciliationStateV2`はplanへbindingした観測値とhard lockだけを渡し、全域fieldを複製しません。`HydrologyReconciliationArtifactV2`はfinal value、actual delta／residual／correction count、stable failure reason、resource usage、source／final state／result／canonical checksumを保持します。strict codecは4 MiB document上限、duplicate／unknown／future field、checksum改変、symlinkを拒否します。

`ConstraintFieldIndexV2`はbinding、content-addressed field descriptor、categorical source sample→canonical ID→label tableを保存します。1000×1000値はindexではなくsidecarだけに置きます。manual prepareはcanonical artifact IDを返し、frozen生成はTerrainIntentのartifact IDとsemantic checksumが一致しなければ公開前に拒否します。

### V2-2-09 offline tile contract

`OfflineTilePlanV2`はtile ID／index、release-local origin、width／length、inclusive minY／maxYを固定し、水平各辺256、vertical span 512、world bounds 1000×1000を超える値を拒否します。`OfflineTileArtifactV2`はsource Blueprint checksum、`RELEASE_LOCAL_XYZ`、`SPONGE_X_Z_Y_V1`、Minecraft／DataVersion／Sponge version、safe relative schematic path、block／palette／byte budget、artifact／semantic／canonical checksumを持つRelease外metadataです。

### V2-2-10 Release format 2 core contract

`ReleaseManifestV2`はformat version 2、manifest version 1、release ID、sorted／unique `requiredCapabilities[]`、path順のstrict `ReleaseArtifactDescriptorV2[]`、exclude-self canonical checksumを持ちます。descriptorはartifact ID／type／version、safe relative path、byte length、artifact SHA-256、semantic SHA-256を持ち、manifest自身をindexへ入れません。空capability coreは引き続き空indexだけを許可する。`V2-2-11`の`surface-2_5d` dispatchはrequest／intent／Blueprint、field index＋sidecar、validation artifact、preview index＋11 PNG、tile metadata＋Sponge v3のcomplete setだけを許可し、unknown capability／type／versionと欠損／余分なpayloadを拒否する。

`CanonicalBlockStreamV2`はtile boundsをversion headerへ含め、X fastest→Z→Y順のcanonical block-stateを長さ付きUTF-8でhashします。`OfflineTileSchematicWriterV2`はfinal `TerrainBlockResolver`を二走査し、palette count以外のblock collectionを保持しません。`WorldEditOfflineTileReaderV2`はstrict bounded inspector後にWorldEdit 7.3.19で同じsemantic checksumを再計算します。これらはv1 `TerrainPlan`、`ManifestTile`、Release 1 recordへ追加しません。

### Phase 0で追加済みのrecord

- `TerrainPlan`: 全体の2次元mapとfeature descriptor
- `TilePlan`: tile origin、範囲、margin、checksum
- `TerrainTile`: marginを除外した単独tileのheight／water／material／feature grid
- `StructurePlan`: asset、anchor、rotation、collision bounds
- `ValidationIssue`／`ValidationResult`
- `GenerationMetrics`: measured generation timeと推定memory
- `ExportManifest`／`ManifestTile`

`ExportManifest`はtile ID、case-foldしたfile path、x/z indexの一意性と、grid全体のcoverageをconstructorで検査します。`ManifestTile`はschematic artifact checksum、materialize前のterrain semantic checksum、air policy、READY status、airを含むblock countを固定します。

### Phase 3の配置record

- `PlacementPlan`: Release checksum、request、world UUID／name、target／計算済みbounds、作成時刻
- `PlacementJournal`: 配置state、確認action／hash／期限、tile checkpoint、更新時刻、運用message
- `PlacementTileCheckpoint`: `PENDING`／`SNAPSHOTTED`／`APPLIED`／`VERIFIED`／`RESTORED`とsnapshot相対path／SHA-256
- `WorldDescriptor`: Paper worldのUUID、name、inclusive height／border bounds
- `SelectionBounds`: WorldEdit selectionを外部型なしで表したinclusive bounds

確認tokenそのものはjournalへ保存せず、operation、plan／Release checksum、world UUID、origin／bounds、actor、createdAt、expiresAt、nonceへ結合したSHA-256だけを保存します。`PLANNED`は`APPLY`確認、`APPLIED`は必要に応じて`UNDO`確認、`RECOVERY_REQUIRED`は安全診断後だけ`RECOVERY_ROLLBACK`または`RECOVERY_ACCEPT`確認を持ちます。tokenは一回用で、別actor／world／origin／operation、期限切れ、再利用を拒否します。

### Phase 4のProvider／監査record

- `TerrainDesignResult`: 検証済みIntent、provider／model／prompt version、response ID、usage、attempts、完了時刻
- `ProviderUsage`: input／output／total token数。料金表ではなくprovider応答の使用量を保持
- `TerrainDesignPolicy`: request timeout、attempt上限、backoff、output token、RPM、累積token budget
- `DesignAudit`: job／request ID、上記provider metadata、request／Intent checksum、開始／完了時刻
- `GenerationJobSnapshot`: stage、progress、安全な短いmessageを持つ最新checkpoint

`DesignAudit`はAPIキー、Authorization header、prompt本文、provider error本文を保持しません。Intentは別の`terrain-intent.json`へ保存し、監査側のSHA-256とDesign Packageの`checksums.sha256`で結びます。

### Phase 5の画像record

- `PreparedReferenceImage`: 検証・向き補正・metadata除去・PNG再符号化済みのProvider向けin-memory handle。defensive copyを行い、raw fileをProviderへ公開しない
- `ImageInputEvidence`: request ID、正規化version、provider／response／prompt version、画像別証跡、文章との整合検査、作成時刻
- `ImageEvidenceEntry`: 安定した画像ID、role、Provider送信有無、`VERIFIED`状態、source／normalized checksum・寸法・byte数、metadata／orientation、変換履歴、上面図観測
- `TopDownCoordinateMapping`: `TOP_LEFT`原点、右方向`POSITIVE_X_EAST`、下方向`POSITIVE_Z_SOUTH`、対象bounds
- `ImageConsistencyCheck`: 文章が明示した方角の海／陸と、上面図edgeのwater ratioとの`CONSISTENT`／`INCONCLUSIVE`結果

画像は1枚8 MiB、source合計32 MiB、1枚4,000,000 pixel、合計16,000,000 pixel、各辺4096、縦横比32:1、正規化後1枚16 MiB／Provider送信合計16 MiBを上限とします。PNG／JPEGのmagicと拡張子を照合し、single-frameだけを受理します。resizeは行わず、上限外を拒否します。強い矛盾は記録して続行せず、Design Package公開前に拒否します。

### Phase 6のstructure record

- `StructurePlan`: asset ID／semantic checksum／Minecraft version、type、release-local anchor、quarter-turn rotation、回転後寸法、terrain-following
- `RequiredAsset`: 利用assetのtype／version／semantic checksum、standalone schematic相対path／artifact checksum、未回転寸法
- `RequiredAssets`: Release内で実際に参照されたassetだけを持つversion付きcatalog
- `StructurePlacementManifest`: generator versionと配置済み`StructurePlan`のportable一覧

asset IDはbuilt-in catalogへ解決でき、placementのtype、checksum、version、寸法、terrain-followingが一致しなければなりません。anchorはgeneration bounds内、structure同士は1 blockの安全間隔を含めて非衝突とします。preferred zone外へfallbackした配置は`preferredZoneFallback: true`と`structure-preferred-zone-fallback` warningで明示します。配置要求を満たす安全な候補がない場合は`StructurePlan`を作らず、`structure-not-placed` warningを残して地形だけを有効な候補として維持します。

## 座標系

- local X/Z: release minimum cornerを `(0, 0)` とする
- global X/Z: generator内部の連続したsample座標。tileが変わってもresetしない
- local Y: manifestのanchor規則に従う。`minY`と`maxY`はどちらもinclusive
- Paper world座標: apply planで初めて決定する
- tile ID: `tile-{xIndex:02}-{zIndex:02}`
- tile origin: `(xIndex * tileSize, zIndex * tileSize)`

1000が128で割り切れないため、端tileは最大範囲から切り詰めます。tile countはceil divisionで計算します。

縦spanは `maxY - minY + 1` で、初期hard limitは512です。apply時には対象Paper world固有のmin/max heightも別途検証します。

## Versioning

### Schema version

JSON/YAMLのfield、型、enum、意味のversionです。各artifactに保存します。

- v1 Schemaの `$id` はversion付きIDとする。`0.1.0-SNAPSHOT`監査中の契約修正を除き、最初の安定版公開後は同じIDの内容を書き換えない
- additiveなoptional fieldは新readerが旧documentを読む方向だけ後方互換にできる
- `additionalProperties: false` の旧readerは新fieldを拒否するため、旧reader→新documentを互換とは扱わない
- required fieldの変更、型変更、意味変更は新schema version
- unknown fieldは入力時に原則拒否し、誤記を見逃さない
- migrationは元ファイルを上書きせず、新artifactを作る

### Generator version

同じBlueprintとseedから得られる結果を識別します。algorithm、noise、material rule、asset catalogの変更で結果が変わる場合は更新します。

### Plugin／application version

配布binaryのversionです。schema／generator versionとは独立です。

## Determinism

再現性の入力は次の組です。

```text
canonical WorldBlueprint bytes
base seed / candidate seed
generator version
asset checksums
Minecraft block registry compatibility version
```

JSON object order、locale、timezone、default charset、thread scheduling、unordered collectionのiteration順に結果を依存させません。

## Job state

`GenerationStage`はcheckpointの状態名です。progressは0.0〜1.0ですが、stageの遷移可否はcoreのstate machineが検証します。`READY`、`FAILED`、`CANCELLED`をterminal stateとし、`RECOVERY_REQUIRED`は人間またはpolicyの判断を待つ非実行状態です。

## Schemaファイル

active inventoryは`schemas/`のv2 contractである。既存v1 artifactのread／verify／migrateに必要なSchemaは、V2-12-06でpackaged legacy resourceへ隔離した。`$id`とbytesは変更していない。

- [`generation-request.schema.json`](../src/main/resources/legacy/v1/contracts/generation-request.schema.json)（legacy resource、immutable IDは`/schemas/v1/...`）
- [`terrain-intent.schema.json`](../src/main/resources/legacy/v1/contracts/terrain-intent.schema.json)（legacy resource）
- [`world-blueprint.schema.json`](../src/main/resources/legacy/v1/contracts/world-blueprint.schema.json)（legacy resource）
- [`generation-request-v2.schema.json`](../schemas/generation-request-v2.schema.json)（V2-1 reference／constraint source分離、immutable IDは`/schemas/v2/...`）
- [`terrain-intent-v2.schema.json`](../schemas/terrain-intent-v2.schema.json)（V2-0 contract＋V2-2-01〜06 coastal feature profile＋V2-2-07 version付きtransition policy、immutable IDは`/schemas/v2/...`）
- [`world-blueprint-v2.schema.json`](../schemas/world-blueprint-v2.schema.json)（V2-0 descriptor-only＋V2-1 SIDECAR＋V2-2 coastal＋V2-3 Hydrology＋V2-4 geology／climate plan binding、immutable IDは`/schemas/v2/...`）
- [`hydrology-plan-v2.schema.json`](../schemas/hydrology-plan-v2.schema.json)（V2-3-01 graph／fixed prior／field／budget contract）
- [`hydrology-routing-artifact-v2.schema.json`](../schemas/hydrology-routing-artifact-v2.schema.json)（V2-3-02 basin／outlet／routing field／resource／checksum index）
- [`hydrology-reconciliation-plan-v2.schema.json`](../schemas/hydrology-reconciliation-plan-v2.schema.json)（V2-3-12 fixed pass／scan order／scalar target／budget contract）
- [`hydrology-reconciliation-artifact-v2.schema.json`](../schemas/hydrology-reconciliation-artifact-v2.schema.json)（V2-3-12 final state／residual／failure reason／resource／checksum evidence）
- [`geology-plan-v2.schema.json`](../schemas/geology-plan-v2.schema.json)（V2-4-01 prior replacement／province／4 field ownership／resource budget）
- [`lithology-plan-v2.schema.json`](../schemas/lithology-plan-v2.schema.json)（V2-4-02 fixed catalog／province assignment／checksum／budget）
- [`strata-plan-v2.schema.json`](../schemas/strata-plan-v2.schema.json)（V2-4-03 ordered strata／fold-tilt／derived scalars／hydrology handoff）
- [`climate-plan-v2.schema.json`](../schemas/climate-plan-v2.schema.json)（V2-4-04 coarse prior／final temperature・moisture／Hydrology runoff-prior version handoff／budget）
- [`constraint-field-index-v2.schema.json`](../schemas/constraint-field-index-v2.schema.json)（V2-1 binding／field artifact／label table index＋V2-3-02 routing sidecar semantic code）
- [`coastal-preview-index-v2.schema.json`](../schemas/coastal-preview-index-v2.schema.json)（V2-2-08 fixed coastal diagnostic layer index）
- [`offline-tile-artifact-v2.schema.json`](../schemas/offline-tile-artifact-v2.schema.json)（V2-2-09 standalone tile metadata。Release manifestではない）
- [`release-manifest-v2.schema.json`](../schemas/release-manifest-v2.schema.json)（V2-2-10 empty-capability Release format 2 core index）
- [`coastal-validation-artifact-v2.schema.json`](../schemas/coastal-validation-artifact-v2.schema.json)（V2-2-11 sealed coastal validation evidence）
- [`export-manifest.schema.json`](../src/main/resources/legacy/v1/contracts/export-manifest.schema.json)（legacy resource）
- [`placement-journal.schema.json`](../src/main/resources/legacy/v1/contracts/placement-journal.schema.json)（legacy resource）
- [`design-audit.schema.json`](../src/main/resources/legacy/v1/contracts/design-audit.schema.json)（legacy resource）
- [`generation-job.schema.json`](../src/main/resources/legacy/v1/contracts/generation-job.schema.json)（legacy resource）
- [`image-input-evidence.schema.json`](../schemas/image-input-evidence.schema.json)（immutable IDは`/schemas/v1/...`）
- [`required-assets.schema.json`](../schemas/required-assets.schema.json)（immutable IDは`/schemas/v1/...`）
- [`structure-placements.schema.json`](../src/main/resources/legacy/v1/contracts/structure-placements.schema.json)（legacy resource）

v1 schemaはimmutable legacy contractとして維持する。新規作成には使わず、既存artifactのread／verify／migrateのみが利用する。破壊的変更は同じ`$id`を書き換えず、新しいschema versionとmigrationを追加する。
