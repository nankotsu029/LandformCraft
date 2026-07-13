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

AI出力は非信頼入力なので、湾／岬は各64、川16、湖64、海深512、遠浅幅1000、structure総数256のhard limitをrecordとschemaの両方で課します。zone IDは一意、structureのzone参照は実在必須、zone areaShare合計は1.0以下です。topologyと海・川・湖の最低限の整合もrecordで検査します。

### `WorldBlueprint`

検証・正規化済みIntentをgenerator用にコンパイルした設計です。少なくともrequest ID、bounds、seed、tile size、logical resolution、generator versionを固定します。

Blueprint以降でAI応答を参照せずに生成できることが要件です。

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

- [`generation-request.schema.json`](../schemas/generation-request.schema.json)（immutable IDは`/schemas/v1/...`）
- [`terrain-intent.schema.json`](../schemas/terrain-intent.schema.json)（immutable IDは`/schemas/v1/...`）
- [`world-blueprint.schema.json`](../schemas/world-blueprint.schema.json)（immutable IDは`/schemas/v1/...`）
- [`export-manifest.schema.json`](../schemas/export-manifest.schema.json)（immutable IDは`/schemas/v1/...`）
- [`placement-journal.schema.json`](../schemas/placement-journal.schema.json)（immutable IDは`/schemas/v1/...`）
- [`design-audit.schema.json`](../schemas/design-audit.schema.json)（immutable IDは`/schemas/v1/...`）
- [`generation-job.schema.json`](../schemas/generation-job.schema.json)（immutable IDは`/schemas/v1/...`）
- [`image-input-evidence.schema.json`](../schemas/image-input-evidence.schema.json)（immutable IDは`/schemas/v1/...`）
- [`required-assets.schema.json`](../schemas/required-assets.schema.json)（immutable IDは`/schemas/v1/...`）
- [`structure-placements.schema.json`](../schemas/structure-placements.schema.json)（immutable IDは`/schemas/v1/...`）

v1 schemaは`0.1.0-SNAPSHOT`のPhase 0〜6実装で相互整合を確定しました。最初の安定版公開後の破壊的変更は同じ`$id`を書き換えず、新しいschema versionとmigrationを追加します。
