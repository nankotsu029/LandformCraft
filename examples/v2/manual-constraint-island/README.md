# Manual constraint island fixture

このdirectoryはV2-1のAI非経由manual path用contract fixtureです。

- `request-v2.json`: `LAND_WATER_MASK`、`HEIGHT_GUIDE`、`ZONE_LABEL_MAP`のsource／encoding／座標契約
- `terrain-intent-v2.json`: 3 sourceへのsemantic bindingと、V2-4-04 Blueprint compileで必須となる明示`TEMPERATE_MARITIME` climate preset

JSON内のSHA-256とartifact IDはSchema fixture用placeholderです。公開CLI／Paper commandにはV2-1をまだ接続していないため、この2 fileだけを直接generate commandへ渡す例ではありません。

実行可能なfixtureは `ManualConstraintMapGenerationServiceV2Test` です。testがU8 land-water PNG、U16 height PNG、U16 zone PNGを一時directoryへ生成して実checksumをRequestへfreezeし、AIを呼ばずに`LFC_GRID_V1` field indexと固定8 diagnostic previewを生成・strict read-backします。
