# AGENTS.md

このファイルはLandformCraftを変更するCodex／開発エージェント向けのルート規範である。

詳細仕様の正本は`docs/`と`schemas/`、進捗の正本は`docs/roadmap.md`、v2 Task ScopeとAcceptance gateの正本は以下である。

* `docs/design-v2/task-execution-guide.md`
* `docs/design-v2/task-index.md`

このファイルと詳細文書が矛盾する場合は、作業を続行せず矛盾を報告する。推測で解決しない。

## 1. 作業開始

次の順序で必要な範囲だけ確認する。

1. `git status --short`
2. `docs/roadmap.md`
3. `docs/design-v2/task-execution-guide.md`
4. Task Index内の現在の未完了Task 1件
5. そのTaskから直接参照されるdocs、Schema、ADR、example
6. 変更候補の実装と対応テスト

`README.md`や`docs/architecture.md`は、現在Taskが製品全体の説明またはarchitecture境界を変更する場合に読む。

`docs/`、`schemas/`、ソース全体を理由なく一括読込しない。現在Taskと無関係なファイルを探索、整理、削除、reformatしない。

ユーザーの既存変更を保持する。作業前から存在する差分を自身の変更として上書きしない。

Git未初期化、前提Task未完、正本間の矛盾、clean build不能がある場合は、その事実を報告して停止する。

## 2. Task単位

V2-2〜V2-6では、`docs/roadmap.md`が示す次の未完了`V2-x-NN`を1件だけ実行する。

* 後続Taskへ進まない
* Scope外の独立機能を追加しない
* Acceptanceを弱めない
* 未完を完了と記録しない
* 親Phaseを通常Taskから完了へ変更しない
* Task完了時にtest、Schema、example、ADR、docs、roadmapを同期する

親Phaseを閉じられるのは次のTaskだけである。

* `V2-2-12`
* `V2-3-15`
* `V2-4-15`
* `V2-5-18`
* `V2-6-19`

Scopeが2個目の独立generator、大規模subsystem、新artifact format、またはgeneratorとPaper mutationの同時実装を必要とする場合は停止し、新しいTask IDを提案する。既存IDは振り直さない。

実機依存Taskで環境がない場合は`BLOCKED_EXTERNAL`とする。mockやoffline testだけで完了扱いにしない。

## 3. Task capsule

各Taskの開始時に、必要に応じて未追跡の次のファイルを作成する。

```text
.task-context/V2-x-NN.md
```

内容は現在Taskに必要なものだけとする。

1. Task Indexの対象Task全文
2. Task Execution Guideの必須事項
3. 直接関係するSchema、ADR、docsの要点
4. 変更候補ファイル
5. 凍結対象
6. Acceptanceチェックリスト
7. 必須テスト
8. 停止条件
9. 現在の関連差分と失敗テスト要約

Task capsuleは補助資料であり正本ではない。Task終了後に再利用して進捗を判断せず、必要な場合は正本から再生成する。

`.task-context/`をGitへcommitしない。

レビュー時は原則として以下だけを渡す。

* Task capsule
* `git diff --stat`
* 関連する`git diff`
* 実行したテストと結果
* 新規または変更したSchema／ADR

大量の全テストログや無関係なソースを渡さない。

## 4. 固定識別子

明示依頼なしに変更しない。

* 製品／Paper Plugin名: `LandformCraft`
* group: `com.github.nankotsu029`
* ベースpackage: `com.github.nankotsu029.landformcraft`
* Paper main: `com.github.nankotsu029.landformcraft.Landformcraft`
* Java: 21
* Minecraft／Paper: 1.21.11
* Build: Gradle Kotlin DSL、Gradle Wrapper

## 5. Architecture境界

* modelはBukkit、Paper、WorldEdit、AI Providerに依存させない
* データ契約は不変なJava `record`とenumを基本とする
* generatorはAI ProviderとMinecraft worldに依存させない
* AIは`TerrainIntent`を生成し、全block一覧や実行可能コードを生成・実行しない
* UIとPaper adapterはcore Application Serviceを呼び、生成処理を直接所有しない
* WorldEditは公開APIだけを専用adapterへ隔離する
* FAWE固有処理はWorldEdit共通処理と分離する
* Release Packageを持ち運び可能な正本とし、`.schem`はtile artifactとして扱う

## 6. v1／v2互換性

次のv1契約を変更しない。

* v1 Schema
* generator `3.0.0-phase6`
* Release format 1
* v1 placement
* v1 Undo
* 既存checksumとgolden

v2はversion dispatchで並設し、v1をin-placeで書き換えない。

future Schema、future Release、unknown capability、unknown artifact versionを推測で解釈しない。v1から欠落した位置、形状、関係、地質をhard constraintとして補完しない。

互換性を壊す必要がある場合は停止する。

## 7. v2不変条件

* `HARD`同士の矛盾はcompile errorにする
* priorityでHARD constraintを上書きしない
* feature接続、内包、上下流、supportはrelationを正本とする
* module登録順、thread完了順、locale、timezoneへ結果を依存させない
* 暗黙のlast-write-winsを使わない
* 外部JAR、script、任意class、任意式を実行しない
* semantic materialとMinecraft block stateを分離する
* fallbackで未知ID、未知version、未知encodingを受理しない

同じcanonical Blueprint、seed、generator versionから同じfield、metric、artifact、block checksumを生成する。

## 8. 地形・水系・volume

* 基本地表は2.5D fieldとする
* cave、overhang、arch、sky island等だけをbounded AABBのsparse volumeとして扱う
* 1000×1000×512のdense voxel配列を正本または常駐配列にしない
* river basin、lake、delta、fjord、waterfall graphはtile化前にglobal solveしてfreezeする
* tileごとに独立した大域graphを作らない
* surface、hydrology、volume、fluid、materialはpure `TerrainQuery`／`TerrainBlockResolver`経由で読む
* global X/ZまたはX/Y/Zでsampleする
* stageごとのhaloとsupport radiusをBlueprintへ明示する
* 全field値をBlueprint JSONへ埋め込まず、version付きsidecarとstrict indexへ保存する

volume operationは明示順序を持つ。

* `ADD_SOLID`
* `CARVE_SOLID`
* `ADD_FLUID`

`CARVE_SOLID`はfluidを所有しない。

## 9. ArtifactとRelease

入力となるpath、ZIP、画像、JSON、schematic、sidecarを信頼しない。

必ず次を検査する。

* version
* exact file set
* path traversal／case collision／symlink
* entry数
* dimensions
* decode／expand／resident／disk budget
* artifact checksum
* semantic checksum
* strict read-back

artifact bundleはstagingへ書込み、strict read-backとchecksum検証後にatomic publishする。

atomic move直前を最後のcancel観測点、move成功をcommit pointとする。commit後のlate cancelを理由に公開済みartifactを削除しない。

Release format 1のallowlistを緩めない。Release format 2では`manifest.json.artifacts[]`と`requiredCapabilities[]`を正本とし、未知、欠損、extra、duplicateを拒否する。

最終tile checksumは、structure、volume、fluidを解決したcanonical XYZ block-state streamから計算する。

## 10. Placement

v2のworld mutationは次の順序を変更しない。

```text
validate
→ preview/export
→ mutation/effect envelope算出
→ memory/region/disk見積
→ reservation
→ reservation-bound confirmation
→ snapshot-all
→ apply
→ settle
→ full verify
```

* 最初のapply前に全effect envelopeのsnapshotを完了する
* fluid、gravity、neighbor updateがsnapshot外へ影響し得る場合はapply前に拒否する
* tile checkpointをsettle後のexact verifyとして扱わない
* exact verifyはeffect envelope全体に対して行う
* 失敗時はsnapshotを逆順復元する
* snapshot外副作用をrollback可能と仮定しない
* 曖昧なrestart状態を成功または自動acceptへ分類しない

## 11. Threadとresource

Paperメインスレッドで次を実行しない。

* AI API
* 画像解析
* artifact I/O
* heightmap／erosion／validation／PNG等の重いCPU処理
* Futureの`join()`／`get()`

Bukkit／Paper worldの読み書きはPaper Scheduler経由で行う。

* CPU処理には上限付き`ExecutorService`
* blocking I/Oには必要に応じてVirtual Threads
* 非同期合成には`CompletableFuture`
* queue、task数、memory、disk、decode、cacheを事前admissionする
* cancel token、Future cancel、interrupt、job stateを連動させる
* Executor所有者を明確にし、shutdown時に終了する
* scheduler受理済みworld operationをobserver側timeoutだけで取消済みにしない
* WorldEdit／FAWEのcloseと非同期完了を確認する

## 12. Security

* API key、Authorization、Cookie、秘密ファイルをGit、fixture、ログ、manifestへ保存しない
* secretは環境変数または外部Secretから取得する
* Minecraft chatやcommand argumentへsecretを入力させない
* AIへ送るデータを現在Taskに必要な範囲へ限定する
* path、raw payload、画像metadata、secretをログでredactする

## 13. 検証

編集中は対象範囲だけを高速に検証する。

```bash
./gradlew <targeted-test>
```

Task close前には最低限次を実行する。

```bash
./gradlew test
./gradlew build
```

CLIまたは外部挙動を変更した場合は必要に応じて次も実行する。

```bash
./gradlew run --args="--help"
```

Taskに該当する以下を検証する。

* positive fixture
* negative／corruption fixture
* Schema strict round-trip
* whole／tile
* tile順
* thread数
* module／candidate順
* locale／timezone／charset
* cancel／interrupt／cleanup
* memory／CPU／disk／artifact budget
* v1 goldenと直前Release capability回帰

Phase gateだけで全scenario、全tamper corpus、全runtime profile、full clean suiteを統合実行する。

Paper adapterを`new JavaPlugin()`で単体テストしない。実機Taskは指定されたWorldEdit／FAWE環境で実行し、version、build、checksum、memory、disk、Undo／Recoveryの証拠を記録する。

## 14. 完了報告

作業終了時は次の形式で報告する。

```text
Task:
Status: COMPLETE / BLOCKED / BLOCKED_EXTERNAL / STOPPED

Changed:
- 主要変更

Tests:
- command
- result

Acceptance:
- 満たした条件
- 未達条件

Compatibility:
- v1 golden
- Release回帰
- Schema／checksum

Docs:
- 更新した文書

Risks:
- 残存riskまたはnone

Next:
- roadmap上の次Task
```

Taskを完了しても、依頼されていない次Taskを開始しない。

## 15. 完了条件

* compileと必須testが成功している
* Task ScopeとAcceptanceを満たしている
* determinism、memory、security境界を破っていない
* v1互換性を維持している
* Schema、example、ADR、docs、roadmapが実装と一致している
* 未実装機能を実装済みと表現していない
* secret、server artifact、生成物、`.task-context/`が差分へ混入していない
