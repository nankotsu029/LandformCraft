# 地形Feature Taxonomy

> Status: V2-0〜V2-5のoffline gate、V2-9／V2-10 Phase gate、`V2-6-18` Feature Support Catalog、および`V2-6-19` V2-6 Phase gate（[audit](audits/v2-6-phase-gate.md)）まで完了した。能力別supportの正本は`FeatureSupportCatalogV2`（`examples/v2/catalog/feature-support-catalog-v2.json`）である。Paper能力列は`UNSUPPORTED`のまま（実機evidence範囲での昇格は`V2-11-01`）、Paper寸法hard limitはsmoke実測の64×64。500／1000実測Taskは無効化済み。次のTrack A Taskは`V2-11-01`。詳細は [V2-9 Phase gate audit](audits/v2-9-phase-gate.md)、[V2-10 Phase gate audit](audits/v2-10-phase-gate.md)、[Gap監査](../audits/terrain-feature-gap-audit-2026-07-18.md) を参照する。

## 1. 目的

地形名を1個の巨大enumへ追加し続けると、意味、geometry、parameter、generator、validatorの対応が崩れる。v2ではversion付きcatalogを正本にし、各feature kindが実行可能な契約を持つ。

```text
FeatureKindDescriptor
├── kindId / catalogVersion
├── primaryRole / allowedUsages[]
├── category / dimensionality
├── allowedGeometryTypes[]
├── parameterSchemaId
├── allowedRelationKinds[]
├── compilerModuleId / generatorModuleId
├── requiredFields[] / providedFields[]
├── validatorRuleIds[] / previewLayerIds[]
├── defaultSupportRadius / resourceClass
├── support.{capability}
└── lifecycleStatus（互換表示。能力判定には使用しない）
```

`lifecycleStatus` は既存moduleとの互換表示として `EXPERIMENTAL`、`SUPPORTED`、`DEPRECATED` を持つが、offline対応、限定利用、Paper対応の判定へ単独使用しない。未知kindを近いkindへ黙って変換しない。

### 1.1 Support capability contract

各catalog entryは最低でも次を持つ。省略や「offline supported」の一語への集約を禁止する。

```yaml
support:
  intent_compile: SUPPORTED
  offline_generate: SUPPORTED
  validation: SUPPORTED
  preview: SUPPORTED
  export: SUPPORTED
  standalone_usage: SUPPORTED
  child_plan_usage: NOT_APPLICABLE
  volume_overlay_usage: NOT_APPLICABLE
  paper_apply: UNSUPPORTED
  post_apply_validation: UNSUPPORTED
  snapshot: UNSUPPORTED
  rollback: UNSUPPORTED
  restart_recovery: UNSUPPORTED
```

| 状態 | 意味 |
|---|---|
| `SUPPORTED` | 公開contract、positive/negative test、決定性、resource上限が揃い、通常利用できる。 |
| `PARTIAL` | 特定親、入力、出力経路、またはplan-level APIでのみ利用できる。制限をentryへ列挙する。 |
| `EXPERIMENTAL` | API、出力、品質、決定性、互換性のいずれかがRelease contractとして安定していない。 |
| `UNSUPPORTED` | 実装がない、または意図的に利用を許可しない。 |
| `NOT_APPLICABLE` | そのroleの性質上、その能力を適用しない。未実装の言い換えには使わない。 |

`paper_apply`、`post_apply_validation`、`snapshot`、`rollback`、`restart_recovery`は、`V2-11-01`（2026-07-20完了）で実機evidenceの範囲だけを`SUPPORTED`へ昇格した。昇格対象はsmoke実測済みの`surface-2_5d` Release capability prefixを持つ4 entry（SANDY_BEACH、BREAKWATER_HARBOR、HARBOR_BASIN、ROCKY_CAPE）であり、runtimeは`paper-1.21.11+worldedit-7.3.19|fawe-2.15.2`、寸法は64×64 hard limit内に限る。`hydrology-plan`／`environment-fields`／`sparse-volume` prefixの21 entryはprefixごとの実機smokeが無いため5列とも`EXPERIMENTAL`のままであり、Release capabilityを持たないV2-9／V2-10 foundation entryは`UNSUPPORTED`のままである（共通canonical placement streamを共有していても、export接続前にPaper能力を昇格させない）。snapshot-all基盤やWE／FAWE smokeだけの完了を、evidenceの無いprefixのFeature能力へ波及させない。Paper寸法hard limitはsmoke実測の64×64であり、500／1000は未測定（再実測は`V2-11-04`／`V2-11-05`、昇格は`V2-11-06`）である。正本は`V2-6-18` catalog（`BuiltInFeatureSupportCatalogV2`）、証拠は`V2-6-14`／`V2-6-15` smokeと [V2-6 Phase gate audit](audits/v2-6-phase-gate.md) である。

### 1.2 Role contract

primary roleは次の8分類から選び、複数利用できる場合だけ`allowedUsages[]`へ追加する。

| Role | 所有するもの | FeatureKind候補 |
|---|---|---|
| `MACRO_CONSTRAINT` | land-water connectivity、containment、幅、mask topology | 原則no |
| `STANDALONE_FEATURE` | 独立ownership、入力、generator、validation | yes |
| `VOLUME_OVERLAY` | 親surface/graphへchecksum-boundした局所3D operation | 原則no |
| `CHILD_PLAN_ONLY` | 特定親のownership内でだけ意味を持つtyped child plan | 原則no。legacy enumは維持 |
| `COMPONENT` | ridge、peak、graph node等の親内部要素 | no |
| `MODIFIER` | reach、surface、material等の限定変更 | no |
| `ENVIRONMENT_PROFILE` | climate、ecology、material条件 | no |
| `COMPOSITE_PRESET` | 複数Feature/profile/constraintのchecksum付き展開 | no |

FeatureKindへ追加できるのは、独立ownership、独立validator contract、明確なgenerator境界、親なしでも意味のある入力または明確なvolume feature、Preview/Export識別の必要性をすべて満たすものだけである。見た目、気候、生態、graph role、preset名だけでは追加しない。

## 2. 8つの概念を混ぜない

| 分類 | 例 | 責務 |
|---|---|---|
| A. Macro Land-Water Constraint | bay、strait、peninsula | land-water topologyとinput mask semantic |
| B. Standalone Surface Feature | beach、river、mountain range | 2.5D fieldの独立ownershipとtransition |
| C. Standalone Volumetric Feature | cave network、natural arch | bounded AABB内のsparse solid/fluid ownership |
| D. Child Feature / Child Plan | lagoon、reef pass、caldera | 特定親のcompiled planとしてのみ利用 |
| E. Graph Node / Edge / Reach Class | headwater、confluence、rapids | global graph内部のrole |
| F. Component / Modifier | ridge、peak、talus、sandbar | 親Feature内部の再利用要素 |
| G. Environment / Ecology Profile | forest、snow、mangrove ecology | 温度、湿度、habitat、material条件 |
| H. Composite Preset | ice fjord、waterfall chain | 複数Feature/profile/constraintの展開 |

`MANGROVE_WETLAND`や`CORAL_REEF`の既存IDは互換性のためstandalone regional kindとして維持するが、environment/profileやchild planを同一kindへ吸収しない。breakwaterは水深と海岸を変えるためstructureではなくfeatureである。

## 3. Category catalog

能力別状態の機械可読正本は`FeatureSupportCatalogV2`（V2-6-18、`feature-support-catalog-v1`）である。人間向け要約として次のprofileを用い、個別根拠は [Gap監査 §3〜4](../audits/terrain-feature-gap-audit-2026-07-18.md#3-capability-profile) に置く。

| Group | Primary role | intent | generate | validate | preview | export | standalone | child | overlay | Paper以降 |
|---|---|---|---|---|---|---|---|---|---|---|
| 専用moduleへbinding済みsurface 16 kind | `STANDALONE_FEATURE` | S | S | S | S | S | S | N | N | U |
| `LAGOON` / `REEF_PASS` / `VOLCANIC_CALDERA` / `LAVA_FLOW_FIELD` | `CHILD_PLAN_ONLY` | P | P | P | P | P | U | P | N | U |
| plan-level volume 7 feature | `STANDALONE_FEATURE` | EまたはU | S | S | S | S | P | feature依存 | N | U |
| `WATERFALL_VOLUME` | `VOLUME_OVERLAY` | N | S | S | S | S | N | N | S | U |
| post-volume local environment | `COMPONENT` | N | S | S | S | S | N | N | N | U |
| V2-9 foundation 19 kind＋macro topology／`WATERFALL_CHAIN` preset | `STANDALONE_FEATURE`等 | P | S | S | S | P | P | feature依存 | feature依存 | U |
| V2-10 deferred family 14 kind（glacial ice／deposition、karst graph、additional marine、escarpment/plateau、lava tube、spring、oxbow lake） | `STANDALONE_FEATURE`等 | P | S | S | S（`MORAINE_FIELD`／`OUTWASH_PLAIN`はE） | P | P | feature依存 | feature依存 | U |
| V2-10 preset（`ICE_FJORD` `CENOTE` `BARRIER_ISLAND` `ATOLL`） | `COMPOSITE_PRESET` | N | S | S | E | P | P | N | N | U |

V2-9／V2-10行の`intent = P`はdiagnostic module binding経由のみ（専用module catalog未登録・WorldBlueprint checksum凍結）、`export = P`はsealed canonical plan JSON＋streaming tile checksum経路のみ（Release 2 capability未定義）を意味する。詳細matrixは [V2-9 Phase gate audit](audits/v2-9-phase-gate.md) と [V2-10 Phase gate audit](audits/v2-10-phase-gate.md) を正本とする。

`S/P/E/U/N`は順に`SUPPORTED`／`PARTIAL`／`EXPERIMENTAL`／`UNSUPPORTED`／`NOT_APPLICABLE`である。`Paper以降`はpaper apply、post-apply validation、snapshot、rollback、restart recoveryの全列を指す。

### 3.0 Macro Land-Water

`BAY`、`COVE`、`HEADLAND`、`PENINSULA`、`ISTHMUS`、`STRAIT`、`ENCLOSED_BASIN`、`COASTAL_EMBAYMENT`は、通常のregional generatorではなく`MACRO_CONSTRAINT`として扱う。入力mask/zoneからland-water connectivity、containment、minimum neck/channel width、coast orientationをcompileし、後段Featureへfreezeしたtopologyを渡す。独立FeatureKindや8個のgeneratorへ分割しない。V2-9-12でmanual mask／zone→`MacroLandWaterTopologyPlanV2`のoffline compile／validation／preview／freezeを実装し、`V2-9-14` gateでplan-level offline compile／validation／previewを`SUPPORTED`とした（画像draft経路とcoast shapingは未接続のまま）。

`OCEAN_BASIN`はmacro topologyのhostにもなるが、depth fieldの独立ownershipとvalidatorを持つためmarine bathymetryの`STANDALONE_FEATURE`候補である。

### 3.1 Coast / Marine

| Kind | 次元 | 主なgeometry | 専用生成要素 |
|---|---:|---|---|
| `SANDY_BEACH` | 2.5D | spline/polygon | 幅profile、shore slope、砂帯、遠浅 |
| `GRAVEL_BEACH` | 2.5D | spline/polygon | gravel ridge、粒径帯、急なshore |
| `ROCKY_COAST` | 2.5D+局所3D | spline/polygon | 岩棚、水路、露岩 |
| `ROCKY_CAPE` | 2.5D+局所3D | polygon | cape relief、sea stack、channel |
| `SEA_CLIFF` | 2.5D+局所3D | spline | cliff face、talus、wave-cut notch |
| `BREAKWATER_HARBOR` | 2.5D | multi-spline | arm、crest、opening、接続 |
| `HARBOR_BASIN` | 2.5D | polygon | navigable depth、entrance、inner/outer side |
| `TIDAL_POOL_COAST` | 2.5D | spline/polygon | 岩棚、basin、spill elevation |
| `LAGOON` | 2.5D | polygon | enclosing rim、depth、inlet |
| `TIDAL_FLAT` | 2.5D | polygon | 微勾配、潮路、泥／砂帯 |
| `BLACK_SAND_BEACH` | 2.5D | spline | volcanic sediment profile |
| `SEA_STACK_FIELD` | 2.5D+3D | polygon/multipoint | stack分布、基部侵食 |

`SANDY_BEACH`、`BREAKWATER_HARBOR`、`HARBOR_BASIN`、`ROCKY_CAPE`だけが`v2.coast.foundation`へbindingされる。block座標geometry、coast-side、signed-distance／normal／nearshore field ownershipを持ち、COASTLINE planは共通integer rasterでsampleできる。各feature固有generatorの出力は、明示transition relationとpriorityをfreezeした`v2.coast.transition`が合成する。`v2.coast.validation-preview`はfinal field samplerだけを読み、Blueprint target、corruption validator、4 overlay＋desired/actual/residual＋constraint errorのstrict preview indexを提供する。HARD cellはexactに保護し、breakwater／basinは明示0-band seam、他の隣接／overlapは1..32 blockのpriority blendとする。未契約overlapや曖昧な同票は拒否し、module登録順で解決しない。`capeMode=LOCAL_VOLUME_REQUIRED`は3Dへfallbackせず`v2.cape-volume-required`で診断し、overhang／sea cave／full 3D stackはV2-5へ残す。4 kindはRelease capabilityと`V2-2-12`統合監査までを完了したため、offline生成／検証／export経路で`SUPPORTED`である。

`ROCKY_COAST`と`SEA_CLIFF`はP0 foundationであり、V2-9-06のvertical slice（`RockyCoastPlanV2`／`SeaCliffPlanV2`／`FoundationRockyCoastCliffSliceCompilerV2`）は`V2-9-14` gateでplan-level offline generate／validation／previewが`SUPPORTED`となった（catalog未登録のままintent／standalone／exportは`PARTIAL`）。v1 `ROCKY_COAST`をv2対応済みと数えず、`ROCKY_CAPE`／`SEA_CAVE`／`OVERHANG`とのownership、transition、volume host関係をこのsliceで接続する。

### 3.1a Marine Bathymetry

| Kind | Primary role | 主要contract | 現在 |
|---|---|---|---|
| `OCEAN_BASIN` | `STANDALONE_FEATURE` | deep-water depth field、water level、basin ownership | EXPERIMENTAL foundation slice（V2-9-08、catalog未登録・V2-9-14でplan-level offline G/V/P `SUPPORTED`・2.5D only） |
| `CONTINENTAL_SHELF` | `STANDALONE_FEATURE` | coast-to-shelf depth、低勾配transition | EXPERIMENTAL foundation slice（V2-9-08、catalog未登録・V2-9-14でplan-level offline G/V/P `SUPPORTED`・2.5D only） |
| `CONTINENTAL_SLOPE` | `STANDALONE_FEATURE` | shelf-to-basin depth、bounded slope | EXPERIMENTAL foundation slice（V2-9-08、catalog未登録・V2-9-14でplan-level offline G/V/P `SUPPORTED`・2.5D only） |
| `SUBMARINE_CANYON` | `STANDALONE_FEATURE` | shelf/slopeを横断するbathymetric carve | EXPERIMENTAL foundation slice（V2-9-09、catalog未登録・V2-9-14でplan-level offline G/V/P `SUPPORTED`・surface `CANYON`と別kind・2.5D only） |
| `ABYSSAL_PLAIN` | `STANDALONE_FEATURE` | basin内の深海flat floor、低relief depth field | EXPERIMENTAL foundation slice（V2-10-04、catalog未登録・HARD `WITHIN`→`OCEAN_BASIN`・2.5D only・V2-10-09でplan-level offline G/V/P `SUPPORTED`） |
| `SEAMOUNT` | `STANDALONE_FEATURE` | basin内の局所underwater relief cone | EXPERIMENTAL foundation slice（V2-10-04、catalog未登録・HARD `WITHIN`→`OCEAN_BASIN`・2.5D only・V2-10-09でplan-level offline G/V/P `SUPPORTED`） |
| `OCEAN_TRENCH` / `MID_OCEAN_RIDGE` / `SUBMARINE_VOLCANO` | advanced standalone候補 | plate-scale linear carve／divergent ridge continuity／volcanic provenance vs seamount | catalog評価のみ（V2-10-04：FeatureKind化・generatorなし。`OCEAN_TRENCH`はLARGE/global graph、`MID_OCEAN_RIDGE`はplate-scale continuity、`SUBMARINE_VOLCANO`は`SEAMOUNT`／`VOLCANIC_CONE` ownership未確定） |

最初のmarine waveはbasin→shelf→slopeの連続性を先に閉じ、submarine canyonを後続sliceとする。V2-9-08のfoundation slicesは`V2-9-14` gateでplan-level offline generate／validation／previewが`SUPPORTED`となった（catalog未登録のままintent／standalone／exportは`PARTIAL`）。深海候補を最初のwaveへ無条件に含めない。全域dense 3D bathymetryは作らず、2.5D depth fieldとstreaming tile queryを使う。

### 3.2 Mountain / Tectonic / Volcanic

| Kind | 次元 | 主なgeometry | 専用生成要素 |
|---|---:|---|---|
| `HILL_RANGE` | 2.5D | spline/polygon | smooth ridge、saddle |
| `MOUNTAIN_RANGE` | 2.5D | spline | ridge、peak、spur |
| `ALPINE_MOUNTAIN_RANGE` | 2.5D | spline | sharp ridge、scree、snow band |
| `GLACIAL_MOUNTAIN_RANGE` | 2.5D | spline/polygon | U谷、cirque、arête |
| `VOLCANIC_CONE` | 2.5D | point/polygon | cone、crater、radial drainage |
| `VOLCANIC_CALDERA` | 2.5D | point/polygon | rim、floor、breach |
| `LAVA_FLOW_FIELD` | 2.5D+3D | spline/polygon | levee、lobe、lava tube hook |
| `PLATEAU` | 2.5D | polygon | cap elevation/relief、escarpment transition band（`EXPERIMENTAL` V2-10-06、V2-10-09でplan-level offline G/V/P `SUPPORTED`） |
| `MESA` / `BUTTE` | — | — | `PlateauProfile` only（FeatureKind化しない） |
| `KARST_PEAK_FIELD` | 2.5D+3D | polygon | tower distribution、sinkhole |
| `GLACIAL_CIRQUE_FIELD` | 2.5D | multipoint | bowl、headwall、threshold |

`ALPINE_MOUNTAIN_RANGE`／`GLACIAL_MOUNTAIN_RANGE`はridge／peak／saddle／spur fieldにV2-4 snow／material／ecology portfolioを統合し、`V2-4-15`監査後のoffline lifecycleは`SUPPORTED`である。`GLACIAL_CIRQUE_FIELD`は独立生成が未完成のため`EXPERIMENTAL`を維持する。

`RIDGE`、`PEAK`、`SADDLE`、`SPUR`、`MOUNTAIN_PASS`、`FOOTHILLS`、`TALUS_SLOPE`、`SCREE`は`COMPONENT`または`MODIFIER`でありFeatureKindへ追加しない。P0 `MOUNTAIN_RANGE`はこれらの再利用contractを提供するが、既存ALPINE／GLACIAL generatorのseed、checksum、出力を一括変更せず、version境界付きで段階的に共通化する。`ESCARPMENT`はV2-10-06で`EXPERIMENTAL` FeatureKind＋`EscarpmentPlanV2`として独立ownershipとlong-scarp transition validatorを持つ（catalog未登録、V2-10-09でplan-level offline G/V/P `SUPPORTED`）。

### 3.3 Valley / Canyon

| Kind | 次元 | 主なgeometry | 専用生成要素 |
|---|---:|---|---|
| `VALLEY` | 2.5D | spline | V/U断面、floor、shoulder |
| `CANYON` | 2.5D | spline | rim/floor幅、terrace、strata exposure |
| `GORGE` | 2.5D+3D | spline | narrow wall、岩棚、滝 |
| `RAVINE` | 2.5D | spline | small incised channel |
| `RIFT_VALLEY` | 2.5D | spline/polygon | opposing scarps、flat floor |
| `FJORD` | 2.5D | spline | marine channel、U断面、head basin |

`FJORD` は海岸と谷の両方の性質を持つが、主な形成／validationがglacial valley断面とmarine channelなのでValley catalogへ置く。UIは複数tagで検索できる。

### 3.4 Hydrology

| Kind | 主なIR | 専用生成要素 | V2-10-05 disposition |
|---|---|---|---|
| `RIVER` / `MEANDERING_RIVER` | reach graph | source、合流、bank、floodplain | existing |
| `BRAIDED_RIVER` | reach DAG | split/merge、bar、active channel | deferred later DAG Task |
| `BEDROCK_RIVER` | reach graph | bedrock channel、step/pool | existing diagnostic |
| `LAKE` | basin plan | independent level、rim、inlet/outlet | existing SUPPORTED |
| `OXBOW_LAKE` | reach relation | cutoff、stagnant water、wetland | implemented EXPERIMENTAL（V2-10-11、`V2-10-09` gateでplan-level offline G/V/P `SUPPORTED`・catalog未登録） |
| `DELTA` | distributary DAG | fan、分流、sandbar、sediment | existing SUPPORTED |
| `WATERFALL` | fall node | lip、drop、water column、plunge pool | existing |
| `SPRING` | source node | groundwater source、outflow（≠`KARST_SPRING`） | implemented EXPERIMENTAL on `V2-10-10`（`V2-10-09` gateでplan-level offline G/V/P `SUPPORTED`・catalog未登録） |
| `DAM_RESERVOIR` | barrier＋lake plan | dam crest、reservoir level、spillway、outlet | deferred later barrier Task |
| `ESTUARY` | mouth composition | DELTA＋tidal coupling | deferred later composition |
| `ALLUVIAL_FAN` | sediment fan | inland fan terminal | deferred later standalone |
| `RIVER_TERRACE` | reach profile/child | terrace band | deferred child/profile |
| `TIDAL_CHANNEL_NETWORK` | bidirectional/tidal graph | branching channel、tidal range | existing SUPPORTED |

一般`RIVER`を共通contractとし、既存`MEANDERING_RIVER`のID、schema、seed、checksumは維持したままsubtypeとして段階接続する。`STREAM`はsmall reach class、`HEADWATER`はsource node、`TRIBUTARY`はreach relation、`CONFLUENCE`はjunction node、`DISTRIBUTARY`はsplit edge、`RAPIDS`はreach modifier、`PLUNGE_POOL`はwaterfall child plan、`SANDBAR`と`RIVER_ISLAND`はchannel/sediment child featureであり、新FeatureKindにしない。V2-9-13で一般`RiverPlanV2`へこれらのrole／class／modifier／child ownershipを載せ、`WATERFALL_CHAIN` COMPOSITE_PRESET（`WaterfallChainPlanV2`）が複数WATERFALL node＋plunge-pool child＋elevation continuityを同じIR上でcompileする。専用waterfall-chain world generatorとestuary／braided vertical sliceは非Scopeのままである。`V2-9-14` gateで一般`RIVER` contractと`WATERFALL_CHAIN` presetのplan-level offline generate／validation／previewを`SUPPORTED`とした（`MEANDERING_RIVER`の既存SUPPORTED経路は不変）。

`WATERFALL_CHAIN`は`COMPOSITE_PRESET`であり、river reach graph＋複数`WATERFALL` node＋plunge-pool child plan＋elevation continuityとしてcompileする。`V2-10-05`は`AdvancedRiverLakeSplitContractV2`で7候補のownership分類をfreezeし、最初の実装対象を`SPRING`（`V2-10-10`）と`OXBOW_LAKE`（`V2-10-11`）へ固定した。残りはdeferred（FeatureKind未導入）。

`DELTA`の現在のcatalog bindingは`v2.hydrology.delta` version `0.1.0-v2-3-07`である。単一POLYGON fan、HARD river `DRAINS_TO`、HARD SEA boundary `EMPTIES_INTO`を要求し、flow-conserving distributary、低起伏fan、sandbar／shallow receiving seaのdescriptor fieldだけを所有する。独立validator／previewとstrict Releaseを含むV2-3 gate完了によりoffline lifecycleは`SUPPORTED`である。mangrove／sediment environmentは別のV2-4 supported portfolioで結合し、Paperは未対応である。

`TIDAL_CHANNEL_NETWORK`の現在のcatalog bindingは`v2.hydrology.tidal` version `0.1.0-v2-3-08`である。MULTI_SPLINE、`BIDIRECTIONAL` edge kind、HARD SEA boundary `EMPTIES_INTO`を要求し、optional HARD `WITHIN` mangrove child-plan hookとchannel／branch／depth／marine-connection descriptor fieldだけを所有する。standalone networkのoffline lifecycleはV2-3 gate完了により`SUPPORTED`である。複合mangrove scenarioのregional wetland shaping、salinity／hydroperiod、sparse habitat／root descriptorと、複合coral atollのregional bathymetry／colony descriptorはV2-4 gate完了によりoffline `SUPPORTED`である。Paper／entity／実block配置は未接続である。

`VOLCANIC_ARCHIPELAGO`の現在のcatalog bindingは`v2.landform.volcanic` version `0.1.0-v2-3-11`である。MULTI_POINT island mass、dry-land gap、central dominance、submarine saddle、radial drainage、optional HARD `WITHIN` caldera／`ORIGINATES_AT` lava child-plan hookと6個のdescriptor fieldだけを所有する。basalt／tuff／ashのfeature material overlayをV2-4-12で統合し、V2-4-15監査後のoffline G/V/P/Xは`SUPPORTED`である。`VOLCANIC_CALDERA`／`LAVA_FLOW_FIELD`は親内でtyped child hookとしてcompile/generate/testされるため`child_plan_usage: PARTIAL`、`standalone_usage: UNSUPPORTED`である。lava tubeとPaperは`UNSUPPORTED`である。

### 3.5 Wetland / Plain / Dry land

| Category | Kinds |
|---|---|
| Plain | `PLAIN`, `FLOODPLAIN`, `SAVANNA_PLAIN` |
| Wetland | `MARSH`, `BOG`, `FEN`, `MANGROVE_WETLAND`, `SALT_MARSH` |
| Dune | `COASTAL_DUNE_FIELD`, `INLAND_DUNE_FIELD`（`DryLandModifierContractV2` modifier only） |
| Arid | `DESERT_FLAT`, `BADLANDS`, `DRY_CANYON`, `SALT_FLAT`（modifier only；surface `CANYON` FeatureKindは不変） |

plainは「何もないdefault」ではなく、微地形、地下水位、河川との関係を持つfeatureである。duneは風向き、crest line、stoss/lee slopeをparameterに持つ。V2-10-06ではdry-land modifier候補7種を`DryLandModifierContractV2`へ分類し、FeatureKind／generatorは導入しない。

P0は`PLAIN`、`HILL_RANGE`、`FLOODPLAIN`、`MARSH`の共通field／ownership／transitionを先に閉じる。v1 `PLAINS`／`WETLAND`や既存river `FLOODPLAIN_MASK`をv2 standalone対応済みとは数えない。`BOG`、`FEN`、`SALT_MARSH`、`SAVANNA_PLAIN`はこの基盤後にprofileかstandaloneかを判定する。

### 3.6 Island / Reef

| Kind | 専用生成要素 |
|---|---|
| `SINGLE_ISLAND` | core、coast band、drainage |
| `BARRIER_ISLAND` | shore-parallel ridge、lagoon（V2-10-08 `BarrierIslandPlanV2` COMPOSITE_PRESET、FeatureKindなし） |
| `ARCHIPELAGO` | size distribution、spacing、submarine saddle |
| `VOLCANIC_ARCHIPELAGO` | volcanic arc、cone/caldera relation |
| `ATOLL` | reef ring、lagoon、islets、pass（V2-10-08 `AtollPlanV2` COMPOSITE_PRESET、FeatureKindなし） |
| `CORAL_REEF` | reef crest、outer slope、coral habitat |
| `REEF_PASS` | reefを横切るmarine connection、幅、navigable depth |

`SINGLE_ISLAND`、`ARCHIPELAGO`、`VOLCANIC_CONE`はP0 coast/island foundationである。V2-9-07のfoundation slices（`SingleIslandPlanV2`／`ArchipelagoPlanV2`／`VolcanicConePlanV2`と各slice compiler）は`V2-9-14` gateでplan-level offline generate／validation／previewが`SUPPORTED`となった（catalog未登録のままintent／standalone／exportは`PARTIAL`）。既存`VOLCANIC_ARCHIPELAGO`を壊さず、island mass、shore transition、cone childの共通contractを段階的に抽出する。`VolcanicIslandConeAdapterV2`はsuggested paramsのみを提供し、volcanic checksum／hook IDは未変更である。

`LAGOON`と`REEF_PASS`は現行`CORAL_REEF` compilerが親内で利用する。両者は`primaryRole: CHILD_PLAN_ONLY`、`child_plan_usage: PARTIAL`、`standalone_usage: UNSUPPORTED`であり、親Featureのoffline `SUPPORTED`を根拠にstandaloneへ昇格させない。

V2-10-08では`BARRIER_ISLAND`（`SINGLE_ISLAND`＋`LAGOON`）と`ATOLL`（`CORAL_REEF`＋`LAGOON`＋`REEF_PASS`）を`COMPOSITE_PRESET`としてplan-level composition sliceで閉じた（FeatureKind／dedicated moduleなし、`AdvancedIslandReefCatalogContractV2`でcatalog候補を分類）。`CORAL_COAST`／`MANGROVE_COAST`／`VOLCANIC_COAST`／`DUNE_BACKED_BEACH`はcatalog classification only、`FLOATING_REEF`はdeferred。

### 3.7 Volumetric / Subterranean / Aerial

| Kind | 主operation |
|---|---|
| `CAVE_NETWORK` | tunnel/chamber carve（plan-level offline G/V/P/Xは`SUPPORTED`、public Intentは`EXPERIMENTAL`） |
| `CAVE_ENTRANCE` | surface-volume connector（V2-9-10）。`MOUNTAIN_RANGE`／`VALLEY`＋frozen `CAVE_NETWORK`へHARD bind。in-network ENTRANCE nodeとは別。V2-9-14でplan-level offline G/V/P `SUPPORTED`（intent/standalone/export `PARTIAL`） |
| `LUSH_CAVE` | chamber carve＋wet surface／ecology hooks（plan-level offline G/V/P/Xは`SUPPORTED`、public Intentは`EXPERIMENTAL`） |
| `SEA_CAVE` | carve＋marine opening＋static fluid（plan-level offline G/V/P/Xは`SUPPORTED`、Paperは`UNSUPPORTED`） |
| `LAVA_TUBE` | swept tunnel carve（EXPERIMENTAL、V2-10-07、V2-10-09でplan-level offline G/V/P `SUPPORTED`） |
| `OVERHANG` | solid add＋recess carve（plan-level offline G/V/P/Xは`SUPPORTED`、Paperは`UNSUPPORTED`） |
| `NATURAL_ARCH` | solid add＋through carve（plan-level offline G/V/P/Xは`SUPPORTED`、public Intent/Paperは`UNSUPPORTED`） |
| `SINKHOLE` | surface collapse＋cave connection（V2-10-03 EXPERIMENTAL plan-level slice。host `CAVE_NETWORK` checksum bind、loss volume static balance。V2-10-09でplan-level offline G/V/P `SUPPORTED`） |
| `UNDERGROUND_LAKE` | basin carve＋fluid add（plan-level offline G/V/P/Xは`SUPPORTED`、public Intent/Paperは`UNSUPPORTED`） |
| `UNDERGROUND_RIVER` | cave内bounded fluid reach（V2-9-11）。frozen cave＋lake checksum bind、carve→単一`ADD_FLUID`。V2-9-14でplan-level offline G/V/P `SUPPORTED`（intent/standalone/export `PARTIAL`） |
| `FLOODED_CAVE` | fluid-region hook（V2-9-11）。plan-level `FloodedCaveFluidRegionHook`＋optional Intent kind。V2-9-14でhookとしてplan-level `PARTIAL` |
| `SKY_ISLAND_GROUP` | independent solid add＋underside carve（plan-level offline G/V/P/Xは`SUPPORTED`、Paperは`UNSUPPORTED`） |
| `WATERFALL_VOLUME` | fall checksum-bound column／behind carve／plunge fluid（`VOLUME_OVERLAY`、offline G/V/P/Xは`SUPPORTED`） |
| post-volume local environment | surface／wetness／drip／shade／sparse moss-root（`COMPONENT`、offline G/V/P/Xは`SUPPORTED`） |

詳細なoperatorは [局所3D地形](volumetric-terrain.md) で定義する。

ここでG/V/P/Xはoffline generate／validation／preview／exportを表す。V2-5-18はplan-level経路を昇格したが、`CAVE_NETWORK`、`LUSH_CAVE`、`OVERHANG`、`SKY_ISLAND_GROUP`のpublic Intent moduleはdiagnostic-onlyのままである。`VERTICAL_SHAFT`、`CAVE_CHAMBER`、`SUMP`はcave graph内部componentとしFeatureKindへ追加しない。

### 3.8 Special environment / theme

すべてを新しい地形generatorにしない。次は既存landformへenvironment／material／volumeを組み合わせるpresetとして扱うのが基本である。

| Theme | 合成例 |
|---|---|
| Cherry grove | hill/mountain＋temperate climate＋cherry assemblage |
| Glacier / ice fjord | glacial mountain/fjord＋cold climate＋ice/snow profile |
| Geothermal terrace | spring＋terraced deposition＋thermal material/ecology |
| Bamboo/rainforest basin | valley/wetland＋warm humid ecology |
| Crystal/luminous cave | cave network＋special material/ecology profile |
| Mushroom-like biome | plain/forest/cave＋fungal assemblage preset |
| Dead volcanic wasteland | volcano/lava field＋arid climate＋early succession |
| Shattered cliffs / floating reef | cliff/sky island/coral featureの明示的複合 |

独自の形状不変条件が必要になった時だけ新kindへ昇格させる。色や植生themeだけでgenerator kindを増やさない。

`FOREST`、`CHERRY_GROVE`、`MUSHROOM_BIOME`、`GRASSLAND`、`SWAMP`、`SAVANNA`、`LUMINOUS_CAVE`、`CRYSTAL_CAVE`、`SNOW_MOUNTAINS`は`ENVIRONMENT_PROFILE`である。`CORAL_COAST`、`MANGROVE_COAST`、`VOLCANIC_COAST`、`ICE_FJORD`、`DUNE_BACKED_BEACH`、`OASIS`、`CRATER_LAKE`、`GLACIAL_LAKE`、`WATERFALL_CHAIN`、`SNOWY_MOUNTAIN_LAKE`、`FLOATING_REEF`は`COMPOSITE_PRESET`であり専用generatorを作らない。

### 3.9 Lake / Glacial / Karstの派生規則

- `POND`、`CRATER_LAKE`、`GLACIAL_LAKE`、`KETTLE_LAKE`、`EPHEMERAL_LAKE`は一般`LAKE`のprofile、provenance、親子relationで表す。`DAM_RESERVOIR`だけはbarrier/spillwayの独立ownershipを持てる場合にkind候補とする。
- `VALLEY_GLACIER`、`ICE_CAP`、`ICE_SHEET`はV2-10-01でstandalone EXPERIMENTAL foundation slice（共通`GlacialIcePlanV2`、V2-10-09でplan-level offline G/V/P `SUPPORTED`）。`MORAINE_FIELD`と`OUTWASH_PLAIN`はV2-10-02でstandalone EXPERIMENTAL deposition slice（glacial parent geometry bind、V2-10-09でplan-level offline generate／validation `SUPPORTED`・previewはpreview index未整備のため`EXPERIMENTAL`のまま、`PermafrostPlainProfileV2`は`PLAIN`＋cold climate profileで`PERMAFROST_PLAIN` FeatureKindは導入しない）。`ICE_FJORD`は`FJORD + VALLEY_GLACIER + cold/snow/ice profile`の`COMPOSITE_PRESET`（`IceFjordPlanV2`、専用generatorなし）。
- karstは地形名の列挙ではなく、`SINKHOLE → UNDERGROUND_RIVER → CAVE_NETWORK（graph role: KARST_CAVE_SYSTEM） → KARST_SPRING`のtyped underground drainage graphを中心にする（V2-10-03）。`SINKHOLE`／`KARST_SPRING`はEXPERIMENTAL FeatureKindでplan-level offline slice（V2-10-09でplan-level offline G/V/P `SUPPORTED`）。dedicated moduleはcatalog未登録。`CENOTE`は`SINKHOLE + CAVE_NETWORK + FLOODED_CAVE fluid hook`の`COMPOSITE_PRESET`（`CenotePlanV2`、FeatureKindなし）。`DOLINE_FIELD`、`POLJE`、`LIMESTONE_PAVEMENT`、`KARST_PEAK_FIELD`は初期karst wave後の候補である。

## 4. 複合featureとcomposition

複合地形を1個の巨大generatorへ閉じ込めず、親featureが必要な子planをcompileする。

```text
VOLCANIC_ARCHIPELAGO
├── island mass plans
├── volcanic cone / caldera plans
├── radial drainage plans
├── lava / ash geology profiles
└── coast transition plans
```

```text
MANGROVE_WETLAND
├── low-relief wetland surface      ← V2-4-15 SUPPORTED
├── tidal channel network           ← V2-3-08 SUPPORTED
├── salinity / groundwater fields   ← V2-4-15 SUPPORTED
├── mud/silt substrate              ← V2-4-09 descriptor + V2-4-07/08 material path
└── mangrove habitat / root placement ← V2-4-15（`SUPPORTED` sparse offline descriptors）
```

子planはstable derived IDを持ち、validatorとpreviewから追跡できる。親featureを複数moduleが無秩序に変更するのではなく、compile済みstage graphとfield mergeで合成する。

## 5. Feature間interaction

compile時にinteraction policyを明示する。

| Interaction | 例 | 方針 |
|---|---|---|
| Required | river → delta | relationがなければcompile error |
| Cooperative | canyon + river | shared centerline、hydrologyが床高を所有 |
| Overlay | snow on mountain | environment fieldで後段適用 |
| Exclusive | lake vs independent solid island | hard overlapならerror |
| Ordered | cliff add then sea-cave carve | `ORDERED_CSG` をBlueprintに保存 |
| Transition | beach ↔ rocky cape | ownership境界＋blend band |

同じcellのelevationを2つのregional featureが所有する場合は、priorityだけで黙って決めない。専用interaction compiler、明示merge、または入力修正のいずれかを要求する。

## 6. Generator / Validator / Previewの拡張単位

新しいkindをsupportedへ上げるには、最低限次が同じchange setに必要である。

1. kind descriptorとparameter Schema
2. Intent→FeaturePlan compiler
3. declared field I/Oを持つgenerator
4. baseline invariantに加えたfeature validator
5. feature geometry、actual field、residualのpreview layer
6. deterministic fixture、negative fixture、budget test
7. presetとユーザー向けdocs

generatorだけ実装し、validatorやpreviewがない状態は `EXPERIMENTAL` とする。

## 7. Preset

presetはfeatureの別名ではなく、複数feature、environment、soft constraint、default parameterをまとめるdataである。

```text
rocky-coast
azure-coast
fjord-basin
volcanic-archipelago
mangrove-delta
snowy-mountain-lake
lush-karst-caves
canyon-waterfall
floating-islands
coral-atoll
```

初期はrepository内の署名／checksum対象JSON/YAMLをbuilt-in catalogから読む。presetから任意class、script、外部URL、block commandを参照させない。展開後のcanonical TerrainIntent v2をReleaseへ保存し、preset更新が過去Releaseを変えないようにする。

## 8. 実装優先順

完了済みV2-2〜V2-5を再openせず、V2-6 placementを最優先で閉じる。その後の順序は次のとおりである。

1. Wave 0: `V2-6-18/19`で能力別support catalog、role、evidence検査、Paper昇格条件を閉じる。
2. Wave 1: `PLAIN`、`HILL_RANGE`、`MOUNTAIN_RANGE`、`VALLEY`、`RIVER`、`FLOODPLAIN`、`MARSH`のsurface foundation。
3. Wave 2: `ROCKY_COAST`、`SEA_CLIFF`、`SINGLE_ISLAND`、`ARCHIPELAGO`、`VOLCANIC_CONE`のcoast/island foundation。
4. Wave 3: `OCEAN_BASIN`→`CONTINENTAL_SHELF`→`CONTINENTAL_SLOPE`→`SUBMARINE_CANYON`のmarine bathymetry。
5. Wave 4: `CAVE_ENTRANCE`（V2-9-10 EXPERIMENTAL connector完了）、`UNDERGROUND_RIVER`／`FLOODED_CAVE`（V2-9-11 EXPERIMENTAL flooded-volume connection完了）。
6. Wave 5: Macro land-water topologyと一般river graph role／`WATERFALL_CHAIN` preset（V2-9-12／V2-9-13完了、V2-9-14でplan-level offline G/V/P `SUPPORTED`・catalog未登録）。
7. Wave 6: glacial、karst、advanced marine/river、escarpment/dry land、lava tube、advanced island/reef。基礎gateを妨げない後続Phaseとする。

網羅性はkind数ではなく、「契約・生成・検証・診断・性能上限が揃ったsupported kind数」で測る。

## 9. Taskとsupport昇格の対応

実装順の詳細は [Task Index](task-index.md) を正本とする。新しいvertical sliceはintent/schema→compile→deterministic generation→validation→preview→export→tests→docsを閉じるが、個別TaskだけでPaper能力を昇格させない。V2-6の共通placement contractを利用し、Feature別Paper adapterを作らない。

| Feature group | 主な実装Task | lifecycleを確定するTask |
|---|---|---|
| Beach／harbor／cape | `V2-2-03`〜`V2-2-07` | `V2-2-12` |
| River／lake／canyon／waterfall／delta／tidal／fjord | `V2-3-03`〜`V2-3-09` | `V2-3-15` |
| Mountain／volcanic skeleton | `V2-3-10`〜`V2-3-11` | `V2-3-15`。material完成は`V2-4-15`（完了） |
| Mangrove／coral／snow／environmental completion | `V2-4-06`、`V2-4-09`〜`V2-4-12` | `V2-4-15`（完了） |
| Cave／lush／underground lake／sea cave／overhang／arch／sky island | `V2-5-06`〜`V2-5-14` | `V2-5-18`（完了） |
| Placementと能力別support catalog | `V2-6-01`〜`V2-6-18` | `V2-6-19`（完了。catalog昇格は`V2-11-01`） |
| Surface/coast/island/marine/connection/macro foundation | `V2-9-01`〜`V2-9-13` | `V2-9-14`（完了。plan-level offline G/V/P `SUPPORTED`、intent/standalone/export `PARTIAL`、Paper以降`UNSUPPORTED`） |
| Glacial/karst/advanced landforms | `V2-10-01`〜`V2-10-08` | `V2-10-09` |

複数Phaseにまたがるscenarioは、骨格Taskが終わっても全能力のcompletionではない。waterfallはV2-3のgraph／2.5D、`V2-5-13`のvolume overlay、`V2-5-14`のlocal environment、`V2-5-15`のvalidators、`V2-5-17`の`sparse-volume` capabilityが揃い、`V2-5-18`でoffline G/V/P/Xだけが`SUPPORTED`になった。Paper apply以降はV2-6 gateまで`UNSUPPORTED`である。親Phaseや別featureの成功を根拠に、未完の能力やkindを一括して`SUPPORTED`へ上げない。
