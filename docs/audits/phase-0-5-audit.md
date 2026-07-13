# Phase 0〜5 総合監査

> この監査はPhase 5完了時点の履歴です。現在の状態は [phase-6-beta-audit.md](phase-6-beta-audit.md) を参照してください。

- 監査日: 2026-07-13
- 対象: Phase 0〜5
- 対象外: Phase 6の建築asset／手続き型建築、Phase 7のWeb UI／外部Worker
- 判定の優先順位: 実行コード → 自動test → 実動作 → 生成artifact → Schema／example → docs

この文書は`0.1.0-SNAPSHOT`の監査結果です。roadmapのチェックを根拠にせず、class、test、CLI、Paper log、生成物を再確認しました。ユーザーの既存の未commit変更を正本として保持し、projectの作り直しやGradle multi-project化は行っていません。

## 状態の定義

| 状態 | 意味 |
|---|---|
| `IMPLEMENTED` | 実装があり、自動testまたは今回の実動作で主要経路を確認 |
| `PARTIALLY_IMPLEMENTED` | 安全な一部は動くが、列挙した境界の一部が未実装 |
| `IMPLEMENTED_BUT_UNTESTED` | 実装は確認したが、今回必要な外部環境で未実行 |
| `DOCUMENTED_ONLY` | 将来計画としてのみ記載され、実装済みとは表示していない |
| `BROKEN` | 監査開始時に実装済み経路が誤動作。修正後の状態は対応欄に記載 |
| `MISSING` | Phase 0〜5の監査観点として存在しない |
| `OBSOLETE` | 実装と一致しない古い説明／設定。監査で修正または削除 |
| `OUT_OF_SCOPE` | Phase 6以降、非対応形式、実課金live testなど今回実装しないもの |

## Initial state

- Gitは初期化済みで、監査開始時点からdirty worktreeだった。29個のtracked fileに1,056 additions／123 deletionsがあり、Phase 2〜5の多数のsource、test、Schema、example、ADRがuntrackedだった。
- 既存変更は削除、移動、reformatせず、その上へ監査修正を加えた。
- 開始時の`./gradlew test`と`./gradlew build`はいずれも成功（UP-TO-DATE）。
- 開始時roadmapは「Phase 6開始時点」、Phase 0〜5を一律完了としていた。
- `TODO|FIXME|XXX|UnsupportedOperationException`等の検索では、実装stubに該当する箇所はなかった。

## 監査表

### Phase 0: Model、Schema、Codec

| Phase | 機能 | 状態 | 実装箇所 | テスト | docs | 問題 | 対応 |
|---|---|---|---|---|---|---|---|
| 0 | request／bounds／image role model | IMPLEMENTED | `model/GenerationRequest`, `GenerationBounds`, `ReferenceImage`, `ReferenceImageRole` | `GenerationRequestTest`, `LandformDataCodecTest`, `SchemaContractTest` | `data-model.md` | role count、path、inclusive height、enum契約を照合 | Schema／enum／example一括testを追加 |
| 0 | Intent／Blueprint model | IMPLEMENTED | `TerrainIntent`, `WorldBlueprint`, `BlueprintCompiler` | codec／generator／Provider E2E tests | `data-model.md`, ADR 0001／0003／0006 | Provider出力とgenerator境界を確認 | package境界testを追加 |
| 0 | Plan／Tile／Validation model | IMPLEMENTED | `TerrainPlan`, `TerrainTile`, `TilePlan`, `StructurePlan`, `ValidationResult`, `GenerationMetrics` | `TerrainGeneratorTest` | `data-model.md` | collectionのdefensive copyと範囲不変条件を確認 | 変更なし |
| 0 | Release／Placement／Design／Image model | BROKEN → IMPLEMENTED | `ExportManifest`, `ManifestTile`, `Placement*`, `DesignAudit`, `GenerationJobSnapshot`, `Image*Evidence` | `SchemaContractTest`, codec／Release／Design／Placement tests | `data-model.md`, `artifact-format.md` | job schemaVersion欠落、schematic checksumとterrain checksumの意味混同、画像証跡とProvider応答の結合不足 | `schemaVersion`、`terrainChecksum`、normalization／provider／response／prompt／validation fieldsを追加 |
| 0 | model package独立性 | IMPLEMENTED | `model` package | `PackageBoundaryTest` | `architecture.md`, `AGENTS.md` | 外部依存混入を自動検出していなかった | modelはJDKのみ、generator／coreからPaper／AI逆流なしをtest化 |
| 0 | 全Schema／example整合 | BROKEN → IMPLEMENTED | `schemas/*.json`, `examples/*` | `SchemaContractTest` | `data-model.md` | 個別testだけではenum、全example、Schema自体のJSON構文退行を見逃す | 全Schema parse、全静的example検証、enum同期testを追加 |
| 0 | JSON／YAML Codec | IMPLEMENTED | `format/LandformDataCodec`, `validation/StructuredDataValidator` | `LandformDataCodecTest` | `data-model.md` | unknown field、duplicate key、invalid enum、future version、document上限を確認 | invalid role、画像16枚超過testを追加 |
| 0 | versioning | IMPLEMENTED | Schema `schemaVersion`、generator `2.0.0-phase2`、Gradle application version | codec／generator tests | `data-model.md` | pre-release v1修正中なのに「公開済みimmutable」と断定 | stable公開後immutableへ文言修正 |

### Phase 1: Terrain、Validation、Preview

| Phase | 機能 | 状態 | 実装箇所 | テスト | docs | 問題 | 対応 |
|---|---|---|---|---|---|---|---|
| 1 | low-resolution layout／height／coast／island／river／lake | IMPLEMENTED | `generator/TerrainGenerator`, layout／noise classes | `TerrainGeneratorTest` | `architecture.md`, ADR 0003 | 実生成gridとfixtureを確認 | 変更なし |
| 1 | mountain／valley／zone／material／feature／erosion | IMPLEMENTED | generator package | `TerrainGeneratorTest` | `roadmap.md` | 名前だけのstubなし | 変更なし |
| 1 | global coordinate／margin／edge tile／seam | IMPLEMENTED | `TerrainGenerator.generateTile` | seam／standalone tile tests | `architecture.md` | 端tileを含む全セル比較あり | 変更なし |
| 1 | seed／thread／locale／timezone決定性 | PARTIALLY_IMPLEMENTED → IMPLEMENTED | generator、candidate seed derivation | `TerrainGeneratorTest` | `data-model.md` | locale／timezone／pool／candidate順の明示test不足 | 同一checksum testを追加 |
| 1 | bounds／height／water／river／connectivity validator | IMPLEMENTED | `validation/TerrainValidator` | abnormal grid tests | `roadmap.md` | coverageが総面積だけで、重複＋欠損が相殺可能 | index／origin／寸法／ID／重複座標を厳密化しtest追加 |
| 1 | 8 PNG preview | PARTIALLY_IMPLEMENTED → IMPLEMENTED | `preview/TerrainPreviewRenderer` | `TerrainPreviewRendererTest` | README | 8 file存在だけでは同一画像copyを検出できなかった | 8つのdistinct checksumとbyte決定性をtest |
| 1 | 500×500性能 | IMPLEMENTED | CLI generate、`TerrainPerformanceValidator` | budget test＋CLI実測 | `performance.md` | 過去version／概算値だった | 219〜225 ms、12,065,536 bytes、16 tilesで更新 |
| 1 | 1000×1000性能 | IMPLEMENTED | CLI generate | 手動CLI実測 | `performance.md` | 自動testは500だけ | 492〜760 ms、48,262,144 bytes、64 tilesを記録。1000は手動smoke維持 |

### Phase 2: Schematic、Release、Verify

| Phase | 機能 | 状態 | 実装箇所 | テスト | docs | 問題 | 対応 |
|---|---|---|---|---|---|---|---|
| 2 | block column materialization | IMPLEMENTED | `worldedit/BlockColumnMaterializer`, `MinecraftBlockPalette` | `BlockColumnMaterializerTest` | `artifact-format.md` | negative Y、inclusive Y、全SurfaceMaterialを確認 | 変更なし |
| 2 | Sponge v3 stream writer／reader | IMPLEMENTED | `SpongeSchematicWriter`, `SpongeSchematicInspector` | `ReleasePackageTest` | ADR 0004 | full 3D Listを保持しない。通常／edge tileをWorldEdit readerで読込 | 変更なし |
| 2 | artifact checksum／semantic checksum | BROKEN → IMPLEMENTED | `ManifestTile`, `ReleasePublisher` | Release／Schema tests | `artifact-format.md` | `.schem` checksumだけでmaterialize前semantic checksumを保存していなかった | `terrainChecksum`をmanifest v1へ追加 |
| 2 | Release構成 | IMPLEMENTED | `ReleasePublisher` | `ReleasePackageTest`, CLI test | `artifact-format.md` | manifest／request／Intent／Blueprint／8 preview／schem／asset一覧／README／checksums／ZIP／digestを確認 | 変更なし |
| 2 | strict directory／ZIP verify | PARTIALLY_IMPLEMENTED → IMPLEMENTED | `ReleaseVerifier`, `SpongeSchematicInspector` | `ReleasePackageTest` | `security.md` | symlink、ZIP slip、case collision、future manifestの明示test不足。Schema例外型がCLIへ漏れた | 異常test追加、Schema例外を`IOException`へ統一 |
| 2 | atomic publish | IMPLEMENTED | `ReleasePublisher`, `DesignArtifactPublisher` | read-back／existing release tests | ADR 0004 | self-verify、fsync、atomic move、上書き拒否を確認 | 変更なし |
| 2 | Release publishのthread境界 | BROKEN → IMPLEMENTED | `core/ReleaseApplicationService` | Release tests | `architecture.md` | CPU completion threadでfile publishを続ける経路 | admitted I/O virtual threadへ分離 |
| 2 | Paper／CLI JAR境界 | IMPLEMENTED | Gradle `shadowJar`, `PluginArtifactTest` | JAR inspection test | `development.md` | Paper／Bukkit／WorldEdit／FAWE本体のshade禁止 | 禁止class／config／schema同梱testを維持 |

### Phase 3: Paper、Placement、Recovery

| Phase | 機能 | 状態 | 実装箇所 | テスト | docs | 問題 | 対応 |
|---|---|---|---|---|---|---|---|
| 3 | Paper load／plugin metadata | IMPLEMENTED | `Landformcraft`, `plugin.yml` | `PluginArtifactTest`、今回WorldEdit実server smoke | `development.md` | load、command登録、正常停止を確認 | current build＋Paper 1.21.11＋WE 7.3.19で再実行 |
| 3 | help／selection／apply／undo command | IMPLEMENTED | `paper/LandformCraftCommand` | suggestion／artifact tests＋実server help | `commands.md` | 以前のselectionは不適切な非同期受付表示と読みにくいmessage、補完不足 | Adventure色／行分け、未選択案内、非同期cache補完を実装済みと再確認 |
| 3 | version command | MISSING | なし | なし | 実装済みとは記載なし | `/lfc version`はない | 低優先。plugin versionはserver plugin一覧／JAR名で確認 |
| 3 | request／job／design Paper command | DOCUMENTED_ONLY | なし | なし | `commands.md`の「将来」section | CLI／coreだけが実装 | Phase 6／7へ先行せず未実装表示を維持 |
| 3 | permission／tab completion | IMPLEMENTED | `plugin.yml`, `LandformCraftCommand`, `LandformCraftSuggestions` | `PluginArtifactTest`, `LandformCraftSuggestionsTest` | `commands.md` | tokenは補完せず、権限別候補、release／world／ID／coordsを確認 | 変更なし |
| 3 | WorldEdit selection | IMPLEMENTED | `WorldEditSelectionAccess`, `PaperWorldEditSelectionService` | 実server incomplete selection／help確認 | `commands.md` | player以外、pluginなし、incompleteを明示。非cuboidはinclusive bounding regionとして扱う | 表示を修正済み。selection自体に生成最大範囲制約は適用しない |
| 3 | plan／confirm | PARTIALLY_IMPLEMENTED | `PlacementApplicationService`, `PlacementPlan` | `PlacementApplicationServiceTest` | ADR 0005 | Release／world／height／border、TTL、一回token、action／plan結合は実装。actor、disk見積、overlap予約は未実装 | 未実装をoperations／roadmap gateへ明記 |
| 3 | snapshot／apply／verify／rollback | IMPLEMENTED | `PlacementApplicationService`, `PaperWorldEditPlacementGateway`, `WorldEditWorldAccess` | success、paste failure、reverse rollback、snapshot tamper tests | `operations.md` | tile単位、checksum、full block verify、reverse restoreを確認 | 変更なし |
| 3 | Undo | PARTIALLY_IMPLEMENTED → IMPLEMENTED | `PlacementApplicationService.undo` | success、snapshot tamper、world drift tests | `commands.md`, `operations.md` | 配置後のworld driftを確認せず人の変更を消す危険 | Undo前に全tileをReleaseとfull照合し、drift時は無変更拒否 |
| 3 | restart recovery | PARTIALLY_IMPLEMENTED | `recoverInterrupted`, atomic journal | interrupted journal test | `operations.md` | startupで`RECOVERY_REQUIRED`へするがmanual recovery commandなし | 自動推測しない方針を維持し、未実装を明記 |
| 3 | cancel／plugin disable during mutation | PARTIALLY_IMPLEMENTED | dispatcher、executor shutdown、startup recovery | pending dispatcher／executor tests、journal recovery test | `architecture.md`, `operations.md` | 開始後cancel／disableからrollback完了までの専用protocolと実server failure injectionなし | overclaimを削除しPhase 6開始gateへ。停止後は`RECOVERY_REQUIRED`で扱う |
| 3 | WorldEdit実server | IMPLEMENTED | Paper runtime | `runServer`実行、`run/logs/latest.log`、実placement journal | `development.md` | current audit buildを再確認 | Paper 1.21.11 build 132＋WE 7.3.19でload、help、32×32 plan／apply／full verify／Undo／journal read-back／正常停止成功 |
| 3 | FAWE実server | IMPLEMENTED_BUT_UNTESTED | WorldEdit公開API adapter | 過去のFAWE 2.15.2 load／small apply／undo log | `roadmap.md` | current audit buildではFAWE JARがなく再実行不能 | current regression smokeを未checkの開始gateとして記録 |

### Phase 4: AI Provider

| Phase | 機能 | 状態 | 実装箇所 | テスト | docs | 問題 | 対応 |
|---|---|---|---|---|---|---|---|
| 4 | Provider-neutral SPI | IMPLEMENTED | `ai.spi/TerrainDesignProvider`, `TerrainDesignRequest/Result` | Provider／Application Service E2E tests、package boundary test | ADR 0006 | generatorへHTTP／Provider型の漏れなし | 画像送信能力をSPIで明示 |
| 4 | Imported JSON／Fixture | IMPLEMENTED | `ImportedJsonTerrainDesignProvider`, `FixtureTerrainDesignProvider` | CLI／Application Service tests | `commands.md` | 同じimage前処理とvalidationを通る | 変更なし |
| 4 | OpenAI Responses adapter | IMPLEMENTED | `OpenAiTerrainDesignProvider` | fake HTTP shape／image／E2E tests | `ai-providers.md` | 現行`text.format`／`input_image`形式の文書がなかった | 公式現行仕様と照合し文書追加 |
| 4 | Anthropic Messages adapter | IMPLEMENTED | `AnthropicTerrainDesignProvider` | fake HTTP shape／image／E2E tests | `ai-providers.md` | 現行`output_config.format`形式の文書がなかった | 公式現行仕様と照合し文書追加 |
| 4 | timeout／retry／cancel／quota | IMPLEMENTED | `ai.http/RetriableJsonHttpClient`, `ProviderQuota` | 429、503枯渇、400非retry、timeout、cancel tests | `operations.md`, `ai-providers.md` | response全体を無制限byte[]で受ける経路 | stream読込4 MiB上限、request 24 MiB上限を追加 |
| 4 | Structured Output再検証 | IMPLEMENTED | Provider schema support、`StructuredDataValidator`, compiler | invalid response／E2E tests | `ai-providers.md` | Provider subsetと完全Schemaを分離 | 完全local Schema／record検証を維持 |
| 4 | secret／audit | IMPLEMENTED | CLI env lookup、`DesignAudit`, safe errors | payload／audit secret/path tests | `security.md` | API keyをarg／config／artifactへ保存しない | 変更なし |
| 4 | Design Package／job state | IMPLEMENTED | `TerrainDesignApplicationService`, publisher／verifier、job repository | success／failure／cancel／tamper／missing tests | `artifact-format.md` | evidenceとのProvider応答結合不足 | evidenceとauditのprovider／response／prompt一致をverify |
| 4 | paid live API compatibility | IMPLEMENTED_BUT_UNTESTED | 両HTTP adapter | fake HTTPのみ | `ai-providers.md` | secret、課金、model capabilityを必要とする | 通常buildでは未実行。利用者指定modelで手動smokeが必要 |
| 4 | Paper Provider wiring | DOCUMENTED_ONLY | なし | なし | README／operationsで未実装 | CLI／coreのみ | dead provider configを削除し、未実装表示を維持 |

### Phase 5: Image input

| Phase | 機能 | 状態 | 実装箇所 | テスト | docs | 問題 | 対応 |
|---|---|---|---|---|---|---|---|
| 5 | role enum／Schema／Provider mapping | IMPLEMENTED | `ReferenceImageRole`, request Schema、両Provider | `SchemaContractTest`, codec／HTTP tests | `data-model.md` | 6 roleの同期を自動保証していなかった | enum同期test追加。roleなし／unknown拒否 |
| 5 | root／path／file security | PARTIALLY_IMPLEMENTED → IMPLEMENTED | `ReferenceImageProcessor` | traversal、symlink、hardlink alias、duplicate tests | `security.md`, ADR 0007 | follow後readとTOCTOU／hardlink方針不足 | `NOFOLLOW_LINKS` open、前後属性照合、同一file key拒否、方針明記 |
| 5 | format／magic／decode／frame | IMPLEMENTED | `ReferenceImageProcessor` | PNG、JPEG、spoof、corrupt、multi-frame tests | `security.md` | WebPは非対応 | PNG／JPEGだけを明記。WebPはOUT_OF_SCOPE |
| 5 | byte／pixel／dimension／count／aspect limits | BROKEN → IMPLEMENTED | processor、`TerrainDesignRequest`, HTTP client | oversize／aggregate／aspect／count tests | `security.md` | 正規化合計64 MiB表記とProvider 16 MiB制約が不一致、aspect上限なし | 合計16 MiBへ統一、32:1上限、24 MiB request guard |
| 5 | EXIF／metadata／color／alpha normalization | PARTIALLY_IMPLEMENTED → IMPLEMENTED | processor normalization | EXIF、metadata、color、alpha、deterministic bytes tests | ADR 0007 | orientation 1画像を元objectのまま返し、色表現のcanonical化が不明瞭 | 常にTYPE_INT_ARGBへcopy、alpha保持、metadataなしPNGへ再encode |
| 5 | image provenance | BROKEN → IMPLEMENTED | `ImageInputEvidence`, `ImageEvidenceEntry`, Design verifier | Schema／roundtrip／semantic tamper tests | `artifact-format.md` | image ID、送信有無、response結合、変換version、validation結果不足 | 全fieldを追加しauditとの一致を検証 |
| 5 | TOP_DOWN座標／role分離 | IMPLEMENTED | processor、Provider prompt | coordinate／MOOD non-map tests | `architecture.md` | `MOOD_REFERENCE`を座標図扱いしない | 変更なし |
| 5 | text／image contradiction | PARTIALLY_IMPLEMENTED | edge observation／consistency checks | east/west sea-land conflict、inconclusive tests | ADR 0007 | 強い方角海陸だけ。湖有無、HEIGHT_REFERENCE高山等の意味矛盾は未実装 | docsを「強い海陸矛盾」に限定。黙って採用せず不確実はINCONCLUSIVE |
| 5 | Provider image submission | IMPLEMENTED | `PreparedReferenceImage`,両Provider | payload MIME／bytes／role／path非送信／oversize／cancel tests | `ai-providers.md` | raw path／metadata送信なし、base64はbounded | response stream closeとaggregate上限を確認 |
| 5 | image→Provider→Intent→Preview E2E | IMPLEMENTED | `TerrainDesignApplicationService`＋fake HTTP＋generator | `HttpTerrainDesignProviderTest` | `architecture.md` | API／import／fixtureを同じpipelineへ合流 | 8 previewまでE2E test追加 |
| 5 | ICC metadata node-size専用制限 | PARTIALLY_IMPLEMENTED | source byte／pixel cap、metadata stripping | metadata stripping test | `security.md` | ICC node単独の上限はない | 8 MiB source上限でbounded。必要なら専用ImageIO plugin hardeningを将来追加 |
| 5 | structure referenceから建築物生成 | OUT_OF_SCOPE | Intent roleのみ | role mapping test | README | Phase 6 asset配置はない | 「参考情報のみ」、Phase 6未実装を維持 |

### Phase間、CLI、Config、Docs

| Phase | 機能 | 状態 | 実装箇所 | テスト | docs | 問題 | 対応 |
|---|---|---|---|---|---|---|---|
| 0–5 | manual JSON→Release→verify | IMPLEMENTED | CLI／core／format／generator | CLI tests＋今回の実CLI | README／commands | 別directory／ZIP read-backを確認 | 正常、改変、欠損を再実行 |
| 4–5 | fake OpenAI／Anthropic→Preview | IMPLEMENTED | HTTP Provider＋compiler／generator／preview | `HttpTerrainDesignProviderTest` | `ai-providers.md` | Provider切替後段のE2E不足 | 両Providerから8 previewまでtest |
| 2–3 | verified Release→plan→apply→undo | IMPLEMENTED | Placement service／fake gateway | `PlacementApplicationServiceTest` | operations | drift test不足 | drift拒否を追加 |
| 0–5 | CLI help／主要subcommand | IMPLEMENTED | `LandformCraftCli` | `LandformCraftCliTest`＋実CLI | README／commands | help、引数、exit codeを照合 | 実動作を監査artifactへ保存 |
| 3 | Paper config | BROKEN → IMPLEMENTED | `config.yml`, `Landformcraft` | `PluginArtifactTest` | operations | readerが使わない`limits`／`providers` keyが存在 | dead keyを削除し、実装3 sectionだけへ同期 |
| 3 | plugin.yml permission／command | IMPLEMENTED | `plugin.yml`, command | `PluginArtifactTest` | commands | admin childrenとcommandを照合 | 変更なし |
| 0–5 | README／docs／ADR | OBSOLETE → IMPLEMENTED | README、docs、ADR 0001〜0007 | command／artifact contract tests＋手動照合 | 本文書 | Phase 6開始断定、古い性能値、過大画像上限、停止時cancel overclaim | 実装境界へ更新しProvider文書を追加 |
| 6–7 | structure assets／Web UI | OUT_OF_SCOPE | なし | なし | roadmapの将来計画 | 実装済みではない | 未開始を明記 |

## 主な修正

1. Schema／record／exampleを同期し、全Schema、全example、enum、package境界を自動監査するtestを追加。
2. Release manifestへschematic artifactとは別のterrain semantic checksumを追加し、verify異常系を拡充。
3. tile coverage validatorを面積比較から厳密grid比較へ変更。
4. Preview 8種が別内容であり、同入力でbyte一致することをtest。
5. Release publishをCPU completion threadからI/O virtual threadへ移動。
6. Undo前のworld drift全tile検査を追加。
7. HTTP request／streaming response、画像aggregate／aspect／TOCTOU／hardlink aliasを制限。
8. 画像を常にcanonical ARGB化し、alphaを保持してmetadataなしPNGへ決定論的に再符号化。
9. image evidenceをProvider応答まで追跡可能にし、Design Package verifierでauditとのsemantic整合を検査。
10. dead configを削除し、README、security、operations、performance、architecture、artifact、roadmapを実装へ同期。

## Remaining issues

| 重要度 | Phase | 状態 | 影響 | 未完了理由 | 次の作業 |
|---|---|---|---|---|---|
| HIGH | 3 | PARTIALLY_IMPLEMENTED | placement開始後にplugin disable／外部cancelが発生すると、自動rollback完了を保証せず`RECOVERY_REQUIRED`になり得る | Paper停止中のScheduler、WorldEdit close、journal I/Oを1つのquiesce protocolへ結ぶ設計と実server failure injectionが必要 | 新規mutation停止→active tile checkpoint確定→rollbackまたはdurable recovery記録→dispatcher／executor停止の順序を実装し、Paperでdisable smoke |
| MEDIUM | 3 | PARTIALLY_IMPLEMENTED | 同一／重複領域を同時配置でき、snapshot容量不足を事前検知できない | world-wide reservationとstorage見積もりが未実装 | `(world UUID,bounds)`の排他予約、usable space＋worst-case snapshot見積もり、retention policy |
| MEDIUM | 3 | PARTIALLY_IMPLEMENTED | confirmationはplacement／world／origin／actionへ結合されるがactorへ結合されない | journal modelにoperator identityがない | plan時operator UUID／console identityを保存しexecuteで一致確認。permissionをrecoveryと分離 |
| MEDIUM | 3 | MISSING | `RECOVERY_REQUIRED`を安全に調査／復元する専用commandがなく、管理者手順が手動 | 不確定tileを自動推測しない方針を優先 | read-only diagnose、tile checksum照合、明示restore／accept commandとaudit log |
| MEDIUM | 3 | IMPLEMENTED_BUT_UNTESTED | current buildとFAWE 2.15.2の回帰互換性は未確認 | workspaceの`run/plugins`にFAWE JARがない。過去logだけ存在 | WorldEditを外した別run directoryでFAWE 2.15.2を導入しsmall plan／apply／undoを再実行 |
| LOW | 3 | MISSING | `/lfc version`でplugin versionを表示できない | 配置安全性に影響せず、docsも実装済みとしていない | version subcommandと補完を追加 |
| MEDIUM | 4 | IMPLEMENTED_BUT_UNTESTED | Providerのlive model capability／将来API互換はfake serverだけでは保証できない | secret／課金を通常buildへ持ち込まない | 利用者指定modelでsecret非表示のmanual live smokeをrelease前に1回実施 |
| MEDIUM | 4 | PARTIALLY_IMPLEMENTED | retryでProvider側に同一requestが二重処理される可能性 | idempotency key未実装、Provider差がある | Provider対応を調査し、response ID／job IDに結ぶidempotency strategyを追加 |
| LOW | 5 | PARTIALLY_IMPLEMENTED | 矛盾検出は方角海陸に限定され、湖／標高／zoneの意味矛盾は自動検出しない | edge pixel heuristicで断定できない | role別semantic observationを追加する場合も不確実は人間確認へ送り、warningをsilent overrideしない |

## 外部仕様照合

- OpenAI Responses APIのStructured Outputsは`text.format`のJSON Schema、画像は`input_image`のdata URLという現行公式形式を確認。
- Anthropic Messages APIのStructured Outputsは`output_config.format`、画像はbase64 sourceという現行公式形式を確認。
- Sponge Schematic v3とWorldEdit 7.3.19公開APIの利用を確認。
- model対応状況は変化するため、LandformCraftはlatest modelを固定せずCLIで明示させる。

参照: [AI Provider連携](../ai-providers.md)

## Roadmap decision

**Phase 5は完了状態として維持します。Phase 0〜5のExit criteriaは実装と自動testで満たしていますが、Phase 6開始前にPhase 3の追加修正が必要です。**

開始gateは、placement cancel／plugin disable protocol、current buildのFAWE再smoke、overlap／actor／disk対策の優先度決定です。Phase 6／7機能は今回実装していません。

## 検証artifact

最終CLI実行の成果物rootは`build/audit-phase-0-5/`です。

- `generate-500/`: 500×500の8 previewとsummary
- `generate-1000/`: 1000×1000の8 previewとsummary
- `exports/rocky-coast-001/`: Release directory、ZIP、ZIP digest
- `designs/rocky-coast-001/`: 画像ありrequestから作ったDesign Package
- `jobs/`: Design job checkpoint
- `negative/`: 改変／欠損時の失敗確認用copy
- `paper-exports/audit-paper-smoke/`: Paper＋WorldEdit smoke用32×32 Release
- `run/plugins/LandformCraft/data/placements/a6c632d6-f444-4290-9b39-7092e19ea5e6.json`: 実serverで`UNDONE`／tile `RESTORED`まで読み戻したjournal

release IDとjob UUIDを含む完全pathはCLIが実行時に採番するため、最終報告へ記載します。`./gradlew clean`はこのrootを削除するため、監査成果物を確認する場合はclean前に退避してください。
