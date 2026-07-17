# Implementation Roadmap v2

> Status: v2の詳細設計とgate。進捗状態の正本は [docs/roadmap.md](../roadmap.md) である。V2-0／V2-1／V2-2／V2-3のPhase gateと`V2-4-01`〜`V2-4-05`が完了し、次は`V2-4-06`、V2-5〜V2-6は前提待ちである。

## 1. Roadmapの読み方

このroadmapはV2-0〜V2-6の依存順と親Phase gateを示す。日付や完了を約束せず、各Phaseは前Phaseのgateを満たすまでsupportedとしない。実行単位は [Task Index](task-index.md)、運用規則は [Task Execution Guide](task-execution-guide.md) を正本とする。現行Beta hardeningのFAWE smoke、500×500実world計測、release checklist未完了を消さない。V2-2 gateは [監査](audits/v2-2-phase-gate.md)、V2-3 gateは [Hydrology監査](audits/v2-3-phase-gate.md) により完了した。`V2-4-01`〜`V2-4-03`のgeology／lithology／strataと、`V2-4-04`のclimate prior／final field／Hydrology handoff、`V2-4-05`のwetness／salinity／hydroperiodはAcceptanceを通過し、現在の次Taskは`V2-4-06`である。

```text
V2-0 Compatibility spine
  → V2-1 Constraint maps / compiled fields
  → V2-2 Coastal 2.5D vertical slice
  → V2-3 Hydrology / regional landforms
  → V2-4 Geology / climate / ecology
  → V2-5 Sparse local volume
  → V2-6 Release 2 placement / hardening / catalog expansion
```

## 2. 共通Definition of Done

各Phaseは次を満たす。

- production code、Schema、docs、example、roadmapの実態が一致する。
- compile、対象test、`./gradlew build` が成功する。
- pure Java logicにunit／negative／determinism testがある。
- whole/tile、seam、seed、cancel、budgetを対象範囲で検証する。
- secret、raw Provider payload、生成temporary artifactがGit差分へ混入しない。
- model/generatorのpackage境界とPaper thread規則を破らない。
- unsupported featureをREADMEで実装済みと表現しない。
- 新Release／配置変更はstrict verifier、rollback、脅威分析を伴う。

## 3. V2-0: Compatibility spine and diagnostic compiler

### 目的

最初に実装すべき最小Phaseである。地形結果を変えず、v1を壊さず、v2の契約とmodule/field境界をend-to-endで通す。

### Scope

- v1/v2のversion dispatch骨格
- TerrainIntent v2の最小contract: ID、POINT/MULTI_POINT/SPLINE/MULTI_SPLINE/POLYGON/VOLUME_GUIDE、stable point/path/endpoint、relation、hard/soft constraint
- WorldBlueprint v2のidentity、space、module、field descriptor、named seed、budget
- compile-time built-in module catalog
- ValidationTarget、MetricResult、diagnostic issueの最小型
- `TerrainQuery`／`TerrainBlockResolver` interfaceとv1 column adapter
- canonical JSON、checksum、unknown version拒否
- 1個のdiagnostic-only coastal fixture

### 明示的な非Scope

- 新しい海岸形状、hydrology、3D、素材、Release 2、Paper配置
- dynamic module/plugin、preset import
- v1 artifactの書換え

### Acceptance gate

- v1全golden checksumとblock streamが変更前と一致する。
- diagnostic coastal fixtureがparse→normalize→Blueprint diagnostic compile→round-tripできる。
- 残り10 scenario例はcontract parse/round-tripでき、未実装feature kindをfallbackせずcapability diagnosticとして返す。
- duplicate ID、hard conflict、未知module、field owner cycle、budget超過を拒否する。
- named seedがmodule登録順、thread、locale、timezoneで不変である。
- v1 adapter経由のresolver出力が現行column materializerと全block一致する。
- v2 outputはdiagnostic temporary artifactに限り、Release 1へ混入しない。

このPhaseが小さい理由は、新地形を作る前にversion、determinism、field ownership、3D query adapterという後戻りしにくい境界だけを検証するためである。

### 実装状態

V2-0 gateは完了した。exact v1/v2 dispatch、TerrainIntent v2／WorldBlueprint v2、strict Schema、millionths canonical JSON、compile-time catalog、named seed、diagnostic compilerに加え、pure `TerrainQuery`／`TerrainBlockResolver`とv1 adapterを実装した。adapterはschema 1／generator `3.0.0-phase6`だけを受け、現行materializerとの全XYZ block一致、whole／tile／tile順／thread数／locale／timezone不変を固定する。Release 1、placement、Undoへv2型は接続していない。

## 4. V2-1: Direct constraint maps and field store

### Scope

- reference imageとconstraint mapのRequest契約分離
- `LAND_WATER_MASK`、`HEIGHT_GUIDE`、`ZONE_LABEL_MAP` の3 role
- categorical U8／U16とheight U8／U16のnumeric decoder、coordinate mapping、fixed-point canonicalization
- compact field sidecar formatとbounded window reader
- desired/actual/residual field
- version固定の8枚map diagnostic PNG bundle
- AIを介さないmanual generation path

### Acceptance gate

- 8/16-bit、label、no-data、rotation/flip/cropのgolden fixture。
- hard land-waterはblock量子化後もtolerance内、soft guideはresidualを報告する。
- malformed image、unknown label、過大decode、path/link/TOCTOUを拒否する。
- 1000角のstage peak memoryが事前budget内で、cancel時にcanonical artifactを公開しない。
- whole/tile samplingが一致する。
- 全artifactとsealed indexのstrict read-back後、最後のcancel checkとbundle atomic moveの間に処理を挟まない。atomic move後はcommit済みとし、late cancelで削除しない。

### Deliverable scenario

mask＋height guideから作る単純なcoast／island preview。まだspecialized coast generatorをsupportedとしない。

### 実装状態

V2-1 gateは完了した。`GenerationRequestV2`、3 roleのstrict binding、grayscale non-interlaced PNGのcategorical／height U8／U16 numeric decoder、clockwise quarter-turn→flip X/Z→crop→pixel-center mapping、no-data、label table、3種類を明示するheight意味、integer fixed-point samplingを実装した。canonical fieldはADR 0011の非圧縮`LFC_GRID_V1`へstream書込みし、artifact／semantic checksumとsource provenanceを持つsealed indexからbounded windowで読む。manual pathはAIを呼ばずdesired／actual／residual fieldと固定8 diagnostic PNGを一つのstaging directoryからatomic publishする。hard land-waterはactualへ完全copyし、soft heightはwhole-block量子化差をresidualに残す。

security／corruption testはmalformed PNG、format／sample mismatch、unknown label、invalid no-data、dimension／aspect mismatch、absolute／traversal、symlink、request内hardlink alias、TOCTOU、byte／pixel／decode／resident／artifact budget、future version、source／artifact／index checksum改変、hard conflict、cancel cleanupを拒否する。trusted ceilingはmap 32、source 1枚8 MiB／合計32 MiB、1枚4,000,000 pixel／合計16,000,000 pixel、decoded 1枚8 MiB／合計32 MiB、decoder working 32 MiB、artifact 64 MiB、resident 96 MiBで、Requestから拡張できない。1000×1000 manual fixture、whole／tile、seam、tile／thread順、locale／timezoneもAcceptance内で検証した。bundle atomic moveをcommit pointとし、move前のcancel／failureだけをcleanupする。V2-2の専用coast generator、Release 2、Paper配置は未実装である。

## 5. V2-2〜V2-6のTask構造

詳細なScope、非Scope、変更面、test、D/M/S条件、Acceptance、停止条件は [Task Index](task-index.md) を正本とする。実行規則と短い共通プロンプトは [Task Execution Guide](task-execution-guide.md) を参照する。Taskを1個完了しても親Phaseは完了しない。

| Phase | Task数 | 主な直列chain | Phase gate task |
|---|---:|---|---|
| V2-2 | 12 | coastal基盤→4 feature→transition→validator/preview→offline schematic→Release 2 core/capability | `V2-2-12` |
| V2-3 | 15 | Hydrology IR/solve→river/lake/canyon/waterfall/delta/tidal/fjord/mountain/volcano→reconcile→diagnostic/capability | `V2-3-15` |
| V2-4 | 15 | geology→lithology/strata→climate/water/snow→material/palette→mangrove/coral/ecology→diagnostic/capability | `V2-4-15` |
| V2-5 | 18 | SDF→CSG→AABB/cache/query→volume feature単位→post-environment→diagnostic/export/capability | `V2-5-18` |
| V2-6 | 19 | placement contract→envelope/reservation/snapshot/containment→apply/verify/rollback/Undo/Recovery→hardening→smoke/measurement/catalog | `V2-6-19` |

## 6. V2-2: Coastal 2.5D vertical slice

`V2-2-01`から`V2-2-12`まで完了した。`V2-2-01`〜`V2-2-07`はcoastal field、4 feature、transition compositor、`V2-2-08`は独立validator／strict preview、`V2-2-09`はstreaming offline tile schematicとWorldEdit read-back、`V2-2-10`はempty-capability Release format 2 core、`V2-2-11`はrequest／intent／Blueprint、field、validation、preview、tileのexact `surface-2_5d` artifact setを完成した。`V2-2-12`はAzure Coast統合fixtureと全portfolioを再検証し、親Phase gateを閉じた。

Phase gateはAzure Coast統合fixtureでbeach width、harbor depth/opening、cape rock/complexity、whole/tile block stream、Sponge palette 127/128、WorldEdit offline read-back、Release 2 strict directory/ZIP、Release 1回帰を再検証した`V2-2-12`が閉じた。4 coastal kindとoffline `surface-2_5d`は`SUPPORTED`である。Paper applyは有効化していない。

## 7. V2-3: Global hydrology and regional landforms

`V2-3-01`〜`V2-3-15`は完了した。Hydrology IR／fixed priorとglobal basin/routing solverに加え、RIVER／MEANDERING_RIVER、独立LAKE、CANYON＋shared river skeleton、WATERFALL 2.5D lip／base／plunge、DELTA distributary DAG／fan／sandbar／shallow sea、TIDAL_CHANNEL_NETWORK bidirectional marine graph、FJORD glacial U／sidewall／marine outlet、ALPINE／GLACIAL mountain ridge skeleton、VOLCANIC_ARCHIPELAGO island／caldera／radial skeleton、固定3 passのbounded reconciliation、独立validator／preview、Release 2 `hydrology-plan`（`surface-2_5d`依存）を持つ。統合監査により、offlineでfull completionしたriver／lake／canyon／delta／tidal／fjordと`hydrology-plan`を`SUPPORTED`とした。

Phase gateはsource-mouth reachability、bed monotonicity、confluence flow、lake spill、delta branch、fjord marine/U profile、waterfall graph、graph/field budget、tile/thread/candidate決定性、`hydrology-plan` strict capabilityを統合した`V2-3-15`が閉じた。waterfall volume、mountain environment、volcanic materialを要するkindは`EXPERIMENTAL`のままである。V2-4 priorへの切替はgenerator/capability version変更として扱う。

## 8. V2-4: Geology, climate, ecology, semantic material

`V2-4-01`から`V2-4-15`までを順に実行する。`V2-4-01`〜`V2-4-03`はtyped geology plan、fixed lithology catalog、ordered strata profile／derived scalarを、`V2-4-04`はcoarse climate prior、final temperature／moisture、Hydrology runoff-prior version transitionを、`V2-4-05`はregional wetness／salinity／hydroperiodを`EXPERIMENTAL`で完了した。`V2-4-06`以降でsnow、semantic material、Minecraft palette、mangrove shaping、coral bathymetry、ecology、volcanic/canyon materialを分離する。

`MANGROVE_WETLAND`と`CORAL_REEF`のregional shapingはStage 5、固定回数reconciliationはStage 6、environment/ecology/materialはStage 8/10の責務を保つ。Phase gateはsnow/mangrove/coral/volcanic/canyon metric、palette read-back、descriptor/memory budget、`environment-fields` strict capabilityを統合した`V2-4-15`だけが閉じる。

## 9. V2-5: Sparse local volumetric terrain

`V2-5-01`から`V2-5-18`までを順に実行する。SDF、ordered CSG、AABB index、3D tile cache、TerrainQuery volume対応を別Taskで固定してから、cave、lush cave、underground lake、sea cave、overhang、natural arch、sky island、waterfall volumeを1 featureずつ追加する。

Phase gateはconnectivity、roof/support/clearance、fluid continuity、post-volume environment、3D whole/tile/XYZ seam、dense allocation禁止、general VarInt/palette、offline read-back、`sparse-volume` strict capabilityを統合した`V2-5-18`だけが閉じる。Paper applyはこのgate完了後まで開始しない。

## 10. V2-6: Placement, hardening, and supported catalog

`V2-6-01`から`V2-6-19`までを順に実行する。placement contract、effect envelope、region/disk reservation、snapshot-all、fluid/gravity containment、apply orchestration、settle/full verify、rollback、Undo、Recoveryはそれぞれ別Taskである。Provider/manual/image統合、Release hardening、operations、WorldEdit/FAWE smoke、500/1000実world計測、catalog、RC auditも分離する。

WorldEdit/FAWE smokeと実world計測は指定環境がなければ`BLOCKED_EXTERNAL`であり、mockやoffline testで完了扱いにしない。500角／1000角は各計測Taskが成功した寸法だけをsupported catalogへ載せる。Phase gateは全必須Task、v1回帰、security/performance evidence、supported catalogを監査する`V2-6-19`だけが閉じる。Beta hardeningの未完項目は別trackとして残す。

### V2-3〜V2-5 capability共通gate

`hydrology-plan`、`environment-fields`、`sparse-volume` はそれぞれ専用Taskでのみ有効化する。直前PhaseのRelease 2を引き続きstrict verifyし、capability artifactの欠損／追加／version改ざん、未知capabilityをdirectory/ZIP双方で拒否する。予約名だけをmanifestへ出さない。

## 11. Scenario coverage matrix

| Required scenario | 最初に表現 | Regional／semantic completion | Full completion |
|---|---|---|---|
| beach＋breakwater＋rockycape | V2-0 Intent例 | V2-2 | V2-2 |
| fjord | V2-0 Intent例 | V2-3 | V2-3 |
| delta | V2-0 Intent例 | V2-3 | V2-3 |
| volcanic archipelago | V2-0 Intent例 | V2-3骨格、V2-4素材 | V2-4 |
| canyon＋waterfall | V2-0 Intent例 | V2-3 graph／2.5D骨格 | V2-5 water column／滝裏volume |
| mangrove wetland | V2-0 Intent例 | V2-3 tidal graph、V2-4地形／環境 | V2-4 |
| snowy mountains | V2-0 Intent例 | V2-4 | V2-4 |
| coral reef | V2-0 Intent例 | V2-4 bathymetry／環境 | V2-4 |
| cave／lush cave | V2-0 Intent例 | V2-5 | V2-5 |
| overhang | V2-0 Intent例 | V2-5 | V2-5 |
| sky islands | V2-0 Intent例 | V2-5 | V2-5 |
| 通常画像から地形 | V2-1 draft/soft map | V2-2以降のsupported kindに限定 | 対象kindのcompletion Phase |

「Intentで表現可能」と「generatorがsupported」は別の状態として表示する。

## 12. Test portfolio

### Contract

- v1/v2 Schema、canonical JSON、unknown version、migration warning
- geometry、relation、constraint、module descriptor、field artifact

### Pure unit / property / metamorphic

- fixed-point、geometry raster、distance、SDF、graph、merge operator
- rounding、overflow、stable reduction、deterministic math kernelのgolden fixture
- seed、tile order、thread、locale、timezone、default charset independence
- inputを平行移動／反転したときの期待変換
- map解像度だけを上げたときのtolerance内一致

### Feature fixture

- 11 required scenarioのpositive fixture
- 各validatorを壊すnegative／corruption fixture
- feature interactionとtransition fixture

### Security / format

- image/map path、link、TOCTOU、bomb、unknown label
- field／Release／ZIP path、checksum、extra/missing、allocation上限
- Sponge palette/VarInt、WorldEdit read-back

### Runtime / placement

- bounded executor saturation、cancel、shutdown、resume
- `retained + parallelism × per-task peak + cache/decode/queue overhead` に基づくadmissionと同時stage上限
- snapshot/apply/verify failure、disk不足、overlap、Undo、Recovery
- Paper Scheduler main-thread assertion
- WorldEdit／FAWE smoke、500/1000実測

## 13. Parallel workstreamとmerge gate

同じTaskのScope内で安全に分担できるworkstream:

- Contract/Schema/compiler
- field storage/image decoder
- module generator/validator fixture
- Release/preview format
- Paper placement gateway

ただし、1回の作業はTask indexの1 Taskだけを完了し、後続Taskを並行実装しない。次のmerge gateは直列にする。

1. contractとcanonicalization確定前にgenerator APIを固定しない。
2. field ownershipとseed規則確定前に複数moduleを追加しない。
3. Release 2 self-verify前にPaper applyを実装しない。
4. offline 3D exportとcorruption validation前にvolume placementを有効化しない。
5. snapshot-allと実機計測前にRelease 2をproduction-readyとしない。

## 14. V2-0／V2-1の実装結果

V2-0全体より小さい最初のchange setとして、次を実装した。

```text
TerrainIntentV2の最小record/Schema
+ FeatureId / POINT・MULTI_POINT・SPLINE・MULTI_SPLINE・POLYGON・VOLUME_GUIDE / stable point・path・endpoint
+ HARD・SOFT metric constraint
+ ModuleDescriptor / FieldKey
+ BlueprintCompilerV2のdiagnostic plan
+ v1 checksum不変test
```

V2-0ではAzure Coastの5 featureをmodule／field diagnosticまでcompileし、残り10 scenarioもfallbackせずround-tripする。v1出力はquery adapterを含むgolden testで固定した。V2-1ではprecision mapをAI referenceから分離し、numeric decodeからfield sidecar、constraint reconciliation、diagnostic previewまでを縦に完成した。`V2-2-01`〜`V2-2-12`でcoastal foundationからoffline Release／Phase gateまでを完了した。`V2-3-01`〜`V2-3-14`でstrict Hydrology graph IR、global routing、regional feature skeleton、固定3 pass reconciliation、独立validator／preview、Release 2 `hydrology-plan`を追加した。`V2-3-15`で9 scenario、決定性／resource／cancel、tampering、v1／V2-2回帰を統合監査し、完成featureとcapabilityをofflineで`SUPPORTED`とした。`V2-4-01`でtyped geology field core、`V2-4-02`でversion/checksum固定lithology catalog、`V2-4-03`でstrata／derived scalar、`V2-4-04`でcoarse climate prior／final field／Hydrology runoff-prior handoffを追加したが、V2-4 Phase gateは未完了である。次は`V2-4-05`である。
