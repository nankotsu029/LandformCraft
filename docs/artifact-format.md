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

## V2-1 offline constraint field bundle

V2-1のmanual constraint pathは、Release Packageとは別のoffline canonical input bundleを生成します。公開CLI／Paper、Release format 1、配置、Undoのartifactではありません。Release format 2へ収容する方法はV2-2以降で別途version化します。

```text
<constraint-bundle>/
├── fields/
│   ├── index.json
│   ├── 000-desired-land-water.lfgrid
│   ├── 000-actual-land-water.lfgrid
│   ├── 000-residual-land-water.lfgrid
│   └── ...
└── previews/
    ├── desired-land-water.png
    ├── actual-land-water.png
    ├── residual-land-water.png
    ├── desired-height.png
    ├── actual-height.png
    ├── residual-height.png
    ├── zone-label-map.png
    └── constraint-errors.png
```

`fields/index.json`は`constraint-field-index-v2.schema.json`に従うstrict indexです。index version、request ID、source Request／Intent checksum、exclude-selfのcanonical index checksum、適用binding、label辞書、全field descriptorを保存します。各bindingはroleに応じたfield集合、semantic、value type、sampling、scale／offset、canonical artifact IDへ完全一致しなければなりません。index外field、共有／孤立field、未知label、改変checksum、future versionを拒否します。

各descriptorはfield ID、semantic、value type、width／length、`RELEASE_LOCAL_XZ`座標、sampling、scale／offset、no-data、encoding version、artifact／semantic SHA-256、source provenanceを持ちます。1000×1000の値をJSONへ埋めず、[ADR 0011](adr/0011-compact-field-sidecar.md)の`LFC_GRID_V1`へ保存します。

`LFC_GRID_V1`はbig-endian、非圧縮、paddingなしのrow-major形式です。`U8`／`U16`／`I32` payloadを`index = z * width + x`で並べ、header内のsemantic checksumと外部descriptorのfile全体artifact checksumで検証します。version 1はchunk、chunk index、chunk checksum、圧縮を持ちません。readerはfile全体を配列化せず、現行の1 sidecar 8 MiB、1 window working set 8 MiBというdefault hard limitの範囲でrow／windowを読みます。bundle全体のartifact ceilingは64 MiBです。

V2-4-01のgeology field setは同じbinary layoutへsemantic code 12〜15を追加し、`GEOLOGY_PROVINCE_ID`、`GEOLOGY_FORMATION_ID`、`GEOLOGY_HARDNESS`、`GEOLOGY_PERMEABILITY`の4個を収容します。すべてU16／NEAREST／no-data 65535で、IDはscale 1,000,000、hardness／permeabilityはscale 1,000です。専用publisherは4 sidecarをsibling stagingへstreamし、descriptor集合、plan provenance、province→formation／scalar対応、artifact／semantic checksumをbounded windowで全域検査してからdirectoryをatomic publishします。これはstandalone field bundleであり、constraint field indexやRelease 2 capabilityへ暗黙追加しません。

V2-4-02の`LithologyPlanV2`はJSONのstrict catalog／assignment contractであり、新しい`LFC_GRID_V1`、geology semantic code、bundle file、Release capabilityを追加しません。readerはsource geology checksumとprovince assignmentを検査し、既存province sidecarをbounded windowで走査する。Minecraft block stateとstrata payloadをcatalogへ埋め込まない。

V2-4-04の`ClimatePlanV2`もstrict JSON descriptor contractです。このTaskでは4 climate fieldのsidecar、`LFC_GRID_V1` semantic code、bundle、Release capabilityを追加しません。coarse prior／final kernelとHydrology transition checksumだけをBlueprintへfreezeし、payload artifact化は後続Taskのcomplete-set契約まで保留します。

publisherはbundle staging内でsidecar、sealed index、固定8 previewを書き、全checksumとstrict read-back、artifact／resident budgetを確認します。cancelの最終checkに成功した後のbundle `ATOMIC_MOVE`をcommit pointとします。move前のcancel／failureはstagingを削除してtargetを作らず、move後に届いたcancelを理由にcommitted targetを削除しません。既存targetの暗黙上書きと非atomic fallbackは行いません。

## V2-2 offline tile schematic

`V2-2-09`のoffline tileはRelease containerではなく、1個の`.schem`と`offline-tile-artifact-v2.schema.json`に従うmetadataである。metadataはtile ID、source Blueprint checksum、release-local origin／dimension／Y範囲、`SPONGE_X_Z_Y_V1` order、Minecraft `1.21.11`、DataVersion `4671`、Sponge version `3`、相対path、block／palette／byte数、artifact／semantic／canonical SHA-256を持つ。exampleは`examples/v2/offline-tile/offline-tile-artifact-v2.json`である。

semantic checksumはtile boundsをversion headerへbindingし、global座標をX fastest、次にZ、最後にYで走査したcanonical block-state UTF-8列から計算する。palette IDやGZip byte列は意味checksumへ使わない。writerはfinal `TerrainBlockResolver`を二走査し、第1走査ではblock-state別countだけを保持して辞書順palette、general VarInt byte長、semantic checksumを決める。第2走査はblock Listを作らず直接NBT `Blocks.Data`へstreamし、checksum不一致ならresolverの非決定性として拒否する。

paletteは1..16384、VarInt IDは0..16383、水平tileは各辺256以下、vertical spanは512以下、encoded Dataは40 MiB以下、圧縮artifactは64 MiB以下、palette retained estimateは16 MiB以下である。unknown／非canonical block state、future tuple、非zero offset、truncated VarInt、block count／dimension／checksum不一致、block entity／entity／biome、decode／path／cancel違反をstagingで拒否する。strict bounded read-backとWorldEdit 7.3.19 offline read-backが同じsemantic checksumを返したことをAcceptance testで固定している。`V2-2-11`ではこのstandalone tileを`surface-2_5d` Release 2 indexへ、metadataと対応Sponge v3 fileの組として収容した。

### V2-5-16 volume 3D offline read-back

`V2-5-16`はこのoffline tile pathをvolume合成へ拡張する。writer／inspector／WorldEdit reader（`OfflineTileSchematicWriterV2`／`SpongeV3TileInspectorV2`／`WorldEditOfflineTileReaderV2`）はversion header以来`minY..maxY`全域をX fastest／Z／Yで走査する汎用3D exporterであり、意味・format・checksum・budgetを変更しない。新規は`format.v2.tile.VolumeTileBlockResolverV2`のみで、`terrain-query-volume-v1` kernelのvolume合成`TerrainQuery`（V2-5-05）をcanonical block stateへ橋渡しする。air cavity→`minecraft:air`、fluid(WATER)→`minecraft:water`、independent solid→semantic material（`EnvironmentBlockStateCatalogV2` allowlist経由）で、NONE material solidと非water fluidを拒否し、v1 adapterと分離する。

cave（carve→air）、floating solid（sky island／overhang topology）、fluid pool、air を含むvolume tileがexport→strict inspector read-back→WorldEdit 7.3.19 read-backでresolver semantic checksumと全XYZ一致すること、whole／tile分割・thread／order・locale／timezone不変、palette 127/128 VarInt境界を跨ぐ3D read-back、truncated／corrupt byte／checksum改変拒否を`OfflineVolumeTileReadBackV2Test`／`WorldEditOfflineVolumeTileReaderV2Test`で固定した。exampleは`examples/v2/volume/offline-volume-tile-artifact-v2.json`である。出力Sponge v3は一般仕様features（Version 3、DataVersion 4671、Offset `[0,0,0]`、general VarInt palette、proprietary tag無し）だけを使うためoffline FAWE readerでも同じfileが読める。running-server FAWE smokeはV2-6の実機Taskとして分離し、ここでは有効化しない。Paper applyと`sparse-volume` Release capability（`V2-5-17`）は対象外である。

V2-6-20の`VerifiedReleaseCanonicalBlockSourceV2`は既存Release format 2とSponge v3を変更せず、`ReleaseCoreVerifierV2.openVerified`のcloseable view上でplacement用final streamを再構築する。surface／hydrology／environment prefixは`tiles/`、`sparse-volume` prefixは`volume/tiles/`のvolume-composed tileを正本とし、cursorごとにbyte length／artifact checksum／metadata semantic checksumを再検査して1 tileだけをbounded decodeする。ZIP stagingはsource closeで削除し、directory rootは削除しない。新artifact type／Schema／example formatは追加しない。

## V2-4-08 Minecraft palette plan

`V2-4-08`のMinecraft paletteはRelease containerではなく、`minecraft-palette-plan-v2.schema.json`に従うstandalone planである。`MinecraftPalettePlanV2`はsealed `MaterialProfilePlanV2` checksumへbindingし、Minecraft `1.21.11`／DataVersion `4671`／`minecraft-palette-resolver-v1`と、6 semantic class × SURFACE／CEILING／FLOORの閉じた18 mappingを持つ。exampleは`examples/v2/minecraft/minecraft-palette-plan-v2.json`である。

`format.v2.minecraft.MinecraftPaletteResolverV2`はcompact codeをcanonical block-state文字列へ解決し、fallbackしない。offline writer／inspectorの共有allowlistは`EnvironmentBlockStateCatalogV2`（coastal setのstrict超集合）であり、palette ID 127／128のVarInt境界を含む。v1 `MinecraftBlockPalette`とRelease format 1は変更しない。

## V2-2-10 Release format 2 core

Release format 2はv1の`ReleasePublisher`／`ReleaseVerifier`とは別の`format.v2.release` packageである。`release-manifest-v2.schema.json`に従う唯一のcore fileは`manifest.json`で、version 2／manifest version 1、release ID、`requiredCapabilities[]`、`artifacts[]`、exclude-self canonical SHA-256を持つ。manifest自身はartifact indexへ入れず、non-manifest fileはindexへ完全列挙する。

V2-2-10のcore-only containerは現在も空capability・空artifactだけである。`V2-2-11`は別の`surface-2_5d` capability dispatchを追加し、生成結果、tile、field、previewを別formatとして持ち運べるようにした。CLI／Paper／Release 1へは接続しない。

## V2-2-11 `surface-2_5d` capability

`surface-2_5d` manifestはこのcapabilityだけをrequired capabilityにし、`source/generation-request.json`、`source/terrain-intent.json`、`blueprint/world-blueprint.json`、`constraints/index.json`とindexが列挙する全`.lfgrid`、`validation/coastal-validation.json`、`previews/index.json`と固定11 PNG、`tiles/<tile-id>.json`と`tiles/<tile-id>.schem`を全て`artifacts[]`へ個別にindexする。artifact type/versionは`generation-request-v2`、`terrain-intent-v2`、`world-blueprint-v2`、`constraint-field-index-v2`、`constraint-field-grid-v1`、`coastal-validation-artifact-v2`、`coastal-preview-index-v2`、`coastal-preview-png-v1`、`offline-tile-artifact-v2`、`sponge-schematic-v3`のversion 1だけである。

strict verifierはcoreのpath／checksum／ZIP／budget検査後、request→intent→Blueprint checksum、Intent map binding→field index、Blueprint→validation／preview／tile、field sidecar／preview PNG／Sponge read-back、tile coverageを確認する。validationがhard errorを含む、sidecar／preview／tileが欠損・余分・version不一致、semantic checksumが不一致なら拒否する。sourceのraw pathはmanifestに残らず、publisherはstaging self-verify後にatomic publishする。

publisherは新規targetだけを使い、staging directoryのmanifestをSchema／canonical checksum／strict directory verifierで読み戻し、fsync後にatomic moveする。ZIPを要求した場合はdirectoryから辞書順・epoch timeのZIPを作り、bounded extractorで再検証してから別のatomic moveを行う。cancel／failureではstagingと未公開ZIPを削除し、ZIP要求中に失敗した場合は公開済みdirectoryも削除する。

verifierはdirectory／ZIPのどちらでもnon-symbolic regular file、canonical relative path、case-insensitive unique path、strict artifact index、artifact byte length／SHA-256、unknown capability／artifact type/versionを検査する。core limitは最多257 file、1 artifact 64 MiB、directory／ZIP／展開各128 MiB、manifest 256 KiB、copy buffer 64 KiBである。ZIP directory entry、traversal、duplicate／case collision、index外file、checksum不一致、budget超過を拒否する。exampleは`examples/v2/release-core/release-manifest-v2.json`である。

## V2-3-02 standalone hydrology routing bundle

V2-3-02のglobal routing結果は、単独でもstrict read-backできるcanonical bundleです。`V2-3-14`では同じrouting index／fieldを`hydrology-plan` Release 2 capabilityの必須artifactとしても収容する。

```text
<routing-bundle>/
├── index.json
└── fields/
    ├── flow-direction.lfgrid
    └── flow-accumulation.lfgrid
```

`index.json`は`hydrology-routing-artifact-v2.schema.json`に従い、artifact／solver／direction encoding version、寸法、source HydrologyPlan／provisional surface／fixed-prior checksum、明示outlet、stable basin summary、2 field descriptor、resource usage、graph／routing／canonical SHA-256を持ちます。outletはglobal Z、X、ID順、basinは`basin-000001`からの連番です。同じcellまたはIDの重複、global boundary外の`BOUNDARY` outlet、basin area／outlet accumulation不一致を拒否します。JSON contract例は`examples/v2/hydrology/hydrology-routing-artifact-v2.json`です。例が参照するsidecarはsource treeへ同梱せず、solver testが実bundleを生成してstrict read-backします。

flow directionは`LFC_GRID_V1` semantic code 10、U8、terminal 0、D8 1..8、no-data 255です。flow accumulationはsemantic code 11、I32、routable cell 1以上、no-data 0です。両方ともscale 1、offset 0、`RELEASE_LOCAL_XZ`、`NEAREST`で、source surface checksumをprovenanceへbindingします。readerはartifact／semantic checksumに加え、directionの範囲とD8 adjacency、downstream accumulationのstrict増加、terminalの宣言outlet一致、terminal accumulation合計とroutable cell数、basin areaをrow単位のbounded windowで検査します。

indexは512 KiB以下のnon-symbolic regular file、bundleは上記3 regular fileと必要な`fields/` directoryだけを許可します。symlink、index外file／directory、byte count／checksum改変、future versionを拒否します。publisherはtargetのsibling staging directoryへ2 sidecarとsealed indexを書き、exact file setとrouting semanticsを読み戻します。最後のcancel check直後のdirectory `ATOMIC_MOVE`をcommit pointとし、move前のfailure／cancelではstagingを削除し、move後のlate cancelで公開済みbundleを削除しません。

## V2-3-14 `hydrology-plan` capability

`hydrology-plan` manifestは`requiredCapabilities[] = ["hydrology-plan","surface-2_5d"]`だけを許し、ADR 0013のsurface exact setに加えて次を`artifacts[]`へ個別indexする。

- `hydrology/plan.json`（`hydrology-plan-v2`）
- `hydrology/routing/index.json`（`hydrology-routing-artifact-v2`）とindex列挙の全`hydrology-field-grid-v1`
- `hydrology/reconciliation-plan.json`／`hydrology/reconciliation-artifact.json`
- `hydrology/validation.json`
- `hydrology/previews/index.json`と固定12 PNG（`hydrology-preview-png-v1`）

strict verifierはsurface payload照合後に、plan＝Blueprint内hydrology plan、routing graph／field＝plan checksumとBlueprint寸法、reconciliation＝Blueprint、validation／preview＝Blueprint寸法を検査する。`hydrology-plan`単独、unknown capability／type/version、missing／extra、graph checksum改変を拒否する。publisher／layout例は`examples/v2/release-hydrology/README.md`、Acceptanceは`ReleaseHydrologyPublisherVerifierV2Test`である。V2-3-14単体ではfeature lifecycleを変更せず、V2-3-15統合監査後にcapabilityと完成featureだけをoffline `SUPPORTED`とした。CLI／Paper applyは含まない。

## V2-4-14 `environment-fields` capability

`environment-fields` manifestは`requiredCapabilities[] = ["environment-fields","hydrology-plan","surface-2_5d"]`だけを許し、ADR 0013／0014のsurface／hydrology exact setに加えて次を`artifacts[]`へ個別indexする（ADR 0019）。

- `environment/geology-plan.json`／`lithology-plan.json`／`strata-plan.json`／`climate-plan.json`／`water-condition-plan.json`（Blueprint内planと一致）
- `environment/snow-plan.json`（`snow-plan-v2`、climate checksum binding）
- `environment/material-profile-plan.json`／`minecraft-palette-plan.json`／`ecology-plan.json`／`feature-material-profile-plan.json`
- `environment/validation.json`
- `environment/previews/index.json`と固定10 PNG（`environment-preview-png-v1`）

strict verifierはsurface→hydrology→environmentの順で照合し、palette／material／snow／ecologyのchecksum binding、validation hard-pass、preview寸法を検査する。`environment-fields`単独、unknown capability／type/version、missing／extra、palette checksum改変を拒否する。publisher／layout例は`examples/v2/release-environment/README.md`、Acceptanceは`ReleaseEnvironmentPublisherVerifierV2Test`である。`V2-4-15`統合監査を通過したためcapabilityと対象featureのoffline lifecycleは`SUPPORTED`であるが、CLI／Paper applyは含まない。

## V2-5-17 `sparse-volume` capability

`sparse-volume` manifestは`requiredCapabilities[] = ["environment-fields","hydrology-plan","sparse-volume","surface-2_5d"]`（natural order）だけを許し、`environment-fields` exact setに加えて次を`artifacts[]`へ個別indexする。

- `volume/sdf-primitive-plan.json`（`volume-sdf-primitive-plan-v2`）
- `volume/csg-plan.json`（`volume-csg-plan-v2`、SDF checksum binding）
- `volume/aabb-index-plan.json`（`volume-aabb-index-plan-v2`、CSG checksum binding）
- `volume/validation.json`（`volume-validation-artifact-v2`、`sourcePlanChecksum`＝CSG checksum、hard-pass）
- `volume/tiles/<id>.json`／`volume/tiles/<id>.schem`（3D volume tile。`volume-offline-tile-artifact-v2`／`volume-sponge-schematic-v3`。schemaは`offline-tile-artifact-v2` / Sponge v3を共有するが、surface tile集合の`ofType`集計と混ざらない専用artifact type）

strict verifierはsurface→hydrology→environment→volumeの順で照合し、CSG→SDF／AABB→CSGのplan checksum binding、validation hard-pass、volume tileのstrict Sponge v3 read-back（semantic checksum一致）と`sourceBlueprintChecksum` binding、tile ID／schematic path canonicalを検査する。`sparse-volume`が依存capabilityを欠く、unknown capability／type/version、missing／extra、plan／tile checksum改変を拒否する。既存の`surface-2_5d`／`hydrology-plan`／`environment-fields` releaseは変更なくstrict verifyできる（回帰）。publisher／layout例は`examples/v2/release-sparse-volume/README.md`、Acceptanceは`ReleaseSparseVolumePublisherVerifierV2Test`である。`sparse-volume`と対象volume featureは、V2-5親Phase統合監査（`V2-5-18`、2026-07-18完了）でoffline `SUPPORTED`へ昇格した。CLI／Paper applyは含まない。

## V2-6-12 Release 2 cross-capability hardening

`ReleaseCapabilityDependencyMatrixV2`（ADR 0030）が Release format 2 の valid capability prefix 正本である。許容集合は core 空集合、`surface-2_5d`、`hydrology-plan`＋`surface-2_5d`、`environment-fields`＋hydrology＋surface、`sparse-volume`＋environment の5個だけである。`ReleaseArtifactCatalogV2`と`PlacementPlanV2`は同じ matrix を参照する。`ReleaseCrossVersionReaderPolicyV2`は format 2／manifest 1 以外を forward-read しない。`ReleaseArtifactLimitsCatalogV2`が core／payload-adjacent ceiling を集約する。

`ReleasePlacementEligibilityVerifierV2`は directory／ZIP の strict verify、sealed canonical checksum、valid prefix を placement 前に要求し、plan の Release binding／capability 一致を検査する。`ReleasePlacementInputContractV2`は surface／hydrology／environment／sparse-volume の共通 overlay ordinal stream を定義し、foundation／bathymetry host kind も Feature 別 placement type なしで同じ stream へ bind する契約を固定する。Acceptance は`ReleaseCrossCapabilityHardeningV2Test`／matrix／placement-input contract test。Release format 1 allowlist は共有せず緩めない。world mutation・新 artifact type・format 3 は含まない。

## V2-12-02 production Release 2 export path

`surface-2_5d` Releaseを作るproduction経路は`core.v2.export`の`Release2ExportApplicationServiceV2`である。入力はsealed `GenerationRequestV2`とdesign stageの`TerrainIntentV2`、公開先`exportsRoot`、`releaseId`、cellのbaselineを明示する`SurfaceBaselineV2`、`ExportBudgetV2`だけで、artifact pathやchecksumを呼び出し側が組み立てることはない。serviceは constraint field sidecar → Blueprint → field-only coastal validation → 固定11 preview → `TilePlanV2`幾何のSponge v3 tile を生成し、`SurfaceReleaseSourceV2`として`ReleaseSurfacePublisherV2`へ渡す。publish（staging→strict read-back→atomic publish）後に`ReleaseSurfaceVerifierV2`でdirectoryとZIPのmanifest一致を確認し、`ReleasePlacementEligibilityVerifierV2`のeligibilityを取得できた場合だけ`Release2ExportResultV2`を返す。artifact type／version、必須file集合、capability prefixはV2-2-11から不変である。

tile幾何は`TilePlanV2`が正本のため、published Releaseは再tilingなしに`VerifiedReleaseCanonicalBlockSourceV2`で開ける。生成budget（tile数、dense descriptor working set）はartifactを1本も書く前にadmitし、生成途中のcancelはstagingとpublish済みdirectoryの双方を残さない。

## V2-15-08 production `sparse-volume` export path

`Release2SparseVolumeExportApplicationServiceV2`は既存のsurface→hydrology→environment production chainへ
`SparseVolumeExportPipelineV2`を重ね、V2-5-17で固定済みのexact capability prefixとartifact type／versionだけを
publishする。pipelineはbounded SDF、ordinal 0からのordered CSG、CSG-bound AABB index、hard-pass volume
validation、`TilePlanV2`幾何の3D Sponge v3 tileを生成し、`ReleaseSparseVolumePublisherV2`のstaging→strict
directory／ZIP read-back→atomic publishへ渡す。個別volume Featureの公開配線前なので、sealed bedrock cell上の
identity `ADD_FLUID` operatorでordered kernelを実行しつつsurface canonical block streamを保持する。
全域dense voxel、new artifact type／Schema／capability、CLI既定切替、Paper mutationは含まない。

## V2-12-04 v1→v2 migration bundle

migration bundleは、既存v1資産をv2側で検証可能な形へ移すための出力単位である（[ADR 0035](adr/0035-v1-retirement-governance.md) D9）。Release Packageではなく、Release 2 design packageとその変換記録をまとめたものである。

```text
<output-root>/<migration-id>/
  migration-report-v2.json
  designs/<request-id>/<job-id>/
    terrain-intent-v2.json
    audit-v2.json
    checksums.sha256
  checksums.sha256
```

- `migration-report-v2.json` は`migration-report-v2.schema.json`のstrict契約に従い、source kind／source schemaVersion／source digest／source canonical checksum（v1側が定義しない場合は空文字）／published design packageのrequestId・jobId・intent checksum／写したfield／写さなかったelementとその理由／`DRY_RUN`|`PUBLISHED` を持つ。
- bundleの`checksums.sha256`はbundle内の全fileをsorted relative pathで完全被覆する。missing／extra／改変はすべて拒否される。
- publishは staging → strict read-back → atomic move → published read-back の順で、`LegacyMigrationBundleVerifierV2`がreportとdesign packageの一致（intent checksum、requestId、jobId、intentVersion）まで検証する。
- 出力先が既に存在する場合は上書きせず失敗する。sourceは読むだけで変更しない。
- 同一sourceからは常に同一bundleが得られる。job UUIDはsource digestから導出し、timestampはv1 auditの値（無い場合はepoch）を使う。

v1 contractのstrict readは`src/main/resources/legacy/v1/contracts/`のimmutable resourceでも解決できる（ADR 0035 D2b／R7）。`V2-12-06`がactive `schemas/`からv1 schemaを外した後も、packaged JAR単独でv1資産を読めることを`LegacyMigrationPackagedJarV2Test`が担保する。

Release format 1のtile schematic、structures、required assets、previewは**migrationの変換対象ではない**。Release 2 containerは module／stage／field descriptorを持つv2 Blueprintから封じるもので、Release 1はそれを持たない。したがってblock artifactは移行後のintentから`lfc v2 export`で作り直し、reportはその旨を非対応elementとして明示する。

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
