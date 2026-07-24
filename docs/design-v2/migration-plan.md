# v1からv2への移行計画

> Status: V2-12-06を完了し、v2を唯一のproduction writer／generator／placement／通常command経路とした。v1 production codeはADR 0035 D2a R1〜R8の範囲だけ削除し、既存v1 artifact向けのpackaged legacy read／verify／migrate、immutable golden、custom asset catalogは維持する。V2-12はPhase gate `V2-12-07`（2026-07-21）で完了し、Track Aはその後V2-18（macro foundation、13/13完了）を経てV2-19（Input integrity／block materialization）を実行中である。V2-19は全16 Taskを完了し、Phase gate `V2-19-16`は2026-07-24の人間承認で完了して親Phaseを閉じた（[V2-19-16 Phase gate audit](audits/v2-19-16-phase-gate.md)）。Track Aの残りはV2-17（`V2-15-47`／`V2-16-19`完了待ち）のみである。

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

### 実装状況（`V2-12-04`、2026-07-20）— 上記「自動変換できるもの」との差異

**未解決の正本間差異があり、実装は保守側に倒してある。** `V2-12-04`が実装した`LegacyMigrationApplicationServiceV2`が写すのは `schemaVersion` と `theme` だけで、上記「自動変換できるもの」のうち topology／seaSides／zone／relief／landRatio／water／structure は**v2 intentへ入れず、migration reportへ1件ずつ理由付きで列挙する**。

理由は、現在の`TerrainIntentV2`契約では上記listが実装不能なためである。

- `TerrainIntentV2.Feature` は `Geometry` を必須にし、geometry自体にstrengthを持たない。したがって「soft region候補」は書けず、書けば具体的なpolygon／splineという位置の主張になる。v1のzoneは`preferredArea`（8方位＋CENTER＋ANY）と`areaShare`しか持たないため、そのgeometryはこちらが発明することになる。
- `MetricRangeConstraint` の `subject` は `feature:<id>` を要求する。featureが無ければ relief／landRatio／water のmetric rangeも書けない。
- `StructureRequest` は `preferredFeatureId` を要求する。v1のstructureはzoneを指すため、featureが無ければ移せない。

差異のある正本は次のとおりである。

- 本節の「自動変換できるもの」: 推測値を`SOFT`＋provenanceとして書き出す方針。
- `docs/design-v2/task-index.md` `V2-12-04` Scope: 「v1から欠落した位置・形状・関係・地質をhard constraintへ推測補完しない — **欠落はdraft／未指定のまま**」。Gateは「変換にhard constraint推測が必要なら停止する」。
- AGENTS.md §6: 「v1から欠落した位置、形状、関係、地質をhard constraintとして補完しない」。

`V2-12-04`は後2者（Scope／Gate／AGENTS）に従い、欠落を未指定のまま残してreportへ出す実装とした。本節の方針を採るには、geometryにstrengthまたはcandidate表現を導入する`TerrainIntentV2`契約変更が必要であり、それは`V2-12-04`のScope外である。**この差異は本Taskでは解消しておらず、方針決定が必要である。**

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

`V2-2-10`は`release-manifest-v2.schema.json`、canonical exclude-self checksum、strict `artifacts[]`／`requiredCapabilities[]`、directory／ZIP publisher/verifierを実装した。core-only releaseは`manifest.json`だけを含み、配列は空でなければならない。`V2-2-11`は`surface-2_5d`を別dispatchで有効化し、request／intent／Blueprint、field index＋sidecar、validation、preview index＋PNG、tile metadata＋Sponge v3をexact setとして固定した。`V2-3-14`は`hydrology-plan`を`surface-2_5d`依存で追加し、plan／routing／reconciliation／validation／12 previewをexact setとして固定した。`V2-4-14`は`environment-fields`を`hydrology-plan`＋`surface-2_5d`依存で追加した。`V2-5-17`は`sparse-volume`を`environment-fields`＋`hydrology-plan`＋`surface-2_5d`依存で追加し、`volume/`配下のSDF primitive／ordered CSG／AABB index／volume validation／3D volume tileをexact setとして固定した（CSG→SDF、AABB→CSGのchecksum binding、validation `sourcePlanChecksum`＝CSG、volume tileのstrict Sponge v3 read-back）。volume tileは`volume-offline-tile-artifact-v2`／`volume-sponge-schematic-v3`の専用typeでsurface tile集合と分離する。各capabilityは直前capability setをstrictに回帰verifyする。未知capability、未知type/version、index外file、symlink、ZIP traversal／case collision、checksum、semantic binding、entry／展開／resident／disk budget違反を拒否する。format 1 verifierのallowlistを共有可変Listにして緩めず、version別verifierを使う。

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

`V2-6-01`は上記pipelineの契約層を固定した。`PlacementPlanV2`／`PlacementJournalV2`はRelease format 2 manifest checksum、world target／bounds／anchor、capability set、canonical tile order、envelope／reservation／confirmation binding slot、operation ID、format 2 journal statesをchecksum-boundに持ち、v1 journal codecとは別versionでstrict読取する（ADR 0020）。`V2-6-02`〜`V2-6-05`はmutation／effect envelope、reservation／confirmation、snapshot-all、containment evidenceを順に固定し、`V2-6-06`はこれらを迂回できないcanonical apply application serviceを追加した（ADR 0024）。`V2-6-07`はbounded settleとeffect envelope全体のexact verifyを追加し、成功時だけterminal `APPLIED`へ進める（ADR 0025）。`V2-6-08`はrollback、`V2-6-09`はoperation-bound Undoを追加した（ADR 0026／0027）。startup Recoveryは`V2-6-10`である。

観測FutureのtimeoutだけでScheduler受理済みoperationを取消済みにしない。dispatch前validation／confirm、実operation ID、late completion reconciliationを既存規則どおり持つ。

snapshot scope拡大はdiskと時間へ直接影響するため、Release 2のestimateをRelease 1と分ける。offline generation/exportは1000×1000 gateを必須とする。Paperの初期配置上限を500角とするなら500×500実world apply/verify/Undoの成功が必須であり、1000角配置をsupportedと宣言するには1000×1000の同じ実測を別途必須とする。実測していない寸法は設定上限で拒否する。

## 9. CLI／Paper／Provider

- `V2-12-06`以降はv2だけをproduction commandとして提供する。既存v1 artifactはread-only verifyまたは明示`migrate`で扱い、欠落値やversionから暗黙auto-upgradeしない。
- `--intent-version` 等の具体的UIは実装時に決めるが、曖昧なauto-upgradeをしない。
- Providerごとにv2 structured output capabilityを宣言し、未対応model／capabilityはhard rejectする（V2-6-11、ADR 0029）。v2 dispatchをlegacy readerへ暗黙fallbackしない。
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
| Hydrology／Environment／Volume capability | `V2-3-14`（完了）／`V2-4-14`（完了）／`V2-5-17` | 各直前capability setのstrict回帰 |
| Release 2 placement contract | `V2-6-01` | v1 journalと別versionでchecksum-bound |
| Paper mutation開始条件 | `V2-6-02`〜`V2-6-06` | envelope、予約／confirm、snapshot-all、containment完了 |
| Rollback／Undo／Recovery | `V2-6-08`〜`V2-6-10` | failure injectionとv1回帰 |
| Cross-capability hardening | `V2-6-12`（完了、ADR 0030） | 全valid prefixとdirectory／ZIP corruption拒否；Release 1 allowlist未緩和 |
| Supported runtime／dimension | `V2-6-14`〜`V2-6-18` | 今回buildの実機evidenceがある範囲だけcatalog化 |
| TerrainIntent v2 feature projection | `V2-15-03`／`V2-15-04`（完了） | ADR 0036承認、explicit projection、14 mapping、seed tuple、compatibility fixture／checksum回帰 |

`V2-6-14`／`V2-6-15`は実機依存であり、環境がなければ`BLOCKED_EXTERNAL`とする。`V2-6-16`／`V2-6-17`は無効化済み。mockや過去buildの結果でmigration gateを閉じない。個別Task完了でv1 defaultをv2へ切り替えず、`V2-6-19`のrelease candidate監査後もexplicit version dispatchを維持する（判断根拠は§15）。V2-6が完了しても、既存Beta hardeningの未チェック項目を根拠なく完了へ変更しない。

## 15. V2-6-19時点の完全移行判断（2026-07-19）

`V2-6-19` RC auditは、v1→v2の完全移行（v1 production code／command／Schemaの削除とv2 default化）を**実施しない**と判断した。根拠は時間ではなく、次の技術的未達条件である。

1. **v2のPaper apply能力が最小範囲でしか昇格していない。** `V2-11-01`（2026-07-20）で`paper_apply`／`post_apply_validation`／`snapshot`／`rollback`／`restart_recovery`を`SUPPORTED`にできたのは、`surface-2_5d` capabilityの4 entry（SANDY_BEACH／BREAKWATER_HARBOR／HARBOR_BASIN／ROCKY_CAPE）を64×64以内で使う場合だけである。他prefixは`EXPERIMENTAL`、Release capability未接続のfoundation entryは`UNSUPPORTED`のままである。寸法カバレッジは`V2-11-06`で改善し、FAWE 2.15.2では500×500／1000×1000のPaper lifecycle実測（`V2-11-04`／`V2-11-05`）に基づき1000×1000まで`SUPPORTED`となった（WorldEdit 7.3.19単独は64×64のまま）。ただしv1と同等の運用実績（feature多様性・production稼働）はまだ無く、feature範囲は4 entryに限られる。
2. **v2の公開Intent経路が部分接続。** foundation／volume kindはdiagnostic bindingのままで、公開Intent dispatch・Release 2 capability・CLI／Paperの通常生成経路に接続していない。v2でrequest→design→generate→export→placementの全経路を通常利用できるのは、capability明示のdesign path（V2-6-11）と`/lfc r2`（評価用）に限られる。
3. **Track B／Cが未完。** 画像入力（V2-7、1/7）とLARGE生成（V2-8、1/8）はv1相当機能（画像role前処理、1000×1000生成）をv2側で置換できる段階にない。
4. **v1契約の凍結はAGENTS.mdの正本規範。** v1 Schema、generator `3.0.0-phase6`、Release format 1、v1 placement／Undo、既存checksum／goldenは変更禁止であり、v1削除はこの互換性境界の破壊を必要とするため停止条件に該当する。

このため境界は次のとおり維持する: **v1が既定経路**（CLI／Paper既定command、Release 1）、**v2は明示選択経路**（capability明示design、`/lfc r2`、Release 2）。新規利用へのv2既定化は、`V2-11-01`（完了）に加えてV2-7／V2-8の各gate完了後に、新しいADR（`V2-12-01`）で再判断する。互換レイヤー（version dispatch、v1 adapter）は上記条件が解消するまで残し、削除条件はこの節の1〜3の解消とする。

### 2026-07-21更新（V2-12-05）

上記は`V2-6-19`時点の判断記録である。その後、ADR 0035がv1退役governanceを承認し、`V2-12-02`〜`V2-12-04`がproduction export／正式command／migrationを実装した。初回カバレッジ監査で判明したrequest authoring、job／candidate／確認付きexport、運用verbの不足は`V2-12-08`〜`V2-12-10`で解消し、再監査で未承認劣化なしを確認した。これにより`V2-12-05`は人間承認のもと既定をv2へ切替えた。現在の境界は、**v2が既定、v1は明示opt-inのdeprecated経路**である。v1 Schema、generator `3.0.0-phase6`、Release format 1、placement／Undo、goldenの意味は変えていない。v1 writer／通常commandの削除は`V2-12-06`だけが担当し、legacy reader／verifier／migrationは維持する。

## 16. TerrainIntent v2 feature projection migration（V2-15-04）

これはv1→v2 migrationとは別の、historic TerrainIntent v2 identifier projectionの移行である。
historic Schema／model／fixtureは変更せず、呼出し側が`LEGACY_V2`を明示した専用readerだけで読む。
新規targetは`featureProjection: CANONICAL_V2`を必須とする別Schema／modelで、generic dispatcherは
discriminator欠落からlegacyを推測しない。

strict migratorはADR 0036の14 mappingだけを実行する。owner relationの欠落・複数・unsupported、
parameter loss、seed tupleの欠落／余剰をdocument全体の失敗として扱い、部分migrationしない。
成功時は元文書を上書きせず、canonical targetとversion付き旧新checksum reportをstagingへ書き、
exact file set、strict read-back、checksum／seed binding一致後にatomic publishする。再入力が
`CANONICAL_V2`なら二重移行せず`ALREADY_CANONICAL`を返す。

移行由来parent／childの`legacySeedBinding`はprovenanceではなく生成semanticであり、旧kind、
`sha256-tagged-v1`、seed namespace、module ID／version、generator versionをcanonical JSON／checksumへ
含める。14 tokenの公開lifecycleはこのTask完了では進まず、全て`CURRENT_PUBLIC`のままである。
実装停止／再有効化はoperational modeだけを変更し、一方向lifecycleをrollbackしない。
