# Validation and Preview v2

> Status: 全体は段階実装中。V2-1のconstraint mapに限りdesired／actual／residual等の固定8 diagnostic PNGをatomic bundleとして実装済み。V2-2-03〜06のgenerator単体metricとV2-2-07のtransition diagnosticに加え、V2-2-08でfinal field-only coastal validator、Blueprint target、strict 11-layer coastal preview bundleを実装した。V2-3-13でfield-only hydrology validatorとstrict 12-layer hydrology preview bundleを実装し、V2-3-15で完成featureのoffline `SUPPORTED` gateへ統合した。V2-4-13でfield-only environment validatorとstrict 10-layer environment preview bundleを実装し、V2-4-15で対象environment featureと`environment-fields`のoffline `SUPPORTED` gateへ統合した。V2-5-15でdescriptor-only volume validatorとstrict 5-layer volume preview bundleを実装し、V2-5-17で`sparse-volume` Releaseへ収容、V2-5-18で対象volume featureと`sparse-volume`のoffline `SUPPORTED` gateへ統合した。現行v1 preview 8枚とRelease 1 allowlistは変更していない。

## 1. 目的

v2では「ファイルが生成できた」と「意図した地形が成立した」を分ける。validationは不変条件、field、graph、feature metric、constraint、Release、配置を段階的に検査し、previewは同じ測定結果を追跡可能に可視化する。

```text
Intent constraint
→ Blueprint ValidationTarget
→ generator output measurement
→ ValidationIssue / MetricResult
→ candidate decision
→ diagnostic preview layer
```

## 2. Validation層

| 層 | 例 | 失敗時 |
|---|---|---|
| Contract | Schema、ID、range、geometry | compile前に拒否 |
| Blueprint | DAG、field owner、module、budget | generate前に拒否 |
| Baseline terrain | bounds、height、solid/fluid、tile | candidate失敗 |
| Field | no-data、範囲、seam、profile | candidate失敗 |
| Graph | reachability、cycle、flow、relation | candidate失敗 |
| Feature | beach幅、fjord断面、cave接続等 | hard失敗／soft score |
| Cross-feature | river→delta、fall on river | hard失敗／soft score |
| Artifact | checksum、allowlist、read-back | publish失敗 |
| Placement | snapshot、apply、settled verify | rollback |

module固有validatorが成功しても、baseline invariantを省略しない。moduleは自分の出力を自己承認できない。

## 3. Issueとmetric model

現行のx/z中心issueを拡張する。

```text
ValidationIssueV2
├── issueId / ruleId / ruleVersion
├── severity: ERROR | WARNING | INFO
├── hardness: HARD | SOFT | INVARIANT
├── featureId / constraintId / moduleId
├── spatialScope
│   ├── point / AABB / geometryId / tileId
│   └── optional Y range
├── metric / actual / expected / tolerance
├── messageKey / redacted parameters
└── diagnosticLayerIds[]
```

```text
MetricResult
├── metricId / version
├── subject / sample scope
├── value / unit / aggregation
├── target / residual / passed
└── evidence checksum
```

message文字列を機械判定の正本にしない。metric algorithm、sampling、percentile、boundary handlingをrule versionへ固定する。

### 3.1 Diagnostic gate契約（V2-18-01）

`DiagnosticBlueprintCompilerV2`はBlueprintへ機械可読な`DiagnosticIssueV2`を積むが、production export spineはこれらをgateに使ってこなかった（[macro foundation監査](../audits/macro-foundation-conformance-audit-2026-07-22.md) item 4）。将来の一括fail-closed化を可能にするため、`DiagnosticGateContractV2`（`diagnostic-gate-contract-v1`）がBlueprint diagnostic ruleを`GATING`／`NON_GATING`へ分類する正本である。

- `gates(issue)`は`severity==ERROR`かつruleが`GATING`のissueだけをtrueにする。WARNING／INFOはrule classに関わらずgateしない。
- 現在の状態では全Blueprint diagnostic rule（`v2.unsupported-capability`／`v2.missing-validator-capability`／`v2.missing-preview-capability`／`v2.unsupported-constraint-map`／`v2.unsupported-structure-capability`）が`NON_GATING`であり、`gatingIssues(...)`は常に空である。すなわちexportは依然としてどのdiagnostic issueもgateにしない（fail-closed化はしない）。`ignoredErrorIssues(...)`が「発行されるが無視されるERROR一覧」を返し、golden testで固定する。
- ruleを`GATING`へ変更するのは、それをexport artifact生成前で消費するfail-closed gate（`V2-18-03` HARD preflight gate）を同時に追加するTaskだけである。分類とruntime挙動は常に同一Taskで動かし、どの時点でも動作するproduction経路を1つ保つ。
- 同契約は`production-connected` FeatureKind集合も所有し、production dispatch spine（`ProductionDispatchRegistryV2`）と同じcurrent-feature-state権威から射影する。`DiagnosticBlueprintCompilerV2`はこれを参照し、既にproduction export routeを持つkind（現状はsurface-2.5D coastal 4種）へは一律の`v2.unsupported-capability` ERRORを発行しない。routeを持たないkindには「production export能力なし」の正直な信号として残す。
- 本Taskはterrain field／tileの**semantic checksum**を変更しない（diagnosticはterrain生成に寄与しない）。一方、production-connected coastal featureを含むBlueprintは`unsupported-capability` issueが減るため、**Blueprint diagnostic容器のbyte（`canonicalChecksum`）は変化する**。これはgate対象外の診断容器変化であり、field／tile／block streamには影響しない。

## 4. Hard / Soft判定

- invariant violationは常にcandidate失敗。
- hard constraintはtolerance込みで1件でも失敗すればcandidate失敗。
- soft constraintは0〜1へ正規化し、weight付きscoreへ加える。
- soft scoreが高くてもhard失敗を相殺しない。
- 未計測metricを0点として黙認せず、required validator missingとしてerrorにする。
- candidate間の同score tie-breakはcandidate seed／IDの固定順にする。

Releaseにはtarget、actual、residual、rule version、選択／棄却理由を保存する。

## 5. Feature別validator

| Feature | 最低限測定するmetric |
|---|---|
| Sandy beach | centerline長、幅p10/p50/p90、傾斜、sand share、浅海幅 |
| Rocky coast/cape | rock exposure、cliff率、海岸turning、sea stack数、channel数 |
| Breakwater harbor | crest連続、opening幅、内外水深、陸接続、enclosed water面積 |
| Mountain | peak数／prominence、ridge continuity、slope class、talus/snow band |
| River | source-mouth接続、逆勾配、幅／流量、confluence、bank continuity |
| Lake | basin closure、水面一定、rim/spill、inlet/outlet、depth分布 |
| Canyon | depth、floor/rim幅、断面、wall slope、river alignment |
| Waterfall | graph位置、drop、water column、plunge pool、上下流接続 |
| Delta | distributary数、fan angle、low relief、海到達、flow conservation |
| Fjord | slenderness、marine channel、center depth、U断面、sidewall relief |
| Volcano | summit/crater/caldera closure、radial slope、lava continuity、material |
| Archipelago | component数、size distribution、spacing、submarine saddle |
| Mangrove | tidal接続、salinity、wetness、canopy、root support、open channel |
| Snow mountain | snowline、cover/elevation関係、treeline、ridge exposure |
| Coral reef | shallow/warm/saline area、ring continuity、lagoon、coral cover |
| Cave/lush cave | connected volume、entrance reach、roof厚、moisture、pool、floor area |
| Overhang | support、roof厚、projection、underside clearance、opening |
| Natural arch | span、pier厚、crown厚、through passage、solid connectivity |
| Sky island | solid component数、thickness、ground clearance、gap、walkable area |

metricはfeature geometry内だけでなくtransition bandも測定する。例えばbeach幅は一方向のbounding box幅ではなく、shore normalに沿った複数断面で測る。

`V2-2-03`の暫定`BeachMetrics`はgenerator単体gate用に、shoreline cell上の幅p50、選択shore slope、最大nearshore depth、foreshore／backshore／nearshore cell存在をglobal row-majorのstreaming scanで測る。短いendpoint taper fixture、HARD mask conflict、vertical／width corruptionはcompile／generate前後でstable rule IDにより失敗する。これは上表の完全な独立validatorではなく、p10/p90、normal断面、sand share、preview、意図的output corruption検出は`V2-2-08`へ残す。

`V2-2-04`の暫定`HarborMetrics`はgenerator単体gate用に、navigable depth p50／maximum、interior／entrance corridor／outside cellの存在をglobal row-majorでstreaming測定する。HARDなenclosureとopening endpoint不一致、outer-ring edgeを作らないclosed entrance、profileに足りない寸法、vertical overflow、HARD land conflictはstable rule IDで拒否する。これは独立validator／previewではなく、opening continuity断面、transitionを含むoutput corruption、desired／actual／residual visualは`V2-2-08`へ残す。

`V2-2-05`の暫定`BreakwaterMetrics`はgenerator単体gate用に、stable arm順のarm長、clear opening実幅、crest／inner foundation／outer foundation cellの存在、streaming solid block数を測定する。HARD basin relation／opening endpoint・座標不一致、交差／self-intersect arm、閉鎖opening、未知subgeometry、vertical／support／block budget超過はcanonical plan公開前または測定中にstable rule IDで拒否する。これは独立validator／previewではなく、全断面のcrest幅／opening continuity、basinとの合成結果、transition corruption、feature diagnostic PNGは`V2-2-08`へ残す。

`V2-2-06`の暫定`CapeMetrics`はgenerator単体gate用に、最大relief、露岩cell比、polygonのnon-collinear turning数、channel／sea-stack descriptorとraster cellの存在をglobal row-majorでstreaming測定する。HARD mask conflict、薄いland bridge、孤立stack、vertical／support／descriptor budget、未知parameterをstable rule IDまたはstrict Schemaで拒否する。`capeMode=LOCAL_VOLUME_REQUIRED`は2.5Dへ劣化させず`v2.cape-volume-required`として診断する。これは独立validator／previewではなく、transition後のcoast complexity、intentional output corruption、feature diagnostic PNGは`V2-2-08`へ残す。

`V2-2-07`の`ConflictDiagnostic`はrule ID、global X/Z、canonical feature ID集合、messageを返し、HARD-HARD、未契約overlap、equal categorical vote、HARD cellの未表現をsample失敗と同じ規則で説明する。`coastal.composed.conflict` fieldは成功したcanonical outputでは0であり、失敗を黙ってraster値へ変換しない。V2-2-08の`CoastalValidatorV2`はこのfinal conflict fieldを含むsamplerだけを読み、generator instance／`evaluate()`を読まない。field corruptionでwidth、depth、entrance、cape exposure／descriptor、conflict、residualを独立に検出する。

## 6. Cross-feature rule

- river reachはdelta apexへ接続する。
- waterfall pointはriver graph上にあり、fall前後だけbed discontinuityを許す。
- lagoonはreef／barrierに内包され、少なくとも1つのmarine pass policyを持つ。
- mangrove channelは海またはestuaryへ到達する。
- lush chamberはcave network内で入口から到達可能である。
- overhang／archのcarveはhost cliffの安全厚を破らない。
- snow、coral、mangroveはそれぞれtemperature／depth／salinity条件を満たす。
- structureはfinal 3D TerrainQuery上でsupport、clearance、水深、衝突を満たす。

## 7. Corruption validator

generatorとvalidatorが同じ誤りを共有しないよう、正しいfixtureだけでなく意図的破壊をtestする。

- reachを切る、逆勾配にする、delta branchを孤立させる。
- lake rimを下げる、spillを消す。
- cave tunnelを切る、roofを薄くする。
- overhang supportを消す、sky islandを接地させる。
- material IDを未知にする、coralを乾地へ移す。
- field寸法／checksum／no-dataを壊す。

各破壊に対応するrule IDと位置が返ることを確認する。

## 8. Preview registry

現行の固定8 file列挙から、version付きregistryへ移行する。

```text
PreviewLayerDescriptor
├── layerId / layerVersion
├── fileName / title / category / order
├── rendererProviderId
├── requiredFields[]
├── legend / valueRange / noDataColor
├── dimensions / coordinateOverlay
└── optionalFeatureKinds[]
```

moduleはdescriptorをbuilt-in catalogへ登録するが、Releaseへ出すlayer集合と順序はBlueprintでfreezeする。未知layerをv1 Releaseへ追加しない。

## 9. Preview layer set

### 9.0 V2-1で実装済みの固定diagnostic bundle

V2-1のmanual constraint pathは、Release 2 registry導入前のversion固定集合として次の8 PNGだけを出す。

- `desired-land-water.png`
- `actual-land-water.png`
- `residual-land-water.png`
- `desired-height.png`
- `actual-height.png`
- `residual-height.png`
- `zone-label-map.png`
- `constraint-errors.png`

lazy field samplerから1枚ずつrenderしてbufferを解放し、8枚が完成したpreview directoryをbundle staging内でatomic moveする。外側bundleの全artifact／checksum／budget／strict read-backを完了し、最後のcancel checkを通った後のtargetへのatomic moveをcommit pointとする。move前のcancel／failureではstagingを削除してtargetを作らず、move後のcancelでcommitted targetを削除しない。これは現行v1 Release 1の固定8 previewとは別集合であり、そのallowlistを変更しない。

ここでの`actual`はV2-1のreconciliation診断値であり、V2-2以降の地形generator出力ではない。hard land-waterはdesiredを完全copyし、heightはbounds内のwhole blockへ量子化し、その差をresidualにする。zoneはdesiredとlabel辞書だけを出す。no-dataは共通diagnostic sentinelへ変換してmagenta、constraint errorはredで表示する。

### 9.1 Baseline

- overview
- height / hillshade
- water depth / water body
- slope / aspect
- semantic material
- feature membership
- structures
- validation summary

### 9.2 v2 diagnostic

- land-water desired / actual / residual
- zone／feature assignment
- coastline、ridge、river geometry overlay
- drainage basin、reach graph、flow accumulation
- lake rim/spill、delta distributary、fjord thalweg、fall node
- geology、strata、hardness
- temperature、moisture、wetness、salinity、snow
- habitat、vegetation density、coral/mangrove suitability
- cave footprint／slice、overhang／volume AABB、sky island projection
- hard/soft constraint failure heatmap
- tile、stage halo、mutation envelope

### 9.3 V2-2 coastal diagnostic bundle（実装済み、Release外）

`CoastalDiagnosticPreviewRendererV2`は次の11 fileを固定順で出力する。

- `beach-overlay.png`、`harbor-overlay.png`、`breakwater-overlay.png`、`cape-overlay.png`
- `desired-land-water.png`、`actual-land-water.png`、`residual-land-water.png`
- `desired-height.png`、`actual-height.png`、`residual-height.png`
- `constraint-errors.png`

`coastal-preview-index-v2.schema.json`はlayer ID/version、fixed palette、相対filename、寸法、byte length、SHA-256、Blueprint checksum、exclude-self canonical checksumをstrictに持つ。rendererは1枚ずつARGBを解放し、staging内のindexをschema／checksum／directory entry／PNG dimensionまでread-backし、V2-1のsymlink、hardlink alias、magic、byte、pixel、checksum、TOCTOU防御を経由した後だけbundleをatomic publishする。Release 1へは追加しない。`V2-2-11`の`surface-2_5d`はindexと固定11 PNGを全てRelease 2 manifestへ列挙し、Blueprint checksum、寸法、palette、byte／artifact／semantic checksumを再検証する。

`coastal-validation-artifact-v2.schema.json`はvalidator ID/version、source Blueprint checksum、bounded metric／issue report、exclude-self canonical checksumを持つ。`surface-2_5d` verifierはhard ERRORを含むreportをrejectし、validation fileを省略したReleaseを許可しない。

### 9.4 V2-3 hydrology diagnostic bundle（実装済み、Release外）

`HydrologyDiagnosticPreviewRendererV2`は次の12 fileを固定順で出力する。

- `basin-id.png`、`flow-direction.png`、`flow-accumulation.png`、`reach-graph.png`
- `bed-elevation.png`、`water-surface.png`、`water-body.png`
- `lake-rim-spill.png`、`delta-distributary.png`、`fjord-thalweg.png`、`waterfall-envelope.png`
- `constraint-residual.png`

`hydrology-preview-index-v2.schema.json`はlayer ID/version、fixed palette、相対filename、寸法、byte length、SHA-256、Blueprint checksum、exclude-self canonical checksumをstrictに持つ。`HydrologyValidatorV2`は`HydrologyFieldSamplerV2`とoptional reconciliation artifactだけを読み、isolated reach／reverse gradient／flow cycle／leaking lake／dead delta／broken fjord／fall mismatch等をstable rule IDで検出する。`hydrology-validation-artifact-v2.schema.json`はvalidator ID/versionとbounded reportを持つ。Release 1へは追加せず、`V2-3-14`で`hydrology-plan` Release 2 capabilityへ収容した。

### 9.5 V2-4 environment diagnostic bundle（実装済み、Release外）

`EnvironmentDiagnosticPreviewRendererV2`は次の10 fileを固定順で出力する。

- `temperature.png`、`moisture.png`、`wetness.png`、`salinity.png`、`hydroperiod.png`
- `snow-cover.png`、`habitat.png`、`material-profile.png`、`feature-material.png`
- `constraint-error.png`

`environment-preview-index-v2.schema.json`はlayer ID/version、fixed palette、相対filename、寸法、byte length、SHA-256、source plan checksum、exclude-self canonical checksumをstrictに持つ。`EnvironmentValidatorV2`は`EnvironmentFieldSamplerV2`の公開cell snapshotだけを読み、wrong snowline／mangrove salinity／reef depth／root support／material exposure等をstable rule IDで検出する。`environment-validation-artifact-v2.schema.json`はvalidator ID/versionとbounded reportを持つ。Release 1へは追加せず、`V2-4-14`で`environment-fields` Release 2 capabilityへ収容した（ADR 0019）。

`VolumeDiagnosticPreviewRendererV2`は次の5 fileを固定順で出力する（XZ投影、dense voxel gridは使わない）。

- `aabb-footprint.png`、`operator-ordinal.png`、`y-slice.png`
- `solid-fluid.png`、`surface-class.png`

`volume-preview-index-v2.schema.json`はfixed 5-layer set、palette、寸法（最大256）、byte／SHA-256、source plan checksumをstrictに持つ。`VolumeValidatorV2`は`VolumeFeatureSnapshotV2`の公開descriptorだけをfeatureId順で読み、isolated cave／thin roof／fluid leak／floating overhang／broken arch／merged sky island／fall discontinuity／solid-fluid conflict／unknown materialをstable rule IDで検出する。`volume-validation-artifact-v2.schema.json`はvalidator ID/versionとbounded reportを持つ。`V2-5-17`で`sparse-volume` Releaseへ収容し、`V2-5-18`のPhase gateでoffline `SUPPORTED`へ昇格した。任意3D viewerは将来追加できるが、Releaseの必須検証をGUIへ依存させない。

## 10. `previews/index.json`

Release format 2は固定file名allowlistではなく、strictなindexとdescriptor catalogを使う。

```json
{
  "previewIndexVersion": 1,
  "layers": [
    {
      "layerId": "terrain.height",
      "layerVersion": 1,
      "path": "previews/terrain-height.png",
      "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
      "width": 512,
      "height": 512,
      "legendId": "elevation-block-y-v1"
    }
  ]
}
```

これは提案例である。verifierは次を厳格に検査する。

- index自体と各fileのchecksum
- path正規化、重複、未知layer/version
- pixel／byte／dimension上限
- Blueprintで要求された必須layerの欠損
- indexにないextra preview
- legend/value range整合

現行Release format 1は8 previewをexact allowlistで検査している。これを緩めず、format 2を別verifierとして追加する。

## 11. Rendering

- previewを1枚ずつrender、write、checksum、解放する現行原則を維持する。
- full-resolution fieldを色付きARGB copyとして長時間保持しない。
- stable color table、font非依存legend、locale非依存数値を使う。
- categorical IDはnearest、continuous fieldは明示rangeで色変換する。
- geometry overlayはblock量子化後の座標を使う。
- issueが多い場合は上限付き集約と別JSON reportを併用する。
- rendererはPaper main threadで実行しない。

## 12. Validation report artifact

```text
validation/summary.json
validation/metrics.json
validation/issues.json
validation/candidates/<candidateId>.json
```

各fileはbounded、canonical、checksum対象とする。source prompt、API key、raw image metadata、stack traceを保存しない。ユーザー向けmessageとmachine rule IDを分ける。

## 13. Test戦略

- rule単位のpositive/negative fixture
- hard/soft/tolerance/weightのcontract test
- percentile、distance、connected component、cross-sectionのgolden test
- corruption testで期待rule IDとscopeを確認
- feature kindごとのend-to-end fixture
- whole/tile、thread、locale、timezoneでmetric同一
- preview registryのduplicate／unknown／missing／extra拒否
- PNG dimensions、legend、corner coordinate、pixel checksum golden test
- Release format 1の固定8枚が不変である回帰test
- format 2 directory/ZIPのstrict read-backとtampering test
- peak memory、1枚ずつ解放、cancel時にpartial Releaseを公開しないtest

## 14. 完了条件

featureを「supported」と呼べるのは、少なくとも以下をすべて満たす場合である。

- 正常fixtureがhard validationを通る。
- 専用corruptionをvalidatorが検出する。
- desired、actual、residualをpreviewまたはmetric reportで説明できる。
- tile、thread、seedの再現性を持つ。
- resource budget内で失敗または完了し、無制限処理をしない。

美しさだけを自動metricへ還元しすぎず、形状成立をhard、好みをsoft、最終判断をpreviewで補う。

## 15. Taskごとの導入順

validator／previewは最後に一括追加せず、各Phaseの専用Taskで実装し、その後のcapability Taskと統合監査で再検証する。詳細は [Task Index](task-index.md) を正本とする。

| Phase | validator／preview Task | capability Task | lifecycleを確定する統合Task |
|---|---|---|---|
| V2-2 coastal | `V2-2-08` | `V2-2-11` | `V2-2-12` |
| V2-3 hydrology | `V2-3-13` | `V2-3-14`（完了） | `V2-3-15` |
| V2-4 environment | `V2-4-13`（完了） | `V2-4-14`（完了） | `V2-4-15`（完了） |
| V2-5 volume | `V2-5-15`（完了） | `V2-5-17`（完了） | `V2-5-18`（完了） |

feature generator Taskが完了しても`EXPERIMENTAL`のままである。専用validatorがgenerator-private stateではなくcanonical IR／`TerrainQuery`／artifactからcorruptionを検出し、preview index、budget、whole/tile/thread決定性、Release strict verifyが揃い、統合TaskがPhase portfolioを再確認した場合だけ`SUPPORTED`へ変更できる。V2-2 coastalは`V2-2-12`、V2-3 hydrologyは`V2-3-15`、V2-4 environmentは`V2-4-15`、V2-5 volumeは`V2-5-18`でこの条件を満たし、各offline経路を`SUPPORTED`へ変更済みである。
