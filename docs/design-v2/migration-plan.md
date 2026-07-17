# v1からv2への移行計画

> Status: V2-0〜V2-3の互換・offline Phase gateと`V2-4-01`〜`V2-4-04`を完了した。typed geology／lithology／strataに加え、coarse climate priorとfinal temperature／moistureを別plan／stageとして追加し、V2-3 fixed runoff priorからの境界はchecksum付き明示version transitionにした。environmentとwaterfall／mountain／volcanicは後続依存のため`EXPERIMENTAL`である。v1 Schema、generator、Release、CLI／Paper commandは変更しておらず、v2 Paper配置はまだない。次は`V2-4-05`で、V2-5〜V2-6は前提待ちである。

## 1. 移行原則

1. v1をその場で別の意味へ変えない。
2. v1とv2をversion dispatchで並設する。
3. 既存Releaseは元のgenerator／formatで検証、配置、Undoできるようにする。
4. v2 upgradeは新artifactを作り、元Design／Releaseを上書きしない。
5. 安全性、決定性、tile、atomic publish、confirm／snapshot／rollbackを再利用する。
6. 新しい地形表現をPaper main threadへ持ち込まない。
7. dynamic pluginやfull 3Dを先に導入せず、小さなcoastal vertical sliceで境界を検証する。

## 2. Version戦略

v1ではrequest、Intent、BlueprintのSchema versionを揃える前提が強い。v2は契約を分離する。

| Version | 責務 | 移行方針 |
|---|---|---|
| Request Schema | 入力とimage/map descriptor | v1凍結、v2 `$id` を新設 |
| TerrainIntent Schema | feature意味契約 | v1凍結、v2を並設 |
| Blueprint format | compile済みIR | v1 reader維持、v2新設 |
| Generator version | algorithm結果 | `3.0.0-phase6` を凍結、v2別系列 |
| Release format | package意味 | format 1 strict維持、format 2新設 |
| Minecraft profile | block mapping | 1.21.11 profileを明示version化 |
| Plugin/application | UIとruntime | 両formatのcapabilityを宣言 |

`formatVersion: 1` をv2 artifact集合へ拡張しない。future versionを推測で読まないという既存原則を維持する。

## 3. Phase 0〜6との互換性

現行M0〜M6は捨てる対象ではなく、v2の下部基盤である。

| 現行milestone | 流用する契約 | v2で追加／変更するもの |
|---|---|---|
| M0 Contract | record、enum、strict Schema、JDK-only model、package境界 | version別model／codec、geometry、field、constraint contract |
| M1 Terrain Preview | global sample、tile＋margin、determinism、bounded preview | stage別halo、module pipeline、feature validator、preview registry |
| M2 Portable Release | tile `.schem`、checksum、ZIP、strict verify、atomic publish | Release format 2、field／module／preview index、3D resolver |
| M3 Safe Placement | confirm、reservation、snapshot、verify、rollback、Undo、Recovery | snapshot-all mutation envelope、fluid/gravity containment、multi-tick verify |
| M4 Text to Intent | Provider-neutral structured Intent、manual import、audit | Intent v2 prompt/normalizer、v1/v2 Provider capability |
| M5 Image Intent | sanitized role-aware image boundary、evidence | AI referenceとdirect constraint mapの分離、canonical map artifacts |
| M6 Structures | built-in asset catalog、stable seed、安全探索 | final TerrainQuery対応。大規模人工地形はfeatureへ分離 |

Accepted ADRを履歴から削除しない。v2実装時に、新ADRで「維持する範囲」と「v2に限りsupersedeする範囲」を明記する。

## 4. Component別判断

### 4.1 そのまま流用する

- 製品名、group、package、Java 21、Paper 1.21.11、単一Gradle project
- `landformcraft.model` の外部library非依存境界
- AI Providerが全block／codeを返さない [ADR 0006](../adr/0006-provider-neutral-structured-intent.md)
- `ReferenceImageProcessor` のfilesystem／decode安全原則
- bounded CPU executor、admission付きI/O、CompletableFuture、cancel／interrupt
- global X/Z sampling、既定128 tile、端tile、checksum
- Sponge v3 streaming、strict read-back、fsync、atomic publish
- actor-bound confirm、領域／disk reservation、snapshot、rollback、Undo、Recovery
- built-in small structure asset catalogとsemantic checksum
- package boundary、image security、Release tampering、placement failure test

### 4.2 interfaceを保って内部を拡張する

| 現行領域 | 拡張方針 |
|---|---|
| Application Service | v1/v2 dispatchを追加し、UIは引き続きcoreだけを呼ぶ |
| Job/checkpoint | v2 stage IDとglobal/tile artifact checksumを追加 |
| Preview write | 1枚ずつ生成・解放を維持しregistry入力へする |
| Sponge writer | streamingを維持し、入力を3D resolver、VarIntを一般化 |
| Structure planner | `heightMap` ではなくfinal `TerrainQuery` を読む |
| Performance validator | stage別field/volume/palette/preview peakを見積もる |
| Placement gateway | Scheduler／WorldEdit公開API境界を維持しenvelopeを追加 |

### 4.3 v2で置換する

| 現行component／概念 | v2の置換先 |
|---|---|
| `TerrainIntent` v1の集約値 | feature／geometry／relation／constraintのIntent v2 |
| wrapper型 `WorldBlueprint` v1 | field、module、graph、budgetを持つcompile済みBlueprint v2 |
| `LogicalTerrainLayout` 2 field | typed field registryとartifact |
| monolithic `TerrainGenerator` | stage graph＋built-in LandformModule群 |
| river band／circular lake | global HydrologyPlan |
| `TerrainPlan` の単一surface列 | base fields＋sparse volume＋TerrainQuery |
| 6値 `SurfaceMaterial` | semantic geology/material profile＋block adapter |
| `VEGETATION` bit | habitat field＋sparse ecology placement |
| 固定feature validator | baseline＋feature/cross-feature rule catalog |
| 固定8 preview orchestration | format 2 preview registry/index |
| AI画像要約だけ | reference image＋direct constraint mapの二経路 |

### 4.4 段階的に廃止する

- v1をv2 moduleへ暗黙変換して同じ結果だと称する経路
- `preferredArea`／`areaShare` を正確な形状／面積として扱うUI説明
- 保存されるが生成に効かないv1 `theme`、bay/cape/shelf値への依存
- すべての水を1個のglobal water levelで表すv2経路
- v2でcolumn-only materializerを直接呼ぶ経路
- fixed preview file名をv2 moduleが直接appendする経路
- tileごとにsnapshot直後applyするv2配置経路

廃止はv2 path内で行い、v1 reader／generator／Release 1／placement recoveryを削除しない。削除は公開support期間とmigration実績を別判断してから行う。

## 5. Runtime dispatch

```text
input artifact
→ exact version detection
├── v1 request/intent/blueprint
│   → V1BlueprintCompiler
│   → V1TerrainGenerator
│   → Release format 1 publisher/verifier
└── v2 request/intent
    → V2BlueprintCompiler
    → GenerationPipelineV2
    → Release format 2 publisher/verifier
```

classを一度にrename／置換するより、既存classをv1 facadeとして固定し、新しい型を明示的に追加する。common interfaceは意味が本当に共通なものだけにする。v1の欠落値へnullを足して巨大union modelにしない。

## 6. v1→v2 Intent upgrade

upgrade toolはlossless migrationではない。v1に位置／形／接続情報が存在しないためである。

### 自動変換できるもの

- bounds、seed、tile size、water levelの初期default
- topologyからworld-level feature候補
- seaSidesからsoft boundary classification
- zone kind／preferredAreaからsoft region候補
- relief、land ratio、water数からsoft metric range
- small structure intentからv2 structure request

### 自動変換できないもの

- 正確なcoastline、beach幅、fjord centerline
- river source、流域、合流、delta分流、滝位置
- crater、canyon断面、island size distribution
- cave graph、overhang support、sky island Y
- geology、climate、ecologyの具体的field

upgrade出力はすべて新IDを持ち、推測値を `SOFT` とprovenanceへ記録する。hardに昇格する前にユーザー確認を要求する。upgrade warningが0であるかのように表示しない。

## 7. Release format 2

概念構成:

```text
release-v2/
├── manifest.json  # artifacts[] strict index + requiredCapabilities[]
├── source/request.json
├── source/terrain-intent.json
├── blueprint/world-blueprint.json
├── blueprint/module-manifest.json
├── blueprint/stage-graph.json
├── blueprint/geometries.json
├── blueprint/features.json
├── blueprint/validation-targets.json
├── blueprint/fields/*
├── constraints/*
├── plans/hydrology.json
├── plans/geology.json
├── plans/climate.json
├── plans/ecology.json
├── plans/volumes.json
├── validation/*
├── previews/index.json
├── previews/*.png
├── tiles/*.schem
├── structures.json
└── checksums.sha256
```

`V2-2-10`は`release-manifest-v2.schema.json`、canonical exclude-self checksum、strict `artifacts[]`／`requiredCapabilities[]`、directory／ZIP publisher/verifierを実装した。core-only releaseは`manifest.json`だけを含み、配列は空でなければならない。`V2-2-11`は`surface-2_5d`を別dispatchで有効化し、request／intent／Blueprint、field index＋sidecar、validation、preview index＋PNG、tile metadata＋Sponge v3をexact setとして固定した。`V2-3-14`は`hydrology-plan`を`surface-2_5d`依存で追加し、plan／routing／reconciliation／validation／12 previewをexact setとして固定した。未知capability、未知type/version、index外file、symlink、ZIP traversal／case collision、checksum、semantic binding、entry／展開／resident／disk budget違反を拒否する。format 1 verifierのallowlistを共有可変Listにして緩めず、version別verifierを使う。

## 8. Placement migration

### Release 1

現在のtile checkpoint transactionを維持する。既存placement journalとsnapshotをv2 codeで書き換えない。

### Release 2

```text
validate all artifacts
→ compute mutation/effect envelopes
→ reserve all regions and disk
→ issue and validate confirmation bound to release, target, envelope, and reservation
→ snapshot all envelopes
→ apply tiles in canonical order
→ settle bounded world updates
→ full verify in bounded scheduler slices
→ commit or reverse-order rollback
```

観測FutureのtimeoutだけでScheduler受理済みoperationを取消済みにしない。dispatch前validation／confirm、実operation ID、late completion reconciliationを既存規則どおり持つ。

snapshot scope拡大はdiskと時間へ直接影響するため、Release 2のestimateをRelease 1と分ける。offline generation/exportは1000×1000 gateを必須とする。Paperの初期配置上限を500角とするなら500×500実world apply/verify/Undoの成功が必須であり、1000角配置をsupportedと宣言するには1000×1000の同じ実測を別途必須とする。実測していない寸法は設定上限で拒否する。

## 9. CLI／Paper／Provider

- v1 commandのdefault挙動を変えず、version／capabilityを明示optionまたはartifactから選ぶ。
- `--intent-version` 等の具体的UIは実装時に決めるが、曖昧なauto-upgradeをしない。
- Providerごとにv2 structured output capabilityを宣言し、未対応modelはv1またはmanual importへ限定する。
- Intent v2生成、map compile、Blueprint、generator、preview、Release I/OをPaper main threadで実行しない。
- Bukkit／world accessだけをScheduler経由でmain threadへ送る。
- error codeへversion、stage、rule IDを含め、秘密値をredactする。

## 10. Risk register

| Risk | 影響 | 対策／gate |
|---|---|---|
| v1再現性の破壊 | 既存Releaseを再生成不能 | v1 golden checksum、class path分離、generator凍結 |
| modelが巨大union化 | 境界不明、null増加 | v1/v2型分離、typed union、version codec |
| module interaction爆発 | 結果が順序依存 | field owner、merge operator、interaction compiler |
| graph/field memory増大 | 1000角でOOM | compact型、stage解放、budget、global plan＋tile raster |
| 3D palette／stream不整合 | `.schem`破損 | general VarInt、palette boundary read-back test |
| fluidがsnapshot外へ波及 | rollback不能 | snapshot-all envelope、containment、settled verify |
| validatorとgeneratorの同一bug | false pass | corruption fixture、cross-validator、metric artifact |
| AI map誤解釈 | hardな誤地形 | photoはsoft、confidence＋確認、canonical freeze |
| format 1 verifierを緩める | tampering検出低下 | separate strict format 2 verifier |
| dynamic module実行 | supply-chain／決定性 | 初期はcompile-time built-in catalogのみ |
| 完全物理simulation化 | 時間／停止性悪化 | bounded deterministic approximation |
| Beta hardening停滞 | 現行release品質低下 | v2を別trackとし現行未完gateを消さない |

## 11. Rollback strategy

- 各v2 milestoneはfeature flag／explicit version pathで隔離する。
- v2 compiler／generator失敗時にv1へ暗黙fallbackしない。意味が違うためである。
- publisherはself-verify完了前にReleaseをREADY公開しない。
- incomplete stage artifactはtemporary scopeに置き、resume時はinput/output checksumを再検査する。
- v2 Paper applyを有効化する前はoffline preview/exportだけに限定する。
- apply gateで問題が出た場合、Release 2生成物は保持して診断できるが配置capabilityを無効化できる。

## 12. 実装時に必要な契約変更

この設計段階では変更しない。実装開始時、changeごとに少なくとも次を同時更新する。

- Proposed/Accepted ADR
- 新しいv2 Schema `$id` とexamples
- `docs/architecture.md`、`docs/data-model.md`、`docs/artifact-format.md`
- CLI／Paper command、config、user/admin/operations docs
- `docs/roadmap.md` の対象Phase進捗、完了根拠、実測gate
- Release／placement threat analysis

既存Schema、既存docs、既存exampleを先に「実装済み」に書き換えない。

## 13. Architecture alternatives

### A. v1を破壊的にv2へ置換

型は少なくなるが、generator checksum、Release、Design Package、Provider、配置journalの意味が崩れる。採用しない。

### B. v1 zoneを増やし続ける

短期実装は速いが、形、接続、3D、feature validationを表せない。v1保守以外には採用しない。

### C. 全域voxel engineへ一括移行

表現は統一されるが、memory、tile、preview、Release、配置の全基盤を同時に危険にする。採用しない。

### D. 外部runtime plugin systemを最初から公開

feature追加は柔軟だが、任意code、version、resource、determinism、supportが難しい。built-in catalogでdescriptor契約を固めた後の将来案とする。

### E. v1凍結＋並行v2＋段階vertical slice

既存安全基盤を保ち、各境界を小さく検証できる。これを推奨する。

## 14. Migrationを閉じるTask境界

詳細な実装単位は [Task Index](task-index.md)、1回の作業規則は [Task Execution Guide](task-execution-guide.md) を正本とする。migrationに関する主要gateは次のTaskへ割り当てる。

| Migration boundary | Task | 次へ進む条件 |
|---|---|---|
| Release format 2 coreをformat 1と分離 | `V2-2-10`（完了） | core directory／ZIP strict verifyとRelease 1回帰 |
| 最初のoffline surface capability | `V2-2-11`（完了） | `surface-2_5d`必須集合とtampering test |
| Hydrology／Environment／Volume capability | `V2-3-14`（完了）／`V2-4-14`／`V2-5-17` | 各直前capability setのstrict回帰 |
| Release 2 placement contract | `V2-6-01` | v1 journalと別versionでchecksum-bound |
| Paper mutation開始条件 | `V2-6-02`〜`V2-6-06` | envelope、予約／confirm、snapshot-all、containment完了 |
| Rollback／Undo／Recovery | `V2-6-08`〜`V2-6-10` | failure injectionとv1回帰 |
| Cross-capability hardening | `V2-6-12` | 全valid prefixとdirectory／ZIP corruption拒否 |
| Supported runtime／dimension | `V2-6-14`〜`V2-6-18` | 今回buildの実機evidenceがある範囲だけcatalog化 |

`V2-6-14`〜`V2-6-17`は実機依存であり、環境がなければ`BLOCKED_EXTERNAL`とする。mockや過去buildの結果でmigration gateを閉じない。個別Task完了でv1 defaultをv2へ切り替えず、`V2-6-19`のrelease candidate監査までexplicit version dispatchを維持する。V2-6が完了しても、既存Beta hardeningの未チェック項目を根拠なく完了へ変更しない。
