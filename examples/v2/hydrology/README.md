# Hydrology v2 examples

V2-3 Phase gate完了により、offlineのRIVER／MEANDERING_RIVER、LAKE、CANYON、DELTA、TIDAL_CHANNEL_NETWORK、FJORDと`hydrology-plan`は`SUPPORTED`です。WATERFALL、ALPINE／GLACIAL mountain、VOLCANIC_ARCHIPELAGOは後続のvolume／environment／material完成まで`EXPERIMENTAL`です。

- `hydrology-plan-v2.json` は、V2-3 の固定 prior、field ownership、graph/work budget を示す compile 済み IR 例です。
- `hydrology-routing-artifact-v2.json` は、V2-3-02 の global basin/routing index の canonical JSON 例です。96×64 の provisional surface、明示された2 outlet、stable basin ID、flow direction／accumulation sidecar descriptor、実測 resource usage を含みます。
- `hydrology-reconciliation-plan-v2.json` は、V2-3-12 の固定3 pass／`kind → feature → constraint`順、scalar target、iteration×working-set×artifact budgetをfreezeしたplan例です。
- `hydrology-reconciliation-artifact-v2.json` は、delta mouthのmarine connectionを満たせない入力を、残差とcanonical `UNRECOVERABLE_CONNECTION` reason付きで保存するfailure artifact例です。
- V2-3-13 の hydrology validation／preview Schema（`hydrology-validation-artifact-v2.schema.json`、`hydrology-preview-index-v2.schema.json`）は、testが生成する bounded report／12-layer diagnostic bundle を strict read-back します。PNG／index の正本例は source-tree に同梱せず、atomic publish 後の codec verify で固定します。
- `meandering-river.terrain-intent-v2.json` は、V2-3-03 の `MEANDERING_RIVER`（同一 reach contract の `RIVER` variant を含む）Intent 例です。source→mouth spline、bankfull 幅、discharge class、minimum bed slope、meander variant を持つ`SUPPORTED`例です。
- `open-spill-lake.terrain-intent-v2.json` は、V2-3-04 の独立 `LAKE` Intent 例です。POLYGON basin、単一 surface、rim、DECLARED_EDGE spillway、EDGE_TO_CENTER_LINEAR floor を持つ`SUPPORTED`例です。dam／reservoir は含みません。
- `canyon-river-skeleton.terrain-intent-v2.json` は、V2-3-05 の `CANYON`＋`MEANDERING_RIVER` Intent 例です。共有 SPLINE、HARD `WITHIN`、rim/floor/depth、TERRACED_V を持つ`SUPPORTED`例です。strata／volume は含みません。
- `waterfall-2_5d-skeleton.terrain-intent-v2.json` は、V2-3-06 の `WATERFALL`＋`MEANDERING_RIVER` Intent 例です。HARD `ON_PATH_OF`、lip/drop/plunge pool、`behindFallClearanceBlocks=0`（volume は V2-5 deferred）を持ち、`EXPERIMENTAL` のままです。falling water column／behind-fall cavity は含みません。
- `delta-distributary-fan.terrain-intent-v2.json` は、V2-3-07 の `DELTA`＋`MEANDERING_RIVER` Intent 例です。HARD `DRAINS_TO`／`EMPTIES_INTO`、明示HARD SEA edge、分流数／fan opening／低起伏／sandbar／shallow-sea depthを持つ`SUPPORTED`例です。tidal channel／mangrove／sediment geology は含みません。
- `tidal-channel-network.terrain-intent-v2.json` は、V2-3-08 の `TIDAL_CHANNEL_NETWORK` Intent 例です。MULTI_SPLINE、`BIDIRECTIONAL` edge kind、HARD `EMPTIES_INTO`＋明示HARD SEA、optional HARD `WITHIN` mangrove child-plan hookを持つstandalone `SUPPORTED`例です。mangrove shaping／salinity／hydroperiod は含みません。
- `fjord-glacial-u.terrain-intent-v2.json` は、V2-3-09 の `FJORD` Intent 例です。SPLINE centerline、`GLACIAL_U`、HARD `EMPTIES_INTO`＋明示HARD SEA、optional HARD `FLANKS` glacial wall plan hook（V2-3-10 mountain parameters付き）を持つFJORDの`SUPPORTED`例です。wall側mountainは`EXPERIMENTAL`で、ice／snow／volume sea caveは含みません。
- `alpine-ridge-skeleton.terrain-intent-v2.json` は、V2-3-10 の `ALPINE_MOUNTAIN_RANGE` Intent 例です。ridge spline、peak／saddle／spur、fixed-point ridge sharpness、drainage-compatible provisional surface fieldと、V2-4-04で明示必須になった`COLD_ALPINE` climate presetを持ち、`EXPERIMENTAL` のままです。snowline／material／cirque ecology は含みません。
- `volcanic-archipelago-skeleton.terrain-intent-v2.json` は、V2-3-11 の `VOLCANIC_ARCHIPELAGO` Intent 例です。stable MULTI_POINT island mass、dry-land gap、central dominance、submarine saddle、radial drainage、optional caldera／lava plan hookを持ち、`EXPERIMENTAL` のままです。basalt／tuff／ash material、lava tube、full volcanic ecology は含みません。

Routing index が参照する `.lfgrid` はテストで決定論的に生成・strict read-back するため、この source-tree の JSON 例には同梱しません。実 bundle は `index.json`、`fields/flow-direction.lfgrid`、`fields/flow-accumulation.lfgrid` の3ファイルだけを許可し、index 単体を Release artifact として扱いません。
