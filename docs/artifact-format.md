# Release Package形式

## Phase 4／5 Design Package

AI／manual importの出力は、生成前に独立した小さなDesign Packageとして公開します。

```text
designs/<request-id>/<job-uuid>/
├── terrain-intent.json
├── audit.json
├── image-evidence.json
└── checksums.sha256
```

`audit.json`は`design-audit.schema.json`に従い、provider、model、prompt version、response ID、usage、attempts、request／Intent checksumを保存します。APIキー、prompt本文、画像binary、HTTP headerは保存しません。

`image-evidence.json`は`image-input-evidence.schema.json`に従い、正規化versionとprovider／response／prompt version、画像ごとの安定ID、role、Provider送信有無、検証状態、source／正規化後のmedia type・byte数・pixel寸法・SHA-256、metadata検出、EXIF orientation、変換履歴を保存します。`TOP_DOWN_SKETCH`だけは、左上原点、右が`+X/EAST`、下が`+Z/SOUTH`である座標対応と四辺のwater ratio、文章との整合結果も保存します。元画像や正規化画像のbinaryはDesign Packageへ複製しません。

`checksums.sha256`は`audit.json`、`image-evidence.json`、`terrain-intent.json`を辞書順で完全に列挙し、追加・欠損fileを許可しません。Phase 4で公開済みの3-file packageはread-only互換として引き続き検証できますが、新規publisherは必ずPhase 5の4-file packageを作ります。

publisherは一時directoryへIntent、画像証跡、監査をwriteして厳格に読み戻し、job UUID directoryへatomic moveした後もdirectory identityを含めて再検証します。検証失敗時は公開directoryを除去します。`design-verify`は移送後にも同じ検査を行い、改変、欠損、未知file、job／request directory名の不一致、監査と画像証跡のrequest ID不一致に加え、evidenceのprovider／response／prompt versionとauditの不一致も拒否します。Design PackageがREADYでも地形生成やworld配置は自動実行しません。

## 正本

持ち運び単位は1個の巨大schematicではなく、manifest、設計、preview、tile schematic、checksumをまとめたRelease Packageです。

```text
exports/<request-id>/
├── <release-id>/
│   ├── manifest.json
│   ├── request.yml
│   ├── terrain-intent.json
│   ├── world-blueprint.json
│   ├── validation.json
│   ├── structures.json
│   ├── checksums.sha256
│   ├── README.txt
│   ├── previews/
│   │   ├── overview.png
│   │   ├── height.png
│   │   ├── water.png
│   │   ├── slope.png
│   │   ├── materials.png
│   │   ├── features.png
│   │   ├── structures.png
│   │   └── validation.png
│   ├── schematics/
│   │   ├── tile-00-00.schem
│   │   └── ...
│   └── assets/
│       ├── required-assets.json
│       └── schematics/
│           └── <asset-id>.schem
├── <release-id>.zip
└── <release-id>.zip.sha256
```

ZIPとその外側のdigestはdelivery用で、release directoryのsiblingです。manifestとchecksumで検証した展開directoryを内部の正本として扱い、自己包含やchecksum循環を作りません。

## Manifest必須field

```json
{
  "formatVersion": 1,
  "generatorVersion": "3.0.0-phase6",
  "minecraftVersion": "1.21.11",
  "requestId": "rocky-coast-001",
  "width": 1000,
  "length": 1000,
  "minY": -32,
  "maxY": 160,
  "tileSize": 128,
  "tileCountX": 8,
  "tileCountZ": 8,
  "anchor": "MINIMUM_CORNER",
  "seed": 827413,
  "tiles": []
}
```

各tileには次を保存します。

- ID、x/z index
- release-local origin X/Y/Z
- width、length、min/max Y
- `.schem`相対path
- `.schem` artifact SHA-256と、materialize前のtile地形semantic SHA-256
- block count／air exclusion policy
- status

現在の値は、`airPolicy: INCLUDED`、`status: READY`です。`blockCount`はairを含む `width * length * (maxY - minY + 1)` と一致しなければなりません。

## Schematic

- Sponge Schematic Specification v3、GZip圧縮NBT、拡張子`.schem`
- Minecraft 1.21.11の公式`DataVersion` 4671
- `Offset`は`[0, 0, 0]`。配置位置はmanifestのrelease-local `originX/Y/Z`を唯一の正本とする
- palette IDは0から連続し、block dataは `x + z * Width + y * Width * Length` 順のVarInt
- airも含む全cuboidを保存し、paste時に既存blockを確実に置換できる
- surface、3層までのsubsoil、stone、最下端bedrock、水、airを列ごとにmaterializeする
- tileの全block配列は保持せず、NBT byte arrayへ順次書き込む

paletteは地形用のair、bedrock、stone、dirt、grass block、sand、sandstone、gravel、mud、snow block、waterに加え、Phase 6 asset用のoak planks／log／fence、cobblestone、stone bricksを含みます。洞窟、植生block、block entity、biome、entityはまだ出力しません。

## Phase 6 structure artifact

`structures.json`はgenerator versionと、配置済みstructureごとのasset ID／semantic checksum／Minecraft version／type／anchor／rotation／回転後寸法／terrain-followingを保存します。`preferredZone`は探索時の希望条件で、成果物の実座標はこのfileを正本とします。

`assets/required-assets.json`は配置から実際に参照されたassetだけを辞書順に列挙し、type、Minecraft version、semantic checksum、standalone schematic path／artifact checksum、未回転寸法を保存します。asset `.schem`もtileと同じSponge v3／DataVersion 4671で、offsetはゼロです。

verifierは次をすべて要求します。

- placementがbuilt-in catalogのtype、version、semantic checksum、回転後寸法と一致する
- required asset集合がplacementの利用集合と完全一致する
- standalone asset `.schem`のartifact checksum、寸法、block entry数がcatalogと一致する
- placementがrelease bounds内にあり、互いに重ならない
- structureをmaterializeしたtile `.schem`を含む全artifact checksumが一致する

tileの`semanticChecksum`はPhase 2互換の地形列checksumです。structureを含む全体の再現性は`TerrainPlan.checksum`、`structures.json`のasset semantic checksum、tile `.schem`のartifact checksumで検証します。

仕様参照: [Sponge Schematic v3](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-3.md)、[WorldEdit schematic API example](https://worldedit.enginehub.org/en/7.3.19/api/examples/clipboard/#schematic-examples)

## 命名とpath

- request ID、release ID、tile IDはallowlist patternで検証する
- manifest内pathはrelease rootからの相対pathだけを許可する
- `..`、absolute path、symbolic linkによるroot外参照を拒否する
- 大文字小文字が異なる同一名を作らない
- temporary fileは完成名と別にし、fsync後にatomic moveする

## Checksum

- SHA-256を使用する
- `checksums.sha256` 自身を除く、manifest、structure placement、assetを含むすべての規範的artifactを列挙する
- siblingのdelivery ZIPは内部checksum対象外とし、別の `<release-id>.zip.sha256` で保護する
- pathはUTF-8、`/` separator、辞書順へcanonicalizeする
- apply前、転送後、ZIP展開後に再検証する
- Phase 2ではpreviewを含む規範的artifactの破損と、allowlist外の追加fileをすべて拒否する

現在のstrict verifierは、`checksums.sha256`にない追加fileも含めて拒否します。ZIPのsibling digestが存在する場合は展開前に検査し、移送先にdigestがないZIPでも内部artifact checksumとmanifestを検証します。

## Tile配置

`MINIMUM_CORNER` anchorでは、target originへtileのrelease-local originを加算します。端tileはtile sizeより小さくできます。重複、欠損、範囲外tile、異なるtile sizeをverifyで拒否します。

## Atomic publish

```text
temporary release directory
  → 全artifact write/close
  → checksum生成
  → 自己verify
  → final release directoryへatomic move
  → siblingのtemporary ZIPを生成・検証
  → ZIPと外部digestをatomic move
  → READY checkpoint
```

失敗したtemporary directoryはREADYとして公開しません。掃除は別jobで行い、診断に必要なmetadataを残します。

現在のCLI exportは一時生成directoryをcleanupし、release本体の失敗時は公開済みdirectory／ZIPも除去します。対象filesystemがatomic moveを提供しない場合は非atomicへfallbackせずexportを失敗させます。

## 互換性

- `formatVersion`はpackage構造とmanifest意味のversion
- `generatorVersion`は生成結果のversion
- `minecraftVersion`はblock state registry互換性の目安
- readerは対応しないfuture formatを推測で読み込まない
- block変換が必要な場合は明示的migrationを行い、新releaseを発行する

## 配置時artifact

Release自体は変更せず、Paper data directoryへ配置固有artifactを分離します。

```text
data/
├── placements/<placement-uuid>.json
└── snapshots/<placement-uuid>/tile-XX-ZZ.schem
```

placement journalは`placement-journal.schema.json` v1に従い、Release checksum、world UUID／name、target／bounds、state、確認hash／期限、tileごとのsnapshot相対path／SHA-256を保存します。snapshotはRelease tileと同じ寸法のSponge v3で、Undo／rollback前にchecksumを再検査します。journalは各world操作の前後にtemporary fileからatomic replaceします。
