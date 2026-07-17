# V2-4 geology, lithology, and strata examples

`geology-plan-v2.json` はV2-3の固定 `UNIFORM_GEOLOGY_PRIOR` を、version付きの4個のtyped fieldへ明示的に置換する最小例です。formation IDはこのTaskではopaqueであり、lithology catalogやstrata、Minecraft block paletteの意味を持ちません。

`lithology-plan-v2.json` はV2-4-02の最小catalog／province assignment例です。`geology-plan-v2.json`のcanonical checksum、province／formation／hardness／permeabilityへ厳密に結合します。9種のcatalog entry、8-bit compact code、erosion responseはbuilt-in contractで固定され、Minecraft block state、strata、外部preset／classは含めません。

`strata-plan-v2.json` はV2-4-03の最小strata profile例です。`lithology-plan-v2.json`へchecksumで結合し、provinceごとのordered layer stack、bounded fold/tilt、surface-exposed derived hardness／permeability、およびV2-3 `UNIFORM_GEOLOGY_PRIOR`／`hydrology-reconcile-fixed-v1`からの明示 hydrology geology-input handoffをfreezeします。dense 3D layer map、Minecraft block state、canyon/volcano固有materialは含めません。

各fieldは`LFC_GRID_V1`、`U16`、`NEAREST`、`SINGLE_OWNER`で、実payloadはplan JSONへ埋め込まずsidecar bundleへ保存します。64×48例のbudgetは4 sidecar、最大128 cell window、writerのstrict read-backを含みます。空planでは4 fieldの全cellをno-data `65535`として発行します。strataのderived scalarはprofile descriptorからtile単位で再計算し、追加sidecarを必須にしません。
