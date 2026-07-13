# Release Package形式

## 正本

持ち運び単位は1個の巨大schematicではなく、manifest、設計、preview、tile schematic、checksumをまとめたRelease Packageです。

```text
exports/<request-id>/
├── <release-id>/
│   ├── manifest.json
│   ├── request.yml
│   ├── terrain-intent.json
│   ├── world-blueprint.json
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
│       └── required-assets.json
├── <release-id>.zip
└── <release-id>.zip.sha256
```

ZIPとその外側のdigestはdelivery用で、release directoryのsiblingです。manifestとchecksumで検証した展開directoryを内部の正本として扱い、自己包含やchecksum循環を作りません。

## Manifest必須field

```json
{
  "formatVersion": 1,
  "generatorVersion": "0.1.0",
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
- SHA-256
- block count／air exclusion policy
- status

## 命名とpath

- request ID、release ID、tile IDはallowlist patternで検証する
- manifest内pathはrelease rootからの相対pathだけを許可する
- `..`、absolute path、symbolic linkによるroot外参照を拒否する
- 大文字小文字が異なる同一名を作らない
- temporary fileは完成名と別にし、fsync後にatomic moveする

## Checksum

- SHA-256を使用する
- `checksums.sha256` 自身を除く、manifestを含むすべての規範的artifactを列挙する
- siblingのdelivery ZIPは内部checksum対象外とし、別の `<release-id>.zip.sha256` で保護する
- pathはUTF-8、`/` separator、辞書順へcanonicalizeする
- apply前、転送後、ZIP展開後に再検証する
- optional preview破損と必須tile破損を区別するが、未知の追加実行ファイルは警告または拒否する

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

## 互換性

- `formatVersion`はpackage構造とmanifest意味のversion
- `generatorVersion`は生成結果のversion
- `minecraftVersion`はblock state registry互換性の目安
- readerは対応しないfuture formatを推測で読み込まない
- block変換が必要な場合は明示的migrationを行い、新releaseを発行する
