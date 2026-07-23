# 画像由来Constraint Map

> Status: V2-1〜V2-3のmap contractと回帰を完了した。geology／lithology／strata／climate planはいずれも画像constraintの意味、field index、security envelopeを変更していない。2026-07-17再設計で、通常画像からの**決定論的（AI非依存）抽出**がTrack B（V2-7）として追加され、`V2-7-01`〜`V2-7-07`（抽出core／secure封筒／draft artifact＋confidence preview／明示昇格／height／zone／多入力競合＋Phase gate）を完了した（15節、[Phase gate audit](audits/v2-7-phase-gate.md)）。抽出経路はSUPPORTED候補・runtime `EXPERIMENTAL`。`V2-14-01`（2026-07-21）でCLI `v2 extract`／`v2 promote`と`v2 request constraint-map`宣言連携を配線し、CLIから抽出→昇格→request宣言→`v2 export`到達が可能になった（16節）。`V2-19-04`（2026-07-23）が3 role共通の`request constraint-source`と`intent bind`／`intent bindings`を追加し、`V2-19-06`（同日）が`HEIGHT_GUIDE`をmacro foundationのbackground elevation sourceとして接続した（5.2節）。surface exportのconstraint map要求はrole別（`LAND_WATER_MASK`ちょうど1＋`HEIGHT_GUIDE`任意1）である。Paper UIは未接続、`SUPPORTED`昇格は別承認。AIによるdraft生成は引き続き未実装で、実装する場合も同じdraft／明示昇格契約を通す。詳細は [Task Index](task-index.md) と [ADR 0017](../adr/0017-deterministic-image-mask-extraction.md) を参照する。

## 1. 結論

v2では画像入力を2経路へ分ける。

```text
Reference image
→ AIが意味を解釈
→ confidence付きTerrainIntent draft

Constraint map
→ source/encodingのdeterministic decoder
→ canonical field / curve artifact
→ TerrainIntentの一意なConstraintMapBinding
→ AIを介さずWorldBlueprintへcompile
```

通常写真、mood board、斜視図はreference imageである。座標対応されたland-water mask、zone label、height guide、river path等はconstraint mapである。両者を同じ「画像role」として扱わない。

Request v2の `GenerationRequestV2.ReferenceImageRole`（AI提案入力専用、座標constraint生成禁止）は次の値を持つ。すべてAIへ渡すpromptの意味付けだけを担い、pixel→座標対応・height guide・mask・curveを生成しない。決定論的constraintは常にconstraint map経路を用いる。

| Role | 用途 |
|---|---|
| `MOOD_REFERENCE` | 色、荒々しさ、植生、雰囲気 |
| `TOP_DOWN_SKETCH` | north-upのsoft sketch（image top=north/-Z、right=east/+X）。pixelからHARD constraint mapを生成しない |
| `MATERIAL_REFERENCE` | 表面素材感（岩・砂・植物など） |
| `STRUCTURE_REFERENCE` | 少量の人工物の外観 |
| `OBLIQUE_TERRAIN_REFERENCE` | 斜視（perspective）地形view。top-downへ自動変換せず、座標・HARD geometry・尾根裏／地下の未確認地形を推定しない（`V2-14-02`） |
| `MULTI_VIEW_REFERENCE` | 同一地点の複数視点。view間で座標・HARD geometryをtriangulateせず、未確認地下地形を推定しない（`V2-14-02`） |

例: [examples/v2/diagnostic/oblique-multi-view.request-v2.json](../../examples/v2/diagnostic/oblique-multi-view.request-v2.json)。checksum影響監査は [audits/v2-14-02-reference-role-checksum-audit.md](audits/v2-14-02-reference-role-checksum-audit.md)。

## 2. 現行実装から流用するもの

`ReferenceImageProcessor` の次の安全境界は維持する。

- 許可root、real path、symlink、hardlink、regular file検査
- magic byteとdecoder結果の整合
- byte、pixel、frame、寸法、縦横比の上限
- EXIF orientation処理とmetadata除去
- TOCTOU対策、cancel、redacted error
- image evidenceとchecksum

ただし現行processorはAIへ送るARGB画像のcanonical化が目的である。16-bit height、exact label、no-data、数値scaleを保持するsemantic decoderとしてそのまま流用しない。security envelopeを共有し、decode実装を分ける。

## 3. Role catalog

V2-1で実装済みなのは先頭3 roleだけである。残りは後続Phaseの設計catalogであり、Request v2の現行strict unionでは受理しない。

生成側consumerを持つのは`LAND_WATER_MASK`（`V2-18-09`）と`HEIGHT_GUIDE`（`V2-19-06`）の2 roleだけである。`ZONE_LABEL_MAP`は宣言・binding・検証まで可能だがconsumerを持たず、surface exportは宣言されたbindingを受理せず拒否する（無視しない）。

| Role | 型 | sampling | 用途 | 生成側consumer |
|---|---|---|---|---|
| `LAND_WATER_MASK` | categorical U8/U16 | nearest | hard/soft海陸境界 | macro foundation（medium、必須1件） |
| `ZONE_LABEL_MAP` | categorical U8/U16 | nearest | region／feature assignment | なし |
| `HEIGHT_GUIDE` | continuous U8/U16 | fixed bilinear | 相対／絶対標高guide | macro foundation（background elevation、任意1件） |
| `COASTLINE_REFERENCE` | binary/vector | nearest/vectorize | coast curve corridor | なし（後続Phase設計） |
| `RIVER_PATH_REFERENCE` | binary/vector | vectorize | river centerline／route cost | なし（後続Phase設計） |
| `FLOW_DIRECTION_GUIDE` | vector/angle | fixed vector interpolation | drainage direction bias | なし（後続Phase設計） |
| `RIDGE_LINE_REFERENCE` | binary/vector | vectorize | ridge corridor | なし（後続Phase設計） |
| `FEATURE_MASK_REFERENCE` | categorical | nearest | featureごとのregion | なし（後続Phase設計） |
| `CAVE_MASK_REFERENCE` | 2D/stack descriptor | nearest | volume footprint／slice | なし（後続Phase設計） |
| `VEGETATION_REFERENCE` | categorical/continuous | nearest/fixed | habitat／density guide | なし（後続Phase設計） |
| `GEOLOGY_REFERENCE` | categorical | nearest | lithology province | なし（後続Phase設計） |
| `MATERIAL_REFERENCE` | categorical | nearest | semantic material guide | なし（後続Phase設計） |

unknown roleを汎用RGB画像として推測しない。

## 4. Source descriptorとbinding

Request v2のsource descriptorはraw source、decode、座標対応だけを所有する。

```json
{
  "sourceId": "constraint-source:height-main",
  "file": "inputs/height-16.png",
  "expectedSha256": "0000000000000000000000000000000000000000000000000000000000000000",
  "expectedWidth": 400,
  "expectedLength": 400,
  "decoderKind": "HEIGHT_RASTER",
  "coordinateMapping": {
    "origin": "NORTH_WEST",
    "xAxis": "EAST",
    "zAxis": "SOUTH",
    "pixelReference": "PIXEL_CENTER",
    "aspectMismatchPolicy": "REJECT",
    "rotation": "DEGREES_0",
    "flipX": false,
    "flipZ": false,
    "crop": { "x": 0, "z": 0, "width": 400, "length": 400 }
  },
  "encoding": {
    "kind": "HEIGHT",
    "encodingVersion": 1,
    "sampleType": "U16",
    "channel": "GRAY",
    "noData": { "mode": "SENTINEL", "sample": 65535 },
    "valueScaleMillionths": 3906,
    "valueOffsetMillionths": 0,
    "valueMeaning": "BLOCKS_ABOVE_REQUEST_MIN_Y",
    "validSampleRange": { "minimum": 0, "maximum": 65534 }
  }
}
```

これはV2-1の `generation-request-v2.schema.json` に対応する契約例である。decoder kindごとの `encoding` はstrict unionであり、不要fieldを拒否する。`BLOCKS_ABOVE_REQUEST_MIN_Y` は同じRequestの検証済みboundsを指し、valid sample rangeから得られる値がbounds外ならdecode前の契約検証で拒否する。

constraint mapの`file`は`[A-Za-z0-9._/-]{1,512}`に限定し、absolute path、backslash、空segment、`.`／`..` segment、URL／base64をRequest parse時点で拒否する。さらに宣言寸法から全mapのpixel数と最小decoded sample byte数を合算し、Requestの`maximumPixels`／`maximumDecodedBytes`を超える契約はsourceを開く前に拒否する。source fileの実byte数、decoder working set、sidecar artifact byte数は、それぞれsecure loader、decoder、compiler／writerでも再検査する。

decode成功後、freezeされたTerrainIntent v2の `mapReferences` が唯一のsemantic bindingを所有する。

```json
{
  "id": "height-main-binding",
  "sourceId": "constraint-source:height-main",
  "artifactId": "constraint:height-guide:sha256-0000000000000000000000000000000000000000000000000000000000000000",
  "role": "HEIGHT_GUIDE",
  "strength": "SOFT",
  "weight": 0.8,
  "sampling": "BILINEAR_FIXED",
  "toleranceBlocks": 4
}
```

Request descriptorにrole、strength、weight、toleranceを複製しない。Blueprint compilerはbindingの `artifactId` とsemantic設定だけを1回適用し、`sourceId` はprovenance追跡だけに使う。

## 5. Canonicalization pipeline

```text
path/security validation
→ format-specific safe decode
→ declared quarter-turn / flip / crop mapping
→ decoder-specific sample validation
→ crop/resample/vectorize
→ fixed-point canonical field/geometry
→ IntentのConstraintMapBindingと結合
→ semantic checksum and provenance
→ Design/Release artifact
```

V2-1の実装は `SecureConstraintMapSourceLoader`、`NumericPngDecoder`、`ConstraintMapSamplerV2`、`ManualConstraintMapGenerationServiceV2` でこの境界を構成する。AI向け `ReferenceImageProcessor` のARGB／EXIF正規化コードは呼ばない。入力はgrayscale non-interlaced PNGのU8／U16だけで、categoricalとheightのraw sampleを色変換せず読む。

### 5.1 Categorical map

- color profile変換やantialiasを勝手に適用しない。
- source sample→labelのexact辞書を検査し、未知sampleを拒否する。近い色や近いzoneへのfallbackは行わない。
- resampleはnearestのみ。
- 現行V2-1はgrayscale sampleだけを受理する。RGB label mapは将来別encoding versionで定義するまで受理しない。
- no-data holeの補間はhard mapでは禁止し、soft mapでも明示policyを要求する。

### 5.2 Height guide

- 現行V2-1は8-bit／16-bit grayscaleを明示的に扱う。raw grid等は将来の別decoderである。
- min/max Y、relative/absolute、scale/offset、no-dataを必須化する。
- sRGB gammaを数値標高へ適用しない。
- canonical valueはsigned I32のblock-Y millionthsへ変換する。
- 補間、clamp、roundingをversion化する。
- hard heightはblock単位toleranceを要求する。

heightの意味は`ABSOLUTE_BLOCK_Y`、`BLOCKS_ABOVE_REQUEST_MIN_Y`、`BLOCKS_RELATIVE_TO_WATER_LEVEL`のいずれかをRequestで必須にする。scale／offsetとvalid sample rangeをこの基準へ適用した全域がRequest bounds内に入ることをdecode前に検査し、画像内容から意味を推測しない。

**生成側consumer（`V2-19-06`、2026-07-23）:** `HEIGHT_GUIDE`は macro foundation stage（[ADR 0038](../adr/0038-macro-foundation-contract.md) D2-2の明示source (a)）が**background elevation source**として読む。優先順位はcellごとに guide＞request宣言のmedium別base levelで、guideがno-dataを宣言したcellだけがbase levelへfallbackする。逆転もblendも行わない。guide値がRequestのvertical extentを外れる場合はclampせず拒否する（宣言範囲は上記のdecode前検査、per-cellは`v2.foundation.height-guide-out-of-contract`）。surface modifier（coastal 4種）が所有するcellの高さはmodifierのものであり（ADR 0038 D5-3）、HARD guideが同じcellをtolerance超で指定した場合はHARD同士の矛盾として`v2.foundation.height-guide-modifier-conflict`で拒否、SOFT guideはmodifierへ譲りその差をresidual fieldとconformance residualへ記録する。resamplingはcanonical登録（`NEAREST`／`BILINEAR_FIXED`）が行い、新しい補間規則を追加しない。

### 5.3 Curve extraction（V2-1未実装）

binary lineから次を固定順で行う。

1. label component検査
2. bounded thinning／skeletonization
3. junctionとendpoint抽出
4. graph構築
5. fixed toleranceでpolyline simplification
6. normalized splineへのcompile

曖昧な分岐、閉路、過剰componentはwarningではなくrole policyに従って拒否する。元maskと抽出curveのresidual previewを出す。

### 5.4 Flow direction（V2-1未実装）

RGB color wheel等の曖昧な暗黙表現を使わず、角度またはX/Z成分のencodingをdescriptorに記す。zero vector、no-data、正規化、座標rotationを明示する。これはhard river pathではなくrouting cost guideを基本とする。

## 6. 座標対応

現行V2-1は原点をnorth-west、`+X`をeast、`+Z`をsouth、pixel referenceをcenter、aspect policyを`REJECT`へ固定する。座標変換の意味順は次のとおりである。

1. raw rasterを宣言した`DEGREES_0/90/180/270`だけ時計回りにquarter-turnする。
2. 回転後の座標空間で`flipX`、次に`flipZ`を適用する。
3. 回転・flip後の座標空間から`crop`を切り出す。
4. target cell centerをcropのpixel center座標へ写像し、categoricalはnearest、heightは宣言したnearestまたはfixed-point bilinearでsampleする。

nearestは`floor(((2*target+1)*sourceExtent)/(2*targetExtent))`を末端pixelへclampする。bilinearは`((target+0.5)*sourceExtent/targetExtent)-0.5`をmillionths weightへ量子化し、外側半cellは末端sampleへ固定する。各linear interpolationはinteger演算でhalf-away-from-zeroへ丸め、4点のいずれかがno-dataなら結果もno-dataにする。whole生成とtile生成は同じglobal target X/Zをこの式へ渡す。

少なくとも次をsource descriptorへ固定する。

- source pixelの原点と軸
- bounds全体かsubregionか
- rotation、flip、crop
- pixel center／pixel edgeの定義
- aspect mismatch policy（現行は`REJECT`だけ）
- normalized→block量子化
- Y encodingとwater level基準

現行 `TOP_DOWN_SKETCH` の四辺water ratioはprovenanceとして残せるが、v2の直接mapでは全pixel対応を正本にする。

## 7. Hard / Softと競合

draftをcanonical Intentへ統合する前の提案priority:

1. explicit manual hard geometry／constraint
2. hard canonical map
3. explicit manual soft geometry
4. soft canonical map
5. AI-derived soft suggestion
6. preset default

このpriorityは、どのdraft提案を採用、破棄、または明示的にSOFTへするかをユーザーへ提示するためだけに使う。freeze済みIntentでは、出自やpriorityに関係なくhard land-water mapとhard polygonが矛盾すればcompile errorにする。低位のHARDを高位のHARDで上書きしない。提案を破棄／降格する場合はfreeze前に明示し、provenanceへ残す。

soft mapは `desired field`、weight、tolerance、falloffを持ち、actualとの差をresidual fieldへ保存する。generatorはsoft mapを完全copyする必要はないが、無視した場所と理由をvalidation reportへ出す。

## 8. 通常画像から地形を生成する

### 8.1 対応可能なこと

- beach、cliff、mountain、river、vegetation等のfeature候補
- 画面内の相対配置とocclusion-aware region
- skyline、coastline、ridgeらしいcurve候補
- 乾湿、岩質、植生密度等のsemantic hint
- style／moodに対応するsoft preset

### 8.2 1枚から確定できないこと

- hidden sideを含む正確なtop-down mask
- 実block尺度と絶対標高
- 遮蔽された河川接続
- 地下cave network
- camera投影が不明なpixel→world対応

したがって通常画像の出力はconfidence付きsuggestionである。

```text
photo
→ sanitized AI input
→ feature/region/curve candidates + confidence + ambiguity
→ draft Intent and soft maps
→ overlay preview / missing-information warning
→ user confirmation or explicit edits
→ canonical artifact freeze
```

ユーザー確認後はAIを再呼び出さず、freezeされたIntent／map checksumから同じBlueprintを作る。写真そのものをReleaseの必須生成入力にする場合は、privacy、portable package size、licenseを別ADRで決める。

## 9. Artifactとprovenance

V2-1のmanual bundleは次を保存する。これはReleaseではなく、V2-2以降のoffline compilerが読むcanonical input bundleである。

```text
fields/index.json
fields/<sequence>-desired-<role>.lfgrid
fields/<sequence>-actual-<role>.lfgrid
fields/<sequence>-residual-<role>.lfgrid
previews/<fixed diagnostic name>.png
```

`ZONE_LABEL_MAP` はdesired fieldだけを持ち、source sample→canonical ID→label辞書を`fields/index.json`へ保存する。land-waterとheightはdesired／actual／residualを持つ。1000×1000の値はJSONへ埋め込まず、[ADR 0011](../adr/0011-compact-field-sidecar.md) の非圧縮`LFC_GRID_V1` sidecarへrow-major big-endianで保存する。indexはexclude-selfのcanonical checksum、Request／Intent checksum、binding、field descriptor、artifact／semantic checksum、provenanceをstrictに検証する。

将来のDesign Package／Release format 2では少なくとも次の配置へ収容する。

```text
constraints/index.json
constraints/maps/<id>.lfgrid
constraints/geometries/<id>.json
constraints/provenance/<id>.json
```

provenance:

- source evidence checksum
- decoder/version
- decoder／coordinate-transform algorithm version
- palette/scale/no-data
- manual／AI／derived
- AI model response IDではなくcanonical output checksum
- warnings、confidence、confirmation state

absolute source path、EXIF、secret、Authorization、raw Provider payloadをmanifestへ保存しない。

field descriptorの`transformId`は`constraint-pixel-center-fixed-v1`というalgorithm IDであり、rotation／flip／cropのparameterそのものではない。具体的なmapping、encoding、scale、no-dataはcanonical source Requestに保存し、`fields/index.json`の`sourceRequestChecksum`で改変不能にbindingする。descriptor単体からparameterを補完推測しない。

## 10. Previewとdiagnostic

V2-1で固定したfile名は次の8個である。

- `desired-land-water.png`
- `actual-land-water.png`
- `residual-land-water.png`
- `desired-height.png`
- `actual-height.png`
- `residual-height.png`
- `zone-label-map.png`
- `constraint-errors.png`

rendererは1枚ずつARGB bufferを生成・解放し、8枚すべてが成功した場合だけpreview directoryをstaging内でatomic moveする。外側のmanual bundleも全sidecar、sealed index、preview、budget、strict read-backを完了し、最後のcancel check後にtargetへatomic moveする。この外側moveをcommit pointとし、move後に届いたcancelでcommitted bundleを削除しない。move前のcancel、PNG失敗、budget超過、read-back失敗ではcanonical targetを公開しない。

- source thumbnail（metadata除去済み、必要時のみ）
- canonical mapとlegend
- map coverage/no-data
- vectorized curve overlay
- desired vs actual
- constraint residual magnitude
- hard violation location
- coordinate axes、bounds、crop
- AI confidence／unresolved region

hard mapはpixel-perfect previewだけでなく、block量子化後の実際の境界をoverlayする。

## 11. Securityとresource budget

V2-1のnumeric pathでは、portable relative pathだけを許し、absolute／traversal／backslash／URL／base64、rootまたは途中segmentのsymlink、同一Request内のhardlink alias、非regular fileを拒否する。magic byte、PNG chunk順・CRC、single frame、grayscale sample model、寸法、aspect、pixel、source／decoded／resident／artifact byte、read前後のsize／mtime／file key、source SHA-256を検査する。未知critical chunk、APNG、interlace、RGB／palette、truncated／trailing payload、future decoder／encoding versionは拒否する。

hardlink policyは「同一Requestの複数sourceが同じfile identityを別名参照することを禁止」である。platformがfile keyを提供しない場合も`Files.isSameFile`でrequest-local aliasを照合する。外部からの既存hardlink数だけをportableに断定せず、解決済みsource identityの重複をpolicy違反とする。

Request budgetはtrusted ceilingを拡張できない。上限はmap 32、source 1枚8 MiB／合計32 MiB、1辺4096、縦横比32:1、1枚4,000,000 pixel／合計16,000,000 pixel、decoded sample 1枚8 MiB／合計32 MiB、decoder working 32 MiB、artifact 64 MiB、constraint stage resident 96 MiBである。全sourceをstat／identity／合計byteまでpreflightし、aggregate admissionが通る前にはsource byteを読み始めない。

- decompression bomb、過大chunk、multi-frame、truncated PNGをalloc前後で検査する。
- 16-bit sampleをARGBへ丸めず、sample modelとpayload lengthを検査する。
- ICC、EXIF、text chunk、filenameをsemantic値に使わない。
- ZIP内path traversal、duplicate normalized path、symlink、hardlinkを既存原則で拒否する。
- label数、map count、total decoded bytesへ上限を持つ。curve component／control pointはその機能を実装するPhaseで上限を追加する。
- 将来のvectorizationとdistance transformはbounded CPU executorで行い、cancelを確認する。
- Paper main threadでdecode、AI、map compile、artifact I/Oを行わない。

## 12. Test戦略

V2-1ではU8 mask、U16 height、U16 label、no-data、rotation／flip／crop、pixel center、nearest／fixed bilinear、hard一致、soft residual、whole／tile／seam／thread／locale／timezone、1000角budget、cancel cleanupと、malformed／unknown label／path／link／TOCTOU／checksum／future version／budget／hard conflictのnegative testを実装済みである。curve抽出やAI-derived artifactのtestは、その機能を実装する後続Phaseで追加する。

- role別contract fixture: valid／unknown label／no-data／bad scale
- 1、8、16-bit、alpha、palette、interlace、orientationのformat fixture
- malformed/truncated/decompression bomb、path、link、TOCTOU test
- categorical nearestがlabelを混ぜないtest
- height fixed-point round-tripとendianness test
- curve extractionのjunction、loop、noise、simplification golden test
- rotation/flip/cropのcorner marker test
- whole/tileのconstraint sample一致
- hard conflictとsoft residualのnegative fixture
- AI-derived artifactにraw metadata／secretが残らないtest

## 13. 代替案

| 案 | 判断 |
|---|---|
| すべてAIへ見せてIntentへ要約 | 現行互換だが形状情報を失う。reference image専用に残す |
| すべての画像をhard map扱い | 写真や曖昧sketchには危険で不採用 |
| PNGだけをcanonical artifactにする | gamma／color／16-bit／decoder差が残るため不採用 |
| role別decode→fixed field/geometry | 推奨。PNG等は入力、canonical artifactが正本 |

通常画像と精密mapを同じ精度で扱わないことが、画像入力を強くするための最重要境界である。

## 14. Manual fixture

`examples/v2/manual-constraint-island/` はRequest／Intentの契約fixtureである。actual PNG、checksum、canonical artifact IDのfreezeと、mask＋height guide＋zone label mapからのAI非経由生成は `ManualConstraintMapGenerationServiceV2Test` が一時directory上で実行する。公開CLI／Paper commandにはまだ接続していないため、example JSONのplaceholder checksumだけを使って生成できるとは表現しない。

## 15. 決定論的抽出（V2-7、2026-07-17追加）

reference image（AI解釈）とconstraint map（外部準備の数値PNG）の間に、第3の経路を追加する。

```text
通常画像 / スケッチ
→ SecureImageExtractionEnvelopeV2（path/link/magic/byte/pixel/frame/EXIF、TOCTOU、budget）
→ SanitizedArgbImageV2（oriented TYPE_INT_ARGB + raw-file SHA-256）
→ ImageLandWaterExtractorV2（image-land-water-extract-v1、integer-only固定閾値）
→ ExtractedMaskDraftV2（WATER/LAND/UNKNOWN + 信頼度0..255 + semantic checksum）
→ ExtractedMaskDraftArtifactPublisherV2（classes.u8 / confidence.u8 + extracted-mask-draft-v2.json）
→ ExtractedMaskDraftPreviewRendererV2（class / confidence / unknown PNG + strict index）
→ ExtractedMaskPromotionServiceV2（明示閾値＋UNKNOWN処理 → land-water.png + promotion provenance）
→ V2-1 strict decoderを再経由した通常のconstraint map

並行（V2-7-05）:
通常画像 / スケッチ
→ SecureImageExtractionEnvelopeV2.loadAndExtractHeightGuide
→ ImageHeightGuideExtractorV2（image-height-guide-extract-v1）
→ ExtractedHeightGuideDraftV2
→ ExtractedHeightGuideDraftArtifactPublisherV2
→ ExtractedHeightGuidePromotionServiceV2（明示 HeightValueMeaning → height-guide.png）
→ V2-1 HEIGHT_RASTER + residual

並行（V2-7-06）:
通常画像 / スケッチ
→ SecureImageExtractionEnvelopeV2.loadAndExtractZoneLabel
→ ImageZoneLabelExtractorV2（image-zone-label-extract-v1、固定palette距離量子化）
→ ExtractedZoneLabelDraftV2（label index / UNKNOWN + confidence + proposedLabels）
→ ExtractedZoneLabelDraftArtifactPublisherV2（labels.u8 / confidence.u8 + extracted-zone-label-draft-v2.json）
→ ExtractedZoneLabelPromotionServiceV2（明示 confidence閾値＋noData → zone-labels.png）
→ V2-1 CATEGORICAL + ZONE_LABEL_MAP
```

`V2-7-02`実装状態:

- `SecureImageExtractionEnvelopeV2`がrequest root配下のportable relative pathだけを受理し、symlink・同一Request内hardlink alias・magic/extension不一致・TOCTOU・APNG `acTL` multi-frame・corrupt decodeを拒否する。
- PNG／JPEGのみ。EXIF orientationはARGBへ反映し、metadataをsemantic値に使わない。
- trusted ceilingはsource 8 MiB／合計32 MiB、1辺4096、縦横比32、1枚4M／合計16M pixel、decode working 64 MiB（ARGB＋orientation一時コピー見積）。呼び出し側から天井を上げられない。
- `loadAndExtractLandWater`がfile→draftのstrict経路を提供する。provenanceは raw file SHA-256 → `draft.sourceChecksum` → `draft.semanticChecksum`。
- CLI／Paper／Request Schemaへは未接続のまま`EXPERIMENTAL`である。

`V2-7-03`実装状態:

- draft artifactは`extracted-mask-draft-v2.json`＋固定sidecar（`classes.u8`／`confidence.u8`）。staging→strict read-back→atomic publish。extra／missing／checksum改変／symlinkを拒否する。
- diagnostic previewは固定3層（CLASS／CONFIDENCE／UNKNOWN）、palette `extracted-mask-draft-palette-v1`、1枚ずつrender／flush、PNG 8 MiB/層、strict index read-back。
- Schema／exampleは`schemas/extracted-mask-draft-v2.schema.json`、`schemas/extracted-mask-draft-preview-index-v2.schema.json`、`examples/v2/extract/`。
- 昇格・Release capability・CLI／Paper／Requestは未接続のまま`EXPERIMENTAL`である。

`V2-7-04`実装状態:

- `ExtractedMaskPromotionServiceV2`がconfidence閾値（0..255）とUNKNOWN処理（`REJECT`／`MAP_TO_WATER`／`MAP_TO_LAND`／`MAP_TO_NODATA`）の明示指定を必須とする。閾値未満のLAND／WATERはUNKNOWN扱いに落とす。
- 昇格bundleは`extracted-mask-promotion-v2.json`＋`land-water.png`（U8 grayscale、water=0／land=1、必要時no-data sentinel）。staging→`SecureConstraintMapSourceLoader`＋`NumericPngDecoder`再検証→atomic publish。
- provenance連鎖は source checksum → draft semantic checksum → map SHA-256 → record canonical checksum。
- Schema／exampleは`schemas/extracted-mask-promotion-v2.schema.json`、`examples/v2/extract/extracted-mask-promotion-v2.json`。
- CLI／Paper／Request／Release capabilityは未接続のまま`EXPERIMENTAL`である。暗黙昇格は禁止のまま。

`V2-7-05`実装状態:

- `ImageHeightGuideExtractorV2`（`image-height-guide-extract-v1`）がinteger-only輝度 `(77R+150G+29B)>>8` からU8 sampleを作り、255はno-data sentinel用に254へclampする。alpha < 128はno-data（confidence 0）。
- sample空間宣言は`luminance-u8-requires-explicit-height-value-meaning-v1`。V2-1の3種`HeightValueMeaning`（`ABSOLUTE_BLOCK_Y`／`BLOCKS_ABOVE_REQUEST_MIN_Y`／`BLOCKS_RELATIVE_TO_WATER_LEVEL`）は昇格時に明示し、画像から推測しない。
- draft artifactは`extracted-height-guide-draft-v2.json`＋`samples.u8`／`confidence.u8`。昇格は`ExtractedHeightGuidePromotionServiceV2`→`height-guide.png`＋provenanceで、V2-1 decoderと`CanonicalConstraintRasterV2` residual一貫を検証する。
- Schema／exampleは`schemas/extracted-height-guide-draft-v2.schema.json`、`schemas/extracted-height-guide-promotion-v2.schema.json`、`examples/v2/extract/`。
- CLI／Paper／Request未接続、`EXPERIMENTAL`のまま。

`V2-7-06`実装状態:

- `ImageZoneLabelExtractorV2`（`image-zone-label-extract-v1`）が固定sketch palette（shore／upland／wetland／rock）への整数平方ユークリッド距離でlabel index＋confidenceを抽出し、ambiguous帯・遠色・alpha < 128をUNKNOWNとする。k-means等の反復クラスタリングは禁止。
- sample空間は`fixed-palette-distance-quantize-requires-explicit-categorical-encoding-v1`。proposedLabelsは提案slugのみで、AIによる命名は行わない。
- draft artifactは`extracted-zone-label-draft-v2.json`＋`labels.u8`／`confidence.u8`。昇格は`ExtractedZoneLabelPromotionServiceV2`がconfidence閾値とnoDataを明示し、`zone-labels.png`をV2-1 categorical decoder＋`ZONE_LABEL_MAP` canonical経路で再検証してatomic publishする。
- Schema／exampleは`schemas/extracted-zone-label-draft-v2.schema.json`、`schemas/extracted-zone-label-promotion-v2.schema.json`、`examples/v2/extract/`。
- CLI／Paper／Request未接続、`EXPERIMENTAL`のまま。

`V2-7-07`実装状態:

- `MultiSourceReconciliationServiceV2`（`image-fidelity-multisource-reconcile-v1`）が`image-constraint-priority-v1`（§7と同順: manual hard→hard map→manual soft→soft map→image draft→prompt soft→preset）で多入力を解決する。
- HARD/HARDおよび同rank SOFT peerの不一致はfail closed（結果no-data＋conflict code）。下位SOFTは抑制されsource-to-result metricへ記録する。last-write-wins禁止。
- 成果物は`result.u8`／`conflict.u8`／`source-diff.u8`＋`multi-source-reconciliation-v2.json`、診断previewは固定3層＋strict index。
- Phase gate監査は [audits/v2-7-phase-gate.md](audits/v2-7-phase-gate.md)。抽出経路は**SUPPORTED候補**、runtimeは`EXPERIMENTAL`・CLI／Paper／Request未接続・Release capability追加なし。

不変条件:

- 抽出はAIを呼ばず、同一入力から常に同一のdraft checksumを再生する。閾値変更は新algorithm versionとして追加する。
- draftはSOFT提案であり、hard constraintへの暗黙昇格・fallback昇格を禁止する。昇格はUNKNOWN処理と信頼度閾値を明示指定した操作のみとする（heightは加えて`HeightValueMeaning`／scale／offsetの明示）。
- provenanceはsource checksum → draft semantic checksum → constraint map checksumの連鎖で検証可能にする。
- admissionはtrusted ceiling（envelope: 4096px/辺・4M px/枚・working 64 MiB、extractor: 16Mピクセル・working 64 MiB、promotion: V2-1の4M px／8 MiB map、residual連携はRequest bounds寸法≤1000）を割当前に検査し、cancelは読込／行単位で観測する。

複数入力の競合解決とsource-to-result差分はV2-7-07で実装済みである（上記）。実装状態は [docs/roadmap.md](../roadmap.md) と [V2-7 Phase gate audit](audits/v2-7-phase-gate.md) を正本とする。

## 16. CLI配線（V2-14-01、2026-07-21追加）

V2-7の抽出→明示昇格経路をCLIへ接続し、利用者がCLIから到達できるようにした。runtimeは引き続き`EXPERIMENTAL`であり、`SUPPORTED`昇格は別承認（`V2-14-02`以降）とする。

```text
通常画像 / スケッチ
→ v2 extract <land-water|height-guide|zone-label> <image-file> <draft-output-dir>
    （SecureImageExtractionEnvelopeV2 → extractor → draft artifact publisher）
→ v2 promote land-water  <draft-dir> <output-dir> <confidence-threshold> <reject|water|land|nodata> [nodata-sample]
  v2 promote height-guide <draft-dir> <output-dir> <request-v2.json> <confidence-threshold>
                          <absolute-block-y|blocks-above-min-y|blocks-relative-to-water> <scale-millionths> <offset-millionths>
  v2 promote zone-label   <draft-dir> <output-dir> <request-v2.json> <confidence-threshold> [nodata-sample]
    （draft loadDraft → V2-7 promotion service → V2-1 strict decoder再検証 → land-water.png / height-guide.png / zone-labels.png）
→ v2 request constraint-map <request-id> <source-slug> <file> <sha256> <width> <length>
    （昇格出力のsha256／dimensionsをrequestのconstraint map source宣言へ渡す）
→ v2 export …（宣言済みconstraint map sourceを消費）
```

- 両verbはBukkit非依存backend `core.v2.ImageExtractionWorkflowServiceV2` を経由する。CLIは token→引数の写像だけを行い、生成処理を所有しない。
- `extract`／`promote` はoperator workstationの任意image path／artifact pathを読むため、`migrate` と同様に**CLI専用**（Paperからは安定code `V2_CLI_ONLY`）である。permission node は `landformcraft.v2.extract`。
- 昇格は常にconfidence閾値とUNKNOWN／height／zone処理の明示指定を要求する。height昇格は`request-v2.json`のboundsに対して量子化・residual一貫を検証し、`HeightValueMeaning`／scale／offsetを画像から推測しない。
- 暗黙昇格・fallback昇格は行わない。draftはSOFT提案のままで、明示`promote`だけがconstraint mapを生成する。
- provenance連鎖（source checksum → draft semantic checksum → constraint map sha256）はV2-7のpromotion recordがそのまま保持する。CLIは追加のsecret／raw pathを出力しない。
